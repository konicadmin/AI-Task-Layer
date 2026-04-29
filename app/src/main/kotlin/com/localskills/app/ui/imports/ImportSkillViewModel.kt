package com.localskills.app.ui.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localskills.app.skill.manifest.SkillManifest
import com.localskills.app.skill.share.ImportPreview
import com.localskills.app.skill.share.ImportResult
import com.localskills.app.skill.share.SafetyReport
import com.localskills.app.skill.share.SandboxReport
import com.localskills.app.skill.share.SkillImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the import-review screen. The activity hands us the raw share
 * payload (text or file contents) on construction; we lint, decode, and
 * sandbox it, then expose a [ImportUiState] for the screen to render.
 *
 * The user must press [confirmInstall] to actually persist the manifest;
 * imported skills always land disabled.
 */
@HiltViewModel
class ImportSkillViewModel @Inject constructor(
    private val importer: SkillImporter,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun loadFromText(raw: String) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, raw = raw, terminal = null) }
        viewModelScope.launch {
            val preview = importer.preview(raw)
            _state.update {
                it.copy(
                    loading = false,
                    manifest = preview.manifest,
                    safety = preview.safety,
                    sandbox = preview.sandbox,
                )
            }
        }
    }

    fun confirmInstall() {
        val raw = _state.value.raw ?: return
        if (_state.value.installing) return
        _state.update { it.copy(installing = true) }
        viewModelScope.launch {
            val result = importer.import(raw)
            _state.update { it.copy(installing = false, terminal = result) }
        }
    }
}

/** UI state for the import review screen. */
data class ImportUiState(
    val loading: Boolean = false,
    val installing: Boolean = false,
    val raw: String? = null,
    val manifest: SkillManifest? = null,
    val safety: SafetyReport? = null,
    val sandbox: SandboxReport? = null,
    val terminal: ImportResult? = null,
) {
    val canInstall: Boolean
        get() = !installing &&
            !loading &&
            manifest != null &&
            safety !is SafetyReport.Reject &&
            (sandbox?.ok ?: true) &&
            terminal == null

    val rejectionReasons: List<String>
        get() = buildList {
            (safety as? SafetyReport.Reject)?.reasons?.let(::addAll)
            sandbox?.takeIf { !it.ok }?.issues?.let(::addAll)
            (terminal as? ImportResult.Rejected)?.reasons?.let(::addAll)
        }

    /** Shown by the screen as small advisories. */
    val advisories: List<String>
        get() = (safety as? SafetyReport.Warnings)?.warnings.orEmpty()

    /** Pulled from [ImportPreview] for parity with the importer. */
    val preview: ImportPreview?
        get() = manifest?.let { ImportPreview(manifest = it, safety = safety!!, sandbox = sandbox) }
}
