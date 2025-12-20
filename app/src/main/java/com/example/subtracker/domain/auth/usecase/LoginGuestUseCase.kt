package com.example.subtracker.domain.auth.usecase

import com.example.subtracker.domain.auth.AuthRepository
import com.example.subtracker.domain.auth.AuthSession

class LoginGuestUseCase(private val repo: AuthRepository) {
    suspend operator fun invoke(): AuthSession = repo.loginAsGuestAdmin()
}
