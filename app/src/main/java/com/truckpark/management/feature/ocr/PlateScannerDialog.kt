package com.truckpark.management.feature.ocr

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.truckpark.management.data.local.TransporterStore
import java.util.concurrent.Executors

private val CapsuleShape = RoundedCornerShape(999.dp)

/**
 * CameraX Live OCR Scanner Modal Dialog.
 *
 * Fast, robust plate recognition using Google ML Kit and CameraX.
 */
@Composable
fun PlateScannerDialog(
    targetLabel: String,
    scanMode: OcrScanMode = OcrScanMode.PLATE,
    onPlateScanned: (plate: String, matchedCarrier: String?) -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    var isTorchEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var detectedPlateResult by remember { mutableStateOf<OcrScanResult?>(null) }
    var isAnalyzing by remember { mutableStateOf(true) }

    val knownTransporters = remember { TransporterStore.getKnownTransporters(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // User-placed crop box (fractions of the preview). Only this region is analysed.
    var cropRegion by remember(scanMode) {
        mutableStateOf(
            NormalizedRect.centered(
                cx = 0.5f,
                cy = 0.38f,
                width = 0.84f,
                height = if (scanMode == OcrScanMode.TRANSPORTER) 0.30f else 0.20f,
            )
        )
    }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val analyzer = remember(scanMode) {
        PlateTextAnalyzer(
            scanMode = scanMode,
            knownTransporters = knownTransporters,
            requiredConsecutiveFrames = 3,
            onPlateConfirmed = { result ->
                detectedPlateResult = result
                isAnalyzing = false
            },
        ).also { it.cropRegion = cropRegion }
    }

    fun moveCropTo(offset: Offset) {
        if (containerSize.width <= 0 || containerSize.height <= 0) return
        cropRegion = NormalizedRect.centered(
            cx = offset.x / containerSize.width,
            cy = offset.y / containerSize.height,
            width = cropRegion.width,
            height = cropRegion.height,
        )
        analyzer.cropRegion = cropRegion
    }

    LaunchedEffect(Unit) {
        analyzer.reset()
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.reset()
            cameraExecutor.shutdown()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission) {
                // CameraX Preview View
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            // Wait for layout so the viewport (what the user actually sees) is known.
                            previewView.doOnLayout {
                                val cameraProvider = cameraProviderFuture.get()

                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setResolutionSelector(
                                        ResolutionSelector.Builder()
                                            .setResolutionStrategy(
                                                ResolutionStrategy(
                                                    Size(1920, 1080),
                                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                                )
                                            )
                                            .build()
                                    )
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

                                // Share one viewport so analysis frames cover exactly the on-screen area;
                                // the on-screen crop box then maps 1:1 onto the analysed pixels.
                                val groupBuilder = UseCaseGroup.Builder()
                                    .addUseCase(preview)
                                    .addUseCase(imageAnalysis)
                                previewView.viewPort?.let { groupBuilder.setViewPort(it) }

                                try {
                                    cameraProvider.unbindAll()
                                    val cam = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        groupBuilder.build(),
                                    )
                                    cameraControl = cam.cameraControl
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    }
                )

                // Dimmed scrim with a clear window over the user's crop box; tap or drag to move it.
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { containerSize = it }
                        .pointerInput(Unit) {
                            detectTapGestures { offset -> moveCropTo(offset) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> moveCropTo(offset) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    moveCropTo(change.position)
                                },
                            )
                        }
                ) {
                    val l = cropRegion.left * size.width
                    val t = cropRegion.top * size.height
                    val r = cropRegion.right * size.width
                    val b = cropRegion.bottom * size.height
                    val scrim = Color.Black.copy(alpha = 0.55f)
                    drawRect(scrim, topLeft = Offset(0f, 0f), size = ComposeSize(size.width, t))
                    drawRect(scrim, topLeft = Offset(0f, b), size = ComposeSize(size.width, size.height - b))
                    drawRect(scrim, topLeft = Offset(0f, t), size = ComposeSize(l, b - t))
                    drawRect(scrim, topLeft = Offset(r, t), size = ComposeSize(size.width - r, b - t))
                    drawRoundRect(
                        color = if (detectedPlateResult != null) Color(0xFF10B981) else Color(0xFF38BDF8),
                        topLeft = Offset(l, t),
                        size = ComposeSize(r - l, b - t),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }

                // Viewfinder / Reticle Frame
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header Bar (Title, Target Field & Close)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CapsuleShape)
                            .background(Color(0xFF131B2A).copy(alpha = 0.85f))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), CapsuleShape)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF38BDF8).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Scan $targetLabel",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = if (scanMode == OcrScanMode.TRANSPORTER) {
                                        "Tap to place the box on the transporter info"
                                    } else {
                                        "Tap to place the box on the plate"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Flashlight Toggle
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .clickable {
                                        isTorchEnabled = !isTorchEnabled
                                        cameraControl?.enableTorch(isTorchEnabled)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isTorchEnabled) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                    contentDescription = "Torch",
                                    tint = if (isTorchEnabled) Color(0xFFF59E0B) else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Close Button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .clickable(onClick = onDismiss),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Bottom Floating Action Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF131B2A).copy(alpha = 0.95f))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), RoundedCornerShape(20.dp))
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (detectedPlateResult != null) {
                            Text(
                                text = "Detected Successfully",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF10B981)
                                )
                            )
                            Text(
                                text = detectedPlateResult?.plateNumber.orEmpty(),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White,
                                    letterSpacing = 2.sp,
                                    textAlign = TextAlign.Center,
                                )
                            )
                            detectedPlateResult?.matchedCarrier?.let { carrier ->
                                if (scanMode != OcrScanMode.TRANSPORTER) {
                                    Text(
                                        text = "Carrier: $carrier",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
                                        )
                                    )
                                }
                            }

                            // Confirm & Apply Scanned Result
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CapsuleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF10B981), Color(0xFF059669))
                                        )
                                    )
                                    .clickable {
                                        val res = detectedPlateResult ?: return@clickable
                                        onPlateScanned(res.plateNumber, res.matchedCarrier)
                                        onDismiss()
                                    }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Use Scanned Value (${detectedPlateResult?.plateNumber})",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                )
                            }

                            // Re-Scan Button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CapsuleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .clickable {
                                        analyzer.reset()
                                        detectedPlateResult = null
                                        isAnalyzing = true
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Re-scan / Try Again",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF38BDF8)
                                )
                                Text(
                                    text = "Scanning only inside the box...",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                )
                            }
                            Text(
                                text = if (scanMode == OcrScanMode.TRANSPORTER) {
                                    "Tap or drag to place the box on the transporter name"
                                } else {
                                    "Tap or drag to place the box exactly on the plate"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    }
                }
            } else {
                // Permission Denied / Error State
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF131B2A))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), RoundedCornerShape(24.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = "Camera Permission Required",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "TruckPark needs camera access to perform live OCR license plate scanning and vehicle identification.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CapsuleShape)
                            .background(Color(0xFF38BDF8))
                            .clickable {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Grant Permission",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        )
                    }
                }
            }
        }
    }
}
