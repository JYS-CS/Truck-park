package com.truckpark.management.feature.visits

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.truckpark.management.core.designsystem.components.GlassCard
import com.truckpark.management.core.designsystem.theme.TruckParkExtras
import com.truckpark.management.domain.models.CargoItem
import com.truckpark.management.domain.models.CustomVisitField
import com.truckpark.management.domain.models.SADC_COUNTRIES
import com.truckpark.management.domain.models.SealCondition
import com.truckpark.management.domain.models.TruckVisit
import com.truckpark.management.domain.models.VisitCorrectionDraft
import com.truckpark.management.domain.models.VisitCorrectionRecord
import com.truckpark.management.domain.models.VisitFieldCategory
import com.truckpark.management.domain.models.VisitSeal
import com.truckpark.management.domain.models.VisitStatus
import com.truckpark.management.domain.models.VisitType
import com.truckpark.management.domain.models.cleanDisplayNotes
import com.truckpark.management.domain.models.countryFlagForCode
import com.truckpark.management.domain.models.countryNameForCode
import com.truckpark.management.domain.models.extractPlateInfo
import com.truckpark.management.domain.models.normalizePlate
import com.truckpark.management.feature.ocr.OcrScanMode
import com.truckpark.management.feature.ocr.PlateScannerDialog
import com.truckpark.management.feature.ocr.PlateTextAnalyzer
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault())

internal fun formatVisitTimestamp(instant: Instant): String = TIMESTAMP_FORMATTER.format(instant)

internal fun saveSealBitmapToFilesDir(context: Context, bitmap: Bitmap): String? = try {
    val dir = File(context.filesDir, "seal_photos").apply { mkdirs() }
    val outFile = File(dir, "seal_${UUID.randomUUID()}.jpg")
    FileOutputStream(outFile).use { fos ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
    }
    outFile.absolutePath
} catch (_: Exception) {
    null
}

internal fun copyImageUriToFilesDir(context: Context, uri: Uri): String? {
    return try {
        val dir = File(context.filesDir, "seal_photos").apply { mkdirs() }
        val outFile = File(dir, "seal_${UUID.randomUUID()}.jpg")
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        if (copied != null) outFile.absolutePath else null
    } catch (_: Exception) {
        null
    }
}

private val SEAL_TYPES = listOf("Bolt", "Cable", "Plastic", "Customs", "Other")
private val CARGO_UNITS = listOf("Bags", "Pallets", "Tonnes", "Containers", "Drums", "Other")

/**
 * Operational Cargo & Seal editor used inside Arrival Check-In and Visit Corrections.
 * Keeps data strictly operational (no cargo value, charges, or payments).
 */
