package com.localskills.app.ui.runner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.localskills.app.engine.confidence.ConfidenceBand
import com.localskills.app.skill.manifest.FieldType
import com.localskills.app.skill.manifest.SkillManifest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Renders one editable field per entry in the manifest's output_schema and
 * lets the user confirm. The composable is intentionally stateless beyond
 * the in-memory edit buffer — the caller owns the saved JSON.
 */
@Composable
fun ReviewScreen(
    manifest: SkillManifest,
    payloadJson: String,
    confidence: Double,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsed = remember(payloadJson) {
        runCatching { Json.parseToJsonElement(payloadJson) as? JsonObject }.getOrNull()
            ?: JsonObject(emptyMap())
    }
    val edits: SnapshotStateMap<String, String> = remember(payloadJson) {
        mutableStateMapOf<String, String>().apply {
            manifest.outputSchema.keys.forEach { key ->
                put(key, parsed[key]?.jsonPrimitive?.contentOrNull ?: "")
            }
        }
    }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Review", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                ConfidenceChip(confidence)
            }
            Spacer(Modifier.height(12.dp))

            manifest.outputSchema.forEach { (field, type) ->
                FieldEditor(
                    label = field,
                    type = type,
                    value = edits[field].orEmpty(),
                    onChange = { edits[field] = it },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(buildPayload(manifest, edits)) }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun ConfidenceChip(confidence: Double) {
    val band = when {
        confidence >= 0.85 -> ConfidenceBand.HIGH
        confidence >= 0.60 -> ConfidenceBand.MEDIUM
        confidence >= 0.40 -> ConfidenceBand.LOW
        else -> ConfidenceBand.VERY_LOW
    }
    Text(
        "${(confidence * 100).toInt()}% (${band.name.lowercase()})",
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun FieldEditor(
    label: String,
    type: FieldType,
    value: String,
    onChange: (String) -> Unit,
) {
    when (type) {
        FieldType.BOOLEAN -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.weight(1f))
                Switch(
                    checked = value.equals("true", ignoreCase = true),
                    onCheckedChange = { onChange(it.toString()) },
                )
            }
        }
        FieldType.NUMBER, FieldType.CURRENCY -> {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
        }
        FieldType.DATE -> {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text("$label (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        FieldType.TEXT -> {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
            )
        }
    }
}

/**
 * Re-encode edits as JSON honouring each field's declared type. Values
 * that fail to parse for typed fields are persisted as text so the user
 * can correct them later — the validator surfaces the error if they save.
 */
private fun buildPayload(manifest: SkillManifest, edits: Map<String, String>): String {
    val obj = buildJsonObject {
        manifest.outputSchema.forEach { (field, type) ->
            val raw = edits[field].orEmpty()
            put(field, raw.toPrimitiveFor(type))
        }
    }
    return Json.encodeToString(JsonObject.serializer(), obj)
}

private fun String.toPrimitiveFor(type: FieldType): JsonPrimitive = when (type) {
    FieldType.NUMBER -> this.trim().toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(this)
    FieldType.BOOLEAN -> when (this.trim().lowercase()) {
        "true" -> JsonPrimitive(true)
        "false" -> JsonPrimitive(false)
        else -> JsonPrimitive(this)
    }
    FieldType.TEXT, FieldType.DATE, FieldType.CURRENCY -> JsonPrimitive(this)
}

