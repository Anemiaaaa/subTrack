package com.example.subtracker.domain.model

data class User(
    val id: String = "",
    val username: String = "",
    val familyCode: String = "",
    val familyName: String = "",
    val role: String = "member"  // "admin" или "member"
)