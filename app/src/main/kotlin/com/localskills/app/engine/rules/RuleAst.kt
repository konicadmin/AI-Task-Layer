package com.localskills.app.engine.rules

/**
 * Abstract syntax tree for parsed rule expressions. Nodes are immutable and
 * carry only enough state for the interpreter — no source positions are
 * threaded through because the parser already validated structure.
 */
sealed interface RuleAst {

    sealed interface Expr : RuleAst

    // Literals -------------------------------------------------------------

    data class NumLit(val value: Double) : Expr
    data class StrLit(val value: String) : Expr
    data class BoolLit(val value: Boolean) : Expr
    data object NullLit : Expr

    // Identifier path, e.g. `entity.amount`. ------------------------------
    data class Path(val segments: List<String>) : Expr

    // Operators -----------------------------------------------------------

    enum class CmpOp { EQ, NE, LT, LE, GT, GE }

    data class Compare(val op: CmpOp, val left: Expr, val right: Expr) : Expr
    data class And(val left: Expr, val right: Expr) : Expr
    data class Or(val left: Expr, val right: Expr) : Expr
    data class Not(val expr: Expr) : Expr

    // Membership ----------------------------------------------------------

    data class InList(val value: Expr, val items: List<Expr>) : Expr

    /** `<expr> is in N day[s]` — true when value is between now and now+N days. */
    data class IsInDays(val value: Expr, val days: Int) : Expr

    // Functions -----------------------------------------------------------

    data class DaysUntil(val arg: Expr) : Expr
    data class Month(val arg: Expr) : Expr
    data object CurrentMonth : Expr

    /**
     * Aggregate `sum(<numExpr> where <boolExpr>)` over the implicit `results`
     * collection. Each iteration binds the row's flat field map as the active
     * scope so paths inside the expressions resolve against the current row.
     */
    data class SumWhere(val select: Expr, val predicate: Expr) : Expr
}
