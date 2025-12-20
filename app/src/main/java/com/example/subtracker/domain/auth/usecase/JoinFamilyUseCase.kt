package com.example.subtracker.domain.auth.usecase

import com.example.subtracker.domain.auth.AuthRepository
import com.example.subtracker.domain.auth.AuthSession

class JoinFamilyUseCase(private val repo: AuthRepository) {
    suspend operator fun invoke(requestedUsername: String, familyCode: String): AuthSession {
        return repo.joinFamilyOrCreateMember(requestedUsername, familyCode)
    }
}
