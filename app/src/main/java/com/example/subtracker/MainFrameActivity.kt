package com.example.subtracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.subtracker.app.di.AppGraph
import com.example.subtracker.domain.model.Subscription
import com.example.subtracker.presentation.main.*
import kotlinx.coroutines.launch

class MainFrameActivity : AppCompatActivity() {

    private lateinit var subscriptionsContainer: LinearLayout
    private lateinit var textUsername: TextView
    private lateinit var btnSortPrice: TextView
    private lateinit var btnSortDate: TextView
    private lateinit var btnFilterUsers: TextView

    private lateinit var familyCode: String
    private lateinit var username: String
    private var role: String = "member"
    private var uid: String = ""

    private lateinit var cardRenderer: SubscriptionCardRenderer
    private lateinit var dialogManager: SubscriptionDialogManager
    private lateinit var familyDialogManager: FamilyInfoDialogManager
    private lateinit var filterDialogManager: FilterDialogManager
    private lateinit var uiBinder: MainFrameUiBinder

    private val viewModel: MainFrameViewModel by viewModels {
        AppGraph.mainFrameViewModelFactory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.dumpSession(this)

        val themeMode = ThemeManager.getMode(this)
        val layoutRes = when (themeMode) {
            ThemeManager.MODE_DARK -> R.layout.activity_main_dark
            else -> R.layout.main_frame
        }
        setContentView(layoutRes)

        subscriptionsContainer = findViewById(R.id.subscriptionsContainer)
        textUsername = findViewById(R.id.textUsername)

        btnSortPrice = findViewById(R.id.btnSortPrice)
        btnSortDate = findViewById(R.id.btnSortDate)
        btnFilterUsers = findViewById(R.id.btnFilterUsers)

        username = intent.getStringExtra("username").orEmpty().ifEmpty { SessionManager.username(this) }
        familyCode = intent.getStringExtra("familyCode").orEmpty().ifEmpty { SessionManager.familyCode(this) }
        role = (intent.getStringExtra("role") ?: SessionManager.role(this)).ifEmpty { "member" }
        uid = intent.getStringExtra("uid").orEmpty().ifEmpty { SessionManager.userDocId(this) }

        if (username.isEmpty() || familyCode.isEmpty() || uid.isEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        textUsername.text = "Привет, $username"
        SessionManager.save(this, uid, username, familyCode, role)

        initializeManagers()
        setupBottomNav()
        setupTopButtons()
        setupViewModel()

        findViewById<View>(R.id.btnFamilyInfo).setOnClickListener {
            viewModel.requestFamilyInfo()
        }

        viewModel.start(
            familyCode = familyCode,
            userDocId = uid,
            username = username,
            role = role
        )

        observeViewModel()
    }

    private fun initializeManagers() {
        // Инициализируем dialogManager первым, так как он используется в cardRenderer
        dialogManager = SubscriptionDialogManager(
            context = this,
            onEdit = { /* handled by dialog itself */ },
            onPay = { subscription -> viewModel.pay(subscription) },
            onDelete = { subscription -> viewModel.delete(subscription) },
            onUpdate = { subscription, newName, newPrice, newPeriod, newIcon ->
                viewModel.update(subscription, newName, newPrice, newPeriod, newIcon)
            }
        )

        cardRenderer = SubscriptionCardRenderer(
            context = this,
            container = subscriptionsContainer,
            onCardClick = { subscription -> dialogManager.showActionsDialog(subscription) }
        )

        familyDialogManager = FamilyInfoDialogManager(this)
        filterDialogManager = FilterDialogManager(
            context = this,
            onFilterSelected = { username -> viewModel.setUserFilter(username) }
        )

        uiBinder = MainFrameUiBinder(
            lifecycleOwner = this,
            viewModel = viewModel,
            btnSortPrice = btnSortPrice,
            btnSortDate = btnSortDate,
            btnFilterUsers = btnFilterUsers,
            onStateChanged = { state ->
                role = state.role
                SessionManager.save(this, uid, username, familyCode, role)
                // Видимость кнопки управляется в MainFrameUiBinder на основе isAdmin
                cardRenderer.render(state.items, username)
            }
        )
    }

    private fun setupViewModel() {
        uiBinder.bind()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is MainFrameEvent.Toast -> {
                                Toast.makeText(
                                    this@MainFrameActivity,
                                    event.message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            is MainFrameEvent.ShowFamilyInfo -> {
                                familyDialogManager.show(
                                    familyName = event.familyName,
                                    familyCode = event.familyCode,
                                    members = event.members
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupBottomNav() {
        findViewById<View>(R.id.nav_home)?.setOnClickListener {
            // уже на главном
        }

        findViewById<View>(R.id.nav_add)?.setOnClickListener {
            if (uid.isNotEmpty() && ::username.isInitialized && ::familyCode.isInitialized) {
                startActivity(Intent(this, AddSubscriptionActivity::class.java).apply {
                    putExtra("uid", uid)
                    putExtra("username", username)
                    putExtra("familyCode", familyCode)
                    putExtra("role", role)
                })
            }
        }

        findViewById<View>(R.id.nav_stats)?.setOnClickListener {
            if (uid.isNotEmpty() && ::username.isInitialized && ::familyCode.isInitialized) {
                startActivity(Intent(this, StatsActivity::class.java).apply {
                    putExtra("uid", uid)
                    putExtra("username", username)
                    putExtra("familyCode", familyCode)
                    putExtra("role", role)
                })
            }
        }

        findViewById<View>(R.id.nav_settings)?.setOnClickListener {
            if (uid.isNotEmpty() && ::username.isInitialized && ::familyCode.isInitialized) {
                startActivity(Intent(this, SettingsActivity::class.java).apply {
                    putExtra("uid", uid)
                    putExtra("username", username)
                    putExtra("familyCode", familyCode)
                    putExtra("role", role)
                })
            }
        }
    }

    private fun setupTopButtons() {
        btnSortPrice.setOnClickListener { viewModel.toggleSortPrice() }
        btnSortDate.setOnClickListener { viewModel.toggleSortDate() }

        btnFilterUsers.setOnClickListener {
            val state = viewModel.state.value
            if (state.isAdmin) {
                val members = state.familyMembers
                if (members.isEmpty()) {
                    Toast.makeText(this, "Участники ещё не загружены", Toast.LENGTH_SHORT).show()
                    viewModel.refreshFamilyMembers()
                    return@setOnClickListener
                }
                filterDialogManager.show(members)
            } else {
                viewModel.requestFamilyInfo()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Проверяем, была ли изменена тема при возврате из настроек
        if (ThemeManager.wasThemeJustChanged(this)) {
            ThemeManager.consumeThemeChangedFlag(this)
            recreate()
        }
    }
}
