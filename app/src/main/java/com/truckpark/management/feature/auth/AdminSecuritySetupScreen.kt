package com.truckpark.management.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truckpark.management.R
import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.core.designsystem.components.AppBackground
import com.truckpark.management.domain.models.AuthUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CapsuleShape = RoundedCornerShape(999.dp)
private val CardShape = RoundedCornerShape(20.dp)

private data class PasswordRequirement(
    val title: String,
    val isSatisfied: (String) -> Boolean,
)

private val REQUIREMENTS = listOf(
    PasswordRequirement("At least 8 characters") { it.length >= 8 },
    PasswordRequirement("Uppercase letter (A-Z)") { it.any { c -> c.isUpperCase() } },
    PasswordRequirement("Lowercase letter (a-z)") { it.any { c -> c.isLowerCase() } },
    PasswordRequirement("Number (0-9)") { it.any { c -> c.isDigit() } },
    PasswordRequirement("Special symbol (!@#$...)") { it.any { c -> !c.isLetterOrDigit() && !c.isWhitespace() } },
)

@Composable
fun AdminSecuritySetupScreen(
    user: AuthUser,
    initialPasswordChanged: Boolean = false,
    initialEmailVerified: Boolean = false,
    onSetupComplete: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = AuthViewModel.Factory),
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f

    // Security Gate State
    var passwordChanged by remember { mutableStateOf(initialPasswordChanged) }
    var emailVerified by remember { mutableStateOf(initialEmailVerified || user.emailVerified) }

    // Password change fields
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var isChangingPassword by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var passwordSuccess by remember { mutableStateOf<String?>(null) }

    // Email verification fields
    var verificationCode by remember { mutableStateOf("") }
    var isSendingCode by remember { mutableStateOf(false) }
    var isVerifyingCode by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var emailSuccess by remember { mutableStateOf<String?>(null) }
    var debugCodeHint by remember { mutableStateOf<String?>(null) }
    var resendCooldown by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Resend cooldown timer
    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000)
            resendCooldown -= 1
        }
    }

    val allRequirementsMet = passwordChanged && emailVerified

    AppBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top Brand Emblem
                Image(
                    painter = painterResource(id = R.drawable.hibiscus_emblem_trans),
                    contentDescription = "Hibiscus Logo",
                    modifier = Modifier.size(80.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Image(
                    painter = painterResource(
                        id = if (dark) R.drawable.hibiscus_wordmark_trans else R.drawable.hibiscus_wordmark_light,
                    ),
                    contentDescription = "HIBISCUS",
                    modifier = Modifier.width(160.dp),
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Title and subtitle
                Text(
                    text = "Security Checkpoint",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    ),
                    color = if (dark) Color.White else Color(0xFF0F172A),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Administrator onboarding requirement: update your temporary password and verify your email before entering the command center.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (dark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 420.dp),
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Administrator Info Card
                AdminIdentityCard(
                    user = user,
                    passwordChanged = passwordChanged,
                    emailVerified = emailVerified,
                    dark = dark,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // STEP 1: CHANGE INITIAL PASSWORD
                SecuritySectionCard(
                    title = "1. Update Administrator Password",
                    subtitle = if (passwordChanged) "Secure administrator password configured" else "Enter your current password, your own email and a new password",
                    isComplete = passwordChanged,
                    dark = dark,
                ) {
                    if (passwordChanged) {
                        SecurityCompletedBanner(
                            title = "Password Secured",
                            subtitle = "Your account is secured with your custom password.",
                            dark = dark,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SetupInputField(
                                value = currentPassword,
                                onValueChange = {
                                    currentPassword = it
                                    passwordError = null
                                },
                                placeholder = "Current password (e.g. @Admin)",
                                icon = Icons.Default.Lock,
                                isPassword = true,
                                dark = dark,
                            )

                            SetupInputField(
                                value = newEmail,
                                onValueChange = {
                                    newEmail = it
                                    passwordError = null
                                },
                                placeholder = "Your email address (not the default)",
                                icon = Icons.Default.Email,
                                keyboardType = KeyboardType.Email,
                                dark = dark,
                            )

                            SetupInputField(
                                value = newPassword,
                                onValueChange = {
                                    newPassword = it
                                    passwordError = null
                                },
                                placeholder = "New secure password",
                                icon = Icons.Default.Lock,
                                isPassword = true,
                                dark = dark,
                            )

                            // Requirements Checklist
                            RequirementsChecklistView(
                                password = newPassword,
                                dark = dark,
                            )

                            if (passwordError != null) {
                                ErrorMessageBanner(message = passwordError!!, dark = dark)
                            }

                            if (passwordSuccess != null) {
                                SuccessMessageBanner(message = passwordSuccess!!, dark = dark)
                            }

                            val allRulesSatisfied = REQUIREMENTS.all { it.isSatisfied(newPassword) }
                            val emailLooksValid = android.util.Patterns.EMAIL_ADDRESS
                                .matcher(newEmail.trim()).matches()
                            val canSubmitPassword = currentPassword.isNotBlank() &&
                                emailLooksValid &&
                                allRulesSatisfied &&
                                !isChangingPassword

                            SetupActionButton(
                                text = "Next",
                                isEnabled = canSubmitPassword,
                                isLoading = isChangingPassword,
                                dark = dark,
                                onClick = {
                                    if (!emailLooksValid) {
                                        passwordError = "Enter a valid email address"
                                        return@SetupActionButton
                                    }
                                    if (!allRulesSatisfied) {
                                        passwordError = "New password must satisfy all 5 requirements"
                                        return@SetupActionButton
                                    }
                                    isChangingPassword = true
                                    passwordError = null
                                    viewModel.changePassword(
                                        currentPassword = currentPassword,
                                        newEmail = newEmail.trim(),
                                        newPassword = newPassword,
                                    ) { result ->
                                        isChangingPassword = false
                                        when (result) {
                                            is AuthResult.Success -> {
                                                passwordChanged = true
                                                emailVerified = false
                                                passwordSuccess = "Details saved. Next, verify your new email."
                                                // Send the code straight to the new address, so the next screen is ready.
                                                viewModel.sendVerificationCode { codeResult ->
                                                    when (codeResult) {
                                                        is AuthResult.Success -> {
                                                            resendCooldown = 60
                                                            emailSuccess = "Verification code sent to ${newEmail.trim().lowercase()}"
                                                            debugCodeHint = codeResult.value
                                                        }
                                                        is AuthResult.Failure -> {
                                                            emailError = codeResult.error.message
                                                        }
                                                    }
                                                }
                                            }
                                            is AuthResult.Failure -> {
                                                passwordError = result.error.message
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // STEP 2: VERIFY OFFICIAL EMAIL ADDRESS
                SecuritySectionCard(
                    title = "2. Verify Administrator Email",
                    subtitle = if (emailVerified) "Official email verified and active" else "Confirm access to ${user.email}",
                    isComplete = emailVerified,
                    dark = dark,
                ) {
                    if (emailVerified) {
                        SecurityCompletedBanner(
                            title = "Email Confirmed",
                            subtitle = "${user.email} is officially verified.",
                            dark = dark,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = user.email,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (dark) Color.White else Color(0xFF0F172A),
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(CapsuleShape)
                                        .background(if (dark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                                        .clickable(enabled = !isSendingCode && resendCooldown == 0) {
                                            isSendingCode = true
                                            emailError = null
                                            viewModel.sendVerificationCode { result ->
                                                isSendingCode = false
                                                when (result) {
                                                    is AuthResult.Success -> {
                                                        resendCooldown = 60
                                                        emailSuccess = "Verification code dispatched!"
                                                        if (result.value != null) {
                                                            debugCodeHint = result.value
                                                        }
                                                    }
                                                    is AuthResult.Failure -> {
                                                        emailError = result.error.message
                                                    }
                                                }
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    if (isSendingCode) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = if (dark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                                        )
                                    } else {
                                        Text(
                                            text = if (resendCooldown > 0) "Resend (${resendCooldown}s)" else "Send Code",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (resendCooldown > 0) {
                                                Color(0xFF64748B)
                                            } else if (dark) {
                                                Color(0xFF38BDF8)
                                            } else {
                                                Color(0xFF0284C7)
                                            },
                                        )
                                    }
                                }
                            }

                            if (debugCodeHint != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (dark) Color(0xFF0C4A6E).copy(alpha = 0.4f) else Color(0xFFE0F2FE))
                                        .border(1.dp, if (dark) Color(0xFF0284C7) else Color(0xFF38BDF8), RoundedCornerShape(12.dp))
                                        .clickable { verificationCode = debugCodeHint!! }
                                        .padding(12.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column {
                                            Text(
                                                text = "Test Verification Code",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (dark) Color(0xFF7DD3FC) else Color(0xFF0369A1),
                                            )
                                            Text(
                                                text = debugCodeHint!!,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp),
                                                color = if (dark) Color.White else Color(0xFF0C4A6E),
                                            )
                                        }
                                        Text(
                                            text = "Tap to fill",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (dark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                                        )
                                    }
                                }
                            }

                            SetupInputField(
                                value = verificationCode,
                                onValueChange = {
                                    if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                        verificationCode = it
                                        emailError = null
                                    }
                                },
                                placeholder = "Enter 6-digit code",
                                icon = Icons.Default.Email,
                                keyboardType = KeyboardType.Number,
                                dark = dark,
                            )

                            if (emailError != null) {
                                ErrorMessageBanner(message = emailError!!, dark = dark)
                            }

                            if (emailSuccess != null) {
                                SuccessMessageBanner(message = emailSuccess!!, dark = dark)
                            }

                            val canVerify = verificationCode.length == 6 && !isVerifyingCode

                            SetupActionButton(
                                text = "Verify Email",
                                isEnabled = canVerify,
                                isLoading = isVerifyingCode,
                                dark = dark,
                                onClick = {
                                    isVerifyingCode = true
                                    emailError = null
                                    viewModel.verifyEmail(verificationCode) { result ->
                                        isVerifyingCode = false
                                        when (result) {
                                            is AuthResult.Success -> {
                                                emailVerified = true
                                                emailSuccess = "Email successfully verified!"
                                            }
                                            is AuthResult.Failure -> {
                                                emailError = result.error.message
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // BOTTOM ACTION: PROCEED TO DASHBOARD
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 440.dp),
                ) {
                    val proceedButtonBrush = if (allRequirementsMet) {
                        if (dark) {
                            Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF047857)))
                        }
                    } else {
                        if (dark) {
                            Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFFCBD5E1), Color(0xFF94A3B8)))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(CapsuleShape)
                            .background(proceedButtonBrush)
                            .clickable(
                                enabled = allRequirementsMet,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onSetupComplete,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            if (allRequirementsMet) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = if (allRequirementsMet) "Enter TruckPark Command Center" else "Complete Both Steps to Proceed",
                                color = if (allRequirementsMet) Color.White else if (dark) Color(0xFF64748B) else Color(0xFF475569),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sign Out Link
                Text(
                    text = "Sign Out & Switch Account",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (dark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSignOut,
                        )
                        .padding(8.dp),
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AdminIdentityCard(
    user: AuthUser,
    passwordChanged: Boolean,
    emailVerified: Boolean,
    dark: Boolean,
) {
    val bg = if (dark) Color(0xFF0F172A).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.90f)
    val border = if (dark) Color(0xFF334155).copy(alpha = 0.6f) else Color(0xFFE2E8F0)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 440.dp)
            .clip(CardShape)
            .background(bg)
            .border(1.dp, border, CardShape)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (dark) Color(0xFF1E293B) else Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = if (dark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.fullName.ifBlank { "Administrator" },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (dark) Color.White else Color(0xFF0F172A),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CapsuleShape)
                            .background(Color(0xFF38BDF8).copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "ADMIN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                            ),
                            color = if (dark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        )
                    }
                }
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dark) Color(0xFF94A3B8) else Color(0xFF64748B),
                )
            }
        }
    }
}

@Composable
private fun SecuritySectionCard(
    title: String,
    subtitle: String,
    isComplete: Boolean,
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val bg = if (dark) Color(0xFF0F172A).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.95f)
    val borderColor by animateColorAsState(
        targetValue = if (isComplete) {
            Color(0xFF10B981).copy(alpha = 0.7f)
        } else if (dark) {
            Color(0xFF334155).copy(alpha = 0.7f)
        } else {
            Color(0xFFCBD5E1)
        },
        animationSpec = tween(300),
        label = "borderColor",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 440.dp)
            .clip(CardShape)
            .background(bg)
            .border(1.dp, borderColor, CardShape)
            .padding(20.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (dark) Color.White else Color(0xFF0F172A),
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    )
                }

                if (isComplete) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            content()
        }
    }
}

@Composable
private fun SecurityCompletedBanner(
    title: String,
    subtitle: String,
    dark: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (dark) Color(0xFF064E3B).copy(alpha = 0.35f) else Color(0xFFD1FAE5))
            .border(1.dp, if (dark) Color(0xFF059669).copy(alpha = 0.6f) else Color(0xFF10B981), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (dark) Color(0xFF6EE7B7) else Color(0xFF065F46),
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dark) Color(0xFFA7F3D0) else Color(0xFF047857),
                )
            }
        }
    }
}

