package com.example.subtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.subtracker.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE familyCode = :familyCode ORDER BY username ASC")
    fun observeFamilyUsers(familyCode: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE familyCode = :familyCode ORDER BY username ASC")
    suspend fun getFamilyUsers(familyCode: String): List<UserEntity>

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE uid = :uid")
    suspend fun getUserByUid(uid: String): UserEntity?

    @Query("SELECT avatarUrl FROM users WHERE uid = :uid")
    suspend fun getAvatarUrl(uid: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(users: List<UserEntity>)

    @Query("UPDATE users SET avatarUrl = :avatarUrl, updatedAt = :updatedAt WHERE uid = :uid")
    suspend fun updateAvatarUrl(uid: String, avatarUrl: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM users WHERE familyCode = :familyCode AND id NOT IN (:keepIds)")
    suspend fun deleteMissingForFamily(familyCode: String, keepIds: List<String>)
}

