// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.scan

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.documentfile.provider.DocumentFile

/** One child of a folder, read in a single query together with its siblings. */
data class DocumentEntry(
    val documentId: String,
    val name: String,
    val mimeType: String,
    val lastModified: Long,
    val size: Long,
) {
    val isDirectory: Boolean get() = mimeType == Document.MIME_TYPE_DIR
}

/**
 * The only way Pocket Box reaches music: folders the user picked with the system folder picker
 * (Storage Access Framework). Read-only grants; no storage permission is ever requested.
 */
class FolderAccess(context: Context) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    /** Keeps read access to a picked folder across reboots. Returns false if Android refused. */
    fun persist(treeUri: Uri): Boolean = runCatching {
        resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    }.getOrDefault(false)

    fun release(treeUri: Uri) {
        runCatching { resolver.releasePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    /** True when the grant still exists and the folder can still be read (it may have been moved or revoked). */
    fun isAccessible(treeUri: Uri): Boolean {
        val granted = resolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }
        if (!granted) return false
        return runCatching { DocumentFile.fromTreeUri(appContext, treeUri)?.let { it.exists() && it.canRead() } }
            .getOrNull() == true
    }

    fun displayName(treeUri: Uri): String =
        runCatching { DocumentFile.fromTreeUri(appContext, treeUri)?.name }.getOrNull()
            ?: DocumentsContract.getTreeDocumentId(treeUri).substringAfterLast(':').substringAfterLast('/').ifEmpty { "Music" }

    fun rootDocumentId(treeUri: Uri): String = DocumentsContract.getTreeDocumentId(treeUri)

    fun documentUri(treeUri: Uri, documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    /** Lists a folder's children in one provider query. Returns null if the folder can't be read. */
    fun listChildren(treeUri: Uri, parentDocumentId: String): List<DocumentEntry>? = runCatching {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        resolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
            val entries = ArrayList<DocumentEntry>(cursor.count)
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                entries += DocumentEntry(
                    documentId = id,
                    name = cursor.getString(1).orEmpty(),
                    mimeType = cursor.getString(2).orEmpty(),
                    lastModified = if (cursor.isNull(3)) 0 else cursor.getLong(3),
                    size = if (cursor.isNull(4)) 0 else cursor.getLong(4),
                )
            }
            entries
        }
    }.getOrNull()

    private companion object {
        val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE,
        )
    }
}
