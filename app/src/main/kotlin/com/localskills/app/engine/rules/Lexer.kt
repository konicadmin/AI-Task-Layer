package com.localskills.app.engine.rules

/**
 * Hand-written scanner for the rule expression language. The lexer recognises
 * a closed set of operators, keywords, literals, and dotted identifiers and
 * fails fast on anything else so the interpreter never sees ambiguous input.
 */
class Lexer(private val source: String) {

    private var pos: Int = 0

    fun tokenize(): List<Token> {
        val out = mutableListOf<Token>()
        while (pos < source.length) {
            val ch = source[pos]
            when {
                ch.isWhitespace() -> pos++
                ch == '"' -> out += readString()
                ch.isDigit() -> out += readNumberOrCurrency()
                ch.isLetter() || ch == '_' -> out += readWord()
                ch == '(' || ch == ')' || ch == ',' -> {
                    out += Token.Symbol(ch.toString(), pos); pos++
                }
                ch == '=' || ch == '!' || ch == '<' || ch == '>' -> out += readComparator()
                else -> throw RuleParseException("unexpected character '$ch'", pos)
            }
        }
        out += Token.EndOfInput(pos)
        return out
    }

    private fun readString(): Token {
        val start = pos
        pos++ // opening quote
        val sb = StringBuilder()
        while (pos < source.length && source[pos] != '"') {
            val c = source[pos]
            if (c == '\\' && pos + 1 < source.length) {
                val next = source[pos + 1]
                sb.append(
                    when (next) {
                        'n' -> '\n'
                        't' -> '\t'
                        '"' -> '"'
                        '\\' -> '\\'
                        else -> throw RuleParseException("bad escape \\$next", pos)
                    },
                )
                pos += 2
            } else {
                sb.append(c); pos++
            }
        }
        if (pos >= source.length) throw RuleParseException("unterminated string", start)
        pos++ // closing quote
        return Token.Str(sb.toString(), start)
    }

    private fun readNumberOrCurrency(): Token {
        val start = pos
        while (pos < source.length && source[pos].isDigit()) pos++
        if (pos < source.length && source[pos] == '.' &&
            pos + 1 < source.length && source[pos + 1].isDigit()
        ) {
            pos++
            while (pos < source.length && source[pos].isDigit()) pos++
        }
        val numText = source.substring(start, pos)
        val value = numText.toDoubleOrNull()
            ?: throw RuleParseException("invalid number '$numText'", start)
        // Optional 3-letter ISO currency code, e.g. `20000 INR`. We treat the
        // value as a plain number and discard the code at lex time so the
        // parser/interpreter need not know about currencies at all.
        var look = pos
        while (look < source.length && source[look] == ' ') look++
        if (look + 2 < source.length &&
            source[look].isUpperCase() && source[look + 1].isUpperCase() && source[look + 2].isUpperCase()
        ) {
            val after = look + 3
            val terminated = after >= source.length || !(source[after].isLetterOrDigit() || source[after] == '_')
            if (terminated) {
                pos = look + 3
            }
        }
        return Token.Number(value, start)
    }

    private fun readWord(): Token {
        val start = pos
        while (pos < source.length && (source[pos].isLetterOrDigit() || source[pos] == '_' || source[pos] == '.')) {
            pos++
        }
        val word = source.substring(start, pos)
        return when (word) {
            "true" -> Token.Bool(true, start)
            "false" -> Token.Bool(false, start)
            "null" -> Token.NullLit(start)
            "and", "or", "not", "in", "is", "where" -> Token.Keyword(word, start)
            else -> Token.Identifier(word, start)
        }
    }

    private fun readComparator(): Token {
        val start = pos
        val ch = source[pos]
        val next = if (pos + 1 < source.length) source[pos + 1] else ' '
        val text = when {
            ch == '=' && next == '=' -> { pos += 2; "==" }
            ch == '!' && next == '=' -> { pos += 2; "!=" }
            ch == '<' && next == '=' -> { pos += 2; "<=" }
            ch == '>' && next == '=' -> { pos += 2; ">=" }
            ch == '<' -> { pos++; "<" }
            ch == '>' -> { pos++; ">" }
            ch == '=' -> throw RuleParseException("did you mean '=='?", start)
            ch == '!' -> throw RuleParseException("'!' must be followed by '='", start)
            else -> throw RuleParseException("unexpected '$ch'", start)
        }
        return Token.Symbol(text, start)
    }
}
