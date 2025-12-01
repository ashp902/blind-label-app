package com.example.blindlabel.ui.screens.results

import android.Manifest
import android.app.Activity
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.blindlabel.data.model.ScanResult
import com.example.blindlabel.data.model.ScanState
import com.example.blindlabel.ui.components.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ResultsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ResultsViewModel = viewModel()
) {
    val scanState by viewModel.scanState.collectAsState()
    val questionAnswerState by viewModel.questionAnswer.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()

    // Request microphone permission for voice questions
    val microphonePermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // Activity launcher for speech recognition (fallback when SpeechRecognizer is not available)
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val transcribedText = results?.firstOrNull() ?: ""
            if (transcribedText.isNotBlank()) {
                viewModel.askQuestion(transcribedText)
            }
        }
        viewModel.stopVoiceQuestion()
    }

    // Set up the launcher in the ViewModel
    LaunchedEffect(Unit) {
        viewModel.setSpeechLauncher(speechLauncher)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Results", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go back", Modifier.size(28.dp))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = scanState) {
                is ScanState.Processing -> LoadingContent()
                is ScanState.Success -> ResultsContent(
                    result = state.result,
                    modifier = Modifier.padding(bottom = 96.dp), // Space for bottom button
                    questionAnswerState = questionAnswerState,
                    onAskQuestion = viewModel::askQuestion
                )
                is ScanState.Error -> ErrorContent(state.message, Modifier, onNavigateBack)
                else -> {}
            }

            // Large holdable accessibility button at bottom for voice Q&A
            HoldableAccessibilityButton(
                text = "Hold to Ask Question",
                holdingText = "🎤 Recording...",
                onHoldStart = {
                    if (microphonePermissionState.status.isGranted) {
                        viewModel.startVoiceQuestion()
                    } else {
                        microphonePermissionState.launchPermissionRequest()
                    }
                },
                onHoldEnd = { viewModel.stopVoiceQuestion() },
                isHolding = isRecording,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(24.dp))
            Text("Processing image...", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ErrorContent(message: String, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Error, null, Modifier.size(64.dp), MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Text("Error", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            AccessibleButton("Try Again", onClick = onRetry)
        }
    }
}

@Composable
private fun ResultsContent(
    result: ScanResult,
    modifier: Modifier = Modifier,
    questionAnswerState: QuestionAnswerState,
    onAskQuestion: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Allergen Alert - ALWAYS FIRST for safety
        if (result.hasAllergenAlert()) {
            item { AllergenWarningCard(result.detectedAllergens) }
        }

        // Harmful Ingredients Alert - Show prominently after allergens
        if (result.harmfulIngredients.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Harmful Ingredients Detected",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        result.harmfulIngredients.forEach { harmful ->
                            Text(
                                "• $harmful",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Product Name
        result.productName?.let {
            item { InfoCard("Product Name", it) }
        }

        // Full Nutrition Facts - All macros
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionHeader("Nutrition Facts")
                    result.nutritionInfo.servingSize?.let { NutritionRow("Serving Size", it) }
                    result.nutritionInfo.calories?.let { NutritionRow("Calories", it) }
                    result.nutritionInfo.totalFat?.let { NutritionRow("Total Fat", it) }
                    result.nutritionInfo.saturatedFat?.let { NutritionRow("  Saturated Fat", it) }
                    result.nutritionInfo.transFat?.let { NutritionRow("  Trans Fat", it) }
                    result.nutritionInfo.cholesterol?.let { NutritionRow("Cholesterol", it) }
                    result.nutritionInfo.sodium?.let { NutritionRow("Sodium", it) }
                    result.nutritionInfo.carbohydrates?.let { NutritionRow("Carbohydrates", it) }
                    result.nutritionInfo.fiber?.let { NutritionRow("  Fiber", it) }
                    result.nutritionInfo.sugars?.let { NutritionRow("  Sugars", it) }
                    result.nutritionInfo.protein?.let { NutritionRow("Protein", it) }

                    // Show message if no nutrition info found
                    if (result.nutritionInfo.calories == null &&
                        result.nutritionInfo.protein == null &&
                        result.nutritionInfo.totalFat == null) {
                        Text(
                            "No nutrition information detected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Full Ingredients List
        if (result.ingredients.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader("Ingredients (${result.ingredients.size})")
                        Spacer(Modifier.height(8.dp))
                        result.ingredients.forEachIndexed { index, ingredient ->
                            Text(
                                "${index + 1}. $ingredient",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Allergen Warnings from label
        if (result.allergenWarnings.isNotEmpty()) {
            item {
                InfoCard(
                    "Allergen Warnings",
                    result.allergenWarnings.joinToString("\n• ", prefix = "• "),
                    labelColor = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        // Expiry Date
        result.expiryDate?.let {
            item { InfoCard("Expiry Date", it, labelColor = MaterialTheme.colorScheme.tertiary) }
        }

        // Usage/Storage Instructions
        result.usageInstructions?.let {
            item { InfoCard("Storage Instructions", it) }
        }

        // Ask a Question Section (Voice-based)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Ask a Question",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    // Voice instruction
                    Text(
                        text = "Hold Volume Up to ask a question",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Center
                    )

                    // Show state
                    when (questionAnswerState) {
                        is QuestionAnswerState.Listening -> {
                            Spacer(Modifier.height(16.dp))
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Listening",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Listening... Speak your question",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        is QuestionAnswerState.Loading -> {
                            Spacer(Modifier.height(16.dp))
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Thinking...", style = MaterialTheme.typography.bodyMedium)
                        }
                        is QuestionAnswerState.Success -> {
                            Spacer(Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        "Q: ${questionAnswerState.question}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        questionAnswerState.answer,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                        is QuestionAnswerState.Error -> {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                questionAnswerState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {}
                    }
                }
            }
        }

        // Bottom padding for floating button
        item {
            Spacer(Modifier.height(140.dp))
        }
    }
}

@Composable
private fun NutritionRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}
