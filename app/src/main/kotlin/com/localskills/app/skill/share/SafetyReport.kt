package com.localskills.app.skill.share

/**
 * Outcome of running [SkillLinter] over a raw manifest payload.
 *
 * - [Safe]: the manifest is well-formed and contains no suspicious
 *   fields. Importer can proceed straight to install.
 * - [Warnings]: minor issues that don't block install but should be
 *   surfaced to the reviewer.
 * - [Reject]: at least one rule failed; the manifest must NOT be
 *   handed to the parser or installed.
 */
sealed interface SafetyReport {
    val notes: List<String>

    data class Safe(override val notes: List<String> = emptyList()) : SafetyReport

    data class Warnings(
        val warnings: List<String>,
        override val notes: List<String> = emptyList(),
    ) : SafetyReport

    data class Reject(
        val reasons: List<String>,
        override val notes: List<String> = emptyList(),
    ) : SafetyReport

    val isBlocking: Boolean get() = this is Reject
}
