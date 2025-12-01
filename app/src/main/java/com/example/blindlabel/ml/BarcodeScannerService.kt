package com.example.blindlabel.ml

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Service for scanning barcodes from images using ML Kit.
 * Detects EAN-13, UPC-A, and other common food product barcodes.
 */
class BarcodeScannerService {
    
    companion object {
        private const val TAG = "BarcodeScannerService"
    }
    
    private val scanner = BarcodeScanning.getClient()
    
    /**
     * Scans a bitmap image for barcodes.
     * @param bitmap The image to scan
     * @return List of detected barcode values, or empty list if none found
     */
    suspend fun scanForBarcodes(bitmap: Bitmap): List<String> = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val productBarcodes = barcodes
                    .filter { barcode ->
                        // Filter for product barcodes (EAN, UPC)
                        barcode.format in listOf(
                            Barcode.FORMAT_EAN_13,
                            Barcode.FORMAT_EAN_8,
                            Barcode.FORMAT_UPC_A,
                            Barcode.FORMAT_UPC_E
                        )
                    }
                    .mapNotNull { it.rawValue }
                
                Log.d(TAG, "Found ${productBarcodes.size} product barcodes: $productBarcodes")
                cont.resume(productBarcodes)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Barcode scanning failed", e)
                cont.resumeWithException(e)
            }
    }
    
    /**
     * Scans multiple images for barcodes and returns the first valid barcode found.
     * @param bitmaps List of images to scan
     * @return First detected barcode, or null if none found
     */
    suspend fun scanImagesForBarcode(bitmaps: List<Bitmap>): String? {
        for (bitmap in bitmaps) {
            try {
                val barcodes = scanForBarcodes(bitmap)
                if (barcodes.isNotEmpty()) {
                    return barcodes.first()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning image for barcode", e)
            }
        }
        return null
    }
    
    fun close() {
        scanner.close()
    }
}

