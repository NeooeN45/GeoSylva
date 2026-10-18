package com.forestry.counter.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.data.remote.identity.GoogleCredentialClient
import com.forestry.counter.domain.model.AccountSession
import com.forestry.counter.domain.model.AccountProfile
import com.forestry.counter.domain.model.AccountActionCode
import com.forestry.counter.domain.model.ApiDiagnostic
import com.forestry.counter.domain.model.IdentityClientException
import com.forestry.counter.domain.model.IdentityError
import com.forestry.counter.domain.model.LoginOutcome
import com.forestry.counter.domain.model.ProviderCapability
import com.forestry.counter.domain.model.ParcelSyncPullResult
import com.forestry.counter.domain.model.ParcelSyncSummary
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.IdentitySessionGuard
import com.forestry.counter.domain.repository.ParcelSyncRepository
import com.forestry.counter.presentation.account.AccountInputValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginMode { SIGN_IN, REGISTER }
enum class RegistrationStep { EMAIL, PASSWORD, PROFILE }
enum class LoginInputIssue { EMAIL, PASSWORD_LENGTH, PASSWORD_CONFIRMATION, DISPLAY_NAME }

data class LoginUiState(
    val mode: LoginMode = LoginMode.SIGN_IN,
    val registrationStep: RegistrationStep = RegistrationStep.EMAIL,
    val inputIssue: LoginInputIssue? = null,
    val email: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val displayName: String = "",
    val providers: List<ProviderCapability> = emptyList(),
    val isLoadingProviders: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: IdentityError? = null,
    val completed: Boolean = false,
    val mfaChallengeToken: String? = null,
    val mfaCode: String = "",
    val mfaUsesRecoveryCode: Boolean = false,
    val mfaSetupRequired: Boolean = false,
) {
    val isAwaitingMfa: Boolean get() = mfaChallengeToken != null
}

