package com.example.subtracker.data.mapper

import com.example.subtracker.data.local.entity.PaymentEntity
import com.google.firebase.firestore.DocumentSnapshot

fun DocumentSnapshot.toPaymentEntity(now: Long): PaymentEntity? {
    val id = id
    val familyCode = getString("familyCode") ?: return null
    val subscriptionName = getString("subscriptionName") ?: return null
    val amount = getDouble("amount") ?: 0.0
    val ownerUid = getString("ownerUid") ?: ""
    val ownerUsername = getString("ownerUsername") ?: ""
    val iconResName = getString("iconResName") ?: "ic_default"
    val paidAt = getLong("paidAt") ?: System.currentTimeMillis()

    return PaymentEntity(
        id = id,
        familyCode = familyCode,
        subscriptionName = subscriptionName,
        amount = amount,
        ownerUid = ownerUid,
        ownerUsername = ownerUsername,
        iconResName = iconResName,
        paidAt = paidAt,
        updatedAt = now
    )
}
