package com.example.subtracker

data class Payment(
    val id: String = "",
    val familyCode: String = "",
    val subscriptionName: String = "",
    val amount: Double = 0.0,
    val ownerUid: String? = null,
    val ownerUsername: String = "",
    val iconResName: String = "",
    val paidAt: Long = 0L
)