class LoginViewModel(private val repository: IdentityRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    private var providersRequestRunning = false

    init { reloadProviders() }

    fun setMode(mode: LoginMode) = updateInput {
        copy(mode = mode, registrationStep = RegistrationStep.EMAIL, inputIssue = null,
            password = "", passwordConfirmation = "", error = null, completed = false,
            mfaChallengeToken = null, mfaCode = "", mfaUsesRecoveryCode = false,
            mfaSetupRequired = false)
    }

    fun setEmail(value: String) = updateInput { copy(email = value, error = null, inputIssue = null) }
    fun setPassword(value: String) = updateInput { copy(password = value, error = null, inputIssue = null) }
    fun setPasswordConfirmation(value: String) = updateInput {
        copy(passwordConfirmation = value, error = null, inputIssue = null)
    }
    fun setDisplayName(value: String) = updateInput { copy(displayName = value, error = null, inputIssue = null) }

    fun continueRegistration() {
        val state = _uiState.value
        if (state.isSubmitting || state.mode != LoginMode.REGISTER) return
        val issue = when (state.registrationStep) {
            RegistrationStep.EMAIL -> emailIssue(state)
            RegistrationStep.PASSWORD -> passwordIssue(state)
            RegistrationStep.PROFILE -> return
        }
        if (issue != null) { showInputIssue(issue); return }
        _uiState.update { it.copy(registrationStep = if (state.registrationStep == RegistrationStep.EMAIL)
            RegistrationStep.PASSWORD else RegistrationStep.PROFILE, inputIssue = null, error = null) }
    }

    fun previousRegistrationStep() = updateInput {
        copy(registrationStep = when (registrationStep) {
            RegistrationStep.EMAIL -> RegistrationStep.EMAIL
            RegistrationStep.PASSWORD -> RegistrationStep.EMAIL
            RegistrationStep.PROFILE -> RegistrationStep.PASSWORD
        }, inputIssue = null, error = null)
    }

    fun reloadProviders() {
        if (providersRequestRunning) return
        providersRequestRunning = true
        _uiState.update { it.copy(isLoadingProviders = true) }
        viewModelScope.launch {
            val providers = repository.getProviders().getOrDefault(emptyList())
            // Une erreur de catalogue ne remplace jamais l'erreur d'authentification.
            _uiState.update { it.copy(providers = providers, isLoadingProviders = false) }
            providersRequestRunning = false
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting || state.isAwaitingMfa || state.mfaSetupRequired || state.completed) return
        if (state.mode == LoginMode.REGISTER && state.registrationStep != RegistrationStep.PROFILE) return
        val issue = emailIssue(state) ?: if (state.mode == LoginMode.REGISTER) {
            passwordIssue(state) ?: if (!AccountInputValidator.isValidDisplayName(state.displayName))
                LoginInputIssue.DISPLAY_NAME else null
        } else if (state.password.isEmpty() || state.password.length > 128) LoginInputIssue.PASSWORD_LENGTH else null
        if (issue != null) { showInputIssue(issue); return }
        if (!beginSubmission()) return
        viewModelScope.launch {
            if (state.mode == LoginMode.SIGN_IN) {
                completeLogin(repository.loginWithPassword(state.email.trim(), state.password))
            } else {
                repository.register(state.email.trim(), state.password,
                    state.displayName.trim().takeIf(String::isNotEmpty)).fold(
                    onSuccess = { completeLogin(Result.success(LoginOutcome.Authenticated(it))) },
                    onFailure = { finishWith(it.identityError()) },
                )
            }
        }
    }

    fun setMfaCode(value: String) = updateInput {
        copy(mfaCode = cleanMfaCode(value, mfaUsesRecoveryCode), error = null)
    }
    fun setMfaUsesRecoveryCode(value: Boolean) = updateInput {
        copy(mfaUsesRecoveryCode = value, mfaCode = "", error = null)
    }
    fun cancelMfa() = updateInput {
        copy(mfaChallengeToken = null, mfaCode = "", error = null, mfaUsesRecoveryCode = false, mfaSetupRequired = false)
    }
    fun submitMfa() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val challenge = state.mfaChallengeToken ?: return
        if (!validMfaCode(state.mfaCode, state.mfaUsesRecoveryCode)) {
            _uiState.update { it.copy(error = IdentityError.INVALID_INPUT) }; return
        }
        if (!beginSubmission()) return
        viewModelScope.launch {
            completeLogin(repository.completeMfaLogin(challenge, state.mfaCode, state.mfaUsesRecoveryCode))
        }
    }
    fun signInWithGoogle(client: GoogleCredentialClient) {
        val state = _uiState.value
        if (state.isAwaitingMfa || state.mfaSetupRequired || state.completed || !beginSubmission()) return
        viewModelScope.launch {
            val nonce = repository.requestGoogleNonce().getOrElse {
                finishWith(it.identityError()); return@launch
            }
            val token = client.requestIdToken(nonce.value).getOrElse {
                finishWith(it.identityError().takeUnless { error -> error == IdentityError.CANCELLED })
                return@launch
            }
            completeLogin(repository.loginWithGoogle(token, nonce.value))
        }
    }
    private fun completeLogin(result: Result<LoginOutcome>) = result.fold(
        onSuccess = { outcome ->
            _uiState.update { state ->
                val cleared = state.copy(isSubmitting = false, error = null, inputIssue = null,
                    password = "", passwordConfirmation = "", mfaCode = "", mfaChallengeToken = null,
                    mfaSetupRequired = false)
                when (outcome) {
                    is LoginOutcome.Authenticated -> cleared.copy(completed = true)
                    is LoginOutcome.MfaRequired -> cleared.copy(mfaChallengeToken = outcome.challengeToken)
                    is LoginOutcome.MfaSetupRequired -> cleared.copy(mfaSetupRequired = true)
                }
            }
        },
        onFailure = { finishWith(it.identityError()) },
    )
    private fun beginSubmission(): Boolean {
        while (true) {
            val state = _uiState.value
            if (state.isSubmitting) return false
            if (_uiState.compareAndSet(state, state.copy(isSubmitting = true, error = null, inputIssue = null))) return true
        }
    }
    private fun finishWith(error: IdentityError?) { _uiState.update { it.copy(isSubmitting = false, error = error) } }
    private fun emailIssue(state: LoginUiState): LoginInputIssue? =
        if (AccountInputValidator.isValidEmail(state.email)) null else LoginInputIssue.EMAIL
    private fun passwordIssue(state: LoginUiState): LoginInputIssue? = when {
        state.password.length !in 12..128 -> LoginInputIssue.PASSWORD_LENGTH
        state.password != state.passwordConfirmation -> LoginInputIssue.PASSWORD_CONFIRMATION
        else -> null
    }
    private fun showInputIssue(issue: LoginInputIssue) {
        _uiState.update { it.copy(inputIssue = issue, error = IdentityError.INVALID_INPUT) }
    }
    private fun updateInput(transform: LoginUiState.() -> LoginUiState) {
        _uiState.update { if (it.isSubmitting) it else transform(it) }
    }
}

