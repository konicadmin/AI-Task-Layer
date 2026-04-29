package com.localskills.app.engine.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RuleInterpreterTest {

    private val engine = DefaultRuleEngine()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today: LocalDate = LocalDate.of(2026, 4, 29)
    private val now: Long = today.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun ctx(fields: Map<String, Any?>): RuleContext =
        RuleContext(fields = fields, nowEpochMillis = now, timezone = zone.id)

    @Test
    fun `comparison numeric`() {
        val c = ctx(mapOf("amount" to 25000.0))
        assertTrue(engine.evaluate("amount > 20000", c))
        assertFalse(engine.evaluate("amount < 20000", c))
        assertTrue(engine.evaluate("amount >= 25000", c))
        assertTrue(engine.evaluate("amount == 25000", c))
        assertTrue(engine.evaluate("amount != 1", c))
    }

    @Test
    fun `comparison currency literal`() {
        val c = ctx(mapOf("amount" to 25000.0))
        assertTrue(engine.evaluate("amount > 20000 INR", c))
    }

    @Test
    fun `logical operators with precedence`() {
        val c = ctx(mapOf("a" to true, "b" to false, "c" to true))
        assertTrue(engine.evaluate("a and (b or c)", c))
        assertFalse(engine.evaluate("a and b or false", c))
        assertTrue(engine.evaluate("not b", c))
    }

    @Test
    fun `in list`() {
        val c = ctx(mapOf("brand" to "Acme"))
        assertTrue(engine.evaluate("brand in (\"Acme\", \"Other\")", c))
        assertFalse(engine.evaluate("brand in (\"X\", \"Y\")", c))
    }

    @Test
    fun `is in N days within window`() {
        // 2 days from today
        val c = ctx(mapOf("expiry_date" to today.plusDays(2).toString()))
        assertTrue(engine.evaluate("expiry_date is in 3 days", c))
        assertFalse(engine.evaluate("expiry_date is in 1 day", c))
    }

    @Test
    fun `is in days rejects past dates`() {
        val c = ctx(mapOf("expiry_date" to today.minusDays(1).toString()))
        assertFalse(engine.evaluate("expiry_date is in 3 days", c))
    }

    @Test
    fun `days_until`() {
        val c = ctx(mapOf("expiry_date" to today.plusDays(5).toString()))
        assertTrue(engine.evaluate("days_until(expiry_date) == 5", c))
        assertTrue(engine.evaluate("days_until(expiry_date) <= 7", c))
    }

    @Test
    fun `month and current_month`() {
        val c = ctx(mapOf("txn_date" to today.toString()))
        assertTrue(engine.evaluate("month(txn_date) == current_month()", c))
    }

    @Test
    fun `sum where over results collection`() {
        val rows = listOf(
            mapOf<String, Any?>("amount" to 10000.0, "txn_date" to today.toString()),
            mapOf<String, Any?>("amount" to 15000.0, "txn_date" to today.toString()),
            // different month — excluded
            mapOf<String, Any?>("amount" to 99999.0, "txn_date" to today.minusMonths(2).toString()),
        )
        val c = ctx(mapOf("results" to rows))
        assertTrue(
            engine.evaluate(
                "sum(amount where month(txn_date) == current_month()) > 20000",
                c,
            ),
        )
        assertFalse(
            engine.evaluate(
                "sum(amount where month(txn_date) == current_month()) > 1000000",
                c,
            ),
        )
    }

    @Test
    fun `dotted path lookup`() {
        val c = ctx(mapOf("entity" to mapOf("amount" to 30000.0)))
        assertTrue(engine.evaluate("entity.amount > 20000", c))
    }

    @Test
    fun `unknown identifier evaluates falsey not crash`() {
        val c = ctx(mapOf<String, Any?>())
        // missing identifier compared to a number — non-numeric throws eval
        // exception inside RuleEngine.evaluate; we route via try/catch in
        // evaluator. Here we test the safer truth-context path.
        assertFalse(engine.evaluate("not (mystery_field == null)", c))
    }
}
