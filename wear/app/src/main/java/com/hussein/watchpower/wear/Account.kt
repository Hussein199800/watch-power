package com.hussein.watchpower.wear

import org.json.JSONArray

/** الدخول واكتشاف الإنفرتر، مطابق لـ loginWithHash و listDevices في src/api.js */
object Account {
    fun login(usr: String, pwdHash: String, http: HttpGet = DefaultHttp): Session {
        var last: ApiException? = null
        for (profile in listOf("dess", "shine")) {
            val s = Session(profile, usr, pwdHash, "", "", 0)
            try {
                WatchPowerClient(s, http).authenticate()
                return s
            } catch (e: ApiException) {
                last = e
                if (e.code == null) throw e // خطأ شبكة: لا فائدة من الخادم الآخر
            }
        }
        throw last ?: ApiException("تعذّر الدخول")
    }

    private fun first(arr: JSONArray?, pnFallback: String? = null): Device? {
        if (arr == null) return null
        for (i in 0 until arr.length()) {
            val d = arr.optJSONObject(i) ?: continue
            val pn = d.optString("pn").ifEmpty { pnFallback ?: "" }
            val sn = d.optString("sn")
            if (pn.isEmpty() || sn.isEmpty()) continue
            return Device(pn, sn, d.optString("devcode"), d.optString("devaddr").ifEmpty { "1" })
        }
        return null
    }

    private inline fun <T> soft(block: () -> T?): T? = try {
        block()
    } catch (e: ApiException) {
        if (e.code == null) throw e
        null
    }

    fun discover(c: WatchPowerClient): Device? {
        val plants = soft { c.request("queryPlants", linkedMapOf("pagesize" to "50")).optJSONObject("dat")?.optJSONArray("plant") }
        if (plants != null) for (i in 0 until plants.length()) {
            val pid = plants.optJSONObject(i)?.optString("pid")?.takeIf { it.isNotEmpty() } ?: continue
            soft {
                first(c.request("webQueryDeviceEs", linkedMapOf("pid" to pid, "page" to "0", "pagesize" to "50")).optJSONObject("dat")?.optJSONArray("device"))
            }?.let { return it }
            val collectors = soft {
                c.request("webQueryCollectorsEs", linkedMapOf("pid" to pid, "page" to "0", "pagesize" to "50")).optJSONObject("dat")?.optJSONArray("collector")
            } ?: continue
            for (j in 0 until collectors.length()) {
                val pn = collectors.optJSONObject(j)?.optString("pn")?.takeIf { it.isNotEmpty() } ?: continue
                soft { first(c.request("queryCollectorDevices", linkedMapOf("pn" to pn)).optJSONObject("dat")?.optJSONArray("dev"), pn) }?.let { return it }
            }
        }
        return soft { first(c.request("webQueryDeviceEs", linkedMapOf("page" to "0", "pagesize" to "50")).optJSONObject("dat")?.optJSONArray("device")) }
    }
}
