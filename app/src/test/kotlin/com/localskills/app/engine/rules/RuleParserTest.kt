package com.localskills.app.engine.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleParserTest {

    private fun parse(src: String): RuleAst.Expr =
        RuleParser(Lexer(src).tokenize()).parse()

    @Test
    fun `parses number literal`() {
        val e = parse("42")
        assertEquals(RuleAst.NumLit(42.0), e)
    }

    @Test
    fun `parses currency literal as plain number`() {
        val e = parse("20000 INR")
        assertEquals(RuleAst.NumLit(20000.0), e)
    }

    @Test
    fun `parses string and boolean and null`() {
        assertEquals(RuleAst.StrLit("hi"), parse("\"hi\""))
        assertEquals(RuleAst.BoolLit(true), parse("true"))
        assertEquals(RuleAst.NullLit, parse("null"))
    }

    @Test
    fun `parses dotted path`() {
        val e = parse("entity.amount") as RuleAst.Path
        assertEquals(listOf("entity", "amount"), e.segments)
    }

    @Test
    fun `parses comparison`() {
        val e = parse("entity.amount > 20000") as RuleAst.Compare
        assertEquals(RuleAst.CmpOp.GT, e.op)
    }

    @Test
    fun `precedence not gt and gt or`() {
        // not a and b or c == ((not a) and b) or c
        val e = parse("not a and b or c") as RuleAst.Or
        val left = e.left as RuleAst.And
        assertTrue(left.left is RuleAst.Not)
    }

    @Test
    fun `parses parenthesised expression`() {
        val e = parse("(a or b) and c") as RuleAst.And
        assertTrue(e.left is RuleAst.Or)
    }

    @Test
    fun `parses in list`() {
        val e = parse("brand in (\"a\", \"b\", \"c\")") as RuleAst.InList
        assertEquals(3, e.items.size)
    }

    @Test
    fun `parses is in N days`() {
        val e = parse("expiry_date is in 3 days") as RuleAst.IsInDays
        assertEquals(3, e.days)
    }

    @Test
    fun `parses is in 1 day singular`() {
        val e = parse("expiry_date is in 1 day") as RuleAst.IsInDays
        assertEquals(1, e.days)
    }

    @Test
    fun `parses days_until`() {
        val e = parse("days_until(expiry_date) <= 7") as RuleAst.Compare
        assertTrue(e.left is RuleAst.DaysUntil)
    }

    @Test
    fun `parses month and current_month`() {
        val e = parse("month(txn_date) == current_month()") as RuleAst.Compare
        assertTrue(e.left is RuleAst.Month)
        assertTrue(e.right is RuleAst.CurrentMonth)
    }

    @Test
    fun `parses sum where`() {
        val e = parse("sum(amount where month(txn_date) == current_month()) > 20000") as RuleAst.Compare
        assertTrue(e.left is RuleAst.SumWhere)
    }

    @Test
    fun `rejects unknown function`() {
        assertThrows(RuleParseException::class.java) { parse("explode(x)") }
    }

    @Test
    fun `rejects unknown operator`() {
        assertThrows(RuleParseException::class.java) { parse("a + b") }
    }

    @Test
    fun `rejects single equals`() {
        assertThrows(RuleParseException::class.java) { parse("a = 1") }
    }

    @Test
    fun `rejects trailing junk`() {
        assertThrows(RuleParseException::class.java) { parse("a == 1 garbage") }
    }

    @Test
    fun `rejects empty in list`() {
        assertThrows(RuleParseException::class.java) { parse("a in ()") }
    }

    @Test
    fun `rejects sum without where`() {
        assertThrows(RuleParseException::class.java) { parse("sum(amount)") }
    }
}
