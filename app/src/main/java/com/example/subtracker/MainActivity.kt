package com.example.subtracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Если прошлый запуск был "гость" — на новом запуске чистим и выкидываем навсегда
        if (GuestSession.isActive(this)) {
            GuestSession.clear(this)
            SessionManager.clear(this)
            FirebaseAuth.getInstance().signOut()
        }

        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val usernameInput = findViewById<EditText>(R.id.editTextLogin)
        val familyCodeInput = findViewById<EditText>(R.id.editTextPassword)
        val btnLogin = findViewById<Button>(R.id.buttonLogin)
        val btnCreateFamily = findViewById<Button>(R.id.buttonCreateFamily)
        val guestLink = findViewById<TextView>(R.id.textGuestLogin)

        // Автовход только для НЕ-гостя
        val savedUid = SessionManager.userDocId(this)
        val savedFamily = SessionManager.familyCode(this)
        val savedUsername = SessionManager.username(this)
        val savedRole = SessionManager.role(this)

        if (savedUid.isNotEmpty() && savedFamily.isNotEmpty() && savedUsername.isNotEmpty()) {
            startActivity(Intent(this, MainFrameActivity::class.java).apply {
                putExtra("uid", savedUid)
                putExtra("username", savedUsername)
                putExtra("familyCode", savedFamily)
                putExtra("role", savedRole.ifEmpty { "member" })
            })
            finish()
            return
        }

        btnCreateFamily.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Войти в семью по имени + коду
        btnLogin.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val familyCode = familyCodeInput.text.toString().trim().uppercase()

            if (username.isEmpty() || familyCode.isEmpty()) {
                Toast.makeText(this, "Введите имя и код семьи", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            ensureSignedIn { authUid ->
                joinFamily(authUid, username, familyCode)
            }
        }

        // Войти как гость
        guestLink.setOnClickListener {
            ensureSignedIn { uid ->
                loginAsGuestAdmin(uid)
            }
        }
    }

    private fun ensureSignedIn(onReady: (uid: String) -> Unit) {
        val current = auth.currentUser
        if (current != null) {
            onReady(current.uid)
            return
        }

        auth.signInAnonymously()
            .addOnSuccessListener { res -> onReady(res.user?.uid.orEmpty()) }
            .addOnFailureListener {
                Toast.makeText(this, "Ошибка аутентификации", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * ✅ Гость: временная семья + admin.
     */
    private fun loginAsGuestAdmin(uid: String) {
        if (uid.isEmpty()) {
            Toast.makeText(this, "Не удалось создать гостя", Toast.LENGTH_SHORT).show()
            return
        }

        val guestFamilyCode = generateGuestFamilyCode()
        val guestName = "Гость-${uid.takeLast(4)}"

        val familyData = hashMapOf(
            "familyName" to "Гостевой режим",
            "createdBy" to uid,
            "guest" to true
        )

        db.collection("families").document(guestFamilyCode).set(familyData)
            .addOnSuccessListener {
                val userData = hashMapOf(
                    "uid" to uid,
                    "username" to guestName,
                    "familyCode" to guestFamilyCode,
                    "familyName" to "Гостевой режим",
                    "role" to "admin",
                    "provider" to "guest"
                )

                db.collection("users").document(uid).set(userData)
                    .addOnSuccessListener {
                        GuestSession.setActive(this, true)
                        SessionManager.save(this, uid, guestName, guestFamilyCode, "admin")

                        SubscriptionReminderManager.scheduleDailyReminders(this)

                        startActivity(Intent(this, MainFrameActivity::class.java).apply {
                            putExtra("uid", uid)
                            putExtra("username", guestName)
                            putExtra("familyCode", guestFamilyCode)
                            putExtra("role", "admin")
                        })
                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Не удалось создать гостя", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Не удалось создать гостевую семью", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * ✅ Исправлено:
     * 1) Если username уже есть в этой семье — это "вход" (НЕ создаём нового, НЕ добавляем -35).
     * 2) Если username нет — создаём нового пользователя (как раньше, но без конфликта имён).
     *
     * Важно: "uid" в приложении — это docId документа users/{docId}.
     */
    private fun joinFamily(authUid: String, requestedUsername: String, familyCode: String) {
        db.collection("families").document(familyCode).get()
            .addOnSuccessListener { familyDoc ->
                if (!familyDoc.exists()) {
                    Toast.makeText(this, "Семья с таким кодом не найдена", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val familyName = familyDoc.getString("familyName").orEmpty()

                // 1) Сначала пробуем ВОЙТИ как существующий пользователь по (familyCode + username)
                db.collection("users")
                    .whereEqualTo("familyCode", familyCode)
                    .whereEqualTo("username", requestedUsername)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { existingSnap ->
                        if (!existingSnap.isEmpty) {
                            // ✅ Это вход: используем существующий docId и роль
                            val doc = existingSnap.documents.first()
                            val existingDocId = doc.id
                            val existingRole = doc.getString("role").orEmpty().ifEmpty { "member" }
                            val existingName = doc.getString("username").orEmpty().ifEmpty { requestedUsername }

                            GuestSession.clear(this) // это уже НЕ гость
                            SessionManager.save(this, existingDocId, existingName, familyCode, existingRole)

                            SubscriptionReminderManager.scheduleDailyReminders(this)

                            startActivity(Intent(this, MainFrameActivity::class.java).apply {
                                putExtra("uid", existingDocId)
                                putExtra("username", existingName)
                                putExtra("familyCode", familyCode)
                                putExtra("role", existingRole)
                            })
                            finish()
                            return@addOnSuccessListener
                        }

                        // 2) Иначе — регистрация нового участника в семье
                        db.collection("users")
                            .whereEqualTo("familyCode", familyCode)
                            .get()
                            .addOnSuccessListener { usersSnap ->
                                val existingNames = usersSnap.documents
                                    .mapNotNull { it.getString("username") }
                                    .toSet()

                                val finalUsername = if (!existingNames.contains(requestedUsername)) {
                                    requestedUsername
                                } else {
                                    // На всякий случай оставляем уникализацию, если реально хотят создать "ещё одного" с тем же именем
                                    var candidate: String
                                    do {
                                        candidate = "$requestedUsername-${Random.nextInt(10, 99)}"
                                    } while (existingNames.contains(candidate))
                                    Toast.makeText(this, "Имя занято, назначено: $candidate", Toast.LENGTH_LONG).show()
                                    candidate
                                }

                                val userData = hashMapOf(
                                    "uid" to authUid,                 // auth uid можно хранить, но docId = authUid (как было)
                                    "username" to finalUsername,
                                    "familyCode" to familyCode,
                                    "familyName" to familyName,
                                    "role" to "member",
                                    "provider" to "manual"
                                )

                                // docId = authUid (как раньше), чтобы не ломать существующие поля ownerUid и т.п.
                                db.collection("users").document(authUid).set(userData)
                                    .addOnSuccessListener {
                                        GuestSession.clear(this) // это уже НЕ гость
                                        SessionManager.save(this, authUid, finalUsername, familyCode, "member")

                                        SubscriptionReminderManager.scheduleDailyReminders(this)

                                        startActivity(Intent(this, MainFrameActivity::class.java).apply {
                                            putExtra("uid", authUid)
                                            putExtra("username", finalUsername)
                                            putExtra("familyCode", familyCode)
                                            putExtra("role", "member")
                                        })
                                        finish()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(this, "Не удалось создать пользователя", Toast.LENGTH_SHORT).show()
                                    }
                            }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Ошибка загрузки пользователей", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Ошибка загрузки семьи", Toast.LENGTH_SHORT).show()
            }
    }

    private fun generateGuestFamilyCode(): String {
        val charset = ('A'..'Z') + ('0'..'9')
        return "G" + (1..5).map { charset.random() }.joinToString("")
    }
}
