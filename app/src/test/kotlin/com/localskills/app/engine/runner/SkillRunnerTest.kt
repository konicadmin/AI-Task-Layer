package com.localskills.app.engine.runner

import android.net.Uri
import com.localskills.app.data.db.dao.ResultDao
import com.localskills.app.data.db.dao.RuleDao
import com.localskills.app.data.db.dao.RunDao
import com.localskills.app.data.db.dao.SkillDao
import com.localskills.app.data.db.entity.ResultEntity
import com.localskills.app.data.db.entity.RuleEntity
import com.localskills.app.data.db.entity.RunEntity
import com.localskills.app.data.db.entity.RunStatus
import com.localskills.app.data.db.entity.SkillEntity
import com.localskills.app.data.db.entity.SkillSource
import com.localskills.app.data.repo.RunRepository
import com.localskills.app.data.repo.SkillRepository
import com.localskills.app.engine.CaptureInput
import com.localskills.app.engine.confidence.ConfidenceEngine
import com.localskills.app.engine.ocr.OcrEngine
import com.localskills.app.engine.ocr.OcrResult
import com.localskills.app.engine.runtime.EchoRuntime
import com.localskills.app.engine.validation.SchemaValidator
import com.localskills.app.skill.manifest.FieldType
import com.localskills.app.skill.manifest.InputKind
import com.localskills.app.skill.manifest.RuleAction
import com.localskills.app.skill.manifest.RuleSpec
import com.localskills.app.skill.manifest.SkillManifest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * End-to-end test for the runner pipeline using fake DAOs (Room can't run
 * on plain JVM tests without Robolectric, and the runner doesn't care
 * about the SQL layer — only the DAO contract).
 */
class SkillRunnerTest {

    private val skillDao = FakeSkillDao()
    private val runDao = FakeRunDao()
    private val resultDao = FakeResultDao()
    private val ruleDao = FakeRuleDao()

    private val skillRepository = SkillRepository(skillDao, ruleDao)
    private val runRepository = RunRepository(runDao, resultDao)
    private val runner = SkillRunner(
        skillRepository = skillRepository,
        runRepository = runRepository,
        extractorRuntime = EchoRuntime(),
        schemaValidator = SchemaValidator(),
        confidenceEngine = ConfidenceEngine(),
        ocrEngine = NeverOcr,
    )

    private val manifest = SkillManifest(
        id = "coupon-extractor.v1",
        name = "Coupon Extractor",
        description = "test",
        inputs = listOf(InputKind.TEXT, InputKind.IMAGE),
        instruction = "Extract",
        outputSchema = linkedMapOf(
            "brand" to FieldType.TEXT,
            "coupon_code" to FieldType.TEXT,
            "offer" to FieldType.TEXT,
            "expiry_date" to FieldType.DATE,
        ),
        rules = listOf(RuleSpec("expiry_date is in 3 days", RuleAction.NOTIFY)),
    )

    @Test
    fun `text run persists Run SUCCEEDED and Result with valid JSON`() = runTest {
        skillRepository.upsert(manifest, source = SkillSource.SEED, enabled = true)
        // EchoRuntime returns empty strings — but DATE is "" which is not ISO-8601.
        // To keep the runner-side success path exercised we use a manifest with
        // only TEXT fields here, while the rules-list case tests rule persistence.
        val textOnly = manifest.copy(
            outputSchema = linkedMapOf(
                "brand" to FieldType.TEXT,
                "coupon_code" to FieldType.TEXT,
            ),
            id = "coupon.text-only.v1",
            rules = emptyList(),
        )
        skillRepository.upsert(textOnly, source = SkillSource.SEED, enabled = true)

        val capture = CaptureInput(
            kind = InputKind.TEXT,
            mimeType = "text/plain",
            rawText = "FLAT200 off your next Indian Oil bill, expires 2026-05-01",
        )

        val outcome = runner.run(textOnly.id, capture)
        assertTrue("expected Success, got $outcome", outcome is RunOutcome.Success)
        outcome as RunOutcome.Success

        val storedRun = runDao.findById(outcome.run.id)
        assertNotNull(storedRun)
        assertEquals(RunStatus.SUCCEEDED, storedRun!!.status)

        val storedResult = resultDao.findForRun(outcome.run.id)
        assertNotNull(storedResult)

        // Payload must parse as a JSON object containing every declared field.
        val parsed = Json.parseToJsonElement(storedResult!!.payloadJson) as JsonObject
        assertTrue(parsed.containsKey("brand"))
        assertTrue(parsed.containsKey("coupon_code"))
    }

