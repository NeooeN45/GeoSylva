package com.forestry.counter.data.sync.hierarchy

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.forestry.counter.domain.repository.HierarchySyncRepository
import kotlinx.coroutines.CancellationException

/** Implemented by the application container; the worker never constructs or injects an identity. */
interface HierarchySyncOwner { val hierarchySyncRepository: HierarchySyncRepository }

class HierarchySyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as? HierarchySyncOwner)?.hierarchySyncRepository
            ?: return Result.failure(workDataOf("error" to "HIERARCHY_REPOSITORY_UNAVAILABLE"))
        return try {
            // Startup/session/content observers perform catch-up. A delivery worker must not
            // enqueue itself merely because its own retained requests are still pending.
            val result = repository.processPending()
            if (result.shouldRetry) Result.retry() else Result.success(workDataOf(
                "synced_count" to result.synchronized, "conflict_count" to result.conflicts, "error_count" to result.errors,
            ))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}
