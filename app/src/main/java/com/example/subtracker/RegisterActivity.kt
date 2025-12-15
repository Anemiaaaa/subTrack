package com.example.subtracker

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val usernameInput = findViewById<EditText>(R.id.editTextLogin2)
        val familyNameInput = findViewById<EditText>(R.id.editTextPassword)
        val buttonCreate = findViewById<Button>(R.id.buttonLogin)
        val backButton = findViewById<Button>(R.id.buttonBack)

        buttonCreate.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val familyName = familyNameInput.text.toString().trim()

            if (username.isEmpty() || familyName.isEmpty()) {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val familyCode = generateFamilyCode()

            Log.d("RegisterActivity", "Запуск создания семьи: username=$username, familyName=$familyName, familyCode=$familyCode")

            // Проверяем аутентификацию
            val currentUser = auth.currentUser
            if (currentUser == null) {
                auth.signInAnonymously()
                    .addOnSuccessListener { authResult ->
                        val uid = authResult.user?.uid
                        if (uid != null) {
                            Log.d("RegisterActivity", "Анонимный пользователь создан: UID=$uid")
                            createFamilyUser(uid, username, familyName, familyCode)
                        } else {
                            Log.e("RegisterActivity", "authResult.user == null")
                            Toast.makeText(this, "Ошибка регистрации", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("RegisterActivity", "Ошибка анонимной аутентификации: ${e.message}")
                        Toast.makeText(this, "Ошибка аутентификации: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                createFamilyUser(currentUser.uid, username, familyName, familyCode)
            }
        }

        backButton.setOnClickListener { finish() }
    }

    private fun createFamilyUser(uid: String, username: String, familyName: String, familyCode: String) {
        Log.d("RegisterActivity", "Создание документа пользователя UID=$uid")

        db.collection("users")
            .whereEqualTo("username", username)
            .get()
            .addOnSuccessListener { query ->
                if (!query.isEmpty) {
                    Toast.makeText(this, "Пользователь уже существует", Toast.LENGTH_SHORT).show()
                    Log.d("RegisterActivity", "Username уже существует: $username")
                    return@addOnSuccessListener
                }

                val userMap = hashMapOf(
                    "username" to username,
                    "familyName" to familyName,
                    "familyCode" to familyCode,
                    "isAdmin" to true
                )

                db.collection("users").document(uid)
                    .set(userMap)
                    .addOnSuccessListener {
                        Log.d("RegisterActivity", "Пользователь создан успешно: UID=$uid")
                        showFamilyCodeDialog(familyCode)
                    }
                    .addOnFailureListener { e ->
                        Log.e("RegisterActivity", "Ошибка при создании пользователя: ${e.message}")
                        Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("RegisterActivity", "Ошибка при проверке username: ${e.message}")
                Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showFamilyCodeDialog(familyCode: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Семья создана")
            .setMessage("Код вашей семьи: $familyCode\n(нажмите, чтобы скопировать)")
            .setPositiveButton("OK") { d, _ -> d.dismiss(); finish() }
            .create()

        dialog.setOnShowListener {
            val messageView = dialog.findViewById<TextView>(android.R.id.message)
            messageView?.setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Код семьи", familyCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Код скопирован", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun generateFamilyCode(): String {
        val charset = ('A'..'Z') + ('0'..'9')
        return (1..6).map { charset.random() }.joinToString("")
    }
}
