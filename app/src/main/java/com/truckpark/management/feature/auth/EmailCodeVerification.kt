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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
        boxFill = Color(0xFF1E293B),
        boxBorder = Color(0xFF334155),
        text = Color.White,
        glow = listOf(Color(0xFF38BDF8), Color(0xFF34D399)),
        success = Color(0xFF34D399),
        error = Color(0xFFF87171),
        accent = Color(0xFF38BDF8),
        muted = Color(0xFF94A3B8),
        chip = Color(0xFF1E293B),
    )
} else {
    OtpPalette(
        boxFill = Color.White,
        boxBorder = Color(0xFFCBD5E1),
        text = Color(0xFF0F172A),
        glow = listOf(Color(0xFF38BDF8), Color(0xFF34D399)),
        success = Color(0xFF10B981),
        error = Color(0xFFEF4444),
        accent = Color(0xFF0284C7),
        muted = Color(0xFF64748B),
        chip = Color(0xFFE2E8F0),
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

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(state) {
        if (state == OtpState.Error) {
            shakeX.snapTo(0f)
            for (target in listOf(-8f, 8f, -6f, 6f, 0f)) {
                shakeX.animateTo(target, tween(durationMillis = 70))
            }
        }
    }

    Box(
        modifier = modifier
            .width(OtpRowWidth)
            .height(OtpBoxHeight)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { focusRequester.requestFocus() },
        contentAlignment = Alignment.Center,
    ) {
        // Hidden field that receives the keyboard, digits and paste. The boxes only display it.
        BasicTextField(
            value = code,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(OTP_LENGTH)
                if (digits != code) onCodeChange(digits)
            },
            enabled = enabled && state != OtpState.Success,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
                .focusRequester(focusRequester),
        )

        Row(
            modifier = Modifier
                .offset(x = shakeX.value.dp)
                .alpha(if (state == OtpState.Success) 0f else 1f),
            horizontalArrangement = Arrangement.spacedBy(OtpBoxGap),
        ) {
            repeat(OTP_LENGTH) { index ->
                val filled = index < code.length
                val glowing = filled && state == OtpState.Idle
                val borderColor = when (state) {
                    OtpState.Success -> palette.success
                    OtpState.Error -> palette.error
                    OtpState.Idle -> if (filled) palette.glow.first() else palette.boxBorder
                }
                Box(
                    modifier = Modifier
                        .size(OtpBoxWidth, OtpBoxHeight)
                        .drawBehind {
                            if (glowing) drawElectricGlow(phase, palette.glow, OtpBoxCorner.toPx())
                        }
                        .clip(RoundedCornerShape(OtpBoxCorner))
                        .background(palette.boxFill)
                        .border(1.5.dp, borderColor, RoundedCornerShape(OtpBoxCorner)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = code.getOrNull(index)?.toString().orEmpty(),
                        style = TextStyle(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                        color = palette.text,
                    )
                }
            }
        }

        if (state == OtpState.Success) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(palette.success.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Code accepted",
                    tint = palette.success,
                    modifier = Modifier.size(40.dp),
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
    modifier: Modifier = Modifier,
) {
    val palette = otpPalette(dark)
    val ink = if (dark) Color.White else Color(0xFF0F172A)

    var code by remember { mutableStateOf("") }
    var otpState by remember { mutableStateOf(OtpState.Idle) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

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
                    errorMessage = error
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Enter the 6-digit code sent to",
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
        )
        Text(
            text = email,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = ink,
        )
        if (notice != null) {
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        OtpCodeEntry(
            code = code,
            onCodeChange = { updateCode(it) },
            state = otpState,
            dark = dark,
        )

        Text(
            text = when (otpState) {
                OtpState.Success -> "Email verified"
                OtpState.Error -> errorMessage ?: "Invalid code"
                OtpState.Idle -> if (checking) "Checking…" else ""
            },
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = when (otpState) {
                OtpState.Success -> palette.success
                OtpState.Error -> palette.error
                OtpState.Idle -> palette.muted
            },
            textAlign = TextAlign.Center,
        )

        val canResend = resendCooldownSeconds == 0 && otpState != OtpState.Success
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(palette.chip)
                .clickable(
                    enabled = canResend,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onResend() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (canResend) "Send new code" else "Resend (${resendCooldownSeconds}s)",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (canResend) palette.accent else palette.muted,
            )
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
