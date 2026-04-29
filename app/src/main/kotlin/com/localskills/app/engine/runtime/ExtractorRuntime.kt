package com.localskills.app.engine.runtime

import com.localskills.app.skill.manifest.SkillManifest

/**
 * Pluggable local-LLM runtime. Concrete implementations bind to LiteRT-LM,
 * MediaPipe LLM Inference, or a simple echo runner for tests. Implementations
 * MUST honour the manifest's resource limits and return strict JSON only.
 */
interface ExtractorRuntime {
    val id: String
    val isReady: Boolean

    suspend fun extract(
        manifest: SkillManifest,
        input: ExtractorInput,
    ): ExtractorOutput
}

data class ExtractorInput(
    val text: String,
    val artifactPath: String? = null,
    val ocrText: String? = null,
)

sealed interface ExtractorOutput {
    /** Raw JSON string conforming to the manifest's output_schema. */
    data class Json(val payload: String) : ExtractorOutput
    data class Failure(val reason: String, val cause: Throwable? = null) : ExtractorOutput
}
