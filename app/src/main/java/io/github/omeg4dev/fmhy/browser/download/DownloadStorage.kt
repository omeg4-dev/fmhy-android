package io.github.omeg4dev.fmhy.browser.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import io.github.omeg4dev.fmhy.data.SettingsRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import kotlin.math.abs

data class SavedFile(val uri: String, val displayName: String)

class DownloadStorage(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    /** Hidden holding area: noBackupFilesDir/downloads/. Private ids (< 0) use "p<abs>.part". */
    fun partFile(id: Long): File {
        val dir = File(context.noBackupFilesDir, "downloads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val name = if (id < 0) "p${abs(id)}.part" else "$id.part"
        return File(dir, name)
    }

    /** Deletes every *.part whose id is not in [keepIds]; all "p*.part" files are always deleted. */
    fun cleanOrphanParts(keepIds: Set<Long>) {
        val dir = File(context.noBackupFilesDir, "downloads")
        if (!dir.isDirectory) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            val name = f.name
            if (name.startsWith("p") && name.endsWith(".part")) {
                f.delete()
            } else if (name.endsWith(".part")) {
                val id = name.removeSuffix(".part").toLongOrNull()
                if (id == null || id !in keepIds) {
                    f.delete()
                }
            }
        }
    }

    /** "Downloads" for the default folder, else the picked folder's display name; falls back to "Downloads" when the folder is unavailable. */
    suspend fun targetLabel(): String {
        val folderUri = settings.downloadPrefs.first().folderUri
        if (folderUri.isNullOrBlank()) return DEFAULT_LABEL
        return try {
            val tree = Uri.parse(folderUri)
            if (isTreeAvailable(context, tree)) {
                folderLabel(context, tree) ?: DEFAULT_LABEL
            } else {
                DEFAULT_LABEL
            }
        } catch (_: Exception) {
            DEFAULT_LABEL
        }
    }

    /** Copies [part] into the target folder under a free name, deletes [part] on success, returns the saved file. Throws IOException on failure (part kept). */
    suspend fun finalize(part: File, fileName: String, mimeType: String, onFallback: () -> Unit): SavedFile {
        val folderUri = settings.downloadPrefs.first().folderUri
        if (!folderUri.isNullOrBlank()) {
            val tree = Uri.parse(folderUri)
            if (isTreeAvailable(context, tree)) {
                return finalizeSaf(part, fileName, mimeType, tree)
            } else {
                onFallback()
            }
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            finalizeMediaStore(part, fileName, mimeType)
        } else {
            finalizeLegacy(part, fileName)
        }
    }

    private fun finalizeSaf(part: File, fileName: String, mimeType: String, tree: Uri): SavedFile {
        val resolver = context.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(tree)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(tree, treeDocId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeDocId)

        val existingNames = mutableSetOf<String>()
        try {
            resolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (idx != -1) {
                        cursor.getString(idx)?.let { existingNames.add(it) }
                    }
                }
            }
        } catch (_: Exception) {}

        val name = DownloadRequest.uniqueName(fileName) { it in existingNames }
        val effectiveMime = mimeType.ifBlank { "application/octet-stream" }
        val docUri = DocumentsContract.createDocument(resolver, parentDocUri, effectiveMime, name)
            ?: throw IOException("Failed to create document in picked folder")

        try {
            resolver.openOutputStream(docUri)?.use { out ->
                part.inputStream().use { input ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                }
                out.flush()
            } ?: throw IOException("Failed to open output stream for document")

            val actualDisplayName = queryDisplayName(docUri) ?: name
            part.delete()
            return SavedFile(docUri.toString(), actualDisplayName)
        } catch (e: Exception) {
            try {
                DocumentsContract.deleteDocument(resolver, docUri)
            } catch (_: Exception) {}
            if (e is IOException) throw e else throw IOException(e)
        }
    }

    private fun finalizeMediaStore(part: File, fileName: String, mimeType: String): SavedFile {
        val resolver = context.contentResolver
        val existingNames = mutableSetOf<String>()
        try {
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf("Download/"),
                null,
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (idx != -1) {
                        cursor.getString(idx)?.let { existingNames.add(it) }
                    }
                }
            }
        } catch (_: Exception) {}

        val downloadsDir = try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } catch (_: Exception) {
            null
        }

        val name = DownloadRequest.uniqueName(fileName) { candidate ->
            candidate in existingNames || (downloadsDir?.let { try { File(it, candidate).exists() } catch (_: Exception) { false } } == true)
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            if (mimeType.isNotBlank()) {
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            }
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val docUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Failed to create MediaStore row")

        try {
            resolver.openOutputStream(docUri)?.use { out ->
                part.inputStream().use { input ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                }
                out.flush()
            } ?: throw IOException("Failed to open MediaStore output stream")

            val updateValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(docUri, updateValues, null, null)

            val actualDisplayName = queryDisplayName(docUri) ?: name
            part.delete()
            return SavedFile(docUri.toString(), actualDisplayName)
        } catch (e: Exception) {
            try {
                resolver.delete(docUri, null, null)
            } catch (_: Exception) {}
            if (e is IOException) throw e else throw IOException(e)
        }
    }

    private fun finalizeLegacy(part: File, fileName: String): SavedFile {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads-done")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val name = DownloadRequest.uniqueName(fileName) { File(dir, it).exists() }
        val targetFile = File(dir, name)

        val renamed = try {
            part.renameTo(targetFile)
        } catch (_: Exception) {
            false
        }

        if (!renamed) {
            try {
                targetFile.outputStream().use { out ->
                    part.inputStream().use { input ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    }
                    out.flush()
                }
                part.delete()
            } catch (e: Exception) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (e is IOException) throw e else throw IOException(e)
            }
        }

        val authority = "${context.packageName}.downloads"
        val docUri = FileProvider.getUriForFile(context, authority, targetFile)
        return SavedFile(docUri.toString(), targetFile.name)
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) cursor.getString(idx) else cursor.getString(0)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun delete(uri: String): Boolean {
        return try {
            val u = Uri.parse(uri)
            if (DocumentsContract.isDocumentUri(context, u)) {
                DocumentsContract.deleteDocument(context.contentResolver, u)
            } else {
                context.contentResolver.delete(u, null, null) > 0
            }
        } catch (_: Exception) {
            false
        }
    }

    fun exists(uri: String): Boolean {
        return try {
            val u = Uri.parse(uri)
            context.contentResolver.query(
                u,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val DEFAULT_LABEL = "Downloads"

        fun folderLabel(context: Context, treeUri: Uri): String? {
            return try {
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                context.contentResolver.query(
                    docUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        if (idx != -1) cursor.getString(idx) else cursor.getString(0)
                    } else null
                }
            } catch (_: Exception) {
                null
            }
        }

        fun isTreeAvailable(context: Context, treeUri: Uri): Boolean {
            return try {
                val hasWrite = context.contentResolver.persistedUriPermissions.any {
                    it.uri == treeUri && it.isWritePermission
                }
                if (!hasWrite) return false
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                context.contentResolver.query(
                    docUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    cursor.moveToFirst()
                } ?: false
            } catch (_: Exception) {
                false
            }
        }
    }
}
