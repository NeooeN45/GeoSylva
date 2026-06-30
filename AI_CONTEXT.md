# GeoSylva — AI Context / Documentation technique de référence

## 0) À lire en premier (pour une nouvelle session IA)

Ce fichier est la **source de vérité technique** pour comprendre le code de GeoSylva.

**Ordre de lecture** :
1. `MASTER_PLAN.md` — vision, plan d'exécution, financement, écosystème
2. Ce fichier (`AI_CONTEXT.md`) — contexte technique
3. `AUDIT_FORESTIER_COMPLET.md` — audit vague 1 (DB, calculs, tarifs, foresterie)
4. `AUDIT_GLOBAL_GEOSYLVA.md` — audit vague 2 (sécurité, GIS, UI, i18n, RGPD, perf)
5. Le code

**Standards de qualité** : voir `MASTER_PLAN.md` §7 et `CONTRIBUTING.md`

---

## 1) Présentation

GeoSylva est une application Android (Kotlin / Jetpack Compose) de **gestion forestière de terrain** : inventaire, martelage, diagnostic sylvicole, IBP CNPF, cartographie, exports SIG.

**Version actuelle** : 2.3.0 (versionCode 9)
**DB version** : 29
**Statut** : En développement — **non déployable en production** (voir audits)

---

## 2) Stack technique

| Composant | Techno | Version | Notes |
|---|---|---|---|
| Langage | Kotlin | 1.9.23 | À monter vers 2.1.0 |
| UI | Jetpack Compose | BOM 2024.09.00 | À mettre à jour |
| Design | Material 3 | — | Dark mode + dynamic colors |
| Navigation | Navigation Compose | 2.8.5 | Sealed class Screen, 5 sous-graphes |
| Persistance | Room | 2.6.1 | DB v29, 4 entités commentées (problème KSP) |
| Préférences | DataStore | 1.0.0 | Non chiffré (à corriger) |
| Sérialisation | kotlinx.serialization | 1.6.3 | |
| Coroutines | kotlinx.coroutines | 1.8.1 | Bien scopées (viewModelScope, lifecycleScope) |
| Network | OkHttp | 4.12.0 | SecureHttpClient existe mais cert pinning désactivé |
| Maps | MapLibre GL | 10.3.1 | 12 couches carto |
| Camera | CameraX | 1.3.3 | Clinomètre numérique |
| Location | Play Services Location | 21.3.0 | + LocationManager legacy (à remplacer) |
| WorkManager | Work Runtime KTX | 2.9.0 | BackupWorker, PriceSyncWorker |
| Excel | Apache POI | 5.5.1 | Exports XLSX |
| CSV | OpenCSV | 5.9 | |
| Formules | exp4j | 0.4.8 | FormulaParser |
| Build | AGP | 8.2.2 | À monter vers 8.6.0+ |
| SDK | compileSdk/targetSdk | 35 | minSdk 26 |
| Chiffrement DB | SQLCipher | **DÉSACTIVÉ** | Commenté — CRITICAL à réactiver |

---

## 3) Architecture (Clean-ish)

```
com.forestry.counter/
├── data/                    # Couche données
│   ├── local/               # Room : entities, dao, ForestryDatabase
│   ├── preferences/         # DataStore : UserPreferences
│   ├── logging/             # CrashLogger
│   ├── work/                # WorkManager : BackupWorker, PriceSyncWorker
│   ├── repository/          # Implémentations repositories
│   ├── correlation/         # DataCorrelationEngine
│   ├── calculation/         # AdvancedCalculationEngine
│   ├── interpretation/      # DataInterpretationEngine
│   ├── sylviculture/        # SylvicultureDB (dataset)
│   └── confidence/          # ConfidenceEngine
├── domain/                  # Couche domaine
│   ├── model/               # Modèles : Tige, Parcelle, Placette, Essence, Counter, Group, Formula, IBP, etc.
│   ├── repository/          # Interfaces repositories
│   ├── calculator/          # FormulaParser, ForestryCalculator
│   ├── calculation/         # ExpertForestryCalculator, TarifCalculator, DecoupeCalculator
│   ├── geo/                 # Lambert93, ShapefileParser, GeoImportParser, SrtmElevationService, EmbeddedDemService
│   ├── location/            # Lambert93Converter, GpsAverager, GpsParcelTracer, WktUtils, StationDataAggregator, LocalisationResolverService
│   ├── ibp/                 # IbpCalculator, IbpTremCalculator
│   ├── diagnostic/          # DiagnosticSylvicoleEngine
│   ├── parameters/          # ParameterDefaults, ParameterKeys
│   ├── classification/      # Classification
│   └── usecase/             # Use cases (export, import, brain, confidence, network, pack, territory, autecology, florist, fertility, ripisylve, station, sylviculture)
├── network/                 # Couche réseau
│   ├── SecureHttpClient.kt  # OkHttp + cert pinning (DÉSACTIVÉ)
│   ├── SecureTileService.kt
│   └── MobileCoverageEngine.kt
├── security/                # Couche sécurité
│   └── DatabaseEncryptionService.kt  # Existe mais JAMAIS appelé
└── presentation/            # Couche présentation
    ├── MainActivity.kt
    ├── navigation/          # ForestryNavigation (5 sous-graphes)
    ├── screens/             # Tous les écrans Compose
    ├── components/          # Composants partagés
    ├── theme/               # Theme, Color, Type, Shape
    └── utils/               # HapticFeedback, HeightInputUtils, etc.
```

