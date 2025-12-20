package com.example.subtracker.presentation.stats

import com.example.subtracker.domain.model.Payment

data class StatsUiState(
    val payments: List<Payment> = emptyList(),

    // admin summary
    val mostExpensiveText: String = "💎 Самая дорогая: —",
    val monthlyCostText: String = "📅 Стоимость за месяц: —",
    val avgCostText: String = "📊 Средняя подписка: —",
    val totalSubscriptionsText: String = "📦 Количество подписок: —"
)
