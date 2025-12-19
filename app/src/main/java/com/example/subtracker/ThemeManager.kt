package com.example.subtracker

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    private const val PREFS = "theme_prefs"
    private const val KEY_MODE = "theme_mode"

    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    private const val EXTRA_THEME_CHANGED = "extra_theme_changed"

    fun getMode(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_MODE, MODE_SYSTEM)
    }

    fun setMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MODE, mode)
            .apply()
        applyToApp(mode)
    }

    fun applySavedMode(context: Context) {
        applyToApp(getMode(context))
    }

    private fun applyToApp(mode: Int) {
        val nightMode = when (mode) {
            MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    fun restartActivityWithFade(activity: Activity) {
        val intent = Intent(activity, activity::class.java).apply {
            replaceExtras(activity.intent)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_THEME_CHANGED, true)
        }
        activity.finish()
        activity.startActivity(intent)
        activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    fun wasThemeJustChanged(activity: Activity): Boolean {
        return activity.intent?.getBooleanExtra(EXTRA_THEME_CHANGED, false) == true
    }

    fun consumeThemeChangedFlag(activity: Activity) {
        activity.intent?.removeExtra(EXTRA_THEME_CHANGED)
    }

    fun modeLabel(mode: Int): String = when (mode) {
        MODE_LIGHT -> "Светлая"
        MODE_DARK -> "Тёмная"
        else -> "Как в системе"
    }
}
