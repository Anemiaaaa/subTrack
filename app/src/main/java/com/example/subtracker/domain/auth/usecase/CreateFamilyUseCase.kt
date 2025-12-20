package com.example.subtracker.domain.auth.usecase

import com.example.subtracker.domain.auth.AuthRepository
import com.example.subtracker.domain.auth.AuthSession

class CreateFamilyUseCase(private val repo: AuthRepository) {
    suspend operator fun invoke(username: String, familyName: String): Pair<AuthSession, String> {
        return repo.createFamilyAndAdmin(username, familyName)
    }
}
