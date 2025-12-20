package com.example.subtracker.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.subtracker.data.local.AppDatabase
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PendingActionsSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = AppDatabase.get(context)
    private val pendingDao = db.pendingActions()
    private val firestore = FirebaseFirestore.getInstance()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            while (true) {
                val batch = pendingDao.getNext(limit = 25)
                if (batch.isEmpty()) break

                val doneIds = mutableListOf<String>()

                for (action in batch) {
                    val ok = runCatching {
                        when (action.type) {
                            "CREATE" -> handleCreate(action.subId, action.payloadJson)
                            "PAY" -> handlePay(action.payloadJson)
                            "UPDATE" -> handleUpdate(action.subId, action.payloadJson)
                            "DELETE" -> handleDelete(action.subId)
                            else -> true
                        }
                    }.getOrElse { false }

                    if (ok) doneIds.add(action.id)
                    else return@withContext Result.retry()
                }

                if (doneIds.isNotEmpty()) {
                    pendingDao.deleteByIds(doneIds)
                }
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun handleCreate(subId: String, payloadJson: String): Boolean {
        val json = JSONObject(payloadJson)

        val data = hashMapOf<String, Any>(
            "familyCode" to json.getString("familyCode"),
            "ownerUid" to json.getString("ownerUid"),
            "ownerUsername" to json.getString("ownerUsername"),
            "name" to json.getString("name"),
            "price" to json.getDouble("price"),
            "periodicity" to json.getString("periodicity"),
            "iconResName" to json.getString("iconResName"),
            "nextPaymentDate" to json.getLong("nextPaymentDate"),
            "createdAt" to json.getLong("createdAt")
        )

        // document id заранее задан (UUID), чтобы совпало с локальным
        val task = firestore.collection("subscriptions").document(subId).set(data)
        Tasks.await(task)
        return true
    }

    private fun handleDelete(subId: String): Boolean {
        val task = firestore.collection("subscriptions").document(subId).delete()
        Tasks.await(task)
        return true
    }

    private fun handleUpdate(subId: String, payloadJson: String): Boolean {
        val json = JSONObject(payloadJson)
        val updates = hashMapOf<String, Any>()

        if (json.has("name")) updates["name"] = json.getString("name")
        if (json.has("price")) updates["price"] = json.getDouble("price")
        if (json.has("periodicity")) updates["periodicity"] = json.getString("periodicity")
        if (json.has("iconResName")) updates["iconResName"] = json.getString("iconResName")
        if (json.has("nextPaymentDate")) updates["nextPaymentDate"] = json.getLong("nextPaymentDate")

        val task = firestore.collection("subscriptions").document(subId).update(updates)
        Tasks.await(task)
        return true
    }

    private fun handlePay(payloadJson: String): Boolean {
        val json = JSONObject(payloadJson)

        val subId = json.getString("subId")
        val nextPaymentDate = json.getLong("nextPaymentDate")

        Tasks.await(
            firestore.collection("subscriptions")
                .document(subId)
                .update("nextPaymentDate", nextPaymentDate)
        )

        val payment = hashMapOf(
            "familyCode" to json.getString("familyCode"),
            "subscriptionName" to json.getString("subscriptionName"),
            "amount" to json.getDouble("amount"),
            "ownerUid" to json.getString("ownerUid"),
            "ownerUsername" to json.getString("ownerUsername"),
            "iconResName" to json.getString("iconResName"),
            "paidAt" to json.getLong("paidAt")
        )

        Tasks.await(firestore.collection("payments").add(payment))
        return true
    }
}
