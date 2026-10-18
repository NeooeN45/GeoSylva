package com.forestry.counter.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.local.dao.InventaireSessionDao
import com.forestry.counter.data.local.dao.SurveyContextDao
import com.forestry.counter.data.local.entity.EssenceEntity
import com.forestry.counter.data.local.entity.HierarchySyncAssociationEntity
import com.forestry.counter.data.local.entity.InventaireSessionEntity
import com.forestry.counter.data.local.entity.SurveyContextEntity
import com.forestry.counter.data.mapper.toEntity
import com.forestry.counter.data.mapper.toParcelleEntity
import com.forestry.counter.data.mapper.toPlacetteEntity
import com.forestry.counter.data.mapper.toTigeEntity
import com.forestry.counter.data.repository.InventaireSessionRepositoryImpl
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.data.sync.ParcelMutationJournal
import com.forestry.counter.data.sync.hierarchy.BusinessMutationGuard
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.data.sync.hierarchy.captureHierarchy
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.InventaireSession
import com.forestry.counter.domain.model.MartelageClock
import com.forestry.counter.domain.model.MartelageClockReading
import com.forestry.counter.domain.model.MartelageSnapshotCodec
import com.forestry.counter.domain.model.MartelageSnapshotV1
import com.forestry.counter.domain.model.MartelageStates
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.SurveyContextCodec
import com.forestry.counter.domain.model.SurveyContextDraft
import com.forestry.counter.domain.model.SurveyPurpose
import com.forestry.counter.domain.model.Tige
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Transactions Room réelles dans une base TEST dédiée, avec identités TEST sans jeton.
 * Aucun dossier utilisateur, compte réel, préférence ou service HTTP ne participe aux tests.
 */
