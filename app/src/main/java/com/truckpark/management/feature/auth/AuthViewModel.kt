package com.truckpark.management.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.truckpark.management.app.TruckParkApplication
import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.domain.models.AuthUser
import com.truckpark.management.domain.repositories.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Authenticated(val user: AuthUser) : AuthUiState
    data class AdminSecuritySetup(
        val user: AuthUser,
        val passwordChanged: Boolean = false,
        val emailVerified: Boolean = false,
    ) : AuthUiState
    data class Error(val message: String, val code: String? = null) : AuthUiState
}

class AuthViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(
        if (authRepository.hasStoredSession()) AuthUiState.Loading else AuthUiState.Idle,
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        // If a valid session is already saved in Keystore, silently validate it
        if (authRepository.hasStoredSession()) {
            checkCurrentSession()
        }
    }

    fun checkCurrentSession() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = authRepository.currentUser()) {
                is AuthResult.Success -> {
                    val user = result.value
                    val isAdmin = user.roles.contains("ADMIN")
                    if (isAdmin && (!user.emailVerified || user.mustChangePassword)) {
                        _uiState.value = AuthUiState.AdminSecuritySetup(
                            user = user,
                            emailVerified = user.emailVerified,
                            passwordChanged = !user.mustChangePassword,
                        )
                    } else {
                        _uiState.value = AuthUiState.Authenticated(user)
                    }
                }
                is AuthResult.Failure -> {
                    // Session expired or revoked: return to Idle sign-in
                    _uiState.value = AuthUiState.Idle
                }
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = authRepository.signIn(email, password)) {
                is AuthResult.Success -> {
                    val user = result.value.user
                    val isAdmin = user.roles.contains("ADMIN")
                    // An admin on the setup password or with an unverified email MUST pass security setup.
                    // The server decides via mustChangePassword; the app never compares passwords.
                    val needsSecurityGate = isAdmin && (!user.emailVerified || user.mustChangePassword)

                    if (needsSecurityGate) {
                        _uiState.value = AuthUiState.AdminSecuritySetup(
                            user = user,
                            emailVerified = user.emailVerified,
                            passwordChanged = !user.mustChangePassword,
                        )
                    } else {
                        _uiState.value = AuthUiState.Authenticated(user)
                    }
                }
                is AuthResult.Failure -> {
                    _uiState.value = AuthUiState.Error(
                        message = result.error.message,
                        code = result.error.code,
                    )
                }
            }
        }
    }

    fun changePassword(
        newEmail: String,
        newPassword: String,
        onComplete: (AuthResult<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = authRepository.changePassword(newEmail, newPassword)
            if (result is AuthResult.Success) {
                val current = _uiState.value
                if (current is AuthUiState.AdminSecuritySetup) {
                    // The new email is unverified until its code is confirmed.
                    _uiState.value = current.copy(
                        user = current.user.copy(
                            email = newEmail.trim().lowercase(),
                            emailVerified = false,
                            mustChangePassword = false,
                        ),
                        passwordChanged = true,
                        emailVerified = false,
                    )
                }
            }
            onComplete(result)
        }
    }

    fun sendVerificationCode(
        email: String? = null,
        onComplete: (AuthResult<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val current = _uiState.value
            val resolvedEmail = email?.trim()?.takeIf { it.isNotEmpty() }
                ?: (current as? AuthUiState.AdminSecuritySetup)?.user?.email
            val result = authRepository.sendVerificationCode(resolvedEmail)
            onComplete(result)
        }
    }

    fun requestSignUpVerification(
        fullName: String,
        email: String,
        phone: String,
        password: String,
        onComplete: (AuthResult<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = authRepository.requestSignUpVerification(fullName, email, phone, password)
            onComplete(result)
        }
    }

    fun resendSignUpVerificationCode(
        email: String,
        onComplete: (AuthResult<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = authRepository.resendSignUpVerificationCode(email)
            onComplete(result)
        }
    }

    fun verifySignUpEmail(
        email: String,
        code: String,
        onComplete: (AuthResult<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = authRepository.verifySignUpEmail(email, code)
            onComplete(result)
        }
    }

    fun verifyEmail(
        code: String,
        newEmail: String? = null,
        newPassword: String? = null,
        onComplete: (AuthResult<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val current = _uiState.value
            val userId = if (current is AuthUiState.AdminSecuritySetup) current.user.id else ""
            val result = if (!newEmail.isNullOrBlank() && !newPassword.isNullOrBlank()) {
                authRepository.verifyAndChangeAdminCredentials(
                    userId = userId,
                    newEmail = newEmail,
                    newPassword = newPassword,
                    code = code,
                )
            } else {
                authRepository.verifyEmail(userId, code)
            }
            if (result is AuthResult.Success) {
                if (current is AuthUiState.AdminSecuritySetup) {
                    val resolvedEmail = newEmail?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                        ?: current.user.email
                    val updatedUser = current.user.copy(
                        email = resolvedEmail,
                        emailVerified = true,
                        mustChangePassword = false,
                    )
                    _uiState.value = current.copy(
                        user = updatedUser,
                        passwordChanged = true,
                        emailVerified = true,
                    )
                }
            }
            onComplete(result)
        }
    }

    fun completeSecuritySetup() {
        val current = _uiState.value
        if (current is AuthUiState.AdminSecuritySetup) {
            val updatedUser = current.user.copy(
                mustChangePassword = false,
                emailVerified = true,
            )
            _uiState.value = AuthUiState.Authenticated(updatedUser)
        }
    }

    fun registerUser(fullName: String, email: String) {
        val user = AuthUser(
            id = "usr_" + System.currentTimeMillis(),
            fullName = fullName.ifBlank { "Operator" },
            email = email.ifBlank { "operator@hibiscuss.com" },
            status = "APPROVED",
            emailVerified = true,
            roles = listOf("OPERATOR"),
            permissions = setOf("READ", "WRITE"),
            mustChangePassword = false,
        )
        _uiState.value = AuthUiState.Authenticated(user)
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.value = AuthUiState.Idle
        }
    }

    fun clearError() {
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = AuthUiState.Idle
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruckParkApplication
                AuthViewModel(app.container.authRepository)
            }
        }
    }
}
