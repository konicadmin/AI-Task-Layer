package com.localskills.app.ui.builder.sections

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.localskills.app.engine.builder.DraftError
import com.localskills.app.engine.builder.DraftSection

@Composable
fun InstructionSection(
    instruction: String,
    errors: List<DraftError>,
    onInstruction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val error = errors.firstOrNull { it.section == DraftSection.INSTRUCTION }
    SectionCard(title = "Instruction to the model", modifier = modifier) {
        OutlinedTextField(
            value = instruction,
            onValueChange = onInstruction,
            label = { Text("What should the skill extract or produce?") },
            isError = error != null,
            supportingText = error?.let { { Text(it.message) } },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Write it like a prompt: be explicit about each output field and its format.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
