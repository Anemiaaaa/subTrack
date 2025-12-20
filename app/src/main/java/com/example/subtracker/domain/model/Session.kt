package com.example.subtracker.domain.model

data class Session(
    val userDocId: String,
    val username: String,
    val familyCode: String,
    val role: String
) {
    val isAdmin: Boolean get() = role == "admin"
    val isValid: Boolean get() = userDocId.isNotBlank() && username.isNotBlank() && familyCode.isNotBlank()
}
