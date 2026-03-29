package com.example.gudgum_prod_flow.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.gudgum_prod_flow.data.local.dao.OfflineAuthDao
import com.example.gudgum_prod_flow.data.local.dao.PermissionDao
import com.example.gudgum_prod_flow.data.local.dao.UserDao
import com.example.gudgum_prod_flow.data.local.entity.CachedPermission
import com.example.gudgum_prod_flow.data.local.entity.OfflineAuthEvent
import com.example.gudgum_prod_flow.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        OfflineAuthEvent::class,
        CachedPermission::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun offlineAuthDao(): OfflineAuthDao
    abstract fun permissionDao(): PermissionDao
}
