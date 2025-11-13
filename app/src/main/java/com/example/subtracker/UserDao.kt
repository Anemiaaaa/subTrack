package com.example.subtracker

import androidx.room.*

@Dao
interface UserDao {
    @Insert
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE familyCode = :code")
    suspend fun getUsersByFamilyCode(code: String): List<UserEntity>

    @Query("SELECT * FROM users WHERE familyName = :familyName")
    suspend fun getFamilyByName(familyName: String): List<UserEntity>

    @Query("SELECT COUNT(*) > 0 FROM users WHERE familyCode = :code")
    suspend fun doesFamilyExist(code: String): Boolean

    @Query("SELECT * FROM users WHERE familyCode = :code AND isAdmin = 1 LIMIT 1")
    fun getFamilyHead(code: String): UserEntity?

    // ---------------- Новый метод ----------------
    @Delete
    suspend fun deleteUser(user: UserEntity)
}
