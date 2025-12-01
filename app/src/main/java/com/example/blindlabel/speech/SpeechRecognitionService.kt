package com.example.blindlabel.speech

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Service for speech-to-text functionality.
 * Allows users to ask questions using voice.
 *
 * Supports two modes:
 * 1. Direct SpeechRecognizer (if available) - for continuous listening
 * 2. Activity-based recognition - more reliable fallback using Google's UI
 */
class SpeechRecognitionService(private val context: Context) {

    companion object {
        private const val TAG = "SpeechRecognition"
        const val SPEECH_REQUEST_CODE = 1001
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isInitialized = false
    private var useActivityFallback = false

    private val _state = MutableStateFlow<SpeechRecognitionState>(SpeechRecognitionState.Idle)
    val state: StateFlow<SpeechRecognitionState> = _state.asStateFlow()

    private var onResultCallback: ((String) -> Unit)? = null

    // Store partial results to use if recognition is stopped early
    private var lastPartialResult: String = ""
    private var hasDeliveredResult: Boolean = false

    // Activity launcher for fallback mode
    private var activityLauncher: ActivityResultLauncher<Intent>? = null

    /**
     * Set the activity result launcher for fallback speech recognition.
     * This must be called from a Composable or Activity before using speech recognition.
     */
    fun setActivityLauncher(launcher: ActivityResultLauncher<Intent>) {
        activityLauncher = launcher
        Log.d(TAG, "Activity launcher set")
    }

    /**
     * Initialize the speech recognizer. Must be called on main thread.
     */
    private fun ensureInitialized(): Boolean {
        if (isInitialized) {
            return speechRecognizer != null || useActivityFallback
        }

        isInitialized = true

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Direct speech recognition not available, will use activity fallback")
            useActivityFallback = true
            return activityLauncher != null
        }

        Log.d(TAG, "Speech recognition is available, creating recognizer")
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        setupRecognitionListener()
        return true
    }

    /**
     * Check if we need to use the activity-based fallback
     */
    fun needsActivityFallback(): Boolean {
        return useActivityFallback || !SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Get the intent for activity-based speech recognition
     */
    fun getSpeechRecognitionIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask a question about this product")
        }
    }

    /**
     * Handle result from activity-based speech recognition
     */
    fun handleActivityResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val transcribedText = results?.firstOrNull() ?: ""
            Log.d(TAG, "Activity result: $transcribedText")
            _state.value = SpeechRecognitionState.Success(transcribedText)
            onResultCallback?.invoke(transcribedText)
        } else {
            Log.d(TAG, "Activity result cancelled or failed")
            _state.value = SpeechRecognitionState.Idle
            onResultCallback?.invoke("")
        }
        onResultCallback = null
    }

    private fun setupRecognitionListener() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = SpeechRecognitionState.Listening
                Log.d(TAG, "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _state.value = SpeechRecognitionState.Processing
                Log.d(TAG, "Speech ended")
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Please try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Please try again."
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    else -> "Recognition error"
                }
                Log.e(TAG, "Speech recognition error: $error - $errorMessage")
                _state.value = SpeechRecognitionState.Error(errorMessage)

                // If we have partial results and haven't delivered a result yet, use them
                if (!hasDeliveredResult && lastPartialResult.isNotBlank()) {
                    Log.d(TAG, "Using partial result on error: $lastPartialResult")
                    hasDeliveredResult = true
                    onResultCallback?.invoke(lastPartialResult)
                } else if (!hasDeliveredResult) {
                    hasDeliveredResult = true
                    onResultCallback?.invoke("")
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcribedText = matches?.firstOrNull() ?: ""
                Log.d(TAG, "Recognized: $transcribedText")
                _state.value = SpeechRecognitionState.Success(transcribedText)

                if (!hasDeliveredResult) {
                    hasDeliveredResult = true
                    // Use final result, or partial if final is empty
                    val finalText = if (transcribedText.isNotBlank()) transcribedText else lastPartialResult
                    onResultCallback?.invoke(finalText)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialText = partial?.firstOrNull() ?: ""
                if (partialText.isNotBlank()) {
                    lastPartialResult = partialText
                    Log.d(TAG, "Partial: $partialText")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening(onResult: (String) -> Unit) {
        Log.d(TAG, "startListening called")

        // Reset state for new recording
        lastPartialResult = ""
        hasDeliveredResult = false
        onResultCallback = onResult
        _state.value = SpeechRecognitionState.Starting

        // Must run on main thread
        mainHandler.post {
            // Lazily initialize the recognizer
            ensureInitialized()

            // Use activity fallback if direct recognition isn't available
            if (useActivityFallback) {
                Log.d(TAG, "Using activity fallback for speech recognition")
                val launcher = activityLauncher
                if (launcher != null) {
                    try {
                        launcher.launch(getSpeechRecognitionIntent())
                        _state.value = SpeechRecognitionState.Listening
                    } catch (e: Exception) {
                        Log.e(TAG, "Error launching speech activity", e)
                        _state.value = SpeechRecognitionState.Error("Failed to start: ${e.message}")
                        onResult("")
                    }
                } else {
                    Log.e(TAG, "Activity launcher not set")
                    _state.value = SpeechRecognitionState.Error("Speech recognition not configured")
                    onResult("")
                }
                return@post
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            try {
                Log.d(TAG, "Starting speech recognizer...")
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition", e)
                _state.value = SpeechRecognitionState.Error("Failed to start: ${e.message}")
                onResult("")
            }
        }
    }

    fun stopListening() {
        Log.d(TAG, "stopListening called, partial result: $lastPartialResult")
        // Don't stop if using activity fallback - the activity handles its own lifecycle
        if (useActivityFallback) {
            return
        }
        mainHandler.post {
            speechRecognizer?.stopListening()
        }
    }

    fun destroy() {
        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = null
            isInitialized = false
        }
    }
}

sealed class SpeechRecognitionState {
    object Idle : SpeechRecognitionState()
    object Starting : SpeechRecognitionState()
    object Listening : SpeechRecognitionState()
    object Processing : SpeechRecognitionState()
    data class Success(val text: String) : SpeechRecognitionState()
    data class Error(val message: String) : SpeechRecognitionState()
}

