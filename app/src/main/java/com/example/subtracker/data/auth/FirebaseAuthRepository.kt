package com.example.subtracker.data.auth

import android.content.Context
import com.example.subtracker.data.local.AppDatabase
import com.example.subtracker.domain.auth.AuthRepository
import com.example.subtracker.domain.auth.AuthSession
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random

class FirebaseAuthRepository(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : AuthRepository {

    override suspend fun ensureSignedInAnonymously(): String {
        val current = auth.currentUser
        if (current != null && current.uid.isNotBlank()) return current.uid
        val res = Tasks.await(auth.signInAnonymously())
        return res.user?.uid.orEmpty()
    }

    override suspend fun loginAsGuestAdmin(): AuthSession {
        val uid = ensureSignedInAnonymously()
        val guestFamilyCode = generateUniqueGuestFamilyCode()
        val guestName = "Гость-${uid.takeLast(4)}"

        Tasks.await(
            db.collection("families").document(guestFamilyCode).set(
                mapOf(
                    "familyName" to "Гостевой режим",
                    "createdBy" to uid,
                    "guest" to true
                )
            )
        )

        Tasks.await(
            db.collection("users").document(uid).set(
                mapOf(
                    "uid" to uid,
                    "username" to guestName,
                    "familyCode" to guestFamilyCode,
                    "familyName" to "Гостевой режим",
                    "role" to "admin",
                    "provider" to "guest"
                )
            )
        )

        return AuthSession(
            userDocId = uid,
            username = guestName,
            familyCode = guestFamilyCode,
            role = "admin",
            isGuest = true
        )
    }

    override suspend fun joinFamilyOrCreateMember(
        requestedUsername: String,
        familyCode: String
    ): AuthSession {
        val authUid = ensureSignedInAnonymously()
        val code = familyCode.uppercase()

        val familyDoc = Tasks.await(db.collection("families").document(code).get())
        if (!familyDoc.exists()) error("Семья не найдена")

        val existing = Tasks.await(
            db.collection("users")
                .whereEqualTo("familyCode", code)
                .whereEqualTo("username", requestedUsername)
                .limit(1)
                .get()
        )

        if (!existing.isEmpty) {
            val doc = existing.documents.first()
            return AuthSession(
                userDocId = doc.id,
                username = doc.getString("username") ?: requestedUsername,
                familyCode = code,
                role = doc.getString("role") ?: "member",
                isGuest = false
            )
        }

        val finalName = resolveUsername(code, requestedUsername)

        Tasks.await(
            db.collection("users").document(authUid).set(
                mapOf(
                    "uid" to authUid,
                    "username" to finalName,
                    "familyCode" to code,
                    "role" to "member",
                    "provider" to "manual"
                )
            )
        )

        return AuthSession(
            userDocId = authUid,
            username = finalName,
            familyCode = code,
            role = "member",
            isGuest = false
        )
    }

    override suspend fun createFamilyAndAdmin(
        username: String,
        familyName: String
    ): Pair<AuthSession, String> {
        val uid = ensureSignedInAnonymously()
        val familyCode = generateUniqueFamilyCode()

        Tasks.await(
            db.collection("families").document(familyCode).set(
                mapOf("familyName" to familyName, "createdBy" to uid)
            )
        )

        Tasks.await(
            db.collection("users").document(uid).set(
                mapOf(
                    "uid" to uid,
                    "username" to username,
                    "familyCode" to familyCode,
                    "familyName" to familyName,
                    "role" to "admin",
                    "provider" to "manual"
                )
            )
        )

        return AuthSession(
            userDocId = uid,
            username = username,
            familyCode = familyCode,
            role = "admin",
            isGuest = false
        ) to familyCode
    }

    override suspend fun recoverFamilyCode(username: String): String {
        val query = Tasks.await(
            db.collection("users")
                .whereEqualTo("username", username.trim())
                .limit(1)
                .get()
        )

        if (query.isEmpty) {
            error("Пользователь с таким именем не найден")
        }

        val userDoc = query.documents.first()
        val familyCode = userDoc.getString("familyCode")

        if (familyCode.isNullOrBlank()) {
            error("Код семьи не найден")
        }

        return familyCode
    }

    override suspend fun logout() {
        // 1) sign out
        auth.signOut()

        // 2) очистка pending actions в Room
        AppDatabase.get(context).pendingActions().deleteAll()
    }

    // helpers
    private suspend fun resolveUsername(code: String, base: String): String {
        val snap = Tasks.await(
            db.collection("users").whereEqualTo("familyCode", code).get()
        )
        val taken = snap.documents.mapNotNull { it.getString("username") }.toSet()
        if (!taken.contains(base)) return base

        var name: String
        do {
            name = "$base-${Random.nextInt(10, 99)}"
        } while (taken.contains(name))
        return name
    }

    private suspend fun generateUniqueFamilyCode(): String {
        repeat(20) {
            val code = generateCode()
            if (!Tasks.await(db.collection("families").document(code).get()).exists()) return code
        }
        return generateCode()
    }

    private suspend fun generateUniqueGuestFamilyCode(): String {
        repeat(20) {
            val code = "G" + generateCode().drop(1)
            if (!Tasks.await(db.collection("families").document(code).get()).exists()) return code
        }
        return "G" + generateCode().drop(1)
    }

    private fun generateCode(): String =
        (1..6).map { ('A'..'Z').random() }.joinToString("")
}
