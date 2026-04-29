package com.localskills.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.localskills.app.data.repo.InstalledSkill
import com.localskills.app.skill.manifest.InputKind
import com.localskills.app.ui.share.PendingShareHolder

/**
 * Shows the user's installed skills with quick enable/delete/run/share actions.
 * The "New skill" CTA opens the builder.
 */
@Composable
fun LibraryScreen(
    onRun: (skillId: String) -> Unit,
    onBuild: () -> Unit = {},
    onShare: (skillId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val skills by viewModel.skills.collectAsState()
    val pendingKind = remember { mutableStateOf(PendingShareHolder.peek()?.kind) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Skill Library", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Skills are data, not code. Install, share, or build your own.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = onBuild) { Text("New skill") }
        }
        pendingKind.value?.let { kind ->
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Shared ${kind.name.lowercase()} ready — tap a skill below to run it.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        if (skills.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No skills installed yet.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(skills, key = { it.id }) { skill ->
                    val acceptsPending = pendingKind.value
                        ?.let { it in skill.manifest.inputs } ?: false
                    SkillRow(
                        skill = skill,
                        highlighted = acceptsPending,
                        onToggle = { viewModel.toggleEnabled(skill, it) },
                        onDelete = { viewModel.delete(skill) },
                        onRun = { onRun(skill.id) },
                        onShare = { onShare(skill.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillRow(
    skill: InstalledSkill,
    highlighted: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onRun: () -> Unit,
    onShare: () -> Unit,
) {
    val cardColors = if (highlighted) {
        androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        )
    } else {
        androidx.compose.material3.CardDefaults.elevatedCardColors()
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(skill.manifest.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        skill.manifest.id,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = skill.enabled, onCheckedChange = onToggle)
            }
            if (skill.manifest.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(skill.manifest.description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Source: ${skill.source.name.lowercase()} · " +
                    "Inputs: ${skill.manifest.inputs.joinToString { it.name.lowercase() }}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRun, enabled = skill.enabled) { Text("Run") }
                OutlinedButton(onClick = onShare) { Text("Share") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
