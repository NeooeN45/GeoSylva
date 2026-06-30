# Changelog — GeoSylva

All notable changes to this project will be documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com).

## [2.4.0] — 2026-06-30

### Added
- **Transformation Helmert WGS84→ETRS89** — `Lambert93Converter` corrige le drift continental ~60cm pour les coordonnées GPS vers Lambert 93.
- **Migration Room 31→32** — `Migration31to32` : retire la contrainte FOREIGN KEY incorrecte sur `parcelles.forestOwnerId` qui référençait `forets.foretId` au lieu de `groups.id`. Corrige le crash `SQLiteConstraintException` lors de la création de parcelles.
- **Vérification permission CAMERA** — `DiagnosticPhotoCaptureSection`, `StationPhotoGalleryBlock`, `PlacetteDetailScreen` : ajout `cameraPermissionLauncher` (ActivityResultContracts.RequestPermission) avant lancement `TakePicture`. Corrige le crash `SecurityException` quand la permission caméra a été refusée.
- **Consentement RGPD avec refus** — bouton "Decline" dans l'onboarding avec dialogue de confirmation, conforme Art. 7 RGPD.
- **SCC (Standard Contractual Clauses)** — documentation RGPD mise à jour pour les transferts vers services US (Esri/MapLibre/CartoCDN).
- **Certificate Pinning vérifié** — hashes SHA-256 confirmés pour data.geopf.fr, tile.opentopomap.org, basemaps.cartocdn.com, server.arcgisonline.com.
- **420+ tests unitaires** — couverture étendue : IBP, tarifs, conversion volume, alias essences, triangles structure, formatage monétaire, presets régionaux.
- **Système multi-agent** — skills forest-crew (7 experts), brainstorm, memory-sync, multi-agent. Vault Obsidian pour mémoire cross-tool.
- **Audit complet** — `AUDIT_FORESTIER_COMPLET.md`, `AUDIT_GLOBAL_GEOSYLVA.md`, `AUDIT_UI_UX_GLOBAL.md`, `MASTER_PLAN.md`, `RESEARCH_OPPORTUNITIES.md`.

### Changed
- Version DB 31 → 32.
- README mis à jour : 28 tables, 32 migrations, SQLCipher, certificate pinning, 420+ tests.
- Documentation consolidée : suppression de `IMPLEMENTATION_GUIDE.md`, `PROJECT_SUMMARY.md`, `SECURITY.md` (remplacés par audits et registre RGPD).
- Navigation découpée en 5 sous-graphes (`ForestryNavigation`).

### Fixed
- **Crash FOREIGN KEY constraint** — `ParcelleEntity` avait une FK sur `forestOwnerId` vers `forets.foretId`, mais `forestOwnerId` stocke un `groupId` (table `groups`). Migration 31→32 recrée la table sans cette FK.
- **Crash SecurityException CAMERA** — l'app lançait `Intent(IMAGE_CAPTURE)` sans vérifier `Manifest.permission.CAMERA`, causant un crash si la permission était refusée.
- **Conversion Lambert 93** — ajout transformation Helmert pour alignement ETRS89 (RGF93) au lieu de WGS84 brut.

---

## [2.0.0] — 2026-03-06

### Added
- **GPS auto-capture IBP** — latitude/longitude capturés automatiquement à l'ouverture de l'évaluation IBP via `LocationManager` (GPS + réseau). Stockés en DB. Migration Room 14→15.
- **IbpQgisExporter** — export ZIP contenant `ibp_points.geojson` (points GPS), `ibp_all.csv` (toutes évaluations), `ibp_style.qml` (style QGIS 5 niveaux) et `ibp_metadata.json`. Bouton Map dans `IbpProjectsScreen`.
- **IbpDiagnosticScreen** — tableau de bord biodiversité : en-tête score /50, radar animé A–J, tableau de détail par critère avec barres, liste top-5 actions prioritaires avec gain en pts + effort, carte potentiel (gain max groupe A), résumé tendance si ≥ 2 évaluations. Route `ibp/diagnostic/{parcelleId}`.
- **IbpCompareScreen** — comparaison temporelle : sparklines animées score total, groupe A vs B, et chaque critère A–J ; tableau récapitulatif couleur-codé ; bouton Timeline dans `IbpProjectsScreen` (si ≥ 2 évaluations). Route `ibp/compare/{parcelleId}`.
- **Didacticiel enrichi** — nouvelle page IBP (EmojiNature, vert foncé) entre Synthèse et Export ; 9 pages au total. Export page mise à jour (IBP QGIS, Excel).
- **Strings EN+FR** — modes IBP (COMPLET/RAPIDE/BOIS_MORT/CONTEXTE/PEUPLEMENT), labels saisie numérique (BMg/BMm/TGB/GB/DMH/VS), sections référence IBP.

