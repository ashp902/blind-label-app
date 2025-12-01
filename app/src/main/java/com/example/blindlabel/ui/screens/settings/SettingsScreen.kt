package com.example.blindlabel.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.blindlabel.data.model.CommonAllergen
import com.example.blindlabel.data.model.AllergenProfile
import com.example.blindlabel.data.repository.PreferencesRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val preferencesRepository = remember { PreferencesRepository(context) }
    val scope = rememberCoroutineScope()
    
    val userPrefs by preferencesRepository.userPreferences.collectAsState(initial = null)
    val allergenProfile by preferencesRepository.allergenProfile.collectAsState(initial = AllergenProfile())
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go back", Modifier.size(28.dp))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Speech Settings Section
            item {
                Text("Speech Settings", style = MaterialTheme.typography.headlineSmall, 
                     color = MaterialTheme.colorScheme.primary)
            }
            
            item {
                userPrefs?.let { prefs ->
                    Column {
                        Text("Speech Rate: ${String.format("%.1f", prefs.speechRate)}x",
                             style = MaterialTheme.typography.bodyLarge)
                        Slider(
                            value = prefs.speechRate,
                            onValueChange = { scope.launch { preferencesRepository.setSpeechRate(it) } },
                            valueRange = 0.5f..2.0f,
                            steps = 5,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // Reading Preferences Section
            item {
                Spacer(Modifier.height(8.dp))
                Text("What to Read", style = MaterialTheme.typography.headlineSmall,
                     color = MaterialTheme.colorScheme.primary)
            }
            
            item {
                ReadingPreferenceItem("Read Allergen Alerts", userPrefs?.readAllergenAlert ?: true)
            }
            item {
                ReadingPreferenceItem("Read Product Name", userPrefs?.readProductName ?: true)
            }
            item {
                ReadingPreferenceItem("Read Ingredients", userPrefs?.readIngredients ?: true)
            }
            item {
                ReadingPreferenceItem("Read Nutrition Facts", userPrefs?.readNutrition ?: true)
            }
            item {
                ReadingPreferenceItem("Read Expiry Date", userPrefs?.readExpiryDate ?: true)
            }
            
            // Allergen Profile Section
            item {
                Spacer(Modifier.height(8.dp))
                Text("Your Allergens", style = MaterialTheme.typography.headlineSmall,
                     color = MaterialTheme.colorScheme.primary)
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        if (allergenProfile.selectedCommonAllergens.isEmpty() && 
                            allergenProfile.customAllergens.isEmpty()) {
                            Text("No allergens configured", style = MaterialTheme.typography.bodyLarge)
                        } else {
                            allergenProfile.selectedCommonAllergens.forEach { allergen ->
                                Text("• ${allergen.displayName}", style = MaterialTheme.typography.bodyLarge)
                            }
                            allergenProfile.customAllergens.forEach { custom ->
                                Text("• $custom", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            
            // About Section
            item {
                Spacer(Modifier.height(8.dp))
                Text("About", style = MaterialTheme.typography.headlineSmall,
                     color = MaterialTheme.colorScheme.primary)
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("BlindLabel", style = MaterialTheme.typography.titleLarge)
                        Text("Version 1.0", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Accessible Food Label Reader", style = MaterialTheme.typography.bodyLarge)
                        Text("CS663 - Computer Vision Project", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingPreferenceItem(label: String, enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = enabled, onCheckedChange = { /* TODO: Save preference */ })
    }
}

