package com.localskills.app.engine

import com.localskills.app.skill.manifest.InputKind

/**
 * Standardised envelope for everything fed into a skill run. The collector
 * resolves shared text, paste, image picker, and camera capture into this
 * type so downstream stages don't care about the source.
 */
data class CaptureInput(
    val kind: InputKind,
    val mimeType: String,
    val rawText: String? = null,
    val artifactPath: String? = null,
    val sourceAppHint: String? = null,
    val localeHint: String = "en-IN",
    val timezone: String = "Asia/Kolkata",
)
