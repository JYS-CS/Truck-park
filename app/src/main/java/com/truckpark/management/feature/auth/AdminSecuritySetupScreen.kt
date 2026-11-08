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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.runtime.saveable.rememberSaveable
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
    viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = AuthViewModel.Factory),
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f

    // Security Gate State
    var passwordChanged by rememberSaveable { mutableStateOf(initialPasswordChanged) }
    var emailVerified by rememberSaveable { mutableStateOf(initialEmailVerified || user.emailVerified) }

    // Profile update fields
    var fullName by rememberSaveable {
        val raw = user.fullName.takeIf { !it.equals("System Administrator", ignoreCase = true) && !it.equals("Administrator", ignoreCase = true) && !it.equals("null", ignoreCase = true) }.orEmpty()
        mutableStateOf(raw)
    }
    var phoneNumber by rememberSaveable {
        val raw = user.phone?.takeIf { !it.equals("null", ignoreCase = true) }.orEmpty()
        mutableStateOf(raw)
    }
    var selectedCountryIso by rememberSaveable { mutableStateOf("ZM") }
    val selectedCountry = remember(selectedCountryIso) {
        LIGHTWEIGHT_COUNTRY_CODES.find { it.iso == selectedCountryIso } ?: DEFAULT_COUNTRY_CODE
    }
    var onVerifyPage by rememberSaveable { mutableStateOf(initialPasswordChanged) }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var newEmail by rememberSaveable {
        mutableStateOf(if (user.email.equals("admin@hibiscuss.com", ignoreCase = true)) "" else user.email)
    }
    var isChangingPassword by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var passwordSuccess by remember { mutableStateOf<String?>(null) }

    // Email verification fields
    var emailError by remember { mutableStateOf<String?>(null) }
    var emailSuccess by remember { mutableStateOf<String?>(null) }
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

    // Automatically send verification code when landing directly on step 2 from a previous session
    LaunchedEffect(Unit) {
        if (initialPasswordChanged && !emailVerified) {
            val target = if (newEmail.isNotBlank()) newEmail.trim() else user.email
            if (target.isNotBlank() && !target.equals("admin@hibiscuss.com", ignoreCase = true)) {
                viewModel.sendVerificationCode(email = target) { codeResult ->
                    when (codeResult) {
                        is AuthResult.Success -> {
                            resendCooldown = 40
                            emailError = null
                            emailSuccess = "Verification code sent to ${target.lowercase()}"
                        }
                        is AuthResult.Failure -> {
                            emailSuccess = null
                            emailError = codeResult.error.message
                        }
                    }
                }
            }
        }
    }

    // When both password change and email verification are completed, proceed to dashboard
    LaunchedEffect(passwordChanged, emailVerified) {
        if (passwordChanged && emailVerified) {
            delay(1200)
            onSetupComplete()
        }
    }

    AppBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = if (onVerifyPage) Alignment.Center else Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        horizontal = 20.dp,
                        vertical = if (onVerifyPage) 16.dp else 32.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (onVerifyPage) Arrangement.Center else Arrangement.Top,
            ) {
                // Top Brand Emblem (step 1 only; verify screen has its own integrated lock artwork)
                if (!onVerifyPage) {
                    Image(
                        painter = painterResource(id = R.drawable.hibiscus_emblem_trans),
                        contentDescription = "Hibiscus Logo",
                        modifier = Modifier.size(80.dp),
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Image(
                        painter = painterResource(
                            id = if (dark) R.drawable.hibiscus_wordmark_trans else R.drawable.hibiscus_wordmark_light,
                        ),
                        contentDescription = "HIBISCUS",
                        modifier = Modifier.width(160.dp),
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Title and subtitle (shown on step 1 only)
                if (!onVerifyPage) {
                    Text(
                        text = "Admin Account Setup",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                        ),
                        color = if (dark) Color.White else Color(0xFF0F172A),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Set up your admin email and a new password to get started.",
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
                }

                // STEP 1: REPLACE DEFAULT EMAIL AND PASSWORD (its own page)
                if (!onVerifyPage) {
                SecuritySectionCard(
                    title = "1. Update Your Credentials",
                    subtitle = if (passwordChanged && emailVerified) "Credentials updated" else "Enter your new email and password",
                    isComplete = passwordChanged && emailVerified,
                    dark = dark,
                ) {
                    if (passwordChanged && emailVerified) {
                        SecurityCompletedBanner(
                            title = "Password Secured",
                            subtitle = "Your account is secured with your custom password.",
                            dark = dark,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SetupActionButton(
                            text = "Continue to Dashboard →",
                            isEnabled = true,
                            isLoading = false,
                            dark = dark,
                            onClick = onSetupComplete,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "Please provide your full name, phone number, email and choose a strong password.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (dark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            )

                            SetupInputField(
                                value = fullName,
                                onValueChange = {
                                    fullName = it
                                    passwordError = null
                                },
                                placeholder = "Full name",
                                icon = Icons.Default.Person,
                                keyboardType = KeyboardType.Text,
                                dark = dark,
                            )

                            // Phone number with country code picker
                            PhoneInputField(
                                value = phoneNumber,
                                onValueChange = {
                                    phoneNumber = AuthSecurityUtils.filterPhoneInput(it, phoneNumber)
                                    passwordError = null
                                },
                                selectedCountry = selectedCountry,
                                onCountrySelected = { selectedCountryIso = it.iso },
                                dark = dark,
                            )

                            SetupInputField(
                                value = newEmail,
                                onValueChange = {
                                    newEmail = it
                                    passwordError = null
                                },
                                placeholder = "New email",
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
                                placeholder = "New password",
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
                            val canSubmitPassword = emailLooksValid &&
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
                                    if (newEmail.trim().equals("admin@hibiscuss.com", ignoreCase = true)) {
                                        passwordError = "Please enter your personal email address"
                                        return@SetupActionButton
                                    }
                                    if (!allRulesSatisfied) {
                                        passwordError = "New password must satisfy all 5 requirements"
                                        return@SetupActionButton
                                    }
                                    if (newPassword == "@Admin") {
                                        passwordError = "New password must be different from the default password"
                                        return@SetupActionButton
                                    }
                                    val formattedPhone = if (phoneNumber.isNotBlank()) {
                                        val phoneErr = AuthSecurityUtils.validatePhone(phoneNumber, selectedCountry.dialCode)
                                        if (phoneErr != null) {
                                            passwordError = phoneErr
                                            return@SetupActionButton
                                        }
                                        AuthSecurityUtils.formatPhoneWithDialCode(phoneNumber, selectedCountry.dialCode)
                                    } else null
                                    isChangingPassword = true
                                    passwordError = null
                                    // Send verification code first; credentials are only saved to DB once the code is verified
                                    viewModel.sendVerificationCode(email = newEmail.trim()) { codeResult ->
                                        isChangingPassword = false
                                        when (codeResult) {
                                            is AuthResult.Success -> {
                                                emailVerified = false
                                                onVerifyPage = true
                                                resendCooldown = 40
                                                emailError = null
                                                emailSuccess = "Verification code sent to ${newEmail.trim().lowercase()}"
                                            }
                                            is AuthResult.Failure -> {
                                                passwordError = codeResult.error.message
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                }

                // STEP 2: VERIFY OFFICIAL EMAIL ADDRESS (its own page)
                if (onVerifyPage) {
                    val targetEmail = if (newEmail.isNotBlank()) newEmail.trim() else user.email
                    if (emailVerified) {
                        SecurityCompletedBanner(
                            title = "Email Confirmed",
                            subtitle = "$targetEmail is officially verified.",
                            dark = dark,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SetupActionButton(
                            text = "Continue to Dashboard →",
                            isEnabled = true,
                            isLoading = false,
                            dark = dark,
                            onClick = onSetupComplete,
                        )
                    } else {
                        EmailCodeVerification(
                            email = targetEmail,
                            dark = dark,
                            notice = emailError ?: emailSuccess,
                            resendCooldownSeconds = resendCooldown,
                            onResend = {
                                emailError = null
                                viewModel.sendVerificationCode(email = targetEmail) { codeResult ->
                                    when (codeResult) {
                                        is AuthResult.Success -> {
                                            resendCooldown = 40
                                            emailError = null
                                            emailSuccess = "Verification code sent to $targetEmail"
                                        }
                                        is AuthResult.Failure -> {
                                            emailSuccess = null
                                            emailError = codeResult.error.message
                                        }
                                    }
                                }
                            },
                            onVerify = { code, onResult ->
                                val formattedPhone = if (phoneNumber.isNotBlank()) {
                                    AuthSecurityUtils.formatPhoneWithDialCode(phoneNumber, selectedCountry.dialCode)
                                } else null

                                viewModel.verifyEmail(
                                    code = code,
                                    newEmail = targetEmail,
                                    newPassword = newPassword.takeIf { it.isNotBlank() },
                                    fullName = fullName.trim().takeIf { it.isNotEmpty() },
                                    phone = formattedPhone,
                                ) { result ->
                                    when (result) {
                                        is AuthResult.Success -> {
                                            onResult(null)
                                            // Let the green success animation play before proceeding to dashboard
                                            scope.launch {
                                                delay(900)
                                                passwordChanged = true
                                                emailVerified = true
                                                delay(800)
                                                onSetupComplete()
                                            }
                                        }
                                        is AuthResult.Failure -> onResult(result.error.message)
                                    }
                                }
                            },
                            onBack = {
                                onVerifyPage = false
                                emailError = null
                                emailSuccess = null
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Sign out and use another account",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (dark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    modifier = Modifier
                        .clip(CapsuleShape)
                        .clickable { viewModel.signOut() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))
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
private fun PhoneInputField(
    value: String,
    onValueChange: (String) -> Unit,
    selectedCountry: CountryDialCode,
    onCountrySelected: (CountryDialCode) -> Unit,
    dark: Boolean,
) {
    val bg = if (dark) Color(0xFF1E293B) else Color.White
    val border = if (dark) Color(0xFF334155) else Color(0xFFCBD5E1)
    var expandedDropdown by remember { mutableStateOf(false) }

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
            // Country picker button
            val hasMultipleCountries = LIGHTWEIGHT_COUNTRY_CODES.size > 1
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (hasMultipleCountries) Modifier.clickable { expandedDropdown = true }
                            else Modifier
                        )
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${selectedCountry.flag} ${selectedCountry.dialCode}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (dark) Color.White else Color(0xFF0F172A),
                    )
                    if (hasMultipleCountries) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = if (dark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (hasMultipleCountries) {
                    DropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false },
                    ) {
                        LIGHTWEIGHT_COUNTRY_CODES.forEach { country ->
                            DropdownMenuItem(
                                text = {
                                    Text("${country.flag} ${country.name} (${country.dialCode})")
                                },
                                onClick = {
                                    onCountrySelected(country)
                                    expandedDropdown = false
                                },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = "Phone number (optional)",
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
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next,
                    ),
                    cursorBrush = SolidColor(if (dark) Color(0xFF38BDF8) else Color(0xFF0284C7)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val isPhoneComplete = remember(value, selectedCountry.dialCode) {
                value.isNotBlank() && AuthSecurityUtils.validatePhone(value, selectedCountry.dialCode) == null
            }

            AnimatedVisibility(
                visible = isPhoneComplete,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981).copy(alpha = 0.18f))
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Valid phone number",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(15.dp),
                    )
                }
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
