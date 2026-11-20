package com.hussein.watchpower.wear

import android.content.Context
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** يجلب آخر قراءة ويحدّث الـ Tile. يعمل كل 15 دقيقة، وعند الضغط على الـ Tile. */
class RefreshWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val store = Store(ctx)
        store.markAttempt(System.currentTimeMillis())
        val cred = SecureStore.get(ctx, "cred")?.let { JSONObject(it) } ?: return Result.success()
        try {
            val session = Session.fromJson(SecureStore.get(ctx, "session"))
                ?: Account.login(cred.getString("usr"), cred.getString("pwdHash")).also { SecureStore.put(ctx, "session", it.toJson()) }
            val client = WatchPowerClient(session)
            try {
                val dev = store.device()
                    ?: Account.discover(client)?.also { store.saveDevice(it) }
                    ?: throw ApiException("لم يُعثر على إنفرتر في الحساب")
                val points = client.lastData(dev)
                if (points.isEmpty()) throw ApiException("لا توجد بيانات من الإنفرتر")
                store.saveState(SolarParser.parse(points, System.currentTimeMillis()))
            } finally {
                if (client.sessionChanged) SecureStore.put(ctx, "session", client.session.toJson())
            }
        } catch (e: Exception) {
            Log.w(TAG, "refresh failed", e)
            store.saveError(SolarText.error(e.message))
        } finally {
            TileService.getUpdater(ctx).requestUpdate(SolarTileService::class.java)
        }
        return Result.success()
    }

    companion object {
        const val TAG = "TaqatiWear"
        private const val PERIODIC = "taqati-wear-periodic"
        private const val NOW = "taqati-wear-now"
        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).setConstraints(net).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun refreshNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(net).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, req)
        }
    }
}
