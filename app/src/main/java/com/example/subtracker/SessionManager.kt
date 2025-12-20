package com.example.subtracker

import android.content.Context
import android.util.Log

object SessionManager {
    private const val PREFS = "subtracker_session"
    private const val KEY_USER_DOC_ID = "user_doc_id"   // users/{docId}
    private const val KEY_USERNAME = "username"
    private const val KEY_FAMILY_CODE = "family_code"
    private const val KEY_ROLE = "role"

    fun save(context: Context, userDocId: String, username: String, familyCode: String, role: String) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_USER_DOC_ID, userDocId)
            .putString(KEY_USERNAME, username)
            .putString(KEY_FAMILY_CODE, familyCode)
            .putString(KEY_ROLE, role)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun userDocId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USER_DOC_ID, "") ?: ""

    fun username(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USERNAME, "") ?: ""

    fun familyCode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FAMILY_CODE, "") ?: ""

    fun role(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ROLE, "member") ?: "member"

    fun dumpSession(context: Context, tag: String = "SESSION_DUMP") {
        Log.d(tag, "===== SESSION DUMP =====")
        Log.d(tag, "userDocId = ${userDocId(context)}")
        Log.d(tag, "username  = ${username(context)}")
        Log.d(tag, "familyCode= ${familyCode(context)}")
        Log.d(tag, "role      = ${role(context)}")
        Log.d(tag, "isGuest   = ${GuestSession.isActive(context)}")
        Log.d(tag, "========================")
    }

}
