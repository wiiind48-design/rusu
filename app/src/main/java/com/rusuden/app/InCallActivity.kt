package com.rusuden.app

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.VideoProfile
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class InCallActivity : AppCompatActivity() {

    private lateinit var numberText: TextView
    private lateinit var stateText: TextView
    private lateinit var answerButton: Button
    private lateinit var hangupButton: Button

    private val handler = Handler(Looper.getMainLooper())

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

        numberText = findViewById(R.id.text_number)
        stateText = findViewById(R.id.text_state)
        answerButton = findViewById(R.id.button_answer)
        hangupButton = findViewById(R.id.button_hangup)

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
        val number = CallManager.callNumber().ifEmpty { getString(R.string.unknown_caller) }
        numberText.text = number

        val state = CallManager.callState()
        answerButton.isEnabled = state == Call.STATE_RINGING
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
            Call.STATE_ACTIVE ->
                if (CallManager.isRecording) getString(R.string.state_recording)
                else getString(R.string.state_active)
            Call.STATE_DISCONNECTED -> getString(R.string.state_disconnected)
            else -> getString(R.string.state_other)
        }
    }
}
