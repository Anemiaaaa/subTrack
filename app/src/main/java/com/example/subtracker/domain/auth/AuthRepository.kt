package com.example.subtracker.domain.auth

interface AuthRepository {

    suspend fun ensureSignedInAnonymously(): String

    suspend fun loginAsGuestAdmin(): AuthSession

    suspend fun joinFamilyOrCreateMember(
        requestedUsername: String,
        familyCode: String
    ): AuthSession

    suspend fun createFamilyAndAdmin(
        username: String,
        familyName: String
    ): Pair<AuthSession, String>

    suspend fun recoverFamilyCode(username: String): String

    suspend fun logout()
}
