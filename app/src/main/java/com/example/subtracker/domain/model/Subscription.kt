package com.example.subtracker.domain.model

data class Subscription(
    val id: String,
    val familyCode: String,
    val ownerUid: String,
    val ownerUsername: String,
    val name: String,
    val price: Double,
    val periodicity: String,
    val iconResName: String,
    val nextPaymentDate: Long
)
