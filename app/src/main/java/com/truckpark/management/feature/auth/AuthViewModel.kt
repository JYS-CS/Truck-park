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
import com.truckpark.management.domain.models.ManagedDevice
import com.truckpark.management.domain.models.ManagedUser
import com.truckpark.management.domain.repositories.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    private val _uiState = MutableStateFlow(initialUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var autoLogoutJob: Job? = null

    init {
        if (authRepository.hasStoredSession()) {
            val hasCachedUser = authRepository.getCachedUser() != null
            if (hasCachedUser) {
                scheduleAutoLogout()
            }
            // Silently refresh role/permissions in the background when online;
            // if offline, keep the cached session active instead of logging out.
            checkCurrentSession(silent = hasCachedUser)
        }
    }

    private fun initialUiState(): AuthUiState {
        if (!authRepository.hasStoredSession()) return AuthUiState.Idle
        val cached = authRepository.getCachedUser()
        return if (cached != null) userStateFor(cached) else AuthUiState.Loading
    }

    private fun userStateFor(user: AuthUser): AuthUiState = if (user.isAdminSetupRequired) {
        AuthUiState.AdminSecuritySetup(
            user = user,
            emailVerified = user.emailVerified,
            passwordChanged = !user.mustChangePassword,
        )
    } else {
        AuthUiState.Authenticated(user)
    }

    private fun scheduleAutoLogout() {
        autoLogoutJob?.cancel()
        val remainingMillis = authRepository.getSessionRemainingMillis() ?: return
        if (remainingMillis <= 0L) {
            signOut()
            return
        }
        autoLogoutJob = viewModelScope.launch {
            delay(remainingMillis)
            signOut()
        }
    }

    fun checkCurrentSession(silent: Boolean = false) {
        viewModelScope.launch {
            val alreadySignedIn = _uiState.value is AuthUiState.Authenticated ||
                _uiState.value is AuthUiState.AdminSecuritySetup
            if (!silent && !alreadySignedIn) {
                _uiState.value = AuthUiState.Loading
            }
            when (val result = authRepository.currentUser()) {
                is AuthResult.Success -> {
                    _uiState.value = userStateFor(result.value)
                    scheduleAutoLogout()
                }
                is AuthResult.Failure -> {
                    val err = result.error
                    val isSessionInvalidated = err.endsSession ||
                        err.code == com.truckpark.management.core.common.AuthError.UNAUTHORIZED ||
                        err.code == com.truckpark.management.core.common.AuthError.NOT_SIGNED_IN ||
                        !authRepository.hasStoredSession()

                    when {
                        err.code == com.truckpark.management.core.common.AuthError.DEVICE_REVOKED -> {
                            autoLogoutJob?.cancel()
                            autoLogoutJob = null
                            _uiState.value = AuthUiState.Error(
                                message = err.message,
                                code = err.code,
                            )
                        }
                        !isSessionInvalidated -> {
                            // Offline / transient network or server error while session token is still valid:
                            // preserve active UI state or restore from encrypted cached user instead of logging out.
                            if (alreadySignedIn) {
                                scheduleAutoLogout()
                            } else {
                                val cached = authRepository.getCachedUser()
                                if (cached != null) {
                                    _uiState.value = userStateFor(cached)
                                    scheduleAutoLogout()
                                } else {
                                    _uiState.value = AuthUiState.Error(
                                        message = err.message,
                                        code = err.code,
                                    )
                                }
                            }
                        }
                        else -> {
                            autoLogoutJob?.cancel()
                            autoLogoutJob = null
                            _uiState.value = AuthUiState.Idle
                        }
                    }
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
                    // An account on the default setup credentials (mustChangePassword) or unverified admin
                    // MUST pass security setup. The backend decides via mustChangePassword / emailVerified.
                    if (user.isAdminSetupRequired) {
                        _uiState.value = AuthUiState.AdminSecuritySetup(
                            user = user,
                            emailVerified = user.emailVerified,
                            passwordChanged = !user.mustChangePassword,
                        )
                    } else {
                        _uiState.value = AuthUiState.Authenticated(user)
                    }
                    scheduleAutoLogout()
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
                ?: (current as? AuthUiState.Authenticated)?.user?.email
            val result = authRepository.sendVerificationCode(resolvedEmail)
            onComplete(result)
        }
    }

    fun verifyCurrentEmailCode(
        code: String,
        onComplete: (AuthResult<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val current = _uiState.value
            val userId = when (current) {
                is AuthUiState.Authenticated -> current.user.id
                is AuthUiState.AdminSecuritySetup -> current.user.id
                else -> ""
            }
            val result = authRepository.verifyEmail(userId, code)
            onComplete(result)
        }
    }

    fun updateUserProfile(
        newEmail: String,
        fullName: String,
        phone: String,
        currentPassword: String? = null,
        newPassword: String? = null,
        code: String? = null,
        onComplete: (AuthResult<AuthUser>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = authRepository.updateUserProfile(
                newEmail = newEmail,
                fullName = fullName,
                phone = phone,
                currentPassword = currentPassword,
                newPassword = newPassword,
                code = code,
            )
            if (result is AuthResult.Success) {
                val current = _uiState.value
                if (current is AuthUiState.Authenticated) {
                    _uiState.value = current.copy(user = result.value)
                } else if (current is AuthUiState.AdminSecuritySetup) {
                    _uiState.value = current.copy(user = result.value)
                }
            }
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

    fun requestPasswordReset(
        email: String,
        onComplete: (AuthResult<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = authRepository.requestPasswordReset(email)
            onComplete(result)
        }
    }

    fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
        onComplete: (AuthResult<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = authRepository.resetPassword(email, code, newPassword)
            onComplete(result)
        }
    }

    fun loadManagedUsers(
        onComplete: (AuthResult<List<ManagedUser>>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = authRepository.listUsers()
            onComplete(result)
        }
    }

    fun updateUserRole(
        userId: String,
        roleCode: String,
        onComplete: (AuthResult<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = authRepository.updateUserRole(userId, roleCode)
            val current = _uiState.value
            if (result is AuthResult.Success && current is AuthUiState.Authenticated && current.user.id == userId) {
                checkCurrentSession()
            }
            onComplete(result)
        }
    }

    fun deactivateUser(
        userId: String,
        onComplete: (AuthResult<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = authRepository.deactivateUser(userId)
            onComplete(result)
        }
    }

    fun loadManagedDevices(
        onComplete: (AuthResult<List<ManagedDevice>>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = authRepository.listDevices()
            onComplete(result)
        }
    }

    fun revokeDevice(
        deviceId: String,
        reason: String,
        onComplete: (AuthResult<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = authRepository.revokeDevice(deviceId, reason)
            onComplete(result)
        }
    }

    fun reinstateDevice(
        deviceId: String,
        onComplete: (AuthResult<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = authRepository.reinstateDevice(deviceId)
            onComplete(result)
        }
    }

    fun loadUserExceptions(
        userId: String,
        onComplete: (AuthResult<List<com.truckpark.management.domain.models.UserPermissionException>>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = authRepository.listUserExceptions(userId)
            onComplete(result)
        }
    }

    fun addUserException(
        userId: String,
        permissionCode: String,
        grantOrRemove: String,
        reason: String,
        expiresInHours: Int? = null,
        onComplete: (AuthResult<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = authRepository.addUserException(
                userId = userId,
                permissionCode = permissionCode,
                grantOrRemove = grantOrRemove,
                reason = reason,
                expiresInHours = expiresInHours,
            )
            val current = _uiState.value
            if (result is AuthResult.Success && current is AuthUiState.Authenticated && current.user.id == userId) {
                checkCurrentSession()
            }
            onComplete(result)
        }
    }

    fun revokeUserException(
        exceptionId: String,
        userId: String? = null,
        onComplete: (AuthResult<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = authRepository.revokeUserException(exceptionId)
            val current = _uiState.value
            if (result is AuthResult.Success && current is AuthUiState.Authenticated && current.user.id == userId) {
                checkCurrentSession()
            }
            onComplete(result)
        }
    }

    fun verifyEmail(
        code: String,
        newEmail: String? = null,
        newPassword: String? = null,
        fullName: String? = null,
        phone: String? = null,
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
                    fullName = fullName,
                    phone = phone,
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
                        fullName = fullName?.trim()?.takeIf { it.isNotEmpty() } ?: current.user.fullName,
                        phone = phone?.trim()?.takeIf { it.isNotEmpty() } ?: current.user.phone,
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
            scheduleAutoLogout()
        }
    }

    fun signOut() {
        autoLogoutJob?.cancel()
        autoLogoutJob = null
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.value = AuthUiState.Idle
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoLogoutJob?.cancel()
        autoLogoutJob = null
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
