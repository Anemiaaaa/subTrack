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

    // ---- Сортировка / фильтры ----
    private enum class SortMode { NONE, PRICE, DATE }
    private var sortMode = SortMode.NONE
    private var sortAsc = true

    private var filterByUserEnabled = false
    private var filterUsername: String? = null

    // Фильтр-кнопки (в layout должны быть TextView / Button с такими id)
    private lateinit var btnSortPrice: TextView
    private lateinit var btnSortDate: TextView
    private lateinit var btnFilterUsers: TextView

    // Формат даты: показываем день, месяц и год
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

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

                // ----- Сортировка (вариант C) -----
                btnSortPrice.setOnClickListener {
                    if (sortMode == SortMode.PRICE) {
                        sortAsc = !sortAsc
                    } else {
                        sortMode = SortMode.PRICE
                        sortAsc = true
                    }
                    updateButtonsUI()
                    loadSubscriptions()
                }

                btnSortDate.setOnClickListener {
                    if (sortMode == SortMode.DATE) {
                        sortAsc = !sortAsc
                    } else {
                        sortMode = SortMode.DATE
                        sortAsc = true
                    }
                    updateButtonsUI()
                    loadSubscriptions()
                }

                // Пользователи (диалог выбора)
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

    private fun updateButtonsUI() {
        btnSortPrice.apply {
            val active = sortMode == SortMode.PRICE
            setBackgroundResource(if (active) R.drawable.chip_bg_active else R.drawable.chip_bg)
            setTextColor(if (active) Color.WHITE else Color.BLACK)
            text = "Цена" + if (active) (if (sortAsc) " ↑" else " ↓") else ""
        }

        btnSortDate.apply {
            val active = sortMode == SortMode.DATE
            setBackgroundResource(if (active) R.drawable.chip_bg_active else R.drawable.chip_bg)
            setTextColor(if (active) Color.WHITE else Color.BLACK)
            text = "Дата" + if (active) (if (sortAsc) " ↑" else " ↓") else ""
        }

        btnFilterUsers.apply {
            setBackgroundResource(if (filterByUserEnabled) R.drawable.chip_bg_active else R.drawable.chip_bg)
            setTextColor(if (filterByUserEnabled) Color.WHITE else Color.BLACK)
            text = if (filterByUserEnabled) "Семья (фильтр ON)" else "Семья"
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

            // ---- СОРТИРОВКА (вариант C: только одна активна) ----
            val sorted = when (sortMode) {
                SortMode.PRICE -> if (sortAsc) filtered.sortedBy { it.price } else filtered.sortedByDescending { it.price }
                SortMode.DATE -> if (sortAsc) filtered.sortedBy { it.nextPaymentDate } else filtered.sortedByDescending { it.nextPaymentDate }
                SortMode.NONE -> filtered
            }

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
        ownerText.text = if (sub.ownerUsername == username) "Для: вы" else "Для: ${sub.ownerUsername}"

        // Показываем именно дату следующего платежа в формате dd.MM.yyyy
        dateText.text = dateFormat.format(Date(sub.nextPaymentDate))
        priceText.text = "${sub.price}₽"

        // Клик на карточке — показываем меню действий
        card.setOnClickListener {
            showSubscriptionActionsDialog(sub)
        }

        return card
    }

    // -------------------- ДИАЛОГ ДЕЙСТВИЙ С ПОДПИСКОЙ --------------------
    private fun showSubscriptionActionsDialog(sub: SubscriptionEntity) {
        val items = arrayOf("Редактировать", "Оплатить", "Удалить")
        AlertDialog.Builder(this)
            .setTitle(sub.name)
            .setItems(items) { dialog, which ->
                when (which) {
                    0 -> showEditSubscriptionDialog(sub)
                    1 -> paySubscription(sub)
                    2 -> deleteSubscription(sub)
                }
                dialog.dismiss()
            }
            .show()
    }

    // ---- Редактирование (используем layout activity_add_subscription для формы) ----
    private fun showEditSubscriptionDialog(sub: SubscriptionEntity) {
        val dialogView = layoutInflater.inflate(R.layout.activity_add_subscription, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.editTextName)
        val priceInput = dialogView.findViewById<EditText>(R.id.editTextPrice)
        val periodSpinner = dialogView.findViewById<Spinner>(R.id.spinnerPeriodicity)
        val iconSpinner = dialogView.findViewById<Spinner>(R.id.spinnerIcon)

        // Предзаполняем
        nameInput.setText(sub.name)
        priceInput.setText(sub.price.toString())

        // Попытка выставить позицию spinner'ов (если в resources есть такие строки)
        try {
            val periodAdapter = periodSpinner.adapter
            for (i in 0 until (periodAdapter?.count ?: 0)) {
                if ((periodAdapter?.getItem(i) as? String) == sub.periodicity) {
                    periodSpinner.setSelection(i)
                    break
                }
            }
            val iconAdapter = iconSpinner.adapter
            for (i in 0 until (iconAdapter?.count ?: 0)) {
                if ((iconAdapter?.getItem(i) as? String)?.let { mapIconNameToChoice(sub.iconResName) == it } == true) {
                    iconSpinner.setSelection(i)
                    break
                }
            }
        } catch (_: Exception) {
            // Игнорируем — если адаптеры отличны, оставляем default
        }

        AlertDialog.Builder(this)
            .setTitle("Редактировать подписку")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { dialog, _ ->
                val newName = nameInput.text.toString().trim().ifEmpty { sub.name }
                val newPrice = priceInput.text.toString().toDoubleOrNull() ?: sub.price
                val newPeriod = periodSpinner.selectedItem?.toString() ?: sub.periodicity
                val newIconChoice = iconSpinner.selectedItem?.toString()
                val newIconResName = when (newIconChoice) {
                    "Netflix" -> "netflix"
                    "YouTube" -> "youtube"
                    "Spotify" -> "spotify"
                    "Google One" -> "google_one"
                    "Amazon Prime" -> "amazon_prime"
                    "Disney+" -> "disney"
                    "VK Music" -> "vk_music"
                    else -> sub.iconResName
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    val db = Room.databaseBuilder(
                        applicationContext,
                        AppDatabase::class.java,
                        "subtrack-db"
                    ).build()
                    val subscriptionDao = db.subscriptionDao()

                    // Удаляем старую и вставляем новую запись с тем же id (Room с @Insert autoGenerate may ignore id,
                    // но такой подход работал в проекте — если хочешь стабильное обновление, добавь @Update в DAO)
                    subscriptionDao.delete(sub)
                    val updated = SubscriptionEntity(
                        id = sub.id,
                        familyCode = sub.familyCode,
                        name = newName,
                        price = newPrice,
                        periodicity = newPeriod,
                        iconResName = newIconResName,
                        ownerUsername = sub.ownerUsername,
                        nextPaymentDate = sub.nextPaymentDate // не меняем дату при редактировании
                    )
                    subscriptionDao.insert(updated)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainFrameActivity, "Подписка обновлена", Toast.LENGTH_SHORT).show()
                        loadSubscriptions()
                    }
                }

                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { d, _ -> d.dismiss() }
            .show()
    }

    // ---- Оплатить: переносим nextPaymentDate на следующий период (учитываем период подписки) ----
    private fun normalizePeriodicity(raw: String): String {
        return raw.trim().lowercase(Locale.getDefault()).replace("ё", "е")
    }

    private fun paySubscription(sub: SubscriptionEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "subtrack-db").build()
            val subscriptionDao = db.subscriptionDao()

            val baseTime = if (sub.nextPaymentDate > System.currentTimeMillis()) sub.nextPaymentDate else System.currentTimeMillis()
            val cal = Calendar.getInstance().apply { timeInMillis = baseTime }

            val period = normalizePeriodicity(sub.periodicity)

            when {
                period.contains("день") || period.contains("day") -> cal.add(Calendar.DAY_OF_YEAR, 1)
                period.contains("недел") || period.contains("week") -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                period.contains("месяц") || period.contains("month") -> cal.add(Calendar.MONTH, 1)
                period.contains("кварт") || period.contains("quarter") -> cal.add(Calendar.MONTH, 3)
                period.contains("год") || period.contains("year") -> cal.add(Calendar.YEAR, 1)
                else -> cal.add(Calendar.MONTH, 1)
            }

            val newDate = cal.timeInMillis

            // LOG (чтобы проверить что сохраняется)
            android.util.Log.d("PAY", "sub.id=${sub.id} periodicity='${sub.periodicity}' normalized='$period' base=${Date(baseTime)} -> new=${Date(newDate)}")

            // обновляем запись (лучше использовать @Update — ниже добавлю изменение DAO)
            subscriptionDao.delete(sub)
            subscriptionDao.insert(sub.copy(nextPaymentDate = newDate))

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainFrameActivity, "Оплата проведена — дата обновлена", Toast.LENGTH_SHORT).show()
                loadSubscriptions()
            }
        }
    }

    // ---- Удалить подписку ----
    private fun deleteSubscription(sub: SubscriptionEntity) {
        AlertDialog.Builder(this)
            .setTitle("Удалить подписку?")
            .setMessage("Подтвердите удаление подписки \"${sub.name}\"")
            .setPositiveButton("Удалить") { dialog, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = Room.databaseBuilder(
                        applicationContext,
                        AppDatabase::class.java,
                        "subtrack-db"
                    ).build()
                    val subscriptionDao = db.subscriptionDao()
                    subscriptionDao.delete(sub)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainFrameActivity, "Подписка удалена", Toast.LENGTH_SHORT).show()
                        loadSubscriptions()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { d, _ -> d.dismiss() }
            .show()
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

    // ---------- Утилиты ----------
    private fun mapIconNameToChoice(iconResName: String): String {
        return when (iconResName) {
            "netflix" -> "Netflix"
            "youtube" -> "YouTube"
            "spotify" -> "Spotify"
            "google_one" -> "Google One"
            "amazon_prime" -> "Amazon Prime"
            "disney" -> "Disney+"
            "vk_music" -> "VK Music"
            else -> "ic_default"
        }
    }
}