@Composable
private fun RequirementsChecklistView(
    password: String,
    dark: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (dark) Color(0xFF1E293B).copy(alpha = 0.6f) else Color(0xFFF8FAFC))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        REQUIREMENTS.forEach { req ->
            val satisfied = req.isSatisfied(password)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (satisfied) Color(0xFF10B981) else if (dark) Color(0xFF475569) else Color(0xFFCBD5E1)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (satisfied) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = req.title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (satisfied) {
                            if (dark) Color(0xFF6EE7B7) else Color(0xFF047857)
                        } else {
                            if (dark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        },
                        fontWeight = if (satisfied) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SetupInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    dark: Boolean,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    val bg = if (dark) Color(0xFF1E293B) else Color.White
    val border = if (dark) Color(0xFF334155) else Color(0xFFCBD5E1)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (dark) Color(0xFF64748B) else Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp),
            )

            Spacer(modifier = Modifier.width(10.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dark) Color(0xFF64748B) else Color(0xFF94A3B8),
                    )
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = if (dark) Color.White else Color(0xFF0F172A),
                    ),
                    visualTransformation = if (isPassword && !passwordVisible) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = ImeAction.Next,
                    ),
                    cursorBrush = SolidColor(if (dark) Color(0xFF38BDF8) else Color(0xFF0284C7)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (isPassword) {
                Text(
                    text = if (passwordVisible) "HIDE" else "SHOW",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (dark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { passwordVisible = !passwordVisible },
                        )
                        .padding(4.dp),
                )
            }
        }
    }
}

@Composable
private fun SetupActionButton(
    text: String,
    isEnabled: Boolean,
    isLoading: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val buttonBrush = if (isEnabled) {
        if (dark) {
            Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7)))
        } else {
            Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF0369A1)))
        }
    } else {
        if (dark) {
            Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
        } else {
            Brush.linearGradient(listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1)))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(buttonBrush)
            .clickable(
                enabled = isEnabled && !isLoading,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isEnabled) Color.White else if (dark) Color(0xFF64748B) else Color(0xFF94A3B8),
            )
        }
    }
}

@Composable
private fun ErrorMessageBanner(message: String, dark: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (dark) Color(0xFF7F1D1D).copy(alpha = 0.4f) else Color(0xFFFEE2E2))
            .border(1.dp, if (dark) Color(0xFFEF4444) else Color(0xFFF87171), RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (dark) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
            )
        }
    }
}

@Composable
private fun SuccessMessageBanner(message: String, dark: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (dark) Color(0xFF064E3B).copy(alpha = 0.4f) else Color(0xFFD1FAE5))
            .border(1.dp, if (dark) Color(0xFF10B981) else Color(0xFF34D399), RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (dark) Color(0xFF6EE7B7) else Color(0xFF065F46),
            )
        }
    }
}
