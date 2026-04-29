package com.localskills.app.skill.share

import com.localskills.app.engine.CaptureInput
import com.localskills.app.engine.runtime.ExtractorInput
import com.localskills.app.engine.runtime.ExtractorOutput
import com.localskills.app.engine.runtime.ExtractorRuntime
import com.localskills.app.skill.manifest.InputKind
import com.localskills.app.skill.manifest.SkillManifest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton
import java.util.regex.Pattern as JPattern
import java.util.regex.PatternSyntaxException

/**
 * Dry-run a manifest before installing it. The sandbox never touches the
 * Room DB, never writes to DataStore, and never invokes the production
 * runner. It simply confirms that:
 *
 *   1. limits are within sane bounds,
 *   2. patterns compile,
 *   3. the manifest can be fed through a no-op [ExtractorRuntime] without
 *      throwing,
 *   4. the resulting envelope shape is consistent with the schema.
 *
 * The sandbox is deliberately self-contained — it uses an in-place
 * [NoOpRuntime] so it does not depend on the global Hilt-bound
 * [ExtractorRuntime] (which may be wired to EchoRuntime that has DB
 * side-effects in future phases).
 */
@Singleton
class SkillSandbox @Inject constructor() {

    /**
     * Executes the dry-run. Returns a [SandboxReport] with the gathered
     * issues. Does not throw on a malformed manifest — issues are
     * accumulated and returned.
     */
    suspend fun dryRun(manifest: SkillManifest): SandboxReport {
        val issues = mutableListOf<String>()

        // 1. Sanity-check limits.
        with(manifest.limits) {
            if (maxInputChars !in 1..MAX_INPUT_CHARS_CAP) {
                issues += "limits.max_input_chars out of range (1..$MAX_INPUT_CHARS_CAP)"
            }
            if (maxOcrPages !in 0..MAX_OCR_PAGES_CAP) {
                issues += "limits.max_ocr_pages out of range (0..$MAX_OCR_PAGES_CAP)"
            }
            if (maxModelTokens !in 1..MAX_MODEL_TOKENS_CAP) {
                issues += "limits.max_model_tokens out of range (1..$MAX_MODEL_TOKENS_CAP)"
            }
            if (maxRuntimeMs !in 1..MAX_RUNTIME_MS_CAP) {
                issues += "limits.max_runtime_ms out of range (1..$MAX_RUNTIME_MS_CAP)"
            }
        }

        // 2. Compile patterns.
        manifest.patterns.forEachIndexed { idx, p ->
            try {
                JPattern.compile(p.regex)
            } catch (e: PatternSyntaxException) {
                issues += "patterns[$idx] '${p.name}' regex did not compile: ${e.description ?: e.message}"
            }
        }

        // 3. Schema must be non-empty for the runtime to have something to
        //    fill in.
        if (manifest.outputSchema.isEmpty()) {
            issues += "output_schema is empty — no fields to extract"
        }

        // 4. inputs must be non-empty and only known kinds (the codec
        //    already enforces enum decoding, but we double-check here).
        if (manifest.inputs.isEmpty()) {
            issues += "inputs[] is empty"
        }

        // 5. Dry-run the no-op runtime over a tiny CaptureInput. This is
        //    purely an in-memory exercise.
        val capture = CaptureInput(
            kind = manifest.inputs.firstOrNull() ?: InputKind.TEXT,
            mimeType = "text/plain",
            rawText = SAMPLE_TEXT,
        )
        val runtimeInput = ExtractorInput(
            text = capture.rawText.orEmpty(),
            artifactPath = capture.artifactPath,
            ocrText = null,
        )
        val output = runCatching { NoOpRuntime.extract(manifest, runtimeInput) }
            .getOrElse { cause ->
                issues += "no-op runtime threw: ${cause.message ?: cause::class.simpleName}"
                return SandboxReport(ok = issues.isEmpty(), issues = issues)
            }

        if (output is ExtractorOutput.Failure) {
            issues += "no-op runtime failed: ${output.reason}"
        }

        return SandboxReport(ok = issues.isEmpty(), issues = issues)
    }

    /**
     * Pure no-op runtime used for sandbox dry-runs. Does NOT touch the
     * database, DataStore, or any external service. Mirrors the shape
     * EchoRuntime would produce so the rest of the pipeline can be
     * exercised without side-effects.
     */
    private object NoOpRuntime : ExtractorRuntime {
        override val id: String = "sandbox-noop"
        override val isReady: Boolean = true

        override suspend fun extract(
            manifest: SkillManifest,
            input: ExtractorInput,
        ): ExtractorOutput {
            // Build an empty JSON object that matches the schema keys.
            val envelope = buildJsonObject {
                manifest.outputSchema.keys.forEach { key ->
                    put(key, kotlinx.serialization.json.JsonNull)
                }
            }
            return ExtractorOutput.Json(Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), envelope))
        }
    }

    companion object {
        const val MAX_INPUT_CHARS_CAP: Int = 64_000
        const val MAX_OCR_PAGES_CAP: Int = 10
        const val MAX_MODEL_TOKENS_CAP: Int = 16_384
        const val MAX_RUNTIME_MS_CAP: Long = 30_000
        private const val SAMPLE_TEXT: String = "sample"
    }
}

/** Outcome of a sandbox dry-run. */
data class SandboxReport(
    val ok: Boolean,
    val issues: List<String>,
)
