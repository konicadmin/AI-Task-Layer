package com.localskills.app.skill.share

import com.localskills.app.skill.manifest.FieldType
import com.localskills.app.skill.manifest.InputKind
import com.localskills.app.skill.manifest.ManifestCodec
import com.localskills.app.skill.manifest.RuleAction
import com.localskills.app.skill.manifest.RuleSpec
import com.localskills.app.skill.manifest.SkillManifest
import com.localskills.app.skill.share.install.InstallOutcome
import com.localskills.app.skill.share.install.SkillInstaller
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillImporterTest {

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

    private class RecordingInstaller : SkillInstaller {
        val installs = mutableListOf<SkillManifest>()
        var nextOutcome: (SkillManifest) -> InstallOutcome = { InstallOutcome.Installed(it.id) }
        override suspend fun installDisabled(manifest: SkillManifest): InstallOutcome {
            installs += manifest
            return nextOutcome(manifest)
        }
    }

    private fun importer(installer: RecordingInstaller = RecordingInstaller()): Pair<SkillImporter, RecordingInstaller> {
        val imp = SkillImporter(
            linter = SkillLinter(),
            sandbox = SkillSandbox(),
            installer = installer,
        )
        return imp to installer
    }

    @Test
    fun `importing a valid manifest installs it disabled`() = runTest {
        val (importer, installer) = importer()
        val raw = ManifestCodec.encodeShareText(sample)

        val result = importer.import(raw)

        assertTrue("got $result", result is ImportResult.Installed)
        assertEquals(sample.id, (result as ImportResult.Installed).id)
        assertEquals(1, installer.installs.size)
        assertEquals(sample, installer.installs.single())
    }

    @Test
    fun `oversized payload short-circuits before install`() = runTest {
        val (importer, installer) = importer()
        val oversized = "x".repeat(SkillLinter.MAX_PAYLOAD_BYTES + 1)

        val result = importer.import(oversized)

        assertTrue(result is ImportResult.Rejected)
        assertEquals(0, installer.installs.size)
    }

    @Test
    fun `manifest with forbidden key is rejected and never installed`() = runTest {
        val (importer, installer) = importer()
        val malicious = """
            {
              "manifest_version": "skill/1",
              "id": "x",
              "name": "x",
              "inputs": ["text"],
              "instruction": "x",
              "output_schema": { "a": "text" },
              "rules": [ { "condition": "x", "action": "notify", "command": "rm -rf /" } ]
            }
        """.trimIndent()

        val result = importer.import(malicious)

        assertTrue(result is ImportResult.Rejected)
        assertEquals(0, installer.installs.size)
        assertTrue(
            (result as ImportResult.Rejected).reasons.any { it.contains("command", ignoreCase = true) },
        )
    }

    @Test
    fun `installer failure surfaces as Rejected`() = runTest {
        val installer = RecordingInstaller().apply {
            nextOutcome = { InstallOutcome.Failed(reason = "db locked") }
        }
        val (importer, _) = importer(installer)
        val raw = ManifestCodec.encodeShareText(sample)

        val result = importer.import(raw)

        assertTrue(result is ImportResult.Rejected)
        assertTrue(
            (result as ImportResult.Rejected).reasons.any { it.contains("db locked") },
        )
    }

    @Test
    fun `already-installed surfaces distinctly`() = runTest {
        val installer = RecordingInstaller().apply {
            nextOutcome = { InstallOutcome.AlreadyInstalled(it.id) }
        }
        val (importer, _) = importer(installer)
        val raw = ManifestCodec.encodeShareText(sample)

        val result = importer.import(raw)

        assertTrue(result is ImportResult.AlreadyInstalled)
        assertEquals(sample.id, (result as ImportResult.AlreadyInstalled).id)
    }

    @Test
    fun `preview surfaces manifest plus reports without installing`() = runTest {
        val (importer, installer) = importer()
        val raw = ManifestCodec.encodeShareText(sample)

        val preview = importer.preview(raw)

        assertNotNull(preview.manifest)
        assertEquals(sample, preview.manifest)
        assertTrue(preview.safety is SafetyReport.Safe)
        assertTrue(preview.sandbox?.ok == true)
        assertEquals(0, installer.installs.size)
    }
}
