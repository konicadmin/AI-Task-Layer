package com.localskills.app.engine.ocr

import android.net.Uri

/**
 * On-device OCR contract. Implementations should never send pixels off-device.
 * The default implementation will wrap ML Kit Text Recognition v2.
 */
interface OcrEngine {
    suspend fun recognize(uri: Uri): OcrResult
}

data class OcrResult(
    val fullText: String,
    val blocks: List<OcrBlock>,
)

data class OcrBlock(
    val text: String,
    val confidence: Float,
    val boundingBox: Rect?,
)

data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int)
