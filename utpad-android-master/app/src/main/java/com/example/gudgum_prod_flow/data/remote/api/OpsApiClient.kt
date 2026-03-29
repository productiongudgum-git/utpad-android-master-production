package com.example.gudgum_prod_flow.data.remote.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Lightweight client for the Utpad Ops REST API (separate from Supabase).
 * Base URL: https://utpad-ops-api-seven.vercel.app
 */
object OpsApiClient {

    private const val TODAY_BATCH_URL =
        "https://utpad-ops-api-seven.vercel.app/api/v1/ops/batch-code/today"

    private val httpClient = OkHttpClient()

    /**
     * Fetches today's batch code from the Ops API.
     * Returns null if the call fails or the response cannot be parsed.
     */
    suspend fun fetchTodayBatchCode(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(TODAY_BATCH_URL).build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@runCatching null
                val json = JSONObject(body)
                // Accept common field names the API might use
                json.optString("batch_code").takeIf { it.isNotBlank() }
                    ?: json.optString("batchCode").takeIf { it.isNotBlank() }
                    ?: json.optString("code").takeIf { it.isNotBlank() }
            } else null
        }.getOrNull()
    }
}
