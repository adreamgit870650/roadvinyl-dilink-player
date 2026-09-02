package com.car.mp3player.data

import java.io.File
import java.net.URLDecoder

/** Enumerates audio files strictly beneath roots explicitly selected by the user. */
object SelectedDirectoryAudioFiles {
    fun collect(roots: List<File>, audioExtensions: Set<String>): List<File> {
        val normalizedExtensions = audioExtensions.map { it.lowercase() }.toSet()
        val seenFiles = linkedSetOf<String>()
        val result = mutableListOf<File>()

        roots.distinctBy(::stablePath).forEach { root ->
            if (!root.exists() || !root.isDirectory) return@forEach
            root.walkTopDown()
                .onFail { _, _ -> }
                .filter { file ->
                    file.isFile && file.extension.lowercase() in normalizedExtensions
                }
                .forEach { file ->
                    if (seenFiles.add(stablePath(file))) result += file
                }
        }
        return result
    }

    private fun stablePath(file: File): String =
        runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
}

/** Maps raw paths and ExternalStorageProvider document URIs for the same file to one key. */
object AudioFileIdentity {
    private const val EXTERNAL_STORAGE_AUTHORITY = "content://com.android.externalstorage.documents/"

    fun key(pathOrUri: String): String {
        val filePath = externalStorageDocumentPath(pathOrUri)
            ?: pathOrUri.takeUnless { it.startsWith("content://") }
            ?: return "uri:$pathOrUri"
        val stablePath = runCatching { File(filePath).canonicalPath }.getOrDefault(File(filePath).absolutePath)
        return "file:$stablePath"
    }

    private fun externalStorageDocumentPath(uri: String): String? {
        if (!uri.startsWith(EXTERNAL_STORAGE_AUTHORITY) || "/document/" !in uri) return null
        val encodedId = uri.substringAfterLast("/document/").substringBefore('?')
        val documentId = runCatching {
            URLDecoder.decode(encodedId.replace("+", "%2B"), Charsets.UTF_8.name())
        }.getOrNull() ?: return null
        val separator = documentId.indexOf(':')
        if (separator < 0) return null
        val volume = documentId.substring(0, separator)
        val relativePath = documentId.substring(separator + 1).trimStart('/')
        val root = if (volume.equals("primary", ignoreCase = true)) {
            "/storage/emulated/0"
        } else {
            "/storage/$volume"
        }
        return if (relativePath.isEmpty()) root else "$root/$relativePath"
    }
}
