package com.example.subtracker

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "subtrack-db"
        )
            .fallbackToDestructiveMigration()
            .build()

        val userDao = db.userDao()

        val usernameInput = findViewById<EditText>(R.id.editTextLogin)
        val familyCodeInput = findViewById<EditText>(R.id.editTextPassword)
        val loginButton = findViewById<Button>(R.id.buttonLogin)
        val buttonCreateFamily = findViewById<Button>(R.id.buttonCreateFamily)

        // Переход на регистрацию новой семьи
        buttonCreateFamily.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        loginButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val familyCode = familyCodeInput.text.toString().trim()

            if (username.isEmpty() || familyCode.isEmpty()) {
                Toast.makeText(this, "Введите имя и код семьи", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val user = userDao.getUserByUsername(username)

                if (user != null) {
                    // Пользователь уже существует
                    if (user.familyCode != familyCode) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                "Пользователь $username уже находится в другой семье",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return@launch
                    }
                } else {
                    // Новый пользователь, проверяем существует ли семья
                    val familyMembers = userDao.getUsersByFamilyCode(familyCode)

                    if (familyMembers.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                "Семья с таким кодом не найдена",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }

                    // Автоматическая регистрация нового пользователя
                    val newUser = UserEntity(
                        username = username,
                        familyName = familyMembers.first().familyName,
                        familyCode = familyCode,
                        isAdmin = false
                    )
                    userDao.insertUser(newUser)
                }

                // Переход на главный экран
                withContext(Dispatchers.Main) {
                    val intent = Intent(this@MainActivity, MainFrameActivity::class.java)
                    intent.putExtra("username", username)
                    intent.putExtra("familyCode", familyCode)
                    startActivity(intent)
                    finish()
                }
            }
        }

    }
}