---

## 4) Points d'entrée

| Point | Fichier |
|---|---|
| Application | `ForestryCounterApplication.kt` |
| Activity | `presentation/MainActivity.kt` |
| Navigation | `presentation/navigation/ForestryNavigation.kt` |
| Database | `data/local/ForestryDatabase.kt` (DB v29) |
| Preferences | `data/preferences/UserPreferences.kt` |
| Calculator | `domain/calculation/ForestryCalculator.kt` |
| Tarifs | `domain/calculation/TarifCalculator.kt` + `TarifData.kt` |
| GIS | `domain/geo/Lambert93Converter.kt` (IGN NTG 71, bidirectionnelle) |
| GPS | `domain/location/GpsAverager.kt` (MAD + inverse-variance) |
| IBP | `domain/ibp/IbpCalculator.kt` (CNPF officiel 0/2/5, max 50) |
| Exports | `domain/usecase/export/` (CSV, XLSX, JSON, ZIP, Shapefile, GeoJSON, PDF, QGIS) |

---

## 5) Navigation (routes principales)

Définie dans `ForestryNavigation.kt` (sealed class `Screen`).

**Sous-graphe Forets** :
- `Screen.Forets` → `GroupsScreen` (sélection projet)
- `Screen.Parcelles` → `ParcellesScreen`
- `Screen.Placettes` → `PlacettesScreen`
- `Screen.PlacetteDetail` → `PlacetteDetailScreen`
- `Screen.EssenceDiam` → `EssenceDiamScreen`
- `Screen.Martelage` → `MartelageScreen`
- `Screen.Map` → `MapScreen`

**Sous-graphe Compteurs** :
- `Screen.GroupDetail` → `GroupScreen`
- `Screen.Formulas` → `FormulasScreen`
- `Screen.Calculator` → `CalculatorScreen`

**Sous-graphe IBP** :
- `Screen.IbpEvaluation` → `IbpEvaluationScreen`
- `Screen.IbpProjects` → `IbpProjectsScreen`
- `Screen.IbpDiagnostic` → `IbpDiagnosticScreen`
- `Screen.IbpCompare` → `IbpCompareScreen`
- `Screen.IbpHistory` → `IbpHistoryScreen`

**Sous-graphe Diagnostic** :
- `Screen.DiagnosticMenu` → `DiagnosticMenuScreen`
- `Screen.StationDiagnostic` → `StationDiagnosticScreen`
- `Screen.RipisylveDiagnostic` → `RipisylveDiagnosticScreen`
- `Screen.Diagnostic` → `DiagnosticScreen`

**Sous-graphe Settings/Packs** :
- `Screen.Settings` → `SettingsScreen`
- `Screen.PackManager` → `PackManagerScreen`
- `Screen.TarifDocumentation` → `TarifDocumentationScreen`
- `Screen.StandClassification` → `StandClassificationScreen`

---

## 6) Données métier (modèles principaux)

### 6.1 Compteurs
- `Group` — groupe de compteurs
- `Counter` — compteur individuel
- `Formula` — formule de calcul (exp4j)

### 6.2 Foresterie
- `Foret` — forêt/projet (proprietaireNom, proprietaireEmail, gestionnaireNom, psgNumero)
- `Parcelle` — parcelle (surface, shape, codeInsee, sectionCadastrale, numeroCadastral, geometrieIgnWkt)
- `Placette` — placette d'inventaire (centerWkt, referenceGpsWkt, samplingMode, sampleArea)
- `Essence` — essence forestière (95+ pré-configurées, code, nom, densité, qualité, croissance)
- `Tige` — tige mesurée (essenceCode, diamCm, hauteurM, qualite, gpsWkt, precisionM, photoUri)

### 6.3 Tarifs / Prix
- `PriceEntry` — prix par essence/produit/classe diam (€/m³)
- `ProductRule` — mapping essence+diam → produit (BO, BI, BCh, etc.)
- `DefaultProductPrices` — prix par défaut
- `ParameterDefaults` — paramètres par défaut

