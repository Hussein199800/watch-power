package com.hussein.watchpower.wear

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** آخر قراءة معروضة على الساعة. منطق نقي يُختبر باختبارات JVM. */
data class SolarState(
    val soc: Int?,
    val pvW: Int?,
    val loadW: Int?,
    val batW: Int?,
    val gridV: Int?,
    /** وقت القراءة كما يرسله الإنفرتر (بتوقيت الساعة)، أو 0 إن لم يُرسل */
    val readAt: Long,
    /** وقت جلب القراءة من الخادم */
    val fetchedAt: Long,
) {
    fun isStale(now: Long, staleMin: Int = 30): Boolean {
        val at = if (readAt > 0) readAt else fetchedAt
        return at <= 0 || now - at >= staleMin * 60_000L || now - at < -60_000L
    }

    fun toJson(): String = JSONObject()
        .put("soc", soc ?: JSONObject.NULL).put("pvW", pvW ?: JSONObject.NULL)
        .put("loadW", loadW ?: JSONObject.NULL).put("batW", batW ?: JSONObject.NULL)
        .put("gridV", gridV ?: JSONObject.NULL).put("readAt", readAt).put("fetchedAt", fetchedAt)
        .toString()

    companion object {
        fun fromJson(s: String?): SolarState? = try {
            val o = JSONObject(s ?: return null)
            fun int(k: String) = if (!o.has(k) || o.isNull(k)) null else o.optDouble(k).takeIf { !it.isNaN() }?.toInt()
            SolarState(int("soc"), int("pvW"), int("loadW"), int("batW"), int("gridV"), o.optLong("readAt"), o.optLong("fetchedAt"))
        } catch (e: Exception) {
            null
        }
    }
}

/** تحويل نقاط queryDeviceLastData إلى حالة. نفس أنماط AlertEvaluator.snapshot في تطبيق الهاتف. */
object SolarParser {
    private fun num(v: String?): Double? {
        if (v == null || v.isBlank() || v.trim().all { it == '-' }) return null
        return v.replace(Regex("[^0-9.\\-]"), "").toDoubleOrNull()
    }

    private fun find(points: List<Point>, vararg patterns: Regex): String? {
        for (re in patterns) for (p in points) if (re.containsMatchIn(p.title.trim().lowercase())) return p.value
        return null
    }

    fun parseTs(s: String?): Long? {
        val m = Regex("(\\d{4})-(\\d{2})-(\\d{2})[ T](\\d{2}):(\\d{2})(?::(\\d{2}))?").find(s ?: return null) ?: return null
        val g = m.groupValues
        return Calendar.getInstance().apply {
            clear()
            set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt())
        }.timeInMillis
    }

    fun parse(points: List<Point>, now: Long): SolarState {
        val batV = num(find(points, Regex("^battery voltage$")))
        val chg = num(find(points, Regex("^battery charging current")))
        val dis = num(find(points, Regex("^battery discharg.* current")))
        val pv = listOf(
            num(find(points, Regex("^pv1 charging power"), Regex("^pv1 .*power"), Regex("^pv charging power"), Regex("^pv input power"))),
            num(find(points, Regex("^pv2 charging power"), Regex("^pv2 .*power"))),
        ).filterNotNull().takeIf { it.isNotEmpty() }?.sum()
        return SolarState(
            soc = num(find(points, Regex("^battery capacity"), Regex("^soc$"), Regex("state of charge")))?.toInt(),
            pvW = pv?.toInt(),
            loadW = num(find(points, Regex("^ac output active power"), Regex("^output active power")))?.toInt(),
            batW = if (batV != null && (chg != null || dis != null)) (batV * ((chg ?: 0.0) - (dis ?: 0.0))).toInt() else null,
            gridV = num(find(points, Regex("^grid voltage"), Regex("^ac input voltage")))?.toInt(),
            readAt = parseTs(find(points, Regex("^timestamp$"), Regex("^time$"), Regex("^date$"))) ?: 0L,
            fetchedAt = now,
        )
    }
}

/** نصوص العرض. الأرقام دائماً 0-9 كتطبيق الهاتف. */
object SolarText {
    private val LRI = Char(0x2066)
    private val PDI = Char(0x2069)

    fun watts(w: Int?): String = when {
        w == null -> "—"
        kotlin.math.abs(w) >= 1000 -> String.format(Locale.US, "%.1f ك.واط", w / 1000.0)
        else -> "$w واط"
    }

    fun percent(soc: Int?): String = soc?.let { "$LRI$it%$PDI" } ?: "—"

    fun battery(s: SolarState): String = when {
        s.batW == null -> "البطارية"
        s.batW > 10 -> "تشحن ${watts(s.batW)}"
        s.batW < -10 -> "تُفرّغ ${watts(-s.batW)}"
        else -> "مستقرة"
    }

    /** null إذا لم يرسل الإنفرتر جهد الشبكة */
    fun grid(s: SolarState): String? = s.gridV?.let { if (it >= 90) "الكهرباء موجودة" else "الكهرباء مقطوعة" }

    fun clock(t: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(t))

    fun footer(s: SolarState, now: Long): String {
        val at = if (s.readAt > 0) s.readAt else s.fetchedAt
        if (s.isStale(now)) return "غير متصل · آخر قراءة ${clock(at)}"
        return listOfNotNull(grid(s), clock(at)).joinToString(" · ")
    }

    private val ERRORS = mapOf(
        "ERR_PASSWORD_VERIF_FAIL" to "اسم المستخدم أو كلمة المرور غير صحيحة",
        "ERR_USR_NOT_FOUND" to "المستخدم غير موجود",
        "ERR_DEV_OFFLINE" to "الإنفرتر غير متصل",
        "ERR_NO_RECORD" to "لا توجد بيانات",
    )

    fun error(msg: String?): String {
        val key = Regex("^ERR_[A-Z_]+").find(msg ?: "")?.value
        return ERRORS[key] ?: msg?.takeIf { it.isNotBlank() } ?: "خطأ غير معروف"
    }
}

/** حالة غير سرية: الجهاز المختار وآخر قراءة وآخر خطأ. */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("taqati_wear", Context.MODE_PRIVATE)

    fun state(): SolarState? = SolarState.fromJson(p.getString("state", null))
    fun saveState(s: SolarState) = p.edit().putString("state", s.toJson()).remove("error").apply()

    fun error(): String? = p.getString("error", null)
    fun saveError(e: String) = p.edit().putString("error", e).apply()

    fun lastAttempt(): Long = p.getLong("attempt", 0)
    fun markAttempt(t: Long) = p.edit().putLong("attempt", t).apply()

    fun device(): Device? {
        val raw = p.getString("device", null) ?: return null
        return try {
            JSONObject(raw).let {
                Device(it.getString("pn"), it.getString("sn"), it.getString("devcode"), it.optString("devaddr", "1"))
            }
        } catch (e: Exception) {
            null
        }
    }

    fun saveDevice(d: Device) = p.edit().putString(
        "device",
        JSONObject().put("pn", d.pn).put("sn", d.sn).put("devcode", d.devcode).put("devaddr", d.devaddr).toString(),
    ).apply()

    fun reset() = p.edit().clear().apply()
}
