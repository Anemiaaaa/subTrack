package com.example.subtracker

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "subtrack-db"
        )
            .fallbackToDestructiveMigration()
            .build()

        val userDao = db.userDao()

        val usernameInput = findViewById<EditText>(R.id.editTextLogin2)
        val familyNameInput = findViewById<EditText>(R.id.editTextPassword)
        val buttonCreate = findViewById<Button>(R.id.buttonLogin)
        val backButton = findViewById<Button>(R.id.buttonBack)

        // Кнопка "Создать семью"
        buttonCreate.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val familyName = familyNameInput.text.toString().trim()

            if (username.isEmpty() || familyName.isEmpty()) {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val familyCode = generateFamilyCode() // уникальный код семьи

            lifecycleScope.launch(Dispatchers.IO) {
                val existingUser = userDao.getUserByUsername(username)
                if (existingUser != null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RegisterActivity, "Пользователь уже существует", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Создаём нового пользователя и делаем его главой семьи
                val newUser = UserEntity(
                    username = username,
                    familyName = familyName,
                    familyCode = familyCode,
                    isAdmin = true
                )
                userDao.insertUser(newUser)

                withContext(Dispatchers.Main) {
                    // Показываем код семьи в диалоге
                    val dialog = AlertDialog.Builder(this@RegisterActivity)
                        .setTitle("Семья создана")
                        .setMessage("Код вашей семьи: $familyCode\n(нажмите, чтобы скопировать)")
                        .setPositiveButton("ОК") { d, _ ->
                            d.dismiss()
                            finish()
                        }
                        .create()

                    dialog.setOnShowListener {
                        val messageView = dialog.findViewById<TextView>(android.R.id.message)
                        messageView?.setOnClickListener {
                            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Код семьи", familyCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(this@RegisterActivity, "Код скопирован", Toast.LENGTH_SHORT).show()
                        }
                    }

                    dialog.show()
                }
            }
        }

        // Кнопка "Назад"
        backButton.setOnClickListener { finish() }
    }

    // Генерация уникального кода семьи
    private fun generateFamilyCode(): String {
        val charset = ('A'..'Z') + ('0'..'9')
        return (1..6).map { charset.random() }.joinToString("")
    }
}
