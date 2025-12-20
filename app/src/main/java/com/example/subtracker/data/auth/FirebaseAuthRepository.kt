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
        if (current != null && current.uid.isNotBlank()) {
            // Если пользователь уже аутентифицирован (в т.ч. через Google), используем его UID
            return current.uid
        }
        // Только если нет пользователя, создаем анонимного
        val res = Tasks.await(auth.signInAnonymously())
        return res.user?.uid.orEmpty()
    }
    
    /**
     * Получает UID текущего аутентифицированного пользователя
     * Если пользователь не аутентифицирован, выбрасывает исключение
     */
    private suspend fun getCurrentUserUid(): String {
        val current = auth.currentUser
        if (current == null || current.uid.isBlank()) {
            error("Пользователь не аутентифицирован")
        }
        return current.uid
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
        android.util.Log.d("FirebaseAuthRepository", "joinFamilyOrCreateMember: username=$requestedUsername, familyCode=$familyCode")
        
        // Используем текущего аутентифицированного пользователя (Google или анонимного)
        val authUid = try {
            getCurrentUserUid()
        } catch (e: Exception) {
            android.util.Log.d("FirebaseAuthRepository", "User not authenticated, creating anonymous user")
            // Если пользователь не аутентифицирован, создаем анонимного
            ensureSignedInAnonymously()
        }
        
        android.util.Log.d("FirebaseAuthRepository", "Using authUid: $authUid")
        val code = familyCode.uppercase()

        val familyDoc = Tasks.await(db.collection("families").document(code).get())
        if (!familyDoc.exists()) error("Семья не найдена")

        // Проверяем, есть ли уже пользователь с таким UID
        val existingByUid = Tasks.await(
            db.collection("users").document(authUid).get()
        )
        
        if (existingByUid.exists()) {
            val existingFamilyCode = existingByUid.getString("familyCode")
            if (existingFamilyCode == code) {
                // Пользователь уже в этой семье - проверяем, нужно ли обновить имя
                val existingUsername = existingByUid.getString("username") ?: requestedUsername
                if (existingUsername == requestedUsername) {
                    // Имя не изменилось, возвращаем существующую сессию
                    return AuthSession(
                        userDocId = authUid,
                        username = existingUsername,
                        familyCode = code,
                        role = existingByUid.getString("role") ?: "member",
                        isGuest = false
                    )
                } else {
                    // Имя изменилось - обновляем имя пользователя
                    val finalName = resolveUsername(code, requestedUsername)
                    Tasks.await(
                        db.collection("users").document(authUid).update(
                            mapOf("username" to finalName)
                        )
                    )
                    return AuthSession(
                        userDocId = authUid,
                        username = finalName,
                        familyCode = code,
                        role = existingByUid.getString("role") ?: "member",
                        isGuest = false
                    )
                }
            } else {
                // Пользователь существует, но в другой семье - переводим его в новую семью
                val finalName = resolveUsername(code, requestedUsername)
                val currentUser = auth.currentUser
                val provider = when {
                    currentUser != null && currentUser.providerData.any { it.providerId == "google.com" } -> "google"
                    else -> "manual"
                }
                
                Tasks.await(
                    db.collection("users").document(authUid).update(
                        mapOf(
                            "username" to finalName,
                            "familyCode" to code,
                            "role" to "member", // При переходе в новую семью всегда member
                            "provider" to provider
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
        }

        // Пользователь не существует по UID - проверяем, есть ли пользователь с таким именем в этой семье
        val existing = Tasks.await(
            db.collection("users")
                .whereEqualTo("familyCode", code)
                .whereEqualTo("username", requestedUsername)
                .limit(1)
                .get()
        )

        if (!existing.isEmpty) {
            // Пользователь с таким именем уже существует в семье - входим под этим именем
            val doc = existing.documents.first()
            val existingUid = doc.getString("uid") ?: doc.id
            
            android.util.Log.d("FirebaseAuthRepository", "User with username $requestedUsername already exists in family, using existing user: $existingUid")
            
            // Обновляем текущего пользователя (если нужно) или используем существующего
            // Если это тот же UID - просто возвращаем сессию
            if (doc.id == authUid || existingUid == authUid) {
                return AuthSession(
                    userDocId = doc.id,
                    username = doc.getString("username") ?: requestedUsername,
                    familyCode = code,
                    role = doc.getString("role") ?: "member",
                    isGuest = false
                )
            }
            
            // Если это другой UID, но пользователь хочет войти под существующим именем
            // Обновляем запись существующего пользователя с новым UID (если нужно)
            // Или просто возвращаем сессию существующего пользователя
            val currentUser = auth.currentUser
            val provider = when {
                currentUser != null && currentUser.providerData.any { it.providerId == "google.com" } -> "google"
                else -> "manual"
            }
            
            // Обновляем UID существующего пользователя на текущий
            Tasks.await(
                db.collection("users").document(doc.id).update(
                    mapOf(
                        "uid" to authUid,
                        "provider" to provider
                    )
                )
            )
            
            return AuthSession(
                userDocId = doc.id,
                username = doc.getString("username") ?: requestedUsername,
                familyCode = code,
                role = doc.getString("role") ?: "member",
                isGuest = false
            )
        }

        // Пользователя с таким именем нет - создаем нового
        val finalName = resolveUsername(code, requestedUsername)
        
        // Определяем провайдера: если это Google пользователь, ставим "google", иначе "manual"
        val currentUser = auth.currentUser
        val provider = when {
            currentUser != null && currentUser.providerData.any { it.providerId == "google.com" } -> "google"
            else -> "manual"
        }

        Tasks.await(
            db.collection("users").document(authUid).set(
                mapOf(
                    "uid" to authUid,
                    "username" to finalName,
                    "familyCode" to code,
                    "role" to "member", // Всегда добавляем как обычного участника
                    "provider" to provider
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
