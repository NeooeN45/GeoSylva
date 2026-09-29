package com.forestry.counter.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.data.remote.identity.GoogleCredentialClient
import com.forestry.counter.domain.model.AccountSession
import com.forestry.counter.domain.model.AccountProfile
import com.forestry.counter.domain.model.AccountConsent
import com.forestry.counter.domain.model.AccountConsentPolicy
import com.forestry.counter.domain.model.ApiDiagnostic
import com.forestry.counter.domain.model.IdentityClientException
import com.forestry.counter.domain.model.IdentityError
import com.forestry.counter.domain.model.LoginOutcome
import com.forestry.counter.domain.model.ProviderCapability
import com.forestry.counter.domain.model.ParcelSyncPullResult
import com.forestry.counter.domain.model.ParcelSyncSummary
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.ParcelSyncRepository
import com.forestry.counter.presentation.account.AccountInputValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginMode {
    SIGN_IN,
    REGISTER,
}

data class LoginUiState(
    val mode: LoginMode = LoginMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val displayName: String = "",
    val termsAccepted: Boolean = false,
    val privacyAccepted: Boolean = false,
    val providers: List<ProviderCapability> = emptyList(),
    val isLoadingProviders: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: IdentityError? = null,
    val completed: Boolean = false,
    /**
     * Jeton de défi renvoyé par le serveur quand le compte exige un second
     * facteur. Non nul ⇒ l'écran affiche la saisie du code au lieu du
     * formulaire e-mail/mot de passe.
     */
    val mfaChallengeToken: String? = null,
    val mfaCode: String = "",
    val mfaUsesRecoveryCode: Boolean = false,
    /** Compte administrateur sans MFA : la configuration se fait sur le web. */
    val mfaSetupRequired: Boolean = false,
) {
    val isAwaitingMfa: Boolean get() = mfaChallengeToken != null
}