### Changed
- versionCode 7 → 8, versionName 1.7.0 → **2.0.0**.
- `IbpProjectsScreen` TopAppBar : icônes Analytics (diagnostic), Timeline (comparaison) et Map (export QGIS) conditionnels.
- Settings : suppression de l'item "Mode GPS : Standard" non interactif (trompeur).

### Fixed
- Conflit `ibpLevelLabel` entre `IbpEvaluationScreen` (@Composable) et `IbpDiagnosticScreen` (privé) — renommé `diagLevelStr` dans le screen diagnostic.

---

## [1.7.0] — 2026-03-05

### Added
- **IBP scoring officiel CNPF** — système 0/2/5 pts par critère (max 50 pts total) remplaçant l'ancien 0/1/2. Groupe A max 35 pts (7 critères), Groupe B max 15 pts (3 critères).
- **Conditions de croissance IBP (v3)** — nouveau champ `growthConditions` (Plaine/Colline, Montagne, Sub-alpin, Méditerranéen) avec dropdown dans `IbpMetaSection`. Migration DB 12→13.
- **Améliorations prioritaires** — section dans `IbpResultCard` listant les top 3 critères faibles (score 0 ou 2) avec conseil actionnable par critère (`ibpCriterionTip`).
- **Radar chart normalisé** — `IbpRadarChart` normalisé sur /5 (max), grille intérieure à 40 % (= 2 pts) et 100 % (= 5 pts).
- **Chips de score colorés** — options 0 pt (rouge), 2 pts (ambre), 5 pts (vert) dans `IbpCriterionCard`.
- **10 conseils IBP** — `ibp_tip_e1` → `ibp_tip_hc` en EN et FR : actions concrètes par critère.
- **Rétrocompatibilité schema v1** — `IbpAnswers.migrateToV2()` convertit automatiquement les scores 0/1/2 historiques vers 0/2/5.

### Changed
- `IbpGroupHeader` : max A affiché = 35 pts, max B = 15 pts, total = /50.
- `IbpScoreHeader` : score total sur /50, alignement baseline texte score/max.
- `IbpResultDialog` : totaux affichés /50, /35, /15.
- `IbpMartelageCard` : score /50 (était /20).
- Strings EN+FR : onboarding mis à jour (max 35/15/50), sous-titres groupes, `ibp_in_martelage`.
- `IbpLevel` : seuils 0-9/10-19/20-29/30-39/40-50 (calibrés pour le nouveau max 50).

### Fixed
- `IbpGroupHeader` perdu lors d'une édition précédente — restauré.
- Apostrophes non échappées dans les strings FR (`ibp_group_b_subtitle`, `ibp_tip_vs`, `ibp_tip_cf`) causant une erreur AAPT2.

---

## [1.6.0] — 2026-03-04

### Added
- **IBP Projets** — écran global listant toutes les évaluations IBP classées par parcelle et date, avec badge score, suppression individuelle et navigation vers l'édition.
- **Outil de mesure carte** — tracé de distances et surfaces sur la carte, avec sélection d'unités (m/km, m²/ares/ha) et palette de 8 couleurs. Panneau de résultats avec bascule d'unité.
- **Bannière GPS** — avertissement discret et masquable si la précision GPS dépasse 20 m pour une tige.
- **Permissions après onboarding** — demande groupée des permissions CAMERA, READ_MEDIA_IMAGES et ACCESS_FINE_LOCATION au lancement initial.
- **Mesure de hauteur** — caméra ouverte par défaut quand les capteurs sont disponibles. Chips de distance : 10/15/20/25 m (30 m retiré).

### Changed
- **EssenceDiamScreen TopAppBar** redessiné — double ligne (nom + actions), ton surface + ombre.
- **EssenceDiamScreen** remplissage rapide — remplit toutes les classes déjà saisies (pas seulement les vides).
- **MartelageScreen Tab 2** renommé Analysis → Écologie / Ecology ; `BiodiversityCard` → "Écologie du peuplement" / "Stand ecology".
- **HeightCameraAimOverlay** — bouton Capturer remonté (padding top=16, bottom=56).
- **IBP crash release** corrigé — `IbpRepositoryImpl` et `IbpEvaluationScreen` utilisent `IbpAnswers.serializer()` explicite (ProGuard safe). Route `IbpHistory` déplacée avant `IbpEvaluation` dans `ForestryNavigation`.
- `EssenceDiamScreen` LazyColumn : hauteur max 350 → 520 dp.

---

## [1.5.0] — 2026-03-03

