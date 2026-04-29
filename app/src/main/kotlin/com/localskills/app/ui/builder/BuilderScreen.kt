// P5-WIRING: this is the top-level entry composable for the Skill Builder.
// To navigate here from the app shell, add a destination to the Compose nav
// graph in MainActivity (or its NavHost) that routes "builder" to a call of
// BuilderScreen(onClose = navController::popBackStack). MainActivity must
// not be edited in P2 — P5 will integrate the route along with the rest of
// the navigation surface.
package com.localskills.app.ui.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localskills.app.ui.builder.sections.InputsSection
import com.localskills.app.ui.builder.sections.InstructionSection
import com.localskills.app.ui.builder.sections.JsonPreviewSheet
import com.localskills.app.ui.builder.sections.LimitsSection
import com.localskills.app.ui.builder.sections.MetadataSection
import com.localskills.app.ui.builder.sections.OutputFieldsSection
import com.localskills.app.ui.builder.sections.PatternsSection
import com.localskills.app.ui.builder.sections.RulesSection
import com.localskills.app.ui.builder.sections.TemplatesSection
import com.localskills.app.ui.builder.sections.TestHarnessSheet

/**
 * Form-driven Skill Builder. Hosts every section composable, surfaces live
 * validation errors, and exposes the test harness + JSON preview as bottom
 * sheets. The save button is enabled only when [BuilderUiState.errors] is
 * empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuilderScreen(
    onClose: () -> Unit = {},
    viewModel: BuilderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showJson by remember { mutableStateOf(false) }
    var showHarness by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(state.saveResult) {
        when (val r = state.saveResult) {
            is SaveResult.Saved -> {
                snackbarHost.showSnackbar("Saved skill ${r.id}")
                viewModel.acknowledgeSave()
            }
            is SaveResult.Failed -> {
                snackbarHost.showSnackbar("Save failed: ${r.reason}")
                viewModel.acknowledgeSave()
            }
            null -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New skill") },
                navigationIcon = {
                    TextButton(onClick = onClose) { Text("Close") }
                },
                actions = {
                    TextButton(onClick = { showJson = true }) { Text("JSON") }
                    TextButton(onClick = { showHarness = true }) { Text("Test") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TemplatesSection(
                templates = viewModel.templates,
                onPick = viewModel::loadTemplate,
            )
            MetadataSection(
                name = state.draft.name,
                description = state.draft.description,
                id = state.draft.id,
                errors = state.errors,
                onName = viewModel::updateName,
                onDescription = viewModel::updateDescription,
                onId = viewModel::updateId,
            )
            InputsSection(
                selected = state.draft.inputs,
                errors = state.errors,
                onToggle = viewModel::toggleInput,
            )
            InstructionSection(
                instruction = state.draft.instruction,
                errors = state.errors,
                onInstruction = viewModel::updateInstruction,
            )
            OutputFieldsSection(
                fields = state.draft.outputFields,
                errors = state.errors,
                onNameChange = viewModel::updateOutputFieldName,
                onTypeChange = viewModel::updateOutputFieldType,
                onAdd = viewModel::addOutputField,
                onRemove = viewModel::removeOutputField,
            )
            PatternsSection(
                patterns = state.draft.patterns,
                errors = state.errors,
                onNameChange = viewModel::updatePatternName,
                onRegexChange = viewModel::updatePatternRegex,
                onAdd = viewModel::addPattern,
                onRemove = viewModel::removePattern,
            )
            RulesSection(
                rules = state.draft.rules,
                errors = state.errors,
                onConditionChange = viewModel::updateRuleCondition,
                onActionChange = viewModel::updateRuleAction,
                onTitleChange = viewModel::updateRuleTitle,
                onBodyChange = viewModel::updateRuleBody,
                onAdd = viewModel::addRule,
                onRemove = viewModel::removeRule,
            )
            LimitsSection(
                limits = state.draft.limits,
                errors = state.errors,
                onUpdate = viewModel::updateLimit,
            )
            if (state.errors.isNotEmpty()) {
                Text(
                    text = "Resolve ${state.errors.size} validation issue(s) before saving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showHarness = true }) { Text("Test on sample") }
                OutlinedButton(onClick = { showJson = true }) { Text("Preview JSON") }
                Button(
                    onClick = viewModel::save,
                    enabled = state.errors.isEmpty() && !state.saving,
                ) {
                    Text(if (state.saving) "Saving..." else "Save skill")
                }
            }
        }
    }

    if (showJson) {
        JsonPreviewSheet(
            json = viewModel.jsonPreview(),
            onDismiss = { showJson = false },
        )
    }
    if (showHarness) {
        TestHarnessSheet(
            sampleInput = state.sampleInput,
            report = state.testReport,
            running = state.testRunning,
            onSampleChange = viewModel::updateSampleInput,
            onRun = viewModel::runTest,
            onDismiss = { showHarness = false },
        )
    }
}
