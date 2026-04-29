package com.localskills.app.engine.builder

import com.localskills.app.engine.runtime.EchoRuntime
import com.localskills.app.engine.runtime.ExtractorInput
import com.localskills.app.engine.runtime.ExtractorOutput
import com.localskills.app.engine.runtime.ExtractorRuntime
import com.localskills.app.engine.validation.SchemaValidator
import com.localskills.app.skill.manifest.FieldType
import com.localskills.app.skill.manifest.InputKind
import com.localskills.app.skill.manifest.SkillManifest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillTestHarnessTest {

    private val draftToManifest = DraftToManifest()
    private val validator = SchemaValidator()

    private fun harness(runtime: ExtractorRuntime) = SkillTestHarness(
        draftToManifest = draftToManifest,
        runtime = runtime,
        validator = validator,
    )

    private fun validDraft(): ManifestDraft = ManifestDraft(
        id = "expense.v1",
        name = "Expense",
        inputs = setOf(InputKind.TEXT),
        instruction = "Extract merchant + amount",
        outputFields = listOf(
            DraftField("merchant", FieldType.TEXT),
            DraftField("amount", FieldType.NUMBER),
        ),
    )

    @Test
    fun `returns DraftInvalid when draft fails validation`() = runTest {
        val report = harness(EchoRuntime()).run(ManifestDraft(), "anything")
        assertTrue(report is TestReport.DraftInvalid)
        assertTrue((report as TestReport.DraftInvalid).errors.isNotEmpty())
    }

    @Test
    fun `runs extractor and reports per-field values`() = runTest {
        val report = harness(EchoRuntime()).run(validDraft(), "Indian Oil 845.20 28-Apr")
        assertTrue(report is TestReport.Success)
        val s = report as TestReport.Success
        assertTrue(s.fieldValues.containsKey("merchant"))
        assertTrue(s.fieldValues.containsKey("amount"))
        // Echo runtime returns empty text and 0 for number — both validate.
        assertTrue(s.valid)
        assertEquals(1.0, s.passRate, 0.0001)
    }

    @Test
    fun `surfaces validation errors when payload is malformed`() = runTest {
        val malformed = object : ExtractorRuntime {
            override val id = "bad"
            override val isReady = true
            override suspend fun extract(manifest: SkillManifest, input: ExtractorInput) =
                ExtractorOutput.Json("""{"merchant":"X","amount":"not a number"}""")
        }
        val report = harness(malformed).run(validDraft(), "x")
        assertTrue(report is TestReport.Success)
        val s = report as TestReport.Success
        assertFalse(s.valid)
        assertTrue(s.validationErrors.any { it.field == "amount" })
    }

    @Test
    fun `runtime failure becomes RuntimeFailed report`() = runTest {
        val failing = object : ExtractorRuntime {
            override val id = "fail"
            override val isReady = true
            override suspend fun extract(manifest: SkillManifest, input: ExtractorInput) =
                ExtractorOutput.Failure("model unavailable")
        }
        val report = harness(failing).run(validDraft(), "x")
        assertTrue(report is TestReport.RuntimeFailed)
        assertEquals("model unavailable", (report as TestReport.RuntimeFailed).reason)
    }

    @Test
    fun `caps sample input to manifest limits`() = runTest {
        var seenLength = -1
        val capturing = object : ExtractorRuntime {
            override val id = "cap"
            override val isReady = true
            override suspend fun extract(manifest: SkillManifest, input: ExtractorInput): ExtractorOutput {
                seenLength = input.text.length
                return ExtractorOutput.Json("""{"merchant":"","amount":0}""")
            }
        }
        val draft = validDraft().copy(
            limits = DraftLimits(maxInputChars = "10"),
        )
        harness(capturing).run(draft, "0123456789abcdefg")
        assertEquals(10, seenLength)
    }
}
