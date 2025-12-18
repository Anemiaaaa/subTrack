package com.example.subtracker

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class StatsActivity : AppCompatActivity() {

    private lateinit var historyContainer: LinearLayout
    private lateinit var mostExpensiveSub: TextView
    private lateinit var monthlyCost: TextView
    private lateinit var avgCost: TextView
    private lateinit var totalSubscriptions: TextView
    private lateinit var statsSummaryContainer: LinearLayout

    private lateinit var familyCode: String
    private lateinit var username: String
    private lateinit var role: String

    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        // ===== INTENT =====
        username = intent.getStringExtra("username") ?: return
        familyCode = intent.getStringExtra("familyCode") ?: return
        role = intent.getStringExtra("role") ?: "member"

        // ===== UI =====
        historyContainer = findViewById(R.id.historyContainer)
        mostExpensiveSub = findViewById(R.id.mostExpensiveSub)
        monthlyCost = findViewById(R.id.monthlyCost)
        avgCost = findViewById(R.id.avgCost)
        totalSubscriptions = findViewById(R.id.totalSubscriptions)
        statsSummaryContainer = findViewById(R.id.statsSummaryContainer)

        // ===== NAVIGATION =====
        setupNavigation()

        // ===== VISIBILITY =====
        statsSummaryContainer.visibility =
            if (role == "admin") View.VISIBLE else View.GONE

        loadPayments()
    }

    // ================= NAVIGATION =================
    private fun setupNavigation() {

        findViewById<ImageButton>(R.id.nav_home).setOnClickListener {
            startActivity(Intent(this, MainFrameActivity::class.java).apply {
                putExtra("username", username)
                putExtra("familyCode", familyCode)
            })
            finish()
        }

        findViewById<ImageButton>(R.id.nav_add).setOnClickListener {
            startActivity(Intent(this, AddSubscriptionActivity::class.java).apply {
                putExtra("username", username)
                putExtra("familyCode", familyCode)
            })
        }

        findViewById<ImageView>(R.id.nav_stats).setOnClickListener {
            // уже тут
        }

        findViewById<ImageButton>(R.id.nav_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java).apply {
                putExtra("username", username)
                putExtra("familyCode", familyCode)
            })
        }
    }


    // ================= LOAD PAYMENTS =================
    private fun loadPayments() {
        var query = db.collection("payments")
            .whereEqualTo("familyCode", familyCode)

        if (role != "admin") {
            query = query.whereEqualTo("ownerUsername", username)
        }

        query.get().addOnSuccessListener { snapshot ->
            historyContainer.removeAllViews()

            if (snapshot.isEmpty) {
                historyContainer.addView(TextView(this).apply {
                    text = "История оплат пуста"
                    textSize = 18f
                    gravity = Gravity.CENTER
                })
                return@addOnSuccessListener
            }

            val payments = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Payment::class.java)?.copy(id = doc.id)
            }.sortedByDescending { it.paidAt }

            payments.forEach {
                historyContainer.addView(createPaymentCard(it))
            }

            if (role == "admin") {
                calculateSummary(payments)
            }
        }
    }


    private fun createPaymentCard(payment: Payment): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            background = getDrawable(R.drawable.sub_card_bg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }

        card.addView(TextView(this).apply {
            text = payment.subscriptionName
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        card.addView(TextView(this).apply {
            text = "💰 ${payment.amount} ₽"
            textSize = 15f
        })

        card.addView(TextView(this).apply {
            text = "📅 ${dateFormat.format(Date(payment.paidAt))}"
            textSize = 14f
        })

        card.addView(TextView(this).apply {
            text = "👤 ${payment.ownerUsername}"
            textSize = 14f
        })

        return card
    }


    // ================= SUMMARY =================
    private fun calculateSummary(payments: List<Payment>) {
        if (payments.isEmpty()) return

        val nonNullPayments = payments.filter { it.amount != null }

        if (nonNullPayments.isEmpty()) return

        val mostExpensive = nonNullPayments.maxByOrNull { it.amount!! } ?: return

        mostExpensiveSub.text =
            "💎 Самая дорогая: ${mostExpensive.subscriptionName}"

        avgCost.text =
            "📊 Средняя оплата: ${"%.2f".format(nonNullPayments.map { it.amount!! }.average())} ₽"

        totalSubscriptions.text =
            "📦 Всего оплат: ${nonNullPayments.size}"

        monthlyCost.text =
            "📅 Максимальный платёж: ${mostExpensive.amount} ₽"
    }


}
