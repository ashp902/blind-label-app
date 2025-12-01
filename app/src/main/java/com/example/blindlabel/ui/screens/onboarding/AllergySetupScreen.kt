package com.example.blindlabel.ui.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.blindlabel.data.model.AllergenProfile
import com.example.blindlabel.data.model.CommonAllergen
import com.example.blindlabel.data.repository.PreferencesRepository
import com.example.blindlabel.ui.components.AccessibleButton
import com.example.blindlabel.ui.components.AccessibleOutlinedButton
import kotlinx.coroutines.launch

@Composable
fun AllergySetupScreen(
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val preferencesRepository = remember { PreferencesRepository(context) }
    val scope = rememberCoroutineScope()
    
    var selectedAllergens by remember { mutableStateOf(setOf<CommonAllergen>()) }
    var customAllergens by remember { mutableStateOf(listOf<String>()) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customAllergenInput by remember { mutableStateOf("") }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header
            Text(
                text = "Set Up Your Allergy Profile",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                text = "Select any allergens you need to avoid. The app will alert you when these are detected.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(24.dp))
            
            // Allergen List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(CommonAllergen.entries) { allergen ->
                    AllergenChip(
                        allergen = allergen,
                        isSelected = allergen in selectedAllergens,
                        onToggle = {
                            selectedAllergens = if (allergen in selectedAllergens) {
                                selectedAllergens - allergen
                            } else {
                                selectedAllergens + allergen
                            }
                        }
                    )
                }
                
                // Custom allergens section
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Custom Allergens",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                items(customAllergens) { custom ->
                    CustomAllergenChip(
                        name = custom,
                        onRemove = { customAllergens = customAllergens - custom }
                    )
                }
                
                item {
                    AccessibleOutlinedButton(
                        text = "Add Custom Allergen",
                        onClick = { showCustomDialog = true },
                        icon = Icons.Default.Add
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Continue Button
            AccessibleButton(
                text = "Continue",
                onClick = {
                    scope.launch {
                        val profile = AllergenProfile(selectedAllergens, customAllergens)
                        preferencesRepository.updateAllergenProfile(profile)
                        preferencesRepository.setOnboardingCompleted(true)
                        onSetupComplete()
                    }
                },
                contentDescription = "Continue to main app"
            )
        }
    }
    
    // Custom Allergen Dialog
    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Add Custom Allergen", style = MaterialTheme.typography.headlineSmall) },
            text = {
                OutlinedTextField(
                    value = customAllergenInput,
                    onValueChange = { customAllergenInput = it },
                    label = { Text("Allergen name") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (customAllergenInput.isNotBlank()) {
                        customAllergens = customAllergens + customAllergenInput.trim()
                        customAllergenInput = ""
                    }
                    showCustomDialog = false
                }) { Text("Add", style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }
}

@Composable
private fun AllergenChip(
    allergen: CommonAllergen,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Surface(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${allergen.displayName}. ${if (isSelected) "Selected" else "Not selected"}. Double tap to toggle."
            },
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(2.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = allergen.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun CustomAllergenChip(
    name: String,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            TextButton(onClick = onRemove) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

