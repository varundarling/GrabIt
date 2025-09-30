package com.varun.grabit

import android.annotation.SuppressLint
import android.graphics.Paint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class ItemAdapter(
    private val items: MutableList<Items>,
    private val onItemClick: (Items, Int) -> Unit,
    private val onItemLongClick: (Items, Int) -> Boolean,
    private val onDeleteClick: (Items) -> Unit,
    private val interactionListener: OnInteractionListener
) : RecyclerView.Adapter<ItemAdapter.ViewHolder>() {

    private var isSelectionMode = false
    private val selectedItemIds = mutableSetOf<String>()

    interface OnInteractionListener {
        fun onSelectionChanged(selectedCount: Int)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textItemName: TextView = itemView.findViewById(R.id.textItemName)
        private val textQuantity: TextView = itemView.findViewById(R.id.textQuantity)
        private val textPrice: TextView = itemView.findViewById(R.id.textPrice)
        private val buttonDelete: ImageButton = itemView.findViewById(R.id.buttonDelete)
        private val completionBadge: TextView? = itemView.findViewById(R.id.completionBadge)
        private val selectionCheckbox: ImageView = itemView.findViewById(R.id.selectionCheckBox)

        @SuppressLint("SetTextI18n", "DefaultLocale")
        fun bind(item: Items, position: Int) {
            textItemName.text = item.name
            textQuantity.text = "Qty: ${item.quantity}"
            val totalPrice = item.price * item.quantity
            textPrice.text = "₹${String.format("%.2f", totalPrice)}"

            if (isSelectionMode) {
                setupSelectionMode(item)
            } else {
                setupNormalMode(item)
            }

            setupClickListeners(item, position)
        }

        private fun setupSelectionMode(item: Items) {
            selectionCheckbox.visibility = View.VISIBLE
            buttonDelete.visibility = View.GONE
            completionBadge?.visibility = View.GONE

            val isSelected = selectedItemIds.contains(item.id)

            if (isSelected) {
                selectionCheckbox.setImageResource(R.drawable.check_box_selected)
                itemView.setBackgroundResource(R.drawable.selected_item_background)
                Log.d("GrabIt", "✅ ${item.name}: Showing TICK MARK")
            } else {
                selectionCheckbox.setImageResource(R.drawable.checkbox_unselected)
                itemView.setBackgroundColor(
                    ContextCompat.getColor(itemView.context, android.R.color.white)
                )
                Log.d("GrabIt", "⭕ ${item.name}: Showing EMPTY CIRCLE")
            }

            setTextOpacity(1.0f)
            removeStrikeThrough()
            textItemName.setTextColor(
                ContextCompat.getColor(itemView.context, R.color.text_primary)
            )

            selectionCheckbox.setOnClickListener {
                onItemClick(item, position)
            }
        }

        @SuppressLint("SetTextI18n")
        private fun setupNormalMode(item: Items) {
            selectionCheckbox.visibility = View.GONE
            buttonDelete.visibility = View.VISIBLE

            itemView.setBackgroundColor(
                ContextCompat.getColor(itemView.context, android.R.color.white)
            )

            if (item.isChecked) {
                completionBadge?.let {
                    it.visibility = View.VISIBLE
                    it.text = "✓ DONE"
                    it.setBackgroundResource(R.drawable.completion_badge)
                }

                setTextOpacity(0.6f)
                addStrikeThrough()
                textItemName.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.text_completed)
                )
            } else {
                completionBadge?.visibility = View.GONE
                setTextOpacity(1.0f)
                removeStrikeThrough()
                textItemName.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.text_primary)
                )
            }
        }

        private fun setupClickListeners(item: Items, position: Int) {
            itemView.setOnClickListener {
                onItemClick(item, position)
            }

            itemView.setOnLongClickListener {
                onItemLongClick(item, position)
            }

            buttonDelete.setOnClickListener {
                onDeleteClick(item)
            }
        }

        private fun setTextOpacity(alpha: Float) {
            textItemName.alpha = alpha
            textQuantity.alpha = alpha
            textPrice.alpha = alpha
        }

        private fun addStrikeThrough() {
            textItemName.paintFlags = textItemName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }

        private fun removeStrikeThrough() {
            textItemName.paintFlags = textItemName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setSelectionMode(enabled: Boolean) {
        Log.d("GrabIt", "🔘 Selection mode: $enabled")
        isSelectionMode = enabled
        if (!enabled) {
            selectedItemIds.clear()
        }
        notifyDataSetChanged()
    }

    fun setItemSelected(itemId: String, selected: Boolean) {
        Log.d("GrabIt", "🎯 Setting item $itemId selected: $selected")

        val wasSelected = selectedItemIds.contains(itemId)

        if (selected && !wasSelected) {
            selectedItemIds.add(itemId)
            Log.d("GrabIt", "✅ Added $itemId to selection")
        } else if (!selected && wasSelected) {
            selectedItemIds.remove(itemId)
            Log.d("GrabIt", "❌ Removed $itemId from selection")
        }

        val position = items.indexOfFirst { it.id == itemId }
        if (position != -1) {
            notifyItemChanged(position)
            Log.d("GrabIt", "🔄 Updated UI for item at position $position")
        }

        Log.d("GrabIt", "📊 Total selected items: ${selectedItemIds.size}")

        interactionListener.onSelectionChanged(selectedItemIds.size)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun selectAll() {
        selectedItemIds.clear()
        items.forEach { selectedItemIds.add(it.id) }
        Log.d("GrabIt", "📋 Selected all ${items.size} items")
        notifyDataSetChanged()
        interactionListener.onSelectionChanged(selectedItemIds.size)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearSelection() {
        Log.d("GrabIt", "🧹 Clearing all selections")
        selectedItemIds.clear()
        notifyDataSetChanged()
        interactionListener.onSelectionChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position < items.size) {
            holder.bind(items[position], position)
        }
    }

    override fun getItemCount(): Int = items.size
}
