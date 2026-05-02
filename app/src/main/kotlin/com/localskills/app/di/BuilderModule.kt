package com.localskills.app.di

import com.localskills.app.ui.builder.SkillSink
import com.localskills.app.ui.builder.SkillSinkImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BuilderModule {

    @Binds
    @Singleton
    abstract fun bindSkillSink(impl: SkillSinkImpl): SkillSink
}
