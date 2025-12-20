package com.example.subtracker

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.subtracker.presentation.stats.PaymentCardRenderer
import com.example.subtracker.presentation.stats.StatsViewModel
import kotlinx.coroutines.launch

class StatsActivity : AppCompatActivity() {

    private lateinit var historyContainer: LinearLayout
    private lateinit var mostExpensiveSub: TextView
    private lateinit var monthlyCost: TextView
    private lateinit var avgCost: TextView
    private lateinit var totalSubscriptions: TextView
    private lateinit var statsSummaryContainer: CardView

    private var familyCode: String = ""
    private var username: String = ""
    private var role: String = "member"
    private var uid: String = ""

    private lateinit var paymentCardRenderer: PaymentCardRenderer

    private val viewModel: StatsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("StatsActivity", "onCreate: started")
        setupLayout()
        
        if (!loadSessionData()) {
            Log.e("StatsActivity", "onCreate: loadSessionData failed")
            return
        }

        Log.d("StatsActivity", "onCreate: familyCode=$familyCode, uid=$uid, role=$role")
        
        try {
            initializeViews()
            setupNavigation()
            initializeRenderer()
            observeViewModel()
            
            Log.d("StatsActivity", "onCreate: calling viewModel.start")
            viewModel.start(familyCode, uid, role)
            Log.d("StatsActivity", "onCreate: viewModel.start completed")
        } catch (e: Exception) {
            Log.e("StatsActivity", "onCreate: error", e)
            throw e
        }
    }

    private fun setupLayout() {
        val isDark = ThemeManager.getMode(this) == ThemeManager.MODE_DARK
        setContentView(if (isDark) R.layout.activity_stats_dark else R.layout.activity_stats)
    }

    private fun loadSessionData(): Boolean {
        username = intent.getStringExtra("username").orEmpty().ifEmpty { SessionManager.username(this) }
        familyCode = intent.getStringExtra("familyCode").orEmpty().ifEmpty { SessionManager.familyCode(this) }
        role = (intent.getStringExtra("role") ?: SessionManager.role(this)).ifEmpty { "member" }
        uid = intent.getStringExtra("uid").orEmpty().ifEmpty { SessionManager.userDocId(this) }

        if (familyCode.isEmpty() || uid.isEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return false
        }
        return true
    }

    private fun initializeViews() {
        historyContainer = findViewById(R.id.historyContainer)
        mostExpensiveSub = findViewById(R.id.mostExpensiveSub)
        monthlyCost = findViewById(R.id.monthlyCost)
        avgCost = findViewById(R.id.avgCost)
        totalSubscriptions = findViewById(R.id.totalSubscriptions)
        statsSummaryContainer = findViewById(R.id.statsSummaryContainer)

        statsSummaryContainer.visibility = if (role == "admin") View.VISIBLE else View.GONE
    }

    private fun initializeRenderer() {
        paymentCardRenderer = PaymentCardRenderer(this, historyContainer)
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.nav_home)?.setOnClickListener {
            navigateToMainFrame()
        }

        findViewById<View>(R.id.nav_add)?.setOnClickListener {
            navigateToAddSubscription()
        }

        findViewById<View>(R.id.nav_stats)?.setOnClickListener { /* already here */ }

        findViewById<View>(R.id.nav_settings)?.setOnClickListener {
            navigateToSettings()
        }
    }

    private fun navigateToMainFrame() {
        startActivity(Intent(this, MainFrameActivity::class.java).apply {
            putExtra("username", username)
            putExtra("familyCode", familyCode)
            putExtra("role", role)
            putExtra("uid", uid)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        finish()
    }

    private fun navigateToAddSubscription() {
        startActivity(Intent(this, AddSubscriptionActivity::class.java).apply {
            putExtra("username", username)
            putExtra("familyCode", familyCode)
            putExtra("role", role)
            putExtra("uid", uid)
        })
    }

    private fun navigateToSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).apply {
            putExtra("username", username)
            putExtra("familyCode", familyCode)
            putExtra("role", role)
            putExtra("uid", uid)
        })
    }

    private fun observeViewModel() {
        Log.d("StatsActivity", "observeViewModel: setting up observer")
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    Log.d("StatsActivity", "observeViewModel: starting to collect state")
                    viewModel.state.collect { state ->
                        Log.d("StatsActivity", "observeViewModel: received state - payments=${state.payments.size}, admin=$role")
                        try {
                            paymentCardRenderer.render(state.payments)
                            Log.d("StatsActivity", "observeViewModel: rendered ${state.payments.size} payments")

                            if (role == "admin") {
                                Log.d("StatsActivity", "observeViewModel: updating admin stats")
                                mostExpensiveSub.text = state.mostExpensiveText
                                monthlyCost.text = state.monthlyCostText
                                avgCost.text = state.avgCostText
                                totalSubscriptions.text = state.totalSubscriptionsText
                                Log.d("StatsActivity", "observeViewModel: admin stats updated")
                            }
                        } catch (e: Exception) {
                            Log.e("StatsActivity", "observeViewModel: error rendering state", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("StatsActivity", "observeViewModel: error collecting state", e)
                }
            }
        }
    }
}
