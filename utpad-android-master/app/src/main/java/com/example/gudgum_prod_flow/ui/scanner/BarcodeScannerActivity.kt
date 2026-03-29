package com.example.gudgum_prod_flow.ui.scanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.gudgum_prod_flow.ui.theme.GudGumProdFlowTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object BarcodeScannerExtras {
    const val PromptText = "barcode_scanner_prompt"
    const val ResultBarcodeValue = "barcode_scanner_result"
    const val ResultQuantity = "barcode_scanner_quantity"
}

class BarcodeScannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prompt = "Scan QR Code"

        setContent {
            GudGumProdFlowTheme {
                BarcodeScannerRoute(
                    prompt = prompt,
                    onClose = ::closeCancelled,
                    onBarcodeConfirmed = ::closeWithBarcode,
                )
            }
        }
    }

    private fun closeCancelled() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun closeWithBarcode(value: String, quantity: Int) {
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(BarcodeScannerExtras.ResultBarcodeValue, value)
                .putExtra(BarcodeScannerExtras.ResultQuantity, quantity),
        )
        finish()
    }
}

@Composable
private fun BarcodeScannerRoute(
    prompt: String,
    onClose: () -> Unit,
    onBarcodeConfirmed: (String, Int) -> Unit,
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    var scanEnabled by remember { mutableStateOf(true) }
    var scannedBarcode by remember { mutableStateOf<String?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    val infiniteTransition = rememberInfiniteTransition()
    val scanLineOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLineAnimation"
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler(onBack = onClose)

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            MlKitBarcodeScannerPreview(
                modifier = Modifier.fillMaxSize(),
                scanEnabled = scanEnabled && scannedBarcode == null,
                torchEnabled = torchEnabled,
                onBarcodeDetected = { value ->
                    if (scannedBarcode == null) {
                        scannedBarcode = value
                        // Immediately populate and return
                        onBarcodeConfirmed(value, 1)
                    }
                },
            )
            
            // PIXEL PERFECT SCANNER OVERLAY
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            ) {
                // 1. Draw dark translucent background
                drawRect(color = Color(0xCC000000)) // 80% Black

                // 2. Define Cutout Area
                // Based on screenshot, cutout is square-ish, horizontally centered, vertically somewhat in upper-middle.
                val cutoutWidth = size.width * 0.75f
                val cutoutHeight = cutoutWidth * 0.8f 
                val cutoutLeft = (size.width - cutoutWidth) / 2
                val cutoutTop = size.height * 0.25f
                
                // 3. Draw Cutout (Clear)
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(cutoutLeft, cutoutTop),
                    size = Size(cutoutWidth, cutoutHeight),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    blendMode = BlendMode.Clear
                )

                // 4. Draw Blue Brackets
                val blueTone = Color(0xFF2563EB) // UtpadPrimary
                val strokeWidth = 2.dp.toPx()
                val bracketLen = 32.dp.toPx()
                val cornerRad = 16.dp.toPx()
                
                // We'll draw 4 corner brackets. 
                // Top-Left
                drawLine(blueTone, Offset(cutoutLeft, cutoutTop + cornerRad), Offset(cutoutLeft, cutoutTop + bracketLen), strokeWidth)
                drawLine(blueTone, Offset(cutoutLeft + cornerRad, cutoutTop), Offset(cutoutLeft + bracketLen, cutoutTop), strokeWidth)
                // Bottom-Left
                drawLine(blueTone, Offset(cutoutLeft, cutoutTop + cutoutHeight - cornerRad), Offset(cutoutLeft, cutoutTop + cutoutHeight - bracketLen), strokeWidth)
                drawLine(blueTone, Offset(cutoutLeft + cornerRad, cutoutTop + cutoutHeight), Offset(cutoutLeft + bracketLen, cutoutTop + cutoutHeight), strokeWidth)
                // Top-Right
                drawLine(blueTone, Offset(cutoutLeft + cutoutWidth, cutoutTop + cornerRad), Offset(cutoutLeft + cutoutWidth, cutoutTop + bracketLen), strokeWidth)
                drawLine(blueTone, Offset(cutoutLeft + cutoutWidth - cornerRad, cutoutTop), Offset(cutoutLeft + cutoutWidth - bracketLen, cutoutTop), strokeWidth)
                // Bottom-Right
                drawLine(blueTone, Offset(cutoutLeft + cutoutWidth, cutoutTop + cutoutHeight - cornerRad), Offset(cutoutLeft + cutoutWidth, cutoutTop + cutoutHeight - bracketLen), strokeWidth)
                drawLine(blueTone, Offset(cutoutLeft + cutoutWidth - cornerRad, cutoutTop + cutoutHeight), Offset(cutoutLeft + cutoutWidth - bracketLen, cutoutTop + cutoutHeight), strokeWidth)
                
                // Rounded corner arcs for brackets
                drawArc(
                    color = blueTone,
                    startAngle = 180f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(cutoutLeft, cutoutTop), 
                    size = Size(cornerRad*2, cornerRad*2), 
                    style = Stroke(strokeWidth)
                )
                drawArc(
                    color = blueTone,
                    startAngle = 270f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(cutoutLeft + cutoutWidth - cornerRad*2, cutoutTop), 
                    size = Size(cornerRad*2, cornerRad*2), 
                    style = Stroke(strokeWidth)
                )
                drawArc(
                    color = blueTone,
                    startAngle = 90f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(cutoutLeft, cutoutTop + cutoutHeight - cornerRad*2), 
                    size = Size(cornerRad*2, cornerRad*2), 
                    style = Stroke(strokeWidth)
                )
                drawArc(
                    color = blueTone,
                    startAngle = 0f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(cutoutLeft + cutoutWidth - cornerRad*2, cutoutTop + cutoutHeight - cornerRad*2), 
                    size = Size(cornerRad*2, cornerRad*2), 
                    style = Stroke(strokeWidth)
                )
                
                // 5. Draw Horizontal Scan Line
                val linePadding = 16.dp.toPx()
                val lineY = cutoutTop + (cutoutHeight * scanLineOffset)
                drawLine(
                    color = blueTone.copy(alpha = 0.8f),
                    start = Offset(cutoutLeft + linePadding, lineY),
                    end = Offset(cutoutLeft + cutoutWidth - linePadding, lineY),
                    strokeWidth = 2.dp.toPx()
                )
            }
            
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UTPAD",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 4.sp, 
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )
            }

            // Bottom Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Back Button centered directly above align barcode text
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0x33FFFFFF), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "ALIGN BARCODE",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 2.sp, 
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(Color(0xFFE85D04), CircleShape) // Warning orange dot
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                IconButton(
                    onClick = { torchEnabled = !torchEnabled },
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0x33FFFFFF), CircleShape) // Semi-transparent white
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = "Flashlight",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
            ) {
                CameraPermissionRequired(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 22.dp),
                    onGrantPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onClose = onClose,
                )
            }
        }
    }
}

