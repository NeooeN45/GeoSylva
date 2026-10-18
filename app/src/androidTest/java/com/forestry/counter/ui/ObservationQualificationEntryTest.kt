package com.forestry.counter.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect as AndroidRect
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.IdlingResource
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.R
import com.forestry.counter.data.local.BusinessReadIdentityFixture
import com.forestry.counter.data.local.DatabaseMigrations
import com.forestry.counter.data.local.ForestryDatabase
import com.forestry.counter.data.local.entity.EssenceEntity
import com.forestry.counter.data.local.entity.HierarchySyncAssociationEntity
import com.forestry.counter.data.preferences.ThemeMode
import com.forestry.counter.data.repository.ForetRepositoryImpl
import com.forestry.counter.data.repository.InventaireSessionRepositoryImpl
import com.forestry.counter.data.repository.ObservationQualificationRepositoryImpl
import com.forestry.counter.data.repository.ParcelleRepositoryImpl
import com.forestry.counter.data.repository.PlacetteRepositoryImpl
import com.forestry.counter.data.repository.TigeRepositoryImpl
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.data.sync.ParcelMutationJournal
import com.forestry.counter.data.sync.hierarchy.BusinessMutationGuard
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.model.DiameterQualificationDraft
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.MartelageClock
import com.forestry.counter.domain.model.MartelageClockReading
import com.forestry.counter.domain.model.MartelageStates
import com.forestry.counter.domain.model.MeasurementOrigin
import com.forestry.counter.domain.model.ObservationQualificationDraft
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.SurveyContextDraft
import com.forestry.counter.domain.model.SurveyPurpose
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.presentation.screens.forestry.ObservationQualificationEntry
import com.forestry.counter.presentation.theme.ForestryCounterTheme
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

/**
 * Parcours Entry réel et vrais dépôts sur une base Room TEST neuve par cas.
 * Identités et mesures TEST explicites, sans jeton, préférence produit, GPS, tarif ou réseau.
 */
