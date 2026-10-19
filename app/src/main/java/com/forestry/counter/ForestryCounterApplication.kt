package com.forestry.counter

import android.app.Application
import androidx.room.Room
import com.forestry.counter.data.local.ForestryDatabase
import com.forestry.counter.data.local.DatabaseMigrations
import com.forestry.counter.data.local.CanonicalEssences
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.data.repository.CounterRepositoryImpl
import com.forestry.counter.data.repository.DiagnosticSylvicoleRepositoryImpl
import com.forestry.counter.data.repository.ForetRepositoryImpl
import com.forestry.counter.data.repository.FormulaRepositoryImpl
import com.forestry.counter.data.repository.GroupRepositoryImpl
import com.forestry.counter.data.repository.InventaireSessionRepositoryImpl
import com.forestry.counter.data.repository.ObservationFloreRepositoryImpl
import com.forestry.counter.data.repository.ParcelleRepositoryImpl
import com.forestry.counter.data.repository.PlacetteRepositoryImpl
import com.forestry.counter.data.repository.EssenceRepositoryImpl
import com.forestry.counter.data.repository.StationEnvironnementaleRepositoryImpl
import com.forestry.counter.data.repository.TigeRepositoryImpl
import com.forestry.counter.data.repository.ParameterRepositoryImpl
import com.forestry.counter.data.repository.ValeurFonciereRepositoryImpl
import com.forestry.counter.data.repository.RipisylveRepositoryImpl
import com.forestry.counter.data.repository.StationRepositoryImpl
import com.forestry.counter.domain.repository.RipisylveRepository
import com.forestry.counter.domain.repository.StationRepository
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.data.repository.IdentityRepositoryFactory
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.data.sync.ParcelSyncRepositoryFactory
import com.forestry.counter.domain.calculator.FormulaParser
import com.forestry.counter.domain.calculation.ForestryCalculator
import com.forestry.counter.domain.calculation.PeuplementAvantCoupeCalculator
import com.forestry.counter.domain.repository.CounterRepository
import com.forestry.counter.domain.repository.DiagnosticSylvicoleRepository
import com.forestry.counter.domain.repository.ForetRepository
import com.forestry.counter.domain.repository.FormulaRepository
import com.forestry.counter.domain.repository.GroupRepository
import com.forestry.counter.domain.repository.InventaireSessionRepository
import com.forestry.counter.domain.repository.ObservationFloreRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.ParcelSyncRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import com.forestry.counter.domain.repository.EssenceRepository
import com.forestry.counter.domain.repository.StationEnvironnementaleRepository
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.domain.repository.IbpRepository
import com.forestry.counter.domain.repository.ParameterRepository
import com.forestry.counter.domain.repository.ValeurFonciereRepository
import com.forestry.counter.data.repository.IbpRepositoryImpl
import com.forestry.counter.domain.usecase.export.ExportDataUseCase
import com.forestry.counter.domain.usecase.import.ImportDataUseCase
import com.forestry.counter.data.logging.CrashLogger
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.forestry.counter.domain.parameters.ParameterKeys
import com.forestry.counter.data.parameters.ParameterDefaults
import com.forestry.counter.domain.model.ParameterItem
import com.forestry.counter.data.sylviculture.SylvicultureDataSeeder
import com.forestry.counter.domain.location.LocalisationResolverService

class ForestryCounterApplication : Application(), com.forestry.counter.data.sync.hierarchy.HierarchySyncOwner {

    companion object {
        init {
            // Load SQLCipher native library (required before any DB operation)
            System.loadLibrary("sqlcipher")
        }
    }

    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Database
    lateinit var database: ForestryDatabase
        private set

