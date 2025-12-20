package com.example.subtracker.data.mapper

import com.example.subtracker.data.local.entity.PaymentEntity
import com.example.subtracker.domain.model.Payment

fun PaymentEntity.toDomain(): Payment {
    return Payment(
        id = id,
        familyCode = familyCode,
        subscriptionName = subscriptionName,
        amount = amount,
        ownerUid = ownerUid,
        ownerUsername = ownerUsername,
        iconResName = iconResName,
        paidAt = paidAt
    )
}
