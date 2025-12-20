package com.example.subtracker.data.mapper

import com.example.subtracker.domain.model.User
import com.google.firebase.firestore.DocumentSnapshot

fun DocumentSnapshot.toUser(): User? {
    val id = id
    val username = getString("username") ?: return null
    val familyCode = getString("familyCode") ?: ""
    val familyName = getString("familyName") ?: ""
    val role = getString("role") ?: "member"

    return User(
        id = id,
        username = username,
        familyCode = familyCode,
        familyName = familyName,
        role = role
    )
}

