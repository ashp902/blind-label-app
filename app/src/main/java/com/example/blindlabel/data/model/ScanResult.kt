package com.example.blindlabel.data.model

import java.util.Date

/**
 * Represents nutrition information extracted from a food label.
 */
data class NutritionInfo(
    val servingSize: String? = null,
    val calories: String? = null,
    val totalFat: String? = null,
    val saturatedFat: String? = null,
    val transFat: String? = null,
    val cholesterol: String? = null,
    val sodium: String? = null,
    val carbohydrates: String? = null,
    val fiber: String? = null,
    val sugars: String? = null,
    val protein: String? = null
)

/**
 * Represents the complete result of scanning a food product label.
 */
data class ScanResult(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Date = Date(),
    val productName: String? = null,
    val ingredients: List<String> = emptyList(),
    val majorIngredients: List<String> = emptyList(),
    val nutritionInfo: NutritionInfo = NutritionInfo(),
    val allergenWarnings: List<String> = emptyList(),
    val detectedAllergens: List<String> = emptyList(),
    val expiryDate: String? = null,
    val usageInstructions: String? = null,
    val harmfulIngredients: List<String> = emptyList(),
    val rawText: String = ""
) {
    /**
     * Check if this product has any allergen alerts for the user.
     */
    fun hasAllergenAlert(): Boolean = detectedAllergens.isNotEmpty()
    
    /**
     * Get a summary of key information for TTS.
     */
    fun getSummary(): String {
        val parts = mutableListOf<String>()
        
        if (hasAllergenAlert()) {
            parts.add("Warning! This product contains: ${detectedAllergens.joinToString(", ")}")
        }
        
        productName?.let { parts.add("Product: $it") }
        
        if (majorIngredients.isNotEmpty()) {
            parts.add("Main ingredients: ${majorIngredients.joinToString(", ")}")
        }
        
        nutritionInfo.calories?.let { parts.add("Calories: $it") }
        nutritionInfo.protein?.let { parts.add("Protein: $it") }
        nutritionInfo.sugars?.let { parts.add("Sugars: $it") }
        
        expiryDate?.let { parts.add("Expiry date: $it") }
        
        usageInstructions?.let { parts.add("Instructions: $it") }
        
        return parts.joinToString(". ")
    }
}

/**
 * Represents the current state of the scanning process.
 */
sealed class ScanState {
    object Idle : ScanState()
    object CapturingFront : ScanState()
    object CapturingBack : ScanState()
    object Processing : ScanState()
    data class Success(val result: ScanResult) : ScanState()
    data class Error(val message: String) : ScanState()
}

