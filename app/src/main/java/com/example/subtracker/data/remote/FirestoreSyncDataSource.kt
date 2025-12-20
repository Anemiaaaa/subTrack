package com.example.subtracker.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class FirestoreSyncDataSource(
    private val db: FirebaseFirestore
) {

    fun listenSubscriptions(familyCode: String, onSnapshot: (ids: List<String>, docs: List<com.google.firebase.firestore.DocumentSnapshot>) -> Unit,
                            onError: (String) -> Unit): ListenerRegistration {
        return db.collection("subscriptions")
            .whereEqualTo("familyCode", familyCode)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    onError(e.message ?: "Ошибка синхронизации подписок")
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                val docs = snap.documents
                onSnapshot(docs.map { it.id }, docs)
            }
    }

    fun listenPayments(familyCode: String, onSnapshot: (ids: List<String>, docs: List<com.google.firebase.firestore.DocumentSnapshot>) -> Unit,
                       onError: (String) -> Unit): ListenerRegistration {
        return db.collection("payments")
            .whereEqualTo("familyCode", familyCode)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    onError(e.message ?: "Ошибка синхронизации оплат")
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                val docs = snap.documents
                onSnapshot(docs.map { it.id }, docs)
            }
    }
}
