package com.example.subtracker.presentation.main

import android.graphics.Color
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.subtracker.R
import kotlinx.coroutines.launch

/**
 * Привязывает UI элементы к ViewModel состоянию
 */
class MainFrameUiBinder(
    private val lifecycleOwner: LifecycleOwner,
    private val viewModel: MainFrameViewModel,
    private val btnSortPrice: TextView,
    private val btnSortDate: TextView,
    private val btnFilterUsers: TextView,
    private val onStateChanged: (MainFrameUiState) -> Unit
) {

    fun bind() {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    updateButtonsUI(state)
                    onStateChanged(state)
                }
            }
        }
    }

    private fun updateButtonsUI(state: MainFrameUiState) {
        val inactiveBg = R.drawable.chip_dg
        val activeBg = R.drawable.chip_dg_active
        val inactiveText = Color.parseColor("#E7EAF0")
        val activeText = Color.WHITE

        btnSortPrice.apply {
            val active = state.sortMode == MainFrameUiState.SortMode.PRICE
            setBackgroundResource(if (active) activeBg else inactiveBg)
            setTextColor(if (active) activeText else inactiveText)
            text = "Цена" + if (active) (if (state.sortAsc) " ↑" else " ↓") else ""
        }

        btnSortDate.apply {
            val active = state.sortMode == MainFrameUiState.SortMode.DATE
            setBackgroundResource(if (active) activeBg else inactiveBg)
            setTextColor(if (active) activeText else inactiveText)
            text = "Дата" + if (active) (if (state.sortAsc) " ↑" else " ↓") else ""
        }

        btnFilterUsers.apply {
            // Скрываем кнопку для не-админов
            visibility = if (state.isAdmin) View.VISIBLE else View.GONE
            
            val active = state.filterByUserEnabled
            setBackgroundResource(if (active) activeBg else inactiveBg)
            setTextColor(if (active) activeText else inactiveText)
            text = if (active) "Семья (фильтр)" else "Семья"
        }
    }
}

