package com.example.gudgum_prod_flow

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.gudgum_prod_flow.data.notification.UtpadFirebaseMessagingService
import com.example.gudgum_prod_flow.data.sync.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class UtpadApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        setupSyncWorker()
        setupNotificationChannel()
    }

    /**
     * FCM uses this channel for invoice-created pushes (HIGH importance →
     * heads-up display on Android 8+). Channel id matches the service constant
     * and the manifest meta-data default.
     */
    private fun setupNotificationChannel() {
        val channel = NotificationChannel(
            UtpadFirebaseMessagingService.CHANNEL_ID,
            "Invoices",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Push alerts for new invoices ready to dispatch."
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun setupSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SyncOfflineOperations",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
