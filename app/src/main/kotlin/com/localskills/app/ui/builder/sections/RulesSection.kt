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
import com.localskills.app.engine.builder.DraftRule
import com.localskills.app.engine.builder.DraftSection
import com.localskills.app.skill.manifest.RuleAction

@Composable
fun RulesSection(
    rules: List<DraftRule>,
    errors: List<DraftError>,
    onConditionChange: (Int, String) -> Unit,
    onActionChange: (Int, RuleAction) -> Unit,
    onTitleChange: (Int, String) -> Unit,
    onBodyChange: (Int, String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sectionErrors = errors.filter { it.section == DraftSection.RULES }
    SectionCard(title = "Rules (optional)", modifier = modifier) {
        Text(
            text = "Conditions over output fields. Authoring DSL is finalised in P4 — keep notes here for now.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rules.forEachIndexed { index, rule ->
                RuleRow(
                    index = index,
                    rule = rule,
                    onCondition = { onConditionChange(index, it) },
                    onAction = { onActionChange(index, it) },
                    onTitle = { onTitleChange(index, it) },
                    onBody = { onBodyChange(index, it) },
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
        TextButton(onClick = onAdd) { Text("+ Add rule") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleRow(
    index: Int,
    rule: DraftRule,
    onCondition: (String) -> Unit,
    onAction: (RuleAction) -> Unit,
    onTitle: (String) -> Unit,
    onBody: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Rule #${index + 1}",
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = rule.condition,
                onValueChange = onCondition,
                label = { Text("Condition") },
                placeholder = { Text("expiry_date in 3 days") },
                modifier = Modifier.weight(1f),
            )
            Box(modifier = Modifier.width(160.dp)) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = rule.action.label(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Action") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(),
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        RuleAction.entries.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.label()) },
                                onClick = {
                                    onAction(action)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            value = rule.title,
            onValueChange = onTitle,
            label = { Text("Notification title (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = rule.body,
            onValueChange = onBody,
            label = { Text("Notification body (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onRemove) { Text("Remove rule") }
    }
}

private fun RuleAction.label(): String = when (this) {
    RuleAction.NOTIFY -> "notify"
    RuleAction.TAG -> "tag"
    RuleAction.SAVE_DRAFT -> "save_draft"
}
