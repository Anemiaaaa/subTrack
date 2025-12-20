package com.example.subtracker.domain.usecase

import com.example.subtracker.domain.model.Subscription
import com.example.subtracker.domain.repository.SubscriptionRepository

class PaySubscriptionUseCase(
    private val repo: SubscriptionRepository
) {
    suspend operator fun invoke(
        sessionUserDocId: String,
        sessionUsername: String,
        sub: Subscription
    ) = repo.payOfflineFirst(sessionUserDocId, sessionUsername, sub)
}
