package com.compresoraudio.app.audio

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File

/** Helpers for Storage Access Framework (SAF) file/folder access. */
object SafUtils {

    private val audioExts = setOf("mp3", "ogg", "opus", "wav")

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
}
