package com.localskills.app.engine.rules

import com.localskills.app.data.db.dao.ResultDao
import com.localskills.app.data.db.dao.RuleDao
import com.localskills.app.data.db.entity.ResultEntity
import com.localskills.app.data.db.entity.RuleEntity
import com.localskills.app.notifications.RuleNotifier
import com.localskills.app.skill.manifest.RuleAction
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges Room rows into [RuleContext] values and dispatches matched rules to
 * the notifier. Used by the worker — kept off the UI thread, no Android types.
 */
@Singleton
class RuleEvaluator @Inject constructor(
    private val ruleDao: RuleDao,
    private val resultDao: ResultDao,
    private val engine: RuleEngine,
    private val notifier: RuleNotifier,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    /**
     * Evaluate every enabled rule once. For each match, dispatch its action.
     *
     * Per-rule context:
     *  - `entity.*` and the top-level fields come from the rule's bound result
     *    (or the most recent result for the skill if `resultId` is null).
     *  - `results` is the list of all results for the same skill, each as a
     *    flat field map — feeds `sum(... where ...)` aggregates.
     *
     * @return number of rules that fired (post-dedupe, post-rate-limit).
     */
    suspend fun sweep(): Int {
        val rules = ruleDao.observeEnabled().first()
        var fired = 0
        for (rule in rules) {
            val context = buildContext(rule) ?: continue
            val matched = try {
                engine.evaluate(rule.expression, context)
            } catch (_: RuleParseException) {
                false
            } catch (_: RuleEvalException) {
                false
            }
            if (!matched) continue
            if (dispatch(rule)) {
                fired++
                ruleDao.markFired(rule.id, Instant.now(clock))
            }
        }
        return fired
    }

    private suspend fun dispatch(rule: RuleEntity): Boolean {
        return when (parseAction(rule.action)) {
            RuleAction.NOTIFY -> notifier.dispatch(rule)
            // Other actions are handled by P5+ wiring; treat as fired no-op
            // so we still record `lastFiredAt` and don't re-evaluate noisily.
            RuleAction.TAG, RuleAction.SAVE_DRAFT, null -> true
        }
    }

    private suspend fun buildContext(rule: RuleEntity): RuleContext? {
        val results = resultDao.observeForSkill(rule.skillId).first()
        if (results.isEmpty()) return null
        val primary = rule.resultId?.let { id -> results.firstOrNull { it.id == id } } ?: results.first()
        val primaryFields = decode(primary)
        val rowsForAggregate = results.map { decode(it) }

        val fields = HashMap<String, Any?>(primaryFields.size + 2)
        fields.putAll(primaryFields)
        fields["entity"] = primaryFields
        fields[RuleInterpreter.ITER_NAME] = rowsForAggregate
        return RuleContext(
            fields = fields,
            nowEpochMillis = clock.millis(),
        )
    }

    private fun decode(result: ResultEntity): Map<String, Any?> {
        val element = runCatching { json.parseToJsonElement(result.payloadJson) }.getOrNull() ?: return emptyMap()
        if (element !is JsonObject) return emptyMap()
        return element.toMap()
    }

    private fun JsonObject.toMap(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>(size)
        for ((k, v) in this) out[k] = v.unwrap()
        return out
    }

    private fun JsonElement.unwrap(): Any? = when (this) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            isString -> content
            else -> booleanOrNull ?: longOrNull?.toDouble() ?: doubleOrNull ?: content
        }
        is JsonObject -> toMap()
        is JsonArray -> map { it.unwrap() }
    }

    private fun parseAction(raw: String): RuleAction? = runCatching {
        json.decodeFromString(RuleAction.serializer(), "\"$raw\"")
    }.getOrElse {
        runCatching { RuleAction.valueOf(raw.uppercase()) }.getOrNull()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
