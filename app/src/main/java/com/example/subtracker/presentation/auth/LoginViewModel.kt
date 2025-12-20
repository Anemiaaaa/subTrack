package com.example.subtracker.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.subtracker.domain.auth.AuthSession
import com.example.subtracker.domain.auth.usecase.JoinFamilyUseCase
import com.example.subtracker.domain.auth.usecase.LoginGuestUseCase
import com.example.subtracker.domain.auth.usecase.RecoverFamilyCodeUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val joinFamilyUseCase: JoinFamilyUseCase,
    private val loginGuestUseCase: LoginGuestUseCase,
    private val recoverFamilyCodeUseCase: RecoverFamilyCodeUseCase
) : ViewModel() {

    // ================= EVENTS =================
    sealed class Event {
        data class Success(val session: AuthSession) : Event()
        data class Error(val message: String) : Event()
        data class FamilyCodeRecovered(val familyCode: String) : Event()
    }

    private val _event = MutableStateFlow<Event?>(null)
    val event: StateFlow<Event?> = _event

    fun clearEvent() {
        _event.value = null
    }

    // ================= CLASSIC LOGIN =================
    fun joinFamily(username: String, familyCode: String) {
        android.util.Log.d("LoginViewModel", "joinFamily called: username=$username, familyCode=$familyCode")
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                android.util.Log.d("LoginViewModel", "Calling joinFamilyUseCase")
                joinFamilyUseCase(
                    requestedUsername = username.trim(),
                    familyCode = familyCode.trim().uppercase()
                )
            }.onSuccess { session ->
                android.util.Log.d("LoginViewModel", "joinFamilyUseCase success: session=$session")
                _event.value = Event.Success(session)
            }.onFailure { e ->
                android.util.Log.e("LoginViewModel", "joinFamilyUseCase failed", e)
                _event.value = Event.Error(e.message ?: "Ошибка входа")
            }
        }
    }

    // ================= GOOGLE LOGIN =================
    fun googleJoinFamily(
        uid: String,
        username: String,
        familyCode: String
    ) {
        // uid пока не используется в usecase — это ОК, если внутри usecase берёшь FirebaseAuth.currentUser.uid
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                joinFamilyUseCase(
                    requestedUsername = username.trim(),
                    familyCode = familyCode.trim().uppercase()
                )
            }.onSuccess { session ->
                _event.value = Event.Success(session)
            }.onFailure { e ->
                _event.value = Event.Error(e.message ?: "Ошибка входа через Google")
            }
        }
    }

    // ================= GUEST =================
    fun guestLogin() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { loginGuestUseCase() }
                .onSuccess { session -> _event.value = Event.Success(session) }
                .onFailure { e -> _event.value = Event.Error(e.message ?: "Ошибка гостевого входа") }
        }
    }

    // ================= RECOVER =================
    fun recoverFamilyCode(username: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { recoverFamilyCodeUseCase(username) }
                .onSuccess { familyCode -> _event.value = Event.FamilyCodeRecovered(familyCode) }
                .onFailure { e ->
                    _event.value = Event.Error(e.message ?: "Не удалось восстановить код семьи")
                }
        }
    }
}
