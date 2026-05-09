package com.forestry.counter.data.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.forestry.counter.ForestryCounterApplication
import com.forestry.counter.domain.model.ParameterItem
import com.forestry.counter.domain.parameters.ParameterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class PriceSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val app = applicationContext as ForestryCounterApplication
            val paramRepo = app.parameterRepository

            // Determine URL (prefer input, else parameter, else fail)
            val urlFromInput = inputData.getString(KEY_URL)
            val url = urlFromInput ?: paramRepo.getParameter(ParameterKeys.PRICE_FEED_URL).firstOrNull()?.valueJson?.trim('"')
            if (url.isNullOrBlank()) return@withContext Result.failure()

            val client = OkHttpClient()
            val req = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext Result.retry()
            val body = resp.body?.string() ?: return@withContext Result.retry()

            // Expect JSON array of PriceEntry
            paramRepo.setParameter(ParameterItem(ParameterKeys.PRIX_MARCHE, body))
            Result.success()
        } catch (e: Exception) {
            Log.e("PriceSyncWorker", "Price sync failed", e)
            Result.retry()
        }
    }

    companion object {
        const val KEY_URL = "url"
    }
}
