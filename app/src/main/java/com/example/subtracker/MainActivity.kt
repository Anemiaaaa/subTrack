package com.example.subtracker

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 🔹 Инициализация Firebase
        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val usernameInput = findViewById<EditText>(R.id.editTextLogin)
        val familyCodeInput = findViewById<EditText>(R.id.editTextPassword)
        val loginButton = findViewById<Button>(R.id.buttonLogin)
        val buttonCreateFamily = findViewById<Button>(R.id.buttonCreateFamily)

        // 🔁 AUTO LOGIN
        auth.currentUser?.let { user ->
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val username = doc.getString("username") ?: ""
                        val familyCode = doc.getString("familyCode") ?: ""
                        openMain(username, familyCode)
                    }
                }
        }

        buttonCreateFamily.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        loginButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val familyCode = familyCodeInput.text.toString().trim()

            if (username.isEmpty() || familyCode.isEmpty()) {
                Toast.makeText(this, "Введите имя и код семьи", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginWithFamilyCode(username, familyCode)
        }
    }

    private fun loginWithFamilyCode(username: String, familyCode: String) {

        // 1️⃣ проверяем семью
        db.collection("families").document(familyCode).get()
            .addOnSuccessListener { familyDoc ->

                if (!familyDoc.exists()) {
                    Toast.makeText(this, "Семья с таким кодом не найдена", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // 2️⃣ ищем пользователя с таким именем в этой семье
                db.collection("users")
                    .whereEqualTo("username", username)
                    .get()
                    .addOnSuccessListener { result ->

                        val existingUser = result.documents.firstOrNull()

                        if (existingUser != null) {
                            val existingFamilyCode = existingUser.getString("familyCode")

                            if (existingFamilyCode != familyCode) {
                                Toast.makeText(
                                    this,
                                    "Пользователь $username уже в другой семье",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@addOnSuccessListener
                            }

                            // ✅ пользователь найден — логинимся анонимно
                            signInAndOpen(username, familyCode)
                        } else {
                            // 3️⃣ новый пользователь → создаём
                            signInAndCreateUser(username, familyCode)
                        }
                    }
            }
    }

    private fun signInAndCreateUser(username: String, familyCode: String) {
        auth.signInAnonymously().addOnSuccessListener { result ->
            val uid = result.user!!.uid

            val userData = hashMapOf(
                "uid" to uid,
                "username" to username,
                "familyCode" to familyCode,
                "provider" to "manual"
            )

            db.collection("users").document(uid).set(userData)
                .addOnSuccessListener {
                    openMain(username, familyCode)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Ошибка создания пользователя", Toast.LENGTH_SHORT).show()
                }
        }.addOnFailureListener {
            Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
        }
    }

    private fun signInAndOpen(username: String, familyCode: String) {
        auth.signInAnonymously().addOnSuccessListener {
            openMain(username, familyCode)
        }.addOnFailureListener {
            Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openMain(username: String, familyCode: String) {
        val intent = Intent(this, MainFrameActivity::class.java)
        intent.putExtra("username", username)
        intent.putExtra("familyCode", familyCode)
        startActivity(intent)
        finish()
    }
}