@Composable
internal fun CargoAndSealsEditorCard(
    cargoDescription: String,
    onCargoDescriptionChange: (String) -> Unit,
    cargoItems: List<CargoItem>,
    onCargoItemsChange: (List<CargoItem>) -> Unit,
    seals: List<VisitSeal>,
    onSealsChange: (List<VisitSeal>) -> Unit,
    customFields: List<CustomVisitField> = emptyList(),
    onCustomFieldsChange: (List<CustomVisitField>) -> Unit = {},
    dark: Boolean,
    initiallyExpanded: Boolean = false,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors
    val cargoCustomCount = customFields.count { it.category == VisitFieldCategory.Cargo }
    var expanded by remember {
        mutableStateOf(
            initiallyExpanded ||
                cargoDescription.isNotBlank() ||
                cargoItems.isNotEmpty() ||
                seals.isNotEmpty() ||
                cargoCustomCount > 0,
        )
    }

    // Inline add-cargo-item form state
    var showAddCargoForm by remember { mutableStateOf(false) }
    var newCargoDesc by remember { mutableStateOf("") }
    var newCargoQty by remember { mutableStateOf("") }
    var newCargoUnit by remember { mutableStateOf("Bags") }
    var customCargoUnitReason by remember { mutableStateOf("") }
    var newCargoNotes by remember { mutableStateOf("") }

    // Inline add-seal form state
    var showAddSealForm by remember { mutableStateOf(false) }
    var newSealNumber by remember { mutableStateOf("") }
    var newSealType by remember { mutableStateOf("Bolt") }
    var customSealTypeReason by remember { mutableStateOf("") }
    var newSealCondition by remember { mutableStateOf(SealCondition.Intact) }
    var newSealNotes by remember { mutableStateOf("") }

    // Target seal ID awaiting photo capture / gallery pick
    var pendingPhotoSealId by remember { mutableStateOf<String?>(null) }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        val targetId = pendingPhotoSealId
        pendingPhotoSealId = null
        if (bitmap != null && targetId != null) {
            val savedPath = saveSealBitmapToFilesDir(context, bitmap)
            if (savedPath != null) {
                onSealsChange(
                    seals.map { if (it.id == targetId) it.copy(localPhotoPath = savedPath) else it },
                )
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingPhotoSealId != null) {
            takePhotoLauncher.launch(null)
        } else {
            pendingPhotoSealId = null
        }
    }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        val targetId = pendingPhotoSealId
        pendingPhotoSealId = null
        if (uri != null && targetId != null) {
            val savedPath = copyImageUriToFilesDir(context, uri)
            if (savedPath != null) {
                onSealsChange(
                    seals.map { if (it.id == targetId) it.copy(localPhotoPath = savedPath) else it },
                )
            }
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { expanded = !expanded },
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "CARGO & SEALS (OPTIONAL)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.primary,
                        letterSpacing = 0.8.sp,
                    ),
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Quick Cargo Description
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Quick Cargo Description",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                            ),
                        )
                        LiquidTextInput(
                            value = cargoDescription,
                            onValueChange = { onCargoDescriptionChange(it.take(300)) },
                            placeholder = "",
                            maxLines = 2,
                            dark = dark,
                        )
                    }

                    // Structured Cargo Records
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Structured Cargo Items (${cargoItems.size})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                            ),
                        )
                        SmallGlassChipButton(
                            label = if (showAddCargoForm) "Close" else "+ Add Item",
                            dark = dark,
                            onClick = { showAddCargoForm = !showAddCargoForm },
                        )
                    }

                    AnimatedVisibility(visible = showAddCargoForm) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.28f else 0.45f))
                                .border(BorderStroke(1.dp, extras.glassBorder), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LiquidTextInput(
                                value = newCargoDesc,
                                onValueChange = { newCargoDesc = it.take(120) },
                                placeholder = "Cargo description",
                                dark = dark,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LiquidTextInput(
                                    value = newCargoQty,
                                    onValueChange = { newCargoQty = it.take(20) },
                                    placeholder = "Quantity",
                                    modifier = Modifier.weight(1f),
                                    dark = dark,
                                )
                                LiquidTextInput(
                                    value = newCargoUnit,
                                    onValueChange = { newCargoUnit = it.take(24) },
                                    placeholder = "Unit",
                                    modifier = Modifier.weight(1f),
                                    dark = dark,
                                )
                            }
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                CARGO_UNITS.forEach { unit ->
                                    SelectableSmallChip(
                                        label = unit,
                                        selected = newCargoUnit == unit,
                                        accent = scheme.primary,
                                        dark = dark,
                                        onClick = { newCargoUnit = unit },
                                    )
                                }
                            }
                            if (newCargoUnit.equals("Other", ignoreCase = true) || newCargoUnit !in CARGO_UNITS) {
                                LiquidTextInput(
                                    value = customCargoUnitReason,
                                    onValueChange = { customCargoUnitReason = it.take(80) },
                                    placeholder = "",
                                    dark = dark,
                                )
                            }
                            LiquidTextInput(
                                value = newCargoNotes,
                                onValueChange = { newCargoNotes = it.take(120) },
                                placeholder = "",
                                dark = dark,
                            )
                            LiquidActionButton(
                                text = "Save Cargo Item",
                                enabled = newCargoDesc.isNotBlank(),
                                dark = dark,
                                onClick = {
                                    val resolvedUnit = if (newCargoUnit.equals("Other", ignoreCase = true) && customCargoUnitReason.isNotBlank()) {
                                        "Other (${customCargoUnitReason.trim()})"
                                    } else {
                                        newCargoUnit.trim()
                                    }
                                    onCargoItemsChange(
                                        cargoItems + CargoItem(
                                            id = UUID.randomUUID().toString(),
                                            description = newCargoDesc.trim(),
                                            quantity = newCargoQty.trim().takeIf { it.isNotBlank() },
                                            unit = resolvedUnit.takeIf { it.isNotBlank() },
                                            notes = newCargoNotes.trim().takeIf { it.isNotBlank() },
                                        ),
                                    )
                                    newCargoDesc = ""
                                    newCargoQty = ""
                                    customCargoUnitReason = ""
                                    newCargoNotes = ""
                                    showAddCargoForm = false
                                },
                            )
                        }
                    }

                    cargoItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.25f else 0.35f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.onSurface,
                                    ),
                                )
                                val qtyText = listOfNotNull(item.quantity, item.unit).joinToString(" ")
                                if (qtyText.isNotBlank()) {
                                    Text(
                                        text = qtyText,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = scheme.onSurfaceVariant,
                                        ),
                                    )
                                }
                                item.notes?.takeIf { it.isNotBlank() }?.let { note ->
                                    Text(
                                        text = note,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            color = scheme.onSurfaceVariant,
                                        ),
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove item",
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable {
                                        onCargoItemsChange(cargoItems.filterNot { it.id == item.id })
                                    },
                            )
                        }
                    }

                    // Seals Register Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Container & Trailer Seals (${seals.size})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                            ),
                        )
                        SmallGlassChipButton(
                            label = if (showAddSealForm) "Close" else "+ Add Seal",
                            dark = dark,
                            onClick = { showAddSealForm = !showAddSealForm },
                        )
                    }

                    AnimatedVisibility(visible = showAddSealForm) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.28f else 0.45f))
                                .border(BorderStroke(1.dp, extras.glassBorder), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LiquidTextInput(
                                value = newSealNumber,
                                onValueChange = { newSealNumber = it.uppercase().take(40) },
                                placeholder = "Seal number",
                                isMonospace = true,
                                leadingIcon = Icons.Default.Lock,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                dark = dark,
                            )
                            Text(
                                text = "Seal Type",
                                style = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant),
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                SEAL_TYPES.forEach { t ->
                                    SelectableSmallChip(
                                        label = t,
                                        selected = newSealType == t,
                                        accent = scheme.primary,
                                        dark = dark,
                                        onClick = { newSealType = t },
                                    )
                                }
                            }
                            if (newSealType.equals("Other", ignoreCase = true)) {
                                LiquidTextInput(
                                    value = customSealTypeReason,
                                    onValueChange = { customSealTypeReason = it.take(80) },
                                    placeholder = "",
                                    dark = dark,
                                )
                            }
                            Text(
                                text = "Condition",
                                style = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant),
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                SealCondition.entries.forEach { cond ->
                                    val accent = if (cond == SealCondition.Intact) Color(0xFF10B981) else Color(0xFFEF4444)
                                    SelectableSmallChip(
                                        label = cond.label,
                                        selected = newSealCondition == cond,
                                        accent = accent,
                                        dark = dark,
                                        onClick = { newSealCondition = cond },
                                    )
                                }
                            }
                            if (newSealCondition != SealCondition.Intact || newSealType.equals("Other", ignoreCase = true)) {
                                LiquidTextInput(
                                    value = newSealNotes,
                                    onValueChange = { newSealNotes = it.take(150) },
                                    placeholder = "",
                                    dark = dark,
                                )
                            }
                            LiquidActionButton(
                                text = "Add Seal",
                                enabled = newSealNumber.isNotBlank(),
                                dark = dark,
                                onClick = {
                                    val resolvedSealType = if (newSealType.equals("Other", ignoreCase = true) && customSealTypeReason.isNotBlank()) {
                                        "Other (${customSealTypeReason.trim()})"
                                    } else {
                                        newSealType
                                    }
                                    onSealsChange(
                                        seals + VisitSeal(
                                            id = UUID.randomUUID().toString(),
                                            sealNumber = newSealNumber.trim(),
                                            sealType = resolvedSealType,
                                            condition = newSealCondition,
                                            notes = newSealNotes.trim().takeIf { it.isNotBlank() },
                                        ),
                                    )
                                    newSealNumber = ""
                                    customSealTypeReason = ""
                                    newSealNotes = ""
                                    newSealCondition = SealCondition.Intact
                                    showAddSealForm = false
                                },
                            )
                        }
                    }

                    seals.forEach { seal ->
                        val condColor = when (seal.condition) {
                            SealCondition.Intact -> Color(0xFF10B981)
                            SealCondition.Broken, SealCondition.Missing, SealCondition.Tampered -> Color(0xFFEF4444)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.25f else 0.35f))
                                .border(
                                    BorderStroke(
                                        0.8.dp,
                                        if (seal.condition == SealCondition.Intact) extras.glassBorder else condColor.copy(alpha = 0.5f),
                                    ),
                                    RoundedCornerShape(10.dp),
                                )
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = seal.sealNumber,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = scheme.onSurface,
                                        ),
                                    )
                                    Text(
                                        text = "${seal.sealType} • ${seal.condition.label}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = condColor,
                                        ),
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove seal",
                                    tint = scheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable {
                                            seal.localPhotoPath?.let { path ->
                                                try { File(path).delete() } catch (_: Exception) {}
                                            }
                                            onSealsChange(seals.filterNot { it.id == seal.id })
                                        },
                                )
                            }
                            seal.notes?.let { n ->
                                Text(
                                    text = n,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = scheme.onSurfaceVariant,
                                    ),
                                )
                            }
                            // Damage / loss photo attachment bar
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SealPhotoThumbnail(path = seal.localPhotoPath)
                                SmallGlassChipButton(
                                    label = "Camera",
                                    icon = Icons.Default.CameraAlt,
                                    dark = dark,
                                    onClick = {
                                        pendingPhotoSealId = seal.id
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.CAMERA,
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            takePhotoLauncher.launch(null)
                                        } else {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                )
                                SmallGlassChipButton(
                                    label = "Gallery",
                                    icon = Icons.Default.PhotoLibrary,
                                    dark = dark,
                                    onClick = {
                                        pendingPhotoSealId = seal.id
                                        pickPhotoLauncher.launch("image/*")
                                    },
                                )
                            }
                        }
                    }

                    CategoryCustomFieldsEditor(
                        category = VisitFieldCategory.Cargo,
                        customFields = customFields,
                        onCustomFieldsChange = onCustomFieldsChange,
                        dark = dark,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CargoAndSealsReadOnlySection(
    visit: TruckVisit,
    customFields: List<CustomVisitField> = emptyList(),
    dark: Boolean,
) {
    val cargoCustomFields = customFields.filter { it.category == VisitFieldCategory.Cargo }
    val hasCargoOrSeals = !visit.cargoDescription.isNullOrBlank() ||
        visit.cargoItems.isNotEmpty() ||
        visit.seals.isNotEmpty() ||
        cargoCustomFields.isNotEmpty()
    if (!hasCargoOrSeals) return

    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors
    var zoomedPhotoPath by remember { mutableStateOf<String?>(null) }

    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "CARGO & SEALS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.primary,
                        letterSpacing = 0.8.sp,
                    ),
                )
            }

            visit.cargoDescription?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = scheme.onSurface,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }

            visit.cargoItems.forEach { item ->
                val qty = listOfNotNull(item.quantity, item.unit).joinToString(" ")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.25f else 0.4f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = scheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    if (qty.isNotBlank()) {
                        Text(
                            text = qty,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = scheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }

            visit.seals.forEach { seal ->
                val condColor = if (seal.condition == SealCondition.Intact) Color(0xFF10B981) else Color(0xFFEF4444)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.25f else 0.4f))
                        .border(BorderStroke(0.8.dp, extras.glassBorder), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = seal.sealNumber,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface,
                                ),
                            )
                            Text(
                                text = "${seal.sealType} • ${seal.condition.label}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = condColor,
                                ),
                            )
                        }
                        seal.notes?.let { n ->
                            Text(
                                text = n,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = scheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                    seal.localPhotoPath?.let { path ->
                        Box(modifier = Modifier.clickable { zoomedPhotoPath = path }) {
                            SealPhotoThumbnail(path = path)
                        }
                    }
                }
            }

            CategoryCustomFieldsReadOnlyList(
                category = VisitFieldCategory.Cargo,
                customFields = customFields,
                dark = dark,
            )
        }
    }

    zoomedPhotoPath?.let { path ->
        val bmp = remember(path) { BitmapFactory.decodeFile(path) }
        Dialog(onDismissRequest = { zoomedPhotoPath = null }) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { zoomedPhotoPath = null }
                    .padding(12.dp),
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Seal damage photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Text(
                        text = "Photo file is no longer available on this device.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ConflictResolutionCard(
    visit: TruckVisit,
    canResolve: Boolean,
    isBusy: Boolean,
    dark: Boolean,
    onKeepDevice: () -> Unit,
    onUseServer: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val amber = Color(0xFFF59E0B)

    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = amber,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "SYNC CONFLICT DETECTED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = amber,
                        letterSpacing = 0.8.sp,
                    ),
                )
            }
            Text(
                text = visit.syncError ?: "This record clashes with updates on the server.",
                style = MaterialTheme.typography.bodySmall.copy(color = scheme.onSurface),
            )
            visit.conflictServerSnapshot?.let { snap ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.5f))
                        .padding(10.dp),
                ) {
                    Text(
                        text = "Server version: $snap",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant,
                        ),
                    )
                }
            }
            if (canResolve) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CapsuleShape)
                            .background(scheme.primary)
                            .clickable(enabled = !isBusy, onClick = onKeepDevice)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isBusy) "Working..." else "Keep This Device",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CapsuleShape)
                            .background(scheme.surfaceVariant)
                            .clickable(enabled = !isBusy, onClick = onUseServer)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Use Server Copy",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                            ),
                        )
                    }
                }
            } else {
                Text(
                    text = "Supervisor or Admin permission is required to resolve this conflict.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun VisitCorrectionHistoryCard(
    history: List<VisitCorrectionRecord>,
    dark: Boolean,
) {
    if (history.isEmpty()) return
    val scheme = MaterialTheme.colorScheme

    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "CORRECTION HISTORY (${history.size})",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.primary,
                        letterSpacing = 0.8.sp,
                    ),
                )
            }

            history.forEach { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.25f else 0.4f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${entry.editedByName ?: "Staff"} • ${formatVisitTimestamp(entry.editedAt)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                            ),
                        )
                        if (entry.wasCritical) {
                            Text(
                                text = "POST-DEPARTURE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFF59E0B),
                                ),
                            )
                        }
                    }
                    Text(
                        text = entry.changesSummary,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = scheme.onSurface,
                        ),
                    )
                    entry.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                        Text(
                            text = "Reason: $reason",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = scheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun VisitCorrectionDialog(
    visit: TruckVisit,
    isSubmitting: Boolean,
    errorMessage: String?,
    dark: Boolean,
    onDismiss: () -> Unit,
    onSave: (VisitCorrectionDraft) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors
    val initialInfo = remember(visit.id, visit.plateNumber, visit.arrivalNotes, visit.departureNotes) {
        extractPlateInfo(visit.plateNumber, visit.arrivalNotes, visit.departureNotes)
    }

    var countryCode by remember { mutableStateOf(initialInfo.country.ifBlank { "ZM" }) }
    var customCountryReason by remember { mutableStateOf(initialInfo.customCountryReason.orEmpty()) }
    var frontPlate by remember { mutableStateOf(initialInfo.frontPlate) }
    var horsePlate by remember { mutableStateOf(initialInfo.horsePlate) }
    var transporter by remember { mutableStateOf(initialInfo.transporter.orEmpty()) }
    var visitType by remember { mutableStateOf(visit.visitType) }
    var customPurpose by remember { mutableStateOf(initialInfo.customPurpose.orEmpty()) }
    var driverName by remember { mutableStateOf(visit.driverName.orEmpty()) }
    var driverPhoneDigits by remember {
        mutableStateOf(visit.driverPhone?.filter { it.isDigit() }.orEmpty())
    }
    var arrivalNotes by remember {
        mutableStateOf(cleanDisplayNotes(visit.arrivalNotes).orEmpty())
    }
    var departureNotes by remember {
        mutableStateOf(cleanDisplayNotes(visit.departureNotes).orEmpty())
    }
    var exitVerifiedPlate by remember { mutableStateOf(initialInfo.exitVerifiedPlate.orEmpty()) }
    var cargoDescription by remember { mutableStateOf(visit.cargoDescription.orEmpty()) }
    var cargoItems by remember { mutableStateOf(visit.cargoItems) }
    var seals by remember { mutableStateOf(visit.seals) }
    var customFields by remember { mutableStateOf(initialInfo.customFields) }
    var reason by remember { mutableStateOf("") }
    var activeScannerTarget by remember { mutableStateOf<String?>(null) }

    val isCompleted = visit.status == VisitStatus.Completed
    val isPhoneValid = driverPhoneDigits.isEmpty() || driverPhoneDigits.length in 7..15
    val canSubmit = !isSubmitting &&
        countryCode.trim().isNotEmpty() &&
        normalizePlate(frontPlate).isNotEmpty() &&
        normalizePlate(horsePlate).isNotEmpty() &&
        isPhoneValid &&
        (!isCompleted || reason.trim().isNotEmpty())

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.68f))
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (dark) Color(0xFF0F172A) else Color(0xFFF8FAFC))
                    .border(BorderStroke(1.dp, extras.glassBorder), RoundedCornerShape(24.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Column {
                            Text(
                                text = if (isCompleted) "Correct Completed Visit" else "Edit Visit Details",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = scheme.onSurface,
                                ),
                            )
                            Text(
                                text = "Original arrival & departure times are preserved.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = scheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(extras.glassFill)
                            .clickable(enabled = !isSubmitting, onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = scheme.onSurface,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SadcCountrySelectorField(
                        countryCode = countryCode,
                        onCountryCodeChange = { countryCode = it },
                        customCountryReason = customCountryReason,
                        onCustomCountryReasonChange = { customCountryReason = it },
                        dark = dark,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Front Plate",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                            ),
                        )
                        SmallGlassChipButton(
                            label = "Scan",
                            icon = Icons.Default.CameraAlt,
                            dark = dark,
                            onClick = { activeScannerTarget = "FRONT" },
                        )
                    }
                    LiquidTextInput(
                        value = frontPlate,
                        onValueChange = { frontPlate = it.uppercase().take(20) },
                        placeholder = "Front Plate",
                        isMonospace = true,
                        dark = dark,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Horse / Trailer Plate",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                            ),
                        )
                        SmallGlassChipButton(
                            label = "Scan",
                            icon = Icons.Default.CameraAlt,
                            dark = dark,
                            onClick = { activeScannerTarget = "HORSE" },
                        )
                    }
                    LiquidTextInput(
                        value = horsePlate,
                        onValueChange = { horsePlate = it.uppercase().take(20) },
                        placeholder = "Horse / Trailer Plate",
                        isMonospace = true,
                        dark = dark,
                    )
                    CategoryCustomFieldsEditor(
                        category = VisitFieldCategory.Vehicle,
                        customFields = customFields,
                        onCustomFieldsChange = { customFields = it },
                        dark = dark,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Transporter / Carrier",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                            ),
                        )
                        SmallGlassChipButton(
                            label = "Scan",
                            icon = Icons.Default.CameraAlt,
                            dark = dark,
                            onClick = { activeScannerTarget = "TRANSPORTER" },
                        )
                    }
                    LiquidTextInput(
                        value = transporter,
                        onValueChange = { transporter = it.take(60) },
                        placeholder = "Transporter / Carrier",
                        dark = dark,
                    )
                    CategoryCustomFieldsEditor(
                        category = VisitFieldCategory.Transporter,
                        customFields = customFields,
                        onCustomFieldsChange = { customFields = it },
                        dark = dark,
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        VisitType.entries.forEach { t ->
                            SelectableSmallChip(
                                label = t.label,
                                selected = visitType == t,
                                accent = scheme.primary,
                                dark = dark,
                                onClick = { visitType = t },
                            )
                        }
                    }
                    if (visitType == VisitType.Other) {
                        LiquidTextInput(
                            value = customPurpose,
                            onValueChange = { customPurpose = it.take(80) },
                            placeholder = "Specify other visit purpose / reason...",
                            dark = dark,
                        )
                    }
                    CategoryCustomFieldsEditor(
                        category = VisitFieldCategory.Purpose,
                        customFields = customFields,
                        onCustomFieldsChange = { customFields = it },
                        dark = dark,
                    )

                    LiquidTextInput(
                        value = driverName,
                        onValueChange = { driverName = it.take(80) },
                        placeholder = "Driver Name",
                        dark = dark,
                    )
                    LiquidInternationalPhoneInput(
                        phoneDigits = driverPhoneDigits,
                        onPhoneDigitsChange = { driverPhoneDigits = it },
                        isValid = isPhoneValid,
                        dark = dark,
                    )
                    CategoryCustomFieldsEditor(
                        category = VisitFieldCategory.Driver,
                        customFields = customFields,
                        onCustomFieldsChange = { customFields = it },
                        dark = dark,
                    )

                    LiquidTextInput(
                        value = arrivalNotes,
                        onValueChange = { arrivalNotes = it.take(300) },
                        placeholder = "Arrival notes",
                        maxLines = 3,
                        dark = dark,
                    )
                    CategoryCustomFieldsEditor(
                        category = VisitFieldCategory.Notes,
                        customFields = customFields,
                        onCustomFieldsChange = { customFields = it },
                        dark = dark,
                    )

                    if (isCompleted) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Exit Verified Plate",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface,
                                ),
                            )
                            SmallGlassChipButton(
                                label = "Scan",
                                icon = Icons.Default.CameraAlt,
                                dark = dark,
                                onClick = { activeScannerTarget = "EXIT" },
                            )
                        }
                        LiquidTextInput(
                            value = exitVerifiedPlate,
                            onValueChange = { exitVerifiedPlate = it.uppercase().take(20) },
                            placeholder = "Exit verified plate (optional)",
                            isMonospace = true,
                            dark = dark,
                        )
                        LiquidTextInput(
                            value = departureNotes,
                            onValueChange = { departureNotes = it.take(300) },
                            placeholder = "Departure notes",
                            maxLines = 3,
                            dark = dark,
                        )
                        CategoryCustomFieldsEditor(
                            category = VisitFieldCategory.Departure,
                            customFields = customFields,
                            onCustomFieldsChange = { customFields = it },
                            dark = dark,
                        )
                    }
                    CargoAndSealsEditorCard(
                        cargoDescription = cargoDescription,
                        onCargoDescriptionChange = { cargoDescription = it },
                        cargoItems = cargoItems,
                        onCargoItemsChange = { cargoItems = it },
                        seals = seals,
                        onSealsChange = { seals = it },
                        customFields = customFields,
                        onCustomFieldsChange = { customFields = it },
                        dark = dark,
                        initiallyExpanded = false,
                    )
                    LiquidTextInput(
                        value = reason,
                        onValueChange = { reason = it.take(300) },
                        placeholder = if (isCompleted) "Correction reason (required) *" else "Reason for edit (optional)",
                        maxLines = 2,
                        dark = dark,
                    )
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = scheme.error,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CapsuleShape)
                        .background(
                            if (canSubmit) {
                                Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF38BDF8)))
                            } else {
                                Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f)))
                            },
                        )
                        .clickable(enabled = canSubmit) {
                            onSave(
                                VisitCorrectionDraft(
                                    visitId = visit.id,
                                    countryCode = countryCode.trim().uppercase().ifBlank { "ZM" },
                                    customCountryReason = customCountryReason.trim().takeIf { it.isNotBlank() },
                                    frontPlate = normalizePlate(frontPlate),
                                    horsePlate = normalizePlate(horsePlate),
                                    transporter = transporter.trim().takeIf { it.isNotBlank() },
                                    visitType = visitType,
                                    customPurpose = customPurpose.trim().takeIf { it.isNotBlank() && visitType == VisitType.Other },
                                    driverName = driverName.trim().takeIf { it.isNotBlank() },
                                    driverPhone = if (driverPhoneDigits.isNotEmpty()) "+$driverPhoneDigits" else null,
                                    arrivalNotes = arrivalNotes.trim().takeIf { it.isNotBlank() },
                                    departureNotes = departureNotes.trim().takeIf { it.isNotBlank() },
                                    exitVerifiedPlate = exitVerifiedPlate.trim().takeIf { it.isNotBlank() },
                                    cargoDescription = cargoDescription.trim().takeIf { it.isNotBlank() },
                                    cargoItems = cargoItems,
                                    seals = seals,
                                    customFields = customFields,
                                    reason = reason.trim().takeIf { it.isNotBlank() },
                                ),
                            )
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Save Changes",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    activeScannerTarget?.let { target ->
        val scanTitle = when (target) {
            "FRONT" -> "Scan Front Plate"
            "HORSE" -> "Scan Horse / Trailer Plate"
            "EXIT" -> "Scan Exit Verification Plate"
            else -> "Scan Transporter Marking"
        }
        val scanMode = if (target == "TRANSPORTER") OcrScanMode.TRANSPORTER else OcrScanMode.PLATE
        PlateScannerDialog(
            targetLabel = scanTitle,
            scanMode = scanMode,
            dark = dark,
            onDismiss = { activeScannerTarget = null },
            onPlateScanned = { scannedValue, matchedCarrier ->
                when (target) {
                    "FRONT" -> frontPlate = scannedValue.uppercase().take(20)
                    "HORSE" -> horsePlate = scannedValue.uppercase().take(20)
                    "EXIT" -> exitVerifiedPlate = scannedValue.uppercase().take(20)
                    "TRANSPORTER" -> transporter = (matchedCarrier ?: scannedValue).take(60)
                }
                activeScannerTarget = null
            },
        )
    }
}

