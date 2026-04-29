package com.localskills.app.ui

import com.localskills.app.data.repo.SkillRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Lets non-Hilt Composables (e.g. nav-graph route builders) reach the
 * [SkillRepository] through the application graph instead of relying on
 * a ViewModel that owns it. ViewModels remain the primary way to access
 * the repository — this is for places that need a one-shot read tied to
 * a route argument.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SkillRepositoryEntryPoint {
    fun skillRepository(): SkillRepository
}
