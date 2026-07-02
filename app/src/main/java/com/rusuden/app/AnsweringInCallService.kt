package com.rusuden.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.File
import java.util.Locale

class AnsweringInCallService : InCallService() {

    companion object {
        private const val TAG = "AnsweringService"
        private const val CHANNEL_INCOMING = "incoming_call"
        private const val CHANNEL_MESSAGES = "new_messages"
        private const val NOTIFY_ID_INCOMING = 1
        private const val NOTIFY_ID_MESSAGE = 2
        private const val GREETING_TIMEOUT_MS = 30_000L
        private const val BEEP_DURATION_MS = 600
    }

    private val handler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var toneGenerator: ToneGenerator? = null

    /** 自動応答で取った通話かどうか。手動応答なら応答メッセージ・録音は行わない。 */
    private var autoAnswered = false
    private var greetingStarted = false
    private var recordStarted = false

    private val autoAnswerRunnable = Runnable {
        val call = CallManager.currentCall ?: return@Runnable
        if (stateOf(call) == Call.STATE_RINGING) {
            autoAnswered = true
            call.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
    }

    @Suppress("DEPRECATION")
    private fun stateOf(call: Call): Int = call.state

    private val greetingTimeoutRunnable = Runnable {
        Log.w(TAG, "greeting timeout, starting recording anyway")
        playBeepThenRecord()
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            handleStateChanged(call, state)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.JAPAN
            }
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.currentCall = call
        CallManager.service = this
        autoAnswered = false
        greetingStarted = false
        recordStarted = false
        call.registerCallback(callCallback)

        val ringing = stateOf(call) == Call.STATE_RINGING
        if (ringing && Prefs.getEnabled(this) && hasRecordPermission()) {
            val delayMs = Prefs.getAnswerDelaySec(this) * 1000L
            CallManager.autoAnswerAtMillis = System.currentTimeMillis() + delayMs
            handler.postDelayed(autoAnswerRunnable, delayMs)
        } else {
            CallManager.autoAnswerAtMillis = 0
        }
        CallManager.notifyChanged()
        showInCallUi(ringing)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        handler.removeCallbacks(autoAnswerRunnable)
        handler.removeCallbacks(greetingTimeoutRunnable)
        stopTts()
        stopRecordingAndSave(call)
        cancelIncomingNotification()
        CallManager.currentCall = null
        CallManager.autoAnswerAtMillis = 0
        CallManager.isRecording = false
        CallManager.notifyChanged()
    }

    /** InCallActivity からの手動応答時に呼ばれ、自動応答タイマーを止める。 */
    fun cancelAutoAnswer() {
        handler.removeCallbacks(autoAnswerRunnable)
        CallManager.autoAnswerAtMillis = 0
        CallManager.notifyChanged()
    }

    private fun handleStateChanged(call: Call, state: Int) {
        when (state) {
            Call.STATE_ACTIVE -> {
                cancelIncomingNotification()
                if (autoAnswered && !greetingStarted) {
                    greetingStarted = true
                    startAnsweringFlow()
                }
            }
            Call.STATE_DISCONNECTED -> {
                handler.removeCallbacks(autoAnswerRunnable)
                handler.removeCallbacks(greetingTimeoutRunnable)
                stopTts()
                stopRecordingAndSave(call)
                cancelIncomingNotification()
            }
        }
        CallManager.notifyChanged()
    }

    // ----- 自動応答フロー: スピーカーON → 応答メッセージ → ピー音 → 録音 -----

