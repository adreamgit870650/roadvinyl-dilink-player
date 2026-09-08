package com.car.mp3player.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/** Validates only the directory explicitly chosen by the user; never scans it. */
object SelectedDirectoryAccess {
    fun persistReadableTree(context: Context, uri: Uri): Boolean = validateDirectoryGrant(
        persist = {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
        hasPersistedRead = {
            context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        },
        isReadableDirectory = {
            DocumentFile.fromTreeUri(context, uri)?.let { it.isDirectory && it.canRead() } == true
        }
    )

    fun readablePath(input: String): String? = runCatching {
        val file = File(input.trim())
        if (!file.isAbsolute) return null
        file.canonicalFile.takeIf { it.isDirectory && it.canRead() }?.absolutePath
    }.getOrNull()
}

internal fun validateDirectoryGrant(
    persist: () -> Unit,
    hasPersistedRead: () -> Boolean,
    isReadableDirectory: () -> Boolean
): Boolean = runCatching {
    persist()
    hasPersistedRead() && isReadableDirectory()
}.getOrDefault(false)
