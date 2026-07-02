package com.rusuden.app

import android.telecom.Call
import android.telecom.CallAudioState
import java.util.concurrent.CopyOnWriteArrayList

/**
 * InCallService と InCallActivity の間で現在の通話状態を共有するためのシングルトン。
 */
object CallManager {
    @Volatile
    var currentCall: Call? = null

    @Volatile
    var service: AnsweringInCallService? = null

    /** 自動応答予定時刻 (epoch millis)。0 なら自動応答なし。 */
    @Volatile
    var autoAnswerAtMillis: Long = 0

    @Volatile
    var isRecording: Boolean = false

    @Volatile
    var audioState: CallAudioState? = null

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChanged() {
        for (listener in listeners) {
            listener()
        }
    }

    @Suppress("DEPRECATION")
    fun callState(): Int = currentCall?.state ?: Call.STATE_DISCONNECTED

    fun callNumber(): String =
        currentCall?.details?.handle?.schemeSpecificPart ?: ""
}
