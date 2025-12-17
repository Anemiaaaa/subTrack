package com.example.subtracker

data class FirebaseUser(
    val id: String = "",
    val username: String = "",
    val familyCode: String = "",
    val familyName: String = "",
    val role: String = "member"  // "admin" или "member"
)
