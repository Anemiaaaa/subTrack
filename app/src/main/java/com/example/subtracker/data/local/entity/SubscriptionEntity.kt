package com.example.subtracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subscriptions",
    indices = [
        Index(value = ["familyCode"]),
        Index(value = ["ownerUid"]),
        Index(value = ["nextPaymentDate"])
    ]
)
data class SubscriptionEntity(
    @PrimaryKey val id: String,          // Firestore docId
    val familyCode: String,
    val ownerUid: String,
    val ownerUsername: String,
    val name: String,
    val price: Double,
    val periodicity: String,
    val iconResName: String,
    val nextPaymentDate: Long,
    val updatedAt: Long                 // локальная метка обновления
)
