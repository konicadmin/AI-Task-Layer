package com.localskills.app.ui.builder

import com.localskills.app.data.db.entity.SkillSource
import com.localskills.app.data.repo.SkillRepository
import com.localskills.app.skill.manifest.SkillManifest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [SkillSink] backed by the canonical [SkillRepository]. User-
 * authored skills land enabled so the user can immediately run them after
 * saving from the builder.
 */
@Singleton
class SkillSinkImpl @Inject constructor(
    private val repository: SkillRepository,
) : SkillSink {
    override suspend fun upsert(
        manifest: SkillManifest,
        source: SkillSource,
    ): Result<String> = runCatching {
        repository.upsert(manifest = manifest, source = source, enabled = true).id
    }
}
