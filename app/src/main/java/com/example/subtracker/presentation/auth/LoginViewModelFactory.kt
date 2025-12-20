package com.example.subtracker.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.subtracker.domain.auth.usecase.JoinFamilyUseCase
import com.example.subtracker.domain.auth.usecase.LoginGuestUseCase
import com.example.subtracker.domain.auth.usecase.RecoverFamilyCodeUseCase

class LoginViewModelFactory(
    private val joinFamilyUseCase: JoinFamilyUseCase,
    private val loginGuestUseCase: LoginGuestUseCase,
    private val recoverFamilyCodeUseCase: RecoverFamilyCodeUseCase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LoginViewModel::class.java))
        return LoginViewModel(
            joinFamilyUseCase,
            loginGuestUseCase,
            recoverFamilyCodeUseCase
        ) as T
    }
}
