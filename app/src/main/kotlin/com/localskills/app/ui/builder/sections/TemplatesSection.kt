package com.localskills.app.ui.builder.sections

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localskills.app.ui.builder.templates.BuilderTemplate

@Composable
fun TemplatesSection(
    templates: List<BuilderTemplate>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = "Start from a template", modifier = modifier) {
        Text(
            text = "Templates seed the form with sensible defaults. You can edit anything afterwards.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            templates.forEach { t ->
                OutlinedCard(modifier = Modifier.width(220.dp)) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(t.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            t.tagline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { onPick(t.key) }) { Text("Use template") }
                    }
                }
            }
        }
    }
}
