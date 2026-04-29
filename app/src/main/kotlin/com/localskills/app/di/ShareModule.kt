package com.localskills.app.di

import com.localskills.app.skill.share.install.SkillInstaller
import com.localskills.app.skill.share.install.SkillInstallerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ShareModule {

    @Binds
    @Singleton
    abstract fun bindSkillInstaller(impl: SkillInstallerImpl): SkillInstaller
}
