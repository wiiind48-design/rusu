package com.rusuden.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * デフォルトの電話アプリとしてのダイヤル画面。発着信履歴とキーパッドを持つ。
 */
class DialerActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CALL = 200
        private const val REQUEST_LOG = 201
    }

    private lateinit var numberEdit: EditText
    private lateinit var emptyLogText: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: CallLogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialer)

        numberEdit = findViewById(R.id.edit_number)
        emptyLogText = findViewById(R.id.text_empty_log)
        recycler = findViewById(R.id.recycler_calllog)
        // キーパッドで入力するのでソフトキーボードは出さない
        numberEdit.showSoftInputOnFocus = false

        intent?.data?.schemeSpecificPart?.let { numberEdit.setText(it) }

        adapter = CallLogAdapter(
            onSelect = { entry -> numberEdit.setText(entry.number) },
            onCall = { entry ->
                numberEdit.setText(entry.number)
                placeCall()
            }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

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

        if (!CallLogHelper.hasPermission(this)) {
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
        refreshLog()
    }

    private fun refreshLog() {
        val entries = CallLogHelper.loadRecent(this)
        adapter.submit(entries)
        emptyLogText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
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
            REQUEST_LOG -> refreshLog()
        }
    }
}
