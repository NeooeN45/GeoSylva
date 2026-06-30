# AUDIT COMPLET — Carte, sources carto libres & GPS/positionnement

**Projet** : GeoSylva
**Date** : 2026-06-30
**Périmètre** : MapScreen + couche géospatiale + GPS + sources carto externes
**Méthode** : 10 sous-agents parallèles (audit read-only) + synthèse

---

## 0. STACK TECHNIQUE AUDITÉE

| Composant | Technologie | Version |
|-----------|-------------|---------|
| Carte | MapLibre GL Android SDK | 10.3.1 (namespace `com.mapbox.mapboxsdk`) |
| GPS | Fused Location Provider | 21.3.0 |
| Capteurs | SensorManager (rotation vector, accel, mag) | API Android |
| Coordonnées | Lambert93 custom (Helmert + Snyder) | — |
| Sources tuiles | IGN GéoPortail WMTS, OSM, OpenTopoMap, CartoDB, ESRI | raster XYZ |
| Élévation | SRTM 30m (OpenTopoData API) + SRTM 90m HGT embarqué | — |
| HTTP | OkHttp (SecureHttpClient) + HttpURLConnection (legacy) | — |
| Stockage | SQLCipher (chiffré) + filesystem | — |

**Fichier critique** : `MapScreen.kt` = **3105 lignes** (monolithe "God Composable").

---

## 1. SYNTHÈSE EXÉCUTIVE

### Verdict global par domaine

| # | Domaine | Score | Critiques | Majeurs | Priorité |
|---|---------|-------|-----------|---------|----------|
| 1 | MapScreen UI/Compose | 4/10 | 3 | 7 | 🔴 Refactor |
| 2 | GPS & Fused Location | 6/10 | 8 | 12 | 🔴 Robustesse |
| 3 | Compas & Navigation | 6/10 | 6 | 3 | 🔴 Précision |
| 4 | Sources tuiles & presets | 6/10 | 0 | 3 | 🟡 Conformité |
| 5 | Tuiles hors-ligne | 5/10 | 3 | 5 | 🔴 Perf+resilience |
| 6 | Transformations coord. | 7/10 | 0 | 3 | 🟡 Exactitude |
| 7 | Import/export géo | 6/10 | 6 | 6 | 🟡 Formats |
| 8 | Élévation & terrain | 5/10 | 3 | 4 | 🟡 Précision |
| 9 | APIs externes & RGPD | 6/10 | 4 | 21 | 🔴 Sécurité |
| 10 | Rendu MapLibre | 7/10 | 0 | 4 | 🟡 Perf+légal |

### Top 10 problèmes CRITIQUES (transverses)

1. **MapScreen.kt monolithe 3105 lignes** — 23 états locaux, 22 LaunchedEffect, 0 ViewModel → intestable, fuites mémoire (listeners MapLibre jamais retirés)
2. **Fuites mémoire listeners MapLibre** — `addOnMapClickListener` ajouté en factory, jamais `removeOnMapClickListener` → fuite garantie à chaque recomposition
3. **31 catch vides `catch (_: Throwable) {}`** — erreurs silencieuses, impossible à déboguer, violation "fail fast"
4. **Pas de cache HTTP** — aucun OkHttp Cache, aucun respect ETag/Cache-Control → non-conformité politique OSM (min 7 jours requis)
5. **APIs IGN sans clé API** — depuis 2023, services privés nécessitent `apikey` → risque de blocage
6. **User-Agent OSM non conforme** — `"GeoSylva/1.0 Android"` sans contact → violation policy OSM
7. **Aucune attribution légale dans le style JSON** — violation licences OSM/IGN/ESRI/CartoDB
8. **GPS : 0 monitoring GnssStatus/NMEA/DOP** — aucune visibilité qualité signal, pas de mode "forêt" adaptatif sous canopée
9. **Pas de foreground service** pour GpsParcelTracer → tracking tué par OS en arrière-plan
10. **Téléchargement tuiles séquentiel** — 6000 tuiles × 50ms = 5 min, pas de parallélisation, pas de reprise, pas de retry

---

## 2. DÉTAIL PAR DOMAINE

### 2.1 MapScreen UI/Compose — 🔴 CRITIQUE

