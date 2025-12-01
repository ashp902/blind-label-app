package com.example.blindlabel.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.blindlabel.data.model.ScanResult
import com.example.blindlabel.data.model.TtsSection
import com.example.blindlabel.data.model.TtsState
import com.example.blindlabel.data.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * Service for Text-to-Speech functionality.
 * Handles prioritized reading of scan results with user preferences.
 */
class TextToSpeechService(context: Context) {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    private val _state = MutableStateFlow<TtsState>(TtsState.Initializing)
    val state: StateFlow<TtsState> = _state.asStateFlow()
    
    private var currentSections: List<SpeechSection> = emptyList()
    private var currentSectionIndex = 0
    
    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isInitialized = true
                _state.value = TtsState.Ready
                
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        val section = currentSections.getOrNull(currentSectionIndex)
                        _state.value = TtsState.Speaking(section?.title ?: "")
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        currentSectionIndex++
                        if (currentSectionIndex < currentSections.size) {
                            speakCurrentSection()
                        } else {
                            _state.value = TtsState.Ready
                        }
                    }
                    
                    override fun onError(utteranceId: String?) {
                        _state.value = TtsState.Error("Speech error occurred")
                    }
                })
            } else {
                _state.value = TtsState.Error("TTS initialization failed")
            }
        }
    }
    
    /**
     * Generate speech sections from scan result based on user preferences.
     */
    fun prepareSpeech(result: ScanResult, preferences: UserPreferences): List<SpeechSection> {
        val sections = mutableListOf<SpeechSection>()
        
        // Allergen Alert - ALWAYS FIRST for safety
        if (preferences.readAllergenAlert && result.hasAllergenAlert()) {
            sections.add(SpeechSection(
                type = TtsSection.ALLERGEN_ALERT,
                title = "Allergen Alert",
                content = "Warning! This product contains: ${result.detectedAllergens.joinToString(", ")}"
            ))
        }
        
        // Product Name
        if (preferences.readProductName && result.productName != null) {
            sections.add(SpeechSection(
                type = TtsSection.PRODUCT_NAME,
                title = "Product Name",
                content = "Product: ${result.productName}"
            ))
        }
        
        // Ingredients
        if (preferences.readIngredients) {
            val ingredients = if (preferences.readMajorIngredientsOnly) {
                result.majorIngredients
            } else {
                result.ingredients
            }
            if (ingredients.isNotEmpty()) {
                val label = if (preferences.readMajorIngredientsOnly) "Main ingredients" else "Ingredients"
                sections.add(SpeechSection(
                    type = TtsSection.INGREDIENTS,
                    title = "Ingredients",
                    content = "$label: ${ingredients.joinToString(", ")}"
                ))
            }
        }
        
        // Harmful Ingredients Warning - Read early for safety awareness
        if (preferences.readHarmfulIngredients && result.harmfulIngredients.isNotEmpty()) {
            sections.add(SpeechSection(
                type = TtsSection.HARMFUL_INGREDIENTS,
                title = "Harmful Ingredients",
                content = "Warning! This product contains potentially harmful ingredients: ${result.harmfulIngredients.joinToString(". ")}"
            ))
        }

        // Nutrition Facts - All macros
        if (preferences.readNutrition) {
            val nutritionParts = mutableListOf<String>()
            result.nutritionInfo.servingSize?.let { nutritionParts.add("Serving size: $it") }
            if (preferences.readCalories) result.nutritionInfo.calories?.let { nutritionParts.add("Calories: $it") }
            if (preferences.readFats) {
                result.nutritionInfo.totalFat?.let { nutritionParts.add("Total fat: $it") }
                result.nutritionInfo.saturatedFat?.let { nutritionParts.add("Saturated fat: $it") }
            }
            result.nutritionInfo.carbohydrates?.let { nutritionParts.add("Carbohydrates: $it") }
            if (preferences.readSugars) result.nutritionInfo.sugars?.let { nutritionParts.add("Sugars: $it") }
            result.nutritionInfo.fiber?.let { nutritionParts.add("Fiber: $it") }
            if (preferences.readProtein) result.nutritionInfo.protein?.let { nutritionParts.add("Protein: $it") }
            result.nutritionInfo.sodium?.let { nutritionParts.add("Sodium: $it") }

            if (nutritionParts.isNotEmpty()) {
                sections.add(SpeechSection(
                    type = TtsSection.NUTRITION,
                    title = "Nutrition Facts",
                    content = nutritionParts.joinToString(". ")
                ))
            }
        }
        
        // Expiry Date
        if (preferences.readExpiryDate && result.expiryDate != null) {
            sections.add(SpeechSection(
                type = TtsSection.EXPIRY_DATE,
                title = "Expiry Date",
                content = "Best before: ${result.expiryDate}"
            ))
        }
        
        // Usage Instructions
        if (preferences.readUsageInstructions && result.usageInstructions != null) {
            sections.add(SpeechSection(
                type = TtsSection.USAGE_INSTRUCTIONS,
                title = "Storage Instructions",
                content = result.usageInstructions
            ))
        }

        currentSections = sections
        currentSectionIndex = 0
        return sections
    }
    
    fun speak(sections: List<SpeechSection>, speechRate: Float = 1.0f) {
        currentSections = sections
        currentSectionIndex = 0
        tts?.setSpeechRate(speechRate)
        speakCurrentSection()
    }
    
    private fun speakCurrentSection() {
        val section = currentSections.getOrNull(currentSectionIndex) ?: return
        tts?.speak(section.content, TextToSpeech.QUEUE_FLUSH, null, "section_$currentSectionIndex")
    }
    
    fun pause() { tts?.stop(); _state.value = TtsState.Paused }
    fun resume() { speakCurrentSection() }
    fun stop() { tts?.stop(); currentSectionIndex = 0; _state.value = TtsState.Ready }
    fun skipToNext() { tts?.stop(); currentSectionIndex++; if (currentSectionIndex < currentSections.size) speakCurrentSection() else _state.value = TtsState.Ready }
    fun skipToPrevious() { tts?.stop(); currentSectionIndex = maxOf(0, currentSectionIndex - 1); speakCurrentSection() }
    fun shutdown() { tts?.stop(); tts?.shutdown() }
}

data class SpeechSection(val type: TtsSection, val title: String, val content: String)

