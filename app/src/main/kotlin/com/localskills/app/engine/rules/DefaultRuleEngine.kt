package com.localskills.app.engine.rules

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [RuleEngine] implementation: lex → parse → interpret.
 *
 * Caches parsed ASTs by source string so repeated evaluations during a worker
 * sweep do not re-tokenize. The cache is bounded so a misbehaving caller
 * cannot grow it without limit.
 */
@Singleton
class DefaultRuleEngine @Inject constructor() : RuleEngine {

    private val cache = LinkedHashMap<String, RuleAst.Expr>(16, 0.75f, true)
    private val interpreter = RuleInterpreter()

    override fun evaluate(expression: String, context: RuleContext): Boolean {
        val ast = compile(expression)
        return interpreter.evalBoolean(ast, context)
    }

    fun compile(expression: String): RuleAst.Expr {
        synchronized(cache) {
            cache[expression]?.let { return it }
        }
        val tokens = Lexer(expression).tokenize()
        val ast = RuleParser(tokens).parse()
        synchronized(cache) {
            if (cache.size >= MAX_CACHED) {
                val oldest = cache.keys.iterator().next()
                cache.remove(oldest)
            }
            cache[expression] = ast
        }
        return ast
    }

    companion object {
        private const val MAX_CACHED = 128
    }
}
