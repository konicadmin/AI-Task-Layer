package com.localskills.app.engine.builder

import com.localskills.app.skill.manifest.FieldType
import com.localskills.app.skill.manifest.InputKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftToManifestTest {

    private val sut = DraftToManifest()

    private fun valid(): ManifestDraft = ManifestDraft(
        id = "expense.v1",
        name = "Expense",
        description = "Pull merchant + amount",
        inputs = setOf(InputKind.TEXT),
        instruction = "Extract merchant, amount, date",
        outputFields = listOf(
            DraftField("merchant", FieldType.TEXT),
            DraftField("amount", FieldType.NUMBER),
        ),
    )

    @Test
    fun `builds a canonical manifest from a valid draft`() {
        val manifest = sut.build(valid()).getOrThrow()
        assertEquals("expense.v1", manifest.id)
        assertEquals("Expense", manifest.name)
        assertEquals(listOf(InputKind.TEXT), manifest.inputs)
        assertEquals(FieldType.NUMBER, manifest.outputSchema["amount"])
    }

    @Test
    fun `auto-derives id from name when blank`() {
        val draft = valid().copy(id = "")
        val manifest = sut.build(draft).getOrThrow()
        assertEquals("expense.v1", manifest.id)
    }

    @Test
    fun `flags blank name and instruction`() {
        val errors = sut.validate(valid().copy(name = "", instruction = ""))
        val sections = errors.map { it.section }.toSet()
        assertTrue(DraftSection.NAME in sections)
        assertTrue(DraftSection.INSTRUCTION in sections)
    }

    @Test
    fun `requires at least one input`() {
        val errors = sut.validate(valid().copy(inputs = emptySet()))
        assertTrue(errors.any { it.section == DraftSection.INPUTS })
    }

    @Test
    fun `requires at least one output field`() {
        val errors = sut.validate(valid().copy(outputFields = emptyList()))
        assertTrue(errors.any { it.section == DraftSection.OUTPUT_FIELDS })
    }

    @Test
    fun `flags non snake_case field names`() {
        val draft = valid().copy(
            outputFields = listOf(DraftField("MerchantName", FieldType.TEXT)),
        )
        val errors = sut.validate(draft)
        assertTrue(errors.any { it.section == DraftSection.OUTPUT_FIELDS })
    }

    @Test
    fun `flags duplicate field names`() {
        val draft = valid().copy(
            outputFields = listOf(
                DraftField("merchant", FieldType.TEXT),
                DraftField("merchant", FieldType.TEXT),
            ),
        )
        val errors = sut.validate(draft)
        assertTrue(errors.any {
            it.section == DraftSection.OUTPUT_FIELDS && it.message.contains("Duplicate")
        })
    }

    @Test
    fun `flags invalid id slug`() {
        val errors = sut.validate(valid().copy(id = "Bad ID!!"))
        assertTrue(errors.any { it.section == DraftSection.ID })
    }

    @Test
    fun `flags invalid regex in pattern`() {
        val draft = valid().copy(
            patterns = listOf(DraftPattern("amt", "(unbalanced")),
        )
        val errors = sut.validate(draft)
        assertTrue(errors.any { it.section == DraftSection.PATTERNS })
    }

    @Test
    fun `coerces blank limits to defaults`() {
        val draft = valid().copy(
            limits = DraftLimits(
                maxInputChars = "",
                maxOcrPages = "",
                maxModelTokens = "",
                maxRuntimeMs = "",
            ),
        )
        val manifest = sut.build(draft).getOrThrow()
        // Limits default to non-zero positives.
        assertTrue(manifest.limits.maxInputChars > 0)
        assertTrue(manifest.limits.maxRuntimeMs > 0)
    }

    @Test
    fun `failure carries DraftValidationException`() {
        val res = sut.build(ManifestDraft())
        assertNull(res.getOrNull())
        val ex = res.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex is DraftValidationException)
        assertTrue((ex as DraftValidationException).errors.isNotEmpty())
    }
}
