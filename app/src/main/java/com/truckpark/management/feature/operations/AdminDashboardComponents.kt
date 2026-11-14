package com.truckpark.management.feature.operations

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truckpark.management.core.designsystem.components.GlassCard
import com.truckpark.management.domain.models.AuthUser
import com.truckpark.management.domain.models.ManagedDevice
import com.truckpark.management.domain.models.ManagedUser
import com.truckpark.management.domain.models.SealCondition
import com.truckpark.management.domain.models.TruckVisit
import com.truckpark.management.domain.models.VisitSeal
import com.truckpark.management.domain.models.VisitStatus
import com.truckpark.management.domain.models.VisitType
import com.truckpark.management.domain.models.countryFlagForCode
import com.truckpark.management.domain.models.countryNameForCode
import com.truckpark.management.domain.models.extractPlateInfo
import com.truckpark.management.feature.visits.VisitsUiState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DashTimestampFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())

private val DashFullDateFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())

private val DayLabelFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

private data class UserSessionSummary(
    val userId: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val role: String,
    val status: String,
    val emailVerified: Boolean,
    val createdAt: Instant?,
    val failedAttempts: Int,
    val lockedUntil: Instant?,
    val isLocked: Boolean,
    val deactivateAt: Instant?,
    val lastActiveAt: Instant?,
    val arrivalsLogged: Int,
    val departuresLogged: Int,
    val todayHandled: Int,
    val userDevices: List<ManagedDevice>,
)

private data class DailyTrendBucket(
    val date: LocalDate,
    val label: String,
    val arrivals: Int,
    val departures: Int,
)

private data class ShiftActivityBucket(
    val title: String,
    val timeWindow: String,
    val arrivals: Int,
    val departures: Int,
) {
    val total: Int get() = arrivals + departures
}

private data class TransporterSummary(
    val name: String,
    val totalVisits: Int,
    val onYardNow: Int,
)

private data class YardActivityEvent(
    val id: String,
    val visitId: String,
    val isDeparture: Boolean,
    val timestamp: Instant,
    val countryCode: String,
    val frontPlate: String,
    val horsePlate: String,
    val visitTypeLabel: String,
    val transporter: String?,
    val driverName: String?,
    val driverPhone: String?,
    val staffName: String?,
    val cargoCount: Int,
    val sealCount: Int,
    val flaggedSealCount: Int,
)

private data class OverstayTruckAlert(
    val visitId: String,
    val countryCode: String,
    val frontPlate: String,
    val horsePlate: String,
    val arrivedAt: Instant,
    val stayDurationLabel: String,
    val hoursOnYard: Long,
    val visitTypeLabel: String,
    val transporter: String?,
    val driverName: String?,
    val driverPhone: String?,
    val arrivedByStaff: String?,
    val cargoCount: Int,
    val sealCount: Int,
)

