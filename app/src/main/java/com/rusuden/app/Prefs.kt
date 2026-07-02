package com.rusuden.app

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "rusuden_prefs"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_ANSWER_DELAY_SEC = "answer_delay_sec"
    private const val KEY_MAX_RECORD_SEC = "max_record_sec"
    private const val KEY_GREETING_TEXT = "greeting_text"

    const val DEFAULT_ANSWER_DELAY_SEC = 15
    const val DEFAULT_MAX_RECORD_SEC = 60

    private fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun getAnswerDelaySec(context: Context): Int =
        sp(context).getInt(KEY_ANSWER_DELAY_SEC, DEFAULT_ANSWER_DELAY_SEC)

    fun setAnswerDelaySec(context: Context, value: Int) {
        sp(context).edit().putInt(KEY_ANSWER_DELAY_SEC, value.coerceIn(0, 120)).apply()
    }

    fun getMaxRecordSec(context: Context): Int =
        sp(context).getInt(KEY_MAX_RECORD_SEC, DEFAULT_MAX_RECORD_SEC)

    fun setMaxRecordSec(context: Context, value: Int) {
        sp(context).edit().putInt(KEY_MAX_RECORD_SEC, value.coerceIn(10, 600)).apply()
    }

    fun getGreetingText(context: Context): String =
        sp(context).getString(KEY_GREETING_TEXT, null)
            ?: context.getString(R.string.default_greeting)

    fun setGreetingText(context: Context, value: String) {
        sp(context).edit().putString(KEY_GREETING_TEXT, value).apply()
    }
}
