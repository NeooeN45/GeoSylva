package com.forestry.counter.data.remote.analysis

import com.forestry.counter.data.remote.identity.EncryptedIdentitySessionStore
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

internal class GeoSylvaAnalysisRepository(
    private val api: GeoSylvaAnalysisApiService,
    private val sessionStore: EncryptedIdentitySessionStore,
) {
    suspend fun submit(request: GeoSylvaAnalysisRequestDto): Result<AnalysisAcceptedDto> =
        authenticated { token ->
            api.createAnalysis(
                authorization = "Bearer $token",
                idempotencyKey = request.requestId,
                request = request,
            )
        }

    suspend fun status(analysisId: String): Result<AnalysisStatusDto> =
        authenticated { token -> api.getAnalysisStatus("Bearer $token", analysisId) }

    suspend fun result(analysisId: String): Result<ScientificAnalysisResultDto> =
        authenticated { token -> api.getAnalysisResult("Bearer $token", analysisId) }

    /**
     * Polling canonique V1. `delay` et les appels Retrofit sont annulables avec le cycle de vie.
     * Seuls POST idempotent et GET sont rejoués ; aucune mutation non idempotente n'est répétée.
     */
    suspend fun awaitTerminal(
        analysisId: String,
        initialDelaySeconds: Int = 2,
        maximumPolls: Int = 300,
    ): Result<AnalysisStatusDto> {
        var waitSeconds = initialDelaySeconds.coerceIn(1, 60)
        repeat(maximumPolls) {
            val current = status(analysisId).getOrElse { return Result.failure(it) }
            if (AnalysisState.fromWire(current.status).isTerminal) return Result.success(current)
            waitSeconds = (current.retryAfterSeconds ?: waitSeconds).coerceIn(1, 60)
            delay(waitSeconds * 1_000L)
        }
        return Result.failure(IllegalStateException("Délai maximal de suivi dépassé"))
    }

    private suspend fun <T> authenticated(block: suspend (String) -> T): Result<T> {
        val token = sessionStore.read()?.accessToken
            ?: return Result.failure(IllegalStateException("Session Quintessences absente"))
        return try {
            Result.success(block(token))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }
}
