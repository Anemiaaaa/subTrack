package com.example.subtracker.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.subtracker.app.di.AppGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    sealed class Event {
        object LoggedOut : Event()
        data class Error(val message: String) : Event()
    }

    private val logoutUseCase = AppGraph.logoutUseCase

    private val _event = MutableStateFlow<Event?>(null)
    val event: StateFlow<Event?> = _event

    fun clearEvent() {
        _event.value = null
    }

    fun logout() {
        viewModelScope.launch {
            runCatching {
                logoutUseCase()
            }.onSuccess {
                _event.value = Event.LoggedOut
            }.onFailure {
                _event.value = Event.Error("Ошибка выхода")
            }
        }
    }
}
