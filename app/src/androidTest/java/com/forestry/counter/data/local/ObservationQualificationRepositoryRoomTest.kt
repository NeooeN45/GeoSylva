package com.forestry.counter.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.local.dao.InventaireSessionDao
import com.forestry.counter.data.local.dao.ObservationQualificationDao
import com.forestry.counter.data.local.entity.EssenceEntity
import com.forestry.counter.data.local.entity.HierarchySyncAssociationEntity
import com.forestry.counter.data.local.entity.ObservationQualificationEntity
import com.forestry.counter.data.mapper.toEntity
import com.forestry.counter.data.mapper.toParcelleEntity
import com.forestry.counter.data.mapper.toPlacetteEntity
import com.forestry.counter.data.mapper.toTige
import com.forestry.counter.data.mapper.toTigeEntity
import com.forestry.counter.data.repository.InventaireSessionRepositoryImpl
import com.forestry.counter.data.repository.ObservationQualificationRepositoryImpl
import com.forestry.counter.data.repository.TigeRepositoryImpl
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.data.sync.ParcelMutationJournal
import com.forestry.counter.data.sync.hierarchy.BusinessMutationGuard
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.data.sync.hierarchy.captureHierarchy
import com.forestry.counter.domain.model.DiameterQualificationDraft
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.HeightNature
import com.forestry.counter.domain.model.HeightQualificationDraft
import com.forestry.counter.domain.model.InventaireSession
import com.forestry.counter.domain.model.MartelageClock
import com.forestry.counter.domain.model.MartelageClockReading
import com.forestry.counter.domain.model.MartelageStates
import com.forestry.counter.domain.model.MeasurementOrigin
import com.forestry.counter.domain.model.ObservationMeasurementBasis
import com.forestry.counter.domain.model.ObservationQualificationCodec
import com.forestry.counter.domain.model.ObservationQualificationDraft
import com.forestry.counter.domain.model.ObservationQualificationRead
import com.forestry.counter.domain.model.ObservationQualificationStatus
import com.forestry.counter.domain.model.ObservationSupport
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.PositionOrigin
import com.forestry.counter.domain.model.PositionQualificationDraft
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Transactions Room dans une base TEST dédiée, identités TEST sans jeton et aucune requête HTTP.
 * Les altérations SQL ci-dessous concernent seulement cette base de test, jamais un dossier réel.
 */
