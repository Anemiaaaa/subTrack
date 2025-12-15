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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MainFrameActivity : AppCompatActivity() {

    private lateinit var subscriptionsContainer: LinearLayout
    private lateinit var textUsername: TextView
    private lateinit var familyCode: String
    private lateinit var username: String

    private val db = FirebaseFirestore.getInstance()

    // ---- Сортировка / фильтры ----
    private enum class SortMode { NONE, PRICE, DATE }
    private var sortMode = SortMode.NONE
    private var sortAsc = true

    private var filterByUserEnabled = false
    private var filterUsername: String? = null

    private lateinit var btnSortPrice: TextView
    private lateinit var btnSortDate: TextView
    private lateinit var btnFilterUsers: TextView

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

        findViewById<ImageButton>(R.id.nav_add).setOnClickListener {
            val intent = Intent(this, AddSubscriptionActivity::class.java)
            intent.putExtra("familyCode", familyCode)
            intent.putExtra("username", username)
            startActivity(intent)
        }

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
        db.collection("users")
            .whereEqualTo("familyCode", familyCode)
            .get()
            .addOnSuccessListener { snapshot ->
                val familyMembers = snapshot.documents.mapNotNull { it.toObject(FirebaseUser::class.java) }
                val currentUser = familyMembers.find { it.username == username }

                btnFilterUsers.visibility = if (currentUser?.isAdmin == true) View.VISIBLE else View.GONE

                btnSortPrice.setOnClickListener {
                    if (sortMode == SortMode.PRICE) sortAsc = !sortAsc else { sortMode = SortMode.PRICE; sortAsc = true }
                    updateButtonsUI()
                    loadSubscriptions()
                }

                btnSortDate.setOnClickListener {
                    if (sortMode == SortMode.DATE) sortAsc = !sortAsc else { sortMode = SortMode.DATE; sortAsc = true }
                    updateButtonsUI()
                    loadSubscriptions()
                }

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

    private fun loadSubscriptions() {
        var query = db.collection("subscriptions").whereEqualTo("familyCode", familyCode)
        if (filterByUserEnabled && filterUsername != null) query = query.whereEqualTo("ownerUsername", filterUsername)

        query.get().addOnSuccessListener { snapshot ->
            subscriptionsContainer.removeAllViews()

            if (snapshot.isEmpty) {
                val noSubs = TextView(this).apply { text = "Подписок нет"; gravity = Gravity.CENTER; textSize = 18f }
                subscriptionsContainer.addView(noSubs)
                return@addOnSuccessListener
            }

            var subs = snapshot.documents.mapNotNull { it.toObject(FirebaseSubscription::class.java) }

            // Сортировка
            subs = when (sortMode) {
                SortMode.PRICE -> if (sortAsc) subs.sortedBy { it.price } else subs.sortedByDescending { it.price }
                SortMode.DATE -> if (sortAsc) subs.sortedBy { it.nextPaymentDate } else subs.sortedByDescending { it.nextPaymentDate }
                else -> subs
            }

            subs.forEach { subscriptionsContainer.addView(createSubscriptionCard(it)) }
        }
    }

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
                    2 -> deleteSubscription(sub)
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

        // Предзаполняем
        nameInput.setText(sub.name)
        priceInput.setText(sub.price.toString())

        // Попытка выставить позицию spinner'ов
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
                if ((iconAdapter?.getItem(i) as? String)?.let { mapChoiceToIconResName(sub.iconResName) == it } == true) {
                    iconSpinner.setSelection(i)
                    break
                }
            }
        } catch (_: Exception) {}

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

                // Обновляем в Firestore
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
                        Toast.makeText(this, "Подписка обновлена", Toast.LENGTH_SHORT).show()
                        loadSubscriptions()
                    }
                    .addOnFailureListener { e ->
                        e.printStackTrace()
                        Toast.makeText(this, "Ошибка при обновлении подписки", Toast.LENGTH_SHORT).show()
                    }

                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { d, _ -> d.dismiss() }
            .show()
    }



    private fun paySubscription(sub: FirebaseSubscription) {
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

        db.collection("subscriptions").document(sub.id)
            .update("nextPaymentDate", newDate)
            .addOnSuccessListener {
                Toast.makeText(this, "Оплата проведена — дата обновлена", Toast.LENGTH_SHORT).show()
                loadSubscriptions()
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(this, "Ошибка при оплате", Toast.LENGTH_SHORT).show()
            }
    }


    private fun deleteSubscription(sub: FirebaseSubscription) {
        AlertDialog.Builder(this)
            .setTitle("Удалить подписку?")
            .setMessage("Подтвердите удаление подписки \"${sub.name}\"")
            .setPositiveButton("Удалить") { dialog, _ ->
                db.collection("subscriptions").document(sub.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Подписка удалена", Toast.LENGTH_SHORT).show()
                        loadSubscriptions()
                    }
                    .addOnFailureListener { e -> e.printStackTrace() }
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { d, _ -> d.dismiss() }
            .show()
    }

    private fun normalizePeriodicity(raw: String): String {
        return raw.trim().lowercase(Locale.getDefault()).replace("ё", "е")
    }

    private fun showFamilyInfoDialog() {
        db.collection("users")
            .whereEqualTo("familyCode", familyCode)
            .get()
            .addOnSuccessListener { snapshot ->
                val familyMembers = snapshot.documents.mapNotNull { it.toObject(FirebaseUser::class.java) }
                val currentUser = familyMembers.find { it.username == username }
                val familyNameValue = familyMembers.firstOrNull()?.familyName ?: "—"

                val dialogView = layoutInflater.inflate(R.layout.family_info_dialog, null)
                val familyNameText = dialogView.findViewById<TextView>(R.id.familyName)
                val familyCodeText = dialogView.findViewById<TextView>(R.id.familyCode)
                val membersContainer = dialogView.findViewById<LinearLayout>(R.id.familyMembersContainer)
                val roleText = dialogView.findViewById<TextView>(R.id.familyRole)
                val closeBtn = dialogView.findViewById<Button>(R.id.btnCloseDialog)
                val leaveBtn = dialogView.findViewById<Button>(R.id.leaveFamilyButton)
                val logoutBtn = dialogView.findViewById<Button>(R.id.logoutButton)

                familyNameText.text = "Семья: $familyNameValue"
                familyCodeText.text = "Код семьи: $familyCode"
                roleText.text = "Роль: ${if (currentUser?.isAdmin == true) "глава" else "участник"}"

                membersContainer.removeAllViews()
                familyMembers.forEach { member ->
                    val itemLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,8,0,8) }
                    val avatar = TextView(this).apply {
                        text = member.username.first().uppercaseChar().toString()
                        textSize = 18f
                        gravity = Gravity.CENTER
                        setTextColor(ContextCompat.getColor(this@MainFrameActivity, android.R.color.white))
                        background = ContextCompat.getDrawable(this@MainFrameActivity, R.drawable.circle_bg)
                        setPadding(20,10,20,10)
                    }
                    val nameText = TextView(this).apply { text = member.username; textSize = 18f; setPadding(16,0,0,0) }
                    itemLayout.addView(avatar)
                    itemLayout.addView(nameText)
                    membersContainer.addView(itemLayout)
                }

                val dialog = AlertDialog.Builder(this)
                    .setView(dialogView)
                    .create()
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                dialog.show()
                dialog.window?.setLayout(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                closeBtn.setOnClickListener { dialog.dismiss() }

                leaveBtn.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Подтверждение")
                        .setMessage("Вы уверены, что хотите покинуть семью?")
                        .setPositiveButton("Да") { d, _ ->
                            currentUser?.let { user ->
                                if (user.isAdmin) {
                                    val others = familyMembers.filter { it.username != user.username }
                                    if (others.isNotEmpty()) {
                                        val newHead = others.random()
                                        db.collection("users").document(newHead.id).update("isAdmin", true)
                                    }
                                }
                                db.collection("users").document(user.id).delete()
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Вы покинули семью", Toast.LENGTH_SHORT).show()
                                        val intent = Intent(this, MainActivity::class.java)
                                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        startActivity(intent)
                                        finish()
                                    }
                            }
                            d.dismiss()
                        }
                        .setNegativeButton("Отмена") { d, _ -> d.dismiss() }.show()
                }

                logoutBtn.setOnClickListener {
                    val prefs = getSharedPreferences("subtracker_prefs", MODE_PRIVATE)
                    prefs.edit().clear().apply()
                    Toast.makeText(this, "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
    }

    private fun mapChoiceToIconResName(choice: String?): String? = when(choice) {
        "Netflix" -> "netflix"
        "YouTube" -> "youtube"
        "Spotify" -> "spotify"
        "Google One" -> "google_one"
        "Amazon Prime" -> "amazon_prime"
        "Disney+" -> "disney"
        "VK Music" -> "vk_music"
        else -> null
    }
}
