package com.localskills.app.ui.builder.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localskills.app.engine.builder.DraftError
import com.localskills.app.engine.builder.DraftPattern
import com.localskills.app.engine.builder.DraftSection

@Composable
fun PatternsSection(
    patterns: List<DraftPattern>,
    errors: List<DraftError>,
    onNameChange: (Int, String) -> Unit,
    onRegexChange: (Int, String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sectionErrors = errors.filter { it.section == DraftSection.PATTERNS }
    SectionCard(title = "Patterns (optional)", modifier = modifier) {
        Text(
            text = "Regex hints the runner can use to verify or reinforce extracted values.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            patterns.forEachIndexed { index, p ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = p.name,
                        onValueChange = { onNameChange(index, it) },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = p.regex,
                        onValueChange = { onRegexChange(index, it) },
                        label = { Text("Regex") },
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                    )
                    TextButton(onClick = { onRemove(index) }) { Text("Remove") }
                }
            }
        }
        sectionErrors.forEach {
            Text(
                text = it.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        TextButton(onClick = onAdd) { Text("+ Add pattern") }
    }
}
