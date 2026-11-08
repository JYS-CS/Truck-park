package com.truckpark.management.feature.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
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
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Result of the last code check. Idle = typing or waiting; Success and Error drive the visuals. */
enum class OtpState { Idle, Success, Error }

private const val OTP_LENGTH = 6
private val OtpBoxWidth = 44.dp
private val OtpBoxHeight = 72.dp
private val OtpBoxGap = 10.dp
private val OtpBoxCorner = 12.dp
private val OtpRowWidth = OtpBoxWidth * OTP_LENGTH + OtpBoxGap * (OTP_LENGTH - 1)

private class OtpPalette(
    val boxFill: Color,
    val boxBorder: Color,
    val text: Color,
    val glow: List<Color>,
    val success: Color,
    val error: Color,
    val accent: Color,
    val muted: Color,
    val chip: Color,
)

private fun otpPalette(dark: Boolean) = if (dark) {
    OtpPalette(
        boxFill = Color(0xFF101426),
        boxBorder = Color(0xFF222944),
        text = Color.White,
        glow = listOf(Color(0xFF6366F1), Color(0xFF818CF8)),
        success = Color(0xFF34D399),
        error = Color(0xFFF87171),
        accent = Color(0xFF818CF8),
        muted = Color(0xFF8E9BB5),
        chip = Color(0xFF161C33),
    )
} else {
    OtpPalette(
        boxFill = Color.White,
        boxBorder = Color(0xFFCBD5E1),
        text = Color(0xFF0F172A),
        glow = listOf(Color(0xFF6366F1), Color(0xFF818CF8)),
        success = Color(0xFF10B981),
        error = Color(0xFFEF4444),
        accent = Color(0xFF4F46E5),
        muted = Color(0xFF64748B),
        chip = Color(0xFFF1F5F9),
    )
}

/**
 * Six-box one-time code entry with a travelling electric glow on filled boxes,
 * a shake on a wrong code and a green check on success.
 * Typing, pasting and backspace all update [code] through [onCodeChange].
 */
