package com.rusuden.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val delayEdit = findViewById<EditText>(R.id.edit_delay)
        val maxEdit = findViewById<EditText>(R.id.edit_max_record)
        val greetingEdit = findViewById<EditText>(R.id.edit_greeting)
        val saveButton = findViewById<Button>(R.id.button_save)

        delayEdit.setText(Prefs.getAnswerDelaySec(this).toString())
        maxEdit.setText(Prefs.getMaxRecordSec(this).toString())
        greetingEdit.setText(Prefs.getGreetingText(this))

        saveButton.setOnClickListener {
            val delay = delayEdit.text.toString().toIntOrNull()
            val max = maxEdit.text.toString().toIntOrNull()
            val greeting = greetingEdit.text.toString().trim()

            if (delay == null || delay < 0 || delay > 120) {
                Toast.makeText(this, R.string.error_delay, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (max == null || max < 10 || max > 600) {
                Toast.makeText(this, R.string.error_max_record, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (greeting.isEmpty()) {
                Toast.makeText(this, R.string.error_greeting, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Prefs.setAnswerDelaySec(this, delay)
            Prefs.setMaxRecordSec(this, max)
            Prefs.setGreetingText(this, greeting)
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
