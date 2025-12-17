package com.example.subtracker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StatsActivity : AppCompatActivity() {

    private lateinit var statsContainer: LinearLayout
    private lateinit var familyTotalText: TextView
    private lateinit var mySubscriptionsContainer: LinearLayout
    private lateinit var topSubscriptionsContainer: LinearLayout

    private lateinit var username: String
    private lateinit var familyCode: String
    private lateinit var role: String

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        statsContainer = findViewById(R.id.statsContainer)
        familyTotalText = findViewById(R.id.familyTotalText)
        mySubscriptionsContainer = findViewById(R.id.mySubscriptionsContainer)
        topSubscriptionsContainer = findViewById(R.id.topSubscriptionsContainer)

        username = intent.getStringExtra("username") ?: ""
        familyCode = intent.getStringExtra("familyCode") ?: ""
        role = intent.getStringExtra("role") ?: "member"

        loadStats()
        setupBottomNav()
    }

    private fun loadStats() {
        db.collection("subscriptions")
            .whereEqualTo("familyCode", familyCode)
            .get()
            .addOnSuccessListener { snapshot ->
                val subs = snapshot.documents.mapNotNull { it.toObject(FirebaseSubscription::class.java) }

                statsContainer.removeAllViews()
                mySubscriptionsContainer.removeAllViews()
                topSubscriptionsContainer.removeAllViews()

                if (role == "admin") {
                    // Общие расходы семьи
                    val total = subs.sumOf { it.price }
                    familyTotalText.text = "Общие расходы семьи: $total ₽"

                    // Расходы по членам семьи
                    val members = subs.groupBy { it.ownerUsername }
                    members.forEach { (member, memberSubs) ->
                        val totalMember = memberSubs.sumOf { it.price }
                        val tv = TextView(this).apply {
                            text = "$member: $totalMember ₽"
                            textSize = 16f
                            setPadding(16, 4, 0, 4)
                        }
                        statsContainer.addView(tv)
                    }
                } else {
                    familyTotalText.text = "Мои расходы"
                }

                // Личные подписки
                val mySubs = subs.filter { it.ownerUsername == username }
                mySubs.forEach { sub ->
                    val tv = TextView(this).apply {
                        text = "${sub.name}: ${sub.price} ₽"
                        textSize = 16f
                        setPadding(16, 4, 0, 4)
                    }
                    mySubscriptionsContainer.addView(tv)
                }

                // Топ дорогих подписок
                val topSubs = mySubs.sortedByDescending { it.price }.take(3)
                topSubs.forEach { sub ->
                    val tv = TextView(this).apply {
                        text = "${sub.name}: ${sub.price} ₽"
                        textSize = 16f
                        setPadding(16, 4, 0, 4)
                    }
                    topSubscriptionsContainer.addView(tv)
                }
            }
    }

    private fun setupBottomNav() {
        findViewById<ImageButton>(R.id.nav_home).setOnClickListener {
            val intent = Intent(this, MainFrameActivity::class.java)
            intent.putExtra("username", username)
            intent.putExtra("familyCode", familyCode)
            startActivity(intent)
            finish()
        }

        findViewById<ImageButton>(R.id.nav_calendar).setOnClickListener {
            Toast.makeText(this, "Календарь пока не реализован", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageView>(R.id.nav_stats).setOnClickListener {
            // Уже на этом экране, можно показать Toast
            Toast.makeText(this, "Вы на статистике", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.nav_add).setOnClickListener {
            val intent = Intent(this, AddSubscriptionActivity::class.java)
            intent.putExtra("username", username)
            intent.putExtra("familyCode", familyCode)
            startActivity(intent)
        }
    }
}
