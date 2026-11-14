package com.forestry.counter.domain.usecase.privacy

import android.content.Context
import com.forestry.counter.data.local.dao.AlerteSanitaireDao
import com.forestry.counter.data.local.dao.ArbreHabitatDao
import com.forestry.counter.data.local.dao.DiagnosticSylvicoleDao
import com.forestry.counter.data.local.dao.ForetDao
import com.forestry.counter.data.local.dao.IbpEvaluationDao
import com.forestry.counter.data.local.dao.InventaireSessionDao
import com.forestry.counter.data.local.dao.ObservationFloreDao
import com.forestry.counter.data.local.dao.ParcelleDao
import com.forestry.counter.data.local.dao.ParcelSyncOperationDao
import com.forestry.counter.data.local.dao.ParcelSyncDao
import com.forestry.counter.data.local.dao.PlacetteDao
import com.forestry.counter.data.local.dao.RipisylveDao
import com.forestry.counter.data.local.dao.StationDao
import com.forestry.counter.data.local.dao.TigeDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests du use case de droit à l'effacement RGPD (Art. 17).
 *
 * Vérifie que tous les DAOs contenant des PII sont bien appelés lors de
 * l'exécution, et que le compte retourné correspond au nombre de DAOs.
 */
class DeleteAllUserDataUseCaseTest {
    @get:Rule val privateFiles = TemporaryFolder()

    private val foretDao = mockk<ForetDao>(relaxed = true)
    private val parcelleDao = mockk<ParcelleDao>(relaxed = true)
    private val tigeDao = mockk<TigeDao>(relaxed = true)
    private val placetteDao = mockk<PlacetteDao>(relaxed = true)
    private val inventaireSessionDao = mockk<InventaireSessionDao>(relaxed = true)
    private val stationDao = mockk<StationDao>(relaxed = true)
    private val ripisylveDao = mockk<RipisylveDao>(relaxed = true)
    private val ibpEvaluationDao = mockk<IbpEvaluationDao>(relaxed = true)
    private val arbreHabitatDao = mockk<ArbreHabitatDao>(relaxed = true)
    private val diagnosticSylvicoleDao = mockk<DiagnosticSylvicoleDao>(relaxed = true)
    private val alerteSanitaireDao = mockk<AlerteSanitaireDao>(relaxed = true)
    private val observationFloreDao = mockk<ObservationFloreDao>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val operationDao = mockk<ParcelSyncOperationDao>(relaxed = true)
    private val syncDao = mockk<ParcelSyncDao>(relaxed = true)
    private val hierarchySyncDao = mockk<com.forestry.counter.data.local.dao.HierarchySyncDao>(relaxed = true)
    private val woodObjectDao = mockk<com.forestry.counter.data.local.dao.WoodObjectDao>(relaxed = true)
    private val woodDepotDao = mockk<com.forestry.counter.data.local.dao.WoodDepotDao>(relaxed = true)
    private val fieldDao = mockk<com.forestry.counter.data.local.dao.FieldDao>(relaxed = true)
    private val cartographicMeasurementDao = mockk<com.forestry.counter.data.local.dao.CartographicMeasurementDao>(relaxed = true)
    private val cartographicLayerDao = mockk<com.forestry.counter.data.local.dao.CartographicLayerDao>(relaxed = true)
    private val cartographicTraceDao = mockk<com.forestry.counter.data.local.dao.CartographicTraceDao>(relaxed = true)
    private val offlineMapPackDao = mockk<com.forestry.counter.data.local.dao.OfflineMapPackDao>(relaxed = true)
    private var heightCapturePurgeCount = 0
    private val woodDataGate = com.forestry.counter.data.repository.WoodDataAccessGate()
    private var stopCartographicWork: suspend () -> Unit = {}
    private var resumeCartographicWork: suspend () -> Unit = {}
    private var resumeCartographicWorkCount = 0
    private var purgeMapboxRegions: suspend (List<String>) -> Unit = {}

    init {
        every { context.filesDir } answers { privateFiles.root }
        every { context.cacheDir } answers { privateFiles.root }
        every { context.getExternalFilesDir("backups") } returns null
        coEvery { operationDao.runJournalTransaction(any()) } coAnswers { firstArg<suspend () -> Unit>().invoke() }
    }

