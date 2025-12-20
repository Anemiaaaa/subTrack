package com.example.subtracker.core.time

import com.example.subtracker.core.model.Periodicity
import java.util.Calendar

object NextPaymentCalculator {
    fun nextDateMillis(periodicity: Periodicity, fromMillis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = fromMillis }
        when (periodicity) {
            Periodicity.DAY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            Periodicity.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            Periodicity.MONTH -> cal.add(Calendar.MONTH, 1)
            Periodicity.QUARTER -> cal.add(Calendar.MONTH, 3)
            Periodicity.YEAR -> cal.add(Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }
}
