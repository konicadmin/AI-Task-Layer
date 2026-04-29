package com.localskills.app.data.repo

import com.localskills.app.data.db.dao.ResultDao
import com.localskills.app.data.db.dao.RunDao
import com.localskills.app.data.db.entity.ResultEntity
import com.localskills.app.data.db.entity.RunEntity
import com.localskills.app.data.db.entity.RunStatus
import com.localskills.app.engine.CaptureInput
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists [RunEntity] and [ResultEntity] rows for the runner pipeline.
 * Calling code is expected to follow the lifecycle:
 *  1. [startRun] — writes a PENDING row before the extractor is invoked.
 *  2. [completeRun] / [failRun] — flips status and stamps duration.
 *  3. [saveResult] — only on success, after schema validation.
 */
@Singleton
class RunRepository @Inject constructor(
    private val runDao: RunDao,
    private val resultDao: ResultDao,
) {

    fun observeRecent(limit: Int = 50): Flow<List<RunEntity>> = runDao.observeRecent(limit)

    fun observeForSkill(skillId: String): Flow<List<RunEntity>> = runDao.observeForSkill(skillId)

    suspend fun findRun(id: String): RunEntity? = runDao.findById(id)

    suspend fun findResult(runId: String): ResultEntity? = resultDao.findForRun(runId)

    suspend fun startRun(skillId: String, capture: CaptureInput): RunEntity {
        val run = RunEntity(
            id = UUID.randomUUID().toString(),
            skillId = skillId,
            inputKind = capture.kind.name,
            inputText = capture.rawText,
            artifactPath = capture.artifactPath,
            sourceAppHint = capture.sourceAppHint,
            createdAt = Instant.now(),
            durationMs = null,
            status = RunStatus.PENDING,
            errorMessage = null,
        )
        runDao.upsert(run)
        return run
    }

    suspend fun completeRun(run: RunEntity, durationMs: Long): RunEntity {
        val updated = run.copy(
            status = RunStatus.SUCCEEDED,
            durationMs = durationMs,
            errorMessage = null,
        )
        runDao.update(updated)
        return updated
    }

    suspend fun failRun(run: RunEntity, durationMs: Long, message: String): RunEntity {
        val updated = run.copy(
            status = RunStatus.FAILED,
            durationMs = durationMs,
            errorMessage = message,
        )
        runDao.update(updated)
        return updated
    }

    suspend fun saveResult(
        runId: String,
        skillId: String,
        payloadJson: String,
        confidence: Double,
        confirmed: Boolean = false,
    ): ResultEntity {
        val result = ResultEntity(
            id = UUID.randomUUID().toString(),
            runId = runId,
            skillId = skillId,
            payloadJson = payloadJson,
            confidence = confidence,
            confirmed = confirmed,
        )
        resultDao.upsert(result)
        return result
    }

    suspend fun updateResultPayload(
        existing: ResultEntity,
        payloadJson: String,
        confirmed: Boolean,
    ): ResultEntity {
        val updated = existing.copy(payloadJson = payloadJson, confirmed = confirmed)
        resultDao.upsert(updated)
        return updated
    }

    suspend fun setConfirmed(resultId: String, confirmed: Boolean) {
        resultDao.setConfirmed(resultId, confirmed)
    }
}
