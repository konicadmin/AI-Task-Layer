package com.localskills.app.engine.rules

/**
 * Declarative rule engine contract. Rule expressions are parsed strings;
 * they are NEVER eval'd as code. The default implementation will support
 * a small grammar covering date relations, comparisons, and aggregates.
 */
interface RuleEngine {
    fun evaluate(expression: String, context: RuleContext): Boolean
}

/**
 * Bag of values exposed to a rule expression. Field access goes through
 * `fields` so the engine can keep an allowlist of safe identifiers.
 */
data class RuleContext(
    val fields: Map<String, Any?>,
    val nowEpochMillis: Long,
    val timezone: String = "Asia/Kolkata",
)
