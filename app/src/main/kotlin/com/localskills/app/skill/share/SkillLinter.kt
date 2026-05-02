package com.localskills.app.skill.share

import com.localskills.app.skill.manifest.FieldType
import com.localskills.app.skill.manifest.InputKind
import com.localskills.app.skill.manifest.ManifestCodec
import com.localskills.app.skill.manifest.RuleAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton
import java.util.regex.Pattern as JPattern
import java.util.regex.PatternSyntaxException

/**
 * Static lint over a raw manifest payload. Runs BEFORE [ManifestCodec.decode]
 * so we can reject anything that smells of executable content before it ever
 * reaches the strongly-typed parser.
 *
 * Skills are data, not code: the lint enforces an explicit allowlist of
 * top-level keys, enum values, and key/value shapes that the runtime knows
 * how to consume.
 */
@Singleton
class SkillLinter @Inject constructor() {

    fun lint(raw: String): SafetyReport {
        val warnings = mutableListOf<String>()
        val reasons = mutableListOf<String>()

        if (raw.length > MAX_PAYLOAD_BYTES) {
            return SafetyReport.Reject(
                reasons = listOf("payload exceeds ${MAX_PAYLOAD_BYTES / 1024}KB cap"),
            )
        }

        val payload = raw.trim().removePrefix(ManifestCodec.SHARE_HEADER).trim()
        if (payload.isEmpty()) {
            return SafetyReport.Reject(reasons = listOf("empty manifest payload"))
        }

        val root = runCatching { Json.parseToJsonElement(payload) }
            .getOrElse { cause ->
                return SafetyReport.Reject(
                    reasons = listOf("not valid JSON: ${cause.message ?: "parse error"}"),
                )
            }

        if (root !is JsonObject) {
            return SafetyReport.Reject(reasons = listOf("manifest root must be a JSON object"))
        }

        // 1. Allowlist top-level keys.
        val unknownTop = root.keys - ALLOWED_TOP_LEVEL_KEYS
        if (unknownTop.isNotEmpty()) {
            reasons += "unknown top-level keys: ${unknownTop.sorted().joinToString(", ")}"
        }

        // 2. Enum allowlists.
        (root["inputs"] as? JsonArray)?.forEachIndexed { idx, el ->
            val v = (el as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (v == null || v !in ALLOWED_INPUT_KINDS) {
                reasons += "inputs[$idx] is not an allowed InputKind"
            }
        }
        (root["output_schema"] as? JsonObject)?.forEach { (key, el) ->
            val v = (el as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (v == null || v !in ALLOWED_FIELD_TYPES) {
                reasons += "output_schema.$key has unknown FieldType '${(el as? JsonPrimitive)?.content}'"
            }
        }
        (root["rules"] as? JsonArray)?.forEachIndexed { idx, ruleEl ->
            val rule = ruleEl as? JsonObject ?: return@forEachIndexed
            val action = (rule["action"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (action != null && action !in ALLOWED_RULE_ACTIONS) {
                reasons += "rules[$idx].action '$action' is not an allowed RuleAction"
            }
        }

        // 3. Recursive walk for forbidden keys, suspicious tokens, and
        //    base64-shaped blobs.
        walk(root, path = "", reasons = reasons, warnings = warnings)

        // 4. Regex compile + obvious catastrophic-backtracking shapes.
        (root["patterns"] as? JsonArray)?.forEachIndexed { idx, patternEl ->
            val obj = patternEl as? JsonObject ?: return@forEachIndexed
            val regex = (obj["regex"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: return@forEachIndexed
            try {
                JPattern.compile(regex)
            } catch (e: PatternSyntaxException) {
                reasons += "patterns[$idx].regex does not compile: ${e.description ?: e.message}"
            }
            if (CATASTROPHIC_BACKTRACK.containsMatchIn(regex)) {
                warnings += "patterns[$idx].regex has nested-quantifier shape (possible ReDoS)"
            }
        }

        return when {
            reasons.isNotEmpty() -> SafetyReport.Reject(reasons = reasons.distinct())
            warnings.isNotEmpty() -> SafetyReport.Warnings(warnings = warnings.distinct())
            else -> SafetyReport.Safe()
        }
    }

    private fun walk(
        element: JsonElement,
        path: String,
        reasons: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                val lower = key.lowercase()
                if (lower in FORBIDDEN_KEYS) {
                    reasons += "forbidden key '$key' at $path"
                }
                walk(value, if (path.isEmpty()) key else "$path.$key", reasons, warnings)
            }
            is JsonArray -> element.forEachIndexed { idx, child ->
                walk(child, "$path[$idx]", reasons, warnings)
            }
            is JsonPrimitive -> if (element.isString) inspectString(element.content, path, reasons, warnings)
            else -> Unit
        }
    }

    private fun inspectString(
        value: String,
        path: String,
        reasons: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val lower = value.lowercase()
        SUSPICIOUS_TOKENS.forEach { token ->
            if (lower.contains(token)) {
                reasons += "suspicious token '$token' in $path"
            }
        }
        if (URL_PATTERN.containsMatchIn(value)) {
            reasons += "URL not allowed in manifest text at $path"
        }
        if (looksLikeBase64Blob(value)) {
            reasons += "large base64-shaped blob at $path"
        }
    }

    private fun looksLikeBase64Blob(value: String): Boolean {
        if (value.length <= BASE64_BLOB_THRESHOLD) return false
        val stripped = value.trim()
        if (stripped.length <= BASE64_BLOB_THRESHOLD) return false
        return BASE64_LIKE.matches(stripped)
    }

    companion object {
        const val MAX_PAYLOAD_BYTES: Int = 64 * 1024
        const val BASE64_BLOB_THRESHOLD: Int = 1024

        val ALLOWED_TOP_LEVEL_KEYS: Set<String> = setOf(
            "manifest_version",
            "id",
            "name",
            "description",
            "inputs",
            "instruction",
            "output_schema",
            "patterns",
            "validators",
            "rules",
            "limits",
        )

        val ALLOWED_INPUT_KINDS: Set<String> = InputKind.values().map { it.serialName() }.toSet()
        val ALLOWED_FIELD_TYPES: Set<String> = FieldType.values().map { it.serialName() }.toSet()
        val ALLOWED_RULE_ACTIONS: Set<String> = RuleAction.values().map { it.serialName() }.toSet()

        val FORBIDDEN_KEYS: Set<String> = setOf(
            "script", "code", "command", "eval", "exec", "binary",
        )

        val SUSPICIOUS_TOKENS: List<String> = listOf(
            "eval(", "exec(", "runtime.exec", "loadclass", "dlopen",
        )

        // Strict URL guard: reject any http/https occurrence in manifest text.
        private val URL_PATTERN: Regex = Regex("""https?://""", RegexOption.IGNORE_CASE)

        // Conservative base64 shape: long, only base64 alphabet, no spaces.
        private val BASE64_LIKE: Regex = Regex("""^[A-Za-z0-9+/=\r\n]+$""")

        // Conservative ReDoS sniff — matches obvious "(.+)+", "(.*)+", "(\w+)+"
        // style nested-quantifier groups. We only warn; we don't reject.
        private val CATASTROPHIC_BACKTRACK: Regex =
            Regex("""\([^)]*[+*][^)]*\)[+*]""")

        private fun InputKind.serialName(): String = when (this) {
            InputKind.TEXT -> "text"
            InputKind.IMAGE -> "image"
            InputKind.FILE -> "file"
        }

        private fun FieldType.serialName(): String = when (this) {
            FieldType.TEXT -> "text"
            FieldType.NUMBER -> "number"
            FieldType.DATE -> "date"
            FieldType.BOOLEAN -> "boolean"
            FieldType.CURRENCY -> "currency"
        }

        private fun RuleAction.serialName(): String = when (this) {
            RuleAction.NOTIFY -> "notify"
            RuleAction.TAG -> "tag"
            RuleAction.SAVE_DRAFT -> "save_draft"
        }
    }
}
