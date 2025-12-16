package com.example.subtracker

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupGoogleSignIn()

        val usernameInput = findViewById<EditText>(R.id.editTextLogin)
        val familyCodeInput = findViewById<EditText>(R.id.editTextPassword)
        val loginButton = findViewById<Button>(R.id.buttonLogin)
        val buttonCreateFamily = findViewById<Button>(R.id.buttonCreateFamily)
        val googleButton = findViewById<ConstraintLayout>(R.id.googleLoginButton)

        loginButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val familyCode = familyCodeInput.text.toString().trim()

            if (username.isEmpty() || familyCode.isEmpty()) {
                Toast.makeText(this, "Введите имя и код семьи", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginWithFamilyCode(username, familyCode)
        }

        buttonCreateFamily.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        googleButton.setOnClickListener {
            signInWithGoogle()
        }
    }

    // 🔹 Вход по имени+коду семьи
    private fun loginWithFamilyCode(username: String, familyCode: String) {
        db.collection("families").document(familyCode).get()
            .addOnSuccessListener { familyDoc ->
                if (!familyDoc.exists()) {
                    Toast.makeText(this, "Семья с таким кодом не найдена", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                db.collection("users")
                    .whereEqualTo("username", username)
                    .whereEqualTo("familyCode", familyCode)
                    .get()
                    .addOnSuccessListener { result ->
                        val userDoc = result.documents.firstOrNull()
                        if (userDoc != null) {
                            openMain(username, familyCode)
                        } else {
                            createMemberUser(username, familyCode)
                        }
                    }
            }
    }

    private fun createMemberUser(username: String, familyCode: String) {
        auth.signInAnonymously().addOnSuccessListener { result ->
            val uid = result.user!!.uid
            val userData = hashMapOf(
                "uid" to uid,
                "username" to username,
                "familyCode" to familyCode,
                "role" to "member",
                "provider" to "manual"
            )
            db.collection("users").document(uid).set(userData)
                .addOnSuccessListener {
                    openMain(username, familyCode)
                }
        }
    }

    private fun openMain(username: String, familyCode: String) {
        val intent = Intent(this, MainFrameActivity::class.java)
        intent.putExtra("username", username)
        intent.putExtra("familyCode", familyCode)
        startActivity(intent)
        finish()
    }

    // 🔹 Google Sign-In
    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Ошибка Google Sign-In: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener {
                val user = auth.currentUser!! // <- FirebaseUser

                // Можно сразу взять displayName и email
                val username = user.displayName ?: "User"
                val uid = user.uid

                // 🔹 Проверяем есть ли пользователь в Firestore
                db.collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            // Пользователь уже есть
                            val familyCode = doc.getString("familyCode") ?: ""
                            openMain(username, familyCode)
                        } else {
                            // Новый пользователь, вводим код семьи
                            val input = EditText(this)
                            input.hint = "Введите код семьи"
                            AlertDialog.Builder(this)
                                .setTitle("Вход через Google")
                                .setMessage("Введите код семьи")
                                .setView(input)
                                .setPositiveButton("OK") { _, _ ->
                                    val familyCode = input.text.toString().trim()
                                    if (familyCode.isEmpty()) return@setPositiveButton

                                    // Создаем пользователя
                                    val userData = hashMapOf(
                                        "uid" to uid,
                                        "username" to username,
                                        "familyCode" to familyCode,
                                        "role" to "member",
                                        "provider" to "google"
                                    )
                                    db.collection("users").document(uid).set(userData)
                                        .addOnSuccessListener { openMain(username, familyCode) }
                                }
                                .setNegativeButton("Отмена", null)
                                .show()
                        }
                    }
            }
    }


    private fun checkOrCreateGoogleUser(uid: String, username: String, familyCode: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    openMain(username, doc.getString("familyCode") ?: familyCode)
                } else {
                    val userData = hashMapOf(
                        "uid" to uid,
                        "username" to username,
                        "familyCode" to familyCode,
                        "role" to "member",
                        "provider" to "google"
                    )
                    db.collection("users").document(uid).set(userData)
                        .addOnSuccessListener { openMain(username, familyCode) }
                }
            }
    }
}
