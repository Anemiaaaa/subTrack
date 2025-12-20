package com.example.subtracker.presentation.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.subtracker.app.di.AppGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AddSubscriptionViewModel : ViewModel() {

    sealed class Event {
        data object Saved : Event()
        data class Error(val message: String) : Event()
    }

    private val createUseCase = AppGraph.createSubscriptionUseCase

    private val _event = MutableStateFlow<Event?>(null)
    val event: StateFlow<Event?> = _event

    fun clearEvent() {
        _event.value = null
    }

    fun create(
        sessionUserDocId: String,
        sessionUsername: String,
        familyCode: String,
        name: String,
        price: Double,
        periodicity: String,
        iconResName: String,
        nextPaymentDate: Long
    ) {
        viewModelScope.launch {
            runCatching {
                createUseCase(
                    sessionUserDocId = sessionUserDocId,
                    sessionUsername = sessionUsername,
                    familyCode = familyCode,
                    name = name,
                    price = price,
                    periodicity = periodicity,
                    iconResName = iconResName,
                    nextPaymentDate = nextPaymentDate
                )
            }.onSuccess {
                _event.value = Event.Saved
            }.onFailure { e ->
                _event.value = Event.Error(e.message ?: "Ошибка сохранения")
            }
        }
    }
}
