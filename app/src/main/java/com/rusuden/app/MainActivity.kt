package com.rusuden.app

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_ROLE = 100
        private const val REQUEST_PERMISSIONS = 101
    }

    private lateinit var statusText: TextView
    private lateinit var setupButton: Button
    private lateinit var enabledSwitch: SwitchCompat
    private lateinit var recycler: RecyclerView
    private lateinit var emptyText: TextView

    private lateinit var adapter: MessageAdapter
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.text_status)
        setupButton = findViewById(R.id.button_setup)
        enabledSwitch = findViewById(R.id.switch_enabled)
        recycler = findViewById(R.id.recycler_messages)
        emptyText = findViewById(R.id.text_empty)

        findViewById<ImageButton>(R.id.button_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        adapter = MessageAdapter(
            onPlay = { message -> togglePlay(message) },
            onDelete = { message -> confirmDelete(message) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        enabledSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.setEnabled(this, checked)
        }

        setupButton.setOnClickListener { runSetup() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshList()
    }

    override fun onPause() {
        super.onPause()
        stopPlayback()
    }

    // ----- セットアップ状態 -----

    private fun isDefaultDialer(): Boolean {
        val telecom = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return telecom.defaultDialerPackage == packageName
    }

    private fun hasRecordPermission(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun refreshStatus() {
        enabledSwitch.isChecked = Prefs.getEnabled(this)
        val dialerOk = isDefaultDialer()
        val recordOk = hasRecordPermission()
        when {
            !dialerOk -> {
                statusText.text = getString(R.string.status_need_dialer)
                setupButton.text = getString(R.string.button_set_default_dialer)
                setupButton.visibility = View.VISIBLE
            }
            !recordOk -> {
                statusText.text = getString(R.string.status_need_mic)
                setupButton.text = getString(R.string.button_grant_mic)
                setupButton.visibility = View.VISIBLE
            }
            else -> {
                statusText.text = if (Prefs.getEnabled(this)) {
                    getString(R.string.status_ready)
                } else {
                    getString(R.string.status_disabled)
                }
                setupButton.visibility = View.GONE
            }
        }
    }

    private fun runSetup() {
        if (!isDefaultDialer()) {
            requestDefaultDialerRole()
        } else if (!hasRecordPermission()) {
            requestPermissions()
        }
    }

    private fun requestDefaultDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                @Suppress("DEPRECATION")
                startActivityForResult(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER),
                    REQUEST_ROLE
                )
            }
        } else {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            startActivity(intent)
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_PERMISSIONS)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ROLE) {
            refreshStatus()
            if (isDefaultDialer() && !hasRecordPermission()) {
                requestPermissions()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
    }

    // ----- 伝言リスト -----

    private fun refreshList() {
        val messages = MessageStore.list(this)
        adapter.submit(messages)
        emptyText.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun togglePlay(message: VoiceMessage) {
        if (adapter.playingFile == message.file) {
            stopPlayback()
            return
        }
        stopPlayback()
        try {
            val p = MediaPlayer()
            p.setDataSource(message.file.absolutePath)
            p.setOnCompletionListener { stopPlayback() }
            p.prepare()
            p.start()
            player = p
            adapter.playingFile = message.file
            adapter.notifyDataSetChanged()
        } catch (e: Exception) {
            stopPlayback()
        }
    }

    private fun stopPlayback() {
        try {
            player?.stop()
            player?.release()
        } catch (ignored: Exception) {
        }
        player = null
        if (adapter.playingFile != null) {
            adapter.playingFile = null
            adapter.notifyDataSetChanged()
        }
    }

    private fun confirmDelete(message: VoiceMessage) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_message)
            .setPositiveButton(R.string.delete_ok) { _, _ ->
                if (adapter.playingFile == message.file) stopPlayback()
                message.file.delete()
                refreshList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
