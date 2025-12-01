package com.example.blindlabel.ai

import android.util.Log
import com.example.blindlabel.data.model.NutritionInfo
import com.example.blindlabel.data.model.ScanResult
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Service for interacting with Google's Gemini AI for:
 * 1. Extracting structured product information from OCR text
 * 2. Identifying harmful ingredients
 * 3. Natural language Q&A about scanned products
 */
class GeminiService(private val apiKey: String) {

    companion object {
        private const val TAG = "GeminiService"
    }

    private val model by lazy {
        GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.3f  // Lower temperature for more consistent extraction
                topK = 40
                topP = 0.95f
                maxOutputTokens = 2048
            }
        )
    }

    /**
     * Extract structured product information from raw OCR text using Gemini.
     * This replaces regex-based parsing with AI-powered extraction.
     */
    suspend fun extractProductInfo(ocrText: String, userAllergens: List<String>): ScanResult = withContext(Dispatchers.IO) {
        try {
            val prompt = buildExtractionPrompt(ocrText, userAllergens)
            val response = model.generateContent(prompt)
            val responseText = response.text ?: throw Exception("Empty response from Gemini")

            Log.d(TAG, "Gemini response: $responseText")

            parseGeminiResponse(responseText, ocrText)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting product info", e)
            // Return a basic result with raw text if Gemini fails
            ScanResult(
                productName = "Could not identify product",
                rawText = ocrText,
                harmfulIngredients = listOf("Analysis failed: ${e.message}")
            )
        }
    }

    private fun buildExtractionPrompt(ocrText: String, userAllergens: List<String>): String {
        val allergensStr = if (userAllergens.isNotEmpty()) {
            userAllergens.joinToString(", ")
        } else {
            "none specified"
        }

        return """
You are a food label analysis AI. Extract structured information from this OCR text of a food product label.

OCR TEXT:
\"\"\"
$ocrText
\"\"\"

USER'S ALLERGENS: $allergensStr

IMPORTANT INSTRUCTIONS:
1. For ALL nutrition values, extract ONLY the NUMERIC VALUE with its unit (e.g., "150 kcal", "8g", "200mg")
2. Do NOT include labels like "per serving" or "Amount per serving" - just the number and unit
3. If a nutrition value is not found or unclear, use null
4. Look for numbers near nutrition labels (Calories, Fat, Protein, etc.)

Analyze the text and respond with ONLY a valid JSON object (no markdown, no explanation) with this exact structure:
{
    "product_name": "extracted product name or null if not found",
    "ingredients": ["ingredient1", "ingredient2", ...],
    "nutrition": {
        "serving_size": "numeric amount with unit, e.g., 1 cup (240ml), 30g, 2 cookies",
        "calories": "ONLY the number, e.g., 150, 200, 90 (no 'kcal' needed)",
        "total_fat": "number with g, e.g., 8g",
        "saturated_fat": "number with g, e.g., 3g",
        "trans_fat": "number with g, e.g., 0g",
        "carbohydrates": "number with g, e.g., 20g",
        "sugars": "number with g, e.g., 12g",
        "fiber": "number with g, e.g., 2g",
        "protein": "number with g, e.g., 5g",
        "sodium": "number with mg, e.g., 150mg",
        "cholesterol": "number with mg, e.g., 10mg"
    },
    "allergen_warnings": ["contains milk", "may contain nuts", ...],
    "detected_user_allergens": ["allergens from user's list found in product"],
    "harmful_ingredients": ["ingredient name: reason it's harmful", ...],
    "expiry_date": "extracted date or null",
    "storage_instructions": "extracted instructions or null"
}

NUTRITION EXTRACTION TIPS:
- Calories often appear as "Calories 150" or "Energy 150kcal" - extract just "150"
- Look for patterns like "Total Fat 8g" - extract "8g"
- Ignore % Daily Value percentages, only extract the actual amounts
- If you see "0g" or "0mg", that's valid - include it

HARMFUL INGREDIENTS GUIDELINES - Flag these if found:
- High fructose corn syrup: linked to obesity and diabetes
- Artificial colors (Red 40, Yellow 5, Blue 1, etc.): may cause hyperactivity
- MSG/Monosodium glutamate: may cause headaches in sensitive individuals
- Sodium nitrite/nitrate: linked to cancer risk when processed
- BHA/BHT: potential carcinogens
- Partially hydrogenated oils: contains trans fats
- Artificial sweeteners (aspartame, sucralose, saccharin): controversial health effects
- Carrageenan: may cause digestive issues
- Potassium bromate: banned in many countries, potential carcinogen
- Propyl paraben: endocrine disruptor

Be thorough in identifying ALL ingredients and ALL nutrition facts. Return ONLY the JSON.
""".trimIndent()
    }

    private fun parseGeminiResponse(responseText: String, originalOcrText: String): ScanResult {
        // Clean up the response - remove markdown code blocks if present
        val cleanedResponse = responseText
            .replace("```json", "")
            .replace("```", "")
            .trim()

        return try {
            val json = JSONObject(cleanedResponse)

            // Parse ingredients list
            val ingredientsList = mutableListOf<String>()
            json.optJSONArray("ingredients")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i)?.takeIf { it.isNotBlank() }?.let { ingredientsList.add(it) }
                }
            }

            // Parse nutrition info - helper function to clean values
            fun cleanNutritionValue(value: String?): String? {
                if (value == null || value == "null" || value.isBlank()) return null
                // Remove any non-numeric prefixes like "per serving" etc
                val cleaned = value.trim()
                // Check if it contains a number
                return if (cleaned.any { it.isDigit() }) cleaned else null
            }

            val nutritionJson = json.optJSONObject("nutrition")
            val nutritionInfo = NutritionInfo(
                servingSize = cleanNutritionValue(nutritionJson?.optString("serving_size")),
                calories = cleanNutritionValue(nutritionJson?.optString("calories")),
                totalFat = cleanNutritionValue(nutritionJson?.optString("total_fat")),
                saturatedFat = cleanNutritionValue(nutritionJson?.optString("saturated_fat")),
                transFat = cleanNutritionValue(nutritionJson?.optString("trans_fat")),
                cholesterol = cleanNutritionValue(nutritionJson?.optString("cholesterol")),
                sodium = cleanNutritionValue(nutritionJson?.optString("sodium")),
                carbohydrates = cleanNutritionValue(nutritionJson?.optString("carbohydrates")),
                fiber = cleanNutritionValue(nutritionJson?.optString("fiber")),
                sugars = cleanNutritionValue(nutritionJson?.optString("sugars")),
                protein = cleanNutritionValue(nutritionJson?.optString("protein"))
            )

            // Parse allergen warnings
            val allergenWarnings = mutableListOf<String>()
            json.optJSONArray("allergen_warnings")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i)?.takeIf { it.isNotBlank() }?.let { allergenWarnings.add(it) }
                }
            }

            // Parse detected user allergens
            val detectedAllergens = mutableListOf<String>()
            json.optJSONArray("detected_user_allergens")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i)?.takeIf { it.isNotBlank() }?.let { detectedAllergens.add(it) }
                }
            }

            // Parse harmful ingredients
            val harmfulIngredients = mutableListOf<String>()
            json.optJSONArray("harmful_ingredients")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i)?.takeIf { it.isNotBlank() }?.let { harmfulIngredients.add(it) }
                }
            }

            ScanResult(
                productName = json.optString("product_name")?.takeIf { it != "null" && it.isNotBlank() },
                ingredients = ingredientsList,
                majorIngredients = ingredientsList.take(5),
                nutritionInfo = nutritionInfo,
                allergenWarnings = allergenWarnings,
                detectedAllergens = detectedAllergens,
                expiryDate = json.optString("expiry_date")?.takeIf { it != "null" && it.isNotBlank() },
                usageInstructions = json.optString("storage_instructions")?.takeIf { it != "null" && it.isNotBlank() },
                harmfulIngredients = harmfulIngredients,
                rawText = originalOcrText
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Gemini JSON response", e)
            ScanResult(
                productName = "Parse error",
                rawText = originalOcrText,
                harmfulIngredients = listOf("Failed to parse: ${e.message}")
            )
        }
    }

    /**
     * Ask a follow-up question about a scanned product.
     */
    suspend fun askQuestion(question: String, scanResult: ScanResult): String = withContext(Dispatchers.IO) {
        try {
            val context = buildProductContext(scanResult)
            val prompt = """
You are a helpful food nutrition assistant for visually impaired users.

PRODUCT INFORMATION:
$context

USER QUESTION: $question

Provide a clear, concise answer (under 100 words) suitable for text-to-speech.
If the question cannot be answered from the available information, say so clearly.
""".trimIndent()

            val response = model.generateContent(prompt)
            response.text ?: "I couldn't generate a response. Please try again."
        } catch (e: Exception) {
            "Error: ${e.message ?: "Failed to get response from AI"}"
        }
    }

    private fun buildProductContext(result: ScanResult): String {
        return buildString {
            result.productName?.let { appendLine("Product: $it") }
            if (result.ingredients.isNotEmpty()) {
                appendLine("Ingredients: ${result.ingredients.joinToString(", ")}")
            }
            appendLine("Nutrition Facts:")
            result.nutritionInfo.servingSize?.let { appendLine("  Serving Size: $it") }
            result.nutritionInfo.calories?.let { appendLine("  Calories: $it") }
            result.nutritionInfo.protein?.let { appendLine("  Protein: $it") }
            result.nutritionInfo.totalFat?.let { appendLine("  Total Fat: $it") }
            result.nutritionInfo.saturatedFat?.let { appendLine("  Saturated Fat: $it") }
            result.nutritionInfo.carbohydrates?.let { appendLine("  Carbohydrates: $it") }
            result.nutritionInfo.sugars?.let { appendLine("  Sugars: $it") }
            result.nutritionInfo.fiber?.let { appendLine("  Fiber: $it") }
            result.nutritionInfo.sodium?.let { appendLine("  Sodium: $it") }
            if (result.allergenWarnings.isNotEmpty()) {
                appendLine("Allergen Warnings: ${result.allergenWarnings.joinToString(", ")}")
            }
            if (result.harmfulIngredients.isNotEmpty()) {
                appendLine("Harmful Ingredients: ${result.harmfulIngredients.joinToString(", ")}")
            }
            result.expiryDate?.let { appendLine("Expiry: $it") }
        }
    }

    /**
     * Suggested questions based on the scan result.
     */
    fun getSuggestedQuestions(result: ScanResult): List<String> {
        val questions = mutableListOf<String>()

        questions.add("Is this product healthy for me?")

        if (result.harmfulIngredients.isNotEmpty()) {
            questions.add("Tell me more about the harmful ingredients")
        }

        if (result.nutritionInfo.sugars != null) {
            questions.add("Is this safe for diabetics?")
        }

        if (result.ingredients.isNotEmpty()) {
            questions.add("Is this vegan friendly?")
            questions.add("Is this keto friendly?")
        }

        return questions.take(5)
    }
}

/**
 * Result of an AI query.
 */
sealed class AiQueryResult {
    data class Success(val answer: String) : AiQueryResult()
    data class Error(val message: String) : AiQueryResult()
    object Loading : AiQueryResult()
}

