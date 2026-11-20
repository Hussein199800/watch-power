package com.hussein.watchpower.wear

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** شاشة بسيطة: نفس معلومات الـ Tile وزر تحديث. */
class MainActivity : Activity() {
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (resources.displayMetrics.density * 28).toInt()
        info = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_RTL
        }
        val refresh = Button(this).apply {
            text = "تحديث"
            setOnClickListener {
                RefreshWorker.refreshNow(this@MainActivity)
                info.text = "جارٍ التحديث…"
                info.postDelayed({ render() }, 6000)
            }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            addView(info)
            addView(refresh)
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.BLACK); addView(col) })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val store = Store(this)
        val s = store.state()
        val now = System.currentTimeMillis()
        info.text = when {
            SecureStore.get(this, "cred") == null -> "طاقتي\n\nبانتظار الإعداد من الجوال"
            s == null -> "طاقتي\n\n" + (store.error() ?: "جارٍ التحميل…")
            else -> listOfNotNull(
                "البطارية ${SolarText.percent(s.soc)}",
                SolarText.battery(s),
                "الألواح ${SolarText.watts(s.pvW)}",
                "الحمل ${SolarText.watts(s.loadW)}",
                SolarText.footer(s, now),
                store.error()?.let { "⚠ $it" },
            ).joinToString("\n")
        }
    }
}