@Composable
private fun SealPhotoThumbnail(path: String?) {
    if (path.isNullOrBlank()) return
    val bmp = remember(path) { BitmapFactory.decodeFile(path) } ?: return
    Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = "Seal photo",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), RoundedCornerShape(8.dp)),
    )
}

@Composable
private fun SmallGlassChipButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(CapsuleShape)
            .background(scheme.primary.copy(alpha = if (dark) 0.20f else 0.12f))
            .border(BorderStroke(0.8.dp, scheme.primary.copy(alpha = 0.4f)), CapsuleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = scheme.primary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SelectableSmallChip(
    label: String,
    selected: Boolean,
    accent: Color,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors
    Box(
        modifier = Modifier
            .clip(CapsuleShape)
            .background(if (selected) accent.copy(alpha = if (dark) 0.28f else 0.16f) else extras.glassFill)
            .border(
                BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.7f) else extras.glassBorder),
                CapsuleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) accent else scheme.onSurfaceVariant,
            ),
            maxLines = 1,
        )
    }
}

private val COMMON_COUNTRY_NAME_CODES: Map<String, String> by lazy {
    val map = mutableMapOf(
        "zambia" to "ZM",
        "south africa" to "ZA",
        "rsa" to "ZA",
        "zimbabwe" to "ZW",
        "dr congo" to "CD",
        "drc" to "CD",
        "democratic republic of congo" to "CD",
        "democratic republic of the congo" to "CD",
        "congo" to "CG",
        "tanzania" to "TZ",
        "malawi" to "MW",
        "botswana" to "BW",
        "mozambique" to "MZ",
        "namibia" to "NA",
        "angola" to "AO",
        "lesotho" to "LS",
        "eswatini" to "SZ",
        "swaziland" to "SZ",
        "madagascar" to "MG",
        "mauritius" to "MU",
        "seychelles" to "SC",
        "comoros" to "KM",
        "kenya" to "KE",
        "uganda" to "UG",
        "rwanda" to "RW",
        "burundi" to "BI",
        "ethiopia" to "ET",
        "somalia" to "SO",
        "south sudan" to "SS",
        "sudan" to "SD",
        "nigeria" to "NG",
        "ghana" to "GH",
        "egypt" to "EG",
        "cameroon" to "CM",
        "gabon" to "GA",
        "chad" to "TD",
        "djibouti" to "DJ",
        "eritrea" to "ER",
        "morocco" to "MA",
        "algeria" to "DZ",
        "tunisia" to "TN",
        "libya" to "LY",
        "senegal" to "SN",
        "ivory coast" to "CI",
        "cote d'ivoire" to "CI",
        "mali" to "ML",
        "burkina faso" to "BF",
        "niger" to "NE",
        "guinea" to "GN",
        "sierra leone" to "SL",
        "liberia" to "LR",
        "togo" to "TG",
        "benin" to "BJ",
    )
    for (iso in java.util.Locale.getISOCountries()) {
        val display = java.util.Locale("", iso).getDisplayCountry(java.util.Locale.ENGLISH)
            .trim()
            .lowercase()
        if (display.isNotBlank() && !map.containsKey(display)) {
            map[display] = iso.uppercase()
        }
    }
    map
}

