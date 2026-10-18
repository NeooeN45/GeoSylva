package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.InventaireSessionDao
import com.forestry.counter.data.mapper.toDomain
import com.forestry.counter.data.mapper.toEntity
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.domain.model.MartelageClock
import com.forestry.counter.domain.model.MartelageGpsPoint
import com.forestry.counter.domain.model.MartelageGpsTrace
import com.forestry.counter.domain.model.MartelageGpsTraceCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** La mutation GPS revalide identité, parent et état dans la même transaction que l'ajout. */
class MartelageGpsTraceRecorder(
    private val dao: InventaireSessionDao,
    private val metadata: MetadataService,
    private val clock: MartelageClock,
    private val mutationGuardFactory: (String?) -> (() -> Boolean),
) {
    suspend fun append(sessionId: String, point: MartelageGpsPoint, continuationGuard: () -> Boolean = { true }): Boolean {
        MartelageGpsTraceCodec.validatePoint(point)
        val account = metadata.currentAccountId()
        val guard = mutationGuardFactory(account)
        return withContext(Dispatchers.IO) {
            var appended = false
            dao.withMartelageTransaction {
                check(continuationGuard() && guard() && metadata.currentAccountId() == account) { "MARTELAGE_ACCOUNT_CHANGED" }
                val entity = checkNotNull(dao.getById(sessionId)) { "SESSION_UNAVAILABLE" }
                val session = entity.toDomain()
                val plotId = checkNotNull(session.placetteId) { "MARTELAGE_LEGACY_SCOPE_READ_ONLY" }
                check(dao.getLiveParent(session.parcelleId) != null && dao.getLivePlot(session.parcelleId, plotId) != null) {
                    "MARTELAGE_PARENT_UNAVAILABLE"
                }
                check(dao.getLinkedAccounts(session.parcelleId).all { account != null && it == account }) {
                    "MARTELAGE_LINKED_TO_ANOTHER_ACCOUNT"
                }
                check(MartelageGpsTraceCodec.isCurrentPoint(point, session, clock.read())) { "MARTELAGE_TRACE_STALE" }
                val previous = session.traceJson?.let(MartelageGpsTraceCodec::decode)?.points.orEmpty()
                check(previous.size < MartelageGpsTraceCodec.MAX_POINTS) { "MARTELAGE_TRACE_FULL" }
                val last = previous.lastOrNull()
                if (last == null || last.segmentId != point.segmentId || point.elapsedRealtimeMs > last.elapsedRealtimeMs) {
                    val payload = MartelageGpsTraceCodec.encode(MartelageGpsTrace(points = previous + point))
                    dao.update(session.copy(traceJson = payload, version = session.version + 1).toEntity())
                    appended = true
                }
                check(continuationGuard() && guard() && metadata.currentAccountId() == account) { "MARTELAGE_ACCOUNT_CHANGED" }
            }
            appended
        }
    }
}
