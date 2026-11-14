package com.truckpark.management.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import com.truckpark.management.R
import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.core.common.ThemeMode
import com.truckpark.management.core.designsystem.icons.TruckParkIcons
import com.truckpark.management.domain.models.AuthUser
import com.truckpark.management.feature.auth.AuthViewModel

@Composable
fun MoreScreen(themeMode: ThemeMode, onThemeModeSelected: (ThemeMode) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.more_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        AppearanceCard(themeMode, onThemeModeSelected)
        val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.55f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.about_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.about_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AppearanceCard(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
    initiallyExpanded: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    val selectedLabel = when (selected) {
        ThemeMode.SYSTEM -> "System Default"
        ThemeMode.LIGHT -> "Light Theme"
        ThemeMode.DARK -> "Dark Theme"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.55f))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.primary.copy(alpha = 0.14f))
                        .border(1.dp, scheme.primary.copy(alpha = 0.30f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when (selected) {
                            ThemeMode.SYSTEM -> TruckParkIcons.ThemeSystem
                            ThemeMode.LIGHT -> TruckParkIcons.ThemeLight
                            ThemeMode.DARK -> TruckParkIcons.ThemeDark
                        },
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.appearance_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface,
                    )
                    Text(
                        text = "Current: $selectedLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(scheme.surface.copy(alpha = if (dark) 0.45f else 0.70f))
                    .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse appearance" else "Expand appearance",
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            ThemeModeSelector(selected = selected, onSelected = onSelected)
        }
    }
}

@Composable
fun ThemeModeSelector(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeOption(
            mode = ThemeMode.SYSTEM,
            title = "System Default",
            subtitle = "Follow device light or dark mode",
            icon = TruckParkIcons.ThemeSystem,
            accentColor = Color(0xFF6366F1), // Indigo
        ),
        ThemeOption(
            mode = ThemeMode.LIGHT,
            title = "Light Theme",
            subtitle = "Crisp, clean high-contrast daytime mode",
            icon = TruckParkIcons.ThemeLight,
            accentColor = Color(0xFFEA580C), // Warm Amber-Orange
        ),
        ThemeOption(
            mode = ThemeMode.DARK,
            title = "Dark Theme",
            subtitle = "Sleek, battery-friendly midnight mode",
            icon = TruckParkIcons.ThemeDark,
            accentColor = Color(0xFF818CF8), // Sky/Lavender
        ),
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { option ->
            val isSelected = option.mode == selected
            val animatedBorderColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) option.accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                label = "border_${option.mode}",
            )
            val animatedBgColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) option.accentColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                label = "bg_${option.mode}",
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSelected) 1.6.dp else 1.dp,
                        color = animatedBorderColor,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    )
                    .background(animatedBgColor, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelected(option.mode) },
                        role = Role.RadioButton,
                    )
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Frosted Glass Icon Badge with subtle accent glow
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = option.accentColor.copy(alpha = if (isSelected) 0.20f else 0.10f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        )
                        .border(
                            width = 1.dp,
                            color = option.accentColor.copy(alpha = if (isSelected) 0.40f else 0.20f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = option.icon,
                        contentDescription = option.title,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp),
                    )
                }

                // Title and description
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = option.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) option.accentColor else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = option.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    )
                }

                // Selection Radio Indicator
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(
                            width = 2.dp,
                            color = if (isSelected) option.accentColor else MaterialTheme.colorScheme.outlineVariant,
                            shape = androidx.compose.foundation.shape.CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .background(
                                    option.accentColor,
                                    androidx.compose.foundation.shape.CircleShape,
                                ),
                        )
                    }
                }
            }
        }
    }
}

