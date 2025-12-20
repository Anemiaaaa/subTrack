package com.example.subtracker.presentation.main

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.example.subtracker.R
import com.example.subtracker.ThemeManager
import com.example.subtracker.domain.model.Subscription
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Отвечает за рендеринг карточек подписок в контейнер
 */
class SubscriptionCardRenderer(
    private val context: Context,
    private val container: LinearLayout,
    private val onCardClick: (Subscription) -> Unit
) {
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val inflater = LayoutInflater.from(context)
    private val isDark: Boolean
        get() = ThemeManager.getMode(context) == ThemeManager.MODE_DARK

    fun render(subscriptions: List<Subscription>, currentUsername: String) {
        container.removeAllViews()

        if (subscriptions.isEmpty()) {
            container.addView(createEmptyStateView())
            return
        }

        subscriptions.forEach { subscription ->
            container.addView(createCard(subscription, currentUsername))
        }
    }

    private fun createEmptyStateView(): TextView {
        return TextView(context).apply {
            text = "Подписок нет"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setTextColor(if (isDark) 0xFF9CA3AF.toInt() else 0xFF666666.toInt())
        }
    }

    private fun createCard(subscription: Subscription, currentUsername: String): View {
        val card = inflater.inflate(R.layout.sub_card_item, container, false)

        val cardView = card as? CardView
        val iconImage = card.findViewById<ImageView>(R.id.iconImage)
        val nameText = card.findViewById<TextView>(R.id.nameText)
        val ownerText = card.findViewById<TextView>(R.id.ownerText)
        val dateText = card.findViewById<TextView>(R.id.dateText)
        val priceText = card.findViewById<TextView>(R.id.priceText)
        val monthText = card.findViewById<TextView>(R.id.monthText)

        // Применяем темную тему к карточке
        if (isDark) {
            cardView?.setCardBackgroundColor(0xFF1C1F26.toInt())
            nameText.setTextColor(0xFFE7EAF0.toInt())
            ownerText.setTextColor(0xFF9CA3AF.toInt())
            dateText.setTextColor(0xFF9CA3AF.toInt())
            priceText.setTextColor(0xFFE7EAF0.toInt())
            monthText?.setTextColor(0xFF9CA3AF.toInt())
        } else {
            cardView?.setCardBackgroundColor(0xFFFFFFFF.toInt())
            nameText.setTextColor(0xFF1A1A1A.toInt())
            ownerText.setTextColor(0xFF666666.toInt())
            dateText.setTextColor(0xFF888888.toInt())
            priceText.setTextColor(0xFF1A1A1A.toInt())
            monthText?.setTextColor(0xFF999999.toInt())
        }

        val iconId = context.resources.getIdentifier(
            subscription.iconResName,
            "drawable",
            context.packageName
        )
        iconImage.setImageResource(if (iconId != 0) iconId else R.drawable.ic_default)

        nameText.text = subscription.name
        ownerText.text = if (subscription.ownerUsername == currentUsername) {
            "Для: вы"
        } else {
            "Для: ${subscription.ownerUsername}"
        }
        dateText.text = dateFormat.format(Date(subscription.nextPaymentDate))
        priceText.text = "${subscription.price}₽"

        card.setOnClickListener { onCardClick(subscription) }
        return card
    }
}

