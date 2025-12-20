package com.example.subtracker.core.model

import java.util.Locale

enum class Periodicity {
    DAY, WEEK, MONTH, QUARTER, YEAR;

    companion object {
        fun fromUiText(text: String): Periodicity? {
            val t = text.trim().lowercase(Locale.getDefault())
            return when (t) {
                "день", "каждый день" -> DAY
                "неделя", "каждую неделю" -> WEEK
                "месяц", "каждый месяц" -> MONTH
                "квартал", "каждый квартал" -> QUARTER
                "год", "каждый год" -> YEAR
                else -> null
            }
        }
    }
}
