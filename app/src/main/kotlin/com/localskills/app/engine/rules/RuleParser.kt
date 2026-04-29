package com.localskills.app.engine.rules

/**
 * Recursive-descent parser for the rule expression grammar.
 *
 * Grammar (lowest to highest precedence):
 *   or       := and ('or' and)*
 *   and      := not ('and' not)*
 *   not      := 'not' not | membership
 *   membership := comparison (('in' list) | ('is' 'in' duration))?
 *   comparison := primary ((==|!=|<|<=|>|>=) primary)?
 *   primary  := number | string | bool | null | path | call | '(' or ')'
 *   call     := IDENT '(' args ')'
 *   args     := (none) | expr | expr ('where' expr)
 *   list     := '(' expr (',' expr)* ')'
 *   duration := number ('day' | 'days')
 *
 * The parser only accepts identifiers and function names from a closed set —
 * unknown calls are rejected here so the interpreter never has to defend
 * against arbitrary names.
 */
class RuleParser(private val tokens: List<Token>) {

    private var index: Int = 0

    fun parse(): RuleAst.Expr {
        val expr = parseOr()
        val tail = peek()
        if (tail !is Token.EndOfInput) {
            throw RuleParseException("unexpected token after expression: $tail", tail.position)
        }
        return expr
    }

    // ---------------------------------------------------------------- parsing

    private fun parseOr(): RuleAst.Expr {
        var left = parseAnd()
        while (matchKeyword("or")) {
            val right = parseAnd()
            left = RuleAst.Or(left, right)
        }
        return left
    }

    private fun parseAnd(): RuleAst.Expr {
        var left = parseNot()
        while (matchKeyword("and")) {
            val right = parseNot()
            left = RuleAst.And(left, right)
        }
        return left
    }

    private fun parseNot(): RuleAst.Expr {
        if (matchKeyword("not")) {
            return RuleAst.Not(parseNot())
        }
        return parseMembership()
    }

    private fun parseMembership(): RuleAst.Expr {
        val left = parseComparison()
        val tk = peek()
        if (tk is Token.Keyword && tk.word == "in") {
            advance()
            val items = parseList()
            return RuleAst.InList(left, items)
        }
        if (tk is Token.Keyword && tk.word == "is") {
            advance()
            val inTk = peek()
            if (inTk !is Token.Keyword || inTk.word != "in") {
                throw RuleParseException("expected 'in' after 'is'", inTk.position)
            }
            advance()
            val days = parseDuration()
            return RuleAst.IsInDays(left, days)
        }
        return left
    }

    private fun parseComparison(): RuleAst.Expr {
        val left = parsePrimary()
        val tk = peek()
        if (tk is Token.Symbol) {
            val op = when (tk.text) {
                "==" -> RuleAst.CmpOp.EQ
                "!=" -> RuleAst.CmpOp.NE
                "<" -> RuleAst.CmpOp.LT
                "<=" -> RuleAst.CmpOp.LE
                ">" -> RuleAst.CmpOp.GT
                ">=" -> RuleAst.CmpOp.GE
                else -> null
            }
            if (op != null) {
                advance()
                val right = parsePrimary()
                return RuleAst.Compare(op, left, right)
            }
        }
        return left
    }

    private fun parsePrimary(): RuleAst.Expr {
        val tk = peek()
        return when (tk) {
            is Token.Number -> { advance(); RuleAst.NumLit(tk.value) }
            is Token.Str -> { advance(); RuleAst.StrLit(tk.value) }
            is Token.Bool -> { advance(); RuleAst.BoolLit(tk.value) }
            is Token.NullLit -> { advance(); RuleAst.NullLit }
            is Token.Identifier -> parseIdentOrCall(tk)
            is Token.Symbol -> {
                if (tk.text == "(") {
                    advance()
                    val inner = parseOr()
                    expectSymbol(")")
                    inner
                } else {
                    throw RuleParseException("unexpected symbol '${tk.text}'", tk.position)
                }
            }
            is Token.Keyword -> throw RuleParseException("unexpected keyword '${tk.word}'", tk.position)
            is Token.CurrencyCode -> throw RuleParseException("stray currency code '${tk.code}'", tk.position)
            is Token.EndOfInput -> throw RuleParseException("unexpected end of input", tk.position)
        }
    }

    private fun parseIdentOrCall(head: Token.Identifier): RuleAst.Expr {
        advance()
        val next = peek()
        if (next is Token.Symbol && next.text == "(") {
            return parseCall(head)
        }
        // Dotted path: lexer already collapsed `a.b.c` into a single identifier.
        val segments = head.name.split('.')
        if (segments.any { it.isEmpty() }) {
            throw RuleParseException("invalid identifier '${head.name}'", head.position)
        }
        return RuleAst.Path(segments)
    }

    private fun parseCall(head: Token.Identifier): RuleAst.Expr {
        expectSymbol("(")
        val expr = when (head.name) {
            "current_month" -> {
                expectSymbol(")")
                return RuleAst.CurrentMonth
            }
            "days_until" -> {
                val arg = parseOr()
                expectSymbol(")")
                RuleAst.DaysUntil(arg)
            }
            "month" -> {
                val arg = parseOr()
                expectSymbol(")")
                RuleAst.Month(arg)
            }
            "sum" -> {
                val select = parseOr()
                val whereTk = peek()
                if (whereTk !is Token.Keyword || whereTk.word != "where") {
                    throw RuleParseException("sum() requires 'where' clause", whereTk.position)
                }
                advance()
                val predicate = parseOr()
                expectSymbol(")")
                RuleAst.SumWhere(select, predicate)
            }
            else -> throw RuleParseException("unknown function '${head.name}'", head.position)
        }
        return expr
    }

    private fun parseList(): List<RuleAst.Expr> {
        expectSymbol("(")
        val items = mutableListOf<RuleAst.Expr>()
        if (!(peek() is Token.Symbol && (peek() as Token.Symbol).text == ")")) {
            items += parseOr()
            while (peek() is Token.Symbol && (peek() as Token.Symbol).text == ",") {
                advance()
                items += parseOr()
            }
        }
        expectSymbol(")")
        if (items.isEmpty()) {
            throw RuleParseException("empty list literal", peek().position)
        }
        return items
    }

    private fun parseDuration(): Int {
        val numTk = peek()
        if (numTk !is Token.Number) {
            throw RuleParseException("expected number for duration", numTk.position)
        }
        advance()
        val unitTk = peek()
        if (unitTk !is Token.Identifier || (unitTk.name != "day" && unitTk.name != "days")) {
            throw RuleParseException("expected 'day' or 'days'", unitTk.position)
        }
        advance()
        val value = numTk.value
        if (value < 0 || value != value.toInt().toDouble()) {
            throw RuleParseException("duration must be a non-negative integer", numTk.position)
        }
        return value.toInt()
    }

    // ---------------------------------------------------------------- helpers

    private fun peek(): Token = tokens[index]

    private fun advance(): Token = tokens[index++]

    private fun matchKeyword(word: String): Boolean {
        val tk = peek()
        if (tk is Token.Keyword && tk.word == word) {
            advance()
            return true
        }
        return false
    }

    private fun expectSymbol(text: String) {
        val tk = peek()
        if (tk !is Token.Symbol || tk.text != text) {
            throw RuleParseException("expected '$text'", tk.position)
        }
        advance()
    }
}
