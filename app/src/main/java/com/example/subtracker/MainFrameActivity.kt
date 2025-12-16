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

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var btnSortPrice: TextView
    private lateinit var btnSortDate: TextView
    private lateinit var btnFilterUsers: TextView

    private enum class SortMode { NONE, PRICE, DATE }
    private var sortMode = SortMode.NONE
    private var sortAsc = true
    private var filterByUserEnabled = false
    private var filterUsername: String? = null

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_frame)

        subscriptionsContainer = findViewById(R.id.subscriptionsContainer)
        textUsername = findViewById(R.id.textUsername)
        btnSortPrice = findViewById(R.id.btnSortPrice)
        btnSortDate = findViewById(R.id.btnSortDate)
        btnFilterUsers = findViewById(R.id.btnFilterUsers)

        username = intent.getStringExtra("username") ?: return
        familyCode = intent.getStringExtra("familyCode") ?: return

        textUsername.text = "Привет, $username"

        findViewById<ImageButton>(R.id.nav_add).setOnClickListener {
            val intent = Intent(this, AddSubscriptionActivity::class.java)
            intent.putExtra("username", username)
            intent.putExtra("familyCode", familyCode)
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
                val noSubs = TextView(this).apply {
                    text = "Подписок нет"; gravity = Gravity.CENTER; textSize = 18f
                }
                subscriptionsContainer.addView(noSubs)
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

    // --- Методы редактирования, оплаты и удаления подписок ---
    private fun showEditSubscriptionDialog(sub: FirebaseSubscription) {
        val dialogView = layoutInflater.inflate(R.layout.activity_add_subscription, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.editTextName)
        val priceInput = dialogView.findViewById<EditText>(R.id.editTextPrice)
        val periodSpinner = dialogView.findViewById<Spinner>(R.id.spinnerPeriodicity)
        val iconSpinner = dialogView.findViewById<Spinner>(R.id.spinnerIcon)

        nameInput.setText(sub.name)
        priceInput.setText(sub.price.toString())

        AlertDialog.Builder(this)
            .setTitle("Редактировать подписку")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { dialog, _ ->
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
                    ).addOnSuccessListener { loadSubscriptions() }
                    .addOnFailureListener { e -> e.printStackTrace() }

                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { d, _ -> d.dismiss() }
            .show()
    }

    private fun paySubscription(sub: FirebaseSubscription) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = maxOf(System.currentTimeMillis(), sub.nextPaymentDate)
        }
        when (sub.periodicity.lowercase(Locale.getDefault())) {
            "день" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "неделя" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            "месяц" -> cal.add(Calendar.MONTH, 1)
            "квартал" -> cal.add(Calendar.MONTH, 3)
            "год" -> cal.add(Calendar.YEAR, 1)
            else -> cal.add(Calendar.MONTH, 1)
        }

        db.collection("subscriptions").document(sub.id)
            .update("nextPaymentDate", cal.timeInMillis)
            .addOnSuccessListener { loadSubscriptions() }
            .addOnFailureListener { it.printStackTrace() }
    }

    private fun deleteSubscription(sub: FirebaseSubscription) {
        db.collection("subscriptions").document(sub.id)
            .delete()
            .addOnSuccessListener { loadSubscriptions() }
            .addOnFailureListener { it.printStackTrace() }
    }

    private fun mapChoiceToIconResName(choice: String?): String? = when (choice) {
        "Netflix" -> "netflix"
        "YouTube" -> "youtube"
        "Spotify" -> "spotify"
        "Google One" -> "google_one"
        "Amazon Prime" -> "amazon_prime"
        "Disney+" -> "disney"
        "VK Music" -> "vk_music"
        else -> null
    }

    // --- Данные о семье ---
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
                        setTextColor(Color.WHITE)
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
                                val intent = Intent(this, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }
                    }
                    dialog.dismiss()
                }

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
