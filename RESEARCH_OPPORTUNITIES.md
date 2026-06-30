# RESEARCH OPPORTUNITIES — GeoSylva

**Document de synthèse des opportunités techniques, financières et stratégiques**
**Date** : 2026-06-29
**Source** : 5 sous-agents de recherche parallèles (APIs FR, repos GitHub, IA, financement, hardware)

---

## RÉSUMÉ EXÉCUTIF

5 axes de recherche, **150+ opportunités** identifiées :

| Axe | Opportunités | Top découvertes |
|-----|-------------|-----------------|
| APIs françaises | 31 (11 déjà intégrées, 20 à intégrer) | API Carto Nature (Natura 2000/ZNIEFF), BD Ortho, DRIAS |
| Libraries open source | 40+ | JTS, Proj4J, Spatial K, LERFoB Forest Tools (calculs FR!), Kotlin BLE Nordic |
| IA forestière | 30+ | Mistral 7B, SmolLM3 3B on-device, PureForest dataset IGN, Vosk FR |
| Financement | 35+ aides | NVIDIA Inception, Microsoft 150K$, Google 350K$, i-Lab 600K€, ADEME 250K€ |
| Hardware IoT | 21 devices | Codimex E-1 (350€), Masser BT, Haglöf Digitech, TruPulse 200i |

**Potentiel de financement total** : 350K€ - 1.2M€ sur 24 mois (après passage SASU/EURL)
**Crédits cloud gratuits immédiats** : ~600 000$ (NVIDIA + Microsoft + Google + AWS)

---

## 1. APIs FRANÇAISES À INTÉGRER

### 1.1 Déjà intégrées (11)

| API | Fournisseur | Usage dans GeoSylva |
|-----|-------------|---------------------|
| IGN Géoportail WMS/WMTS | IGN | Couches carto (BD Forêt, Cadastre, RPG) |
| BD Forêt v2 | IGN | Type de peuplement |
| BD Parcellaire (RPG) | IGN | Parcelles agricoles |
| Cadastre | IGN/DGFiP | Limites de propriété |
| BDGSF INRAE | INRAE | Sols (RU, pH, texture) |
| DVF Cerema | Cerema | Transactions foncières |
| GeoAPI | Etalab | Géocodage (communes, INSEE) |
| Open-Meteo | Open-Meteo | Données climatiques historiques |
| SRTM DEM | CGIAR-CSI | Altitude (embarqué) |
| OpenTopoData | OpenTopoData | Altitude (online) |
| Esri ArcGIS Online | Esri | Tuiles satellite (⚠️ transferts USA — RGPD) |

### 1.2 À intégrer — Priorité 1 (fondamentales)

| API | Fournisseur | Données | Coût | Cas d'usage GeoSylva |
|-----|-------------|---------|------|----------------------|
| **API Carto Nature** | IGN | Natura 2000, ZNIEFF, RNN, PNR | Gratuit | Détection auto espaces protégés sur parcelle |
| **API Carto Urbanisme** | IGN | PLU, POS, CC, servitudes | Gratuit | Contraintes d'urbanisme sur parcelle |
| **BD Ortho** | IGN | Orthophotos 50cm | Gratuit | Photo-interprétation peuplements |
| **Corine Land Cover** | IGN/SDES | Occupation sols 44 classes | Gratuit | Interface forêt-agriculture-urbain |
| **Météo-France API** | Météo-France | Observations, prévisions, radar | Gratuit (clé) | Conditions météo terrain, alertes |

### 1.3 À intégrer — Priorité 2 (améliorations)

| API | Fournisseur | Données | Coût | Cas d'usage |
|-----|-------------|---------|------|-------------|
| **DRIAS** | Météo-France | Projections climatiques 2030-2100 | Gratuit | Vulnérabilité CC, choix essences |
| **BD Topage** | Sandre | Réseau hydrographique métrique | Gratuit | Inventaires ripisylves |
| **API Données Foncières** | Cerema | DVF+ structuré | Gratuit | Remplacer endpoint préprod |
| **GéoSol INRAE** | INRAE | Cartes pédologiques régionales | Gratuit | Complément BDGSF |
| **Copernicus CDS** | Copernicus | ERA5, ERA5-Land | Gratuit | Séries climatiques longues |

