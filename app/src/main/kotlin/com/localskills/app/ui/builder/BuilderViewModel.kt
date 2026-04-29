package com.localskills.app.ui.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localskills.app.engine.builder.DraftError
import com.localskills.app.engine.builder.DraftField
import com.localskills.app.engine.builder.DraftPattern
import com.localskills.app.engine.builder.DraftRule
import com.localskills.app.engine.builder.DraftToManifest
import com.localskills.app.engine.builder.ManifestDraft
import com.localskills.app.engine.builder.SkillTestHarness
import com.localskills.app.engine.builder.TestReport
import com.localskills.app.skill.manifest.FieldType
import com.localskills.app.skill.manifest.InputKind
import com.localskills.app.skill.manifest.ManifestCodec
import com.localskills.app.skill.manifest.RuleAction
import com.localskills.app.skill.manifest.SkillManifest
import com.localskills.app.ui.builder.templates.BuilderTemplate
import com.localskills.app.ui.builder.templates.BuilderTemplates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single-source-of-truth ViewModel for the Skill Builder screen.
 *
 * State shape: a single [BuilderUiState] flow holding the current
 * [ManifestDraft], live validation errors, last test-harness report, and
 * transient UI signals (saving / saved). Per-field flows would multiply the
 * recompositions and complicate template replacement; one snapshot is
 * easier to diff for Compose.
 */
