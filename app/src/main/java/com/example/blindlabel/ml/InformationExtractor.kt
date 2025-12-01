package com.example.blindlabel.ml

import com.example.blindlabel.data.model.AllergenProfile
import com.example.blindlabel.data.model.NutritionInfo
import com.example.blindlabel.data.model.ScanResult

/**
 * Extracts structured information from raw OCR text.
 * Handles ingredients, nutrition facts, allergens, and expiry dates.
 */
class InformationExtractor {
    
    companion object {
        // Regex patterns for section detection
        private val INGREDIENTS_PATTERN = Regex(
            """(?:ingredients?|contains)\s*[:\s]*(.+?)(?=nutrition|allergen|warning|best before|use by|expiry|\z)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        
        private val NUTRITION_PATTERN = Regex(
            """(?:nutrition(?:al)?\s*(?:facts?|information)?|per\s+serving)[:\s]*(.+?)(?=ingredients|allergen|warning|best before|use by|expiry|\z)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        
        private val EXPIRY_PATTERN = Regex(
            """(?:best\s*before|use\s*by|expiry|exp|bb)[:\s]*(\d{1,2}[\/\-\.]\d{1,2}[\/\-\.]\d{2,4}|\w+\s*\d{4}|\d{1,2}\s*\w+\s*\d{4})""",
            RegexOption.IGNORE_CASE
        )
        
        private val ALLERGEN_WARNING_PATTERN = Regex(
            """(?:allergen|may\s+contain|warning)[:\s]*(.+?)(?=\.|$)""",
            RegexOption.IGNORE_CASE
        )
        
        // Nutrient extraction patterns
        private val CALORIES_PATTERN = Regex("""(?:calories?|energy)[:\s]*(\d+\.?\d*)\s*(?:kcal|cal)?""", RegexOption.IGNORE_CASE)
        private val PROTEIN_PATTERN = Regex("""protein[:\s]*(\d+\.?\d*)\s*g?""", RegexOption.IGNORE_CASE)
        private val FAT_PATTERN = Regex("""(?:total\s+)?fat[:\s]*(\d+\.?\d*)\s*g?""", RegexOption.IGNORE_CASE)
        private val SUGAR_PATTERN = Regex("""sugar[s]?[:\s]*(\d+\.?\d*)\s*g?""", RegexOption.IGNORE_CASE)
        private val CARBS_PATTERN = Regex("""(?:total\s+)?carbohydrate[s]?[:\s]*(\d+\.?\d*)\s*g?""", RegexOption.IGNORE_CASE)
        private val SODIUM_PATTERN = Regex("""sodium[:\s]*(\d+\.?\d*)\s*(?:mg)?""", RegexOption.IGNORE_CASE)
        private val FIBER_PATTERN = Regex("""(?:dietary\s+)?fiber[:\s]*(\d+\.?\d*)\s*g?""", RegexOption.IGNORE_CASE)
        
        // Harmful ingredients database
        private val HARMFUL_INGREDIENTS = listOf(
            "high fructose corn syrup", "hfcs",
            "monosodium glutamate", "msg",
            "sodium nitrite", "sodium nitrate",
            "bha", "bht",
            "potassium bromate",
            "propyl paraben",
            "artificial color", "artificial flavour", "artificial flavor",
            "aspartame", "saccharin", "sucralose",
            "partially hydrogenated", "trans fat"
        )
    }
    
    /**
     * Extract all information from combined text results.
     */
    fun extract(textResult: CombinedTextResult, allergenProfile: AllergenProfile): ScanResult {
        val allText = textResult.allText
        
        return ScanResult(
            productName = extractProductName(textResult.frontText),
            ingredients = extractIngredients(allText),
            majorIngredients = extractMajorIngredients(allText),
            nutritionInfo = extractNutritionInfo(allText),
            allergenWarnings = extractAllergenWarnings(allText),
            detectedAllergens = allergenProfile.detectAllergens(allText),
            expiryDate = extractExpiryDate(allText),
            usageInstructions = extractUsageInstructions(allText),
            harmfulIngredients = detectHarmfulIngredients(allText),
            rawText = allText
        )
    }
    
    private fun extractProductName(frontText: String): String? {
        // First few lines often contain product name
        val lines = frontText.lines().filter { it.isNotBlank() }
        return lines.firstOrNull()?.take(100)
    }
    
    private fun extractIngredients(text: String): List<String> {
        val match = INGREDIENTS_PATTERN.find(text) ?: return emptyList()
        val ingredientsText = match.groupValues.getOrNull(1) ?: return emptyList()
        
        return ingredientsText
            .replace(Regex("""\([^)]*\)"""), "") // Remove parentheses content
            .split(Regex("""[,;]"""))
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 1 }
    }
    
    private fun extractMajorIngredients(text: String): List<String> {
        return extractIngredients(text).take(5)
    }
    
    private fun extractNutritionInfo(text: String): NutritionInfo {
        return NutritionInfo(
            calories = CALORIES_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { "$it kcal" },
            protein = PROTEIN_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { "$it g" },
            totalFat = FAT_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { "$it g" },
            sugars = SUGAR_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { "$it g" },
            carbohydrates = CARBS_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { "$it g" },
            sodium = SODIUM_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { "$it mg" },
            fiber = FIBER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { "$it g" }
        )
    }
    
    private fun extractAllergenWarnings(text: String): List<String> {
        val match = ALLERGEN_WARNING_PATTERN.find(text) ?: return emptyList()
        return match.groupValues.getOrNull(1)?.split(",")?.map { it.trim() } ?: emptyList()
    }
    
    private fun extractExpiryDate(text: String): String? {
        return EXPIRY_PATTERN.find(text)?.groupValues?.getOrNull(1)
    }
    
    private fun extractUsageInstructions(text: String): String? {
        val patterns = listOf(
            Regex("""(?:refrigerate|keep refrigerated|store in|keep in)[^.]*\.?""", RegexOption.IGNORE_CASE),
            Regex("""(?:use within|consume within)[^.]*\.?""", RegexOption.IGNORE_CASE),
            Regex("""(?:after opening)[^.]*\.?""", RegexOption.IGNORE_CASE)
        )
        return patterns.firstNotNullOfOrNull { it.find(text)?.value?.trim() }
    }
    
    private fun detectHarmfulIngredients(text: String): List<String> {
        val lowerText = text.lowercase()
        return HARMFUL_INGREDIENTS.filter { lowerText.contains(it) }
    }
}

