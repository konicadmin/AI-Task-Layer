package com.localskills.app.di

import com.localskills.app.engine.ocr.MlKitOcrEngine
import com.localskills.app.engine.ocr.OcrEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bindings introduced for the P1 Skill Runner.
 *
 * EngineModule is intentionally not modified here so P5 retains a single
 * source of truth for runtime selection. New runner-side bindings (OCR
 * impl, etc.) live here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RunnerModule {

    @Binds
    @Singleton
    abstract fun bindOcrEngine(impl: MlKitOcrEngine): OcrEngine
}
