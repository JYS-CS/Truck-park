package com.truckpark.management.feature.placeholder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SolidColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truckpark.management.R
import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.core.designsystem.components.GlassCard
import com.truckpark.management.core.designsystem.components.StatusBadge
import com.truckpark.management.core.designsystem.components.StatusKind
import com.truckpark.management.core.designsystem.icons.TruckParkIcons
import com.truckpark.management.core.designsystem.theme.TruckParkExtras
import com.truckpark.management.domain.models.AuthUser
import com.truckpark.management.domain.models.ManagedDevice
import com.truckpark.management.domain.models.ManagedUser
import com.truckpark.management.feature.auth.AuthViewModel
import java.time.LocalTime

/**
 * Role-adaptive workspace screen:
 * - Shows role-specific greeting and navigation scope.
 * - On the Home tab for ADMIN users, displays the live Access & Handset Governance panel
 *   to update staff roles and manage handsets.
 */
@Composable
fun PlannedFeatureScreen(
    title: String,
    summary: String,
    milestone: String,
    icon: ImageVector = TruckParkIcons.Neutral,
    user: AuthUser? = null,
    authViewModel: AuthViewModel? = null,
    isHomeTab: Boolean = false,
    onMenuClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top Header: HIBISCUS Wordmark in Brand Gradient + "Truck Park" subtitle + Hamburger Menu Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Hibiscus",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            letterSpacing = 0.5.sp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF00F5FF), // Cyan
                                    Color(0xFF26BFFF), // Sky blue
                                    Color(0xFF4938FF), // Royal Indigo
                                    Color(0xFFC500FF), // Magenta
                                ),
                            ),
                        ),
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "Truck Park",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onMenuClick != null) {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(12.dp),
                            ),
                    ) {
                        Icon(
                            imageVector = TruckParkIcons.Menu,
                            contentDescription = "Options and settings menu",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (user != null && isHomeTab) {
                PersonalizedGreetingCard(user = user)
            }

            if (user != null && user.isAdmin && isHomeTab && authViewModel != null) {
                AdminManagementPanel(
                    currentUser = user,
                    authViewModel = authViewModel,
                )
            }

            // Planned feature placeholder card
            GlassCard(Modifier.fillMaxWidth(), contentPadding = 24) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.status_not_implemented),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )

                    Spacer(Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = CircleShape,
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.planned_in_milestone, milestone),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single card showing ONLY a personalized greeting chosen by local hour.
 * Picked randomly on first composition and preserved across recompositions.
 */
