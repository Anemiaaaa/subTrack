package com.example.subtracker.presentation.stats

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.example.subtracker.R
import com.example.subtracker.ThemeManager
import com.example.subtracker.domain.model.Payment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Отвечает за рендеринг карточек платежей
 */
class PaymentCardRenderer(
    private val context: Context,
    private val container: LinearLayout
) {
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    private val isDark: Boolean
        get() = ThemeManager.getMode(context) == ThemeManager.MODE_DARK

    fun render(payments: List<Payment>) {
        container.removeAllViews()

        if (payments.isEmpty()) {
            container.addView(createEmptyStateView())
            return
        }

        payments.forEach { payment ->
            container.addView(createPaymentCard(payment))
        }
    }

    private fun createEmptyStateView(): View {
        val card = CardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { 
                setMargins(16, 16, 16, 16)
            }
            radius = 20f
            cardElevation = 4f
            setCardBackgroundColor(if (isDark) 0xFF1C1F26.toInt() else 0xFFFFFFFF.toInt())
        }

        val textView = TextView(context).apply {
            text = "История оплат пуста"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(32, 48, 32, 48)
            setTextColor(if (isDark) 0xFF9CA3AF.toInt() else 0xFF666666.toInt())
        }

        card.addView(textView)
        return card
    }

    private fun createPaymentCard(payment: Payment): View {
        val card = CardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { 
                setMargins(16, 0, 16, 16)
            }
            radius = 20f
            cardElevation = 6f
            setCardBackgroundColor(if (isDark) 0xFF1C1F26.toInt() else 0xFFFFFFFF.toInt())
            setContentPadding(24, 24, 24, 24)
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Название подписки (заголовок)
        content.addView(TextView(context).apply {
            text = payment.subscriptionName
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (isDark) 0xFFE7EAF0.toInt() else 0xFF1A1A1A.toInt())
            setPadding(0, 0, 0, 12)
        })

        // Сумма (выделенная)
        content.addView(TextView(context).apply {
            text = "${formatMoney(payment.amount)} ₽"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (isDark) 0xFF4ADE80.toInt() else 0xFF4CAF50.toInt())
            setPadding(0, 0, 0, 16)
        })

        // Информация в строку
        val infoRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }

        infoRow.addView(TextView(context).apply {
            text = "📅 ${dateFormat.format(Date(payment.paidAt))}"
            textSize = 14f
            setTextColor(if (isDark) 0xFF9CA3AF.toInt() else 0xFF666666.toInt())
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        })

        infoRow.addView(TextView(context).apply {
            text = "👤 ${payment.ownerUsername}"
            textSize = 14f
            setTextColor(if (isDark) 0xFF9CA3AF.toInt() else 0xFF666666.toInt())
            gravity = Gravity.END
        })

        content.addView(infoRow)

        card.addView(content)
        return card
    }

    private fun formatMoney(value: Double): String {
        val rounded = (value * 100.0).roundToInt() / 100.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.2f", rounded)
        }
    }
}