### 6.4 IBP
- `IbpAnswers` — 10 critères (E1/E2/GB/BMS/BMC/DMH/VS/CF/CO/HC), scoring 0/2/5, max 50
- `IbpEvaluation` — évaluation IBP avec contexte (parcelle, date, évaluateur, GPS)

### 6.5 Diagnostic
- `DiagnosticSylvicole` — diagnostic avec recommandations
- `StationDiagnostic` — diagnostic de station (floristique, gradient)
- `RipisylveDiagnostic` — diagnostic ripisylve

---

## 7) Calculs dendrométriques

Fichier clé : `domain/calculation/ForestryCalculator.kt`

- Agrégation par essence et classes de diamètre
- **Calculs** : N/ha, G (surface terrière), Dg (diamètre moyen), Dm, Hm, hauteur de Lorey, volumes
- **7 méthodes de cubage** : Schaeffer 1E/2E, Algan, IFN Rapide/Lent, FGH, coefficient de forme
- **Coûts/recettes** : €/m³ appliqué sur volume, avec alias essence + fallback wildcard
- **Tarifs** : `TarifCalculator.kt` (25+ alias essence) + `TarifData.kt`

**Problèmes connus** (voir audit vague 1) :
- Alias essence incohérents entre ForestryCalculator (4) et TarifCalculator (25+) → CHENE tombe à 50-80€/m³ au lieu de 165-315€/m³
- Prix appliqué au volume total, pas par produit (DecoupeCalculator existe mais inutilisé)
- Pas de conversion stère/m³
- Hdom absent (CRITICAL pour ONF)

---

## 8) GIS / Géomatique

### 8.1 Lambert-93 (EPSG:2154)

**2 implémentations** (à unifier — voir audit) :
- `domain/geo/Lambert93.kt` — formules Snyder USGS, **unidirectionnelle** (L93→WGS84 uniquement)
- `domain/location/Lambert93Converter.kt` — formules IGN NTG 71, **bidirectionnelle** ✅

**Conserver** : `Lambert93Converter.kt` (correct, 30 itérations, tolérance 1e-12)
**À supprimer** : `Lambert93.kt` (Phase 1.17)

### 8.2 GPS

`domain/location/GpsAverager.kt` :
- Moyennage MAD (Median Absolute Deviation, facteur 1.4826)
- Pondération inverse-variance (1/σ²)
- Rejet cold fix (première lecture)
- Profil unique : 6 lectures, max 20m, timeout 15s

### 8.3 WKT

`domain/location/WktUtils.kt` :
- Parse `POINT` et `POINT Z` uniquement
- Pas de SRID, pas de LINESTRING/POLYGON (à étendre — Phase 3.4)

### 8.4 DEM SRTM

`domain/geo/SrtmElevationService.kt` + `EmbeddedDemService.kt` :
- Format HGT NASA/CGIAR (Int16 big-endian, 1201×1201)
- Algorithme de Horn (1981) pour pente/exposition
- Asset pack `dem_pack` (install-time delivery)

### 8.5 Shapefile

`domain/geo/ShapefileParser.kt` :
- Auto-détection Lambert-93 par bbox (X: 100k-1.3M, Y: 6M-7.2M)
- Reprojection L93→WGS84 conditionnelle

### 8.6 Export QGIS

`domain/usecase/export/QgisExportHelper.kt` :
- GeoJSON (CRS84 = WGS84 lon/lat)
- CSV-XY (lon/lat + Lambert93 E/N)

---

## 9) Internationalisation (i18n)

- `app/src/main/res/values/strings.xml` — **1186 chaînes** (anglais/défaut)
- `app/src/main/res/values-fr/strings.xml` — **1164 chaînes** (français)
- **22 chaînes manquantes en français**
- **100+ chaînes françaises codées en dur** dans le code Kotlin (CRITICAL — voir audit i18n)
- **Pas de plurals.xml**
- `SimpleDateFormat(Locale.FRANCE)` forcé dans 5+ fichiers (CRITICAL)
- Symbole € codé en dur dans 7+ fichiers (CRITICAL)

**Règle** : `stringResource(R.string.xxx)` dans les Composables, `context.getString(R.string.xxx)` hors Composable.

---

## 10) Sécurité (état actuel — CRITICAL)

| Élément | Statut | Fichier |
|---|---|---|
| SQLCipher | **DÉSACTIVÉ** (commenté) | `build.gradle.kts:220`, `ForestryDatabase.kt:124-137` |
| Certificate pinning | **DÉSACTIVÉ** (commenté) | `SecureHttpClient.kt:47-56` |
| FLAG_SECURE | **Absent** | `MainActivity.kt` |
| DataStore chiffrement | **Absent** | `UserPreferences.kt` |
| Root detection | **Absent** | — |
| Auth biométrique | **Absente** | — |
| ProGuard logs | Partiel (v/d seulement) | `proguard-rules.pro:81-85` |

