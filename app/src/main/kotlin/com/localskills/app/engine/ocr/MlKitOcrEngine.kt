package com.localskills.app.engine.ocr

import android.content.Context
import android.graphics.Rect as AndroidRect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device OCR backed by ML Kit Text Recognition v2.
 *
 * Recognizes Latin and Devanagari scripts, then merges the per-script
 * outputs by best-confidence block. Pixels never leave the device — ML
 * Kit's recognizer runs entirely locally.
 */
@Singleton
class MlKitOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : OcrEngine {

    private val latin: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val devanagari: TextRecognizer by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    override suspend fun recognize(uri: Uri): OcrResult = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, uri)
        val latinResult = runRecognizer(latin, image)
        val devResult = runRecognizer(devanagari, image)
        merge(latinResult, devResult)
    }

    private suspend fun runRecognizer(recognizer: TextRecognizer, image: InputImage): Text =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    private fun merge(latin: Text, devanagari: Text): OcrResult {
        val combined = listOf(latin, devanagari).flatMap { result ->
            result.textBlocks.map { block ->
                OcrBlock(
                    text = block.text,
                    confidence = blockConfidence(block),
                    boundingBox = block.boundingBox?.toEngineRect(),
                )
            }
        }
        // Prefer the recognizer that returned more text — typical pages are
        // dominantly one script and the other recognizer's output is noise.
        val primary = if (latin.text.length >= devanagari.text.length) latin else devanagari
        return OcrResult(fullText = primary.text, blocks = combined)
    }

    private fun blockConfidence(block: Text.TextBlock): Float {
        // ML Kit v2 does not expose a public confidence on TextBlock; use a
        // text-density heuristic so downstream consumers have a monotonic
        // signal to rank blocks. Real per-symbol confidence requires the
        // legacy on-device API.
        val lines = block.lines.size.coerceAtLeast(1)
        val chars = block.text.length
        return (chars.toFloat() / (lines * 60f)).coerceIn(0f, 1f)
    }

    private fun AndroidRect.toEngineRect(): Rect = Rect(left, top, right, bottom)
}
