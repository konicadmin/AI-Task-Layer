package com.localskills.app.engine.runner

import android.content.Context
import com.localskills.app.data.db.entity.SkillSource
import com.localskills.app.data.repo.SkillRepository
import com.localskills.app.skill.manifest.ManifestCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs seed manifests bundled in `assets/seeds/*.json` on first launch.
 *
 * Idempotency contract:
 *  * If the skill row does not exist, install it as `source = SEED`.
 *  * If a row with the same id exists and has been edited by the user
 *    (`updatedAt > installedAt`), leave it alone — never overwrite user
 *    edits.
 *  * Otherwise, refresh the manifest JSON in case the bundled seed has
 *    been updated between app versions.
 */
@Singleton
class SkillSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val skillRepository: SkillRepository,
) {

    suspend fun seedIfNeeded(): SeedReport = withContext(Dispatchers.IO) {
        val installed = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val failed = mutableListOf<Pair<String, String>>()

        val assetMgr = context.assets
        val files = runCatching { assetMgr.list(SEED_DIR) }.getOrNull().orEmpty()
        for (name in files) {
            if (!name.endsWith(".json", ignoreCase = true)) continue
            val path = "$SEED_DIR/$name"
            try {
                val raw = assetMgr.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val manifest = ManifestCodec.decode(raw).getOrThrow()

                val existing = skillRepository.findById(manifest.id)
                if (existing != null && existing.entity.updatedAt.isAfter(existing.entity.installedAt)) {
                    skipped += manifest.id
                    continue
                }
                skillRepository.upsert(
                    manifest = manifest,
                    source = SkillSource.SEED,
                    enabled = true,
                )
                installed += manifest.id
            } catch (t: Throwable) {
                failed += name to (t.message ?: t::class.java.simpleName)
            }
        }
        SeedReport(installed, skipped, failed)
    }

    companion object {
        private const val SEED_DIR = "seeds"
    }
}

data class SeedReport(
    val installed: List<String>,
    val skipped: List<String>,
    val failed: List<Pair<String, String>>,
)
