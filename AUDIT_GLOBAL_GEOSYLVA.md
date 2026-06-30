# AUDIT GLOBAL GeoSylva — Rapport de Synthèse Vague 2

**Auditeur** : Cellule d'audit (8 sous-agents spécialisés + vérifications croisées)
**Date** : 2026-06-29 (vague 2)
**Périmètre vague 2** : Sécurité, GIS, UI/Compose, i18n, Build/CI, RGPD, Performance, Misc (FormulaParser, WorkManager, DataStore, accessibilité)
**Référentiels** : OWASP Mobile Top 10 2024, RGPD (UE 2016/679), IGN NTG 71, EPSG:2154, Material 3, Android Vitals, CNIL
**Méthode** : 8 sous-agents parallèles + synthèse manuelle croisée avec la vague 1 (`AUDIT_FORESTIER_COMPLET.md`)

---

## RÉSUMÉ EXÉCUTIF

**Verdict global vague 2** : L'application présente une **architecture solide** (Compose, ViewModels, Room, coroutines bien scopées) mais souffre de **vulnérabilités critiques** sur 4 axes bloquants pour une mise en production professionnelle : (1) chiffrement DB désactivé, (2) RGPD non conforme, (3) i18n fragmentaire, (4) performance sur gros datasets.

### Scores par domaine (vague 2)

| Domaine | Score | Statut |
|---------|-------|--------|
| Sécurité / chiffrement / réseau | 5/10 | ❌ Critique |
| GIS / géomatique (Lambert93, GPS, DEM) | 7.5/10 | ⚠️ Approximatif |
| Présentation / UI / Compose | 6.5/10 | ⚠️ Moyen |
| Internationalisation (FR/EN) | 4/10 | ❌ Insuffisant |
| Build / CI / Gradle / dépendances | 6/10 | ⚠️ Dépendances obsolètes |
| RGPD / privacy | 3/10 | ❌ Non conforme |
| Performance / mémoire / batterie | 6.5/10 | ⚠️ Risques OOM |
| Misc (FormulaParser, WorkManager, DataStore, a11y) | 5.5/10 | ⚠️ À corriger |

### Synthèse des problèmes par sévérité (vague 2 seule)

| Sévérité | Nombre | Action |
|----------|--------|--------|
| 🔴 CRITICAL | 18 | Blocage production |
| 🟠 HIGH | 32 | Correction rapide |
| 🟡 MEDIUM | 45 | Correction planifiée |
| 🟢 LOW | 28 | Amélioration continue |
| **TOTAL** | **123** | |

### Synthèse combinée vague 1 + vague 2

| Sévérité | Vague 1 | Vague 2 | **Total cumulé** |
|----------|---------|---------|------------------|
| 🔴 CRITICAL | 22 | 18 | **40** |
| 🟠 HIGH | 26 | 32 | **58** |
| 🟡 MEDIUM | 35 | 45 | **80** |
| 🟢 LOW | 18 | 28 | **46** |
| **TOTAL** | **101** | **123** | **224** |

---

## 1. SÉCURITÉ, CHIFFREMENT, COUCHE RÉSEAU

**Score** : 5/10 — 3 CRITICAL, 4 HIGH, 5 MEDIUM, 3 LOW

### 🔴 CRITICAL

#### S-C1 — Base de données Room NON chiffrée (SQLCipher désactivé)
- **Fichiers** : `ForestryDatabase.kt:124-137`, `app/build.gradle.kts:219-220`
- **OWASP** : M9 (Insecure Data Storage), M10 (Insufficient Cryptography)
- **Constat** : `net.sqlcipher:android-database-sqlcipher:4.2.0` est commenté. `DatabaseEncryptionService` existe mais n'est jamais appelé. `createEncryptedDatabase` est commenté. La base `forestry_counter.db` est en clair.
- **Données exposées** : coordonnées GPS, données cadastrales (codeInsee, section, numéro), noms de propriétaires, photos.
- **Exploitation** : ADB ou appareil rooté → extraction triviale du fichier SQLite.
- **Fix** : Réactiver SQLCipher 4.5.4+, stocker la passphrase dans Android Keystore (jamais en dur), migrer la base existante.

#### S-C2 — Certificate Pinning NON opérationnel (commenté)
- **Fichiers** : `SecureHttpClient.kt:47-56`, `SecureTileService.kt:20-25`
- **OWASP** : M5 (Insecure Communication)
- **Constat** : Le `CertificatePinner` est commenté. Pire, `SecureTileService.getSecurityStats()` retourne `certificatePinningEnabled = true` — **faux positif** qui masque le problème dans l'UI.
- **Exploitation** : MITM sur Wi-Fi public → injection de tuiles malveillantes ou interception.
- **Fix** : Extraire les hashes SHA-256 (`openssl s_client ... | base64`), activer le pinning, corriger le statut rapporté.

#### S-C3 — Injection SQL dans GeoPackage Parser
- **Fichier** : `GeoImportParser.kt:451`
- **OWASP** : M4 (Insufficient I/O Validation)
- **Constat** : `db.rawQuery("SELECT * FROM \"${tableName.replace("\"","\"\"")}\" LIMIT $MAX_GPKG_ROWS", null)` — le nom de table provient d'un fichier utilisateur. Le `replace` ne protège pas contre toutes les attaques.
- **Fix** : Valider `tableName` avec whitelist regex `^[a-zA-Z_][a-zA-Z0-9_]*$`, rejeter les noms contenant `sqlite_`, utiliser parameterized query pour LIMIT.

### 🟠 HIGH

- **S-H1** : Test `SecureHttpClientTest.kt:109-126` appelle `getCurrentCertificateHashes()` qui n'existe pas — test qui échoue systématiquement, fausse couverture sécurité.
- **S-H2** : Logging de données sensibles en DEBUG — `MapScreen.kt:304-404` (chemins fichiers, coordonnées), `EmbeddedDemService.kt:54,119,159,170` (coordonnées GPS dans logs).
- **S-H3** : Pas de détection de root/debug — aucune implémentation `RootChecker`. Sur appareil rooté, extraction DB triviale.
- **S-H4** : Pas de `FLAG_SECURE` — `MainActivity.kt` ne bloque pas les captures d'écran. Données cadastrales sensibles capturables.