    // Repositories
    lateinit var woodObjectRepository: com.forestry.counter.domain.repository.WoodObjectRepository
        private set
    lateinit var fieldRepository: com.forestry.counter.domain.repository.FieldRepository
        private set
    lateinit var woodDepotRepository: com.forestry.counter.domain.repository.WoodDepotRepository
        private set
    lateinit var groupRepository: GroupRepository
        private set
    lateinit var counterRepository: CounterRepository
        private set
    lateinit var formulaRepository: FormulaRepository
        private set
    lateinit var parcelleRepository: ParcelleRepository
        private set
    lateinit var placetteRepository: PlacetteRepository
        private set
    lateinit var essenceRepository: EssenceRepository
        private set
    lateinit var tigeRepository: TigeRepository
        private set
    lateinit var parameterRepository: ParameterRepository
        private set
    lateinit var ibpRepository: IbpRepository
        private set
    lateinit var foretRepository: ForetRepository
        private set
    lateinit var inventaireSessionRepository: InventaireSessionRepository
        private set
    lateinit var surveyContextRepository: com.forestry.counter.domain.repository.SurveyContextRepository
        private set
    lateinit var observationQualificationRepository: com.forestry.counter.domain.repository.ObservationQualificationRepository
        private set
    lateinit var stationEnvironnementaleRepository: StationEnvironnementaleRepository
        private set
    lateinit var diagnosticSylvicoleRepository: DiagnosticSylvicoleRepository
        private set
    lateinit var observationFloreRepository: ObservationFloreRepository
        private set
    lateinit var valeurFonciereRepository: ValeurFonciereRepository
        private set
    lateinit var ripisylveRepository: RipisylveRepository
        private set
    lateinit var stationRepository: StationRepository
        private set
    lateinit var identityRepository: IdentityRepository
        private set
    lateinit var parcelleSyncRepository: ParcelSyncRepository
        private set
    override lateinit var hierarchySyncRepository: com.forestry.counter.domain.repository.HierarchySyncRepository
        private set
    lateinit var businessReadAccess: com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
        private set
    lateinit var projectRepository: com.forestry.counter.domain.repository.ProjectRepository
        private set

    // Services
    lateinit var localisationResolverService: LocalisationResolverService
        private set
    lateinit var metadataService: MetadataService
        private set

    // Preferences
    lateinit var userPreferences: UserPreferencesManager
        private set

    // Use cases
    lateinit var exportDataUseCase: ExportDataUseCase
        private set
    lateinit var importDataUseCase: ImportDataUseCase
        private set
    lateinit var deleteAllUserDataUseCase: com.forestry.counter.domain.usecase.privacy.DeleteAllUserDataUseCase
        private set

    // Calculator
    lateinit var formulaParser: FormulaParser
        private set
    lateinit var forestryCalculator: ForestryCalculator
        private set
    lateinit var peuplementAvantCoupeCalculator: PeuplementAvantCoupeCalculator
        private set

    // Offline tiles
    lateinit var offlineTileManager: com.forestry.counter.domain.location.OfflineTileManager
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize encryption service and get SQLCipher key from Android Keystore
        val encryptionService = com.forestry.counter.security.DatabaseEncryptionService(this)
        val dbKey = encryptionService.getOrCreateDatabaseKey()

        // Room opens lazily. Its factory owns a separate key copy until that opening;
        // clearing the caller's buffer must never change the actual SQLCipher key.
        try {
            database = ForestryDatabase.createDatabase(this, DatabaseMigrations.ALL, dbKey)
        } finally {
            dbKey.fill(0)
        }

        // Install crash logger (controlled via settings)
        CrashLogger.install(this)

