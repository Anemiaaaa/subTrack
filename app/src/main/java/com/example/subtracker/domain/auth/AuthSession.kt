package com.example.subtracker.domain.auth

data class AuthSession(
    val userDocId: String,   // docId в users/{docId}
    val username: String,
    val familyCode: String,
    val role: String,        // "admin" | "member"
    val isGuest: Boolean
)
