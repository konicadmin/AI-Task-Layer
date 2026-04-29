package com.localskills.app.ui.builder.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.localskills.app.engine.builder.DraftError
import com.localskills.app.engine.builder.DraftLimits
import com.localskills.app.engine.builder.DraftSection
import com.localskills.app.ui.builder.LimitField

@Composable
fun LimitsSection(
    limits: DraftLimits,
    errors: List<DraftError>,
    onUpdate: (LimitField, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sectionErrors = errors.filter { it.section == DraftSection.LIMITS }
    SectionCard(title = "Resource limits", modifier = modifier) {
        Text(
            text = "Hard caps the runner enforces. Defaults are sized for a 1B model on a mid-tier device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NumberField(
                    label = "max_input_chars",
                    value = limits.maxInputChars,
                    onChange = { onUpdate(LimitField.MAX_INPUT_CHARS, it) },
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = "max_ocr_pages",
                    value = limits.maxOcrPages,
                    onChange = { onUpdate(LimitField.MAX_OCR_PAGES, it) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NumberField(
                    label = "max_model_tokens",
                    value = limits.maxModelTokens,
                    onChange = { onUpdate(LimitField.MAX_MODEL_TOKENS, it) },
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = "max_runtime_ms",
                    value = limits.maxRuntimeMs,
                    onChange = { onUpdate(LimitField.MAX_RUNTIME_MS, it) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        sectionErrors.forEach {
            Text(
                text = it.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
