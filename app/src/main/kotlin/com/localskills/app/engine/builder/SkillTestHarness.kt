package com.localskills.app.engine.builder

import com.localskills.app.engine.runtime.ExtractorInput
import com.localskills.app.engine.runtime.ExtractorOutput
import com.localskills.app.engine.runtime.ExtractorRuntime
import com.localskills.app.engine.validation.SchemaValidator
import com.localskills.app.engine.validation.ValidationError
import com.localskills.app.skill.manifest.SkillManifest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-builder harness: turns a [ManifestDraft] + sample input into the same
 * extract/validate cycle the runner will execute, so authors can spot
 * schema or prompt issues before saving.
 *
 * The harness deliberately reuses [ExtractorRuntime] (default binding is
 * [com.localskills.app.engine.runtime.EchoRuntime] in P2) so swapping in the
 * real LLM later requires no changes here.
 */
@Singleton
class SkillTestHarness @Inject constructor(
    private val draftToManifest: DraftToManifest,
    private val runtime: ExtractorRuntime,
    private val validator: SchemaValidator,
) {

    suspend fun run(draft: ManifestDraft, sampleInput: String): TestReport {
        val build = draftToManifest.build(draft)
        val manifest = build.getOrElse { cause ->
            val errs = (cause as? DraftValidationException)?.errors.orEmpty()
            return TestReport.DraftInvalid(errs)
        }
        return runManifest(manifest, sampleInput)
    }

    suspend fun runManifest(manifest: SkillManifest, sampleInput: String): TestReport {
        val capped = sampleInput.take(manifest.limits.maxInputChars)
        val extractorInput = ExtractorInput(text = capped)
        val output = runCatching { runtime.extract(manifest, extractorInput) }
            .getOrElse { return TestReport.RuntimeFailed(it.message ?: "runtime threw") }
        return when (output) {
            is ExtractorOutput.Failure -> TestReport.RuntimeFailed(output.reason)
            is ExtractorOutput.Json -> {
                val report = validator.validate(manifest, output.payload)
                TestReport.Success(
                    rawJson = output.payload,
                    fieldValues = parseFieldValues(manifest, output.payload),
                    validationErrors = report.errors,
                    passRate = report.passRate,
                    valid = report.ok,
                )
            }
        }
    }

    private fun parseFieldValues(
        manifest: SkillManifest,
        payload: String,
    ): Map<String, String> {
        val obj = runCatching { Json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
            ?: return emptyMap()
        val out = linkedMapOf<String, String>()
        manifest.outputSchema.keys.forEach { key ->
            val element = obj[key]
            out[key] = when (element) {
                null -> "<missing>"
                is JsonPrimitive -> element.contentOrNull ?: element.toString()
                else -> element.toString()
            }
        }
        return out
    }
}

/** UI-facing result of a single harness run. */
sealed interface TestReport {
    /** Draft cannot even be built; show the field-level errors. */
    data class DraftInvalid(val errors: List<DraftError>) : TestReport

    /** Runtime threw or produced [ExtractorOutput.Failure]. */
    data class RuntimeFailed(val reason: String) : TestReport

    /**
     * Runtime returned JSON. [valid] reflects whether it conforms to the
     * declared output schema; UI may still display partial values when not.
     */
    data class Success(
        val rawJson: String,
        val fieldValues: Map<String, String>,
        val validationErrors: List<ValidationError>,
        val passRate: Double,
        val valid: Boolean,
    ) : TestReport
}