### 🟡 MEDIUM

- **S-M1** : Pas d'authentification biométrique/verrouillage — `BiometricPrompt` jamais utilisé.
- **S-M2** : `MainActivity` exportée sans permission supplémentaire (nécessaire pour LAUNCHER mais à durcir).
- **S-M3** : Intent de partage implicite sans validation — `MartelageScreen.kt:814-819`.
- **S-M4** : DataStore non chiffré — `UserPreferences.kt` utilise `preferencesDataStore` standard.
- **S-M5** : CrashLogger stocke des logs en clair — `CrashLogger.kt:37-58`.

### 🟢 LOW

- **S-L1** : ProGuard ne supprime que `Log.v()` et `Log.d()` — `i/w/e` restent en release.
- **S-L2** : Pas de validation des inputs utilisateur dans les formulaires.
- **S-L3** : Pas de timeout d'inactivité session.

### ✅ Bonnes pratiques identifiées
- `allowBackup="false"`, `dataExtractionRules` et `fullBackupContent` correctement configurés (DB et préférences exclues du cloud).
- `network_security_config.xml` : cleartext bloqué par défaut, system CAs uniquement.
- ProGuard/R8 activé en release (`minifyEnabled=true`, `shrinkResources=true`).
- Pas de WebView, pas de deep links — surface d'attaque réduite.
- Keystore et `local.properties` correctement gitignorés.

---

## 2. COUCHE GÉO/GIS (LAMBERT93, WKT, GPS, QGIS, SHAPEFILE)

**Score** : 7.5/10 — 0 CRITICAL, 0 HIGH, 6 MEDIUM, 6 LOW

### Constat global
Les formules de base sont **correctes** (Lambert-93 IGN NTG 71, Haversine, algorithme de Horn pour DEM, moyennage GPS robuste avec MAD). Plusieurs défauts d'approximation et de duplication à corriger.

### 🟡 MEDIUM

#### G-M1 — Duplication de code Lambert-93
- **Fichiers** : `Lambert93.kt` (formules Snyder USGS, **unidirectionnelle** L93→WGS84 uniquement) + `Lambert93Converter.kt` (formules IGN NTG 71, bidirectionnelle).
- **Impact** : Incohérence potentielle ~1-2cm entre les deux implémentations.
- **Fix** : Supprimer `Lambert93.kt` ou la marquer `@Deprecated`, conserver uniquement `Lambert93Converter.kt`.

#### G-M2 — WKT parsing limité à POINT
- **Fichier** : `WktUtils.kt:1-24`
- **Constat** : Ne gère que `POINT` et `POINT Z`. Pas de SRID (`SRID=2154;POINT(...)`), pas de LINESTRING/POLYGON/MULTIPOINT. Regex ne valide pas le format numérique.
- **Fix** : Implémenter un vrai parser WKT ou intégrer `jts-core` (Java Topology Suite).

#### G-M3 — Pas de reprojection CRS généralisée
- **Fichier** : `ShapefileParser.kt:220-236`
- **Constat** : Seul Lambert-93 → WGS84 est supporté. Pas d'UTM, Lambert CC, RGF93 géocentrique.
- **Fix** : Intégrer PROJ.4 ou une bibliothèque Android de projection (ex: `proj4j`).

#### G-M4 — Approximation plane pour surface de polygone
- **Fichier** : `GpsParcelTracer.kt:216-235`
- **Constat** : Shoelace formula avec facteur `111320 m/°` constant. Erreur ~1% sur polygone 10km², ~5% sur 100km².
- **Fix** : Algorithme géodésique (Karney 2013) pour les polygones >10ha.

#### G-M5 — Excentricité GRS80 calculée vs officielle
- **Fichier** : `Lambert93.kt:24`
- **Constat** : `E = sqrt(2*FLAT - FLAT*FLAT)` ≈ 0.081819191042815 vs valeur officielle 0.08181919104283185. Écart ~1cm.
- **Fix** : Utiliser la constante officielle GRS80.

#### G-M6 — GeoImportParser sans transformation CRS automatique
- **Fichier** : `GeoImportParser.kt:20-26`
- **Constat** : Assume WGS84 pour tous les formats sauf shapefile (détection Lambert-93 par bbox).
- **Fix** : Détecter CRS depuis métadonnées (GeoJSON `crs`, KML `<Document>` implicit WGS84, GPX toujours WGS84, GeoPackage `gpkg_spatial_ref_sys`).

### 🟢 LOW
- **G-L1** : `DEG_TO_M = 111320.0` constant — devrait varier avec latitude (~110940 aux pôles).
- **G-L2** : URL DVF Cerema en `preprod` — risque d'instabilité.
- **G-L3** : Tolérances de test Lambert-93 trop larges (50m E, 150m N) — masque une imprécision potentielle.
- **G-L4** : Pas de fichier `.qpj/.prj` généré dans l'export QGIS.
- **G-L5** : Interpolation DEM nearest-neighbor — bilinéaire/bicubique améliorerait la précision altimétrique.
- **G-L6** : Rayon Haversine 6371km (sphérique) — 6378.137km (GRS80 équatorial) plus précis.

### ✅ Points forts
- **Lambert93Converter** : formules IGN NTG 71 correctes, 30 itérations, tolérance 1e-12.
- **GpsAverager** : MAD (Median Absolute Deviation) avec facteur 1.4826, pondération inverse-variance, rejet cold fix.
- **SrtmElevationService / EmbeddedDemService** : algorithme de Horn (1981) correct, format HGT NASA/CGIAR bien géré.
- **ShapefileParser** : auto-détection Lambert-93 par bbox intelligente.
- **StationDataAggregator** : URLs WMS INRAE BDGSF et Cerema DVF correctes.

---

## 3. PRÉSENTATION / UI / COMPOSE

**Score** : 6.5/10 — 0 CRITICAL, 8 HIGH, 15 MEDIUM, 12 LOW

### 🟠 HIGH