class LoginViewModel(private val repository: IdentityRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        reloadProviders()
    }

    fun setMode(mode: LoginMode) {
        _uiState.update {
            it.copy(mode = mode, password = "", passwordConfirmation = "", error = null)
        }
    }

    fun setEmail(value: String) = updateInput { copy(email = value, error = null) }

    fun setPassword(value: String) = updateInput { copy(password = value, error = null) }

    fun setPasswordConfirmation(value: String) = updateInput {
        copy(passwordConfirmation = value, error = null)
    }

    fun setDisplayName(value: String) = updateInput { copy(displayName = value, error = null) }

    fun setTermsAccepted(value: Boolean) = updateInput {
        copy(termsAccepted = value, error = null)
    }

    fun setPrivacyAccepted(value: Boolean) = updateInput {
        copy(privacyAccepted = value, error = null)
    }

    fun reloadProviders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProviders = true, error = null) }
            repository.getProviders().fold(
                onSuccess = { providers ->
                    _uiState.update {
                        it.copy(providers = providers, isLoadingProviders = false)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingProviders = false,
                            error = error.identityError(),
                        )
                    }
                },
            )
        }
    }

    fun submit() {
        val state = _uiState.value
        val validationError = validate(state)
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            if (state.mode == LoginMode.SIGN_IN) {
                completeLogin(repository.loginWithPassword(state.email, state.password))
            } else {
                completeRegistration(state)
            }
        }
    }

    /**
     * Création puis enregistrement des consentements explicitement cochés.
     * Si un enregistrement échoue, les consentements déjà posés sont révoqués
     * et la session locale est fermée : l'application ne valide jamais une
     * inscription comme terminée sans son parcours juridique complet.
     */
    private suspend fun completeRegistration(state: LoginUiState) {
        val registration = repository.register(
            email = state.email,
            password = state.password,
            displayName = state.displayName,
        )
        val session = registration.getOrElse { error ->
            finishWith(error.identityError())
            return
        }
        val accepted = mutableListOf<String>()
        for (requirement in AccountConsentPolicy.required) {
            val result = repository.acceptConsent(requirement.type, requirement.documentVersion)
            if (result.isSuccess) {
                accepted += requirement.type
            } else {
                accepted.asReversed().forEach { repository.revokeConsent(it) }
                repository.logout()
                finishWith(
                    result.exceptionOrNull()?.identityError()
                        ?: IdentityError.UNKNOWN,
                )
                return
            }
        }
        completeAuthentication(Result.success(session))
    }

    fun setMfaCode(value: String) = updateInput {
        // Le TOTP fait six chiffres ; un code de récupération est alphanumérique
        // et plus long. On filtre selon le mode choisi plutôt qu'en aveugle.
        val cleaned = if (mfaUsesRecoveryCode) {
            value.filter { it.isLetterOrDigit() || it == '-' }.take(20)
        } else {
            value.filter(Char::isDigit).take(6)
        }
        copy(mfaCode = cleaned, error = null)
    }

    fun setMfaUsesRecoveryCode(value: Boolean) = updateInput {
        copy(mfaUsesRecoveryCode = value, mfaCode = "", error = null)
    }

    /** Abandonne le défi en cours et revient au formulaire de connexion. */
    fun cancelMfa() = updateInput {
        copy(mfaChallengeToken = null, mfaCode = "", error = null, mfaUsesRecoveryCode = false)
    }

    fun submitMfa() {
        val state = _uiState.value
        val challenge = state.mfaChallengeToken ?: return
        if (state.mfaCode.length < 6) {
            _uiState.update { it.copy(error = IdentityError.INVALID_INPUT) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            completeLogin(
                repository.completeMfaLogin(
                    challengeToken = challenge,
                    code = state.mfaCode,
                    isRecoveryCode = state.mfaUsesRecoveryCode,
                )
            )
        }
    }

    private fun completeLogin(result: Result<LoginOutcome>) {
        result.fold(
            onSuccess = { outcome ->
                when (outcome) {
                    is LoginOutcome.Authenticated -> _uiState.update {
                        it.copy(isSubmitting = false, error = null, completed = true)
                    }

                    is LoginOutcome.MfaRequired -> _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = null,
                            password = "",
                            mfaChallengeToken = outcome.challengeToken,
                            mfaCode = "",
                        )
                    }

                    // GeoSylva ne porte pas la configuration du second facteur :
                    // elle se fait depuis l'interface web, sur un vrai clavier.
                    is LoginOutcome.MfaSetupRequired -> _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            password = "",
                            mfaChallengeToken = null,
                            mfaSetupRequired = true,
                        )
                    }
                }
            },
            onFailure = { finishWith(it.identityError()) },
        )
    }

    fun signInWithGoogle(client: GoogleCredentialClient) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val nonce = repository.requestGoogleNonce().getOrElse { error ->
                finishWith(error.identityError())
                return@launch
            }
            val idToken = client.requestIdToken(nonce.value).getOrElse { error ->
                val identityError = error.identityError()
                finishWith(identityError.takeUnless { it == IdentityError.CANCELLED })
                return@launch
            }
            completeAuthentication(repository.loginWithGoogle(idToken, nonce.value))
        }
    }

    private fun completeAuthentication(result: Result<AccountSession>) {
        result.fold(
            onSuccess = {
                _uiState.update { state ->
                    state.copy(isSubmitting = false, error = null, completed = true)
                }
            },
            onFailure = { finishWith(it.identityError()) },
        )
    }

    private fun finishWith(error: IdentityError?) {
        _uiState.update { it.copy(isSubmitting = false, error = error) }
    }

    private fun validate(state: LoginUiState): IdentityError? {
        if (!EMAIL_PATTERN.matches(state.email.trim()) || state.password.isBlank()) {
            return IdentityError.INVALID_INPUT
        }
        if (state.mode == LoginMode.REGISTER) {
            if (state.password.length !in 12..128) return IdentityError.INVALID_INPUT
            if (state.password != state.passwordConfirmation) return IdentityError.INVALID_INPUT
            if (!state.termsAccepted || !state.privacyAccepted) {
                return IdentityError.REGISTRATION_CONSENT_REQUIRED
            }
        }
        return null
    }

    private fun updateInput(transform: LoginUiState.() -> LoginUiState) {
        _uiState.update(transform)
    }

    private companion object {
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

data class AccountUiState(
    val session: AccountSession? = null,
    val profile: AccountProfile? = null,
    val consents: List<AccountConsent> = emptyList(),
    val isLoadingConsents: Boolean = false,
    val providers: List<ProviderCapability> = emptyList(),
    val isLoadingProviders: Boolean = true,
    val isLoggingOut: Boolean = false,
    val isWorking: Boolean = false,
    val verificationCode: String = "",
    val notice: AccountNotice? = null,
    val error: IdentityError? = null,
)

enum class AccountNotice {
    PROFILE_UPDATED,
    VERIFICATION_SENT,
    EMAIL_VERIFIED,
    GOOGLE_LINKED,
    CONSENT_UPDATED,
}

class AccountViewModel(private val repository: IdentityRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AccountUiState(
            session = repository.session.value,
            profile = repository.profile.value,
        )
    )
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                _uiState.update {
                    it.copy(
                        session = session,
                        consents = if (session == null) emptyList() else it.consents,
                    )
                }
                if (session != null) refreshConsents()
            }
        }
        viewModelScope.launch {
            repository.profile.collect { profile ->
                _uiState.update { it.copy(profile = profile) }
            }
        }
        if (repository.session.value != null) refreshProfile()
        reloadProviders()
    }

    fun refreshConsents() {
        if (repository.session.value == null) {
            _uiState.update { it.copy(consents = emptyList(), isLoadingConsents = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingConsents = true) }
            repository.listConsents().fold(
                onSuccess = { consents ->
                    _uiState.update {
                        it.copy(consents = consents, isLoadingConsents = false)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoadingConsents = false, error = error.identityError())
                    }
                },
            )
        }
    }

    fun setConsentAccepted(consentType: String, accepted: Boolean) {
        val version = AccountConsentPolicy.versionFor(consentType) ?: return
        runAccountAction(
            action = {
                if (accepted) {
                    repository.acceptConsent(consentType, version).map { Unit }
                } else {
                    repository.revokeConsent(consentType)
                }
            },
            notice = AccountNotice.CONSENT_UPDATED,
            afterSuccess = ::refreshConsents,
        )
    }

    fun setVerificationCode(value: String) {
        _uiState.update { it.copy(verificationCode = value.take(9), error = null, notice = null) }
    }

    fun refreshProfile() {
        val hadExpiredCachedSession = repository.session.value?.isExpired() == true
        viewModelScope.launch {
            repository.loadProfile().onFailure { error ->
                val isInvalidExpiredSession =
                    hadExpiredCachedSession &&
                        (error as? IdentityClientException)?.error == IdentityError.INVALID_CREDENTIALS
                if (!isInvalidExpiredSession) {
                    _uiState.update { it.copy(error = error.identityError()) }
                }
            }
        }
    }

    fun updateDisplayName(displayName: String?) = runAccountAction(
        action = { repository.updateDisplayName(displayName).map { Unit } },
        notice = AccountNotice.PROFILE_UPDATED,
    )

    fun requestEmailVerification() = runAccountAction(
        action = repository::requestEmailVerification,
        notice = AccountNotice.VERIFICATION_SENT,
    )

    fun confirmEmailVerification() {
        val code = _uiState.value.verificationCode
        if (!AccountInputValidator.isValidActionCode(code)) {
            _uiState.update { it.copy(error = IdentityError.INVALID_INPUT) }
            return
        }
        runAccountAction(
            action = { repository.confirmEmailVerification(code).map { Unit } },
            notice = AccountNotice.EMAIL_VERIFIED,
        )
    }

    fun reloadProviders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProviders = true) }
            val providers = repository.getProviders().getOrDefault(emptyList())
            _uiState.update { it.copy(providers = providers, isLoadingProviders = false) }
        }
    }

    /**
     * Rattache Google au compte connecté (ID-F-008).
     *
     * Résout l'impasse dans laquelle se trouvait l'utilisateur : une tentative
     * de connexion Google sur un compte local existant renvoyait
     * `ACCOUNT_LINK_REQUIRED`, sans aucun moyen d'y donner suite. Le
     * rattachement se fait ici, session ouverte, comme le serveur l'exige.
     */
    fun linkGoogle(client: GoogleCredentialClient) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null, notice = null) }
            val nonce = repository.requestGoogleNonce().getOrElse { error ->
                _uiState.update {
                    it.copy(isWorking = false, error = error.identityError())
                }
                return@launch
            }
            val idToken = client.requestIdToken(nonce.value).getOrElse { error ->
                val identityError = error.identityError()
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        // Une annulation de l'utilisateur n'est pas une erreur.
                        error = identityError.takeUnless { e -> e == IdentityError.CANCELLED },
                    )
                }
                return@launch
            }
            repository.linkGoogle(idToken, nonce.value).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isWorking = false, notice = AccountNotice.GOOGLE_LINKED)
                    }
                    refreshProfile()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isWorking = false, error = error.identityError())
                    }
                },
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true, error = null) }
            repository.logout().fold(
                onSuccess = { _uiState.update { it.copy(isLoggingOut = false) } },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoggingOut = false, error = error.identityError())
                    }
                },
            )
        }
    }

    private fun runAccountAction(
        action: suspend () -> Result<Unit>,
        notice: AccountNotice,
        afterSuccess: (() -> Unit)? = null,
    ) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null, notice = null) }
            action().fold(
                onSuccess = {
                    afterSuccess?.invoke()
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            verificationCode = if (notice == AccountNotice.EMAIL_VERIFIED) "" else it.verificationCode,
                            notice = notice,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isWorking = false, error = error.identityError())
                    }
                },
            )
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
    fun setCode(value: String) = update { copy(code = value.take(9), error = null) }
    fun setPassword(value: String) = update { copy(password = value, error = null) }
    fun setPasswordConfirmation(value: String) = update {
        copy(passwordConfirmation = value, error = null)
    }

    fun requestCode() {
        val email = _uiState.value.email
        if (!EMAIL_PATTERN.matches(email.trim())) {
            update { copy(error = IdentityError.INVALID_INPUT) }
            return
        }
        submit(action = { repository.requestPasswordReset(email) }) { state ->
            state.copy(step = RecoveryStep.CONFIRM_RESET)
        }
    }

    fun confirmReset() {
        val state = _uiState.value
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
        ) { current -> current.copy(completed = true) }
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
        viewModelScope.launch {
            update { copy(isSubmitting = true, error = null) }
            action().fold(
                onSuccess = { update { onSuccess(this).copy(isSubmitting = false) } },
                onFailure = { error ->
                    update { copy(isSubmitting = false, error = error.identityError()) }
                },
            )
        }
    }

    private fun update(transform: PasswordRecoveryUiState.() -> PasswordRecoveryUiState) {
        _uiState.update(transform)
    }

    private companion object {
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

data class DeveloperOptionsUiState(
    val developerModeEnabled: Boolean? = null,
    val session: AccountSession? = null,
    val diagnostic: ApiDiagnostic? = null,
    val providers: List<ProviderCapability> = emptyList(),
    val isRefreshing: Boolean = false,
    val parcelSync: ParcelSyncSummary = ParcelSyncSummary(),
    val isQueueingParcels: Boolean = false,
    val queuedParcelCount: Int? = null,
    val parcelSyncError: Boolean = false,
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

    fun synchronizeParcels() {
        if (_uiState.value.isQueueingParcels) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isQueueingParcels = true,
                    queuedParcelCount = null,
                    parcelSyncError = false,
                )
            }
            parcelSyncRepository.enqueueAll().fold(
                onSuccess = { count ->
                    _uiState.update {
                        it.copy(isQueueingParcels = false, queuedParcelCount = count)
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(isQueueingParcels = false, parcelSyncError = true)
                    }
                },
            )
        }
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
