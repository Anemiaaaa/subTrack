package com.example.subtracker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.subtracker.presentation.settings.SettingsViewModel
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLayout()
        setupViews()
        setupBottomNav()
        observeViewModel()
    }

    private fun setupLayout() {
        val isDark = ThemeManager.getMode(this) == ThemeManager.MODE_DARK
        setContentView(if (isDark) R.layout.activity_settings_dark else R.layout.activity_settings)
    }

    private fun setupViews() {
        val username = SessionManager.username(this)
        val familyCode = SessionManager.familyCode(this)

        findViewById<TextView?>(R.id.username_value)?.text = username.ifEmpty { "—" }
        
        val familyCodeValue = findViewById<TextView?>(R.id.familycode_value)
        familyCodeValue?.text = familyCode.ifEmpty { "—" }
        familyCodeValue?.setOnClickListener {
            if (familyCode.isNotEmpty()) {
                copyToClipboard("Код семьи", familyCode)
                Toast.makeText(this, "Код семьи скопирован", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Код семьи не найден", Toast.LENGTH_SHORT).show()
            }
        }

        // Настройка переключателя темы
        setupThemeSwitcher()

        findViewById<View>(R.id.button_logout)?.setOnClickListener {
            viewModel.logout()
        }
    }

    private fun setupThemeSwitcher() {
        val themeButton = findViewById<View>(R.id.button_change_theme)
        val themeLabel = findViewById<TextView>(R.id.theme_current_label)

        // Обновляем текст текущей темы
        val currentMode = ThemeManager.getMode(this)
        themeLabel?.text = ThemeManager.modeLabel(currentMode)

        themeButton?.setOnClickListener {
            toggleTheme()
        }
    }

    private fun toggleTheme() {
        val currentMode = ThemeManager.getMode(this)
        val newMode = when (currentMode) {
            ThemeManager.MODE_LIGHT -> ThemeManager.MODE_DARK
            ThemeManager.MODE_DARK -> ThemeManager.MODE_LIGHT
            else -> ThemeManager.MODE_DARK // Если системная, переключаем на темную
        }

        ThemeManager.setMode(this, newMode)
        ThemeManager.restartActivityWithFade(this)
    }

    private fun setupBottomNav() {
        findViewById<View>(R.id.nav_home)?.setOnClickListener { finish() }

        findViewById<View>(R.id.nav_stats)?.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
            finish()
        }

        findViewById<View>(R.id.nav_add)?.setOnClickListener {
            startActivity(Intent(this, AddSubscriptionActivity::class.java))
            finish()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.event.collect { event ->
                    when (event) {
                        SettingsViewModel.Event.LoggedOut -> {
                            handleLogout()
                            viewModel.clearEvent()
                        }

                        is SettingsViewModel.Event.Error -> {
                            Toast.makeText(this@SettingsActivity, event.message, Toast.LENGTH_SHORT).show()
                            viewModel.clearEvent()
                        }

                        null -> Unit
                    }
                }
            }
        }
    }

    private fun handleLogout() {
        SessionManager.clear(this)
        GuestSession.clear(this)

        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
