package com.localskills.app.engine.validation

import com.localskills.app.skill.manifest.FieldType
import com.localskills.app.skill.manifest.InputKind
import com.localskills.app.skill.manifest.SkillManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaValidatorTest {

    private val validator = SchemaValidator()

    private val manifest = SkillManifest(
        id = "expense.v1",
        name = "Expense",
        inputs = listOf(InputKind.TEXT),
        instruction = "Extract expense",
        outputSchema = linkedMapOf(
            "merchant" to FieldType.TEXT,
            "amount" to FieldType.NUMBER,
            "txn_date" to FieldType.DATE,
            "autopay" to FieldType.BOOLEAN,
        ),
    )

    @Test
    fun `accepts well-formed payload`() {
        val payload = """
            {
              "merchant": "Indian Oil",
              "amount": 845.20,
              "txn_date": "2026-04-28",
              "autopay": false
            }
        """.trimIndent()
        val report = validator.validate(manifest, payload)
        assertTrue(report.errors.toString(), report.ok)
        assertEquals(1.0, report.passRate, 0.0001)
    }

    @Test
    fun `flags missing field and bad date`() {
        val payload = """
            {
              "merchant": "Shop",
              "amount": "not a number",
              "txn_date": "yesterday",
              "autopay": false
            }
        """.trimIndent()
        val report = validator.validate(manifest, payload)
        assertFalse(report.ok)
        val fields = report.errors.map { it.field }.toSet()
        assertTrue(fields.contains("amount"))
        assertTrue(fields.contains("txn_date"))
    }

    @Test
    fun `rejects non-object root`() {
        val report = validator.validate(manifest, "[]")
        assertFalse(report.ok)
        assertEquals(0.0, report.passRate, 0.0001)
    }
}
