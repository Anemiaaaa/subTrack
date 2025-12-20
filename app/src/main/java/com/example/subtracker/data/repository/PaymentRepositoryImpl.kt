package com.example.subtracker.data.repository

import com.example.subtracker.data.local.AppDatabase
import com.example.subtracker.data.mapper.toDomain
import com.example.subtracker.domain.model.Payment
import com.example.subtracker.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PaymentRepositoryImpl(
    private val db: AppDatabase
) : PaymentRepository {

    override fun observePayments(
        familyCode: String,
        userDocId: String,
        role: String
    ): Flow<List<Payment>> {
        // PaymentDao сейчас отдаёт family payments.
        // Для member фильтруем по ownerUid на уровне flow (без новой DAO-таблицы).
        return db.payments()
            .observeFamilyPayments(familyCode)
            .map { list ->
                val filtered = if (role == "admin") list else list.filter { it.ownerUid == userDocId }
                filtered.map { it.toDomain() }
            }
    }
}
