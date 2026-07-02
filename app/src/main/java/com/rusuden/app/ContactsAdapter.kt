package com.rusuden.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class ContactsAdapter(
    private val onSelect: (ContactEntry) -> Unit,
    private val onCall: (ContactEntry) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.Holder>() {

    private val all = mutableListOf<ContactEntry>()
    private val visible = mutableListOf<ContactEntry>()
    private var query: String = ""

    fun submit(entries: List<ContactEntry>) {
        all.clear()
        all.addAll(entries)
        applyFilter()
    }

    fun filter(text: String) {
        query = text.trim().lowercase(Locale.JAPAN)
        applyFilter()
    }

    private fun applyFilter() {
        visible.clear()
        if (query.isEmpty()) {
            visible.addAll(all)
        } else {
            visible.addAll(all.filter {
                it.name.lowercase(Locale.JAPAN).contains(query) ||
                    it.number.replace(Regex("[^0-9+]"), "").contains(query)
            })
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = visible.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = visible[position]
        holder.name.text = item.name
        holder.number.text = item.number
        holder.star.visibility = if (item.starred) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onSelect(item) }
        holder.callButton.setOnClickListener { onCall(item) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val star: ImageView = view.findViewById(R.id.image_contact_star)
        val name: TextView = view.findViewById(R.id.text_contact_name)
        val number: TextView = view.findViewById(R.id.text_contact_number)
        val callButton: ImageButton = view.findViewById(R.id.button_contact_call)
    }
}
