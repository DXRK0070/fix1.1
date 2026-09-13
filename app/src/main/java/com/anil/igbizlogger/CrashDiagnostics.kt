package com.anil.igbizlogger

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Startup diagnostics that do not require root or runtime storage permission.
 *
 * On Android 10+ the active log is placed in the public Documents folder through
 * MediaStore, so it can be found and shared from a normal file manager. A private
 * app-specific fallback is kept as well in case MediaStore is unavailable.
 */
object CrashDiagnostics {
    private const val FOLDER = "Biz Logger Diagnostics"
    private val lock = Any()
    private val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private var publicUri: Uri? = null
    private var installed = false
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        synchronized(lock) {
            if (installed) return
            installed = true
            previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                append(context, "UNCAUGHT EXCEPTION on ${thread.name}: ${throwable.stackTraceToString()}")
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun checkpoint(context: Context, message: String) {
        append(context, "CHECKPOINT: $message")
    }

    private fun append(context: Context, message: String) {
        synchronized(lock) {
            try {
                val line = "[${timestamp()}] $message\n"
                privateFile(context).apply {
                    parentFile?.mkdirs()
                    appendText(line)
                }
                publicUri(context)?.let { uri ->
                    context.contentResolver.openOutputStream(uri, "wa")?.use {
                        it.write(line.toByteArray(Charsets.UTF_8))
                    }
                }
            } catch (_: Exception) {
                // Diagnostics must never become the crash.
            }
        }
    }

    private fun timestamp(): String = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss.SSS", Locale.US
    ).format(Date())

    private fun privateFile(context: Context): File =
        File(context.getExternalFilesDir(null), "IGBizLogs/startup_diagnostics.txt")

    private fun publicUri(context: Context): Uri? {
        publicUri?.let { return it }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "biz_logger_startup_${timestampFormat.format(Date())}.txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$FOLDER")
        }
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return resolver.insert(collection, values)?.also { publicUri = it }
    }
}