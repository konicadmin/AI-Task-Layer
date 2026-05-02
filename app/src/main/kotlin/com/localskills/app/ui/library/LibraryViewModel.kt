package com.localskills.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localskills.app.data.repo.InstalledSkill
import com.localskills.app.data.repo.SkillRepository
import com.localskills.app.engine.runner.SkillSeeder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val skillRepository: SkillRepository,
    private val skillSeeder: SkillSeeder,
) : ViewModel() {

    val skills: StateFlow<List<InstalledSkill>> = skillRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Idempotent — installs seed manifests on first launch and refreshes
        // them on app upgrade unless the user has edited a seed skill.
        viewModelScope.launch { skillSeeder.seedIfNeeded() }
    }

    fun toggleEnabled(skill: InstalledSkill, enabled: Boolean) {
        viewModelScope.launch { skillRepository.setEnabled(skill.id, enabled) }
    }

    fun delete(skill: InstalledSkill) {
        viewModelScope.launch { skillRepository.delete(skill.id) }
    }
}
