package com.localskills.app.ui.imports

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.localskills.app.skill.manifest.ManifestCodec
import com.localskills.app.skill.share.SkillLinter
import dagger.hilt.android.AndroidEntryPoint

/**
 * Entry point for receiving a shared manifest. Two intent shapes route
 * here (declared in AndroidManifest.xml — additive only):
 *
 *  - `ACTION_VIEW` for `application/json`, `text/*`, and `*.skill.json`
 *    file URIs (a chat app "open with" on a saved manifest file).
 *  - `ACTION_SEND` for `text/*` payloads whose body starts with the
 *    `AOSL-SKILL/1` header (a pasted manifest text).
 *
 * The activity reads the payload, hands it to [ImportSkillViewModel],
 * then renders [ImportSkillScreen] for review.
 */
@AndroidEntryPoint
class ImportReceiverActivity : ComponentActivity() {

    private val viewModel: ImportSkillViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = readPayload(intent)
        if (raw == null) {
            Toast.makeText(this, "No skill payload found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        viewModel.loadFromText(raw)
        setContent {
            MaterialTheme {
                Scaffold { padding ->
                    ImportSkillScreen(
                        onCancel = { finish() },
                        onInstalled = { _ -> finish() },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    /** Pull the raw manifest text out of either ACTION_SEND or ACTION_VIEW. */
    private fun readPayload(intent: Intent): String? = when (intent.action) {
        Intent.ACTION_SEND -> readFromSend(intent)
        Intent.ACTION_VIEW -> readFromView(intent)
        else -> null
    }

    private fun readFromSend(intent: Intent): String? {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        // Only route here if the payload is plausibly a manifest. Other
        // text shares should fall through to ShareReceiverActivity.
        return text.takeIf { looksLikeManifestText(it) }
    }

    private fun readFromView(intent: Intent): String? {
        val uri: Uri = intent.data ?: return null
        return runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            }
        }.getOrNull()?.takeIf { it.length <= MAX_INBOUND_BYTES }
    }

    private fun looksLikeManifestText(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith(ManifestCodec.SHARE_HEADER) ||
            trimmed.startsWith("{")
    }

    companion object {
        // Same envelope the linter uses, doubled to give the linter
        // a chance to reject oversized payloads with a friendly message
        // rather than aborting before review.
        private const val MAX_INBOUND_BYTES: Int = SkillLinter.MAX_PAYLOAD_BYTES * 2

        @Suppress("unused")
        const val MIME_JSON: String = "application/json"

        @Suppress("unused")
        fun mimeFromUri(resolver: ContentResolver, uri: Uri): String? = resolver.getType(uri)
    }
}
