package com.localskills.app.engine.validation

import com.localskills.app.skill.manifest.FieldType
import com.localskills.app.skill.manifest.SkillManifest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifies that an extractor's output conforms to the manifest's
 * declared output_schema. The validator is intentionally strict: it
 * rejects non-JSON output, missing keys, and value-shape mismatches.
 */
@Singleton
class SchemaValidator @Inject constructor() {

    fun validate(manifest: SkillManifest, payloadJson: String): ValidationReport {
        val parsed = runCatching { Json.parseToJsonElement(payloadJson) }.getOrNull()
        if (parsed !is JsonObject) {
            return ValidationReport(
                ok = false,
                errors = listOf(ValidationError("<root>", "expected JSON object")),
                passRate = 0.0,
            )
        }
        val errors = mutableListOf<ValidationError>()
        manifest.outputSchema.forEach { (field, type) ->
            val value = parsed[field]
            if (value == null || value is JsonNull) {
                errors += ValidationError(field, "missing value")
                return@forEach
            }
            if (value !is JsonPrimitive) {
                errors += ValidationError(field, "expected primitive, got ${value::class.simpleName}")
                return@forEach
            }
            when (type) {
                FieldType.TEXT, FieldType.CURRENCY -> if (!value.isString) {
                    errors += ValidationError(field, "expected text")
                }
                FieldType.NUMBER -> if (value.doubleOrNullSafe() == null) {
                    errors += ValidationError(field, "expected number")
                }
                FieldType.BOOLEAN -> if (value.booleanOrNullSafe() == null) {
                    errors += ValidationError(field, "expected boolean")
                }
                FieldType.DATE -> {
                    val text = value.contentOrNullSafe()
                    if (text == null || !isIsoDate(text)) {
                        errors += ValidationError(field, "expected ISO-8601 date")
                    }
                }
            }
        }
        val total = manifest.outputSchema.size.coerceAtLeast(1)
        val passRate = (total - errors.size).toDouble() / total
        return ValidationReport(ok = errors.isEmpty(), errors = errors, passRate = passRate)
    }

    private fun isIsoDate(text: String): Boolean = try {
        LocalDate.parse(text); true
    } catch (_: DateTimeParseException) {
        false
    }

    private fun JsonPrimitive.doubleOrNullSafe(): Double? =
        if (isString) null else content.toDoubleOrNull()

    private fun JsonPrimitive.booleanOrNullSafe(): Boolean? =
        if (isString) null else when (content) {
            "true" -> true
            "false" -> false
            else -> null
        }

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (isString) content else null
}

data class ValidationReport(
    val ok: Boolean,
    val errors: List<ValidationError>,
    val passRate: Double,
)

data class ValidationError(val field: String, val message: String)
