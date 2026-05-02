package com.localskills.app.di

import com.localskills.app.engine.runtime.EchoRuntime
import com.localskills.app.engine.runtime.ExtractorRuntime
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    /**
     * The default extractor binding is [EchoRuntime] until the on-device LLM
     * pack is wired up. Replacing this binding swaps the model with no
     * downstream changes.
     */
    @Binds
    @Singleton
    abstract fun bindExtractorRuntime(impl: EchoRuntime): ExtractorRuntime
}
