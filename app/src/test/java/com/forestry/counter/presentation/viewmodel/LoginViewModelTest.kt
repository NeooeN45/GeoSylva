package com.forestry.counter.presentation.viewmodel

import androidx.lifecycle.ViewModelStore
import com.forestry.counter.domain.model.AccountSession
import com.forestry.counter.domain.model.IdentityError
import com.forestry.counter.domain.model.LoginOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: IdentityRepositoryFake
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = IdentityRepositoryFake()
        viewModel = LoginViewModel(repository)
    }

    @After
    fun tearDown() {
        ViewModelStore().apply {
            put("identity-login", viewModel)
            clear()
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `une adresse invalide reste dans la premiere etape sans inscription reseau`() = runTest(dispatcher) {
        runCurrent()
        viewModel.setMode(LoginMode.REGISTER)
        viewModel.setEmail("adresse-incomplete")
        viewModel.continueRegistration()
        viewModel.submit()
        runCurrent()

        assertEquals(RegistrationStep.EMAIL, viewModel.uiState.value.registrationStep)
        assertEquals(LoginInputIssue.EMAIL, viewModel.uiState.value.inputIssue)
        assertTrue(repository.registrations.isEmpty())

        viewModel.setEmail(EMAIL)
        viewModel.continueRegistration()
        viewModel.submit()
        runCurrent()

        assertEquals(RegistrationStep.PASSWORD, viewModel.uiState.value.registrationStep)
        assertTrue(repository.registrations.isEmpty())
    }

    @Test
    fun `longueur et confirmation du mot de passe sont validees avant le profil`() = runTest(dispatcher) {
        runCurrent()
        beginRegistration()

        for (password in listOf("trop-court", "x".repeat(129))) {
            viewModel.setPassword(password)
            viewModel.setPasswordConfirmation(password)
            viewModel.continueRegistration()
            assertEquals(RegistrationStep.PASSWORD, viewModel.uiState.value.registrationStep)
            assertEquals(LoginInputIssue.PASSWORD_LENGTH, viewModel.uiState.value.inputIssue)
        }

        viewModel.setPassword(PASSWORD)
        viewModel.setPasswordConfirmation("confirmation-incompatible")
        viewModel.continueRegistration()
        assertEquals(RegistrationStep.PASSWORD, viewModel.uiState.value.registrationStep)
        assertEquals(LoginInputIssue.PASSWORD_CONFIRMATION, viewModel.uiState.value.inputIssue)

        viewModel.setPasswordConfirmation(PASSWORD)
        viewModel.continueRegistration()
        runCurrent()
        assertEquals(RegistrationStep.PROFILE, viewModel.uiState.value.registrationStep)
        assertTrue(repository.registrations.isEmpty())
    }

    @Test
    fun `revenir en arriere conserve les saisies du parcours en cours`() = runTest(dispatcher) {
        runCurrent()
        beginRegistration()
        viewModel.setPassword(PASSWORD)
        viewModel.setPasswordConfirmation(PASSWORD)
        viewModel.continueRegistration()
        viewModel.setDisplayName("Technicien test JVM")

        viewModel.previousRegistrationStep()
        assertEquals(RegistrationStep.PASSWORD, viewModel.uiState.value.registrationStep)
        viewModel.previousRegistrationStep()
        assertEquals(RegistrationStep.EMAIL, viewModel.uiState.value.registrationStep)
        assertEquals(EMAIL, viewModel.uiState.value.email)
        assertEquals(PASSWORD, viewModel.uiState.value.password)
        assertEquals(PASSWORD, viewModel.uiState.value.passwordConfirmation)
        assertEquals("Technicien test JVM", viewModel.uiState.value.displayName)
        assertTrue(repository.registrations.isEmpty())
    }

    @Test
    fun `la validation finale inscrit une fois et efface les secrets apres succes`() = runTest(dispatcher) {
        val finish = CompletableDeferred<Result<AccountSession>>()
        repository.registerHandler = { finish.await() }
        runCurrent()
        beginRegistration()
        viewModel.setPassword(PASSWORD)
        viewModel.setPasswordConfirmation(PASSWORD)
        viewModel.continueRegistration()
        viewModel.setDisplayName("x".repeat(201))
        viewModel.submit()
        runCurrent()
        assertEquals(LoginInputIssue.DISPLAY_NAME, viewModel.uiState.value.inputIssue)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertTrue(repository.registrations.isEmpty())
        viewModel.setDisplayName("Technicien test JVM")

        viewModel.submit()
        viewModel.submit()
        assertTrue(viewModel.uiState.value.isSubmitting)
        viewModel.setEmail("autre@example.invalid")
        viewModel.setPassword("autre-mot-de-passe")
        viewModel.setDisplayName("Autre saisie")
        viewModel.setMode(LoginMode.SIGN_IN)
        viewModel.previousRegistrationStep()
        runCurrent()

        assertEquals(
            listOf(IdentityRepositoryFake.Registration(EMAIL, PASSWORD, "Technicien test JVM")),
            repository.registrations,
        )
        assertEquals(LoginMode.REGISTER, viewModel.uiState.value.mode)
        assertEquals(RegistrationStep.PROFILE, viewModel.uiState.value.registrationStep)
        assertEquals(EMAIL, viewModel.uiState.value.email)
        assertFalse(viewModel.uiState.value.completed)

        finish.complete(Result.success(identitySessionFixture()))
        runCurrent()
        assertTrue(viewModel.uiState.value.completed)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals("", viewModel.uiState.value.password)
        assertEquals("", viewModel.uiState.value.passwordConfirmation)
    }

    @Test
    fun `deux validations de connexion ne font qu une requete meme avant execution du scope`() = runTest(dispatcher) {
        val finish = CompletableDeferred<Result<LoginOutcome>>()
        repository.passwordLoginHandler = { finish.await() }
        runCurrent()
        viewModel.setEmail(EMAIL)
        viewModel.setPassword(PASSWORD)

        viewModel.submit()
        viewModel.submit()
        assertTrue(viewModel.uiState.value.isSubmitting)
        viewModel.setPassword("saisie-ignoree-pendant-requete")
        viewModel.setMode(LoginMode.REGISTER)
        runCurrent()

        assertEquals(listOf(IdentityRepositoryFake.PasswordLogin(EMAIL, PASSWORD)), repository.passwordLogins)
        assertEquals(LoginMode.SIGN_IN, viewModel.uiState.value.mode)
        assertEquals(PASSWORD, viewModel.uiState.value.password)
        assertFalse(viewModel.uiState.value.completed)

        finish.complete(Result.success(LoginOutcome.Authenticated(identitySessionFixture())))
        runCurrent()
        assertTrue(viewModel.uiState.value.completed)
        assertEquals("", viewModel.uiState.value.password)
        assertEquals("", viewModel.uiState.value.passwordConfirmation)
    }

    @Test
    fun `un defi MFA bloque la connexion jusqu a validation unique du second facteur`() = runTest(dispatcher) {
        repository.passwordLoginHandler = { Result.success(LoginOutcome.MfaRequired("test-only-challenge", 300)) }
        val finish = CompletableDeferred<Result<LoginOutcome>>()
        repository.mfaLoginHandler = { finish.await() }
        runCurrent()
        viewModel.setEmail(EMAIL)
        viewModel.setPassword(PASSWORD)
        viewModel.submit()
        runCurrent()

        assertTrue(viewModel.uiState.value.isAwaitingMfa)
        assertFalse(viewModel.uiState.value.completed)
        assertEquals("", viewModel.uiState.value.password)
        viewModel.setMfaCode("123")
        viewModel.submitMfa()
        runCurrent()
        assertTrue(repository.mfaLogins.isEmpty())
        assertEquals(IdentityError.INVALID_INPUT, viewModel.uiState.value.error)

        viewModel.setMfaCode("123456")
        viewModel.submitMfa()
        viewModel.submitMfa()
        assertTrue(viewModel.uiState.value.isSubmitting)
        viewModel.cancelMfa()
        viewModel.setMfaCode("999999")
        viewModel.setMfaUsesRecoveryCode(true)
        runCurrent()

        assertEquals(
            listOf(IdentityRepositoryFake.MfaLogin("test-only-challenge", "123456", false)),
            repository.mfaLogins,
        )
        assertTrue(viewModel.uiState.value.isAwaitingMfa)
        assertEquals("123456", viewModel.uiState.value.mfaCode)

        finish.complete(Result.success(LoginOutcome.Authenticated(identitySessionFixture())))
        runCurrent()
        assertTrue(viewModel.uiState.value.completed)
        assertFalse(viewModel.uiState.value.isAwaitingMfa)
        assertNull(viewModel.uiState.value.mfaChallengeToken)
        assertEquals("", viewModel.uiState.value.mfaCode)
        assertEquals("", viewModel.uiState.value.password)
    }

    private fun beginRegistration() {
        viewModel.setMode(LoginMode.REGISTER)
        viewModel.setEmail(EMAIL)
        viewModel.continueRegistration()
        assertEquals(RegistrationStep.PASSWORD, viewModel.uiState.value.registrationStep)
    }

    private companion object {
        const val EMAIL = "operator@example.invalid"
        const val PASSWORD = "mot-de-passe-test-uniquement"
    }
}
