package com.forestry.counter.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.forestry.counter.data.service.BackupService
import com.forestry.counter.data.mapper.toEntity
import com.forestry.counter.domain.model.AppExport
import com.forestry.counter.domain.model.Counter
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.Formula
import com.forestry.counter.domain.model.Group
import com.forestry.counter.domain.model.ImportMode
import com.forestry.counter.domain.repository.CounterRepository
import com.forestry.counter.domain.repository.FormulaRepository
import com.forestry.counter.domain.repository.GroupRepository
import com.forestry.counter.domain.usecase.export.ExportDataUseCase
import com.forestry.counter.domain.usecase.import.ImportDataUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Tests de restauration §11 (export/import/crash) — spec GeoSylva 3.0, Vague C P0 #14.
 *
 * Deux niveaux de tests :
 *
 * 1. **Use cases réels** ([ExportDataUseCase] / [ImportDataUseCase]) avec des
 *    fake repositories et un `ContentResolver` mocké : on valide le flux
 *    complet export → (crash simulé) → import → données restaurées, sur les
 *    entités « compteur » (Group / Counter / Formula) qui sont réellement
 *    couvertes par l'export actuel.
 * 2. **Gap forestry core** : les entités cœur métier forestières (Forêt,
 *    Parcelle, Placette, Tige) et leurs metadata §3.1 (`auteur`, `source`,
 *    `version`) ne sont PAS encore couvertes par l'export/import. Les tests
 *    correspondants sont marqués `@Ignore` en attendant l'implémentation de
 *    [BackupService] (TODO issue #14).
 *
 * Tests JVM (mockk pour les classes Android, pas de Robolectric).
 */
class BackupRestoreTest {

    // ── Fakes repositories (in-memory) ──────────────────────────────────────

    /**
     * Fake [GroupRepository] en mémoire. Seules les méthodes utilisées par les
     * use cases d'export/import sont implémentées ; les autres lèvent pour
     * échouer bruyamment si elles sont appelées par accident.
     */
    private class FakeGroupRepository : GroupRepository {
        private val groups = MutableStateFlow<List<Group>>(emptyList())

        override fun getAllGroups(): Flow<List<Group>> = groups
        override fun getGroupById(groupId: String): Flow<Group?> =
            groups.map { list -> list.find { g -> g.id == groupId } }
        override suspend fun getByForetId(foretId: String): Group? =
            throw UnsupportedOperationException("non utilisé par les tests d'export/import")
        override suspend fun insertGroup(group: Group) = groups.update { it -> it + group }
        override suspend fun updateGroup(group: Group) =
            groups.update { list -> list.map { if (it.id == group.id) group else it } }
        override suspend fun deleteGroup(groupId: String) =
            groups.update { list -> list.filter { it.id != groupId } }
        override suspend fun deleteByForetId(foretId: String) =
            throw UnsupportedOperationException("non utilisé par les tests d'export/import")
        override suspend fun deleteAllGroups() { groups.value = emptyList() }
        override suspend fun duplicateGroup(groupId: String): String =
            throw UnsupportedOperationException("non utilisé par les tests d'export/import")

        /** Simule un crash : vide toutes les données. */
        fun clear() { groups.value = emptyList() }
    }

    private class FakeCounterRepository : CounterRepository {
        private val counters = MutableStateFlow<List<Counter>>(emptyList())

        override fun getCountersByGroup(groupId: String): Flow<List<Counter>> =
            counters.map { list -> list.filter { it.groupId == groupId } }
        override fun getCounterById(counterId: String): Flow<Counter?> =
            counters.map { list -> list.find { it.id == counterId } }
        override suspend fun insertCounter(counter: Counter) = counters.update { it -> it + counter }
        override suspend fun updateCounter(counter: Counter) =
            counters.update { list -> list.map { if (it.id == counter.id) counter else it } }
        override suspend fun deleteCounter(counterId: String) =
            counters.update { list -> list.filter { it.id != counterId } }
        override suspend fun incrementCounter(counterId: String) = throw UnsupportedOperationException()
        override suspend fun decrementCounter(counterId: String) = throw UnsupportedOperationException()
        override suspend fun resetCounter(counterId: String) = throw UnsupportedOperationException()
        override suspend fun duplicateCounter(counterId: String, count: Int): List<String> =
            throw UnsupportedOperationException()
        override suspend fun updateComputedCounters(groupId: String) = throw UnsupportedOperationException()

        fun clear() { counters.value = emptyList() }
    }

    private class FakeFormulaRepository : FormulaRepository {
        private val formulas = MutableStateFlow<List<Formula>>(emptyList())

        override fun getFormulasByGroup(groupId: String): Flow<List<Formula>> =
            formulas.map { list -> list.filter { it.groupId == groupId } }
        override fun getFormulaById(formulaId: String): Flow<Formula?> =
            formulas.map { list -> list.find { it.id == formulaId } }
        override suspend fun insertFormula(formula: Formula) = formulas.update { it -> it + formula }
        override suspend fun updateFormula(formula: Formula) =
            formulas.update { list -> list.map { if (it.id == formula.id) formula else it } }
        override suspend fun deleteFormula(formulaId: String) =
            formulas.update { list -> list.filter { it.id != formulaId } }
        override suspend fun evaluateFormula(formulaId: String): Double? = throw UnsupportedOperationException()

        fun clear() { formulas.value = emptyList() }
    }

    // ── Fabriques de fixtures ───────────────────────────────────────────────

    private suspend fun seedGroup(
        group: Group,
        counters: List<Counter>,
        formulas: List<Formula>,
        groupRepo: FakeGroupRepository,
        counterRepo: FakeCounterRepository,
        formulaRepo: FakeFormulaRepository,
    ) {
        groupRepo.insertGroup(group)
        counters.forEach { counterRepo.insertCounter(it) }
        formulas.forEach { formulaRepo.insertFormula(it) }
    }

    private fun sampleGroup(id: String = "g1"): Group =
        Group(id = id, name = "Forêt $id", color = "#00AA00")

    private fun sampleCounter(gid: String, name: String): Counter = Counter(
        id = "c-$name", groupId = gid, name = name, value = 42.0, step = 0.5,
        min = 0.0, max = 100.0, bgColor = "#FF0000", fgColor = "#FFFFFF",
        targetValue = 50.0, tags = listOf("sapin", "hêtre"),
    )

    private fun sampleFormula(gid: String, name: String): Formula = Formula(
        id = "f-$name", groupId = gid, name = name, expression = "a + b * 2", description = "Formule test",
    )

    /** Monte les use cases avec des fakes frais et un ContentResolver mocké. */
    private data class Harness(
        val contentResolver: ContentResolver,
        val groupRepo: FakeGroupRepository,
        val counterRepo: FakeCounterRepository,
        val formulaRepo: FakeFormulaRepository,
        val exportUseCase: ExportDataUseCase,
        val importUseCase: ImportDataUseCase,
        val uri: Uri,
    )

    private fun harness(): Harness {
        val groupRepo = FakeGroupRepository()
        val counterRepo = FakeCounterRepository()
        val formulaRepo = FakeFormulaRepository()
        val context = mockk<Context>()
        val contentResolver = mockk<ContentResolver>()
        every { context.contentResolver } returns contentResolver
        val exportUseCase = ExportDataUseCase(context, groupRepo, counterRepo, formulaRepo)
        val importUseCase = ImportDataUseCase(context, groupRepo, counterRepo, formulaRepo)
        return Harness(
            contentResolver = contentResolver,
            groupRepo = groupRepo,
            counterRepo = counterRepo,
            formulaRepo = formulaRepo,
            exportUseCase = exportUseCase,
            importUseCase = importUseCase,
            uri = mockk<Uri>(),
        )
    }

    private val json: Json = Json { ignoreUnknownKeys = true }

    // ── Scénario 1 : Round-trip complet via les use cases réels ──────────────

    @Test
    fun should_restore_data_after_simulated_crash() = runTest {
        val h = harness()
        // Arrange — on peuple la « base »
        seedGroup(
            sampleGroup("g1"),
            listOf(sampleCounter("g1", "Tige A"), sampleCounter("g1", "Tige B")),
            listOf(sampleFormula("g1", "Densité")),
            h.groupRepo, h.counterRepo, h.formulaRepo,
        )
        seedGroup(
            sampleGroup("g2"),
            listOf(sampleCounter("g2", "Tige C")),
            emptyList(),
            h.groupRepo, h.counterRepo, h.formulaRepo,
        )

        // Act — export initial
        val exportBuffer = ByteArrayOutputStream()
        every { h.contentResolver.openOutputStream(any<Uri>()) } returns exportBuffer
        val exportResult = h.exportUseCase.exportToJson(h.uri)
        assertTrue("l'export doit réussir", exportResult.isSuccess)
        val originalJson = exportBuffer.toString(Charsets.UTF_8.name())
        val originalExport: AppExport = json.decodeFromString(originalJson)

        // Crash simulé — on vide toute la « base » (groups, counters, formulas)
        h.groupRepo.clear()
        h.counterRepo.clear()
        h.formulaRepo.clear()

        // Restauration — import en mode REPLACE (préserve les identifiants)
        every { h.contentResolver.openInputStream(any<Uri>()) } returns
            ByteArrayInputStream(originalJson.toByteArray(Charsets.UTF_8))
        val importResult = h.importUseCase.importFromJson(h.uri, mode = ImportMode.REPLACE)
        assertTrue("l'import doit réussir", importResult.isSuccess)

        // Then — on ré-exporte et on compare les groupes (exportDate mis à part)
        val reexportBuffer = ByteArrayOutputStream()
        every { h.contentResolver.openOutputStream(any<Uri>()) } returns reexportBuffer
        h.exportUseCase.exportToJson(h.uri)
        val restoredExport: AppExport = json.decodeFromString(reexportBuffer.toString(Charsets.UTF_8.name()))

        assertEquals(originalExport.groups, restoredExport.groups)
        assertEquals(2, restoredExport.groups.size)
        assertEquals(2, restoredExport.groups[0].counters.size)
        assertEquals(1, restoredExport.groups[0].formulas.size)
    }

    // ── Scénario 3 : Export vide puis import ─────────────────────────────────

    @Test
    fun should_handle_empty_database_export_then_import() = runTest {
        val h = harness()
        // Arrange — base vide
        val exportBuffer = ByteArrayOutputStream()
        every { h.contentResolver.openOutputStream(any<Uri>()) } returns exportBuffer

        // Act — export d'une base vide
        val exportResult = h.exportUseCase.exportToJson(h.uri)
        assertTrue(exportResult.isSuccess)
        val emptyJson = exportBuffer.toString(Charsets.UTF_8.name())

        // Import du fichier vide
        every { h.contentResolver.openInputStream(any<Uri>()) } returns
            ByteArrayInputStream(emptyJson.toByteArray(Charsets.UTF_8))
        val importResult = h.importUseCase.importFromJson(h.uri, mode = ImportMode.REPLACE)

        // Then — pas d'erreur, base toujours vide
        assertTrue(importResult.isSuccess)
        val decoded: AppExport = json.decodeFromString(emptyJson)
        assertTrue(decoded.groups.isEmpty())
    }

    // ── Scénario 4 : Import d'un fichier corrompu ────────────────────────────

    @Test
    fun should_return_failure_when_importing_corrupt_json() = runTest {
        val h = harness()
        // Arrange — JSON malformé
        val corrupt = """{"version":"3.0.0","exportDate":,"groups":[]"""
        every { h.contentResolver.openInputStream(any<Uri>()) } returns
            ByteArrayInputStream(corrupt.toByteArray(Charsets.UTF_8))

        // Act
        val result = h.importUseCase.importFromJson(h.uri, mode = ImportMode.REPLACE)

        // Then — l'erreur est gérée (Result.failure, pas de crash silencieux)
        assertTrue("l'import corrompu doit échouer proprement", result.isFailure)
    }

    @Test
    fun should_return_failure_when_input_stream_is_null() = runTest {
        val h = harness()
        // Arrange — ContentResolver ne peut pas ouvrir le fichier
        every { h.contentResolver.openInputStream(any<Uri>()) } returns null

        val result = h.importUseCase.importFromJson(h.uri, mode = ImportMode.REPLACE)

        assertTrue(result.isFailure)
    }

    // ── Scénario 5 : Préservation de la metadata de format ────────────────────

    @Test
    fun should_preserve_format_version_through_export_import() = runTest {
        val h = harness()
        seedGroup(
            sampleGroup("g1"),
            listOf(sampleCounter("g1", "Tige A")),
            emptyList(),
            h.groupRepo, h.counterRepo, h.formulaRepo,
        )

        val exportBuffer = ByteArrayOutputStream()
        every { h.contentResolver.openOutputStream(any<Uri>()) } returns exportBuffer
        h.exportUseCase.exportToJson(h.uri)

        val decoded: AppExport = json.decodeFromString(exportBuffer.toString(Charsets.UTF_8.name()))

        // Le manifest embarque la metadata de format `version` (§11).
        assertEquals("1.0.0", decoded.version)
    }

    // ── Scénario 2 : Import en mode MERGE préserve les données existantes ─────

    @Test
    fun should_keep_existing_data_when_importing_in_merge_mode() = runTest {
        val h = harness()
        // Arrange — une donnée existante
        seedGroup(
            sampleGroup("existing"),
            listOf(sampleCounter("existing", "Tige Z")),
            emptyList(),
            h.groupRepo, h.counterRepo, h.formulaRepo,
        )

        // Export
        val exportBuffer = ByteArrayOutputStream()
        every { h.contentResolver.openOutputStream(any<Uri>()) } returns exportBuffer
        h.exportUseCase.exportToJson(h.uri)
        val exported = exportBuffer.toString(Charsets.UTF_8.name())

        // Act — import en mode MERGE (n'efface pas l'existant)
        every { h.contentResolver.openInputStream(any<Uri>()) } returns
            ByteArrayInputStream(exported.toByteArray(Charsets.UTF_8))
        val result = h.importUseCase.importFromJson(h.uri, mode = ImportMode.MERGE)

        // Then — l'import réussit et la donnée existante est toujours là
        assertTrue(result.isSuccess)
        // 1 groupe existant + 1 groupe importé (nouvel UUID en MERGE)
        assertEquals(2, h.groupRepo.getAllGroups().first().size)
    }

    // ════════════════════════════════════════════════════════════════════════
    // GAP — Entités cœur forestières (Forêt / Parcelle / Placette / Tige)
    // Le service de backup/restauration pour les entités cœur métier n'existe
    // pas encore. [BackupService] est un squelette qui lève NotImplementedError.
    // Les tests ci-dessous documentent le contrat attendu (§11) et restent
    // @Ignore jusqu'à l'implémentation (TODO issue #14, Vague C P0).
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun should_round_trip_foret_with_parcelles_and_tiges() = runTest {
        val foretDao = FakeForetDao()
        val parcelleDao = FakeParcelleDao()
        val placetteDao = FakePlacetteDao()
        val tigeDao = FakeTigeDao()
        val backupService = BackupService(foretDao, parcelleDao, placetteDao, tigeDao)

        // Given — une Forêt avec metadata §3.1
        val foret = Foret(
            foretId = "f1", nom = "Forêt test", proprietaireNom = "Dupont",
            proprietaireEmail = null, gestionnaireNom = null, typeForet = null,
            objectifGestion = null, psgNumero = null, psgDateExpiration = null,
            departement = null, remarques = null,
            auteur = "account-uuid", source = "manual", version = 1,
        )
        foretDao.insert(foret.toEntity())

        // When — export puis import
        val backup = backupService.export()
        foretDao.hardDeleteAll()
        backupService.import(backup)

        // Then — la forêt restaurée préserve les metadata
        val restored = foretDao.getById("f1")
        assertEquals("Forêt test", restored?.nom)
        assertEquals("account-uuid", restored?.auteur)
        assertEquals("manual", restored?.source)
        assertEquals(1, restored?.version)
    }

    @Test
    fun should_restore_foret_data_after_crash() = runTest {
        val foretDao = FakeForetDao()
        val parcelleDao = FakeParcelleDao()
        val placetteDao = FakePlacetteDao()
        val tigeDao = FakeTigeDao()
        val backupService = BackupService(foretDao, parcelleDao, placetteDao, tigeDao)

        val foret = Foret(
            foretId = "f1", nom = "Forêt crash", proprietaireNom = "Test",
            proprietaireEmail = null, gestionnaireNom = null, typeForet = null,
            objectifGestion = null, psgNumero = null, psgDateExpiration = null,
            departement = null, remarques = null,
        )
        foretDao.insert(foret.toEntity())

        val backup = backupService.export()
        // Crash simulé — base vidée
        foretDao.hardDeleteAll()
        backupService.import(backup)

        // Then — les entités restaurées sont présentes
        assertEquals(1, foretDao.getAllNow().size)
        assertEquals("Forêt crash", foretDao.getById("f1")?.nom)
    }

    @Test(expected = IllegalArgumentException::class)
    fun should_handle_corrupt_forestry_backup_without_silent_crash() = runTest {
        val foretDao = FakeForetDao()
        val parcelleDao = FakeParcelleDao()
        val placetteDao = FakePlacetteDao()
        val tigeDao = FakeTigeDao()
        val backupService = BackupService(foretDao, parcelleDao, placetteDao, tigeDao)

        // Import d'un backup malformé doit lever une exception explicite
        backupService.import(BackupService.ForestryBackup(exportDate = 0L))
    }
}
