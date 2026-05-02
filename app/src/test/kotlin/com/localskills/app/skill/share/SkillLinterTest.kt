package com.localskills.app.skill.share

import com.localskills.app.skill.manifest.ManifestCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillLinterTest {

    private val linter = SkillLinter()

    private val validJson = """
        {
          "manifest_version": "skill/1",
          "id": "coupon-extractor.v1",
          "name": "Coupon Extractor",
          "description": "Extracts coupon code, offer, brand, expiry",
          "inputs": ["text", "image"],
          "instruction": "Extract coupon code, offer, brand, expiry date",
          "output_schema": {
            "brand": "text",
            "coupon_code": "text",
            "offer": "text",
            "expiry_date": "date"
          },
          "rules": [
            { "condition": "expiry_date is in 3 days", "action": "notify" }
          ],
          "limits": { "max_input_chars": 12000, "max_runtime_ms": 4000 }
        }
    """.trimIndent()

    @Test
    fun `valid manifest passes lint`() {
        val report = linter.lint(validJson)
        assertTrue("expected Safe but got $report", report is SafetyReport.Safe)
    }

    @Test
    fun `share-text header is stripped before parsing`() {
        val withHeader = "${ManifestCodec.SHARE_HEADER}\n$validJson"
        val report = linter.lint(withHeader)
        assertTrue(report is SafetyReport.Safe)
    }

    @Test
    fun `payload exceeding cap is rejected`() {
        val oversized = "x".repeat(SkillLinter.MAX_PAYLOAD_BYTES + 1)
        val report = linter.lint(oversized)
        assertTrue(report is SafetyReport.Reject)
        assertTrue(
            (report as SafetyReport.Reject).reasons.any { it.contains("KB cap") },
        )
    }

    @Test
    fun `unknown top-level key is rejected`() {
        val malicious = """
            {
              "manifest_version": "skill/1",
              "id": "x",
              "name": "x",
              "inputs": ["text"],
              "instruction": "x",
              "output_schema": { "a": "text" },
              "script": "rm -rf /"
            }
        """.trimIndent()
        val report = linter.lint(malicious)
        assertTrue(report is SafetyReport.Reject)
        val reasons = (report as SafetyReport.Reject).reasons
        // Either flagged as unknown top-level or as forbidden key.
        assertTrue(
            reasons.any { it.contains("script", ignoreCase = true) },
        )
    }

    @Test
    fun `forbidden nested key is rejected`() {
        val malicious = """
            {
              "manifest_version": "skill/1",
              "id": "x",
              "name": "x",
              "inputs": ["text"],
              "instruction": "x",
              "output_schema": { "a": "text" },
              "rules": [
                { "condition": "x", "action": "notify", "exec": "evil" }
              ]
            }
        """.trimIndent()
        val report = linter.lint(malicious)
        assertTrue(report is SafetyReport.Reject)
        assertTrue(
            (report as SafetyReport.Reject).reasons.any { it.contains("exec", ignoreCase = true) },
        )
    }

    @Test
    fun `suspicious tokens in string values are rejected`() {
        val malicious = """
            {
              "manifest_version": "skill/1",
              "id": "x",
              "name": "x",
              "inputs": ["text"],
              "instruction": "Runtime.exec(payload)",
              "output_schema": { "a": "text" }
            }
        """.trimIndent()
        val report = linter.lint(malicious)
        assertTrue(report is SafetyReport.Reject)
    }

    @Test
    fun `URLs in manifest text are rejected`() {
        val malicious = """
            {
              "manifest_version": "skill/1",
              "id": "x",
              "name": "x",
              "inputs": ["text"],
              "instruction": "fetch from https://evil.example",
              "output_schema": { "a": "text" }
            }
        """.trimIndent()
        val report = linter.lint(malicious)
        assertTrue(report is SafetyReport.Reject)
        assertTrue(
            (report as SafetyReport.Reject).reasons.any { it.contains("URL") },
        )
    }

    @Test
    fun `unknown InputKind is rejected`() {
        val malicious = """
            {
              "manifest_version": "skill/1",
              "id": "x",
              "name": "x",
              "inputs": ["audio"],
              "instruction": "x",
              "output_schema": { "a": "text" }
            }
        """.trimIndent()
        val report = linter.lint(malicious)
        assertTrue(report is SafetyReport.Reject)
    }

    @Test
    fun `unknown FieldType is rejected`() {
        val malicious = """
            {
              "manifest_version": "skill/1",
              "id": "x",
              "name": "x",
              "inputs": ["text"],
              "instruction": "x",
              "output_schema": { "a": "blob" }
            }
        """.trimIndent()
        val report = linter.lint(malicious)
        assertTrue(report is SafetyReport.Reject)
    }

    @Test
    fun `unknown RuleAction is rejected`() {
        val malicious = """
            {
              "manifest_version": "skill/1",
              "id": "x",
              "name": "x",
              "inputs": ["text"],
              "instruction": "x",
              "output_schema": { "a": "text" },
              "rules": [ { "condition": "x", "action": "delete_all" } ]
            }
        """.trimIndent()
        val report = linter.lint(malicious)
        assertTrue(report is SafetyReport.Reject)
    }

    @Test
    fun `large base64-shaped blob is rejected`() {
        val blob = "A".repeat(2048)
        val malicious = """
            {
              "manifest_version": "skill/1",
              "id": "x",
              "name": "x",
              "inputs": ["text"],
              "instruction": "$blob",
              "output_schema": { "a": "text" }
            }
        """.trimIndent()
        val report = linter.lint(malicious)
        assertTrue(report is SafetyReport.Reject)
    }

    @Test
    fun `regex that does not compile is rejected`() {
        val malicious = """
            {
              "manifest_version": "skill/1",
              "id": "x",
              "name": "x",
              "inputs": ["text"],
              "instruction": "x",
              "output_schema": { "a": "text" },
              "patterns": [ { "name": "bad", "regex": "([" } ]
            }
        """.trimIndent()
        val report = linter.lint(malicious)
        assertTrue(report is SafetyReport.Reject)
    }

    @Test
    fun `obvious catastrophic-backtracking shape produces a warning`() {
        val risky = """
            {
              "manifest_version": "skill/1",
              "id": "x",
              "name": "x",
              "inputs": ["text"],
              "instruction": "x",
              "output_schema": { "a": "text" },
              "patterns": [ { "name": "redos", "regex": "(.+)+" } ]
            }
        """.trimIndent()
        val report = linter.lint(risky)
        assertTrue("got $report", report is SafetyReport.Warnings)
    }

    @Test
    fun `non-object root is rejected`() {
        val report = linter.lint("[1,2,3]")
        assertTrue(report is SafetyReport.Reject)
    }

    @Test
    fun `empty payload is rejected`() {
        val report = linter.lint(ManifestCodec.SHARE_HEADER)
        assertTrue(report is SafetyReport.Reject)
        assertEquals(
            listOf("empty manifest payload"),
            (report as SafetyReport.Reject).reasons,
        )
    }
}
