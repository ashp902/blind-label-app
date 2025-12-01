package com.example.blindlabel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.blindlabel.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "blind_label_prefs")

class PreferencesRepository(private val context: Context) {
    
    companion object {
        // Onboarding
        private val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        
        // TTS Settings
        private val SPEECH_RATE = floatPreferencesKey("speech_rate")
        private val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        private val AUTO_PLAY_TTS = booleanPreferencesKey("auto_play_tts")
        
        // Read preferences
        private val READ_ALLERGEN_ALERT = booleanPreferencesKey("read_allergen_alert")
        private val READ_PRODUCT_NAME = booleanPreferencesKey("read_product_name")
        private val READ_INGREDIENTS = booleanPreferencesKey("read_ingredients")
        private val READ_MAJOR_INGREDIENTS_ONLY = booleanPreferencesKey("read_major_ingredients_only")
        private val READ_NUTRITION = booleanPreferencesKey("read_nutrition")
        private val READ_CALORIES = booleanPreferencesKey("read_calories")
        private val READ_PROTEIN = booleanPreferencesKey("read_protein")
        private val READ_FATS = booleanPreferencesKey("read_fats")
        private val READ_SUGARS = booleanPreferencesKey("read_sugars")
        private val READ_EXPIRY_DATE = booleanPreferencesKey("read_expiry_date")
        private val READ_USAGE_INSTRUCTIONS = booleanPreferencesKey("read_usage_instructions")
        private val READ_HARMFUL_INGREDIENTS = booleanPreferencesKey("read_harmful_ingredients")
        
        // Display Settings
        private val FONT_SIZE = stringPreferencesKey("font_size")
        private val HIGH_CONTRAST_MODE = booleanPreferencesKey("high_contrast_mode")
        
        // Gemini
        private val ENABLE_GEMINI_QA = booleanPreferencesKey("enable_gemini_qa")
        private val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        
        // Allergen Profile
        private val SELECTED_ALLERGENS = stringSetPreferencesKey("selected_allergens")
        private val CUSTOM_ALLERGENS = stringPreferencesKey("custom_allergens")
    }
    
    val userPreferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            hasCompletedOnboarding = prefs[HAS_COMPLETED_ONBOARDING] ?: false,
            speechRate = prefs[SPEECH_RATE] ?: 1.0f,
            speechPitch = prefs[SPEECH_PITCH] ?: 1.0f,
            autoPlayTts = prefs[AUTO_PLAY_TTS] ?: true,
            readAllergenAlert = prefs[READ_ALLERGEN_ALERT] ?: true,
            readProductName = prefs[READ_PRODUCT_NAME] ?: true,
            readIngredients = prefs[READ_INGREDIENTS] ?: true,
            readMajorIngredientsOnly = prefs[READ_MAJOR_INGREDIENTS_ONLY] ?: true,
            readNutrition = prefs[READ_NUTRITION] ?: true,
            readCalories = prefs[READ_CALORIES] ?: true,
            readProtein = prefs[READ_PROTEIN] ?: true,
            readFats = prefs[READ_FATS] ?: true,
            readSugars = prefs[READ_SUGARS] ?: true,
            readExpiryDate = prefs[READ_EXPIRY_DATE] ?: true,
            readUsageInstructions = prefs[READ_USAGE_INSTRUCTIONS] ?: true,
            readHarmfulIngredients = prefs[READ_HARMFUL_INGREDIENTS] ?: true,
            fontSize = FontSize.valueOf(prefs[FONT_SIZE] ?: FontSize.LARGE.name),
            highContrastMode = prefs[HIGH_CONTRAST_MODE] ?: true,
            enableGeminiQA = prefs[ENABLE_GEMINI_QA] ?: true,
            geminiApiKey = prefs[GEMINI_API_KEY] ?: ""
        )
    }
    
    val allergenProfile: Flow<AllergenProfile> = context.dataStore.data.map { prefs ->
        val selectedNames = prefs[SELECTED_ALLERGENS] ?: emptySet()
        val selectedAllergens = selectedNames.mapNotNull { name ->
            try { CommonAllergen.valueOf(name) } catch (e: Exception) { null }
        }.toSet()
        
        val customString = prefs[CUSTOM_ALLERGENS] ?: ""
        val customList = if (customString.isBlank()) emptyList() 
                        else customString.split(",").map { it.trim() }.filter { it.isNotBlank() }
        
        AllergenProfile(selectedAllergens, customList)
    }
    
    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[HAS_COMPLETED_ONBOARDING] = completed }
    }
    
    suspend fun setSpeechRate(rate: Float) {
        context.dataStore.edit { it[SPEECH_RATE] = rate.coerceIn(0.5f, 2.0f) }
    }
    
    suspend fun updateAllergenProfile(profile: AllergenProfile) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_ALLERGENS] = profile.selectedCommonAllergens.map { it.name }.toSet()
            prefs[CUSTOM_ALLERGENS] = profile.customAllergens.joinToString(",")
        }
    }
    
    suspend fun setGeminiApiKey(apiKey: String) {
        context.dataStore.edit { it[GEMINI_API_KEY] = apiKey }
    }
    
    suspend fun setFontSize(fontSize: FontSize) {
        context.dataStore.edit { it[FONT_SIZE] = fontSize.name }
    }
}

