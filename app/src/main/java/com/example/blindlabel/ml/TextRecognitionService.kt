package com.example.blindlabel.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Service for extracting text from images using ML Kit.
 * Handles both front (product name) and back (ingredients/nutrition) label images.
 */
class TextRecognitionService {
    
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    /**
     * Extract text from a bitmap image.
     * @param bitmap The image to process
     * @return Extracted text as a string
     */
    suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val extractedText = buildString {
                    for (block in visionText.textBlocks) {
                        append(block.text)
                        append("\n")
                    }
                }
                continuation.resume(extractedText.trim())
            }
            .addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
    }
    
    /**
     * Extract text from multiple images and combine results.
     * Typically used for front + back label images.
     */
    suspend fun recognizeTextFromImages(bitmaps: List<Bitmap>): CombinedTextResult {
        val results = bitmaps.mapIndexed { index, bitmap ->
            try {
                val text = recognizeText(bitmap)
                TextExtractionResult(
                    imageIndex = index,
                    text = text,
                    isSuccess = true
                )
            } catch (e: Exception) {
                TextExtractionResult(
                    imageIndex = index,
                    text = "",
                    isSuccess = false,
                    error = e.message
                )
            }
        }
        
        return CombinedTextResult(
            frontText = results.getOrNull(0)?.text ?: "",
            backText = results.getOrNull(1)?.text ?: "",
            allText = results.joinToString("\n\n") { it.text },
            results = results
        )
    }
    
    fun close() {
        recognizer.close()
    }
}

/**
 * Result of text extraction from a single image.
 */
data class TextExtractionResult(
    val imageIndex: Int,
    val text: String,
    val isSuccess: Boolean,
    val error: String? = null
)

/**
 * Combined result from processing multiple images.
 */
data class CombinedTextResult(
    val frontText: String,
    val backText: String,
    val allText: String,
    val results: List<TextExtractionResult>
) {
    fun hasText(): Boolean = allText.isNotBlank()
}

