package com.rusuden.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat

data class CallLogEntry(
    val number: String,
    val cachedName: String?,
    val type: Int,
    val date: Long
)

object CallLogHelper {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    fun loadRecent(context: Context, limit: Int = 50): List<CallLogEntry> {
        if (!hasPermission(context)) return emptyList()
        val result = mutableListOf<CallLogEntry>()
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE
                ),
                null, null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext() && result.size < limit) {
                    result.add(
                        CallLogEntry(
                            number = cursor.getString(0) ?: "",
                            cachedName = cursor.getString(1),
                            type = cursor.getInt(2),
                            date = cursor.getLong(3)
                        )
                    )
                }
            }
        } catch (ignored: Exception) {
        }
        return result
    }
}