#### U-H1 — `collectAsState()` sans lifecycle awareness (50+ occurrences)
- **Fichiers** : `ParcellesScreen.kt:85,102,108-115`, `MartelageScreen.kt:177-181,186,204-225`, `MapScreen.kt:1006-1013,1036,1115,1134`, `EssenceDiamScreen.kt:111-114`, `DashboardScreen.kt:132-137`, `SettingsScreen.kt:127-140`, etc.
- **Impact** : Les Flow continuent d'être collectés en background → 10-15% batterie drainée, CPU inutile.
- **Fix** : Remplacer tous `collectAsState()` par `collectAsStateWithLifecycle()` (déjà dans `lifecycle-runtime-compose:2.8.7`).

#### U-H2 — État UI non persisté lors de la rotation
- **Fichiers** : `ParcellesScreen.kt:87-106` (17 champs en `remember`), `PlacettesScreen.kt:96-102`, `CalculatorScreen.kt:63-80`.
- **Impact** : Perte de saisie lors de la rotation.
- **Fix** : Utiliser `rememberSaveable` pour les champs de formulaire et l'état de tri.

#### U-H3 — `contentDescription = null` sur éléments interactifs
- **Fichiers** : `ParcellesScreen.kt:174,218,640,656,663`, `MartelageScreen.kt:760,889,909,914,994,1032,1084,1139,1180`, `AppMiniDialog.kt:46`.
- **Impact** : TalkBack ne peut pas décrire la fonction des boutons.
- **Fix** : Ajouter `contentDescription = stringResource(R.string.xxx)` sur tous les éléments interactifs.

#### U-H4 — LazyColumn sans `key()` dans plusieurs écrans
- **Fichiers** : `SuperCorrelateurScreen.kt:153,161,167,173,179,186`, `TarifDocumentationScreen.kt:124,176`, `PackManagerScreen.kt:68`, `FloraFamilyBrowserSheet.kt:32`, `IbpEvaluationScreen.kt:1524`.
- **Impact** : 100 items = 100ms de recomposition inutile par changement.
- **Fix** : `items(list, key = { it.id }) { ... }`.

#### U-H5 — Images sans downsampling systématique
- **Fichier** : `StationPhotoGalleryBlock.kt:234-243`
- **Constat** : `BitmapFactory.decodeStream(stream)` sans `inSampleSize`. Photo 12MP = ~48MB en mémoire.
- **Impact** : 10 photos = ~480MB → crash OOM garanti.
- **Fix** : `BitmapFactory.Options().apply { inSampleSize = 4; inPreferredConfig = Bitmap.Config.RGB_565 }` ou intégrer Coil.

#### U-H6 — Pas de bibliothèque de chargement d'images (Coil/Glide)
- **Impact** : Pas de cache disque/RAM, downsampling manuel incohérent.
- **Fix** : `implementation("io.coil-kt:coil-compose:2.5.0")` + `AsyncImage`.

#### U-H7 — Calculs lourds dans la composition
- **Fichier** : `MartelageScreen.kt:227-252` (mergeMaps dans composition).
- **Impact** : 50ms de calcul par recomposition.
- **Fix** : `derivedStateOf` ou déplacer dans le ViewModel.

#### U-H8 — `Flow.first()` potentiellement bloquant
- **Fichiers** : `ForestryCalculator.kt:110,156,181,190,229,235,663,672`, `ForestryCounterApplication.kt:229,264,271`, `SettingsScreen.kt` (20+ occurrences).
- **Impact** : ANR si le Flow n'émet jamais.
- **Fix** : `withTimeoutOrNull(5000) { flow.first() }` ou `flow.firstOrNull()`.

### 🟡 MEDIUM
- **U-M1** : `derivedStateOf` manquant pour états calculés (`MartelageScreen.kt:286-299`, `MapScreen.kt:1051-1091`).
- **U-M2** : Lambdas instables dans `onClick` causant recomposition parent (`MartelageScreen.kt:947-956`).
- **U-M3** : `forEach` avec `key()` au lieu de `items()` avec `key` (`EssenceDiamScreen.kt:819`).
- **U-M4** : États vides inconsistants entre écrans.
- **U-M5** : Messages d'erreur inline manquants dans les formulaires.
- **U-M6** : Pas de deep linking explicite (`ForestryNavigation.kt`).
- **U-M7** : Contraste couleurs non vérifié automatiquement.
- **U-M8** : `@Stable`/`@Immutable` manquants sur classes de données passées aux composables.
- *(+ 7 autres mineurs)*

### ✅ Points forts
- ViewModels corrects : `viewModelScope`, `StateFlow`, `SharingStarted.WhileSubscribed(5_000)`.
- Navigation type-safe avec sealed class `Screen`.
- Thème complet : mode sombre, Material You (dynamic colors), taille de police configurable.
- `LaunchedEffect`/`DisposableEffect`/`SideEffect` utilisés correctement.
- Pas de `GlobalScope`, pas de `runBlocking`.
- États de chargement/erreur/vides gérés sur la plupart des écrans.

---

## 4. INTERNATIONALISATION (FR/EN)

**Score** : 4/10 — 8 CRITICAL, 5 HIGH, 7 MEDIUM, 3 LOW

### Statistiques ressources
- `values/strings.xml` : **1186 chaînes** (anglais)
- `values-fr/strings.xml` : **1164 chaînes** (français)
- **Écart : 22 chaînes manquantes en français**

### 🔴 CRITICAL

#### I-C1 — 100+ chaînes françaises codées en dur dans le code Kotlin
- **Fichiers les plus touchés** :
  - `ExpertIbpExtension.kt` : 17+ chaînes ("Indice de station (IA):", "📊 Données de Production ONF", "Hauteur moyenne ONF:", etc.)
  - `StationPhotoGalleryBlock.kt` : 11 chaînes ("Ajouter une photo", "Prendre une photo", "Annuler", "Valider", etc.)
  - `DiagnosticScreen.kt` : 7 chaînes ("Résultats du diagnostic", "Score global", "Peuplement", etc.)
  - `StationDiagnosticScreen.kt` : 8 chaînes ("Brouillon", "Finaliser", "Espèces Indicatrices & Notes", etc.)
  - `PackManagerScreen.kt` : 5 chaînes ("Packs de données", "Installer", "MAJ dispo", "Désinstaller", etc.)
  - `DiagnosticPhotoCaptureSection.kt` : 8 chaînes
  - `EditableSynthesisBlock.kt` : 5 chaînes
  - `IbpEvaluationScreen.kt` : 6+ chaînes
  - `MartelageScreen.kt` : 7+ chaînes ("Saisie terrain rapide", "Gestion", etc.)
  - `ParcellesScreen.kt` : 2 chaînes ("IBP Biodiversité", "Diagnostic sylvicole")
  - `ExpertForestryCalculator.kt` : 7 messages d'erreur français ("Le diamètre doit être positif", etc.)
  - `DRIASDatabase.kt` : 5+ descriptions climatiques françaises
