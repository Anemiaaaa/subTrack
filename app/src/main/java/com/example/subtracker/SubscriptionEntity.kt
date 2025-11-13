package com.example.subtracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val familyCode: String,
    val name: String,
    val price: Double,
    val periodicity: String,       // "день", "неделя", "месяц", "квартал", "год"
    val iconResName: String,
    val ownerUsername: String,
    val nextPaymentDate: Long      // <-- новое поле
)