**Bonnes pratiques en place** :
- `allowBackup=false`, backup rules exclusions DB + préférences
- `network_security_config.xml` : cleartext bloqué, system CAs uniquement
- ProGuard/R8 activé en release (minify + shrink)
- Pas de WebView, pas de deep links
- Keystore externalisé via `keystore.properties`

---

## 11) RGPD (état actuel — NON CONFORME)

**26 champs de données personnelles** identifiés (noms, emails, GPS, cadastrales, photos).
**5 non-conformités critiques** :
1. Politique de confidentialité mensongère (`PRIVACY_POLICY.md:23`)
2. DB non chiffrée (Art. 32)
3. Absence de consentement (Art. 6, 7, 13)
4. Absence de registre des traitements (Art. 30)
5. Transferts hors UE Esri/USA non documentés (Art. 44)

Voir `docs/RGPD_AUDIT_REPORT.md` + `AUDIT_GLOBAL_GEOSYLVA.md` §6.

---

## 12) Tests

| Type | Commande | Couverture actuelle |
|---|---|---|
| Unitaires | `./gradlew testDebugUnitTest` | ~35% |
| Lint | `./gradlew lint` | — |
| Build debug | `./gradlew assembleDebug` | — |
| Build release | `./gradlew assembleRelease` | — |
| Instrumentés | `./gradlew connectedAndroidTest` | — |

**27 fichiers de test** dans `app/src/test/java/com/forestry/counter/`.

**Tests clés** :
- `ForestryCalculatorTest` — calculs dendrométriques
- `ExpertForestryCalculatorTest` — IS, surface terrière, tables ONF
- `Lambert93ConverterTest` — conversion Lambert-93 (tolérance 50m — à réduire)
- `WktUtilsTest` — parsing WKT
- `SecureHttpClientTest` — sécurité réseau (cassé — méthode inexistante)
- `DatabaseMigrationTest` — migrations Room
- `DataCorrelationEngineTest` — corrélation données
- `ConfidenceEngineTest` — moteur de confiance
- `TerritorialResolverTest` — résolution territoriale
- `FormulaParserTest` — parser de formules

---

## 13) Build

### 13.1 Commandes

```bash
# Build debug
./gradlew assembleDebug

# Build release (nécessite keystore.properties)
./gradlew assembleRelease

# Bundle Play Store (AAB)
./gradlew bundleRelease

# Tests unitaires
./gradlew testDebugUnitTest

# Lint
./gradlew lint
```

### 13.2 Configuration

- `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`
- `versionCode = 9`, `versionName = "2.3.0"`
- `BUILD_ID` injecté à chaque compilation (timestamp)
- Signing : `keystore.properties` externalisé, conditionnel
- ProGuard : `proguard-android-optimize.txt` + `proguard-rules.pro`
- Asset pack : `dem_pack` (tuiles DEM SRTM, install-time)
- **`kotlin.incremental=false`** dans `gradle.properties` — **À CORRIGER** (Phase 0.1)

### 13.3 CI/CD

- `.github/workflows/ci.yml` — lint + tests + build debug
- **Pas de release.yml** — à créer (Phase 1.16)

---

## 14) Problèmes connus (synthèse)

Voir les audits pour le détail complet :

| Audit | Domaines | Issues | Fichier |
|---|---|---|---|
| Vague 1 | DB, calculs, tarifs, foresterie, données | 101 (22 CRITICAL) | `AUDIT_FORESTIER_COMPLET.md` |
| Vague 2 | Sécurité, GIS, UI, i18n, build, RGPD, perf, misc | 123 (18 CRITICAL) | `AUDIT_GLOBAL_GEOSYLVA.md` |
| **Total** | | **224 (40 CRITICAL)** | |

**Top 10 des actions bloquantes** (Phase 0) : voir `MASTER_PLAN.md` §3.2

---

## 15) TODO (priorité actuelle)

**Phase 0 en cours** — voir `MASTER_PLAN.md` §3.2 pour le détail.

Priorité immédiate :
1. `kotlin.incremental=true` (0.1j)
2. SQLCipher + Keystore + migration (3j)
3. Certificate pinning (1j)
4. RGPD : politique + consentement + registre (4j)
5. `collectAsStateWithLifecycle()` (2j)
6. Coil + downsampling images (2j)

---

*Document mis à jour le 2026-06-29. Source de vérité technique pour GeoSylva v2.3.0.*