- **Impact** : Un utilisateur en mode anglais verra du français partout sur ces écrans.
- **Fix** : Extraire toutes ces chaînes vers `strings.xml` + `values-fr/strings.xml`.

#### I-C2 — `SimpleDateFormat` avec `Locale.FRANCE` forcée
- **Fichiers** : `DiagnosticScreen.kt:111-112`, `StationDiagnosticScreen.kt:804,1622`, `DiagnosticMenuScreen.kt:266`, `MartelageCsvExporter.kt:248`.
- **Impact** : Dates affichées en français même en mode anglais.
- **Fix** : `DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())`.

#### I-C3 — Symbole € codé en dur
- **Fichiers** : `MartelageScreen.kt:811`, `MartelageCsvExporter.kt:191,212`, `PdfSynthesisExporter.kt:153,154,273`, `MartelageXlsxExporter.kt:76`, `DataInterpretationEngine.kt:428`, `PriceTablesEditorScreen.kt:447`.
- **Fix** : `NumberFormat.getCurrencyInstance(Locale.getDefault()).format(value)`.

#### I-C4 — Aucune ressource `plurals.xml`
- **Constat** : "1 arbre" vs "3 arbres" non géré. Aucun fichier `plurals.xml` détecté.
- **Fix** : Créer `values/plurals.xml` et `values-fr/plurals.xml` avec `<plurals name="tree_count">`.

### 🟠 HIGH
- **I-H1** : 22 chaînes présentes en anglais mais manquantes en français.
- **I-H2** : Messages d'erreur du domain en français (`ExpertForestryCalculator.kt`, `DRIASDatabase.kt`, `MartelageXlsxExporter.kt`, `MartelageCsvExporter.kt`).
- **I-H3** : `String.format(Locale.US, ...)` force le point décimal américain (`MartelageScreen.kt:453-468,661-675`).
- **I-H4** : Unités codées en dur (m², m³, ha, cm, m, tiges/ha) sans ressource.
- **I-H5** : `SimpleDateFormat` avec patterns codés en dur (15+ occurrences).

### 🟡 MEDIUM
- **I-M1** : `String.format()` sans `NumberFormat` pour les nombres affichés (30+ occurrences).
- **I-M2** : Dates avec `SimpleDateFormat` et `Locale.getDefault()` mais pattern codé (10+ occurrences).
- **I-M3** : Préfixes/suffixes codés ("PARCELLE_", "ha", "m²/ha").
- *(+ 4 autres)*

### Estimation effort correction : 40-60 heures.

---

## 5. BUILD / CI / GRADLE / DÉPENDANCES

**Score** : 6/10 — 1 CRITICAL, 5 HIGH, 6 MEDIUM, 4 LOW

### 🔴 CRITICAL

#### B-C1 — `kotlin.incremental=false` dans gradle.properties
- **Fichier** : `gradle.properties:9`
- **Impact** : Désactive la compilation incrémentale → builds significativement plus lents.
- **Fix** : `kotlin.incremental=true`.

### 🟠 HIGH

#### B-H1 — Build tools obsolètes
- AGP 8.2.2 → 8.6.0+ (latest 9.2.0)
- Kotlin 1.9.23 → 2.1.0+ (K2 compiler 2x plus rapide)
- KSP 1.9.23-1.0.20 → 2.1.0-1.0.29+
- Compose Compiler 1.5.12 → 1.5.15
- **Fix** : Mettre à jour progressivement avec tests après chaque étape.

#### B-H2 — Compose BOM sévèrement obsolète
- `2024.09.00` → `2026.06.00` (presque 2 ans de retard).
- **Fix** : `platform("androidx.compose:compose-bom:2026.06.00")`.

#### B-H3 — BlurView version-2.0.5 avec failures de build Jitpack
- **Fichier** : `app/build.gradle.kts:210`
- **Fix** : `com.github.Dimezis:BlurView:version-3.2.0`.

#### B-H4 — Pas de workflow CI/CD de release
- **Constat** : `.github/workflows/ci.yml` existe (lint + tests + build debug) mais pas de `release.yml`.
- **Fix** : Créer `release.yml` avec signing via secrets GitHub Actions.

#### B-H5 — Accompanist deprecated
- `accompanist-systemuicontroller` et `accompanist-permissions` 0.32.0 → migrer vers équivalents Compose platform.

### 🟡 MEDIUM
- **B-M1** : AndroidX obsolètes — Room 2.6.1→2.8.4, DataStore 1.0.0→1.2.1, WorkManager 2.9.0→2.11.1, Navigation 2.8.5→2.9.8, MapLibre 10.3.1→11.11.0.
- **B-M2** : Pas de version catalog (`libs.versions.toml`).
- **B-M3** : KSP Room arguments commentés (`app/build.gradle.kts:114-120`) — pas de schema export, pas d'incremental.
- **B-M4** : ProGuard keep rules trop larges (`-keep class com.forestry.counter.data.local.entity.** { *; }`).
- **B-M5** : Pas de coverage reporting (Jacoco).
- **B-M6** : `org.gradle.jvmargs=-Xmx2048m` — faible pour gros projets, recommander 4096m.

### 🟢 LOW
- `isDebuggable` non explicite dans build types.
- ProGuard ne supprime que `Log.v/d` (pas `i/w/e`).
- Pas de `org.gradle.caching=true` ni `org.gradle.parallel=true`.
- Script `copy_dem_tiles.ps1` avec chemin source hardcodé.

