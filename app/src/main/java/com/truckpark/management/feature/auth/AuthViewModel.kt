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
                    if (isAdmin && !user.emailVerified) {
                        _uiState.value = AuthUiState.AdminSecuritySetup(
                            user = user,
                            emailVerified = false,
                            passwordChanged = true, // already has active session
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
                    // An admin using the initial password or with unverified email MUST pass security setup
                    val isInitialPassword = password == "@Admin"
                    val needsSecurityGate = isAdmin && (!user.emailVerified || isInitialPassword)

                    if (needsSecurityGate) {
                        _uiState.value = AuthUiState.AdminSecuritySetup(
                            user = user,
                            emailVerified = user.emailVerified,
                            passwordChanged = !isInitialPassword,
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
        currentPassword: String,
        newPassword: String,
        onComplete: (AuthResult<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = authRepository.changePassword(currentPassword, newPassword)
            if (result is AuthResult.Success) {
                val current = _uiState.value
                if (current is AuthUiState.AdminSecuritySetup) {
                    _uiState.value = current.copy(passwordChanged = true)
                }
            }
            onComplete(result)
        }
    }

    fun sendVerificationCode(onComplete: (AuthResult<String?>) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.sendVerificationCode()
            onComplete(result)
        }
    }

    fun verifyEmail(code: String, onComplete: (AuthResult<Unit>) -> Unit) {
        viewModelScope.launch {
            val current = _uiState.value
            val userId = if (current is AuthUiState.AdminSecuritySetup) current.user.id else ""
            val result = authRepository.verifyEmail(userId, code)
            if (result is AuthResult.Success) {
                if (current is AuthUiState.AdminSecuritySetup) {
                    val updatedUser = current.user.copy(emailVerified = true)
                    _uiState.value = current.copy(
                        user = updatedUser,
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
            _uiState.value = AuthUiState.Authenticated(current.user)
        }
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