// Removed ScanSuccessCard and QuantityCircleButton

@Composable
private fun CameraPermissionRequired(
    modifier: Modifier = Modifier,
    onGrantPermission: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Camera permission is required to scan barcodes.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onGrantPermission) {
                Text("Grant camera permission")
            }
            TextButton(onClick = onClose) {
                Text("Go back")
            }
        }
    }
}

@Composable
private fun MlKitBarcodeScannerPreview(
    modifier: Modifier = Modifier,
    scanEnabled: Boolean,
    torchEnabled: Boolean = false,
    onBarcodeDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val detectionEnabled = remember { AtomicBoolean(scanEnabled) }

    LaunchedEffect(scanEnabled) {
        detectionEnabled.set(scanEnabled)
    }

    LaunchedEffect(torchEnabled) {
        cameraControl?.let {
            if (it.enableTorch(torchEnabled) == null) {
                // Ignore failure if unsupported
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val analyzerExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        var imageAnalysis: ImageAnalysis? = null
        var cameraProvider: ProcessCameraProvider? = null
        var scanner: com.google.mlkit.vision.barcode.BarcodeScanner? = null
        var analyzerAlive = true
        var lastCandidate: String? = null
        var stableCount = 0

        val listener = Runnable {
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val previewBuilder = Preview.Builder()
            Camera2Interop.Extender(previewBuilder)
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                )
            val preview = previewBuilder.build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val analysisBuilder = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            Camera2Interop.Extender(analysisBuilder)
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                )
            val analysisUseCase = analysisBuilder.build()
            imageAnalysis = analysisUseCase

            try {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysisUseCase,
                )
                
                cameraControl = camera.cameraControl
                
                // Enable Torch
                if (camera.cameraInfo.hasFlashUnit()) {
                    camera.cameraControl.enableTorch(torchEnabled)
                }

                val optionsBuilder = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .enableAllPotentialBarcodes()

                scanner = BarcodeScanning.getClient(optionsBuilder.build())

                analysisUseCase.setAnalyzer(analyzerExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    val activeScanner = scanner
                    if (!analyzerAlive || mediaImage == null || activeScanner == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    if (!detectionEnabled.get()) {
                        lastCandidate = null
                        stableCount = 0
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees,
                    )

                    activeScanner
                        .process(image)
                        .addOnSuccessListener { barcodes ->
                            val value = barcodes
                                .asSequence()
                                .mapNotNull { it.rawValue?.trim()?.takeIf(String::isNotEmpty) }
                                .firstOrNull()

                            if (value == null) {
                                lastCandidate = null
                                stableCount = 0
                            } else if (value == lastCandidate) {
                                stableCount += 1
                            } else {
                                lastCandidate = value
                                stableCount = 1
                            }

                            if (value != null && stableCount >= 2 && detectionEnabled.get()) {
                                detectionEnabled.set(false)
                                onBarcodeDetected(value)
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }
            } catch (_: Exception) {
                analyzerAlive = false
            }
        }

        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            analyzerAlive = false
            imageAnalysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
            scanner?.close()
            analyzerExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

// Removed ScannerFrameOverlay and itemNameForBarcode
