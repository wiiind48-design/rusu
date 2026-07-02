package com.rusuden.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ContactEntry(
    val name: String,
    val number: String,
    val starred: Boolean
)

object ContactHelper {

    private val cache = HashMap<String, String?>()

    fun hasContactsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** 全連絡先(電話番号付き)を、お気に入り→名前順で読み込む。 */
    fun loadContacts(context: Context): List<ContactEntry> {
        if (!hasContactsPermission(context)) return emptyList()
        val result = mutableListOf<ContactEntry>()
        val seen = HashSet<String>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.STARRED
                ),
                null, null,
                "${ContactsContract.CommonDataKinds.Phone.STARRED} DESC, " +
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0) ?: continue
                    val number = cursor.getString(1) ?: continue
                    val key = "$name|${number.replace(Regex("[^0-9+]"), "")}"
                    if (!seen.add(key)) continue
                    result.add(ContactEntry(name, number, cursor.getInt(2) != 0))
                }
            }
        } catch (ignored: Exception) {
        }
        return result
    }

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
