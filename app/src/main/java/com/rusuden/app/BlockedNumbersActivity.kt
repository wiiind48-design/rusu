package com.rusuden.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class BlockedNumbersActivity : AppCompatActivity() {

    private lateinit var adapter: BlockedNumbersAdapter
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)

        emptyText = findViewById(R.id.text_blocked_empty)
        val numberEdit = findViewById<EditText>(R.id.edit_block_number)
        val recycler = findViewById<RecyclerView>(R.id.recycler_blocked)

        adapter = BlockedNumbersAdapter { entry -> confirmRemove(entry) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<Button>(R.id.button_block_add).setOnClickListener {
            val number = numberEdit.text.toString().trim()
            if (number.isEmpty()) return@setOnClickListener
            if (BlockedNumbersHelper.add(this, number)) {
                numberEdit.setText("")
                refresh()
            } else {
                Toast.makeText(this, R.string.blocked_error, Toast.LENGTH_SHORT).show()
            }
        }

        if (!BlockedNumbersHelper.canBlock(this)) {
            Toast.makeText(this, R.string.blocked_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val entries = BlockedNumbersHelper.list(this)
        adapter.submit(entries)
        emptyText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmRemove(entry: BlockedNumber) {
        AlertDialog.Builder(this)
            .setTitle(R.string.blocked_remove_title)
            .setMessage(getString(R.string.blocked_remove_message, entry.number))
            .setPositiveButton(R.string.blocked_remove_ok) { _, _ ->
                BlockedNumbersHelper.remove(this, entry.id)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
