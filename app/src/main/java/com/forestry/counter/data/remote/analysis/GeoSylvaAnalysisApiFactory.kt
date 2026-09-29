package com.forestry.counter.data.remote.analysis

import android.content.Context
import com.forestry.counter.BuildConfig
import com.forestry.counter.network.SecureHttpClient
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Réutilise la pile HTTPS durcie de GeoSylva ; aucun secret applicatif n'est ajouté. */
@OptIn(ExperimentalSerializationApi::class)
internal object GeoSylvaAnalysisApiFactory {
    fun create(context: Context): GeoSylvaAnalysisApiService? {
        val baseUrl = BuildConfig.GSIE_API_BASE_URL.trim().trimEnd('/').let { value ->
            if (value.isEmpty()) "" else "$value/"
        }
        val localDebug = BuildConfig.DEBUG && SecureHttpClient.isSafeLocalDebugUrl(baseUrl)
        if (baseUrl.isEmpty() || (!SecureHttpClient.isSafeRemoteHttpsUrl(baseUrl) && !localDebug)) {
            return null
        }
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(SecureHttpClient.createSecureClient(context, allowLocalDebug = localDebug))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeoSylvaAnalysisApiService::class.java)
    }
}