        // Initialiser le compte et la file avant les dépôts métier permet de
        // mettre chaque mutation de parcelle en attente, sans bloquer le mode local.
        userPreferences = UserPreferencesManager(applicationContext)
        identityRepository = IdentityRepositoryFactory.create(applicationContext)
        // Service metadata — renseigne auteur/source/version à l'écriture (spec §3.1).
        // Le accountProvider lit l'identité courante de façon synchrone via le StateFlow.
        metadataService = MetadataService(
            accountProvider = { identityRepository.session.value?.accountId }
        )
        val mutationGuardFactory = com.forestry.counter.data.sync.hierarchy.BusinessMutationGuard(identityRepository)::capture
        woodObjectRepository = com.forestry.counter.data.repository.WoodObjectRepositoryImpl(database, identityRepository)
        woodDepotRepository = com.forestry.counter.data.repository.WoodDepotRepositoryImpl(database, identityRepository)
        businessReadAccess = com.forestry.counter.data.sync.hierarchy.BusinessReadAccess(
            identityRepository, database.parcelSyncDao(),
        )
        fieldRepository = com.forestry.counter.data.repository.FieldRepositoryImpl(database, identityRepository, businessReadAccess)
        businessReadAccess.startObserving(applicationScope)
        val mutationJournal = com.forestry.counter.data.sync.ParcelMutationJournal(
            parcelleDao = database.parcelleDao(),
            syncDao = database.parcelSyncDao(),
            operationDao = database.parcelSyncOperationDao(),
            apiOrigin = BuildConfig.GSIE_API_BASE_URL.trim().trimEnd('/').takeIf { it.isNotBlank() },
            accountProvider = { identityRepository.session.value?.accountId },
            onCommitted = { applicationScope.launch {
                ParcelSyncRepositoryFactory.resumeStoredWork(parcelleSyncRepository)
            } },
            mutationGuardFactory = mutationGuardFactory,
        )
        parcelleSyncRepository = ParcelSyncRepositoryFactory.create(
            context = applicationContext,
            syncDao = database.parcelSyncDao(),
            parcelleDao = database.parcelleDao(),
            operationDao = database.parcelSyncOperationDao(),
            mutationJournal = mutationJournal,
            identityRepository = identityRepository,
        )
        hierarchySyncRepository = com.forestry.counter.data.sync.hierarchy.HierarchySyncRepositoryFactory.create(
            applicationContext, database.hierarchySyncDao(), identityRepository,
        )
        val martelageClock = com.forestry.counter.data.service.AndroidMartelageClock(this)

        // Initialize calculator
        formulaParser = FormulaParser()

        // Initialize repositories
        groupRepository = GroupRepositoryImpl(
            database,
            database.groupDao(),
            database.counterDao(),
            database.formulaDao(),
            database.groupVariableDao(),
            database.parcelleDao(),
            database.placetteDao(),
            database.tigeDao(),
            readAccess = businessReadAccess,
        )

        counterRepository = CounterRepositoryImpl(
            database.counterDao(),
            database.formulaDao(),
            database.groupVariableDao(),
            formulaParser
        )

        formulaRepository = FormulaRepositoryImpl(
            database.formulaDao(),
            database.counterDao(),
            database.groupVariableDao(),
            formulaParser
        )

