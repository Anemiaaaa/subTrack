package com.example.subtracker.presentation.stats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.subtracker.app.di.AppGraph
import com.example.subtracker.domain.model.Subscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class StatsViewModel : ViewModel() {

    private val observePayments = AppGraph.observePaymentsUseCase
    private val observeSubs = AppGraph.observeSubscriptionsUseCase
    private val repoSubs = AppGraph.subscriptionRepository

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state

    fun start(familyCode: String, userDocId: String, role: String) {
        Log.d("StatsViewModel", "start: familyCode=$familyCode, userDocId=$userDocId, role=$role")
        
        try {
            // важно: sync запускаем здесь тоже, чтобы stats офлайн показывал, а онлайн обновлялся
            Log.d("StatsViewModel", "start: calling repoSubs.startSync")
            repoSubs.startSync(familyCode)
            Log.d("StatsViewModel", "start: repoSubs.startSync completed")

            viewModelScope.launch {
                try {
                    Log.d("StatsViewModel", "start: launching payments observer")
                    observePayments.invoke(familyCode, userDocId, role).collectLatest { list ->
                        Log.d("StatsViewModel", "start: received ${list.size} payments")
                        _state.value = _state.value.copy(payments = list.sortedByDescending { it.paidAt })
                        Log.d("StatsViewModel", "start: state updated with ${_state.value.payments.size} payments")
                    }
                } catch (e: Exception) {
                    Log.e("StatsViewModel", "start: error in payments observer", e)
                }
            }

            if (role == "admin") {
                viewModelScope.launch {
                    try {
                        Log.d("StatsViewModel", "start: launching subscriptions observer for admin")
                        // берём подписки из Room через тот же usecase (admin => все семейные)
                        observeSubs.invoke(familyCode, userDocId, "admin").collectLatest { subs ->
                            Log.d("StatsViewModel", "start: received ${subs.size} subscriptions")
                            _state.value = _state.value.copy(
                                mostExpensiveText = mostExpensive(subs),
                                monthlyCostText = monthlyTotal(subs),
                                avgCostText = monthlyAvg(subs),
                                totalSubscriptionsText = "📦 Количество подписок: ${subs.size}"
                            )
                            Log.d("StatsViewModel", "start: admin stats updated")
                        }
                    } catch (e: Exception) {
                        Log.e("StatsViewModel", "start: error in subscriptions observer", e)
                    }
                }
            } else {
                Log.d("StatsViewModel", "start: user is not admin, skipping subscriptions observer")
            }
        } catch (e: Exception) {
            Log.e("StatsViewModel", "start: error", e)
            throw e
        }
    }

    private fun mostExpensive(subs: List<Subscription>): String {
        if (subs.isEmpty()) return "💎 Самая дорогая: —"
        val monthlyPairs = subs.map { it to estimateMonthlyCost(it.price, it.periodicity) }
        val (maxSub, maxMonthly) = monthlyPairs.maxByOrNull { it.second } ?: return "💎 Самая дорогая: —"
        return "💎 Самая дорогая: ${maxSub.name} • ${formatMoney(maxMonthly)} ₽/мес"
    }

    private fun monthlyTotal(subs: List<Subscription>): String {
        if (subs.isEmpty()) return "📅 Стоимость за месяц: —"
        val totalMonthly = subs.sumOf { estimateMonthlyCost(it.price, it.periodicity) }
        return "📅 Стоимость за месяц: ${formatMoney(totalMonthly)} ₽"
    }

    private fun monthlyAvg(subs: List<Subscription>): String {
        if (subs.isEmpty()) return "📊 Средняя подписка: —"
        val totalMonthly = subs.sumOf { estimateMonthlyCost(it.price, it.periodicity) }
        val avgMonthly = totalMonthly / subs.size
        return "📊 Средняя подписка: ${formatMoney(avgMonthly)} ₽/мес"
    }

    private fun estimateMonthlyCost(price: Double, periodicity: String): Double {
        val p = periodicity.trim().lowercase(Locale.getDefault())
        return when {
            p.contains("день") -> price * 30.0
            p.contains("нед") -> price * 4.345
            p.contains("кварт") -> price / 3.0
            p.contains("год") -> price / 12.0
            else -> price // месяц / неизвестно
        }
    }

    private fun formatMoney(v: Double): String {
        val rounded = (v * 100.0).roundToInt() / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString()
        else String.format(Locale.getDefault(), "%.2f", rounded)
    }
}
