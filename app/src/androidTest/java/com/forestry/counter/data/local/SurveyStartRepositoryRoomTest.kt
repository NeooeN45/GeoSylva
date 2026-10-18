package com.forestry.counter.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.local.dao.InventaireSessionDao
import com.forestry.counter.data.local.dao.SurveyContextDao
import com.forestry.counter.data.local.entity.HierarchySyncAssociationEntity
import com.forestry.counter.data.local.entity.InventaireSessionEntity
import com.forestry.counter.data.local.entity.SurveyContextEntity
import com.forestry.counter.data.mapper.toEntity
import com.forestry.counter.data.mapper.toParcelleEntity
import com.forestry.counter.data.mapper.toPlacetteEntity
import com.forestry.counter.data.repository.InventaireSessionRepositoryImpl
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.data.sync.ParcelMutationJournal
import com.forestry.counter.data.sync.hierarchy.BusinessMutationGuard
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.InventaireSession
import com.forestry.counter.domain.model.MartelageClock
import com.forestry.counter.domain.model.MartelageClockReading
import com.forestry.counter.domain.model.MartelageStates
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.SurveyContextCodec
import com.forestry.counter.domain.model.SurveyContextDraft
import com.forestry.counter.domain.model.SurveyContextRecord
import com.forestry.counter.domain.model.SurveyPopulationScope
import com.forestry.counter.domain.model.SurveyPurpose
import com.forestry.counter.domain.repository.InventaireSessionRepository
import com.forestry.counter.domain.repository.SurveyContextRepository
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Transactions Room dans une base TEST dédiée ; aucune base, préférence ou identité utilisateur. */
@RunWith(AndroidJUnit4::class)
class SurveyStartRepositoryRoomTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: ForestryDatabase
    private lateinit var access: BusinessReadAccess
    private val identity = BusinessReadIdentityFixture()
    private val metadata = MetadataService { identity.session.value?.accountId }
    private val clock = object : MartelageClock {
        override fun read() = MartelageClockReading(1_000L, 500L, 7)
    }
    private val sessions get() = repository()
    private val draft get() = SurveyContextDraft(purpose = SurveyPurpose.MARKING)

    @Before fun setup() = checked {
        context.deleteDatabase(DB_NAME)
        identity.login(ACCOUNT_A)
        database = open()
        access = BusinessReadAccess(identity, database.parcelSyncDao())
        database.foretDao().insert(Foret(
            foretId = FOREST, nom = "TEST atomic forest", proprietaireNom = "TEST",
            proprietaireEmail = null, gestionnaireNom = null, typeForet = null,
            objectifGestion = null, psgNumero = null, psgDateExpiration = null,
            departement = null, remarques = null, createdAt = 100L, updatedAt = 100L,
        ).toEntity())
        database.parcelleDao().insertParcelle(parcel().toParcelleEntity())
        database.placetteDao().insertNewPlacettes(listOf(plot(PLOT).toPlacetteEntity(), plot(OTHER_PLOT).toPlacetteEntity()))
        database.hierarchySyncDao().insertAssociation(HierarchySyncAssociationEntity(
            forestId = FOREST, accountId = ACCOUNT_A, apiOrigin = "https://test.invalid",
            activatedAt = 100L, lastCapturedHash = null, serverVersion = null,
            lastSuccessAt = null, lastErrorCode = null,
        ))
    }

    @After fun cleanup() {
        database.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test fun inventory_starts_with_unknown_protocol_population_completeness_and_area() = checked {
        val session = start(SurveyPurpose.INVENTORY)
        assertInitialPair(session, SurveyPurpose.INVENTORY)
    }

    @Test fun marking_starts_with_unknown_protocol_population_completeness_and_area() = checked {
        val session = start(SurveyPurpose.MARKING)
        assertInitialPair(session, SurveyPurpose.MARKING)
    }

    @Test fun combined_is_a_declaration_without_duplicating_trees_or_presuming_exhaustiveness() = checked {
        val session = start(SurveyPurpose.COMBINED)
        assertInitialPair(session, SurveyPurpose.COMBINED)
        assertEquals(0, tableCount("tiges"))
        assertEquals(0, tableCount("observation_qualifications"))
    }

    @Test fun explicit_draft_is_preserved_without_cadastral_area_or_protocol_inference() = checked {
        val declared = SurveyContextDraft(
            purpose = SurveyPurpose.INVENTORY, populationReference = "TEST-population",
            populationScope = SurveyPopulationScope.REMAINING_STEMS, inventoryComplete = false,
            protocolReference = "TEST-protocol", protocolVersion = "TEST-v1",
            sourceReference = "TEST-notice", inventoriedHorizontalAreaM2 = 480.0,
            areaSourceReference = "TEST-horizontal-area-measure",
        )
        val session = sessions.startForPlacetteWithContext(PARCEL, PLOT, declared)
        val saved = checkNotNull(sessions.getContext(session.sessionId))
        assertEquals(declared, saved.draft)
        assertEquals(SurveyContextCodec.encode(declared), database.surveyContextDao().getBySession(session.sessionId)?.payloadJson)
        assertEquals(false, saved.draft.inventoryComplete)
        assertEquals(1, saved.revision)
        assertNull(saved.copiedFromSessionId)
        assertEquals(0, tableCount("tiges"))
    }

    @Test fun missing_purpose_refuses_start_before_any_insertion() = checked {
        failure("SURVEY_CONTEXT_PURPOSE_REQUIRED") {
            sessions.startForPlacetteWithContext(PARCEL, PLOT, SurveyContextDraft())
        }
        assertNoPair()
    }

    @Test fun missing_context_dao_refuses_start_before_any_insertion() = checked {
        failure("SURVEY_CONTEXT_NOT_CONFIGURED") {
            repository(contextDao = null).startForPlacetteWithContext(PARCEL, PLOT, draft)
        }
        assertNoPair()
    }

    @Test fun missing_read_policy_refuses_start_before_any_insertion() = checked {
        failure("SURVEY_CONTEXT_ACCESS_NOT_CONFIGURED") {
            repository(readPolicy = null).startForPlacetteWithContext(PARCEL, PLOT, draft)
        }
        assertNoPair()
    }

    @Test fun unknown_parent_missing_plot_wrong_plot_parent_and_blank_plot_refuse_start() = checked {
        database.parcelleDao().insertParcelle(parcel().copy(id = "TEST-other-parent").toParcelleEntity())
        for ((parcelId, plotId) in listOf("TEST-absent-parent" to PLOT, PARCEL to "TEST-absent-plot",
            "TEST-other-parent" to PLOT, PARCEL to "")) {
            assertTrue(runCatching { sessions.startForPlacetteWithContext(parcelId, plotId, draft) }.isFailure)
            assertNoPair()
        }
    }

    @Test fun deleted_plot_refuses_start_without_changing_its_saved_row() = checked {
        database.placetteDao().deletePlacette(PLOT, 200L)
        val before = database.openHelper.readableDatabase.query("SELECT deletedAt FROM placettes WHERE placetteId = ?", arrayOf(PLOT))
            .use { check(it.moveToFirst()); it.getLong(0) }
        assertTrue(runCatching { sessions.startForPlacetteWithContext(PARCEL, PLOT, draft) }.isFailure)
        assertNoPair()
        assertEquals(200L, before)
    }

    @Test fun deleted_parent_refuses_start_without_creating_a_context_or_session() = checked {
        database.parcelleDao().deleteParcelle(PARCEL, 200L)
        assertTrue(runCatching { sessions.startForPlacetteWithContext(PARCEL, PLOT, draft) }.isFailure)
        assertNoPair()
    }

    @Test fun foreign_account_and_guest_cannot_start_even_when_ui_guard_returns_true() = checked {
        for (account in listOf(ACCOUNT_B, null)) {
            identity.login(account)
            assertTrue(runCatching { sessions.startForPlacetteWithContext(PARCEL, PLOT, draft) { true } }.isFailure)
            assertNoPair()
        }
        identity.login(ACCOUNT_A)
        assertInitialPair(start(SurveyPurpose.MARKING), SurveyPurpose.MARKING)
    }

    @Test fun stale_ui_scope_refuses_start_on_historically_shared_local_parent() = checked {
        database.hierarchySyncDao().hardDeleteAssociations()
        identity.login(ACCOUNT_B)
        assertTrue(access.canReadParcelle(PARCEL))
        failure("SURVEY_CONTEXT_REQUEST_SCOPE_CHANGED") {
            sessions.startForPlacetteWithContext(PARCEL, PLOT, draft) { false }
        }
        assertNoPair()
    }

    @Test fun ui_scope_invalidated_after_context_insert_rolls_back_the_whole_pair() = checked {
        var currentScope = true
        val intercepted = object : SurveyContextDao by database.surveyContextDao() {
            override suspend fun insert(context: SurveyContextEntity) {
                database.surveyContextDao().insert(context)
                assertEquals(1, tableCount("inventaire_sessions"))
                assertEquals(1, tableCount("survey_contexts"))
                currentScope = false
            }
        }
        failure("SURVEY_CONTEXT_REQUEST_SCOPE_CHANGED") {
            repository(contextDao = intercepted).startForPlacetteWithContext(PARCEL, PLOT, draft) { currentScope }
        }
        assertNoPair()
        reopen()
        assertNoPair()
    }

    @Test fun exception_after_context_insert_rolls_back_both_real_room_rows() = checked {
        val intercepted = object : SurveyContextDao by database.surveyContextDao() {
            override suspend fun insert(context: SurveyContextEntity) {
                database.surveyContextDao().insert(context)
                assertEquals(1, tableCount("survey_contexts"))
                error("TEST-after-context-insert")
            }
        }
        failure("TEST-after-context-insert") {
            repository(contextDao = intercepted).startForPlacetteWithContext(PARCEL, PLOT, draft)
        }
        assertNoPair()
        reopen()
        assertNoPair()
    }

    @Test fun account_changed_after_session_insert_rolls_back_session_and_context() = checked {
        val intercepted = object : InventaireSessionDao by database.inventaireSessionDao() {
            override suspend fun insert(session: InventaireSessionEntity) {
                database.inventaireSessionDao().insert(session)
                identity.login(ACCOUNT_B)
            }
        }
        assertTrue(runCatching {
            repository(sessionDao = intercepted).startForPlacetteWithContext(PARCEL, PLOT, draft)
        }.isFailure)
        assertNoPair()
        identity.login(ACCOUNT_A)
        assertInitialPair(start(SurveyPurpose.MARKING), SurveyPurpose.MARKING)
    }

    @Test fun account_aba_after_context_insert_rolls_back_both_rows() = checked {
        val intercepted = object : SurveyContextDao by database.surveyContextDao() {
            override suspend fun insert(context: SurveyContextEntity) {
                database.surveyContextDao().insert(context)
                identity.login(ACCOUNT_B)
                identity.login(ACCOUNT_A)
            }
        }
        failure("MARTELAGE_ACCOUNT_CHANGED") {
            repository(contextDao = intercepted).startForPlacetteWithContext(PARCEL, PLOT, draft)
        }
        assertNoPair()
    }

    @Test fun captured_author_change_before_transaction_is_not_reassigned_to_new_account() = checked {
        database.hierarchySyncDao().hardDeleteAssociations()
        var reads = 0
        val changingMetadata = MetadataService {
            reads += 1
            if (reads == 2) identity.login(ACCOUNT_B)
            identity.session.value?.accountId
        }
        failure("MARTELAGE_ACCOUNT_CHANGED") {
            repository(actorMetadata = changingMetadata).startForPlacetteWithContext(PARCEL, PLOT, draft)
        }
        assertNoPair()
    }

    @Test fun foreign_pending_owner_added_after_context_insert_rolls_back_pair_and_operation() = checked {
        val operation = ParcelMutationJournal.freeze(
            parcel = parcel().toParcelleEntity(), operation = "UPSERT", accountId = ACCOUNT_B,
            apiOrigin = "https://test.invalid", baseVersion = null, predecessor = null,
            state = "PENDING", now = 200L, operationId = UUID.randomUUID().toString(),
        )
        val intercepted = object : SurveyContextDao by database.surveyContextDao() {
            override suspend fun insert(context: SurveyContextEntity) {
                database.surveyContextDao().insert(context)
                database.parcelSyncOperationDao().append(operation)
            }
        }
        assertTrue(runCatching {
            repository(contextDao = intercepted).startForPlacetteWithContext(PARCEL, PLOT, draft)
        }.isFailure)
        assertNoPair()
        assertNull(database.parcelSyncOperationDao().getByOperationId(operation.operationId))
        assertTrue(access.canReadParcelle(PARCEL))
    }

    @Test fun plot_deleted_after_context_insert_rolls_back_pair_and_deletion() = checked {
        val before = database.placetteDao().getPlacetteById(PLOT)
        val intercepted = object : SurveyContextDao by database.surveyContextDao() {
            override suspend fun insert(context: SurveyContextEntity) {
                database.surveyContextDao().insert(context)
                database.placetteDao().deletePlacette(PLOT, 200L)
            }
        }
        assertTrue(runCatching {
            repository(contextDao = intercepted).startForPlacetteWithContext(PARCEL, PLOT, draft)
        }.isFailure)
        assertNoPair()
        assertEquals(before, database.placetteDao().getPlacetteById(PLOT))
    }

    @Test fun concurrent_start_has_one_complete_pair_and_no_orphan_or_overwrite() = checked {
        val results = coroutineScope {
            listOf(SurveyPurpose.INVENTORY, SurveyPurpose.COMBINED).map { purpose ->
                async(Dispatchers.Default) { runCatching { start(purpose) } }
            }.awaitAll()
        }
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.isFailure })
        val winner = results.first { it.isSuccess }.getOrThrow()
        val saved = checkNotNull(sessions.getContext(winner.sessionId))
        assertEquals(1, saved.revision)
        assertTrue(saved.draft.purpose in setOf(SurveyPurpose.INVENTORY, SurveyPurpose.COMBINED))
        assertEquals(1, tableCount("inventaire_sessions"))
        assertEquals(1, tableCount("survey_contexts"))
        assertEquals(listOf(winner.sessionId), database.inventaireSessionDao().getActiveForPlacette(PARCEL, PLOT).map { it.sessionId })
        reopen()
        assertEquals(saved, sessions.getContext(winner.sessionId))
        assertEquals(winner.toEntity(), database.inventaireSessionDao().getById(winner.sessionId))
    }

    @Test fun existing_active_session_refuses_new_mode_without_overwriting_its_context() = checked {
        val first = start(SurveyPurpose.MARKING)
        val before = database.surveyContextDao().getBySession(first.sessionId)
        failure("MARTELAGE_ALREADY_ACTIVE") { start(SurveyPurpose.INVENTORY) }
        assertEquals(before, database.surveyContextDao().getBySession(first.sessionId))
        assertEquals(first.toEntity(), database.inventaireSessionDao().getById(first.sessionId))
        assertEquals(1, tableCount("inventaire_sessions"))
        assertEquals(1, tableCount("survey_contexts"))
    }

    @Test fun reopen_close_and_correction_preserve_exact_context_bytes_and_source_filiation() = checked {
        val session = start(SurveyPurpose.COMBINED)
        val saved = checkNotNull(sessions.getContext(session.sessionId))
        val raw = checkNotNull(database.surveyContextDao().getBySession(session.sessionId))
        reopen()
        assertEquals(saved, sessions.getContext(session.sessionId))
        assertEquals(session.toEntity(), database.inventaireSessionDao().getById(session.sessionId))
        sessions.closeMartelage(session.sessionId, null)
        val closed = checkNotNull(database.inventaireSessionDao().getById(session.sessionId))
        val sealed = checkNotNull(database.surveyContextDao().getBySession(session.sessionId))
        assertEquals(raw.payloadJson, sealed.payloadJson)
        assertEquals(raw.payloadJson, sealed.sealedPayloadJson)
        assertTrue(checkNotNull(sessions.getContext(session.sessionId)).isSealed)
        assertTrue(runCatching {
            sessions.saveContextForScope(session.sessionId, saved.draft.copy(purpose = SurveyPurpose.INVENTORY), 1)
        }.isFailure)
        val correction = sessions.createCorrection(session.sessionId)
        val copied = checkNotNull(sessions.getContext(correction.sessionId))
        assertEquals(saved.draft, copied.draft)
        assertEquals(1, copied.revision)
        assertEquals(session.sessionId, copied.copiedFromSessionId)
        assertEquals(ACCOUNT_A, copied.createdBy)
        assertFalse(copied.isSealed)
        assertEquals(raw.payloadJson, database.surveyContextDao().getBySession(correction.sessionId)?.payloadJson)
        assertEquals(closed, database.inventaireSessionDao().getById(session.sessionId))
        assertEquals(sealed, database.surveyContextDao().getBySession(session.sessionId))
        assertEquals(0, tableCount("tiges"))
        reopen()
        assertEquals(copied, sessions.getContext(correction.sessionId))
        assertEquals(closed.closedSnapshotJson, database.inventaireSessionDao().getById(session.sessionId)?.closedSnapshotJson)
    }

    @Test fun old_start_remains_without_context_and_is_not_backfilled_by_new_start() = checked {
        val old = sessions.startForPlacette(PARCEL, OTHER_PLOT)
        val before = database.inventaireSessionDao().getById(old.sessionId)
        assertNull(sessions.getContext(old.sessionId))
        val newer = start(SurveyPurpose.INVENTORY)
        assertNotNull(sessions.getContext(newer.sessionId))
        assertNull(sessions.getContext(old.sessionId))
        assertEquals(before, database.inventaireSessionDao().getById(old.sessionId))
        assertEquals(2, tableCount("inventaire_sessions"))
        assertEquals(1, tableCount("survey_contexts"))
        reopen()
        assertEquals(before, database.inventaireSessionDao().getById(old.sessionId))
        assertNull(sessions.getContext(old.sessionId))
    }

    @Test fun unknown_boot_count_preserves_paused_recovery_with_the_new_context() = checked {
        val unknownBoot = object : MartelageClock {
            override fun read() = MartelageClockReading(1_000L, 500L, null)
        }
        val session = repository(readingClock = unknownBoot).startForPlacetteWithContext(PARCEL, PLOT, draft)
        assertEquals(MartelageStates.PAUSED, session.martelageState)
        assertTrue(session.recoveryRequired)
        assertNull(session.segmentStartedElapsedMs)
        assertNull(session.segmentBootCount)
        assertEquals(session.toEntity(), database.inventaireSessionDao().getById(session.sessionId))
        assertEquals(draft, sessions.getContext(session.sessionId)?.draft)
        assertEquals(1, tableCount("inventaire_sessions"))
        assertEquals(1, tableCount("survey_contexts"))
    }

    @Test fun scoped_save_refuses_stale_ui_even_when_new_account_can_read_historical_local_data() = checked {
        database.hierarchySyncDao().hardDeleteAssociations()
        val session = start(SurveyPurpose.MARKING)
        val before = database.surveyContextDao().getBySession(session.sessionId)
        identity.login(ACCOUNT_B)
        assertTrue(access.canReadParcelle(PARCEL))
        failure("SURVEY_CONTEXT_REQUEST_SCOPE_CHANGED") {
            sessions.saveContextForScope(session.sessionId, draft.copy(protocolReference = "TEST-other-account"), 1) { false }
        }
        assertEquals(before, database.surveyContextDao().getBySession(session.sessionId))
    }

    @Test fun scoped_save_invalidated_after_insert_rolls_back_on_old_unqualified_session() = checked {
        val old = sessions.startForPlacette(PARCEL, PLOT)
        var scopeCurrent = true
        val intercepted = object : SurveyContextDao by database.surveyContextDao() {
            override suspend fun insert(context: SurveyContextEntity) {
                database.surveyContextDao().insert(context)
                scopeCurrent = false
            }
        }
        failure("SURVEY_CONTEXT_REQUEST_SCOPE_CHANGED") {
            repository(contextDao = intercepted).saveContextForScope(old.sessionId, draft, null) { scopeCurrent }
        }
        assertNull(database.surveyContextDao().getBySession(old.sessionId))
        assertEquals(old.toEntity(), database.inventaireSessionDao().getById(old.sessionId))
        assertEquals(1, tableCount("inventaire_sessions"))
        assertEquals(0, tableCount("survey_contexts"))
    }

    @Test fun scoped_save_invalidated_after_compare_and_set_rolls_back_revision_and_bytes() = checked {
        val session = start(SurveyPurpose.MARKING)
        val before = database.surveyContextDao().getBySession(session.sessionId)
        var scopeCurrent = true
        val intercepted = object : SurveyContextDao by database.surveyContextDao() {
            override suspend fun compareAndSet(sessionId: String, expectedRevision: Int,
                payloadJson: String, updatedAt: Long, updatedBy: String): Int {
                val count = database.surveyContextDao().compareAndSet(sessionId, expectedRevision, payloadJson, updatedAt, updatedBy)
                scopeCurrent = false
                return count
            }
        }
        failure("SURVEY_CONTEXT_REQUEST_SCOPE_CHANGED") {
            repository(contextDao = intercepted).saveContextForScope(session.sessionId,
                draft.copy(protocolReference = "TEST-stale-ui"), 1) { scopeCurrent }
        }
        assertEquals(before, database.surveyContextDao().getBySession(session.sessionId))
    }

    @Test fun scoped_save_keeps_canonical_aba_and_permissions_checks() = checked {
        val session = start(SurveyPurpose.MARKING)
        val before = database.surveyContextDao().getBySession(session.sessionId)
        identity.login(ACCOUNT_B)
        assertTrue(runCatching { sessions.saveContextForScope(session.sessionId, draft, 1) { true } }.isFailure)
        assertEquals(before, database.surveyContextDao().getBySession(session.sessionId))
        identity.login(ACCOUNT_A)
        val intercepted = object : SurveyContextDao by database.surveyContextDao() {
            override suspend fun compareAndSet(sessionId: String, expectedRevision: Int,
                payloadJson: String, updatedAt: Long, updatedBy: String): Int {
                val count = database.surveyContextDao().compareAndSet(sessionId, expectedRevision, payloadJson, updatedAt, updatedBy)
                identity.login(ACCOUNT_B)
                identity.login(ACCOUNT_A)
                return count
            }
        }
        failure("MARTELAGE_ACCOUNT_CHANGED") {
            repository(contextDao = intercepted).saveContextForScope(session.sessionId,
                draft.copy(protocolReference = "TEST-ABA"), 1) { true }
        }
        assertEquals(before, database.surveyContextDao().getBySession(session.sessionId))
    }

    @Test fun scoped_save_uses_the_same_revision_rules_and_preserves_first_author() = checked {
        val session = start(SurveyPurpose.MARKING)
        val first = checkNotNull(sessions.getContext(session.sessionId))
        val updated = sessions.saveContextForScope(session.sessionId, first.draft.copy(inventoryComplete = false), first.revision)
        assertEquals(2, updated.revision)
        assertEquals(first.createdAt, updated.createdAt)
        assertEquals(first.createdBy, updated.createdBy)
        assertEquals(ACCOUNT_A, updated.updatedBy)
        assertEquals(false, updated.draft.inventoryComplete)
        failure("SURVEY_CONTEXT_REVISION_CONFLICT") {
            sessions.saveContextForScope(session.sessionId, first.draft, first.revision)
        }
        assertEquals(updated, sessions.getContext(session.sessionId))
    }

    @Test fun interface_defaults_fail_closed_without_fallback_to_old_start_or_save() = checked {
        val oldStartImplementation = object : InventaireSessionRepository by sessions {
            override suspend fun startForPlacetteWithContext(parcelleId: String, placetteId: String,
                draft: SurveyContextDraft, requestGuard: () -> Boolean): InventaireSession =
                super<InventaireSessionRepository>.startForPlacetteWithContext(parcelleId, placetteId, draft, requestGuard)
        }
        failure("SURVEY_CONTEXT_ATOMIC_START_NOT_IMPLEMENTED") {
            oldStartImplementation.startForPlacetteWithContext(PARCEL, PLOT, draft)
        }
        assertNoPair()
        val old = sessions.startForPlacette(PARCEL, PLOT)
        val oldContextImplementation = object : SurveyContextRepository by sessions {
            override suspend fun saveContextForScope(sessionId: String, draft: SurveyContextDraft,
                expectedRevision: Int?, requestGuard: () -> Boolean): SurveyContextRecord =
                super<SurveyContextRepository>.saveContextForScope(sessionId, draft, expectedRevision, requestGuard)
        }
        failure("SURVEY_CONTEXT_SCOPED_SAVE_NOT_IMPLEMENTED") {
            oldContextImplementation.saveContextForScope(old.sessionId, draft, null)
        }
        assertNull(sessions.getContext(old.sessionId))
        assertEquals(1, tableCount("inventaire_sessions"))
        assertEquals(0, tableCount("survey_contexts"))
    }

    private suspend fun start(purpose: SurveyPurpose) = sessions.startForPlacetteWithContext(PARCEL, PLOT,
        SurveyContextDraft(purpose = purpose))

    private suspend fun assertInitialPair(session: InventaireSession, purpose: SurveyPurpose) {
        assertEquals(MartelageStates.TYPE, session.typeSession)
        assertEquals(MartelageStates.RUNNING, session.martelageState)
        assertEquals(PARCEL, session.parcelleId)
        assertEquals(PLOT, session.placetteId)
        assertEquals(ACCOUNT_A, session.auteur)
        assertEquals(session.toEntity(), database.inventaireSessionDao().getById(session.sessionId))
        val saved = checkNotNull(sessions.getContext(session.sessionId))
        assertEquals(SurveyContextDraft(purpose = purpose), saved.draft)
        assertNull(saved.draft.inventoryComplete)
        assertNull(saved.draft.protocolReference)
        assertNull(saved.draft.populationScope)
        assertNull(saved.draft.inventoriedHorizontalAreaM2)
        assertEquals(1, saved.revision)
        assertEquals(ACCOUNT_A, saved.createdBy)
        assertEquals(ACCOUNT_A, saved.updatedBy)
        assertFalse(saved.isSealed)
        assertEquals(1, tableCount("inventaire_sessions"))
        assertEquals(1, tableCount("survey_contexts"))
    }

    private fun assertNoPair() {
        assertEquals(0, tableCount("inventaire_sessions"))
        assertEquals(0, tableCount("survey_contexts"))
    }

    private suspend fun failure(expectedCode: String, block: suspend () -> Unit) {
        val error = runCatching { block() }.exceptionOrNull()
        assertNotNull(error)
        assertEquals(expectedCode, error?.message)
    }

    private fun checked(block: suspend () -> Unit) = runBlocking { withTimeout(TIMEOUT) { block() } }

    private fun repository(
        sessionDao: InventaireSessionDao = database.inventaireSessionDao(),
        contextDao: SurveyContextDao? = database.surveyContextDao(),
        readPolicy: BusinessReadAccess? = access,
        actorMetadata: MetadataService = metadata,
        readingClock: MartelageClock = clock,
    ) = InventaireSessionRepositoryImpl(sessionDao, actorMetadata, readingClock,
        BusinessMutationGuard(identity)::capture, readPolicy, contextDao)

    private fun open() = Room.databaseBuilder(context, ForestryDatabase::class.java, DB_NAME)
        .addMigrations(*DatabaseMigrations.ALL).build()

    private fun reopen() {
        database.close()
        database = open()
        access = BusinessReadAccess(identity, database.parcelSyncDao())
    }

    private fun tableCount(table: String): Int = database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM $table").use { check(it.moveToFirst()); it.getInt(0) }

    private fun parcel() = Parcelle(
        id = PARCEL, forestId = null, foretId = FOREST, name = "TEST atomic parcel", surfaceHa = 1.25,
        shape = null, slopePct = null, aspect = null, access = null, altitudeM = null,
        objectifType = null, objectifVal = null, tolerancePct = null, samplingMode = null,
        sampleAreaM2 = null, targetSpeciesCsv = null, srid = 4326, remarks = null,
        createdAt = 100L, updatedAt = 100L,
    )

    private fun plot(id: String) = Placette(
        id = id, parcelleId = PARCEL, name = "TEST atomic plot", type = "CIRC",
        rayonM = 10.0, surfaceM2 = 314.159, centerWkt = "POINT (2.2 46.2)",
        createdAt = 100L, updatedAt = 100L,
    )

    private companion object {
        const val DB_NAME = "TEST-survey-atomic-start.db"
        const val TIMEOUT = 20_000L
        const val ACCOUNT_A = "TEST-atomic-owner-a"
        const val ACCOUNT_B = "TEST-atomic-other-b"
        const val FOREST = "66666666-6666-4666-8666-666666666666"
        const val PARCEL = "TEST-atomic-parcel"
        const val PLOT = "TEST-atomic-plot"
        const val OTHER_PLOT = "TEST-atomic-other-plot"
    }
}
