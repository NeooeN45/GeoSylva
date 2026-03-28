package com.forestry.counter

import android.app.Application
import androidx.room.Room
import com.forestry.counter.data.local.ForestryDatabase
import com.forestry.counter.data.local.DatabaseMigrations
import com.forestry.counter.data.local.CanonicalEssences
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.data.repository.CounterRepositoryImpl
import com.forestry.counter.data.repository.FormulaRepositoryImpl
import com.forestry.counter.data.repository.GroupRepositoryImpl
import com.forestry.counter.data.repository.ParcelleRepositoryImpl
import com.forestry.counter.data.repository.PlacetteRepositoryImpl
import com.forestry.counter.data.repository.EssenceRepositoryImpl
import com.forestry.counter.data.repository.TigeRepositoryImpl
import com.forestry.counter.data.repository.ParameterRepositoryImpl
import com.forestry.counter.domain.calculator.FormulaParser
import com.forestry.counter.domain.calculation.ForestryCalculator
import com.forestry.counter.domain.calculation.PeuplementAvantCoupeCalculator
import com.forestry.counter.domain.repository.CounterRepository
import com.forestry.counter.domain.repository.FormulaRepository
import com.forestry.counter.domain.repository.GroupRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import com.forestry.counter.domain.repository.EssenceRepository
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.domain.repository.IbpRepository
import com.forestry.counter.domain.repository.RipisylveRepository
import com.forestry.counter.domain.repository.StationRepository
import com.forestry.counter.domain.repository.ParameterRepository
import com.forestry.counter.data.repository.IbpRepositoryImpl
import com.forestry.counter.data.repository.RipisylveRepositoryImpl
import com.forestry.counter.data.repository.StationRepositoryImpl
import com.forestry.counter.domain.usecase.export.ExportDataUseCase
import com.forestry.counter.domain.usecase.import.ImportDataUseCase
import com.forestry.counter.data.logging.CrashLogger
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.forestry.counter.domain.parameters.ParameterKeys
import com.forestry.counter.data.parameters.ParameterDefaults
import com.forestry.counter.domain.model.ParameterItem
import com.forestry.counter.domain.usecase.brain.LocalBrainCore

class ForestryCounterApplication : Application() {

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
    lateinit var ripisylveRepository: RipisylveRepository
        private set
    lateinit var stationRepository: StationRepository
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
        ripisylveRepository = RipisylveRepositoryImpl(database.ripisylveDao())
        stationRepository = StationRepositoryImpl(database.stationDao())

        // Initialize forestry calculator
        forestryCalculator = ForestryCalculator(parameterRepository)
        // Initialize pre-harvest stand calculator
        peuplementAvantCoupeCalculator = PeuplementAvantCoupeCalculator()

        // Initialize offline tile manager
        offlineTileManager = com.forestry.counter.domain.location.OfflineTileManager(applicationContext)

        // Initialize preferences
        userPreferences = UserPreferencesManager(applicationContext)

        // Apply saved app language at startup
        try {
            val lang = runBlocking { userPreferences.appLanguage.first() }
            if (lang == "system") {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            } else {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
            }
        } catch (_: Throwable) {}

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

        // Seed default forestry parameters and essences if missing
        CoroutineScope(Dispatchers.IO).launch {
            val classes = parameterRepository.getParameter(ParameterKeys.CLASSES_DIAM).first()
            if (classes == null || !classes.valueJson.contains("500")) {
                parameterRepository.setParameter(
                    ParameterItem(
                        key = ParameterKeys.CLASSES_DIAM,
                        valueJson = ParameterDefaults.classesDiametreJson
                    )
                )
            }
            val coefs = parameterRepository.getParameter(ParameterKeys.COEFS_VOLUME).first()
            if (coefs == null || !coefs.valueJson.contains("500")) {
                parameterRepository.setParameter(
                    ParameterItem(
                        key = ParameterKeys.COEFS_VOLUME,
                        valueJson = ParameterDefaults.coefsVolumeJson
                    )
                )
            }
            val heights = parameterRepository.getParameter(ParameterKeys.HAUTEURS_DEFAUT).first()
            if (heights == null || !heights.valueJson.contains("\"max\":500")) {
                parameterRepository.setParameter(
                    ParameterItem(
                        key = ParameterKeys.HAUTEURS_DEFAUT,
                        valueJson = ParameterDefaults.hauteursDefautJson
                    )
                )
            }

            val rules = parameterRepository.getParameter(ParameterKeys.RULES_PRODUITS).first()
            if (rules == null) {
                parameterRepository.setParameter(
                    ParameterItem(
                        key = ParameterKeys.RULES_PRODUITS,
                        valueJson = ParameterDefaults.reglesProduitsJson
                    )
                )
            }
            val prices = parameterRepository.getParameter(ParameterKeys.PRIX_MARCHE).first()
            if (prices == null) {
                parameterRepository.setParameter(
                    ParameterItem(
                        key = ParameterKeys.PRIX_MARCHE,
                        valueJson = ParameterDefaults.prixMarcheJson
                    )
                )
            }
            val heightModes = parameterRepository.getParameter(ParameterKeys.HEIGHT_MODES).first()
            if (heightModes == null) {
                parameterRepository.setParameter(
                    ParameterItem(
                        key = ParameterKeys.HEIGHT_MODES,
                        valueJson = "[]"
                    )
                )
            }

            // Tarif de cubage par défaut : Algan (le plus polyvalent)
            val tarifSel = parameterRepository.getParameter(ParameterKeys.TARIF_SELECTION).first()
            if (tarifSel == null) {
                parameterRepository.setParameter(
                    ParameterItem(
                        key = ParameterKeys.TARIF_SELECTION,
                        valueJson = """{"method":"ALGAN"}"""
                    )
                )
            }

            // Seed FTS flora index (idempotent)
            LocalBrainCore.getInstance(applicationContext).ensureFtsIndexReady()

            val ess = essenceRepository.getAllEssences().first()
            if (ess.isEmpty()) {
                essenceRepository.insertEssences(CanonicalEssences.ALL)
            } else {
                val existingCodes = ess.map { it.code }.toSet()
                val canonicalMap = CanonicalEssences.ALL.associateBy { it.code }

                // Insert new essences not yet in database
                val missing = CanonicalEssences.ALL.filter { it.code !in existingCodes }
                if (missing.isNotEmpty()) {
                    essenceRepository.insertEssences(missing)
                }

                // Enrich existing essences with new forestry fields if still empty
                for (existing in ess) {
                    val canonical = canonicalMap[existing.code] ?: continue
                    if (existing.densiteBois == null && canonical.densiteBois != null) {
                        essenceRepository.updateEssence(existing.copy(
                            densiteBois = canonical.densiteBois,
                            qualiteTypique = existing.qualiteTypique ?: canonical.qualiteTypique,
                            typeCoupePreferee = existing.typeCoupePreferee ?: canonical.typeCoupePreferee,
                            usageBois = existing.usageBois ?: canonical.usageBois,
                            vitesseCroissance = existing.vitesseCroissance ?: canonical.vitesseCroissance,
                            hauteurMaxM = existing.hauteurMaxM ?: canonical.hauteurMaxM,
                            diametreMaxCm = existing.diametreMaxCm ?: canonical.diametreMaxCm,
                            toleranceOmbre = existing.toleranceOmbre ?: canonical.toleranceOmbre,
                            remarques = existing.remarques ?: canonical.remarques
                        ))
                    }
                }
            }
        }
    }
}
