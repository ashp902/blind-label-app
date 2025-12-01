package com.example.blindlabel.data.model

/**
 * User preferences for app behavior and TTS settings.
 */
data class UserPreferences(
    // Onboarding
    val hasCompletedOnboarding: Boolean = false,
    
    // TTS Settings
    val speechRate: Float = 1.0f, // 0.5 to 2.0
    val speechPitch: Float = 1.0f,
    val autoPlayTts: Boolean = true,
    
    // Information to read
    val readAllergenAlert: Boolean = true,
    val readProductName: Boolean = true,
    val readIngredients: Boolean = true,
    val readMajorIngredientsOnly: Boolean = true, // If false, read all ingredients
    val readNutrition: Boolean = true,
    val readCalories: Boolean = true,
    val readProtein: Boolean = true,
    val readFats: Boolean = true,
    val readSugars: Boolean = true,
    val readExpiryDate: Boolean = true,
    val readUsageInstructions: Boolean = true,
    val readHarmfulIngredients: Boolean = true,
    
    // Display Settings
    val fontSize: FontSize = FontSize.LARGE,
    val highContrastMode: Boolean = true,
    
    // Gemini Q&A
    val enableGeminiQA: Boolean = true,
    val geminiApiKey: String = ""
)

/**
 * Font size options for accessibility.
 */
enum class FontSize(val scaleFactor: Float) {
    MEDIUM(1.0f),
    LARGE(1.25f),
    EXTRA_LARGE(1.5f)
}

/**
 * TTS playback state.
 */
sealed class TtsState {
    object Idle : TtsState()
    object Initializing : TtsState()
    object Ready : TtsState()
    data class Speaking(val currentSection: String) : TtsState()
    object Paused : TtsState()
    data class Error(val message: String) : TtsState()
}

/**
 * Sections that can be read by TTS.
 */
enum class TtsSection(val displayName: String) {
    ALLERGEN_ALERT("Allergen Alert"),
    PRODUCT_NAME("Product Name"),
    INGREDIENTS("Ingredients"),
    NUTRITION("Nutrition Facts"),
    EXPIRY_DATE("Expiry Date"),
    USAGE_INSTRUCTIONS("Usage Instructions"),
    HARMFUL_INGREDIENTS("Harmful Ingredients"),
    QA_ANSWER("Answer")
}