    @Test
    fun `rules in manifest persisted as RuleEntity rows`() = runTest {
        skillRepository.upsert(manifest, source = SkillSource.SEED, enabled = true)
        val rules = ruleDao.allFor(manifest.id)
        assertEquals(1, rules.size)
        assertEquals("expiry_date is in 3 days", rules.first().expression)
        assertEquals(RuleAction.NOTIFY.name, rules.first().action)
    }

    @Test
    fun `unknown skill id returns SkillNotFound without persisting Run`() = runTest {
        val outcome = runner.run("does.not.exist", CaptureInput(InputKind.TEXT, "text/plain", "hi"))
        assertTrue(outcome is RunOutcome.SkillNotFound)
        assertTrue(runDao.all().isEmpty())
    }

    private object NeverOcr : OcrEngine {
        override suspend fun recognize(uri: Uri): OcrResult =
            error("OCR should not be called for text-only runs")
    }
}

// ---------- Fake DAOs ----------

private class FakeSkillDao : SkillDao {
    private val rows = MutableStateFlow<Map<String, SkillEntity>>(emptyMap())
    override fun observeAll(): Flow<List<SkillEntity>> =
        rows.asStateFlow().map { it.values.sortedByDescending { e -> e.updatedAt } }
    override fun observeEnabled(): Flow<List<SkillEntity>> =
        rows.asStateFlow().map { it.values.filter { e -> e.enabled }.sortedByDescending { e -> e.updatedAt } }
    override suspend fun findById(id: String): SkillEntity? = rows.value[id]
    override suspend fun upsert(skill: SkillEntity) { rows.value = rows.value + (skill.id to skill) }
    override suspend fun update(skill: SkillEntity) { rows.value = rows.value + (skill.id to skill) }
    override suspend fun setEnabled(id: String, enabled: Boolean) {
        val r = rows.value[id] ?: return
        rows.value = rows.value + (id to r.copy(enabled = enabled))
    }
    override suspend fun deleteById(id: String) { rows.value = rows.value - id }
}

private class FakeRunDao : RunDao {
    private val rows = MutableStateFlow<Map<String, RunEntity>>(emptyMap())
    override fun observeForSkill(skillId: String): Flow<List<RunEntity>> =
        rows.asStateFlow().map { it.values.filter { r -> r.skillId == skillId }.sortedByDescending { it.createdAt } }
    override fun observeRecent(limit: Int): Flow<List<RunEntity>> =
        rows.asStateFlow().map { it.values.sortedByDescending { r -> r.createdAt }.take(limit) }
    override suspend fun findById(id: String): RunEntity? = rows.value[id]
    override suspend fun upsert(run: RunEntity) { rows.value = rows.value + (run.id to run) }
    override suspend fun update(run: RunEntity) { rows.value = rows.value + (run.id to run) }
    fun all(): List<RunEntity> = rows.value.values.toList()
}

private class FakeResultDao : ResultDao {
    private val rows = MutableStateFlow<Map<String, ResultEntity>>(emptyMap())
    override suspend fun findForRun(runId: String): ResultEntity? =
        rows.value.values.firstOrNull { it.runId == runId }
    override fun observeForSkill(skillId: String): Flow<List<ResultEntity>> =
        rows.asStateFlow().map { it.values.filter { r -> r.skillId == skillId } }
    override suspend fun upsert(result: ResultEntity) { rows.value = rows.value + (result.id to result) }
    override suspend fun setConfirmed(id: String, confirmed: Boolean) {
        val r = rows.value[id] ?: return
        rows.value = rows.value + (id to r.copy(confirmed = confirmed))
    }
}

private class FakeRuleDao : RuleDao {
    private val rows = MutableStateFlow<Map<String, RuleEntity>>(emptyMap())
    override fun observeEnabled(): Flow<List<RuleEntity>> =
        rows.asStateFlow().map { it.values.filter { r -> r.enabled } }
    override fun observeForSkill(skillId: String): Flow<List<RuleEntity>> =
        rows.asStateFlow().map { it.values.filter { r -> r.skillId == skillId } }
    override suspend fun upsert(rule: RuleEntity) { rows.value = rows.value + (rule.id to rule) }
    override suspend fun update(rule: RuleEntity) { rows.value = rows.value + (rule.id to rule) }
    override suspend fun markFired(id: String, firedAt: Instant) {
        val r = rows.value[id] ?: return
        rows.value = rows.value + (id to r.copy(lastFiredAt = firedAt))
    }
    override suspend fun deleteById(id: String) { rows.value = rows.value - id }
    fun allFor(skillId: String): List<RuleEntity> = rows.value.values.filter { it.skillId == skillId }
}
