package com.example.subtracker

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class AddSubscriptionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_subscription)

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "subtrack-db"
        ).build()

        val subscriptionDao = db.subscriptionDao()

        val nameInput = findViewById<EditText>(R.id.editTextName)
        val priceInput = findViewById<EditText>(R.id.editTextPrice)
        val periodicitySpinner = findViewById<Spinner>(R.id.spinnerPeriodicity)
        val iconSpinner = findViewById<Spinner>(R.id.spinnerIcon)
        val buttonSave = findViewById<Button>(R.id.buttonSave)

        val familyCode = intent.getStringExtra("familyCode") ?: return
        val username = intent.getStringExtra("username") ?: "—"

        buttonSave.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val price = priceInput.text.toString().toDoubleOrNull() ?: 0.0
            val periodicity = periodicitySpinner.selectedItem.toString()
            val iconChoice = iconSpinner.selectedItem.toString()
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

            if (name.isEmpty()) {
                Toast.makeText(this, "Введите название подписки", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Вычисляем следующую дату платежа
            val nextPaymentDate = calculateNextPaymentDate(periodicity)

            lifecycleScope.launch(Dispatchers.IO) {
                val sub = SubscriptionEntity(
                    familyCode = familyCode,
                    name = name,
                    price = price,
                    periodicity = periodicity,
                    iconResName = iconName,
                    ownerUsername = username,
                    nextPaymentDate = nextPaymentDate   // <- добавлено
                )
                subscriptionDao.insert(sub)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddSubscriptionActivity, "Подписка добавлена", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    // Функция для вычисления следующей даты платежа
    private fun calculateNextPaymentDate(periodicity: String): Long {
        val calendar = Calendar.getInstance()
        when (periodicity) {
            "день" -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            "неделя" -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            "месяц" -> calendar.add(Calendar.MONTH, 1)
            "квартал" -> calendar.add(Calendar.MONTH, 3)
            "год" -> calendar.add(Calendar.YEAR, 1)
        }
        return calendar.timeInMillis
    }
}