private data class ThemeOption(
    val mode: ThemeMode,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accentColor: Color,
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MoreOptionsBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    user: AuthUser? = null,
    authViewModel: AuthViewModel? = null,
    onRefreshRole: () -> Unit = {},
    onSignOut: () -> Unit = {},
) {
    if (visible) {
        val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = Color.Black.copy(alpha = 0.65f),
            dragHandle = {
                androidx.compose.material3.BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                )
            },
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .padding(bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                    if (user != null) {
                        var isProfileExpanded by remember { mutableStateOf(false) }
                        var showPermissionsSection by remember { mutableStateOf(false) }
                        var showPasswordSection by remember { mutableStateOf(false) }

                        var fullNameInput by remember(user.id, user.fullName) { mutableStateOf(user.fullName) }
                        var phoneInput by remember(user.id, user.phone) { mutableStateOf(user.phone.orEmpty()) }
                        var emailInput by remember(user.id, user.email) { mutableStateOf(user.email) }
                        var currentPasswordInput by remember(user.id) { mutableStateOf("") }
                        var newPasswordInput by remember(user.id) { mutableStateOf("") }
                        var showCurrentPassword by remember(user.id) { mutableStateOf(true) }
                        var showNewPassword by remember(user.id) { mutableStateOf(true) }

                        // Dual 6-digit OTP state when changing email address (Old Email -> New Email)
                        val cleanEmailInput = emailInput.trim().lowercase()
                        val currentEmailLower = user.email.trim().lowercase()
                        val isEmailChanged = cleanEmailInput.isNotEmpty() && !cleanEmailInput.equals(currentEmailLower, ignoreCase = true)

                        var oldEmailCodeSent by remember(user.id, currentEmailLower) { mutableStateOf(false) }
                        var oldEmailCodeInput by remember(user.id, currentEmailLower) { mutableStateOf("") }
                        var oldEmailVerified by remember(user.id, currentEmailLower) { mutableStateOf(false) }

                        var newEmailCodeSentFor by remember(user.id) { mutableStateOf<String?>(null) }
                        var newEmailCodeInput by remember(user.id) { mutableStateOf("") }

                        var isBusy by remember { mutableStateOf(false) }
                        var statusMessage by remember { mutableStateOf<String?>(null) }
                        var isErrorStatus by remember { mutableStateOf(false) }

                        // Reset new-email code state if user edits the target email after sending
                        LaunchedEffect(cleanEmailInput) {
                            if (newEmailCodeSentFor != null && newEmailCodeSentFor != cleanEmailInput) {
                                newEmailCodeSentFor = null
                                newEmailCodeInput = ""
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.55f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = user.fullName.ifBlank { "Operator" },
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = user.primaryRole,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                ),
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    Text(
                                        text = user.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (!user.phone.isNullOrBlank()) {
                                        Text(
                                            text = user.phone,
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            // View / Update Profile & Info Button (replaces Sync Role & Permissions button)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .clickable {
                                        isProfileExpanded = !isProfileExpanded
                                        statusMessage = null
                                    }
                                    .padding(vertical = 10.dp, horizontal = 14.dp),
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
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = if (isProfileExpanded) "Close Profile & Account Info" else "View / Update Profile & Info",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isProfileExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = isProfileExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // 1. Read-Only Role & Permissions Collapsible Chip Box
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.50f else 0.75f))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { showPermissionsSection = !showPermissionsSection },
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "Role: ${user.roles.joinToString(", ").ifBlank { user.primaryRole }} (${user.permissions.size} permissions)",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Icon(
                                                imageVector = if (showPermissionsSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        AnimatedVisibility(visible = showPermissionsSection) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                user.permissions.sorted().forEach { perm ->
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(999.dp))
                                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                                            .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
                                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                                    ) {
                                                        Text(
                                                            text = perm,
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontFamily = FontFamily.Monospace,
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.primary,
                                                            ),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 2. Full Name Input
                                    ProfileInputField(
                                        label = "Full Name",
                                        value = fullNameInput,
                                        onValueChange = { fullNameInput = it.take(80) },
                                        placeholder = "Full name",
                                        leadingIcon = Icons.Default.Person,
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                    )

                                    // 3. Phone Number Input
                                    ProfileInputField(
                                        label = "Phone Number",
                                        value = phoneInput,
                                        onValueChange = { phoneInput = it.take(30) },
                                        placeholder = "Phone number",
                                        leadingIcon = Icons.Default.Phone,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    )

                                    // 4. Email Address Input
                                    ProfileInputField(
                                        label = "Email Address",
                                        value = emailInput,
                                        onValueChange = { emailInput = it.take(120) },
                                        placeholder = "Email address",
                                        leadingIcon = Icons.Default.Email,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    )

                                    // Dual 6-Digit Email Reconfirmation Flow (shown whenever Email is modified)
                                    AnimatedVisibility(
                                        visible = isEmailChanged,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut(),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.12f else 0.07f))
                                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text(
                                                text = "EMAIL CHANGE SECURITY VERIFICATION",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    letterSpacing = 0.6.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                ),
                                            )

                                            // STEP 1: Verify Old Email
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.65f else 0.85f))
                                                    .border(
                                                        BorderStroke(
                                                            1.dp,
                                                            if (oldEmailVerified) Color(0xFF10B981).copy(alpha = 0.6f)
                                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                        ),
                                                        RoundedCornerShape(10.dp),
                                                    )
                                                    .padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "Step 1: Reconfirm Old Email",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                            ),
                                                        )
                                                        Text(
                                                            text = user.email,
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            ),
                                                        )
                                                    }
                                                    if (oldEmailVerified) {
                                                        Text(
                                                            text = "✓ Verified",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = Color(0xFF10B981),
                                                            ),
                                                        )
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(999.dp))
                                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                                                .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                                                                .clickable(enabled = !isBusy && authViewModel != null) {
                                                                    isBusy = true
                                                                    statusMessage = null
                                                                    authViewModel?.sendVerificationCode(user.email) { res ->
                                                                        isBusy = false
                                                                        when (res) {
                                                                            is AuthResult.Success -> {
                                                                                oldEmailCodeSent = true
                                                                                statusMessage = "6-digit code sent to old email (${user.email})."
                                                                                isErrorStatus = false
                                                                            }
                                                                            is AuthResult.Failure -> {
                                                                                statusMessage = res.error.message
                                                                                isErrorStatus = true
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                                        ) {
                                                            Text(
                                                                text = if (oldEmailCodeSent) "Resend Code" else "Send Code",
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 10.sp,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                ),
                                                            )
                                                        }
                                                    }
                                                }

                                                if (oldEmailCodeSent && !oldEmailVerified) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Box(modifier = Modifier.weight(1f)) {
                                                            ProfileInputField(
                                                                label = "6-Digit Code from Old Email",
                                                                value = oldEmailCodeInput,
                                                                onValueChange = { oldEmailCodeInput = it.filter { c -> c.isDigit() }.take(6) },
                                                                placeholder = "6-digit code",
                                                                isMonospace = true,
                                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                            )
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .padding(top = 16.dp)
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .background(MaterialTheme.colorScheme.primary)
                                                                .clickable(enabled = !isBusy && oldEmailCodeInput.length == 6 && authViewModel != null) {
                                                                    isBusy = true
                                                                    statusMessage = null
                                                                    authViewModel?.verifyCurrentEmailCode(oldEmailCodeInput) { res ->
                                                                        when (res) {
                                                                            is AuthResult.Success -> {
                                                                                oldEmailVerified = true
                                                                                // Automatically dispatch code to the new email address
                                                                                authViewModel.sendVerificationCode(cleanEmailInput) { sendNewRes ->
                                                                                    isBusy = false
                                                                                    when (sendNewRes) {
                                                                                        is AuthResult.Success -> {
                                                                                            newEmailCodeSentFor = cleanEmailInput
                                                                                            statusMessage = "Old email verified! A 6-digit code has been sent to $cleanEmailInput."
                                                                                            isErrorStatus = false
                                                                                        }
                                                                                        is AuthResult.Failure -> {
                                                                                            statusMessage = sendNewRes.error.message
                                                                                            isErrorStatus = true
                                                                                        }
                                                                                    }
                                                                                }
                                                                            }
                                                                            is AuthResult.Failure -> {
                                                                                isBusy = false
                                                                                statusMessage = res.error.message
                                                                                isErrorStatus = true
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                .padding(horizontal = 12.dp, vertical = 11.dp),
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            Text(
                                                                text = "Verify",
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                                ),
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // STEP 2: Verify New Email (enabled once Old Email is verified)
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.65f else 0.85f))
                                                    .border(
                                                        BorderStroke(
                                                            1.dp,
                                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                        ),
                                                        RoundedCornerShape(10.dp),
                                                    )
                                                    .padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "Step 2: Confirm New Email",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (oldEmailVerified) MaterialTheme.colorScheme.onSurface
                                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            ),
                                                        )
                                                        Text(
                                                            text = cleanEmailInput,
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            ),
                                                        )
                                                    }
                                                    if (oldEmailVerified) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(999.dp))
                                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                                                .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                                                                .clickable(enabled = !isBusy && authViewModel != null) {
                                                                    isBusy = true
                                                                    statusMessage = null
                                                                    authViewModel?.sendVerificationCode(cleanEmailInput) { res ->
                                                                        isBusy = false
                                                                        when (res) {
                                                                            is AuthResult.Success -> {
                                                                                newEmailCodeSentFor = cleanEmailInput
                                                                                statusMessage = "6-digit code sent to new email ($cleanEmailInput)."
                                                                                isErrorStatus = false
                                                                            }
                                                                            is AuthResult.Failure -> {
                                                                                statusMessage = res.error.message
                                                                                isErrorStatus = true
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                                        ) {
                                                            Text(
                                                                text = if (newEmailCodeSentFor == cleanEmailInput) "Resend Code" else "Send Code",
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 10.sp,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                ),
                                                            )
                                                        }
                                                    } else {
                                                        Text(
                                                            text = "Verify Step 1 first",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            ),
                                                        )
                                                    }
                                                }

                                                if (oldEmailVerified) {
                                                    ProfileInputField(
                                                        label = "6-Digit Code from New Email",
                                                        value = newEmailCodeInput,
                                                        onValueChange = { newEmailCodeInput = it.filter { c -> c.isDigit() }.take(6) },
                                                        placeholder = "6-digit code",
                                                        isMonospace = true,
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Strict password validation rules (enforced whenever a new password is entered)
                                    val isChangingPassword = newPasswordInput.isNotEmpty() || currentPasswordInput.isNotEmpty()
                                    val hasCurrentPassword = currentPasswordInput.isNotEmpty()
                                    val isDifferentFromCurrent = newPasswordInput.isNotEmpty() && newPasswordInput != currentPasswordInput
                                    val pwdHasMinLength = newPasswordInput.length >= 8
                                    val pwdHasUpper = newPasswordInput.any { it.isUpperCase() }
                                    val pwdHasLower = newPasswordInput.any { it.isLowerCase() }
                                    val pwdHasDigit = newPasswordInput.any { it.isDigit() }
                                    val pwdHasSymbol = newPasswordInput.any { !it.isLetterOrDigit() }
                                    val isNewPasswordValid = !isChangingPassword || (
                                        hasCurrentPassword &&
                                            isDifferentFromCurrent &&
                                            pwdHasMinLength &&
                                            pwdHasUpper &&
                                            pwdHasLower &&
                                            pwdHasDigit &&
                                            pwdHasSymbol
                                        )

                                    // 5. Optional Password Change Collapsible Section
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.50f else 0.75f))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { showPasswordSection = !showPasswordSection },
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(15.dp),
                                                )
                                                Text(
                                                    text = "Change Password (Optional)",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                            Icon(
                                                imageVector = if (showPasswordSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }

                                        AnimatedVisibility(visible = showPasswordSection) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                ProfileInputField(
                                                    label = "Current Password",
                                                    value = currentPasswordInput,
                                                    onValueChange = { currentPasswordInput = it.take(100) },
                                                    placeholder = "Enter your current password",
                                                    leadingIcon = Icons.Default.Lock,
                                                    isPassword = true,
                                                    isPasswordVisible = showCurrentPassword,
                                                    onTogglePasswordVisibility = { showCurrentPassword = !showCurrentPassword },
                                                )
                                                ProfileInputField(
                                                    label = "New Password",
                                                    value = newPasswordInput,
                                                    onValueChange = { newPasswordInput = it.take(100) },
                                                    placeholder = "Enter your new password",
                                                    leadingIcon = Icons.Default.Lock,
                                                    isPassword = true,
                                                    isPasswordVisible = showNewPassword,
                                                    onTogglePasswordVisibility = { showNewPassword = !showNewPassword },
                                                )

                                                if (currentPasswordInput.isNotEmpty() && newPasswordInput.isNotEmpty() && currentPasswordInput == newPasswordInput) {
                                                    Text(
                                                        text = "New password cannot be the same as your current password.",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFEF4444),
                                                        ),
                                                    )
                                                }

                                                // Live Password Strictness Checklist
                                                if (isChangingPassword) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.55f))
                                                            .padding(10.dp),
                                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        Text(
                                                            text = "Password Requirements:",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                            ),
                                                        )
                                                        PasswordRequirementRow("Current password entered", hasCurrentPassword)
                                                        PasswordRequirementRow("Different from current password", isDifferentFromCurrent)
                                                        PasswordRequirementRow("At least 8 characters", pwdHasMinLength)
                                                        PasswordRequirementRow("At least 1 capital letter (A–Z)", pwdHasUpper)
                                                        PasswordRequirementRow("At least 1 lowercase letter (a–z)", pwdHasLower)
                                                        PasswordRequirementRow("At least 1 number (0–9)", pwdHasDigit)
                                                        PasswordRequirementRow("At least 1 symbol (!@#\$%^&*)", pwdHasSymbol)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Status / Error Banner
                                    statusMessage?.let { msg ->
                                        val bannerColor = if (isErrorStatus) Color(0xFFEF4444) else Color(0xFF10B981)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(bannerColor.copy(alpha = 0.14f))
                                                .border(1.dp, bannerColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                        ) {
                                            Text(
                                                text = msg,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = bannerColor,
                                                ),
                                            )
                                        }
                                    }

                                    // Save Profile Changes Button
                                    val canSaveProfile = !isBusy &&
                                        fullNameInput.isNotBlank() &&
                                        cleanEmailInput.isNotBlank() &&
                                        (!isEmailChanged || (oldEmailVerified && newEmailCodeInput.length == 6)) &&
                                        isNewPasswordValid

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (canSaveProfile) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                            )
                                            .clickable(enabled = canSaveProfile && authViewModel != null) {
                                                isBusy = true
                                                statusMessage = null
                                                authViewModel?.updateUserProfile(
                                                    newEmail = cleanEmailInput,
                                                    fullName = fullNameInput.trim(),
                                                    phone = phoneInput.trim(),
                                                    currentPassword = currentPasswordInput.takeIf { it.isNotBlank() },
                                                    newPassword = newPasswordInput.takeIf { it.isNotBlank() },
                                                    code = if (isEmailChanged) newEmailCodeInput else null,
                                                ) { res ->
                                                    isBusy = false
                                                    when (res) {
                                                        is AuthResult.Success -> {
                                                            currentPasswordInput = ""
                                                            newPasswordInput = ""
                                                            oldEmailCodeInput = ""
                                                            newEmailCodeInput = ""
                                                            oldEmailCodeSent = false
                                                            oldEmailVerified = false
                                                            newEmailCodeSentFor = null
                                                            statusMessage = "Profile updated successfully."
                                                            isErrorStatus = false
                                                        }
                                                        is AuthResult.Failure -> {
                                                            statusMessage = res.error.message
                                                            isErrorStatus = true
                                                        }
                                                    }
                                                }
                                            }
                                            .padding(vertical = 12.dp, horizontal = 16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            if (isBusy) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = if (canSaveProfile) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                            Text(
                                                text = when {
                                                    isBusy -> "Saving..."
                                                    isEmailChanged -> "Verify New Email & Save Changes"
                                                    else -> "Save Profile Changes"
                                                },
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (canSaveProfile) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Collapsible Appearance Card with Chevron Arrow
                    AppearanceCard(
                        selected = themeMode,
                        onSelected = onThemeModeSelected,
                    )

                    // About Card with solid colors
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.55f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.about_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.about_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Sign Out Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                            .border(
                                width = 1.dp,
                                color = Color(0xFFEF4444).copy(alpha = 0.45f),
                                shape = RoundedCornerShape(16.dp),
                            )
                            .clickable(onClick = onSignOut)
                            .padding(vertical = 14.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Sign Out",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFEF4444),
                        )
                    }

                    // Footer version note with high-contrast text
                    Text(
                        text = "Hibiscus TruckPark v1.5.0 (Milestone 5)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordRequirementRow(
    label: String,
    satisfied: Boolean,
) {
    val color = if (satisfied) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (satisfied) "✓" else "•",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                color = color,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (satisfied) FontWeight.Bold else FontWeight.Normal,
                color = color,
            ),
        )
    }
}

@Composable
private fun ProfileInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isMonospace: Boolean = false,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = true,
    onTogglePasswordVisibility: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
            ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                leadingIcon?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    visualTransformation = if (isPassword && !isPasswordVisible) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = keyboardOptions,
                    cursorBrush = SolidColor(scheme.primary),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = scheme.onSurface,
                        fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                        fontWeight = if (isMonospace) FontWeight.Bold else FontWeight.Medium,
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                ),
                            )
                        }
                        inner()
                    },
                )
                if (isPassword && onTogglePasswordVisibility != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(scheme.primary.copy(alpha = 0.14f))
                            .border(0.8.dp, scheme.primary.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                            .clickable(onClick = onTogglePasswordVisibility)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = if (isPasswordVisible) "Hide" else "Show",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = scheme.primary,
                            ),
                        )
                    }
                }
            }
        }
    }
}
