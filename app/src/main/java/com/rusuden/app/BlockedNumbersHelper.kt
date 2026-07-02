package com.rusuden.app

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract

data class BlockedNumber(val id: Long, val number: String)

/**
 * システムの着信拒否リスト。デフォルトの電話アプリのみ読み書きできる。
 * ここに追加した番号は OS 側で着信自体がブロックされる。
 */
object BlockedNumbersHelper {

    fun canBlock(context: Context): Boolean = try {
        BlockedNumberContract.canCurrentUserBlockNumbers(context)
    } catch (e: Exception) {
        false
    }

    fun list(context: Context): List<BlockedNumber> {
        val result = mutableListOf<BlockedNumber>()
        try {
            context.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(
                    BlockedNumberContract.BlockedNumbers.COLUMN_ID,
                    BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(BlockedNumber(cursor.getLong(0), cursor.getString(1) ?: ""))
                }
            }
        } catch (ignored: Exception) {
        }
        return result
    }

    fun add(context: Context, number: String): Boolean = try {
        val values = ContentValues().apply {
            put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
        }
        context.contentResolver.insert(
            BlockedNumberContract.BlockedNumbers.CONTENT_URI, values
        ) != null
    } catch (e: Exception) {
        false
    }

    fun remove(context: Context, id: Long): Boolean = try {
        val uri = ContentUris.withAppendedId(
            BlockedNumberContract.BlockedNumbers.CONTENT_URI, id
        )
        context.contentResolver.delete(uri, null, null) > 0
    } catch (e: Exception) {
        false
    }
}
