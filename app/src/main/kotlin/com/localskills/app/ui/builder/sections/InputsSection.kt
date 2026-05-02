package com.localskills.app.ui.builder.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localskills.app.engine.builder.DraftError
import com.localskills.app.engine.builder.DraftSection
import com.localskills.app.skill.manifest.InputKind

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InputsSection(
    selected: Set<InputKind>,
    errors: List<DraftError>,
    onToggle: (InputKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val error = errors.firstOrNull { it.section == DraftSection.INPUTS }
    SectionCard(title = "Input types", modifier = modifier) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            InputKind.entries.forEach { kind ->
                FilterChip(
                    selected = kind in selected,
                    onClick = { onToggle(kind) },
                    label = { Text(kind.label()) },
                )
            }
        }
        if (error != null) {
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Text(
                text = "Multiple inputs are allowed; the runner picks the most specific available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun InputKind.label(): String = when (this) {
    InputKind.TEXT -> "Text"
    InputKind.IMAGE -> "Image"
    InputKind.FILE -> "File"
}
