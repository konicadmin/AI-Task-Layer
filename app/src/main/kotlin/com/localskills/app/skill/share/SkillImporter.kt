package com.localskills.app.skill.share

import com.localskills.app.skill.manifest.ManifestCodec
import com.localskills.app.skill.manifest.SkillManifest
import com.localskills.app.skill.share.install.InstallOutcome
import com.localskills.app.skill.share.install.SkillInstaller
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the import pipeline for a shared manifest payload:
 *
 *   raw text → lint → decode → sandbox dry-run → install (disabled)
 *
 * Each stage short-circuits on failure. Imported skills are always
 * installed with `enabled = false` and `source = IMPORTED`; the user
 * must explicitly review and enable them from the library.
 *
 * P5-WIRING: the [installer] dependency is satisfied by an in-package
 * port. P5 will bind it to the runner-owned `SkillRepository` so the
 * canonical store is the only place skills live.
 */
@Singleton
class SkillImporter @Inject constructor(
    private val linter: SkillLinter,
    private val sandbox: SkillSandbox,
    private val installer: SkillInstaller,
) {

    /** Parse + lint only — used by the review screen to populate the UI. */
    suspend fun preview(raw: String): ImportPreview {
        val report = linter.lint(raw)
        if (report is SafetyReport.Reject) {
            return ImportPreview(
                manifest = null,
                safety = report,
                sandbox = null,
            )
        }
        val manifest = ManifestCodec.decode(raw).getOrElse { cause ->
            return ImportPreview(
                manifest = null,
                safety = SafetyReport.Reject(
                    reasons = listOf("manifest did not decode: ${cause.message ?: "unknown error"}"),
                ),
                sandbox = null,
            )
        }
        val sandboxReport = sandbox.dryRun(manifest)
        return ImportPreview(
            manifest = manifest,
            safety = report,
            sandbox = sandboxReport,
        )
    }

    /**
     * Runs the full pipeline including install. Caller is expected to
     * have already shown the lint/sandbox report to the user.
     */
    suspend fun import(raw: String): ImportResult {
        val preview = preview(raw)
        val manifest = preview.manifest
            ?: return ImportResult.Rejected(
                reasons = (preview.safety as? SafetyReport.Reject)?.reasons
                    ?: listOf("manifest could not be decoded"),
            )
        val safety = preview.safety
        if (safety is SafetyReport.Reject) {
            return ImportResult.Rejected(reasons = safety.reasons)
        }
        val sandbox = preview.sandbox
        if (sandbox != null && !sandbox.ok) {
            return ImportResult.Rejected(reasons = sandbox.issues)
        }
        val warnings = (safety as? SafetyReport.Warnings)?.warnings ?: emptyList()
        return when (val outcome = installer.installDisabled(manifest)) {
            is InstallOutcome.Installed -> ImportResult.Installed(
                id = outcome.id,
                manifest = manifest,
                warnings = warnings,
            )
            is InstallOutcome.AlreadyInstalled -> ImportResult.AlreadyInstalled(
                id = outcome.id,
                manifest = manifest,
            )
            is InstallOutcome.Failed -> ImportResult.Rejected(
                reasons = listOf("install failed: ${outcome.reason}"),
            )
        }
    }
}

/** Snapshot returned by [SkillImporter.preview] for the review UI. */
data class ImportPreview(
    val manifest: SkillManifest?,
    val safety: SafetyReport,
    val sandbox: SandboxReport?,
)

/** Final outcome of [SkillImporter.import]. */
sealed interface ImportResult {
    data class Installed(
        val id: String,
        val manifest: SkillManifest,
        val warnings: List<String> = emptyList(),
    ) : ImportResult

    data class AlreadyInstalled(
        val id: String,
        val manifest: SkillManifest,
    ) : ImportResult

    data class Rejected(val reasons: List<String>) : ImportResult
}
