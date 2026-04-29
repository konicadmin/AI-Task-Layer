package com.localskills.app.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Compose screen that previews the `AOSL-SKILL/1` share text and lets
 * the user kick off either a text share or a file share.
 *
 * The actual "load manifest by skill id" step is left to P5 — for now
 * the host hands the ViewModel a manifest via [ExportSkillViewModel.bind].
 */
@Composable
fun ExportSkillScreen(
    modifier: Modifier = Modifier,
    viewModel: ExportSkillViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = state.manifest?.name?.let { "Share \"$it\"" } ?: "Share skill",
            style = MaterialTheme.typography.headlineSmall,
        )
        state.manifest?.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "Share text preview", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.shareText.ifEmpty { "(no manifest bound)" },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Button(
            onClick = {
                viewModel.buildTextShareIntent()?.let { context.startActivity(it) }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.manifest != null,
        ) {
            Text(text = "Share as text")
        }

        OutlinedButton(
            onClick = {
                viewModel.buildFileShareIntent(context)?.let { context.startActivity(it) }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.manifest != null,
        ) {
            Text(text = "Share as .skill.json")
        }
    }
}