        // Forestry repositories
        parcelleRepository = ParcelleRepositoryImpl(
            parcelleDao = database.parcelleDao(),
            metadataService = metadataService,
            mutationJournal = mutationJournal,
            tigeDao = database.tigeDao(),
            placetteDao = database.placetteDao(),
            readAccess = businessReadAccess,
        )
        placetteRepository = PlacetteRepositoryImpl(database.placetteDao(), metadataService,
            mutationGuardFactory = mutationGuardFactory, readAccess = businessReadAccess)
        essenceRepository = EssenceRepositoryImpl(database.essenceDao(), metadataService)
        tigeRepository = TigeRepositoryImpl(database.tigeDao(), metadataService, martelageClock,
            mutationGuardFactory = mutationGuardFactory, readAccess = businessReadAccess)
        observationQualificationRepository = com.forestry.counter.data.repository.ObservationQualificationRepositoryImpl(
            database.tigeDao(), database.inventaireSessionDao(), database.observationQualificationDao(),
            metadataService, businessReadAccess, mutationGuardFactory)
        parameterRepository = ParameterRepositoryImpl(database.parameterDao())
        ibpRepository = IbpRepositoryImpl(database.ibpEvaluationDao())
        foretRepository = ForetRepositoryImpl(database.foretDao(), metadataService,
            database.parcelSyncDao(), database.parcelSyncOperationDao(), mutationGuardFactory,
            readAccess = businessReadAccess)
        inventaireSessionRepository = InventaireSessionRepositoryImpl(database.inventaireSessionDao(), metadataService, martelageClock,
            mutationGuardFactory = mutationGuardFactory, readAccess = businessReadAccess,
            surveyContextDao = database.surveyContextDao(),
            qualificationDao = database.observationQualificationDao()).also { surveyContextRepository = it }
        stationEnvironnementaleRepository = StationEnvironnementaleRepositoryImpl(database.stationEnvironnementaleDao())
        diagnosticSylvicoleRepository = DiagnosticSylvicoleRepositoryImpl(database.diagnosticSylvicoleDao(), metadataService)
        observationFloreRepository = ObservationFloreRepositoryImpl(database.observationFloreDao(), metadataService)
        valeurFonciereRepository = ValeurFonciereRepositoryImpl(database.valeurFonciereDao())
        ripisylveRepository = RipisylveRepositoryImpl(database.ripisylveDao(), metadataService)
        stationRepository = StationRepositoryImpl(database.stationDao(), metadataService)
        localisationResolverService = LocalisationResolverService(parcelleRepository, stationEnvironnementaleRepository)
        projectRepository = com.forestry.counter.data.repository.ProjectRepositoryImpl(database.projectDao())

        // Initialize forestry calculator
        forestryCalculator = ForestryCalculator(parameterRepository)
        // Initialize pre-harvest stand calculator
        peuplementAvantCoupeCalculator = PeuplementAvantCoupeCalculator()

        // Initialize offline tile manager
        offlineTileManager = com.forestry.counter.domain.location.OfflineTileManager(applicationContext)

        applyAppLocale()

        // Initialize use cases
        exportDataUseCase = ExportDataUseCase(
            applicationContext,
            groupRepository,
            counterRepository,
            formulaRepository
        )

        importDataUseCase = ImportDataUseCase(
            applicationContext,
            groupRepository,
            counterRepository,
            formulaRepository
        )

        // Use case RGPD — droit à l'effacement (Art. 17)
        deleteAllUserDataUseCase = com.forestry.counter.domain.usecase.privacy.DeleteAllUserDataUseCase(
            foretDao = database.foretDao(),
            parcelleDao = database.parcelleDao(),
            tigeDao = database.tigeDao(),
            placetteDao = database.placetteDao(),
            inventaireSessionDao = database.inventaireSessionDao(),
            stationDao = database.stationDao(),
            ripisylveDao = database.ripisylveDao(),
            ibpEvaluationDao = database.ibpEvaluationDao(),
            arbreHabitatDao = database.arbreHabitatDao(),
            diagnosticSylvicoleDao = database.diagnosticSylvicoleDao(),
            alerteSanitaireDao = database.alerteSanitaireDao(),
            observationFloreDao = database.observationFloreDao(),
            context = applicationContext,
            operationDao = database.parcelSyncOperationDao(),
            syncDao = database.parcelSyncDao(),
            hierarchySyncDao = database.hierarchySyncDao(),
            woodDepotDao = database.woodDepotDao(),
            woodObjectDao = database.woodObjectDao(),
            woodDataGate = database.woodDataGate,
            fieldDao = database.fieldDao(),
        )

        applicationScope.launch {
            identityRepository.session.collect {
                ParcelSyncRepositoryFactory.resumeStoredWork(parcelleSyncRepository)
            }
        }
        com.forestry.counter.data.sync.hierarchy.HierarchySyncRepositoryFactory.startObserving(
            hierarchySyncRepository, applicationScope,
        )

