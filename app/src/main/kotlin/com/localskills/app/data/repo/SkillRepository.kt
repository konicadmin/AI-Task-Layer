package com.localskills.app.data.repo

import com.localskills.app.data.db.dao.RuleDao
import com.localskills.app.data.db.dao.SkillDao
import com.localskills.app.data.db.entity.RuleEntity
import com.localskills.app.data.db.entity.SkillEntity
import com.localskills.app.data.db.entity.SkillSource
import com.localskills.app.skill.manifest.ManifestCodec
import com.localskills.app.skill.manifest.SkillManifest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the [SkillManifest] world (immutable canonical data) and the
 * persistence world ([SkillEntity] + [RuleEntity] rows). The repository
 * owns the only place we hand-roll manifest <-> entity conversion so the
 * runner, library UI, and importer all see a consistent shape.
 *
 * Rule rows are persisted alongside their parent skill so the P4 rule
 * engine can pick them up later — this layer never schedules anything.
 */
@Singleton
class SkillRepository @Inject constructor(
    private val skillDao: SkillDao,
    private val ruleDao: RuleDao,
) {

    fun observeAll(): Flow<List<InstalledSkill>> =
        skillDao.observeAll().map { rows -> rows.mapNotNull { it.toInstalledOrNull() } }

    fun observeEnabled(): Flow<List<InstalledSkill>> =
        skillDao.observeEnabled().map { rows -> rows.mapNotNull { it.toInstalledOrNull() } }

    suspend fun findById(id: String): InstalledSkill? =
        skillDao.findById(id)?.toInstalledOrNull()

    suspend fun setEnabled(id: String, enabled: Boolean) {
        skillDao.setEnabled(id, enabled)
    }

    suspend fun delete(id: String) {
        skillDao.deleteById(id)
    }

    /**
     * Upserts a skill from a [SkillManifest]. Rules declared in the manifest
     * are persisted as `enabled = true` rows attached to the skill so P4 can
     * later observe and schedule them. Existing rule rows for the skill are
     * NOT pruned here — that's a separate concern handled when the user
     * edits/deletes rules.
     *
     * P4-WIRING: when the rule engine scheduler exists, observe rule rows
     * for changes here (or at app start) and (re)register WorkManager jobs.
     */
    suspend fun upsert(
        manifest: SkillManifest,
        source: SkillSource,
        enabled: Boolean,
        installedAt: Instant = Instant.now(),
    ): InstalledSkill {
        val now = Instant.now()
        val existing = skillDao.findById(manifest.id)
        val entity = SkillEntity(
            id = manifest.id,
            name = manifest.name,
            description = manifest.description,
            manifestJson = ManifestCodec.encodeJson(manifest),
            enabled = enabled,
            source = source,
            installedAt = existing?.installedAt ?: installedAt,
            updatedAt = now,
        )
        skillDao.upsert(entity)

        manifest.rules.forEachIndexed { index, rule ->
            ruleDao.upsert(
                RuleEntity(
                    id = "${manifest.id}#rule:$index",
                    skillId = manifest.id,
                    resultId = null,
                    expression = rule.condition,
                    action = rule.action.name,
                    title = rule.title,
                    body = rule.body,
                    scheduleHint = null,
                    enabled = true,
                    lastFiredAt = null,
                    createdAt = now,
                ),
            )
        }
        return InstalledSkill(entity, manifest)
    }

    /** Generate a stable unique id for a manifest where the user did not provide one. */
    fun newId(prefix: String = "skill"): String = "$prefix-${UUID.randomUUID()}"

    private fun SkillEntity.toInstalledOrNull(): InstalledSkill? =
        ManifestCodec.decode(manifestJson).getOrNull()?.let { InstalledSkill(this, it) }
}

/**
 * View of a stored skill that pairs the row with its decoded manifest so
 * callers don't have to re-parse JSON on every read.
 */
data class InstalledSkill(
    val entity: SkillEntity,
    val manifest: SkillManifest,
) {
    val id: String get() = entity.id
    val enabled: Boolean get() = entity.enabled
    val source: SkillSource get() = entity.source
}
