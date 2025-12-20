package com.example.subtracker.presentation.main

import android.app.AlertDialog
import android.content.Context
import com.example.subtracker.domain.model.User

/**
 * Управляет диалогом фильтрации по пользователям
 */
class FilterDialogManager(
    private val context: Context,
    private val onFilterSelected: (String?) -> Unit
) {

    fun show(members: List<User>) {
        if (members.isEmpty()) {
            return
        }

        val names = members.map { it.username }.toTypedArray()
        var selectedIndex = -1

        AlertDialog.Builder(context)
            .setTitle("Фильтр по пользователю")
            .setSingleChoiceItems(names, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("Ок") { dialog, _ ->
                if (selectedIndex >= 0) {
                    onFilterSelected(names[selectedIndex])
                } else {
                    onFilterSelected(null)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Сброс") { dialog, _ ->
                onFilterSelected(null)
                dialog.dismiss()
            }
            .show()
    }
}

