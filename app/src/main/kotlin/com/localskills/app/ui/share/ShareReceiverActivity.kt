package com.localskills.app.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.localskills.app.engine.CaptureInput
import com.localskills.app.skill.manifest.InputKind
import com.localskills.app.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Entry point for ACTION_SEND intents. Builds a [CaptureInput] from
 * shared text or image and forwards the user to the library, where they
 * pick which installed skill should consume the input. Skill selection
 * stays user-driven so we never silently run an extractor on shared data.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val capture = buildCapture(intent)
        if (capture == null) {
            Toast.makeText(this, "Unsupported content", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        PendingShareHolder.put(capture)
        Toast.makeText(this, "Pick a skill to run on the shared ${capture.kind.name.lowercase()}.", Toast.LENGTH_SHORT).show()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
        )
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

/**
 * Process-local hand-off slot for shared captures. Intentionally not
 * persisted: a share intent is a foreground user gesture, and surviving
 * process death would let stale shared content silently feed a skill
 * after a future launch.
 */
object PendingShareHolder {
    @Volatile private var pending: CaptureInput? = null

    fun put(capture: CaptureInput) { pending = capture }
    fun consume(): CaptureInput? = pending.also { pending = null }
    fun peek(): CaptureInput? = pending
}
