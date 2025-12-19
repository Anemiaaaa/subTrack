package com.example.subtracker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var userAvatar: ImageView
    private lateinit var editAvatarButton: ImageButton

    private lateinit var usernameValue: TextView
    private lateinit var familyCodeValue: TextView

    private lateinit var rowChangeName: LinearLayout
    private lateinit var rowChangeTheme: LinearLayout
    private lateinit var rowLeaveFamily: LinearLayout
    private lateinit var rowLogout: LinearLayout
    private lateinit var rowExport: LinearLayout

    private lateinit var progress: ProgressBar

    private val db = FirebaseFirestore.getInstance()

    private var uid: String = ""
    private var username: String = ""
    private var familyCode: String = ""
    private var role: String = "member"

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launchWhenStarted {
                setBusy(true)
                try {
                    val bmp = loadBitmapFromUri(uri)
                    if (bmp != null) {
                        saveAvatarBitmap(bmp)
                        userAvatar.setImageBitmap(bmp)
                    } else toast("Не удалось открыть изображение")
                } finally {
                    setBusy(false)
                }
            }
        }
    }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            lifecycleScope.launchWhenStarted {
                setBusy(true)
                try {
                    saveAvatarBitmap(bitmap)
                    userAvatar.setImageBitmap(bitmap)
                } finally {
                    setBusy(false)
                }
            }
        }
    }

    private val createExportDocLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) exportToCsv(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isDark = ThemeManager.getMode(this) == ThemeManager.MODE_DARK
        setContentView(
            if (isDark) R.layout.activity_settings_dark
            else R.layout.activity_settings
        )

        // если только что сменили тему — мягко “допогасим” анимацию
        if (ThemeManager.wasThemeJustChanged(this)) {
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            ThemeManager.consumeThemeChangedFlag(this)
        }

        bindViews()
        setupNavigation()
        setupClicks()

        uid = intent.getStringExtra("uid").orEmpty()
        if (uid.isEmpty()) uid = SessionManager.userDocId(this)

        if (uid.isEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        loadProfileAndSyncSession()
        loadAvatarFromDisk()
        updateThemeRowLabel()
    }


    private fun bindViews() {
        userAvatar = findViewById(R.id.user_avatar)
        editAvatarButton = findViewById(R.id.edit_avatar_button)

        usernameValue = findViewById(R.id.username_value)
        familyCodeValue = findViewById(R.id.familycode_value)

        rowChangeName = findViewById(R.id.button_change_name)
        rowChangeTheme = findViewById(R.id.button_change_theme)
        rowLeaveFamily = findViewById(R.id.button_leave_family)
        rowLogout = findViewById(R.id.button_logout)
        rowExport = findViewById(R.id.button_export_data)

        progress = findViewById(R.id.settings_progress)
    }

    private fun setupNavigation() {
        findViewById<android.view.View>(R.id.nav_home).setOnClickListener {
            startActivity(Intent(this, MainFrameActivity::class.java).apply {
                putSessionExtras()
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            finish()
        }

        findViewById<android.view.View>(R.id.nav_stats).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java).apply { putSessionExtras() })
        }

        findViewById<android.view.View>(R.id.nav_add).setOnClickListener {
            startActivity(Intent(this, AddSubscriptionActivity::class.java).apply { putSessionExtras() })
        }

        findViewById<android.view.View>(R.id.nav_settings).setOnClickListener {
            // already here
        }
    }

    private fun Intent.putSessionExtras() {
        val u = if (uid.isNotEmpty()) uid else SessionManager.userDocId(this@SettingsActivity)
        val un = if (username.isNotEmpty()) username else SessionManager.username(this@SettingsActivity)
        val fc = if (familyCode.isNotEmpty()) familyCode else SessionManager.familyCode(this@SettingsActivity)
        val r = if (role.isNotEmpty()) role else SessionManager.role(this@SettingsActivity)

        putExtra("uid", u)
        putExtra("username", un)
        putExtra("familyCode", fc)
        putExtra("role", r)
    }

    private fun setupClicks() {
        editAvatarButton.setOnClickListener { showAvatarOptions() }

        rowChangeName.setOnClickListener { showChangeNameDialog() }

        rowChangeTheme.setOnClickListener { showThemePickerDialog() }

        rowLeaveFamily.setOnClickListener {
            confirm("Покинуть семью?") { leaveFamily() }
        }

        rowLogout.setOnClickListener {
            confirm("Выйти из аккаунта?") { logout() }
        }

        rowExport.setOnClickListener {
            confirm("Экспортировать данные в CSV?") { startExportFlow() }
        }

        familyCodeValue.setOnClickListener {
            val code = familyCode.trim()
            if (code.isNotEmpty()) copyToClipboard("Код семьи", code)
        }
    }

    private fun showThemePickerDialog() {
        val items = arrayOf("Как в системе", "Светлая", "Тёмная")
        val current = ThemeManager.getMode(this)
        val checked = when (current) {
            ThemeManager.MODE_LIGHT -> 1
            ThemeManager.MODE_DARK -> 2
            else -> 0
        }

        var chosen = checked

        AlertDialog.Builder(this)
            .setTitle("Тема")
            .setSingleChoiceItems(items, checked) { _, which -> chosen = which }
            .setPositiveButton("Применить") { _, _ ->
                val newMode = when (chosen) {
                    1 -> ThemeManager.MODE_LIGHT
                    2 -> ThemeManager.MODE_DARK
                    else -> ThemeManager.MODE_SYSTEM
                }
                if (newMode != ThemeManager.getMode(this)) {
                    ThemeManager.setMode(this, newMode)
                    ThemeManager.restartActivityWithFade(this)
                } else {
                    toast("Тема не изменилась")
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateThemeRowLabel() {
        // В rowChangeTheme у тебя внутри один TextView — меняем текст ему
        val tv = rowChangeTheme.getChildAt(0) as? TextView
        val label = ThemeManager.modeLabel(ThemeManager.getMode(this))
        tv?.text = "Тема: $label"
    }

    private fun loadProfileAndSyncSession() {
        lifecycleScope.launchWhenStarted {
            setBusy(true)
            try {
                val doc = db.collection("users").document(uid).get().await()
                if (!doc.exists()) {
                    toast("Профиль не найден")
                    SessionManager.clear(this@SettingsActivity)
                    startActivity(Intent(this@SettingsActivity, MainActivity::class.java))
                    finish()
                    return@launchWhenStarted
                }

                username = doc.getString("username")?.trim().orEmpty()
                familyCode = doc.getString("familyCode")?.trim().orEmpty()
                role = (doc.getString("role") ?: "member").trim().ifEmpty { "member" }

                usernameValue.text = if (username.isNotEmpty()) username else "—"
                familyCodeValue.text = if (familyCode.isNotEmpty()) familyCode else "—"

                rowLeaveFamily.isVisible = familyCode.isNotEmpty()
                rowExport.isVisible = familyCode.isNotEmpty()

                SessionManager.save(this@SettingsActivity, uid, username, familyCode, role)
            } catch (e: Exception) {
                toast("Ошибка профиля: ${e.message}")
            } finally {
                setBusy(false)
            }
        }
    }

    private fun showChangeNameDialog() {
        val input = EditText(this).apply {
            setText(usernameValue.text?.toString().orEmpty().takeIf { it != "—" } ?: "")
            hint = "Новое имя"
        }

        AlertDialog.Builder(this)
            .setTitle("Сменить имя")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) {
                    toast("Имя не может быть пустым")
                    return@setPositiveButton
                }
                if (newName == username) {
                    toast("Имя не изменилось")
                    return@setPositiveButton
                }
                updateUsername(newName)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateUsername(newName: String) {
        lifecycleScope.launchWhenStarted {
            setBusy(true)
            try {
                if (familyCode.isNotEmpty()) {
                    val snap = db.collection("users")
                        .whereEqualTo("familyCode", familyCode)
                        .whereEqualTo("username", newName)
                        .get()
                        .await()

                    val occupied = snap.documents.any { it.id != uid }
                    if (occupied) {
                        toast("В этой семье имя уже занято")
                        return@launchWhenStarted
                    }
                }

                db.collection("users").document(uid)
                    .update("username", newName)
                    .await()

                if (familyCode.isNotEmpty()) {
                    val subs = db.collection("subscriptions")
                        .whereEqualTo("familyCode", familyCode)
                        .whereEqualTo("ownerUid", uid)
                        .get()
                        .await()
                    for (d in subs.documents) d.reference.update("ownerUsername", newName)

                    val pays = db.collection("payments")
                        .whereEqualTo("familyCode", familyCode)
                        .whereEqualTo("ownerUid", uid)
                        .get()
                        .await()
                    for (d in pays.documents) d.reference.update("ownerUsername", newName)
                }

                username = newName
                usernameValue.text = newName
                SessionManager.save(this@SettingsActivity, uid, username, familyCode, role)

                toast("Имя изменено")
            } catch (e: Exception) {
                toast("Ошибка смены имени: ${e.message}")
            } finally {
                setBusy(false)
            }
        }
    }

    private fun leaveFamily() {
        if (familyCode.isEmpty()) {
            toast("Вы не в семье")
            return
        }

        lifecycleScope.launchWhenStarted {
            setBusy(true)
            val oldFamily = familyCode
            val wasAdmin = role == "admin"

            try {
                val userRef = db.collection("users").document(uid)

                db.runTransaction { tx ->
                    tx.update(
                        userRef,
                        mapOf(
                            "familyCode" to "",
                            "role" to "member",
                            "leftAt" to FieldValue.serverTimestamp()
                        )
                    )
                    null
                }.await()

                if (wasAdmin) {
                    val members = db.collection("users")
                        .whereEqualTo("familyCode", oldFamily)
                        .get()
                        .await()

                    val candidates = members.documents.filter { it.id != uid }
                    val newAdminDoc = candidates.randomOrNull()
                    if (newAdminDoc != null) newAdminDoc.reference.update("role", "admin").await()
                }

                familyCode = ""
                role = "member"
                familyCodeValue.text = "—"
                rowLeaveFamily.isVisible = false
                rowExport.isVisible = false

                SessionManager.save(this@SettingsActivity, uid, username, familyCode, role)

                toast("Вы покинули семью")
            } catch (e: Exception) {
                toast("Ошибка: ${e.message}")
            } finally {
                setBusy(false)
            }
        }
    }

    private fun logout() {
        SessionManager.clear(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun startExportFlow() {
        if (familyCode.isEmpty()) {
            toast("Экспорт доступен только внутри семьи")
            return
        }
        val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val fileName = "subtracker_export_${familyCode}_$ts.csv"
        createExportDocLauncher.launch(fileName)
    }

    private fun exportToCsv(uri: Uri) {
        lifecycleScope.launchWhenStarted {
            setBusy(true)
            try {
                val family = familyCode
                val isAdmin = role == "admin"

                val subsSnap = db.collection("subscriptions")
                    .whereEqualTo("familyCode", family)
                    .get()
                    .await()

                val paysSnap = db.collection("payments")
                    .whereEqualTo("familyCode", family)
                    .get()
                    .await()

                val subs = subsSnap.documents.filter { d ->
                    if (isAdmin) true else d.getString("ownerUid") == uid
                }
                val pays = paysSnap.documents.filter { d ->
                    if (isAdmin) true else d.getString("ownerUid") == uid
                }

                contentResolver.openOutputStream(uri)?.use { os ->
                    OutputStreamWriter(os, Charsets.UTF_8).use { w ->
                        w.appendLine("TYPE;id;familyCode;name;price;periodicity;iconResName;nextPaymentDate;ownerUid;ownerUsername;paidAt;amount")

                        for (d in subs) {
                            val id = d.id
                            val name = d.getString("name").orEmpty()
                            val price = d.getDouble("price") ?: 0.0
                            val periodicity = d.getString("periodicity").orEmpty()
                            val icon = d.getString("iconResName").orEmpty()
                            val next = d.getLong("nextPaymentDate") ?: 0L
                            val ownerUid = d.getString("ownerUid").orEmpty()
                            val ownerUsername = d.getString("ownerUsername").orEmpty()

                            w.appendLine("SUB;$id;$family;$name;$price;$periodicity;$icon;$next;$ownerUid;$ownerUsername;;")
                        }

                        for (d in pays) {
                            val id = d.id
                            val subName = d.getString("subscriptionName").orEmpty()
                            val amount = d.getDouble("amount") ?: 0.0
                            val icon = d.getString("iconResName").orEmpty()
                            val paidAt = d.getLong("paidAt") ?: 0L
                            val ownerUid = d.getString("ownerUid").orEmpty()
                            val ownerUsername = d.getString("ownerUsername").orEmpty()

                            w.appendLine("PAY;$id;$family;$subName;;;;;$ownerUid;$ownerUsername;$paidAt;$amount")
                        }

                        w.flush()
                    }
                }

                toast("Экспорт сохранён")
            } catch (e: Exception) {
                toast("Ошибка экспорта: ${e.message}")
            } finally {
                setBusy(false)
            }
        }
    }

    private fun showAvatarOptions() {
        val options = arrayOf("Выбрать из галереи", "Сфоткать")
        AlertDialog.Builder(this)
            .setTitle("Аватар")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickImageLauncher.launch("image/*")
                    1 -> takePhotoLauncher.launch(null)
                }
            }
            .show()
    }

    private fun avatarFilePath(): String = "${filesDir.absolutePath}/avatars/avatar_${uid}.png"

    private fun loadAvatarFromDisk() {
        try {
            val file = java.io.File(avatarFilePath())
            if (file.exists()) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) userAvatar.setImageBitmap(bmp)
            }
        } catch (_: Exception) { }
    }

    private fun ensureAvatarDir() {
        val dir = java.io.File("${filesDir.absolutePath}/avatars")
        if (!dir.exists()) dir.mkdirs()
    }

    private suspend fun saveAvatarBitmap(bitmap: Bitmap) = withContext(Dispatchers.IO) {
        ensureAvatarDir()
        val file = java.io.File(avatarFilePath())
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private suspend fun loadBitmapFromUri(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        }
    }

    private fun setBusy(busy: Boolean) {
        progress.isVisible = busy
        editAvatarButton.isEnabled = !busy
        rowChangeName.isEnabled = !busy
        rowChangeTheme.isEnabled = !busy
        rowLeaveFamily.isEnabled = !busy
        rowLogout.isEnabled = !busy
        rowExport.isEnabled = !busy
    }

    private fun confirm(message: String, onYes: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("Да") { _, _ -> onYes() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        toast("Скопировано")
    }
}
