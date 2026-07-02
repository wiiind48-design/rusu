package com.rusuden.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

object ContactHelper {

    private val cache = HashMap<String, String?>()

    /** 電話番号から連絡先の名前を引く。見つからない・権限がない場合は null。 */
    fun lookupName(context: Context, number: String): String? {
        if (number.isEmpty()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        synchronized(cache) {
            if (cache.containsKey(number)) return cache[number]
        }
        var name: String? = null
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0)
                }
            }
        } catch (ignored: Exception) {
        }
        synchronized(cache) {
            cache[number] = name
        }
        return name
    }

    /** 名前があれば名前、なければ番号(それもなければ「非通知・不明」)を返す。 */
    fun displayName(context: Context, number: String): String {
        if (number.isEmpty() || number == MessageStore.UNKNOWN_NUMBER) {
            return context.getString(R.string.unknown_caller)
        }
        return lookupName(context, number) ?: number
    }

    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }
}
