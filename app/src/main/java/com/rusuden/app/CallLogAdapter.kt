package com.rusuden.app

import android.provider.CallLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallLogAdapter(
    private val onSelect: (CallLogEntry) -> Unit,
    private val onCall: (CallLogEntry) -> Unit
) : RecyclerView.Adapter<CallLogAdapter.Holder>() {

    private val items = mutableListOf<CallLogEntry>()
    private val dateFormat = SimpleDateFormat("M月d日 HH:mm", Locale.JAPAN)

    fun submit(entries: List<CallLogEntry>) {
        items.clear()
        items.addAll(entries)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        val name = item.cachedName?.takeIf { it.isNotBlank() }
            ?: ContactHelper.lookupName(context, item.number)
        holder.name.text = when {
            name != null -> name
            item.number.isNotEmpty() -> item.number
            else -> context.getString(R.string.unknown_caller)
        }
        holder.date.text = dateFormat.format(Date(item.date))
        holder.typeIcon.setImageResource(
            when (item.type) {
                CallLog.Calls.OUTGOING_TYPE -> android.R.drawable.sym_call_outgoing
                CallLog.Calls.MISSED_TYPE,
                CallLog.Calls.REJECTED_TYPE -> android.R.drawable.sym_call_missed
                else -> android.R.drawable.sym_call_incoming
            }
        )
        holder.itemView.setOnClickListener { onSelect(item) }
        holder.callButton.setOnClickListener { onCall(item) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val typeIcon: ImageView = view.findViewById(R.id.image_call_type)
        val name: TextView = view.findViewById(R.id.text_call_name)
        val date: TextView = view.findViewById(R.id.text_call_date)
        val callButton: ImageButton = view.findViewById(R.id.button_call_item)
    }
}
