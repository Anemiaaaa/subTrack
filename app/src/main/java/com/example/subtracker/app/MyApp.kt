package com.example.subtracker.app

import android.app.Application
import com.example.subtracker.ThemeManager
import com.example.subtracker.app.di.AppGraph
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)
        AppGraph.init(this)

        ThemeManager.applySavedMode(this)

        // Всегда держим анонимную сессию, чтобы экраны не падали.
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
        }
    }
}
