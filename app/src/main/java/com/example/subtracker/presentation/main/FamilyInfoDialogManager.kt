package com.example.subtracker.presentation.main

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.example.subtracker.R
import com.example.subtracker.ThemeManager
import com.example.subtracker.domain.model.User
import java.io.File

/**
 * Управляет диалогом информации о семье
 */
class FamilyInfoDialogManager(
    private val context: Context
) {
    private val inflater = LayoutInflater.from(context)

    fun show(
        familyName: String,
        familyCode: String,
        members: List<User>
    ) {
        val isDark = ThemeManager.getMode(context) == ThemeManager.MODE_DARK
        val layoutRes = if (isDark) {
            R.layout.family_info_dialog_dark
        } else {
            R.layout.family_info_dialog
        }

        val dialogView = inflater.inflate(layoutRes, null)

        val familyNameText = dialogView.findViewById<TextView>(R.id.familyName)
        val familyCodeText = dialogView.findViewById<TextView>(R.id.familyCode)
        val shareHint = dialogView.findViewById<TextView>(R.id.share)
        val membersContainer = dialogView.findViewById<LinearLayout>(R.id.familyMembersContainer)
        val currentUserName = dialogView.findViewById<TextView>(R.id.currentUserName)
        val currentUserRole = dialogView.findViewById<TextView>(R.id.currentUserRole)

        // Заполняем информацию о семье
        familyNameText?.text = if (familyName.isNotEmpty()) "Семья: $familyName" else "Семья: —"
        familyCodeText?.text = if (familyCode.isNotEmpty()) "Код семьи: $familyCode" else "Код семьи: —"

        // Заполняем информацию о текущем пользователе (первый в списке или админ)
        val currentUser = members.firstOrNull { it.role.trim().equals("admin", ignoreCase = true) }
            ?: members.firstOrNull()
        
        val currentUserAvatarView = dialogView.findViewById<ImageView>(R.id.currentUserAvatar)
        
        if (currentUser != null) {
            currentUserName?.text = currentUser.username
            val roleLabel = if (currentUser.role.trim().equals("admin", ignoreCase = true)) {
                "Администратор"
            } else {
                "Участник"
            }
            currentUserRole?.text = roleLabel
            
            // Загружаем аватар текущего пользователя
            loadAvatar(currentUser.avatarUrl, currentUserAvatarView)
        } else {
            currentUserName?.text = "—"
            currentUserRole?.text = "—"
            currentUserAvatarView?.setImageResource(R.drawable.avatar_placeholder)
        }

        shareHint?.setOnClickListener {
            if (familyCode.isNotEmpty()) {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, "Код моей семьи: $familyCode")
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Поделиться кодом семьи"))
            }
        }

        membersContainer.removeAllViews()
        members.forEach { user ->
            membersContainer.addView(createMemberItem(user))
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        // Обработчик кнопки закрыть из layout
        val btnClose = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCloseDialog)
        btnClose?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun createMemberItem(user: User): android.view.View {
        val isDark = ThemeManager.getMode(context) == ThemeManager.MODE_DARK
        val layoutRes = if (isDark) {
            R.layout.item_family_member_dark
        } else {
            R.layout.item_family_member
        }
        val item = inflater.inflate(layoutRes, null)

        val nameView = item.findViewById<TextView>(R.id.memberName)
        val letterView = item.findViewById<TextView>(R.id.memberLetter)
        val avatarView = item.findViewById<ImageView>(R.id.memberAvatar)

        val roleLabel = if (user.role.trim().equals("admin", ignoreCase = true)) {
            "Админ"
        } else {
            "Участник"
        }
        nameView.text = "${user.username} • $roleLabel"

        val letter = user.username.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        letterView.text = letter

        // Загружаем аватар из локального файла, если есть
        loadAvatar(user.avatarUrl, avatarView)

        return item
    }
    
    /**
     * Загружает аватар из локального файла или показывает placeholder
     */
    private fun loadAvatar(avatarPath: String, imageView: ImageView) {
        android.util.Log.d("FamilyInfoDialog", "loadAvatar: path=$avatarPath, imageView=$imageView")
        
        if (avatarPath.isNotEmpty()) {
            val avatarFile = File(avatarPath)
            android.util.Log.d("FamilyInfoDialog", "Avatar file exists: ${avatarFile.exists()}, path: ${avatarFile.absolutePath}")
            
            if (avatarFile.exists()) {
                // Загружаем из локального файла
                android.util.Log.d("FamilyInfoDialog", "Loading avatar from file: ${avatarFile.absolutePath}")
                Glide.with(context)
                    .load(avatarFile)
                    .placeholder(R.drawable.avatar_placeholder)
                    .error(R.drawable.avatar_placeholder)
                    .circleCrop()
                    .into(imageView)
            } else {
                // Файл не существует, показываем placeholder
                android.util.Log.w("FamilyInfoDialog", "Avatar file does not exist: ${avatarFile.absolutePath}")
                imageView.setImageResource(R.drawable.avatar_placeholder)
            }
        } else {
            // Путь пустой, показываем placeholder
            android.util.Log.d("FamilyInfoDialog", "Avatar path is empty")
            imageView.setImageResource(R.drawable.avatar_placeholder)
        }
    }
}