@RunWith(AndroidJUnit4::class)
class ObservationQualificationEntryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val testName = TestName()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val identity = BusinessReadIdentityFixture()
    private val metadata = MetadataService { identity.session.value?.accountId }
    private val clock = object : MartelageClock {
        override fun read() = MartelageClockReading(1_000L, 500L, 7)
    }
    private val guard get() = BusinessMutationGuard(identity)::capture
    private val saved = AtomicInteger(0)
    private val testContentMounted = mutableStateOf(true)
    private val dbName = "TEST-observation-qualification-entry-${UUID.randomUUID()}.db"
    private lateinit var database: ForestryDatabase
    private lateinit var access: BusinessReadAccess
    private lateinit var sessions: InventaireSessionRepositoryImpl
    private lateinit var trees: TigeRepositoryImpl
    private lateinit var qualifications: ObservationQualificationRepositoryImpl
    private lateinit var testScope: CoroutineScope
    private lateinit var observer: Job
    private lateinit var sessionId: String
    private var originalConfiguration: Configuration? = null
    private lateinit var nativeUiScope: CoroutineScope
    private lateinit var nativeRecomposer: Recomposer
    private lateinit var nativeRunner: Job
    private var nativeContentView: ComposeView? = null
    private var nativeIdlingResource: IdlingResource? = null
    private val nativeFailure = AtomicReference<Throwable?>(null)

    @Before
    @Suppress("DEPRECATION")
    fun create_only_the_test_hierarchy_and_start_its_access_observer() = runBlocking {
        identity.login(OWNER)
        saved.set(0)
        database = Room.databaseBuilder(context, ForestryDatabase::class.java, dbName)
            .addMigrations(*DatabaseMigrations.ALL).build()
        access = BusinessReadAccess(identity, database.parcelSyncDao())
        val forests = ForetRepositoryImpl(database.foretDao(), metadata, database.parcelSyncDao(),
            database.parcelSyncOperationDao(), guard, access)
        val journal = ParcelMutationJournal(database.parcelleDao(), database.parcelSyncDao(),
            database.parcelSyncOperationDao(), apiOrigin = null,
            accountProvider = { identity.session.value?.accountId }, mutationGuardFactory = guard)
        val parcels = ParcelleRepositoryImpl(database.parcelleDao(), metadata, journal,
            database.tigeDao(), database.placetteDao(), access)
        val plots = PlacetteRepositoryImpl(database.placetteDao(), metadata, guard, access)
        forests.insert(Foret(foretId = FOREST, nom = "TEST forêt de qualification", proprietaireNom = "TEST propriétaire",
            proprietaireEmail = null, gestionnaireNom = null, typeForet = null, objectifGestion = null,
            psgNumero = null, psgDateExpiration = null, departement = null, remarques = null,
            createdAt = 100L, updatedAt = 100L))
        parcels.insertParcelle(Parcelle(id = PARCEL, forestId = null, foretId = FOREST,
            name = "TEST parcelle", surfaceHa = null, shape = null, slopePct = null,
            aspect = null, access = null, altitudeM = null, objectifType = null, objectifVal = null,
            tolerancePct = null, samplingMode = null, sampleAreaM2 = null, targetSpeciesCsv = null,
            srid = 4326, remarks = null, createdAt = 100L, updatedAt = 100L))
        plots.insertPlacette(Placette(id = PLOT, parcelleId = PARCEL, name = "TEST placette",
            type = "CIRC", rayonM = 10.0, surfaceM2 = 314.159, centerWkt = null,
            createdAt = 100L, updatedAt = 100L))
        // Association de propriété uniquement dans la base TEST ; aucune connexion GSIE simulée.
        database.hierarchySyncDao().insertAssociation(HierarchySyncAssociationEntity(
            forestId = FOREST, accountId = OWNER, apiOrigin = "https://test.invalid",
            activatedAt = 100L, lastCapturedHash = null, serverVersion = null,
            lastSuccessAt = null, lastErrorCode = null))
        database.essenceDao().insertEssence(EssenceEntity(ESSENCE, "TEST chêne", null, null, null))
        sessions = InventaireSessionRepositoryImpl(database.inventaireSessionDao(), metadata, clock, guard,
            readAccess = access, surveyContextDao = database.surveyContextDao(),
            qualificationDao = database.observationQualificationDao())
        trees = TigeRepositoryImpl(database.tigeDao(), metadata, clock, guard, access)
        qualifications = ObservationQualificationRepositoryImpl(database.tigeDao(),
            database.inventaireSessionDao(), database.observationQualificationDao(), metadata, access, guard)
        sessionId = sessions.startForPlacetteWithContext(PARCEL, PLOT,
            SurveyContextDraft(purpose = SurveyPurpose.MARKING), guard(OWNER)).sessionId
        trees.insertTige(observation(FIRST, 101, 30.0, null))
        trees.insertTige(observation(SECOND, 102, 36.0, 22.0))
        assertNull(qualifications.getQualification(FIRST))
        assertNull(qualifications.getQualification(SECOND))
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        observer = access.startObserving(testScope)
        instrumentation.runOnMainSync {
            val resources = compose.activity.resources
            originalConfiguration = Configuration(resources.configuration)
            resources.updateConfiguration(Configuration(resources.configuration).apply { setLocale(Locale.FRANCE) },
                resources.displayMetrics)
        }
    }

    @After
    @Suppress("DEPRECATION")
    fun dispose_test_flows_restore_activity_and_delete_only_its_unique_database() {
        var cleanupFailure: Throwable? = null
        fun retainFailure(failure: Throwable) {
            val first = cleanupFailure
            if (first == null) cleanupFailure = failure
            else if (first !== failure) first.addSuppressed(failure)
        }
        fun cleanup(block: () -> Unit) {
            try { block() } catch (failure: Throwable) { retainFailure(failure) }
        }
        try {
            cleanup { exportAfterTestDiagnostics() }
            cleanup {
                compose.runOnUiThread {
                    testContentMounted.value = false
                    nativeContentView?.disposeComposition()
                    nativeContentView = null
                }
            }
            cleanup {
                if (::nativeRecomposer.isInitialized) {
                    runBlocking { withTimeout(TIMEOUT) { nativeRecomposer.awaitIdle() } }
                }
            }
        } finally {
            try {
                cleanup { if (::nativeRecomposer.isInitialized) nativeRecomposer.cancel() }
                cleanup { if (::nativeRunner.isInitialized) nativeRunner.cancel() }
                cleanup { if (::nativeUiScope.isInitialized) nativeUiScope.cancel() }
                cleanup {
                    if (::nativeRecomposer.isInitialized) {
                        runBlocking { withTimeout(TIMEOUT) { nativeRecomposer.join() } }
                    }
                }
                cleanup {
                    if (::nativeRunner.isInitialized) {
                        runBlocking { withTimeout(TIMEOUT) { nativeRunner.cancelAndJoin() } }
                    }
                }
                cleanup {
                    if (::nativeUiScope.isInitialized) {
                        runBlocking { withTimeout(TIMEOUT) { nativeUiScope.coroutineContext.job.cancelAndJoin() } }
                    }
                }
            } finally {
                cleanup { nativeIdlingResource?.let { compose.unregisterIdlingResource(it) } }
                nativeIdlingResource = null
                cleanup { runBlocking { if (::observer.isInitialized) observer.cancelAndJoin() } }
                cleanup { if (::testScope.isInitialized) testScope.cancel() }
                cleanup { if (::database.isInitialized) database.close() }
                cleanup { context.deleteDatabase(dbName) }
                cleanup {
                    originalConfiguration?.let { previous -> instrumentation.runOnMainSync {
                        val resources = compose.activity.resources
                        resources.updateConfiguration(previous, resources.displayMetrics)
                    } }
                }
                originalConfiguration = null
            }
        }
        nativeFailure.get()?.let { retainFailure(AssertionError("Échec de la composition Android native TEST.", it)) }
        cleanupFailure?.let { throw it }
    }

    @Test fun should_open_the_real_list_and_save_only_one_partial_declaration() {
        val rawBefore = runBlocking { database.tigeDao().getAllTigesNow() }
        content()
        openList()
        item(FIRST).assertIsDisplayed().assertHasClickAction()
        item(SECOND).assertIsDisplayed()
        captureScreenshot("qualification-liste-fr-normal.png")
        item(FIRST).performClick()
        awaitDialog()
        compose.onNodeWithTag("qualification_section_diameter").performScrollTo().performClick()
        compose.onNodeWithTag("qualification_diameter_origin_MEASURED").performScrollTo().performClick()
        compose.onNodeWithTag("qualification_save").assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitUntil(TIMEOUT) { saved.get() == 1 }
        runBlocking {
            val record = requireNotNull(qualifications.getQualification(FIRST)).record
            assertEquals(ObservationQualificationDraft(diameter = DiameterQualificationDraft(
                origin = MeasurementOrigin.MEASURED)), record.payload.draft)
            assertEquals(OWNER, record.createdBy)
            assertNull(qualifications.getQualification(SECOND))
            assertEquals(rawBefore, database.tigeDao().getAllTigesNow())
        }
        compose.onNodeWithTag("qualification_dialog_panel").assertDoesNotExist()
        compose.onNodeWithTag("observation_qualification_list").assertIsDisplayed()
    }

    @Test fun should_dispose_list_and_editor_on_generation_change_and_deny_the_foreign_account() {
        val rawBefore = runBlocking { database.tigeDao().getAllTigesNow() }
        content()
        openList()
        compose.runOnIdle { identity.login(OWNER) }
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag("observation_qualification_list").fetchSemanticsNodes().isEmpty()
        }
        openList()
        item(FIRST).performClick()
        awaitDialog()
        compose.runOnIdle { identity.login(OTHER) }
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag("qualification_dialog_panel").fetchSemanticsNodes().isEmpty() &&
                compose.onNodeWithTag("observation_qualification_open").fetchSemanticsNode()
                    .config.contains(SemanticsProperties.Disabled)
        }
        compose.onNodeWithTag("observation_qualification_list").assertDoesNotExist()
        compose.onNodeWithTag("observation_qualification_open").assertIsNotEnabled()
        runBlocking {
            assertFalse(access.canReadParcelle(PARCEL))
            assertTrue(trees.getTigesByPlacette(PLOT).first().isEmpty())
            assertNull(database.observationQualificationDao().getByTige(FIRST))
            assertNull(database.observationQualificationDao().getByTige(SECOND))
            assertEquals(rawBefore, database.tigeDao().getAllTigesNow())
        }
        assertEquals(0, saved.get())
    }

    @Test fun should_allow_a_paused_declaration_then_consult_the_closed_seal_without_editing() {
        runBlocking { sessions.pauseMartelage(sessionId) }
        content()
        openList()
        item(FIRST).performClick()
        awaitDialog()
        compose.onNodeWithTag("qualification_save").assertIsEnabled().performClick()
        compose.waitUntil(TIMEOUT) { saved.get() == 1 }
        runBlocking {
            assertEquals(MartelageStates.PAUSED, sessions.getById(sessionId)?.martelageState)
            sessions.closeMartelage(sessionId, null)
        }
        val closed = runBlocking { database.inventaireSessionDao().getById(sessionId) }
        val sealed = runBlocking { requireNotNull(database.observationQualificationDao().getByTige(FIRST)) }
        assertNotNull(sealed.sealedPayloadJson)
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag("observation_qualification_list").fetchSemanticsNodes().isNotEmpty()
        }
        item(FIRST).performClick()
        awaitDialog()
        compose.onNodeWithTag("qualification_save").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.observation_qualification_read_only)).assertIsDisplayed()
        compose.onNodeWithTag("qualification_support_INDIVIDUAL_TREE").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("qualification_close").assertIsDisplayed().assertIsEnabled()
        runBlocking {
            assertEquals(closed, database.inventaireSessionDao().getById(sessionId))
            assertEquals(sealed, database.observationQualificationDao().getByTige(FIRST))
            assertNull(database.observationQualificationDao().getByTige(SECOND))
        }
    }

    @Test fun should_keep_the_real_list_and_its_actions_usable_with_large_local_typography() {
        content(fontScale = 2f)
        openList()
        item(FIRST).assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText(compose.activity.getString(R.string.observation_qualification_list_close))
            .assertIsDisplayed().assertIsEnabled()
        captureScreenshot("qualification-liste-fr-grande-police.png")
        compose.onNodeWithTag("observation_qualification_list").performScrollToNode(hasTestTag(itemTag(SECOND)))
        item(SECOND).assertIsDisplayed().assertHasClickAction().performClick()
        awaitDialog()
        compose.onNodeWithTag("qualification_close").assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag("observation_qualification_list").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, saved.get())
        runBlocking {
            assertNull(database.observationQualificationDao().getByTige(FIRST))
            assertNull(database.observationQualificationDao().getByTige(SECOND))
        }
    }

    @Test fun should_make_an_open_editor_read_only_when_its_campaign_is_closed() {
        val rawBefore = runBlocking { database.tigeDao().getAllTigesNow() }
        content()
        openList()
        item(FIRST).performClick()
        awaitDialog()
        compose.onNodeWithTag("qualification_section_diameter").performScrollTo().performClick()
        compose.onNodeWithTag("qualification_diameter_origin_MEASURED").performScrollTo().performClick()
        compose.onNodeWithTag("qualification_save").assertIsDisplayed().assertIsEnabled()
        // La vraie API clôture pendant que le dialogue et son brouillon non enregistré restent ouverts.
        val closed = runBlocking { sessions.closeMartelage(sessionId, null) }
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag("qualification_save").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("qualification_dialog_panel").assertIsDisplayed()
        compose.onNodeWithTag("qualification_diameter_origin_MEASURED").performScrollTo()
            .assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithTag("qualification_close").assertIsDisplayed().assertIsEnabled()
        runBlocking {
            assertEquals(MartelageStates.CLOSED, sessions.getById(sessionId)?.martelageState)
            assertEquals(closed.closedSnapshotJson,
                requireNotNull(database.inventaireSessionDao().getById(sessionId)).closedSnapshotJson)
            assertNull(database.observationQualificationDao().getByTige(FIRST))
            assertNull(database.observationQualificationDao().getByTige(SECOND))
            assertEquals(rawBefore, database.tigeDao().getAllTigesNow())
        }
        assertEquals(0, saved.get())
    }

    private fun content(fontScale: Float = 1f) {
        check(!::nativeRecomposer.isInitialized)
        compose.runOnUiThread {
            // La règle 1.7.2 impose son intercepteur même aux changements de contexte Room.
            // Ce parent explicite conserve les dispatchers Android/Room réels pour ce montage.
            val errors = CoroutineExceptionHandler { _, failure ->
                nativeFailure.compareAndSet(null, failure)
            }
            nativeUiScope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main + errors)
            nativeRecomposer = Recomposer(nativeUiScope.coroutineContext)
            nativeRunner = nativeUiScope.launch { nativeRecomposer.runRecomposeAndApplyChanges() }
            compose.activity.setContent(parent = nativeRecomposer) {
                ForestryCounterTheme(themeMode = ThemeMode.LIGHT) {
                    val original = MaterialTheme.typography
                    val scaled = remember(original, fontScale) { original.scaled(fontScale) }
                    MaterialTheme(typography = scaled) {
                        if (!testContentMounted.value) return@MaterialTheme
                        val observations by remember { trees.getTigesByPlacette(PLOT) }.collectAsState(emptyList())
                        val campaigns by remember { sessions.getByPlacette(PLOT) }.collectAsState(emptyList())
                        val selected = campaigns.firstOrNull { it.sessionId == sessionId }
                        Surface(Modifier.fillMaxSize()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("TEST · Placette · Chêne", style = MaterialTheme.typography.titleLarge)
                                ObservationQualificationEntry(PARCEL, PLOT, ESSENCE, "TEST chêne", selected,
                                    observations, selected != null, identity, access, trees, sessions, qualifications,
                                    onSaved = { saved.incrementAndGet() })
                            }
                        }
                    }
                }
            }
            val contentRoot = requireNotNull(compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content))
            nativeContentView = contentRoot.getChildAt(0) as? ComposeView
            checkNotNull(nativeContentView)
        }
        nativeIdlingResource = object : IdlingResource {
            override val isIdleNow: Boolean
                get() {
                    throwNativeFailure()
                    return !nativeRecomposer.hasPendingWork
                }

            override fun getDiagnosticMessageIfBusy() = "La composition Android native TEST a des changements en attente."
        }.also(compose::registerIdlingResource)
        compose.waitForIdle()
        throwNativeFailure()
    }

    private fun throwNativeFailure() {
        nativeFailure.get()?.let { throw AssertionError("Échec de la composition Android native TEST.", it) }
    }

    private fun openList() {
        compose.waitUntil(TIMEOUT) {
            val node = compose.onNodeWithTag("observation_qualification_open").fetchSemanticsNode()
            !node.config.contains(SemanticsProperties.Disabled)
        }
        compose.onNodeWithTag("observation_qualification_open").assertIsEnabled().performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag("observation_qualification_list").fetchSemanticsNodes().isNotEmpty()
        }
    }

    // Précondition du clic réel : la présence Semantics seule ne qualifie pas sa fenêtre.
    // Le délai et les scénarios restent inchangés ; aucune cause du timeout n'est présumée.
    private fun awaitDialog() = compose.waitUntil(TIMEOUT) {
        val panels = compose.onAllNodesWithTag("qualification_dialog_panel").fetchSemanticsNodes()
        val closes = compose.onAllNodesWithTag("qualification_close").fetchSemanticsNodes()
        val saves = compose.onAllNodesWithTag("qualification_save").fetchSemanticsNodes()
        if (panels.size != 1 || closes.size != 1 || saves.size > 1) false
        else if (!compose.onNodeWithTag("qualification_dialog_panel").isDisplayed() ||
            !compose.onNodeWithTag("qualification_close").isDisplayed() ||
            (saves.isNotEmpty() && !compose.onNodeWithTag("qualification_save").isDisplayed())) false
        else {
            var ready = false
            compose.runOnUiThread {
                val root = panels.single().root as? ViewRootForTest
                if (root != null) {
                    val view = root.view
                    val viewport = AndroidRect()
                    val commands = closes + saves
                    ready = view.isAttachedToWindow && view.isShown && view.hasWindowFocus() &&
                        view.rootView !== compose.activity.window.decorView &&
                        !root.hasPendingMeasureOrLayout && view.getLocalVisibleRect(viewport) &&
                        !viewport.isEmpty && commands.all { node ->
                            val bounds = node.boundsInRoot
                            node.root === root && bounds.width > 0f && bounds.height > 0f &&
                                listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).all { it.isFinite() } &&
                                bounds.left >= viewport.left - 1f && bounds.top >= viewport.top - 1f &&
                                bounds.right <= viewport.right + 1f && bounds.bottom <= viewport.bottom + 1f
                        }
                }
            }
            ready
        }
    }

    private fun itemTag(id: String) = "observation_qualification_item_$id"
    private fun item(id: String) = compose.onNodeWithTag(itemTag(id))

    private fun observation(id: String, number: Int, diameter: Double, height: Double?) = Tige(
        id = id, parcelleId = PARCEL, placetteId = PLOT, essenceCode = ESSENCE, diamCm = diameter,
        hauteurM = height, gpsWkt = null, precisionM = null, altitudeM = null, note = null,
        produit = null, fCoef = null, valueEur = null, timestamp = 300L + number,
        sessionId = sessionId, numero = number)

    private fun Typography.scaled(scale: Float) = Typography(
        displayLarge = displayLarge.scaled(scale), displayMedium = displayMedium.scaled(scale), displaySmall = displaySmall.scaled(scale),
        headlineLarge = headlineLarge.scaled(scale), headlineMedium = headlineMedium.scaled(scale), headlineSmall = headlineSmall.scaled(scale),
        titleLarge = titleLarge.scaled(scale), titleMedium = titleMedium.scaled(scale), titleSmall = titleSmall.scaled(scale),
        bodyLarge = bodyLarge.scaled(scale), bodyMedium = bodyMedium.scaled(scale), bodySmall = bodySmall.scaled(scale),
        labelLarge = labelLarge.scaled(scale), labelMedium = labelMedium.scaled(scale), labelSmall = labelSmall.scaled(scale))
    private fun TextStyle.scaled(scale: Float) = copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)

    private fun captureScreenshot(name: String) {
        compose.waitForIdle()
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val external = requireNotNull(context.getExternalFilesDir(null))
            val directory = File(external, "GeoSylva-TEST-observation-qualification-entry-2026-10-10/${UUID.randomUUID()}")
            assertTrue(directory.mkdirs())
            val destination = File(directory, name)
            assertTrue(destination.createNewFile())
            destination.outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val semantics = File(directory, "${name.removeSuffix(".png")}.semantics.txt")
            assertTrue(semantics.createNewFile())
            semantics.writeText(compose.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = 15), Charsets.UTF_8)
            assertTrue(destination.length() > 0L)
            println("GEOSYLVA_TEST_QUALIFICATION_ENTRY_SCREENSHOT=${destination.absolutePath}")
            println("GEOSYLVA_TEST_QUALIFICATION_ENTRY_SEMANTICS=${semantics.absolutePath}")
        } finally { screenshot.recycle() }
    }

    /** État TEST après le cas, avant toute disposition ; ces exports ne qualifient pas l'interface. */
    private fun exportAfterTestDiagnostics() {
        // Le scénario et ses assertions sont terminés. Un chargement animé ne doit pas empêcher
        // d'inspecter son état présent ; les erreurs natives restent conservées puis relancées.
        nativeIdlingResource?.let { compose.unregisterIdlingResource(it) }
        nativeIdlingResource = null
        val external = requireNotNull(context.getExternalFilesDir(null))
        val directory = File(external, "GeoSylva-TEST-observation-qualification-entry-2026-10-10/${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        val prefix = "DIAG-${testName.methodName ?: "unknown-test"}"
        var exportFailure: Throwable? = null
        fun export(block: () -> Unit) {
            try { block() } catch (failure: Throwable) {
                val first = exportFailure
                if (first == null) exportFailure = failure
                else if (first !== failure) first.addSuppressed(failure)
            }
        }
        export {
            val state = buildString {
                appendLine("TEST diagnostic, lecture seule de la base dédiée : $dbName")
                appendLine("case=${testName.methodName}")
                appendLine("saved=${saved.get()}")
                appendLine("nativePending=${if (::nativeRecomposer.isInitialized) nativeRecomposer.hasPendingWork else null}")
                appendLine("nativeFailure=${nativeFailure.get()}")
                if (::database.isInitialized && ::sessionId.isInitialized) {
                    val rows = runBlocking { withTimeout(TIMEOUT) {
                        Triple(database.inventaireSessionDao().getById(sessionId),
                            database.observationQualificationDao().getByTige(FIRST),
                            database.observationQualificationDao().getByTige(SECOND))
                    } }
                    appendLine("sessionId=$sessionId")
                    appendLine("sessionState=${rows.first?.martelageState}")
                    appendLine("sessionDeletedAt=${rows.first?.deletedAt}")
                    appendLine("closedSnapshotPresent=${rows.first?.closedSnapshotJson != null}")
                    listOf(FIRST to rows.second, SECOND to rows.third).forEach { (id, row) ->
                        appendLine("observation=$id")
                        appendLine("qualificationPresent=${row != null}")
                        appendLine("qualificationRevision=${row?.revision}")
                        appendLine("qualificationSealed=${row?.sealedPayloadJson != null}")
                        appendLine("qualificationPayload=${row?.payloadJson}")
                        appendLine("qualificationSealedPayload=${row?.sealedPayloadJson}")
                    }
                } else appendLine("databaseOrSessionUnavailable=true")
            }
            val destination = File(directory, "$prefix.state.txt")
            assertTrue(destination.createNewFile())
            destination.writeText(state, Charsets.UTF_8)
            println("GEOSYLVA_TEST_QUALIFICATION_ENTRY_DIAG_STATE=${destination.absolutePath}")
        }
        export {
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                val destination = File(directory, "$prefix.png")
                assertTrue(destination.createNewFile())
                destination.outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                assertTrue(destination.length() > 0L)
                println("GEOSYLVA_TEST_QUALIFICATION_ENTRY_DIAG_SCREENSHOT=${destination.absolutePath}")
            } finally { screenshot.recycle() }
        }
        export {
            val destination = File(directory, "$prefix.semantics.txt")
            assertTrue(destination.createNewFile())
            destination.writeText(compose.onAllNodes(isRoot(), useUnmergedTree = true)
                .printToString(maxDepth = 20), Charsets.UTF_8)
            println("GEOSYLVA_TEST_QUALIFICATION_ENTRY_DIAG_SEMANTICS=${destination.absolutePath}")
        }
        exportFailure?.let { throw it }
    }

    private companion object {
        const val TIMEOUT = 20_000L
        const val OWNER = "TEST-entry-owner"
        const val OTHER = "TEST-entry-foreign-account"
        const val FOREST = "99999999-9999-4999-8999-999999999999"
        const val PARCEL = "TEST-entry-parcel"
        const val PLOT = "TEST-entry-plot"
        const val ESSENCE = "TEST-entry-oak"
        const val FIRST = "TEST-entry-observation-01"
        const val SECOND = "TEST-entry-observation-02"
    }
}
