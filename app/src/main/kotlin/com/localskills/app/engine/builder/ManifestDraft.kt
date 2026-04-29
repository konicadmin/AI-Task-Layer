package com.localskills.app.engine.builder

import com.localskills.app.skill.manifest.FieldType
import com.localskills.app.skill.manifest.InputKind
import com.localskills.app.skill.manifest.Limits
import com.localskills.app.skill.manifest.RuleAction

/**
 * In-progress, mutable representation of a skill the user is authoring.
 *
 * Design note: we keep the draft as a single immutable [ManifestDraft] data class
 * exposed via a `StateFlow<ManifestDraft>` in the ViewModel rather than a tree
 * of per-field flows. Reasons:
 *  * Simpler diffing — Compose recomposes off a single reference.
 *  * Templates can replace the entire draft atomically.
 *  * Validation is a pure function of the snapshot, not a stream of events.
 *
 * The draft intentionally stores raw user strings (e.g. limits as strings)
 * so the UI can show partially-typed input without losing characters; the
 * coercion to typed values happens in [DraftToManifest.build].
 */
data class ManifestDraft(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val inputs: Set<InputKind> = emptySet(),
    val instruction: String = "",
    val outputFields: List<DraftField> = emptyList(),
    val patterns: List<DraftPattern> = emptyList(),
    val rules: List<DraftRule> = emptyList(),
    val limits: DraftLimits = DraftLimits(),
)

data class DraftField(
    val name: String = "",
    val type: FieldType = FieldType.TEXT,
)

data class DraftPattern(
    val name: String = "",
    val regex: String = "",
)

data class DraftRule(
    val condition: String = "",
    val action: RuleAction = RuleAction.NOTIFY,
    val title: String = "",
    val body: String = "",
)

/**
 * Limits stored as strings while editing so the UI can hold partial input
 * (e.g. an empty field while the user is typing). [DraftToManifest] coerces
 * blanks to the platform defaults from [Limits].
 */
data class DraftLimits(
    val maxInputChars: String = Limits().maxInputChars.toString(),
    val maxOcrPages: String = Limits().maxOcrPages.toString(),
    val maxModelTokens: String = Limits().maxModelTokens.toString(),
    val maxRuntimeMs: String = Limits().maxRuntimeMs.toString(),
)
