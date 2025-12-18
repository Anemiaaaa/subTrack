package com.example.subtracker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MainFrameActivity : AppCompatActivity() {

    private lateinit var subscriptionsContainer: LinearLayout
    private lateinit var textUsername: TextView
    private lateinit var familyCode: String
    private lateinit var username: String
    private var currentUserRole: String = "member"

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var btnSortPrice: TextView
    private lateinit var btnSortDate: TextView
    private lateinit var btnFilterUsers: TextView
    private lateinit var navAdd: ImageButton

    private enum class SortMode { NONE, PRICE, DATE }
    private var sortMode = SortMode.NONE
    private var sortAsc = true
    private var filterByUserEnabled = false
    private var filterUsername: String? = null

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    private var familyMembers: List<FirebaseUser> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_frame)

        // ===== UI =====
        subscriptionsContainer = findViewById(R.id.subscriptionsContainer)
        textUsername = findViewById(R.id.textUsername)
        btnSortPrice = findViewById(R.id.btnSortPrice)
        btnSortDate = findViewById(R.id.btnSortDate)
        btnFilterUsers = findViewById(R.id.btnFilterUsers)
        navAdd = findViewById(R.id.nav_add)

        username = intent.getStringExtra("username") ?: return
        familyCode = intent.getStringExtra("familyCode") ?: return

        textUsername.text = "Привет, $username"

        // ===== НАВИГАЦИЯ =====
        navAdd.setOnClickListener {
            val intent = Intent(this, AddSubscriptionActivity::class.java)
            intent.putExtra("username", username)
            intent.putExtra("familyCode", familyCode)
            startActivity(intent)
        }

        findViewById<ImageView>(R.id.nav_stats).setOnClickListener {
            val intent = Intent(this, StatsActivity::class.java)
            intent.putExtra("username", username)
            intent.putExtra("familyCode", familyCode)
            intent.putExtra("role", currentUserRole)
            startActivity(intent)
        }

        findViewById<ImageButton>(R.id.btnFamilyInfo).setOnClickListener {
            showFamilyInfoDialog()
        }

        // ===== Инициализация фильтров =====
        setupFilterButtons()
    }

    override fun onResume() {
        super.onResume()
        // Перезагрузка подписок после возвращения на экран
        loadSubscriptions()
    }

    // ================= Setup фильтров =================
    private fun setupFilterButtons() {
        db.collection("users")
            .whereEqualTo("familyCode", familyCode)
            .get()
            .addOnSuccessListener { snapshot ->
                familyMembers = snapshot.documents.mapNotNull { it.toObject(FirebaseUser::class.java) }
                val currentUser = familyMembers.find { it.username == username }
                currentUserRole = currentUser?.role ?: "member"

                btnFilterUsers.visibility = if (currentUserRole == "admin") View.VISIBLE else View.GONE

                // 🔹 Загружаем подписки сразу после определения роли
                loadSubscriptions()

                // ===== Настройка сортировки =====
                btnSortPrice.setOnClickListener {
                    if (sortMode == SortMode.PRICE) sortAsc = !sortAsc
                    else { sortMode = SortMode.PRICE; sortAsc = true }
                    updateButtonsUI()
                    loadSubscriptions()
                }

                btnSortDate.setOnClickListener {
                    if (sortMode == SortMode.DATE) sortAsc = !sortAsc
                    else { sortMode = SortMode.DATE; sortAsc = true }
                    updateButtonsUI()
                    loadSubscriptions()
                }

                // ===== Фильтр по пользователю =====
                btnFilterUsers.setOnClickListener {
                    if (familyMembers.isEmpty()) return@setOnClickListener
                    val names = familyMembers.map { it.username }.toTypedArray()
                    var selectedIndex = names.indexOf(filterUsername)

                    AlertDialog.Builder(this)
                        .setTitle("Выберите пользователя")
                        .setSingleChoiceItems(names, selectedIndex) { _, which -> selectedIndex = which }
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

    // ================= Load Subscriptions =================
    private fun loadSubscriptions() {
        var query = db.collection("subscriptions").whereEqualTo("familyCode", familyCode)

        if (currentUserRole != "admin") {
            query = query.whereEqualTo("ownerUsername", username)
        } else if (filterByUserEnabled && filterUsername != null) {
            query = query.whereEqualTo("ownerUsername", filterUsername)
        }

        query.get().addOnSuccessListener { snapshot ->
            subscriptionsContainer.removeAllViews()

            if (snapshot.isEmpty) {
                subscriptionsContainer.addView(TextView(this).apply {
                    text = "Подписок нет"
                    textSize = 18f
                    gravity = Gravity.CENTER
                })
                return@addOnSuccessListener
            }

            var subs = snapshot.documents.mapNotNull { it.toObject(FirebaseSubscription::class.java) }

            subs = when (sortMode) {
                SortMode.PRICE -> if (sortAsc) subs.sortedBy { it.price } else subs.sortedByDescending { it.price }
                SortMode.DATE -> if (sortAsc) subs.sortedBy { it.nextPaymentDate } else subs.sortedByDescending { it.nextPaymentDate }
                else -> subs
            }

            subs.forEach { subscriptionsContainer.addView(createSubscriptionCard(it)) }
        }
    }

    // ================= Создание карточки подписки =================
    private fun createSubscriptionCard(sub: FirebaseSubscription): View {
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
        dateText.text = dateFormat.format(Date(sub.nextPaymentDate))
        priceText.text = "${sub.price}₽"

        card.setOnClickListener { showSubscriptionActionsDialog(sub) }

        return card
    }

    private fun showSubscriptionActionsDialog(sub: FirebaseSubscription) {
        val items = arrayOf("Редактировать", "Оплатить", "Удалить")
        AlertDialog.Builder(this)
            .setTitle(sub.name)
            .setItems(items) { dialog, which ->
                when (which) {
                    0 -> showEditSubscriptionDialog(sub)
                    1 -> paySubscription(sub)
                    2 -> confirmDeleteSubscription(sub)
                }
                dialog.dismiss()
            }.show()
    }

    private fun showEditSubscriptionDialog(sub: FirebaseSubscription) {
        val dialogView = layoutInflater.inflate(R.layout.activity_add_subscription, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.editTextName)
        val priceInput = dialogView.findViewById<EditText>(R.id.editTextPrice)
        val periodSpinner = dialogView.findViewById<Spinner>(R.id.spinnerPeriodicity)
        val iconSpinner = dialogView.findViewById<Spinner>(R.id.spinnerIcon)
        val saveButton = dialogView.findViewById<Button>(R.id.buttonSave)

        nameInput.setText(sub.name)
        priceInput.setText(sub.price.toString())

        val dialog = AlertDialog.Builder(this)
            .setTitle("Редактировать подписку")
            .setView(dialogView)
            .create()
        dialog.show()

        saveButton.setOnClickListener {
            val newName = nameInput.text.toString().trim().ifEmpty { sub.name }
            val newPrice = priceInput.text.toString().toDoubleOrNull() ?: sub.price
            val newPeriod = periodSpinner.selectedItem?.toString() ?: sub.periodicity
            val newIconResName = mapChoiceToIconResName(iconSpinner.selectedItem?.toString()) ?: sub.iconResName

            db.collection("subscriptions").document(sub.id)
                .update(
                    mapOf(
                        "name" to newName,
                        "price" to newPrice,
                        "periodicity" to newPeriod,
                        "iconResName" to newIconResName
                    )
                )
                .addOnSuccessListener {
                    loadSubscriptions()
                    dialog.dismiss()
                }
                .addOnFailureListener { e -> e.printStackTrace() }
        }
    }


    // --- Подтверждение удаления ---
    private fun confirmDeleteSubscription(sub: FirebaseSubscription) {
        AlertDialog.Builder(this)
            .setTitle("Удалить подписку?")
            .setMessage("Вы уверены, что хотите удалить ${sub.name}?")
            .setPositiveButton("Удалить") { _, _ -> deleteSubscription(sub) }
            .setNegativeButton("Отмена", null)
            .show()
    }



    private fun paySubscription(sub: FirebaseSubscription) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = maxOf(System.currentTimeMillis(), sub.nextPaymentDate)
        }

        // 👉 считаем следующую дату
        when (sub.periodicity.lowercase(Locale.getDefault())) {
            "день", "каждый день" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "неделя", "каждую неделю" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            "месяц", "каждый месяц" -> cal.add(Calendar.MONTH, 1)
            "квартал", "каждый квартал" -> cal.add(Calendar.MONTH, 3)
            "год", "каждый год" -> cal.add(Calendar.YEAR, 1)
        }

        // 👉 обновляем подписку
        db.collection("subscriptions").document(sub.id)
            .update("nextPaymentDate", cal.timeInMillis)
            .addOnSuccessListener {

                val paymentData = hashMapOf(
                    "familyCode" to sub.familyCode,
                    "subscriptionName" to sub.name,
                    "amount" to sub.price,
                    "ownerUid" to auth.currentUser?.uid,
                    "ownerUsername" to sub.ownerUsername,
                    "iconResName" to sub.iconResName,
                    "paidAt" to System.currentTimeMillis()
                )

                db.collection("payments")
                    .add(paymentData)
                    .addOnSuccessListener {
                        loadSubscriptions()
                    }
                    .addOnFailureListener {
                        it.printStackTrace()
                    }
            }
            .addOnFailureListener {
                it.printStackTrace()
            }
    }



    // --- Удаление ---
    private fun deleteSubscription(sub: FirebaseSubscription) {
        db.collection("subscriptions").document(sub.id)
            .delete()
            .addOnSuccessListener { loadSubscriptions() }
            .addOnFailureListener { it.printStackTrace() }
    }


    private fun mapChoiceToIconResName(choice: String?): String? = when (choice) {
        // Видео стриминг
        "Netflix" -> "netflix"
        "YouTube" -> "youtube"
        "Ivi" -> "ivi"
        "Okko" -> "okko"
        "Megogo" -> "megogo"
        "Amediateka" -> "amediateka"
        "Disney+" -> "disney"
        "Amazon Prime" -> "amazon_prime"

        // Музыка
        "Spotify" -> "spotify"
        "VK Music" -> "vk_music"
        "Yandex Music" -> "yandex_music"

        // Игры
        "Steam" -> "steam"
        "Epic Games" -> "epic_games"
        "PlayStation Plus" -> "ps_plus"
        "Xbox Game Pass" -> "xbox_gamepass"

        // Облачные сервисы
        "Google One" -> "google_one"
        "Яндекс Диск" -> "yandex_disk"
        "Облако Mail.ru" -> "mailru_cloud"
        "Dropbox" -> "dropbox"

        // Социальные сети / доп. подписки
        "TikTok" -> "tiktok"
        "Telegram Premium" -> "telegram_premium"

        else -> null
    }


    private fun showFamilyInfoDialog() {
        db.collection("families").document(familyCode).get()
            .addOnSuccessListener { familyDoc ->
                val familyNameValue = familyDoc.getString("familyName") ?: "—"

                db.collection("users")
                    .whereEqualTo("familyCode", familyCode)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val familyMembers = snapshot.documents.mapNotNull { it.toObject(FirebaseUser::class.java) }
                        val currentUser = familyMembers.find { it.username == username }
                        currentUserRole = currentUser?.role ?: "member"

                        val dialogView = layoutInflater.inflate(R.layout.family_info_dialog, null)
                        val familyNameText = dialogView.findViewById<TextView>(R.id.familyName)
                        val familyCodeText = dialogView.findViewById<TextView>(R.id.familyCode)
                        val membersContainer = dialogView.findViewById<LinearLayout>(R.id.familyMembersContainer)
                        val roleText = dialogView.findViewById<TextView>(R.id.familyRole)
                        val closeBtn = dialogView.findViewById<Button>(R.id.btnCloseDialog)
                        val leaveBtn = dialogView.findViewById<Button>(R.id.leaveFamilyButton)
                        val logoutBtn = dialogView.findViewById<Button>(R.id.logoutButton)

                        // Настроим текст
                        roleText.text = "Роль: ${if (currentUserRole == "admin") "глава" else "участник"}"
                        familyNameText.text = "Семья: $familyNameValue"
                        familyCodeText.text = "Код семьи: $familyCode"

                        // Поделиться кодом семьи
                        familyCodeText.setOnClickListener {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Присоединяйтесь к нашей семье! Код семьи: $familyCode")
                                type = "text/plain"
                            }
                            startActivity(Intent.createChooser(shareIntent, "Поделиться кодом семьи через"))
                        }

                        // Создаем карточки участников
                        membersContainer.removeAllViews()
                        familyMembers.forEach { member ->
                            val itemLayout = LinearLayout(this).apply {
                                orientation = LinearLayout.HORIZONTAL
                                setPadding(16, 12, 16, 12)
                                gravity = Gravity.CENTER_VERTICAL
                                setBackgroundResource(R.drawable.member_card_bg) // нужно создать drawable с тенью/отступами
                            }

                            val avatar = TextView(this).apply {
                                text = member.username.first().uppercaseChar().toString()
                                textSize = 18f
                                gravity = Gravity.CENTER
                                setTextColor(Color.WHITE)
                                background = ContextCompat.getDrawable(this@MainFrameActivity, R.drawable.circle_bg)
                                setPadding(24, 16, 24, 16)
                            }

                            val nameText = TextView(this).apply {
                                text = member.username
                                textSize = 18f
                                setPadding(16, 0, 0, 0)
                                // если это глава семьи, выделяем цветом
                                if (member.role == "admin") setTextColor(ContextCompat.getColor(this@MainFrameActivity, R.color.gold))
                            }

                            // Если это текущий пользователь, добавляем бордер или фон
                            if (member.username == username) {
                                itemLayout.background = ContextCompat.getDrawable(this, R.drawable.current_user_card_bg)
                            }

                            itemLayout.addView(avatar)
                            itemLayout.addView(nameText)
                            membersContainer.addView(itemLayout)
                        }

                        // Создаем диалог
                        val dialog = AlertDialog.Builder(this)
                            .setView(dialogView)
                            .create()

                        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                        dialog.show()
                        dialog.window?.setLayout(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )

                        // Кнопка закрыть
                        closeBtn.setOnClickListener { dialog.dismiss() }

                        // Кнопка покинуть семью
                        leaveBtn.setOnClickListener {
                            currentUser?.let { user ->
                                if (user.role == "admin") {
                                    val others = familyMembers.filter { it.username != user.username }
                                    if (others.isNotEmpty()) {
                                        val newHead = others.random()
                                        db.collection("users").document(newHead.id).update("role", "admin")
                                    }
                                }
                                db.collection("users").document(user.id).delete()
                                    .addOnSuccessListener {
                                        val intent = Intent(this, MainActivity::class.java)
                                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        startActivity(intent)
                                        finish()
                                    }
                            }
                            dialog.dismiss()
                        }

                        // Кнопка выход
                        logoutBtn.setOnClickListener {
                            auth.signOut()
                            val intent = Intent(this, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                    }
            }
    }


}
