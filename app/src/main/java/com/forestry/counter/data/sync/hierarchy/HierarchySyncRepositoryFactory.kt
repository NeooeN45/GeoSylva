package com.forestry.counter.data.sync.hierarchy

import android.content.Context
import com.forestry.counter.BuildConfig
import com.forestry.counter.data.local.dao.HierarchySyncDao
import com.forestry.counter.data.remote.identity.EncryptedIdentitySessionStore
import com.forestry.counter.domain.repository.HierarchySyncRepository
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.network.SecureHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object HierarchySyncRepositoryFactory {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun create(context: Context, dao: HierarchySyncDao, identityRepository: IdentityRepository): HierarchySyncRepository {
        val origin = BuildConfig.GSIE_API_BASE_URL.trim().trimEnd('/').takeIf { it.isNotBlank() }
        val base = origin?.let { "$it/" }
        val localDebug = BuildConfig.DEBUG && base != null && SecureHttpClient.isSafeLocalDebugUrl(base)
        val api = if (base == null || (!SecureHttpClient.isSafeRemoteHttpsUrl(base) && !localDebug)) null else Retrofit.Builder()
            .baseUrl(base).client(SecureHttpClient.createSecureClient(context, allowLocalDebug = localDebug))
            .addConverterFactory(hierarchyJson.asConverterFactory("application/json".toMediaType()))
            .build().create(HierarchySyncApiService::class.java)
        return HierarchySyncRepositoryImpl(context, dao, identityRepository, EncryptedIdentitySessionStore(context), api, origin)
    }

    /** A fresh session/startup scan captures only already activated forests; it does not activate others. */
    fun startObserving(repository: HierarchySyncRepository, scope: CoroutineScope): Job? =
        (repository as? HierarchySyncRepositoryImpl)?.startObserving(scope)
}
