package com.example.subtracker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.subtracker.app.di.AppGraph
import com.example.subtracker.presentation.auth.RegisterViewModel
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private val viewModel: RegisterViewModel by viewModels {
        AppGraph.registerViewModelFactory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        val usernameInput = findViewById<EditText>(R.id.editTextLogin2)
        val familyNameInput = findViewById<EditText>(R.id.editTextPassword)
        val buttonCreate = findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonLogin)
        val backButton = findViewById<TextView>(R.id.buttonBack)

        buttonCreate?.setOnClickListener {
            val username = usernameInput?.text?.toString()?.trim() ?: ""
            val familyName = familyNameInput?.text?.toString()?.trim() ?: ""

            if (username.isEmpty() || familyName.isEmpty()) {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.create(username, familyName)
        }

        backButton?.setOnClickListener { finish() }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.event.collect { event ->
                    when (event) {
                        is RegisterViewModel.Event.Created -> {
                            handleFamilyCreated(event)
                            viewModel.clearEvent()
                        }

                        is RegisterViewModel.Event.Error -> {
                            Toast.makeText(this@RegisterActivity, event.message, Toast.LENGTH_SHORT).show()
                            viewModel.clearEvent()
                        }

                        null -> {}
                    }
                }
            }
        }
    }

    private fun handleFamilyCreated(event: RegisterViewModel.Event.Created) {
        GuestSession.clear(this)
        SessionManager.save(
            this,
            event.session.userDocId,
            event.session.username,
            event.session.familyCode,
            event.session.role
        )
        showFamilyCodeDialog(event.familyCode)
    }

    private fun showFamilyCodeDialog(familyCode: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Семья создана")
            .setMessage("Код вашей семьи: $familyCode\n(нажмите, чтобы скопировать)")
            .setPositiveButton("OK") { d, _ ->
                d.dismiss()
                navigateToMainFrame()
                finish()
            }
            .create()

        dialog.setOnShowListener {
            val messageView = dialog.findViewById<TextView>(android.R.id.message)
            messageView?.setOnClickListener {
                copyToClipboard("Код семьи", familyCode)
                Toast.makeText(this, "Код скопирован", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun navigateToMainFrame() {
        val uid = SessionManager.userDocId(this)
        val username = SessionManager.username(this)
        val familyCode = SessionManager.familyCode(this)
        val role = SessionManager.role(this)

        if (uid.isNotEmpty() && familyCode.isNotEmpty() && username.isNotEmpty()) {
            SubscriptionReminderManager.scheduleDailyReminders(this)
            startActivity(Intent(this, MainFrameActivity::class.java).apply {
                putExtra("uid", uid)
                putExtra("username", username)
                putExtra("familyCode", familyCode)
                putExtra("role", role)
            })
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