    private val useCase = DeleteAllUserDataUseCase(
        foretDao = foretDao,
        parcelleDao = parcelleDao,
        tigeDao = tigeDao,
        placetteDao = placetteDao,
        inventaireSessionDao = inventaireSessionDao,
        stationDao = stationDao,
        ripisylveDao = ripisylveDao,
        ibpEvaluationDao = ibpEvaluationDao,
        arbreHabitatDao = arbreHabitatDao,
        diagnosticSylvicoleDao = diagnosticSylvicoleDao,
        alerteSanitaireDao = alerteSanitaireDao,
        observationFloreDao = observationFloreDao,
        context = context,
        operationDao = operationDao,
        syncDao = syncDao,
        hierarchySyncDao = hierarchySyncDao,
        woodDepotDao = woodDepotDao,
        woodObjectDao = woodObjectDao,
        woodDataGate = woodDataGate,
        fieldDao = fieldDao,
        cartographicMeasurementDao = cartographicMeasurementDao,
        cartographicLayerDao = cartographicLayerDao,
        cartographicTraceDao = cartographicTraceDao,
        offlineMapPackDao = offlineMapPackDao,
        stopCartographicWork = { stopCartographicWork() },
        resumeCartographicWork = { resumeCartographicWorkCount++; resumeCartographicWork() },
        purgeMapboxRegions = { ids -> purgeMapboxRegions(ids) },
        clearHeightReferenceCaptures = { heightCapturePurgeCount++ },
    )

    @Test
    fun should_appeler_hardDeleteAll_sur_tous_les_daos_pii() = runTest {
        every { context.getExternalFilesDir(null) } returns createTempDir()

        useCase.execute()

        coVerify(exactly = 1) { operationDao.hardDeleteAll() }
        coVerify(exactly = 1) { syncDao.hardDeleteAll() }
        coVerify(exactly = 1) { hierarchySyncDao.hardDeleteOperations() }
        coVerify(exactly = 1) { hierarchySyncDao.hardDeleteAssociations() }
        coVerify(exactly = 1) { woodObjectDao.deleteCommands() }
        coVerify(exactly = 1) { woodObjectDao.deleteSelections() }
        coVerify(exactly = 1) { woodObjectDao.deleteObservations() }
        coVerify(exactly = 1) { woodObjectDao.deleteObjects() }
        coVerify(exactly = 1) { woodDepotDao.hardDeleteAll() }
        coVerify(exactly = 1) { fieldDao.deleteChunks() }
        coVerify(exactly = 1) { fieldDao.deletePhotos() }
        coVerify(exactly = 1) { fieldDao.deleteDrafts() }
        coVerify(exactly = 1) { fieldDao.deleteEntries() }
        coVerify(exactly = 1) { fieldDao.deleteLinks() }
        coVerify(exactly = 1) { fieldDao.deleteSites() }
        coVerify(exactly = 1) { cartographicLayerDao.deleteAllOperations() }
        coVerify(exactly = 1) { cartographicLayerDao.deleteAll() }
        coVerify(exactly = 1) { cartographicTraceDao.deleteAll() }
        coVerify(exactly = 1) { offlineMapPackDao.deleteAll() }
        coVerify(exactly = 1) { cartographicMeasurementDao.deleteAll() }
        coVerify(exactly = 1) { cartographicMeasurementDao.deleteStoreIdentity() }
        // Les 11 entités cœur métier (Vague C) : suppression physique via hardDeleteAll.
        coVerify(exactly = 1) { foretDao.hardDeleteAll() }
        coVerify(exactly = 1) { parcelleDao.hardDeleteAll() }
        coVerify(exactly = 1) { tigeDao.hardDeleteAll() }
        coVerify(exactly = 1) { placetteDao.hardDeleteAll() }
        coVerify(exactly = 1) { inventaireSessionDao.hardDeleteAll() }
        coVerify(exactly = 1) { stationDao.hardDeleteAll() }
        coVerify(exactly = 1) { ripisylveDao.hardDeleteAll() }
        coVerify(exactly = 1) { arbreHabitatDao.hardDeleteAll() }
        coVerify(exactly = 1) { diagnosticSylvicoleDao.hardDeleteAll() }
        coVerify(exactly = 1) { alerteSanitaireDao.hardDeleteAll() }
        coVerify(exactly = 1) { observationFloreDao.hardDeleteAll() }
        // IbpEvaluation : pas de colonne deletedAt (hors Vague C), deleteAll reste physique.
        coVerify(exactly = 1) { ibpEvaluationDao.deleteAll() }
        assertEquals(1, heightCapturePurgeCount)
    }

    @Test
    fun should_renvoyer_le_nombre_de_daos_purge() = runTest {
        every { context.getExternalFilesDir(null) } returns createTempDir()

        val result = useCase.execute()

        assertEquals(DeleteAllUserDataUseCase.DAO_COUNT, result)
    }

