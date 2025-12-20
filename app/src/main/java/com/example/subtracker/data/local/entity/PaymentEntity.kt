package com.example.subtracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payments",
    indices = [
        Index(value = ["familyCode"]),
        Index(value = ["paidAt"]),
        Index(value = ["ownerUid"])
    ]
)
data class PaymentEntity(
    @PrimaryKey val id: String,          // Firestore docId
    val familyCode: String,
    val subscriptionName: String,
    val amount: Double,
    val ownerUid: String,
    val ownerUsername: String,
    val iconResName: String,
    val paidAt: Long,
    val updatedAt: Long
)
