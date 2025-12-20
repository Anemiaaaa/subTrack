package com.example.subtracker.presentation.add

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import com.example.subtracker.R
import com.example.subtracker.ServiceItem
import com.example.subtracker.buildServiceItems

/**
 * Управляет поиском и выбором сервисов
 */
class ServiceSearchManager(
    private val context: Context,
    private val isDark: Boolean,
    private val searchInput: EditText,
    private val iconSpinner: Spinner,
    private val nameInput: EditText,
    private val onNameAutoFill: (String) -> Unit
) {
    private val allServices: List<ServiceItem> = buildServiceItems()
    private var currentItems: List<ServiceItem> = allServices

    init {
        setupSpinner()
        setupSearch()
    }

    private fun setupSpinner() {
        setSpinnerData(allServices)
        searchInput.setText("")
        searchInput.clearFocus()

        iconSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val item = currentItems.getOrNull(position) ?: return
                if (nameInput.text.toString().trim().isEmpty()) {
                    onNameAutoFill(item.defaultName)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                val filtered = if (query.isEmpty()) {
                    allServices
                } else {
                    allServices.filter {
                        it.label.contains(query, ignoreCase = true) ||
                                it.defaultName.contains(query, ignoreCase = true)
                    }
                }
                setSpinnerData(filtered)
                if (filtered.size == 1) {
                    iconSpinner.setSelection(0)
                    if (nameInput.text.toString().trim().isEmpty()) {
                        onNameAutoFill(filtered[0].defaultName)
                    }
                }
            }
        })
    }

    private fun setSpinnerData(items: List<ServiceItem>) {
        currentItems = items
        val adapter = if (isDark) {
            ArrayAdapter(context, R.layout.spinner_item_dark, items.map { it.label })
                .apply { setDropDownViewResource(R.layout.spinner_item_dark) }
        } else {
            ArrayAdapter(context, android.R.layout.simple_spinner_item, items.map { it.label })
                .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }
        iconSpinner.adapter = adapter
    }

    fun getSelectedService(): ServiceItem? {
        return currentItems.getOrNull(iconSpinner.selectedItemPosition)
    }
}

