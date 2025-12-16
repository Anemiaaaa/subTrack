package com.example.subtracker

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
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

            val currentUser = auth.currentUser
            if (currentUser == null) {
                auth.signInAnonymously().addOnSuccessListener { authResult ->
                    val uid = authResult.user!!.uid
                    createFamily(uid, username, familyName, familyCode)
                }.addOnFailureListener {
                    Toast.makeText(this, "Ошибка аутентификации", Toast.LENGTH_SHORT).show()
                }
            } else {
                createFamily(currentUser.uid, username, familyName, familyCode)
            }
        }

        backButton.setOnClickListener { finish() }
    }

    private fun createFamily(uid: String, username: String, familyName: String, familyCode: String) {
        // Создаём семью
        val familyData = hashMapOf(
            "familyName" to familyName,
            "createdBy" to uid
        )

        db.collection("families").document(familyCode).set(familyData)
            .addOnSuccessListener {
                // Создаём пользователя как админа через FirebaseUser
                val user = FirebaseUser(
                    id = uid,
                    username = username,
                    familyCode = familyCode,
                    familyName = familyName,
                    isAdmin = true
                )

                db.collection("users").document(uid).set(user)
                    .addOnSuccessListener {
                        showFamilyCodeDialog(familyCode)
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Ошибка при создании пользователя", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Ошибка при создании семьи", Toast.LENGTH_SHORT).show()
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
                clipboard.setPrimaryClip(ClipData.newPlainText("Код семьи", familyCode))
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
