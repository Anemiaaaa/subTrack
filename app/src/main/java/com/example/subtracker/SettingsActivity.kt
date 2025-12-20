package com.example.subtracker

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import android.graphics.drawable.Drawable
import com.example.subtracker.app.di.AppGraph
import com.example.subtracker.data.local.AppDatabase
import com.example.subtracker.presentation.settings.SettingsViewModel
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class SettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModels()
    private var avatarImageView: ImageView? = null
    private var currentAvatarUri: Uri? = null
    
    private var cameraImageUri: Uri? = null
    
    private val pickImageFromGallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadAvatar(it) }
    }
    
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraImageUri // Сохраняем в локальную переменную для smart cast
        android.util.Log.d("SettingsActivity", "takePhoto callback: success=$success, cameraImageUri=$uri")
        if (success && uri != null) {
            android.util.Log.d("SettingsActivity", "Photo taken successfully, URI: $uri")
            
            // Проверяем, что файл действительно существует
            try {
                val file = File(uri.path?.replace("/my_images/", "") ?: "")
                val actualFile = File(getExternalFilesDir(null), file.name)
                android.util.Log.d("SettingsActivity", "Checking file existence: ${actualFile.absolutePath}, exists=${actualFile.exists()}, size=${actualFile.length()}")
                
                if (!actualFile.exists() || actualFile.length() == 0L) {
                    android.util.Log.e("SettingsActivity", "File does not exist or is empty!")
                    Toast.makeText(this, "Ошибка: файл не был создан", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "Error checking file", e)
            }
            
            uploadAvatar(uri)
        } else {
            android.util.Log.w("SettingsActivity", "Photo not taken or URI is null")
            if (uri == null) {
                Toast.makeText(this, "Ошибка: не удалось создать файл для фото", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Фотографирование отменено", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLayout()
        setupViews()
        setupBottomNav()
        observeViewModel()
        loadCurrentAvatar()
    }

    private fun setupLayout() {
        val isDark = ThemeManager.getMode(this) == ThemeManager.MODE_DARK
        setContentView(if (isDark) R.layout.activity_settings_dark else R.layout.activity_settings)
    }

    private fun setupViews() {
        val username = SessionManager.username(this)
        val familyCode = SessionManager.familyCode(this)

        findViewById<TextView?>(R.id.username_value)?.text = username.ifEmpty { "—" }
        
        val familyCodeValue = findViewById<TextView?>(R.id.familycode_value)
        familyCodeValue?.text = familyCode.ifEmpty { "—" }
        familyCodeValue?.setOnClickListener {
            if (familyCode.isNotEmpty()) {
                copyToClipboard("Код семьи", familyCode)
                Toast.makeText(this, "Код семьи скопирован", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Код семьи не найден", Toast.LENGTH_SHORT).show()
            }
        }

        // Настройка аватара
        avatarImageView = findViewById(R.id.user_avatar)
        findViewById<View>(R.id.edit_avatar_button)?.setOnClickListener {
            showAvatarSelectionDialog()
        }

        // Настройка переключателя темы
        setupThemeSwitcher()

        findViewById<View>(R.id.button_logout)?.setOnClickListener {
            viewModel.logout()
        }
        
        // Кнопка смены имени
        findViewById<View>(R.id.button_change_name)?.setOnClickListener {
            showChangeNameDialog()
        }
        
        // Кнопка экспорта CSV
        findViewById<View>(R.id.button_export_data)?.setOnClickListener {
            exportToCSV()
        }
    }
    
    private fun showChangeNameDialog() {
        val currentUsername = SessionManager.username(this)
        val input = android.widget.EditText(this).apply {
            hint = "Новое имя"
            setText(currentUsername)
            setSelection(text.length)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Сменить имя")
            .setMessage("Введите новое имя")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(this, "Имя не может быть пустым", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newName == currentUsername) {
                    Toast.makeText(this, "Имя не изменилось", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                updateUsername(newName)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun updateUsername(newName: String) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "Пользователь не авторизован", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Обновляем в Firestore
                val db = FirebaseFirestore.getInstance()
                val task = db.collection("users").document(user.uid)
                    .update("username", newName)
                Tasks.await(task)
                
                // Обновляем в Room
                val roomDb = AppDatabase.get(this@SettingsActivity)
                val existingUser = roomDb.users().getUserByUid(user.uid)
                if (existingUser != null) {
                    val updatedUser = existingUser.copy(username = newName)
                    roomDb.users().upsert(updatedUser)
                }
                
                // Обновляем в SessionManager
                withContext(Dispatchers.Main) {
                    val familyCode = SessionManager.familyCode(this@SettingsActivity)
                    val role = SessionManager.role(this@SettingsActivity)
                    SessionManager.save(this@SettingsActivity, user.uid, newName, familyCode, role)
                    
                    // Обновляем UI
                    findViewById<TextView>(R.id.username_value)?.text = newName
                    Toast.makeText(this@SettingsActivity, "Имя обновлено", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "Ошибка обновления имени", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Ошибка обновления имени: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun exportToCSV() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val familyCode = SessionManager.familyCode(this@SettingsActivity)
                if (familyCode.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, "Код семьи не найден", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                val db = AppDatabase.get(this@SettingsActivity)
                
                // Получаем подписки
                val subscriptions = db.subscriptions().getFamilyOnce(familyCode)
                
                // Получаем платежи
                val payments = db.payments().getFamilyPaymentsOnce(familyCode)
                
                // Создаем CSV
                val csvContent = buildCSV(subscriptions, payments)
                
                // Сохраняем файл
                val fileName = "subtracker_export_${System.currentTimeMillis()}.csv"
                val file = File(getExternalFilesDir(null), fileName)
                file.writeText(csvContent)
                
                withContext(Dispatchers.Main) {
                    shareCSVFile(file)
                    Toast.makeText(this@SettingsActivity, "CSV файл создан", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "Ошибка экспорта CSV", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Ошибка экспорта: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun buildCSV(
        subscriptions: List<com.example.subtracker.data.local.entity.SubscriptionEntity>,
        payments: List<com.example.subtracker.data.local.entity.PaymentEntity>
    ): String {
        val sb = StringBuilder()
        
        // Заголовок для подписок
        sb.appendLine("=== ПОДПИСКИ ===")
        sb.appendLine("Название,Цена,Периодичность,Владелец,Следующий платеж")
        
        val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        subscriptions.forEach { sub ->
            val nextPaymentDate = dateFormat.format(java.util.Date(sub.nextPaymentDate))
            sb.appendLine("${sub.name},${sub.price},${sub.periodicity},${sub.ownerUsername},$nextPaymentDate")
        }
        
        sb.appendLine()
        sb.appendLine("=== ПЛАТЕЖИ ===")
        sb.appendLine("Подписка,Сумма,Владелец,Дата оплаты")
        
        payments.forEach { payment ->
            val paidDate = dateFormat.format(java.util.Date(payment.paidAt))
            sb.appendLine("${payment.subscriptionName},${payment.amount},${payment.ownerUsername},$paidDate")
        }
        
        return sb.toString()
    }
    
    private fun shareCSVFile(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Экспорт данных subTracker")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        startActivity(Intent.createChooser(shareIntent, "Поделиться CSV файлом"))
    }
    
    private fun showAvatarSelectionDialog() {
        val options = arrayOf("Выбрать из галереи", "Сфотографировать")
        AlertDialog.Builder(this)
            .setTitle("Изменить аватар")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickImageFromGallery.launch("image/*")
                    1 -> {
                        if (checkCameraPermission()) {
                            createCameraImageUri()?.let { uri ->
                                cameraImageUri = uri
                                takePhoto.launch(uri)
                            }
                        } else {
                            requestCameraPermission()
                        }
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun createCameraImageUri(): Uri? {
        return try {
            val imageFile =
                File(getExternalFilesDir(null), "avatar_${System.currentTimeMillis()}.jpg")
            android.util.Log.d("SettingsActivity", "Creating camera image file: ${imageFile.absolutePath}")
            
            // Убеждаемся, что директория существует
            imageFile.parentFile?.mkdirs()
            
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                imageFile
            )
            android.util.Log.d("SettingsActivity", "Camera image URI created: $uri")
            uri
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "Error creating camera image URI", e)
            e.printStackTrace()
            null
        }
    }
    
    private fun checkCameraPermission(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestCameraPermission() {
        requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 100)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            createCameraImageUri()?.let { uri ->
                cameraImageUri = uri
                takePhoto.launch(uri)
            }
        } else {
            Toast.makeText(this, "Разрешение на камеру необходимо для фотографирования", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun uploadAvatar(uri: Uri) {
        android.util.Log.d("SettingsActivity", "uploadAvatar called with URI: $uri")
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            android.util.Log.e("SettingsActivity", "User is null")
            Toast.makeText(this, "Пользователь не авторизован", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Проверяем доступность файла
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    android.util.Log.e("SettingsActivity", "Cannot open input stream for URI: $uri")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, "Не удалось открыть изображение", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                android.util.Log.d("SettingsActivity", "Input stream opened successfully")
                
                // Сохраняем изображение локально
                val avatarDir = File(getFilesDir(), "avatars")
                if (!avatarDir.exists()) {
                    avatarDir.mkdirs()
                }
                
                val fileName = "${user.uid}_${System.currentTimeMillis()}.jpg"
                val avatarFile = File(avatarDir, fileName)
                
                // Копируем файл
                inputStream.use { input ->
                    avatarFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                android.util.Log.d("SettingsActivity", "Avatar saved locally: ${avatarFile.absolutePath}")
                
                // Сохраняем путь к файлу в Room
                val localPath = avatarFile.absolutePath
                saveAvatarToRoom(user.uid, localPath)
                
                // Обновляем UI
                withContext(Dispatchers.Main) {
                    android.util.Log.d("SettingsActivity", "Updating avatar image view with local path: $localPath")
                    updateAvatarImageView(localPath)
                    Toast.makeText(this@SettingsActivity, "Аватар сохранен", Toast.LENGTH_SHORT).show()
                }
                
                // Синхронизируем с Firestore в фоне (опционально, для синхронизации между устройствами)
                // updateAvatarUrl(localPath) // Можно отключить, если не нужна синхронизация
                
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "Ошибка при сохранении аватара", e)
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Ошибка: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun saveAvatarToRoom(uid: String, avatarUrl: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.get(this@SettingsActivity)
                // Проверяем, есть ли пользователь в Room, если нет - создаем запись
                val existingUser = db.users().getUserByUid(uid)
                if (existingUser != null) {
                    db.users().updateAvatarUrl(uid, avatarUrl)
                } else {
                    // Если пользователя нет в Room, создаем запись
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        val userEntity = com.example.subtracker.data.local.entity.UserEntity(
                            id = user.uid,
                            uid = user.uid,
                            username = SessionManager.username(this@SettingsActivity),
                            familyCode = SessionManager.familyCode(this@SettingsActivity),
                            role = SessionManager.role(this@SettingsActivity),
                            avatarUrl = avatarUrl
                        )
                        db.users().upsert(userEntity)
                    }
                }
                
                android.util.Log.d("SettingsActivity", "Avatar saved to Room: $avatarUrl")
                
                // Обновляем UI в главном потоке
                withContext(Dispatchers.Main) {
                    updateAvatarImageView(avatarUrl)
                    Toast.makeText(this@SettingsActivity, "Аватар обновлен", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "Ошибка сохранения в Room", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Ошибка сохранения аватара", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun updateAvatarImageView(avatarPath: String) {
        android.util.Log.d("SettingsActivity", "updateAvatarImageView called with path: $avatarPath, imageView: $avatarImageView")
        avatarImageView?.let { imageView ->
            if (avatarPath.isNotEmpty()) {
                val avatarFile = File(avatarPath)
                if (avatarFile.exists()) {
                    android.util.Log.d("SettingsActivity", "Loading avatar with Glide from local file: $avatarPath")
                    Glide.with(this)
                        .load(avatarFile)
                        .placeholder(R.drawable.avatar_placeholder)
                        .error(R.drawable.avatar_placeholder)
                        .circleCrop()
                        .listener(object : RequestListener<Drawable> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Drawable>,
                                isFirstResource: Boolean
                            ): Boolean {
                                android.util.Log.e("SettingsActivity", "Glide failed to load avatar: ${e?.message}", e)
                                return false
                            }

                            override fun onResourceReady(
                                resource: Drawable,
                                model: Any,
                                target: Target<Drawable>,
                                dataSource: DataSource,
                                isFirstResource: Boolean
                            ): Boolean {
                                android.util.Log.d("SettingsActivity", "Glide successfully loaded avatar")
                                return false
                            }
                        })
                        .into(imageView)
                } else {
                    android.util.Log.w("SettingsActivity", "Avatar file does not exist: $avatarPath")
                    imageView.setImageResource(R.drawable.avatar_placeholder)
                }
            } else {
                android.util.Log.w("SettingsActivity", "Avatar path is empty, setting placeholder")
                imageView.setImageResource(R.drawable.avatar_placeholder)
            }
        } ?: run {
            android.util.Log.w("SettingsActivity", "avatarImageView is null!")
        }
    }
    
    private fun updateAvatarUrl(avatarUrl: String) {
        // Синхронизируем с Firestore в фоне (не блокируем UI)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                if (user == null) return@launch
                
                val db = FirebaseFirestore.getInstance()
                val task = db.collection("users").document(user.uid)
                    .update("avatarUrl", avatarUrl)
                Tasks.await(task)
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "Ошибка синхронизации с Firestore", e)
                // Не показываем ошибку пользователю, так как данные уже в Room
            }
        }
    }
    
    private fun loadCurrentAvatar() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) return

        // Загружаем из Room (быстро, локально)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.get(this@SettingsActivity)
                val avatarPath = db.users().getAvatarUrl(user.uid) ?: ""

                withContext(Dispatchers.Main) {
                    if (avatarPath.isNotEmpty()) {
                        updateAvatarImageView(avatarPath)
                    } else {
                        avatarImageView?.setImageResource(R.drawable.avatar_placeholder)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "Ошибка загрузки аватара из Room", e)
            }
        }
    }

    private fun setupThemeSwitcher() {
        val themeButton = findViewById<View>(R.id.button_change_theme)
        val themeLabel = findViewById<TextView>(R.id.theme_current_label)

        // Обновляем текст текущей темы
        val currentMode = ThemeManager.getMode(this)
        themeLabel?.text = ThemeManager.modeLabel(currentMode)

        themeButton?.setOnClickListener {
            toggleTheme()
        }
    }

    private fun toggleTheme() {
        val currentMode = ThemeManager.getMode(this)
        val newMode = when (currentMode) {
            ThemeManager.MODE_LIGHT -> ThemeManager.MODE_DARK
            ThemeManager.MODE_DARK -> ThemeManager.MODE_LIGHT
            else -> ThemeManager.MODE_DARK // Если системная, переключаем на темную
        }

        ThemeManager.setMode(this, newMode)
        ThemeManager.restartActivityWithFade(this)
    }

    private fun setupBottomNav() {
        findViewById<View>(R.id.nav_home)?.setOnClickListener { finish() }

        findViewById<View>(R.id.nav_stats)?.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
            finish()
        }

        findViewById<View>(R.id.nav_add)?.setOnClickListener {
            startActivity(Intent(this, AddSubscriptionActivity::class.java))
            finish()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.event.collect { event ->
                    when (event) {
                        SettingsViewModel.Event.LoggedOut -> {
                            handleLogout()
                            viewModel.clearEvent()
                        }

                        is SettingsViewModel.Event.Error -> {
                            Toast.makeText(this@SettingsActivity, event.message, Toast.LENGTH_SHORT).show()
                            viewModel.clearEvent()
                        }

                        null -> Unit
                    }
                }
            }
        }
    }

    private fun handleLogout() {
        SessionManager.clear(this)
        GuestSession.clear(this)

        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
