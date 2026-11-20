package com.compresoraudio.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

/**
 * Opens the SAF folder picker at the public Music folder instead of the
 * storage root, which Android refuses to grant as a tree ("No se puede usar
 * esta carpeta").
 */
class MusicFolderPickerContract : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        val musicUri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Music",
        )
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, musicUri)
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}
