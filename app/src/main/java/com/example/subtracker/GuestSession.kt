package com.example.subtracker

import android.content.Context

object GuestSession {
    private const val PREFS = "guest_session_prefs"
    private const val KEY_ACTIVE = "guest_active"

    fun setActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, active)
            .apply()
    }

    fun isActive(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACTIVE, false)
    }

    fun clear(context: Context) {
        setActive(context, false)
    }
}
