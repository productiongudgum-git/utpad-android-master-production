package com.example.gudgum_prod_flow.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations for [GudGumDatabase].
 *
 * This database is not a disposable cache. Alongside the cached catalogue it
 * holds `pending_operation_events` — the queue of packing, inwarding, dispatch
 * and returns submissions a worker made while offline, which [SyncWorker]
 * replays once connectivity returns. Losing that table loses real work that
 * was never sent to the server, silently, with no way to recover it.
 *
 * The database was previously built with `fallbackToDestructiveMigration()`
 * (marked "Dev only" in DatabaseModule). Under that setting any schema version
 * bump wipes every table, so shipping a schema change would have quietly
 * discarded the offline queue of every worker who updated before syncing.
 * Every version bump from here on needs a real migration in this file.
 */
object GudGumMigrations {

    /**
     * v3 → v4: packing variants.
     *
     * Adds the two flavour columns the packing-variant feature reads:
     *
     *   units_per_box     gums in one box of this flavour. 15 is the count
     *                     every flavour used before variants existed, and
     *                     matches the server-side default in migration 0008,
     *                     so existing cached rows stay correct.
     *   parent_flavor_id  set when the row is a packing variant of another
     *                     flavour; NULL for a base flavour.
     *
     * Only `cached_flavors` is touched. The values are placeholders until the
     * next catalogue refresh overwrites them from Supabase — `refreshFlavors()`
     * clears and reinserts the table — so the defaults only have to be right
     * for the window between upgrade and first sync. They are: a worker who
     * upgrades offline keeps packing at 15 to a box, exactly as before.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE cached_flavors ADD COLUMN unitsPerBox INTEGER NOT NULL DEFAULT 15"
            )
            db.execSQL(
                "ALTER TABLE cached_flavors ADD COLUMN parentFlavorId TEXT DEFAULT NULL"
            )
        }
    }

    /** Every migration, in order, for the Room builder. */
    val ALL = arrayOf(MIGRATION_3_4)
}
