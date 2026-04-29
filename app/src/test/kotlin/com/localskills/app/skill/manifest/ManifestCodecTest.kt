package com.localskills.app.skill.manifest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestCodecTest {

    private val sample = SkillManifest(
        id = "coupon-extractor.v1",
        name = "Coupon Extractor",
        description = "Extracts coupon code, offer, brand, expiry",
        inputs = listOf(InputKind.TEXT, InputKind.IMAGE),
        instruction = "Extract coupon code, offer, brand, expiry date",
        outputSchema = linkedMapOf(
            "brand" to FieldType.TEXT,
            "coupon_code" to FieldType.TEXT,
            "offer" to FieldType.TEXT,
            "expiry_date" to FieldType.DATE,
        ),
        rules = listOf(
            RuleSpec(condition = "expiry_date is in 3 days", action = RuleAction.NOTIFY),
        ),
    )

    @Test
    fun `round-trips through share text format`() {
        val text = ManifestCodec.encodeShareText(sample)
        assertTrue(text.startsWith(ManifestCodec.SHARE_HEADER))
        val decoded = ManifestCodec.decode(text).getOrThrow()
        assertEquals(sample, decoded)
    }

    @Test
    fun `accepts plain JSON without header`() {
        val json = ManifestCodec.encodeJson(sample)
        val decoded = ManifestCodec.decode(json).getOrThrow()
        assertEquals(sample, decoded)
    }

    @Test
    fun `rejects empty payload`() {
        val result = ManifestCodec.decode(ManifestCodec.SHARE_HEADER)
        assertTrue(result.isFailure)
    }

    @Test
    fun `rejects malformed JSON with friendly error`() {
        val result = ManifestCodec.decode("AOSL-SKILL/1\n{not json")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
