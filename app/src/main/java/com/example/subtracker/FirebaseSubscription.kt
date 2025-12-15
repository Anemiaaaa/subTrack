package com.example.subtracker

import com.google.firebase.firestore.DocumentId

data class FirebaseSubscription(
    @DocumentId
    val id: String = "",              // ID документа в Firestore
    val familyCode: String = "",
    val ownerUsername: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val periodicity: String = "месяц",
    val iconResName: String = "ic_default",
    val nextPaymentDate: Long = System.currentTimeMillis() // в миллисекундах
)
