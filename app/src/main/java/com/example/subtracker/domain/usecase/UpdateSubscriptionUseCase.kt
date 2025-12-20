package com.example.subtracker.domain.usecase

import com.example.subtracker.domain.model.Subscription
import com.example.subtracker.domain.repository.SubscriptionRepository

class UpdateSubscriptionUseCase(
    private val repo: SubscriptionRepository
) {
    suspend operator fun invoke(
        sub: Subscription,
        newName: String,
        newPrice: Double,
        newPeriodicity: String,
        newIconResName: String,
        newNextPaymentDate: Long
    ) = repo.updateOfflineFirst(sub, newName, newPrice, newPeriodicity, newIconResName, newNextPaymentDate)
}