internal fun resolveCountryCodeFromName(countryName: String): String {
    val clean = countryName.trim()
    if (clean.isEmpty()) return ""
    val lower = clean.lowercase()
    COMMON_COUNTRY_NAME_CODES[lower]?.let { return it }
    COMMON_COUNTRY_NAME_CODES.entries.firstOrNull { (k, _) ->
        k.startsWith(lower) || lower.startsWith(k)
    }?.let { return it.value }

    val words = clean.uppercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    return if (words.size >= 2) {
        "${words[0].first()}${words[1].first()}"
    } else {
        clean.uppercase().filter { it in 'A'..'Z' }.take(2)
    }
}

/**
 * SADC Country selector:
 * Pressing the ZM country box itself drops down the SADC country list + "Other".
 * Selecting "Other" lets the user type the full country name and automatically resolves the country code and flag.
 */
@Composable
internal fun SadcCountrySelectorField(
    countryCode: String,
    onCountryCodeChange: (String) -> Unit,
    customCountryReason: String,
    onCustomCountryReasonChange: (String) -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors
    val cleanCode = countryCode.trim().uppercase()
    val matchedSadc = SADC_COUNTRIES.firstOrNull { it.code == cleanCode }

    var isDropdownOpen by remember { mutableStateOf(false) }
    var isOtherSelected by remember {
        mutableStateOf(
            customCountryReason.isNotBlank() || (cleanCode.isNotEmpty() && matchedSadc == null),
        )
    }

    val activeFlag = countryFlagForCode(cleanCode.ifBlank { "ZM" })
    val displayTitle = when {
        isOtherSelected && customCountryReason.isNotBlank() -> customCountryReason.trim()
        isOtherSelected -> "Other Country"
        matchedSadc != null -> matchedSadc.name
        cleanCode.isNotBlank() -> "Other ($cleanCode)"
        else -> "Zambia"
    }
    val displayCode = cleanCode.ifBlank { if (isOtherSelected) "--" else "ZM" }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The ZM / Country Box itself (tap to toggle dropdown)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(extras.glassFill)
                .border(
                    BorderStroke(
                        if (isDropdownOpen) 1.4.dp else 1.dp,
                        if (isDropdownOpen) scheme.primary else extras.glassBorder,
                    ),
                    RoundedCornerShape(12.dp),
                )
                .clickable { isDropdownOpen = !isDropdownOpen }
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = activeFlag,
                        fontSize = 20.sp,
                    )
                    Column {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                            ),
                        )
                        Text(
                            text = if (isOtherSelected) "Custom non-SADC country" else "Tap box to switch country",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = scheme.onSurfaceVariant,
                            ),
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CapsuleShape)
                            .background(scheme.primary.copy(alpha = if (dark) 0.20f else 0.12f))
                            .border(BorderStroke(0.8.dp, scheme.primary.copy(alpha = 0.45f)), CapsuleShape)
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = displayCode,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                color = scheme.primary,
                            ),
                        )
                    }
                    Icon(
                        imageVector = if (isDropdownOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isDropdownOpen) "Close countries" else "Open countries",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // Dropdown Panel when the ZM Box is pressed
        AnimatedVisibility(visible = isDropdownOpen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.40f else 0.65f))
                    .border(BorderStroke(1.dp, extras.glassBorder), RoundedCornerShape(14.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SADC_COUNTRIES.forEach { country ->
                    val isSelected = !isOtherSelected && cleanCode == country.code
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) scheme.primary.copy(alpha = if (dark) 0.24f else 0.14f)
                                else Color.Transparent,
                            )
                            .clickable {
                                isOtherSelected = false
                                isDropdownOpen = false
                                onCustomCountryReasonChange("")
                                onCountryCodeChange(country.code)
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = country.flag,
                                fontSize = 18.sp,
                            )
                            Text(
                                text = country.name,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = scheme.onSurface,
                                ),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = country.code,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                                ),
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }

                // "Other" option at the bottom of the dropdown
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isOtherSelected) scheme.primary.copy(alpha = if (dark) 0.24f else 0.14f)
                            else Color.Transparent,
                        )
                        .clickable {
                            isOtherSelected = true
                            isDropdownOpen = false
                            if (matchedSadc != null) {
                                onCountryCodeChange("")
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "\uD83C\uDF0D",
                            fontSize = 18.sp,
                        )
                        Column {
                            Text(
                                text = "Other Country",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = scheme.primary,
                                ),
                            )
                            Text(
                                text = "Enter full country name manually",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = scheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                    if (isOtherSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }

        // When "Other" is selected: user enters the full country name and the code + flag are resolved automatically
        AnimatedVisibility(visible = isOtherSelected) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.25f else 0.40f))
                    .border(BorderStroke(1.dp, extras.glassBorder), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Full Country Name",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                        ),
                    )
                    if (cleanCode.isNotBlank()) {
                        Text(
                            text = "$activeFlag Auto-code: $cleanCode",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary,
                            ),
                        )
                    }
                }
                LiquidTextInput(
                    value = customCountryReason,
                    onValueChange = { input ->
                        val trimmed = input.take(80)
                        onCustomCountryReasonChange(trimmed)
                        val autoCode = resolveCountryCodeFromName(trimmed)
                        onCountryCodeChange(autoCode)
                    },
                    placeholder = "Country name",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    dark = dark,
                )
            }
        }
    }
}

