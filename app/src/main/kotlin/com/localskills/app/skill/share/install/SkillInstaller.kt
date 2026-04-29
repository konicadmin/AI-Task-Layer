package com.localskills.app.skill.share.install

import com.localskills.app.skill.manifest.SkillManifest

/**
 * Port interface for persisting an imported manifest into the local
 * skill library. Phase 3 only depends on this contract; the concrete
 * binding lives in P5 once the runner-owned `SkillRepository` exists.
 *
 * Imported skills always land with `enabled = false` and `source =
 * IMPORTED` so the user must explicitly review and turn them on.
 *
 * P5-WIRING: bind this to `SkillRepository` (owned by P1 runner) inside
 * a Hilt module so the importer writes through the canonical store.
 */
interface SkillInstaller {

    /**
     * Persists [manifest] in a disabled state. Returns the stored skill
     * id on success, or [InstallOutcome.AlreadyInstalled] if a skill with
     * the same id is already present (the caller decides whether to
     * upgrade — Phase 3 simply surfaces the conflict).
     */
    suspend fun installDisabled(manifest: SkillManifest): InstallOutcome
}

sealed interface InstallOutcome {
    data class Installed(val id: String) : InstallOutcome
    data class AlreadyInstalled(val id: String) : InstallOutcome
    data class Failed(val reason: String, val cause: Throwable? = null) : InstallOutcome
}
