package com.example.subtracker

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.text.format

class MainFrameActivity : AppCompatActivity() {

    private lateinit var subscriptionsContainer: LinearLayout
    private lateinit var textUsername: TextView
    private lateinit var familyCode: String
    private lateinit var username: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_frame)

        subscriptionsContainer = findViewById(R.id.subscriptionsContainer)
        textUsername = findViewById(R.id.textUsername)

        familyCode = intent.getStringExtra("familyCode") ?: return
        username = intent.getStringExtra("username") ?: "—"
        textUsername.text = "Привет, $username"

        // Кнопка "плюс" — добавление подписки
        val navAdd = findViewById<ImageButton>(R.id.nav_add)
        navAdd.setOnClickListener {
            val intent = Intent(this, AddSubscriptionActivity::class.java)
            intent.putExtra("familyCode", familyCode)
            intent.putExtra("username", username)
            startActivity(intent)
        }

        // Кнопка "информация о семье"
        val btnFamilyInfo = findViewById<ImageButton>(R.id.btnFamilyInfo)
        btnFamilyInfo.setOnClickListener {
            showFamilyInfoDialog()
        }

        loadSubscriptions()
    }

    override fun onResume() {
        super.onResume()
        loadSubscriptions() // обновляем список после возврата
    }

    // kotlin
    private fun loadSubscriptions() {
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "subtrack-db"
        ).build()
        val subscriptionDao = db.subscriptionDao()
        val userDao = db.userDao()

        lifecycleScope.launch(Dispatchers.IO) {
            val currentUser = userDao.getUserByUsername(username)
            val subscriptions = subscriptionDao.getByFamily(familyCode)
            // Если не админ — показываем только подписки, где владелец совпадает с username
            val visibleSubscriptions = if (currentUser?.isAdmin == true) {
                subscriptions
            } else {
                subscriptions.filter { it.ownerUsername == username }
            }

            withContext(Dispatchers.Main) {
                subscriptionsContainer.removeAllViews()
                if (visibleSubscriptions.isEmpty()) {
                    val noSubsText = TextView(this@MainFrameActivity)
                    noSubsText.text = "Подписок нет"
                    noSubsText.gravity = Gravity.CENTER
                    noSubsText.textSize = 18f
                    noSubsText.setTextColor(ContextCompat.getColor(this@MainFrameActivity, android.R.color.black))
                    subscriptionsContainer.addView(noSubsText)
                } else {
                    visibleSubscriptions.forEach { sub ->
                        subscriptionsContainer.addView(createSubscriptionCard(sub, username))
                    }
                }
            }
        }
    }


    // kotlin
    private fun createSubscriptionCard(sub: SubscriptionEntity, username: String): View {
        val card = layoutInflater.inflate(R.layout.sub_card_item, subscriptionsContainer, false)

        val iconImage = card.findViewById<ImageView>(R.id.iconImage)
        val nameText = card.findViewById<TextView>(R.id.nameText)
        val ownerText = card.findViewById<TextView>(R.id.ownerText)
        val dateText = card.findViewById<TextView>(R.id.dateText)
        val priceText = card.findViewById<TextView>(R.id.priceText)

        val iconId = resources.getIdentifier(sub.iconResName, "drawable", packageName)
        iconImage.setImageResource(if (iconId != 0) iconId else R.drawable.ic_default)

        nameText.text = sub.name
        // Показываем реального владельца подписки; если это текущий пользователь — "вы"
        val ownerDisplay = if (sub.ownerUsername == username) "вы" else sub.ownerUsername
        ownerText.text = "Для: $ownerDisplay"

        val sdf = SimpleDateFormat("dd.MM", Locale.getDefault())
        dateText.text = sdf.format(Date(sub.nextPaymentDate))
        priceText.text = "${sub.price}₽"

        return card
    }


    // ---------- Новый метод ----------
    private fun showFamilyInfoDialog() {
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "subtrack-db"
        ).build()
        val userDao = db.userDao()

        lifecycleScope.launch(Dispatchers.IO) {
            val familyMembers = userDao.getUsersByFamilyCode(familyCode)
            val currentUser = userDao.getUserByUsername(username) // текущий пользователь

            // Берём название семьи из любого пользователя
            val familyNameValue = familyMembers.firstOrNull()?.familyName ?: "—"

            withContext(Dispatchers.Main) {
                val dialogView = layoutInflater.inflate(R.layout.family_info_dialog, null)
                val familyNameText = dialogView.findViewById<TextView>(R.id.familyName)
                val familyCodeText = dialogView.findViewById<TextView>(R.id.familyCode)
                val membersContainer = dialogView.findViewById<LinearLayout>(R.id.familyMembersContainer)
                val roleText = dialogView.findViewById<TextView>(R.id.familyRole)
                val closeBtn = dialogView.findViewById<Button>(R.id.btnCloseDialog)
                val leaveBtn = dialogView.findViewById<Button>(R.id.leaveFamilyButton)

                // Заполняем название и код семьи
                familyNameText.text = "Семья: $familyNameValue"
                familyCodeText.text = "Код семьи: $familyCode"

                // При нажатии на TextView с кодом семьи
                familyCodeText.setOnClickListener {
                    // Берём только сам код, без надписи "(поделиться)"
                    val codeOnly = familyCode // это уже ваш код семьи, переданный в intent

                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, codeOnly)
                        type = "text/plain"
                    }
                    startActivity(Intent.createChooser(shareIntent, "Поделиться кодом семьи"))
                }


                roleText.text = "Роль: ${if (currentUser?.isAdmin == true) "глава" else "участник"}"

                membersContainer.removeAllViews()
                if (familyMembers.isEmpty()) {
                    val noMembers = TextView(this@MainFrameActivity)
                    noMembers.text = "Пока никто не присоединился"
                    noMembers.textSize = 18f
                    noMembers.gravity = Gravity.CENTER
                    membersContainer.addView(noMembers)
                } else {
                    familyMembers.forEach { member ->
                        val itemLayout = LinearLayout(this@MainFrameActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(0, 8, 0, 8)
                        }

                        val avatar = TextView(this@MainFrameActivity).apply {
                            text = member.username.first().uppercaseChar().toString()
                            textSize = 18f
                            gravity = Gravity.CENTER
                            setTextColor(ContextCompat.getColor(this@MainFrameActivity, android.R.color.white))
                            background = ContextCompat.getDrawable(this@MainFrameActivity, R.drawable.circle_bg)
                            setPadding(20, 10, 20, 10)
                        }

                        val nameText = TextView(this@MainFrameActivity).apply {
                            text = member.username
                            textSize = 18f
                            setPadding(16, 0, 0, 0)
                        }

                        itemLayout.addView(avatar)
                        itemLayout.addView(nameText)
                        membersContainer.addView(itemLayout)
                    }
                }

                val dialog = AlertDialog.Builder(this@MainFrameActivity)
                    .setView(dialogView)
                    .create()

                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                dialog.show()
                dialog.window?.setLayout(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

                closeBtn.setOnClickListener { dialog.dismiss() }

                leaveBtn.setOnClickListener {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val membersList = userDao.getUsersByFamilyCode(familyCode).toMutableList()

                        currentUser?.let { user ->
                            if (user.isAdmin) {
                                val others = membersList.filter { it.username != user.username }
                                if (others.isNotEmpty()) {
                                    val newHead = others.random()
                                    userDao.updateUser(newHead.copy(isAdmin = true))
                                }
                            }
                            userDao.deleteUser(user)
                        }

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainFrameActivity, "Вы покинули семью", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@MainFrameActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                    }
                }
            }
        }
    }

}
// 0U3EKX