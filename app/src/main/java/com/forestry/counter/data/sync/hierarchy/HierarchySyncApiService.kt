package com.forestry.counter.data.sync.hierarchy

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

internal interface HierarchySyncApiService {
    @PUT("api/v1/sync/geosylva/dossiers/{forestId}")
    suspend fun publish(@Header("Authorization") authorization: String, @Path("forestId") forestId: String,
        @Body frozenRequest: RequestBody): Response<HierarchyReceiptDto>
    @GET("api/v1/sync/geosylva/dossiers/{forestId}")
    suspend fun get(@Header("Authorization") authorization: String, @Path("forestId") forestId: String): Response<HierarchyRemoteDto>
    @GET("api/v1/sync/geosylva/dossiers")
    suspend fun list(@Header("Authorization") authorization: String, @Query("page") page: Int,
        @Query("size") size: Int): Response<HierarchyPageDto>
    @GET("api/v1/sync/geosylva/dossier-operations/{operationId}")
    suspend fun receipt(@Header("Authorization") authorization: String, @Path("operationId") operationId: String): Response<HierarchyReceiptDto>
}
