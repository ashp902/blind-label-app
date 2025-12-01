package com.example.blindlabel.ui.screens.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.blindlabel.ui.components.LargeAccessibilityButton
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    captureType: String, // "front" or "back"
    onImageCaptured: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }
    
    if (cameraPermissionState.status.isGranted) {
        CameraContent(
            captureType = captureType,
            onImageCaptured = onImageCaptured,
            onNavigateBack = onNavigateBack
        )
    } else {
        PermissionDeniedContent(onNavigateBack = onNavigateBack)
    }
}

@Composable
private fun CameraContent(
    captureType: String,
    onImageCaptured: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var isCameraReady by remember { mutableStateOf(false) }

    val labelText = if (captureType == "front") {
        "Capture FRONT of package"
    } else {
        "Capture BACK of package (ingredients)"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val newImageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()
                    imageCapture = newImageCapture

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            newImageCapture
                        )
                        isCameraReady = true
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Camera binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Alignment Guide Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(0.75f)
                    .border(4.dp, Color.White, RoundedCornerShape(16.dp))
            )
        }
        
        // Top Bar with instructions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(16.dp)
                .semantics { contentDescription = labelText },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = if (captureType == "front") "Step 1 of 2" else "Step 2 of 2",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(text = labelText, style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center)
        }

        // Large accessibility button at bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Align the label within the frame",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            if (isCapturing) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator(color = Color.White)
            }

            Spacer(Modifier.height(8.dp))

            val buttonText = if (captureType == "front") {
                if (isCapturing) "Capturing..." else "Capture Front Label"
            } else {
                if (isCapturing) "Capturing..." else "Capture Back Label"
            }

            LargeAccessibilityButton(
                text = buttonText,
                onClick = {
                    val capture = imageCapture
                    if (!isCapturing && capture != null) {
                        isCapturing = true
                        captureImage(context, capture) {
                            isCapturing = false
                            onImageCaptured()
                        }
                    }
                },
                enabled = !isCapturing && isCameraReady
            )
        }
    }
}

private fun captureImage(
    context: Context,
    imageCapture: ImageCapture?,
    onCaptured: () -> Unit
) {
    imageCapture?.let { capture ->
        val executor = Executors.newSingleThreadExecutor()

        capture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    // Store the image for processing
                    CapturedImageHolder.addImage(imageProxy.toBitmap())
                    imageProxy.close()

                    // Navigate to next screen
                    android.os.Handler(context.mainLooper).post {
                        onCaptured()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraScreen", "Capture failed", exception)
                    android.os.Handler(context.mainLooper).post {
                        onCaptured() // Still proceed for demo purposes
                    }
                }
            }
        )
    } ?: onCaptured()
}

@Composable
private fun PermissionDeniedContent(onNavigateBack: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Camera Permission Required",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "This app needs camera access to scan food labels. Please grant camera permission in your device settings.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            Button(onClick = onNavigateBack) {
                Text("Go Back", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Singleton to hold captured images between screens.
 */
object CapturedImageHolder {
    private val images = mutableListOf<Bitmap>()

    fun addImage(bitmap: Bitmap) {
        images.add(bitmap)
    }

    fun getImages(): List<Bitmap> = images.toList()

    fun clear() {
        images.forEach { it.recycle() }
        images.clear()
    }

    fun hasImages(): Boolean = images.isNotEmpty()
}

