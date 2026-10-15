package com.pyrun.mobile.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

object StorageUtils {
    fun projectsRoot(context: Context): File = File(context.filesDir, "projects")

    fun displayName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "imported.py"

    fun readTextFromUri(context: Context, uri: Uri, maxBytes: Long = 2L * 1024 * 1024): String {
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) throw IOException("Cannot open file")
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) throw IOException("File too large (max ${maxBytes / 1024 / 1024} MB)")
                out.write(buf, 0, n)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }

    fun writeTextToUri(context: Context, uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "wt").use { out ->
            if (out == null) throw IOException("Cannot open destination")
            out.write(text.toByteArray(Charsets.UTF_8))
        }
    }
}
