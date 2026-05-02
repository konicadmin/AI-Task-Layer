package com.localskills.app.engine.runtime

import com.localskills.app.skill.manifest.FieldType
import com.localskills.app.skill.manifest.SkillManifest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stand-in extractor used until the real on-device LLM pack is wired up.
 * Returns a JSON object that conforms to the manifest's output_schema with
 * empty values, so the rest of the pipeline (validator, review UI, rules)
 * can be exercised end-to-end without a model on the device.
 */
@Singleton
class EchoRuntime @Inject constructor() : ExtractorRuntime {
    override val id: String = "echo"
    override val isReady: Boolean = true

    override suspend fun extract(
        manifest: SkillManifest,
        input: ExtractorInput,
    ): ExtractorOutput {
        val payload: JsonObject = buildJsonObject {
            manifest.outputSchema.forEach { (name, type) ->
                put(name, defaultFor(type))
            }
        }
        return ExtractorOutput.Json(Json.encodeToString(JsonObject.serializer(), payload))
    }

    private fun defaultFor(type: FieldType): JsonElement = when (type) {
        FieldType.TEXT, FieldType.DATE, FieldType.CURRENCY -> JsonPrimitive("")
        FieldType.NUMBER -> JsonPrimitive(0)
        FieldType.BOOLEAN -> JsonPrimitive(false)
    }
}
