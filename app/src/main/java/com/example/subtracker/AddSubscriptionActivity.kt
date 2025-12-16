package com.example.subtracker

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar


class AddSubscriptionActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_subscription)

        // 🔹 Инициализация Firebase
        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val nameInput = findViewById<EditText>(R.id.editTextName)
        val priceInput = findViewById<EditText>(R.id.editTextPrice)
        val periodicitySpinner = findViewById<Spinner>(R.id.spinnerPeriodicity)
        val iconSpinner = findViewById<Spinner>(R.id.spinnerIcon)
        val buttonSave = findViewById<Button>(R.id.buttonSave)

        // 🔥 нижняя навигация
        val navHome = findViewById<ImageButton>(R.id.nav_home)
        val navCalendar = findViewById<ImageButton>(R.id.nav_calendar)
        val navAdd = findViewById<ImageButton>(R.id.nav_add)
        val navStats = findViewById<ImageView>(R.id.nav_stats)

        val familyCode = intent.getStringExtra("familyCode")
        val username = intent.getStringExtra("username") ?: "—"
        val currentUser = auth.currentUser

        if (familyCode == null || currentUser == null) {
            Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        navHome.setOnClickListener {
            finish()
        }

        buttonSave.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val price = priceInput.text.toString().toDoubleOrNull() ?: 0.0
            val periodicity = periodicitySpinner.selectedItem.toString()
            val iconChoice = iconSpinner.selectedItem.toString()

            if (name.isEmpty()) {
                Toast.makeText(this, "Введите название подписки", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val iconName = when (iconChoice) {
                "Netflix" -> "netflix"
                "YouTube" -> "youtube"
                "Spotify" -> "spotify"
                "Google One" -> "google_one"
                "Amazon Prime" -> "amazon_prime"
                "Disney+" -> "disney"
                "VK Music" -> "vk_music"
                else -> "ic_default"
            }

            val nextPaymentDate = calculateNextPaymentDate(periodicity)

            val data = hashMapOf(
                "familyCode" to familyCode,
                "name" to name,
                "price" to price,
                "periodicity" to periodicity,
                "iconResName" to iconName,
                "ownerUid" to currentUser.uid,
                "ownerUsername" to username,
                "nextPaymentDate" to nextPaymentDate,
                "createdAt" to FieldValue.serverTimestamp()
            )

            db.collection("subscriptions")
                .add(data)
                .addOnSuccessListener {
                    Toast.makeText(this, "Подписка добавлена", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun calculateNextPaymentDate(periodicity: String): Long {
        val calendar = Calendar.getInstance()
        when (periodicity.lowercase()) {
            "день" -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            "неделя" -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            "месяц" -> calendar.add(Calendar.MONTH, 1)
            "квартал" -> calendar.add(Calendar.MONTH, 3)
            "год" -> calendar.add(Calendar.YEAR, 1)
        }
        return calendar.timeInMillis
    }
}
