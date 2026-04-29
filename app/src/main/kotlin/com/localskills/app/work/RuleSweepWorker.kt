package com.localskills.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.localskills.app.engine.rules.RuleEvaluator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic worker that sweeps every enabled rule against current Results and
 * fires matches via [RuleEvaluator]. WorkManager guarantees at-most-once
 * execution per period, so dedupe lives in [com.localskills.app.notifications.RuleNotifier].
 */
@HiltWorker
class RuleSweepWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val evaluator: RuleEvaluator,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        evaluator.sweep()
        Result.success()
    } catch (t: Throwable) {
        if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
    }

    companion object {
        const val UNIQUE_NAME: String = "rule-sweep"
        private const val MAX_ATTEMPTS: Int = 3
    }
}
