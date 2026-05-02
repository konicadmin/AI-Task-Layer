package com.localskills.app.engine.confidence

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic confidence score for an extraction. Models do not score
 * themselves — confidence is a weighted sum of independent signals.
 */
@Singleton
class ConfidenceEngine @Inject constructor() {

    fun score(signals: ConfidenceSignals): Double {
        val s = signals
        val raw = 0.30 * s.classifierMargin +
            0.30 * s.evidenceCoverage +
            0.25 * s.validatorPassRate +
            0.15 * s.sourceReliability
        return raw.coerceIn(0.0, 1.0)
    }

    fun band(score: Double): ConfidenceBand = when {
        score >= 0.85 -> ConfidenceBand.HIGH
        score >= 0.60 -> ConfidenceBand.MEDIUM
        score >= 0.40 -> ConfidenceBand.LOW
        else -> ConfidenceBand.VERY_LOW
    }
}

data class ConfidenceSignals(
    val classifierMargin: Double,
    val evidenceCoverage: Double,
    val validatorPassRate: Double,
    val sourceReliability: Double,
)

enum class ConfidenceBand { HIGH, MEDIUM, LOW, VERY_LOW }