private fun cleanMfaCode(value: String, recovery: Boolean): String = if (recovery)
    value.filter { it.isLetterOrDigit() || it == '-' }.take(20) else value.filter { it in '0'..'9' }.take(6)
private fun validMfaCode(code: String, recovery: Boolean): Boolean =
    if (recovery) code.length in 6..20 else code.length == 6 && code.all { it in '0'..'9' }

data class AccountUiState(
    val session: AccountSession? = null,
    val profile: AccountProfile? = null,
    val providers: List<ProviderCapability> = emptyList(),
    val isLoadingProviders: Boolean = true,
    val isRefreshingProfile: Boolean = false,
    val isLoggingOut: Boolean = false,
    val isWorking: Boolean = false,
    val verificationCode: String = "",
    val notice: AccountNotice? = null,
    val error: IdentityError? = null,
    val mfaChallengeToken: String? = null,
    val mfaCode: String = "",
    val mfaUsesRecoveryCode: Boolean = false,
    val mfaSetupRequired: Boolean = false,
)

enum class AccountNotice { PROFILE_UPDATED, VERIFICATION_SENT, EMAIL_VERIFIED, GOOGLE_LINKED }

class AccountViewModel(private val repository: IdentityRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountUiState(session = repository.session.value,
        profile = repository.profile.value?.takeIf { it.accountId == repository.session.value?.accountId }))
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()
    private var providersRequestRunning = false
    private var operationSequence = 0L
    private var accountGeneration = 0L
    private var loginGeneration = 0L
    private var profileRefreshSequence = 0L
    private var profileRefreshGuard: IdentitySessionGuard? = null

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                val previousSession = _uiState.value.session
                if (previousSession?.accountId != session?.accountId) {
                    if (session != null) loginGeneration++
                    accountGeneration++
                    operationSequence++
                    profileRefreshSequence++
                    profileRefreshGuard = null
                    _uiState.update { it.copy(session = session, profile = null, isWorking = false,
                        isRefreshingProfile = false, verificationCode = "", notice = null, error = null,
                        mfaChallengeToken = null, mfaCode = "", mfaSetupRequired = false) }
                } else {
                    val replacementIsValid = session != null && session != previousSession && !session.isExpired()
                    val profileRefreshIsStale = profileRefreshGuard?.let {
                        !repository.isSessionGuardCurrent(it) && !repository.isSessionGuardRenewalCurrent(it)
                    } == true
                    if (profileRefreshIsStale) {
                        profileRefreshSequence++
                        profileRefreshGuard = null
                    }
                    _uiState.update { it.copy(
                        session = session,
                        isRefreshingProfile = if (profileRefreshIsStale) false else it.isRefreshingProfile,
                        error = if (replacementIsValid && it.error == IdentityError.INVALID_CREDENTIALS) null else it.error,
                    ) }
                }
            }
        }
        viewModelScope.launch {
            repository.profile.collect { profile ->
                _uiState.update { it.copy(profile = profile?.takeIf { candidate ->
                    candidate.accountId == repository.session.value?.accountId }) }
            }
        }
        if (repository.session.value != null) refreshProfile()
        reloadProviders()
    }
    fun setVerificationCode(value: String) {
        _uiState.update { if (it.isWorking || it.isLoggingOut) it else
            it.copy(verificationCode = AccountActionCode.normalize(value) ?: value.take(9), error = null, notice = null) }
    }
    fun refreshAccount() { refreshProfile(); reloadProviders() }
    fun refreshProfile() {
        val accountId = repository.session.value?.accountId ?: return
        val state = _uiState.value
        if (state.isRefreshingProfile || state.isWorking || state.isLoggingOut || state.mfaChallengeToken != null) return
        val expected = repository.captureSessionGuard(accountId).getOrElse { error ->
            _uiState.update { it.copy(error = error.identityError()) }
            return
        }
        val generation = accountGeneration
        val requestSequence = ++profileRefreshSequence
        profileRefreshGuard = expected
        _uiState.update { it.copy(isRefreshingProfile = true) }
        viewModelScope.launch {
            val guardedResult = repository.loadProfile(expected)
            if (requestSequence != profileRefreshSequence || generation != accountGeneration ||
                repository.session.value?.accountId != accountId) return@launch
            profileRefreshGuard = null
            // Un ancien /me, même pour A, ne peut pas réafficher son erreur après une reconnexion A.
            if (!repository.isSessionGuardCurrent(guardedResult.sessionGuard)) {
                _uiState.update { it.copy(isRefreshingProfile = false) }
                return@launch
            }
            _uiState.update { it.copy(isRefreshingProfile = false,
                error = guardedResult.result.exceptionOrNull()?.identityError()) }
        }
    }
    fun updateDisplayName(displayName: String?) {
        if (!AccountInputValidator.isValidDisplayName(displayName.orEmpty())) {
            _uiState.update { it.copy(error = IdentityError.INVALID_INPUT) }; return
        }
        runAccountAction({ repository.updateDisplayName(displayName?.trim()?.takeIf(String::isNotEmpty)).map { Unit } },
            AccountNotice.PROFILE_UPDATED)
    }
    fun requestEmailVerification() = runAccountAction(repository::requestEmailVerification, AccountNotice.VERIFICATION_SENT)
    fun confirmEmailVerification() {
        val code = _uiState.value.verificationCode
        if (!AccountInputValidator.isValidActionCode(code)) {
            _uiState.update { it.copy(error = IdentityError.INVALID_INPUT) }; return
        }
        runAccountAction({ repository.confirmEmailVerification(code).map { Unit } }, AccountNotice.EMAIL_VERIFIED)
    }
    fun reloadProviders() {
        if (providersRequestRunning) return
        providersRequestRunning = true
        _uiState.update { it.copy(isLoadingProviders = true) }
        viewModelScope.launch {
            val providers = repository.getProviders().getOrDefault(emptyList())
            _uiState.update { it.copy(providers = providers, isLoadingProviders = false) }
            providersRequestRunning = false
        }
    }
    fun linkGoogle(client: GoogleCredentialClient) {
        val accountId = repository.session.value?.accountId ?: return
        val operation = beginWork() ?: return
        val generation = accountGeneration
        viewModelScope.launch {
            val nonce = repository.requestGoogleNonce().getOrElse {
                finishOperation(operation, it.identityError()); return@launch
            }
            val token = client.requestIdToken(nonce.value).getOrElse {
                finishOperation(operation, it.identityError().takeUnless { error -> error == IdentityError.CANCELLED })
                return@launch
            }
            // Le sélecteur Google peut rester ouvert après une déconnexion.
            if (generation != accountGeneration || operation != operationSequence ||
                repository.session.value?.accountId != accountId) return@launch
            completeLink(operation, repository.linkGoogle(token, nonce.value))
        }
    }
    fun setMfaCode(value: String) { _uiState.update { if (it.isWorking || it.isLoggingOut) it else
        it.copy(mfaCode = cleanMfaCode(value, it.mfaUsesRecoveryCode), error = null) } }
    fun setMfaUsesRecoveryCode(value: Boolean) { _uiState.update { if (it.isWorking || it.isLoggingOut) it else
        it.copy(mfaUsesRecoveryCode = value, mfaCode = "", error = null) } }
    fun cancelMfa() { _uiState.update { if (it.isWorking || it.isLoggingOut) it else
        it.copy(mfaChallengeToken = null, mfaCode = "", mfaUsesRecoveryCode = false, mfaSetupRequired = false, error = null) } }
    fun submitMfa() {
        val state = _uiState.value
        if (state.isWorking || state.isLoggingOut) return
        val challenge = state.mfaChallengeToken ?: return
        if (!validMfaCode(state.mfaCode, state.mfaUsesRecoveryCode)) {
            _uiState.update { it.copy(error = IdentityError.INVALID_INPUT) }; return
        }
        val operation = beginWork(allowMfa = true) ?: return
        viewModelScope.launch { completeLink(operation,
            repository.completeMfaLogin(challenge, state.mfaCode, state.mfaUsesRecoveryCode)) }
    }
    private fun completeLink(operation: Long, result: Result<LoginOutcome>) {
        if (operation != operationSequence) return
        result.fold(onSuccess = { outcome ->
            _uiState.update { state ->
                val cleared = state.copy(isWorking = false, error = null, notice = null,
                    mfaChallengeToken = null, mfaCode = "", mfaSetupRequired = false)
                when (outcome) {
                    is LoginOutcome.Authenticated -> cleared.copy(notice = AccountNotice.GOOGLE_LINKED)
                    is LoginOutcome.MfaRequired -> cleared.copy(mfaChallengeToken = outcome.challengeToken)
                    is LoginOutcome.MfaSetupRequired -> cleared.copy(mfaSetupRequired = true)
                }
            }
            if (outcome is LoginOutcome.Authenticated) refreshProfile()
        }, onFailure = { finishOperation(operation, it.identityError()) })
    }
    fun logout() {
        if (_uiState.value.isLoggingOut) return
        val generation = loginGeneration
        operationSequence++
        _uiState.update { it.copy(isLoggingOut = true, isWorking = false, error = null, notice = null,
            mfaChallengeToken = null, mfaCode = "", mfaSetupRequired = false) }
        viewModelScope.launch {
            val result = repository.logout()
            _uiState.update { it.copy(isLoggingOut = false,
                error = if (generation == loginGeneration && repository.session.value == null)
                    result.exceptionOrNull()?.identityError() else it.error) }
        }
    }
    private fun beginWork(allowMfa: Boolean = false): Long? {
        val state = _uiState.value
        if (repository.session.value == null || state.isWorking || state.isLoggingOut || state.isRefreshingProfile ||
            (!allowMfa && (state.mfaChallengeToken != null || state.mfaSetupRequired))) return null
        operationSequence++
        _uiState.update { it.copy(isWorking = true, error = null, notice = null) }
        return operationSequence
    }
    private fun finishOperation(operation: Long, error: IdentityError?) {
        if (operation == operationSequence) _uiState.update { it.copy(isWorking = false, error = error) }
    }
    private fun runAccountAction(action: suspend () -> Result<Unit>, notice: AccountNotice) {
        val operation = beginWork() ?: return
        viewModelScope.launch {
            action().fold(onSuccess = {
                if (operation == operationSequence) _uiState.update { it.copy(isWorking = false, notice = notice,
                    verificationCode = if (notice == AccountNotice.EMAIL_VERIFIED) "" else it.verificationCode) }
            }, onFailure = { finishOperation(operation, it.identityError()) })
        }
    }
}

