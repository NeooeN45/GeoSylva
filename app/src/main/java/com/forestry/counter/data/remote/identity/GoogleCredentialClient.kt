package com.forestry.counter.data.remote.identity

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.forestry.counter.BuildConfig
import com.forestry.counter.domain.model.IdentityClientException
import com.forestry.counter.domain.model.IdentityError
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException

/** Pont Android officiel entre Credential Manager et l'identité GSIE. */
class GoogleCredentialClient(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun requestIdToken(nonce: String): Result<String> {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.takeIf(String::isNotBlank)
            ?: return Result.failure(
                IdentityClientException(IdentityError.GOOGLE_NOT_CONFIGURED)
            )
        return try {
            val option = GetSignInWithGoogleOption.Builder(clientId)
                .setNonce(nonce)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential as? CustomCredential
                ?: return Result.failure(
                    IdentityClientException(IdentityError.INVALID_SERVER_RESPONSE)
                )
            if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return Result.failure(
                    IdentityClientException(IdentityError.INVALID_SERVER_RESPONSE)
                )
            }
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            Result.success(googleCredential.idToken)
        } catch (cancelled: GetCredentialCancellationException) {
            Result.failure(IdentityClientException(IdentityError.CANCELLED, cancelled))
        } catch (error: GetCredentialException) {
            Result.failure(IdentityClientException(IdentityError.GOOGLE_NOT_CONFIGURED, error))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(IdentityClientException(IdentityError.UNKNOWN, error))
        }
    }
}
