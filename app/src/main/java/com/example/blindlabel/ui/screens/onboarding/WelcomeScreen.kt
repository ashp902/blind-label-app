package com.example.blindlabel.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.blindlabel.ui.components.AccessibleButton

/**
 * Welcome screen - first screen users see when launching the app.
 * Announces app purpose and provides Get Started button.
 */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .semantics { 
                    contentDescription = "Welcome to Blind Label. An accessible food label reader app. Press Get Started to begin setup."
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Icon
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.height(32.dp))
            
            // App Title
            Text(
                text = "BlindLabel",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(16.dp))
            
            // App Description
            Text(
                text = "Accessible Food Label Reader",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(24.dp))
            
            // Description
            Text(
                text = "Scan food labels and get voice-based information about ingredients, nutrition, and allergens.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(Modifier.height(64.dp))
            
            // Get Started Button
            AccessibleButton(
                text = "Get Started",
                onClick = onGetStarted,
                modifier = Modifier.padding(horizontal = 16.dp),
                contentDescription = "Get Started. Press to begin setting up your allergy profile."
            )
        }
    }
}