enum class RecoveryStep {
    REQUEST_CODE,
    CONFIRM_RESET,
}

data class PasswordRecoveryUiState(
    val step: RecoveryStep = RecoveryStep.REQUEST_CODE,
    val email: String = "",
    val code: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val isSubmitting: Boolean = false,
    val completed: Boolean = false,
    val error: IdentityError? = null,
)

class PasswordRecoveryViewModel(private val repository: IdentityRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(PasswordRecoveryUiState())
    val uiState: StateFlow<PasswordRecoveryUiState> = _uiState.asStateFlow()

    fun setEmail(value: String) = update { copy(email = value, error = null) }
    fun setCode(value: String) = update {
        copy(code = AccountActionCode.normalize(value) ?: value.take(9), error = null)
    }
    fun setPassword(value: String) = update { copy(password = value, error = null) }
    fun setPasswordConfirmation(value: String) = update {
        copy(passwordConfirmation = value, error = null)
    }

    fun requestCode() {
        if (_uiState.value.isSubmitting) return
        val email = _uiState.value.email
        if (!AccountInputValidator.isValidEmail(email)) {
            update { copy(error = IdentityError.INVALID_INPUT) }
            return
        }
        submit(action = { repository.requestPasswordReset(email) }) { state ->
            state.copy(step = RecoveryStep.CONFIRM_RESET)
        }
    }

    fun confirmReset() {
        val state = _uiState.value
        if (state.isSubmitting) return
        if (
            !AccountInputValidator.isValidActionCode(state.code) ||
            !AccountInputValidator.isValidPasswordReset(
                state.password,
                state.passwordConfirmation,
            )
        ) {
            update { copy(error = IdentityError.INVALID_INPUT) }
            return
        }
        submit(
            action = {
                repository.confirmPasswordReset(state.email, state.code, state.password)
            }
        ) { current -> current.copy(completed = true, password = "", passwordConfirmation = "", code = "") }
    }

    fun restart() = update {
        copy(
            step = RecoveryStep.REQUEST_CODE,
            code = "",
            password = "",
            passwordConfirmation = "",
            error = null,
        )
    }

    private fun submit(
        action: suspend () -> Result<Unit>,
        onSuccess: (PasswordRecoveryUiState) -> PasswordRecoveryUiState,
    ) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            action().fold(
                onSuccess = { _uiState.update { onSuccess(it).copy(isSubmitting = false) } },
                onFailure = { error ->
                    _uiState.update { it.copy(isSubmitting = false, error = error.identityError()) }
                },
            )
        }
    }

    private fun update(transform: PasswordRecoveryUiState.() -> PasswordRecoveryUiState) {
        _uiState.update { if (it.isSubmitting) it else transform(it) }
    }
}