    @Test
    fun should_remove_private_closed_reports_with_local_privacy_purge() = runTest {
        val directory = createTempDir(prefix = "geosylva-report-purge-test-")
        every { context.filesDir } returns directory
        every { context.getExternalFilesDir(null) } returns directory
        val report = File(directory, "martelage_reports/revision/report.pdf").also {
            it.parentFile.mkdirs(); it.writeText("TESTONLY private report")
        }
        try {
            useCase.execute()
            assertFalse(report.exists())
            coVerify(exactly = 1) { hierarchySyncDao.hardDeleteOperations() }
            coVerify(exactly = 1) { hierarchySyncDao.hardDeleteAssociations() }
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun failed_local_database_purge_keeps_photos_and_does_not_report_success() = runTest {
        val directory = createTempDir(prefix = "geosylva-purge-test-")
        val photo = File(directory, "photos/fixture.txt").also {
            it.parentFile.mkdirs()
            it.writeText("Test fixture, no user data")
        }
        every { context.getExternalFilesDir(null) } returns directory
        coEvery { parcelleDao.hardDeleteAll() } throws IllegalStateException("fixture-transaction-failure")
        try {
            val failure = runCatching { useCase.execute() }.exceptionOrNull()
            assertEquals("fixture-transaction-failure", failure?.message)
            assertTrue(photo.exists())
            assertEquals(0, heightCapturePurgeCount)
            coVerify(exactly = 0) { tigeDao.hardDeleteAll() }
            coVerify(exactly = 0) { placetteDao.hardDeleteAll() }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun should_remove_local_backup_copies_containing_height_evidence() = runTest {
        val internal = privateFiles.newFolder("internal")
        val external = privateFiles.newFolder("external-backups")
        every { context.filesDir } returns internal
        every { context.getExternalFilesDir(null) } returns null
        every { context.getExternalFilesDir("backups") } returns external
        val internalBackup = File(internal, "backups/test.zip").also {
            it.parentFile.mkdirs(); it.writeText("fixture")
        }
        val externalBackup = File(external, "test.zip").also { it.writeText("fixture") }

        useCase.execute()

        assertFalse(internalBackup.exists())
        assertFalse(externalBackup.exists())
        assertEquals(1, heightCapturePurgeCount)
    }

    @Test
    fun purge_waits_for_cartographic_cleanup_before_reading_regions_or_deleting_sql() = runTest {
        every { context.getExternalFilesDir(null) } returns null
        val cleanupStarted = CompletableDeferred<Unit>()
        val cleanupReleased = CompletableDeferred<Unit>()
        stopCartographicWork = { cleanupStarted.complete(Unit); cleanupReleased.await() }
        val execution = async { useCase.execute() }
        try {
            withContext(Dispatchers.IO) { withTimeout(5_000) { cleanupStarted.await() } }
            coVerify(exactly = 0) { offlineMapPackDao.allForPurge() }
            coVerify(exactly = 0) { operationDao.runJournalTransaction(any()) }
            assertEquals(0, heightCapturePurgeCount)
            cleanupReleased.complete(Unit)
            assertEquals(DeleteAllUserDataUseCase.DAO_COUNT, execution.await())
            coVerify(exactly = 1) { offlineMapPackDao.allForPurge() }
        } finally {
            cleanupReleased.complete(Unit)
        }
    }

    @Test
    fun only_registered_regions_are_removed_under_the_gate_before_the_sql_transaction() = runTest {
        val externalPhotos = privateFiles.newFolder("completion-photos")
        val externalBackups = privateFiles.newFolder("completion-backups")
        every { context.getExternalFilesDir(null) } returns externalPhotos
        every { context.getExternalFilesDir("backups") } returns externalBackups
        val privateArtifacts = listOf(
            File(externalPhotos, "photos/test.txt"),
            File(privateFiles.root, "field_capture/test.txt"),
            File(privateFiles.root, "martelage_reports/test.pdf"),
            File(privateFiles.root, "backups/test.zip"),
            File(externalBackups, "test.zip"),
        ).onEach { file -> file.parentFile.mkdirs(); file.writeText("TEST-only private artifact") }
        val ids = listOf("TEST-owned-region-one", "TEST-owned-region-two")
        val rows = ids.map { id -> mockk<com.forestry.counter.data.local.entity.OfflineMapPackEntity> {
            every { nativeRegionId } returns id
        } }
        val order = mutableListOf<String>()
        stopCartographicWork = { order += "joined" }
        coEvery { offlineMapPackDao.allForPurge() } coAnswers {
            assertEquals(1, woodDataGate.state.value.pendingPurges)
            order += "registry"
            rows
        }
        purgeMapboxRegions = { registered ->
            assertEquals(ids, registered)
            assertEquals(1, woodDataGate.state.value.pendingPurges)
            order += "native"
        }
        coEvery { operationDao.runJournalTransaction(any()) } coAnswers {
            order += "sql"
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery { offlineMapPackDao.deleteAll() } coAnswers { order += "delete-registry" }
        resumeCartographicWork = {
            assertTrue(privateArtifacts.none { it.exists() })
            assertEquals(1, heightCapturePurgeCount)
            assertEquals(0, woodDataGate.state.value.pendingPurges)
            order += "resumed"
        }

        useCase.execute()

        assertEquals(listOf("joined", "registry", "native", "sql", "delete-registry", "resumed"), order)
        assertEquals(1, resumeCartographicWorkCount)
        assertEquals(0, woodDataGate.state.value.pendingPurges)
    }

    @Test
    fun partial_native_failure_preserves_the_registry_and_files_for_an_explicit_retry() = runTest {
        val external = privateFiles.newFolder("native-failure-photos")
        val photo = File(external, "photos/fixture.txt").also {
            it.parentFile.mkdirs(); it.writeText("TEST-only photo")
        }
        every { context.getExternalFilesDir(null) } returns external
        val ids = listOf("TEST-owned-region-already-removed", "TEST-owned-region-failed")
        val rows = ids.map { id -> mockk<com.forestry.counter.data.local.entity.OfflineMapPackEntity> {
            every { nativeRegionId } returns id
        } }
        coEvery { offlineMapPackDao.allForPurge() } returns rows
        val attempts = mutableListOf<List<String>>()
        purgeMapboxRegions = { registered ->
            attempts += registered.toList()
            if (attempts.size == 1) throw IllegalStateException("TEST-native-second-region-failed")
        }

        val failure = runCatching { useCase.execute() }.exceptionOrNull()

        assertEquals("TEST-native-second-region-failed", failure?.message)
        assertTrue(photo.exists())
        assertEquals(0, heightCapturePurgeCount)
        assertEquals(1, resumeCartographicWorkCount)
        assertEquals(0, woodDataGate.state.value.pendingPurges)
        coVerify(exactly = 0) { operationDao.runJournalTransaction(any()) }
        coVerify(exactly = 0) { offlineMapPackDao.deleteAll() }
        coVerify(exactly = 0) { cartographicMeasurementDao.deleteStoreIdentity() }

        assertEquals(DeleteAllUserDataUseCase.DAO_COUNT, useCase.execute())

        assertEquals(listOf(ids, ids), attempts)
        assertFalse(photo.exists())
        coVerify(exactly = 1) { offlineMapPackDao.deleteAll() }
        assertEquals(1, heightCapturePurgeCount)
        assertEquals(2, resumeCartographicWorkCount)
    }

    @Test
    fun failed_cartographic_stop_still_reopens_admission_without_starting_the_purge() = runTest {
        stopCartographicWork = { throw IllegalStateException("TEST-stop-failed") }

        val failure = runCatching { useCase.execute() }.exceptionOrNull()

        assertEquals("TEST-stop-failed", failure?.message)
        assertEquals(1, resumeCartographicWorkCount)
        assertEquals(0, woodDataGate.state.value.pendingPurges)
        coVerify(exactly = 0) { offlineMapPackDao.allForPurge() }
        coVerify(exactly = 0) { operationDao.runJournalTransaction(any()) }
    }

    @Test
    fun cancelling_only_the_purge_child_waits_for_the_non_cancellable_admission_resume() = runTest {
        val stopStarted = CompletableDeferred<Unit>()
        val resumeStarted = CompletableDeferred<Unit>()
        val allowResume = CompletableDeferred<Unit>()
        var resumeFinished = false
        stopCartographicWork = { stopStarted.complete(Unit); awaitCancellation() }
        resumeCartographicWork = {
            assertTrue(currentCoroutineContext().isActive)
            resumeStarted.complete(Unit)
            allowResume.await()
            resumeFinished = true
        }
        val execution = launch { useCase.execute() }
        try {
            withContext(Dispatchers.IO) { withTimeout(5_000) { stopStarted.await() } }
            execution.cancel()
            withContext(Dispatchers.IO) { withTimeout(5_000) { resumeStarted.await() } }
            assertFalse(execution.isCompleted)
            assertFalse(resumeFinished)
            coVerify(exactly = 0) { offlineMapPackDao.allForPurge() }
            coVerify(exactly = 0) { operationDao.runJournalTransaction(any()) }
            allowResume.complete(Unit)
            execution.join()
            assertTrue(execution.isCancelled)
            assertTrue(resumeFinished)
            assertEquals(1, resumeCartographicWorkCount)
        } finally {
            allowResume.complete(Unit)
            execution.cancel()
            execution.join()
        }
    }

}
