package com.hussein.watchpower.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/**
 * إعداد الحساب مرة واحدة من adb (لا لوحة مفاتيح مريحة على الساعة):
 *   adb shell "am broadcast -n com.hussein.watchpower.wear/.SetupReceiver --es usr 'NAME' --es pwd 'PASS'"
 * محمي بصلاحية DUMP: يملكها adb shell فقط، فلا يستطيع تطبيق آخر على الساعة تغيير الحساب.
 * تُحفظ بصمة كلمة المرور (sha1) مشفّرة، ولا تُحفظ كلمة المرور نفسها.
 */
class SetupReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val usr = intent.getStringExtra("usr")?.trim()
        val pwd = intent.getStringExtra("pwd")
        if (usr.isNullOrEmpty() || pwd.isNullOrEmpty()) {
            resultData = "ERROR: usr and pwd are required"
            return
        }
        SecureStore.put(ctx, "cred", JSONObject().put("usr", usr).put("pwdHash", WatchPowerClient.sha1(pwd)).toString())
        SecureStore.remove(ctx, "session")
        Store(ctx).reset()
        RefreshWorker.schedulePeriodic(ctx)
        RefreshWorker.refreshNow(ctx)
        resultData = "OK: saved, refreshing"
    }
}
