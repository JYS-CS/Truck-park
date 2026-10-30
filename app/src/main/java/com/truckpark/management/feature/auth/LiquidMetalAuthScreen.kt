package com.truckpark.management.feature.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
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
import com.truckpark.management.core.designsystem.components.AppBackground
import kotlinx.coroutines.launch
import kotlin.math.sin

private data class PasswordRule(
    val title: String,
    val isSatisfied: (String) -> Boolean,
)

private object AuthSecurityUtils {
    private val SQL_INJECTION_KEYWORDS = Regex(
        "(?i)\\b(SELECT|INSERT|UPDATE|DELETE|DROP|UNION|ALTER|CREATE|EXEC|EXECUTE|TRUNCATE|DECLARE)\\b"
    )
    private val SQL_INJECTION_SYNTAX = Regex(
        "(--|/\\*|\\*/|;|(?:'|\")\\s*(?:OR|AND)\\s*['\"\\d]|(?:'|\")\\s*=\\s*(?:'|\"))",
        RegexOption.IGNORE_CASE
    )
    private val SCRIPT_INJECTION = Regex(
        "(?i)<script.*?>.*?</script.*?>|<.*?javascript:.*?>|<.*?\\b(on\\w+)=.*?>"
    )

    private val USERNAME_REGEX = Regex("^[a-zA-Z0-9._-]{3,30}$")
    private val EMAIL_REGEX = Regex("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$")
    private val FULL_NAME_REGEX = Regex("^[\\p{L} .'-]{2,50}$")

    val PASSWORD_RULES = listOf(
        PasswordRule("At least 8 characters") { it.length >= 8 },
        PasswordRule("Uppercase letter (A-Z)") { it.any { c -> c.isUpperCase() } },
        PasswordRule("Lowercase letter (a-z)") { it.any { c -> c.isLowerCase() } },
        PasswordRule("Number (0-9)") { it.any { c -> c.isDigit() } },
        PasswordRule("Special symbol (!@#$...)") { it.any { c -> !c.isLetterOrDigit() && !c.isWhitespace() } },
    )

    fun sanitizeText(input: String, maxLength: Int = 100): String {
        return input
            .replace("\u0000", "")
            .filter { it >= ' ' || it == '\n' || it == '\r' || it == '\t' }
            .take(maxLength)
    }

    fun sanitizePassword(input: String): String {
        return input.replace("\u0000", "").take(100)
    }

    fun sanitizePhone(input: String): String {
        return input.filter { it.isDigit() || it == '+' || it == '-' || it == ' ' || it == '(' || it == ')' }.take(20)
    }

    fun hasDangerousPattern(input: String): Boolean {
        return SQL_INJECTION_KEYWORDS.containsMatchIn(input) ||
            SQL_INJECTION_SYNTAX.containsMatchIn(input) ||
            SCRIPT_INJECTION.containsMatchIn(input)
    }

    fun validateEmail(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "Email address is required"
        if (hasDangerousPattern(trimmed)) return "Invalid or unsafe input detected"
        if (!trimmed.contains("@")) return "Email must contain an '@' symbol"
        val parts = trimmed.split("@")
        if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return "Please enter a complete email address"
        }
        val local = parts[0]
        val domain = parts[1]
        if (local.startsWith(".") || local.endsWith(".") || local.contains("..")) {
            return "Email prefix has an invalid dot format"
        }
        if (!domain.contains(".") || domain.startsWith(".") || domain.endsWith(".")) {
            return "Email domain must include a valid dot extension (e.g. .com)"
        }
        val domainParts = domain.split(".")
        if (domainParts.any { it.isEmpty() } || domainParts.last().length < 2) {
            return "Please enter a valid top-level domain (e.g. .com)"
        }
        if (!EMAIL_REGEX.matches(trimmed)) {
            return "Please enter a valid email address (e.g. name@domain.com)"
        }
        return null
    }

    fun validateEmailOrUsername(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "Email or username is required"
        if (hasDangerousPattern(trimmed)) return "Invalid or unsafe input detected"
        if (trimmed.contains("@")) {
            return validateEmail(trimmed)
        }
        if (!USERNAME_REGEX.matches(trimmed)) {
            return "Username must be 3-30 chars (letters, numbers, dot, underscore)"
        }
        return null
    }

    fun validateFullName(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "Full name is required"
        if (hasDangerousPattern(trimmed)) return "Invalid or unsafe input detected"
        if (!FULL_NAME_REGEX.matches(trimmed)) return "Enter a valid name (2-50 letters, spaces, hyphens)"
        return null
    }

    fun validatePhone(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "Phone number is required"
        if (hasDangerousPattern(trimmed)) return "Invalid or unsafe input detected"
        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.length < 7 || digitsOnly.length > 15) {
            return "Enter a valid phone number (7-15 digits)"
        }
        return null
    }

    fun validatePassword(input: String, requireComplexity: Boolean = false): String? {
        if (input.isEmpty()) return "Password is required"
        if (input.contains("\u0000")) return "Password contains invalid characters"
        if (input.length > 100) return "Password cannot exceed 100 characters"
        if (requireComplexity) {
            if (input.length < 8) return "Password must be at least 8 characters"
            val unmet = PASSWORD_RULES.filter { !it.isSatisfied(input) }
            if (unmet.isNotEmpty()) {
                return "Password must meet all 5 requirements below"
            }
        }
        return null
    }
}