@HiltViewModel
class BuilderViewModel @Inject constructor(
    private val draftToManifest: DraftToManifest,
    private val harness: SkillTestHarness,
    private val sink: SkillSink,
) : ViewModel() {

    private val _state = MutableStateFlow(BuilderUiState())
    val state: StateFlow<BuilderUiState> = _state.asStateFlow()

    val templates: List<BuilderTemplate> = BuilderTemplates.all

    // ---- Draft mutators -----------------------------------------------------

    fun loadTemplate(key: String) {
        val template = BuilderTemplates.byKey(key) ?: return
        replaceDraft(template.factory())
    }

    fun replaceDraft(draft: ManifestDraft) {
        _state.update {
            it.copy(
                draft = draft,
                errors = draftToManifest.validate(draft),
                testReport = null,
                saveResult = null,
            )
        }
    }

    fun updateName(value: String) = mutate { it.copy(name = value) }
    fun updateId(value: String) = mutate { it.copy(id = value) }
    fun updateDescription(value: String) = mutate { it.copy(description = value) }
    fun updateInstruction(value: String) = mutate { it.copy(instruction = value) }

    fun toggleInput(kind: InputKind) = mutate {
        val next = if (kind in it.inputs) it.inputs - kind else it.inputs + kind
        it.copy(inputs = next)
    }

    fun addOutputField() = mutate {
        it.copy(outputFields = it.outputFields + DraftField())
    }

    fun updateOutputFieldName(index: Int, value: String) = mutate {
        it.copy(outputFields = it.outputFields.replaceAt(index) { f -> f.copy(name = value) })
    }

    fun updateOutputFieldType(index: Int, type: FieldType) = mutate {
        it.copy(outputFields = it.outputFields.replaceAt(index) { f -> f.copy(type = type) })
    }

    fun removeOutputField(index: Int) = mutate {
        it.copy(outputFields = it.outputFields.removeAt(index))
    }

    fun addPattern() = mutate { it.copy(patterns = it.patterns + DraftPattern()) }

    fun updatePatternName(index: Int, value: String) = mutate {
        it.copy(patterns = it.patterns.replaceAt(index) { p -> p.copy(name = value) })
    }

    fun updatePatternRegex(index: Int, value: String) = mutate {
        it.copy(patterns = it.patterns.replaceAt(index) { p -> p.copy(regex = value) })
    }

    fun removePattern(index: Int) = mutate {
        it.copy(patterns = it.patterns.removeAt(index))
    }

    fun addRule() = mutate { it.copy(rules = it.rules + DraftRule()) }

    fun updateRuleCondition(index: Int, value: String) = mutate {
        it.copy(rules = it.rules.replaceAt(index) { r -> r.copy(condition = value) })
    }

    fun updateRuleAction(index: Int, action: RuleAction) = mutate {
        it.copy(rules = it.rules.replaceAt(index) { r -> r.copy(action = action) })
    }

    fun updateRuleTitle(index: Int, value: String) = mutate {
        it.copy(rules = it.rules.replaceAt(index) { r -> r.copy(title = value) })
    }

    fun updateRuleBody(index: Int, value: String) = mutate {
        it.copy(rules = it.rules.replaceAt(index) { r -> r.copy(body = value) })
    }

    fun removeRule(index: Int) = mutate {
        it.copy(rules = it.rules.removeAt(index))
    }

    fun updateLimit(field: LimitField, value: String) = mutate {
        val l = it.limits
        it.copy(
            limits = when (field) {
                LimitField.MAX_INPUT_CHARS -> l.copy(maxInputChars = value)
                LimitField.MAX_OCR_PAGES -> l.copy(maxOcrPages = value)
                LimitField.MAX_MODEL_TOKENS -> l.copy(maxModelTokens = value)
                LimitField.MAX_RUNTIME_MS -> l.copy(maxRuntimeMs = value)
            },
        )
    }

    // ---- Test harness / preview --------------------------------------------

    fun updateSampleInput(value: String) {
        _state.update { it.copy(sampleInput = value) }
    }

    fun runTest() {
        val current = _state.value
        _state.update { it.copy(testRunning = true) }
        viewModelScope.launch {
            val report = harness.run(current.draft, current.sampleInput)
            _state.update { it.copy(testRunning = false, testReport = report) }
        }
    }

    fun jsonPreview(): String? = draftToManifest.build(_state.value.draft)
        .map(ManifestCodec::encodeJson)
        .getOrNull()

    fun currentManifest(): SkillManifest? =
        draftToManifest.build(_state.value.draft).getOrNull()

    // ---- Save ---------------------------------------------------------------

    fun save() {
        val current = _state.value
        val manifest = draftToManifest.build(current.draft).getOrElse { cause ->
            _state.update {
                it.copy(saveResult = SaveResult.Failed(cause.message ?: "Invalid draft"))
            }
            return
        }
        _state.update { it.copy(saving = true, saveResult = null) }
        viewModelScope.launch {
            val outcome = sink.upsert(manifest).fold(
                onSuccess = { id -> SaveResult.Saved(id) },
                onFailure = { e -> SaveResult.Failed(e.message ?: "Save failed") },
            )
            _state.update { it.copy(saving = false, saveResult = outcome) }
        }
    }

    fun acknowledgeSave() {
        _state.update { it.copy(saveResult = null) }
    }

    // ---- helpers ------------------------------------------------------------

    private inline fun mutate(crossinline transform: (ManifestDraft) -> ManifestDraft) {
        _state.update { current ->
            val nextDraft = transform(current.draft)
            current.copy(
                draft = nextDraft,
                errors = draftToManifest.validate(nextDraft),
            )
        }
    }
}

/** Indicates which numeric limit a UI event targets. */
enum class LimitField { MAX_INPUT_CHARS, MAX_OCR_PAGES, MAX_MODEL_TOKENS, MAX_RUNTIME_MS }

data class BuilderUiState(
    val draft: ManifestDraft = ManifestDraft(),
    val errors: List<DraftError> = emptyList(),
    val sampleInput: String = "",
    val testRunning: Boolean = false,
    val testReport: TestReport? = null,
    val saving: Boolean = false,
    val saveResult: SaveResult? = null,
)

sealed interface SaveResult {
    data class Saved(val id: String) : SaveResult
    data class Failed(val reason: String) : SaveResult
}

private fun <T> List<T>.replaceAt(index: Int, transform: (T) -> T): List<T> =
    if (index !in indices) this else toMutableList().also { it[index] = transform(it[index]) }

private fun <T> List<T>.removeAt(index: Int): List<T> =
    if (index !in indices) this else toMutableList().also { it.removeAt(index) }
