package com.example.subtracker.presentation.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.subtracker.FirebaseSubscription
import com.example.subtracker.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubscriptionAdapter(
    private val packageNameStr: String,
    private val onClick: (FirebaseSubscription) -> Unit,
    private val username: String
) : ListAdapter<FirebaseSubscription, SubscriptionAdapter.VH>(DIFF) {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.sub_card_item, parent, false)
        return VH(v, packageNameStr, dateFormat, onClick, username)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        itemView: View,
        private val packageNameStr: String,
        private val dateFormat: SimpleDateFormat,
        private val onClick: (FirebaseSubscription) -> Unit,
        private val username: String
    ) : RecyclerView.ViewHolder(itemView) {

        private val iconImage = itemView.findViewById<ImageView>(R.id.iconImage)
        private val nameText = itemView.findViewById<TextView>(R.id.nameText)
        private val ownerText = itemView.findViewById<TextView>(R.id.ownerText)
        private val dateText = itemView.findViewById<TextView>(R.id.dateText)
        private val priceText = itemView.findViewById<TextView>(R.id.priceText)

        fun bind(sub: FirebaseSubscription) {
            val res = itemView.resources
            val iconId = res.getIdentifier(sub.iconResName, "drawable", packageNameStr)
            iconImage.setImageResource(if (iconId != 0) iconId else R.drawable.ic_default)

            nameText.text = sub.name
            ownerText.text = if (sub.ownerUsername == username) "Для: вы" else "Для: ${sub.ownerUsername}"
            dateText.text = dateFormat.format(Date(sub.nextPaymentDate))
            priceText.text = "${sub.price}₽"

            itemView.setOnClickListener { onClick(sub) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FirebaseSubscription>() {
            override fun areItemsTheSame(oldItem: FirebaseSubscription, newItem: FirebaseSubscription): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: FirebaseSubscription, newItem: FirebaseSubscription): Boolean =
                oldItem == newItem
        }
    }
}
