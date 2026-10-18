package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.TigeDao
import com.forestry.counter.data.local.entity.InventaireSessionEntity
import com.forestry.counter.data.local.entity.TigeEntity
import com.forestry.counter.data.mapper.toDomain
import com.forestry.counter.data.mapper.toTige
import com.forestry.counter.data.mapper.toTigeEntity
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.model.MartelageClock
import com.forestry.counter.domain.model.MartelageStates
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.model.needsClockRecovery
import com.forestry.counter.domain.repository.TigeRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TigeRepositoryImpl(
    private val tigeDao: TigeDao,
    private val metadataService: MetadataService,
    private val martelageClock: MartelageClock? = null,
    private val mutationGuardFactory: ((String?) -> (() -> Boolean))? = null,
    private val readAccess: BusinessReadAccess? = null,
) : TigeRepository {
    override fun getAllTiges(): Flow<List<Tige>> =
        readableTrees(tigeDao.getAllTiges().map { it.map { row -> row.toTige() } })
    override fun getTigesByParcelle(parcelleId: String): Flow<List<Tige>> =
        readableTrees(tigeDao.getTigesByParcelle(parcelleId).map { it.map { row -> row.toTige() } })
    override fun getTigesByPlacette(placetteId: String): Flow<List<Tige>> =
        readableTrees(tigeDao.getTigesByPlacette(placetteId).map { it.map { row -> row.toTige() } })
    override fun getTigeById(tigeId: String): Flow<Tige?> {
        val source = tigeDao.getTigeByIdFlow(tigeId).map { it?.toTige() }
        val access = readAccess ?: return source
        return access.observe(source, null) { value, snapshot ->
            value?.takeIf { access.canReadParcelle(it.parcelleId, snapshot) }
        }
    }

    private fun readableTrees(source: Flow<List<Tige>>): Flow<List<Tige>> {
        val access = readAccess ?: return source
        return access.observe(source, emptyList()) { values, snapshot ->
            values.filter { access.canReadParcelle(it.parcelleId, snapshot) }
        }
    }

    override suspend fun insertTige(tige: Tige) = insertTiges(listOf(tige))

    override suspend fun insertTiges(tiges: List<Tige>) {
        if (tiges.isEmpty()) return
        require(tiges.map { it.id }.distinct().size == tiges.size) { "TIGE_IDS_DUPLICATED" }
        val first = metadataService.enrichForCreate(tiges.first())
        val actor = first.auteur
        withAuthorizedMutation {
            val rows = tiges.map { tree ->
                require(tree.deletedAt == null) { "TIGE_DELETED_INSERT_REFUSED" }
                checkParent(tree.parcelleId, tree.placetteId, actor)
                val sessions = tigeDao.getManagedSessionsForPlacetteMutation(tree.parcelleId, tree.placetteId)
                val active = activeSession(sessions, tree.placetteId)
                val assigned = if (sessions.isEmpty()) {
                    // Explicit legacy sessions remain untouched. Old null observations are never claimed.
                    tree.sessionId?.let { id ->
                        val legacy = checkNotNull(tigeDao.getSessionForMutation(id)) { "TIGE_SESSION_UNAVAILABLE" }
                        check(legacy.parcelleId == tree.parcelleId &&
                            (legacy.placetteId == null || legacy.placetteId == tree.placetteId) && legacy.martelageState == null && legacy.deletedAt == null) {
                            "TIGE_SESSION_MISMATCH"
                        }
                    }
                    tree
                } else {
                    check(active != null && runningWithCurrentClock(active)) { "MARTELAGE_NOT_RUNNING" }
                    check(tree.sessionId == null || tree.sessionId == active.sessionId) { "MARTELAGE_SESSION_MISMATCH" }
                    tree.copy(sessionId = active.sessionId, originTigeId = tree.originTigeId ?: tree.id)
                }
                assigned.withMetadata(checkNotNull(actor), checkNotNull(first.source), 1)
                    .toTigeEntity().copy(uuid = UUID.randomUUID().toString())
            }
            // Batch, state checks and inserts share one Room transaction with close/pause.
            tigeDao.insertNewTiges(rows)
        }
    }

    override suspend fun updateTige(tige: Tige) {
        val ok = mutateById(tige.id) { current ->
            require(tige.parcelleId == current.parcelleOwnerId && tige.placetteId == current.placetteOwnerId &&
                tige.sessionId == current.sessionId && tige.originTigeId == current.originTigeId) {
                "TIGE_IDENTITY_CHANGE_REFUSED"
            }
            tige.toTigeEntity()
        }
        check(ok) { "MARTELAGE_TIGE_READ_ONLY" }
    }

    override suspend fun deleteTige(tigeId: String) {
        check(mutateById(tigeId) { it.copy(deletedAt = System.currentTimeMillis()) }) {
            "MARTELAGE_TIGE_READ_ONLY"
        }
    }

    override suspend fun deleteTigesByParcelle(parcelleId: String) = deleteMatching(parcelleId) { true }
    override suspend fun deleteTigesByPlacette(placetteId: String) {
        val rows = tigeDao.getAllTigesNow().filter { it.placetteOwnerId == placetteId }
        rows.map { it.parcelleOwnerId }.distinct().forEach { parent -> deleteMatching(parent) { it.placetteOwnerId == placetteId } }
    }
    override suspend fun deleteTigesByPlacetteAndEssence(placetteId: String, essenceCode: String) {
        val rows = tigeDao.getAllTigesNow().filter { it.placetteOwnerId == placetteId && it.essenceCode == essenceCode }
        rows.map { it.parcelleOwnerId }.distinct().forEach { parent ->
            deleteMatching(parent) { it.placetteOwnerId == placetteId && it.essenceCode == essenceCode }
        }
    }

    override suspend fun deleteTigesBySessionAndEssence(sessionId: String, essenceCode: String) {
        val actor = capturedActor()
        withAuthorizedMutation {
            val session = checkNotNull(tigeDao.getSessionForMutation(sessionId)) { "TIGE_SESSION_UNAVAILABLE" }
            val plotId = checkNotNull(session.placetteId) { "MARTELAGE_LEGACY_SCOPE_READ_ONLY" }
            checkParent(session.parcelleId, plotId, actor)
            check(session.deletedAt == null && runningWithCurrentClock(session)) { "MARTELAGE_NOT_RUNNING" }
            val rows = tigeDao.getTigesByParcelleNow(session.parcelleId)
                .filter { it.sessionId == sessionId && it.essenceCode == essenceCode }
            check(rows.all { it.placetteOwnerId == plotId && editable(it, actor) }) { "MARTELAGE_TIGE_READ_ONLY" }
            val now = System.currentTimeMillis()
            rows.forEach { saveChange(it, it.copy(deletedAt = now), actor) }
        }
    }

    override suspend fun deleteLatest(parcelleId: String, placetteId: String?, essenceCode: String, diamCm: Double): Boolean =
        mutateLatest(parcelleId, placetteId, essenceCode, diamCm) { it.copy(deletedAt = System.currentTimeMillis()) }
    override suspend fun updateLatestGPS(
        parcelleId: String, placetteId: String?, essenceCode: String, diamCm: Double,
        wkt: String, precisionM: Double?, altitudeM: Double?,
    ): Boolean = mutateLatest(parcelleId, placetteId, essenceCode, diamCm) {
        it.copy(gpsWkt = wkt, precisionM = precisionM, altitudeM = altitudeM)
    }
    override suspend fun updateLatestHeight(parcelleId: String, placetteId: String?, essenceCode: String, diamCm: Double, heightM: Double?): Boolean =
        mutateLatest(parcelleId, placetteId, essenceCode, diamCm) { it.copy(hauteurM = heightM) }
    override suspend fun setTigeHeight(tigeId: String, heightM: Double?): Boolean =
        mutateById(tigeId) { it.copy(hauteurM = heightM) }
    override suspend fun setTigeGps(tigeId: String, wkt: String, precisionM: Double?, altitudeM: Double?): Boolean =
        mutateById(tigeId) { it.copy(gpsWkt = wkt, precisionM = precisionM, altitudeM = altitudeM) }
    override suspend fun updateLatestCategory(parcelleId: String, placetteId: String?, essenceCode: String, diamCm: Double, category: String?): Boolean =
        mutateLatest(parcelleId, placetteId, essenceCode, diamCm) { it.copy(categorie = category) }
    override suspend fun updateLatestQuality(parcelleId: String, placetteId: String?, essenceCode: String, diamCm: Double, quality: Int?): Boolean =
        mutateLatest(parcelleId, placetteId, essenceCode, diamCm) { it.copy(qualite = quality) }
    override suspend fun updateLatestDefects(parcelleId: String, placetteId: String?, essenceCode: String, diamCm: Double, defects: List<String>?): Boolean =
        mutateLatest(parcelleId, placetteId, essenceCode, diamCm) { it.copy(defauts = defects?.joinToString(",")) }
    override suspend fun updateLatestNumero(parcelleId: String, placetteId: String?, essenceCode: String, diamCm: Double, numero: Int?): Boolean =
        mutateLatest(parcelleId, placetteId, essenceCode, diamCm) { it.copy(numero = numero) }
    override suspend fun updateLatestPhoto(parcelleId: String, placetteId: String?, essenceCode: String, diamCm: Double, photoUri: String?): Boolean =
        mutateLatest(parcelleId, placetteId, essenceCode, diamCm) { it.copy(photoUri = photoUri) }
    override suspend fun updateTigeQuality(tigeId: String, qualite: Int?, produit: String?, qualiteDetail: String?): Boolean =
        mutateById(tigeId) { it.copy(qualite = qualite, produit = produit, qualiteDetail = qualiteDetail) }
    override suspend fun updateTigeSylviculture(
        tigeId: String, classeKraft: Int?, etatSanitaire: String?, vigueur: String?, origine: String?, isTigeHabitat: Boolean,
    ): Boolean = mutateById(tigeId) {
        it.copy(classeKraft = classeKraft, etatSanitaire = etatSanitaire, vigueur = vigueur, origine = origine, isTigeHabitat = isTigeHabitat)
    }

    private suspend fun mutateById(id: String, transform: (TigeEntity) -> TigeEntity): Boolean {
        val actor = capturedActor()
        var changed = false
        withAuthorizedMutation {
            val current = tigeDao.getTigeById(id) ?: return@withAuthorizedMutation
            if (!editable(current, actor)) return@withAuthorizedMutation
            saveChange(current, transform(current), actor)
            changed = true
        }
        return changed
    }

    private suspend fun mutateLatest(
        parcelleId: String, placetteId: String?, essenceCode: String, diamCm: Double,
        transform: (TigeEntity) -> TigeEntity,
    ): Boolean {
        val actor = capturedActor()
        var changed = false
        withAuthorizedMutation {
            val sessions = tigeDao.getManagedSessionsForPlacetteMutation(parcelleId, placetteId)
            val active = activeSession(sessions, placetteId)
            if (sessions.isNotEmpty() && (active == null || !runningWithCurrentClock(active))) return@withAuthorizedMutation
            val current = if (sessions.isEmpty()) tigeDao.getLatestMatching(parcelleId, placetteId, essenceCode, diamCm)
                else tigeDao.getLatestMatchingInSession(parcelleId, placetteId, essenceCode, diamCm, active?.sessionId)
            if (current == null || !editable(current, actor)) return@withAuthorizedMutation
            saveChange(current, transform(current), actor)
            changed = true
        }
        return changed
    }

    private suspend fun deleteMatching(parcelleId: String, predicate: (TigeEntity) -> Boolean) {
        val actor = capturedActor()
        withAuthorizedMutation {
            val rows = tigeDao.getTigesByParcelleNow(parcelleId).filter(predicate)
            check(rows.all { editable(it, actor) }) { "MARTELAGE_TIGE_READ_ONLY" }
            val now = System.currentTimeMillis()
            rows.forEach { saveChange(it, it.copy(deletedAt = now), actor) }
        }
    }

    private suspend fun saveChange(current: TigeEntity, changed: TigeEntity, actor: String?) {
        require(changed.tigeId == current.tigeId && changed.parcelleOwnerId == current.parcelleOwnerId &&
            changed.placetteOwnerId == current.placetteOwnerId && changed.sessionId == current.sessionId &&
            changed.originTigeId == current.originTigeId) { "TIGE_IDENTITY_CHANGE_REFUSED" }
        val row = changed.copy(auteur = actor, source = current.source ?: "manual", version = current.version + 1, uuid = current.uuid)
        tigeDao.updateTige(row)
    }

    private suspend fun editable(current: TigeEntity, actor: String?): Boolean {
        if (runCatching { checkParent(current.parcelleOwnerId, current.placetteOwnerId, actor) }.isFailure) return false
        val sessions = tigeDao.getManagedSessionsForPlacetteMutation(current.parcelleOwnerId, current.placetteOwnerId)
        if (sessions.isEmpty()) {
            return current.sessionId?.let { id ->
                tigeDao.getSessionForMutation(id)?.let { it.parcelleId == current.parcelleOwnerId &&
                    (it.placetteId == null || it.placetteId == current.placetteOwnerId) && it.martelageState == null && it.deletedAt == null }
            } ?: (current.sessionId == null)
        }
        val active = activeSession(sessions, current.placetteOwnerId) ?: return false
        return current.sessionId == active.sessionId && runningWithCurrentClock(active)
    }

    private suspend fun checkParent(parcelleId: String, placetteId: String?, actor: String?) {
        val currentAccount = metadataService.currentAccountId()
        check(actor == (currentAccount ?: "anonymous")) { "TIGE_ACCOUNT_CHANGED" }
        check(tigeDao.countLiveParentForMutation(parcelleId) == 1) { "TIGE_PARENT_UNAVAILABLE" }
        check(tigeDao.getLinkedAccountsForMutation(parcelleId).all { currentAccount != null && it == currentAccount }) {
            "TIGE_LINKED_TO_ANOTHER_ACCOUNT"
        }
        if (placetteId != null) check(tigeDao.countLivePlotForMutation(parcelleId, placetteId) == 1) { "TIGE_PLOT_UNAVAILABLE" }
    }

    private fun activeSession(sessions: List<InventaireSessionEntity>, placetteId: String?): InventaireSessionEntity? {
        val active = sessions.filter { placetteId != null && it.placetteId == placetteId && it.martelageState in MartelageStates.ACTIVE && it.deletedAt == null }
        check(active.size <= 1) { "MARTELAGE_MULTIPLE_ACTIVE_SESSIONS" }
        return active.firstOrNull()
    }
    private fun runningWithCurrentClock(session: InventaireSessionEntity): Boolean {
        val reading = martelageClock?.read() ?: return false
        return session.martelageState == MartelageStates.RUNNING && !session.toDomain().needsClockRecovery(reading)
    }
    private fun capturedActor(): String = metadataService.currentAccountId() ?: "anonymous"
    private suspend fun withAuthorizedMutation(block: suspend () -> Unit) {
        val account = metadataService.currentAccountId()
        val guard = mutationGuardFactory?.invoke(account) ?: { metadataService.currentAccountId() == account }
        tigeDao.withTigeMutationTransaction {
            check(guard() && metadataService.currentAccountId() == account) { "TIGE_ACCOUNT_CHANGED" }
            block()
            check(guard() && metadataService.currentAccountId() == account) { "TIGE_ACCOUNT_CHANGED" }
        }
    }
}
