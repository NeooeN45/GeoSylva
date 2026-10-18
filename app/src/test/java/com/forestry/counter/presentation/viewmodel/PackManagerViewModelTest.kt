package com.forestry.counter.presentation.viewmodel

import androidx.lifecycle.ViewModelStore
import com.forestry.counter.BuildConfig
import com.forestry.counter.data.packs.AdministrativePackContent
import com.forestry.counter.data.packs.PackCatalogIssue
import com.forestry.counter.domain.usecase.pack.PackArtifactIdentity
import com.forestry.counter.domain.model.pack.GeoPackDescriptor
import com.forestry.counter.domain.model.pack.PackFeatures
import com.forestry.counter.domain.model.pack.PackLevel
import com.forestry.counter.domain.model.pack.PackStatus
import com.forestry.counter.domain.usecase.pack.PackManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PackManagerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    // Fixture JVM uniquement : aucune dépendance au catalogue exposé dans l'application.
    private val regionalPack = GeoPackDescriptor(
        id = "test.region.fixture",
        level = PackLevel.REGIONAL,
        name = "Fixture pack régional — tests uniquement",
        codeINSEE = "TEST",
        version = "1.0.0",
        buildDate = "test-only",
        sizeKb = 0L,
        status = PackStatus.AVAILABLE,
        features = PackFeatures()
    )
    private val packId = regionalPack.id
    private val otherPackId = "test.department.fixture"

    private lateinit var manager: PackManager
    private lateinit var managerState: MutableStateFlow<PackManager.PackState>
    private lateinit var viewModel: PackManagerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        managerState = MutableStateFlow(
            PackManager.PackState(
                allPacks = listOf(regionalPack)
            )
        )
        manager = mockk()
        every { manager.packState } returns managerState
        viewModel = PackManagerViewModel(manager)
    }

    @After
    fun tearDown() {
        // Fermer le scope du ViewModel même si une assertion a échoué.
        ViewModelStore().apply {
            put("packs", viewModel)
            clear()
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `le catalogue et la progression restent ceux du gestionnaire`() = runTest(dispatcher) {
        assertEquals(managerState.value, viewModel.packState.value)

        val activePack = regionalPack.copy(status = PackStatus.INSTALLED)
        val updated = managerState.value.copy(
            activePacks = listOf(activePack),
            downloadProgress = mapOf(packId to 0.5f)
        )
        managerState.value = updated

        assertEquals(updated, viewModel.packState.value)
        assertEquals(0.5f, viewModel.packState.value.downloadProgress.getValue(packId), 0f)
        assertEquals(listOf(activePack), viewModel.packState.value.activePacks)
    }

    @Test
    fun `deux pressions sur installer ne lancent qu une action pour ce pack`() = runTest(dispatcher) {
        val finish = CompletableDeferred<Unit>()
        coEvery { manager.installPack(packId, any<(Float) -> Unit>()) } coAnswers { finish.await() }

        viewModel.installPack(packId)
        viewModel.installPack(packId)
        assertEquals(setOf(packId), viewModel.actionState.value.running)
        runCurrent()

        coVerify(exactly = 1) { manager.installPack(packId, any<(Float) -> Unit>()) }
        assertEquals(setOf(packId), viewModel.actionState.value.running)

        finish.complete(Unit)
        runCurrent()
        assertTrue(viewModel.actionState.value.running.isEmpty())
        assertTrue(viewModel.actionState.value.errors.isEmpty())

        viewModel.installPack(packId)
        runCurrent()
        coVerify(exactly = 2) { manager.installPack(packId, any<(Float) -> Unit>()) }
    }

    @Test
    fun `une erreur est locale au pack et une nouvelle tentative l efface`() = runTest(dispatcher) {
        val finishOtherPack = CompletableDeferred<Unit>()
        coEvery { manager.installPack(packId, any<(Float) -> Unit>()) } throws IOException("Réseau interrompu")
        coEvery { manager.installPack(otherPackId, any<(Float) -> Unit>()) } coAnswers { finishOtherPack.await() }

        viewModel.installPack(packId)
        viewModel.installPack(otherPackId)
        runCurrent()

        assertEquals(mapOf(packId to "Réseau interrompu"), viewModel.actionState.value.errors)
        assertEquals(setOf(otherPackId), viewModel.actionState.value.running)

        coEvery { manager.installPack(packId, any<(Float) -> Unit>()) } returns Unit
        viewModel.installPack(packId)

        assertTrue(viewModel.actionState.value.errors.isEmpty())
        assertEquals(setOf(packId, otherPackId), viewModel.actionState.value.running)
        runCurrent()
        coVerify(exactly = 2) { manager.installPack(packId, any<(Float) -> Unit>()) }
        assertEquals(setOf(otherPackId), viewModel.actionState.value.running)

        finishOtherPack.complete(Unit)
        runCurrent()
        assertTrue(viewModel.actionState.value.running.isEmpty())
    }

    @Test
    fun `interrompre avant la premiere execution libere le pack pour une reprise`() = runTest(dispatcher) {
        coEvery { manager.installPack(packId, any<(Float) -> Unit>()) } returns Unit

        viewModel.installPack(packId)
        viewModel.interrupt(packId)
        runCurrent()

        coVerify(exactly = 0) { manager.installPack(packId, any<(Float) -> Unit>()) }
        assertTrue(viewModel.actionState.value.running.isEmpty())
        assertTrue(viewModel.actionState.value.errors.isEmpty())

        viewModel.installPack(packId)
        runCurrent()
        coVerify(exactly = 1) { manager.installPack(packId, any<(Float) -> Unit>()) }
        assertTrue(viewModel.actionState.value.running.isEmpty())
    }

    @Test
    fun `interrompre une action en cours propage l annulation sans erreur utilisateur`() = runTest(dispatcher) {
        var cancelled = false
        coEvery { manager.installPack(packId, any<(Float) -> Unit>()) } coAnswers {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }

        viewModel.installPack(packId)
        runCurrent()
        coVerify(exactly = 1) { manager.installPack(packId, any<(Float) -> Unit>()) }
        assertFalse(cancelled)

        viewModel.interrupt(packId)
        runCurrent()

        assertTrue(cancelled)
        assertTrue(viewModel.actionState.value.running.isEmpty())
        assertTrue(viewModel.actionState.value.errors.isEmpty())

        coEvery { manager.installPack(packId, any<(Float) -> Unit>()) } returns Unit
        viewModel.installPack(packId)
        runCurrent()
        coVerify(exactly = 2) { manager.installPack(packId, any<(Float) -> Unit>()) }
    }

    @Test
    fun `la desactivation passe par le gestionnaire et reflete son nouvel etat`() = runTest(dispatcher) {
        managerState.value = managerState.value.copy(
            activePacks = listOf(regionalPack.copy(status = PackStatus.INSTALLED))
        )
        val finish = CompletableDeferred<Unit>()
        coEvery { manager.uninstallPack(packId) } coAnswers {
            finish.await()
            managerState.value = managerState.value.copy(activePacks = emptyList())
        }

        viewModel.deactivatePack(packId)
        viewModel.deactivatePack(packId)
        runCurrent()

        coVerify(exactly = 1) { manager.uninstallPack(packId) }
        coVerify(exactly = 0) { manager.installPack(any(), any<(Float) -> Unit>()) }
        assertEquals(setOf(packId), viewModel.actionState.value.running)
        assertEquals(1, viewModel.packState.value.activePacks.size)

        finish.complete(Unit)
        runCurrent()

        assertTrue(viewModel.packState.value.activePacks.isEmpty())
        assertTrue(viewModel.actionState.value.running.isEmpty())
        assertTrue(viewModel.actionState.value.errors.isEmpty())
    }

    @Test
    fun `reactiver une version locale est une action unique sans telechargement`() = runTest(dispatcher) {
        val retainedPack = regionalPack.copy(
            version = "0.9.0",
            installedVersion = "0.9.0",
            status = PackStatus.INSTALLED,
            downloadUrl = null
        )
        managerState.value = managerState.value.copy(retainedPacks = listOf(retainedPack))
        val finish = CompletableDeferred<Unit>()
        coEvery { manager.reactivatePack(packId, retainedPack.version) } coAnswers {
            finish.await()
            managerState.value = managerState.value.copy(
                allPacks = managerState.value.allPacks.map { descriptor ->
                    if (descriptor.id == packId) {
                        descriptor.copy(
                            status = PackStatus.UPDATE_PENDING,
                            installedVersion = retainedPack.version
                        )
                    } else {
                        descriptor
                    }
                },
                activePacks = listOf(retainedPack)
            )
        }

        viewModel.reactivatePack(packId, retainedPack.version)
        viewModel.reactivatePack(packId, retainedPack.version)
        // Une installation concurrente du même pack ne doit pas démarrer.
        viewModel.installPack(packId)
        runCurrent()

        coVerify(exactly = 1) { manager.reactivatePack(packId, retainedPack.version) }
        coVerify(exactly = 0) { manager.installPack(any(), any<(Float) -> Unit>()) }
        coVerify(exactly = 0) { manager.uninstallPack(any()) }
        assertEquals(setOf(packId), viewModel.actionState.value.running)
        assertTrue(viewModel.packState.value.activePacks.isEmpty())

        finish.complete(Unit)
        runCurrent()

        assertEquals(listOf(retainedPack), viewModel.packState.value.activePacks)
        val catalogPack = viewModel.packState.value.allPacks.first { it.id == packId }
        assertEquals(regionalPack.version, catalogPack.version)
        assertEquals(retainedPack.version, catalogPack.installedVersion)
        assertTrue(viewModel.actionState.value.running.isEmpty())
        assertTrue(viewModel.actionState.value.errors.isEmpty())
        coVerify(exactly = 0) { manager.installPack(any(), any<(Float) -> Unit>()) }
    }

    @Test
    fun `fermer le ViewModel annule une action en cours`() = runTest(dispatcher) {
        var cancelled = false
        coEvery { manager.installPack(packId, any<(Float) -> Unit>()) } coAnswers {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        val store = ViewModelStore().apply { put("packs", viewModel) }

        viewModel.installPack(packId)
        runCurrent()
        assertFalse(cancelled)

        store.clear()
        runCurrent()

        assertTrue(cancelled)
        assertTrue(viewModel.actionState.value.running.isEmpty())
        assertTrue(viewModel.actionState.value.errors.isEmpty())
    }

    private fun publishedPack(id: String = packId, bytes: Long = 120L) = regionalPack.copy(
        id = id, level = PackLevel.DEPARTEMENTAL, contentKind = "administrative_boundaries", territoryId = "department:16",
        parentId = "fr-region-75",
        archiveBytes = bytes, checksum = "a".repeat(64), downloadUrl = "https://test.invalid/$id"
    )

    private fun publishedRegion(code: String = "75", bytes: Long = 120L) = publishedPack("fr-region-$code", bytes)
        .copy(level = PackLevel.REGIONAL, territoryId = "region:$code", parentId = null, codeINSEE = code)

    private fun publishedDepartment(code: String, region: String = "75", bytes: Long = 35L) = publishedPack("fr-department-$code", bytes)
        .copy(territoryId = "department:$code", codeINSEE = code, parentId = "fr-region-$region")

    private fun regionalCoverage(vararg codes: String) {
        coEvery { manager.administrativeDepartmentsByRegion() } returns mapOf("region:75" to codes.map { "department:$it" }.toSet())
    }

    @Test
    fun `region inclut automatiquement tous ses departements et deduplique le choix explicite`() = runTest(dispatcher) {
        val region = publishedRegion(bytes = 120L)
        val department = publishedDepartment("16", bytes = 35L)
        val secondDepartment = publishedDepartment("17", bytes = 40L)
        val unrelated = publishedDepartment("30", region = "76", bytes = 50L)
        regionalCoverage("16", "17")
        managerState.value = managerState.value.copy(allPacks = listOf(region, department, secondDepartment, unrelated))
        coEvery { manager.actualAvailableBytes() } returns 1000L

        viewModel.prepareDownload(setOf(region.id, department.id))
        runCurrent()

        val plan = requireNotNull(viewModel.actionState.value.plan)
        assertEquals(listOf(region, department, secondDepartment), plan.packs)
        assertEquals(195L, plan.archiveBytes)
        assertEquals(2, plan.includedDepartmentCount)
        assertEquals(1000L, plan.availableBytes)
        assertTrue(plan.fits)
        coVerify(exactly = 0) { manager.installPack(any(), any<PackArtifactIdentity>(), any()) }
    }

    @Test
    fun `region deja active complete ses enfants manquants avec leur budget exact`() = runTest(dispatcher) {
        val region = publishedRegion()
        val first = publishedDepartment("16")
        val missing = publishedDepartment("17", bytes = 40L)
        regionalCoverage("16", "17")
        managerState.value = managerState.value.copy(
            allPacks = listOf(region, first, missing), activePacks = listOf(region, first)
        )
        coEvery { manager.actualAvailableBytes() } returns 39L

        viewModel.prepareDownload(setOf(region.id))
        runCurrent()

        val plan = requireNotNull(viewModel.actionState.value.plan)
        assertEquals(listOf(missing), plan.packs)
        assertEquals(listOf(region, first, missing), plan.coveredPacks)
        assertEquals(40L, plan.archiveBytes)
        assertFalse(plan.fits)
        viewModel.confirmDownload()
        runCurrent()
        coVerify(exactly = 0) { manager.installPack(any(), any<PackArtifactIdentity>(), any()) }
    }

    @Test
    fun `departement seul ne telecharge ni sa region ni ses voisins`() = runTest(dispatcher) {
        val region = publishedRegion()
        val first = publishedDepartment("16")
        val second = publishedDepartment("17")
        managerState.value = managerState.value.copy(allPacks = listOf(region, first, second))
        coEvery { manager.actualAvailableBytes() } returns 1000L
        viewModel.prepareDownload(setOf(first.id))
        runCurrent()
        assertEquals(listOf(first), viewModel.actionState.value.plan?.packs)
        assertEquals(0, viewModel.actionState.value.plan?.includedDepartmentCount)
        coVerify(exactly = 0) { manager.administrativeDepartmentsByRegion() }
    }

    @Test
    fun `region incompletement publiee refuse de preparer un telechargement partiel`() = runTest(dispatcher) {
        val region = publishedRegion()
        val onlyPublished = publishedDepartment("16")
        regionalCoverage("16", "17")
        managerState.value = managerState.value.copy(allPacks = listOf(region, onlyPublished))

        viewModel.prepareDownload(setOf(region.id))
        runCurrent()

        assertEquals(null, viewModel.actionState.value.plan)
        assertTrue(viewModel.actionState.value.incompleteRegion)
        coVerify(exactly = 0) { manager.actualAvailableBytes() }
        coVerify(exactly = 0) { manager.installPack(any(), any<PackArtifactIdentity>(), any()) }
    }

    @Test
    fun `confirmation refuse un departement retire apres preparation`() = runTest(dispatcher) {
        val region = publishedRegion()
        val child = publishedDepartment("16")
        regionalCoverage("16")
        managerState.value = managerState.value.copy(allPacks = listOf(region, child))
        coEvery { manager.actualAvailableBytes() } returns 1000L
        viewModel.prepareDownload(setOf(region.id))
        runCurrent()
        managerState.value = managerState.value.copy(allPacks = listOf(region))

        viewModel.confirmDownload()
        runCurrent()

        assertTrue(viewModel.actionState.value.incompleteRegion)
        assertEquals(null, viewModel.actionState.value.plan)
        coVerify(exactly = 0) { manager.installPack(any(), any<PackArtifactIdentity>(), any()) }
    }

    @Test
    fun `confirmation revalide aussi une version active de la couverture regionale`() = runTest(dispatcher) {
        val region = publishedRegion()
        val active = publishedDepartment("16")
        val pending = publishedDepartment("17")
        regionalCoverage("16", "17")
        managerState.value = managerState.value.copy(allPacks = listOf(region, active, pending), activePacks = listOf(active))
        coEvery { manager.actualAvailableBytes() } returns 1000L
        viewModel.prepareDownload(setOf(region.id))
        runCurrent()
        managerState.value = managerState.value.copy(allPacks = listOf(region, active.copy(version = "2.0.0"), pending))

        viewModel.confirmDownload()
        runCurrent()

        assertTrue(viewModel.actionState.value.planError.orEmpty().contains("catalogue"))
        coVerify(exactly = 0) { manager.installPack(any(), any<PackArtifactIdentity>(), any()) }
    }

    @Test
    fun `confirmation refuse une couverture active retiree du budget entre temps`() = runTest(dispatcher) {
        val region = publishedRegion()
        val child = publishedDepartment("16")
        regionalCoverage("16")
        managerState.value = managerState.value.copy(allPacks = listOf(region, child), activePacks = listOf(child))
        coEvery { manager.actualAvailableBytes() } returns 1000L
        viewModel.prepareDownload(setOf(region.id))
        runCurrent()
        assertEquals(listOf(region), viewModel.actionState.value.plan?.packs)
        managerState.value = managerState.value.copy(activePacks = emptyList())

        viewModel.confirmDownload()
        runCurrent()

        assertTrue(viewModel.actionState.value.planError.orEmpty().contains("actifs"))
        coVerify(exactly = 0) { manager.installPack(any(), any<PackArtifactIdentity>(), any()) }
    }

    @Test
    fun `confirmation installe region et enfants une fois et conserve les erreurs partielles`() = runTest(dispatcher) {
        val region = publishedRegion()
        val first = publishedDepartment("16")
        val second = publishedDepartment("17")
        regionalCoverage("16", "17")
        managerState.value = managerState.value.copy(allPacks = listOf(region, first, second))
        coEvery { manager.actualAvailableBytes() } returns 1000L
        coEvery { manager.installPack(any(), any<PackArtifactIdentity>(), any()) } returns Unit
        coEvery { manager.installPack(first.id, any<PackArtifactIdentity>(), any()) } throws IOException("Interruption réseau — fixture JVM")
        viewModel.prepareDownload(setOf(region.id, first.id))
        runCurrent()

        viewModel.confirmDownload()
        runCurrent()

        for (pack in listOf(region, first, second)) {
            coVerify(exactly = 1) { manager.installPack(pack.id, PackArtifactIdentity(pack.id, pack.version, requireNotNull(pack.checksum)), any()) }
        }
        assertTrue(first.id in viewModel.actionState.value.errors)
        assertFalse(viewModel.actionState.value.downloadingSelection)
    }

    @Test
    fun `catalogue en cours de rafraichissement ne prepare pas un groupe instable`() = runTest(dispatcher) {
        val region = publishedRegion()
        managerState.value = managerState.value.copy(allPacks = listOf(region), catalogRefreshing = true)
        viewModel.prepareDownload(setOf(region.id))
        runCurrent()
        assertEquals(null, viewModel.actionState.value.plan)
        assertTrue(viewModel.actionState.value.planError != null)
        coVerify(exactly = 0) { manager.administrativeDepartmentsByRegion() }
    }

    @Test
    fun `la version active exacte ne declenche aucun transfert`() = runTest(dispatcher) {
        val pack = publishedPack()
        managerState.value = managerState.value.copy(allPacks = listOf(pack), activePacks = listOf(pack.copy(status = PackStatus.INSTALLED)))
        coEvery { manager.actualAvailableBytes() } returns 1000L

        viewModel.prepareDownload(setOf(pack.id))
        runCurrent()
        viewModel.confirmDownload()
        runCurrent()

        assertEquals(0L, viewModel.actionState.value.plan?.archiveBytes)
        assertTrue(viewModel.actionState.value.plan?.packs.orEmpty().isEmpty())
        coVerify(exactly = 0) { manager.installPack(any(), any<PackArtifactIdentity>(), any()) }
    }

    @Test
    fun `confirmation refuse une empreinte changee meme si id version restent identiques`() = runTest(dispatcher) {
        val pack = publishedPack()
        managerState.value = managerState.value.copy(allPacks = listOf(pack))
        coEvery { manager.actualAvailableBytes() } returns 1000L
        viewModel.prepareDownload(setOf(pack.id))
        runCurrent()
        managerState.value = managerState.value.copy(allPacks = listOf(pack.copy(checksum = "b".repeat(64))))

        viewModel.confirmDownload()
        runCurrent()

        assertTrue(viewModel.actionState.value.planError.orEmpty().contains("catalogue"))
        assertFalse(viewModel.actionState.value.downloadingSelection)
        coVerify(exactly = 0) { manager.installPack(any(), any<PackArtifactIdentity>(), any()) }
    }

    @Test
    fun `confirmation remesure espace libre avant le premier transfert`() = runTest(dispatcher) {
        val pack = publishedPack()
        managerState.value = managerState.value.copy(allPacks = listOf(pack))
        coEvery { manager.actualAvailableBytes() } returnsMany listOf(1000L, 10L)
        viewModel.prepareDownload(setOf(pack.id))
        runCurrent()

        viewModel.confirmDownload()
        runCurrent()

        assertTrue(viewModel.actionState.value.planError.orEmpty().contains("espace"))
        coVerify(exactly = 2) { manager.actualAvailableBytes() }
        coVerify(exactly = 0) { manager.installPack(any(), any<PackArtifactIdentity>(), any()) }
    }

    @Test
    fun `confirmation transmet uniquement identite immuable acceptee`() = runTest(dispatcher) {
        val pack = publishedPack()
        val expected = PackArtifactIdentity(pack.id, pack.version, requireNotNull(pack.checksum))
        managerState.value = managerState.value.copy(allPacks = listOf(pack))
        coEvery { manager.actualAvailableBytes() } returns 1000L
        coEvery { manager.installPack(pack.id, expected, any()) } returns Unit
        viewModel.prepareDownload(setOf(pack.id))
        runCurrent()

        viewModel.confirmDownload()
        viewModel.confirmDownload()
        runCurrent()

        coVerify(exactly = 1) { manager.installPack(pack.id, expected, any()) }
        coVerify(exactly = 0) { manager.installPack(pack.id, any<(Float) -> Unit>()) }
        assertFalse(viewModel.actionState.value.downloadingSelection)
        assertTrue(viewModel.actionState.value.running.isEmpty())
    }

    @Test
    fun `espace insuffisant est visible et bloque la confirmation`() = runTest(dispatcher) {
        val pack = publishedPack()
        managerState.value = managerState.value.copy(allPacks = listOf(pack))
        coEvery { manager.actualAvailableBytes() } returns 10L
        viewModel.prepareDownload(setOf(pack.id))
        runCurrent()

        assertFalse(requireNotNull(viewModel.actionState.value.plan).fits)
        viewModel.confirmDownload()
        runCurrent()
        coVerify(exactly = 0) { manager.installPack(any(), any<PackArtifactIdentity>(), any()) }
    }

    @Test
    fun `apercu provient du lecteur du fichier actif et erreur ne remplace pas par des assets`() = runTest(dispatcher) {
        val content = AdministrativePackContent("boundary-test-only", "label-test-only", "region:75", "test-v1", "test attribution", "test licence")
        coEvery { manager.readActiveAdministrativePack(packId) } returns content
        viewModel.previewInstalledPack(packId)
        runCurrent()
        assertEquals(content, viewModel.actionState.value.preview)

        coEvery { manager.readActiveAdministrativePack(packId) } throws IOException("Fichier altéré")
        viewModel.previewInstalledPack(packId)
        runCurrent()
        assertEquals(null, viewModel.actionState.value.preview)
        assertEquals("Fichier altéré", viewModel.actionState.value.previewError)
        assertFalse(viewModel.actionState.value.loadingPreview)
    }

    @Test
    fun `refresh du catalogue est unique tant que le precedent est actif`() = runTest(dispatcher) {
        val finish = CompletableDeferred<Unit>()
        coEvery { manager.refreshCatalog() } coAnswers { finish.await() }
        viewModel.refreshCatalog()
        viewModel.refreshCatalog()
        runCurrent()
        coVerify(exactly = 1) { manager.refreshCatalog() }
        finish.complete(Unit)
        runCurrent()
        viewModel.refreshCatalog()
        runCurrent()
        coVerify(exactly = 2) { manager.refreshCatalog() }
    }


    @Test
    fun `garde URL autorise HTTPS public et limite HTTP a la recette locale debug`() {
        assertTrue(PackManagerViewModel.isAllowedDownloadUrl("https://gsie.example.invalid/api/v1/packs/test/artifact"))
        assertEquals(BuildConfig.DEBUG, PackManagerViewModel.isAllowedDownloadUrl("http://10.0.2.2:8001/api/v1/packs/test/artifact"))
        assertFalse(PackManagerViewModel.isAllowedDownloadUrl("http://gsie.example.invalid/api/v1/packs/test/artifact"))
        assertFalse(PackManagerViewModel.isAllowedDownloadUrl("http://192.168.1.20/api/v1/packs/test/artifact"))
        assertFalse(PackManagerViewModel.isAllowedDownloadUrl("https://user:secret@gsie.example.invalid/api/v1/packs/test/artifact"))
    }

    @Test
    fun `preparation accepte distribution du serveur local uniquement dans build debug`() = runTest(dispatcher) {
        val pack = publishedPack().copy(downloadUrl = "http://10.0.2.2:8001/api/v1/packs/test/artifact")
        managerState.value = managerState.value.copy(allPacks = listOf(pack))
        coEvery { manager.actualAvailableBytes() } returns 1000L
        viewModel.prepareDownload(setOf(pack.id))
        runCurrent()
        assertEquals(BuildConfig.DEBUG, viewModel.actionState.value.plan != null)
        assertEquals(!BuildConfig.DEBUG, viewModel.actionState.value.planError != null)
    }


    @Test
    fun `erreur reseau du refresh reste affichee sans exception non geree`() = runTest(dispatcher) {
        coEvery { manager.refreshCatalog() } coAnswers {
            managerState.value = managerState.value.copy(catalogIssue = PackCatalogIssue.NETWORK, catalogError = "Réseau indisponible")
            throw IOException("Réseau indisponible")
        }
        viewModel.refreshCatalog()
        runCurrent()
        assertEquals(PackCatalogIssue.NETWORK, viewModel.packState.value.catalogIssue)
        assertEquals("Réseau indisponible", viewModel.packState.value.catalogError)
        assertFalse(viewModel.actionState.value.catalogRefreshFailed)

        coEvery { manager.refreshCatalog() } throws IllegalStateException("Erreur inattendue du lecteur")
        managerState.value = managerState.value.copy(catalogIssue = null, catalogError = null)
        viewModel.refreshCatalog()
        runCurrent()
        assertTrue(viewModel.actionState.value.catalogRefreshFailed)
    }


    @Test
    fun `presentation du catalogue ignore URL et exception brutes y compris hors ligne avec cache`() = runTest(dispatcher) {
        val raw = "Failed to connect to /10.0.2.2:8001; internal-path=/private/packs secret=test-only"
        val offline = managerState.value.copy(catalogIssue = PackCatalogIssue.NETWORK, catalogError = raw, catalogFromCache = true)
        assertEquals(PackManagerViewModel.CatalogMessage.NETWORK, PackManagerViewModel.catalogMessage(offline))
        assertEquals(PackManagerViewModel.CatalogMessage.CACHE, PackManagerViewModel.catalogMessage(offline.copy(catalogIssue = null, catalogError = null)))
        assertEquals(PackManagerViewModel.CatalogMessage.UNAVAILABLE, PackManagerViewModel.catalogMessage(offline.copy(catalogIssue = null)))
        for ((issue, expected) in listOf(
            PackCatalogIssue.AUTH_REQUIRED to PackManagerViewModel.CatalogMessage.AUTH_REQUIRED,
            PackCatalogIssue.NOT_CONFIGURED to PackManagerViewModel.CatalogMessage.NOT_CONFIGURED,
            PackCatalogIssue.INVALID_RESPONSE to PackManagerViewModel.CatalogMessage.INVALID_RESPONSE
        )) {
            assertEquals(expected, PackManagerViewModel.catalogMessage(offline.copy(catalogIssue = issue)))
        }
        coEvery { manager.refreshCatalog() } throws IOException(raw)
        viewModel.refreshCatalog()
        runCurrent()
        assertTrue(viewModel.actionState.value.catalogRefreshFailed)
        assertEquals(PackManagerViewModel.CatalogMessage.UNAVAILABLE,
            PackManagerViewModel.catalogMessage(managerState.value, viewModel.actionState.value.catalogRefreshFailed))
        assertFalse(viewModel.actionState.value.toString().contains(raw))
    }

}
