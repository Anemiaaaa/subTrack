package com.example.subtracker.domain.repository

import com.example.subtracker.domain.model.Payment
import kotlinx.coroutines.flow.Flow

interface PaymentRepository {
    fun observePayments(
        familyCode: String,
        userDocId: String,
        role: String
    ): Flow<List<Payment>>
}
