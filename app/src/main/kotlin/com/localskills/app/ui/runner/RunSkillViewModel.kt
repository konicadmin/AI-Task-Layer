package com.localskills.app.ui.runner

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localskills.app.data.repo.InstalledSkill
import com.localskills.app.data.repo.RunRepository
import com.localskills.app.data.repo.SkillRepository
import com.localskills.app.engine.CaptureInput
import com.localskills.app.engine.runner.RunOutcome
import com.localskills.app.engine.runner.SkillRunner
import com.localskills.app.skill.manifest.InputKind
import com.localskills.app.ui.share.PendingShareHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RunSkillViewModel @Inject constructor(
    private val skillRepository: SkillRepository,
    private val runRepository: RunRepository,
    private val skillRunner: SkillRunner,
) : ViewModel() {

    private val _state = MutableStateFlow(RunSkillUiState())
    val state: StateFlow<RunSkillUiState> = _state.asStateFlow()

    fun load(skillId: String) {
        if (_state.value.skill?.id == skillId) return
        viewModelScope.launch {
            val skill = skillRepository.findById(skillId)
            // If a share-sheet hand-off is waiting and matches an input kind
            // this skill accepts, pre-fill the form so the user only has to
            // confirm + run. Anything left over stays in the holder for the
            // next runner load (e.g. user picks a different skill).
            val pending = PendingShareHolder.peek()
            val prefilledText = if (
                pending != null &&
                skill != null &&
                pending.kind == InputKind.TEXT &&
                InputKind.TEXT in skill.manifest.inputs
            ) {
                PendingShareHolder.consume()?.rawText.orEmpty()
            } else {
                ""
            }
            val prefilledImage = if (
                pending != null &&
                skill != null &&
                pending.kind == InputKind.IMAGE &&
                InputKind.IMAGE in skill.manifest.inputs
            ) {
                PendingShareHolder.consume()?.artifactPath?.let(Uri::parse)
            } else {
                null
            }
            _state.update {
                it.copy(skill = skill, inputText = prefilledText, imageUri = prefilledImage)
            }
        }
    }

    fun updateText(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    fun updateImageUri(uri: Uri?) {
        _state.update { it.copy(imageUri = uri) }
    }

    fun run() {
        val skill = _state.value.skill ?: return
        val capture = buildCapture(skill, _state.value) ?: run {
            _state.update { it.copy(error = "Provide text or an image first.") }
            return
        }
        _state.update { it.copy(running = true, error = null, outcome = null) }
        viewModelScope.launch {
            val outcome = skillRunner.run(skill.id, capture)
            _state.update { it.copy(running = false, outcome = outcome) }
        }
    }

    fun savePayload(updatedJson: String) {
        val outcome = _state.value.outcome as? RunOutcome.Success ?: return
        viewModelScope.launch {
            runRepository.updateResultPayload(
                existing = outcome.result,
                payloadJson = updatedJson,
                confirmed = true,
            )
            _state.update { it.copy(saved = true) }
        }
    }

    fun reset() {
        _state.update { RunSkillUiState(skill = it.skill) }
    }

    private fun buildCapture(skill: InstalledSkill, ui: RunSkillUiState): CaptureInput? {
        val text = ui.inputText.takeIf { it.isNotBlank() }
        val image = ui.imageUri
        val supportsImage = InputKind.IMAGE in skill.manifest.inputs
        val supportsText = InputKind.TEXT in skill.manifest.inputs

        return when {
            image != null && supportsImage -> CaptureInput(
                kind = InputKind.IMAGE,
                mimeType = "image/*",
                rawText = text,
                artifactPath = image.toString(),
            )
            text != null && supportsText -> CaptureInput(
                kind = InputKind.TEXT,
                mimeType = "text/plain",
                rawText = text,
            )
            else -> null
        }
    }
}

data class RunSkillUiState(
    val skill: InstalledSkill? = null,
    val inputText: String = "",
    val imageUri: Uri? = null,
    val running: Boolean = false,
    val outcome: RunOutcome? = null,
    val saved: Boolean = false,
    val error: String? = null,
)
