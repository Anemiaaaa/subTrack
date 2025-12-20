package com.example.subtracker.domain.usecase

import com.example.subtracker.domain.model.Subscription
import com.example.subtracker.domain.repository.SubscriptionRepository

class DeleteSubscriptionUseCase(
    private val repo: SubscriptionRepository
) {
    suspend operator fun invoke(sub: Subscription) = repo.deleteOfflineFirst(sub)
}