private data class CustomFieldTemplate(
    val suggestions: List<String>,
    val labelPlaceholder: String,
    val valuePlaceholder: String,
)

private fun templateForCategory(category: VisitFieldCategory): CustomFieldTemplate = when (category) {
    VisitFieldCategory.Vehicle -> CustomFieldTemplate(
        suggestions = listOf("Trailer 2 Plate", "Fleet Number", "Chassis / VIN", "Truck Make"),
        labelPlaceholder = "",
        valuePlaceholder = "",
    )
    VisitFieldCategory.Transporter -> CustomFieldTemplate(
        suggestions = listOf("Clearing Agent", "Dispatcher", "Account Ref", "Depot"),
        labelPlaceholder = "",
        valuePlaceholder = "",
    )
    VisitFieldCategory.Purpose -> CustomFieldTemplate(
        suggestions = listOf("Loading Bay", "Customer Name", "Order / PO No.", "Gate Pass"),
        labelPlaceholder = "",
        valuePlaceholder = "",
    )
    VisitFieldCategory.Driver -> CustomFieldTemplate(
        suggestions = listOf("NRC / Passport", "License No.", "Co-Driver Name", "Emergency Phone"),
        labelPlaceholder = "",
        valuePlaceholder = "",
    )
    VisitFieldCategory.Cargo -> CustomFieldTemplate(
        suggestions = listOf("Waybill No.", "Container No.", "Net Weight (MT)", "T1 Customs Ref"),
        labelPlaceholder = "",
        valuePlaceholder = "",
    )
    VisitFieldCategory.Notes, VisitFieldCategory.Departure -> CustomFieldTemplate(
        suggestions = listOf("Parking Slot", "Security Officer", "Exit Pass No.", "Odometer (km)"),
        labelPlaceholder = "",
        valuePlaceholder = "",
    )
}

