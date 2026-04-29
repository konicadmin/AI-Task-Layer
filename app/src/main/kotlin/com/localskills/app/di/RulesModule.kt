package com.localskills.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.localskills.app.engine.rules.DefaultRuleEngine
import com.localskills.app.engine.rules.RuleEngine
import com.localskills.app.notifications.AndroidNotificationPoster
import com.localskills.app.notifications.RuleNotifier
import com.localskills.app.notifications.ruleNotifierDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bindings for Phase 4: rule engine, notifier, scheduler, evaluator.
 *
 * This module is intentionally separate from [EngineModule] / [DataModule] so
 * P4 changes do not collide with the runner work happening in parallel.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RulesModule {

    @Binds
    @Singleton
    abstract fun bindRuleEngine(impl: DefaultRuleEngine): RuleEngine

    @Binds
    @Singleton
    abstract fun bindNotificationPoster(impl: AndroidNotificationPoster): RuleNotifier.Poster

    companion object {

        @Provides
        @Singleton
        fun provideRuleNotifierDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = context.ruleNotifierDataStore

        @Provides
        @Singleton
        fun provideClock(): java.time.Clock = java.time.Clock.systemDefaultZone()
    }
}
