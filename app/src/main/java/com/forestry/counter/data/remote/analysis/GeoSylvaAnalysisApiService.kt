package com.forestry.counter.data.remote.analysis

import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

internal interface GeoSylvaAnalysisApiService {
    @POST("api/v1/geosylva/analyses")
    suspend fun createAnalysis(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: GeoSylvaAnalysisRequestDto,
    ): AnalysisAcceptedDto

    @GET("api/v1/geosylva/analyses/{analysis_id}")
    suspend fun getAnalysisStatus(
        @Header("Authorization") authorization: String,
        @Path("analysis_id") analysisId: String,
    ): AnalysisStatusDto

    @GET("api/v1/geosylva/analyses/{analysis_id}/result")
    suspend fun getAnalysisResult(
        @Header("Authorization") authorization: String,
        @Path("analysis_id") analysisId: String,
    ): ScientificAnalysisResultDto

    @GET("api/v1/geosylva/resources")
    suspend fun listResources(
        @Header("Authorization") authorization: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("domain") domain: String? = null,
    ): JsonElement

    @GET("api/v1/geosylva/resources/{dataset_id}")
    suspend fun getResource(
        @Header("Authorization") authorization: String,
        @Path("dataset_id") datasetId: String,
    ): JsonElement
}
