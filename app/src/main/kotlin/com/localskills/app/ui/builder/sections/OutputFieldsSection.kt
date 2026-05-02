package com.localskills.app.ui.builder.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localskills.app.engine.builder.DraftError
import com.localskills.app.engine.builder.DraftField
import com.localskills.app.engine.builder.DraftSection
import com.localskills.app.skill.manifest.FieldType

@Composable
fun OutputFieldsSection(
    fields: List<DraftField>,
    errors: List<DraftError>,
    onNameChange: (Int, String) -> Unit,
    onTypeChange: (Int, FieldType) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sectionErrors = errors.filter { it.section == DraftSection.OUTPUT_FIELDS }
    SectionCard(title = "Output fields", modifier = modifier) {
        if (fields.isEmpty()) {
            Text(
                text = "Each field becomes a typed slot in the JSON the runner returns.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fields.forEachIndexed { index, field ->
                FieldRow(
                    index = index,
                    field = field,
                    onName = { onNameChange(index, it) },
                    onType = { onTypeChange(index, it) },
                    onRemove = { onRemove(index) },
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
        TextButton(onClick = onAdd) { Text("+ Add field") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldRow(
    index: Int,
    field: DraftField,
    onName: (String) -> Unit,
    onType: (FieldType) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = field.name,
            onValueChange = onName,
            label = { Text("Field #${index + 1}") },
            placeholder = { Text("snake_case") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Box(modifier = Modifier.width(140.dp)) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = field.type.label(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor(),
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    FieldType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label()) },
                            onClick = {
                                onType(type)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}

private fun FieldType.label(): String = when (this) {
    FieldType.TEXT -> "text"
    FieldType.NUMBER -> "number"
    FieldType.DATE -> "date"
    FieldType.BOOLEAN -> "boolean"
    FieldType.CURRENCY -> "currency"
}
