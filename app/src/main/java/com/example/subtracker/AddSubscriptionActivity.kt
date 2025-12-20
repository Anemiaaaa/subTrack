package com.example.subtracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.subtracker.core.time.NextPaymentCalculator
import com.example.subtracker.core.model.Periodicity
import com.example.subtracker.presentation.add.AddSubscriptionViewModel
import com.example.subtracker.presentation.add.ServiceSearchManager
import kotlinx.coroutines.launch

class AddSubscriptionActivity : AppCompatActivity() {

    private var familyCode: String = ""
    private var username: String = ""
    private var role: String = "member"
    private var userDocId: String = ""

    private lateinit var nameInput: EditText
    private lateinit var priceInput: EditText
    private lateinit var periodicitySpinner: Spinner
    private lateinit var serviceSearchManager: ServiceSearchManager

    private val viewModel: AddSubscriptionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val isDark = ThemeManager.getMode(this) == ThemeManager.MODE_DARK
        setContentView(if (isDark) R.layout.add_subscription_dark else R.layout.activity_add_subscription)

        if (!loadSessionData()) return

        initializeViews(isDark)
        setupNavigation()
        setupPeriodicitySpinner(isDark)
        setupServiceSearch(isDark)
        setupSaveButton()
        observeViewModel()
    }

    private fun loadSessionData(): Boolean {
        familyCode = intent.getStringExtra("familyCode").orEmpty().ifEmpty { SessionManager.familyCode(this) }
        username = intent.getStringExtra("username").orEmpty().ifEmpty { SessionManager.username(this) }
        role = intent.getStringExtra("role") ?: SessionManager.role(this)
        userDocId = intent.getStringExtra("uid").orEmpty().ifEmpty { SessionManager.userDocId(this) }

        if (familyCode.isEmpty() || username.isEmpty() || userDocId.isEmpty()) {
            Toast.makeText(this, "Сессия не найдена. Войдите заново.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return false
        }
        return true
    }

    private fun initializeViews(isDark: Boolean) {
        nameInput = findViewById(R.id.editTextName)
        priceInput = findViewById(R.id.editTextPrice)
        periodicitySpinner = findViewById(R.id.spinnerPeriodicity)
        val iconSpinner = findViewById<Spinner>(R.id.spinnerIcon)
        val searchInput = findViewById<EditText>(R.id.editTextServiceSearch)

        serviceSearchManager = ServiceSearchManager(
            context = this,
            isDark = isDark,
            searchInput = searchInput,
            iconSpinner = iconSpinner,
            nameInput = nameInput,
            onNameAutoFill = { name -> nameInput.setText(name) }
        )
    }

    private fun setupPeriodicitySpinner(isDark: Boolean) {
        val adapter = if (isDark) {
            ArrayAdapter.createFromResource(this, R.array.periodicity_options, R.layout.spinner_item_dark)
                .apply { setDropDownViewResource(R.layout.spinner_item_dark) }
        } else {
            ArrayAdapter.createFromResource(this, R.array.periodicity_options, android.R.layout.simple_spinner_item)
                .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }
        periodicitySpinner.adapter = adapter
    }

    private fun setupServiceSearch(isDark: Boolean) {
        // Уже настроено в ServiceSearchManager
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.nav_home).setOnClickListener {
            navigateToMainFrame()
        }

        findViewById<View>(R.id.nav_stats).setOnClickListener {
            navigateToStats()
        }

        findViewById<View>(R.id.nav_settings).setOnClickListener {
            navigateToSettings()
        }

        findViewById<View>(R.id.nav_add).setOnClickListener { /* already here */ }
    }

    private fun navigateToMainFrame() {
        startActivity(Intent(this, MainFrameActivity::class.java).apply {
            putExtra("username", username)
            putExtra("familyCode", familyCode)
            putExtra("role", role)
            putExtra("uid", userDocId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        finish()
    }

    private fun navigateToStats() {
        startActivity(Intent(this, StatsActivity::class.java).apply {
            putExtra("username", username)
            putExtra("familyCode", familyCode)
            putExtra("role", role)
            putExtra("uid", userDocId)
        })
        finish()
    }

    private fun navigateToSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).apply {
            putExtra("username", username)
            putExtra("familyCode", familyCode)
            putExtra("role", role)
            putExtra("uid", userDocId)
        })
        finish()
    }

    private fun setupSaveButton() {
        findViewById<Button>(R.id.buttonSave).setOnClickListener {
            if (!validateInput()) return@setOnClickListener

            val name = nameInput.text.toString().trim()
            val price = priceInput.text.toString().toDoubleOrNull() ?: 0.0
            val periodicityText = periodicitySpinner.selectedItem?.toString().orEmpty()
            val selected = serviceSearchManager.getSelectedService()

            val periodicity = Periodicity.fromUiText(periodicityText)
                ?: return@setOnClickListener

            val iconResName = selected?.iconResName ?: "ic_default"
            val nextPaymentDate = NextPaymentCalculator.nextDateMillis(
                periodicity,
                System.currentTimeMillis()
            )

            viewModel.create(
                sessionUserDocId = userDocId,
                sessionUsername = username,
                familyCode = familyCode,
                name = name,
                price = price,
                periodicity = periodicityText,
                iconResName = iconResName,
                nextPaymentDate = nextPaymentDate
            )
        }
    }

    private fun validateInput(): Boolean {
        val name = nameInput.text.toString().trim()
        val price = priceInput.text.toString().toDoubleOrNull() ?: 0.0
        val periodicityText = periodicitySpinner.selectedItem?.toString().orEmpty()

        if (name.isEmpty()) {
            Toast.makeText(this, "Введите название подписки", Toast.LENGTH_SHORT).show()
            return false
        }

        if (price <= 0.0) {
            Toast.makeText(this, "Введите корректную стоимость", Toast.LENGTH_SHORT).show()
            return false
        }

        if (periodicityText.isEmpty() || Periodicity.fromUiText(periodicityText) == null) {
            Toast.makeText(this, "Выберите периодичность", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.event.collect { event ->
                    when (event) {
                        AddSubscriptionViewModel.Event.Saved -> {
                            handleSubscriptionSaved()
                            viewModel.clearEvent()
                        }

                        is AddSubscriptionViewModel.Event.Error -> {
                            Toast.makeText(this@AddSubscriptionActivity, event.message, Toast.LENGTH_SHORT).show()
                            viewModel.clearEvent()
                        }

                        null -> {}
                    }
                }
            }
        }
    }

    private fun handleSubscriptionSaved() {
        Toast.makeText(this, "Подписка добавлена", Toast.LENGTH_SHORT).show()
        navigateToMainFrame()
    }
}