@Composable
private fun PersonalizedGreetingCard(user: AuthUser) {
    val hour = remember {
        try {
            LocalTime.now().hour
        } catch (_: Throwable) {
            12
        }
    }
    val greetingArrayRes = when (hour) {
        in 5..10 -> R.array.greetings_morning
        in 11..13 -> R.array.greetings_midday
        in 14..16 -> R.array.greetings_afternoon
        in 17..20 -> R.array.greetings_evening
        else -> R.array.greetings_night
    }
    val greetings = stringArrayResource(greetingArrayRes)
    val greetingTemplate = rememberSaveable(user.id) {
        if (greetings.isNotEmpty()) greetings.random() else "Welcome, {name}."
    }

    val firstName = remember(user.fullName) {
        user.fullName.trim().split("\\s+".toRegex()).firstOrNull().orEmpty().ifBlank { "Operator" }
    }
    val greetingText = remember(greetingTemplate, firstName) {
        greetingTemplate.replace("{name}", firstName)
    }

    GlassCard(Modifier.fillMaxWidth(), contentPadding = 18) {
        Text(
            text = greetingText,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                lineHeight = 23.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AdminManagementPanel(
    currentUser: AuthUser,
    authViewModel: AuthViewModel,
) {
    var selectedSection by remember { mutableIntStateOf(0) } // 0 = Staff, 1 = Handsets
    var users by remember { mutableStateOf<List<ManagedUser>>(emptyList()) }
    var devices by remember { mutableStateOf<List<ManagedDevice>>(emptyList()) }
    var isUsersLoading by remember { mutableStateOf(true) }
    var isDevicesLoading by remember { mutableStateOf(true) }

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }

    // Detail view state for Staff (tapping a staff row opens an inline detail view in place of the list)
    var selectedStaffDetail by remember { mutableStateOf<ManagedUser?>(null) }

    // Role editing in Staff Detail view
    var isEditingRole by remember { mutableStateOf(false) }
    var pendingRoleSelection by remember { mutableStateOf<String?>(null) }
    var isSavingRole by remember { mutableStateOf(false) }

    // Handset inline revoke state
    var confirmingDeviceId by remember { mutableStateOf<String?>(null) }
    var revokingDeviceId by remember { mutableStateOf<String?>(null) }
    var selectedReason by remember { mutableStateOf("") }

    val defaultLostReason = stringResource(R.string.reason_lost_stolen)
    val defaultStaffReason = stringResource(R.string.reason_staff_reassigned)
    val defaultDecomReason = stringResource(R.string.reason_decommissioned)
    val defaultPolicyReason = stringResource(R.string.reason_policy_violation)

    val quickReasons = remember(defaultLostReason, defaultStaffReason, defaultDecomReason, defaultPolicyReason) {
        listOf(
            defaultLostReason,
            defaultStaffReason,
            defaultDecomReason,
            defaultPolicyReason,
        )
    }

    // Feedback strip: stays until user taps "Got it", no auto-dismiss
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isErrorFeedback by remember { mutableStateOf(false) }

    fun refreshUsers() {
        isUsersLoading = true
        authViewModel.loadManagedUsers { result ->
            isUsersLoading = false
            when (result) {
                is AuthResult.Success -> {
                    users = result.value
                    // Keep detail view in sync if open
                    selectedStaffDetail?.let { detail ->
                        result.value.find { it.id == detail.id }?.let { updated ->
                            selectedStaffDetail = updated
                        }
                    }
                }
                is AuthResult.Failure -> {
                    isErrorFeedback = true
                    feedbackMessage = result.error.message
                }
            }
        }
    }

    fun refreshDevices() {
        isDevicesLoading = true
        authViewModel.loadManagedDevices { result ->
            isDevicesLoading = false
            when (result) {
                is AuthResult.Success -> devices = result.value
                is AuthResult.Failure -> {
                    isErrorFeedback = true
                    feedbackMessage = result.error.message
                }
            }
        }
    }

    fun refreshAll() {
        refreshUsers()
        refreshDevices()
    }

    LaunchedEffect(Unit) {
        refreshAll()
    }

    val staffAccent = TruckParkExtras.colors.staffAccent
    val handsetAccent = TruckParkExtras.colors.handsetAccent
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f

    val adminCount = users.count { it.role.equals("ADMIN", true) }
    val supervisorCount = users.count { it.role.equals("SUPERVISOR", true) }
    val runnerCount = users.count { it.role.equals("RUNNER", true) }

    val activeDeviceCount = devices.count { !it.isRevoked }
    val revokedDeviceCount = devices.count { it.isRevoked }

    // One outer container for the whole panel without nested cards
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 18) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header: Title and one-line description (no refresh button)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.admin_panel_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 0.2.sp,
                    ),
                    color = scheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.admin_panel_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            // Two Stat Tiles Side-by-Side: Staff (indigo accent) and Handsets (green accent)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Staff Stat Tile
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    staffAccent.copy(alpha = if (dark) 0.18f else 0.12f),
                                    staffAccent.copy(alpha = if (dark) 0.08f else 0.04f),
                                )
                            )
                        )
                        .border(1.dp, staffAccent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.tile_staff_label),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            ),
                            color = staffAccent,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${users.size}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                            ),
                            color = scheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.tile_staff_breakdown, adminCount, supervisorCount, runnerCount),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }

                // Handsets Stat Tile
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    handsetAccent.copy(alpha = if (dark) 0.18f else 0.12f),
                                    handsetAccent.copy(alpha = if (dark) 0.08f else 0.04f),
                                )
                            )
                        )
                        .border(1.dp, handsetAccent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.tile_handsets_label),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            ),
                            color = handsetAccent,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${devices.size}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                            ),
                            color = scheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.tile_handsets_breakdown, activeDeviceCount, revokedDeviceCount),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Segmented Tabs: Staff (N) and Handsets (N). Labels on one line. Selected tab uses accent color.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (dark) Color(0xFF0F172A).copy(alpha = 0.7f) else Color(0xFFE2E8F0).copy(alpha = 0.6f))
                    .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(3.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    val isStaffSelected = selectedSection == 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (isStaffSelected) staffAccent else Color.Transparent)
                            .clickable {
                                selectedSection = 0
                                selectedStaffDetail = null
                                isEditingRole = false
                                pendingRoleSelection = null
                                statusFilter = "ALL"
                                searchQuery = ""
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.tab_staff_n, users.size),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isStaffSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = if (isStaffSelected) Color.White else scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }

                    val isHandsetSelected = selectedSection == 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (isHandsetSelected) handsetAccent else Color.Transparent)
                            .clickable {
                                selectedSection = 1
                                selectedStaffDetail = null
                                isEditingRole = false
                                pendingRoleSelection = null
                                statusFilter = "ALL"
                                searchQuery = ""
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.tab_handsets_n, devices.size),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isHandsetSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = if (isHandsetSelected) Color.White else scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Search & Filters: Hidden while Staff Detail view is open
            if (selectedStaffDetail == null) {
                // Search Field: lighter fill, placeholder changes with section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.5f))
                        .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = if (selectedSection == 0) {
                                        stringResource(R.string.search_staff_placeholder)
                                    } else {
                                        stringResource(R.string.search_handsets_placeholder)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    color = scheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(if (selectedSection == 0) staffAccent else handsetAccent),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.clear_search),
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .clickable { searchQuery = "" },
                            )
                        }
                    }
                }

                // Filter Chips in one horizontally scrollable row; selected chip uses section accent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val filterPairs = if (selectedSection == 0) {
                        listOf(
                            "ALL" to stringResource(R.string.filter_all),
                            "RUNNER" to stringResource(R.string.filter_runner),
                            "SUPERVISOR" to stringResource(R.string.filter_supervisor),
                            "ADMIN" to stringResource(R.string.filter_admin),
                        )
                    } else {
                        listOf(
                            "ALL" to stringResource(R.string.filter_all),
                            "ACTIVE" to stringResource(R.string.filter_active),
                            "REVOKED" to stringResource(R.string.filter_revoked),
                        )
                    }
                    val currentAccent = if (selectedSection == 0) staffAccent else handsetAccent

                    filterPairs.forEach { (filterKey, filterLabel) ->
                        val isFilterActive = statusFilter.equals(filterKey, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (isFilterActive) currentAccent
                                    else scheme.surfaceVariant.copy(alpha = if (dark) 0.45f else 0.7f)
                                )
                                .border(
                                    1.dp,
                                    if (isFilterActive) currentAccent
                                    else scheme.outlineVariant.copy(alpha = 0.35f),
                                    RoundedCornerShape(999.dp),
                                )
                                .clickable { statusFilter = filterKey }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = filterLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isFilterActive) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                ),
                                color = if (isFilterActive) Color.White else scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Feedback strip directly above the list: message on left, "Got it" button on right (stays until user taps "Got it")
            AnimatedVisibility(
                visible = !feedbackMessage.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                if (!feedbackMessage.isNullOrBlank()) {
                    val fbColor = if (isErrorFeedback) scheme.error else TruckParkExtras.colors.success.content
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(fbColor.copy(alpha = 0.12f))
                            .border(1.dp, fbColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = if (isErrorFeedback) Icons.Default.Warning else TruckParkIcons.Success,
                                contentDescription = null,
                                tint = fbColor,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = feedbackMessage!!,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = fbColor,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(R.string.btn_got_it),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                ),
                                color = fbColor,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { feedbackMessage = null }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            // Content Area: Staff Detail View OR Staff List OR Handset List
            if (selectedSection == 0 && selectedStaffDetail != null) {
                // Inline Detail View for selected Staff member
                val staff = selectedStaffDetail!!
                val isSelf = staff.id == currentUser.id
                val staffDevices = devices.filter { it.userId == staff.id || it.userEmail.equals(staff.email, ignoreCase = true) }
                val activeStaffDevices = staffDevices.count { !it.isRevoked }

                StaffDetailView(
                    staff = staff,
                    isCurrentUser = isSelf,
                    staffDevices = staffDevices,
                    activeStaffDevices = activeStaffDevices,
                    isEditingRole = isEditingRole,
                    pendingRole = pendingRoleSelection,
                    isSaving = isSavingRole,
                    onBack = {
                        selectedStaffDetail = null
                        isEditingRole = false
                        pendingRoleSelection = null
                    },
                    onStartEditRole = {
                        isEditingRole = true
                        pendingRoleSelection = null
                    },
                    onSelectPendingRole = { newRole ->
                        pendingRoleSelection = newRole
                    },
                    onCancelRoleEdit = {
                        isEditingRole = false
                        pendingRoleSelection = null
                    },
                    onConfirmRoleEdit = {
                        val newRole = pendingRoleSelection ?: return@StaffDetailView
                        isSavingRole = true
                        authViewModel.updateUserRole(staff.id, newRole) { res ->
                            isSavingRole = false
                            isEditingRole = false
                            pendingRoleSelection = null
                            when (res) {
                                is AuthResult.Success -> {
                                    val updated = staff.copy(role = newRole, status = "APPROVED")
                                    selectedStaffDetail = updated
                                    users = users.map { if (it.id == staff.id) updated else it }
                                    isErrorFeedback = false
                                    feedbackMessage = "${staff.fullName} role updated to $newRole"
                                }
                                is AuthResult.Failure -> {
                                    isErrorFeedback = true
                                    feedbackMessage = res.error.message
                                }
                            }
                        }
                    },
                    staffAccent = staffAccent,
                    dark = dark,
                )
            } else if (selectedSection == 0) {
                // Staff List View (Flat rows separated by thin dividers, with 3dp left accent border)
                val filteredUsers = users.filter { u ->
                    val matchesSearch = searchQuery.isBlank() ||
                        u.fullName.contains(searchQuery, ignoreCase = true) ||
                        u.email.contains(searchQuery, ignoreCase = true) ||
                        u.role.contains(searchQuery, ignoreCase = true)
                    val matchesFilter = statusFilter == "ALL" || u.role.equals(statusFilter, ignoreCase = true)
                    matchesSearch && matchesFilter
                }

                if (isUsersLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                } else if (filteredUsers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.no_staff_found),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.25f else 0.35f))
                            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                    ) {
                        filteredUsers.forEachIndexed { index, staff ->
                            StaffFlatRow(
                                staff = staff,
                                isCurrentUser = staff.id == currentUser.id,
                                staffAccent = staffAccent,
                                onClick = {
                                    selectedStaffDetail = staff
                                    isEditingRole = false
                                    pendingRoleSelection = null
                                },
                            )
                            if (index < filteredUsers.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 14.dp),
                                    color = scheme.outlineVariant.copy(alpha = 0.25f),
                                )
                            }
                        }
                    }
                }
            } else {
                // Handset List View (Flat rows separated by thin dividers, 3dp left accent border: green for active, red for revoked)
                val filteredDevices = devices.filter { d ->
                    val matchesSearch = searchQuery.isBlank() ||
                        d.name.contains(searchQuery, ignoreCase = true) ||
                        d.userName.contains(searchQuery, ignoreCase = true) ||
                        d.userEmail.contains(searchQuery, ignoreCase = true) ||
                        d.id.contains(searchQuery, ignoreCase = true)
                    val matchesFilter = when (statusFilter) {
                        "ACTIVE" -> !d.isRevoked
                        "REVOKED" -> d.isRevoked
                        else -> true
                    }
                    matchesSearch && matchesFilter
                }

                if (isDevicesLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                } else if (filteredDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.no_handsets_found),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.25f else 0.35f))
                            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                    ) {
                        filteredDevices.forEachIndexed { index, device ->
                            HandsetFlatRow(
                                device = device,
                                handsetAccent = handsetAccent,
                                isConfirmingRevoke = confirmingDeviceId == device.id,
                                isRevoking = revokingDeviceId == device.id,
                                selectedReason = selectedReason,
                                quickReasons = quickReasons,
                                onSelectReason = { selectedReason = it },
                                onStartRevoke = {
                                    confirmingDeviceId = device.id
                                    selectedReason = quickReasons.first()
                                },
                                onCancelRevoke = { confirmingDeviceId = null },
                                onConfirmRevoke = {
                                    revokingDeviceId = device.id
                                    authViewModel.revokeDevice(device.id, selectedReason) { res ->
                                        revokingDeviceId = null
                                        confirmingDeviceId = null
                                        when (res) {
                                            is AuthResult.Success -> {
                                                devices = devices.map {
                                                    if (it.id == device.id) {
                                                        it.copy(
                                                            revokedAt = "Just now",
                                                            revokeReason = selectedReason,
                                                        )
                                                    } else it
                                                }
                                                isErrorFeedback = false
                                                feedbackMessage = "Revoked handset \"${device.name}\" (${device.userName})"
                                            }
                                            is AuthResult.Failure -> {
                                                isErrorFeedback = true
                                                feedbackMessage = res.error.message
                                            }
                                        }
                                    }
                                },
                            )
                            if (index < filteredDevices.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 14.dp),
                                    color = scheme.outlineVariant.copy(alpha = 0.25f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Flat row for a Staff member with a 3dp left accent stripe in Staff Accent.
 * Tapping opens the inline detail view.
 */
@Composable
private fun StaffFlatRow(
    staff: ManagedUser,
    isCurrentUser: Boolean,
    staffAccent: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val initial = staff.fullName.firstOrNull()?.uppercaseChar()?.toString() ?: "U"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 3dp left accent stripe
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(58.dp)
                .background(staffAccent),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // User Avatar Bubble
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(staffAccent.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp,
                    ),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = staff.fullName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isCurrentUser) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "(You)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = staffAccent,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
                Text(
                    text = staff.email,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            StatusBadge(
                kind = when (staff.role) {
                    "ADMIN" -> StatusKind.Info
                    "SUPERVISOR" -> StatusKind.Warning
                    else -> StatusKind.Success
                },
                label = staff.role,
            )
        }
    }
}

/**
 * Inline Detail View for a Staff member.
 * Shows avatar, name (+ (You)), role badge, info section, assigned handsets,
 * and the "Edit role" flow with confirmation and self-demotion warning.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaffDetailView(
    staff: ManagedUser,
    isCurrentUser: Boolean,
    staffDevices: List<ManagedDevice>,
    activeStaffDevices: Int,
    isEditingRole: Boolean,
    pendingRole: String?,
    isSaving: Boolean,
    onBack: () -> Unit,
    onStartEditRole: () -> Unit,
    onSelectPendingRole: (String) -> Unit,
    onCancelRoleEdit: () -> Unit,
    onConfirmRoleEdit: () -> Unit,
    staffAccent: Color,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val initial = staff.fullName.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
    val allRoles = listOf("RUNNER", "SUPERVISOR", "ADMIN")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Back Button
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onBack() }
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = null,
                tint = staffAccent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.btn_back_to_staff),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = staffAccent,
                ),
            )
        }

        // Header: Avatar, Name, Role badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(staffAccent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp,
                    ),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = staff.fullName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface,
                    )
                    if (isCurrentUser) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "(You)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = staffAccent,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
                Text(
                    text = staff.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            StatusBadge(
                kind = when (staff.role) {
                    "ADMIN" -> StatusKind.Info
                    "SUPERVISOR" -> StatusKind.Warning
                    else -> StatusKind.Success
                },
                label = staff.role,
            )
        }

        // Info Block (Email, Phone, Role, Handsets count)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.45f))
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfoRow(label = stringResource(R.string.lbl_email), value = staff.email)
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
            InfoRow(
                label = stringResource(R.string.lbl_phone),
                value = staff.phone.ifBlank { stringResource(R.string.not_provided) },
            )
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
            InfoRow(label = stringResource(R.string.lbl_role), value = staff.role)
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
            InfoRow(
                label = stringResource(R.string.lbl_handsets),
                value = stringResource(R.string.handset_count_format, staffDevices.size, activeStaffDevices),
            )
        }

        // Assigned Handsets Subsection
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.assigned_handsets_title),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                ),
            )

            if (staffDevices.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_assigned_handsets),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.25f else 0.35f))
                        .border(1.dp, scheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                ) {
                    staffDevices.forEachIndexed { idx, d ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = d.name,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = scheme.onSurface,
                                )
                                val shortId = if (d.id.length > 12) "${d.id.take(12)}…" else d.id
                                Text(
                                    text = "ID: $shortId",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            StatusBadge(
                                kind = if (d.isRevoked) StatusKind.Critical else StatusKind.Success,
                                label = if (d.isRevoked) "REVOKED" else "ACTIVE",
                            )
                        }
                        if (idx < staffDevices.lastIndex) {
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }

        // Role Action Block
        if (isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.saving_role),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        } else if (!isEditingRole) {
            // "Edit role" button initially
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(staffAccent.copy(alpha = 0.15f))
                        .border(1.dp, staffAccent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable { onStartEditRole() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.btn_edit_role),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = staffAccent,
                        ),
                    )
                }
            }
        } else {
            // Role Editing expanded block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surface.copy(alpha = if (dark) 0.6f else 0.8f))
                    .border(1.dp, scheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Role options: RUNNER, SUPERVISOR, ADMIN (current role disabled)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    allRoles.forEach { roleOption ->
                        val isCurrentRole = staff.role.equals(roleOption, ignoreCase = true)
                        val isPending = pendingRole.equals(roleOption, ignoreCase = true)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isPending -> staffAccent
                                        isCurrentRole -> scheme.surfaceVariant.copy(alpha = 0.4f)
                                        else -> scheme.surfaceVariant.copy(alpha = 0.8f)
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (isPending) staffAccent else scheme.outlineVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable(enabled = !isCurrentRole) {
                                    onSelectPendingRole(roleOption)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = roleOption,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isPending || isCurrentRole) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = when {
                                    isPending -> Color.White
                                    isCurrentRole -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    else -> scheme.onSurface
                                },
                            )
                        }
                    }
                }

                // If a pending change is selected, show confirmation box
                if (pendingRole != null && !pendingRole.equals(staff.role, ignoreCase = true)) {
                    val isChangingToAdmin = pendingRole.equals("ADMIN", ignoreCase = true)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isCurrentUser) scheme.error.copy(alpha = 0.08f)
                                else scheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                            .border(
                                1.dp,
                                if (isCurrentUser) scheme.error.copy(alpha = 0.3f)
                                else scheme.outlineVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(10.dp),
                            )
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.role_change_confirm, staff.fullName, staff.role, pendingRole),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = scheme.onSurface,
                        )

                        if (isCurrentUser) {
                            Text(
                                text = stringResource(R.string.role_change_self_warn),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = scheme.error,
                                ),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onCancelRoleEdit() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_cancel),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = scheme.onSurfaceVariant,
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            val confirmBg = if (isChangingToAdmin || isCurrentUser) scheme.error else staffAccent
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(confirmBg)
                                    .clickable { onConfirmRoleEdit() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_confirm),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onCancelRoleEdit() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.btn_cancel),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Flat row for a Handset with 3dp left accent stripe (handsetAccent for active, error for revoked).
 * Contains inline revocation flow and reason chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HandsetFlatRow(
    device: ManagedDevice,
    handsetAccent: Color,
    isConfirmingRevoke: Boolean,
    isRevoking: Boolean,
    selectedReason: String,
    quickReasons: List<String>,
    onSelectReason: (String) -> Unit,
    onStartRevoke: () -> Unit,
    onCancelRevoke: () -> Unit,
    onConfirmRevoke: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val edgeColor = if (device.isRevoked) scheme.error else handsetAccent

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        // 3dp left accent stripe
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(if (isConfirmingRevoke || device.isRevoked) 120.dp else 68.dp)
                .background(edgeColor),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Handset icon: uses Handsets accent for active devices, error color for revoked ones
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(edgeColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = edgeColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (device.isCurrentDevice) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(handsetAccent.copy(alpha = 0.15f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.tag_this_handset),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = handsetAccent,
                                        ),
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${device.userName} · ${device.userEmail}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        val shortId = if (device.id.length > 14) "${device.id.take(14)}…" else device.id
                        val seenNote = device.lastSeenAt?.take(16)?.replace('T', ' ') ?: "Recently"
                        Text(
                            text = "ID: $shortId · ${stringResource(R.string.last_active_format, seenNote)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                }

                StatusBadge(
                    kind = if (device.isRevoked) StatusKind.Critical else StatusKind.Success,
                    label = if (device.isRevoked) "REVOKED" else "ACTIVE",
                )
            }

            // Revoked Reason note or Revoke Action button
            if (device.isRevoked) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.error.copy(alpha = 0.08f))
                        .border(1.dp, scheme.error.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.revoked_reason_format,
                            device.revokeReason ?: "Access terminated",
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = scheme.error,
                        ),
                    )
                }
            } else if (!device.isCurrentDevice) {
                if (!isConfirmingRevoke) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(scheme.error.copy(alpha = 0.12f))
                                .border(1.dp, scheme.error.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .clickable(enabled = !isRevoking) { onStartRevoke() }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.btn_revoke_handset),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.error,
                                ),
                            )
                        }
                    }
                } else {
                    // Inline Revoke confirmation drawer
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(scheme.surface.copy(alpha = if (dark) 0.6f else 0.85f))
                            .border(1.dp, scheme.error.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.select_revocation_reason),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = scheme.onSurface,
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            quickReasons.forEach { reasonOption ->
                                val isSelectedReason = selectedReason == reasonOption
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isSelectedReason) handsetAccent
                                            else scheme.surfaceVariant.copy(alpha = 0.6f),
                                        )
                                        .clickable { onSelectReason(reasonOption) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = reasonOption,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelectedReason) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 10.sp,
                                        ),
                                        color = if (isSelectedReason) Color.White else scheme.onSurface,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(enabled = !isRevoking) { onCancelRevoke() }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_cancel),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(scheme.error)
                                    .clickable(enabled = !isRevoking) { onConfirmRevoke() }
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = if (isRevoking) stringResource(R.string.revoking_device) else stringResource(R.string.btn_confirm),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