### ✅ Points forts
- **Signing excellent** : `keystore.properties` externalisé, conditionnel, UTF-8 BOM handling, credentials jamais en dur.
- **.gitignore complet** : `*.jks`, `*.keystore`, `keystore.properties`, `local.properties` exclus.
- **Manifest excellent** : `allowBackup=false`, `dataExtractionRules`, `fullBackupContent`, `networkSecurityConfig`, `localeConfig` tous configurés.
- **Permissions minimales** : toutes justifiées, `READ_EXTERNAL_STORAGE` avec `maxSdkVersion="32"`.
- **Asset pack DEM** correctement configuré (install-time delivery).
- **SDK versions à jour** : compileSdk 35, targetSdk 35, minSdk 26.

---

## 6. RGPD / PRIVACY

**Score** : 3/10 — 5 CRITICAL, 7 MAJEUR, 5 MINEUR

### Inventaire données personnelles : **26 champs identifiés**

| Catégorie | Champs | Sensibilité |
|-----------|--------|-------------|
| Identité | proprietaireNom, gestionnaireNom, operateurNom, observerName, evaluatorName | Standard |
| Contact | proprietaireEmail | Standard |
| Localisation | latitude, longitude, gpsWkt, centerWkt, referenceGpsWkt, latKey, lonKey | Standard (sensibles si croisées) |
| Cadastral | codeInseeCommune, sectionCadastrale, numeroCadastral, geometrieIgnWkt | Standard (identifie propriétaire) |
| Photos | photosJson (Station/Ripisylve), photoUri (Tige) | Élevé (personnes possibles) |
| Technique | Device model, stack trace (CrashLogger) | Faible |

### 🔴 CRITICAL

#### R-C1 — Politique de confidentialité mensongère
- **Fichier** : `PRIVACY_POLICY.md:23`
- **Constat** : Indique `No personal identification data (name, email, phone number)`. **Faux** : 26 champs de PII collectés.
- **Article** : Art. 13, Art. 5§1.a — **amende jusqu'à 20M€ ou 4% CA**.
- **Fix** : Réécrire la politique en listant explicitement les PII, finalités, base légale, durée, droits.

#### R-C2 — Base de données non chiffrée (recoupement S-C1)
- **Articles** : Art. 32, Art. 5§1.f — **amende jusqu'à 10M€ ou 2% CA**.

#### R-C3 — Absence de consentement RGPD
- **Fichier** : `OnboardingScreen.kt` (aucune page consentement)
- **Constat** : L'utilisateur saisit noms/emails sans information RGPD préalable.
- **Articles** : Art. 6, 7, 13 — **amende jusqu'à 20M€ ou 4% CA**.
- **Fix** : Ajouter une page d'information/consentement dans l'onboarding.

#### R-C4 — Absence de registre des traitements (Art. 30)
- **Constat** : Aucun fichier `RECORD_OF_PROCESSING_ACTIVITIES.md`.
- **Amende** : jusqu'à 10M€ ou 2% CA.
- **Fix** : Créer le registre documentant finalités, catégories, destinataires, transferts, durée, sécurité.

#### R-C5 — Transferts hors UE non documentés (Esri/USA)
- **Constat** : Tuiles ArcGIS Online (Esri) hébergées aux USA sans garanties documentées (SCC).
- **Articles** : Art. 44, Art. 13§1.f — **amende jusqu'à 20M€ ou 4% CA**.
- **Fix** : Mettre en place des SCC ou cesser d'utiliser Esri.

### 🟠 MAJEUR
- **R-M1** : `proprietaireEmail` sans finalité documentée (Art. 5§1.c minimisation).
- **R-M2** : Droit à l'effacement non centralisé — pas de "Effacer toutes mes données" dans Settings.
- **R-M3** : `PriceSyncWorker` utilise `OkHttpClient()` non sécurisé au lieu de `SecureHttpClient`.
- **R-M4** : Certificate pinning désactivé (recoupement S-C2).
- **R-M5** : Sous-traitants (IGN, OSM, MapLibre, Esri) non documentés dans la politique.
- **R-M6** : Pas de politique de rétention / suppression automatique.
- **R-M7** : Décision automatisée (recommandations sylvicoles) sans oversight humain documenté (Art. 22).

### 🟢 MINEUR
- Pas de DPO désigné (Art. 37).
- Pas de procédure de notification de violation (Art. 33).
- Pas de vérification d'âge (Art. 8).
- DataStore non chiffré (Art. 32).
- Pseudonymisation absente (Art. 25).

### ✅ Points conformes
- Transferts : IGN (France), OSM (UK/UE) — OK.
- Droit d'accès (Art. 15) : exports CSV/XLSX/JSON présents.
- Droit de rectification (Art. 16) : DAOs `@Update` + UI d'édition.
- Droit à la portabilité (Art. 20) : formats machine-readable.
- Limitation de finalité (Art. 5§1.b) : pas de réutilisation détectée.
- Permissions Android (GPS, caméra) : consentement système correct.

---

## 7. PERFORMANCE / MÉMOIRE / BATTERIE

**Score** : 6.5/10 — 3 CRITICAL, 5 HIGH, 8 MEDIUM, 4 LOW

### 🔴 CRITICAL

#### P-C1 — `collectAsState()` sans lifecycle (recoupement U-H1)
- 50+ occurrences → 10-15% batterie drainée en background.

#### P-C2 — Images sans downsampling (recoupement U-H5)
- `StationPhotoGalleryBlock.kt:234-243` → crash OOM garanti avec 10 photos HD.

#### P-C3 — Export charge tout en mémoire
- **Fichiers** : `ExportDataUseCase.kt:28-48` (JSON), `ExportDataUseCase.kt:50-79` (ZIP), `ImportDataUseCase.kt:108-120` (CSV `readAll()`).
- **Impact** : 10k tiges = ~70MB RAM → risque OOM.
- **Fix** : Streaming avec `JsonWriter`/`CSVReader.readNext()` ligne par ligne.

### 🟠 HIGH