private data class SealIssueAlert(
    val visitId: String,
    val countryCode: String,
    val frontPlate: String,
    val horsePlate: String,
    val isOnYard: Boolean,
    val transporter: String?,
    val driverName: String?,
    val driverPhone: String?,
    val flaggedSeals: List<VisitSeal>,
    val recordedAt: Instant,
    val staffName: String?,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdminLiveOperationsDashboard(
    currentUser: AuthUser,
    visitsState: VisitsUiState,
    users: List<ManagedUser>,
    devices: List<ManagedDevice>,
    onNavigateToVisits: () -> Unit,
    onRefreshDashboard: () -> Unit,
    onUnlockOrEnableUser: ((userId: String, role: String, onDone: (Boolean, String) -> Unit) -> Unit)? = null,
    onDeactivateUser: ((userId: String, onDone: (Boolean, String) -> Unit) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val context = LocalContext.current

    val allVisits = remember(visitsState.openVisits, visitsState.recentCompleted) {
        (visitsState.openVisits + visitsState.recentCompleted).distinctBy { it.id }
    }

    val now = remember(allVisits, visitsState.lastSyncedAt, users, devices) { Instant.now() }
    val zone = remember { ZoneId.systemDefault() }
    val todayDate = remember(now, zone) { LocalDate.now(zone) }

    // Extract plate/staff metadata once
    val visitPlateInfos = remember(allVisits) {
        allVisits.associate { v ->
            v.id to extractPlateInfo(v.plateNumber, v.arrivalNotes, v.departureNotes)
        }
    }

    // 1. Overstaying trucks (> 24 hours on yard)
    val overstayAlerts = remember(visitsState.openVisits, visitPlateInfos, now) {
        visitsState.openVisits
            .mapNotNull { v ->
                val mins = Duration.between(v.arrivedAt, now).toMinutes().coerceAtLeast(0)
                val hrs = mins / 60
                if (hrs < 24) return@mapNotNull null
                val remMins = mins % 60
                val days = hrs / 24
                val remHrs = hrs % 24
                val durationLabel = if (days > 0) {
                    "${days}d ${remHrs}h ${remMins}m"
                } else {
                    "${hrs}h ${remMins}m"
                }
                val info = visitPlateInfos[v.id]
                OverstayTruckAlert(
                    visitId = v.id,
                    countryCode = info?.country?.ifBlank { "ZM" } ?: "ZM",
                    frontPlate = info?.frontPlate?.ifBlank { v.plateNumber } ?: v.plateNumber,
                    horsePlate = info?.horsePlate.orEmpty(),
                    arrivedAt = v.arrivedAt,
                    stayDurationLabel = durationLabel,
                    hoursOnYard = hrs,
                    visitTypeLabel = info?.customPurpose ?: v.visitType.label,
                    transporter = info?.transporter?.takeIf { it.isNotBlank() },
                    driverName = v.driverName?.takeIf { it.isNotBlank() },
                    driverPhone = v.driverPhone?.takeIf { it.isNotBlank() },
                    arrivedByStaff = info?.arrivedByName?.takeIf { it.isNotBlank() },
                    cargoCount = v.cargoItems.size,
                    sealCount = v.seals.size,
                )
            }
            .sortedByDescending { it.hoursOnYard }
    }

    // 2. Broken / Tampered / Missing Seal Alerts
    val sealAlerts = remember(allVisits, visitPlateInfos) {
        allVisits.mapNotNull { v ->
            val badSeals = v.seals.filter { it.condition != SealCondition.Intact }
            if (badSeals.isEmpty()) return@mapNotNull null
            val info = visitPlateInfos[v.id]
            SealIssueAlert(
                visitId = v.id,
                countryCode = info?.country?.ifBlank { "ZM" } ?: "ZM",
                frontPlate = info?.frontPlate?.ifBlank { v.plateNumber } ?: v.plateNumber,
                horsePlate = info?.horsePlate.orEmpty(),
                isOnYard = v.status == VisitStatus.Open,
                transporter = info?.transporter?.takeIf { it.isNotBlank() },
                driverName = v.driverName?.takeIf { it.isNotBlank() },
                driverPhone = v.driverPhone?.takeIf { it.isNotBlank() },
                flaggedSeals = badSeals,
                recordedAt = v.departedAt ?: v.arrivedAt,
                staffName = info?.departedByName ?: info?.arrivedByName,
            )
        }.sortedByDescending { it.recordedAt }
    }

    val longStayCount = overstayAlerts.size

    // Average stay duration for completed visits
    val avgStayLabel = remember(visitsState.recentCompleted) {
        val durations = visitsState.recentCompleted.mapNotNull { v ->
            v.departedAt?.let { dep -> Duration.between(v.arrivedAt, dep).toMinutes().coerceAtLeast(0) }
        }
        if (durations.isEmpty()) {
            "—"
        } else {
            val avgMins = durations.average().toLong()
            val hrs = avgMins / 60
            val mins = avgMins % 60
            if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
        }
    }

    // Seals & cargo metrics
    val totalSeals = remember(allVisits) { allVisits.sumOf { it.seals.size } }
    val flaggedSeals = remember(allVisits) {
        allVisits.sumOf { v -> v.seals.count { it.condition != SealCondition.Intact } }
    }
    val totalCargoItems = remember(allVisits) { allVisits.sumOf { it.cargoItems.size } }

    // 7-day Arrivals & Departures trend
    val trendBuckets = remember(allVisits, todayDate, zone) {
        val days = (6 downTo 0).map { offset -> todayDate.minusDays(offset.toLong()) }
        days.map { day ->
            val arrivals = allVisits.count { v ->
                v.arrivedAt.atZone(zone).toLocalDate() == day
            }
            val departures = allVisits.count { v ->
                v.departedAt?.atZone(zone)?.toLocalDate() == day
            }
            DailyTrendBucket(
                date = day,
                label = if (day == todayDate) "Today" else day.format(DayLabelFmt),
                arrivals = arrivals,
                departures = departures,
            )
        }
    }

    // Shift & Peak Hours Insights (Morning 06-12, Afternoon 12-18, Night 18-06)
    val shiftBuckets = remember(allVisits, zone) {
        var morningIn = 0
        var morningOut = 0
        var afternoonIn = 0
        var afternoonOut = 0
        var nightIn = 0
        var nightOut = 0

        allVisits.forEach { v ->
            val arrHour = v.arrivedAt.atZone(zone).hour
            when (arrHour) {
                in 6..11 -> morningIn++
                in 12..17 -> afternoonIn++
                else -> nightIn++
            }
            v.departedAt?.let { dep ->
                val depHour = dep.atZone(zone).hour
                when (depHour) {
                    in 6..11 -> morningOut++
                    in 12..17 -> afternoonOut++
                    else -> nightOut++
                }
            }
        }

        listOf(
            ShiftActivityBucket("Morning Shift", "06:00 – 12:00", morningIn, morningOut),
            ShiftActivityBucket("Afternoon Shift", "12:00 – 18:00", afternoonIn, afternoonOut),
            ShiftActivityBucket("Night Shift", "18:00 – 06:00", nightIn, nightOut),
        )
    }

    // Visit Purpose Breakdown
    val purposeCounts = remember(allVisits) {
        VisitType.entries.map { type ->
            type to allVisits.count { it.visitType == type }
        }.sortedByDescending { it.second }
    }

    // Top Transport Companies / Carriers
    val topTransporters = remember(allVisits, visitPlateInfos) {
        allVisits
            .mapNotNull { v ->
                val rawName = visitPlateInfos[v.id]?.transporter?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                rawName to (v.status == VisitStatus.Open)
            }
            .groupBy { it.first.uppercase(Locale.ROOT) }
            .map { (_, entries) ->
                val displayName = entries.first().first
                TransporterSummary(
                    name = displayName,
                    totalVisits = entries.size,
                    onYardNow = entries.count { it.second },
                )
            }
            .sortedWith(
                compareByDescending<TransporterSummary> { it.onYardNow }
                    .thenByDescending { it.totalVisits }
                    .thenBy { it.name },
            )
            .take(5)
    }

    // Top Origin Countries
    val topCountries = remember(visitPlateInfos) {
        visitPlateInfos.values
            .groupBy { it.country.ifBlank { "ZM" } }
            .map { (code, list) -> code to list.size }
            .sortedByDescending { it.second }
            .take(5)
    }

    // Logged-in / Active Users summary (grouped by USER, with full account & handset info)
    val userSummaries = remember(users, devices, currentUser, visitPlateInfos, allVisits, now, todayDate, zone) {
        val baseUsers = if (users.isNotEmpty()) {
            users
        } else {
            listOf(
                ManagedUser(
                    id = currentUser.id,
                    fullName = currentUser.fullName,
                    email = currentUser.email,
                    phone = currentUser.phone.orEmpty(),
                    status = currentUser.status,
                    role = currentUser.primaryRole,
                    emailVerified = currentUser.emailVerified,
                ),
            )
        }

        baseUsers.map { u ->
            val matchingDevices = devices.filter {
                it.userId == u.id || (u.email.isNotBlank() && it.userEmail.equals(u.email, ignoreCase = true))
            }
            val activeDevices = matchingDevices.filter { !it.isRevoked }
            val latestSeen = activeDevices
                .mapNotNull { parseIsoInstant(it.lastSeenAt) }
                .maxOrNull()
                ?: if (u.id == currentUser.id) now else null

            val arrivalsCount = allVisits.count { v ->
                val by = visitPlateInfos[v.id]?.arrivedByName
                !by.isNullOrBlank() && (
                    by.equals(u.fullName, ignoreCase = true) ||
                        by.equals(u.email, ignoreCase = true)
                    )
            }
            val departuresCount = allVisits.count { v ->
                val by = visitPlateInfos[v.id]?.departedByName
                !by.isNullOrBlank() && (
                    by.equals(u.fullName, ignoreCase = true) ||
                        by.equals(u.email, ignoreCase = true)
                    )
            }
            val todayCount = allVisits.count { v ->
                val info = visitPlateInfos[v.id]
                val arrByUser = !info?.arrivedByName.isNullOrBlank() && (
                    info!!.arrivedByName.equals(u.fullName, ignoreCase = true) ||
                        info.arrivedByName.equals(u.email, ignoreCase = true)
                    ) && v.arrivedAt.atZone(zone).toLocalDate() == todayDate
                val depByUser = !info?.departedByName.isNullOrBlank() && (
                    info!!.departedByName.equals(u.fullName, ignoreCase = true) ||
                        info.departedByName.equals(u.email, ignoreCase = true)
                    ) && v.departedAt?.atZone(zone)?.toLocalDate() == todayDate
                arrByUser || depByUser
            }

            val parsedLockedUntil = parseIsoInstant(u.lockedUntil)
            val isCurrentlyLocked = u.isLocked ||
                (parsedLockedUntil != null && parsedLockedUntil.isAfter(now)) ||
                u.status.equals("DEACTIVATED", ignoreCase = true) ||
                u.status.equals("REJECTED", ignoreCase = true) ||
                u.status.equals("LOCKED", ignoreCase = true)

            UserSessionSummary(
                userId = u.id,
                fullName = u.fullName.ifBlank { u.email },
                email = u.email,
                phone = u.phone,
                role = u.role,
                status = u.status,
                emailVerified = u.emailVerified,
                createdAt = parseIsoInstant(u.createdAt),
                failedAttempts = u.failedAttempts,
                lockedUntil = parsedLockedUntil,
                isLocked = isCurrentlyLocked,
                deactivateAt = parseIsoInstant(u.deactivateAt),
                lastActiveAt = latestSeen,
                arrivalsLogged = arrivalsCount,
                departuresLogged = departuresCount,
                todayHandled = todayCount,
                userDevices = matchingDevices,
            )
        }.sortedWith(
            compareByDescending<UserSessionSummary> { it.lastActiveAt ?: Instant.EPOCH }
                .thenByDescending { it.arrivalsLogged + it.departuresLogged }
                .thenBy { it.fullName },
        )
    }

    // Recent yard operations (balanced 4 On Yard + 4 Departed, backfilling up to 8 if one side has fewer)
    val recentEvents = remember(visitsState.openVisits, visitsState.recentCompleted, visitPlateInfos) {
        fun toEvent(v: TruckVisit, isDeparture: Boolean): YardActivityEvent {
            val info = visitPlateInfos[v.id]
            val country = info?.country?.ifBlank { "ZM" } ?: "ZM"
            val front = info?.frontPlate?.ifBlank { v.plateNumber } ?: v.plateNumber
            val horse = info?.horsePlate.orEmpty()
            return YardActivityEvent(
                id = if (isDeparture) "${v.id}_out" else "${v.id}_in",
                visitId = v.id,
                isDeparture = isDeparture,
                timestamp = if (isDeparture) (v.departedAt ?: v.arrivedAt) else v.arrivedAt,
                countryCode = country,
                frontPlate = front,
                horsePlate = horse,
                visitTypeLabel = info?.customPurpose ?: v.visitType.label,
                transporter = info?.transporter?.takeIf { it.isNotBlank() },
                driverName = v.driverName?.takeIf { it.isNotBlank() },
                driverPhone = v.driverPhone?.takeIf { it.isNotBlank() },
                staffName = if (isDeparture) info?.departedByName else info?.arrivedByName,
                cargoCount = v.cargoItems.size,
                sealCount = v.seals.size,
                flaggedSealCount = v.seals.count { it.condition != SealCondition.Intact },
            )
        }

        val yardEvents = visitsState.openVisits
            .sortedByDescending { it.arrivedAt }
            .map { toEvent(it, isDeparture = false) }
        val departEvents = visitsState.recentCompleted
            .filter { it.departedAt != null }
            .sortedByDescending { it.departedAt }
            .map { toEvent(it, isDeparture = true) }

        val targetPerSide = 4
        val initialDepart = departEvents.take(targetPerSide)
        val initialYard = yardEvents.take(targetPerSide)

        val extraYardNeeded = (8 - initialYard.size - initialDepart.size).coerceAtLeast(0)
        val selectedYard = if (initialDepart.size < targetPerSide) {
            yardEvents.take(targetPerSide + extraYardNeeded)
        } else {
            initialYard
        }

        val remainingSlots = (8 - selectedYard.size - initialDepart.size).coerceAtLeast(0)
        val selectedDepart = if (selectedYard.size < targetPerSide) {
            departEvents.take(targetPerSide + remainingSlots)
        } else {
            initialDepart
        }

        (selectedYard + selectedDepart)
            .sortedByDescending { it.timestamp }
            .take(8)
    }

    val totalAlertsCount = overstayAlerts.size +
        sealAlerts.size +
        visitsState.conflictVisits.size +
        (if (visitsState.pendingSyncCount > 0) 1 else 0)

    var showAlertsCard by remember(totalAlertsCount > 0) { mutableStateOf(totalAlertsCount > 0) }
    var showTrendsCard by remember { mutableStateOf(true) }
    var showTimelineCard by remember { mutableStateOf(false) }
    var showUsersCard by remember { mutableStateOf(true) }
    var useLineTrendChart by remember { mutableStateOf(true) }

    var expandedAlertIds by remember { mutableStateOf(setOf<String>()) }
    var expandedEventIds by remember { mutableStateOf(setOf<String>()) }
    var expandedUserIds by remember { mutableStateOf(setOf<String>()) }

    var busyActionUserId by remember { mutableStateOf<String?>(null) }
    var confirmingDisableUserId by remember { mutableStateOf<String?>(null) }
    var userActionBanner by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    fun dialPhoneNumber(phone: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}"))
            context.startActivity(intent)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. OVERVIEW HEADER + KPIs (Refresh & Visits buttons removed; uses pull-down to refresh)
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = visitsState.lastSyncedAt?.let {
                        "Last updated ${DashTimestampFmt.format(it)} • Pull down to refresh"
                    } ?: "Saved on device & cloud • Pull down to refresh",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariant,
                    ),
                )

                // 2x2 KPI Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DashboardKpiTile(
                        modifier = Modifier.weight(1f),
                        title = "On Yard Now",
                        value = "${visitsState.openVisits.size}",
                        subtitle = if (longStayCount > 0) "$longStayCount parked over 24h" else "Trucks currently inside",
                        icon = Icons.Default.LocalShipping,
                        accent = Color(0xFF10B981),
                        dark = dark,
                    )
                    DashboardKpiTile(
                        modifier = Modifier.weight(1f),
                        title = "Departed",
                        value = "${visitsState.recentCompleted.size}",
                        subtitle = "Average stay: $avgStayLabel",
                        icon = Icons.Default.Schedule,
                        accent = Color(0xFF6366F1),
                        dark = dark,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val syncAccent = when {
                        visitsState.conflictVisits.isNotEmpty() -> Color(0xFFEF4444)
                        visitsState.pendingSyncCount > 0 -> Color(0xFFF59E0B)
                        else -> Color(0xFF10B981)
                    }
                    DashboardKpiTile(
                        modifier = Modifier.weight(1f),
                        title = "Backup & Sync",
                        value = when {
                            visitsState.conflictVisits.isNotEmpty() -> "${visitsState.conflictVisits.size} Needs Review"
                            visitsState.pendingSyncCount > 0 -> "${visitsState.pendingSyncCount} Waiting"
                            else -> "All Saved"
                        },
                        subtitle = if (visitsState.isSyncing) "Saving to cloud..." else "Device + Cloud",
                        icon = if (visitsState.pendingSyncCount > 0) Icons.Default.CloudUpload else Icons.Default.CloudDone,
                        accent = syncAccent,
                        dark = dark,
                    )
                    DashboardKpiTile(
                        modifier = Modifier.weight(1f),
                        title = "Cargo & Seals",
                        value = "$totalSeals Seals",
                        subtitle = if (flaggedSeals > 0) "$flaggedSeals broken/damaged • $totalCargoItems items" else "$totalCargoItems cargo items logged",
                        icon = if (flaggedSeals > 0) Icons.Default.Warning else Icons.Default.Lock,
                        accent = if (flaggedSeals > 0) Color(0xFFEF4444) else Color(0xFF0284C7),
                        dark = dark,
                    )
                }
            }
        }

        // 2. YARD ALERTS & ATTENTION NEEDED (Overstaying trucks > 24h, Broken/Damaged Seals, Sync Queue)
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardCollapsibleHeader(
                    title = if (totalAlertsCount > 0) {
                        "ATTENTION NEEDED ($totalAlertsCount)"
                    } else {
                        "YARD HEALTH & ALERTS (ALL CLEAR)"
                    },
                    subtitle = if (totalAlertsCount > 0) {
                        "Overstaying trucks (>24h), seal issues & pending cloud backups"
                    } else {
                        "No overstaying trucks, damaged seals, or sync issues right now"
                    },
                    expanded = showAlertsCard,
                    onToggle = { showAlertsCard = !showAlertsCard },
                )

                AnimatedVisibility(
                    visible = showAlertsCard,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    if (totalAlertsCount == 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF10B981).copy(alpha = if (dark) 0.16f else 0.10f))
                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Everything is running smoothly",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSurface,
                                    ),
                                )
                                Text(
                                    text = "All trucks on yard are under 24 hours, all recorded seals are intact, and device records are backed up to the cloud.",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        color = scheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // A. Overstaying Trucks (> 24h)
                            if (overstayAlerts.isNotEmpty()) {
                                Text(
                                    text = "Trucks Parked Over 24 Hours (${overstayAlerts.size})",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF59E0B),
                                    ),
                                )
                                overstayAlerts.forEach { alert ->
                                    val alertKey = "overstay_${alert.visitId}"
                                    val isExpanded = alertKey in expandedAlertIds
                                    val warnColor = if (alert.hoursOnYard >= 48) scheme.error else Color(0xFFF59E0B)

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateContentSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(warnColor.copy(alpha = if (dark) 0.14f else 0.08f))
                                            .border(1.dp, warnColor.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                                            .clickable {
                                                expandedAlertIds = if (isExpanded) expandedAlertIds - alertKey else expandedAlertIds + alertKey
                                            }
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(warnColor.copy(alpha = 0.18f))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                            ) {
                                                Text(
                                                    text = alert.stayDurationLabel,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 10.sp,
                                                        color = warnColor,
                                                    ),
                                                )
                                            }

                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                            ) {
                                                Text(
                                                    text = "${countryFlagForCode(alert.countryCode)} ${alert.frontPlate}" +
                                                        if (alert.horsePlate.isNotBlank() && alert.horsePlate != alert.frontPlate) " / ${alert.horsePlate}" else "",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        color = scheme.onSurface,
                                                    ),
                                                )
                                                Text(
                                                    text = buildString {
                                                        append(alert.visitTypeLabel)
                                                        alert.transporter?.let { append(" • $it") }
                                                        alert.driverName?.let { append(" • Driver: $it") }
                                                    },
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 11.sp,
                                                        color = scheme.onSurfaceVariant,
                                                    ),
                                                )
                                            }

                                            DetailsPillButton(
                                                expanded = isExpanded,
                                                accent = warnColor,
                                                dark = dark,
                                            )
                                        }

                                        AnimatedVisibility(
                                            visible = isExpanded,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut(),
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(scheme.surface.copy(alpha = if (dark) 0.55f else 0.80f))
                                                    .border(1.dp, scheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                                    .padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                ResponsiveAccountDetailRow(
                                                    label = "Registration Plates",
                                                    value = buildString {
                                                        append("${countryFlagForCode(alert.countryCode)} ${countryNameForCode(alert.countryCode)} — Front: ${alert.frontPlate}")
                                                        if (alert.horsePlate.isNotBlank()) append(" • Horse: ${alert.horsePlate}")
                                                    },
                                                )
                                                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                                                ResponsiveAccountDetailRow(
                                                    label = "Arrived At",
                                                    value = "${DashFullDateFmt.format(alert.arrivedAt)} (${alert.stayDurationLabel} on yard)",
                                                )
                                                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                                                ResponsiveAccountDetailRow(
                                                    label = "Visit Reason",
                                                    value = alert.visitTypeLabel,
                                                )
                                                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                                                ResponsiveAccountDetailRow(
                                                    label = "Transport Company",
                                                    value = alert.transporter ?: "Not specified",
                                                )
                                                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                                                ResponsiveAccountDetailRow(
                                                    label = "Driver",
                                                    value = alert.driverName ?: "Not recorded",
                                                )
                                                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                                                ResponsiveAccountDetailRow(
                                                    label = "Driver Phone",
                                                    value = alert.driverPhone ?: "Not recorded",
                                                )
                                                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                                                ResponsiveAccountDetailRow(
                                                    label = "Cargo & Seals",
                                                    value = "${alert.cargoCount} cargo item(s) • ${alert.sealCount} seal(s)",
                                                )
                                                alert.arrivedByStaff?.let { staff ->
                                                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                                                    ResponsiveAccountDetailRow(
                                                        label = "Checked In By",
                                                        value = staff,
                                                    )
                                                }

                                                if (!alert.driverPhone.isNullOrBlank()) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 4.dp),
                                                        horizontalArrangement = Arrangement.End,
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(999.dp))
                                                                .background(Color(0xFF10B981).copy(alpha = 0.16f))
                                                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                                                                .clickable { dialPhoneNumber(alert.driverPhone) }
                                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Phone,
                                                                contentDescription = null,
                                                                tint = Color(0xFF10B981),
                                                                modifier = Modifier.size(14.dp),
                                                            )
                                                            Text(
                                                                text = "Call Driver (${alert.driverPhone})",
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFF10B981),
                                                                ),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // B. Broken / Tampered / Missing Seal Alerts
                            if (sealAlerts.isNotEmpty()) {
                                Text(
                                    text = "Broken, Tampered or Missing Seals (${sealAlerts.size})",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.error,
                                    ),
                                )
                                sealAlerts.forEach { alert ->
                                    val alertKey = "seal_${alert.visitId}"
                                    val isExpanded = alertKey in expandedAlertIds

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateContentSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(scheme.error.copy(alpha = if (dark) 0.15f else 0.08f))
                                            .border(1.dp, scheme.error.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                            .clickable {
                                                expandedAlertIds = if (isExpanded) expandedAlertIds - alertKey else expandedAlertIds + alertKey
                                            }
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(scheme.error.copy(alpha = 0.18f))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                            ) {
                                                Text(
                                                    text = "${alert.flaggedSeals.size} SEAL ISSUE",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 10.sp,
                                                        color = scheme.error,
                                                    ),
                                                )
                                            }

                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                            ) {
                                                Text(
                                                    text = "${countryFlagForCode(alert.countryCode)} ${alert.frontPlate}" +
                                                        if (alert.horsePlate.isNotBlank() && alert.horsePlate != alert.frontPlate) " / ${alert.horsePlate}" else "",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        color = scheme.onSurface,
                                                    ),
                                                )
                                                Text(
                                                    text = alert.flaggedSeals.joinToString(", ") { "${it.sealNumber} (${it.condition.label})" },
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 11.sp,
                                                        color = scheme.error,
                                                        fontWeight = FontWeight.SemiBold,
                                                    ),
                                                )
                                            }

                                            DetailsPillButton(
                                                expanded = isExpanded,
                                                accent = scheme.error,
                                                dark = dark,
                                            )
                                        }

                                        AnimatedVisibility(
                                            visible = isExpanded,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut(),
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(scheme.surface.copy(alpha = if (dark) 0.55f else 0.80f))
                                                    .border(1.dp, scheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                                    .padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                ResponsiveAccountDetailRow(
                                                    label = "Truck Status",
                                                    value = if (alert.isOnYard) "Currently on yard" else "Departed",
                                                )
                                                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                                                ResponsiveAccountDetailRow(
                                                    label = "Flagged Seals",
                                                    value = alert.flaggedSeals.joinToString(" • ") { s ->
                                                        "#${s.sealNumber}: ${s.condition.label}" +
                                                            (s.position?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") +
                                                            (s.remark?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: "")
                                                    },
                                                )
                                                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                                                ResponsiveAccountDetailRow(
                                                    label = "Transport Company",
                                                    value = alert.transporter ?: "Not specified",
                                                )
                                                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                                                ResponsiveAccountDetailRow(
                                                    label = "Driver",
                                                    value = listOfNotNull(alert.driverName, alert.driverPhone).joinToString(" • ").ifBlank { "Not recorded" },
                                                )
                                                alert.staffName?.let { staff ->
                                                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                                                    ResponsiveAccountDetailRow(
                                                        label = "Logged By",
                                                        value = "$staff (${DashTimestampFmt.format(alert.recordedAt)})",
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // C. Sync Conflicts or Pending Cloud Backups
                            if (visitsState.conflictVisits.isNotEmpty() || visitsState.pendingSyncCount > 0) {
                                val syncColor = if (visitsState.conflictVisits.isNotEmpty()) scheme.error else Color(0xFFF59E0B)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(syncColor.copy(alpha = if (dark) 0.15f else 0.09f))
                                        .border(1.dp, syncColor.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = syncColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (visitsState.conflictVisits.isNotEmpty()) {
                                                "${visitsState.conflictVisits.size} visit record(s) have a cloud conflict"
                                            } else {
                                                "${visitsState.pendingSyncCount} visit record(s) waiting to back up to cloud"
                                            },
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = scheme.onSurface,
                                            ),
                                        )
                                        Text(
                                            text = "Saved safely on this device. Pull down or tap Sync Now to upload.",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                color = scheme.onSurfaceVariant,
                                            ),
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(syncColor)
                                            .clickable(onClick = onRefreshDashboard)
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Text(
                                            text = if (visitsState.isSyncing) "Syncing..." else "Sync Now",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. TRUCK VISIT TRENDS, SHIFT INSIGHTS, TRANSPORTERS & COUNTRIES
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardCollapsibleHeader(
                    title = "TRUCK VISIT TRENDS & INSIGHTS",
                    subtitle = "7-day activity, busiest shifts, top transport companies & countries",
                    expanded = showTrendsCard,
                    onToggle = { showTrendsCard = !showTrendsCard },
                )

                AnimatedVisibility(
                    visible = showTrendsCard,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val maxBucket = remember(trendBuckets) {
                            trendBuckets.maxOfOrNull { maxOf(it.arrivals, it.departures) }?.coerceAtLeast(1) ?: 1
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.25f else 0.40f))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Last 7 Days Activity",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = scheme.onSurface,
                                )
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(scheme.surface.copy(alpha = if (dark) 0.55f else 0.75f))
                                        .border(0.8.dp, scheme.outline.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                                        .padding(2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    TrendChartModeChip(
                                        label = "Line",
                                        selected = useLineTrendChart,
                                        onClick = { useLineTrendChart = true },
                                    )
                                    TrendChartModeChip(
                                        label = "Bars",
                                        selected = !useLineTrendChart,
                                        onClick = { useLineTrendChart = false },
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TrendLegendDot(label = "Arrivals", color = Color(0xFF10B981))
                                TrendLegendDot(label = "Departures", color = Color(0xFF6366F1))
                            }

                            if (useLineTrendChart) {
                                GateActivityLineAreaChart(
                                    buckets = trendBuckets,
                                    maxBucket = maxBucket,
                                    dark = dark,
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(104.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom,
                                ) {
                                    trendBuckets.forEach { bucket ->
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Bottom,
                                        ) {
                                            Text(
                                                text = "${bucket.arrivals}/${bucket.departures}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = scheme.onSurfaceVariant,
                                                ),
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.height(62.dp),
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                verticalAlignment = Alignment.Bottom,
                                            ) {
                                                val arrFraction = (bucket.arrivals.toFloat() / maxBucket.toFloat()).coerceIn(0.08f, 1f)
                                                val depFraction = (bucket.departures.toFloat() / maxBucket.toFloat()).coerceIn(0.08f, 1f)
                                                Box(
                                                    modifier = Modifier
                                                        .width(9.dp)
                                                        .fillMaxHeight(if (bucket.arrivals == 0) 0.06f else arrFraction)
                                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                        .background(
                                                            if (bucket.arrivals > 0) Color(0xFF10B981)
                                                            else scheme.onSurfaceVariant.copy(alpha = 0.2f),
                                                        ),
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .width(9.dp)
                                                        .fillMaxHeight(if (bucket.departures == 0) 0.06f else depFraction)
                                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                        .background(
                                                            if (bucket.departures > 0) Color(0xFF6366F1)
                                                            else scheme.onSurfaceVariant.copy(alpha = 0.2f),
                                                        ),
                                                )
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = bucket.label,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = if (bucket.label == "Today") FontWeight.ExtraBold else FontWeight.Medium,
                                                    color = if (bucket.label == "Today") scheme.primary else scheme.onSurfaceVariant,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Busiest Gate Shifts & Peak Hours Breakdown
                        val totalShiftEvents = shiftBuckets.sumOf { it.total }.coerceAtLeast(1)
                        val busiestShift = shiftBuckets.maxByOrNull { it.total }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.22f else 0.35f))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "Gate Activity by Shift",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = scheme.onSurface,
                                )
                                if (busiestShift != null && busiestShift.total > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(scheme.primary.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = "Busiest: ${busiestShift.title}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = scheme.primary,
                                            ),
                                        )
                                    }
                                }
                            }

                            shiftBuckets.forEach { shift ->
                                val pct = (shift.total.toFloat() / totalShiftEvents.toFloat()).coerceIn(0f, 1f)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = "${shift.title} (${shift.timeWindow})",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = scheme.onSurface,
                                            ),
                                        )
                                        Text(
                                            text = "In: ${shift.arrivals} • Out: ${shift.departures} (${shift.total} total)",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                color = scheme.onSurfaceVariant,
                                            ),
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(scheme.surfaceVariant.copy(alpha = 0.55f)),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(if (shift.total == 0) 0.02f else pct)
                                                .height(7.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(
                                                    if (shift == busiestShift && shift.total > 0) Color(0xFF10B981)
                                                    else scheme.primary,
                                                ),
                                        )
                                    }
                                }
                            }
                        }

                        // Top Transport Companies / Carriers
                        if (topTransporters.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Top Transport Companies",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = scheme.onSurface,
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    topTransporters.forEach { carrier ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.25f else 0.40f))
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                text = carrier.name,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = scheme.onSurface,
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 8.dp),
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                if (carrier.onYardNow > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(999.dp))
                                                            .background(Color(0xFF10B981).copy(alpha = 0.16f))
                                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                                    ) {
                                                        Text(
                                                            text = "${carrier.onYardNow} on yard",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF10B981),
                                                            ),
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "${carrier.totalVisits} visit${if (carrier.totalVisits == 1) "" else "s"}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = scheme.onSurfaceVariant,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Visit Purpose Mix
                        if (allVisits.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Reasons for Visit",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = scheme.onSurface,
                                )
                                purposeCounts.filter { it.second > 0 }.forEach { (type, count) ->
                                    val pct = (count.toFloat() / allVisits.size.toFloat()).coerceIn(0f, 1f)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = type.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = scheme.onSurface,
                                            ),
                                            modifier = Modifier.width(84.dp),
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(scheme.surfaceVariant.copy(alpha = 0.5f)),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(pct)
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(scheme.primary),
                                            )
                                        }
                                        Text(
                                            text = "$count (${(pct * 100).toInt()}%)",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = scheme.onSurfaceVariant,
                                            ),
                                        )
                                    }
                                }
                            }
                        }

                        // Top Origin Countries
                        if (topCountries.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Truck Countries",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = scheme.onSurface,
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    topCountries.forEach { (code, count) ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(scheme.primary.copy(alpha = if (dark) 0.16f else 0.10f))
                                                .border(0.8.dp, scheme.primary.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                        ) {
                                            Text(
                                                text = "${countryFlagForCode(code)} ${countryNameForCode(code)} ($code) • $count",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = scheme.onSurface,
                                                ),
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

        // 4. RECENT GATE ACTIVITY (collapsed by default, with responsive "Details" expansion per truck)
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardCollapsibleHeader(
                    title = "RECENT GATE ACTIVITY (${recentEvents.size})",
                    subtitle = "Latest trucks entering and leaving the yard",
                    expanded = showTimelineCard,
                    onToggle = { showTimelineCard = !showTimelineCard },
                )

                AnimatedVisibility(
                    visible = showTimelineCard,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    if (recentEvents.isEmpty()) {
                        Text(
                            text = "No arrivals or departures recorded yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            recentEvents.forEach { ev ->
                                val isExpanded = ev.id in expandedEventIds
                                val badgeColor = if (ev.isDeparture) Color(0xFF6366F1) else Color(0xFF10B981)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateContentSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.25f else 0.40f))
                                        .border(
                                            width = 1.dp,
                                            color = if (isExpanded) badgeColor.copy(alpha = 0.45f) else scheme.outlineVariant.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                        .clickable {
                                            expandedEventIds = if (isExpanded) expandedEventIds - ev.id else expandedEventIds + ev.id
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(badgeColor.copy(alpha = 0.16f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        ) {
                                            Text(
                                                text = if (ev.isDeparture) "OUT" else "IN",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 10.sp,
                                                    color = badgeColor,
                                                ),
                                            )
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                text = "${countryFlagForCode(ev.countryCode)} ${ev.frontPlate}" +
                                                    if (ev.horsePlate.isNotBlank() && ev.horsePlate != ev.frontPlate) " / ${ev.horsePlate}" else "",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = scheme.onSurface,
                                                ),
                                            )
                                            Text(
                                                text = buildString {
                                                    append(ev.visitTypeLabel)
                                                    if (!ev.staffName.isNullOrBlank()) {
                                                        append(" • by ${ev.staffName}")
                                                    }
                                                    append(" • ${DashTimestampFmt.format(ev.timestamp)}")
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    color = scheme.onSurfaceVariant,
                                                ),
                                            )
                                        }

                                        DetailsPillButton(
                                            expanded = isExpanded,
                                            accent = badgeColor,
                                            dark = dark,
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut(),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(scheme.surface.copy(alpha = if (dark) 0.55f else 0.80f))
                                                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            ResponsiveAccountDetailRow(
                                                label = "Event",
                                                value = if (ev.isDeparture) "Truck Checked Out (Departed)" else "Truck Checked In (On Yard)",
                                            )
                                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                                            ResponsiveAccountDetailRow(
                                                label = "Registration",
                                                value = "${countryFlagForCode(ev.countryCode)} ${countryNameForCode(ev.countryCode)} — Front: ${ev.frontPlate}" +
                                                    if (ev.horsePlate.isNotBlank()) " • Horse: ${ev.horsePlate}" else "",
                                            )
                                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                                            ResponsiveAccountDetailRow(
                                                label = "Reason for Visit",
                                                value = ev.visitTypeLabel,
                                            )
                                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                                            ResponsiveAccountDetailRow(
                                                label = "Transport Company",
                                                value = ev.transporter ?: "Not specified",
                                            )
                                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                                            ResponsiveAccountDetailRow(
                                                label = "Driver",
                                                value = listOfNotNull(ev.driverName, ev.driverPhone).joinToString(" • ").ifBlank { "Not recorded" },
                                            )
                                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                                            ResponsiveAccountDetailRow(
                                                label = "Cargo & Seals",
                                                value = "${ev.cargoCount} cargo item(s) • ${ev.sealCount} seal(s)" +
                                                    if (ev.flaggedSealCount > 0) " (${ev.flaggedSealCount} flagged)" else "",
                                            )
                                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                                            ResponsiveAccountDetailRow(
                                                label = "Logged By",
                                                value = "${ev.staffName ?: "Gate Staff"} at ${DashFullDateFmt.format(ev.timestamp)}",
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

        // 5. ACTIVE & LOGGED-IN STAFF (Placed at the bottom right before Access & Handsets)
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardCollapsibleHeader(
                    title = "ACTIVE & LOGGED-IN STAFF (${userSummaries.size})",
                    subtitle = "Staff activity, account lock status, unlock/disable controls & trucks handled",
                    expanded = showUsersCard,
                    onToggle = { showUsersCard = !showUsersCard },
                )

                AnimatedVisibility(
                    visible = showUsersCard,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Feedback strip for quick unlock/disable actions
                        userActionBanner?.let { (isSuccess, msg) ->
                            val bannerColor = if (isSuccess) Color(0xFF10B981) else scheme.error
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bannerColor.copy(alpha = 0.14f))
                                    .border(1.dp, bannerColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = bannerColor,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = bannerColor,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = bannerColor,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .clickable { userActionBanner = null },
                                )
                            }
                        }

                        userSummaries.forEach { summary ->
                            val isExpanded = summary.userId in expandedUserIds
                            val isSelf = summary.userId == currentUser.id
                            val isRecentlyActive = summary.lastActiveAt?.let {
                                Duration.between(it, now).toMinutes() < 60
                            } ?: false
                            val hasBlockedHandsets = summary.userDevices.any { it.isRevoked }
                            val dotColor = when {
                                summary.isLocked -> scheme.error
                                isRecentlyActive -> Color(0xFF10B981)
                                else -> scheme.onSurfaceVariant.copy(alpha = 0.45f)
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.28f else 0.42f))
                                    .border(
                                        width = 1.dp,
                                        color = when {
                                            summary.isLocked -> scheme.error.copy(alpha = 0.45f)
                                            isExpanded -> scheme.primary.copy(alpha = 0.45f)
                                            else -> scheme.outlineVariant.copy(alpha = 0.28f)
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                    )
                                    .clickable {
                                        expandedUserIds = if (isExpanded) {
                                            expandedUserIds - summary.userId
                                        } else {
                                            expandedUserIds + summary.userId
                                        }
                                    }
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // Summary Row: Fully responsive, no "..." cutoff, with clear "Details" button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier.padding(top = 2.dp),
                                        contentAlignment = Alignment.BottomEnd,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (summary.isLocked) scheme.error.copy(alpha = 0.16f)
                                                    else scheme.primary.copy(alpha = 0.16f),
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = if (summary.isLocked) Icons.Default.Lock else Icons.Default.Person,
                                                contentDescription = null,
                                                tint = if (summary.isLocked) scheme.error else scheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(dotColor)
                                                .border(1.5.dp, scheme.surface, CircleShape),
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = if (isSelf) {
                                                    "${summary.fullName} (You)"
                                                } else {
                                                    summary.fullName
                                                },
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = scheme.onSurface,
                                                ),
                                            )

                                            // Role badge
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(scheme.primary.copy(alpha = 0.14f))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = formatFriendlyRoleBadge(summary.role),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = scheme.primary,
                                                    ),
                                                )
                                            }

                                            // Lock / Account Status badge
                                            val statusColor = if (summary.isLocked) scheme.error else Color(0xFF10B981)
                                            val statusText = when {
                                                summary.status.equals("DEACTIVATED", ignoreCase = true) -> "Disabled"
                                                summary.isLocked -> "Locked"
                                                isRecentlyActive -> "Online"
                                                else -> "Unlocked"
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(statusColor.copy(alpha = 0.14f))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = statusText,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = statusColor,
                                                    ),
                                                )
                                            }

                                            if (summary.todayHandled > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(999.dp))
                                                        .background(Color(0xFF0284C7).copy(alpha = 0.14f))
                                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                                ) {
                                                    Text(
                                                        text = "${summary.todayHandled} today",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF0284C7),
                                                        ),
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = summary.email,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 12.sp,
                                                color = scheme.onSurfaceVariant,
                                            ),
                                        )

                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                text = summary.lastActiveAt?.let {
                                                    "Last active: ${DashTimestampFmt.format(it)}"
                                                } ?: "Not signed in yet",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 11.sp,
                                                    color = if (isRecentlyActive) Color(0xFF10B981) else scheme.onSurfaceVariant,
                                                ),
                                            )
                                            Text(
                                                text = "• Checked in: ${summary.arrivalsLogged} • Checked out: ${summary.departuresLogged}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    color = scheme.onSurfaceVariant,
                                                ),
                                            )
                                        }
                                    }

                                    DetailsPillButton(
                                        expanded = isExpanded,
                                        accent = scheme.primary,
                                        dark = dark,
                                    )
                                }

                                // Expanded Full Account & Lock Details Panel
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut(),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(scheme.surface.copy(alpha = if (dark) 0.55f else 0.78f))
                                            .border(
                                                width = 1.dp,
                                                color = scheme.outlineVariant.copy(alpha = 0.35f),
                                                shape = RoundedCornerShape(12.dp),
                                            )
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        // Lock & Security Status Banner
                                        val lockBannerColor = if (summary.isLocked || hasBlockedHandsets) {
                                            scheme.error
                                        } else {
                                            Color(0xFF10B981)
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(lockBannerColor.copy(alpha = 0.12f))
                                                .border(1.dp, lockBannerColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (summary.isLocked || hasBlockedHandsets) {
                                                    Icons.Default.Warning
                                                } else {
                                                    Icons.Default.CheckCircle
                                                },
                                                contentDescription = null,
                                                tint = lockBannerColor,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = when {
                                                        summary.status.equals("DEACTIVATED", ignoreCase = true) ->
                                                            "Account Disabled — This staff member cannot sign in"
                                                        summary.status.equals("REJECTED", ignoreCase = true) ->
                                                            "Account Rejected — Access is blocked"
                                                        summary.isLocked ->
                                                            "Account Locked — Temporarily blocked after failed sign-in attempts"
                                                        hasBlockedHandsets ->
                                                            "Account Unlocked • ${summary.userDevices.count { it.isRevoked }} phone(s) blocked"
                                                        else ->
                                                            "Account Unlocked & Active — Can sign in normally"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = lockBannerColor,
                                                    ),
                                                )
                                                val lockSubNote = buildString {
                                                    if (summary.lockedUntil != null && summary.lockedUntil.isAfter(now)) {
                                                        append("Locked until ${DashTimestampFmt.format(summary.lockedUntil)}")
                                                    } else if (summary.failedAttempts > 0) {
                                                        append("${summary.failedAttempts} recent failed sign-in attempt(s)")
                                                    } else {
                                                        append("No failed sign-in lockouts on this account")
                                                    }
                                                }
                                                Text(
                                                    text = lockSubNote,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        color = scheme.onSurfaceVariant,
                                                    ),
                                                )
                                            }
                                        }

                                        // Full Account Details Rows (responsive, wraps long text cleanly without "...")
                                        ResponsiveAccountDetailRow(
                                            label = "Full Name",
                                            value = summary.fullName,
                                        )
                                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))

                                        ResponsiveAccountDetailRow(
                                            label = "Email Address",
                                            value = "${summary.email} (${if (summary.emailVerified) "Verified" else "Not verified"})",
                                        )
                                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))

                                        ResponsiveAccountDetailRow(
                                            label = "Phone Number",
                                            value = summary.phone.ifBlank { "Not provided" },
                                        )
                                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))

                                        ResponsiveAccountDetailRow(
                                            label = "Access Role",
                                            value = formatFriendlyRoleDescription(summary.role),
                                        )
                                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))

                                        ResponsiveAccountDetailRow(
                                            label = "Account Status",
                                            value = formatFriendlyAccountStatus(summary.status, summary.isLocked),
                                        )
                                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))

                                        ResponsiveAccountDetailRow(
                                            label = "Last Active / Login",
                                            value = summary.lastActiveAt?.let {
                                                "${DashFullDateFmt.format(it)} (${formatRelativeInstant(it, now)})"
                                            } ?: "Has not signed in on a handset yet",
                                        )
                                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))

                                        ResponsiveAccountDetailRow(
                                            label = "Gate Activity",
                                            value = "${summary.arrivalsLogged} trucks checked in • ${summary.departuresLogged} checked out (${summary.arrivalsLogged + summary.departuresLogged} total, ${summary.todayHandled} today)",
                                        )

                                        summary.createdAt?.let { created ->
                                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))
                                            ResponsiveAccountDetailRow(
                                                label = "Account Created",
                                                value = DashFullDateFmt.format(created),
                                            )
                                        }

                                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))

                                        // Registered Handsets for this User
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            val activePhonesCount = summary.userDevices.count { !it.isRevoked }
                                            val blockedPhonesCount = summary.userDevices.count { it.isRevoked }
                                            Text(
                                                text = "Registered Phones / Handsets (${summary.userDevices.size} total: $activePhonesCount active, $blockedPhonesCount blocked)",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = scheme.onSurface,
                                                ),
                                            )
                                            if (summary.userDevices.isEmpty()) {
                                                Text(
                                                    text = "No phone or handset registered to this account yet.",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = scheme.onSurfaceVariant,
                                                )
                                            } else {
                                                summary.userDevices.forEach { dev ->
                                                    val devColor = if (dev.isRevoked) scheme.error else Color(0xFF10B981)
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.5f))
                                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Phone,
                                                            contentDescription = null,
                                                            tint = devColor,
                                                            modifier = Modifier.size(15.dp),
                                                        )
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = dev.name + if (dev.isCurrentDevice) " (This phone)" else "",
                                                                style = MaterialTheme.typography.bodySmall.copy(
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    fontSize = 11.sp,
                                                                    color = scheme.onSurface,
                                                                ),
                                                            )
                                                            val devSub = if (dev.isRevoked) {
                                                                "Blocked" + (dev.revokeReason?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
                                                            } else {
                                                                parseIsoInstant(dev.lastSeenAt)?.let {
                                                                    "Last used ${DashTimestampFmt.format(it)}"
                                                                } ?: "Allowed to sign in"
                                                            }
                                                            Text(
                                                                text = devSub,
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontSize = 10.sp,
                                                                    color = scheme.onSurfaceVariant,
                                                                ),
                                                            )
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(999.dp))
                                                                .background(devColor.copy(alpha = 0.15f))
                                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                                        ) {
                                                            Text(
                                                                text = if (dev.isRevoked) "Blocked" else "Active",
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = devColor,
                                                                ),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Quick Account Actions (Call Staff, Unlock/Enable Account, or Disable Account)
                                        val isBusy = busyActionUserId == summary.userId
                                        val isConfirmingDisable = confirmingDisableUserId == summary.userId
                                        val needsUnlockOrEnable = summary.isLocked ||
                                            summary.failedAttempts > 0 ||
                                            !summary.status.equals("APPROVED", ignoreCase = true)

                                        if (summary.phone.isNotBlank() || onUnlockOrEnableUser != null || (!isSelf && onDeactivateUser != null)) {
                                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))

                                            if (isConfirmingDisable) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(scheme.error.copy(alpha = 0.10f))
                                                        .border(1.dp, scheme.error.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                                        .padding(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    Text(
                                                        text = "Disable ${summary.fullName}'s account? They will be signed out immediately and unable to sign in until re-enabled.",
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontWeight = FontWeight.Medium,
                                                            color = scheme.error,
                                                        ),
                                                    )
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.End,
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .clickable(enabled = !isBusy) { confirmingDisableUserId = null }
                                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                                        ) {
                                                            Text(
                                                                text = "Cancel",
                                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                                color = scheme.onSurfaceVariant,
                                                            )
                                                        }
                                                        Spacer(Modifier.width(8.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(scheme.error)
                                                                .clickable(enabled = !isBusy) {
                                                                    busyActionUserId = summary.userId
                                                                    onDeactivateUser?.invoke(summary.userId) { ok, msg ->
                                                                        busyActionUserId = null
                                                                        confirmingDisableUserId = null
                                                                        userActionBanner = ok to msg
                                                                    }
                                                                }
                                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                                        ) {
                                                            Text(
                                                                text = if (isBusy) "Disabling..." else "Confirm Disable",
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color.White,
                                                                ),
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                FlowRow(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    if (summary.phone.isNotBlank()) {
                                                        Row(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(999.dp))
                                                                .background(scheme.primary.copy(alpha = 0.14f))
                                                                .border(1.dp, scheme.primary.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                                                                .clickable { dialPhoneNumber(summary.phone) }
                                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Phone,
                                                                contentDescription = null,
                                                                tint = scheme.primary,
                                                                modifier = Modifier.size(14.dp),
                                                            )
                                                            Text(
                                                                text = "Call Staff",
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = scheme.primary,
                                                                ),
                                                            )
                                                        }
                                                    }

                                                    if (needsUnlockOrEnable && onUnlockOrEnableUser != null) {
                                                        Row(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(999.dp))
                                                                .background(Color(0xFF10B981).copy(alpha = 0.16f))
                                                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                                                                .clickable(enabled = !isBusy) {
                                                                    busyActionUserId = summary.userId
                                                                    onUnlockOrEnableUser(summary.userId, summary.role) { ok, msg ->
                                                                        busyActionUserId = null
                                                                        userActionBanner = ok to msg
                                                                    }
                                                                }
                                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        ) {
                                                            if (isBusy) {
                                                                CircularProgressIndicator(
                                                                    modifier = Modifier.size(12.dp),
                                                                    strokeWidth = 1.8.dp,
                                                                    color = Color(0xFF10B981),
                                                                )
                                                            } else {
                                                                Icon(
                                                                    imageVector = Icons.Default.CheckCircle,
                                                                    contentDescription = null,
                                                                    tint = Color(0xFF10B981),
                                                                    modifier = Modifier.size(14.dp),
                                                                )
                                                            }
                                                            Text(
                                                                text = if (isBusy) "Unlocking..." else "Unlock & Enable Account",
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFF10B981),
                                                                ),
                                                            )
                                                        }
                                                    }

                                                    if (!isSelf && !summary.status.equals("DEACTIVATED", ignoreCase = true) && onDeactivateUser != null) {
                                                        Row(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(999.dp))
                                                                .background(scheme.error.copy(alpha = 0.14f))
                                                                .border(1.dp, scheme.error.copy(alpha = 0.38f), RoundedCornerShape(999.dp))
                                                                .clickable(enabled = !isBusy) {
                                                                    confirmingDisableUserId = summary.userId
                                                                }
                                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Lock,
                                                                contentDescription = null,
                                                                tint = scheme.error,
                                                                modifier = Modifier.size(14.dp),
                                                            )
                                                            Text(
                                                                text = "Disable Account",
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = scheme.error,
                                                                ),
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
            }
        }
    }
}

