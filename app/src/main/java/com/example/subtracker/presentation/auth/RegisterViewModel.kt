package com.example.subtracker.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.subtracker.domain.auth.AuthSession
import com.example.subtracker.domain.auth.usecase.CreateFamilyUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val createFamilyUseCase: CreateFamilyUseCase
) : ViewModel() {

    sealed class Event {
        data class Created(val session: AuthSession, val familyCode: String) : Event()
        data class Error(val message: String) : Event()
    }

    private val _event = MutableStateFlow<Event?>(null)
    val event: StateFlow<Event?> = _event

    fun clearEvent() {
        _event.value = null
    }

    fun create(username: String, familyName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                createFamilyUseCase(username.trim(), familyName.trim())
            }.onSuccess { (session, code) ->
                _event.value = Event.Created(session, code)
            }.onFailure { e ->
                _event.value = Event.Error(e.message ?: "Ошибка регистрации")
            }
        }
    }
}
