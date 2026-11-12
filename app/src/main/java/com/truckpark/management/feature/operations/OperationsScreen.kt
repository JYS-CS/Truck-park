package com.truckpark.management.feature.operations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
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
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Role-adaptive operations workspace screen:
 * - On the Home tab for ADMIN users, displays the live Access & Handset Governance panel
 *   to update staff roles and manage handsets.
 */
@Composable
fun OperationsScreen(
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
                        ),
                        color = MaterialTheme.colorScheme.primary,
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
                    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dark) 0.45f else 0.65f),
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
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

@Composable
private fun AdminManagementPanel(
    currentUser: AuthUser,
    authViewModel: AuthViewModel,
) {
    var isExpanded by remember { mutableStateOf(false) }
    var users by remember { mutableStateOf<List<ManagedUser>>(emptyList()) }
    var devices by remember { mutableStateOf<List<ManagedDevice>>(emptyList()) }
    var isUsersLoading by remember { mutableStateOf(true) }
    var isDevicesLoading by remember { mutableStateOf(true) }

    // Separate search and filter states for Staff and Handsets
    var staffSearchQuery by remember { mutableStateOf("") }
    var staffFilter by remember { mutableStateOf("ALL") }

    var handsetSearchQuery by remember { mutableStateOf("") }
    var handsetFilter by remember { mutableStateOf("ALL") }

    // Detail view state for Staff (tapping a staff row opens an inline detail view in place of the list)
    var selectedStaffDetail by remember { mutableStateOf<ManagedUser?>(null) }
    // Detail view state for Handsets (tapping a handset row opens an inline detail view in place of the list)
    var selectedDeviceDetail by remember { mutableStateOf<ManagedDevice?>(null) }

    // Role editing in Staff Detail view
    var isEditingRole by remember { mutableStateOf(false) }
    var pendingRoleSelection by remember { mutableStateOf<String?>(null) }
    var isSavingRole by remember { mutableStateOf(false) }

    // User permission exceptions state
    var userExceptions by remember { mutableStateOf<List<com.truckpark.management.domain.models.UserPermissionException>>(emptyList()) }
    var isExceptionsLoading by remember { mutableStateOf(false) }

    // Handset inline revoke state
    var confirmingDeviceId by remember { mutableStateOf<String?>(null) }
    var revokingDeviceId by remember { mutableStateOf<String?>(null) }
    var reinstatingDeviceId by remember { mutableStateOf<String?>(null) }
    var selectedReason by remember { mutableStateOf("") }

    val defaultLostReason = stringResource(R.string.reason_lost_stolen)
    val defaultStaffReason = stringResource(R.string.reason_staff_reassigned)
    val defaultDecomReason = stringResource(R.string.reason_decommissioned)
    val defaultPolicyReason = stringResource(R.string.reason_policy_violation)
    val defaultOtherReason = stringResource(R.string.reason_other)

    val quickReasons = remember(defaultLostReason, defaultStaffReason, defaultDecomReason, defaultPolicyReason, defaultOtherReason) {
        listOf(
            defaultLostReason,
            defaultStaffReason,
            defaultDecomReason,
            defaultPolicyReason,
            defaultOtherReason,
        )
    }

    // Feedback strip: stays until user taps "Got it", no auto-dismiss
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isErrorFeedback by remember { mutableStateOf(false) }

    fun refreshExceptions(userId: String) {
        isExceptionsLoading = true
        authViewModel.loadUserExceptions(userId) { result ->
            isExceptionsLoading = false
            when (result) {
                is AuthResult.Success -> userExceptions = result.value
                is AuthResult.Failure -> userExceptions = emptyList()
            }
        }
    }

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
                is AuthResult.Success -> {
                    devices = result.value
                    // Keep handset detail view in sync if open
                    selectedDeviceDetail?.let { detail ->
                        result.value.find { it.id == detail.id }?.let { updated ->
                            selectedDeviceDetail = updated
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

    fun refreshAll() {
        refreshUsers()
        refreshDevices()
    }

    LaunchedEffect(Unit) {
        refreshAll()
    }

    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val primaryAccent = scheme.primary

    val adminCount = users.count { it.role.equals("ADMIN", true) }
    val supervisorCount = users.count { it.role.equals("SUPERVISOR", true) }
    val runnerCount = users.count { it.role.equals("RUNNER", true) }

    val activeDeviceCount = devices.count { !it.isRevoked }
    val revokedDeviceCount = devices.count { it.isRevoked }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "accessChevronRotation",
    )

    // Outer GlassCard container
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 18,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header: Access & Handsets with Chevron Icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                        text = "${users.size} staff members • ${devices.size} handsets registered",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.45f else 0.65f))
                        .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = primaryAccent,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(chevronRotation),
                    )
                }
            }

            // Expanded Content: Staff on Top, Handsets on Bottom
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Feedback strip
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

                    // ==========================================
                    // 1. STAFF SECTION (ON TOP, FULL WIDTH)
                    // ==========================================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.25f else 0.40f))
                            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Section Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(primaryAccent),
                                )
                                Text(
                                    text = stringResource(R.string.tile_staff_label),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                    ),
                                    color = scheme.onSurface,
                                )
                                Text(
                                    text = "(${users.size})",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = primaryAccent,
                                )
                            }
                            Text(
                                text = "$adminCount admin · $supervisorCount sup · $runnerCount run",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = scheme.onSurfaceVariant,
                            )
                        }

                        if (selectedStaffDetail != null) {
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
                                onSelectDevice = { dev ->
                                    selectedStaffDetail = null
                                    selectedDeviceDetail = dev
                                },
                                exceptions = userExceptions,
                                isExceptionsLoading = isExceptionsLoading,
                                onAddException = { perm, grantOrRemove, reason, hours ->
                                    authViewModel.addUserException(
                                        userId = staff.id,
                                        permissionCode = perm,
                                        grantOrRemove = grantOrRemove,
                                        reason = reason,
                                        expiresInHours = hours,
                                    ) { res ->
                                        when (res) {
                                            is AuthResult.Success -> {
                                                isErrorFeedback = false
                                                feedbackMessage = "Permission override added for " + staff.fullName
                                                refreshExceptions(staff.id)
                                            }
                                            is AuthResult.Failure -> {
                                                isErrorFeedback = true
                                                feedbackMessage = res.error.message
                                            }
                                        }
                                    }
                                },
                                onRevokeException = { excId ->
                                    authViewModel.revokeUserException(excId, staff.id) { res ->
                                        when (res) {
                                            is AuthResult.Success -> {
                                                isErrorFeedback = false
                                                feedbackMessage = "Permission override revoked"
                                                refreshExceptions(staff.id)
                                            }
                                            is AuthResult.Failure -> {
                                                isErrorFeedback = true
                                                feedbackMessage = res.error.message
                                            }
                                        }
                                    }
                                },
                                staffAccent = primaryAccent,
                                dark = dark,
                            )
                        } else {
                            // Search field for Staff
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
                                        if (staffSearchQuery.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.search_staff_placeholder),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            )
                                        }
                                        BasicTextField(
                                            value = staffSearchQuery,
                                            onValueChange = { staffSearchQuery = it },
                                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                                color = scheme.onSurface,
                                                fontWeight = FontWeight.Medium,
                                            ),
                                            singleLine = true,
                                            cursorBrush = SolidColor(primaryAccent),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    if (staffSearchQuery.isNotEmpty()) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.clear_search),
                                            tint = scheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .clickable { staffSearchQuery = "" },
                                        )
                                    }
                                }
                            }

                            // Filter Chips for Staff
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val staffFilterPairs = listOf(
                                    "ALL" to stringResource(R.string.filter_all),
                                    "RUNNER" to stringResource(R.string.filter_runner),
                                    "SUPERVISOR" to stringResource(R.string.filter_supervisor),
                                    "ADMIN" to stringResource(R.string.filter_admin),
                                )
                                staffFilterPairs.forEach { (filterKey, filterLabel) ->
                                    val isFilterActive = staffFilter.equals(filterKey, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(
                                                if (isFilterActive) primaryAccent
                                                else scheme.surfaceVariant.copy(alpha = if (dark) 0.45f else 0.7f)
                                            )
                                            .border(
                                                1.dp,
                                                if (isFilterActive) primaryAccent
                                                else scheme.outlineVariant.copy(alpha = 0.35f),
                                                RoundedCornerShape(999.dp),
                                            )
                                            .clickable { staffFilter = filterKey }
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

                            // Staff List
                            val filteredUsers = users.filter { u ->
                                val matchesSearch = staffSearchQuery.isBlank() ||
                                    u.fullName.contains(staffSearchQuery, ignoreCase = true) ||
                                    u.email.contains(staffSearchQuery, ignoreCase = true) ||
                                    u.role.contains(staffSearchQuery, ignoreCase = true)
                                val matchesFilter = staffFilter == "ALL" || u.role.equals(staffFilter, ignoreCase = true)
                                matchesSearch && matchesFilter
                            }

                            if (isUsersLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            } else if (filteredUsers.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
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
                                            staffAccent = primaryAccent,
                                            onClick = {
                                                selectedStaffDetail = staff
                                                isEditingRole = false
                                                pendingRoleSelection = null
                                                refreshExceptions(staff.id)
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
                        }
                    }

                    // ==========================================
                    // 2. HANDSETS SECTION (ON BOTTOM, FULL WIDTH)
                    // ==========================================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.25f else 0.40f))
                            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Section Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(primaryAccent),
                                )
                                Text(
                                    text = stringResource(R.string.tile_handsets_label),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                    ),
                                    color = scheme.onSurface,
                                )
                                Text(
                                    text = "(${devices.size})",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = primaryAccent,
                                )
                            }
                            Text(
                                text = "$activeDeviceCount active · $revokedDeviceCount revoked",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = scheme.onSurfaceVariant,
                            )
                        }

                        if (selectedDeviceDetail != null) {
                            val currentDevice = selectedDeviceDetail!!
                            HandsetDetailView(
                                device = currentDevice,
                                onBack = {
                                    selectedDeviceDetail = null
                                    confirmingDeviceId = null
                                },
                                isConfirmingRevoke = confirmingDeviceId == currentDevice.id,
                                isRevoking = revokingDeviceId == currentDevice.id,
                                selectedReason = selectedReason,
                                quickReasons = quickReasons,
                                onSelectReason = { selectedReason = it },
                                onStartRevoke = {
                                    confirmingDeviceId = currentDevice.id
                                    selectedReason = quickReasons.first()
                                },
                                onCancelRevoke = { confirmingDeviceId = null },
                                onConfirmRevoke = { reasonToSubmit ->
                                    revokingDeviceId = currentDevice.id
                                    authViewModel.revokeDevice(currentDevice.id, reasonToSubmit) { res ->
                                        revokingDeviceId = null
                                        confirmingDeviceId = null
                                        when (res) {
                                            is AuthResult.Success -> {
                                                val updated = currentDevice.copy(
                                                    revokedAt = "Just now",
                                                    revokeReason = reasonToSubmit,
                                                )
                                                selectedDeviceDetail = updated
                                                devices = devices.map { if (it.id == currentDevice.id) updated else it }
                                                isErrorFeedback = false
                                                feedbackMessage = "Revoked handset \"${currentDevice.name}\" (${currentDevice.userName})"
                                            }
                                            is AuthResult.Failure -> {
                                                isErrorFeedback = true
                                                feedbackMessage = res.error.message
                                            }
                                        }
                                    }
                                },
                                isReinstating = reinstatingDeviceId == currentDevice.id,
                                onConfirmReinstate = {
                                    reinstatingDeviceId = currentDevice.id
                                    authViewModel.reinstateDevice(currentDevice.id) { res ->
                                        reinstatingDeviceId = null
                                        when (res) {
                                            is AuthResult.Success -> {
                                                val updated = currentDevice.copy(
                                                    revokedAt = null,
                                                    revokeReason = null,
                                                )
                                                selectedDeviceDetail = updated
                                                devices = devices.map { if (it.id == currentDevice.id) updated else it }
                                                isErrorFeedback = false
                                                feedbackMessage = "Handset \"${currentDevice.name}\" (${currentDevice.userName}) is now allowed"
                                            }
                                            is AuthResult.Failure -> {
                                                isErrorFeedback = true
                                                feedbackMessage = res.error.message
                                            }
                                        }
                                    }
                                },
                                handsetAccent = primaryAccent,
                                dark = dark,
                            )
                        } else {
                            // Search field for Handsets
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
                                        if (handsetSearchQuery.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.search_handsets_placeholder),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            )
                                        }
                                        BasicTextField(
                                            value = handsetSearchQuery,
                                            onValueChange = { handsetSearchQuery = it },
                                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                                color = scheme.onSurface,
                                                fontWeight = FontWeight.Medium,
                                            ),
                                            singleLine = true,
                                            cursorBrush = SolidColor(primaryAccent),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    if (handsetSearchQuery.isNotEmpty()) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.clear_search),
                                            tint = scheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .clickable { handsetSearchQuery = "" },
                                        )
                                    }
                                }
                            }

                            // Filter Chips for Handsets
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val handsetFilterPairs = listOf(
                                    "ALL" to stringResource(R.string.filter_all),
                                    "ACTIVE" to stringResource(R.string.filter_active),
                                    "REVOKED" to stringResource(R.string.filter_revoked),
                                )
                                handsetFilterPairs.forEach { (filterKey, filterLabel) ->
                                    val isFilterActive = handsetFilter.equals(filterKey, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(
                                                if (isFilterActive) primaryAccent
                                                else scheme.surfaceVariant.copy(alpha = if (dark) 0.45f else 0.7f)
                                            )
                                            .border(
                                                1.dp,
                                                if (isFilterActive) primaryAccent
                                                else scheme.outlineVariant.copy(alpha = 0.35f),
                                                RoundedCornerShape(999.dp),
                                            )
                                            .clickable { handsetFilter = filterKey }
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

                            // Handset List
                            val filteredDevices = devices.filter { d ->
                                val matchesSearch = handsetSearchQuery.isBlank() ||
                                    d.name.contains(handsetSearchQuery, ignoreCase = true) ||
                                    d.userName.contains(handsetSearchQuery, ignoreCase = true) ||
                                    d.userEmail.contains(handsetSearchQuery, ignoreCase = true) ||
                                    d.id.contains(handsetSearchQuery, ignoreCase = true)
                                val matchesFilter = when (handsetFilter) {
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
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            } else if (filteredDevices.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
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
                                            handsetAccent = primaryAccent,
                                            onClick = {
                                                selectedDeviceDetail = device
                                                confirmingDeviceId = null
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
            .height(IntrinsicSize.Min)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 3dp left accent stripe matches the content height exactly
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
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
                val displayName = if (isCurrentUser) "${staff.fullName} (You)" else staff.fullName
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
    exceptions: List<com.truckpark.management.domain.models.UserPermissionException>,
    isExceptionsLoading: Boolean,
    onBack: () -> Unit,
    onStartEditRole: () -> Unit,
    onSelectPendingRole: (String) -> Unit,
    onCancelRoleEdit: () -> Unit,
    onConfirmRoleEdit: () -> Unit,
    onSelectDevice: (ManagedDevice) -> Unit = {},
    onAddException: (permissionCode: String, grantOrRemove: String, reason: String, hours: Int?) -> Unit,
    onRevokeException: (exceptionId: String) -> Unit,
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
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                val displayName = if (isCurrentUser) "${staff.fullName} (You)" else staff.fullName
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = scheme.onSurface,
                )
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
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSelectDevice(d) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = d.name,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = scheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (d.isRevoked) {
                                        "Revoked ${formatRelativeTime(d.revokedAt, "recently")}"
                                    } else {
                                        "Last active ${formatRelativeTime(d.lastSeenAt, "recently")}"
                                    },
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

        // Granular Permission Exceptions & Overrides
        PermissionExceptionsSection(
            staff = staff,
            exceptions = exceptions,
            isLoading = isExceptionsLoading,
            onAddException = onAddException,
            onRevokeException = onRevokeException,
            staffAccent = staffAccent,
            dark = dark,
        )
    }
}

private enum class DurationUnit {
    Permanent,
    Hours,
    Days,
}

private data class PermissionItem(
    val code: String,
    val title: String,
    val description: String,
)

private val COMMON_PERMISSIONS = listOf(
    PermissionItem("visit.arrive", "Gate Arrival", "Record truck check-in and gate arrivals"),
    PermissionItem("visit.depart", "Gate Departure", "Record truck check-out and gate departures"),
    PermissionItem("visit.correct_critical", "Critical Edits", "Correct critical visit and payload records"),
    PermissionItem("visit.override_open", "Gate Override", "Manually trigger gate open overrides"),
    PermissionItem("device.manage", "Handset Governance", "Authorize and revoke active handsets"),
    PermissionItem("audit.view", "Audit Logs", "View operational and security audit events"),
    PermissionItem("sync.push", "Push Sync", "Force sync local records to backend"),
    PermissionItem("sync.pull", "Pull Sync", "Pull real-time cloud data updates"),
    PermissionItem("user.invite", "Invite Staff", "Create and send staff invite tokens"),
    PermissionItem("user.approve", "Approve Staff", "Approve or reject pending staff registrations"),
    PermissionItem("user.deactivate", "Deactivate Users", "Deactivate and terminate staff accounts"),
)

private fun formatExpiryTime(raw: String?): String {
    if (raw.isNullOrBlank()) return "Permanent"
    val trimmed = raw.trim()
    val instant = runCatching {
        val normalized = when {
            trimmed.contains('T') && (trimmed.endsWith("Z") || trimmed.contains('+') || trimmed.contains('-')) -> trimmed
            trimmed.contains('T') -> "${trimmed}Z"
            trimmed.contains(' ') -> "${trimmed.replace(' ', 'T')}Z"
            else -> trimmed
        }
        Instant.parse(normalized)
    }.getOrNull() ?: runCatching {
        OffsetDateTime.parse(trimmed).toInstant()
    }.getOrNull() ?: return trimmed.take(19).replace('T', ' ')

    val now = Instant.now()
    if (instant.isBefore(now)) return "Expired"
    val diffSeconds = Duration.between(now, instant).seconds
    val hours = diffSeconds / 3600
    if (hours < 1) {
        val mins = (diffSeconds / 60).coerceAtLeast(1)
        return "Expires in $mins min${if (mins > 1) "s" else ""}"
    }
    if (hours < 24) {
        return "Expires in $hours hr${if (hours > 1) "s" else ""}"
    }
    val days = hours / 24
    val remHours = hours % 24
    return if (remHours > 0) {
        "Expires in $days day${if (days > 1) "s" else ""}, $remHours hr${if (remHours > 1) "s" else ""}"
    } else {
        "Expires in $days day${if (days > 1) "s" else ""}"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionExceptionsSection(
    staff: ManagedUser,
    exceptions: List<com.truckpark.management.domain.models.UserPermissionException>,
    isLoading: Boolean,
    onAddException: (permissionCode: String, grantOrRemove: String, reason: String, hours: Int?) -> Unit,
    onRevokeException: (exceptionId: String) -> Unit,
    staffAccent: Color,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    var isAdding by remember { mutableStateOf(false) }

    var selectedPerm by remember { mutableStateOf(COMMON_PERMISSIONS.first().code) }
    var selectedAction by remember { mutableStateOf("GRANT") } // "GRANT" or "REMOVE"
    var inputReason by remember { mutableStateOf("") }

    var durationUnit by remember { mutableStateOf(DurationUnit.Permanent) }
    var inputDurationValue by remember { mutableStateOf("") }
    var revokingExceptionId by remember { mutableStateOf<String?>(null) }

    val hourPresets = listOf(2, 4, 8, 12, 24, 48)
    val dayPresets = listOf(1, 3, 7, 14, 30)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.exceptions_title),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                    ),
                )
                Text(
                    text = stringResource(R.string.exceptions_subtitle),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = scheme.onSurfaceVariant,
                )
            }

            if (!isAdding) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(staffAccent.copy(alpha = 0.15f))
                        .border(1.dp, staffAccent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .clickable { isAdding = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = staffAccent,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = stringResource(R.string.btn_add_exception),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = staffAccent,
                            ),
                        )
                    }
                }
            }
        }

        // Add Exception Form with Smooth Expansion
        AnimatedVisibility(
            visible = isAdding,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.5f))
                    .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header of Form
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "New Permission Override",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                        ),
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { isAdding = false }
                            .padding(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))

                // 1. Override Action Selector (GRANT vs BLOCK)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.lbl_override_type),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val isGrant = selectedAction == "GRANT"
                        val grantColor = Color(0xFF10B981)
                        val removeColor = scheme.error

                        // GRANT Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isGrant) grantColor.copy(alpha = 0.16f) else scheme.surface.copy(alpha = 0.5f))
                                .border(1.dp, if (isGrant) grantColor else scheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .clickable { selectedAction = "GRANT" }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isGrant) grantColor else scheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = "GRANT ACCESS",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isGrant) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                        ),
                                        color = if (isGrant) grantColor else scheme.onSurface,
                                    )
                                }
                                Text(
                                    text = "Allow specific capability",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }

                        // REMOVE Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (!isGrant) removeColor.copy(alpha = 0.16f) else scheme.surface.copy(alpha = 0.5f))
                                .border(1.dp, if (!isGrant) removeColor else scheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .clickable { selectedAction = "REMOVE" }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        tint = if (!isGrant) removeColor else scheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = "RESTRICT / BLOCK",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (!isGrant) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                        ),
                                        color = if (!isGrant) removeColor else scheme.onSurface,
                                    )
                                }
                                Text(
                                    text = "Block role capability",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // 2. Permission Selector with human titles
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Select Permission",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface,
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        COMMON_PERMISSIONS.forEach { item ->
                            val isSelected = selectedPerm == item.code
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) staffAccent
                                        else scheme.surface.copy(alpha = 0.6f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) staffAccent else scheme.outlineVariant.copy(alpha = 0.35f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable { selectedPerm = item.code }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                            ) {
                                Column {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                        ),
                                        color = if (isSelected) Color.White else scheme.onSurface,
                                    )
                                    Text(
                                        text = item.code,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        ),
                                        color = if (isSelected) Color.White.copy(alpha = 0.85f) else scheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Duration & Expiry Selector (Optional - Days, Hours, or Permanent)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.lbl_duration_type),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = scheme.onSurface,
                        )
                        Text(
                            text = "Optional",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = scheme.onSurfaceVariant,
                            ),
                        )
                    }

                    // 3-way Segmented Switch: Permanent / Hours / Days
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(scheme.surface.copy(alpha = 0.5f))
                            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf(
                            DurationUnit.Permanent to stringResource(R.string.duration_permanent),
                            DurationUnit.Hours to stringResource(R.string.duration_hours),
                            DurationUnit.Days to stringResource(R.string.duration_days),
                        ).forEach { (unit, label) ->
                            val isChosen = durationUnit == unit
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isChosen) staffAccent.copy(alpha = if (dark) 0.3f else 0.2f)
                                        else Color.Transparent
                                    )
                                    .border(
                                        1.dp,
                                        if (isChosen) staffAccent else Color.Transparent,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable {
                                        durationUnit = unit
                                        if (unit == DurationUnit.Permanent) {
                                            inputDurationValue = ""
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                    ),
                                    color = if (isChosen) staffAccent else scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Conditional Input & Presets for Hours / Days
                    when (durationUnit) {
                        DurationUnit.Permanent -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(scheme.surface.copy(alpha = 0.4f))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.duration_permanent_hint),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                        DurationUnit.Hours -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Frosted input box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(scheme.surface.copy(alpha = 0.7f))
                                        .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        BasicTextField(
                                            value = inputDurationValue,
                                            onValueChange = { if (it.all { ch -> ch.isDigit() }) inputDurationValue = it.take(4) },
                                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                                color = scheme.onSurface,
                                                fontWeight = FontWeight.SemiBold,
                                            ),
                                            cursorBrush = SolidColor(staffAccent),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            decorationBox = { inner ->
                                                if (inputDurationValue.isEmpty()) {
                                                    Text(
                                                        text = stringResource(R.string.hint_override_hours),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    )
                                                }
                                                inner()
                                            },
                                        )
                                        Text(
                                            text = "hours",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = staffAccent,
                                            ),
                                        )
                                    }
                                }

                                // Quick presets for Hours
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    hourPresets.forEach { h ->
                                        val isMatch = inputDurationValue == h.toString()
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isMatch) staffAccent.copy(alpha = 0.2f) else scheme.surface.copy(alpha = 0.4f))
                                                .border(1.dp, if (isMatch) staffAccent else scheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                                .clickable { inputDurationValue = h.toString() }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        ) {
                                            Text(
                                                text = "${h}h",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isMatch) FontWeight.Bold else FontWeight.Medium,
                                                ),
                                                color = if (isMatch) staffAccent else scheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        DurationUnit.Days -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Frosted input box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(scheme.surface.copy(alpha = 0.7f))
                                        .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        BasicTextField(
                                            value = inputDurationValue,
                                            onValueChange = { if (it.all { ch -> ch.isDigit() }) inputDurationValue = it.take(3) },
                                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                                color = scheme.onSurface,
                                                fontWeight = FontWeight.SemiBold,
                                            ),
                                            cursorBrush = SolidColor(staffAccent),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            decorationBox = { inner ->
                                                if (inputDurationValue.isEmpty()) {
                                                    Text(
                                                        text = stringResource(R.string.hint_override_days),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    )
                                                }
                                                inner()
                                            },
                                        )
                                        Text(
                                            text = "days",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = staffAccent,
                                            ),
                                        )
                                    }
                                }

                                // Quick presets for Days
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    dayPresets.forEach { d ->
                                        val isMatch = inputDurationValue == d.toString()
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isMatch) staffAccent.copy(alpha = 0.2f) else scheme.surface.copy(alpha = 0.4f))
                                                .border(1.dp, if (isMatch) staffAccent else scheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                                .clickable { inputDurationValue = d.toString() }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        ) {
                                            Text(
                                                text = if (d == 1) "1 day" else "$d days",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isMatch) FontWeight.Bold else FontWeight.Medium,
                                                ),
                                                color = if (isMatch) staffAccent else scheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Business Reason
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.lbl_override_reason),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(scheme.surface.copy(alpha = 0.7f))
                            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        BasicTextField(
                            value = inputReason,
                            onValueChange = { inputReason = it },
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = scheme.onSurface),
                            cursorBrush = SolidColor(staffAccent),
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (inputReason.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.hint_override_reason),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                inner()
                            },
                        )
                    }
                }

                // 5. Actions: Cancel / Save
                val canSave = inputReason.isNotBlank() && selectedPerm.isNotBlank()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                isAdding = false
                                inputReason = ""
                                inputDurationValue = ""
                                durationUnit = DurationUnit.Permanent
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (canSave) staffAccent else scheme.outlineVariant.copy(alpha = 0.3f))
                            .clickable(enabled = canSave) {
                                val finalHours: Int? = when (durationUnit) {
                                    DurationUnit.Permanent -> null
                                    DurationUnit.Hours -> inputDurationValue.trim().toIntOrNull()?.takeIf { it > 0 }
                                    DurationUnit.Days -> inputDurationValue.trim().toIntOrNull()?.takeIf { it > 0 }?.times(24)
                                }
                                onAddException(selectedPerm, selectedAction, inputReason.trim(), finalHours)
                                isAdding = false
                                inputReason = ""
                                inputDurationValue = ""
                                durationUnit = DurationUnit.Permanent
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.btn_save_override),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (canSave) Color.White else scheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }

        // Active Exceptions List
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        } else if (exceptions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.2f else 0.3f))
                    .border(1.dp, scheme.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.no_exceptions),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                exceptions.forEach { exc ->
                    val isGrant = exc.isGrant
                    val grantColor = Color(0xFF10B981)
                    val removeColor = scheme.error
                    val itemColor = if (isGrant) grantColor else removeColor
                    val permTitle = COMMON_PERMISSIONS.find { it.code == exc.permissionCode }?.title ?: exc.permissionCode
                    val isRevokingThis = revokingExceptionId == exc.id

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.5f))
                            .border(1.dp, itemColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Row 1: Badges, Title, Revoke Button
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
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(itemColor.copy(alpha = 0.16f))
                                            .border(1.dp, itemColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 7.dp, vertical = 3.dp),
                                    ) {
                                        Text(
                                            text = if (isGrant) "GRANT" else "BLOCK",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                            ),
                                            color = itemColor,
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = permTitle,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = scheme.onSurface,
                                        )
                                        Text(
                                            text = exc.permissionCode,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            ),
                                            color = scheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                // Revoke button
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(scheme.error.copy(alpha = 0.12f))
                                        .border(1.dp, scheme.error.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                        .clickable(enabled = !isRevokingThis) {
                                            revokingExceptionId = exc.id
                                            onRevokeException(exc.id)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                ) {
                                    if (isRevokingThis) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 1.5.dp,
                                            color = scheme.error,
                                        )
                                    } else {
                                        Text(
                                            text = stringResource(R.string.btn_revoke_override),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = scheme.error,
                                                fontSize = 11.sp,
                                            ),
                                        )
                                    }
                                }
                            }

                            // Row 2: Business Reason
                            if (exc.reason.isNotBlank()) {
                                Text(
                                    text = exc.reason,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = scheme.onSurface,
                                )
                            }

                            // Row 3: Expiry & Metadata Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val expiryText = formatExpiryTime(exc.expiresAt)
                                val isPermanent = exc.expiresAt.isNullOrBlank()
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isPermanent) staffAccent.copy(alpha = 0.12f)
                                            else if (expiryText == "Expired") scheme.error.copy(alpha = 0.12f)
                                            else scheme.surface.copy(alpha = 0.6f)
                                        )
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = expiryText,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        color = if (isPermanent) staffAccent
                                        else if (expiryText == "Expired") scheme.error
                                        else scheme.onSurfaceVariant,
                                    )
                                }

                                if (!exc.approvedAt.isNullOrBlank()) {
                                    Text(
                                        text = formatRelativeTime(exc.approvedAt, "Recently added"),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatRelativeTime(raw: String?, fallback: String = "Recently"): String {
    if (raw.isNullOrBlank()) return fallback
    val trimmed = raw.trim()
    if (trimmed.equals("Just now", ignoreCase = true)) return "Just now"

    val instant = runCatching {
        val normalized = when {
            trimmed.contains('T') && (trimmed.endsWith("Z") || trimmed.contains('+') || trimmed.contains('-')) -> trimmed
            trimmed.contains('T') -> "${trimmed}Z"
            trimmed.contains(' ') -> "${trimmed.replace(' ', 'T')}Z"
            else -> trimmed
        }
        Instant.parse(normalized)
    }.getOrNull() ?: runCatching {
        OffsetDateTime.parse(trimmed).toInstant()
    }.getOrNull() ?: return trimmed.take(19).replace('T', ' ')

    val now = Instant.now()
    val diffSeconds = Duration.between(instant, now).seconds

    if (diffSeconds < 60) return "Just now"

    val minutes = diffSeconds / 60
    if (minutes < 60) {
        return if (minutes == 1L) "1 min ago" else "$minutes mins ago"
    }

    val hours = minutes / 60
    if (hours < 24) {
        return if (hours == 1L) "1 hour ago" else "$hours hours ago"
    }

    val days = hours / 24
    if (days == 1L) return "Yesterday"
    if (days < 7) return "$days days ago"

    val weeks = days / 7
    if (weeks < 4) {
        return if (weeks == 1L) "1 week ago" else "$weeks weeks ago"
    }

    val months = days / 30
    if (months < 12) {
        return if (months <= 1L) "1 month ago" else "$months months ago"
    }

    val years = days / 365
    return if (years <= 1L) "1 year ago" else "$years years ago"
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * Flat row for a Handset matching the clean layout of StaffFlatRow.
 * Shows 3dp accent stripe, handset icon, full responsive device name, owner info, and status badge.
 * Tapping opens HandsetDetailView with full details and actions.
 */
@Composable
private fun HandsetFlatRow(
    device: ManagedDevice,
    handsetAccent: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edgeColor = if (device.isRevoked) scheme.error else handsetAccent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 3dp left accent stripe matching exact dynamic height of row
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(edgeColor),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Handset icon bubble
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

            // Device Name in full and responsive
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (device.isCurrentDevice) {
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

                val lastActiveNote = if (device.isRevoked) {
                    "Revoked ${formatRelativeTime(device.revokedAt, "recently")}"
                } else {
                    "Active ${formatRelativeTime(device.lastSeenAt, "recently")}"
                }

                Text(
                    text = "${device.userName} · $lastActiveNote",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            StatusBadge(
                kind = if (device.isRevoked) StatusKind.Critical else StatusKind.Success,
                label = if (device.isRevoked) "REVOKED" else "ACTIVE",
            )
        }
    }
}

/**
 * Inline Detail View for a Handset.
 * Shows back button, large handset header, complete device info block (name, ID, owner, timestamps,
 * revocation status and reason), and handset revocation workflow with quick reasons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HandsetDetailView(
    device: ManagedDevice,
    onBack: () -> Unit,
    isConfirmingRevoke: Boolean,
    isRevoking: Boolean,
    selectedReason: String,
    quickReasons: List<String>,
    onSelectReason: (String) -> Unit,
    onStartRevoke: () -> Unit,
    onCancelRevoke: () -> Unit,
    onConfirmRevoke: (String) -> Unit,
    isReinstating: Boolean = false,
    onConfirmReinstate: () -> Unit = {},
    handsetAccent: Color,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val edgeColor = if (device.isRevoked) scheme.error else handsetAccent
    var customReasonText by remember(device.id) { mutableStateOf("") }
    var isConfirmingReinstate by remember(device.id) { mutableStateOf(false) }
    val otherLabel = stringResource(R.string.reason_other)
    val isOtherSelected = selectedReason.equals(otherLabel, ignoreCase = true)

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
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = handsetAccent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.btn_back_to_handsets),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = handsetAccent,
                ),
            )
        }

        // Header: Handset Icon Badge, Full Name, Current Device Tag, Status Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(edgeColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (device.isCurrentDevice) {
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
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            StatusBadge(
                kind = if (device.isRevoked) StatusKind.Critical else StatusKind.Success,
                label = if (device.isRevoked) "REVOKED" else "ACTIVE",
            )
        }

        // Complete Device Info Block (Model, Assigned User, User Email, Full Device ID, Last Active, Registered At, Revoked details)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.45f))
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfoRow(label = stringResource(R.string.lbl_device_name), value = device.name)
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
            InfoRow(label = stringResource(R.string.lbl_assigned_to), value = device.userName)
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
            InfoRow(label = stringResource(R.string.lbl_email), value = device.userEmail)
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
            InfoRow(label = stringResource(R.string.lbl_device_id), value = device.id)
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
            InfoRow(
                label = stringResource(R.string.lbl_last_active),
                value = formatRelativeTime(device.lastSeenAt, "Recently active"),
            )
            device.registeredAt?.let { reg ->
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                InfoRow(
                    label = stringResource(R.string.lbl_registered_at),
                    value = formatRelativeTime(reg, "Recently"),
                )
            }
            if (device.isRevoked) {
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                InfoRow(
                    label = stringResource(R.string.lbl_revoked_at),
                    value = formatRelativeTime(device.revokedAt, "Revoked"),
                )
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                InfoRow(label = stringResource(R.string.lbl_revoke_reason), value = device.revokeReason ?: "Access terminated")
            }
        }

        // Action Block: Revocation / Reinstatement / Current Handset Note / Status Message
        if (device.isRevoked) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.error.copy(alpha = 0.1f))
                        .border(1.dp, scheme.error.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.revoked_reason_format,
                            device.revokeReason ?: "Access terminated",
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = scheme.error,
                        ),
                    )
                }

                if (!isConfirmingReinstate) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .clickable(enabled = !isReinstating) { isConfirmingReinstate = true }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = if (isReinstating) stringResource(R.string.allowing_device) else stringResource(R.string.btn_allow_handset),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981),
                                    ),
                                )
                            }
                        }
                    }
                } else {
                    // Confirmation Box to Allow/Reinstate Handset
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.surface.copy(alpha = if (dark) 0.6f else 0.85f))
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.reinstate_handset_confirm_msg),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurface,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isReinstating) { isConfirmingReinstate = false }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
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
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF10B981))
                                    .clickable(enabled = !isReinstating) {
                                        isConfirmingReinstate = false
                                        onConfirmReinstate()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = if (isReinstating) stringResource(R.string.allowing_device) else stringResource(R.string.btn_confirm_allow),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        } else if (device.isCurrentDevice) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(handsetAccent.copy(alpha = 0.12f))
                    .border(1.dp, handsetAccent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.this_device_current_note),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = scheme.onSurface,
                )
            }
        } else if (!isConfirmingRevoke) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.error.copy(alpha = 0.14f))
                        .border(1.dp, scheme.error.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable { onStartRevoke() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
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
            // Revocation Confirmation Box with Reason Chips
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surface.copy(alpha = if (dark) 0.6f else 0.85f))
                    .border(1.dp, scheme.error.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.revoke_handset_confirm_warn),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                )
                Text(
                    text = stringResource(R.string.select_revocation_reason),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    quickReasons.forEach { reasonOption ->
                        val isChosen = selectedReason == reasonOption
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isChosen) scheme.error else scheme.surfaceVariant.copy(alpha = 0.7f)
                                )
                                .border(
                                    1.dp,
                                    if (isChosen) scheme.error else scheme.outlineVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable(enabled = !isRevoking) { onSelectReason(reasonOption) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = reasonOption,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (isChosen) Color.White else scheme.onSurface,
                            )
                        }
                    }
                }

                // If "Other" reason is selected, show custom detail text input box
                AnimatedVisibility(
                    visible = isOtherSelected,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.lbl_custom_reason_title),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = scheme.onSurface,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.5f))
                                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            BasicTextField(
                                value = customReasonText,
                                onValueChange = { customReasonText = it },
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    color = scheme.onSurface,
                                ),
                                cursorBrush = SolidColor(scheme.primary),
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3,
                                decorationBox = { innerTextField ->
                                    if (customReasonText.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.hint_custom_revoke_reason),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                    innerTextField()
                                },
                            )
                        }
                    }
                }

                val canConfirm = !isRevoking && (!isOtherSelected || customReasonText.isNotBlank())
                val finalReason = if (isOtherSelected) {
                    customReasonText.trim().ifEmpty { otherLabel }
                } else {
                    selectedReason
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isRevoking) { onCancelRevoke() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (canConfirm) scheme.error else scheme.error.copy(alpha = 0.4f))
                            .clickable(enabled = canConfirm) { onConfirmRevoke(finalReason) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
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
