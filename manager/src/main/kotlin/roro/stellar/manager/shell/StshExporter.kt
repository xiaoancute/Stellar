package roro.stellar.manager.shell

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

object StshExporter {
    private val assets = listOf(
        ExportedAsset("stsh", "application/x-sh")
    )

    fun export(context: Context, treeUri: Uri) {
        val resolver = context.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)
        val existing = mutableMapOf<String, Uri>()

        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID
            )
            val nameColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            while (cursor.moveToNext()) {
                existing[cursor.getString(nameColumn)] =
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idColumn))
            }
        }

        assets.forEach { asset ->
            existing[asset.name]?.let { DocumentsContract.deleteDocument(resolver, it) }
            val destination = DocumentsContract.createDocument(
                resolver,
                rootUri,
                asset.mimeType,
                asset.name
            ) ?: throw IOException("Unable to create ${asset.name}")

            context.assets.open(asset.name).use { input ->
                resolver.openOutputStream(destination, "wt")?.use { output ->
                    input.copyTo(output)
                } ?: throw IOException("Unable to open ${asset.name}")
            }
        }
    }

    private data class ExportedAsset(val name: String, val mimeType: String)
}
