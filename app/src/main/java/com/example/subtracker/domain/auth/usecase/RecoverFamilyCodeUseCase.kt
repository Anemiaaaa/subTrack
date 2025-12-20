package com.example.subtracker.domain.auth.usecase

import com.example.subtracker.domain.auth.AuthRepository

/**
 * UseCase для восстановления кода семьи по имени пользователя
 */
class RecoverFamilyCodeUseCase(
    private val repo: AuthRepository
) {
    suspend operator fun invoke(username: String): String {
        return repo.recoverFamilyCode(username.trim())
    }
}

