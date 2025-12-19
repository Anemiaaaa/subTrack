package com.example.subtracker

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.Locale

class AddSubscriptionActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore

    private var familyCode: String = ""
    private var username: String = ""
    private var role: String = "member"
    private var userDocId: String = "" // users/{docId}

    data class ServiceItem(
        val label: String,
        val iconResName: String,
        val defaultName: String = label
    )

    private val serviceItems: List<ServiceItem> by lazy { buildServiceItems() }
    private var currentItems: List<ServiceItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isDark = ThemeManager.getMode(this) == ThemeManager.MODE_DARK
        setContentView(
            if (isDark) R.layout.add_subscription_dark
            else R.layout.activity_add_subscription
        )

        FirebaseApp.initializeApp(this)
        db = FirebaseFirestore.getInstance()

        val nameInput = findViewById<EditText>(R.id.editTextName)
        val priceInput = findViewById<EditText>(R.id.editTextPrice)
        val periodicitySpinner = findViewById<Spinner>(R.id.spinnerPeriodicity)
        val iconSpinner = findViewById<Spinner>(R.id.spinnerIcon)
        val buttonSave = findViewById<Button>(R.id.buttonSave)
        val searchInput = findViewById<EditText>(R.id.editTextServiceSearch)

        familyCode = intent.getStringExtra("familyCode").orEmpty().ifEmpty { SessionManager.familyCode(this) }
        username = intent.getStringExtra("username").orEmpty().ifEmpty { SessionManager.username(this) }
        role = intent.getStringExtra("role") ?: SessionManager.role(this)
        userDocId = intent.getStringExtra("uid").orEmpty().ifEmpty { SessionManager.userDocId(this) }

        if (familyCode.isEmpty() || username.isEmpty() || userDocId.isEmpty()) {
            Toast.makeText(this, "Сессия не найдена. Войдите заново.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // ===== NAV (не меняю) =====
        findViewById<View>(R.id.nav_home).setOnClickListener {
            startActivity(Intent(this, MainFrameActivity::class.java).apply {
                putExtra("username", username)
                putExtra("familyCode", familyCode)
                putExtra("role", role)
                putExtra("uid", userDocId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            finish()
        }
        findViewById<View>(R.id.nav_stats).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java).apply {
                putExtra("username", username)
                putExtra("familyCode", familyCode)
                putExtra("role", role)
                putExtra("uid", userDocId)
            })
            finish()
        }
        findViewById<View>(R.id.nav_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java).apply {
                putExtra("username", username)
                putExtra("familyCode", familyCode)
                putExtra("role", role)
                putExtra("uid", userDocId)
            })
            finish()
        }
        findViewById<View>(R.id.nav_add).setOnClickListener { }

        // ===== Spinner: Periodicity (item1 + item2) =====
        val periodicityAdapter = if (isDark) {
            ArrayAdapter.createFromResource(
                this,
                R.array.periodicity_options,
                R.layout.spinner_item_dark
            ).apply {
                setDropDownViewResource(R.layout.spinner_item_dark)
            }
        } else {
            ArrayAdapter.createFromResource(
                this,
                R.array.periodicity_options,
                android.R.layout.simple_spinner_item
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        periodicitySpinner.adapter = periodicityAdapter

        // ===== Spinner: Services (item1 + item2) =====
        fun setSpinnerData(items: List<ServiceItem>) {
            currentItems = items

            val adapter = if (isDark) {
                ArrayAdapter(
                    this,
                    R.layout.spinner_item_dark,
                    items.map { it.label }
                ).apply {
                    setDropDownViewResource(R.layout.spinner_item_dark)
                }
            } else {
                ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    items.map { it.label }
                ).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
            }

            iconSpinner.adapter = adapter
        }

        // Всегда стартуем с полного списка
        setSpinnerData(serviceItems)

        // На всякий случай чистим поиск
        searchInput.setText("")
        searchInput.clearFocus()

        iconSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val item = currentItems.getOrNull(position) ?: return
                if (nameInput.text.toString().trim().isEmpty()) {
                    nameInput.setText(item.defaultName)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim().orEmpty()
                val filtered = if (q.isEmpty()) serviceItems else serviceItems.filter {
                    it.label.contains(q, ignoreCase = true) ||
                            it.defaultName.contains(q, ignoreCase = true)
                }
                setSpinnerData(filtered)

                if (filtered.size == 1) {
                    iconSpinner.setSelection(0)
                    if (nameInput.text.toString().trim().isEmpty()) {
                        nameInput.setText(filtered[0].defaultName)
                    }
                }
            }
        })

        buttonSave.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val price = priceInput.text.toString().toDoubleOrNull() ?: 0.0
            val periodicity = periodicitySpinner.selectedItem?.toString().orEmpty()
            val selected = currentItems.getOrNull(iconSpinner.selectedItemPosition)

            if (name.isEmpty()) {
                Toast.makeText(this, "Введите название подписки", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (price <= 0.0) {
                Toast.makeText(this, "Введите корректную стоимость", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (periodicity.isEmpty()) {
                Toast.makeText(this, "Выберите периодичность", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val iconResName = selected?.iconResName ?: "ic_default"
            val nextPaymentDate = calculateNextPaymentDate(periodicity)

            val data = hashMapOf(
                "familyCode" to familyCode,
                "name" to name,
                "price" to price,
                "periodicity" to periodicity,
                "iconResName" to iconResName,
                "ownerUid" to userDocId,
                "ownerUsername" to username,
                "nextPaymentDate" to nextPaymentDate,
                "createdAt" to FieldValue.serverTimestamp()
            )

            db.collection("subscriptions")
                .add(data)
                .addOnSuccessListener {
                    Toast.makeText(this, "Подписка добавлена", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainFrameActivity::class.java).apply {
                        putExtra("username", username)
                        putExtra("familyCode", familyCode)
                        putExtra("role", role)
                        putExtra("uid", userDocId)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    })
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun calculateNextPaymentDate(periodicity: String): Long {
        val calendar = Calendar.getInstance()
        when (periodicity.lowercase(Locale.getDefault())) {
            "день" -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            "неделя" -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            "месяц" -> calendar.add(Calendar.MONTH, 1)
            "квартал" -> calendar.add(Calendar.MONTH, 3)
            "год" -> calendar.add(Calendar.YEAR, 1)
        }
        return calendar.timeInMillis
    }

    // === Большой список (RU + Global) ===
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
