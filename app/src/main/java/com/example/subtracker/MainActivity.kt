package com.example.subtracker

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.subtracker.app.di.AppGraph
import com.example.subtracker.domain.auth.AuthSession
import com.example.subtracker.presentation.auth.ForgotFamilyCodeDialogManager
import com.example.subtracker.presentation.auth.LoginViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels {
        AppGraph.loginViewModelFactory()
    }

    private lateinit var forgotCodeDialogManager: ForgotFamilyCodeDialogManager
    private lateinit var googleSignInClient: GoogleSignInClient

    private val tag = "MainActivityAuth"

    // ================= GOOGLE RESULT =================
    private val googleAuthLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            // Проверяем только на явную отмену, не на все не-OK коды
            if (result.resultCode == RESULT_CANCELED) {
                val data = result.data
                val exception = data?.let { GoogleSignIn.getSignedInAccountFromIntent(it).exception }
                
                if (exception is ApiException) {
                    val errorMessage = when (exception.statusCode) {
                        GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> {
                            Log.d(tag, "Google sign-in was cancelled by user")
                            return@registerForActivityResult
                        }
                        GoogleSignInStatusCodes.SIGN_IN_FAILED -> {
                            "Ошибка входа. Проверьте интернет-соединение"
                        }
                        GoogleSignInStatusCodes.NETWORK_ERROR -> {
                            "Ошибка сети. Проверьте интернет-соединение"
                        }
                        GoogleSignInStatusCodes.DEVELOPER_ERROR -> {
                            "Ошибка конфигурации. Проверьте настройки Google Sign-In"
                        }
                        else -> {
                            "Ошибка входа через Google (код ${exception.statusCode})"
                        }
                    }
                    Log.w(tag, "Google sign-in error: ${exception.statusCode}")
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    return@registerForActivityResult
                } else {
                    Log.d(tag, "Google sign-in was cancelled")
                    return@registerForActivityResult
                }
            }

            val data = result.data
            if (data == null) {
                Log.w(tag, "Google sign-in returned OK but data is null")
                Toast.makeText(this, "Google вход: пустой ответ", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val token = account.idToken

                Log.d(tag, "Google account received: email=${account.email}, hasToken=${token != null}")

                if (token.isNullOrBlank()) {
                    Toast.makeText(
                        this,
                        "Не удалось получить Google токен. Проверь default_web_client_id + SHA-1 в Firebase",
                        Toast.LENGTH_LONG
                    ).show()
                    return@registerForActivityResult
                }

                firebaseAuthWithGoogle(token)
            } catch (e: ApiException) {
                Log.e(tag, "Google sign-in ApiException statusCode=${e.statusCode}", e)
                val errorMessage = when (e.statusCode) {
                    GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> {
                        // Пользователь отменил - не показываем ошибку
                        Log.d(tag, "Google sign-in cancelled by user")
                        return@registerForActivityResult
                    }
                    GoogleSignInStatusCodes.SIGN_IN_FAILED -> {
                        "Ошибка входа. Проверьте интернет-соединение"
                    }
                    GoogleSignInStatusCodes.NETWORK_ERROR -> {
                        "Ошибка сети. Проверьте интернет-соединение"
                    }
                    GoogleSignInStatusCodes.DEVELOPER_ERROR -> {
                        "Ошибка конфигурации. Проверьте настройки Google Sign-In в Firebase Console"
                    }
                    else -> {
                        "Ошибка Google входа (код ${e.statusCode})"
                    }
                }
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(tag, "Google sign-in unknown error", e)
                Toast.makeText(this, "Ошибка входа Google", Toast.LENGTH_SHORT).show()
            }
        }

    // ================= LIFECYCLE =================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        clearGuestSessionIfNeeded()
        setContentView(R.layout.activity_main)

        if (tryAutoLogin()) return

        initGoogleSignIn()
        initializeManagers()
        setupViews()
        observeViewModel()
    }

    // ================= INIT =================
    private fun initGoogleSignIn() {
        val webClientId = getString(R.string.default_web_client_id)

        if (webClientId.isBlank() || webClientId == "YOUR_WEB_CLIENT_ID") {
            Log.e(tag, "default_web_client_id is blank or placeholder!")
            Toast.makeText(this, "Ошибка конфигурации: Web Client ID не настроен", Toast.LENGTH_LONG).show()
            return
        }
        
        Log.d(tag, "Initializing Google Sign-In with Web Client ID: ${webClientId.take(20)}...")
        Log.d(tag, "Full Web Client ID: $webClientId")

        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .requestProfile()
                .build()

            googleSignInClient = GoogleSignIn.getClient(this, gso)
            Log.d(tag, "Google Sign-In client initialized successfully")
            
            // Проверяем, что Google Play Services доступны
            val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(this)
            if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                Log.w(tag, "Google Play Services not available: $resultCode")
                if (availability.isUserResolvableError(resultCode)) {
                    availability.getErrorDialog(this, resultCode, 9000)?.show()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error initializing Google Sign-In", e)
            Toast.makeText(
                this,
                "Ошибка инициализации Google Sign-In: ${e.message}\n\nПроверьте:\n1. SHA-1 fingerprint добавлен в Firebase\n2. Google Sign-In включен в Firebase Console",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun initializeManagers() {
        forgotCodeDialogManager = ForgotFamilyCodeDialogManager(
            context = this,
            onRecover = { username -> viewModel.recoverFamilyCode(username) }
        )
    }

    // ================= SESSION =================
    private fun clearGuestSessionIfNeeded() {
        if (GuestSession.isActive(this)) {
            GuestSession.clear(this)
            SessionManager.clear(this)
            FirebaseAuth.getInstance().signOut()
        }
    }

    private fun tryAutoLogin(): Boolean {
        val savedUid = SessionManager.userDocId(this)
        val savedFamily = SessionManager.familyCode(this)
        val savedUsername = SessionManager.username(this)
        val savedRole = SessionManager.role(this)

        if (savedUid.isNotEmpty() && savedFamily.isNotEmpty() && savedUsername.isNotEmpty()) {
            navigateToMainFrame(
                savedUid,
                savedUsername,
                savedFamily,
                savedRole.ifEmpty { "member" }
            )
            finish()
            return true
        }
        return false
    }

    // ================= UI =================
    private fun setupViews() {
        Log.d(tag, "setupViews() called")
        
        val usernameInput = findViewById<EditText>(R.id.editTextLogin)
        val familyCodeInput = findViewById<EditText>(R.id.editTextPassword)
        val btnLogin = findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonLogin)
        val btnGoogleLogin = findViewById<CardView>(R.id.googleLoginButton)
        val btnCreateFamily = findViewById<TextView>(R.id.buttonCreateFamily)
        val guestLink = findViewById<TextView>(R.id.textGuestLogin)
        val forgotCodeLink = findViewById<TextView>(R.id.textForgotFamilyCode)

        if (btnLogin == null) {
            Log.e(tag, "ERROR: buttonLogin is null!")
            Toast.makeText(this, "Ошибка: кнопка входа не найдена", Toast.LENGTH_LONG).show()
            return
        }

        Log.d(tag, "All views found, setting up listeners")

        btnCreateFamily?.setOnClickListener {
            Log.d(tag, "Create family button clicked")
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnLogin.setOnClickListener {
            val username = usernameInput?.text?.toString()?.trim() ?: ""
            val familyCode = familyCodeInput?.text?.toString()?.trim()?.uppercase() ?: ""

            Log.d(tag, "Login button clicked: username=$username, familyCode=$familyCode")

            if (username.isEmpty() || familyCode.isEmpty()) {
                Log.w(tag, "Empty username or family code")
                Toast.makeText(this, "Введите имя и код семьи", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Log.d(tag, "Calling viewModel.joinFamily()")
            try {
                viewModel.joinFamily(username, familyCode)
                Log.d(tag, "viewModel.joinFamily() called successfully")
            } catch (e: Exception) {
                Log.e(tag, "Error calling viewModel.joinFamily()", e)
                Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        btnGoogleLogin.setOnClickListener {
            Log.d(tag, "Google login button clicked")
            
            // Проверяем, что Google Sign-In клиент инициализирован
            if (!::googleSignInClient.isInitialized) {
                Log.e(tag, "Google Sign-In client not initialized!")
                Toast.makeText(
                    this,
                    "Google Sign-In не настроен. Проверьте конфигурацию.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            
            // ВАЖНО: не делаем signOut() перед запуском — это часто ломает/сбрасывает флоу
            val webClientId = getString(R.string.default_web_client_id)
            Log.d(tag, "Launching Google sign-in intent")
            Log.d(tag, "Using Web Client ID: ${webClientId.take(30)}...")
            
            // Проверяем текущего пользователя Firebase
            val currentUser = FirebaseAuth.getInstance().currentUser
            Log.d(tag, "Current Firebase user: ${currentUser?.uid ?: "null"}")
            
            try {
                val signInIntent = googleSignInClient.signInIntent
                googleAuthLauncher.launch(signInIntent)
            } catch (e: Exception) {
                Log.e(tag, "Error launching Google sign-in", e)
                Toast.makeText(
                    this,
                    "Ошибка запуска Google Sign-In: ${e.message}\n\nУбедитесь, что SHA-1 fingerprint добавлен в Firebase Console",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        guestLink.setOnClickListener {
            viewModel.guestLogin()
        }

        forgotCodeLink.setOnClickListener {
            forgotCodeDialogManager.show()
        }
    }

    // ================= GOOGLE FLOW =================
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)

        FirebaseAuth.getInstance()
            .signInWithCredential(credential)
            .addOnSuccessListener {
                Log.d(tag, "Firebase signInWithCredential success")
                showFamilyCodeDialog()
            }
            .addOnFailureListener { e ->
                Log.e(tag, "Firebase signInWithCredential failed", e)
                Toast.makeText(
                    this,
                    "Ошибка Firebase авторизации: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun showFamilyCodeDialog() {
        val input = EditText(this).apply {
            hint = "Код семьи"
        }

        AlertDialog.Builder(this)
            .setTitle("Вход в семью")
            .setMessage("Введите код семьи для присоединения")
            .setView(input)
            .setPositiveButton("Войти") { _, _ ->
                val familyCode = input.text.toString().trim().uppercase()
                if (familyCode.isEmpty()) {
                    Toast.makeText(this, "Введите код семьи", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val user = FirebaseAuth.getInstance().currentUser
                if (user == null) {
                    Toast.makeText(this, "Google пользователь не найден", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Используем имя из Google аккаунта или email
                val username = (user.displayName ?: user.email?.substringBefore("@") ?: "GoogleUser").trim()

                viewModel.googleJoinFamily(
                    uid = user.uid,
                    username = username,
                    familyCode = familyCode
                )
            }
            .setNegativeButton("Отмена") { _, _ ->
                // При отмене выходим из Firebase Auth, чтобы можно было войти снова
                FirebaseAuth.getInstance().signOut()
                Log.d(tag, "Google sign-in cancelled, signed out from Firebase")
            }
            .setCancelable(false) // Не позволяем закрыть диалог свайпом, только кнопками
            .show()
    }

    // ================= OBSERVE =================
    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Log.d(tag, "Starting to observe ViewModel events")
                viewModel.event.collect { event ->
                    Log.d(tag, "Received event: $event")
                    when (event) {
                        is LoginViewModel.Event.Success -> {
                            Log.d(tag, "Login success: session=${event.session}")
                            handleLoginSuccess(event.session)
                            viewModel.clearEvent()
                        }

                        is LoginViewModel.Event.FamilyCodeRecovered -> {
                            Log.d(tag, "Family code recovered: ${event.familyCode}")
                            forgotCodeDialogManager.showRecoveredCode(event.familyCode)
                            findViewById<EditText>(R.id.editTextPassword)
                                ?.setText(event.familyCode)
                            viewModel.clearEvent()
                        }

                        is LoginViewModel.Event.Error -> {
                            Log.e(tag, "Login error: ${event.message}")
                            Toast.makeText(
                                this@MainActivity,
                                event.message,
                                Toast.LENGTH_LONG
                            ).show()
                            viewModel.clearEvent()
                        }

                        null -> {
                            Log.d(tag, "Event is null")
                        }
                    }
                }
            }
        }
    }

    // ================= NAVIGATION =================
    private fun handleLoginSuccess(session: AuthSession) {
        Log.d(tag, "handleLoginSuccess: session=$session")
        
        if (session.isGuest) {
            GuestSession.setActive(this, true)
        } else {
            GuestSession.clear(this)
        }

        SessionManager.save(
            this,
            session.userDocId,
            session.username,
            session.familyCode,
            session.role
        )

        SubscriptionReminderManager.scheduleDailyReminders(this)

        Log.d(tag, "Navigating to MainFrameActivity")
        navigateToMainFrame(
            session.userDocId,
            session.username,
            session.familyCode,
            session.role
        )
        finish()
    }

    private fun navigateToMainFrame(
        uid: String,
        username: String,
        familyCode: String,
        role: String
    ) {
        startActivity(Intent(this, MainFrameActivity::class.java).apply {
            putExtra("uid", uid)
            putExtra("username", username)
            putExtra("familyCode", familyCode)
            putExtra("role", role)
        })
    }
}
