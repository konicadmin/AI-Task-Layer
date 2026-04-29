package com.localskills.app.di

import com.localskills.app.ui.builder.SkillSink
import com.localskills.app.ui.builder.SkillSinkPlaceholder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings unique to the Skill Builder.
 *
 * P5-WIRING: replace [SkillSinkPlaceholder] with the implementation backed
 * by `SkillRepository` (created in P1) once the repository module lands.
 * The repository implementation should map [com.localskills.app.skill.manifest.SkillManifest]
 * → [com.localskills.app.data.db.entity.SkillEntity] with
 * `source = USER_AUTHORED` and call `SkillDao.upsert(...)`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BuilderModule {

    @Binds
    @Singleton
    abstract fun bindSkillSink(impl: SkillSinkPlaceholder): SkillSink
}
