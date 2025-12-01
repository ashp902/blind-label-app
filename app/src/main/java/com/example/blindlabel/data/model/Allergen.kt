package com.example.blindlabel.data.model

/**
 * Represents common allergens that can be tracked in the user's profile.
 * Based on FDA's major food allergens list.
 */
enum class CommonAllergen(val displayName: String, val keywords: List<String>) {
    MILK("Milk", listOf("milk", "dairy", "lactose", "casein", "whey", "cream", "butter", "cheese", "yogurt")),
    EGGS("Eggs", listOf("egg", "eggs", "albumin", "globulin", "lysozyme", "mayonnaise", "meringue")),
    PEANUTS("Peanuts", listOf("peanut", "peanuts", "groundnut", "arachis")),
    TREE_NUTS("Tree Nuts", listOf("almond", "cashew", "walnut", "pecan", "pistachio", "hazelnut", "macadamia", "brazil nut", "chestnut", "pine nut")),
    SOY("Soy", listOf("soy", "soya", "soybean", "edamame", "tofu", "tempeh", "miso")),
    WHEAT("Wheat", listOf("wheat", "flour", "bread", "pasta", "semolina", "durum", "spelt", "kamut", "farina")),
    FISH("Fish", listOf("fish", "cod", "salmon", "tuna", "halibut", "anchovy", "bass", "catfish", "flounder", "haddock", "perch", "pike", "pollock", "snapper", "sole", "swordfish", "tilapia", "trout")),
    SHELLFISH("Shellfish", listOf("shellfish", "shrimp", "crab", "lobster", "crawfish", "crayfish", "prawn", "scallop", "clam", "mussel", "oyster", "squid", "octopus")),
    SESAME("Sesame", listOf("sesame", "tahini", "halvah", "hummus"))
}

/**
 * User's allergen profile containing both common and custom allergens.
 */
data class AllergenProfile(
    val selectedCommonAllergens: Set<CommonAllergen> = emptySet(),
    val customAllergens: List<String> = emptyList()
) {
    /**
     * Get all allergen keywords for matching against ingredient lists.
     */
    fun getAllKeywords(): List<String> {
        val commonKeywords = selectedCommonAllergens.flatMap { it.keywords }
        val customKeywords = customAllergens.map { it.lowercase() }
        return commonKeywords + customKeywords
    }
    
    /**
     * Check if ingredient text contains any allergens.
     * Returns list of detected allergens.
     */
    fun detectAllergens(ingredientText: String): List<String> {
        val lowerText = ingredientText.lowercase()
        val detectedAllergens = mutableListOf<String>()
        
        // Check common allergens
        selectedCommonAllergens.forEach { allergen ->
            if (allergen.keywords.any { keyword -> lowerText.contains(keyword) }) {
                detectedAllergens.add(allergen.displayName)
            }
        }
        
        // Check custom allergens
        customAllergens.forEach { custom ->
            if (lowerText.contains(custom.lowercase())) {
                detectedAllergens.add(custom)
            }
        }
        
        return detectedAllergens.distinct()
    }
}

