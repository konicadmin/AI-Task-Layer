package com.localskills.app.engine.rules

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Evaluates a parsed [RuleAst] against a [RuleContext].
 *
 * Path resolution is allowlisted: only segments that exist in the active scope
 * map are followed. Unknown identifiers evaluate to `null`, which then either
 * short-circuits comparisons or surfaces as a typed mismatch — never as a
 * crash and never as reflection into Kotlin classes.
 */
class RuleInterpreter {

    /**
     * Stack of scopes. The bottom is the rule's [RuleContext.fields]; aggregate
     * functions push a per-row scope on top so paths inside `sum(... where ...)`
     * resolve against the current row first.
     */
    private class EvalState(
        val context: RuleContext,
        val scopes: ArrayDeque<Map<String, Any?>>,
    )

    fun eval(expr: RuleAst.Expr, context: RuleContext): Any? {
        val state = EvalState(context, ArrayDeque<Map<String, Any?>>().apply { addLast(context.fields) })
        return evalExpr(expr, state)
    }

    fun evalBoolean(expr: RuleAst.Expr, context: RuleContext): Boolean {
        return toBool(eval(expr, context))
    }

    private fun evalExpr(expr: RuleAst.Expr, state: EvalState): Any? = when (expr) {
        is RuleAst.NumLit -> expr.value
        is RuleAst.StrLit -> expr.value
        is RuleAst.BoolLit -> expr.value
        RuleAst.NullLit -> null
        is RuleAst.Path -> resolvePath(expr.segments, state)
        is RuleAst.Compare -> evalCompare(expr, state)
        is RuleAst.And -> toBool(evalExpr(expr.left, state)) && toBool(evalExpr(expr.right, state))
        is RuleAst.Or -> toBool(evalExpr(expr.left, state)) || toBool(evalExpr(expr.right, state))
        is RuleAst.Not -> !toBool(evalExpr(expr.expr, state))
        is RuleAst.InList -> {
            val v = evalExpr(expr.value, state)
            expr.items.any { equalsLoose(v, evalExpr(it, state)) }
        }
        is RuleAst.IsInDays -> evalIsInDays(expr, state)
        is RuleAst.DaysUntil -> daysUntil(evalExpr(expr.arg, state), state.context).toDouble()
        is RuleAst.Month -> monthOf(evalExpr(expr.arg, state), state.context).toDouble()
        RuleAst.CurrentMonth -> currentMonth(state.context).toDouble()
        is RuleAst.SumWhere -> evalSumWhere(expr, state)
    }

    private fun evalCompare(expr: RuleAst.Compare, state: EvalState): Boolean {
        val l = evalExpr(expr.left, state)
        val r = evalExpr(expr.right, state)
        return when (expr.op) {
            RuleAst.CmpOp.EQ -> equalsLoose(l, r)
            RuleAst.CmpOp.NE -> !equalsLoose(l, r)
            RuleAst.CmpOp.LT -> compareNum(l, r) < 0
            RuleAst.CmpOp.LE -> compareNum(l, r) <= 0
            RuleAst.CmpOp.GT -> compareNum(l, r) > 0
            RuleAst.CmpOp.GE -> compareNum(l, r) >= 0
        }
    }

    private fun evalIsInDays(expr: RuleAst.IsInDays, state: EvalState): Boolean {
        val target = toLocalDate(evalExpr(expr.value, state), state.context) ?: return false
        val today = todayIn(state.context)
        if (target.isBefore(today)) return false
        val diff = ChronoUnit.DAYS.between(today, target)
        return diff <= expr.days.toLong()
    }