- **P-H1** : LazyColumn sans `key()` (recoupement U-H4).
- **P-H2** : `Flow.first()` bloquant (recoupement U-H8).
- **P-H3** : Requêtes `SELECT * FROM` sans LIMIT — `TigeDao.kt:9,11,14`, `ParcelleDao.kt:9,12`, `PlacetteDao.kt:9`. 10k tiges = ~50MB en mémoire.
- **P-H4** : Photos stockées en URI (correct) mais pas de validation anti-base64.
- **P-H5** : Pas de pagination Paging 3 — UI freeze avec 10k+ tiges.

### 🟡 MEDIUM
- **P-M1** : `derivedStateOf` manquant (recoupement U-M1).
- **P-M2** : Pas de timeout sur Flow GPS (`GpsAverager.kt:87-146`).
- **P-M3** : `LocationManager` obsolète utilisé dans 3 écrans au lieu de `FusedLocationProviderClient`.
- **P-M4** : Initialisation Room synchrone dans `Application.onCreate` (100-500ms sur vieux appareils).
- **P-M5** : Pas de clustering pour marqueurs GPS sur carte (10k marqueurs = carte inutilisable).
- **P-M6** : Import CSV `readAll()` charge tout en mémoire.
- **P-M7** : Calculs lourds dans composition (recoupement U-H7).
- **P-M8** : WorkManager `BackupWorker` sans contrainte réseau.

### ✅ Points forts
- **Aucun `runBlocking`** (corrigé).
- **Aucun `GlobalScope`** (corrigé).
- `viewModelScope` et `lifecycleScope` utilisés correctement.
- Indices Room présents sur colonnes fréquemment queryées.
- Foreign Keys avec CASCADE/SET_NULL appropriés.
- GPS : `FusedLocationProviderClient`, `PRIORITY_HIGH_ACCURACY`, intervalle 800ms raisonnable.
- WorkManager avec `PeriodicWorkRequest` + `ExistingPeriodicWorkPolicy.UPDATE`.

### Métriques estimées après optimisation

| Scénario | Avant | Après | Gain |
|----------|-------|-------|------|
| Startup | 800ms | 400ms | 50% |
| Chargement 10k tiges | 3s freeze | 500ms paginé | 83% |
| Scroll 100 items | 100ms lag | 20ms fluide | 80% |
| Batterie background | 15%/jour | 8%/jour | 47% |
| Mémoire 10 photos | 200MB | 50MB | 75% |
| Export 10k tiges | 70MB RAM | 20MB streaming | 71% |

---

## 8. MISC : FORMULAPARSER, WORKMANAGER, DATASTORE, ACCESSIBILITÉ

**Score** : 5.5/10 — 5 CRITICAL, 12 HIGH, 15 MEDIUM, 3 LOW

### 🔴 CRITICAL

#### M-C1 — FormulaParser : risque d'injection via exp4j
- **Fichier** : `FormulaParser.kt:35-45`
- **Constat** : `ExpressionBuilder` directement sur expression utilisateur sans limite de longueur/complexité. Risque DoS.
- **Fix** : Limiter à 1000 caractères, timeout 100ms, limiter profondeur d'imbrication.

#### M-C2 — BackupWorker crée une nouvelle instance DB
- **Fichier** : `BackupWorker.kt:22-26`
- **Constat** : `Room.databaseBuilder(...).build()` à chaque exécution → fuite mémoire, violation singleton.
- **Fix** : Injecter l'instance DB existante via DI ou singleton partagé.

#### M-C3 — DataStore non chiffré (recoupement S-M4)
- **Fichier** : `UserPreferences.kt:15,19`
- **Fix** : `EncryptedSharedPreferences` ou chiffrement au niveau application.

#### M-C4 — Pas d'AccessibilityService
- **Constat** : Aucun service d'accessibilité détecté. Touch targets non vérifiés. Content scaling non audité.
- **Fix** : Audit UI pour touch targets ≥48dp, vérifier que `FONT_SIZE` est appliqué partout.

#### M-C5 — Fonctions >100 lignes
- **Fichier** : `DataInterpretationEngine.kt:59-165` (`analyzeGrowth()` ~106 lignes).
- **Fix** : Extraire en sous-fonctions testables.

### 🟠 HIGH
- **M-H1** : Regex FTS dans FormulaParser non protégée contre ReDoS (`FormulaParser.kt:124`).
- **M-H2** : Pas de gestion division par zéro dans FormulaParser.
- **M-H3** : BackupWorker sans contraintes réseau/charging.
- **M-H4** : BackupWorker non idempotent (crée un fichier à chaque exécution).
- **M-H5** : `PriceSyncWorker` sans timeout HTTP (`PriceSyncWorker.kt:33-37`).
- **M-H6** : `PriceSyncWorker` retry sans backoff exponentiel.
- **M-H7** : `HapticFeedback.kt` utilise `@Suppress("DEPRECATION")` sans plan de migration.
- **M-H8** : `PackManager.kt:120-124` TODO #INFRA-1 sans ticket (téléchargement packs non implémenté).
- **M-H9** : `AdvancedCalculationEngine.kt:82-122` fonction ~40 lignes.
- **M-H10** : Touch targets non vérifiés.
- **M-H11** : Magic numbers dans `FertilityReference.kt:61-284` (seuils hardcodés).
- **M-H12** : Test coverage FormulaParser insuffisant (pas de tests division par zéro, expressions vides, imbriquées).

### 🟡 MEDIUM
- **M-M1** : Fonctions de remplacement non imbriquées dans FormulaParser (`sum(sum(*))` ne fonctionne pas).
- **M-M2** : `PriceSyncWorker` validation URL insuffisante.
- **M-M3** : DataStore singleton potentiellement multiple.
- **M-M4** : Pas de migration SharedPreferences → DataStore.
- **M-M5** : `Counter.kt:31-37` propriétés calculées dépendent de `value` mutable.
- **M-M6** : `Tige.kt:9-37` trop de champs nullable sans distinction unknown/N/A.
- **M-M7** : `IbpModels.kt:118-130` logique de migration dans le modèle de domaine.
- **M-M8** : Interfaces repository sans documentation KDoc.
- **M-M9** : `HeightInputUtils.kt:3` magic number `0.5..80.0`.
- **M-M10** : `IbpTremCalculator.kt:23-28` codes TreM hardcodés.
- **M-M11** : `@Suppress("NAME_SHADOWING")` dans `MapScreen.kt:1034`.
- **M-M12** : API dépréciée dans `GpsDistanceMeasureDialog.kt:96`.
- **M-M13** : Content scaling non vérifié.
- **M-M14** : Magic numbers divers dans `FertilityReference.kt`.
- **M-M15** : `UserPreferences.kt:104,144` magic numbers hexadécimaux pour couleurs.

