package com.example.gudgum_prod_flow.data.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.gudgum_prod_flow.MainActivity
import com.example.gudgum_prod_flow.R
import com.example.gudgum_prod_flow.data.repository.WorkerDeviceRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives FCM pushes from the `notify-invoice-created` Supabase Edge Function.
 *
 * Payload shape (data block, both `notification` and `data` are sent):
 *   data = { type: "invoice_created", invoice_id, invoice_number, route: "dispatch" }
 *
 * On tap → launches MainActivity with intent extras the activity reads to
 * navigate to AppRoute.Dispatch after the auth state is restored.
 *
 * Also handles token refresh by re-registering with the OPS API so a device
 * that ever changes its FCM token keeps receiving pushes.
 */
@AndroidEntryPoint
class UtpadFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var deviceRepo: WorkerDeviceRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val data         = message.data
        val type         = data["type"] ?: "unknown"
        val invoiceId    = data["invoice_id"]
        val invoiceNum   = data["invoice_number"] ?: "Invoice"
        val notification = message.notification

        Log.i(TAG, "FCM received: type=$type invoice=$invoiceNum")

        if (type != INVOICE_CREATED_TYPE) {
            Log.w(TAG, "Unknown push type; ignoring")
            return
        }

        val title = notification?.title ?: "Invoice $invoiceNum"
        val body  = notification?.body  ?: "New invoice has arrived"

        showInvoiceNotification(invoiceId = invoiceId, invoiceNumber = invoiceNum,
            title = title, body = body)
    }

    /**
     * FCM may rotate the token (new install, restore from backup, clear data).
     * Re-register so the server keeps the right one against this worker.
     * The repo is a no-op if no worker is logged in yet.
     */
    override fun onNewToken(token: String) {
        Log.i(TAG, "FCM token rotated; re-registering")
        scope.launch {
            runCatching { deviceRepo.registerCurrentToken(token) }
                .onFailure { Log.w(TAG, "Token re-register failed: ${it.message}") }
        }
    }

    private fun showInvoiceNotification(
        invoiceId: String?,
        invoiceNumber: String,
        title: String,
        body: String,
    ) {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            // singleTop on MainActivity + CLEAR_TOP so we don't stack multiple copies
            // of the same screen when the user taps several pushes quickly.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTE, ROUTE_DISPATCH)
            putExtra(EXTRA_INVOICE_ID, invoiceId)
            putExtra(EXTRA_INVOICE_NUMBER, invoiceNumber)
        }
        val pending = PendingIntent.getActivity(
            this, invoiceNumber.hashCode(), launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.gudgum_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Stable per-invoice id so re-sending the same push updates instead of duplicating.
        mgr.notify(invoiceNumber.hashCode(), notif)
    }

    companion object {
        private const val TAG = "UtpadFCM"
        const val CHANNEL_ID            = "invoices"
        const val INVOICE_CREATED_TYPE  = "invoice_created"
        const val ROUTE_DISPATCH        = "dispatch"
        const val EXTRA_ROUTE           = "extra_route"
        const val EXTRA_INVOICE_ID      = "extra_invoice_id"
        const val EXTRA_INVOICE_NUMBER  = "extra_invoice_number"
    }
}