@Composable
fun OtpCodeEntry(
    code: String,
    onCodeChange: (String) -> Unit,
    state: OtpState,
    dark: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = otpPalette(dark)
    val focusRequester = remember { FocusRequester() }
    val shakeX = remember { Animatable(0f) }
    val phase by rememberInfiniteTransition(label = "otpGlow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "otpPhase",
    )

    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(250)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(state) {
        if (state == OtpState.Error) {
            shakeX.snapTo(0f)
            for (target in listOf(-8f, 8f, -6f, 6f, 0f)) {
                shakeX.animateTo(target, tween(durationMillis = 70))
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                focusRequester.requestFocus()
                keyboardController?.show()
            },
        contentAlignment = Alignment.Center,
    ) {
        // Calculate dynamic box width & gap based on available width
        val totalAvailableWidth = maxWidth
        val calculatedGap = if (totalAvailableWidth < 340.dp) 6.dp else 8.dp
        val availableForBoxes = totalAvailableWidth - (calculatedGap * (OTP_LENGTH - 1))
        val boxWidth = minOf(44.dp, (availableForBoxes / OTP_LENGTH).coerceAtLeast(36.dp))
        val boxHeight = (boxWidth * 1.45f).coerceIn(52.dp, 68.dp)
        val corner = 10.dp

        // Transparent field spanning the row so Android OS directly handles touches
        BasicTextField(
            value = code,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(OTP_LENGTH)
                if (digits != code) onCodeChange(digits)
            },
            enabled = enabled && state != OtpState.Success,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
            ),
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .matchParentSize()
                .alpha(0.01f)
                .focusRequester(focusRequester),
        )

        Row(
            modifier = Modifier
                .alpha(if (state == OtpState.Idle) 1f else 0f),
            horizontalArrangement = Arrangement.spacedBy(calculatedGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(OTP_LENGTH) { index ->
                val isCurrent = index == code.length.coerceAtMost(OTP_LENGTH - 1)
                val isFilled = index < code.length
                val isFocused = isCurrent && code.length < OTP_LENGTH && state == OtpState.Idle
                val borderColor = when (state) {
                    OtpState.Success -> palette.success
                    OtpState.Error -> palette.error
                    OtpState.Idle -> when {
                        isFocused -> Color(0xFFC7D2FE) // Bright highlighted focus border like reference
                        isFilled -> Color(0xFF4338CA) // Subtle deep purple/indigo border
                        else -> palette.boxBorder
                    }
                }
                val borderWidth = if (isFocused) 2.dp else 1.dp

                Box(
                    modifier = Modifier
                        .size(boxWidth, boxHeight)
                        .clip(RoundedCornerShape(corner))
                        .background(palette.boxFill)
                        .border(borderWidth, borderColor, RoundedCornerShape(corner)),
                    contentAlignment = Alignment.Center,
                ) {
                    val char = code.getOrNull(index)?.toString().orEmpty()
                    if (char.isNotEmpty()) {
                        Text(
                            text = char,
                            style = TextStyle(
                                fontSize = if (boxWidth < 40.dp) 20.sp else 24.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            ),
                            color = palette.text,
                        )
                    } else if (isFocused) {
                        // Animated pulsing cursor line matching reference
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(22.dp)
                                .alpha(if (phase > 0.5f) 1f else 0.2f)
                                .background(Color(0xFF818CF8)),
                        )
                    }
                }
            }
        }

        // Result seal: a ringed badge with a soft glow, like the reference's morphed circle.
        if (state != OtpState.Idle) {
            val sealColor = if (state == OtpState.Success) palette.success else palette.error
            Box(
                modifier = Modifier
                    .offset(x = shakeX.value.dp)
                    .size(78.dp)
                    .drawBehind {
                        drawCircle(
                            color = sealColor.copy(alpha = 0.28f),
                            radius = size.minDimension / 2f + 6.dp.toPx(),
                        )
                    }
                    .clip(CircleShape)
                    .background(sealColor.copy(alpha = 0.12f))
                    .border(2.dp, sealColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (state == OtpState.Success) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = if (state == OtpState.Success) "Code accepted" else "Code rejected",
                    tint = sealColor,
                    modifier = Modifier.size(38.dp),
                )
            }
        }
    }
}

/**
 * The email verification page of the security checkpoint: shows the address, the code entry,
 * a status line and a resend button. The code is checked automatically once six digits are in.
 */
