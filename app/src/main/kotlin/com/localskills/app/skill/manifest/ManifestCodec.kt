package com.localskills.app.skill.manifest

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serializes manifests to/from the share-friendly text format.
 *
 * The wire format is a single header line followed by JSON:
 *
 *     AOSL-SKILL/1
 *     { ... }
 *
 * The header lets share-sheet recipients route a pasted blob to the
 * importer without sniffing JSON. Plain `.skill.json` (no header) is
 * also accepted for file-based imports.
 */
object ManifestCodec {
    const val SHARE_HEADER: String = "AOSL-SKILL/1"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun encodeJson(manifest: SkillManifest): String = json.encodeToString(manifest)

    fun encodeShareText(manifest: SkillManifest): String =
        "$SHARE_HEADER\n${json.encodeToString(manifest)}"

    fun decode(raw: String): Result<SkillManifest> = runCatching {
        val payload = raw.trim().removePrefix(SHARE_HEADER).trim()
        if (payload.isEmpty()) error("empty manifest payload")
        json.decodeFromString(SkillManifest.serializer(), payload)
    }.recoverCatching { cause ->
        if (cause is SerializationException) {
            throw IllegalArgumentException("Invalid skill manifest: ${cause.message}", cause)
        }
        throw cause
    }
}
