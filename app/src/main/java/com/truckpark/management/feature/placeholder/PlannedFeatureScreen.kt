package com.truckpark.management.feature.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truckpark.management.R
import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.core.designsystem.components.GlassCard
import com.truckpark.management.core.designsystem.components.StatusBadge
import com.truckpark.management.core.designsystem.components.StatusKind
import com.truckpark.management.core.designsystem.icons.TruckParkIcons
import com.truckpark.management.domain.models.AuthUser
import com.truckpark.management.domain.models.ManagedDevice
import com.truckpark.management.domain.models.ManagedUser
import com.truckpark.management.feature.auth.AuthViewModel

/**
 * Role-adaptive workspace screen:
 * - Shows role-specific capabilities and navigation scope (RUNNER, SUPERVISOR, ADMIN).
 * - On the Home tab for ADMIN users, displays the live Staff & Role Management panel
 *   to update any user's role between RUNNER, SUPERVISOR, and ADMIN.
 */
@OptIn(ExperimentalLayoutApi::class)
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
        // Top Header: HIBISCUSS Wordmark in Brand Gradient + "Truck Park" subtitle + Hamburger Menu Button
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
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color(0xFF00F5FF), // Cyan
                                androidx.compose.ui.graphics.Color(0xFF26BFFF), // Sky blue
                                androidx.compose.ui.graphics.Color(0xFF4938FF), // Royal Indigo
                                androidx.compose.ui.graphics.Color(0xFFC500FF), // Magenta
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
                    androidx.compose.material3.IconButton(
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
                RoleWorkspaceCard(user = user)
            }

            if (user != null && user.isAdmin && isHomeTab && authViewModel != null) {
                AdminRoleManagementCard(
                    currentAdminId = user.id,
                    authViewModel = authViewModel,
                )
                AdminDeviceManagementCard(
                    authViewModel = authViewModel,
                )
            }

            // Feature Card with Icon Orb
        GlassCard(Modifier.fillMaxWidth(), contentPadding = 24) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Circular Icon Orb
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

                // Milestone Scope Pill (matches HTML indigo pill)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleWorkspaceCard(user: AuthUser) {
    val role = user.primaryRole
    val (roleTitle, roleSubtitle, capabilities) = when (role) {
        "ADMIN" -> Triple(
            "System Administrator Workspace",
            "Full access to Home, Visits, Registers, Reports, and Staff Role Management.",
            listOf("Manage Staff Roles", "Invite Users", "Revoke Devices", "Audit Trail", "All Reports"),
        )
        "SUPERVISOR" -> Triple(
            "Yard Supervisor Workspace",
            "Access to Home, Visits, and Registers with override and correction controls.",
            listOf("Gate Arrivals", "Gate Departures", "Yard Registers", "Critical Corrections", "Visit Overrides"),
        )
        else -> Triple(
            "Gate Runner Workspace",
            "Field gate operations workspace with Home and Visits access.",
            listOf("Log Vehicle Arrivals", "Log Vehicle Departures", "Offline Queue Sync"),
        )
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Welcome, ${user.fullName.ifBlank { "Operator" }}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = roleTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                StatusBadge(
                    kind = when (role) {
                        "ADMIN" -> StatusKind.Info
                        "SUPERVISOR" -> StatusKind.Warning
                        else -> StatusKind.Success
                    },
                    label = role,
                )
            }

            Text(
                text = roleSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                capabilities.forEach { item ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminRoleManagementCard(
    currentAdminId: String,
    authViewModel: AuthViewModel,
) {
    var users by remember { mutableStateOf<List<ManagedUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var updatingUserId by remember { mutableStateOf<String?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isErrorFeedback by remember { mutableStateOf(false) }

    fun refreshUsers() {
        isLoading = true
        authViewModel.loadManagedUsers { result ->
            isLoading = false
            when (result) {
                is AuthResult.Success -> {
                    users = result.value
                }
                is AuthResult.Failure -> {
                    isErrorFeedback = true
                    feedbackMessage = result.error.message
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshUsers()
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Staff & Role Management",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "New accounts default to RUNNER. Tap a role below to update any staff member's access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .clickable { refreshUsers() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Refresh",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (!feedbackMessage.isNullOrBlank()) {
                val fbColor = if (isErrorFeedback) Color(0xFFEF4444) else Color(0xFF10B981)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(fbColor.copy(alpha = 0.12f))
                        .border(1.dp, fbColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = feedbackMessage!!,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = fbColor,
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                }
            } else if (users.isEmpty()) {
                Text(
                    text = "No registered users found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val roles = listOf("RUNNER", "SUPERVISOR", "ADMIN")
                users.forEach { staff ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(14.dp),
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = staff.fullName + if (staff.id == currentAdminId) " (You)" else "",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = staff.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            roles.forEach { roleOption ->
                                val isSelected = staff.role.equals(roleOption, ignoreCase = true)
                                val isUpdating = updatingUserId == staff.id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                        .clickable(enabled = !isSelected && !isUpdating) {
                                            updatingUserId = staff.id
                                            feedbackMessage = null
                                            authViewModel.updateUserRole(staff.id, roleOption) { res ->
                                                updatingUserId = null
                                                when (res) {
                                                    is AuthResult.Success -> {
                                                        users = users.map {
                                                            if (it.id == staff.id) it.copy(role = roleOption, status = "APPROVED") else it
                                                        }
                                                        isErrorFeedback = false
                                                        feedbackMessage = "${staff.fullName} updated to $roleOption"
                                                    }
                                                    is AuthResult.Failure -> {
                                                        isErrorFeedback = true
                                                        feedbackMessage = res.error.message
                                                    }
                                                }
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = roleOption,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp,
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminDeviceManagementCard(
    authViewModel: AuthViewModel,
) {
    var devices by remember { mutableStateOf<List<ManagedDevice>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var confirmingDeviceId by remember { mutableStateOf<String?>(null) }
    var selectedReason by remember { mutableStateOf("Lost or stolen handset") }
    var revokingDeviceId by remember { mutableStateOf<String?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isErrorFeedback by remember { mutableStateOf(false) }

    val quickReasons = listOf(
        "Lost or stolen handset",
        "Staff member reassigned",
        "Decommissioned device",
        "Security policy violation",
    )

    fun refreshDevices() {
        isLoading = true
        authViewModel.loadManagedDevices { result ->
            isLoading = false
            when (result) {
                is AuthResult.Success -> {
                    devices = result.value
                }
                is AuthResult.Failure -> {
                    isErrorFeedback = true
                    feedbackMessage = result.error.message
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshDevices()
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Registered Handsets & Device Control",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Revoking a lost, stolen, or reassigned handset immediately terminates all active sessions on that device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .clickable { refreshDevices() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Refresh",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (!feedbackMessage.isNullOrBlank()) {
                val fbColor = if (isErrorFeedback) Color(0xFFEF4444) else Color(0xFF10B981)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(fbColor.copy(alpha = 0.12f))
                        .border(1.dp, fbColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = feedbackMessage!!,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = fbColor,
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                }
            } else if (devices.isEmpty()) {
                Text(
                    text = "No registered handsets found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                devices.forEach { device ->
                    val isConfirming = confirmingDeviceId == device.id
                    val isRevoking = revokingDeviceId == device.id

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .border(
                                width = 1.dp,
                                color = if (device.isRevoked) Color(0xFFEF4444).copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(14.dp),
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.name + if (device.isCurrentDevice) " (This Handset)" else "",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${device.userName} · ${device.userEmail}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val shortId = if (device.id.length > 12) "${device.id.take(12)}…" else device.id
                                val seenNote = device.lastSeenAt?.take(16)?.replace('T', ' ') ?: "Recently"
                                Text(
                                    text = "ID: $shortId · Last active: $seenNote",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                )
                            }
                            StatusBadge(
                                kind = if (device.isRevoked) StatusKind.Critical else StatusKind.Success,
                                label = if (device.isRevoked) "REVOKED" else "ACTIVE",
                            )
                        }

                        if (device.isRevoked) {
                            Text(
                                text = "Reason: ${device.revokeReason ?: "Revoked by administrator"}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = Color(0xFFEF4444),
                            )
                        } else if (!device.isCurrentDevice) {
                            if (!isConfirming) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                            .clickable(enabled = !isRevoking) {
                                                confirmingDeviceId = device.id
                                                selectedReason = quickReasons.first()
                                                feedbackMessage = null
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Text(
                                            text = "Revoke Handset",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFFEF4444),
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "Select revocation reason (audited):",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        quickReasons.forEach { reasonOption ->
                                            val isSelectedReason = selectedReason == reasonOption
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isSelectedReason) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                    )
                                                    .clickable { selectedReason = reasonOption }
                                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                            ) {
                                                Text(
                                                    text = reasonOption,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = if (isSelectedReason) FontWeight.Bold else FontWeight.Normal,
                                                    ),
                                                    color = if (isSelectedReason) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurface,
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
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable(enabled = !isRevoking) {
                                                    confirmingDeviceId = null
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                        ) {
                                            Text(
                                                text = "Cancel",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFEF4444))
                                                .clickable(enabled = !isRevoking) {
                                                    revokingDeviceId = device.id
                                                    feedbackMessage = null
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
                                                                    } else {
                                                                        it
                                                                    }
                                                                }
                                                                isErrorFeedback = false
                                                                feedbackMessage = "Revoked handset ${device.name} (${device.userName})"
                                                            }
                                                            is AuthResult.Failure -> {
                                                                isErrorFeedback = true
                                                                feedbackMessage = res.error.message
                                                            }
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                        ) {
                                            Text(
                                                text = if (isRevoking) "Revoking..." else "Confirm Revoke",
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
        }
    }
}
