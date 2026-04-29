package com.localskills.app.engine.builder

import com.localskills.app.skill.manifest.Limits
import com.localskills.app.skill.manifest.Pattern
import com.localskills.app.skill.manifest.RuleSpec
import com.localskills.app.skill.manifest.SkillManifest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts a [ManifestDraft] into a canonical [SkillManifest].
 *
 * The builder UI calls [build] both for live validation (errors are surfaced
 * before save) and at save time. Validation rules:
 *  * `name` and `instruction` non-empty after trim.
 *  * At least one [com.localskills.app.skill.manifest.InputKind] selected.
 *  * At least one output field, each with a snake_case ASCII identifier; names unique.
 *  * `id` either supplied as a valid slug or auto-derived from `name`.
 *  * Pattern names follow field-name rules; regex must compile.
 *  * Limits parse as positive numbers (blanks fall back to [Limits] defaults).
 */
@Singleton
class DraftToManifest @Inject constructor() {

    fun build(draft: ManifestDraft): Result<SkillManifest> {
        val errors = validate(draft)
        if (errors.isNotEmpty()) {
            return Result.failure(DraftValidationException(errors))
        }
        val name = draft.name.trim()
        val id = draft.id.takeIf { it.isNotBlank() }?.trim() ?: defaultIdFromName(name)
        val outputSchema = linkedMapOf<String, com.localskills.app.skill.manifest.FieldType>()
        draft.outputFields.forEach { outputSchema[it.name.trim()] = it.type }
        val patterns = draft.patterns.map { Pattern(name = it.name.trim(), regex = it.regex) }
        val rules = draft.rules.map {
            RuleSpec(
                condition = it.condition.trim(),
                action = it.action,
                title = it.title.takeIf { t -> t.isNotBlank() },
                body = it.body.takeIf { b -> b.isNotBlank() },
            )
        }
        val limits = Limits(
            maxInputChars = draft.limits.maxInputChars.toIntOrDefault(Limits().maxInputChars),
            maxOcrPages = draft.limits.maxOcrPages.toIntOrDefault(Limits().maxOcrPages),
            maxModelTokens = draft.limits.maxModelTokens.toIntOrDefault(Limits().maxModelTokens),
            maxRuntimeMs = draft.limits.maxRuntimeMs.toLongOrDefault(Limits().maxRuntimeMs),
        )
        return Result.success(
            SkillManifest(
                id = id,
                name = name,
                description = draft.description.trim(),
                inputs = draft.inputs.toList(),
                instruction = draft.instruction.trim(),
                outputSchema = outputSchema,
                patterns = patterns,
                rules = rules,
                limits = limits,
            ),
        )
    }

    /**
     * Pure validation that returns errors without throwing — used by the UI
     * to highlight invalid form sections live.
     */
    fun validate(draft: ManifestDraft): List<DraftError> {
        val errors = mutableListOf<DraftError>()
        if (draft.name.isBlank()) errors += DraftError(DraftSection.NAME, "Name is required")
        if (draft.instruction.isBlank()) errors += DraftError(DraftSection.INSTRUCTION, "Instruction is required")
        if (draft.inputs.isEmpty()) errors += DraftError(DraftSection.INPUTS, "Pick at least one input type")

        if (draft.id.isNotBlank() && !ID_REGEX.matches(draft.id.trim())) {
            errors += DraftError(DraftSection.ID, "ID must be a slug like coupon-extractor.v1")
        }

        if (draft.outputFields.isEmpty()) {
            errors += DraftError(DraftSection.OUTPUT_FIELDS, "Add at least one output field")
        } else {
            val seen = mutableSetOf<String>()
            draft.outputFields.forEachIndexed { index, field ->
                val n = field.name.trim()
                if (n.isEmpty()) {
                    errors += DraftError(DraftSection.OUTPUT_FIELDS, "Field #${index + 1} needs a name")
                } else if (!FIELD_NAME_REGEX.matches(n)) {
                    errors += DraftError(
                        DraftSection.OUTPUT_FIELDS,
                        "'$n' must be snake_case ASCII (a-z, 0-9, _)",
                    )
                } else if (!seen.add(n)) {
                    errors += DraftError(DraftSection.OUTPUT_FIELDS, "Duplicate field name '$n'")
                }
            }
        }

        draft.patterns.forEachIndexed { index, p ->
            val n = p.name.trim()
            if (n.isNotEmpty() && !FIELD_NAME_REGEX.matches(n)) {
                errors += DraftError(
                    DraftSection.PATTERNS,
                    "Pattern #${index + 1} name must be snake_case ASCII",
                )
            }
            if (p.regex.isNotBlank()) {
                runCatching { Regex(p.regex) }.onFailure {
                    errors += DraftError(
                        DraftSection.PATTERNS,
                        "Pattern #${index + 1} regex is invalid: ${it.message}",
                    )
                }
            }
        }

        draft.rules.forEachIndexed { index, r ->
            if (r.condition.isBlank()) {
                errors += DraftError(DraftSection.RULES, "Rule #${index + 1} needs a condition")
            }
        }

        listOf(
            "max_input_chars" to draft.limits.maxInputChars,
            "max_ocr_pages" to draft.limits.maxOcrPages,
            "max_model_tokens" to draft.limits.maxModelTokens,
            "max_runtime_ms" to draft.limits.maxRuntimeMs,
        ).forEach { (label, raw) ->
            if (raw.isNotBlank() && raw.toLongOrNull()?.let { it > 0 } != true) {
                errors += DraftError(DraftSection.LIMITS, "$label must be a positive integer")
            }
        }

        return errors
    }

    private fun defaultIdFromName(name: String): String {
        val slug = name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "skill" }
        return "$slug.v1"
    }

    private fun String.toIntOrDefault(default: Int): Int =
        if (isBlank()) default else toIntOrNull() ?: default

    private fun String.toLongOrDefault(default: Long): Long =
        if (isBlank()) default else toLongOrNull() ?: default

    companion object {
        private val FIELD_NAME_REGEX = Regex("^[a-z][a-z0-9_]*$")
        private val ID_REGEX = Regex("^[a-z0-9]+(?:[-_.][a-z0-9]+)*$")
    }
}

/**
 * Identifies which form section an error belongs to so the UI can scroll to
 * and highlight the offending block.
 */
enum class DraftSection {
    ID,
    NAME,
    INSTRUCTION,
    INPUTS,
    OUTPUT_FIELDS,
    PATTERNS,
    RULES,
    LIMITS,
}

data class DraftError(val section: DraftSection, val message: String)

class DraftValidationException(
    val errors: List<DraftError>,
) : IllegalArgumentException(errors.joinToString("; ") { "${it.field}: ${it.message}" })