@Composable
fun EmailCodeVerification(
    email: String,
    dark: Boolean,
    notice: String?,
    resendCooldownSeconds: Int,
    onResend: () -> Unit,
    onVerify: (code: String, onResult: (errorMessage: String?) -> Unit) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = otpPalette(dark)
    val ink = if (dark) Color.White else Color(0xFF0F172A)

    var code by remember { mutableStateOf("") }
    var otpState by remember { mutableStateOf(OtpState.Idle) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    // Resets everything so the user gets a fresh set of empty boxes to retype
    fun resetForRetry() {
        code = ""
        checking = false
        otpState = OtpState.Idle
        errorMessage = null
    }

    fun updateCode(newCode: String) {
        code = newCode
        if (otpState == OtpState.Error) {
            otpState = OtpState.Idle
            errorMessage = null
        }
    }

    LaunchedEffect(code) {
        if (code.length == OTP_LENGTH && !checking && otpState != OtpState.Success) {
            checking = true
            onVerify(code) { error ->
                checking = false
                if (error == null) {
                    otpState = OtpState.Success
                    errorMessage = null
                } else {
                    otpState = OtpState.Error
                    errorMessage = error.ifBlank { "Invalid or expired code." }
                }
            }
        }
    }

    // 35-second safety timeout: network calls with hashing & ORDS roundtrips can take ~15-20s.
    // If checking turns false when the server responds, this coroutine is automatically cancelled.
    LaunchedEffect(checking) {
        if (checking) {
            delay(35_000)
            if (checking && otpState != OtpState.Success) {
                checking = false
                otpState = OtpState.Error
                errorMessage = "Verification timed out. Please try again."
            }
        }
    }

    val cardBg = if (dark) Color(0xFF0C1021) else Color.White
    val cardBorder = if (dark) Color(0xFF1E2648) else Color(0xFFE2E8F0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Lock artwork with subtle ambient glow matching reference
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF6366F1).copy(alpha = 0.35f), Color(0xFF312E81).copy(alpha = 0.2f)),
                        )
                    )
                    .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFFC7D2FE),
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Main Title matching reference "6 digit code"
            Text(
                text = "6 digit code",
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.3).sp,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle matching reference
            Text(
                text = "Please enter 6 digit verification code\nsent to $email",
                style = TextStyle(
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                ),
            )

            if (!notice.isNullOrBlank() && otpState == OtpState.Idle) {
                val isErrorNotice = notice.contains("fail", ignoreCase = true) ||
                    notice.contains("unable", ignoreCase = true) ||
                    notice.contains("error", ignoreCase = true) ||
                    notice.contains("could not", ignoreCase = true)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = notice,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isErrorNotice) palette.error else palette.success,
                        textAlign = TextAlign.Center,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Responsive 6 boxes — disabled while a verify request is in-flight
            OtpCodeEntry(
                code = code,
                onCodeChange = { updateCode(it) },
                state = otpState,
                dark = dark,
                enabled = !checking,
            )

            // Verifying spinner — shown while awaiting server response
            if (checking) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF818CF8),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Verifying…",
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                        ),
                    )
                }
            }

            // Error state: show message + explicit "Try Again" button
            if (otpState == OtpState.Error) {
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = errorMessage ?: "Invalid or expired code.",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.error,
                        textAlign = TextAlign.Center,
                    ),
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Prominent "Try Again" button — clears boxes and refocuses keyboard
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFEF4444).copy(alpha = 0.15f), Color(0xFFF87171).copy(alpha = 0.10f))
                            )
                        )
                        .border(1.dp, palette.error.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { resetForRetry() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Try Again",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.error,
                            letterSpacing = 0.3.sp,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Resend footer — shown in Idle and Error so the user can always request a fresh code
            if (otpState != OtpState.Success) {
                val onCooldown = resendCooldownSeconds > 0
                val resendEnabled = !checking && !onCooldown
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Didn't receive code? ",
                        style = TextStyle(fontSize = 13.sp, color = Color(0xFF94A3B8)),
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(
                                enabled = resendEnabled,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                resetForRetry()
                                onResend()
                            }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (onCooldown) "Resend (${resendCooldownSeconds}s)" else "Resend",
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (resendEnabled) Color(0xFF818CF8) else Color(0xFF4B5563),
                            ),
                        )
                    }
                }
            }

            if (onBack != null && otpState != OtpState.Success) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Change details",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B),
                    ),
                    modifier = Modifier.clickable { onBack() },
                )
            }
        }
    }
}

/** Traveling gradient stroke with a soft halo, drawn behind a filled box. */
private fun DrawScope.drawElectricGlow(phase: Float, colors: List<Color>, corner: Float) {
    val haloPad = 4.dp.toPx()
    drawRoundRect(
        color = colors.first().copy(alpha = 0.25f),
        topLeft = Offset(-haloPad, -haloPad),
        size = Size(size.width + haloPad * 2, size.height + haloPad * 2),
        cornerRadius = CornerRadius(corner + haloPad),
        style = Stroke(width = 6.dp.toPx()),
    )
    val sweepStart = size.width * (2f * phase - 1f)
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = colors,
            start = Offset(sweepStart, 0f),
            end = Offset(sweepStart + size.width, size.height),
        ),
        cornerRadius = CornerRadius(corner),
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
    )
}
