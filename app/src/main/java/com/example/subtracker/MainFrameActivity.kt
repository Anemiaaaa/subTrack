package com.example.subtracker

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
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

class MainFrameActivity : AppCompatActivity() {

    private lateinit var subscriptionsContainer: LinearLayout
    private lateinit var textUsername: TextView
    private lateinit var familyCode: String
    private lateinit var username: String

    // ---- ФЛАГИ ФИЛЬТРОВ/СОРТИРОВОК ----
    private var sortByPriceAsc = true
    private var sortByDateAsc = true
    private var filterByUserEnabled = false
    private var filterUsername: String? = null

    // Фильтр-кнопки
    private lateinit var btnSortPrice: TextView
    private lateinit var btnSortDate: TextView
    private lateinit var btnFilterUsers: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_frame)

        subscriptionsContainer = findViewById(R.id.subscriptionsContainer)
        textUsername = findViewById(R.id.textUsername)

        btnSortPrice = findViewById(R.id.btnSortPrice)
        btnSortDate = findViewById(R.id.btnSortDate)
        btnFilterUsers = findViewById(R.id.btnFilterUsers)

        familyCode = intent.getStringExtra("familyCode") ?: return
        username = intent.getStringExtra("username") ?: "—"
        textUsername.text = "Привет, $username"

        // Кнопка "добавить подписку"
        findViewById<ImageButton>(R.id.nav_add).setOnClickListener {
            val intent = Intent(this, AddSubscriptionActivity::class.java)
            intent.putExtra("familyCode", familyCode)
            intent.putExtra("username", username)
            startActivity(intent)
        }

        // Информация о семье
        findViewById<ImageButton>(R.id.btnFamilyInfo).setOnClickListener {
            showFamilyInfoDialog()
        }

        setupFilterButtons()
        loadSubscriptions()
    }

    override fun onResume() {
        super.onResume()
        loadSubscriptions()
    }

    // ------------------------- ЛОГИКА ФИЛЬТРОВ -------------------------
    private fun setupFilterButtons() {
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "subtrack-db"
        ).build()

        lifecycleScope.launch(Dispatchers.IO) {
            val userDao = db.userDao()
            val currentUser = userDao.getUserByUsername(username)
            val familyMembers = userDao.getUsersByFamilyCode(familyCode)

            withContext(Dispatchers.Main) {
                // Только глава семьи видит кнопку "Семья"
                btnFilterUsers.visibility =
                    if (currentUser?.isAdmin == true) View.VISIBLE else View.GONE

                // ---- Цена ----
                btnSortPrice.setOnClickListener {
                    sortByPriceAsc = !sortByPriceAsc
                    updateButtonsUI()
                    loadSubscriptions()
                }

                // ---- Дата ----
                btnSortDate.setOnClickListener {
                    sortByDateAsc = !sortByDateAsc
                    updateButtonsUI()
                    loadSubscriptions()
                }

                // ---- Пользователи (диалог выбора) ----
                btnFilterUsers.setOnClickListener {
                    if (familyMembers.isEmpty()) return@setOnClickListener

                    val names = familyMembers.map { it.username }.toTypedArray()
                    var selectedIndex = names.indexOf(filterUsername)

                    AlertDialog.Builder(this@MainFrameActivity)
                        .setTitle("Выберите пользователя")
                        .setSingleChoiceItems(names, selectedIndex) { _, which ->
                            selectedIndex = which
                        }
                        .setPositiveButton("Ок") { dialog, _ ->
                            filterByUserEnabled = selectedIndex >= 0
                            filterUsername = if (filterByUserEnabled) names[selectedIndex] else null
                            updateButtonsUI()
                            loadSubscriptions()
                            dialog.dismiss()
                        }
                        .setNegativeButton("Сброс") { dialog, _ ->
                            filterByUserEnabled = false
                            filterUsername = null
                            updateButtonsUI()
                            loadSubscriptions()
                            dialog.dismiss()
                        }
                        .show()
                }
            }
        }
    }

    // Подсветка кнопок
    private fun updateButtonsUI() {
        btnSortPrice.apply {
            setBackgroundResource(if (sortByPriceAsc) R.drawable.chip_bg_active else R.drawable.chip_bg)
            setTextColor(if (sortByPriceAsc) Color.WHITE else Color.BLACK)
        }

        btnSortDate.apply {
            setBackgroundResource(if (sortByDateAsc) R.drawable.chip_bg_active else R.drawable.chip_bg)
            setTextColor(if (sortByDateAsc) Color.WHITE else Color.BLACK)
        }

        btnFilterUsers.apply {
            setBackgroundResource(if (filterByUserEnabled) R.drawable.chip_bg_active else R.drawable.chip_bg)
            setTextColor(if (filterByUserEnabled) Color.WHITE else Color.BLACK)
        }
    }

    // ---------------------- ЗАГРУЗКА ПОДПИСОК -------------------------
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
            val allSubscriptions = subscriptionDao.getByFamily(familyCode)
            val isAdmin = currentUser?.isAdmin == true

            // ---- ФИЛЬТР ----
            val filtered = allSubscriptions.filter { sub ->
                when {
                    isAdmin -> {
                        if (filterByUserEnabled && filterUsername != null)
                            sub.ownerUsername == filterUsername
                        else
                            true
                    }
                    else -> sub.ownerUsername == username
                }
            }

            // ---- СОРТИРОВКА ----
            val sorted = filtered.sortedWith(compareBy(
                { if (sortByPriceAsc) it.price else -it.price },
                { if (sortByDateAsc) it.nextPaymentDate else -it.nextPaymentDate }
            ))

            withContext(Dispatchers.Main) {
                subscriptionsContainer.removeAllViews()
                if (sorted.isEmpty()) {
                    val noSubs = TextView(this@MainFrameActivity)
                    noSubs.text = "Подписок нет"
                    noSubs.gravity = Gravity.CENTER
                    noSubs.textSize = 18f
                    noSubs.setTextColor(ContextCompat.getColor(this@MainFrameActivity, android.R.color.black))
                    subscriptionsContainer.addView(noSubs)
                } else {
                    sorted.forEach { sub ->
                        subscriptionsContainer.addView(createSubscriptionCard(sub, username))
                    }
                }
            }
        }
    }

    // -------------------- КАРТОЧКА ПОДПИСКИ --------------------
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
        ownerText.text =
            if (sub.ownerUsername == username) "Для: вы" else "Для: ${sub.ownerUsername}"

        val sdf = SimpleDateFormat("dd.MM", Locale.getDefault())
        dateText.text = sdf.format(Date(sub.nextPaymentDate))
        priceText.text = "${sub.price}₽"

        return card
    }

    // -------------------- ОКНО СЕМЬИ --------------------
    private fun showFamilyInfoDialog() {
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "subtrack-db"
        ).build()
        val userDao = db.userDao()

        lifecycleScope.launch(Dispatchers.IO) {
            val familyMembers = userDao.getUsersByFamilyCode(familyCode)
            val currentUser = userDao.getUserByUsername(username)
            val familyNameValue = familyMembers.firstOrNull()?.familyName ?: "—"

            withContext(Dispatchers.Main) {
                val dialogView = layoutInflater.inflate(R.layout.family_info_dialog, null)
                val familyNameText = dialogView.findViewById<TextView>(R.id.familyName)
                val familyCodeText = dialogView.findViewById<TextView>(R.id.familyCode)
                val membersContainer = dialogView.findViewById<LinearLayout>(R.id.familyMembersContainer)
                val roleText = dialogView.findViewById<TextView>(R.id.familyRole)
                val closeBtn = dialogView.findViewById<Button>(R.id.btnCloseDialog)
                val leaveBtn = dialogView.findViewById<Button>(R.id.leaveFamilyButton)

                familyNameText.text = "Семья: $familyNameValue"
                familyCodeText.text = "Код семьи: $familyCode"

                familyCodeText.setOnClickListener {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, familyCode)
                        type = "text/plain"
                    }
                    startActivity(Intent.createChooser(shareIntent, "Поделиться кодом семьи"))
                }

                roleText.text = "Роль: ${if (currentUser?.isAdmin == true) "глава" else "участник"}"

                membersContainer.removeAllViews()
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
                        currentUser?.let { user ->
                            if (user.isAdmin) {
                                val others = familyMembers.filter { it.username != user.username }
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
