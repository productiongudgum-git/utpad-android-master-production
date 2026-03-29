package com.example.gudgum_prod_flow.data.remote

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Supabase Realtime WebSocket connections.
 * Listens for Postgres changes on ALL manufacturing tables
 * and emits events so ViewModels/Repositories can refresh their caches instantly.
 *
 * This ensures Android app ↔ Web dashboard stay in sync in real time.
 */
@Singleton
class SupabaseRealtimeManager @Inject constructor(
    private val supabaseClient: SupabaseClient,
) {
    companion object {
        private const val TAG = "RealtimeManager"
        private val WATCHED_TABLES = listOf(
            "gg_ingredients",
            "gg_vendors",
            "gg_flavors",
            "gg_recipes",
            "recipe_lines",
            "gg_inwarding",
            "production_batches",
            "packing_sessions",
            "dispatch_events",
            "returns_events",
            "inventory_raw_materials",
            "inventory_finished_goods",
            "gg_customers",
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Emitted table names whenever a change is detected */
    private val _tableChanged = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val tableChanged: SharedFlow<String> = _tableChanged.asSharedFlow()

    private var isConnected = false

    /**
     * Call once to establish the WebSocket connection and subscribe to all
     * manufacturing-related tables.
     */
    fun connect() {
        if (isConnected) return
        isConnected = true

        scope.launch {
            try {
                val channel = supabaseClient.channel("manufacturing-changes")

                // Create a flow for each watched table
                val flows = WATCHED_TABLES.map { tableName ->
                    tableName to channel.postgresChangeFlow<PostgresAction>("public") {
                        table = tableName
                    }
                }

                // Subscribe to the channel (opens WebSocket)
                channel.subscribe()
                Log.d(TAG, "Realtime channel subscribed — watching ${WATCHED_TABLES.size} tables")

                // Collect each flow and emit the table name
                flows.forEach { (tableName, flow) ->
                    launch {
                        flow.collect { action ->
                            Log.d(TAG, "$tableName changed: ${action::class.simpleName}")
                            _tableChanged.emit(tableName)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Realtime connection error", e)
                isConnected = false
            }
        }
    }
}
