package com.example.blindlabel.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.blindlabel.ui.screens.camera.CameraScreen
import com.example.blindlabel.ui.screens.camera.CapturedImageHolder
import com.example.blindlabel.ui.screens.home.HomeScreen
import com.example.blindlabel.ui.screens.onboarding.AllergySetupScreen
import com.example.blindlabel.ui.screens.onboarding.WelcomeScreen
import com.example.blindlabel.ui.screens.results.ResultsScreen
import com.example.blindlabel.ui.screens.settings.SettingsScreen

/**
 * Navigation routes for the BlindLabel app.
 */
sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object AllergySetup : Screen("allergy_setup")
    object Home : Screen("home")
    object Camera : Screen("camera/{captureType}") {
        fun createRoute(captureType: String) = "camera/$captureType"
    }
    object Results : Screen("results")
    object Settings : Screen("settings")
}

/**
 * Main navigation host for the app.
 */
@Composable
fun BlindLabelNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Welcome.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onGetStarted = {
                    navController.navigate(Screen.AllergySetup.route)
                }
            )
        }
        
        composable(Screen.AllergySetup.route) {
            AllergySetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToCamera = { captureType ->
                    // Clear any previous images before starting a new scan
                    CapturedImageHolder.clear()
                    navController.navigate(Screen.Camera.createRoute(captureType))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        composable(Screen.Camera.route) { backStackEntry ->
            val captureType = backStackEntry.arguments?.getString("captureType") ?: "front"
            CameraScreen(
                captureType = captureType,
                onImageCaptured = {
                    // Handle navigation based on capture type
                    if (captureType == "front") {
                        navController.navigate(Screen.Camera.createRoute("back"))
                    } else {
                        navController.navigate(Screen.Results.route) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Results.route) {
            ResultsScreen(
                onNavigateBack = {
                    // Clear images when going back to home
                    CapturedImageHolder.clear()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