@Composable
private fun DetailsPillButton(
    expanded: Boolean,
    accent: Color,
    dark: Boolean,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (expanded) accent
                else accent.copy(alpha = if (dark) 0.18f else 0.12f),
            )
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.4f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (expanded) "Hide" else "Details",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = if (expanded) Color.White else accent,
            ),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResponsiveAccountDetailRow(
    label: String,
    value: String,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
}

private fun formatFriendlyRoleBadge(role: String): String = when (role.uppercase(Locale.ROOT)) {
    "ADMIN" -> "Admin"
    "SUPERVISOR" -> "Supervisor"
    "RUNNER" -> "Runner"
    else -> role
}

private fun formatFriendlyRoleDescription(role: String): String = when (role.uppercase(Locale.ROOT)) {
    "ADMIN" -> "Admin (Full system, staff & handset access)"
    "SUPERVISOR" -> "Supervisor (Yard management & visit corrections)"
    "RUNNER" -> "Runner (Gate check-in & check-out)"
    else -> role
}

private fun formatFriendlyAccountStatus(status: String, isLocked: Boolean): String {
    if (isLocked && !status.equals("DEACTIVATED", ignoreCase = true) && !status.equals("REJECTED", ignoreCase = true)) {
        return "Temporarily Locked (Too many failed password attempts)"
    }
    return when (status.uppercase(Locale.ROOT)) {
        "APPROVED" -> "Approved & Active"
        "PENDING_VERIFICATION" -> "Waiting for Email Verification"
        "PENDING_APPROVAL" -> "Waiting for Admin Approval"
        "DEACTIVATED" -> "Disabled / Deactivated"
        "REJECTED" -> "Rejected"
        else -> status
    }
}

