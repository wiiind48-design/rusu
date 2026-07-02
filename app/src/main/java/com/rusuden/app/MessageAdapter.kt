package com.rusuden.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val onPlay: (VoiceMessage) -> Unit,
    private val onDelete: (VoiceMessage) -> Unit
) : RecyclerView.Adapter<MessageAdapter.Holder>() {

    private val items = mutableListOf<VoiceMessage>()
    var playingFile: File? = null

    private val dateFormat = SimpleDateFormat("M月d日 HH:mm", Locale.JAPAN)

    fun submit(messages: List<VoiceMessage>) {
        items.clear()
        items.addAll(messages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.number.text = if (item.number == MessageStore.UNKNOWN_NUMBER || item.number.isEmpty()) {
            context.getString(R.string.unknown_caller)
        } else {
            item.number
        }
        holder.date.text = dateFormat.format(Date(item.timestamp))
        val totalSec = item.durationMs / 1000
        holder.duration.text = String.format(Locale.JAPAN, "%d:%02d", totalSec / 60, totalSec % 60)

        val playing = playingFile == item.file
        holder.play.setImageResource(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
        holder.play.setOnClickListener { onPlay(item) }
        holder.delete.setOnClickListener { onDelete(item) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val number: TextView = view.findViewById(R.id.text_item_number)
        val date: TextView = view.findViewById(R.id.text_item_date)
        val duration: TextView = view.findViewById(R.id.text_item_duration)
        val play: ImageButton = view.findViewById(R.id.button_item_play)
        val delete: ImageButton = view.findViewById(R.id.button_item_delete)
    }
}
