package com.example.blindlabel.ui.screens.results

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.blindlabel.BuildConfig
import com.example.blindlabel.ai.GeminiService
import com.example.blindlabel.api.OpenFoodFactsService
import com.example.blindlabel.data.model.AllergenProfile
import com.example.blindlabel.data.model.NutritionInfo
import com.example.blindlabel.data.model.ScanResult
import com.example.blindlabel.data.model.ScanState
import com.example.blindlabel.data.model.TtsSection
import com.example.blindlabel.data.model.TtsState
import com.example.blindlabel.data.model.UserPreferences
import com.example.blindlabel.data.repository.PreferencesRepository
import com.example.blindlabel.ml.BarcodeScannerService
import com.example.blindlabel.ml.TextRecognitionService
import com.example.blindlabel.speech.SpeechRecognitionService
import com.example.blindlabel.speech.SpeechRecognitionState
import com.example.blindlabel.tts.SpeechSection
import com.example.blindlabel.tts.TextToSpeechService
import com.example.blindlabel.ui.screens.camera.CapturedImageHolder
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ResultsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ResultsViewModel"
    }

    private val preferencesRepository = PreferencesRepository(application)
    private val textRecognitionService = TextRecognitionService()
    private val barcodeScannerService = BarcodeScannerService()
    private val openFoodFactsService = OpenFoodFactsService()
    private val ttsService = TextToSpeechService(application)
    private val speechRecognitionService = SpeechRecognitionService(application)

    // Initialize Gemini service with API key from BuildConfig
    private val geminiService: GeminiService? = try {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNotBlank() && apiKey != "your_gemini_api_key_here") {
            GeminiService(apiKey)
        } else {
            Log.w(TAG, "Gemini API key not configured")
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Gemini service", e)
        null
    }

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Processing)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    val ttsState: StateFlow<TtsState> = ttsService.state

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _speechSections = MutableStateFlow<List<SpeechSection>>(emptyList())
    val speechSections: StateFlow<List<SpeechSection>> = _speechSections.asStateFlow()

    // Q&A state
    private val _questionAnswer = MutableStateFlow<QuestionAnswerState>(QuestionAnswerState.Idle)
    val questionAnswer: StateFlow<QuestionAnswerState> = _questionAnswer.asStateFlow()

    // Speech recognition state
    val speechRecognitionState: StateFlow<SpeechRecognitionState> = speechRecognitionService.state

    // Activity launcher for speech recognition fallback
    private var speechLauncher: ActivityResultLauncher<Intent>? = null

    val userPreferences: StateFlow<UserPreferences> = preferencesRepository.userPreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val allergenProfile: StateFlow<AllergenProfile> = preferencesRepository.allergenProfile
        .stateIn(viewModelScope, SharingStarted.Eagerly, AllergenProfile())

    init {
        processImages()
    }

    private fun processImages() {
        viewModelScope.launch {
            try {
                _scanState.value = ScanState.Processing

                val images = CapturedImageHolder.getImages()
                if (images.isEmpty()) {
                    // Demo mode with sample data
                    val demoResult = createDemoResult()
                    _scanState.value = ScanState.Success(demoResult)
                    prepareSpeechSections(demoResult)
                    return@launch
                }

                val profile = allergenProfile.value
                val userAllergenNames = profile.selectedCommonAllergens.map { it.displayName } + profile.customAllergens

                // Step 1: Try to find barcode in images and get product info from API
                Log.d(TAG, "Scanning for barcode in ${images.size} images...")
                val barcode = barcodeScannerService.scanImagesForBarcode(images)
                var apiResult: ScanResult? = null

                if (barcode != null) {
                    Log.d(TAG, "Barcode found: $barcode. Querying Open Food Facts...")
                    apiResult = openFoodFactsService.getProductByBarcode(barcode, userAllergenNames)
                    if (apiResult != null) {
                        Log.d(TAG, "Product found in Open Food Facts: ${apiResult.productName}")
                    } else {
                        Log.d(TAG, "Product not found in Open Food Facts")
                    }
                } else {
                    Log.d(TAG, "No barcode found")
                }

                // Step 2: Always run OCR + Gemini to extract nutrition facts (macros) from images
                Log.d(TAG, "Starting OCR on ${images.size} images for nutrition extraction")
                val textResult = textRecognitionService.recognizeTextFromImages(images)
                Log.d(TAG, "OCR completed. Text length: ${textResult.allText.length}")

                var ocrResult: ScanResult? = null
                if (textResult.hasText() && geminiService != null) {
                    Log.d(TAG, "Sending text to Gemini for nutrition analysis...")
                    ocrResult = geminiService.extractProductInfo(textResult.allText, userAllergenNames)
                }

                // Step 3: Merge results - API for name/ingredients/harmful, OCR for macros
                val scanResult = when {
                    apiResult != null && ocrResult != null -> {
                        // Hybrid: Use API for name/ingredients/harmful, OCR for nutrition
                        Log.d(TAG, "Using hybrid result: API (name/ingredients/harmful) + OCR (nutrition)")
                        apiResult.copy(
                            nutritionInfo = ocrResult.nutritionInfo
                        )
                    }
                    apiResult != null -> {
                        // API only (no OCR text or Gemini unavailable)
                        Log.d(TAG, "Using API result only")
                        apiResult
                    }
                    ocrResult != null -> {
                        // OCR only (no barcode or product not in database)
                        Log.d(TAG, "Using OCR/Gemini result only")
                        ocrResult
                    }
                    textResult.hasText() -> {
                        // Fallback: basic extraction
                        Log.w(TAG, "Using basic extraction (no API, no Gemini)")
                        createBasicResult(textResult.allText, profile)
                    }
                    else -> {
                        _scanState.value = ScanState.Error("No text detected in images. Please try again with clearer images.")
                        return@launch
                    }
                }

                Log.d(TAG, "Analysis complete. Product: ${scanResult.productName}")
                _scanState.value = ScanState.Success(scanResult)
                prepareSpeechSections(scanResult)

                // Clear captured images
                CapturedImageHolder.clear()

            } catch (e: Exception) {
                Log.e(TAG, "Error processing images", e)
                _scanState.value = ScanState.Error(e.message ?: "Processing failed")
            }
        }
    }

    /**
     * Basic fallback extraction when Gemini is unavailable
     */
    private fun createBasicResult(text: String, profile: AllergenProfile): ScanResult {
        return ScanResult(
            productName = "Product (Gemini API not configured)",
            ingredients = listOf("Raw text captured - configure GEMINI_API_KEY for full analysis"),
            detectedAllergens = profile.detectAllergens(text),
            rawText = text
        )
    }
    
    private fun prepareSpeechSections(result: ScanResult) {
        val preferences = userPreferences.value
        val sections = ttsService.prepareSpeech(result, preferences)
        _speechSections.value = sections
        
        // Auto-play if enabled
        if (preferences.autoPlayTts && sections.isNotEmpty()) {
            ttsService.speak(sections, preferences.speechRate)
        }
    }
    
    private fun createDemoResult(): ScanResult {
        return ScanResult(
            productName = "Organic Whole Grain Cereal",
            ingredients = listOf("Whole grain oats", "Sugar", "Honey", "Salt", "Natural flavor", "Vitamin E"),
            majorIngredients = listOf("Whole grain oats", "Sugar", "Honey"),
            nutritionInfo = com.example.blindlabel.data.model.NutritionInfo(
                calories = "120 kcal", protein = "3 g", totalFat = "2 g",
                sugars = "8 g", carbohydrates = "24 g", fiber = "3 g", sodium = "140 mg"
            ),
            allergenWarnings = listOf("Contains wheat", "May contain tree nuts"),
            detectedAllergens = allergenProfile.value.detectAllergens("wheat oats gluten"),
            expiryDate = "March 2025",
            usageInstructions = "Store in a cool, dry place. Refrigerate after opening.",
            harmfulIngredients = emptyList(),
            rawText = "Sample text..."
        )
    }
    
    fun playTts() {
        val sections = _speechSections.value
        if (sections.isNotEmpty()) {
            ttsService.speak(sections, userPreferences.value.speechRate)
        }
    }

    fun pauseTts() = ttsService.pause()
    fun resumeTts() = ttsService.resume()
    fun stopTts() = ttsService.stop()
    fun skipNext() = ttsService.skipToNext()
    fun skipPrevious() = ttsService.skipToPrevious()

    /**
     * Ask a question about the scanned product.
     * The answer will be spoken aloud.
     */
    fun askQuestion(question: String) {
        if (question.isBlank()) return

        val currentState = _scanState.value
        if (currentState !is ScanState.Success) return

        viewModelScope.launch {
            _questionAnswer.value = QuestionAnswerState.Loading

            try {
                if (geminiService == null) {
                    val errorMsg = "Question answering is not available. Gemini API not configured."
                    _questionAnswer.value = QuestionAnswerState.Error(errorMsg)
                    speakAnswer(errorMsg)
                    return@launch
                }

                val answer = geminiService.askQuestion(question, currentState.result)
                _questionAnswer.value = QuestionAnswerState.Success(question, answer)
                speakAnswer(answer)

            } catch (e: Exception) {
                Log.e(TAG, "Error asking question", e)
                val errorMsg = "Sorry, I couldn't answer that question. ${e.message}"
                _questionAnswer.value = QuestionAnswerState.Error(errorMsg)
                speakAnswer(errorMsg)
            }
        }
    }

    /**
     * Speak an answer using TTS.
     */
    private fun speakAnswer(text: String) {
        // Stop any current speech first
        ttsService.stop()
        // Create a single section for the answer
        val section = SpeechSection(
            type = TtsSection.QA_ANSWER,
            title = "Answer",
            content = text
        )
        ttsService.speak(listOf(section), userPreferences.value.speechRate)
    }

    /**
     * Clear the current question/answer state.
     */
    fun clearQuestionAnswer() {
        _questionAnswer.value = QuestionAnswerState.Idle
    }

    /**
     * Set the speech launcher for activity-based speech recognition.
     * This is called from the UI when the composable is first composed.
     */
    fun setSpeechLauncher(launcher: ActivityResultLauncher<Intent>) {
        speechLauncher = launcher
        speechRecognitionService.setActivityLauncher(launcher)
        Log.d(TAG, "Speech launcher set")
    }

    /**
     * Start voice recording for question.
     * Called when the accessibility button is pressed.
     */
    fun startVoiceQuestion() {
        Log.d(TAG, "startVoiceQuestion called")
        // Stop any current TTS first
        ttsService.stop()
        _isRecording.value = true
        _questionAnswer.value = QuestionAnswerState.Listening

        speechRecognitionService.startListening { transcribedText ->
            Log.d(TAG, "Speech recognition result: '$transcribedText'")
            _isRecording.value = false
            if (transcribedText.isNotBlank()) {
                Log.d(TAG, "Asking question: $transcribedText")
                askQuestion(transcribedText)
            } else {
                Log.d(TAG, "No transcribed text, returning to idle")
                _questionAnswer.value = QuestionAnswerState.Idle
            }
        }
    }

    /**
     * Stop voice recording.
     * Called when the accessibility button is released.
     */
    fun stopVoiceQuestion() {
        Log.d(TAG, "stopVoiceQuestion called")
        _isRecording.value = false
        speechRecognitionService.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognitionService.destroy()
        ttsService.shutdown()
        textRecognitionService.close()
        barcodeScannerService.close()
    }
}

/**
 * State for question answering feature.
 */
sealed class QuestionAnswerState {
    object Idle : QuestionAnswerState()
    object Listening : QuestionAnswerState()
    object Loading : QuestionAnswerState()
    data class Success(val question: String, val answer: String) : QuestionAnswerState()
    data class Error(val message: String) : QuestionAnswerState()
}

