package com.rusuden.app

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File

data class VoiceMessage(
    val file: File,
    val number: String,
    val timestamp: Long,
    val durationMs: Long,
    val isCallRecording: Boolean
)

object MessageStore {
    private const val PREFIX_VOICEMAIL = "msg_"
    private const val PREFIX_CALL_REC = "rec_"
    private const val EXT = ".m4a"
    const val UNKNOWN_NUMBER = "unknown"

    fun dir(context: Context): File =
        File(context.filesDir, "messages").apply { mkdirs() }

    fun newFile(context: Context, number: String, callRecording: Boolean = false): File {
        val prefix = if (callRecording) PREFIX_CALL_REC else PREFIX_VOICEMAIL
        val safe = number.replace(Regex("[^0-9+*#]"), "").ifEmpty { UNKNOWN_NUMBER }
        return File(dir(context), "$prefix${System.currentTimeMillis()}_$safe$EXT")
    }

    fun list(context: Context): List<VoiceMessage> {
        val files = dir(context).listFiles() ?: return emptyList()
        return files
            .filter {
                it.isFile && it.name.endsWith(EXT) &&
                    (it.name.startsWith(PREFIX_VOICEMAIL) || it.name.startsWith(PREFIX_CALL_REC))
            }
            .mapNotNull { parse(it) }
            .sortedByDescending { it.timestamp }
    }

    private fun parse(file: File): VoiceMessage? {
        val isCallRec = file.name.startsWith(PREFIX_CALL_REC)
        val prefix = if (isCallRec) PREFIX_CALL_REC else PREFIX_VOICEMAIL
        val body = file.name.removePrefix(prefix).removeSuffix(EXT)
        val sep = body.indexOf('_')
        if (sep <= 0) return null
        val timestamp = body.substring(0, sep).toLongOrNull() ?: return null
        val number = body.substring(sep + 1)
        return VoiceMessage(file, number, timestamp, durationOf(file), isCallRec)
    }

    private fun durationOf(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val ms = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            retriever.release()
            ms
        } catch (e: Exception) {
            0L
        }
    }
}
