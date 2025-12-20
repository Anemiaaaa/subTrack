package com.example.subtracker

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.subtracker.app.di.AppGraph
import com.example.subtracker.domain.auth.AuthSession
import com.example.subtracker.presentation.auth.ForgotFamilyCodeDialogManager
import com.example.subtracker.presentation.auth.LoginViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels {
        AppGraph.loginViewModelFactory()
    }

    private lateinit var forgotCodeDialogManager: ForgotFamilyCodeDialogManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clearGuestSessionIfNeeded()
        setContentView(R.layout.activity_main)

        if (tryAutoLogin()) return

        initializeManagers()
        setupViews()
        observeViewModel()
    }

    private fun initializeManagers() {
        forgotCodeDialogManager = ForgotFamilyCodeDialogManager(
            context = this,
            onRecover = { username -> viewModel.recoverFamilyCode(username) }
        )
    }

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
            navigateToMainFrame(savedUid, savedUsername, savedFamily, savedRole.ifEmpty { "member" })
            finish()
            return true
        }
        return false
    }

    private fun setupViews() {
        val usernameInput = findViewById<EditText>(R.id.editTextLogin)
        val familyCodeInput = findViewById<EditText>(R.id.editTextPassword)
        val btnLogin = findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonLogin)
        val btnCreateFamily = findViewById<TextView>(R.id.buttonCreateFamily)
        val guestLink = findViewById<TextView>(R.id.textGuestLogin)
        val forgotCodeLink = findViewById<TextView>(R.id.textForgotFamilyCode)

        btnCreateFamily?.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnLogin?.setOnClickListener {
            val username = usernameInput?.text?.toString()?.trim() ?: ""
            val familyCode = familyCodeInput?.text?.toString()?.trim()?.uppercase() ?: ""

            if (username.isEmpty() || familyCode.isEmpty()) {
                Toast.makeText(this, "Введите имя и код семьи", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.joinFamily(username, familyCode)
        }

        guestLink?.setOnClickListener {
            viewModel.guestLogin()
        }

        forgotCodeLink?.setOnClickListener {
            forgotCodeDialogManager.show()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.event.collect { event ->
                    when (event) {
                        is LoginViewModel.Event.Success -> {
                            handleLoginSuccess(event.session)
                            viewModel.clearEvent()
                        }

                        is LoginViewModel.Event.FamilyCodeRecovered -> {
                            forgotCodeDialogManager.showRecoveredCode(event.familyCode)
                            // Автоматически заполняем поле кода семьи
                            findViewById<EditText>(R.id.editTextPassword)?.setText(event.familyCode)
                            viewModel.clearEvent()
                        }

                        is LoginViewModel.Event.Error -> {
                            Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                            viewModel.clearEvent()
                        }

                        null -> {}
                    }
                }
            }
        }
    }

    private fun handleLoginSuccess(session: AuthSession) {
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
        navigateToMainFrame(
            session.userDocId,
            session.username,
            session.familyCode,
            session.role
        )
        finish()
    }

    private fun navigateToMainFrame(uid: String, username: String, familyCode: String, role: String) {
        startActivity(Intent(this, MainFrameActivity::class.java).apply {
            putExtra("uid", uid)
            putExtra("username", username)
            putExtra("familyCode", familyCode)
            putExtra("role", role)
        })
    }
}
