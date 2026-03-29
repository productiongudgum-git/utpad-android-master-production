package com.example.gudgum_prod_flow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.gudgum_prod_flow.data.local.entity.CachedPermission

@Dao
interface PermissionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPermissions(permissions: List<CachedPermission>)

    @Query("SELECT * FROM cached_permissions WHERE userId = :userId")
    suspend fun getPermissions(userId: String): List<CachedPermission>

    @Query("DELETE FROM cached_permissions WHERE userId = :userId")
    suspend fun deletePermissions(userId: String)

    @Query("DELETE FROM cached_permissions WHERE cachedAt < :cutoffTimestamp")
    suspend fun deleteExpiredPermissions(cutoffTimestamp: Long)
}
