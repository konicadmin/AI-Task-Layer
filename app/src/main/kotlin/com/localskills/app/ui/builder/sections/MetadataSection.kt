package com.localskills.app.ui.builder.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localskills.app.engine.builder.DraftError
import com.localskills.app.engine.builder.DraftSection

@Composable
fun MetadataSection(
    name: String,
    description: String,
    id: String,
    errors: List<DraftError>,
    onName: (String) -> Unit,
    onDescription: (String) -> Unit,
    onId: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nameError = errors.firstOrNull { it.section == DraftSection.NAME }
    val idError = errors.firstOrNull { it.section == DraftSection.ID }
    SectionCard(title = "Skill details", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                label = { Text("Name") },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it.message) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = description,
                onValueChange = onDescription,
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedTextField(
                value = id,
                onValueChange = onId,
                label = { Text("ID slug (optional)") },
                placeholder = { Text("auto: my-skill.v1") },
                isError = idError != null,
                supportingText = {
                    Text(idError?.message ?: "Lowercase letters, digits, dot, dash, underscore.")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                text = "Skills are saved as data, never as code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
