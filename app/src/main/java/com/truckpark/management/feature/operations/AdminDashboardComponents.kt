package com.truckpark.management.feature.operations

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
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
import com.truckpark.management.domain.models.VisitType
import com.truckpark.management.domain.models.countryFlagForCode
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

private val DayLabelFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

private data class UserSessionSummary(
    val userId: String,
    val fullName: String,
    val email: String,
    val role: String,
    val status: String,
    val lastActiveAt: Instant?,
    val arrivalsLogged: Int,
    val departuresLogged: Int,
)

private data class DailyTrendBucket(
    val date: LocalDate,
    val label: String,
    val arrivals: Int,
    val departures: Int,
)

private data class YardActivityEvent(
    val id: String,
    val isDeparture: Boolean,
    val timestamp: Instant,
    val countryCode: String,
    val frontPlate: String,
    val horsePlate: String,
    val visitTypeLabel: String,
    val staffName: String?,
)

@Composable
internal fun AdminLiveOperationsDashboard(
    currentUser: AuthUser,
    visitsState: VisitsUiState,
    users: List<ManagedUser>,
    devices: List<ManagedDevice>,
    onNavigateToVisits: () -> Unit,
    onRefreshDashboard: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f

    val allVisits = remember(visitsState.openVisits, visitsState.recentCompleted) {
        (visitsState.openVisits + visitsState.recentCompleted).distinctBy { it.id }
    }

    val now = remember(allVisits, visitsState.lastSyncedAt) { Instant.now() }
    val zone = remember { ZoneId.systemDefault() }

    // Long-stay trucks (> 24 hours on yard)
    val longStayCount = remember(visitsState.openVisits, now) {
        visitsState.openVisits.count { Duration.between(it.arrivedAt, now).toHours() >= 24 }
    }

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

    // Extract plate/staff metadata once
    val visitPlateInfos = remember(allVisits) {
        allVisits.associate { v ->
            v.id to extractPlateInfo(v.plateNumber, v.arrivalNotes, v.departureNotes)
        }
    }

    // 7-day Arrivals & Departures trend
    val trendBuckets = remember(allVisits, now) {
        val today = LocalDate.now(zone)
        val days = (6 downTo 0).map { offset -> today.minusDays(offset.toLong()) }
        days.map { day ->
            val arrivals = allVisits.count { v ->
                v.arrivedAt.atZone(zone).toLocalDate() == day
            }
            val departures = allVisits.count { v ->
                v.departedAt?.atZone(zone)?.toLocalDate() == day
            }
            DailyTrendBucket(
                date = day,
                label = if (day == today) "Today" else day.format(DayLabelFmt),
                arrivals = arrivals,
                departures = departures,
            )
        }
    }

    // Visit Purpose Breakdown
    val purposeCounts = remember(allVisits) {
        VisitType.entries.map { type ->
            type to allVisits.count { it.visitType == type }
        }.sortedByDescending { it.second }
    }

    // Top Origin Countries
    val topCountries = remember(visitPlateInfos) {
        visitPlateInfos.values
            .groupBy { it.country.ifBlank { "ZM" } }
            .map { (code, list) -> code to list.size }
            .sortedByDescending { it.second }
            .take(5)
    }

    // Logged-in / Active Users summary (grouped by USER, not handsets)
    val userSummaries = remember(users, devices, currentUser, visitPlateInfos, allVisits) {
        val activeDevicesByUser = devices
            .filter { !it.isRevoked }
            .groupBy { it.userId }

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
                ),
            )
        }

        baseUsers.map { u ->
            val userDevices = activeDevicesByUser[u.id].orEmpty()
            val latestSeen = userDevices
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

            UserSessionSummary(
                userId = u.id,
                fullName = u.fullName.ifBlank { u.email },
                email = u.email,
                role = u.role,
                status = u.status,
                lastActiveAt = latestSeen,
                arrivalsLogged = arrivalsCount,
                departuresLogged = departuresCount,
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
                isDeparture = isDeparture,
                timestamp = if (isDeparture) (v.departedAt ?: v.arrivedAt) else v.arrivedAt,
                countryCode = country,
                frontPlate = front,
                horsePlate = horse,
                visitTypeLabel = info?.customPurpose ?: v.visitType.label,
                staffName = if (isDeparture) info?.departedByName else info?.arrivedByName,
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

    var showUsersCard by remember { mutableStateOf(true) }
    var showTrendsCard by remember { mutableStateOf(true) }
    var showTimelineCard by remember { mutableStateOf(false) }
    var useLineTrendChart by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. OVERVIEW HEADER + KPIs
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = visitsState.lastSyncedAt?.let {
                                "Last synced ${DashTimestampFmt.format(it)}"
                            } ?: "Live local & cloud telemetry",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = scheme.onSurfaceVariant,
                            ),
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(scheme.primary.copy(alpha = if (dark) 0.18f else 0.10f))
                                .border(1.dp, scheme.primary.copy(alpha = 0.35f), CircleShape)
                                .clickable(onClick = onRefreshDashboard),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh dashboard",
                                tint = scheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(scheme.primary)
                                .clickable(onClick = onNavigateToVisits)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = "Visits",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onPrimary,
                                ),
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Go to Visits",
                                tint = scheme.onPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }

                // 2x2 KPI Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DashboardKpiTile(
                        modifier = Modifier.weight(1f),
                        title = "On Yard Now",
                        value = "${visitsState.openVisits.size}",
                        subtitle = if (longStayCount > 0) "$longStayCount over 24h" else "Active trucks",
                        icon = Icons.Default.LocalShipping,
                        accent = Color(0xFF10B981),
                        dark = dark,
                    )
                    DashboardKpiTile(
                        modifier = Modifier.weight(1f),
                        title = "Departed",
                        value = "${visitsState.recentCompleted.size}",
                        subtitle = "Avg stay: $avgStayLabel",
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
                        title = "Sync Health",
                        value = when {
                            visitsState.conflictVisits.isNotEmpty() -> "${visitsState.conflictVisits.size} Conflict"
                            visitsState.pendingSyncCount > 0 -> "${visitsState.pendingSyncCount} Pending"
                            else -> "Synced"
                        },
                        subtitle = if (visitsState.isSyncing) "Syncing now..." else "Room + ORDS",
                        icon = if (visitsState.pendingSyncCount > 0) Icons.Default.CloudUpload else Icons.Default.CloudDone,
                        accent = syncAccent,
                        dark = dark,
                    )
                    DashboardKpiTile(
                        modifier = Modifier.weight(1f),
                        title = "Cargo & Seals",
                        value = "$totalSeals Seals",
                        subtitle = if (flaggedSeals > 0) "$flaggedSeals flagged • $totalCargoItems cargo" else "$totalCargoItems cargo items",
                        icon = if (flaggedSeals > 0) Icons.Default.Warning else Icons.Default.Lock,
                        accent = if (flaggedSeals > 0) Color(0xFFEF4444) else Color(0xFF0284C7),
                        dark = dark,
                    )
                }
            }
        }

        // 2. LOGGED-IN / ACTIVE USERS & TIMESTAMPS (USERS, NOT HANDSETS)
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardCollapsibleHeader(
                    title = "ACTIVE & LOGGED-IN USERS (${userSummaries.size})",
                    subtitle = "Staff accounts, last login/activity timestamps & gate volume",
                    expanded = showUsersCard,
                    onToggle = { showUsersCard = !showUsersCard },
                )

                AnimatedVisibility(
                    visible = showUsersCard,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        userSummaries.forEach { summary ->
                            val isRecentlyActive = summary.lastActiveAt?.let {
                                Duration.between(it, now).toMinutes() < 60
                            } ?: false
                            val dotColor = if (isRecentlyActive) Color(0xFF10B981) else scheme.onSurfaceVariant.copy(alpha = 0.45f)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.28f else 0.42f))
                                    .border(1.dp, scheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(scheme.primary.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = scheme.primary,
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

                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = summary.fullName,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = scheme.onSurface,
                                            ),
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(scheme.primary.copy(alpha = 0.14f))
                                                .padding(horizontal = 7.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = summary.role,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = scheme.primary,
                                                ),
                                            )
                                        }
                                    }
                                    Text(
                                        text = summary.email,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            color = scheme.onSurfaceVariant,
                                        ),
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = summary.lastActiveAt?.let { DashTimestampFmt.format(it) } ?: "No login yet",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = if (isRecentlyActive) Color(0xFF10B981) else scheme.onSurface,
                                        ),
                                    )
                                    Text(
                                        text = "In: ${summary.arrivalsLogged} • Out: ${summary.departuresLogged}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            color = scheme.onSurfaceVariant,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. YARD TRENDS (7-DAY ARRIVAL / DEPARTURE CHART + PURPOSE & ORIGIN MIX)
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardCollapsibleHeader(
                    title = "VISIT TRENDS & BREAKDOWN",
                    subtitle = "7-day gate flow, visit purposes & SADC origin mix",
                    expanded = showTrendsCard,
                    onToggle = { showTrendsCard = !showTrendsCard },
                )

                AnimatedVisibility(
                    visible = showTrendsCard,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // 7-Day Dual Bar Chart
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
                                    text = "7-Day Gate Activity",
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

                        // Visit Purpose Mix
                        if (allVisits.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Visit Purpose Distribution",
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
                                            modifier = Modifier.width(76.dp),
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

                        // Top SADC Origin Countries
                        if (topCountries.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Origin Countries",
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
                                                text = "${countryFlagForCode(code)} $code • $count",
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

        // 4. RECENT YARD OPERATIONS (4 On Yard + 4 Departed, collapsed by default)
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardCollapsibleHeader(
                    title = "RECENT YARD OPERATIONS (${recentEvents.size})",
                    subtitle = "Latest 4 on-yard & 4 departed (backfilled up to 8)",
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
                            text = "No arrivals or departures recorded yet. Tap Visits to log a truck.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            recentEvents.forEach { ev ->
                                val badgeColor = if (ev.isDeparture) Color(0xFF6366F1) else Color(0xFF10B981)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.25f else 0.40f))
                                        .clickable(onClick = onNavigateToVisits)
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
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

                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "${countryFlagForCode(ev.countryCode)} ${ev.frontPlate}" +
                                                if (ev.horsePlate.isNotBlank() && ev.horsePlate != ev.frontPlate) " / ${ev.horsePlate}" else "",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
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
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                color = scheme.onSurfaceVariant,
                                            ),
                                        )
                                    }

                                    Text(
                                        text = DashTimestampFmt.format(ev.timestamp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = scheme.onSurfaceVariant,
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