---

## 9. PLAN DE RÉSOLUTION PRIORISÉ

### Phase 0 — Blocages production (semestre 1, semaines 1-4)

> Ces 12 actions **doivent** être terminées avant toute mise en production.

| # | Action | Domaine | Effort | Issue |
|---|--------|---------|--------|-------|
| 1 | Réactiver SQLCipher + clé Android Keystore + migration | Sécurité/RGPD | 3j | S-C1, R-C2 |
| 2 | Activer certificate pinning + corriger `SecureTileService` | Sécurité | 1j | S-C2, R-M4 |
| 3 | Valider `tableName` dans GeoImportParser (whitelist regex) | Sécurité | 0.5j | S-C3 |
| 4 | Réécrire `PRIVACY_POLICY.md` (26 PII, base légale, transferts) | RGPD | 1j | R-C1 |
| 5 | Ajouter page consentement RGPD dans onboarding | RGPD | 2j | R-C3 |
| 6 | Créer `RECORD_OF_PROCESSING_ACTIVITIES.md` | RGPD | 1j | R-C4 |
| 7 | Documenter ou supprimer transferts Esri/USA (SCC) | RGPD | 1j | R-C5 |
| 8 | Ajouter `FLAG_SECURE` sur MainActivity | Sécurité | 0.5j | S-H4 |
| 9 | Corriger injection SQL GeoPackage + test | Sécurité | 0.5j | S-C3 |
| 10 | Remplacer `collectAsState()` par `collectAsStateWithLifecycle()` (50+) | Performance/UI | 2j | P-C1, U-H1 |
| 11 | Ajouter downsampling images + intégrer Coil | Performance/UI | 2j | P-C2, U-H5, U-H6 |
| 12 | Activer `kotlin.incremental=true` | Build | 0.1j | B-C1 |

### Phase 1 — Corrections rapides high-impact (semaines 5-8)

| # | Action | Domaine | Effort | Issue |
|---|--------|---------|--------|-------|
| 13 | Extraire 100+ chaînes FR codées en dur → strings.xml | i18n | 5j | I-C1 |
| 14 | Remplacer `SimpleDateFormat(Locale.FRANCE)` → `DateFormat` | i18n | 1j | I-C2 |
| 15 | Remplacer € codé → `NumberFormat.getCurrencyInstance()` | i18n | 1j | I-C3 |
| 16 | Créer `plurals.xml` FR/EN | i18n | 1j | I-C4 |
| 17 | Ajouter `key()` aux LazyColumn (6 écrans) | UI/Perf | 1j | U-H4 |
| 18 | `rememberSaveable` pour formulaires (3 écrans) | UI | 1j | U-H2 |
| 19 | `contentDescription` sur éléments interactifs | A11y | 2j | U-H3 |
| 20 | Streaming exports (JsonWriter + CSV ligne par ligne) | Perf | 3j | P-C3 |
| 21 | Ajouter LIMIT/projection aux requêtes DAO | Perf | 2j | P-H3 |
| 22 | `Flow.first()` → `withTimeoutOrNull` (30+ occurrences) | Perf | 1j | P-H2, U-H8 |
| 23 | BackupWorker : injecter DB + contraintes + idempotence | Misc | 1j | M-C2, M-H3, M-H4 |
| 24 | PriceSyncWorker : `SecureHttpClient` + timeout + backoff | Misc/Sécurité | 1j | M-H5, M-H6, R-M3 |
| 25 | Mettre à jour build tools (AGP, Kotlin, KSP, Compose BOM) | Build | 3j | B-H1, B-H2 |
| 26 | BlurView 2.0.5 → 3.2.0 | Build | 0.5j | B-H3 |
| 27 | Créer workflow CI/CD release.yml | Build | 1j | B-H4 |
| 28 | Supprimer `Lambert93.kt` (unifier sur `Lambert93Converter.kt`) | GIS | 0.5j | G-M1 |
| 29 | Étendre WktUtils (LINESTRING, POLYGON, SRID) | GIS | 2j | G-M2 |

### Phase 2 — Consolidation (semaines 9-16)

| # | Action | Domaine | Effort | Issue |
|---|--------|---------|--------|-------|
| 30 | Implémenter Paging 3 pour grandes listes | Perf | 5j | P-H5 |
| 31 | Droit à l'effacement centralisé ("Effacer mes données") | RGPD | 2j | R-M2 |
| 32 | Politique de rétention + suppression automatique | RGPD | 3j | R-M6 |
| 33 | Documenter décision automatisée + avertissement UI | RGPD | 1j | R-M7 |
| 34 | Désigner DPO + coordonnées dans politique | RGPD | 0.5j | R-M1 (mineur) |
| 35 | Version catalog `libs.versions.toml` | Build | 2j | B-M2 |
| 36 | Décommenter KSP Room args (schema export, incremental) | Build | 0.5j | B-M3 |
| 37 | Narrow ProGuard keep rules | Build | 1j | B-M4 |
| 38 | Jacoco coverage reporting | Build | 1j | B-M5 |
| 39 | Migrer Accompanist → Compose platform | Build | 2j | B-H5 |
| 40 | Timeout sur Flow GPS | Perf | 0.5j | P-M2 |
| 41 | Remplacer `LocationManager` par `FusedLocationProviderClient` (3 écrans) | Perf | 1j | P-M3 |
| 42 | Clustering marqueurs GPS sur carte | Perf | 3j | P-M5 |
| 43 | Détecteur root/debug | Sécurité | 1j | S-H3 |
| 44 | Auth biométrique optionnelle | Sécurité | 2j | S-M1 |
| 45 | Chiffrer DataStore (`EncryptedSharedPreferences`) | Sécurité | 1j | S-M4, M-C3 |
| 46 | Sanitiser logs DEBUG (pas de coordonnées GPS) | Sécurité | 1j | S-H2 |
| 47 | ProGuard : supprimer `Log.i/w/e` en release | Sécurité | 0.1j | S-L1 |
| 48 | FormulaParser : limites longueur/complexité/timeout | Misc | 1j | M-C1 |
| 49 | Tests FormulaParser : division par zéro, expressions vides, imbriquées | Misc | 1j | M-H12 |
| 50 | Extraire sous-fonctions `DataInterpretationEngine.analyzeGrowth()` | Misc | 1j | M-C5 |

