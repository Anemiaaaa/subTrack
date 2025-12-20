package com.example.subtracker.data.repository

import android.content.Context
import com.example.subtracker.app.sync.SyncScheduler
import com.example.subtracker.data.local.AppDatabase
import com.example.subtracker.data.local.entity.PendingActionEntity
import com.example.subtracker.data.mapper.toDomain
import com.example.subtracker.data.mapper.toEntity
import com.example.subtracker.data.mapper.toPaymentEntity
import com.example.subtracker.data.mapper.toSubscriptionEntity
import com.example.subtracker.data.remote.FirestoreSyncDataSource
import com.example.subtracker.domain.model.Subscription
import com.example.subtracker.domain.repository.SubscriptionRepository
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max

class SubscriptionRepositoryImpl(
    private val context: Context,
    private val db: AppDatabase,
    private val remote: FirestoreSyncDataSource
) : SubscriptionRepository {

    private val scope = CoroutineScope(Dispatchers.IO)

    private var subReg: ListenerRegistration? = null
    private var payReg: ListenerRegistration? = null

    override fun observeSubscriptions(
        familyCode: String,
        userDocId: String,
        role: String
    ): Flow<List<Subscription>> {
        return if (role == "admin") {
            db.subscriptions().observeFamilySubscriptions(familyCode)
                .map { list -> list.map { it.toDomain() } }
        } else {
            db.subscriptions().observeUserSubscriptions(familyCode, userDocId)
                .map { list -> list.map { it.toDomain() } }
        }
    }

    override fun startSync(familyCode: String) {
        if (subReg != null || payReg != null) return

        subReg = remote.listenSubscriptions(
            familyCode = familyCode,
            onSnapshot = { ids, docs ->
                val now = System.currentTimeMillis()
                val entities = docs.mapNotNull { it.toSubscriptionEntity(now) }
                scope.launch {
                    db.subscriptions().upsertAll(entities)
                    if (ids.isNotEmpty()) db.subscriptions().deleteMissingForFamily(familyCode, ids)
                }
            },
            onError = { }
        )

        payReg = remote.listenPayments(
            familyCode = familyCode,
            onSnapshot = { ids, docs ->
                val now = System.currentTimeMillis()
                val entities = docs.mapNotNull { it.toPaymentEntity(now) }
                scope.launch {
                    db.payments().upsertAll(entities)
                    if (ids.isNotEmpty()) db.payments().deleteMissingForFamily(familyCode, ids)
                }
            },
            onError = { }
        )
    }

    override fun stopSync() {
        subReg?.remove()
        payReg?.remove()
        subReg = null
        payReg = null
    }

    // ================= OFFLINE-FIRST CREATE =================

    override suspend fun createOfflineFirst(
        sessionUserDocId: String,
        sessionUsername: String,
        familyCode: String,
        name: String,
        price: Double,
        periodicity: String,
        iconResName: String,
        nextPaymentDate: Long
    ): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()

        val sub = Subscription(
            id = id,
            familyCode = familyCode,
            ownerUid = sessionUserDocId,
            ownerUsername = sessionUsername,
            name = name,
            price = price,
            periodicity = periodicity,
            iconResName = iconResName,
            nextPaymentDate = nextPaymentDate
        )

        // 1) локально
        db.subscriptions().upsertAll(listOf(sub.toEntity(updatedAt = now)))

        // 2) pending action CREATE (с полным payload)
        val payload = JSONObject().apply {
            put("subId", id)
            put("familyCode", familyCode)
            put("ownerUid", sessionUserDocId)
            put("ownerUsername", sessionUsername)
            put("name", name)
            put("price", price)
            put("periodicity", periodicity)
            put("iconResName", iconResName)
            put("nextPaymentDate", nextPaymentDate)
            put("createdAt", now)
        }.toString()

        enqueuePending(
            familyCode = familyCode,
            type = "CREATE",
            subId = id,
            payloadJson = payload
        )

        return id
    }

    // ================= OFFLINE-FIRST MUTATIONS =================

    override suspend fun payOfflineFirst(sessionUserDocId: String, sessionUsername: String, sub: Subscription) {
        val next = calcNextPayment(sub)
        val now = System.currentTimeMillis()

        val updated = sub.copy(nextPaymentDate = next).toEntity(updatedAt = now)
        db.subscriptions().upsertAll(listOf(updated))

        val payload = JSONObject().apply {
            put("subId", sub.id)
            put("familyCode", sub.familyCode)
            put("subscriptionName", sub.name)
            put("amount", sub.price)
            put("ownerUid", sessionUserDocId)
            put("ownerUsername", sub.ownerUsername.ifBlank { sessionUsername })
            put("iconResName", sub.iconResName)
            put("paidAt", now)
            put("nextPaymentDate", next)
        }.toString()

        enqueuePending(
            familyCode = sub.familyCode,
            type = "PAY",
            subId = sub.id,
            payloadJson = payload
        )
    }

    override suspend fun deleteOfflineFirst(sub: Subscription) {
        db.subscriptions().deleteByIds(listOf(sub.id))

        enqueuePending(
            familyCode = sub.familyCode,
            type = "DELETE",
            subId = sub.id,
            payloadJson = "{}"
        )
    }

    override suspend fun updateOfflineFirst(
        sub: Subscription,
        newName: String,
        newPrice: Double,
        newPeriodicity: String,
        newIconResName: String,
        newNextPaymentDate: Long
    ) {
        val now = System.currentTimeMillis()

        val updated = sub.copy(
            name = newName,
            price = newPrice,
            periodicity = newPeriodicity,
            iconResName = newIconResName,
            nextPaymentDate = newNextPaymentDate
        ).toEntity(updatedAt = now)

        db.subscriptions().upsertAll(listOf(updated))

        val payload = JSONObject().apply {
            put("name", newName)
            put("price", newPrice)
            put("periodicity", newPeriodicity)
            put("iconResName", newIconResName)
            put("nextPaymentDate", newNextPaymentDate)
        }.toString()

        enqueuePending(
            familyCode = sub.familyCode,
            type = "UPDATE",
            subId = sub.id,
            payloadJson = payload
        )
    }

    private suspend fun enqueuePending(familyCode: String, type: String, subId: String, payloadJson: String) {
        val action = PendingActionEntity(
            id = UUID.randomUUID().toString(),
            familyCode = familyCode,
            type = type,
            subId = subId,
            payloadJson = payloadJson,
            createdAt = System.currentTimeMillis()
        )
        db.pendingActions().insert(action)
        SyncScheduler.enqueue(context)
    }

    private fun calcNextPayment(sub: Subscription): Long {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = max(System.currentTimeMillis(), sub.nextPaymentDate)
        }
        when (sub.periodicity.lowercase(java.util.Locale.getDefault())) {
            "день", "каждый день" -> cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            "неделя", "каждую неделю" -> cal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
            "месяц", "каждый месяц" -> cal.add(java.util.Calendar.MONTH, 1)
            "квартал", "каждый квартал" -> cal.add(java.util.Calendar.MONTH, 3)
            "год", "каждый год" -> cal.add(java.util.Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }
}