**Problèmes clés** :
- God Composable 3105 lignes, 23 `mutableStateOf`, 22 `LaunchedEffect`
- 0 ViewModel → état non hoisted, non testable, perdu aux recompositions
- Listeners MapLibre (`addOnMapClickListener`, `attachTigeTapInfo`) **jamais retirés** → fuite mémoire
- LocationComponent activé jamais désactivé → batterie + fuite
- Double `addOnMapClickListener` (ligne 1374 + 685) → conflit, taps cassés après switch layer
- `tigeTapAttached` reste `true` après switch style → taps morts
- Race condition : 2 LaunchedEffect modifient la caméra, `delay(600)` hack
- Tailles tactiles 32-36dp (< 48dp guideline) → inutilisable avec gants
- 0 état vide/erreur/chargement
- Couleurs hardcoded, pas de dark mode adaptatif
- Recompositions excessives (pas de `derivedStateOf`)
- GeoJSON reconstruit entièrement à chaque update (pas de `setGeoJson`)

**Recommandations** :
1. Extraire `MapViewModel` (Hilt) + `MapUiState` data class
2. Découper en 10 sous-composables (LayerPicker, ShapefilePanel, LegendPanel, MeasurePanel, TracePanel, TreeInfoCard, MapControls, MapView, MapUtils)
3. `DisposableEffect` pour retirer listeners + désactiver LocationComponent
4. `derivedStateOf` pour `displayedGeoTiges`
5. `source.setGeoJson()` au lieu de recréer la source
6. Remplacer 31 catch vides par `Log.e` + toast utilisateur

### 2.2 GPS & Fused Location — 🔴 ROBUSTESSE

**Problèmes clés** :
- 0 `GnssStatus.Callback` → pas de compte satellites/constellations
- 0 listener NMEA → pas de parsing DOP ($GPGSA)
- 0 `GnssMeasurementCallback` → pas de C/N0, multipath
- Pas de mode "FOREST" adaptatif sous canopée (multi-path)
- `GpsAverager` : MAD_THRESHOLD=2.5 trop permissif, maxAccuracyM=25m trop élevé, targetReadings=5 insuffisant
- Pas de timeout global sur `averageFlow()` → blocage infini possible
- Pas de fallback COARSE si FINE échoue
- 0 gestion `GoogleApiAvailability` / `ApiException` / `ResolvableApiException`
- Pas de retry backoff sur `requestLocationUpdates`
- `GpsDistanceMeasureDialog` utilise `LocationManager` legacy (pas FusedLocation)
- Pas de rationale permission, pas de gestion refus permanent
- `setMaxUpdateDelayMillis(0)` désactive batching → surconsommation batterie
- Priorité GPS statique (pas adaptative mouvement/immobilité)

**Recommandations** :
1. Créer `GnssMonitor.kt` (GnssStatus + NMEA + DOP)
2. Foreground service pour GpsParcelTracer
3. Timeout global 60s + dégradation gracieuse (relâcher seuil si 0 lecture)
4. Mode FOREST : interval 2-3s, 10-15 lectures, MAD 1.5-2.0
5. Migrer GpsDistanceMeasureDialog vers FusedLocation
6. Retry backoff exponentiel (1s→2s→4s→8s)
7. Smart batching (`setGranularity`, `setMaxUpdateDelayMillis(interval*3)`)

### 2.3 Compas & Navigation — 🔴 PRÉCISION

