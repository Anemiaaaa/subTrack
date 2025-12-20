package com.example.subtracker.presentation.auth

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.EditText
import android.widget.Toast
import com.example.subtracker.R
import com.example.subtracker.ThemeManager

/**
 * Управляет диалогом восстановления кода семьи
 */
class ForgotFamilyCodeDialogManager(
    private val context: Context,
    private val onRecover: (String) -> Unit
) {

    fun show() {
        val isDark = ThemeManager.getMode(context) == ThemeManager.MODE_DARK
        val layoutRes = if (isDark) {
            R.layout.dialog_forgot_family_code_dark
        } else {
            R.layout.dialog_forgot_family_code
        }

        val dialogView = android.view.LayoutInflater.from(context)
            .inflate(layoutRes, null)

        val usernameInput = dialogView.findViewById<EditText>(R.id.editTextUsername)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnRecover = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRecover)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        btnCancel?.setOnClickListener {
            dialog.dismiss()
        }

        btnRecover?.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            if (username.isEmpty()) {
                Toast.makeText(context, "Введите имя пользователя", Toast.LENGTH_SHORT).show()
            } else {
                onRecover(username)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    fun showRecoveredCode(familyCode: String) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("Код семьи восстановлен")
            .setMessage("Ваш код семьи: $familyCode\n\nНажмите, чтобы скопировать")
            .setPositiveButton("Скопировать") { d, _ ->
                copyToClipboard("Код семьи", familyCode)
                Toast.makeText(context, "Код скопирован", Toast.LENGTH_SHORT).show()
                d.dismiss()
            }
            .setNegativeButton("Закрыть", null)
            .create()

        dialog.setOnShowListener {
            val messageView = dialog.findViewById<android.widget.TextView>(android.R.id.message)
            messageView?.setOnClickListener {
                copyToClipboard("Код семьи", familyCode)
                Toast.makeText(context, "Код скопирован", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