data class DeveloperOptionsUiState(
    val developerModeEnabled: Boolean? = null,
    val session: AccountSession? = null,
    val diagnostic: ApiDiagnostic? = null,
    val providers: List<ProviderCapability> = emptyList(),
    val isRefreshing: Boolean = false,
    val parcelSync: ParcelSyncSummary = ParcelSyncSummary(),
    val isPullingParcels: Boolean = false,
    val pullResult: ParcelSyncPullResult? = null,
    val pullError: Boolean = false,
)

class DeveloperOptionsViewModel(
    private val repository: IdentityRepository,
    private val preferences: UserPreferencesManager,
    private val parcelSyncRepository: ParcelSyncRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        DeveloperOptionsUiState(session = repository.session.value)
    )
    val uiState: StateFlow<DeveloperOptionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                _uiState.update { it.copy(session = session) }
            }
        }
        viewModelScope.launch {
            preferences.developerModeEnabled.collect { enabled ->
                _uiState.update { it.copy(developerModeEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            parcelSyncRepository.observeSummary().collect { summary ->
                _uiState.update { it.copy(parcelSync = summary) }
            }
        }
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val diagnostic = repository.diagnoseApi()
            val providers = repository.getProviders().getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    diagnostic = diagnostic,
                    providers = providers,
                    isRefreshing = false,
                )
            }
        }
    }

    fun disableDeveloperMode() {
        viewModelScope.launch { preferences.setDeveloperModeEnabled(false) }
    }

    fun pullParcels() {
        if (_uiState.value.isPullingParcels) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isPullingParcels = true, pullResult = null, pullError = false)
            }
            parcelSyncRepository.pull().fold(
                onSuccess = { result ->
                    _uiState.update {
                        it.copy(isPullingParcels = false, pullResult = result)
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(isPullingParcels = false, pullError = true)
                    }
                },
            )
        }
    }
}

class GeoSylvaViewModelFactory<T : ViewModel>(
    private val create: () -> T,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
}

private fun Throwable.identityError(): IdentityError =
    (this as? IdentityClientException)?.error ?: IdentityError.UNKNOWN
