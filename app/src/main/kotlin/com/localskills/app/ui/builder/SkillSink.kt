package com.localskills.app.ui.builder

import com.localskills.app.data.db.entity.SkillSource
import com.localskills.app.skill.manifest.SkillManifest

/**
 * Builder-local port for persisting a finished manifest. Defined in the
 * builder package so P2 doesn't take a hard dependency on the Skill
 * repository being shipped by P1 in parallel.
 *
 * P5-WIRING: bind this to `SkillRepository` (created in P1) — its `upsert`
 * should map the manifest to a `SkillEntity` with `source = USER_AUTHORED`
 * and persist via `SkillDao.upsert(...)`. See [SkillSinkPlaceholder] for the
 * default no-op binding used until P1 lands.
 */
interface SkillSink {
    /**
     * Persists [manifest] as a user-authored skill, returning the saved
     * skill id on success. Implementations should treat this as upsert by
     * `manifest.id`.
     */
    suspend fun upsert(manifest: SkillManifest, source: SkillSource = SkillSource.USER_AUTHORED): Result<String>
}
