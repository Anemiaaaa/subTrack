package com.example.subtracker.data.mapper

import com.example.subtracker.FirebaseSubscription
import com.example.subtracker.data.local.entity.SubscriptionEntity
import com.google.firebase.firestore.DocumentSnapshot

fun DocumentSnapshot.toSubscriptionEntity(now: Long): SubscriptionEntity? {
    val id = id
    val familyCode = getString("familyCode") ?: return null
    val ownerUid = getString("ownerUid") ?: ""
    val ownerUsername = getString("ownerUsername") ?: ""
    val name = getString("name") ?: return null
    val price = getDouble("price") ?: 0.0
    val periodicity = getString("periodicity") ?: "месяц"
    val iconResName = getString("iconResName") ?: "ic_default"
    val nextPaymentDate = getLong("nextPaymentDate") ?: System.currentTimeMillis()

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
        updatedAt = now
    )
}

fun SubscriptionEntity.toFirebaseModel(): FirebaseSubscription {
    return FirebaseSubscription(
        id = id,
        familyCode = familyCode,
        ownerUsername = ownerUsername,
        name = name,
        price = price,
        periodicity = periodicity,
        iconResName = iconResName,
        nextPaymentDate = nextPaymentDate
    )
}
