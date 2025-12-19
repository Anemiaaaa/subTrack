package com.example.subtracker

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class StatsActivity : AppCompatActivity() {

    private lateinit var historyContainer: LinearLayout
    private lateinit var mostExpensiveSub: TextView
    private lateinit var monthlyCost: TextView
    private lateinit var avgCost: TextView
    private lateinit var totalSubscriptions: TextView
    private lateinit var statsSummaryContainer: LinearLayout

    private var familyCode: String = ""
    private var username: String = ""
    private var role: String = "member"
    private var uid: String = "" // users/{docId}

    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Выбор layout по твоему ThemeManager (только XML, без DayNight)
        val isDark = ThemeManager.getMode(this) == ThemeManager.MODE_DARK
        setContentView(if (isDark) R.layout.activity_stats_dark else R.layout.activity_stats)

        // extras -> fallback на Session
        username = intent.getStringExtra("username").orEmpty().ifEmpty { SessionManager.username(this) }
        familyCode = intent.getStringExtra("familyCode").orEmpty().ifEmpty { SessionManager.familyCode(this) }
        role = (intent.getStringExtra("role") ?: SessionManager.role(this)).ifEmpty { "member" }
        uid = intent.getStringExtra("uid").orEmpty().ifEmpty { SessionManager.userDocId(this) }

        if (familyCode.isEmpty() || uid.isEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        historyContainer = findViewById(R.id.historyContainer)
        mostExpensiveSub = findViewById(R.id.mostExpensiveSub)
        monthlyCost = findViewById(R.id.monthlyCost)
        avgCost = findViewById(R.id.avgCost)
        totalSubscriptions = findViewById(R.id.totalSubscriptions)
        statsSummaryContainer = findViewById(R.id.statsSummaryContainer)

        setupNavigation()

        // раньше ты показывал сводку только админу — оставляю логику
        statsSummaryContainer.visibility = if (role == "admin") View.VISIBLE else View.GONE

        loadPayments()

        // ✅ Новый расчёт — по подпискам (в пересчёте на месяц), а не по платежам
        if (role == "admin") {
            loadSubscriptionsAndCalculateMonthly()
        }
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.nav_home).setOnClickListener {
            startActivity(Intent(this, MainFrameActivity::class.java).apply {
                putExtra("username", username)
                putExtra("familyCode", familyCode)
                putExtra("role", role)
                putExtra("uid", uid)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            finish()
        }

        findViewById<View>(R.id.nav_add).setOnClickListener {
            startActivity(Intent(this, AddSubscriptionActivity::class.java).apply {
                putExtra("username", username)
                putExtra("familyCode", familyCode)
                putExtra("role", role)
                putExtra("uid", uid)
            })
        }

        findViewById<View>(R.id.nav_stats).setOnClickListener {
            // already here
        }

        findViewById<View>(R.id.nav_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java).apply {
                putExtra("username", username)
                putExtra("familyCode", familyCode)
                putExtra("role", role)
                putExtra("uid", uid)
            })
        }
    }

    private fun loadPayments() {
        var query = db.collection("payments")
            .whereEqualTo("familyCode", familyCode)

        // фильтр для member по ownerUid (docId), а не по ownerUsername
        if (role != "admin") {
            query = query.whereEqualTo("ownerUid", uid)
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

            payments.forEach { historyContainer.addView(createPaymentCard(it)) }

            // ⚠️ больше не считаем “самую дорогую” по payments — это некорректно
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
            text = "💰 ${formatMoney(payment.amount)} ₽"
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

    // ===================== NEW: Monthly-equivalent stats =====================

    private fun loadSubscriptionsAndCalculateMonthly() {
        var query = db.collection("subscriptions")
            .whereEqualTo("familyCode", familyCode)

        // если когда-нибудь захочешь показывать сводку member — раскомментируй:
        // if (role != "admin") query = query.whereEqualTo("ownerUid", uid)

        query.get().addOnSuccessListener { snapshot ->
            if (snapshot.isEmpty) {
                mostExpensiveSub.text = "💎 Самая дорогая: —"
                monthlyCost.text = "📅 Стоимость за месяц: —"
                avgCost.text = "📊 Средняя подписка: —"
                totalSubscriptions.text = "📦 Количество подписок: 0"
                return@addOnSuccessListener
            }

            val subs = snapshot.documents.mapNotNull { doc ->
                doc.toObject(FirebaseSubscription::class.java)?.copy(id = doc.id)
            }

            val monthlyPairs = subs.map { sub ->
                val perMonth = estimateMonthlyCost(sub.price, sub.periodicity)
                sub to perMonth
            }

            val (maxSub, maxMonthly) = monthlyPairs.maxByOrNull { it.second } ?: return@addOnSuccessListener
            val totalMonthly = monthlyPairs.sumOf { it.second }
            val avgMonthly = if (monthlyPairs.isNotEmpty()) totalMonthly / monthlyPairs.size else 0.0

            // “красивее” — показываем ₽/мес и владельца
            mostExpensiveSub.text =
                "💎 Самая дорогая: ${maxSub.name} • ${formatMoney(maxMonthly)} ₽/мес"

            monthlyCost.text = "📅 Стоимость за месяц: ${formatMoney(totalMonthly)} ₽"
            avgCost.text = "📊 Средняя подписка: ${formatMoney(avgMonthly)} ₽/мес"
            totalSubscriptions.text = "📦 Количество подписок: ${subs.size}"
        }
    }

    private fun estimateMonthlyCost(price: Double, periodicity: String): Double {
        val p = periodicity.trim().lowercase(Locale.getDefault())

        // коэффициенты — “оценка за месяц”
        return when {
            p.contains("день") -> price * 30.0
            p.contains("нед") -> price * 4.345 // среднее недель в месяце
            p.contains("кварт") -> price / 3.0
            p.contains("год") -> price / 12.0
            // "месяц" и всё неизвестное считаем как "в месяц"
            else -> price
        }
    }

    private fun formatMoney(v: Double): String {
        // без копеек, если целое
        val rounded = (v * 100.0).roundToInt() / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else String.format(Locale.getDefault(), "%.2f", rounded)
    }
}
