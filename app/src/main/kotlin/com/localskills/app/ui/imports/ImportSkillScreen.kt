package com.localskills.app.ui.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.localskills.app.skill.manifest.SkillManifest
import com.localskills.app.skill.share.ImportResult
import com.localskills.app.skill.share.SafetyReport

/**
 * Review-before-install screen. Renders:
 *  - manifest summary (name, description, inputs, outputs, rules)
 *  - safety report (warnings or rejection reasons)
 *  - sandbox dry-run notes
 *  - [Cancel] / [Install Disabled] actions
 *
 * Imported skills always land disabled, so the install button label
 * makes that promise explicit.
 */
@Composable
fun ImportSkillScreen(
    onCancel: () -> Unit,
    onInstalled: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportSkillViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // When install completes successfully, hand off to the host.
    val terminal = state.terminal
    LaunchedEffect(terminal) {
        if (terminal is ImportResult.Installed) {
            onInstalled(terminal.id)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Review imported skill",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Imported skills always install as disabled. Review the details below before turning it on.",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (state.loading) {
            Text(text = "Linting and dry-running…")
            return@Column
        }

        ManifestSummary(manifest = state.manifest)
        SafetyBlock(safety = state.safety)
        SandboxBlock(issues = state.sandbox?.issues.orEmpty(), ok = state.sandbox?.ok ?: true)
        TerminalBlock(terminal = state.terminal)

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            ) { Text("Cancel") }

            Button(
                onClick = { viewModel.confirmInstall() },
                modifier = Modifier.weight(1f),
                enabled = state.canInstall,
            ) { Text("Install Disabled") }
        }
    }
}

@Composable
private fun ManifestSummary(manifest: SkillManifest?) {
    if (manifest == null) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = manifest.name, style = MaterialTheme.typography.titleMedium)
            Text(text = "id: ${manifest.id}", style = MaterialTheme.typography.bodySmall)
            if (manifest.description.isNotBlank()) {
                Text(text = manifest.description, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = "Inputs: ${manifest.inputs.joinToString { it.name.lowercase() }}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Outputs:",
                style = MaterialTheme.typography.bodySmall,
            )
            manifest.outputSchema.forEach { (name, type) ->
                Text(
                    text = "  • $name : ${type.name.lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (manifest.rules.isNotEmpty()) {
                Text(text = "Rules:", style = MaterialTheme.typography.bodySmall)
                manifest.rules.forEach { rule ->
                    Text(
                        text = "  • when ${rule.condition} → ${rule.action.name.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetyBlock(safety: SafetyReport?) {
    if (safety == null) return
    val (title, lines) = when (safety) {
        is SafetyReport.Safe -> "Safety: clean" to emptyList()
        is SafetyReport.Warnings -> "Safety: warnings" to safety.warnings
        is SafetyReport.Reject -> "Safety: rejected" to safety.reasons
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            lines.forEach { line ->
                Text(text = "  • $line", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SandboxBlock(issues: List<String>, ok: Boolean) {
    if (issues.isEmpty() && ok) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (ok) "Sandbox: clean" else "Sandbox: issues",
                style = MaterialTheme.typography.titleSmall,
            )
            issues.forEach { line ->
                Text(text = "  • $line", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TerminalBlock(terminal: ImportResult?) {
    if (terminal == null) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            when (terminal) {
                is ImportResult.Installed -> Text(
                    text = "Installed (disabled): ${terminal.id}",
                    style = MaterialTheme.typography.titleSmall,
                )
                is ImportResult.AlreadyInstalled -> Text(
                    text = "A skill with id '${terminal.id}' is already installed.",
                    style = MaterialTheme.typography.titleSmall,
                )
                is ImportResult.Rejected -> {
                    Text(
                        text = "Install rejected",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    terminal.reasons.forEach { line ->
                        Text(text = "  • $line", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
