package com.rusuden.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

/**
 * デフォルトの電話アプリになるために必要な最小限のダイヤル画面。
 */
class DialerActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CALL = 200
    }

    private lateinit var numberEdit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialer)

        numberEdit = findViewById(R.id.edit_number)
        intent?.data?.schemeSpecificPart?.let { numberEdit.setText(it) }

        findViewById<Button>(R.id.button_call).setOnClickListener { placeCall() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.schemeSpecificPart?.let { numberEdit.setText(it) }
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
        if (requestCode == REQUEST_CALL &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            placeCall()
        }
    }
}