    private fun startAnsweringFlow() {
        try {
            setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, max, 0)
        } catch (e: Exception) {
            Log.w(TAG, "failed to set audio route", e)
        }
        // 応答直後は音声経路が安定しないことがあるので少し待つ
        handler.postDelayed({ playGreeting() }, 1_000)
    }

    private fun playGreeting() {
        val engine = tts
        if (!ttsReady || engine == null) {
            playBeepThenRecord()
            return
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                handler.post { playBeepThenRecord() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handler.post { playBeepThenRecord() }
            }
        })
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL)
        }
        handler.postDelayed(greetingTimeoutRunnable, GREETING_TIMEOUT_MS)
        val result = engine.speak(
            Prefs.getGreetingText(this),
            TextToSpeech.QUEUE_FLUSH,
            params,
            "greeting"
        )
        if (result != TextToSpeech.SUCCESS) {
            handler.removeCallbacks(greetingTimeoutRunnable)
            playBeepThenRecord()
        }
    }

    private fun stopTts() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "tts stop failed", e)
        }
    }

    private fun playBeepThenRecord() {
        if (recordStarted) return
        recordStarted = true
        handler.removeCallbacks(greetingTimeoutRunnable)
        try {
            val tone = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 90)
            toneGenerator = tone
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, BEEP_DURATION_MS)
        } catch (e: Exception) {
            Log.w(TAG, "beep failed", e)
        }
        handler.postDelayed({
            toneGenerator?.release()
            toneGenerator = null
            startRecording()
        }, BEEP_DURATION_MS + 200L)
    }

    private fun startRecording() {
        val call = CallManager.currentCall ?: return
        if (stateOf(call) != Call.STATE_ACTIVE) return
        if (!hasRecordPermission()) return

        val number = call.details?.handle?.schemeSpecificPart ?: ""
        val file = MessageStore.newFile(this, number)
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )
        for (source in sources) {
            if (tryStartRecorder(source, file)) {
                recordingFile = file
                CallManager.isRecording = true
                CallManager.notifyChanged()
                return
            }
        }
        Log.e(TAG, "could not start recorder with any audio source")
        file.delete()
    }

    private fun tryStartRecorder(audioSource: Int, file: File): Boolean {
        var r: MediaRecorder? = null
        return try {
            r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(audioSource)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setMaxDuration(Prefs.getMaxRecordSec(this) * 1000)
            r.setOutputFile(file.absolutePath)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    CallManager.currentCall?.disconnect()
                }
            }
            r.prepare()
            r.start()
            recorder = r
            true
        } catch (e: Exception) {
            Log.w(TAG, "recorder failed with source=$audioSource", e)
            try {
                r?.release()
            } catch (ignored: Exception) {
            }
            false
        }
    }

    private fun stopRecordingAndSave(call: Call) {
        val r = recorder ?: return
        recorder = null
        CallManager.isRecording = false
        var ok = true
        try {
            r.stop()
        } catch (e: Exception) {
            // 録音が短すぎた等。ファイルは破棄する。
            ok = false
        }
        try {
            r.release()
        } catch (ignored: Exception) {
        }
        val file = recordingFile
        recordingFile = null
        if (file == null) return
        if (!ok || !file.exists() || file.length() == 0L) {
            file.delete()
            return
        }
        val number = call.details?.handle?.schemeSpecificPart
            ?: getString(R.string.unknown_caller)
        showNewMessageNotification(number)
    }

    private fun hasRecordPermission(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    // ----- 通知・着信UI -----

    private fun showInCallUi(ringing: Boolean) {
        val intent = Intent(this, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (ringing) {
            showIncomingNotification(intent)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "could not start in-call activity directly", e)
        }
    }

    private fun showIncomingNotification(contentIntent: Intent) {
        val pending = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val number = CallManager.callNumber().ifEmpty { getString(R.string.unknown_caller) }
        val notification = Notification.Builder(this, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.incoming_call))
            .setContentText(number)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setFullScreenIntent(pending, true)
            .setContentIntent(pending)
            .build()
        notificationManager().notify(NOTIFY_ID_INCOMING, notification)
    }

    private fun cancelIncomingNotification() {
        notificationManager().cancel(NOTIFY_ID_INCOMING)
    }

    private fun showNewMessageNotification(number: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.new_message_title))
            .setContentText(getString(R.string.new_message_text, number))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        notificationManager().notify(NOTIFY_ID_MESSAGE, notification)
    }

    private fun createNotificationChannels() {
        val nm = notificationManager()
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INCOMING,
                getString(R.string.channel_incoming),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES,
                getString(R.string.channel_messages),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
