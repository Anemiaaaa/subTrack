package com.example.subtracker.domain.usecase

import com.example.subtracker.domain.model.Payment
import com.example.subtracker.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow

class ObservePaymentsUseCase(
    private val repo: PaymentRepository
) {
    operator fun invoke(
        familyCode: String,
        userDocId: String,
        role: String
    ): Flow<List<Payment>> = repo.observePayments(familyCode, userDocId, role)
}
