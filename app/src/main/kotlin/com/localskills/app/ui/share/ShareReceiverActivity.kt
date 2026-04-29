package com.localskills.app.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.localskills.app.engine.CaptureInput
import com.localskills.app.skill.manifest.InputKind
import dagger.hilt.android.AndroidEntryPoint

/**
 * Entry point for ACTION_SEND intents. Builds a [CaptureInput] from
 * shared text or image and hands off to the runner. Routing logic
 * (skill picker, classifier) lands in P1.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val capture = buildCapture(intent)
        if (capture == null) {
            Toast.makeText(this, "Unsupported content", Toast.LENGTH_SHORT).show()
        } else {
            // TODO(P1): hand off to skill runner / picker
            Toast.makeText(this, "Captured ${capture.kind}", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun buildCapture(intent: Intent): CaptureInput? {
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) {
            return null
        }
        val mime = intent.type ?: return null
        val sourceApp = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
        return when {
            mime.startsWith("text/") -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
                CaptureInput(
                    kind = InputKind.TEXT,
                    mimeType = mime,
                    rawText = text,
                    sourceAppHint = sourceApp,
                )
            }
            mime.startsWith("image/") -> {
                @Suppress("DEPRECATION")
                val uri: Uri = intent.getParcelableExtra(Intent.EXTRA_STREAM) ?: return null
                CaptureInput(
                    kind = InputKind.IMAGE,
                    mimeType = mime,
                    artifactPath = uri.toString(),
                    sourceAppHint = sourceApp,
                )
            }
            else -> null
        }
    }
}
