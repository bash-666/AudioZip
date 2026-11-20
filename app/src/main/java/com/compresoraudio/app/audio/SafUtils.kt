package com.compresoraudio.app.audio

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File

/** Helpers for Storage Access Framework (SAF) file/folder access. */
object SafUtils {

    private val audioExts = setOf("mp3", "ogg", "opus", "wav", "flac", "m4a")

    fun queryNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "audio"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0 && !cursor.isNull(nameIdx)) name = cursor.getString(nameIdx)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }
        return name to size
    }

    fun isAudioFile(name: String, mime: String?): Boolean {
        if (mime != null && mime.startsWith("audio/")) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in audioExts
    }

    /** Duration in milliseconds read from the media metadata, or 0 when unknown. */
    fun queryDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Recursively lists audio files inside a SAF tree (folder) URI.
     * Returns document URIs. Capped to avoid runaway scans.
     */
    fun listAudioFiles(context: Context, treeUri: Uri, maxFiles: Int = 1000): List<Uri> {
        val result = mutableListOf<Uri>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val stack = ArrayDeque<String>()
        stack.addLast(rootId)
        while (stack.isNotEmpty() && result.size < maxFiles) {
            val parentId = stack.removeLast()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null,
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext() && result.size < maxFiles) {
                    val docId = cursor.getString(idIdx) ?: continue
                    val name = if (nameIdx >= 0 && !cursor.isNull(nameIdx)) cursor.getString(nameIdx) else ""
                    val mime = if (mimeIdx >= 0 && !cursor.isNull(mimeIdx)) cursor.getString(mimeIdx) else null
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        stack.addLast(docId)
                    } else if (isAudioFile(name, mime)) {
                        result.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, docId))
                    }
                }
            }
        }
        return result
    }

    /**
     * Creates (or replaces) a document with [fileName] inside the output tree.
     * Returns the new document URI, or null on failure.
     */
    fun createOutputDocument(context: Context, treeUri: Uri, fileName: String, mime: String): Uri? {
        val parentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        // Remove an existing file with the same name so nothing is overwritten blindly.
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val docId = cursor.getString(idIdx) ?: continue
                val name = if (nameIdx >= 0 && !cursor.isNull(nameIdx)) cursor.getString(nameIdx) else ""
                if (name == fileName) {
                    runCatching {
                        DocumentsContract.deleteDocument(
                            context.contentResolver,
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                        )
                    }
                }
            }
        }
        return runCatching {
            DocumentsContract.createDocument(context.contentResolver, childrenUri, mime, fileName)
        }.getOrNull()
    }

    fun copyUriToFile(context: Context, uri: Uri, dest: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("No se pudo abrir el archivo de entrada")
    }

    fun copyFileToUri(context: Context, src: File, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            src.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IllegalStateException("No se pudo escribir el archivo de salida")
    }

    /**
     * Writes [src] into the public Music folder via MediaStore. No storage
     * permission is needed for app-inserted media on Android 10+. Returns the
     * media content Uri, or null on failure.
     */
    fun writeToMusic(context: Context, fileName: String, mime: String, src: File): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } ?: throw IllegalStateException("No se pudo escribir el archivo")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }
}
