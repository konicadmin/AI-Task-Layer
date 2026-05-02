package com.localskills.app.skill.manifest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Canonical declarative description of a user-authored skill.
 *
 * Skills are data, not code. The runtime reads a manifest and orchestrates
 * input collection, prompting, schema validation, and rule scheduling.
 * Imported manifests must never carry executable code.
 */
@Serializable
data class SkillManifest(
    @SerialName("manifest_version") val manifestVersion: String = MANIFEST_VERSION,
    val id: String,
    val name: String,
    val description: String = "",
    val inputs: List<InputKind>,
    val instruction: String,
    @SerialName("output_schema") val outputSchema: Map<String, FieldType>,
    val patterns: List<Pattern> = emptyList(),
    val validators: List<String> = emptyList(),
    val rules: List<RuleSpec> = emptyList(),
    val limits: Limits = Limits(),
) {
    companion object {
        const val MANIFEST_VERSION: String = "skill/1"
    }
}

@Serializable
enum class InputKind {
    @SerialName("text") TEXT,
    @SerialName("image") IMAGE,
    @SerialName("file") FILE,
}

@Serializable
enum class FieldType {
    @SerialName("text") TEXT,
    @SerialName("number") NUMBER,
    @SerialName("date") DATE,
    @SerialName("boolean") BOOLEAN,
    @SerialName("currency") CURRENCY,
}

@Serializable
data class Pattern(
    val name: String,
    val regex: String,
)

@Serializable
data class RuleSpec(
    val condition: String,
    val action: RuleAction,
    val title: String? = null,
    val body: String? = null,
)

@Serializable
enum class RuleAction {
    @SerialName("notify") NOTIFY,
    @SerialName("tag") TAG,
    @SerialName("save_draft") SAVE_DRAFT,
}

@Serializable
data class Limits(
    @SerialName("max_input_chars") val maxInputChars: Int = 12_000,
    @SerialName("max_ocr_pages") val maxOcrPages: Int = 2,
    @SerialName("max_model_tokens") val maxModelTokens: Int = 4_096,
    @SerialName("max_runtime_ms") val maxRuntimeMs: Long = 4_000,
)
