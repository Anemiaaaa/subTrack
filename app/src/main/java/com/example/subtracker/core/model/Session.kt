package com.example.subtracker.core.model

data class Session(
    val userDocId: String,
    val username: String,
    val familyCode: String,
    val role: String
) {
    val isValid: Boolean get() = userDocId.isNotBlank() && username.isNotBlank() && familyCode.isNotBlank()
    val isAdmin: Boolean get() = role == "admin"
}
