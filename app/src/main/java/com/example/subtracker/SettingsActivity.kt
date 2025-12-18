package com.example.subtracker

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class SettingsActivity : AppCompatActivity() {

    private lateinit var userAvatar: ImageView
    private lateinit var editAvatarButton: ImageButton
    private lateinit var usernameText: TextView
    private lateinit var familyCode: String
    private lateinit var username: String
    private lateinit var role: String

    private val db = FirebaseFirestore.getInstance()

    // Выбор картинки из галереи
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            userAvatar.setImageURI(it)
            // TODO: загружать в Firebase Storage
        }
    }

    // Съемка с камеры
    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            userAvatar.setImageBitmap(it)
            // TODO: загружать в Firebase Storage
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // ===== INTENT =====
        username = intent.getStringExtra("username") ?: return
        familyCode = intent.getStringExtra("familyCode") ?: return
        role = intent.getStringExtra("role") ?: "member"

        // ===== UI =====
        userAvatar = findViewById(R.id.user_avatar)
        editAvatarButton = findViewById(R.id.edit_avatar_button)
        usernameText = findViewById(R.id.username_text) // нужно добавить id в xml

        // Инициализация аватара по первой букве имени если фото нет
        if (/* условие нет фото */) {
            userAvatar.setImageBitmap(createAvatarFromInitial(username))
        }

        // ===== NAVIGATION =====
        setupNavigation()

        // ===== Кнопки настроек =====
        findViewById<LinearLayout>(R.id.button_change_name).setOnClickListener {
            confirmAction("Сменить имя пользователя?") {
                showChangeNameDialog()
            }
        }

        findViewById<LinearLayout>(R.id.button_change_theme).setOnClickListener {
            Toast.makeText(this, "Смена темы пока не реализована", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.button_leave_family).setOnClickListener {
            confirmAction("Вы уверены, что хотите покинуть семью?") {
                leaveFamily()
            }
        }

        findViewById<LinearLayout>(R.id.button_logout).setOnClickListener {
            confirmAction("Вы действительно хотите выйти из аккаунта?") {
                logout()
            }
        }

        findViewById<LinearLayout>(R.id.button_export_data).setOnClickListener {
            Toast.makeText(this, "Экспорт пока не реализован", Toast.LENGTH_SHORT).show()
        }

        // ===== Редактирование аватара =====
        editAvatarButton.setOnClickListener {
            confirmAction("Изменить аватар?") {
                showAvatarOptions()
            }
        }
    }

    // ================= NAVIGATION =================
    private fun setupNavigation() {
        findViewById<ImageButton>(R.id.nav_home).setOnClickListener {
            startActivity(Intent(this, MainFrameActivity::class.java).apply {
                putExtra("username", username)
                putExtra("familyCode", familyCode)
            })
            finish()
        }

        findViewById<ImageView>(R.id.nav_stats).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java).apply {
                putExtra("username", username)
                putExtra("familyCode", familyCode)
                putExtra("role", role)
            })
        }

        findViewById<ImageView>(R.id.nav_add).setOnClickListener {
            startActivity(Intent(this, AddSubscriptionActivity::class.java).apply {
                putExtra("username", username)
                putExtra("familyCode", familyCode)
            })
        }

        findViewById<ImageView>(R.id.nav_settings).setOnClickListener {
            // уже здесь
        }
    }

    // ================= CONFIRMATION =================
    private fun confirmAction(message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("Да") { _, _ -> onConfirm() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ================= AVATAR OPTIONS =================
    private fun showAvatarOptions() {
        val options = arrayOf("Выбрать из галереи", "Сфоткать")
        AlertDialog.Builder(this)
            .setTitle("Изменить аватар")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickImageLauncher.launch("image/*")
                    1 -> takePhotoLauncher.launch(null)
                }
            }
            .show()
    }

    private fun createAvatarFromInitial(name: String): Bitmap {
        val size = 110 // px
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.color = Color.parseColor("#3498DB") // синий фон
        paint.isAntiAlias = true
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        paint.color = Color.WHITE
        paint.textSize = 50f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER

        val xPos = canvas.width / 2f
        val yPos = (canvas.height / 2f - (paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(name.first().uppercaseChar().toString(), xPos, yPos, paint)

        return bitmap
    }

    // ================= LEAVE FAMILY =================
    private fun leaveFamily() {
        val userRef = db.collection("users").document(username)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            val currentRole = snapshot.getString("role") ?: "member"
            val familyId = snapshot.getString("familyCode") ?: return@runTransaction

            if (currentRole == "head") {
                val members = db.collection("users")
                    .whereEqualTo("familyCode", familyId)
                    .whereNotEqualTo("username", username)
                    .get().result

                if (!members.isNullOrEmpty()) {
                    val newHead = members.first()
                    transaction.update(newHead.reference, "role", "head")
                }
            }

            transaction.update(userRef, mapOf(
                "familyCode" to null,
                "role" to "member"
            ))
        }.addOnSuccessListener {
            Toast.makeText(this, "Вы покинули семью", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "Ошибка при выходе из семьи", Toast.LENGTH_SHORT).show()
        }
    }

    // ================= LOGOUT =================
    private fun logout() {
        // TODO: очистить локальные данные и перейти на LoginActivity
        Toast.makeText(this, "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show()
    }

    // ================= CHANGE NAME =================
    private fun showChangeNameDialog() {
        val editText = EditText(this)
        editText.setText(username)
        AlertDialog.Builder(this)
            .setTitle("Сменить имя")
            .setView(editText)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    db.collection("users").document(username)
                        .update("displayName", newName)
                        .addOnSuccessListener {
                            usernameText.text = newName
                            Toast.makeText(this, "Имя изменено", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
