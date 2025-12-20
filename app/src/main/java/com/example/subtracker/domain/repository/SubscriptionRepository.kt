package com.example.subtracker.domain.repository

import com.example.subtracker.domain.model.Subscription
import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository {

    fun observeSubscriptions(
        familyCode: String,
        userDocId: String,
        role: String
    ): Flow<List<Subscription>>

    fun startSync(familyCode: String)
    fun stopSync()

    suspend fun createOfflineFirst(
        sessionUserDocId: String,
        sessionUsername: String,
        familyCode: String,
        name: String,
        price: Double,
        periodicity: String,
        iconResName: String,
        nextPaymentDate: Long
    ): String // returns created subscriptionId (UUID)

    suspend fun payOfflineFirst(
        sessionUserDocId: String,
        sessionUsername: String,
        sub: Subscription
    )

    suspend fun deleteOfflineFirst(sub: Subscription)

    suspend fun updateOfflineFirst(
        sub: Subscription,
        newName: String,
        newPrice: Double,
        newPeriodicity: String,
        newIconResName: String
    )
}
