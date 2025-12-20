package com.example.subtracker.presentation.main

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.*
import com.google.android.material.textfield.TextInputEditText
import com.example.subtracker.R
import com.example.subtracker.ServiceItem
import com.example.subtracker.buildServiceItems
import com.example.subtracker.domain.model.Subscription
import java.text.SimpleDateFormat
import java.util.*

/**
 * Управляет диалогами редактирования и действий с подписками
 */
class SubscriptionDialogManager(
    private val context: Context,
    private val onEdit: (Subscription) -> Unit,
    private val onPay: (Subscription) -> Unit,
    private val onDelete: (Subscription) -> Unit,
    private val onUpdate: (
        subscription: Subscription,
        newName: String,
        newPrice: Double,
        newPeriodicity: String,
        newIconResName: String,
        newNextPaymentDate: Long
    ) -> Unit
) {
    private val inflater = LayoutInflater.from(context)

    fun showActionsDialog(subscription: Subscription) {
        val items = arrayOf("Редактировать", "Оплатить", "Удалить")
        AlertDialog.Builder(context)
            .setTitle(subscription.name)
            .setItems(items) { dialog, which ->
                when (which) {
                    0 -> showEditDialog(subscription)
                    1 -> onPay(subscription)
                    2 -> showDeleteConfirmationDialog(subscription)
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun showDeleteConfirmationDialog(subscription: Subscription) {
        AlertDialog.Builder(context)
            .setTitle("Удалить подписку?")
            .setMessage("Вы уверены, что хотите удалить ${subscription.name}?")
            .setPositiveButton("Удалить") { _, _ -> onDelete(subscription) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    fun showEditDialog(subscription: Subscription) {
        val dialogView = inflater.inflate(R.layout.dialog_edit_subscription, null)

        val searchInput = dialogView.findViewById<EditText>(R.id.editTextServiceSearch)
        val serviceSpinner = dialogView.findViewById<Spinner>(R.id.spinnerService)
        val iconPreview = dialogView.findViewById<ImageView>(R.id.serviceIconPreview)
        val nameInput = dialogView.findViewById<EditText>(R.id.editTextName)
        val priceInput = dialogView.findViewById<EditText>(R.id.editTextPrice)
        val periodSpinner = dialogView.findViewById<Spinner>(R.id.spinnerPeriodicity)
        val dateInput = dialogView.findViewById<TextInputEditText>(R.id.editTextNextPaymentDate)
        val saveButton = dialogView.findViewById<Button>(R.id.buttonSave)

        val allServices = buildServiceItems()
        nameInput.setText(subscription.name)
        priceInput.setText(subscription.price.toString())
        
        // Настройка даты следующего платежа
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val calendar = Calendar.getInstance().apply {
            timeInMillis = subscription.nextPaymentDate
        }
        dateInput.setText(dateFormat.format(calendar.time))
        
        dateInput.setOnClickListener {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    dateInput.setText(dateFormat.format(calendar.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val periodicityOptions = context.resources.getStringArray(R.array.periodicity_options).toList()
        val periodIndex = periodicityOptions.indexOfFirst { 
            it.equals(subscription.periodicity, ignoreCase = true) 
        }
        if (periodIndex >= 0) periodSpinner.setSelection(periodIndex)

        var currentItems: List<ServiceItem> = allServices

        fun resolveIconResId(iconResName: String): Int {
            val id = context.resources.getIdentifier(iconResName, "drawable", context.packageName)
            return if (id != 0) id else R.drawable.ic_default
        }

        fun setIconPreviewByItem(item: ServiceItem?) {
            val resId = resolveIconResId(item?.iconResName ?: subscription.iconResName)
            iconPreview.setImageResource(resId)
        }

        fun setSpinner(items: List<ServiceItem>, tryKeepIconResName: String? = null) {
            currentItems = items
            val adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_item,
                items.map { it.label }
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            serviceSpinner.adapter = adapter

            val target = tryKeepIconResName ?: subscription.iconResName
            val idx = items.indexOfFirst { it.iconResName == target }
            if (idx >= 0) serviceSpinner.setSelection(idx)
            else if (items.isNotEmpty()) serviceSpinner.setSelection(0)

            setIconPreviewByItem(currentItems.getOrNull(serviceSpinner.selectedItemPosition))
        }

        setSpinner(allServices, subscription.iconResName)
        searchInput.setText("")
        searchInput.clearFocus()

        serviceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                val item = currentItems.getOrNull(position)
                setIconPreviewByItem(item)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                val keep = currentItems.getOrNull(serviceSpinner.selectedItemPosition)?.iconResName
                    ?: subscription.iconResName
                val filtered = if (query.isEmpty()) {
                    allServices
                } else {
                    allServices.filter {
                        it.label.contains(query, ignoreCase = true) ||
                                it.defaultName.contains(query, ignoreCase = true)
                    }
                }
                setSpinner(filtered, keep)
            }
        })

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()
        dialog.show()

        saveButton.setOnClickListener {
            val newName = nameInput.text.toString().trim().ifEmpty { subscription.name }
            val newPrice = priceInput.text.toString().toDoubleOrNull() ?: subscription.price
            val newPeriod = periodSpinner.selectedItem?.toString() ?: subscription.periodicity
            val selected = currentItems.getOrNull(serviceSpinner.selectedItemPosition)
            val newIconResName = selected?.iconResName ?: subscription.iconResName
            
            // Получаем выбранную дату или используем текущую
            val newNextPaymentDate = try {
                val dateStr = dateInput.text.toString()
                if (dateStr.isNotEmpty()) {
                    dateFormat.parse(dateStr)?.time ?: subscription.nextPaymentDate
                } else {
                    subscription.nextPaymentDate
                }
            } catch (e: Exception) {
                subscription.nextPaymentDate
            }

            onUpdate(subscription, newName, newPrice, newPeriod, newIconResName, newNextPaymentDate)
            dialog.dismiss()
        }
    }
}

