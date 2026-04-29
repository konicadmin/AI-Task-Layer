package com.localskills.app.di

import com.localskills.app.skill.share.install.InstallOutcome
import com.localskills.app.skill.share.install.SkillInstaller
import com.localskills.app.skill.manifest.SkillManifest
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings owned by the share / import slice (Phase 3).
 *
 * Phase 3 only depends on the [SkillInstaller] port. Until P5 wires the
 * runner-owned repository, we provide a deliberately inert default that
 * fails loudly at the install boundary. This keeps the importer pipeline
 * testable end-to-end without pulling in P1's repository code.
 *
 * P5-WIRING: replace [provideSkillInstaller] with a binding that
 * delegates to the canonical `SkillRepository` (from P1), persisting
 * the manifest as a `SkillEntity` with `enabled = false` and
 * `source = IMPORTED`. The default below MUST be removed in P5 — it is
 * a placeholder, not a production path.
 */
@Module
@InstallIn(SingletonComponent::class)
object ShareModule {

    @Provides
    @Singleton
    fun provideSkillInstaller(): SkillInstaller = PendingSkillInstaller

    /**
     * P5-WIRING: this default exists only so the Hilt graph compiles
     * before the runner repository lands. It refuses to install and
     * clearly identifies itself in error messages so a wiring miss is
     * impossible to mistake for a transient install failure.
     */
    private object PendingSkillInstaller : SkillInstaller {
        override suspend fun installDisabled(manifest: SkillManifest): InstallOutcome =
            InstallOutcome.Failed(
                reason = "SkillInstaller not yet bound — P5 wiring pending",
            )
    }
}
