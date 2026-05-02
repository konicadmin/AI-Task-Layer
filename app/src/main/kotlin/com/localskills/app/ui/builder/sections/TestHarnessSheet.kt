package com.localskills.app.ui.builder.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.localskills.app.engine.builder.TestReport

/**
 * Test harness UI: type sample input, run the configured [com.localskills.app.engine.runtime.ExtractorRuntime]
 * via [com.localskills.app.engine.builder.SkillTestHarness], display the
 * raw JSON, per-field values, and validator errors.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestHarnessSheet(
    sampleInput: String,
    report: TestReport?,
    running: Boolean,
    onSampleChange: (String) -> Unit,
    onRun: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Test on sample input", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = sampleInput,
                onValueChange = onSampleChange,
                label = { Text("Sample text") },
                placeholder = { Text("Paste an SMS, receipt OCR, or note here") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onRun, enabled = !running) {
                    Text(if (running) "Running..." else "Run extractor")
                }
                if (running) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .padding(start = 12.dp),
                    )
                }
            }
            HorizontalDivider()
            ReportView(report)
        }
    }
}

@Composable
private fun ReportView(report: TestReport?) {
    when (report) {
        null -> Text(
            text = "Run the extractor to see what the model would return.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is TestReport.DraftInvalid -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Draft is incomplete:",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            report.errors.forEach { e ->
                Text(
                    text = "• ${e.section}: ${e.message}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        is TestReport.RuntimeFailed -> Text(
            text = "Runtime failure: ${report.reason}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        is TestReport.Success -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (report.valid) "Output validates" else "Output failed validation",
                color = if (report.valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Pass rate: ${"%.0f".format(report.passRate * 100)}%",
                style = MaterialTheme.typography.bodySmall,
            )
            if (report.fieldValues.isNotEmpty()) {
                Text("Per-field values:", style = MaterialTheme.typography.titleSmall)
                report.fieldValues.forEach { (k, v) ->
                    Text(text = "  $k = $v", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (report.validationErrors.isNotEmpty()) {
                Text("Validation errors:", style = MaterialTheme.typography.titleSmall)
                report.validationErrors.forEach { e ->
                    Text(
                        text = "• ${e.field}: ${e.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text("Raw JSON:", style = MaterialTheme.typography.titleSmall)
            Text(
                text = report.rawJson,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}
