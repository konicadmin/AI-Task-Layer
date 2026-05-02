package com.localskills.app.engine.runner

import android.net.Uri
import com.localskills.app.data.db.entity.ResultEntity
import com.localskills.app.data.db.entity.RunEntity
import com.localskills.app.data.repo.RunRepository
import com.localskills.app.data.repo.SkillRepository
import com.localskills.app.engine.CaptureInput
import com.localskills.app.engine.confidence.ConfidenceEngine
import com.localskills.app.engine.confidence.ConfidenceSignals
import com.localskills.app.engine.ocr.OcrEngine
import com.localskills.app.engine.runtime.ExtractorInput
import com.localskills.app.engine.runtime.ExtractorOutput
import com.localskills.app.engine.runtime.ExtractorRuntime
import com.localskills.app.engine.validation.SchemaValidator
import com.localskills.app.engine.validation.ValidationReport
import com.localskills.app.skill.manifest.InputKind
import com.localskills.app.skill.manifest.SkillManifest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates a single skill execution end-to-end:
 *
 *   capture -> (optional OCR) -> extractor -> schema validation
 *           -> confidence scoring -> persisted Run + Result
 *
 * The runner is intentionally model-agnostic — it talks only to the
 * [ExtractorRuntime] interface so the EchoRuntime placeholder, an
 * on-device LLM, or a tests double can all participate without changes
 * here.
 *
 * Rule scheduling is NOT performed here. Rules are persisted alongside
 * skills by [SkillRepository]. P4-WIRING: when the rule scheduler exists,
 * call it from the success branch of [run] with the saved Result so
 * conditions like `expiry_date is in 3 days` can be enqueued.
 */
@Singleton
class SkillRunner @Inject constructor(
    private val skillRepository: SkillRepository,
    private val runRepository: RunRepository,
    private val extractorRuntime: ExtractorRuntime,
    private val schemaValidator: SchemaValidator,
    private val confidenceEngine: ConfidenceEngine,
    private val ocrEngine: OcrEngine,
) {

    suspend fun run(skillId: String, capture: CaptureInput): RunOutcome {
        val installed = skillRepository.findById(skillId)
            ?: return RunOutcome.SkillNotFound(skillId)
        val manifest = installed.manifest
        val run = runRepository.startRun(skillId, capture)

        val started = System.nanoTime()
        val outcome = executePipeline(manifest, capture)
        val durationMs = (System.nanoTime() - started) / 1_000_000L

        return when (outcome) {
            is PipelineResult.Success -> {
                val updated = runRepository.completeRun(run, durationMs)
                val saved = runRepository.saveResult(
                    runId = run.id,
                    skillId = skillId,
                    payloadJson = outcome.payloadJson,
                    confidence = outcome.confidence,
                )
                RunOutcome.Success(
                    run = updated,
                    result = saved,
                    confidence = outcome.confidence,
                    validation = outcome.validation,
                )
            }
            is PipelineResult.Failure -> {
                val updated = runRepository.failRun(run, durationMs, outcome.message)
                RunOutcome.Failure(updated, outcome.message, outcome.validation)
            }
        }
    }

    private suspend fun executePipeline(
        manifest: SkillManifest,
        capture: CaptureInput,
    ): PipelineResult {
        val rawText = capture.rawText.orEmpty()
        if (rawText.length > manifest.limits.maxInputChars) {
            return PipelineResult.Failure(
                "Input exceeds max_input_chars (${manifest.limits.maxInputChars})",
            )
        }

        val ocrText = if (capture.kind == InputKind.IMAGE && capture.artifactPath != null) {
            runCatching { ocrEngine.recognize(Uri.parse(capture.artifactPath)).fullText }
                .getOrElse { return PipelineResult.Failure("OCR failed: ${it.message}") }
        } else {
            null
        }

        val extractorInput = ExtractorInput(
            text = rawText,
            artifactPath = capture.artifactPath,
            ocrText = ocrText,
        )

        val output = runCatching { extractorRuntime.extract(manifest, extractorInput) }
            .getOrElse { return PipelineResult.Failure("Extractor crashed: ${it.message}") }

        val payloadJson = when (output) {
            is ExtractorOutput.Json -> output.payload
            is ExtractorOutput.Failure ->
                return PipelineResult.Failure("Extractor returned failure: ${output.reason}")
        }

        val report = schemaValidator.validate(manifest, payloadJson)
        if (!report.ok) {
            // We still return the payload via the result store on success;
            // a hard validation failure (root not object) is treated as a run failure.
            if (report.passRate <= 0.0) {
                return PipelineResult.Failure(
                    "Output failed schema validation: ${report.errors.firstOrNull()?.message}",
                    report,
                )
            }
            // Partial pass — keep going so the user can fix in the review UI.
        }

        val signals = ConfidenceSignals(
            classifierMargin = 1.0,
            evidenceCoverage = if (capture.rawText.isNullOrBlank() && ocrText.isNullOrBlank()) 0.0 else 1.0,
            validatorPassRate = report.passRate,
            sourceReliability = sourceReliability(capture, ocrText),
        )
        val confidence = confidenceEngine.score(signals)

        return PipelineResult.Success(payloadJson, confidence, report)
    }

    private fun sourceReliability(capture: CaptureInput, ocrText: String?): Double = when {
        capture.kind == InputKind.TEXT && !capture.rawText.isNullOrBlank() -> 1.0
        capture.kind == InputKind.IMAGE && !ocrText.isNullOrBlank() -> 0.7
        else -> 0.4
    }

    private sealed interface PipelineResult {
        data class Success(
            val payloadJson: String,
            val confidence: Double,
            val validation: ValidationReport,
        ) : PipelineResult

        data class Failure(
            val message: String,
            val validation: ValidationReport? = null,
        ) : PipelineResult
    }
}

/**
 * Public outcome of a single [SkillRunner.run] invocation. The Run row is
 * always persisted before this returns; on Success the matching Result row
 * is persisted too.
 */
sealed interface RunOutcome {
    data class Success(
        val run: RunEntity,
        val result: ResultEntity,
        val confidence: Double,
        val validation: ValidationReport,
    ) : RunOutcome

    data class Failure(
        val run: RunEntity,
        val message: String,
        val validation: ValidationReport?,
    ) : RunOutcome

    data class SkillNotFound(val skillId: String) : RunOutcome
}
