package com.example.subtracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["familyCode"]),
        Index(value = ["uid"])
    ]
)
data class UserEntity(
    @PrimaryKey val id: String,              // Firestore docId (обычно = uid)
    val uid: String,                          // Firebase Auth UID
    val username: String,
    val familyCode: String,
    val familyName: String = "",
    val role: String = "member",
    val avatarUrl: String = "",              // URL аватара
    val updatedAt: Long = System.currentTimeMillis()
)