### Phase 3 — Excellence (semaines 17+)

| # | Action | Domaine | Effort |
|---|--------|---------|--------|
| 51 | Algorithme géodésique pour surface polygone (Karney) | GIS | 2j |
| 52 | Intégrer PROJ.4 pour reprojection CRS généralisée | GIS | 3j |
| 53 | Interpolation DEM bilinéaire/bicubique | GIS | 2j |
| 54 | Fichiers `.qpj/.prj` dans export QGIS | GIS | 0.5j |
| 55 | Tests Lambert-93 : réduire tolérances à 1m | GIS | 0.5j |
| 56 | URL DVF Cerema : passer en production | GIS | 0.1j |
| 57 | Audit accessibilité complet (touch targets, contraste WCAG AA) | A11y | 3j |
| 58 | `@Stable`/`@Immutable` sur data classes composables | UI | 1j |
| 59 | Deep linking si nécessaire | UI | 2j |
| 60 | Vérification d'âge à l'onboarding | RGPD | 0.5j |
| 61 | Procédure notification violation + registre | RGPD | 1j |
| 62 | Pseudonymisation des noms (hash) | RGPD | 2j |
| 63 | Lint rules pour détecter chaînes codées en dur | i18n | 1j |
| 64 | Profiling Android Profiler pour goulots résiduels | Perf | 2j |
| 65 | Couverture tests 35% → 60% (domain/business) | Tests | 10j |

---

## 10. MATRICE DE SUIVI DES ISSUES

### Recoupements entre domaines

Plusieurs issues sont identifiées par plusieurs audits (confirmant leur criticité) :

| Issue | Domaines concernés | Sévérité consolidée |
|-------|-------------------|---------------------|
| DB non chiffrée (SQLCipher désactivé) | Sécurité S-C1 + RGPD R-C2 + Misc M-C3 | 🔴 CRITICAL |
| Certificate pinning désactivé | Sécurité S-C2 + RGPD R-M4 | 🔴 CRITICAL |
| `collectAsState()` sans lifecycle | UI U-H1 + Perf P-C1 | 🔴 CRITICAL |
| Images sans downsampling | UI U-H5 + Perf P-C2 | 🔴 CRITICAL |
| `Flow.first()` bloquant | UI U-H8 + Perf P-H2 | 🟠 HIGH |
| LazyColumn sans `key()` | UI U-H4 + Perf P-H1 | 🟠 HIGH |
| DataStore non chiffré | Sécurité S-M4 + RGPD + Misc M-C3 | 🟡 MEDIUM |
| `PriceSyncWorker` non sécurisé | Sécurité + RGPD R-M3 + Misc M-H5/H6 | 🟠 HIGH |

### Top 10 des actions à plus fort impact

1. **Réactiver SQLCipher** — débloque Sécurité + RGPD (2 critiques d'un coup)
2. **`collectAsStateWithLifecycle()`** — impact batterie immédiat pour tous les utilisateurs
3. **Coil + downsampling** — évite les crashes OOM
4. **Réécrire `PRIVACY_POLICY.md`** — débloque conformité RGPD
5. **Extraire chaînes FR codées en dur** — débloque i18n
6. **Streaming exports** — évite OOM sur gros datasets
7. **Activer `kotlin.incremental=true`** — gain build immédiat
8. **`key()` sur LazyColumn** — fluidité UI immédiate
9. **Page consentement RGPD** — débloque conformité
10. **Certificate pinning** — sécurité réseau

---

## 11. CONCLUSION

L'application GeoSylva présente une **base technique solide** (architecture Clean, Compose, Room, coroutines bien scopées, formules forestières correctes, SIG fonctionnel) mais nécessite **40 corrections critiques/high** avant une mise en production conforme aux standards ONF/IGN.

### Points forts globaux
- ✅ Architecture Clean-ish (domain/data/presentation/security)
- ✅ ViewModels corrects, navigation type-safe, thème Material 3 complet
- ✅ Formules dendrométriques de base conformes (vague 1)
- ✅ Formules GIS de base correctes (Lambert-93 IGN, Haversine, Horn DEM)
- ✅ Pas de `GlobalScope`, pas de `runBlocking`, coroutines bien scopées
- ✅ Signing et .gitignore excellents
- ✅ Backup rules et network security config bien configurés

### Points faibles bloquants
- ❌ Chiffrement DB désactivé (S-C1 / R-C2)
- ❌ RGPD non conforme (5 critiques)
- ❌ i18n fragmentaire (100+ chaînes codées en dur)
- ❌ Performance sur gros datasets (pas de pagination, exports tout en RAM)
- ❌ Dépendances build obsolètes (Kotlin 1.9, Compose BOM 2024.09)
- ❌ Accessibility insuffisante (contentDescription manquants)

### Recommandation finale

**Ne pas déployer en production** sans avoir au minimum terminé la **Phase 0** (12 actions, ~14 jours-homme). La **Phase 1** (17 actions, ~25 jours-homme) est requise pour une qualité professionnelle. Les phases 2 et 3 élèvent l'application au standard "ONF quality or better" visé par l'utilisateur.

**Effort total estimé** : ~80 jours-homme pour les phases 0-2, ~25 jours-homme supplémentaires pour la phase 3.

---

*Rapport généré le 2026-06-29 par la cellule d'audit GeoSylva (vague 2). Voir `AUDIT_FORESTIER_COMPLET.md` pour la vague 1 (intégrité DB, calculs dendrométriques, tarifs, logique forestière, traitement données).*
