package com.example.subtracker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.subtracker.AddSubscriptionActivity.ServiceItem
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MainFrameActivity : AppCompatActivity() {

    private lateinit var subscriptionsContainer: LinearLayout
    private lateinit var textUsername: TextView

    private lateinit var familyCode: String
    private lateinit var username: String
    private var role: String = "member"
    private var uid: String = "" // users/{docId}

    private val db = FirebaseFirestore.getInstance()

    private lateinit var btnSortPrice: TextView
    private lateinit var btnSortDate: TextView
    private lateinit var btnFilterUsers: TextView

    private enum class SortMode { NONE, PRICE, DATE }
    private var sortMode = SortMode.NONE
    private var sortAsc = true
    private var filterByUserEnabled = false
    private var filterUsername: String? = null

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private var familyMembers: List<FirebaseUser> = emptyList()

    // ===== сервисы для выбора в диалогах =====
    data class ServiceItem(val label: String, val iconResName: String, val defaultName: String = label)
    private val allServices: List<ServiceItem> by lazy { buildServiceItems() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1️⃣ Определяем режим темы из ThemeManager
        val themeMode = ThemeManager.getMode(this)

        // 2️⃣ Выбираем layout ТОЛЬКО по режиму
        val layoutRes = when (themeMode) {
            ThemeManager.MODE_DARK -> R.layout.activity_main_dark
            else -> R.layout.main_frame
        }

        setContentView(layoutRes)

        // 3️⃣ Обычная инициализация
        subscriptionsContainer = findViewById(R.id.subscriptionsContainer)
        textUsername = findViewById(R.id.textUsername)
        btnSortPrice = findViewById(R.id.btnSortPrice)
        btnSortDate = findViewById(R.id.btnSortDate)
        btnFilterUsers = findViewById(R.id.btnFilterUsers)

        username = intent.getStringExtra("username")
            .orEmpty()
            .ifEmpty { SessionManager.username(this) }

        familyCode = intent.getStringExtra("familyCode")
            .orEmpty()
            .ifEmpty { SessionManager.familyCode(this) }

        role = (intent.getStringExtra("role")
            ?: SessionManager.role(this))
            .ifEmpty { "member" }

        uid = intent.getStringExtra("uid")
            .orEmpty()
            .ifEmpty { SessionManager.userDocId(this) }

        if (username.isEmpty() || familyCode.isEmpty() || uid.isEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        textUsername.text = "Привет, $username"
        SessionManager.save(this, uid, username, familyCode, role)

        setupBottomNav()
        setupFilterButtons()

        findViewById<ImageButton>(R.id.btnFamilyInfo)
            .setOnClickListener { showFamilyInfoDialog() }

        // 4️⃣ Сбрасываем флаг смены темы (если был fade-restart)
        if (ThemeManager.wasThemeJustChanged(this)) {
            ThemeManager.consumeThemeChangedFlag(this)
        }
    }


    override fun onResume() {
        super.onResume()
        loadSubscriptions()
    }

    // ================= Bottom Navigation =================
    private fun setupBottomNav() {
        findViewById<View>(R.id.nav_home).setOnClickListener { }

        findViewById<View>(R.id.nav_add).setOnClickListener {
            startActivity(Intent(this, AddSubscriptionActivity::class.java).apply {
                putExtra("username", username)
                putExtra("familyCode", familyCode)
                putExtra("role", role)
                putExtra("uid", uid)
            })
        }

        findViewById<View>(R.id.nav_stats).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java).apply {
                putExtra("username", username)
                putExtra("familyCode", familyCode)
                putExtra("role", role)
                putExtra("uid", uid)
            })
        }

        findViewById<View>(R.id.nav_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java).apply {
                putExtra("username", username)
                putExtra("familyCode", familyCode)
                putExtra("role", role)
                putExtra("uid", uid)
            })
        }
    }

    // ================= Filters/Role bootstrap =================
    private fun setupFilterButtons() {
        db.collection("users")
            .whereEqualTo("familyCode", familyCode)
            .get()
            .addOnSuccessListener { snapshot ->
                familyMembers = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(FirebaseUser::class.java)?.copy(id = doc.id)
                }

                val me = familyMembers.find { it.id == uid } ?: familyMembers.find { it.username == username }
                role = me?.role ?: role
                SessionManager.save(this, uid, username, familyCode, role)

                btnFilterUsers.visibility = if (role == "admin") View.VISIBLE else View.GONE

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

                btnFilterUsers.setOnClickListener {
                    if (familyMembers.isEmpty()) return@setOnClickListener
                    val names = familyMembers.map { it.username }.toTypedArray()
                    var selectedIndex = names.indexOf(filterUsername)

                    AlertDialog.Builder(this)
                        .setTitle("Фильтр по пользователю")
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

                updateButtonsUI()
                loadSubscriptions()
            }
    }

    private fun updateButtonsUI() {

        val inactiveBg = R.drawable.chip_dg
        val activeBg = R.drawable.chip_dg_active

        val inactiveText = Color.parseColor("#E7EAF0")
        val activeText = Color.WHITE

        btnSortPrice.apply {
            val active = sortMode == SortMode.PRICE
            setBackgroundResource(if (active) activeBg else inactiveBg)
            setTextColor(if (active) activeText else inactiveText)
            text = "Цена" + if (active) (if (sortAsc) " ↑" else " ↓") else ""
        }

        btnSortDate.apply {
            val active = sortMode == SortMode.DATE
            setBackgroundResource(if (active) activeBg else inactiveBg)
            setTextColor(if (active) activeText else inactiveText)
            text = "Дата" + if (active) (if (sortAsc) " ↑" else " ↓") else ""
        }

        btnFilterUsers.apply {
            val active = filterByUserEnabled
            setBackgroundResource(if (active) activeBg else inactiveBg)
            setTextColor(if (active) activeText else inactiveText)
            text = if (active) "Семья (фильтр)" else "Семья"
        }
    }


    // ================= Load subscriptions =================
    private fun loadSubscriptions() {
        var query = db.collection("subscriptions").whereEqualTo("familyCode", familyCode)

        if (role != "admin") query = query.whereEqualTo("ownerUid", uid)
        else if (filterByUserEnabled && filterUsername != null) query = query.whereEqualTo("ownerUsername", filterUsername)

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

            var subs = snapshot.documents.mapNotNull { doc ->
                doc.toObject(FirebaseSubscription::class.java)?.copy(id = doc.id)
            }

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
                    2 -> confirmDeleteSubscription(sub)
                }
                dialog.dismiss()
            }.show()
    }

    // ✅ Редакт-диалог с поиском + превью иконки
    private fun showEditSubscriptionDialog(sub: FirebaseSubscription) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_subscription, null)

        val search = dialogView.findViewById<EditText>(R.id.editTextServiceSearch)
        val serviceSpinner = dialogView.findViewById<Spinner>(R.id.spinnerService)
        val iconPreview = dialogView.findViewById<ImageView>(R.id.serviceIconPreview)

        val nameInput = dialogView.findViewById<EditText>(R.id.editTextName)
        val priceInput = dialogView.findViewById<EditText>(R.id.editTextPrice)
        val periodSpinner = dialogView.findViewById<Spinner>(R.id.spinnerPeriodicity)
        val saveButton = dialogView.findViewById<Button>(R.id.buttonSave)

        nameInput.setText(sub.name)
        priceInput.setText(sub.price.toString())

        // Периодичность: ставим текущее значение
        val periodicityOptions = resources.getStringArray(R.array.periodicity_options).toList()
        val periodIndex = periodicityOptions.indexOfFirst { it.equals(sub.periodicity, ignoreCase = true) }
        if (periodIndex >= 0) periodSpinner.setSelection(periodIndex)

        var currentItems: List<ServiceItem> = allServices

        fun resolveIconResId(iconResName: String): Int {
            val id = resources.getIdentifier(iconResName, "drawable", packageName)
            return if (id != 0) id else R.drawable.ic_default
        }

        fun setIconPreviewByItem(item: ServiceItem?) {
            val resId = resolveIconResId(item?.iconResName ?: sub.iconResName)
            iconPreview.setImageResource(resId)
        }

        fun setSpinner(items: List<ServiceItem>, tryKeepIconResName: String? = null) {
            currentItems = items
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                items.map { it.label }
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            serviceSpinner.adapter = adapter

            // Пытаемся сохранить текущий выбор при фильтрации
            val target = tryKeepIconResName ?: sub.iconResName
            val idx = items.indexOfFirst { it.iconResName == target }
            if (idx >= 0) serviceSpinner.setSelection(idx)
            else if (items.isNotEmpty()) serviceSpinner.setSelection(0)

            setIconPreviewByItem(currentItems.getOrNull(serviceSpinner.selectedItemPosition))
        }

        // стартуем с полного списка + выставляем по iconResName текущей подписки
        setSpinner(allServices, sub.iconResName)
        search.setText("")
        search.clearFocus()

        serviceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val item = currentItems.getOrNull(position)
                setIconPreviewByItem(item)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // поиск фильтрует и не ломает выбранный сервис
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim().orEmpty()
                val keep = currentItems.getOrNull(serviceSpinner.selectedItemPosition)?.iconResName ?: sub.iconResName
                val filtered = if (q.isEmpty()) allServices else allServices.filter {
                    it.label.contains(q, ignoreCase = true) || it.defaultName.contains(q, ignoreCase = true)
                }
                setSpinner(filtered, keep)
            }
        })

        val dialog = AlertDialog.Builder(this)
            .setTitle("Редактировать подписку")
            .setView(dialogView)
            .create()
        dialog.show()

        saveButton.setOnClickListener {
            val newName = nameInput.text.toString().trim().ifEmpty { sub.name }
            val newPrice = priceInput.text.toString().toDoubleOrNull() ?: sub.price
            val newPeriod = periodSpinner.selectedItem?.toString() ?: sub.periodicity
            val selected = currentItems.getOrNull(serviceSpinner.selectedItemPosition)
            val newIconResName = selected?.iconResName ?: sub.iconResName

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
        }
    }

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

        when (sub.periodicity.lowercase(Locale.getDefault())) {
            "день", "каждый день" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "неделя", "каждую неделю" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            "месяц", "каждый месяц" -> cal.add(Calendar.MONTH, 1)
            "квартал", "каждый квартал" -> cal.add(Calendar.MONTH, 3)
            "год", "каждый год" -> cal.add(Calendar.YEAR, 1)
        }

        db.collection("subscriptions").document(sub.id)
            .update("nextPaymentDate", cal.timeInMillis)
            .addOnSuccessListener {
                val paymentData = hashMapOf(
                    "familyCode" to sub.familyCode,
                    "subscriptionName" to sub.name,
                    "amount" to sub.price,
                    "ownerUid" to uid,
                    "ownerUsername" to sub.ownerUsername,
                    "iconResName" to sub.iconResName,
                    "paidAt" to System.currentTimeMillis()
                )

                db.collection("payments")
                    .add(paymentData)
                    .addOnSuccessListener { loadSubscriptions() }
            }
    }

    private fun deleteSubscription(sub: FirebaseSubscription) {
        db.collection("subscriptions").document(sub.id)
            .delete()
            .addOnSuccessListener { loadSubscriptions() }
    }

    // ================= Family Info Dialog (аватарки у участников) =================
    private fun showFamilyInfoDialog() {
        db.collection("families").document(familyCode).get()
            .addOnSuccessListener { familyDoc ->
                val familyNameValue = familyDoc.getString("familyName") ?: "—"

                db.collection("users")
                    .whereEqualTo("familyCode", familyCode)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val members = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(FirebaseUser::class.java)?.copy(id = doc.id)
                        }

                        val me = members.find { it.id == uid } ?: members.find { it.username == username }
                        val myRole = me?.role ?: role
                        role = myRole
                        SessionManager.save(this, uid, username, familyCode, role)

                        val isDark = ThemeManager.getMode(this) == ThemeManager.MODE_DARK

                        val dialogLayout = if (isDark) {
                            R.layout.family_info_dialog_dark
                        } else {
                            R.layout.family_info_dialog
                        }

                        val memberItemLayout = if (isDark) {
                            R.layout.item_family_member_dark
                        } else {
                            R.layout.item_family_member
                        }

                        val dialogView = layoutInflater.inflate(dialogLayout, null)

                        val familyNameText = dialogView.findViewById<TextView>(R.id.familyName)
                        val familyCodeText = dialogView.findViewById<TextView>(R.id.familyCode)
                        val shareHint = dialogView.findViewById<TextView>(R.id.share)
                        val membersContainer = dialogView.findViewById<LinearLayout>(R.id.familyMembersContainer)
                        val closeBtn = dialogView.findViewById<Button>(R.id.btnCloseDialog)

                        val currentAvatar = dialogView.findViewById<ImageView>(R.id.currentUserAvatar)
                        val currentName = dialogView.findViewById<TextView>(R.id.currentUserName)
                        val currentRole = dialogView.findViewById<TextView>(R.id.currentUserRole)

                        currentName.text = username
                        currentRole.text = if (myRole == "admin") "Глава семьи" else "Участник"

                        val myBmp = loadAvatarBitmap(uid)
                        if (myBmp != null) currentAvatar.setImageBitmap(myBmp)
                        else currentAvatar.setImageResource(R.drawable.avatar_placeholder)

                        familyNameText.text = "Семья: $familyNameValue"
                        familyCodeText.text = "Код семьи: $familyCode"
                        shareHint.text = "Нажми, чтобы скопировать и поделиться"

                        val shareAction = {
                            copyToClipboard("Код семьи", familyCode)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Код семьи SubTracker: $familyCode")
                            }
                            startActivity(Intent.createChooser(shareIntent, "Поделиться кодом"))
                        }
                        familyCodeText.setOnClickListener { shareAction() }
                        shareHint.setOnClickListener { shareAction() }

                        val normalNameColor = if (isDark) Color.parseColor("#E7EAF0") else Color.BLACK

                        membersContainer.removeAllViews()
                        members.forEach { member ->
                            val row = layoutInflater.inflate(memberItemLayout, membersContainer, false)

                            val avatar = row.findViewById<ImageView>(R.id.memberAvatar)
                            val letter = row.findViewById<TextView>(R.id.memberLetter)
                            val name = row.findViewById<TextView>(R.id.memberName)

                            val bmp = loadAvatarBitmap(member.id)
                            if (bmp != null) {
                                avatar.setImageBitmap(bmp)
                                letter.visibility = View.GONE
                            } else {
                                avatar.setImageResource(R.drawable.avatar_placeholder)
                                letter.text = member.username.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                                letter.visibility = View.VISIBLE
                            }

                            val isAdmin = member.role == "admin"
                            name.text = member.username + if (isAdmin) "  ★" else ""
                            name.setTextColor(
                                if (isAdmin) ContextCompat.getColor(this, R.color.gold) else normalNameColor
                            )

                            membersContainer.addView(row)
                        }

                        val dialog = AlertDialog.Builder(this)
                            .setView(dialogView)
                            .create()

                        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                        dialog.show()

                        closeBtn.setOnClickListener { dialog.dismiss() }
                    }
            }
    }


    private fun loadAvatarBitmap(userDocId: String) = try {
        val file = java.io.File("${filesDir.absolutePath}/avatars/avatar_${userDocId}.png")
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    } catch (_: Exception) {
        null
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
    }

    // ✅ Вставь сюда свой большой список сервисов (RU+Global)
    private fun buildServiceItems(): List<ServiceItem> = listOf(
        // ===== Видео / Кино (Global) =====
        ServiceItem("Netflix", "netflix"),
        ServiceItem("YouTube Premium", "youtube_premium", "YouTube Premium"),
        ServiceItem("YouTube Music", "youtube_music", "YouTube Music"),
        ServiceItem("Disney+", "disney"),
        ServiceItem("Amazon Prime Video", "amazon_prime", "Prime Video"),
        ServiceItem("Apple TV+", "apple_tv", "Apple TV+"),
        ServiceItem("Max (HBO)", "max", "Max"),
        ServiceItem("Hulu", "hulu"),
        ServiceItem("Paramount+", "paramount_plus", "Paramount+"),
        ServiceItem("Peacock", "peacock"),
        ServiceItem("Crunchyroll", "crunchyroll"),
        ServiceItem("Twitch Turbo", "twitch", "Twitch Turbo"),
        ServiceItem("MUBI", "mubi", "MUBI"),

        // ===== Видео / ТВ (RU) =====
        ServiceItem("Иви", "ivi", "Иви"),
        ServiceItem("Okko", "okko"),
        ServiceItem("KION", "kion"),
        ServiceItem("START", "start"),
        ServiceItem("Wink", "wink"),
        ServiceItem("PREMIER", "premier"),
        ServiceItem("MEGOGO", "megogo", "MEGOGO"),
        ServiceItem("Amediateka", "amediateka"),
        ServiceItem("Кинопоиск", "kinopoisk", "Кинопоиск"),
        ServiceItem("RUTUBE Premium", "rutube_premium", "RUTUBE Premium"),
        ServiceItem("more.tv", "more_tv", "more.tv"),
        ServiceItem("Смотрим", "smotrim", "Смотрим"),
        ServiceItem("VK Видео", "vk_video", "VK Видео"),
        ServiceItem("Смотрёшка", "smotreshka", "Смотрёшка"),
        ServiceItem("TVzavr", "tvzavr", "TVzavr"),
        ServiceItem("Билайн ТВ", "beeline_tv", "Билайн ТВ"),
        ServiceItem("МТС ТВ", "mts_tv", "МТС ТВ"),
        ServiceItem("МегаФон ТВ", "megafon_tv", "МегаФон ТВ"),
        ServiceItem("Триколор Онлайн", "tricolor", "Триколор Онлайн"),

        // ===== Спорт =====
        ServiceItem("Матч! Премьер", "match_premier", "Матч! Премьер"),
        ServiceItem("Матч! Футбол", "match_football", "Матч! Футбол"),
        ServiceItem("Okko Sport", "okko_sport", "Okko Sport"),
        ServiceItem("Wink Sport", "wink_sport", "Wink Sport"),
        ServiceItem("Setanta Sports", "setanta", "Setanta Sports"),
        ServiceItem("UFC Fight Pass", "ufc_fight_pass", "UFC Fight Pass"),
        ServiceItem("NBA League Pass", "nba_league_pass", "NBA League Pass"),
        ServiceItem("F1 TV", "f1_tv", "F1 TV"),
        ServiceItem("DAZN", "dazn", "DAZN"),

        // ===== Музыка (Global) =====
        ServiceItem("Spotify", "spotify"),
        ServiceItem("Apple Music", "apple_music", "Apple Music"),
        ServiceItem("Deezer", "deezer"),
        ServiceItem("SoundCloud Go+", "soundcloud", "SoundCloud Go+"),
        ServiceItem("TIDAL", "tidal"),
        ServiceItem("Amazon Music", "amazon_music", "Amazon Music"),

        // ===== Музыка (RU) =====
        ServiceItem("Яндекс Музыка", "yandex_music", "Яндекс Музыка"),
        ServiceItem("VK Музыка", "vk_music", "VK Музыка"),
        ServiceItem("Звук", "zvuk", "Звук"),
        ServiceItem("МТС Музыка", "mts_music", "МТС Музыка"),
        ServiceItem("СберЗвук", "sber_zvuk", "СберЗвук"),

        // ===== Игры / Подписки =====
        ServiceItem("PlayStation Plus", "ps_plus", "PlayStation Plus"),
        ServiceItem("Xbox Game Pass", "xbox_gamepass", "Xbox Game Pass"),
        ServiceItem("Nintendo Switch Online", "nintendo_online", "Nintendo Switch Online"),
        ServiceItem("EA Play", "ea_play", "EA Play"),
        ServiceItem("Ubisoft+", "ubisoft_plus", "Ubisoft+"),
        ServiceItem("GeForce NOW", "geforce_now", "GeForce NOW"),
        ServiceItem("Boosteroid", "boosteroid", "Boosteroid"),
        ServiceItem("Battle.net", "battlenet", "Battle.net"),
        ServiceItem("Steam", "steam", "Steam"),
        ServiceItem("Epic Games", "epic_games", "Epic Games"),

        // ===== Облако / Хранилища =====
        ServiceItem("Google One", "google_one", "Google One"),
        ServiceItem("iCloud+", "icloud", "iCloud+"),
        ServiceItem("Dropbox", "dropbox", "Dropbox"),
        ServiceItem("OneDrive", "onedrive", "OneDrive"),
        ServiceItem("MEGA", "mega", "MEGA"),
        ServiceItem("Яндекс 360", "yandex_360", "Яндекс 360"),
        ServiceItem("Яндекс Диск", "yandex_disk", "Яндекс Диск"),
        ServiceItem("Облако Mail.ru", "mailru_cloud", "Облако Mail.ru"),
        ServiceItem("СберДиск", "sberdisk", "СберДиск"),

        // ===== Связь / Интернет / ТВ (RU) =====
        ServiceItem("МТС", "mts", "МТС"),
        ServiceItem("Билайн", "beeline", "Билайн"),
        ServiceItem("МегаФон", "megafon", "МегаФон"),
        ServiceItem("Tele2", "tele2", "Tele2"),
        ServiceItem("Yota", "yota", "Yota"),
        ServiceItem("Ростелеком", "rostelecom", "Ростелеком"),
        ServiceItem("Дом.ru", "domru", "Дом.ru"),
        ServiceItem("ТТК", "ttk", "ТТК"),
        ServiceItem("МГТС", "mgts", "МГТС"),

        // ===== Доставка / Еда / Маркет (RU) =====
        ServiceItem("Яндекс Плюс", "yandex_plus", "Яндекс Плюс"),
        ServiceItem("СберПрайм", "sberprime", "СберПрайм"),
        ServiceItem("СберПрайм+", "sberprime_plus", "СберПрайм+"),
        ServiceItem("Ozon Premium", "ozon_premium", "Ozon Premium"),
        ServiceItem("Wildberries", "wildberries", "Wildberries"),
        ServiceItem("Яндекс Еда", "yandex_eda", "Яндекс Еда"),
        ServiceItem("Delivery Club", "delivery_club", "Delivery Club"),
        ServiceItem("Самокат", "samokat", "Самокат"),
        ServiceItem("ВкусВилл", "vkusvill", "ВкусВилл"),
        ServiceItem("СберМаркет", "sbermarket", "СберМаркет"),
        ServiceItem("Яндекс Лавка", "yandex_lavka", "Яндекс Лавка"),
        ServiceItem("Ozon", "ozon", "Ozon"),
        ServiceItem("Яндекс Маркет", "yandex_market", "Яндекс Маркет"),
        ServiceItem("Магнит Доставка", "magnit_delivery", "Магнит Доставка"),
        ServiceItem("Пятёрочка", "pyaterochka", "Пятёрочка"),
        ServiceItem("Перекрёсток", "perekrestok", "Перекрёсток"),

        // ===== Такси / Транспорт =====
        ServiceItem("Яндекс Go", "yandex_go", "Яндекс Go"),
        ServiceItem("Uber", "uber", "Uber"),
        ServiceItem("Bolt", "bolt", "Bolt"),
        ServiceItem("Ситимобил", "citymobil", "Ситимобил"),
        ServiceItem("Яндекс Драйв", "yandex_drive", "Яндекс Драйв"),
        ServiceItem("Whoosh", "whoosh", "Whoosh"),
        ServiceItem("Urent", "urent", "Urent"),

        // ===== Книги / Аудиокниги / Подписки =====
        ServiceItem("LitRes", "litres", "LitRes"),
        ServiceItem("LitRes: Абонемент", "litres_abonement", "LitRes Абонемент"),
        ServiceItem("MyBook", "mybook", "MyBook"),
        ServiceItem("Bookmate", "bookmate", "Bookmate"),
        ServiceItem("Storytel", "storytel", "Storytel"),
        ServiceItem("Kindle Unlimited", "kindle_unlimited", "Kindle Unlimited"),

        // ===== Обучение =====
        ServiceItem("Duolingo", "duolingo", "Duolingo"),
        ServiceItem("Duolingo Super", "duolingo_super", "Duolingo Super"),
        ServiceItem("Coursera Plus", "coursera_plus", "Coursera Plus"),
        ServiceItem("Skillbox", "skillbox", "Skillbox"),
        ServiceItem("GeekBrains", "geekbrains", "GeekBrains"),
        ServiceItem("Нетология", "netology", "Нетология"),
        ServiceItem("Stepik", "stepik", "Stepik"),

        // ===== Здоровье / Спорт / Фитнес =====
        ServiceItem("Strava", "strava", "Strava"),
        ServiceItem("MyFitnessPal", "myfitnesspal", "MyFitnessPal"),
        ServiceItem("FitStars", "fitstars", "FitStars"),
        ServiceItem("YogaGo", "yogago", "YogaGo"),

        // ===== Соцсети / Мессенджеры =====
        ServiceItem("Telegram Premium", "telegram_premium", "Telegram Premium"),
        ServiceItem("VK Combo", "vk_combo", "VK Combo"),
        ServiceItem("VK Donut", "vk_donut", "VK Donut"),
        ServiceItem("TikTok", "tiktok", "TikTok"),
        ServiceItem("X Premium", "x_premium", "X Premium"),
        ServiceItem("Meta Verified", "meta_verified", "Meta Verified"),
        ServiceItem("Snapchat+", "snapchat_plus", "Snapchat+"),

        // ===== VPN / Безопасность / Утилиты =====
        ServiceItem("Kaspersky", "kaspersky", "Kaspersky"),
        ServiceItem("Dr.Web", "drweb", "Dr.Web"),
        ServiceItem("ESET", "eset", "ESET"),
        ServiceItem("AdGuard", "adguard", "AdGuard"),
        ServiceItem("NordVPN", "nordvpn", "NordVPN"),
        ServiceItem("Surfshark", "surfshark", "Surfshark"),
        ServiceItem("ExpressVPN", "expressvpn", "ExpressVPN"),
        ServiceItem("Proton VPN", "protonvpn", "Proton VPN"),

        // ===== Работа / AI / Дизайн =====
        ServiceItem("ChatGPT Plus", "chatgpt_plus", "ChatGPT Plus"),
        ServiceItem("Notion", "notion", "Notion"),
        ServiceItem("Canva Pro", "canva", "Canva Pro"),
        ServiceItem("Microsoft 365", "microsoft_365", "Microsoft 365"),
        ServiceItem("Adobe Creative Cloud", "adobe_cc", "Adobe Creative Cloud"),
        ServiceItem("Google Workspace", "google_workspace", "Google Workspace"),

        // ===== Банки / Привилегии (RU) =====
        ServiceItem("Tinkoff Pro", "tinkoff_pro", "Tinkoff Pro"),
        ServiceItem("Tinkoff Premium", "tinkoff_premium", "Tinkoff Premium"),
        ServiceItem("СберПремьер", "sber_premier", "СберПремьер"),
        ServiceItem("Альфа Смарт", "alfa_smart", "Альфа Смарт"),
        ServiceItem("ВТБ Привилегия", "vtb_privilege", "ВТБ Привилегия"),
        ServiceItem("Райффайзен Premium", "raiffeisen_premium", "Райффайзен Premium"),

        // ===== Другое =====
        ServiceItem("Другая подписка", "ic_default", "Другая подписка")
    )
}
