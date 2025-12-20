package com.example.subtracker.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.subtracker.domain.repository.SubscriptionRepository
import com.example.subtracker.domain.usecase.DeleteSubscriptionUseCase
import com.example.subtracker.domain.usecase.ObserveSubscriptionsUseCase
import com.example.subtracker.domain.usecase.PaySubscriptionUseCase
import com.example.subtracker.domain.usecase.UpdateSubscriptionUseCase
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Фабрика для создания MainFrameViewModel
 */
class MainFrameViewModelFactory(
    private val observeSubscriptionsUseCase: ObserveSubscriptionsUseCase,
    private val paySubscriptionUseCase: PaySubscriptionUseCase,
    private val deleteSubscriptionUseCase: DeleteSubscriptionUseCase,
    private val updateSubscriptionUseCase: UpdateSubscriptionUseCase,
    private val subscriptionRepository: SubscriptionRepository,
    private val firestore: FirebaseFirestore
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainFrameViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        return MainFrameViewModel(
            observeSubscriptionsUseCase = observeSubscriptionsUseCase,
            paySubscriptionUseCase = paySubscriptionUseCase,
            deleteSubscriptionUseCase = deleteSubscriptionUseCase,
            updateSubscriptionUseCase = updateSubscriptionUseCase,
            subscriptionRepository = subscriptionRepository,
            firestore = firestore
        ) as T
    }
}