@RunWith(AndroidJUnit4::class)
class SurveyContextRepositoryRoomTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: ForestryDatabase
    private lateinit var access: BusinessReadAccess
    private val identity = BusinessReadIdentityFixture()
    private val metadata = MetadataService { identity.session.value?.accountId }
    private val clock = TestClock()
    private val mutationGuard get() = BusinessMutationGuard(identity)::capture
    private val sessions get() = repository()

    @Before fun setup() = runBlocking {
        context.deleteDatabase(DB_NAME)
        identity.login(ACCOUNT_A)
        clock.reading = MartelageClockReading(1_000L, 500L, 7)
        database = open()
        access = BusinessReadAccess(identity, database.parcelSyncDao())
        database.foretDao().insert(Foret(
            foretId = FOREST, nom = "TEST context forest", proprietaireNom = "TEST",
            proprietaireEmail = null, gestionnaireNom = null, typeForet = null,
            objectifGestion = null, psgNumero = null, psgDateExpiration = null,
            departement = null, remarques = null, createdAt = 100L, updatedAt = 100L,
        ).toEntity())
        database.parcelleDao().insertParcelle(parcel().toParcelleEntity())
        database.placetteDao().insertNewPlacettes(listOf(plot(PLOT).toPlacetteEntity(), plot(OTHER_PLOT).toPlacetteEntity()))
        database.essenceDao().insertEssence(EssenceEntity(ESSENCE, "TEST oak", null, null, null))
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

    @Test fun partial_combined_context_survives_reopen_without_reinterpreting_session_or_tree() = runBlocking {
        withTimeout(TIMEOUT) {
            val session = sessions.startForPlacette(PARCEL, PLOT)
            val existingTree = tree("TEST-context-tree", session.sessionId).copy(hauteurM = 18.75)
            database.tigeDao().insertTige(existingTree.toTigeEntity())
            val sessionBefore = database.inventaireSessionDao().getById(session.sessionId)
            val treeBefore = database.tigeDao().getTigeById(existingTree.id)
            val draft = SurveyContextDraft(purpose = SurveyPurpose.COMBINED)

            val saved = sessions.saveContext(session.sessionId, draft, expectedRevision = null)
            assertEquals(1, saved.revision)
            assertEquals(ACCOUNT_A, saved.createdBy)
            assertEquals(ACCOUNT_A, saved.updatedBy)
            assertFalse(saved.isSealed)
            assertNull(saved.draft.inventoryComplete)
            assertNull(saved.draft.populationScope)
            assertNull(saved.draft.protocolReference)
            assertNull(saved.draft.inventoriedHorizontalAreaM2)

            reopen()
            assertEquals(saved, sessions.getContext(session.sessionId))
            assertEquals(sessionBefore, database.inventaireSessionDao().getById(session.sessionId))
            assertEquals(treeBefore, database.tigeDao().getTigeById(existingTree.id))
            assertEquals(MartelageStates.TYPE, sessions.getById(session.sessionId)?.typeSession)
            assertEquals(1, database.inventaireSessionDao().getLiveTrees(session.sessionId).size)
        }
    }

    @Test fun explicit_false_is_persisted_without_default_protocol_population_or_area() = runBlocking {
        withTimeout(TIMEOUT) {
            val session = sessions.startForPlacette(PARCEL, PLOT)
            val first = sessions.saveContext(session.sessionId, SurveyContextDraft(), null)
            assertNull(first.draft.inventoryComplete)
            clock.advance(125L)
            val second = sessions.saveContext(session.sessionId,
                first.draft.copy(purpose = SurveyPurpose.INVENTORY, inventoryComplete = false), first.revision)
            assertEquals(2, second.revision)
            assertEquals(first.createdAt, second.createdAt)
            assertEquals(first.createdBy, second.createdBy)
            assertEquals(false, second.draft.inventoryComplete)
            assertNull(second.draft.populationReference)
            assertNull(second.draft.populationScope)
            assertNull(second.draft.protocolReference)
            assertNull(second.draft.inventoriedHorizontalAreaM2)
            reopen()
            assertEquals(second, sessions.getContext(session.sessionId))
        }
    }

    @Test fun concurrent_compare_and_set_has_one_winner_and_no_blind_replacement() = runBlocking {
        withTimeout(TIMEOUT) {
            val session = sessions.startForPlacette(PARCEL, PLOT)
            val first = sessions.saveContext(session.sessionId, SurveyContextDraft(), null)
            val candidates = listOf(SurveyContextDraft(purpose = SurveyPurpose.INVENTORY),
                SurveyContextDraft(purpose = SurveyPurpose.MARKING))
            val attempts = coroutineScope {
                candidates.map { draft -> async(Dispatchers.IO) {
                    runCatching { sessions.saveContext(session.sessionId, draft, first.revision) }
                } }.awaitAll()
            }
            assertEquals(1, attempts.count { it.isSuccess })
            val winner = attempts.single { it.isSuccess }.getOrThrow()
            assertEquals(2, winner.revision)
            assertEquals(winner, sessions.getContext(session.sessionId))
            assertTrue(runCatching { sessions.saveContext(session.sessionId, SurveyContextDraft(), first.revision) }.isFailure)
            assertTrue(runCatching { sessions.saveContext(session.sessionId, SurveyContextDraft(), null) }.isFailure)
            assertEquals(winner, sessions.getContext(session.sessionId))

            val empty = sessions.startForPlacette(PARCEL, OTHER_PLOT)
            for (revision in listOf(-1, 0, 1)) {
                assertTrue(runCatching { sessions.saveContext(empty.sessionId, SurveyContextDraft(), revision) }.isFailure)
                assertNull(database.surveyContextDao().getBySession(empty.sessionId))
            }
        }
    }

    @Test fun concurrent_first_save_creates_only_one_declaration() = runBlocking {
        withTimeout(TIMEOUT) {
            val session = sessions.startForPlacette(PARCEL, PLOT)
            val attempts = coroutineScope {
                listOf(SurveyPurpose.INVENTORY, SurveyPurpose.MARKING).map { purpose ->
                    async(Dispatchers.IO) {
                        runCatching { sessions.saveContext(session.sessionId, SurveyContextDraft(purpose = purpose), null) }
                    }
                }.awaitAll()
            }
            assertEquals(1, attempts.count { it.isSuccess })
            val winner = attempts.single { it.isSuccess }.getOrThrow()
            assertEquals(1, winner.revision)
            assertEquals(winner, sessions.getContext(session.sessionId))
            assertEquals(1, contextCount())
        }
    }

    @Test fun context_for_another_plot_remains_independent() = runBlocking {
        withTimeout(TIMEOUT) {
            val first = sessions.startForPlacette(PARCEL, PLOT)
            val second = sessions.startForPlacette(PARCEL, OTHER_PLOT)
            val firstRecord = sessions.saveContext(first.sessionId, SurveyContextDraft(purpose = SurveyPurpose.INVENTORY), null)
            val secondRecord = sessions.saveContext(second.sessionId, SurveyContextDraft(purpose = SurveyPurpose.MARKING), null)
            sessions.closeMartelage(first.sessionId, null)
            assertTrue(sessions.getContext(first.sessionId)!!.isSealed)
            assertEquals(firstRecord.draft, sessions.getContext(first.sessionId)!!.draft)
            assertEquals(secondRecord, sessions.getContext(second.sessionId))
            assertEquals(MartelageStates.RUNNING, sessions.getById(second.sessionId)?.martelageState)
            val updated = sessions.saveContext(second.sessionId,
                secondRecord.draft.copy(inventoryComplete = false), secondRecord.revision)
            assertEquals(2, updated.revision)
            assertFalse(updated.isSealed)
        }
    }

    @Test fun close_seals_exact_payload_retry_keeps_bytes_and_correction_has_fresh_provenance() = runBlocking {
        withTimeout(TIMEOUT) {
            val session = sessions.startForPlacette(PARCEL, PLOT)
            val draft = SurveyContextDraft(purpose = SurveyPurpose.COMBINED, inventoryComplete = false,
                sourceReference = "TEST-original-observation")
            // L'auteur historique ne confère aucun droit ; le parent appartient toujours à A.
            val originalPayload = "\n  " + SurveyContextCodec.encode(draft) + "  \n"
            database.surveyContextDao().insert(SurveyContextEntity(
                sessionId = session.sessionId, revision = 3, payloadJson = originalPayload,
                createdAt = 200L, createdBy = ACCOUNT_B, updatedAt = 300L, updatedBy = ACCOUNT_B,
            ))
            val before = database.surveyContextDao().getBySession(session.sessionId)!!
            clock.advance(500L)
            val closed = sessions.closeMartelage(session.sessionId, null)
            val sealed = database.surveyContextDao().getBySession(session.sessionId)!!
            assertEquals(before.copy(sealedPayloadJson = originalPayload), sealed)
            assertEquals(originalPayload, sealed.sealedPayloadJson)
            assertTrue(sessions.getContext(session.sessionId)!!.isSealed)
            assertEquals(draft, sessions.getContext(session.sessionId)!!.draft)
            assertFalse(Json.parseToJsonElement(closed.closedSnapshotJson!!).jsonObject.containsKey("surveyContext"))
            assertFalse(closed.closedSnapshotJson!!.contains("TEST-original-observation"))
            assertTrue(runCatching { sessions.saveContext(session.sessionId, draft.copy(inventoryComplete = true), 3) }.isFailure)
            clock.advance(250L)
            assertEquals(closed, sessions.closeMartelage(session.sessionId, null))
            assertEquals(sealed, database.surveyContextDao().getBySession(session.sessionId))
            reopen()
            assertEquals(closed.closedSnapshotJson, sessions.getById(session.sessionId)?.closedSnapshotJson)
            assertEquals(sealed, database.surveyContextDao().getBySession(session.sessionId))

            clock.advance(1_000L)
            val correction = sessions.createCorrection(session.sessionId)
            val copied = sessions.getContext(correction.sessionId)!!
            assertEquals(1, copied.revision)
            assertEquals(session.sessionId, copied.copiedFromSessionId)
            assertEquals(draft, copied.draft)
            assertFalse(copied.isSealed)
            assertEquals(ACCOUNT_A, copied.createdBy)
            assertEquals(ACCOUNT_A, copied.updatedBy)
            assertTrue(copied.createdAt > before.createdAt)
            assertEquals(copied.createdAt, copied.updatedAt)
            assertEquals(originalPayload, database.surveyContextDao().getBySession(correction.sessionId)?.payloadJson)
            assertEquals(sealed, database.surveyContextDao().getBySession(session.sessionId))
            assertEquals(closed.closedSnapshotJson, sessions.getById(session.sessionId)?.closedSnapshotJson)
        }
    }

    @Test fun close_and_correction_without_context_never_backfill_unknown_scientific_facts() = runBlocking {
        withTimeout(TIMEOUT) {
            val session = sessions.startForPlacette(PARCEL, PLOT)
            assertNull(sessions.getContext(session.sessionId))
            val closed = sessions.closeMartelage(session.sessionId, null)
            val snapshotBytes = closed.closedSnapshotJson
            val correction = sessions.createCorrection(session.sessionId)
            assertNull(sessions.getContext(session.sessionId))
            assertNull(sessions.getContext(correction.sessionId))
            assertEquals(0, contextCount())
            assertEquals(snapshotBytes, sessions.getById(session.sessionId)?.closedSnapshotJson)
        }
    }

    @Test fun closed_context_without_a_seal_is_rejected_without_creating_a_correction() = runBlocking {
        withTimeout(TIMEOUT) {
            val session = sessions.startForPlacette(PARCEL, PLOT)
            val closed = sessions.closeMartelage(session.sessionId, null)
            database.surveyContextDao().insert(SurveyContextEntity(
                sessionId = session.sessionId, revision = 1,
                payloadJson = SurveyContextCodec.encode(SurveyContextDraft()),
                createdAt = 100L, createdBy = ACCOUNT_A, updatedAt = 100L, updatedBy = ACCOUNT_A,
            ))
            val invalid = database.surveyContextDao().getBySession(session.sessionId)
            assertTrue(runCatching { sessions.getContext(session.sessionId) }.isFailure)
            assertTrue(runCatching { sessions.closeMartelage(session.sessionId, null) }.isFailure)
            assertTrue(runCatching { sessions.createCorrection(session.sessionId) }.isFailure)
            assertEquals(1, database.inventaireSessionDao().getCampaignRevisions(session.campaignId!!).size)
            assertEquals(invalid, database.surveyContextDao().getBySession(session.sessionId))
            assertEquals(closed.closedSnapshotJson, sessions.getById(session.sessionId)?.closedSnapshotJson)
        }
    }

    @Test fun payload_tampering_after_seal_is_refused_instead_of_reusing_the_changed_draft() = runBlocking {
        withTimeout(TIMEOUT) {
            val session = sessions.startForPlacette(PARCEL, PLOT)
            sessions.saveContext(session.sessionId, SurveyContextDraft(purpose = SurveyPurpose.INVENTORY), null)
            val closed = sessions.closeMartelage(session.sessionId, null)
            val sealed = database.surveyContextDao().getBySession(session.sessionId)!!
            val changedPayload = SurveyContextCodec.encode(SurveyContextDraft(purpose = SurveyPurpose.MARKING))
            // Corruption volontaire de cette base TEST pour vérifier les contrôles d'intégrité.
            database.openHelper.writableDatabase.execSQL(
                "UPDATE survey_contexts SET payloadJson = ? WHERE sessionId = ?",
                arrayOf(changedPayload, session.sessionId),
            )
            assertEquals(sealed.sealedPayloadJson, database.surveyContextDao().getBySession(session.sessionId)?.sealedPayloadJson)
            assertTrue(runCatching { sessions.getContext(session.sessionId) }.isFailure)
            assertTrue(runCatching { sessions.closeMartelage(session.sessionId, null) }.isFailure)
            assertTrue(runCatching { sessions.createCorrection(session.sessionId) }.isFailure)
            assertEquals(1, contextCount())
            assertEquals(1, database.inventaireSessionDao().getCampaignRevisions(session.campaignId!!).size)
            assertEquals(closed.closedSnapshotJson, sessions.getById(session.sessionId)?.closedSnapshotJson)
        }
    }

    @Test fun foreign_account_and_logout_cannot_read_write_close_or_clone_a_linked_context() = runBlocking {
        withTimeout(TIMEOUT) {
            val running = sessions.startForPlacette(PARCEL, PLOT)
            val draft = SurveyContextDraft(purpose = SurveyPurpose.INVENTORY)
            val saved = sessions.saveContext(running.sessionId, draft, null)
            for (account in listOf(ACCOUNT_B, null)) {
                identity.login(account)
                assertNull(sessions.getContext(running.sessionId))
                assertTrue(runCatching { sessions.saveContext(running.sessionId, draft, saved.revision) }.isFailure)
                assertTrue(runCatching { sessions.closeMartelage(running.sessionId, null) }.isFailure)
                assertEquals(saved, database.surveyContextDao().getBySession(running.sessionId)!!.toRecord())
            }
            identity.login(ACCOUNT_A)
            sessions.closeMartelage(running.sessionId, null)
            val sealed = database.surveyContextDao().getBySession(running.sessionId)
            identity.login(ACCOUNT_B)
            assertNull(sessions.getContext(running.sessionId))
            assertTrue(runCatching { sessions.createCorrection(running.sessionId) }.isFailure)
            assertEquals(1, database.inventaireSessionDao().getCampaignRevisions(running.campaignId!!).size)
            assertEquals(sealed, database.surveyContextDao().getBySession(running.sessionId))
        }
    }

    @Test fun pending_journal_ownership_alone_masks_foreign_reads_and_refuses_writes_close_and_correction() = runBlocking {
        withTimeout(TIMEOUT) {
            val closedSession = sessions.startForPlacette(PARCEL, PLOT)
            sessions.saveContext(closedSession.sessionId, SurveyContextDraft(purpose = SurveyPurpose.INVENTORY), null)
            sessions.closeMartelage(closedSession.sessionId, null)
            val running = sessions.startForPlacette(PARCEL, OTHER_PLOT)
            val saved = sessions.saveContext(running.sessionId, SurveyContextDraft(purpose = SurveyPurpose.MARKING), null)
            val closedBefore = database.inventaireSessionDao().getById(closedSession.sessionId)
            val runningBefore = database.inventaireSessionDao().getById(running.sessionId)
            val runningContextBefore = database.surveyContextDao().getBySession(running.sessionId)
            val sealedBefore = database.surveyContextDao().getBySession(closedSession.sessionId)

            database.hierarchySyncDao().hardDeleteAssociations()
            val operation = pendingParcelOperation(ACCOUNT_A)
            database.parcelSyncOperationDao().append(operation)
            assertTrue(database.inventaireSessionDao().getLinkedAccounts(PARCEL).isEmpty())
            assertTrue(database.parcelSyncDao().getAccountIdsForParcel(PARCEL).isEmpty())
            identity.login(ACCOUNT_B)
            assertFalse(access.canReadParcelle(PARCEL))
            assertNull(sessions.getContext(running.sessionId))
            assertNull(sessions.getContext(closedSession.sessionId))
            assertTrue(runCatching { sessions.saveContext(running.sessionId,
                saved.draft.copy(inventoryComplete = true), saved.revision) }.isFailure)
            assertTrue(runCatching { sessions.closeMartelage(running.sessionId, null) }.isFailure)
            assertTrue(runCatching { sessions.createCorrection(closedSession.sessionId) }.isFailure)
            assertEquals(runningBefore, database.inventaireSessionDao().getById(running.sessionId))
            assertEquals(closedBefore, database.inventaireSessionDao().getById(closedSession.sessionId))
            assertEquals(runningContextBefore, database.surveyContextDao().getBySession(running.sessionId))
            assertEquals(sealedBefore, database.surveyContextDao().getBySession(closedSession.sessionId))
            assertEquals(2, contextCount())
            assertEquals(listOf(operation.operationId), database.parcelSyncOperationDao()
                .getAllForParcel(ACCOUNT_A, PARCEL).map { it.operationId })

            identity.login(ACCOUNT_A)
            assertEquals(saved, sessions.getContext(running.sessionId))
            assertTrue(sessions.getContext(closedSession.sessionId)!!.isSealed)
        }
    }

    @Test fun foreign_pending_operation_added_after_context_update_rolls_back_with_the_update() = runBlocking {
        withTimeout(TIMEOUT) {
            val session = sessions.startForPlacette(PARCEL, PLOT)
            val saved = sessions.saveContext(session.sessionId, SurveyContextDraft(), null)
            val before = database.surveyContextDao().getBySession(session.sessionId)
            val foreignOperation = pendingParcelOperation(ACCOUNT_B)
            val intercepted = object : SurveyContextDao by database.surveyContextDao() {
                override suspend fun compareAndSet(sessionId: String, expectedRevision: Int,
                    payloadJson: String, updatedAt: Long, updatedBy: String): Int {
                    val changed = database.surveyContextDao().compareAndSet(sessionId, expectedRevision,
                        payloadJson, updatedAt, updatedBy)
                    database.parcelSyncOperationDao().append(foreignOperation)
                    return changed
                }
            }
            assertTrue(runCatching { repository(contextDao = intercepted).saveContext(session.sessionId,
                saved.draft.copy(purpose = SurveyPurpose.COMBINED), saved.revision) }.isFailure)
            assertEquals(before, database.surveyContextDao().getBySession(session.sessionId))
            assertNull(database.parcelSyncOperationDao().getByOperationId(foreignOperation.operationId))
            assertEquals(saved, sessions.getContext(session.sessionId))
        }
    }

    @Test fun missing_read_policy_fails_closed_for_owner_foreign_account_and_guest() = runBlocking {
        withTimeout(TIMEOUT) {
            val session = sessions.startForPlacette(PARCEL, PLOT)
            val saved = sessions.saveContext(session.sessionId, SurveyContextDraft(), null)
            val before = database.surveyContextDao().getBySession(session.sessionId)
            for (account in listOf(ACCOUNT_A, ACCOUNT_B, null)) {
                identity.login(account)
                val unconfigured = repository(readPolicy = null)
                assertNull(unconfigured.getContext(session.sessionId))
                assertTrue(runCatching { unconfigured.saveContext(session.sessionId,
                    saved.draft.copy(purpose = SurveyPurpose.INVENTORY), saved.revision) }.isFailure)
                assertEquals(before, database.surveyContextDao().getBySession(session.sessionId))
            }
            identity.login(ACCOUNT_A)
            assertEquals(saved, sessions.getContext(session.sessionId))
        }
    }

    @Test fun same_account_reauthentication_during_context_read_does_not_expose_a_stale_record() = runBlocking {
        withTimeout(TIMEOUT) {
            val session = sessions.startForPlacette(PARCEL, PLOT)
            val saved = sessions.saveContext(session.sessionId, SurveyContextDraft(), null)
            val intercepted = object : SurveyContextDao by database.surveyContextDao() {
                override suspend fun getBySession(sessionId: String): SurveyContextEntity? {
                    val row = database.surveyContextDao().getBySession(sessionId)
                    identity.login(ACCOUNT_A)
                    return row
                }
            }
            assertNull(repository(contextDao = intercepted).getContext(session.sessionId))
            assertEquals(saved, sessions.getContext(session.sessionId))
        }
    }

    @Test fun generation_change_before_context_write_does_not_commit_a_new_row() = runBlocking {
        withTimeout(TIMEOUT) {
            val session = sessions.startForPlacette(PARCEL, PLOT)
            val intercepted = object : InventaireSessionDao by database.inventaireSessionDao() {
                override suspend fun getById(id: String): InventaireSessionEntity? {
                    val row = database.inventaireSessionDao().getById(id)
                    identity.login(ACCOUNT_A)
                    return row
                }
            }
            assertTrue(runCatching { repository(sessionDao = intercepted)
                .saveContext(session.sessionId, SurveyContextDraft(), null) }.isFailure)
            assertNull(database.surveyContextDao().getBySession(session.sessionId))
            assertEquals(session.toEntity(), database.inventaireSessionDao().getById(session.sessionId))
        }
    }

    @Test fun generation_change_after_insert_and_compare_and_set_rolls_back_the_declaration() = runBlocking {
        withTimeout(TIMEOUT) {
            val session = sessions.startForPlacette(PARCEL, PLOT)
            val inserting = object : SurveyContextDao by database.surveyContextDao() {
                override suspend fun insert(context: SurveyContextEntity) {
                    database.surveyContextDao().insert(context)
                    identity.login(ACCOUNT_A)
                }
            }
            assertTrue(runCatching { repository(contextDao = inserting)
                .saveContext(session.sessionId, SurveyContextDraft(), null) }.isFailure)
            assertNull(database.surveyContextDao().getBySession(session.sessionId))

            val first = sessions.saveContext(session.sessionId, SurveyContextDraft(), null)
            val before = database.surveyContextDao().getBySession(session.sessionId)
            val updating = object : SurveyContextDao by database.surveyContextDao() {
                override suspend fun compareAndSet(sessionId: String, expectedRevision: Int,
                    payloadJson: String, updatedAt: Long, updatedBy: String): Int {
                    val changed = database.surveyContextDao().compareAndSet(sessionId, expectedRevision,
                        payloadJson, updatedAt, updatedBy)
                    identity.login(ACCOUNT_A)
                    return changed
                }
            }
            assertTrue(runCatching { repository(contextDao = updating).saveContext(session.sessionId,
                first.draft.copy(purpose = SurveyPurpose.COMBINED), first.revision) }.isFailure)
            assertEquals(before, database.surveyContextDao().getBySession(session.sessionId))
            assertEquals(first, sessions.getContext(session.sessionId))
        }
    }

    @Test fun late_generation_change_rolls_back_both_close_and_correction_with_their_sidecars() = runBlocking {
        withTimeout(TIMEOUT) {
            val session = sessions.startForPlacette(PARCEL, PLOT)
            sessions.saveContext(session.sessionId, SurveyContextDraft(purpose = SurveyPurpose.COMBINED), null)
            database.tigeDao().insertTige(tree("TEST-correction-source", session.sessionId).toTigeEntity())
            val sessionBefore = database.inventaireSessionDao().getById(session.sessionId)
            val contextBefore = database.surveyContextDao().getBySession(session.sessionId)
            val sealing = object : SurveyContextDao by database.surveyContextDao() {
                override suspend fun seal(sessionId: String, expectedRevision: Int): Int {
                    val changed = database.surveyContextDao().seal(sessionId, expectedRevision)
                    identity.login(ACCOUNT_A)
                    return changed
                }
            }
            assertTrue(runCatching { repository(contextDao = sealing).closeMartelage(session.sessionId, null) }.isFailure)
            assertEquals(sessionBefore, database.inventaireSessionDao().getById(session.sessionId))
            assertEquals(contextBefore, database.surveyContextDao().getBySession(session.sessionId))
            assertNull(sessions.getClosedSnapshot(session.sessionId))

            sessions.closeMartelage(session.sessionId, null)
            val sealed = database.surveyContextDao().getBySession(session.sessionId)
            val closed = database.inventaireSessionDao().getById(session.sessionId)
            val copying = object : SurveyContextDao by database.surveyContextDao() {
                override suspend fun insert(context: SurveyContextEntity) {
                    database.surveyContextDao().insert(context)
                    identity.login(ACCOUNT_A)
                }
            }
            assertTrue(runCatching { repository(contextDao = copying).createCorrection(session.sessionId) }.isFailure)
            assertEquals(listOf(session.sessionId), database.inventaireSessionDao()
                .getCampaignRevisions(session.campaignId!!).map { it.sessionId })
            assertEquals(1, contextCount())
            assertEquals(1, database.tigeDao().getAllTigesNow().size)
            assertEquals(closed, database.inventaireSessionDao().getById(session.sessionId))
            assertEquals(sealed, database.surveyContextDao().getBySession(session.sessionId))
        }
    }

    @Test fun deleted_parent_masks_context_and_refuses_mutation_without_changing_saved_bytes() = runBlocking {
        withTimeout(TIMEOUT) {
            val session = sessions.startForPlacette(PARCEL, PLOT)
            val saved = sessions.saveContext(session.sessionId, SurveyContextDraft(), null)
            val before = database.surveyContextDao().getBySession(session.sessionId)
            // Le DAO simule un parent supprimé ; les gardes du cycle normal ont leurs tests propres.
            database.parcelleDao().deleteParcelle(PARCEL, 2_000L)
            assertNull(sessions.getContext(session.sessionId))
            assertTrue(runCatching { sessions.saveContext(session.sessionId,
                saved.draft.copy(purpose = SurveyPurpose.INVENTORY), saved.revision) }.isFailure)
            assertEquals(before, database.surveyContextDao().getBySession(session.sessionId))
        }
    }

    @Test fun historical_unscoped_session_is_never_claimed_or_reencoded() = runBlocking {
        withTimeout(TIMEOUT) {
            val snapshot = MartelageSnapshotV1(
                sessionId = LEGACY_SESSION, parcelleId = PARCEL, campaignId = LEGACY_SESSION,
                revisionNumber = 1, previousSessionId = null, startedAt = 100L,
                closedAt = 200L, activeDurationMs = 50L, auteur = ACCOUNT_A,
                parcelle = parcel(), placettes = listOf(plot(PLOT)), tiges = emptyList(),
            )
            val originalBytes = "\n " + MartelageSnapshotCodec.encode(snapshot) + " \n"
            val legacy = InventaireSession(
                sessionId = LEGACY_SESSION, parcelleId = PARCEL, typeSession = MartelageStates.TYPE,
                dateDebut = 100L, dateFin = 200L, operateurNom = null, methode = null,
                intensiteEchantillonnagePct = null, objectifSession = null, remarques = null,
                martelageState = MartelageStates.CLOSED, campaignId = LEGACY_SESSION,
                activeDurationMs = 50L, closedSnapshotJson = originalBytes, auteur = ACCOUNT_A,
            )
            database.inventaireSessionDao().insert(legacy.toEntity())
            val before = database.inventaireSessionDao().getById(LEGACY_SESSION)
            assertNull(sessions.getContext(LEGACY_SESSION))
            assertEquals(snapshot, sessions.getClosedSnapshot(LEGACY_SESSION))
            assertTrue(runCatching { sessions.saveContext(LEGACY_SESSION,
                SurveyContextDraft(purpose = SurveyPurpose.INVENTORY), null) }.isFailure)
            assertTrue(runCatching { sessions.createCorrection(LEGACY_SESSION) }.isFailure)
            assertEquals(before, database.inventaireSessionDao().getById(LEGACY_SESSION))
            assertEquals(originalBytes, database.inventaireSessionDao().getById(LEGACY_SESSION)?.closedSnapshotJson)
            assertEquals(0, contextCount())
            val oldActive = legacy.copy(sessionId = "TEST-survey-legacy-active", dateFin = null,
                closedSnapshotJson = null, campaignId = "TEST-survey-legacy-active",
                martelageState = MartelageStates.PAUSED)
            database.inventaireSessionDao().insert(oldActive.toEntity())
            assertTrue(runCatching { sessions.saveContext(oldActive.sessionId,
                SurveyContextDraft(purpose = SurveyPurpose.COMBINED), null) }.isFailure)
            assertNull(sessions.getContext(oldActive.sessionId))
            assertEquals(oldActive.toEntity(), database.inventaireSessionDao().getById(oldActive.sessionId))
            assertEquals(0, contextCount())
            reopen()
            assertNull(sessions.getContext(LEGACY_SESSION))
            assertNull(sessions.getById(LEGACY_SESSION)?.placetteId)
            assertEquals(originalBytes, database.inventaireSessionDao().getById(LEGACY_SESSION)?.closedSnapshotJson)
        }
    }

    @Test fun local_sidecar_is_not_included_in_existing_hierarchy_transport() = runBlocking {
        withTimeout(TIMEOUT) {
            val session = sessions.startForPlacette(PARCEL, PLOT)
            val before = hierarchyPayload()
            sessions.saveContext(session.sessionId, SurveyContextDraft(purpose = SurveyPurpose.COMBINED,
                protocolReference = "TEST-local-only-protocol-6dd8"), null)
            val after = hierarchyPayload()
            assertEquals(before, after)
            assertFalse(after.contains("TEST-local-only-protocol-6dd8"))
            assertFalse(Json.parseToJsonElement(after).jsonObject.containsKey("surveyContexts"))
            assertNotNull(sessions.getContext(session.sessionId))
            // Ce test constate l'absence de transport, sans prouver une sauvegarde GSIE complète.
        }
    }

    private fun repository(
        sessionDao: InventaireSessionDao = database.inventaireSessionDao(),
        contextDao: SurveyContextDao = database.surveyContextDao(),
        readPolicy: BusinessReadAccess? = access,
    ) = InventaireSessionRepositoryImpl(sessionDao, metadata, clock, mutationGuard, readPolicy, contextDao)

    private fun pendingParcelOperation(accountId: String) = ParcelMutationJournal.freeze(
        parcel = parcel().toParcelleEntity(), operation = "UPSERT", accountId = accountId,
        apiOrigin = "https://test.invalid", baseVersion = null, predecessor = null,
        state = "PENDING", now = 200L, operationId = UUID.randomUUID().toString(),
    )

    private fun open() = Room.databaseBuilder(context, ForestryDatabase::class.java, DB_NAME)
        .addMigrations(*DatabaseMigrations.ALL).build()

    private fun reopen() {
        database.close()
        database = open()
        access = BusinessReadAccess(identity, database.parcelSyncDao())
    }

    private fun contextCount(): Int = database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM survey_contexts").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private suspend fun hierarchyPayload(): String {
        var payload: String? = null
        database.hierarchySyncDao().transaction {
            payload = captureHierarchy(database.hierarchySyncDao(), FOREST).json
        }
        return checkNotNull(payload)
    }

    private fun parcel() = Parcelle(
        id = PARCEL, forestId = null, foretId = FOREST, name = "TEST context parcel", surfaceHa = 1.25,
        shape = null, slopePct = null, aspect = null, access = null, altitudeM = null,
        objectifType = null, objectifVal = null, tolerancePct = null, samplingMode = null,
        sampleAreaM2 = null, targetSpeciesCsv = null, srid = 4326, remarks = null,
        createdAt = 100L, updatedAt = 100L,
    )

    private fun plot(id: String) = Placette(
        id = id, parcelleId = PARCEL, name = "TEST context plot", type = "CIRC",
        rayonM = 10.0, surfaceM2 = 314.159, centerWkt = "POINT (2.2 46.2)",
        createdAt = 100L, updatedAt = 100L,
    )

    private fun tree(id: String, sessionId: String) = Tige(
        id = id, parcelleId = PARCEL, placetteId = PLOT, essenceCode = ESSENCE, diamCm = 30.0,
        hauteurM = null, gpsWkt = null, precisionM = null, altitudeM = null, note = null,
        produit = null, fCoef = null, valueEur = null, timestamp = 300L, sessionId = sessionId,
    )

    private class TestClock : MartelageClock {
        var reading = MartelageClockReading(1_000L, 500L, 7)
        override fun read() = reading
        fun advance(delta: Long) {
            reading = reading.copy(wallTimeMs = reading.wallTimeMs + delta,
                elapsedRealtimeMs = reading.elapsedRealtimeMs + delta)
        }
    }

    private companion object {
        const val DB_NAME = "TEST-survey-context-repository.db"
        const val TIMEOUT = 20_000L
        const val ACCOUNT_A = "TEST-survey-owner-a"
        const val ACCOUNT_B = "TEST-survey-other-b"
        const val FOREST = "44444444-4444-4444-8444-444444444444"
        const val PARCEL = "TEST-survey-parcel"
        const val PLOT = "TEST-survey-plot"
        const val OTHER_PLOT = "TEST-survey-other-plot"
        const val ESSENCE = "TEST-survey-oak"
        const val LEGACY_SESSION = "TEST-survey-legacy-unscoped"
    }
}
