package com.rusuden.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * デフォルトの電話アプリとしてのダイヤル画面。
 * 発着信履歴・連絡先(お気に入り優先)・キーパッドを持つ。
 */
class DialerActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CALL = 200
        private const val REQUEST_LOG = 201
    }

    private lateinit var numberEdit: EditText
    private lateinit var searchEdit: EditText
    private lateinit var emptyLogText: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var historyTab: Button
    private lateinit var contactsTab: Button

    private lateinit var callLogAdapter: CallLogAdapter
    private lateinit var contactsAdapter: ContactsAdapter
    private var showingContacts = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialer)

        numberEdit = findViewById(R.id.edit_number)
        searchEdit = findViewById(R.id.edit_search)
        emptyLogText = findViewById(R.id.text_empty_log)
        recycler = findViewById(R.id.recycler_calllog)
        historyTab = findViewById(R.id.tab_history)
        contactsTab = findViewById(R.id.tab_contacts)
        // 番号はキーパッドで入力するのでソフトキーボードは出さない
        numberEdit.showSoftInputOnFocus = false

        intent?.data?.schemeSpecificPart?.let { numberEdit.setText(it) }

        callLogAdapter = CallLogAdapter(
            onSelect = { entry -> numberEdit.setText(entry.number) },
            onCall = { entry ->
                numberEdit.setText(entry.number)
                placeCall()
            },
            onLongPress = { entry -> confirmBlock(entry.number) }
        )
        contactsAdapter = ContactsAdapter(
            onSelect = { entry -> numberEdit.setText(entry.number) },
            onCall = { entry ->
                numberEdit.setText(entry.number)
                placeCall()
            }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = callLogAdapter

        historyTab.setOnClickListener { switchTab(contacts = false) }
        contactsTab.setOnClickListener { switchTab(contacts = true) }

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                contactsAdapter.filter(s?.toString() ?: "")
            }
        })

        val dialpad = findViewById<View>(R.id.dialer_dialpad)
        Dialpad.bind(dialpad) { ch -> numberEdit.append(ch.toString()) }
        Dialpad.bindPlusOnZeroLongPress(dialpad) { numberEdit.append("+") }

        findViewById<Button>(R.id.button_call).setOnClickListener { placeCall() }
        val backspace = findViewById<Button>(R.id.button_backspace)
        backspace.setOnClickListener {
            val text = numberEdit.text.toString()
            if (text.isNotEmpty()) {
                numberEdit.setText(text.substring(0, text.length - 1))
                numberEdit.setSelection(numberEdit.text.length)
            }
        }
        backspace.setOnLongClickListener {
            numberEdit.setText("")
            true
        }

        switchTab(contacts = false)

        if (!CallLogHelper.hasPermission(this) || !ContactHelper.hasContactsPermission(this)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS),
                REQUEST_LOG
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.schemeSpecificPart?.let { numberEdit.setText(it) }
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    private fun switchTab(contacts: Boolean) {
        showingContacts = contacts
        historyTab.isEnabled = contacts
        contactsTab.isEnabled = !contacts
        searchEdit.visibility = if (contacts) View.VISIBLE else View.GONE
        recycler.adapter = if (contacts) contactsAdapter else callLogAdapter
        refreshData()
    }

    private fun refreshData() {
        if (showingContacts) {
            val contacts = ContactHelper.loadContacts(this)
            contactsAdapter.submit(contacts)
            contactsAdapter.filter(searchEdit.text.toString())
            emptyLogText.setText(R.string.no_contacts)
            emptyLogText.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE
        } else {
            val entries = CallLogHelper.loadRecent(this)
            callLogAdapter.submit(entries)
            emptyLogText.setText(R.string.no_history)
            emptyLogText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun confirmBlock(number: String) {
        if (number.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.block_title)
            .setMessage(getString(R.string.block_message, number))
            .setPositiveButton(R.string.block_ok) { _, _ ->
                if (BlockedNumbersHelper.add(this, number)) {
                    Toast.makeText(this, R.string.block_done, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.blocked_error, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun placeCall() {
        val number = numberEdit.text.toString().trim()
        if (number.isEmpty()) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL
            )
            return
        }
        try {
            val telecom = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecom.placeCall(Uri.fromParts("tel", number, null), null)
        } catch (e: SecurityException) {
            Toast.makeText(this, R.string.error_call, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CALL -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    placeCall()
                }
            }
            REQUEST_LOG -> refreshData()
        }
    }
}
