package com.rusuden.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BlockedNumbersAdapter(
    private val onRemove: (BlockedNumber) -> Unit
) : RecyclerView.Adapter<BlockedNumbersAdapter.Holder>() {

    private val items = mutableListOf<BlockedNumber>()

    fun submit(entries: List<BlockedNumber>) {
        items.clear()
        items.addAll(entries)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blocked, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.number.text = item.number
        holder.name.text = ContactHelper.lookupName(holder.itemView.context, item.number) ?: ""
        holder.remove.setOnClickListener { onRemove(item) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val number: TextView = view.findViewById(R.id.text_blocked_number)
        val name: TextView = view.findViewById(R.id.text_blocked_name)
        val remove: ImageButton = view.findViewById(R.id.button_blocked_remove)
    }
}
