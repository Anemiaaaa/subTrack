package com.example.subtracker.domain.usecase

import com.example.subtracker.domain.repository.SubscriptionRepository

class CreateSubscriptionUseCase(
    private val repo: SubscriptionRepository
) {
    suspend operator fun invoke(
        sessionUserDocId: String,
        sessionUsername: String,
        familyCode: String,
        name: String,
        price: Double,
        periodicity: String,
        iconResName: String,
        nextPaymentDate: Long
    ): String {
        return repo.createOfflineFirst(
            sessionUserDocId = sessionUserDocId,
            sessionUsername = sessionUsername,
            familyCode = familyCode,
            name = name,
            price = price,
            periodicity = periodicity,
            iconResName = iconResName,
            nextPaymentDate = nextPaymentDate
        )
    }
}
