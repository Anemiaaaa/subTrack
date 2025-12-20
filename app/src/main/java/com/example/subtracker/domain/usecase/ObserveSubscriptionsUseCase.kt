package com.example.subtracker.domain.usecase

import com.example.subtracker.domain.model.Subscription
import com.example.subtracker.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow

class ObserveSubscriptionsUseCase(
    private val repo: SubscriptionRepository
) {
    operator fun invoke(
        familyCode: String,
        userDocId: String,
        role: String
    ): Flow<List<Subscription>> = repo.observeSubscriptions(familyCode, userDocId, role)
}
