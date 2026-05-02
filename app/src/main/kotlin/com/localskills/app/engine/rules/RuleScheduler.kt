package com.localskills.app.engine.rules

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.localskills.app.work.RuleSweepWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the periodic rule sweep. Kept as a thin wrapper so unit tests can
 * stub WorkManager without instantiating a Worker. Called from
 * [com.localskills.app.LocalSkillsApp.onCreate].
 */
@Singleton
class RuleScheduler @Inject constructor() {

    fun scheduleAll(context: Context) {
        val request = PeriodicWorkRequestBuilder<RuleSweepWorker>(
            SWEEP_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RuleSweepWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(RuleSweepWorker.UNIQUE_NAME)
    }

    companion object {
        /** Default sweep cadence. 24h matches a daily reminder cadence. */
        const val SWEEP_INTERVAL_HOURS: Long = 24L
    }
}
