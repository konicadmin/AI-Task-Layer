package com.localskills.app.ui.builder

import com.localskills.app.data.db.entity.SkillSource
import com.localskills.app.skill.manifest.SkillManifest

/**
 * Builder-local port for persisting a finished manifest. Bound to a
 * [SkillRepository][com.localskills.app.data.repo.SkillRepository]-backed
 * implementation in `BuilderModule`; the indirection keeps the builder
 * package free of database concerns.
 */
interface SkillSink {
    /**
     * Persists [manifest] as a user-authored skill, returning the saved
     * skill id on success. Implementations should treat this as upsert by
     * `manifest.id`.
     */
    suspend fun upsert(manifest: SkillManifest, source: SkillSource = SkillSource.USER_AUTHORED): Result<String>
}
