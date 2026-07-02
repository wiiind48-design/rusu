package com.rusuden.app

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class InCallActivity : AppCompatActivity() {

    private lateinit var nameText: TextView
    private lateinit var numberText: TextView
    private lateinit var stateText: TextView
    private lateinit var dtmfText: TextView
    private lateinit var answerButton: Button
    private lateinit var hangupButton: Button
    private lateinit var muteButton: ToggleButton
    private lateinit var speakerButton: ToggleButton
    private lateinit var keypadButton: ToggleButton
    private lateinit var controlsRow: View
    private lateinit var dialpadView: View

    private val handler = Handler(Looper.getMainLooper())
    private var lastNumber: String? = null
    private val dtmfDigits = StringBuilder()

    private val callListener: () -> Unit = {
        handler.post { refresh() }
    }

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incall)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        nameText = findViewById(R.id.text_name)
        numberText = findViewById(R.id.text_number)
        stateText = findViewById(R.id.text_state)
        dtmfText = findViewById(R.id.text_dtmf)
        answerButton = findViewById(R.id.button_answer)
        hangupButton = findViewById(R.id.button_hangup)
        muteButton = findViewById(R.id.button_mute)
        speakerButton = findViewById(R.id.button_speaker)
        keypadButton = findViewById(R.id.button_keypad)
        controlsRow = findViewById(R.id.controls_row)
        dialpadView = findViewById(R.id.incall_dialpad)

        answerButton.setOnClickListener {
            CallManager.service?.cancelAutoAnswer()
            CallManager.currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
        hangupButton.setOnClickListener {
            val call = CallManager.currentCall
            if (call != null && CallManager.callState() == Call.STATE_RINGING) {
                call.reject(false, null)
            } else {
                call?.disconnect()
            }
        }
        muteButton.setOnClickListener {
            CallManager.service?.setMute(muteButton.isChecked)
        }
        speakerButton.setOnClickListener {
            CallManager.service?.setSpeaker(speakerButton.isChecked)
        }
        keypadButton.setOnClickListener {
            dialpadView.visibility = if (keypadButton.isChecked) View.VISIBLE else View.GONE
        }

        Dialpad.bind(dialpadView) { ch -> sendDtmf(ch) }
    }

    private fun sendDtmf(ch: Char) {
        val call = CallManager.currentCall ?: return
        if (CallManager.callState() != Call.STATE_ACTIVE) return
        try {
            call.playDtmfTone(ch)
            handler.postDelayed({
                try {
                    call.stopDtmfTone()
                } catch (ignored: Exception) {
                }
            }, 150)
            dtmfDigits.append(ch)
            dtmfText.text = dtmfDigits.toString()
        } catch (ignored: Exception) {
        }
    }

    override fun onStart() {
        super.onStart()
        CallManager.addListener(callListener)
        handler.post(ticker)
    }

    override fun onStop() {
        super.onStop()
        CallManager.removeListener(callListener)
        handler.removeCallbacks(ticker)
    }

    private fun refresh() {
        val call = CallManager.currentCall
        if (call == null) {
            finish()
            return
        }
        val number = CallManager.callNumber()
        if (number != lastNumber) {
            lastNumber = number
            val name = ContactHelper.displayName(this, number)
            nameText.text = name
            numberText.text = if (name != number && number.isNotEmpty()) number else ""
        }

        val state = CallManager.callState()
        val active = state == Call.STATE_ACTIVE
        answerButton.visibility = if (state == Call.STATE_RINGING) View.VISIBLE else View.GONE
        controlsRow.visibility = if (active) View.VISIBLE else View.GONE
        if (!active && dialpadView.visibility == View.VISIBLE) {
            dialpadView.visibility = View.GONE
            keypadButton.isChecked = false
        }

        val audio = CallManager.audioState
        if (audio != null) {
            muteButton.isChecked = audio.isMuted
            speakerButton.isChecked = audio.route == CallAudioState.ROUTE_SPEAKER
        }

        stateText.text = when (state) {
            Call.STATE_RINGING -> {
                val at = CallManager.autoAnswerAtMillis
                if (at > 0) {
                    val remain = ((at - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                    getString(R.string.state_ringing_auto, remain)
                } else {
                    getString(R.string.state_ringing)
                }
            }
            Call.STATE_DIALING -> getString(R.string.state_dialing)
            Call.STATE_ACTIVE -> {
                val base = if (CallManager.isRecording) {
                    getString(R.string.state_recording)
                } else {
                    getString(R.string.state_active)
                }
                val connectedAt = call.details?.connectTimeMillis ?: 0L
                if (connectedAt > 0) {
                    val sec = ((System.currentTimeMillis() - connectedAt) / 1000).coerceAtLeast(0)
                    String.format(Locale.JAPAN, "%s  %d:%02d", base, sec / 60, sec % 60)
                } else {
                    base
                }
            }
            Call.STATE_DISCONNECTED -> getString(R.string.state_disconnected)
            else -> getString(R.string.state_other)
        }
    }
}
