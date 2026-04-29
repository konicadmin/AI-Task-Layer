package com.localskills.app.skill.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.localskills.app.skill.manifest.ManifestCodec
import com.localskills.app.skill.manifest.SkillManifest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produces share intents for an installed skill manifest.
 *
 * Two flavours are exposed:
 *  - [shareText] hands the receiver an `AOSL-SKILL/1` text blob via
 *    [Intent.EXTRA_TEXT]. Useful for paste-into-chat flows.
 *  - [shareFile] writes a `<id>.skill.json` to a private cache folder
 *    and exposes it via [FileProvider] using [Intent.EXTRA_STREAM].
 *
 * The exporter does not start the activity itself — it returns an
 * [Intent] the caller can wrap with [Intent.createChooser] and launch.
 */
@Singleton
class SkillExporter @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {

    /** Builds the share text and an `ACTION_SEND` intent containing it. */
    fun shareText(manifest: SkillManifest): ShareIntent {
        val text = ManifestCodec.encodeShareText(manifest)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TEXT
            putExtra(Intent.EXTRA_SUBJECT, defaultSubject(manifest))
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return ShareIntent(text = text, intent = intent)
    }

    /**
     * Writes the manifest to the FileProvider-backed cache directory and
     * returns an `ACTION_SEND` intent referencing it. The file is named
     * `<safe-id>.skill.json`.
     */
    fun shareFile(manifest: SkillManifest): ShareIntent {
        val text = ManifestCodec.encodeShareText(manifest)
        val dir = File(appContext.cacheDir, SHARE_DIR).apply { mkdirs() }
        val fileName = "${safeId(manifest.id)}$FILE_EXTENSION"
        val outFile = File(dir, fileName)
        outFile.writeText(text)

        val authority = "${appContext.packageName}$FILE_PROVIDER_SUFFIX"
        val uri: Uri = FileProvider.getUriForFile(appContext, authority, outFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_JSON
            putExtra(Intent.EXTRA_SUBJECT, defaultSubject(manifest))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return ShareIntent(text = text, intent = intent, fileUri = uri)
    }

    private fun defaultSubject(manifest: SkillManifest): String =
        "Skill: ${manifest.name}"

    private fun safeId(id: String): String =
        id.lowercase().replace(Regex("[^a-z0-9._-]"), "_").trim('_').ifEmpty { "skill" }

    companion object {
        const val MIME_TEXT: String = "text/plain"
        const val MIME_JSON: String = "application/json"
        const val FILE_EXTENSION: String = ".skill.json"
        const val SHARE_DIR: String = "skill_share"
        const val FILE_PROVIDER_SUFFIX: String = ".fileprovider"
    }
}

/** Result of building a share payload — the encoded text plus the intent. */
data class ShareIntent(
    val text: String,
    val intent: Intent,
    val fileUri: Uri? = null,
)