@RunWith(AndroidJUnit4::class)
class ObservationQualificationRepositoryRoomTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: ForestryDatabase
    private lateinit var access: BusinessReadAccess
    private val identity = BusinessReadIdentityFixture()
    private val metadata = MetadataService { identity.session.value?.accountId }
    private val clock = TestClock()
    private val guard get() = BusinessMutationGuard(identity)::capture
    private val qualifications get() = qualificationRepository()
    private val sessions get() = sessionRepository()
    private val trees get() = TigeRepositoryImpl(database.tigeDao(), metadata, clock, guard, access)

    @Before fun setup() = runBlocking {
        context.deleteDatabase(DB_NAME)
        identity.login(ACCOUNT_A)
        clock.reading = MartelageClockReading(1_000L, 500L, 7)
        database = open()
        access = BusinessReadAccess(identity, database.parcelSyncDao())
        database.foretDao().insert(Foret(
            foretId = FOREST, nom = "TEST forêt qualification", proprietaireNom = "TEST",
            proprietaireEmail = null, gestionnaireNom = null, typeForet = null,
            objectifGestion = null, psgNumero = null, psgDateExpiration = null,
            departement = null, remarques = null, createdAt = 100L, updatedAt = 100L,
        ).toEntity())
        database.parcelleDao().insertParcelle(parcel().toParcelleEntity())
        database.placetteDao().insertNewPlacettes(listOf(plot(PLOT).toPlacetteEntity()))
        database.essenceDao().insertEssence(EssenceEntity(ESSENCE, "TEST chêne", null, null, null))
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

    @Test fun partial_declaration_survives_reopen_and_preserves_raw_unknown_fields() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree().copy(hauteurM = null, gpsWkt = null, precisionM = null, altitudeM = null)
            database.tigeDao().updateTige(tree.toTigeEntity())
            val sessionBefore = database.inventaireSessionDao().getById(tree.sessionId!!)
            val treeBefore = database.tigeDao().getTigeById(tree.id)
            val saved = save(tree, ObservationQualificationDraft())
            assertEquals(ObservationQualificationStatus.CURRENT, saved.status)
            assertEquals(1, saved.record.revision)
            assertEquals(ACCOUNT_A, saved.record.createdBy)
            assertEquals(ACCOUNT_A, saved.record.updatedBy)
            assertFalse(saved.record.isSealed)
            assertNull(saved.currentDraft!!.support)
            assertNull(saved.currentDraft!!.diameter)
            assertNull(saved.currentDraft!!.height)
            assertNull(saved.currentDraft!!.position)
            assertEquals(tree.version, saved.record.payload.basis.observationVersion)
            assertNull(saved.record.payload.basis.hauteurM)
            assertNull(saved.record.payload.basis.gpsWkt)
            reopen()
            assertEquals(saved, qualifications.getQualification(tree.id))
            assertEquals(treeBefore, database.tigeDao().getTigeById(tree.id))
            assertEquals(sessionBefore, database.inventaireSessionDao().getById(tree.sessionId!!))
        }
    }

    @Test fun historical_observations_remain_unknown_and_existing_transport_stays_identical() = runBlocking {
        withTimeout(TIMEOUT) {
            val old = tree("TEST-old-unknown", null)
            database.tigeDao().insertTige(old.toTigeEntity())
            assertNull(qualifications.getQualification(old.id))
            assertTrue(runCatching { save(old, individualDraft()) }.isFailure)
            val fresh = startTree()
            val before = hierarchyPayload()
            val saved = save(fresh, individualDraft("TEST-local-qualification-6b0d"))
            assertEquals(before, hierarchyPayload())
            assertFalse(hierarchyPayload().contains("TEST-local-qualification-6b0d"))
            assertFalse(Json.parseToJsonElement(before).jsonObject.containsKey("observationQualifications"))
            assertEquals(saved, qualifications.getQualification(fresh.id))
            assertNull(qualifications.getQualification(old.id))
            assertEquals(old.toTigeEntity(), database.tigeDao().getTigeById(old.id))
            assertEquals(1, qualificationCount())
        }
    }

    @Test fun concurrent_first_save_has_one_winner_without_blind_replacement() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val attempts = coroutineScope {
                listOf("TEST source première", "TEST source seconde").map { source ->
                    async(Dispatchers.IO) { runCatching { save(tree, individualDraft(source)) } }
                }.awaitAll()
            }
            assertEquals(1, attempts.count { it.isSuccess })
            val winner = attempts.single { it.isSuccess }.getOrThrow()
            assertEquals(1, winner.record.revision)
            assertEquals(winner, qualifications.getQualification(tree.id))
            assertTrue(runCatching { save(tree, individualDraft()) }.isFailure)
            assertEquals(1, qualificationCount())
        }
    }

    @Test fun concurrent_compare_and_set_has_one_winner_and_stale_revisions_are_refused() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val first = save(tree, ObservationQualificationDraft())
            val attempts = coroutineScope {
                listOf("TEST deuxième A", "TEST deuxième B").map { source ->
                    async(Dispatchers.IO) {
                        runCatching { save(tree, individualDraft(source), first.record.revision) }
                    }
                }.awaitAll()
            }
            assertEquals(1, attempts.count { it.isSuccess })
            val winner = attempts.single { it.isSuccess }.getOrThrow()
            assertEquals(2, winner.record.revision)
            assertEquals(winner, qualifications.getQualification(tree.id))
            for (revision in listOf(null, 0, -1, first.record.revision)) {
                assertTrue(runCatching { save(tree, ObservationQualificationDraft(), revision) }.isFailure)
            }
            assertEquals(winner, qualifications.getQualification(tree.id))
        }
    }

    @Test fun expected_observation_version_is_checked_before_create_and_update() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            for (version in listOf(0, -1, tree.version + 1)) {
                assertTrue(runCatching {
                    qualifications.saveQualification(tree.id, individualDraft(), version, null)
                }.isFailure)
                assertNull(database.observationQualificationDao().getByTige(tree.id))
            }
            val first = save(tree, individualDraft())
            assertTrue(trees.setTigeHeight(tree.id, 19.0))
            val stale = qualifications.getQualification(tree.id)!!
            assertEquals(ObservationQualificationStatus.STALE, stale.status)
            assertNull(stale.currentDraft)
            assertTrue(runCatching { save(tree, individualDraft(), first.record.revision) }.isFailure)
            assertEquals(first.record, database.observationQualificationDao().getByTige(tree.id)!!.toRecord())
        }
    }

    @Test fun editing_then_restoring_height_does_not_resurrect_the_old_declaration() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val saved = save(tree, individualDraft())
            assertTrue(trees.setTigeHeight(tree.id, 19.0))
            assertEquals(ObservationQualificationStatus.STALE, qualifications.getQualification(tree.id)!!.status)
            assertTrue(trees.setTigeHeight(tree.id, tree.hauteurM))
            val restored = database.tigeDao().getTigeById(tree.id)!!.toTige()
            assertEquals(tree.hauteurM, restored.hauteurM)
            assertEquals(tree.version + 2, restored.version)
            val stillStale = qualifications.getQualification(tree.id)!!
            assertEquals(ObservationQualificationStatus.STALE, stillStale.status)
            assertNull(stillStale.currentDraft)
            assertEquals(saved.record.payload, stillStale.record.payload)
            assertEquals(saved.record, database.observationQualificationDao().getByTige(tree.id)!!.toRecord())
        }
    }

    @Test fun raw_changes_without_version_increment_are_still_detected_by_the_basis() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val saved = save(tree, individualDraft())
            val raw = database.tigeDao().getTigeById(tree.id)!!
            val changes = listOf(
                raw.copy(diamCm = 31.0), raw.copy(hauteurM = 20.0),
                raw.copy(gpsWkt = "POINT (2.21 46.21)"), raw.copy(precisionM = 10.0),
                raw.copy(altitudeM = 120.0), raw.copy(timestamp = raw.timestamp + 1),
            )
            for (changed in changes) {
                // Simule un import par DAO conservant la version ; aucun recalcul ni réécriture latérale.
                database.tigeDao().updateTige(changed)
                val read = qualifications.getQualification(tree.id)!!
                assertEquals(ObservationQualificationStatus.STALE, read.status)
                assertNull(read.currentDraft)
                assertEquals(saved.record, read.record)
            }
        }
    }

    @Test fun observation_moved_to_another_valid_scope_does_not_expose_or_rebind_the_old_declaration() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val saved = save(tree, individualDraft("TEST source de la première placette"))
            val oldRow = database.observationQualificationDao().getByTige(tree.id)!!
            val otherPlot = "TEST-qualification-other-plot"
            database.placetteDao().insertNewPlacettes(listOf(plot(otherPlot).toPlacetteEntity()))
            val otherSession = sessions.startForPlacette(PARCEL, otherPlot)
            val current = database.tigeDao().getTigeById(tree.id)!!
            // Simule un import fautif : même ID réaffecté à une autre portée pourtant autorisée.
            val moved = current.copy(placetteOwnerId = otherPlot, sessionId = otherSession.sessionId,
                version = current.version + 1)
            database.tigeDao().updateTige(moved)
            assertTrue(access.canReadParcelle(PARCEL))
            assertEquals(otherSession.sessionId, database.inventaireSessionDao().getById(otherSession.sessionId)?.sessionId)
            assertEquals(listOf(moved), database.inventaireSessionDao().getLiveTrees(otherSession.sessionId))
            assertNull(qualifications.getQualification(tree.id))
            assertTrue(runCatching {
                qualifications.saveQualification(tree.id, individualDraft("TEST tentative de réaffectation"),
                    moved.version, saved.record.revision)
            }.isFailure)
            assertEquals(oldRow, database.observationQualificationDao().getByTige(tree.id))
            assertEquals(moved, database.tigeDao().getTigeById(tree.id))
            assertEquals(1, qualificationCount())
            val targetSessionBefore = database.inventaireSessionDao().getById(otherSession.sessionId)
            assertTrue(runCatching { sessions.closeMartelage(otherSession.sessionId, null) }.isFailure)
            assertEquals(targetSessionBefore, database.inventaireSessionDao().getById(otherSession.sessionId))
            assertEquals(oldRow, database.observationQualificationDao().getByTige(tree.id))
            assertNull(database.observationQualificationDao().getByTige(tree.id)!!.sealedPayloadJson)
            assertNull(sessions.getClosedSnapshot(otherSession.sessionId))
            // Restaurer la portée ne restaure pas la version : la déclaration reste périmée, non scellée.
            val restored = current.copy(version = moved.version + 1)
            database.tigeDao().updateTige(restored)
            val stale = qualifications.getQualification(tree.id)!!
            assertEquals(ObservationQualificationStatus.STALE, stale.status)
            assertNull(stale.currentDraft)
            assertFalse(stale.record.isSealed)
            assertEquals(oldRow.toRecord(), stale.record)
            assertEquals(MartelageStates.RUNNING, sessions.getById(tree.sessionId!!)?.martelageState)
            reopen()
            assertEquals(stale, qualifications.getQualification(tree.id))
            assertEquals(oldRow, database.observationQualificationDao().getByTige(tree.id))
        }
    }

    @Test fun explicit_requalification_of_current_values_is_allowed_while_paused() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val first = save(tree, individualDraft())
            assertTrue(trees.setTigeHeight(tree.id, 19.0))
            val current = database.tigeDao().getTigeById(tree.id)!!.toTige()
            sessions.pauseMartelage(tree.sessionId!!)
            val requalified = save(current, individualDraft("TEST mesure corrigée"), first.record.revision)
            assertEquals(ObservationQualificationStatus.CURRENT, requalified.status)
            assertEquals(2, requalified.record.revision)
            assertEquals(19.0, requalified.record.payload.basis.hauteurM!!, 0.0)
            assertEquals(current.version, requalified.record.payload.basis.observationVersion)
            assertEquals(first.record.createdAt, requalified.record.createdAt)
            assertEquals(first.record.createdBy, requalified.record.createdBy)
            assertEquals(MartelageStates.PAUSED, sessions.getById(tree.sessionId!!)?.martelageState)
        }
    }

    @Test fun class_reference_height_and_reused_point_are_not_promoted_to_an_individual_pair() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val draft = ObservationQualificationDraft(
                support = ObservationSupport.GROUP_REFERENCE,
                diameter = DiameterQualificationDraft(origin = MeasurementOrigin.ASSIGNED_REFERENCE,
                    conventionReference = "TEST classe de diamètre"),
                height = HeightQualificationDraft(nature = HeightNature.TOTAL,
                    origin = MeasurementOrigin.ASSIGNED_REFERENCE,
                    support = ObservationSupport.GROUP_REFERENCE, pairedWithDiameter = false,
                    sourceReference = "TEST arbre de référence"),
                position = PositionQualificationDraft(origin = PositionOrigin.REUSED_POINT,
                    sourceReference = "TEST point de classe"),
            )
            val saved = save(tree, draft)
            assertEquals(ObservationQualificationStatus.CURRENT, saved.status)
            assertEquals(false, saved.currentDraft!!.height!!.pairedWithDiameter)
            assertEquals(ObservationSupport.GROUP_REFERENCE, saved.currentDraft!!.support)
            assertEquals(PositionOrigin.REUSED_POINT, saved.currentDraft!!.position!!.origin)
            assertNull(saved.currentDraft!!.position!!.capturedAt)
            assertNull(saved.currentDraft!!.height!!.observedAt)
            assertEquals(tree.toTigeEntity().copy(uuid = database.tigeDao().getTigeById(tree.id)!!.uuid),
                database.tigeDao().getTigeById(tree.id))
            reopen()
            assertEquals(saved, qualifications.getQualification(tree.id))
            // CURRENT signifie seulement que l'état brut correspond ; aucun calcul H/D n'est lancé ici.
        }
    }

    @Test fun close_seals_exact_bytes_retry_preserves_them_and_writes_are_refused() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val saved = save(tree, individualDraft("TEST provenance hors snapshot"))
            val original = database.observationQualificationDao().getByTige(tree.id)!!
            val exactPayload = "\n  " + original.payloadJson + " \n"
            database.openHelper.writableDatabase.execSQL(
                "UPDATE observation_qualifications SET payloadJson = ? WHERE tigeId = ?",
                arrayOf(exactPayload, tree.id),
            )
            val before = database.observationQualificationDao().getByTige(tree.id)!!
            clock.advance(500L)
            val closed = sessions.closeMartelage(tree.sessionId!!, null)
            val sealed = database.observationQualificationDao().getByTige(tree.id)!!
            assertEquals(before.copy(sealedPayloadJson = exactPayload), sealed)
            assertEquals(exactPayload, sealed.sealedPayloadJson)
            val read = qualifications.getQualification(tree.id)!!
            assertTrue(read.record.isSealed)
            assertEquals(ObservationQualificationStatus.CURRENT, read.status)
            assertEquals(saved.currentDraft, read.currentDraft)
            assertFalse(closed.closedSnapshotJson!!.contains("TEST provenance hors snapshot"))
            assertFalse(Json.parseToJsonElement(closed.closedSnapshotJson!!).jsonObject.containsKey("observationQualifications"))
            assertTrue(runCatching { save(tree, individualDraft(), saved.record.revision) }.isFailure)
            clock.advance(250L)
            assertEquals(closed, sessions.closeMartelage(tree.sessionId!!, null))
            assertEquals(sealed, database.observationQualificationDao().getByTige(tree.id))
            reopen()
            assertEquals(read, qualifications.getQualification(tree.id))
            assertEquals(closed.closedSnapshotJson, sessions.getById(tree.sessionId!!)?.closedSnapshotJson)
        }
    }

    @Test fun correction_rebinds_current_basis_and_preserves_original_measurement_provenance() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val draft = individualDraft("TEST provenance de mesure originale")
            save(tree, draft)
            // Un auteur historique n'accorde pas de droits : le parent reste autorisé pour A.
            database.openHelper.writableDatabase.execSQL(
                "UPDATE observation_qualifications SET createdAt = ?, createdBy = ?, updatedAt = ?, updatedBy = ?, revision = ? WHERE tigeId = ?",
                arrayOf(200L, ACCOUNT_B, 300L, ACCOUNT_B, 3, tree.id),
            )
            val source = database.observationQualificationDao().getByTige(tree.id)!!
            sessions.closeMartelage(tree.sessionId!!, null)
            val sealed = database.observationQualificationDao().getByTige(tree.id)!!
            val closedBytes = sessions.getById(tree.sessionId!!)?.closedSnapshotJson
            clock.advance(500L)
            val correction = sessions.createCorrection(tree.sessionId!!)
            val cloned = database.inventaireSessionDao().getLiveTrees(correction.sessionId).single().toTige()
            assertNotEquals(tree.id, cloned.id)
            assertEquals(tree.originTigeId ?: tree.id, cloned.originTigeId)
            assertEquals(tree.timestamp, cloned.timestamp)
            assertEquals(tree.hauteurM, cloned.hauteurM)
            assertEquals(1, cloned.version)
            val copied = qualifications.getQualification(cloned.id)!!
            assertEquals(ObservationQualificationStatus.CURRENT, copied.status)
            assertEquals(1, copied.record.revision)
            assertFalse(copied.record.isSealed)
            assertEquals(ACCOUNT_A, copied.record.createdBy)
            assertEquals(ACCOUNT_A, copied.record.updatedBy)
            assertEquals(draft, copied.currentDraft)
            assertEquals(cloned.id, copied.record.payload.basis.tigeId)
            assertEquals(correction.sessionId, copied.record.payload.basis.sessionId)
            assertTrue(copied.record.payload.basis.matches(cloned))
            assertEquals(tree.id, copied.record.payload.copiedFrom!!.tigeId)
            assertEquals(source.revision, copied.record.payload.copiedFrom!!.revision)
            assertEquals(source.updatedAt, copied.record.payload.copiedFrom!!.updatedAt)
            assertEquals(source.updatedBy, copied.record.payload.copiedFrom!!.updatedBy)
            assertEquals(draft.height!!.observedAt, copied.currentDraft!!.height!!.observedAt)
            assertEquals(draft.position!!.capturedAt, copied.currentDraft!!.position!!.capturedAt)
            assertEquals(sealed, database.observationQualificationDao().getByTige(tree.id))
            assertEquals(closedBytes, sessions.getById(tree.sessionId!!)?.closedSnapshotJson)
        }
    }

    @Test fun stale_declaration_is_frozen_as_stale_and_is_not_copied_to_correction() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val saved = save(tree, individualDraft())
            assertTrue(trees.setTigeHeight(tree.id, 19.0))
            val before = database.observationQualificationDao().getByTige(tree.id)!!
            val closed = sessions.closeMartelage(tree.sessionId!!, null)
            val stale = qualifications.getQualification(tree.id)!!
            assertTrue(stale.record.isSealed)
            assertEquals(ObservationQualificationStatus.STALE, stale.status)
            assertNull(stale.currentDraft)
            assertEquals(saved.record.payload, stale.record.payload)
            assertEquals(before.copy(sealedPayloadJson = before.payloadJson),
                database.observationQualificationDao().getByTige(tree.id))
            val correction = sessions.createCorrection(tree.sessionId!!)
            val cloned = database.inventaireSessionDao().getLiveTrees(correction.sessionId).single()
            assertEquals(19.0, cloned.hauteurM!!, 0.0)
            assertNull(qualifications.getQualification(cloned.tigeId))
            assertEquals(1, qualificationCount())
            assertEquals(closed.closedSnapshotJson, sessions.getById(tree.sessionId!!)?.closedSnapshotJson)
        }
    }

    @Test fun missing_declarations_are_not_backfilled_by_close_or_correction() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            sessions.closeMartelage(tree.sessionId!!, null)
            assertNull(qualifications.getQualification(tree.id))
            val correction = sessions.createCorrection(tree.sessionId!!)
            val cloned = database.inventaireSessionDao().getLiveTrees(correction.sessionId).single()
            assertNull(qualifications.getQualification(cloned.tigeId))
            assertEquals(0, qualificationCount())
        }
    }

    @Test fun deleted_observation_keeps_its_seal_but_is_not_read_or_copied_as_a_live_tree() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            save(tree, individualDraft())
            val before = database.observationQualificationDao().getByTige(tree.id)!!
            trees.deleteTige(tree.id)
            assertNull(qualifications.getQualification(tree.id))
            val closed = sessions.closeMartelage(tree.sessionId!!, null)
            assertEquals(before.copy(sealedPayloadJson = before.payloadJson),
                database.observationQualificationDao().getByTige(tree.id))
            assertTrue(sessions.getClosedSnapshot(tree.sessionId!!)!!.tiges.isEmpty())
            val correction = sessions.createCorrection(tree.sessionId!!)
            assertTrue(database.inventaireSessionDao().getLiveTrees(correction.sessionId).isEmpty())
            assertTrue(database.observationQualificationDao().getBySession(correction.sessionId).isEmpty())
            assertEquals(1, qualificationCount())
            assertEquals(closed.closedSnapshotJson, sessions.getById(tree.sessionId!!)?.closedSnapshotJson)
        }
    }

    @Test fun concurrent_close_and_save_never_leave_an_unsealed_declaration_in_a_closed_session() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val first = save(tree, individualDraft("TEST avant clôture"))
            val attempts = coroutineScope {
                val close = async(Dispatchers.IO) { runCatching { sessions.closeMartelage(tree.sessionId!!, null) } }
                val saveAttempt = async(Dispatchers.IO) {
                    runCatching { save(tree, individualDraft("TEST pendant clôture"), first.record.revision) }
                }
                close.await() to saveAttempt.await()
            }
            assertTrue(attempts.first.isSuccess)
            val closed = attempts.first.getOrThrow()
            val final = qualifications.getQualification(tree.id)!!
            val row = database.observationQualificationDao().getByTige(tree.id)!!
            assertEquals(MartelageStates.CLOSED, closed.martelageState)
            assertTrue(final.record.isSealed)
            assertEquals(row.payloadJson, row.sealedPayloadJson)
            if (attempts.second.isSuccess) {
                assertEquals(2, final.record.revision)
                assertEquals(attempts.second.getOrThrow().record.payload, final.record.payload)
            } else {
                assertEquals(first.record.revision, final.record.revision)
                assertEquals(first.record.payload, final.record.payload)
            }
        }
    }

    @Test fun payload_tampering_after_seal_is_refused_and_correction_is_atomic() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            save(tree, individualDraft())
            val closed = sessions.closeMartelage(tree.sessionId!!, null)
            val sealed = database.observationQualificationDao().getByTige(tree.id)!!
            val changed = ObservationQualificationCodec.encode(sealed.toRecord().payload.copy(
                draft = individualDraft("TEST déclaration altérée"),
            ))
            database.openHelper.writableDatabase.execSQL(
                "UPDATE observation_qualifications SET payloadJson = ? WHERE tigeId = ?",
                arrayOf(changed, tree.id),
            )
            assertTrue(runCatching { qualifications.getQualification(tree.id) }.isFailure)
            assertTrue(runCatching { sessions.closeMartelage(tree.sessionId!!, null) }.isFailure)
            assertTrue(runCatching { sessions.createCorrection(tree.sessionId!!) }.isFailure)
            assertEquals(1, database.inventaireSessionDao().getCampaignRevisions(tree.sessionId!!).size)
            assertEquals(1, database.tigeDao().getAllTigesNow().size)
            assertEquals(1, qualificationCount())
            assertEquals(sealed.sealedPayloadJson, database.observationQualificationDao().getByTige(tree.id)?.sealedPayloadJson)
            assertEquals(closed.closedSnapshotJson, sessions.getById(tree.sessionId!!)?.closedSnapshotJson)
        }
    }

    @Test fun closed_declaration_without_seal_is_rejected_without_creating_correction() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            save(tree, individualDraft())
            val closed = sessions.closeMartelage(tree.sessionId!!, null)
            database.openHelper.writableDatabase.execSQL(
                "UPDATE observation_qualifications SET sealedPayloadJson = NULL WHERE tigeId = ?",
                arrayOf(tree.id),
            )
            val invalid = database.observationQualificationDao().getByTige(tree.id)
            assertTrue(runCatching { qualifications.getQualification(tree.id) }.isFailure)
            assertTrue(runCatching { sessions.closeMartelage(tree.sessionId!!, null) }.isFailure)
            assertTrue(runCatching { sessions.createCorrection(tree.sessionId!!) }.isFailure)
            assertEquals(invalid, database.observationQualificationDao().getByTige(tree.id))
            assertEquals(1, database.inventaireSessionDao().getCampaignRevisions(tree.sessionId!!).size)
            assertEquals(closed.closedSnapshotJson, sessions.getById(tree.sessionId!!)?.closedSnapshotJson)
        }
    }

    @Test fun foreign_account_logout_and_pending_only_ownership_do_not_expose_or_mutate_declarations() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val saved = save(tree, individualDraft())
            val before = database.observationQualificationDao().getByTige(tree.id)
            for (account in listOf(ACCOUNT_B, null)) {
                identity.login(account)
                assertNull(qualifications.getQualification(tree.id))
                assertTrue(runCatching { save(tree, individualDraft(), saved.record.revision) }.isFailure)
                assertEquals(before, database.observationQualificationDao().getByTige(tree.id))
            }
            identity.login(ACCOUNT_A)
            database.hierarchySyncDao().hardDeleteAssociations()
            val operation = pendingParcelOperation(ACCOUNT_A)
            database.parcelSyncOperationDao().append(operation)
            assertTrue(database.inventaireSessionDao().getLinkedAccounts(PARCEL).isEmpty())
            identity.login(ACCOUNT_B)
            assertFalse(access.canReadParcelle(PARCEL))
            assertNull(qualifications.getQualification(tree.id))
            assertTrue(runCatching { save(tree, individualDraft(), saved.record.revision) }.isFailure)
            assertEquals(before, database.observationQualificationDao().getByTige(tree.id))
            identity.login(ACCOUNT_A)
            assertEquals(saved, qualifications.getQualification(tree.id))
        }
    }

    @Test fun same_account_generation_change_during_read_masks_the_result() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val saved = save(tree, individualDraft())
            val intercepted = object : ObservationQualificationDao by database.observationQualificationDao() {
                override suspend fun getByTige(tigeId: String): ObservationQualificationEntity? {
                    val row = database.observationQualificationDao().getByTige(tigeId)
                    identity.login(ACCOUNT_A)
                    return row
                }
            }
            assertNull(qualificationRepository(intercepted).getQualification(tree.id))
            assertEquals(saved, qualifications.getQualification(tree.id))
        }
    }

    @Test fun generation_change_after_insert_or_compare_and_set_rolls_back_the_declaration() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val inserting = object : ObservationQualificationDao by database.observationQualificationDao() {
                override suspend fun insert(qualification: ObservationQualificationEntity) {
                    database.observationQualificationDao().insert(qualification)
                    identity.login(ACCOUNT_A)
                }
            }
            assertTrue(runCatching {
                qualificationRepository(inserting).saveQualification(tree.id, individualDraft(), tree.version, null)
            }.isFailure)
            assertNull(database.observationQualificationDao().getByTige(tree.id))
            val first = save(tree, individualDraft())
            val before = database.observationQualificationDao().getByTige(tree.id)
            val updating = object : ObservationQualificationDao by database.observationQualificationDao() {
                override suspend fun compareAndSet(tigeId: String, expectedRevision: Int,
                    payloadJson: String, updatedAt: Long, updatedBy: String): Int {
                    val changed = database.observationQualificationDao().compareAndSet(tigeId, expectedRevision,
                        payloadJson, updatedAt, updatedBy)
                    identity.login(ACCOUNT_A)
                    return changed
                }
            }
            assertTrue(runCatching {
                qualificationRepository(updating).saveQualification(tree.id,
                    individualDraft("TEST changement refusé"), tree.version, first.record.revision)
            }.isFailure)
            assertEquals(before, database.observationQualificationDao().getByTige(tree.id))
            assertEquals(first, qualifications.getQualification(tree.id))
        }
    }

    @Test fun foreign_pending_ownership_added_after_update_rolls_back_with_the_update() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val saved = save(tree, individualDraft())
            val before = database.observationQualificationDao().getByTige(tree.id)
            val operation = pendingParcelOperation(ACCOUNT_B)
            val intercepted = object : ObservationQualificationDao by database.observationQualificationDao() {
                override suspend fun compareAndSet(tigeId: String, expectedRevision: Int,
                    payloadJson: String, updatedAt: Long, updatedBy: String): Int {
                    val changed = database.observationQualificationDao().compareAndSet(tigeId, expectedRevision,
                        payloadJson, updatedAt, updatedBy)
                    database.parcelSyncOperationDao().append(operation)
                    return changed
                }
            }
            assertTrue(runCatching {
                qualificationRepository(intercepted).saveQualification(tree.id,
                    individualDraft("TEST origine refusée"), tree.version, saved.record.revision)
            }.isFailure)
            assertEquals(before, database.observationQualificationDao().getByTige(tree.id))
            assertNull(database.parcelSyncOperationDao().getByOperationId(operation.operationId))
            assertEquals(saved, qualifications.getQualification(tree.id))
        }
    }

    @Test fun late_generation_change_rolls_back_close_and_correction_with_all_sidecars() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            save(tree, individualDraft())
            val sessionBefore = database.inventaireSessionDao().getById(tree.sessionId!!)
            val qualificationBefore = database.observationQualificationDao().getByTige(tree.id)
            val sealing = object : ObservationQualificationDao by database.observationQualificationDao() {
                override suspend fun seal(tigeId: String, expectedRevision: Int): Int {
                    val changed = database.observationQualificationDao().seal(tigeId, expectedRevision)
                    identity.login(ACCOUNT_A)
                    return changed
                }
            }
            assertTrue(runCatching { sessionRepository(sealing).closeMartelage(tree.sessionId!!, null) }.isFailure)
            assertEquals(sessionBefore, database.inventaireSessionDao().getById(tree.sessionId!!))
            assertEquals(qualificationBefore, database.observationQualificationDao().getByTige(tree.id))
            assertNull(sessions.getClosedSnapshot(tree.sessionId!!))
            sessions.closeMartelage(tree.sessionId!!, null)
            val sealed = database.observationQualificationDao().getByTige(tree.id)
            val closed = database.inventaireSessionDao().getById(tree.sessionId!!)
            val copying = object : ObservationQualificationDao by database.observationQualificationDao() {
                override suspend fun insert(qualification: ObservationQualificationEntity) {
                    database.observationQualificationDao().insert(qualification)
                    identity.login(ACCOUNT_A)
                }
            }
            assertTrue(runCatching { sessionRepository(copying).createCorrection(tree.sessionId!!) }.isFailure)
            assertEquals(listOf(tree.sessionId), database.inventaireSessionDao()
                .getCampaignRevisions(tree.sessionId!!).map { it.sessionId })
            assertEquals(1, database.tigeDao().getAllTigesNow().size)
            assertEquals(1, qualificationCount())
            assertEquals(closed, database.inventaireSessionDao().getById(tree.sessionId!!))
            assertEquals(sealed, database.observationQualificationDao().getByTige(tree.id))
        }
    }

    @Test fun deleted_parent_plot_tree_or_session_masks_reads_and_refuses_mutations() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val saved = save(tree, individualDraft())
            val before = database.observationQualificationDao().getByTige(tree.id)
            val targets = listOf("parcelles" to "parcelleId", "placettes" to "placetteId",
                "tiges" to "tigeId", "inventaire_sessions" to "sessionId")
            val ids = listOf(PARCEL, PLOT, tree.id, tree.sessionId!!)
            for ((index, target) in targets.withIndex()) {
                val (table, key) = target
                database.openHelper.writableDatabase.execSQL(
                    "UPDATE $table SET deletedAt = ? WHERE $key = ?", arrayOf(2_000L, ids[index]),
                )
                assertNull(qualifications.getQualification(tree.id))
                assertTrue(runCatching { save(tree, individualDraft(), saved.record.revision) }.isFailure)
                assertEquals(before, database.observationQualificationDao().getByTige(tree.id))
                database.openHelper.writableDatabase.execSQL(
                    "UPDATE $table SET deletedAt = NULL WHERE $key = ?", arrayOf(ids[index]),
                )
                assertEquals(saved, qualifications.getQualification(tree.id))
            }
        }
    }

    @Test fun unscoped_legacy_session_is_read_only_without_attributing_an_observation() = runBlocking {
        withTimeout(TIMEOUT) {
            val legacy = InventaireSession(
                sessionId = LEGACY_SESSION, parcelleId = PARCEL, typeSession = MartelageStates.TYPE,
                dateDebut = 100L, dateFin = null, operateurNom = null, methode = null,
                intensiteEchantillonnagePct = null, objectifSession = null, remarques = null,
                martelageState = MartelageStates.PAUSED, campaignId = LEGACY_SESSION,
                activeDurationMs = 50L, auteur = ACCOUNT_A,
            )
            database.inventaireSessionDao().insert(legacy.toEntity())
            val oldTree = tree("TEST-old-scopeless", LEGACY_SESSION)
            database.tigeDao().insertTige(oldTree.toTigeEntity())
            assertNull(qualifications.getQualification(oldTree.id))
            assertTrue(runCatching { save(oldTree, individualDraft()) }.isFailure)
            assertEquals(0, qualificationCount())
            assertEquals(legacy.toEntity(), database.inventaireSessionDao().getById(LEGACY_SESSION))
            assertEquals(oldTree.toTigeEntity(), database.tigeDao().getTigeById(oldTree.id))
            reopen()
            assertNull(qualifications.getQualification(oldTree.id))
            assertNull(database.inventaireSessionDao().getById(LEGACY_SESSION)?.placetteId)
        }
    }

    @Test fun scoped_absence_and_partial_save_preserve_the_exact_observation_after_reopen() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val basis = ObservationMeasurementBasis.capture(tree)
            val openingGuard = guard(ACCOUNT_A)
            val rawBefore = database.tigeDao().getTigeById(tree.id)
            assertNull(qualifications.getQualificationForObservation(basis, openingGuard))
            val partial = ObservationQualificationDraft(diameter = DiameterQualificationDraft(
                origin = MeasurementOrigin.MEASURED, sourceReference = "TEST mesure partielle",
            ))
            val saved = qualifications.saveQualificationForObservation(basis, partial, null, openingGuard)
            assertEquals(basis, saved.record.payload.basis)
            assertEquals(partial, saved.record.payload.draft)
            assertEquals(ACCOUNT_A, saved.record.createdBy)
            assertEquals(ObservationQualificationStatus.CURRENT, saved.status)
            assertEquals(rawBefore, database.tigeDao().getTigeById(tree.id))
            reopen()
            assertEquals(saved, qualifications.getQualificationForObservation(basis, openingGuard))
            assertEquals(rawBefore, database.tigeDao().getTigeById(tree.id))
        }
    }

    @Test fun scoped_delayed_request_after_logout_and_same_account_login_is_refused() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val basis = ObservationMeasurementBasis.capture(tree)
            val openingGuard = guard(ACCOUNT_A)
            val first = save(tree, individualDraft())
            val before = database.observationQualificationDao().getByTige(tree.id)
            identity.login(null)
            identity.login(ACCOUNT_A)
            assertFalse(openingGuard())
            assertEquals("OBSERVATION_QUALIFICATION_REQUEST_SCOPE_CHANGED", runCatching {
                qualifications.getQualificationForObservation(basis, openingGuard)
            }.exceptionOrNull()?.message)
            assertEquals("OBSERVATION_QUALIFICATION_REQUEST_SCOPE_CHANGED", runCatching {
                qualifications.saveQualificationForObservation(basis, individualDraft("TEST retardé"),
                    first.record.revision, openingGuard)
            }.exceptionOrNull()?.message)
            assertEquals(before, database.observationQualificationDao().getByTige(tree.id))
            assertEquals(first, qualifications.getQualificationForObservation(basis, guard(ACCOUNT_A)))
        }
    }

    @Test fun scoped_request_detects_raw_changes_without_a_version_increment() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val basis = ObservationMeasurementBasis.capture(tree)
            val openingGuard = guard(ACCOUNT_A)
            val first = save(tree, individualDraft())
            val before = database.observationQualificationDao().getByTige(tree.id)
            val raw = requireNotNull(database.tigeDao().getTigeById(tree.id))
            val mutations = listOf(raw.copy(diamCm = 31.0), raw.copy(hauteurM = 19.0),
                raw.copy(gpsWkt = "POINT (2.21 46.21)"), raw.copy(timestamp = raw.timestamp + 1))
            for (changed in mutations) {
                // Import TEST fautif conservant la version : le formulaire ne doit pas qualifier l'état reçu après lui.
                database.tigeDao().updateTige(changed)
                assertEquals(tree.version, changed.version)
                assertEquals("OBSERVATION_QUALIFICATION_OBSERVATION_CONFLICT", runCatching {
                    qualifications.getQualificationForObservation(basis, openingGuard)
                }.exceptionOrNull()?.message)
                assertEquals("OBSERVATION_QUALIFICATION_OBSERVATION_CONFLICT", runCatching {
                    qualifications.saveQualificationForObservation(basis, individualDraft("TEST refusée"),
                        first.record.revision, openingGuard)
                }.exceptionOrNull()?.message)
                assertEquals(before, database.observationQualificationDao().getByTige(tree.id))
                assertEquals(changed, database.tigeDao().getTigeById(tree.id))
            }
        }
    }

    @Test fun scoped_stale_declaration_is_consultable_but_reanchoring_requires_an_explicit_fresh_basis() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val oldBasis = ObservationMeasurementBasis.capture(tree)
            val openingGuard = guard(ACCOUNT_A)
            val first = qualifications.saveQualificationForObservation(oldBasis, individualDraft(), null, openingGuard)
            assertTrue(trees.setTigeHeight(tree.id, 19.0))
            val current = requireNotNull(database.tigeDao().getTigeById(tree.id)).toTige()
            val freshBasis = ObservationMeasurementBasis.capture(current)
            val stale = requireNotNull(qualifications.getQualificationForObservation(freshBasis, openingGuard))
            assertEquals(ObservationQualificationStatus.STALE, stale.status)
            assertNull(stale.currentDraft)
            assertEquals(first.record, stale.record)
            assertEquals("OBSERVATION_QUALIFICATION_OBSERVATION_CONFLICT", runCatching {
                qualifications.saveQualificationForObservation(oldBasis, individualDraft("TEST ancien formulaire"),
                    first.record.revision, openingGuard)
            }.exceptionOrNull()?.message)
            val reloadedGuard = guard(ACCOUNT_A)
            val requalified = qualifications.saveQualificationForObservation(freshBasis,
                individualDraft("TEST déclaration après rechargement"), first.record.revision, reloadedGuard)
            assertEquals(2, requalified.record.revision)
            assertEquals(freshBasis, requalified.record.payload.basis)
            assertEquals(ObservationQualificationStatus.CURRENT, requalified.status)
            assertEquals(current, requireNotNull(database.tigeDao().getTigeById(tree.id)).toTige())
        }
    }

    @Test fun scoped_same_id_reparenting_cannot_read_or_reassign_a_declaration() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val oldBasis = ObservationMeasurementBasis.capture(tree)
            val openingGuard = guard(ACCOUNT_A)
            val first = save(tree, individualDraft())
            val before = database.observationQualificationDao().getByTige(tree.id)
            val secondPlot = "TEST-scoped-second-plot"
            database.placetteDao().insertNewPlacettes(listOf(plot(secondPlot).toPlacetteEntity()))
            val secondSession = sessions.startForPlacette(PARCEL, secondPlot)
            val raw = requireNotNull(database.tigeDao().getTigeById(tree.id))
            val moved = raw.copy(placetteOwnerId = secondPlot, sessionId = secondSession.sessionId)
            database.tigeDao().updateTige(moved)
            val freshBasis = ObservationMeasurementBasis.capture(moved.toTige())
            for (basis in listOf(oldBasis, freshBasis)) {
                assertEquals("OBSERVATION_QUALIFICATION_SCOPE_CONFLICT", runCatching {
                    qualifications.getQualificationForObservation(basis, openingGuard)
                }.exceptionOrNull()?.message)
                assertEquals("OBSERVATION_QUALIFICATION_SCOPE_CONFLICT", runCatching {
                    qualifications.saveQualificationForObservation(basis, individualDraft("TEST réaffectation"),
                        first.record.revision, openingGuard)
                }.exceptionOrNull()?.message)
            }
            assertEquals(before, database.observationQualificationDao().getByTige(tree.id))
            assertEquals(moved, database.tigeDao().getTigeById(tree.id))
        }
    }

    @Test fun scoped_revision_conflicts_do_not_replace_the_newer_declaration() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val basis = ObservationMeasurementBasis.capture(tree)
            val openingGuard = guard(ACCOUNT_A)
            val first = qualifications.saveQualificationForObservation(basis, individualDraft(), null, openingGuard)
            val second = qualifications.saveQualificationForObservation(basis,
                individualDraft("TEST seconde déclaration"), first.record.revision, openingGuard)
            val before = database.observationQualificationDao().getByTige(tree.id)
            for (revision in listOf(null, first.record.revision, second.record.revision + 1)) {
                assertEquals("OBSERVATION_QUALIFICATION_REVISION_CONFLICT", runCatching {
                    qualifications.saveQualificationForObservation(basis, individualDraft("TEST conflit"),
                        revision, openingGuard)
                }.exceptionOrNull()?.message)
                assertEquals(before, database.observationQualificationDao().getByTige(tree.id))
            }
            assertEquals(second, qualifications.getQualificationForObservation(basis, openingGuard))
        }
    }

    @Test fun scoped_paused_edit_and_closed_read_preserve_the_seal_and_snapshot() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val basis = ObservationMeasurementBasis.capture(tree)
            val openingGuard = guard(ACCOUNT_A)
            sessions.pauseMartelage(requireNotNull(tree.sessionId))
            val saved = qualifications.saveQualificationForObservation(basis, individualDraft(), null, openingGuard)
            assertEquals(MartelageStates.PAUSED, sessions.getById(requireNotNull(tree.sessionId))?.martelageState)
            sessions.closeMartelage(requireNotNull(tree.sessionId), null)
            val closed = database.inventaireSessionDao().getById(requireNotNull(tree.sessionId))
            val sealed = requireNotNull(database.observationQualificationDao().getByTige(tree.id))
            val read = requireNotNull(qualifications.getQualificationForObservation(basis, openingGuard))
            assertTrue(read.record.isSealed)
            assertEquals(saved.record.payload, read.record.payload)
            assertEquals(sealed.payloadJson, sealed.sealedPayloadJson)
            assertEquals("OBSERVATION_QUALIFICATION_READ_ONLY", runCatching {
                qualifications.saveQualificationForObservation(basis, individualDraft("TEST fermée"),
                    saved.record.revision, openingGuard)
            }.exceptionOrNull()?.message)
            assertEquals(closed, database.inventaireSessionDao().getById(requireNotNull(tree.sessionId)))
            assertEquals(sealed, database.observationQualificationDao().getByTige(tree.id))
        }
    }

    @Test fun scoped_permissive_request_guard_does_not_grant_canonical_access() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val basis = ObservationMeasurementBasis.capture(tree)
            val first = save(tree, individualDraft())
            val before = database.observationQualificationDao().getByTige(tree.id)
            for (account in listOf(ACCOUNT_B, null)) {
                identity.login(account)
                assertEquals("OBSERVATION_QUALIFICATION_ACCESS_DENIED", runCatching {
                    qualifications.getQualificationForObservation(basis) { true }
                }.exceptionOrNull()?.message)
                assertEquals("OBSERVATION_QUALIFICATION_ACCESS_DENIED", runCatching {
                    qualifications.saveQualificationForObservation(basis, individualDraft(), first.record.revision) { true }
                }.exceptionOrNull()?.message)
                assertEquals(before, database.observationQualificationDao().getByTige(tree.id))
            }
        }
    }

    @Test fun scoped_guard_invalidated_after_read_refuses_the_result_instead_of_reporting_absence() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val basis = ObservationMeasurementBasis.capture(tree)
            val saved = save(tree, individualDraft())
            var sameRequest = true
            val intercepted = object : ObservationQualificationDao by database.observationQualificationDao() {
                override suspend fun getByTige(tigeId: String): ObservationQualificationEntity? {
                    val row = database.observationQualificationDao().getByTige(tigeId)
                    sameRequest = false
                    return row
                }
            }
            assertEquals("OBSERVATION_QUALIFICATION_REQUEST_SCOPE_CHANGED", runCatching {
                qualificationRepository(intercepted).getQualificationForObservation(basis) { sameRequest }
            }.exceptionOrNull()?.message)
            assertEquals(saved, qualifications.getQualification(tree.id))
        }
    }

    @Test fun scoped_guard_invalidated_after_insert_or_update_rolls_back_the_write() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val basis = ObservationMeasurementBasis.capture(tree)
            var sameRequest = true
            val inserting = object : ObservationQualificationDao by database.observationQualificationDao() {
                override suspend fun insert(qualification: ObservationQualificationEntity) {
                    database.observationQualificationDao().insert(qualification)
                    sameRequest = false
                }
            }
            assertEquals("OBSERVATION_QUALIFICATION_REQUEST_SCOPE_CHANGED", runCatching {
                qualificationRepository(inserting).saveQualificationForObservation(basis, individualDraft(), null) { sameRequest }
            }.exceptionOrNull()?.message)
            assertNull(database.observationQualificationDao().getByTige(tree.id))
            val first = save(tree, individualDraft())
            val before = database.observationQualificationDao().getByTige(tree.id)
            sameRequest = true
            val updating = object : ObservationQualificationDao by database.observationQualificationDao() {
                override suspend fun compareAndSet(tigeId: String, expectedRevision: Int,
                    payloadJson: String, updatedAt: Long, updatedBy: String): Int {
                    val count = database.observationQualificationDao().compareAndSet(tigeId, expectedRevision,
                        payloadJson, updatedAt, updatedBy)
                    sameRequest = false
                    return count
                }
            }
            assertEquals("OBSERVATION_QUALIFICATION_REQUEST_SCOPE_CHANGED", runCatching {
                qualificationRepository(updating).saveQualificationForObservation(basis,
                    individualDraft("TEST écriture tardive"), first.record.revision) { sameRequest }
            }.exceptionOrNull()?.message)
            assertEquals(before, database.observationQualificationDao().getByTige(tree.id))
            assertEquals(tree, requireNotNull(database.tigeDao().getTigeById(tree.id)).toTige())
        }
    }

    @Test fun scoped_late_raw_change_is_rechecked_before_return_and_transactionally_rolled_back() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val basis = ObservationMeasurementBasis.capture(tree)
            val openingGuard = guard(ACCOUNT_A)
            val saved = save(tree, individualDraft())
            val raw = requireNotNull(database.tigeDao().getTigeById(tree.id))
            val intercepted = object : ObservationQualificationDao by database.observationQualificationDao() {
                override suspend fun getByTige(tigeId: String): ObservationQualificationEntity? {
                    val row = database.observationQualificationDao().getByTige(tigeId)
                    database.tigeDao().updateTige(raw.copy(hauteurM = 20.0))
                    return row
                }
            }
            assertEquals("OBSERVATION_QUALIFICATION_OBSERVATION_CONFLICT", runCatching {
                qualificationRepository(intercepted).getQualificationForObservation(basis, openingGuard)
            }.exceptionOrNull()?.message)
            assertEquals(raw, database.tigeDao().getTigeById(tree.id))
            assertEquals(saved, qualifications.getQualification(tree.id))
        }
    }

    @Test fun scoped_absence_is_not_reported_when_a_parent_plot_tree_or_session_is_deleted() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val basis = ObservationMeasurementBasis.capture(tree)
            val openingGuard = guard(ACCOUNT_A)
            assertNull(qualifications.getQualificationForObservation(basis, openingGuard))
            val targets = listOf("parcelles" to "parcelleId", "placettes" to "placetteId",
                "tiges" to "tigeId", "inventaire_sessions" to "sessionId")
            val ids = listOf(PARCEL, PLOT, tree.id, requireNotNull(tree.sessionId))
            for ((index, target) in targets.withIndex()) {
                val (table, key) = target
                database.openHelper.writableDatabase.execSQL(
                    "UPDATE $table SET deletedAt = ? WHERE $key = ?", arrayOf(2_000L, ids[index]),
                )
                assertTrue(runCatching {
                    qualifications.getQualificationForObservation(basis, openingGuard)
                }.isFailure)
                assertTrue(runCatching {
                    qualifications.saveQualificationForObservation(basis, individualDraft(), null, openingGuard)
                }.isFailure)
                assertNull(database.observationQualificationDao().getByTige(tree.id))
                database.openHelper.writableDatabase.execSQL(
                    "UPDATE $table SET deletedAt = NULL WHERE $key = ?", arrayOf(ids[index]),
                )
                assertNull(qualifications.getQualificationForObservation(basis, openingGuard))
            }
        }
    }

    @Test fun scoped_missing_plot_or_session_is_refused_without_an_unscoped_fallback() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val basis = ObservationMeasurementBasis.capture(tree)
            val openingGuard = guard(ACCOUNT_A)
            for (unscoped in listOf(basis.copy(placetteId = null), basis.copy(sessionId = null))) {
                assertEquals("OBSERVATION_QUALIFICATION_SCOPE_UNAVAILABLE", runCatching {
                    qualifications.getQualificationForObservation(unscoped, openingGuard)
                }.exceptionOrNull()?.message)
                assertEquals("OBSERVATION_QUALIFICATION_SCOPE_UNAVAILABLE", runCatching {
                    qualifications.saveQualificationForObservation(unscoped, individualDraft(), null, openingGuard)
                }.exceptionOrNull()?.message)
            }
            assertNull(database.observationQualificationDao().getByTige(tree.id))
            assertEquals(tree, requireNotNull(database.tigeDao().getTigeById(tree.id)).toTige())
        }
    }

    @Test fun scoped_late_close_rolls_back_the_write_close_and_seal_as_one_transaction() = runBlocking {
        withTimeout(TIMEOUT) {
            val tree = startTree()
            val basis = ObservationMeasurementBasis.capture(tree)
            val openingGuard = guard(ACCOUNT_A)
            val sessionBefore = database.inventaireSessionDao().getById(requireNotNull(tree.sessionId))
            val rawBefore = database.tigeDao().getTigeById(tree.id)
            val intercepted = object : ObservationQualificationDao by database.observationQualificationDao() {
                override suspend fun insert(qualification: ObservationQualificationEntity) {
                    database.observationQualificationDao().insert(qualification)
                    sessions.closeMartelage(requireNotNull(tree.sessionId), null)
                }
            }
            assertEquals("OBSERVATION_QUALIFICATION_READ_ONLY", runCatching {
                qualificationRepository(intercepted).saveQualificationForObservation(basis, individualDraft(), null, openingGuard)
            }.exceptionOrNull()?.message)
            assertNull(database.observationQualificationDao().getByTige(tree.id))
            assertEquals(sessionBefore, database.inventaireSessionDao().getById(requireNotNull(tree.sessionId)))
            assertEquals(rawBefore, database.tigeDao().getTigeById(tree.id))
            assertNull(sessions.getClosedSnapshot(requireNotNull(tree.sessionId)))
        }
    }

    private fun qualificationRepository(
        qualificationDao: ObservationQualificationDao = database.observationQualificationDao(),
        sessionDao: InventaireSessionDao = database.inventaireSessionDao(),
    ) = ObservationQualificationRepositoryImpl(database.tigeDao(), sessionDao, qualificationDao,
        metadata, access, guard)

    private fun sessionRepository(
        qualificationDao: ObservationQualificationDao = database.observationQualificationDao(),
    ) = InventaireSessionRepositoryImpl(database.inventaireSessionDao(), metadata, clock, guard,
        readAccess = access, surveyContextDao = database.surveyContextDao(), qualificationDao = qualificationDao)

    private suspend fun startTree(): Tige {
        val session = sessions.startForPlacette(PARCEL, PLOT)
        val fresh = tree(UUID.randomUUID().toString(), session.sessionId)
        trees.insertTige(fresh)
        return database.tigeDao().getTigeById(fresh.id)!!.toTige()
    }

    private suspend fun save(tree: Tige, draft: ObservationQualificationDraft,
        expectedRevision: Int? = null): ObservationQualificationRead =
        qualifications.saveQualification(tree.id, draft, tree.version, expectedRevision)

    private fun individualDraft(source: String = "TEST mesure terrain") = ObservationQualificationDraft(
        support = ObservationSupport.INDIVIDUAL_TREE,
        diameter = DiameterQualificationDraft(origin = MeasurementOrigin.MEASURED,
            conventionReference = "TEST diamètre 1,30 m", methodReference = "TEST compas",
            sourceReference = source, observedAt = 300L),
        height = HeightQualificationDraft(nature = HeightNature.TOTAL, origin = MeasurementOrigin.MEASURED,
            support = ObservationSupport.INDIVIDUAL_TREE, pairedWithDiameter = true,
            methodReference = "TEST instrument externe", sourceReference = source, observedAt = 350L),
        position = PositionQualificationDraft(origin = PositionOrigin.CAPTURED_FOR_OBSERVATION,
            methodReference = "TEST GPS", sourceReference = source, capturedAt = 400L),
    )

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

    private fun qualificationCount(): Int = database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM observation_qualifications").use { cursor ->
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
        id = PARCEL, forestId = null, foretId = FOREST, name = "TEST parcelle qualification", surfaceHa = 1.25,
        shape = null, slopePct = null, aspect = null, access = null, altitudeM = null,
        objectifType = null, objectifVal = null, tolerancePct = null, samplingMode = null,
        sampleAreaM2 = null, targetSpeciesCsv = null, srid = 4326, remarks = null,
        createdAt = 100L, updatedAt = 100L,
    )

    private fun plot(id: String) = Placette(
        id = id, parcelleId = PARCEL, name = "TEST placette qualification", type = "CIRC",
        rayonM = 10.0, surfaceM2 = 314.159, centerWkt = "POINT (2.2 46.2)",
        createdAt = 100L, updatedAt = 100L,
    )

    private fun tree(id: String, sessionId: String?) = Tige(
        id = id, parcelleId = PARCEL, placetteId = PLOT, essenceCode = ESSENCE, diamCm = 30.0,
        hauteurM = 18.0, gpsWkt = "POINT (2.2 46.2)", precisionM = 6.0, altitudeM = 110.0,
        note = null, produit = null, fCoef = null, valueEur = null,
        timestamp = 300L, sessionId = sessionId,
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
        const val DB_NAME = "TEST-observation-qualification-repository.db"
        const val TIMEOUT = 20_000L
        const val ACCOUNT_A = "TEST-qualification-owner-a"
        const val ACCOUNT_B = "TEST-qualification-other-b"
        const val FOREST = "66666666-6666-4666-8666-666666666666"
        const val PARCEL = "TEST-qualification-parcel"
        const val PLOT = "TEST-qualification-plot"
        const val ESSENCE = "TEST-qualification-oak"
        const val LEGACY_SESSION = "TEST-qualification-legacy-unscoped"
    }
}