        applicationScope.launch { seedSylvicultureData() }
        applicationScope.launch { seedForestryParameters() }
    }

    private fun applyAppLocale() {
        // Utilise applicationScope (lié au cycle de vie de l'Application) plutôt qu'un
        // CoroutineScope orphelin qui causerait un memory leak (SupervisorJob non annulé).
        applicationScope.launch(Dispatchers.Main) {
            try {
                val lang = userPreferences.appLanguage.first()
                if (lang == "system") {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                } else {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
                }
            } catch (e: Exception) {
                android.util.Log.w("ForestryApp", "Impossible d'appliquer la locale : ${e.message}")
            }
        }
    }

    private suspend fun seedSylvicultureData() {
        SylvicultureDataSeeder(
            database.fertiliteEssenceSerDao(),
            database.projectionClimatiqueSerDao()
        ).seedIfEmpty()
    }

    private suspend fun seedForestryParameters() {
        seedParameterIfMissing(ParameterKeys.CLASSES_DIAM, ParameterDefaults.classesDiametreJson) { !it.contains("500") }
        seedParameterIfMissing(ParameterKeys.COEFS_VOLUME, ParameterDefaults.coefsVolumeJson) { !it.contains("500") }
        seedParameterIfMissing(ParameterKeys.HAUTEURS_DEFAUT, ParameterDefaults.hauteursDefautJson) { !it.contains("\"max\":500") }
        seedParameterIfMissing(ParameterKeys.RULES_PRODUITS, ParameterDefaults.reglesProduitsJson)
        seedParameterIfMissing(ParameterKeys.PRIX_MARCHE, ParameterDefaults.prixMarcheJson)
        seedParameterIfMissing(ParameterKeys.HEIGHT_MODES, "[]")
        seedParameterIfMissing(ParameterKeys.TARIF_SELECTION, """{"method":"ALGAN"}""")
        seedEssences()
    }

    private suspend fun seedParameterIfMissing(
        key: String,
        defaultJson: String,
        isStale: (String) -> Boolean = { false }
    ) {
        val existing = parameterRepository.getParameter(key).first()
        if (existing == null || isStale(existing.valueJson)) {
            parameterRepository.setParameter(ParameterItem(key = key, valueJson = defaultJson))
        }
    }

    private suspend fun seedEssences() {
        val existing = essenceRepository.getAllEssences().first()
        if (existing.isEmpty()) {
            essenceRepository.insertEssences(CanonicalEssences.ALL)
            return
        }
        val existingCodes = existing.map { it.code }.toSet()
        val canonicalMap = CanonicalEssences.ALL.associateBy { it.code }
        val missing = CanonicalEssences.ALL.filter { it.code !in existingCodes }
        if (missing.isNotEmpty()) essenceRepository.insertEssences(missing)
        for (tige in existing) {
            val canonical = canonicalMap[tige.code] ?: continue
            if (tige.densiteBois == null && canonical.densiteBois != null) {
                essenceRepository.updateEssence(tige.copy(
                    densiteBois       = canonical.densiteBois,
                    qualiteTypique    = tige.qualiteTypique    ?: canonical.qualiteTypique,
                    typeCoupePreferee = tige.typeCoupePreferee ?: canonical.typeCoupePreferee,
                    usageBois         = tige.usageBois         ?: canonical.usageBois,
                    vitesseCroissance = tige.vitesseCroissance ?: canonical.vitesseCroissance,
                    hauteurMaxM       = tige.hauteurMaxM       ?: canonical.hauteurMaxM,
                    diametreMaxCm     = tige.diametreMaxCm     ?: canonical.diametreMaxCm,
                    toleranceOmbre    = tige.toleranceOmbre    ?: canonical.toleranceOmbre,
                    remarques         = tige.remarques         ?: canonical.remarques
                ))
            }
        }
    }
}
