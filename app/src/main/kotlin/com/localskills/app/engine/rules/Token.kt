package com.localskills.app.engine.rules

/**
 * Lexical tokens emitted by [Lexer]. The token set is intentionally narrow:
 * the parser only accepts the fixed grammar, so unknown identifiers or
 * operators surface as a parse error rather than reaching the interpreter.
 */
sealed interface Token {
    val position: Int

    data class Number(val value: Double, override val position: Int) : Token
    data class Str(val value: String, override val position: Int) : Token
    data class Bool(val value: Boolean, override val position: Int) : Token
    data class NullLit(override val position: Int) : Token
    data class Identifier(val name: String, override val position: Int) : Token
    data class CurrencyCode(val code: String, override val position: Int) : Token

    /** Reserved words that are NOT identifiers. */
    data class Keyword(val word: String, override val position: Int) : Token

    data class Symbol(val text: String, override val position: Int) : Token
    data class EndOfInput(override val position: Int) : Token
}

class RuleParseException(
    message: String,
    val position: Int,
) : RuntimeException("$message (at $position)")