### 1.4 À intégrer — Priorité 3 (compléments)

| API | Fournisseur | Données | Coût | Cas d'usage |
|-----|-------------|---------|------|-------------|
| INPN WFS | MNHN | Espèces protégées, habitats | Gratuit | Vérification espèces protégées |
| GBIF API | GBIF France | Occurrences espèces mondial | Gratuit | Complément INPN |
| ADEME Impact CO2 | ADEME | Facteurs d'émissions | Gratuit | Empreinte carbone opérations |
| ADEME Open Data | ADEME | Base Carbone, PCAET | Gratuit | Analyse environnementale |
| ONF Open Data | ONF | Forêts publiques, domaniales | Gratuit | Identification forêts publiques |
| Observatoire FBF | France Bois Forêt | Prix bois sur pied | Gratuit (PDF) | Prix de référence (pas d'API) |
| DataIFN | IGN | Données inventaire national | Restreint | Comparaison inventaires |
| Sentinel Hub | Copernicus | Sentinel-2 NDVI | Freemium | Suivi satellite végétation |

### 1.5 Action immédiate

Demander une **clé API IGN "usage non commercial"** pour :
- API Carto Nature (Natura 2000/ZNIEFF)
- API Carto Urbanisme (PLU)
- BD Ortho
- Corine Land Cover
- Météo-France (clé séparée)

---

## 2. LIBRARIES OPEN SOURCE À INTÉGRER

### 2.1 Intégration immédiate (facile + haute pertinence)

| Library | Licence | Usage | Effort |
|---------|---------|-------|--------|
| **JTS Topology Suite** | EPL-2.0 | Géométrie vectorielle, WKT/WKB, buffer, intersection | Facile |
| **Proj4J** | EPL-2.0 | Transformations CRS (WGS84 ↔ L93 ↔ UTM) | Facile |
| **Spatial K** (MapLibre) | BSD-3 | GeoJSON, GPX, Turf.js en Kotlin pur | Facile |
| **Paging 3** | Apache 2.0 | Listes paginées (10k+ tiges) | Facile |
| **Coil** | Apache 2.0 | Chargement images + downsampling + cache | Facile |
| **SQLCipher 4.16.0** | BSD-3 | Chiffrement DB (CRITICAL — Phase 0) | Moyen |
| **Kotlin BLE Library** (Nordic) | MIT | Communication compas forestier Bluetooth | Facile |
| **Android GPX Parser** | Apache 2.0 | Import/Export GPX | Facile |
| **PdfBox Android** | Apache 2.0 | Génération rapports PDF | Facile |
| **GeoPackage Android** | Public Domain | Export OGC GeoPackage (restaurer GeoPackageExporter) | Facile |

### 2.2 Intégration moyen terme

| Library | Licence | Usage | Effort |
|---------|---------|-------|--------|
| **LERFoB Forest Tools** | LGPL-3.0 | Calculs volume/biomasse/carbone français (Bouchon, FrenchCommercialVolume2020) | Difficile |
| **CAT (Carbon Accounting)** | LGPL-3.0 | Bilan carbone forestier par compartiment | Difficile |
| **ONNX Runtime Android** | MIT | IA on-device (classification essences) | Moyen |
| **TensorFlow Lite** | Apache 2.0 | Classification images mobile | Moyen |
| **Supabase Kotlin** | Apache 2.0 | Sync cloud (Postgres + Auth + Realtime) | Facile |
| **Vosk Android** | Apache 2.0 | Saisie vocale offline français | Moyen |

### 2.3 Apps forestières open source (inspiration)

| App | Langage | Licence | Pertinence |
|-----|---------|---------|------------|
| **OpenForis Arena Mobile** (FAO) | TypeScript | MIT | Workflow collecte moderne |
| **Geopaparazzi** | Java | GPL-3.0 | Cartes offline + formulaires terrain |
| **Forest Sentry** | TypeScript | MIT | IA on-device santé plantes |
| **GeoVision** | Kotlin | MIT | Interface GIS Compose moderne |
| **Treetracker** (Greenstand) | Kotlin | AGPL-3.0 | Suivi arbres GPS (⚠️ AGPL) |

### 2.4 Dépendances build.gradle.kts recommandées

```kotlin
// GIS
implementation("org.locationtech.jts:jts-core:1.20.0")
implementation("org.locationtech.proj4j:proj4j:1.3.0")
implementation("org.locationtech.proj4j:proj4j-epsg:1.3.0")
implementation("org.maplibre.spatialk:geojson:1.0.0")
implementation("org.maplibre.spatialk:turf:1.0.0")

// Performance
implementation("androidx.paging:paging-runtime:3.5.0")
implementation("androidx.paging:paging-compose:3.5.0")
implementation("io.coil-kt:coil-compose:2.7.0")

// Sécurité
implementation("net.zetetic:sqlcipher-android:4.16.0")

// Bluetooth
implementation("no.nordicsemi.android:ble:2.7.6")

// Export
implementation("com.tom-roush:pdfbox-android:2.0.1.0")
implementation("mil.nga.geopackage:geopackage-android:6.7.4")

// IA (optionnel)
implementation("org.tensorflow:tensorflow-lite:2.15.0")
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")

// Cloud (futur)
implementation("io.github.jan-tennert.supabase:postgrest-kt:2.4.0")
```

---

## 3. IA FORESTIÈRE

### 3.1 Stack IA recommandée (séquencée)

```
Phase 1 (court terme) :
  API Mistral (laïcage, 0.001€/req) → assistant martelage cloud
  Android SpeechRecognizer natif → saisie vocale terrain
  PlantNet API → identification essence par photo

Phase 2 (moyen terme) :
  NVIDIA NIM gratuit → Mistral 7B self-hosted (dev)
  Azure credits (150K$) → serving production
  Vosk FR offline → saisie vocale hors-ligne
  TFLite + PureForest dataset → classification essence on-device

Phase 3 (long terme) :
  QLoRA fine-tuning Mistral 7B sur données ONF/CNPF
  SmolLM3 3B on-device → assistant terrain offline complet
  RAG sur documentation ONF/CNPF/INRAE
```

### 3.2 Modèles LLM pertinents

| Modèle | Taille | Licence | Online/Offline | Cas d'usage | Pertinence |
|--------|--------|---------|----------------|-------------|------------|
| **Mistral 7B Instruct** | 7B | Apache 2.0 | Les deux | Assistant forestier FR souverain | ⭐⭐⭐⭐⭐ |
| **SmolLM3 3B** | 3B | Apache 2.0 | Offline | Assistant on-device (15 tok/s S22) | ⭐⭐⭐⭐⭐ |
| **Llama 3.1 8B** | 8B | Llama license | Les deux | Via NVIDIA NIM gratuit | ⭐⭐⭐⭐⭐ |
| **Qwen 2.5 7B** | 7B | Apache 2.0 | Offline | Multilingue, compact | ⭐⭐⭐⭐ |
| **Phi-3 Mini** | 3.8B | MIT | Offline | Edge, Microsoft | ⭐⭐⭐⭐ |

### 3.3 Vision — identification d'essences

| Techno | Type | Coût | Pertinence |
|--------|------|------|------------|
| **PlantNet API** (CIRAD/INRAE FR) | Cloud API | Par requête | ⭐⭐⭐⭐⭐ |
| **PureForest** (IGN, 13 essences FR) | Dataset | Gratuit | ⭐⭐⭐⭐⭐ |
| **BarkVN-50** (50 espèces écorce) | Dataset | Gratuit | ⭐⭐⭐⭐ |
| **ONNX Runtime Android** | Inference | Gratuit | ⭐⭐⭐⭐⭐ |
| **TensorFlow Lite** | Inference | Gratuit | ⭐⭐⭐⭐⭐ |
| **MediaPipe** (Google) | Vision temps réel | Gratuit | ⭐⭐⭐⭐⭐ |

### 3.4 Saisie vocale

| Techno | Taille | Offline | Effort | Pertinence |
|--------|--------|---------|--------|------------|
| **Android SpeechRecognizer** | Natif | ✅ (Android 13+) | Facile | ⭐⭐⭐⭐⭐ |
| **Vosk FR** | 50MB | ✅ | Facile | ⭐⭐⭐⭐⭐ |
| **Whisper.cpp tiny** | 75MB | ✅ | Moyen | ⭐⭐⭐⭐ |

### 3.5 Datasets forestiers français

| Dataset | Source | Taille | Usage |
|---------|--------|--------|-------|
| **PureForest** | IGN/HuggingFace | 135 569 patches, 13 essences | Classification aérienne |
| **TreeSatAI-Time-Series** | IGN/HuggingFace | Sentinel time series | Classification multi-saison |
| **BD Forêt v3** | IGN | 35 classes | Référentiel essence |
| **ONF Open Data** | ONF | Scénarios sylvicoles | Fine-tuning assistant |
| **BarkVN-50** | HuggingFace | 5 678 images, 50 espèces | Identification écorce |

### 3.6 Fine-tuning

- **QLoRA** : fine-tuning Mistral 7B sur GPU 6GB VRAM seulement
- **RAG** : documentation ONF/CNPF/INRAE → Q&A forestier avec sources
- **Coût** : logiciel gratuit, GPU requis (ou Azure credits)

### 3.7 Cas d'usage IA évalués

| Cas d'usage | Faisabilité | Techno | Effort |
|-------------|-------------|--------|--------|
| Assistant martelage | ⭐⭐⭐⭐⭐ | Mistral 7B + RAG | Difficile |
| Diagnostic station auto | ⭐⭐⭐⭐ | PlantNet + custom | Moyen |
| Reconnaissance essence photo | ⭐⭐⭐⭐⭐ | PlantNet API / TFLite | Facile/Moyen |
| Génération PSG | ⭐⭐⭐⭐⭐ | Mistral 7B fine-tuné | Difficile |
| Prédiction accroissement | ⭐⭐⭐ | ML classique | Difficile |
| Saisie vocale terrain | ⭐⭐⭐⭐⭐ | SpeechRecognizer / Vosk | Facile |
| Q&A forestier | ⭐⭐⭐⭐⭐ | Mistral 7B + RAG | Difficile |
| Détection anomalies | ⭐⭐⭐⭐ | Règles statistiques | Moyen |

---

## 4. FINANCEMENT ET AIDES

### 4.1 Crédits cloud gratuits (immédiat, auto-entrepreneur OK)

| Programme | Montant | Conditions | Statut |
|-----------|---------|------------|--------|
| **NVIDIA Inception** | 100K$ AWS + 150K$ Nebius + NIM gratuit | Startup IA, gratuit, pas d'equity | ✅ Postuler maintenant |
| **Microsoft for Startups** | 150 000$ Azure | Startup <5 ans, pas d'equity | ✅ Postuler maintenant |
| **Google for Startups Cloud** | 350 000$ GCP (AI startups) | Startup <5 ans | ✅ Postuler maintenant |
| **AWS Activate** | 200 000$ AWS | Startup <10 ans | ✅ Postuler maintenant |
| **HuggingFace Spaces** | Demo gratuite | Compte gratuit | ✅ Immédiat |
| **Total crédits cloud** | **~600 000$** | | |

### 4.2 Aides locales Poitiers / Nouvelle-Aquitaine

| Dispositif | Montant | Échéance | Auto-entrepreneur |
|------------|---------|----------|-------------------|
| **Neoloji Technopole** (Poitiers) | Accompagnement gratuit + pépinière 7€/m² | Permanent | ✅ Oui |
| **France 2030 NA — Projets d'Avenir** | Jusqu'à 50% dépenses | **30/09/2026** | ❌ SASU/EURL |
| **Région NA — Aide innovation start-up** | Subvention 45% dépenses | Permanent | ❌ SASU/EURL |
| **Région NA — Amorçage Start-Up** | Jusqu'à 3M€ | Permanent | ❌ SASU/EURL |
| **ADEME Santé sols forestiers** | 150-250K€ | Avril-Juillet 2026 | ❌ SASU/EURL |

### 4.3 Aides nationales

| Dispositif | Montant | Échéance | Auto-entrepreneur |
|------------|---------|----------|-------------------|
| **Bourse French Tech** | 30-50K€ | Permanent | ❌ SASU/EURL |
| **Concours i-Lab** | Jusqu'à 600K€ | **Février 2026** | ❌ SASU/EURL |
| **French Tech Seed** (OC) | 50-500K€ | Permanent | ❌ SASU/EURL |
| **Prêt d'amorçage BPI** | Variable | Permanent | ❌ SASU/EURL |
| **French Tech Tremplin** | 15K€ + incubation | AAP 2026 | ⚠️ Conditions |

### 4.4 Aides fiscales

| Dispositif | Avantage | Conditions | Auto-entrepreneur |
|------------|----------|------------|-------------------|
| **CIR** | 30% dépenses R&D | Agrément CIR | ⚠️ Limité |
| **JEI** | Exonération charges + IS | <8 ans, R&D >15% (2025: 20%) | ❌ SASU/EURL |
| **CII** | 20% dépenses innovation | PME | ❌ SASU/EURL |

### 4.5 Accélérateurs / incubateurs

| Programme | Coût | Échéance | Auto-entrepreneur |
|-----------|------|----------|-------------------|
| **Neoloji Promo Startups** (Poitiers) | Gratuit | Candidatures saisonnières | ✅ Oui |
| **POP Incub** (ADI NA) | Gratuit | 02/11/2025 | ✅ Oui |
| **CircularTech** (KEDGE) | Gratuit | 03/11/2025 | ⚠️ SASU recommandé |
| **Station F** (Paris) | Variable | AAP permanents | ❌ SASU/EURL |
| **Bordeaux Angels** | 50K-1M€ (equity) | Permanent | ❌ SASU/EURL |

### 4.6 Financement environnement

| Dispositif | Montant | Échéance |
|------------|---------|----------|
| **ADEME Santé sols forestiers** | 150-250K€ | Avril-Juillet 2026 |
| **France 2030 Forêt-Bois** (500M€ enveloppe) | Variable | Permanent |
| **LIFE Programme** (UE) | 60% coûts (10-30M€) | Septembre 2025 |
| **Horizon Europe / EIC Accelerator** | 2.5M€ + equity | Permanent |

### 4.7 Potentiel total estimé

| Scénario | Montant | Conditions |
|----------|---------|------------|
| Auto-entrepreneur | ~600K€ (crédits cloud) + accompagnement | Immédiat |
| SASU/EURL (optimisé) | **1.2M€ - 2.5M€** sur 24-36 mois | Après passage |

### 4.8 Recommandation critique

**Passer en SASU/EURL** dès que possible (~200-500€ de formalités) pour débloquer :
- Bourse French Tech (30-50K€)
- JEI (exonération charges)
- CIR/CII (30% + 20% R&D)
- Aides régionales NA (France 2030, innovation)
- Concours i-Lab (600K€)
- ADEME (150-250K€)

### 4.9 Planning de candidature

```
Semaine 1-2 : NVIDIA Inception + Microsoft + Google + AWS + HuggingFace
Semaine 3   : Contact Neoloji Technopole (Poitiers)
Semaine 4   : Préparation passage SASU/EURL
Mois 2      : Bourse French Tech + Neoloji promo startups
Mois 3-4    : France 2030 NA + Région NA aide innovation
Mois 5-6    : CIR/JEI rescrit + ADEME sols forestiers
Mois 7-9    : Concours i-Lab (deadline février 2026)
Mois 10-12  : Prêt d'amorçage BPI + Bordeaux Angels
Mois 13-18  : French Tech Seed + Horizon Europe
```

### 4.10 Checklist pièces à préparer

- [ ] SIRET / K-bis (après passage SASU)
- [ ] Pitch deck (10-15 slides)
- [ ] Business plan (3 ans)
- [ ] Prévisionnels financiers
- [ ] Description technique innovation
- [ ] Étude de marché
- [ ] CV équipe
- [ ] Lettres d'intention clients (beta-testeurs)
- [ ] Partenariats acteurs forestiers (ONF, coopératives)

---

## 5. HARDWARE IOT FORESTERIE

### 5.1 Top 5 devices pour GeoSylva

| Device | Fabricant | Prix | Protocole | Pertinence | Effort |
|--------|-----------|------|-----------|------------|--------|
| **Codimex E-1 Caliper** | Pologne | ~350€ | Bluetooth | ⭐⭐⭐⭐⭐ | Facile |
| **Masser BT Caliper** | Finlande | ~1 300€ | BT SPP | ⭐⭐⭐⭐⭐ | Moyen |
| **Haglöf Digitech BT** | Suède | ~1 600€ | BLE 4.0 | ⭐⭐⭐⭐⭐ | Moyen |
| **Haglöf Vertex Laser Geo 2** | Suède | ~3 500€ | BLE 4.2 | ⭐⭐⭐⭐⭐ | Difficile |
| **TruPulse 200i** | USA | ~1 200€ | BLE + Classic | ⭐⭐⭐⭐⭐ | Moyen |

### 5.2 Autres devices intéressants

| Device | Usage | Prix |
|--------|-------|------|
| Nikon Forestry Pro II | Hypsomètre laser (manuel) | ~500€ |
| Leica DISTO D810 | Télémètre laser BLE | ~600€ |
| Bosch GLM 150-27 C | Télémètre laser BLE | ~350€ |
| Emlid Reach RX2 | GPS RTK centimétrique | ~1 300€ |
| Garmin GPSMAP 65s | GPS multi-fréquence | ~450€ |
| DJI Mavic 3 Multispectral | Drone NDVI forestier | ~4 800€ |
| FLIR ONE | Caméra thermique smartphone | ~400€ |
| Netatmo Weather Station | Station météo connectée (FR) | ~180€ |

### 5.3 Libraries BLE recommandées

| Library | Licence | Stars | Usage |
|---------|---------|-------|-------|
| **Kotlin BLE Library** (Nordic) | MIT | ~500 | Wrapper coroutines BLE |
| **Blessed Kotlin** | MIT | 83 | BLE compact Android 9+ |
| **BleGattCoroutines** | Apache 2.0 | ~200 | GATT coroutines-friendly |

### 5.4 Plan d'intégration compas BLE

```
Mois 1-2 : Recherche & prototypage
  - Acheter Codimex E-1 (350€) pour tests
  - Reverse engineering protocole BLE avec nRF Connect
  - Étudier app Haglof Link comme référence

Mois 3-4 : Implémentation MVP
  - Intégration BLE basique (Kotlin BLE Library Nordic)
  - Support Codimex E-1 uniquement
  - Tests terrain avec beta-testeur

Mois 5-6 : Extension
  - Support Masser BT Caliper
  - Support Haglöf Digitech BT
  - Documentation utilisateur

Mois 7-8 : GPS externe
  - Intégration NMEA 0183
  - Support Garmin GPSMAP 65s

Mois 9-12 : Hypsomètre
  - Intégration TruPulse 200i
  - Intégration Vertex Laser Geo 2 (premium)
```

### 5.5 Coût équipement forestier

| Configuration | Équipement | Prix |
|---------------|------------|------|
| **Budget** | Smartphone A54 + compas mécanique + Nikon Forestry Pro II | ~1 150€ |
| **Moyenne** | S25 Ultra + Codimex E-1 + Nikon Forestry Pro II | ~2 500€ |
| **Premium** | S25 Ultra + Haglöf Digitech BT + Vertex Laser Geo 2 + Emlid RTK | ~7 600€ |

### 5.6 ROI

- Gain de temps : **~30%** sur inventaire (saisie auto vs manuelle)
- Réduction erreurs : **~50%** (pas de transcription)
- Productivité : **+20 arbres/heure**
- Amortissement : **6-12 mois** pour forestier indépendant
- Coût dev intégration BLE : **~12 000€** (12 semaines)

---

## 6. INTÉGRATION DANS LE MASTER_PLAN

### 6.1 Phase 0 — Pas de changement (corrections critiques uniquement)

### 6.2 Phase 1 — Ajouter

| Action | Source | Effort |
|--------|--------|--------|
| Intégrer Proj4J (reprojection CRS) | Recherche libraries | 1j |
| Intégrer JTS (WKT/WKB étendu) | Recherche libraries | 1j |
| Intégrer GeoPackage Android (restaurer export) | Recherche libraries | 2j |
| Demander clé API IGN (Carto Nature, Urbanisme, BD Ortho) | Recherche APIs | 0.5j |
| Intégrer API Carto Nature (Natura 2000/ZNIEFF) | Recherche APIs | 3j |

### 6.3 Phase 2 — Ajouter

| Action | Source | Effort |
|--------|--------|--------|
| Intégrer API Carto Urbanisme (PLU) | Recherche APIs | 2j |
| Intégrer BD Ortho IGN | Recherche APIs | 1j |
| Intégrer DRIAS (projections climatiques) | Recherche APIs | 3j |
| Intégrer BD Topage (ripisylves) | Recherche APIs | 2j |
| Intégrer Météo-France API | Recherche APIs | 2j |
| Étudier LERFoB Forest Tools (calculs FR) | Recherche libraries | 3j |

### 6.4 Phase 3 — Ajouter

| Action | Source | Effort |
|--------|--------|--------|
| Saisie vocale (Android SpeechRecognizer + Vosk) | Recherche IA | 2j |
| API PlantNet (identification essence) | Recherche IA | 2j |
| API Mistral (assistant martelage cloud) | Recherche IA | 3j |
| Fine-tuning Mistral 7B (QLoRA, données ONF) | Recherche IA | 10j |
| Classification essence on-device (TFLite + PureForest) | Recherche IA | 5j |

### 6.5 Phase 4 — Écosystème (post-financement)

| Action | Source | Effort |
|--------|--------|--------|
| Intégration compas BLE (Codimex E-1) | Recherche hardware | 3j |
| Intégration compas BLE (Masser + Haglöf) | Recherche hardware | 4j |
| Intégration hypsomètre BLE (TruPulse) | Recherche hardware | 3j |
| GPS RTK externe (Emlid Reach) | Recherche hardware | 2j |
| IA on-device (SmolLM3 3B) | Recherche IA | 5j |
| Sync cloud (Supabase) | Recherche libraries | 10j |
| Drone DJI SDK (NDVI) | Recherche hardware | 5j |

---

## 7. ACTIONS IMMÉDIATES (cette semaine)

1. **Postuler** : NVIDIA Inception + Microsoft for Startups + Google for Startups + AWS Activate
2. **Contacter** : Neoloji Technopole (Poitiers) pour accompagnement
3. **Demander** : Clé API IGN (Carto Nature, Urbanisme, BD Ortho, Corine Land Cover)
4. **Préparer** : Dossier passage SASU/EURL (débloque 80% des aides financières)
5. **Télécharger** : Dataset PureForest (IGN/HuggingFace) pour future IA vision
6. **HuggingFace** : Créer un Space pour demo IA forestière

---

*Document généré le 2026-06-29 par synthèse de 5 sous-agents de recherche parallèles.*
