package com.example.blindlabel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.blindlabel.data.repository.PreferencesRepository
import com.example.blindlabel.navigation.BlindLabelNavHost
import com.example.blindlabel.navigation.Screen
import com.example.blindlabel.ui.theme.BlindLabelTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private lateinit var preferencesRepository: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferencesRepository = PreferencesRepository(this)

        // Check if onboarding is completed
        val hasCompletedOnboarding = runBlocking {
            preferencesRepository.userPreferences.first().hasCompletedOnboarding
        }

        enableEdgeToEdge()
        setContent {
            BlindLabelTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    BlindLabelNavHost(
                        navController = navController,
                        startDestination = if (hasCompletedOnboarding) {
                            Screen.Home.route
                        } else {
                            Screen.Welcome.route
                        }
                    )
                }
            }
        }
    }
}