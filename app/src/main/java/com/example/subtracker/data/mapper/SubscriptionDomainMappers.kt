package com.example.subtracker.data.mapper

import com.example.subtracker.data.local.entity.SubscriptionEntity
import com.example.subtracker.domain.model.Subscription

fun SubscriptionEntity.toDomain(): Subscription {
    return Subscription(
        id = id,
        familyCode = familyCode,
        ownerUid = ownerUid,
        ownerUsername = ownerUsername,
        name = name,
        price = price,
        periodicity = periodicity,
        iconResName = iconResName,
        nextPaymentDate = nextPaymentDate
    )
}

fun Subscription.toEntity(updatedAt: Long): SubscriptionEntity {
    return SubscriptionEntity(
        id = id,
        familyCode = familyCode,
        ownerUid = ownerUid,
        ownerUsername = ownerUsername,
        name = name,
        price = price,
        periodicity = periodicity,
        iconResName = iconResName,
        nextPaymentDate = nextPaymentDate,
        updatedAt = updatedAt
    )
}
