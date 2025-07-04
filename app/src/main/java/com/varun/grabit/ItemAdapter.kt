package com.varun.grabit

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class ItemAdapter(
    val items: MutableList<Items>,
    val onItemChecked: (Items, Int, Boolean) -> Unit,
    val onItemDeleted: (Items, Int) -> Unit
) : RecyclerView.Adapter<ItemAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)
        private val textItemName: TextView = itemView.findViewById(R.id.textItemName)
        private val textQuantity: TextView = itemView.findViewById(R.id.textQuantity)
        private val textPrice: TextView = itemView.findViewById(R.id.textPrice)
        private val buttonDelete: ImageButton = itemView.findViewById(R.id.buttonDelete)

        @SuppressLint("SetTextI18n", "DefaultLocale")
        fun bind(item: Items, position: Int) {
            textItemName.text = item.name
            textQuantity.text = "Quantity: ${item.quantity}"
            textPrice.text = String.format("%.2f", item.price)

            // Clear previous listener to prevent overlap
            checkBox.setOnCheckedChangeListener(null)
            // Set checkbox state first for immediate UI response
            checkBox.isChecked = item.isChecked
            checkBox.isEnabled = true
            // Set listener before updating state
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                Log.d("ItemAdapter", "Checkbox for ${item.name} at position $position changed to $isChecked")
                checkBox.isEnabled = false
                onItemChecked(item, position, isChecked)
                checkBox.postDelayed({ checkBox.isEnabled = true }, 1000)
            }
            // Set checkbox state after listener to avoid resetting user input
            checkBox.isChecked = item.isChecked

            buttonDelete.setOnClickListener {
                onItemDeleted(item, position)
            }
        }
    }

    fun updateItems(newItems: List<Items>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = items.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean =
                items[oldPos].id == newItems[newPos].id
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
                items[oldPos] == newItems[newPos]
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size
}