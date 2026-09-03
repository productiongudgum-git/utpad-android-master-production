package com.example.gudgum_prod_flow.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.gudgum_prod_flow.data.local.dao.*
import com.example.gudgum_prod_flow.data.local.entity.*

@Database(
    entities = [
        PendingOperationEventEntity::class,
        CachedFlavorEntity::class,
        CachedRecipeLineEntity::class,
        CachedBatchEntity::class,
        CachedIngredientEntity::class,
    ],
    // v4 adds cached_flavors.unitsPerBox / .parentFlavorId for packing variants.
    // Bumping this REQUIRES a matching entry in GudGumMigrations — this database
    // holds the offline submission queue, so a destructive fallback would throw
    // away work that was never sent to the server.
    version = 4,
    exportSchema = true,
)
abstract class GudGumDatabase : RoomDatabase() {
    abstract val pendingOperationEventDao: PendingOperationEventDao
    abstract val cachedFlavorDao: CachedFlavorDao
    abstract val cachedRecipeLineDao: CachedRecipeLineDao
    abstract val cachedBatchDao: CachedBatchDao
    abstract val cachedIngredientDao: CachedIngredientDao
}
