package com.example.subtracker.domain.model

data class Payment(
    val id: String,
    val familyCode: String,
    val subscriptionName: String,
    val amount: Double,
    val ownerUid: String,
    val ownerUsername: String,
    val iconResName: String,
    val paidAt: Long
)
