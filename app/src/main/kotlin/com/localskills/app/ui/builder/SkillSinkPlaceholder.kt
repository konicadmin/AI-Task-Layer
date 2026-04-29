package com.localskills.app.ui.builder

import android.util.Log
import com.localskills.app.data.db.entity.SkillSource
import com.localskills.app.skill.manifest.ManifestCodec
import com.localskills.app.skill.manifest.SkillManifest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [SkillSink] used until P1 wires the real `SkillRepository`. Logs
 * the manifest and returns success so the builder save flow is exercisable
 * in isolation.
 *
 * P5-WIRING: replace this binding in `BuilderModule` with the implementation
 * backed by `SkillRepository` once P1 lands.
 */
@Singleton
class SkillSinkPlaceholder @Inject constructor() : SkillSink {
    override suspend fun upsert(
        manifest: SkillManifest,
        source: SkillSource,
    ): Result<String> {
        Log.i(TAG, "upsert (placeholder) source=$source id=${manifest.id}\n" + ManifestCodec.encodeJson(manifest))
        return Result.success(manifest.id)
    }

    private companion object {
        const val TAG = "SkillSinkPlaceholder"
    }
}
