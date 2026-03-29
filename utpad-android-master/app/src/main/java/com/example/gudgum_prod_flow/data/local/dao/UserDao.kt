package com.example.gudgum_prod_flow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.gudgum_prod_flow.data.local.entity.UserEntity

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    suspend fun getUserByPhone(phone: String): UserEntity?

    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("UPDATE users SET status = :status WHERE userId = :userId")
    suspend fun updateStatus(userId: String, status: String)

    @Query("SELECT lastSyncTimestamp FROM users WHERE userId = :userId")
    suspend fun getLastSyncTimestamp(userId: String): Long?

    @Query("UPDATE users SET lastSyncTimestamp = :timestamp WHERE userId = :userId")
    suspend fun updateLastSyncTimestamp(userId: String, timestamp: Long)

    @Query("DELETE FROM users WHERE userId = :userId")
    suspend fun deleteUser(userId: String)
}