### Added
- **Live G/ha counter** — basal area (G m²/ha) and stem count (N) displayed as real-time badges in EssenceDiamScreen during tally entry; falls back to G total (m²) when no plot surface is configured.
- **Species search bar** — instant filter by name or code in the species grid (PlacetteDetailScreen); animated slide-in/out, clears on close, shows "no results" state.
- **Share synthesis** — one-tap Share button (✉) in MartelageScreen toolbar sends a plain-text report (N, G, V, Dg, Dm, Hdom, revenue) to any messaging/email app via Android Intent.

### Changed
- versionCode 4 → 5, versionName 1.4.0 → **1.5.0**.

---

## [1.4.0] — 2026-03-03

### Added
- **Digital clinometer** — measure tree height with the phone's sensors (rotation vector ±0.5°, gyro+accel ±1°, accelerometer ±2°, or manual fallback). Multi-step dialog with live angle gauge, stability bar, result with precision estimate.
- **Auto-capture** — angle automatically locked after ~1.5 s of stability ≥ 82 %, with green countdown ring on the gauge and haptic feedback on capture.
- **Averaged capture** — uses the rolling average of the last 8 sensor readings for more accurate angle lock (smoother than single-reading snap).
- **Predefined distance chips** — quick-select 10 / 15 / 20 / 25 / 30 m in the distance step, plus step-counting tip (1 step ≈ 0.7 m).
- **Angle validation warnings** — orange warning banner when top angle > 80° (too close), < 5° (too far/too short), or base angle is unexpectedly positive.
- **Recapture button** — after locking an angle, one-tap reset to redo the measurement without restarting the dialog.
- **Step indicator** — "Step X / Y" subtitle in dialog title, adapts to waist-warning and base-angle toggle state.
- **Screen-always-on** during measurement (`keepScreenOn`) so the display never dims in the field.
- **Waist-warning dismissable forever** — "Never show again" checkbox persisted in DataStore (`skipWaistWarning`).
- **Phone height preference** — adjustable from 0.3 to 2.5 m, default **1.5 m**, persisted across sessions (`phoneHeightM`).
- **Height auto-fill** — measured height pre-fills all empty diameter-class height fields in EssenceDiamScreen.
- **Onboarding page** — new "Measure tree heights" page (Height icon, teal #006064) with 3 bullet points.

### Changed
- Default phone height changed from 1.0 m to **1.5 m** (closer to average waist height for forestry technicians).
- versionCode 3 → 4, versionName 1.2.0 → 1.4.0.

## [1.3.0] — 2026-02-22

### Changed
- **GPS capture behavior** — immediate trigger on tap (before snackbar), persists even if user leaves the screen using GlobalScope
- **GPS profile simplification** — removed FAST/STANDARD/PRECISE modes and precision threshold slider, replaced with single optimal profile (6 readings, max 20m, timeout 15s)
- **GPS point reuse** — when a stem is deleted and re-added with same essence and diameter class, the last GPS point is automatically reused
- **Map precision visualization** — colored precision circles around GPS points: ≤3m (excellent) ≤6m (good) ≤12m (moderate) >12m (poor)  no GPS (gray)

## [1.2.0] — 2026-02-20

### Added
- **GPS averaging with outlier rejection** — multi-reading averaging using MAD (Median Absolute Deviation) to reject outlier readings. 3 configurable profiles: Fast (3 readings), Standard (5), Precise (8).
- **GPS precision threshold** — configurable per-stem rejection of imprecise GPS points (3–40 m slider in Settings).
- **GPS periodic monitoring** — background signal quality check with snackbar warnings when accuracy degrades.
- **Offline tile manager** — download map tiles for any region and zoom levels for full offline cartography. Progress tracking with UI overlay.
- **12 map layers** with tile URL templates for offline downloading (OSM, IGN, satellite, cadastre, forests, topo…).
- **Offline local map style** — MapLibre style generated from locally cached tiles.
- **Map GPS reliability filter** — toggle to show only stems below a configurable precision threshold on the map.
- **Shapefile export** (SHP/SHX/DBF/PRJ) — pure Java ESRI Shapefile writer, zero external dependencies. Exports geolocated stems with full attribute table.
- **Dashboard screen** — visual summary with Compose Canvas charts: donut (species distribution), bar chart (diameter classes), horizontal bars (basal area by species), and 4 summary mini-cards.
- **Harvest simulation** — enter N/ha and G/ha before harvest to compute removal rates and residual stand metrics. Persisted per scope.
- **Sanity checker** — 30+ automated data consistency checks at tree-level and aggregate-level (diameter/height bounds, H/D ratio, V/G ratio, N/ha extremes, duplicate detection, surface coherence…).
- **SanityWarningsCard** — UI card displaying coherence warnings with severity icons (error/warning/info).
- **HarvestSimulationCard** — UI card showing removal rates and residual stand.
- **Essence enrichment** — 9 new fields per species: `densiteBois`, `qualiteTypique`, `typeCoupePreferee`, `usageBois`, `vitesseCroissance`, `hauteurMaxM`, `diametreMaxCm`, `toleranceOmbre`, `remarques`.
- **95+ canonical essences** — expanded species database with full forestry metadata.
- **Essence info card** — species properties displayed in PlacetteDetailScreen actions dialog.
- **Contextual tip cards** — dismissible help cards on Parcelles, Placettes, Essences, Map, and Martelage screens.
- **Onboarding revamp** — 7 pages (was 4) with bullet points, accent-colored gradient icons, page counter, scale/alpha animations, and scrollable content. New pages: GPS, Map, Export.
- **Height prompt snooze** — option to dismiss height reminders for 1h, 4h, or 24h. Persisted in preferences.
- **Map stem rendering overhaul** — GeoJSON source with clustering (CircleLayer + SymbolLayer), tap-to-inspect stems, dynamic color/size by essence and diameter.
- **SanityCheckerTest** — unit tests for data coherence validation.
- **QgisExportHelperTest** — 11 tests for GeoJSON, CSV-XY, and WKT export.
- **Price system overhaul** — quality-adjusted pricing (A=×2.5, B=×1.5, C=×1.0, D=×0.4) with per-essence coefficients.
- **Product breakdown UI** — expandable cards in MartelageScreen showing volume ventilation (BO/BI/BCh/PATE) with quality-adjusted pricing per essence.
- **Enriched price defaults** — ~100 entries with essence×product×diameter ranges (e.g., Douglas BO 25-34cm = 80€, 35-44cm = 95€, 45+ = 120€).
- **Documentation refresh** — `docs/methodes_calcul_volume.md` rewritten to match actual TarifCalculator implementation (7 methods + price system).

### Improved
- **Partial volume results** — synthesis shows partial volumes with completeness percentage instead of zeroing when some heights are missing.
- **VolumeCard** always visible with partial-result label when completeness < 100%.
- **ClassDistributionCard** simplified — removed unused `volumeAvailable` and `animationsEnabled` parameters.
- **Martelage CSV export** includes `volume_completeness_pct` and harvest simulation data.
- **OnboardingScreen** — animations with spring physics, radial gradient icons, vertical scroll support.
- **Settings screen** — new GPS section with capture mode dropdown, precision slider, map reliability toggle.
- **ProGuard/R8** rules optimized for release builds (minification + resource shrinking).
- **`.gitignore` hardened** — excludes build logs, keystores, archives, SIG data, OS files.
- **Missing-heights wording** changed from "disabled" to "partial" throughout the app.

### Fixed
- Unescaped apostrophe in French strings causing resource compilation error.
- Volume card was hidden when heights incomplete — now always visible with completeness indicator.
- Missing-height warning correctly tied to actual missing essences, not global flag.

### Removed
- Tracked build artifacts from repository: `buildlog.txt`, `kotlinlog.txt`, `local.properties`, `logo app.png`, `keystore/upload-certificate.pem`.
- Scratch reference code: `martelage_calcul/` directory.
- Unused animation imports from `MartelageSummaryCards.kt`.

### Security
- Created `SECURITY.md` with responsible disclosure policy.
- Removed `upload-certificate.pem` from version control.
- Added price sync (OkHttp) disclosure to `PRIVACY_POLICY.md`.

## [1.1.0] — 2026-02-17

### Added
- **FGH tariff method** for volume calculation (V = F × G × H).
- **PDF synthesis export** — professional A4 PDF report with dendrometry, valorization, and per-essence table.
- **GeoJSON & CSV-XY export** for QGIS integration with Lambert 93 coordinates.
- **PeuplementAvantCoupe calculator** — "stand before harvest" Excel-equivalent computation for softwoods.
- **Wood quality grading** (ABCD) with quality distribution in martelage summary.
- **Privacy Policy** (`PRIVACY_POLICY.md`) for Play Store compliance.
- Automated local backup via WorkManager.

## [1.0.0] — 2025-12-01

### Added
- Initial release with tree counting, diameter measurement, multi-tariff volume calculation.
- MapLibre map with satellite, topographic, cadastral layers.
- GPS single-shot location with precision averaging.
- CSV/XLSX/JSON import and export.
- Shapefile overlay support.
- Multi-language support (French/English).
- Dark/light theme with customizable font size.
- Onboarding wizard.