/**
 * Allows users to add custom fields (label + value) inside any category card
 * with responsive layout, quick-pick suggestions, and human-friendly placeholders.
 */
@Composable
internal fun CategoryCustomFieldsEditor(
    category: VisitFieldCategory,
    customFields: List<CustomVisitField>,
    onCustomFieldsChange: (List<CustomVisitField>) -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors
    val categoryFields = customFields.filter { it.category == category }
    val template = remember(category) { templateForCategory(category) }

    var showAddForm by remember { mutableStateOf(false) }
    var newColumnName by remember { mutableStateOf("") }
    var newColumnValue by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (categoryFields.isEmpty()) {
                    "Extra ${category.label} Details"
                } else {
                    "Extra ${category.label} Details (${categoryFields.size})"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurfaceVariant,
                ),
            )
            SmallGlassChipButton(
                label = if (showAddForm) "Close" else "+ Add Detail",
                dark = dark,
                onClick = { showAddForm = !showAddForm },
            )
        }

        AnimatedVisibility(visible = showAddForm) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.28f else 0.45f))
                    .border(BorderStroke(1.dp, extras.glassBorder), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Quick-tap common field name pills
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "Quick pick a label or type your own:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = scheme.onSurfaceVariant,
                        ),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        template.suggestions.forEach { suggestion ->
                            val isSelected = newColumnName.equals(suggestion, ignoreCase = true)
                            SelectableSmallChip(
                                label = suggestion,
                                selected = isSelected,
                                accent = scheme.primary,
                                dark = dark,
                                onClick = { newColumnName = suggestion },
                            )
                        }
                    }
                }

                // Field 1: Label
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "What are you recording?",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                        ),
                    )
                    LiquidTextInput(
                        value = newColumnName,
                        onValueChange = { newColumnName = it.take(50) },
                        placeholder = template.labelPlaceholder,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        dark = dark,
                    )
                }

                // Field 2: Value
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Enter the detail / value",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                        ),
                    )
                    LiquidTextInput(
                        value = newColumnValue,
                        onValueChange = { newColumnValue = it.take(120) },
                        placeholder = template.valuePlaceholder,
                        dark = dark,
                    )
                }

                // Responsive Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(extras.glassFill)
                            .border(BorderStroke(1.dp, extras.glassBorder), RoundedCornerShape(12.dp))
                            .clickable {
                                newColumnName = ""
                                newColumnValue = ""
                                showAddForm = false
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurfaceVariant,
                            ),
                        )
                    }

                    LiquidActionButton(
                        text = "Save Detail",
                        enabled = newColumnName.isNotBlank() && newColumnValue.isNotBlank(),
                        dark = dark,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onCustomFieldsChange(
                                customFields + CustomVisitField(
                                    id = UUID.randomUUID().toString(),
                                    category = category,
                                    columnName = newColumnName.trim(),
                                    value = newColumnValue.trim(),
                                ),
                            )
                            newColumnName = ""
                            newColumnValue = ""
                            showAddForm = false
                        },
                    )
                }
            }
        }

        categoryFields.forEach { field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.25f else 0.35f))
                    .border(BorderStroke(0.8.dp, extras.glassBorder), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = field.columnName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.primary,
                        ),
                    )
                    Text(
                        text = field.value,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                        ),
                    )
                }
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove custom field",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            onCustomFieldsChange(customFields.filterNot { it.id == field.id })
                        },
                )
            }
        }
    }
}

