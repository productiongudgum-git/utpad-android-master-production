package com.example.gudgum_prod_flow.di

import android.content.Context
import androidx.room.Room
import com.example.gudgum_prod_flow.data.local.GudGumDatabase
import com.example.gudgum_prod_flow.data.local.GudGumMigrations
import com.example.gudgum_prod_flow.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideGudGumDatabase(
        @ApplicationContext context: Context
    ): GudGumDatabase {
        return Room.databaseBuilder(
            context,
            GudGumDatabase::class.java,
            "gudgum_database"
        )
            // No destructive fallback. This database holds pending_operation_events
            // — submissions a worker made offline that SyncWorker has not replayed
            // yet — so wiping it on a version bump would silently destroy real work.
            // Every schema change needs a migration in GudGumMigrations instead.
            .addMigrations(*GudGumMigrations.ALL)
            .build()
    }

    @Provides @Singleton
    fun providePendingOperationEventDao(db: GudGumDatabase): PendingOperationEventDao =
        db.pendingOperationEventDao

    @Provides @Singleton
    fun provideCachedFlavorDao(db: GudGumDatabase): CachedFlavorDao =
        db.cachedFlavorDao

    @Provides @Singleton
    fun provideCachedRecipeLineDao(db: GudGumDatabase): CachedRecipeLineDao =
        db.cachedRecipeLineDao

    @Provides @Singleton
    fun provideCachedBatchDao(db: GudGumDatabase): CachedBatchDao =
        db.cachedBatchDao

    @Provides @Singleton
    fun provideCachedIngredientDao(db: GudGumDatabase): CachedIngredientDao =
        db.cachedIngredientDao
}
