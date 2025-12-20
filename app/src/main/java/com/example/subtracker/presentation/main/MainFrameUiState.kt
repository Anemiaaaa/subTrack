package com.example.subtracker.presentation.main

import com.example.subtracker.domain.model.Subscription
import com.example.subtracker.domain.model.User

/**
 * Состояние UI главного экрана
 */
data class MainFrameUiState(
    val items: List<Subscription> = emptyList(),
    val sortMode: SortMode = SortMode.NONE,
    val sortAsc: Boolean = true,
    val filterByUserEnabled: Boolean = false,
    val filterUsername: String? = null,
    val role: String = "member",
    val familyMembers: List<User> = emptyList()
) {
    /**
     * Проверяет, является ли текущий пользователь администратором
     */
    val isAdmin: Boolean
        get() = role.trim().equals("admin", ignoreCase = true)

    /**
     * Проверяет, активен ли фильтр по пользователю
     */
    val isFilterActive: Boolean
        get() = filterByUserEnabled && filterUsername != null

    /**
     * Проверяет, активна ли сортировка
     */
    val isSortActive: Boolean
        get() = sortMode != SortMode.NONE

    enum class SortMode {
        NONE,
        PRICE,
        DATE
    }
}
