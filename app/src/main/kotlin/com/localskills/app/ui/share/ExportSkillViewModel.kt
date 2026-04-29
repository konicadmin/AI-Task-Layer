package com.localskills.app.ui.share

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.localskills.app.skill.manifest.SkillManifest
import com.localskills.app.skill.share.ShareIntent
import com.localskills.app.skill.share.SkillExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * State holder for the export screen. The screen previews the share
 * text and delegates intent construction to [SkillExporter].
 *
 * P5-WIRING: the manifest is supplied by the caller for now. Once P1's
 * `SkillRepository` is available, we will accept a skill id and load
 * the manifest from the canonical store inside the ViewModel.
 */
@HiltViewModel
class ExportSkillViewModel @Inject constructor(
    private val exporter: SkillExporter,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    /** Initialise the export preview from a manifest. */
    fun bind(manifest: SkillManifest) {
        val preview = exporter.shareText(manifest)
        _state.value = ExportUiState(
            manifest = manifest,
            shareText = preview.text,
        )
    }

    /** Build the text-share intent. */
    fun buildTextShareIntent(): Intent? {
        val manifest = _state.value.manifest ?: return null
        return wrapChooser(exporter.shareText(manifest), title = "Share skill text")
    }

    /** Build the file-share intent (writes a `.skill.json` to cache). */
    fun buildFileShareIntent(@Suppress("UNUSED_PARAMETER") context: Context): Intent? {
        val manifest = _state.value.manifest ?: return null
        return wrapChooser(exporter.shareFile(manifest), title = "Share skill file")
    }

    private fun wrapChooser(share: ShareIntent, title: String): Intent =
        Intent.createChooser(share.intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}

/** UI state for the export screen. */
data class ExportUiState(
    val manifest: SkillManifest? = null,
    val shareText: String = "",
)