    private fun evalSumWhere(expr: RuleAst.SumWhere, state: EvalState): Double {
        val raw = state.scopes.last()[ITER_NAME]
            ?: state.context.fields[ITER_NAME]
        val rows: List<Map<String, Any?>> = when (raw) {
            null -> emptyList()
            is List<*> -> raw.mapNotNull { row ->
                @Suppress("UNCHECKED_CAST")
                row as? Map<String, Any?>
            }
            else -> emptyList()
        }
        var total = 0.0
        for (row in rows) {
            state.scopes.addLast(row)
            try {
                if (toBool(evalExpr(expr.predicate, state))) {
                    val sel = evalExpr(expr.select, state)
                    total += toNum(sel) ?: 0.0
                }
            } finally {
                state.scopes.removeLast()
            }
        }
        return total
    }

    // ---------------------------------------------------------------- helpers

    private fun resolvePath(segments: List<String>, state: EvalState): Any? {
        // Try each scope from top (most recent) down, walking the dotted path.
        for (scope in state.scopes.asReversed()) {
            val v = walk(segments, scope)
            if (v != PATH_MISS) return v
        }
        return null
    }

    private fun walk(segments: List<String>, root: Map<String, Any?>): Any? {
        var cur: Any? = root
        for ((i, seg) in segments.withIndex()) {
            if (cur !is Map<*, *>) return if (i == 0) PATH_MISS else null
            if (!cur.containsKey(seg)) return if (i == 0) PATH_MISS else null
            cur = cur[seg]
        }
        return cur
    }

    private fun toBool(v: Any?): Boolean = when (v) {
        null -> false
        is Boolean -> v
        is Double -> v != 0.0
        is Number -> v.toDouble() != 0.0
        is String -> v.isNotEmpty()
        else -> true
    }

    private fun toNum(v: Any?): Double? = when (v) {
        null -> null
        is Double -> v
        is Number -> v.toDouble()
        is Boolean -> if (v) 1.0 else 0.0
        is String -> v.toDoubleOrNull()
        else -> null
    }

    private fun equalsLoose(a: Any?, b: Any?): Boolean {
        if (a == null || b == null) return a == null && b == null
        val na = toNum(a)
        val nb = toNum(b)
        if (na != null && nb != null) return na == nb
        return a.toString() == b.toString()
    }

    private fun compareNum(a: Any?, b: Any?): Int {
        val na = toNum(a) ?: throw RuleEvalException("cannot compare non-numeric '$a'")
        val nb = toNum(b) ?: throw RuleEvalException("cannot compare non-numeric '$b'")
        return na.compareTo(nb)
    }

    private fun zone(ctx: RuleContext): ZoneId = runCatching { ZoneId.of(ctx.timezone) }.getOrDefault(ZoneId.systemDefault())

    private fun todayIn(ctx: RuleContext): LocalDate =
        Instant.ofEpochMilli(ctx.nowEpochMillis).atZone(zone(ctx)).toLocalDate()

    private fun toLocalDate(v: Any?, ctx: RuleContext): LocalDate? = when (v) {
        null -> null
        is LocalDate -> v
        is Instant -> v.atZone(zone(ctx)).toLocalDate()
        is Number -> Instant.ofEpochMilli(v.toLong()).atZone(zone(ctx)).toLocalDate()
        is String -> parseDate(v)
        else -> null
    }

    private fun parseDate(s: String): LocalDate? {
        if (s.isBlank()) return null
        for (fmt in DATE_FORMATS) {
            try {
                return LocalDate.parse(s, fmt)
            } catch (_: Exception) {
                // try next
            }
        }
        return null
    }

    private fun daysUntil(v: Any?, ctx: RuleContext): Long {
        val target = toLocalDate(v, ctx) ?: return Long.MAX_VALUE
        return ChronoUnit.DAYS.between(todayIn(ctx), target)
    }

    private fun monthOf(v: Any?, ctx: RuleContext): Int {
        val date = toLocalDate(v, ctx) ?: return 0
        return date.monthValue
    }

    private fun currentMonth(ctx: RuleContext): Int = todayIn(ctx).monthValue

    companion object {
        const val ITER_NAME: String = "results"
        private val PATH_MISS = Any()
        private val DATE_FORMATS = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        )
    }
}

class RuleEvalException(message: String) : RuntimeException(message)
