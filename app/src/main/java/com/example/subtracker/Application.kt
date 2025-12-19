package com.example.subtracker

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        ThemeManager.applySavedMode(this)
        // Важно: всегда держим анонимную сессию,
        // иначе часть экранов (Add/Settings) может падать после logout.
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
        }
    }
}