**Problèmes clés** :
- **0 déclinaison magnétique** (0 occurrence "declination"/"IGRF"/"WMM") → compas indique nord magnétique, pas géographique (erreur 1-3° en France)
- `TreeNavigator` n'utilise pas `TYPE_ROTATION_VECTOR` (moins précis)
- `onAccuracyChanged` vide partout → utilisateur ne sait jamais quand calibrer
- 0 UI de calibration (figure 8)
- `TreeNavigator` : 0 lissage azimut → flèche tremble
- Flèche navigation statique (pas d'animation lerp)
- Pas de mode guidage (instructions, haptique, bipeur)

**Recommandations** :
1. Implémenter WMM/IGRF local (coefficients WMM2025 embarqués, ~500KB)
2. Lissage TreeNavigator (buffer + moyenne circulaire comme CompassManager)
3. Exposer accuracy sensor + indicateur visuel (🔴🟡🟢)
4. UI calibration figure 8
5. `TYPE_ROTATION_VECTOR` dans TreeNavigator
6. Animation flèche `animateFloatAsState`

### 2.4 Sources tuiles & presets — 🟡 CONFORMITÉ

**Problèmes clés** :
- Attribution absente du style JSON → violation licences
- User-Agent non conforme OSM (pas de contact)
- Couche `HYDROGRAPHY.HYDROGRAPHY` deprecated
- Pas de retry backoff
- Pas de fallback basemap cascade
- UI config couches manquante (opacity, maxZoom, ordre)

**Sources manquantes recommandées** (TOP 10 à ajouter) :
1. IGN Orthophoto (`ORTHOIMAGERY.ORTHOPHOTOS`)
2. IGN Admin Express (`LIMITES_ADMINISTRATIVES_EXPRESS.LATEST`)
3. IGN OCSGE Couverture (`OCSGE.COUVERTURE.2021-2023`)
4. IGN OCSGE Usage (`OCSGE.USAGE.2021-2023`)
5. IGN MNS Surface (`ELEVATION.ELEVATIONGRIDCOVERAGE.HIGHRES.MNS`)
6. CyclOSM (vélo)
7. HOT OSM (humanitaire)
8. CartoDB Positron (clair)
9. CartoDB Dark Matter (sombre)
10. Stamen Toner (N&B)

### 2.5 Tuiles hors-ligne — 🔴 PERF+RESILIENCE

**Problèmes clés** :
- Téléchargement **séquentiel** (boucles for imbriquées) → 5 min pour 6000 tuiles
- HttpURLConnection sans pooling (vs OkHttp déjà dispo)
- 0 reprise après interruption (pas de checkpoint)
- 0 retry sur échec tuile
- 0 validation intégrité (pas de checksum/taille)
- Cache hit sans validation (`dest.exists()` suppose valide)
- Pas d'estimation taille/temps avant download
- Pas de bouton annulation
- Glyphs online dans style offline → pas 100% offline
- Pas de MBTiles (standard GIS)

**Recommandations** :
1. OkHttp + parallélisation (Semaphore 8 + `limitedParallelism(8)`)
2. Checkpoint JSON pour reprise
3. Retry backoff 3 tentatives
4. `estimateDownload()` (tileCount, sizeMb, timeMin) + dialogue confirmation
5. Support MBTiles (SQLite)
6. LRU eviction + limite 500MB
7. Glyphs offline embarqués
8. Bouton annulation + vitesse + ETA

### 2.6 Transformations coordonnées — 🟡 EXACTITUDE

**Problèmes clés** :
- Paramètres Helmert WGS84→ETRS89 **non officiels** (0.057, 0.005, 0.029) → source inconnue, convention non documentée
- RGF93≈ETRS89≈WGS84 pour France (<1m) → Helmert quasi-nul, paramètres actuels faux
- 0 projection CC42-CC50 (cadastre)
- 0 Lambert I-IV historique, 0 UTM, 0 outre-mer (RGR92, RGFG95, RGSPM06, RGNC14-49, RGPF)
- 0 grille NTv2 (précision 1m vs 1cm)
- Tests : tolérance 50m/150m excessive (théorie <0.1m), 0 point contrôle IGN officiel
- 2 implémentations redondantes (Lambert93.kt + Lambert93Converter.kt)
- 0 validation coordonnées L93 (X∈[100k,1.3M], Y∈[6M,7.2M])

**Recommandations** :
1. Supprimer paramètres Helmert non officiels, documenter RGF93≈WGS84
2. Ajouter points contrôle IGN (Paris, Strasbourg, Bordeaux, Nice, Brest) tolérance 0.1m
3. Implémenter CC42-CC50 (paramètres officiels fournis)
4. `isValidL93()` + validation plage
5. Unifier les 2 implémentations (garder Lambert93Converter)
6. Optionnel : proj4j si besoin multi-projections/outre-mer

### 2.7 Import/export géo — 🟡 FORMATS

**Problèmes clés** :
- WKT : regex POINT uniquement (pas LINESTRING/POLYGON/MULTI*)
- Shapefile parser : Polygon uniquement (pas Point/LineString)
- `readBytes()` charge tout en mémoire → crash >100MB
- 0 tests pour ShapefileParser, GeoImportParser, exporters
- 0 export GeoPackage (standard OGC moderne)
- 0 export DXF (CAD forestier)
- 0 génération projet QGIS (.qgs/.qpj)
- 0 détection CRS GeoJSON/KML/GPX/GeoPackage (assume WGS84)
- 0 preview import
- Mapping champs DBF hardcoded

**Recommandations** :
1. Intégrer JTS Topology Suite (WKT complet, validation géométries)
2. Intégrer Proj4J (reprojection CRS complète)
3. Tests unitaires parsers core (ShapefileParser, GeoImportParser)
4. Export GeoPackage (SQLite, tables gpkg_*)
5. Streaming gros fichiers (buffer 8KB au lieu de readBytes)
6. Preview import (carte + stats)
7. Génération .qgs/.qpj

### 2.8 Élévation & terrain — 🟡 PRÉCISION

**Problèmes clés** :
- **0 intégration IGN RGE Alti** (1m LiDAR HD disponible gratuitement) → précision 30-90m vs <1m possible
- SRTM 30m via API OpenTopoData (online, pas de fallback robuste)
- Cache SRTM résolution 0.01° (1.1km) vs SRTM 30m
- Nodata remplacé par 0 → fausse calculs pente
- 0 interpolation bilinear/bicubic (nearest neighbor)
- 0 remplissage voids SRTM
- 0 couche altitude/MNT/hillshade dans MapScreen
- ParcelleAutoFill : 0 intégration parcellaire IGN, 0 cache commune

**Recommandations** :
1. Intégrer IGN RGE Alti API (`data.geopf.fr/altimetrie/calcul/alti/rest/elevation.json`) → précision ×10-30
2. Interpolation bilinear dans EmbeddedDemService
3. Remplissage voids SRTM (IDW voisins)
4. Cache SRTM résolution 0.001° (110m)
5. Couche RGE ALTI WMTS + hillshade dans MapScreen
6. Parcellaire IGN dans ParcelleAutoFill (CADASTRALPARCELS)
7. Fallback cascade : IGN Alti → SRTM 30m → SRTM 90m

### 2.9 APIs externes & RGPD — 🔴 SÉCURITÉ

**Problèmes clés** :
- **APIs IGN sans clé API** → risque blocage (depuis 2023)
- **0 cache HTTP** (OkHttp Cache) → non-conformité OSM
- User-Agent OSM non conforme (pas de contact)
- OpenTopoData : pas de rate limiter (1 req/sec requis), pas de retry 429
- 5 services utilisent `URL.readText()`/`HttpURLConnection` au lieu d'OkHttp
- 0 retry backoff exponentiel
- 0 gestion codes HTTP (304/404/429/403/500)
- Certificate pinning : 4 domaines seulement, hashes hardcodés
- Caches JSON sans expiration
- URL Cerema "preprod" en production

**Points positifs** :
- ✅ SQLCipher (DB chiffrée)
- ✅ 0 Firebase/analytics/tracking
- ✅ CrashLogger local uniquement
- ✅ Certificate pinning activé (4 domaines)
- ✅ network_security_config : cleartext interdit
- ✅ Transferts UE majoritairement (IGN, Etalab, INRAE, Cerema, Open-Meteo)

**Recommandations** :
1. Clé API IGN Géoplateforme (BuildConfig.IGN_API_KEY)
2. OkHttp Cache 50MB + interceptor Cache-Control
3. User-Agent conforme : `"GeoSylva/2.3.0 (+https://geosylva.fr; contact: contact@geosylva.fr)"`
4. Rate limiter OpenTopoData (1 req/sec)
5. Migrer tous `URL.readText()` vers SecureHttpClient
6. RetryInterceptor backoff exponentiel
7. Gestion codes HTTP (304/429/403/500)
8. Expiration caches locaux (TTL)

### 2.10 Rendu MapLibre — 🟡 PERF+LÉGAL

**Problèmes clés** :
- **Attribution légale absente** du style JSON (IGN/OSM/ESRI/CartoDB)
- Source tige **recréée** à chaque update (pas de `setGeoJson`) → lag
- Sources trace/measurement recréées à chaque update
- Pas de contrôle ordre layers (pas de `addLayerBelow/Above`)
- GeoJsonOptions : pas de `withBuffer`/`withTolerance`/`withClusterExpansionZoom`
- Pas de highlight au tap
- Pas de scale bar native
- `isDark` défini mais non utilisé pour UI
- Pas de transitions de style
- Labels cluster taille fixe (pas adaptative zoom)

**Points positifs** :
- ✅ Clustering bien configuré (radius 50, maxZoom 13)
- ✅ Data-driven styling (couleur essence, précision GPS)
- ✅ Interpolations performantes (peu de stops)
- ✅ Filtres simples (has/eq/not/all)
- ✅ Collision labels bien gérée
- ✅ Palette essence + code couleur précision GPS cohérents
- ✅ Légende interactive

**Recommandations** :
1. `source.setGeoJson()` au lieu de recréer (impact perf majeur)
2. Attribution dans chaque source raster
3. `addLayerBelow/Above` pour ordre z-index contrôlé
4. `withBuffer(8).withTolerance(0.5).withClusterExpansionZoom(true)`
5. Highlight layer au tap
6. `map.uiSettings.isScalebarEnabled = true`
7. Dark mode adaptatif (utiliser `isDark`)
8. Transitions style (`"transition": {"duration": 300}`)

---

## 3. PLAN D'AMÉLIORATION CONSOLIDÉ

### Phase 1 — Critiques (impact maximal, ~5 jours)

| # | Action | Domaine | Fichier | Effort |
|---|--------|---------|---------|--------|
| 1.1 | Ajouter attribution légale dans style JSON | Légal+Rendu | MapScreen.kt:775 | 2h |
| 1.2 | User-Agent OSM conforme | Conformité | OfflineTileManager.kt:114 | 30min |
| 1.3 | Remplacer 31 catch vides par Log.e | Robustesse | MapScreen.kt | 4h |
| 1.4 | `source.setGeoJson()` au lieu de recréer | Perf | MapScreen.kt:495 | 4h |
| 1.5 | Supprimer paramètres Helmert non officiels | Exactitude | Lambert93Converter.kt:55 | 1h |
| 1.6 | OkHttp Cache 50MB + interceptor | Conformité | network/ | 4h |
| 1.7 | Retry backoff tuiles (3 tentatives) | Resilience | OfflineTileManager.kt | 3h |
| 1.8 | Téléchargement parallèle (Semaphore 8) | Perf | OfflineTileManager.kt | 4h |
| 1.9 | Lissage TreeNavigator + TYPE_ROTATION_VECTOR | Précision | TreeNavigator.kt | 3h |
| 1.10 | Exposer accuracy sensor + indicateur visuel | UX | CompassManager, TreeNavigator | 4h |

### Phase 2 — Majeurs (~8 jours)

| # | Action | Domaine | Effort |
|---|--------|---------|--------|
| 2.1 | Extraire MapViewModel + MapUiState | Architecture | 3j |
| 2.2 | DisposableEffect listeners MapLibre | Fuites mémoire | 1j |
| 2.3 | Découper MapScreen en 10 sous-composables | Maintenabilité | 2j |
| 2.4 | GnssMonitor (GnssStatus + NMEA + DOP) | GPS qualité | 2j |
| 2.5 | Foreground service GpsParcelTracer | Robustesse | 1j |
| 2.6 | Mode FOREST adaptatif GPS | Précision forêt | 1j |
| 2.7 | Déclinaison magnétique WMM locale | Précision compas | 2j |
| 2.8 | Ajouter 10 sources carto (IGN + libres) | Options | 1j |
| 2.9 | IGN RGE Alti API (précision 1m) | Élévation | 2j |
| 2.10 | Clé API IGN (BuildConfig) | Conformité | 1j |
| 2.11 | Reprise checkpoint tuiles offline | Resilience | 1j |
| 2.12 | Estimation taille/temps + dialogue confirmation | UX | 1j |
| 2.13 | Tests points contrôle IGN (tolérance 0.1m) | Exactitude | 1j |
| 2.14 | Intégrer JTS + Proj4J | Formats géo | 3j |
| 2.15 | Tests parsers core (Shapefile, GeoImport) | Robustesse | 3j |

### Phase 3 — Améliorations UX/visuel (~5 jours)

| # | Action | Domaine | Effort |
|---|--------|---------|--------|
| 3.1 | Tailles tactiles 48dp + mode terrain (gants) | Accessibilité | 4h |
| 3.2 | États vide/erreur/chargement carte | UX | 4h |
| 3.3 | Dark mode adaptatif (utiliser isDark) | Visuel | 4h |
| 3.4 | Highlight au tap arbre | UX | 4h |
| 3.5 | Scale bar native MapLibre | Visuel | 1h |
| 3.6 | Animation flèche navigation (lerp) | UX | 2h |
| 3.7 | Transitions style (fade 300ms) | Visuel | 1h |
| 3.8 | Labels cluster adaptatifs zoom | Visuel | 2h |
| 3.9 | UI calibration compas (figure 8) | UX | 2j |
| 3.10 | Mode guidage navigation (instructions + haptique) | UX | 2j |
| 3.11 | Bouton "Suivre ma position" (CameraMode.TRACKING) | UX | 2h |
| 3.12 | UI config couches (opacity, maxZoom, ordre, preview) | Options | 2j |
| 3.13 | Export GeoPackage + projet QGIS | Formats | 3j |
| 3.14 | Preview import (carte + stats) | UX | 3j |
| 3.15 | Couche RGE ALTI + hillshade dans carte | Visuel | 1j |

### Phase 4 — Optimisations avancées (~3 jours)

| # | Action | Domaine | Effort |
|---|--------|---------|--------|
| 4.1 | `derivedStateOf` + debounce GPS updates | Perf | 4h |
| 4.2 | Cache LRU GeoJSON shapefile | Perf | 4h |
| 4.3 | MBTiles support (SQLite) | Standard | 2j |
| 4.4 | LRU eviction tuiles + limite 500MB | Stockage | 1j |
| 4.5 | Glyphs offline embarqués | 100% offline | 1j |
| 4.6 | Interpolation bilinear élévation | Précision | 1j |
| 4.7 | Remplissage voids SRTM | Qualité | 1j |
| 4.8 | CC42-CC50 projections | Cadastre | 1j |
| 4.9 | Streaming gros fichiers géo (buffer 8KB) | Robustesse | 2j |
| 4.10 | Rate limiter OpenTopoData | Conformité | 4h |

---

## 4. PRIORISATION RECOMMANDÉE

**Séquence d'implémentation suggérée** (impact décroissant, risque croissant) :

1. **Sprint 1 (jours 1-2)** : Conformité légale + robustesse basique
   - Attribution style JSON, User-Agent OSM, catch vides → Log.e, OkHttp Cache, retry tuiles
   - *Impact : conformité légale + debuggabilité, faible risque*

2. **Sprint 2 (jours 3-4)** : Performance carte + précision compas
   - `setGeoJson()`, parallélisation tuiles, lissage TreeNavigator, TYPE_ROTATION_VECTOR, accuracy sensor
   - *Impact : perf visible + précision compas, risque modéré*

3. **Sprint 3 (jours 5-7)** : Exactitude + sources
   - Supprimer Helmert faux, points contrôle IGN, 10 sources carto, clé API IGN, IGN RGE Alti
   - *Impact : exactitude + options, risque modéré*

4. **Sprint 4 (jours 8-12)** : Architecture + GPS avancé
   - MapViewModel, DisposableEffect listeners, GnssMonitor, foreground service, mode FOREST, WMM déclinaison
   - *Impact : testabilité + robustesse GPS, risque élevé (refactor)*

5. **Sprint 5 (jours 13-18)** : UX/visuel + formats
   - 48dp, états vides/erreur, dark mode, highlight tap, scale bar, calibration, guidage, UI couches, GeoPackage, JTS/Proj4J, tests
   - *Impact : UX pro + formats, risque modéré*

**Effort total estimé** : ~18 jours de développement (cohérent avec les 40-60h GPS + 10-15j compas + autres domaines).

---

## 5. SOURCES CARTO À AJOUTER (URLs exactes vérifiées)

### IGN GéoPortail (gratuit, data.geopf.fr/wmts)

```
ORTHOIMAGERY.ORTHOPHOTOS                                    → Orthophoto
LIMITES_ADMINISTRATIVES_EXPRESS.LATEST                      → Limites admin
OCSGE.COUVERTURE.2021-2023                                  → Occupation sol
OCSGE.USAGE.2021-2023                                       → Usage sol
ELEVATION.ELEVATIONGRIDCOVERAGE.HIGHRES.MNS                 → MNS surface
ELEVATION.ELEVATIONGRIDCOVERAGE.HIGHRES.MNS.SHADOW          → Hillshade MNS
GEOGRAPHICALGRIDSYSTEMS.MAPS.SCAN-EXPRESS.STANDARD          → SCAN Express
```

Template URL : `https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&STYLE=normal&FORMAT=image/png&TILEMATRIXSET=PM&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&LAYER=<LAYER>`

### Sources libres OSM-écosystème

```
OpenTopoMap    : https://tile.opentopomap.org/{z}/{x}/{y}.png
CyclOSM        : https://a.tile-cyclosm.openstreetmap.fr/cyclosm/{z}/{x}/{y}.png
HOT OSM        : https://a.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png
CartoDB Positron : https://a.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png
CartoDB Dark Matter : https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png
Stamen Toner   : https://tiles.stadiamaps.com/tiles/stamen_toner/{z}/{x}/{y}{r}.png
```

---

## 6. PARAMÈTRES OFFICIELS IGN (pour corrections)

### Lambert 93 (EPSG:2154) — ✅ déjà correct
```
Ellipsoïde GRS80 : a=6378137, 1/f=298.257222101
φ0=46.5°, λ0=3°E, φ1=49°, φ2=44°, X0=700000, Y0=6600000
```

### CC42-CC50 (à implémenter)
```
Tous : λ0=3°E, X0=1700000
CC42 : φ0=42°, φ1=41.25°, φ2=42.75°, Y0=1200000
CC43 : φ0=43°, φ1=42.25°, φ2=43.75°, Y0=2200000
... (incrément +1000000 par zone)
CC50 : φ0=50°, φ1=49.25°, φ2=50.75°, Y0=9200000
```

### Points contrôle IGN (pour tests, tolérance 0.1m)
```
Paris Tour Eiffel    : lon=2.2945,  lat=48.8584 → E≈652834,  N≈6858180
Strasbourg Cathédrale: lon=7.7521,  lat=48.5734 → E≈1028422, N≈6836633
Bourse Bordeaux      : lon=-0.5792, lat=44.8378 → E≈381453,  N≈6497215
Nice Promenade       : lon=7.2620,  lat=43.7102 → E≈936238,  N≈6258424
Brest Plougastel     : lon=-4.4861, lat=48.3904 → E≈168893,  N≈6815234
```

---

## 7. CONCLUSION

Le périmètre carte/GPS de GeoSylva est **fonctionnel mais immature pour un usage forestier professionnel**. Les bases sont solides (MapLibre, FusedLocation, SQLCipher, averaging GPS, clustering), mais :

- **Conformité légale** : attributions + User-Agent + clé IGN manquants (bloquant)
- **Robustesse** : fuites mémoire listeners, catch vides, pas de retry/cache HTTP
- **Précision** : pas de déclinaison magnétique, pas de GnssStatus, Helmert faux, pas d'IGN RGE Alti
- **Performance** : tuiles séquentielles, GeoJSON recréé, recompositions excessives
- **Architecture** : MapScreen monolithe 3105 lignes, 0 ViewModel
- **Options** : sources manquantes, pas d'UI config couches, pas de calibration/guidage

**Les 5 actions à démarrer en priorité** :
1. Attribution légale + User-Agent OSM (conformité, 2h)
2. `setGeoJson()` + parallélisation tuiles (perf, 8h)
3. OkHttp Cache + retry (conformité + resilience, 7h)
4. Supprimer Helmert faux + points contrôle IGN (exactitude, 5h)
5. Lissage TreeNavigator + accuracy sensor (précision compas, 7h)

Ces 5 actions (~30h) couvrent les critiques transverses avec un rapport impact/risque maximal.

---

## Historique

| Date | Action |
|------|--------|
| 2026-06-30 | Audit complet 10 sous-agents + synthèse (cette note) |
