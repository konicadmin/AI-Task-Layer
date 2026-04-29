package com.localskills.app.skill.share.install

import com.localskills.app.data.db.entity.SkillSource
import com.localskills.app.data.repo.SkillRepository
import com.localskills.app.skill.manifest.SkillManifest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [SkillInstaller] that writes through [SkillRepository]. Imported
 * skills always land disabled so the user must explicitly enable them after
 * reviewing the lint and sandbox report.
 */
@Singleton
class SkillInstallerImpl @Inject constructor(
    private val repository: SkillRepository,
) : SkillInstaller {

    override suspend fun installDisabled(manifest: SkillManifest): InstallOutcome = try {
        val existing = repository.findById(manifest.id)
        if (existing != null) {
            InstallOutcome.AlreadyInstalled(existing.id)
        } else {
            val installed = repository.upsert(
                manifest = manifest,
                source = SkillSource.IMPORTED,
                enabled = false,
            )
            InstallOutcome.Installed(installed.id)
        }
    } catch (t: Throwable) {
        InstallOutcome.Failed(reason = t.message ?: "install failed", cause = t)
    }
}
