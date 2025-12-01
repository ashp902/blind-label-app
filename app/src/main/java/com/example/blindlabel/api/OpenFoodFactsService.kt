package com.example.blindlabel.api

import android.util.Log
import com.example.blindlabel.data.model.NutritionInfo
import com.example.blindlabel.data.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service for fetching product information from Open Food Facts API.
 * https://openfoodfacts.github.io/openfoodfacts-server/api/
 */
class OpenFoodFactsService {
    
    companion object {
        private const val TAG = "OpenFoodFactsService"
        private const val BASE_URL = "https://world.openfoodfacts.org/api/v2/product"
        private const val USER_AGENT = "BlindLabel/1.0 (Android; blindlabel-app)"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    /**
     * Fetches product information by barcode.
     * @param barcode The product barcode (EAN-13, UPC-A, etc.)
     * @param userAllergens List of user's allergens to check against
     * @return ScanResult if product found, null otherwise
     */
    suspend fun getProductByBarcode(barcode: String, userAllergens: List<String>): ScanResult? = 
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/$barcode.json"
                Log.d(TAG, "Fetching product: $url")
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    Log.e(TAG, "API request failed: ${response.code}")
                    return@withContext null
                }
                
                val body = response.body?.string() ?: return@withContext null
                parseProductResponse(body, userAllergens)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching product", e)
                null
            }
        }
    
    private fun parseProductResponse(jsonString: String, userAllergens: List<String>): ScanResult? {
        try {
            val json = JSONObject(jsonString)
            
            // Check if product was found
            val status = json.optInt("status", 0)
            if (status != 1) {
                Log.d(TAG, "Product not found in database")
                return null
            }
            
            val product = json.optJSONObject("product") ?: return null
            
            // Extract product name
            val productName = product.optString("product_name", "")
                .ifEmpty { product.optString("product_name_en", "Unknown Product") }
            
            // Extract ingredients
            val ingredientsText = product.optString("ingredients_text", "")
                .ifEmpty { product.optString("ingredients_text_en", "") }
            val ingredients = parseIngredientsList(ingredientsText)
            
            // Extract nutrition facts
            val nutriments = product.optJSONObject("nutriments")
            val nutritionInfo = parseNutritionInfo(nutriments, product)

            // Extract allergens from API
            val allergensFromApi = extractAllergens(product)

            // Check for user allergens
            val allergenWarnings = checkUserAllergens(
                ingredientsText.lowercase(),
                allergensFromApi,
                userAllergens
            )

            // Identify harmful ingredients (common additives)
            val harmfulIngredients = identifyHarmfulIngredients(ingredientsText)

            Log.d(TAG, "Successfully parsed product: $productName")

            return ScanResult(
                productName = productName,
                ingredients = ingredients,
                nutritionInfo = nutritionInfo,
                allergenWarnings = allergenWarnings,
                harmfulIngredients = harmfulIngredients,
                rawText = ingredientsText
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing product response", e)
            return null
        }
    }
    
    private fun parseIngredientsList(ingredientsText: String): List<String> {
        if (ingredientsText.isBlank()) return emptyList()

        // Split by common delimiters and clean up
        return ingredientsText
            .replace(Regex("\\([^)]*\\)"), "") // Remove parenthetical info
            .split(Regex("[,;]"))
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 1 }
            .take(30) // Limit to 30 ingredients
    }

    private fun parseNutritionInfo(
        nutriments: JSONObject?,
        product: JSONObject
    ): NutritionInfo {
        if (nutriments == null) return NutritionInfo()

        // Serving size
        val servingSize = product.optString("serving_size", "").ifBlank { null }

        // Helper function to get nutrient value with unit
        fun getNutrient(key: String, unit: String): String? {
            val value = nutriments.optDouble(key, Double.NaN)
            return if (!value.isNaN()) formatNutrientValue(value, unit) else null
        }

        return NutritionInfo(
            servingSize = servingSize,
            calories = getNutrient("energy-kcal_100g", "kcal"),
            totalFat = getNutrient("fat_100g", "g"),
            saturatedFat = getNutrient("saturated-fat_100g", "g"),
            transFat = getNutrient("trans-fat_100g", "g"),
            cholesterol = getNutrient("cholesterol_100g", "mg"),
            sodium = getNutrient("sodium_100g", "mg"),
            carbohydrates = getNutrient("carbohydrates_100g", "g"),
            fiber = getNutrient("fiber_100g", "g"),
            sugars = getNutrient("sugars_100g", "g"),
            protein = getNutrient("proteins_100g", "g")
        )
    }

    private fun formatNutrientValue(value: Double, unit: String): String {
        return when {
            value >= 1 -> "${value.toInt()}$unit"
            value >= 0.1 -> String.format("%.1f%s", value, unit)
            else -> "<0.1$unit"
        }
    }

    private fun extractAllergens(product: JSONObject): List<String> {
        val allergens = mutableListOf<String>()

        // Get allergens from tags
        val allergensTags = product.optJSONArray("allergens_tags")
        if (allergensTags != null) {
            for (i in 0 until allergensTags.length()) {
                val tag = allergensTags.optString(i, "")
                // Tags are like "en:milk", extract the allergen name
                val allergen = tag.substringAfter(":").replace("-", " ")
                if (allergen.isNotBlank()) {
                    allergens.add(allergen)
                }
            }
        }

        // Also check allergens_from_ingredients
        val allergensFromIngredients = product.optString("allergens_from_ingredients", "")
        if (allergensFromIngredients.isNotBlank()) {
            allergensFromIngredients.split(",").forEach {
                val cleaned = it.trim()
                if (cleaned.isNotBlank() && cleaned !in allergens) {
                    allergens.add(cleaned)
                }
            }
        }

        return allergens
    }

    private fun checkUserAllergens(
        ingredientsLower: String,
        apiAllergens: List<String>,
        userAllergens: List<String>
    ): List<String> {
        val warnings = mutableListOf<String>()

        for (userAllergen in userAllergens) {
            val allergenLower = userAllergen.lowercase()

            // Check if allergen is in API allergens list
            val inApiList = apiAllergens.any { it.lowercase().contains(allergenLower) }

            // Check if allergen is mentioned in ingredients
            val inIngredients = ingredientsLower.contains(allergenLower)

            if (inApiList || inIngredients) {
                warnings.add("Contains $userAllergen")
            }
        }

        return warnings
    }

    private fun identifyHarmfulIngredients(ingredientsText: String): List<String> {
        val harmful = mutableListOf<String>()
        val ingredientsLower = ingredientsText.lowercase()

        val harmfulPatterns = mapOf(
            "high fructose corn syrup" to "linked to obesity and metabolic issues",
            "monosodium glutamate" to "may cause headaches in sensitive individuals",
            "msg" to "may cause headaches in sensitive individuals",
            "sodium nitrite" to "may form carcinogenic compounds",
            "sodium nitrate" to "may form carcinogenic compounds",
            "bha" to "possible carcinogen",
            "bht" to "possible carcinogen",
            "aspartame" to "controversial artificial sweetener",
            "sucralose" to "artificial sweetener with debated effects",
            "red 40" to "artificial color linked to hyperactivity",
            "yellow 5" to "artificial color linked to hyperactivity",
            "yellow 6" to "artificial color linked to hyperactivity",
            "blue 1" to "artificial color",
            "partially hydrogenated" to "contains trans fats",
            "hydrogenated oil" to "may contain trans fats"
        )

        for ((pattern, reason) in harmfulPatterns) {
            if (ingredientsLower.contains(pattern)) {
                harmful.add("$pattern: $reason")
            }
        }

        return harmful
    }
}
