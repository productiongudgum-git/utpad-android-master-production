package com.example.gudgum_prod_flow.data.repository

import android.util.Log
import com.example.gudgum_prod_flow.data.remote.api.OperationsApiService
import com.example.gudgum_prod_flow.data.remote.dto.RegisterWorkerDeviceRequest
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges Firebase Cloud Messaging tokens to our OPS API's `worker_devices` table.
 *
 *  - `registerOnLogin()` — call right after a successful worker login. Fetches the
 *    current FCM token and POSTs it together with the worker_id so the Edge Function
 *    can target this device when a Dispatch-Staff worker is the recipient.
 *
 *  - `registerCurrentToken()` — called by the FirebaseMessagingService when FCM
 *    rotates the token (new install, restore from backup, clear data).
 *    Skips when there's no logged-in worker.
 *
 *  - `clearOnLogout()` — DELETE the current token so a signed-out device stops
 *    receiving pushes meant for the previous worker.
 */
@Singleton
class WorkerDeviceRepository @Inject constructor(
    private val opsApi: OperationsApiService,
) {

    suspend fun registerOnLogin() {
        val workerId = WorkerIdentityStore.workerId
        if (workerId.isBlank() || workerId == DEFAULT_WORKER_ID) {
            Log.d(TAG, "No real worker logged in; skipping device registration")
            return
        }
        val token = runCatching { FirebaseMessaging.getInstance().token.await() }
            .onFailure { Log.w(TAG, "Failed to read FCM token: ${it.message}") }
            .getOrNull() ?: return
        postToken(workerId, token)
    }

    suspend fun registerCurrentToken(token: String) {
        val workerId = WorkerIdentityStore.workerId
        if (workerId.isBlank() || workerId == DEFAULT_WORKER_ID) return
        postToken(workerId, token)
    }

    suspend fun clearOnLogout() {
        val token = runCatching { FirebaseMessaging.getInstance().token.await() }
            .getOrNull() ?: return
        runCatching {
            val res = opsApi.unregisterWorkerDevice(token)
            if (!res.isSuccessful) {
                Log.w(TAG, "Logout token unregister returned HTTP ${res.code()}")
            }
        }.onFailure { Log.w(TAG, "Logout token unregister failed: ${it.message}") }
    }

    private suspend fun postToken(workerId: String, token: String) {
        runCatching {
            val res = opsApi.registerWorkerDevice(
                RegisterWorkerDeviceRequest(workerId = workerId, fcmToken = token),
            )
            if (!res.isSuccessful) {
                Log.w(TAG, "Device registration returned HTTP ${res.code()}")
            } else {
                Log.i(TAG, "Device token registered for worker=$workerId")
            }
        }.onFailure { Log.w(TAG, "Device registration failed: ${it.message}") }
    }

    private companion object {
        const val TAG = "WorkerDeviceRepo"
        const val DEFAULT_WORKER_ID = "mobile-worker"
    }
}
