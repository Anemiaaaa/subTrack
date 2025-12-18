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
            loginOrCreateUser(username, familyCode)
        }

        buttonCreateFamily.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        googleButton.setOnClickListener { signInWithGoogle() }
    }

    // =================== LOGIN / CREATE ===================
    private fun loginOrCreateUser(username: String, familyCode: String) {
        // Сначала проверяем, существует ли семья
        db.collection("families").document(familyCode).get()
            .addOnSuccessListener { familyDoc ->
                if (!familyDoc.exists()) {
                    Toast.makeText(this, "Семья с таким кодом не найдена", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Проверяем, существует ли пользователь с таким username в семье
                db.collection("users")
                    .whereEqualTo("familyCode", familyCode)
                    .whereEqualTo("username", username)
                    .get()
                    .addOnSuccessListener { userSnap ->
                        val userDoc = userSnap.documents.firstOrNull()
                        if (userDoc != null) {
                            // Пользователь найден
                            openMain(username, familyCode)
                        } else {
                            // Пользователь не найден — создаем нового
                            val currentUser = auth.currentUser
                            val uid = currentUser?.uid ?: run {
                                auth.signInAnonymously().addOnSuccessListener { res ->
                                    createUserDoc(res.user!!.uid, username, familyCode)
                                }
                                return@addOnSuccessListener
                            }
                            createUserDoc(uid, username, familyCode)
                        }
                    }
            }
    }

    private fun createUserDoc(uid: String, username: String, familyCode: String) {
        val userData = hashMapOf(
            "uid" to uid,
            "username" to username,
            "familyCode" to familyCode,
            "role" to "member",
            "provider" to "manual"
        )
        db.collection("users").document(uid)
            .set(userData)
            .addOnSuccessListener { openMain(username, familyCode) }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка создания пользователя: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // =================== GOOGLE SIGN-IN ===================
    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun signInWithGoogle() {
        startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
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
                val user = auth.currentUser!!
                val username = user.displayName ?: "User"
                val uid = user.uid

                db.collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            val familyCode = doc.getString("familyCode") ?: ""
                            openMain(username, familyCode)
                        } else {
                            promptFamilyCode(uid, username)
                        }
                    }
            }
    }

    private fun promptFamilyCode(uid: String, username: String) {
        val input = EditText(this)
        input.hint = "Введите код семьи"
        AlertDialog.Builder(this)
            .setTitle("Вход через Google")
            .setMessage("Введите код семьи")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val familyCode = input.text.toString().trim()
                if (familyCode.isEmpty()) return@setPositiveButton

                db.collection("families").document(familyCode).get()
                    .addOnSuccessListener { familyDoc ->
                        if (!familyDoc.exists()) {
                            Toast.makeText(this, "Семья с таким кодом не найдена", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

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
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openMain(username: String, familyCode: String) {
        val intent = Intent(this, MainFrameActivity::class.java)
        intent.putExtra("username", username)
        intent.putExtra("familyCode", familyCode)
        startActivity(intent)
        finish()
    }
}