enum class AuthMode {
    SignIn,
    CreateAccount,
}

private val CapsuleShape = RoundedCornerShape(999.dp)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LiquidMetalAuthScreen(
    onAuthSuccess: () -> Unit = {},
    viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = AuthViewModel.Factory),
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f

    val authState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading = authState is AuthUiState.Loading

    // When authentication succeeds on backend, trigger navigation
    androidx.compose.runtime.LaunchedEffect(authState) {
        if (authState is AuthUiState.Authenticated) {
            onAuthSuccess()
        }
    }

    var serverErrorMessage by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(authState) {
        if (authState is AuthUiState.Error) {
            serverErrorMessage = (authState as AuthUiState.Error).message
        } else if (authState !is AuthUiState.Error) {
            serverErrorMessage = null
        }
    }

    var mode by remember { mutableStateOf(AuthMode.SignIn) }
    var isSplit by remember { mutableStateOf(false) }

    var emailOrUser by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }

    var emailOrUserError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var fullNameError by remember { mutableStateOf<String?>(null) }
    var phoneNumberError by remember { mutableStateOf<String?>(null) }

    val submitBringIntoViewRequester = remember { BringIntoViewRequester() }

    var animTime by remember { mutableStateOf(0f) }
    var animPos by remember { mutableStateOf(0f) }
    var isPhysicsActive by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    fun resetState(newMode: AuthMode) {
        mode = newMode
        isSplit = false
        isPhysicsActive = false
        animTime = 0f
        animPos = 0f
        emailOrUserError = null
        passwordError = null
        fullNameError = null
        phoneNumberError = null
        serverErrorMessage = null
        viewModel.clearError()
        focusManager.clearFocus()
    }

    fun submitSignIn() {
        serverErrorMessage = null
        val sanitizedTarget = AuthSecurityUtils.sanitizeText(emailOrUser.trim(), 60)
        val sanitizedPassword = AuthSecurityUtils.sanitizePassword(password)

        val eError = AuthSecurityUtils.validateEmail(sanitizedTarget)
        val pError = AuthSecurityUtils.validatePassword(sanitizedPassword, requireComplexity = false)

        emailOrUserError = eError
        passwordError = pError

        if (eError == null && pError == null) {
            focusManager.clearFocus()
            viewModel.signIn(sanitizedTarget, sanitizedPassword)
        }
    }

    fun submitSignUp() {
        val sanitizedName = AuthSecurityUtils.sanitizeText(fullName.trim(), 50)
        val sanitizedEmail = AuthSecurityUtils.sanitizeText(emailOrUser.trim(), 60)
        val sanitizedPhone = AuthSecurityUtils.sanitizePhone(phoneNumber.trim())
        val sanitizedPassword = AuthSecurityUtils.sanitizePassword(password)

        val nError = AuthSecurityUtils.validateFullName(sanitizedName)
        val eError = AuthSecurityUtils.validateEmail(sanitizedEmail)
        val phError = AuthSecurityUtils.validatePhone(sanitizedPhone)
        val pError = AuthSecurityUtils.validatePassword(sanitizedPassword, requireComplexity = true)

        fullNameError = nError
        emailOrUserError = eError
        phoneNumberError = phError
        passwordError = pError

        if (nError == null && eError == null && phError == null && pError == null) {
            focusManager.clearFocus()
            onAuthSuccess()
        }
    }

    fun triggerSplit() {
        isSplit = true

        coroutineScope.launch {
            isPhysicsActive = true
            val k = 85f
            val damping = 14f
            val dt = 0.016f
            var t = 0f
            var pos = -35f
            var vel = 0f

            while (t < 1.6f) {
                val force = -k * pos - damping * vel
                vel += force * dt
                pos += vel * dt
                t += dt

                animTime = t
                animPos = pos
                kotlinx.coroutines.delay(16)
            }

            animTime = 1.6f
            animPos = 0f
            isPhysicsActive = false
        }
    }

    val scrollState = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Scroll to bottom when the keyboard pops up in split mode
    val imeBottom = WindowInsets.ime.getBottom(density)
    androidx.compose.runtime.LaunchedEffect(imeBottom) {
        if (isSplit && imeBottom > 0) {
            kotlinx.coroutines.delay(450)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    AppBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.hibiscus_emblem_trans),
                    contentDescription = "Hibiscus Logo",
                    modifier = Modifier.size(100.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Image(
                    painter = painterResource(
                        id = if (dark) R.drawable.hibiscus_wordmark_trans else R.drawable.hibiscus_wordmark_light
                    ),
                    contentDescription = "HIBISCUS",
                    modifier = Modifier.width(180.dp),
                )

                Spacer(modifier = Modifier.height(28.dp))

                LiquidModeSwitch(
                    selectedMode = mode,
                    onModeSelected = { newMode ->
                        if (mode != newMode) {
                            resetState(newMode)
                        }
                    },
                    dark = dark,
                )

                Spacer(modifier = Modifier.height(28.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (isSplit && animTime < 1.4f) {
                        val liquidAlpha = ((1.4f - animTime) / 1.4f).coerceIn(0f, 1f)
                        val strandColor = if (dark) scheme.secondary else scheme.primary

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (mode == AuthMode.SignIn) 240.dp else 360.dp),
                        ) {
                            val centerX = size.width / 2f
                            val numStrands = if (mode == AuthMode.SignIn) 2 else 4
                            val slotHeight = 72.dp.toPx()
                            val strandW = 26.dp.toPx() * liquidAlpha

                            if (strandW > 2f) {
                                for (i in 0 until numStrands) {
                                    val yTop = (i * slotHeight) + 56.dp.toPx()
                                    val yBottom = ((i + 1) * slotHeight)
                                    val gap = yBottom - yTop
                                    if (gap in 1f..110f) {
                                        val waist = (strandW * 0.35f * liquidAlpha).coerceAtLeast(1.5f)
                                        val path = Path().apply {
                                            moveTo(centerX - strandW / 2f, yTop)
                                            quadraticBezierTo(centerX - waist, yTop + gap / 2f, centerX - strandW / 2f, yBottom)
                                            lineTo(centerX + strandW / 2f, yBottom)
                                            quadraticBezierTo(centerX + waist, yTop + gap / 2f, centerX + strandW / 2f, yTop)
                                            close()
                                        }

                                        drawPath(
                                            path = path,
                                            brush = Brush.verticalGradient(
                                                listOf(
                                                    strandColor.copy(alpha = 0.65f * liquidAlpha),
                                                    Color.White.copy(alpha = 0.95f * liquidAlpha),
                                                    Color(0xFF6366F1).copy(alpha = 0.70f * liquidAlpha),
                                                ),
                                                startY = yTop,
                                                endY = yBottom,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (!isSplit) {
                            LiquidTriggerButton(
                                text = if (mode == AuthMode.SignIn) "Sign In" else "Create Account",
                                onClick = { triggerSplit() },
                                dark = dark,
                            )
                        } else {
                            if (mode == AuthMode.SignIn) {
                                LiquidCapsuleInput(
                                    value = emailOrUser,
                                    onValueChange = { input ->
                                        val sanitized = AuthSecurityUtils.sanitizeText(input, 60)
                                        emailOrUser = sanitized
                                        if (emailOrUserError != null) {
                                            emailOrUserError = AuthSecurityUtils.validateEmail(sanitized)
                                        }
                                    },
                                    placeholder = "Email Address",
                                    icon = Icons.Default.Email,
                                    errorMessage = emailOrUserError,
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next,
                                    onImeAction = {
                                        val sanitized = AuthSecurityUtils.sanitizeText(emailOrUser.trim(), 60)
                                        emailOrUserError = AuthSecurityUtils.validateEmail(sanitized)
                                        focusManager.moveFocus(FocusDirection.Down)
                                    },
                                    index = 0,
                                    animPos = animPos,
                                    animTime = animTime,
                                    dark = dark,
                                )

                                LiquidCapsuleInput(
                                    value = password,
                                    onValueChange = { input ->
                                        val sanitized = AuthSecurityUtils.sanitizePassword(input)
                                        password = sanitized
                                        if (passwordError != null) {
                                            passwordError = AuthSecurityUtils.validatePassword(sanitized)
                                        }
                                    },
                                    placeholder = "Password",
                                    icon = Icons.Default.Lock,
                                    errorMessage = passwordError,
                                    isPassword = true,
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                    onImeAction = {
                                        submitSignIn()
                                    },
                                    index = 1,
                                    animPos = animPos,
                                    animTime = animTime,
                                    dark = dark,
                                )

                                AnimatedVisibility(
                                    visible = serverErrorMessage != null,
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                ) {
                                    if (serverErrorMessage != null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (dark) Color(0xFF450A0A).copy(alpha = 0.7f) else Color(0xFFFEE2E2))
                                                .border(1.dp, if (dark) Color(0xFFDC2626).copy(alpha = 0.6f) else Color(0xFFF87171), RoundedCornerShape(14.dp))
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = serverErrorMessage ?: "",
                                                    color = if (dark) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                )
                                            }
                                        }
                                    }
                                }

                                LiquidTriggerButton(
                                    text = if (isLoading) "Signing in..." else "Complete Sign In →",
                                    onClick = { submitSignIn() },
                                    dark = dark,
                                    isLoading = isLoading,
                                    bringIntoViewRequester = null,
                                )
                            } else {
                                LiquidCapsuleInput(
                                    value = fullName,
                                    onValueChange = { input ->
                                        val sanitized = AuthSecurityUtils.sanitizeText(input, 50)
                                        fullName = sanitized
                                        if (fullNameError != null) {
                                            fullNameError = AuthSecurityUtils.validateFullName(sanitized)
                                        }
                                    },
                                    placeholder = "Full Name",
                                    icon = Icons.Default.Person,
                                    errorMessage = fullNameError,
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Next,
                                    onImeAction = {
                                        focusManager.moveFocus(FocusDirection.Down)
                                    },
                                    index = 0,
                                    animPos = animPos,
                                    animTime = animTime,
                                    dark = dark,
                                )

                                LiquidCapsuleInput(
                                    value = emailOrUser,
                                    onValueChange = { input ->
                                        val sanitized = AuthSecurityUtils.sanitizeText(input, 60)
                                        emailOrUser = sanitized
                                        if (emailOrUserError != null) {
                                            emailOrUserError = AuthSecurityUtils.validateEmail(sanitized)
                                        }
                                    },
                                    placeholder = "Email Address",
                                    icon = Icons.Default.Email,
                                    errorMessage = emailOrUserError,
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next,
                                    onImeAction = {
                                        val sanitized = AuthSecurityUtils.sanitizeText(emailOrUser.trim(), 60)
                                        emailOrUserError = AuthSecurityUtils.validateEmail(sanitized)
                                        focusManager.moveFocus(FocusDirection.Down)
                                    },
                                    index = 1,
                                    animPos = animPos,
                                    animTime = animTime,
                                    dark = dark,
                                )

                                LiquidCapsuleInput(
                                    value = phoneNumber,
                                    onValueChange = { input ->
                                        val sanitized = AuthSecurityUtils.sanitizePhone(input)
                                        phoneNumber = sanitized
                                        if (phoneNumberError != null) {
                                            phoneNumberError = AuthSecurityUtils.validatePhone(sanitized)
                                        }
                                    },
                                    placeholder = "Phone Number",
                                    icon = Icons.Default.Phone,
                                    errorMessage = phoneNumberError,
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Next,
                                    onImeAction = {
                                        focusManager.moveFocus(FocusDirection.Down)
                                    },
                                    index = 2,
                                    animPos = animPos,
                                    animTime = animTime,
                                    dark = dark,
                                )

                                LiquidCapsuleInput(
                                    value = password,
                                    onValueChange = { input ->
                                        val sanitized = AuthSecurityUtils.sanitizePassword(input)
                                        password = sanitized
                                        if (passwordError != null) {
                                            passwordError = AuthSecurityUtils.validatePassword(sanitized, requireComplexity = true)
                                        }
                                    },
                                    placeholder = "Password",
                                    icon = Icons.Default.Lock,
                                    errorMessage = passwordError,
                                    isPassword = true,
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                    onImeAction = {
                                        submitSignUp()
                                    },
                                    index = 3,
                                    animPos = animPos,
                                    animTime = animTime,
                                    dark = dark,
                                )

                                PasswordRequirementsReflection(
                                    password = password,
                                    dark = dark,
                                )

                                LiquidTriggerButton(
                                    text = "Complete Sign Up →",
                                    onClick = { submitSignUp() },
                                    dark = dark,
                                    bringIntoViewRequester = null,
                                )
                            }

                            Text(
                                text = "Cancel / Back",
                                color = scheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { resetState(mode) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )

                            Spacer(modifier = Modifier.height(120.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidModeSwitch(
    selectedMode: AuthMode,
    onModeSelected: (AuthMode) -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme

    val containerBg = if (dark) {
        Color(0xFF0F172A).copy(alpha = 0.85f)
    } else {
        Color(0xFFE2E8F0).copy(alpha = 0.80f)
    }

    val containerBorder = if (dark) {
        Color(0xFF334155).copy(alpha = 0.5f)
    } else {
        Color(0xFFCBD5E1)
    }

    Box(
        modifier = Modifier
            .clip(CapsuleShape)
            .background(containerBg)
            .border(1.dp, containerBorder, CapsuleShape)
            .padding(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeSwitchButton(
                text = "Sign In",
                isSelected = selectedMode == AuthMode.SignIn,
                onClick = { onModeSelected(AuthMode.SignIn) },
                dark = dark,
            )
            Spacer(modifier = Modifier.width(4.dp))
            ModeSwitchButton(
                text = "Create Account",
                isSelected = selectedMode == AuthMode.CreateAccount,
                onClick = { onModeSelected(AuthMode.CreateAccount) },
                dark = dark,
            )
        }
    }
}

@Composable
private fun ModeSwitchButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme

    val activeBg = if (dark) {
        Color(0xFF1E293B)
    } else {
        Color.White
    }

    val activeText = if (dark) Color.White else scheme.primary
    val inactiveText = scheme.onSurfaceVariant

    val shape = CapsuleShape
    val borderModifier = if (isSelected) {
        Modifier.border(
            1.dp,
            if (dark) Color.White.copy(alpha = 0.15f) else scheme.primary.copy(alpha = 0.2f),
            shape,
        )
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .clip(shape)
            .then(if (isSelected) Modifier.background(activeBg) else Modifier)
            .then(borderModifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (isSelected) activeText else inactiveText,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            ),
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LiquidCapsuleInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    errorMessage: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    onFocused: () -> Unit = {},
    index: Int,
    animPos: Float,
    animTime: Float,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    var isFocused by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    val decay = (18f - animTime * 12f).coerceAtLeast(0f)
    val yOffset = (sin(animPos * 0.15f + index * 0.5f) * decay).dp.value

    val capsuleBrush = if (dark) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF1E293B),
                Color(0xFF0F172A),
            ),
            start = Offset(0f, 0f),
            end = Offset(400f, 400f),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White,
                Color(0xFFF1F5F9),
            ),
            start = Offset(0f, 0f),
            end = Offset(400f, 400f),
        )
    }

    val rimBorder = if (errorMessage != null) {
        Brush.linearGradient(
            listOf(
                Color(0xFFEF4444),
                Color(0xFFF43F5E),
            ),
        )
    } else if (isFocused) {
        Brush.linearGradient(
            listOf(
                if (dark) Color(0xFF38BDF8) else Color(0xFF6366F1),
                if (dark) Color(0xFF818CF8) else Color(0xFF38BDF8),
            ),
        )
    } else if (dark) {
        Brush.linearGradient(
            listOf(
                Color(0xFF38BDF8).copy(alpha = 0.45f),
                Color(0xFF334155),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFF6366F1).copy(alpha = 0.40f),
                Color(0xFFCBD5E1),
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = yOffset
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(CapsuleShape)
                .background(capsuleBrush)
                .border(if (errorMessage != null || isFocused) 1.5.dp else 1.dp, rimBorder, CapsuleShape)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (errorMessage != null) Icons.Default.Warning else icon,
                    contentDescription = null,
                    tint = if (errorMessage != null) {
                        Color(0xFFEF4444)
                    } else if (isFocused) {
                        if (dark) scheme.secondary else scheme.primary
                    } else {
                        scheme.onSurfaceVariant.copy(alpha = 0.8f)
                    },
                    modifier = Modifier.size(20.dp),
                )

                Spacer(modifier = Modifier.width(12.dp))

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    visualTransformation = if (isPassword && !passwordVisible) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = imeAction,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { onImeAction() },
                        onDone = { onImeAction() },
                        onGo = { onImeAction() },
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = scheme.onBackground,
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(if (errorMessage != null) Color(0xFFEF4444) else if (dark) scheme.secondary else scheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focusState ->
                            isFocused = focusState.isFocused
                            if (focusState.isFocused) {
                                onFocused()
                            }
                        },
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = scheme.onSurfaceVariant.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        innerTextField()
                    },
                )

                if (isPassword && value.isNotEmpty()) {
                    Text(
                        text = if (passwordVisible) "HIDE" else "SHOW",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (dark) scheme.secondary else scheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { passwordVisible = !passwordVisible }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            if (errorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 4.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = errorMessage,
                        color = if (dark) Color(0xFFFCA5A5) else Color(0xFFDC2626),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LiquidTriggerButton(
    text: String,
    onClick: () -> Unit,
    dark: Boolean,
    isLoading: Boolean = false,
    bringIntoViewRequester: BringIntoViewRequester? = null,
) {
    val buttonBrush = if (dark) {
        Brush.linearGradient(
            listOf(
                Color(0xFFF1F5F9),
                Color(0xFF94A3B8),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFF4F46E5),
                Color(0xFF0284C7),
            ),
        )
    }

    val textColor = if (dark) Color(0xFF0F172A) else Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .then(
                if (bringIntoViewRequester != null)
                    Modifier.bringIntoViewRequester(bringIntoViewRequester)
                else Modifier
            )
            .clip(CapsuleShape)
            .background(buttonBrush)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !isLoading,
                onClick = onClick,
            )
            .drawBehind {
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) 0.8f else 0.4f),
                            Color.Transparent,
                        ),
                    ),
                    size = Size(size.width, 2.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(999.dp.toPx()),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            androidx.compose.material3.CircularProgressIndicator(
                color = textColor,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
            )
        } else {
            Text(
                text = text,
                color = textColor,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@Composable
private fun PasswordRequirementsReflection(
    password: String,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val satisfiedCount = AuthSecurityUtils.PASSWORD_RULES.count { it.isSatisfied(password) }
    val totalCount = AuthSecurityUtils.PASSWORD_RULES.size

    val strengthColor = when {
        satisfiedCount <= 1 -> Color(0xFFEF4444)
        satisfiedCount in 2..3 -> Color(0xFFF59E0B)
        satisfiedCount == 4 -> Color(0xFF38BDF8)
        else -> Color(0xFF10B981)
    }

    val strengthLabel = when {
        password.isEmpty() -> "Requirements"
        satisfiedCount <= 1 -> "Weak"
        satisfiedCount in 2..3 -> "Moderate"
        satisfiedCount == 4 -> "Good"
        else -> "Strong Password"
    }

    val containerBg = if (dark) {
        Color(0xFF0F172A).copy(alpha = 0.65f)
    } else {
        Color(0xFFF1F5F9).copy(alpha = 0.90f)
    }

    val containerBorder = if (dark) {
        Color(0xFF334155).copy(alpha = 0.5f)
    } else {
        Color(0xFFCBD5E1)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerBg)
            .border(1.dp, containerBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Security Checklist",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                    ),
                )

                Text(
                    text = strengthLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (password.isEmpty()) scheme.onSurfaceVariant else strengthColor,
                    ),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (i in 0 until totalCount) {
                    val isSegmentFilled = i < satisfiedCount
                    val segmentColor = if (isSegmentFilled) strengthColor else if (dark) Color(0xFF334155) else Color(0xFFE2E8F0)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(segmentColor),
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AuthSecurityUtils.PASSWORD_RULES.forEach { rule ->
                    val met = rule.isSatisfied(password)
                    val ruleColor = if (met) {
                        if (dark) Color(0xFF34D399) else Color(0xFF059669)
                    } else {
                        scheme.onSurfaceVariant.copy(alpha = 0.75f)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (met) {
                                        ruleColor.copy(alpha = 0.2f)
                                    } else if (dark) {
                                        Color(0xFF1E293B)
                                    } else {
                                        Color(0xFFE2E8F0)
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (met) ruleColor else if (dark) Color(0xFF475569) else Color(0xFFCBD5E1),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (met) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = ruleColor,
                                    modifier = Modifier.size(10.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = rule.title,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = if (met) FontWeight.SemiBold else FontWeight.Normal,
                                color = ruleColor,
                            ),
                        )
                    }
                }
            }
        }
    }
}

