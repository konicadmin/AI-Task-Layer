package com.localskills.app.ui.runner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.localskills.app.engine.runner.RunOutcome
import com.localskills.app.skill.manifest.InputKind

/**
 * Skill input form + run trigger. On a successful run hands off to
 * [ReviewScreen] inline.
 *
 * P5-WIRING: register this composable on the host NavHost as e.g.
 * `runner/{skillId}`. The library screen's "Run" CTA should navigate
 * here. ShareReceiverActivity (already in the manifest) should also
 * route into this screen once the share flow lands in P3 — the
 * `// TODO(P1): hand off to skill runner / picker` comment in
 * ShareReceiverActivity is the integration point.
 */
@Composable
fun RunSkillScreen(
    skillId: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RunSkillViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(skillId) { viewModel.load(skillId) }

    val skill = state.skill
    if (skill == null) {
        Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
            Text("Loading skill…", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val outcome = state.outcome
    if (outcome is RunOutcome.Success && state.saved) {
        LaunchedEffect(outcome.result.id) { onDone() }
    }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri -> viewModel.updateImageUri(uri) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(skill.manifest.name, style = MaterialTheme.typography.headlineSmall)
        if (skill.manifest.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(skill.manifest.description, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))

        if (InputKind.TEXT in skill.manifest.inputs) {
            OutlinedTextField(
                value = state.inputText,
                onValueChange = viewModel::updateText,
                label = { Text("Text input") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
            Spacer(Modifier.height(12.dp))
        }
        if (InputKind.IMAGE in skill.manifest.inputs) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickImage.launch("image/*") }) {
                    Text(if (state.imageUri == null) "Pick image" else "Change image")
                }
                Spacer(Modifier.width(8.dp).height(1.dp))
                state.imageUri?.let {
                    Text(it.lastPathSegment ?: "image", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::run, enabled = !state.running) {
                if (state.running) CircularProgressIndicator(modifier = Modifier.height(16.dp)) else Text("Run")
            }
            TextButton(onClick = viewModel::reset, enabled = !state.running) { Text("Clear") }
        }

        Spacer(Modifier.height(20.dp))

        when (outcome) {
            is RunOutcome.Success -> ReviewScreen(
                manifest = skill.manifest,
                payloadJson = outcome.result.payloadJson,
                confidence = outcome.confidence,
                onSave = viewModel::savePayload,
            )
            is RunOutcome.Failure -> ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Run failed", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(outcome.message, style = MaterialTheme.typography.bodySmall)
                }
            }
            is RunOutcome.SkillNotFound -> Text("Skill not found.")
            null -> {}
        }
    }
}

