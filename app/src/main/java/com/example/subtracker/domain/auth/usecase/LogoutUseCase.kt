package com.example.subtracker.domain.auth.usecase

import com.example.subtracker.domain.auth.AuthRepository

class LogoutUseCase(
    private val repo: AuthRepository
) {
    suspend operator fun invoke() {
        repo.logout()
    }
}
