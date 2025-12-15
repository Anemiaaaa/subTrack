package com.example.subtracker

import com.google.firebase.firestore.DocumentId

data class FirebaseUser(
    @DocumentId
    val id: String = "",             // ID документа пользователя
    val username: String = "",
    val familyCode: String = "",
    val familyName: String = "",
    val isAdmin: Boolean = false
)
