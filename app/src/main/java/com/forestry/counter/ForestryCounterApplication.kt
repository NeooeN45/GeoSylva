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

class ForestryCounterApplication : Application() {

    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Database
    lateinit var database: ForestryDatabase
        private set

    // Repositories
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

    // Services
    lateinit var localisationResolverService: LocalisationResolverService
        private set

    // Preferences
    lateinit var userPreferences: UserPreferencesManager
        private set

    // Use cases
    lateinit var exportDataUseCase: ExportDataUseCase
        private set
    lateinit var importDataUseCase: ImportDataUseCase
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

        // Initialize database
        database = Room.databaseBuilder(
            applicationContext,
            ForestryDatabase::class.java,
            ForestryDatabase.DATABASE_NAME
        )
            .addMigrations(*DatabaseMigrations.ALL)
            .build()

        // Install crash logger (controlled via settings)
        CrashLogger.install(this)

        // Initialize calculator
        formulaParser = FormulaParser()

        // Initialize repositories
        groupRepository = GroupRepositoryImpl(
            database.groupDao(),
            database.counterDao(),
            database.formulaDao(),
            database.groupVariableDao(),
            database.parcelleDao(),
            database.placetteDao(),
            database.tigeDao()
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
        parcelleRepository = ParcelleRepositoryImpl(database.parcelleDao())
        placetteRepository = PlacetteRepositoryImpl(database.placetteDao())
        essenceRepository = EssenceRepositoryImpl(database.essenceDao())
        tigeRepository = TigeRepositoryImpl(database.tigeDao())
        parameterRepository = ParameterRepositoryImpl(database.parameterDao())
        ibpRepository = IbpRepositoryImpl(database.ibpEvaluationDao())
        foretRepository = ForetRepositoryImpl(database.foretDao())
        inventaireSessionRepository = InventaireSessionRepositoryImpl(database.inventaireSessionDao())
        stationEnvironnementaleRepository = StationEnvironnementaleRepositoryImpl(database.stationEnvironnementaleDao())
        diagnosticSylvicoleRepository = DiagnosticSylvicoleRepositoryImpl(database.diagnosticSylvicoleDao())
        observationFloreRepository = ObservationFloreRepositoryImpl(database.observationFloreDao())
        valeurFonciereRepository = ValeurFonciereRepositoryImpl(database.valeurFonciereDao())
        ripisylveRepository = RipisylveRepositoryImpl(database.ripisylveDao())
        stationRepository = StationRepositoryImpl(database.stationDao())
        localisationResolverService = LocalisationResolverService(parcelleRepository, stationEnvironnementaleRepository)

        // Initialize forestry calculator
        forestryCalculator = ForestryCalculator(parameterRepository)
        // Initialize pre-harvest stand calculator
        peuplementAvantCoupeCalculator = PeuplementAvantCoupeCalculator()

        // Initialize offline tile manager
        offlineTileManager = com.forestry.counter.domain.location.OfflineTileManager(applicationContext)

        // Initialize preferences
        userPreferences = UserPreferencesManager(applicationContext)

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
