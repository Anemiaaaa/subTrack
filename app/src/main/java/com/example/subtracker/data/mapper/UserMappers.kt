package com.example.subtracker.data.mapper

import com.example.subtracker.data.local.entity.UserEntity
import com.example.subtracker.domain.model.User
import com.google.firebase.firestore.DocumentSnapshot

fun DocumentSnapshot.toUser(): User? {
    val id = id
    val username = getString("username") ?: return null
    val familyCode = getString("familyCode") ?: ""
    val familyName = getString("familyName") ?: ""
    val role = getString("role") ?: "member"
    val avatarUrl = getString("avatarUrl") ?: ""
    val uid = getString("uid") ?: id

    return User(
        id = id,
        username = username,
        familyCode = familyCode,
        familyName = familyName,
        role = role,
        avatarUrl = avatarUrl
    )
}

fun DocumentSnapshot.toUserEntity(): UserEntity? {
    val id = id
    val username = getString("username") ?: return null
    val familyCode = getString("familyCode") ?: ""
    val familyName = getString("familyName") ?: ""
    val role = getString("role") ?: "member"
    val avatarUrl = getString("avatarUrl") ?: ""
    val uid = getString("uid") ?: id

    return UserEntity(
        id = id,
        uid = uid,
        username = username,
        familyCode = familyCode,
        familyName = familyName,
        role = role,
        avatarUrl = avatarUrl,
        updatedAt = System.currentTimeMillis()
    )
}

fun UserEntity.toDomain(): User {
    return User(
        id = id,
        username = username,
        familyCode = familyCode,
        familyName = familyName,
        role = role,
        avatarUrl = avatarUrl
    )
}