@Composable
internal fun CategoryCustomFieldsReadOnlyList(
    category: VisitFieldCategory,
    customFields: List<CustomVisitField>,
    dark: Boolean,
) {
    val categoryFields = customFields.filter { it.category == category }
    if (categoryFields.isEmpty()) return

    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        categoryFields.forEach { field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.25f else 0.4f))
                    .border(BorderStroke(0.8.dp, extras.glassBorder), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = field.columnName,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurfaceVariant,
                    ),
                )
                Text(
                    text = field.value,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun DepartureSealVerificationCard(
    seals: List<VisitSeal>,
    onSealsChange: (List<VisitSeal>) -> Unit,
    dark: Boolean,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors

    var isExpanded by remember { mutableStateOf(seals.isNotEmpty()) }
    var showAddExitSealForm by remember { mutableStateOf(false) }
    var showSealScanner by remember { mutableStateOf(false) }
    var isExtractingSealPhoto by remember { mutableStateOf(false) }
    var newSealNumber by remember { mutableStateOf("") }
    var newSealType by remember { mutableStateOf("Bolt") }
    var customSealTypeReason by remember { mutableStateOf("") }
    var newSealCondition by remember { mutableStateOf(SealCondition.Intact) }
    var newSealNotes by remember { mutableStateOf("") }

    val captureSealPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap != null) {
            isExtractingSealPhoto = true
            PlateTextAnalyzer.extractFromBitmap(
                bitmap = bitmap,
                scanMode = OcrScanMode.PLATE,
                knownTransporters = emptySet(),
            ) { res ->
                isExtractingSealPhoto = false
                val extracted = res?.plateNumber
                if (!extracted.isNullOrBlank()) {
                    newSealNumber = extracted.uppercase().take(40)
                }
            }
        }
    }

    val sealCameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            captureSealPhotoLauncher.launch(null)
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { isExpanded = !isExpanded },
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "EXIT SEAL & CARGO VERIFICATION (OPTIONAL)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = scheme.primary,
                            letterSpacing = 0.8.sp,
                        ),
                    )
                    if (seals.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(CapsuleShape)
                                .background(scheme.primary.copy(alpha = if (dark) 0.22f else 0.14f))
                                .border(BorderStroke(0.8.dp, scheme.primary.copy(alpha = 0.45f)), CapsuleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "${seals.size}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = scheme.primary,
                                ),
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (seals.isEmpty()) "No arrival seals logged" else "${seals.size} seal(s) on record",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurfaceVariant,
                            ),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (seals.any { it.condition != SealCondition.Intact }) {
                                SmallGlassChipButton(
                                    label = "Mark All Intact",
                                    icon = Icons.Default.Check,
                                    dark = dark,
                                    onClick = {
                                        onSealsChange(seals.map { it.copy(condition = SealCondition.Intact) })
                                    },
                                )
                            }
                            SmallGlassChipButton(
                                label = if (showAddExitSealForm) "Close" else "+ Add Exit Seal",
                                dark = dark,
                                onClick = { showAddExitSealForm = !showAddExitSealForm },
                            )
                        }
                    }

                    AnimatedVisibility(visible = showAddExitSealForm) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.28f else 0.45f))
                                .border(BorderStroke(1.dp, extras.glassBorder), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Exit Seal Number",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = scheme.onSurface,
                                        ),
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        SmallGlassChipButton(
                                            label = if (isExtractingSealPhoto) "Extracting..." else "Capture",
                                            icon = Icons.Default.PhotoCamera,
                                            dark = dark,
                                            onClick = {
                                                if (!isExtractingSealPhoto) {
                                                    val granted = ContextCompat.checkSelfPermission(
                                                        context,
                                                        Manifest.permission.CAMERA,
                                                    ) == PackageManager.PERMISSION_GRANTED
                                                    if (granted) {
                                                        captureSealPhotoLauncher.launch(null)
                                                    } else {
                                                        sealCameraPermLauncher.launch(Manifest.permission.CAMERA)
                                                    }
                                                }
                                            },
                                        )
                                        SmallGlassChipButton(
                                            label = "Scan Seal",
                                            icon = Icons.Default.CameraAlt,
                                            dark = dark,
                                            onClick = { showSealScanner = true },
                                        )
                                    }
                                }
                                LiquidTextInput(
                                    value = newSealNumber,
                                    onValueChange = { newSealNumber = it.uppercase().take(40) },
                                    placeholder = "Exit seal number",
                                    isMonospace = true,
                                    leadingIcon = Icons.Default.Lock,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                    dark = dark,
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    text = "Seal Type",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSurface,
                                    ),
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    SEAL_TYPES.forEach { t ->
                                        SelectableSmallChip(
                                            label = t,
                                            selected = newSealType == t,
                                            accent = scheme.primary,
                                            dark = dark,
                                            onClick = { newSealType = t },
                                        )
                                    }
                                }
                                if (newSealType.equals("Other", ignoreCase = true)) {
                                    LiquidTextInput(
                                        value = customSealTypeReason,
                                        onValueChange = { customSealTypeReason = it.take(80) },
                                        placeholder = "",
                                        dark = dark,
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    text = "Condition",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSurface,
                                    ),
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    SealCondition.entries.forEach { cond ->
                                        val accent = if (cond == SealCondition.Intact) Color(0xFF10B981) else Color(0xFFEF4444)
                                        SelectableSmallChip(
                                            label = cond.label,
                                            selected = newSealCondition == cond,
                                            accent = accent,
                                            dark = dark,
                                            onClick = { newSealCondition = cond },
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Remarks (Optional)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSurface,
                                    ),
                                )
                                LiquidTextInput(
                                    value = newSealNotes,
                                    onValueChange = { newSealNotes = it.take(150) },
                                    placeholder = "",
                                    dark = dark,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(extras.glassFill)
                                        .border(BorderStroke(1.dp, extras.glassBorder), RoundedCornerShape(12.dp))
                                        .clickable {
                                            newSealNumber = ""
                                            customSealTypeReason = ""
                                            newSealNotes = ""
                                            newSealCondition = SealCondition.Intact
                                            showAddExitSealForm = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Cancel",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = scheme.onSurfaceVariant,
                                        ),
                                    )
                                }

                                LiquidActionButton(
                                    text = "Save Exit Seal",
                                    enabled = newSealNumber.isNotBlank(),
                                    dark = dark,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        val resolvedType = if (newSealType.equals("Other", ignoreCase = true) && customSealTypeReason.isNotBlank()) {
                                            "Other (${customSealTypeReason.trim()})"
                                        } else {
                                            newSealType
                                        }
                                        onSealsChange(
                                            seals + VisitSeal(
                                                id = UUID.randomUUID().toString(),
                                                sealNumber = newSealNumber.trim(),
                                                sealType = resolvedType,
                                                condition = newSealCondition,
                                                notes = newSealNotes.trim().takeIf { it.isNotBlank() } ?: "Added at departure",
                                            ),
                                        )
                                        newSealNumber = ""
                                        customSealTypeReason = ""
                                        newSealNotes = ""
                                        newSealCondition = SealCondition.Intact
                                        showAddExitSealForm = false
                                    },
                                )
                            }
                        }
                    }

                    seals.forEach { seal ->
                        val condColor = if (seal.condition == SealCondition.Intact) Color(0xFF10B981) else Color(0xFFEF4444)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.25f else 0.38f))
                                .border(
                                    BorderStroke(
                                        0.8.dp,
                                        if (seal.condition == SealCondition.Intact) extras.glassBorder else condColor.copy(alpha = 0.5f),
                                    ),
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = condColor,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Column {
                                        Text(
                                            text = "${seal.sealNumber} • ${seal.sealType}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = scheme.onSurface,
                                            ),
                                        )
                                        seal.notes?.takeIf { it.isNotBlank() }?.let { note ->
                                            Text(
                                                text = note,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    color = scheme.onSurfaceVariant,
                                                ),
                                            )
                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    SealPhotoThumbnail(path = seal.localPhotoPath)
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove seal",
                                        tint = scheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                onSealsChange(seals.filterNot { it.id == seal.id })
                                            },
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                SealCondition.entries.forEach { cond ->
                                    val chipAccent = if (cond == SealCondition.Intact) Color(0xFF10B981) else Color(0xFFEF4444)
                                    SelectableSmallChip(
                                        label = cond.label,
                                        selected = seal.condition == cond,
                                        accent = chipAccent,
                                        dark = dark,
                                        onClick = {
                                            onSealsChange(
                                                seals.map { existing ->
                                                    if (existing.id == seal.id) existing.copy(condition = cond) else existing
                                                },
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSealScanner) {
        PlateScannerDialog(
            targetLabel = "Scan Exit Seal Number",
            scanMode = OcrScanMode.PLATE,
            dark = dark,
            onDismiss = { showSealScanner = false },
            onPlateScanned = { scanned, _ ->
                newSealNumber = scanned.uppercase().take(40)
                showSealScanner = false
            },
        )
    }
}
