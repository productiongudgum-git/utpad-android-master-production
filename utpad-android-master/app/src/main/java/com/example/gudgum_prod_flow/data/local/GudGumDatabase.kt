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
    version = 3,
    exportSchema = true,
)
abstract class GudGumDatabase : RoomDatabase() {
    abstract val pendingOperationEventDao: PendingOperationEventDao
    abstract val cachedFlavorDao: CachedFlavorDao
    abstract val cachedRecipeLineDao: CachedRecipeLineDao
    abstract val cachedBatchDao: CachedBatchDao
    abstract val cachedIngredientDao: CachedIngredientDao
}