private fun formatRelativeInstant(instant: Instant, now: Instant): String {
    val mins = Duration.between(instant, now).toMinutes()
    return when {
        mins < 1 -> "just now"
        mins == 1L -> "1 min ago"
        mins < 60 -> "$mins mins ago"
        mins < 1440 -> {
            val hrs = mins / 60
            if (hrs == 1L) "1 hour ago" else "$hrs hours ago"
        }
        else -> {
            val days = mins / 1440
            if (days == 1L) "yesterday" else "$days days ago"
        }
    }
}

@Composable
private fun DashboardCollapsibleHeader(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = scheme.primary,
                    letterSpacing = 0.8.sp,
                ),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant,
                ),
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun DashboardKpiTile(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = if (dark) 0.14f else 0.08f))
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.30f)), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurfaceVariant,
                ),
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = scheme.onSurface,
            ),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                color = scheme.onSurfaceVariant,
            ),
        )
    }
}

@Composable
private fun TrendLegendDot(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

@Composable
private fun TrendChartModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) scheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
            ),
        )
    }
}

@Composable
private fun GateActivityLineAreaChart(
    buckets: List<DailyTrendBucket>,
    maxBucket: Int,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val arrivalColor = Color(0xFF10B981)
    val departureColor = Color(0xFF6366F1)
    val gridColor = scheme.onSurfaceVariant.copy(alpha = if (dark) 0.22f else 0.18f)
    val surfaceColor = scheme.surface

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp),
        ) {
            if (buckets.isEmpty()) return@Canvas
            val topPad = 10.dp.toPx()
            val bottomPad = 6.dp.toPx()
            val usableHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)
            val count = buckets.size
            val colWidth = size.width / count.toFloat()

            // Horizontal dashed reference lines (0%, 50%, 100%)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            listOf(0f, 0.5f, 1f).forEach { frac ->
                val y = topPad + usableHeight * (1f - frac)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = if (frac > 0f) dashEffect else null,
                )
            }

            fun pointsFor(selector: (DailyTrendBucket) -> Int): List<Offset> {
                return buckets.mapIndexed { idx, bucket ->
                    val x = colWidth * (idx + 0.5f)
                    val fraction = (selector(bucket).toFloat() / maxBucket.toFloat()).coerceIn(0f, 1f)
                    val y = topPad + usableHeight * (1f - fraction)
                    Offset(x, y)
                }
            }

            fun drawSmoothSeries(points: List<Offset>, color: Color) {
                if (points.isEmpty()) return
                val linePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 0 until points.lastIndex) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val midX = (p0.x + p1.x) / 2f
                        cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                    }
                }
                val areaPath = Path().apply {
                    addPath(linePath)
                    lineTo(points.last().x, topPad + usableHeight)
                    lineTo(points.first().x, topPad + usableHeight)
                    close()
                }
                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = if (dark) 0.28f else 0.22f),
                            color.copy(alpha = 0.0f),
                        ),
                        startY = topPad,
                        endY = topPad + usableHeight,
                    ),
                )
                drawPath(
                    path = linePath,
                    color = color,
                    style = Stroke(
                        width = 2.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                points.forEach { pt ->
                    drawCircle(color = surfaceColor, radius = 4.dp.toPx(), center = pt)
                    drawCircle(color = color, radius = 2.7.dp.toPx(), center = pt)
                }
            }

            drawSmoothSeries(pointsFor { it.departures }, departureColor)
            drawSmoothSeries(pointsFor { it.arrivals }, arrivalColor)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            buckets.forEach { bucket ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "${bucket.arrivals}/${bucket.departures}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = scheme.onSurfaceVariant,
                        ),
                    )
                    Text(
                        text = bucket.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = if (bucket.label == "Today") FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (bucket.label == "Today") scheme.primary else scheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}

private fun parseIsoInstant(raw: String?): Instant? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        val normalized = when {
            trimmed.endsWith("Z") || trimmed.contains("+") || trimmed.lastIndexOf('-') > 9 -> trimmed
            trimmed.contains(' ') -> "${trimmed.replace(' ', 'T')}Z"
            else -> trimmed
        }
        Instant.parse(normalized)
    }.getOrNull() ?: runCatching {
        OffsetDateTime.parse(trimmed).toInstant()
    }.getOrNull()
}
