package com.example.subtracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_actions",
    indices = [
        Index(value = ["familyCode"]),
        Index(value = ["createdAt"])
    ]
)
data class PendingActionEntity(
    @PrimaryKey val id: String,           // UUID
    val familyCode: String,
    val type: String,                     // "PAY" | "UPDATE" | "DELETE"
    val subId: String,
    val payloadJson: String,              // JSON со всеми данными действия
    val createdAt: Long
)
