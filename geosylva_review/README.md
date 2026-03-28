# GeoSylva — Documentation complète du système Classification / Calcul / Corrélation
**Date de revue : 16 mars 2026**
**Version analysée : commit courant post-fixes (BUILD SUCCESSFUL)**

---

## TABLE DES MATIÈRES

1. [Architecture générale et flux de données](#1-architecture-générale-et-flux-de-données)
2. [Fichier : StandClassificationModels.kt](#2-standclassificationmodelskт)
3. [Fichier : StandClassificationEngine.kt](#3-standclassificationenginekt)
4. [Fichier : StandTypologyDatabase.kt](#4-standtypologydatabasekt)
5. [Fichier : SuperCorrelateurEngine.kt](#5-supercorrelateeurenginekt)
6. [Fichier : DRIASDatabase.kt](#6-driasdatabasekt)
7. [Fichier : BioClimaticRiskDatabase.kt](#7-bioclimaticriskdatabasekt)
8. [Fichier : StationDiagnosticEngine.kt](#8-stationdiagnosticenginekt)
9. [Fichier : SanityChecker.kt](#9-sanitycheckerkт)
10. [Fichier : MartelageScreen.kt](#10-martelagescreenkt)
11. [Fichier : MartelageSummaryCards.kt](#11-martelagesummarycardskт)
12. [Fichier : StandClassificationScreen.kt](#12-standclassificationscreenkt)
13. [Fichier : Export (PDF / CSV)](#13-export-pdf--csv)
14. [Tableau récapitulatif des bugs identifiés](#14-tableau-récapitulatif-des-bugs-identifiés)
15. [Fixes appliqués lors de cette session](#15-fixes-appliqués-lors-de-cette-session)

---

## 1. Architecture générale et flux de données

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         SAISIE TERRAIN                                  │
│   Tiges (D, H, essence, qualité, volume)  +  StationObservation         │
│   + RipisylveObservation  +  GPS (lat/lon/alt)                          │
└───────────────────────────┬─────────────────────────────────────────────┘
                            │
                            ▼
┌───────────────────────────────────────────────────────────────────────────┐
│  CALCUL STATISTIQUES  —  MartelageStats  (calculé dans MartelageScreen)   │
│  nTotal, nPerHa, gTotal, gPerHa, vTotal, vPerHa, dg, dm, cvDiam,         │
│  meanH, hLorey, dMin, dMax, classDistribution, perEssence,               │
│  sanityWarnings, biodiversity, harvestSim, fertilityResults              │
└──────────────────┬────────────────────────────────────────────────────────┘
                   │
        ┌──────────┴──────────┐
        │                     │
        ▼                     ▼
┌──────────────────┐  ┌─────────────────────────────────────────────────┐
│ StandClassifi-   │  │           SuperCorrelateurEngine                 │
│ cationEngine     │  │                                                  │
│                  │  │  Entrées : tiges + station + ripisylve + GPS     │
│ Entrée :         │  │                                                  │
│  MartelageStats  │  │  1. ClimateZone.detect(lat,lon,alt)              │
│  userAnswers     │  │  2. StationDiagnosticEngine.diagnose()           │
│                  │  │  3. RipisylveScorer.score()                      │
│ Sorties :        │  │  4. computeDendroScore()          [0–100]        │
│  TreatmentMode   │  │  5. computeStationScore()         [0–100]        │
│  AgeStructure    │  │  6. computeEssencesBilan()        bilan par ess  │
│  VertStruct.     │  │  7. computeRisquesClimatiques()   DRIAS+sanitaire│
│  Composition     │  │  8. computeRecommandationsFutur() essences 2050  │
│  DevStage        │  │  9. buildPlanAction()             actions priorisées│
│  DiamRatio CNPF  │  │  10. buildAlertesSanitaires()     BioClimaticDB  │
│  ManagementProg. │  │  11. computeResilienceScore()     score global   │
│  StandDiagnosis  │  │  12. DRIASDatabase.getProjection() proj. climate │
└──────────────────┘  └─────────────────────────────────────────────────┘
        │                             │
        ▼                             ▼
┌──────────────────┐  ┌─────────────────────────────────────────────────┐
│ StandClassifi-   │  │   CorrelateurResult                              │
│ cationScreen     │  │   • ResilienceScore (global, dendro, station,    │
│ (UI)             │  │     ripisylve, climAdaptation)                   │
│                  │  │   • List<RisqueClimatique>                       │
│                  │  │   • List<EssenceBilan>                           │
│                  │  │   • List<EssenceRecommandation>                  │
│                  │  │   • List<ActionSylvicole>                        │
│                  │  │   • List<AlerteSanitaire>                        │
│                  │  │   • DRIASProjection?                             │
└──────────────────┘  └─────────────────────────────────────────────────┘
```

---

## 2. StandClassificationModels.kt

**Chemin :** `domain/classification/stand/StandClassificationModels.kt`
**Rôle :** Définit TOUS les types de données utilisés par le système de classification.

### Enums principaux

#### `TreatmentMode` — Mode de traitement sylvicole (20 valeurs)
```
FUTAIE_REGULIERE         FR   — CV < 20 (feuillu ou résineux)
FUTAIE_REGULIERE_PAR_PARCELLES FRP — (non utilisé automatiquement)
FUTAIE_IRREGULIERE       FI   — CV 20–35
FUTAIE_IRREGULIERE_BOUQUETS FIB — CV 35–55
FUTAIE_JARDINEE          FJ   — CV ≥ 55
FUTAIE_JARDINEE_GROUPES  FJG  — (non attribué automatiquement ⚠)
FUTAIE_CONVERSION        FC   — (par règles cépées/réserves)
FUTAIE_ENRICHIE          FE   — (non attribué automatiquement ⚠)
FUTAIE_MELANGEE          FM   — (non attribué automatiquement ⚠)
TAILLIS_SIMPLE           TS   — userConfirmed + nHa 1000–5000
TAILLIS_DENSE            TD   — userConfirmed + nHa > 5000
TAILLIS_CLAIR            TC   — userConfirmed + nHa < 1000
TAILLIS_SOUS_COUVERT     TSC  — (non attribué automatiquement ⚠)
TAILLIS_CONVERSION       TCo  — cépées sans réserves
TAILLIS_VIEILLISSANT     TV   — (non attribué automatiquement ⚠)
TAILLIS_SOUS_FUTAIE      TSF  — cépées + réserves normales
TAILLIS_SOUS_FUTAIE_RICHE TSFR — cépées + réserves + G>15 + N<400
TAILLIS_SOUS_FUTAIE_PAUVRE TSFP — (non attribué automatiquement ⚠)
TAILLIS_SOUS_FUTAIE_CONVERSION TSFCo — (utilisé dans managementProgram)
INCONNU                  ?    — fallback
```
> ⚠️ **Bug** : FJG, FE, FM, TSC, TV, TSFP ne sont JAMAIS assignés automatiquement par `treatmentModeFromData()`. Ils ne peuvent apparaître que si on les saisit manuellement ailleurs.

#### `AgeStructure` — Structure d'âge (calculée depuis CV diamétrique)
```
EQUIENNE                 — CV < 15%
QUASI_EQUIENNE           — CV 15–25%
IRREGULIERE_SIMPLE       — CV 25–35%
IRREGULIERE_COMPLEXE     — CV 35–50% (ou >30% avec cépées)
IRREGULIERE_EQUILIBREE   — CV ≥ 50%  (jardinée)
```

#### `DevelopmentStage` — Stade de développement (depuis Dg)
```
SEMIS          0–2.5 cm
FOURRE         2.5–7.5 cm
GAULIS         7.5–17.5 cm
PERCHIS        17.5–22.5 cm
JEUNE_FUTAIE   22.5–32.5 cm
FUTAIE_ADULTE  32.5–52.5 cm
FUTAIE_MURE    52.5–72.5 cm
FUTAIE_SURANNEE >72.5 cm
```

#### `StandComposition` — Composition en essences
```
PUR_FEUILLU         — essence dominante ≥ 80%, feuillu
PUR_RESINEUX        — essence dominante ≥ 80%, résineux
MELANGE_PIED_A_PIED — mélange (toutes situations sans données GPS)
MELANGE_BOUQUETS    — (JAMAIS assigné automatiquement depuis le fix F4)
MELANGE_PARQUETS    — (jamais assigné)
MELANGE_ETAGES      — (jamais assigné)
MELANGE_MOSAIC      — (jamais assigné)
```

#### `DiameterCategoryRatio` — Triangle des structures CNPF
Calcul des % PB/BM/GB/TGB avec seuils différents feuillu/résineux :
```
          Feuillus           Résineux
PB      17.5 – 27.5 cm    17.5 – 27.5 cm
BM      27.5 – 47.5 cm    27.5 – 42.5 cm
GB      47.5 – 67.5 cm    42.5 – 62.5 cm
TGB     > 67.5 cm         > 62.5 cm
```

Méthode `cnpfStructureCode()` → codes 1–9 :
```
1 — PB dominants (PB > 50%, GB/TGB ≤ 5%)
2 — PB dominants + GB épars (PB > 50%, GB/TGB ≤ 20%)
3 — PB + BM (GB/TGB ≤ 20%, PB ≤ 50%)
4 — BM dominants (BM > 50%)
5 — PB + GB (20% < GB/TGB < 50%, BM ≤ 25%)
6 — Sans dominante (BM > 25%, PB ≥ 25%)
7 — BM + GB (PB < 25%)
8 — GB dominants (GB/TGB ≥ 50%, GB ≥ TGB)
9 — TGB dominants (GB/TGB ≥ 50%, TGB > GB)
```

#### `ManagementProgram` — Programme de gestion complet
```kotlin
data class ManagementProgram(
    val objectiveLabel: String,       // Description de l'objectif
    val targetDiamCm: IntRange?,      // NULL pour peuplements irréguliers (fix F1)
    val targetAgeAns: IntRange?,      // NULL pour peuplements irréguliers
    val targetNha: IntRange?,         // NULL pour peuplements irréguliers
    val qualityCible: String,         // Description qualité attendue
    val densityTrajectory: List<Pair<String, IntRange>>, // Toujours décroissante (fix F5)
    val interventions: List<Intervention>,
    val srgsRegion: SRGSRegion = SRGSRegion.NATIONAL,
    val srgsItineraire: String = "",
    val srgsNotes: List<String> = emptyList()
)
```

---

## 3. StandClassificationEngine.kt

**Chemin :** `domain/classification/stand/StandClassificationEngine.kt`
**Rôle :** Moteur principal de classification automatique du peuplement.

### Entrées
- `MartelageStats` : statistiques dendrométriques calculées
- `userAnswers: Map<String, Int>` : réponses aux questions interactives

### Questions interactives (id → options)
```
"has_cepees"       → 0=non, 1=quelques, 2=majorité  (déclenchée si N>1500 + Dg<20 + CV>20)
"has_reserves"     → 0=non, 1=quelques, 2=bonne qual (déclenchée si hasCepees)
"has_regeneration" → 0=non, 1=oui                   (déclenchée si Dg > 35)
"is_plantation"    → 0=non, 1=oui, 2=enrichissement  (déclenchée si CV<12 + ≤2 ess + Dm>15)
"eco_zone"         → 0=plaine, 1=montagne, 2=méd, 3=rip (TOUJOURS déclenchée)
```

### Pipeline `classify()` — étape par étape

**Étape 1 — Composition**
```kotlin
dominantPct = G% de l'essence la plus représentée
if (dominantPct >= 80 && résineux) → PUR_RESINEUX
if (dominantPct >= 80 && feuillu)  → PUR_FEUILLU
else                               → MELANGE_PIED_A_PIED  // fix F4: plus de BOUQUETS
```

**Étape 2 — Mode de traitement** (délégué à `StandTypologyDatabase.treatmentModeFromData()`)
```
Priority 1 : userConfirmedTaillis + hasReserves → TSF_RICHE ou TSF
Priority 2 : userConfirmedTaillis (sans réserves) → TAILLIS_DENSE/SIMPLE/CLAIR
Priority 3 : hasCepees + !hasReserves → TAILLIS_CONVERSION
Priority 4 : CV < 20  → FUTAIE_REGULIERE  ⚠ (feuillu=résineux, bug non corrigé)
Priority 5 : CV < 35  → FUTAIE_IRREGULIERE
Priority 6 : CV < 55  → FUTAIE_IRREGULIERE_BOUQUETS
Priority 7 : CV ≥ 55  → FUTAIE_JARDINEE
```

**Étape 3 — Triangle CNPF** `computeDiameterRatio()`
Seuils distincts feuillu/résineux (voir §2). Ne prend que les tiges précomptables (DHP ≥ 17.5 cm).

**Étape 4 — Stade** `DevelopmentStage.fromDg(dg)`

**Étape 5 — Structures** `ageStructureFromCvDiam()` + `verticalStructureFromHRatio()`

**Étape 6 — Perturbation** `detectDisturbance()`
```
dyingPct > 20% OU (deadPct > 15% + >5 arbres morts)  → DEPERISSANT
dyingPct > 8% OU erreurs sanity                        → STRESSE
H/D global > 1.2  ⚠ (toutes essences, feuillu inclus) → STRESSE  ← BUG à corriger
deadPct > 5%                                           → PERTURBE
else                                                   → SAIN
```
> ⚠️ **Bug ligne 284–285** : `stats.meanH / stats.dg > 1.2` n'est pas filtré sur résineux — peut classifier à tort un peuplement feuillu comme STRESSE.

**Étape 7 — Objectif suggéré** `suggestObjective()`
```
FJ/FJG                              → MULTIFONCTIONNEL
TAILLIS_SIMPLE/DENSE                → BOIS_ENERGIE
FUTAIE_SURANNEE                     → BIODIVERSITE
résineux + JEUNE_FUTAIE/ADULTE      → BOIS_OEUVRE
PUR_FEUILLU/MELANGE_PIED_A_PIED     → BOIS_OEUVRE  ⚠ même pour taillis mélangé
else                                → MULTIFONCTIONNEL
```

**Étape 8 — Programme de gestion** `managementProgram()` (voir §4)

**Étape 9 — Diagnostic textuel** `buildDiagnosis()` → `StandDiagnosis`

### Confiance (confidence)
Commence à 0.85. Chaque question non répondue réduit :
```
has_cepees        -0.12
has_reserves      -0.08
has_regeneration  -0.05
is_plantation     -0.08
eco_zone          -0.05
```
Minimum : 0.20

---

## 4. StandTypologyDatabase.kt

**Chemin :** `domain/classification/stand/StandTypologyDatabase.kt`
**Rôle :** Référentiel de toute la logique sylvicole : seuils, programmes, CNPF, SRGS, diagnostics.

### Fonctions clés

#### `treatmentModeFromData()` — Attribution du mode de traitement
Voir pipeline §3 Étape 2.
> ⚠️ **Bug ligne 75–76** : la condition `cv < 20` retourne `FUTAIE_REGULIERE` pour feuillu ET résineux (la branche feuillu est une copie exacte de la branche résineux). La distinction feuillu/résineux en futaie régulière n'est donc pas faite.

#### `managementProgram()` — Sélection du programme
La sélection se fait par `when` sur (essenceCode, treatmentMode) :
```
essUp.contains("CH_") || essUp == "CHATAIGNIER"  +  FR/FC/TSFCo  → cheneBourdOeuvreProgram
essUp == "HETRE_COMMUN"  +  FR                                    → hetreBourdOeuvreProgram
essUp.contains("DOUGLAS") || essUp.contains("SAPIN")              → douglasResineuxProgram
essUp.contains("PIN_")                                             → pinProgram
treatmentMode == FUTAIE_JARDINEE                                   → jardineProgram
TAILLIS_SIMPLE / DENSE / VIEILLISSANT                              → taillisProgram
TSF / TSFCo                                                        → tailisSousFutaieConversionProgram
else                                                               → genericProgram
```
> ⚠️ **Bug** : Match par `contains()` sur le code — si l'utilisateur saisit "Chêne" (sans underscore) ou "chene_pedoncule", le programme spécialisé chêne ne s'applique pas → fallback `genericProgram`.
> ⚠️ **Bug** : `FUTAIE_JARDINEE_GROUPES` non listé → tombe en `genericProgram`.

#### `sanitizeDensityTrajectory()` — Correction trajectoire (fix F5)
```kotlin
// Garantit que chaque étape est ≤ à l'étape précédente
// Supprime les étapes qui n'apportent pas de réduction
// Exemple :  n=139, raw=[139, 800, 400, 250, 200]
//         → result=[139]  (toutes étapes supprimées car > n)
```

#### `jardineProgram()` — Programme jardinée (fix F1 + F5)
```
targetDiamCm = null   ← peuplement irrégulier, pas de diamètre cible fixe
targetAgeAns = null   ← irrégulier, pas d'âge cible
targetNha    = null   ← irrégulier, pas de densité cible
densityTrajectory exprimée en G/ha (m²/ha) :
  "G actuelle"          → gPerHa ± 2
  "Après coupe (−15–25%)"→ gPerHa × 0.75..0.85
  "Retour à l'équilibre" → gPerHa ± 2
```
> ⚠️ **Ambiguïté** : L'UI affiche la trajectoire avec "t/ha" alors que jardineProgram utilise G/ha (m²/ha). Il faudrait soit changer l'unité affichée pour jardinage, soit utiliser une unité séparée.

#### `buildCNPFTypeCode()` — Code CNPF formel
```
Format : [Préfixe][ClasseCapital][Structure]
Exemples : F53, M22, T14, R01

Préfixe :
  R = Régénération (FR + stades SEMIS/FOURRE/GAULIS/PERCHIS)
  M = Mélangé/TSF
  T = Taillis
  F = Futaie (défaut)

Classe capital (G/ha) :
  00 : G < 2      0 : 2–5    1 : 5–10
   2 : 10–15      3 : 15–20  4 : 20–25
   5 : 25–30      6 : ≥ 30

Structure : 1–9 (voir §2 DiameterCategoryRatio)
```

#### `srgsItineraireFor()` — Itinéraire SRGS régional
Retourne une description textuelle de l'itinéraire sylvicole recommandé selon :
- `SRGSRegion` (14 régions + NATIONAL)
- `TreatmentMode`
- Essence dominante
- G/ha et N/ha actuels

---

## 5. SuperCorrelateurEngine.kt

**Chemin :** `domain/usecase/correlateur/SuperCorrelateurEngine.kt`
**Rôle :** Moteur central de corrélation multi-sources. Synthétise toutes les données disponibles.

### `correlate()` — Point d'entrée

```kotlin
fun correlate(
    tiges: List<Tige> = emptyList(),
    surfaceHa: Double = 1.0,
    station: StationObservation? = null,
    ripisylve: RipisylveObservation? = null,
    lat: Double? = null,
    lon: Double? = null,
    altM: Double? = null,
    essenceNames: Map<String, String> = emptyMap()
): CorrelateurResult
```

### `computeDendroScore()` — Score dendromètrique [0–100]
Base de départ : **50**

| Critère | Ajustement |
|---|---|
| N/ha 300–1500 | +10 |
| N/ha 100–299 ou 1501–3000 | +3 |
| N/ha < 100 ou > 3000 | -8 |
| 3 essences+ | +12 |
| 2 essences | +6 |
| 1 essence | -8 |
| >50% tiges avec hauteur mesurée | +10 |
| >25% | +5 |
| Gros bois ≥40cm : ≥5 | +12 |
| Gros bois ≥40cm : ≥3 | +8 |
| Gros bois ≥40cm : ≥1 | +3 |
| 0 gros bois + >20 tiges | -5 |
| Élancement résineux <70 | +8 (fix F2 : résineux seulement) |
| Élancement résineux <90 | +3 |
| Élancement résineux >120 | -10 |
| Élancement résineux >100 | -5 |
| >15% petits bois (<20cm) + ≥1 GB | +5 (structure étagée) |

### `computeStationScore()` — Score stationnel [0–100]
Base : **70**

| Contrainte | Malus |
|---|---|
| FAIBLE | -5 |
| MODEREE | -12 |
| FORTE | -20 |
| TRES_FORTE | -30 |
| Engorgement | -10 |
| Dépiècement | -10 |
| +5 par atout | +5 chacun |
| -8 par alerte | -8 chacune |

### `computeEssencesBilan()` — Bilan par essence
Pour chaque essence présente **et référencée dans AutecologyDatabase** :
1. Calcul G% pondéré
2. `evaluateCompatibility(essence, station)` → OPTIMUM/TOLERATED/INCOMPATIBLE
3. `climateChangeResilience` depuis AutecologyDB (1–5)
4. `computeFutureZoneCompatibility()` : projection zone bioclimatique 2050

**Projection zone 2050 (simplifiée) :**
```
SEMI_OCEANIQUE  → MEDITERRANEENNE
CONTINENTALE    → SEMI_OCEANIQUE
MONTAGNARDE     → SEMI_OCEANIQUE
ATLANTIQUE      → SEMI_OCEANIQUE
MEDITERRANEENNE → MEDITERRANEENNE  (pas de projection plus chaude ⚠)
UNKNOWN         → pas de projection ⚠
```

**ZoneFutureCompatibility résultante :**
```
OPTIMAL   — optimal maintenant ET futur + résilience ≥ 4
ACCEPTABLE — optimal maintenant + acceptable futur
MARGINAL  — optimal maintenant mais non optimal futur (résilience ≥ 3), ou acceptable futur
AT_RISK   — non optimal et non acceptable en futur (résilience > 2)
CRITICAL  — résilience ≤ 2
```
> ⚠️ **Bug** : La comparaison `ess.code !in essencesCodesPresentes` est case-sensitive. Si le bilan a le code en MAJUSCULES et AutecologyDB en minuscules (ou inversement), des essences présentes pourraient apparaître dans les recommandations.

### `computeRisquesClimatiques()` — Risques climatiques

**Risques systématiques via DRIASDatabase (3 premiers) :**
- Type "DRIAS 2050", niveau selon `droughtRisk2050` de la zone
- Horizon "2050 SSP8.5"

**Risques par essence détectée :**
```
Épicéa (EPICEA)  + zones semi-oce/cont/mont  → scolytes CRITIQUE
Frêne  (FRENE)                               → chalarose ELEVEE
Hêtre  (HETRE)   + zones semi-oce/cont       → dépérissement ELEVEE
Zone MEDITERRANEENNE + pin/eucalyptus         → incendie ELEVEE
Orme   (ORME)                                → graphiose CRITIQUE
```

**Risques stationnel :**
```
stationResult.risqueEngorgement == true → engorgement MODEREE
essences AT_RISK/CRITICAL (≥2)          → sécheresse ELEVEE
essences AT_RISK/CRITICAL (1)           → sécheresse MODEREE
```

### `computeRecommandationsFutur()` — Essences recommandées 2050
```
Filtre AutecologyDB :
  résilience ≥ 4
  + compatible zone future (optimal ou acceptable)
  + non déjà présente dans le peuplement
  + non INCOMPATIBLE avec la station

Tri : par résilience décroissante
Limite : top 8 essences
```
> ⚠️ **Bug** : Le filtre `ess.code !in essencesCodesPresentes` ne normalise pas la casse.

### `buildPlanAction()` — Plan d'action sylvicole

Actions générées (triées par urgence croissante) :
```
IMMEDIATE    :
  N/ha > 2000           → Éclaircie urgente
  ripisylve < 40/100    → Restauration ripisylve urgente
  risque CRITIQUE       → Surveillance sanitaire (par risque)

COURT_TERME  :
  N/ha > 1200           → Éclaircie planifiée
  1 seule essence       → Diversification spécifique
  contrainteHydrique FORTE/TRES_FORTE → Gestion stress hydrique
  ripisylve 40–60/100   → Amélioration ripisylve
  élancement résineux > 110 → Éclaircie anti-tempête

MOYEN_TERME  :
  essence CRITICAL      → Remplacement progressif (une action par essence)
  sol calcaire + calcifuge → Inadaptation essence/sol
  sol < 30cm + >10 tiges → Limiter conifères profonds

LONG_TERME   :
  gros bois < 3 + > 20 tiges → Conservation gros bois
```

### `computeResilienceScore()` — Score global de résilience [0–100]
```
climAdaptScore = Σ(résilience_essence × 20 × pctG / totalPctG)

global = moyenne(dendroScore, stationScore, ripisylveScore, climAdaptScore)
  Seuls les scores disponibles sont inclus dans la moyenne.

Labels :
  80–100 : Peuplement résilient
  60–79  : Résilience modérée
  40–59  : Peuplement fragile
  0–39   : État critique — intervention urgente
```

---

## 6. DRIASDatabase.kt

**Chemin :** `domain/usecase/correlateur/DRIASDatabase.kt`
**Rôle :** Base de données des projections climatiques DRIAS 2050 par zone bioclimatique.

### Structure `DRIASProjection`
```kotlin
data class DRIASProjection(
    val zone: ClimateZone,
    val deltaT2050_ssp245: Double,        // réchauffement SSP2-4.5 (°C)
    val deltaT2050_ssp585: Double,        // réchauffement SSP5-8.5 (°C)
    val deltaPsummer2050_ssp245: Double,  // variation précip. estivales SSP2-4.5 (%)
    val deltaPsummer2050_ssp585: Double,  // variation précip. estivales SSP5-8.5 (%)
    val deltaPannual2050_ssp245: Double,  // variation précip. annuelles SSP2-4.5 (%)
    val deltaPannual2050_ssp585: Double,  // variation précip. annuelles SSP5-8.5 (%)
    val droughtDays2050: Int,             // jours sécheresse supplémentaires / an
    val heatWaveDays2050: Int,            // jours canicule supplémentaires / an
    val fireRisk2050: FireRisk,
    val droughtRisk2050: DroughtRisk
)
```

### Fonctions
- `getProjection(zone)` → `DRIASProjection` (retourne une projection neutre si zone UNKNOWN)
- `generateRisques(zone)` → `List<String>` : 3 descriptions de risques textuelles quantifiées
- `droughtResistantEssences(zone)` → `List<String>` : essences adaptées à la sécheresse pour cette zone

> ⚠️ **Important** : Les données DRIAS intégrées sont des estimations représentatives (non les données officielles DRIAS brutes téléchargées depuis le serveur DRIAS-météo-france.fr). Elles permettent un diagnostic orientatif mais doivent être mises à jour avec les données officielles pour un usage professionnel.

---

## 7. BioClimaticRiskDatabase.kt

**Chemin :** `domain/usecase/correlateur/BioClimaticRiskDatabase.kt`
**Rôle :** Base de données des risques sanitaires et bioclimatiques par essence.

### Utilisation
Dans `SuperCorrelateurEngine.buildAlertesSanitaires()` :
```kotlin
val profile = BioClimaticRiskDatabase.getProfileByCode(code)
// Génère des AlerteSanitaire depuis profile.risks
```

### Structure des profils
Chaque profil essence contient des `Risk` avec :
- `type` : type de risque (scolyte, champignon, dépérissement...)
- `description` : explication détaillée
- `niveau` : CRITIQUE/ELEVEE/MODEREE/FAIBLE
- `zones` : zones bioclimatiques concernées

---

## 8. StationDiagnosticEngine.kt

**Chemin :** `domain/usecase/station/StationDiagnosticEngine.kt`
**Rôle :** Moteur de diagnostic stationnel à partir des observations pedologiques et floristiques.

### Entrée : `StationObservation`
- `altitudeM`, `pentePct`, `positionTopo`
- `exposition`, `texture`, `profondeurSolCm`
- `testHcl`, `humus`, `drainage`
- `phEstime`, `gradientHydrique` (1–5), `gradientTrophique` (1–5)
- `hydromorphieProfondeurCm`
- `especesIndicatrices: List<EspeceIndicatrice>`
- `latitude`, `longitude`

### `diagnose()` — Pipeline

**Calcul des contraintes :**
```kotlin
contrainteHydrique  : basée sur gradientHydrique (1-5) et drainage
contrainteTrophique : basée sur gradientTrophique (1-5)
contrainteProfondeur: basée sur profondeurSolCm
```

**Atouts détectés automatiquement :**
- `profondeurSolCm ≥ 60` → Sol profond
- `drainage BON/BIEN_DRAINE` → Drainage favorable
- `gradientTrophique ≥ 4` → Station eutrophe
- `altitudeM < 600` → Altitude favorable
- Position MI_VERSANT ou BAS_VERSANT → apports hydriques réguliers

**Alertes auto-générées :**
```
HCl TRES_FORT + humus MOR            → incohérence sol calcaire + mor
drainage TRES_MAUVAIS + expo sud     → combinaison inhabituelle
gradient hydrique sec + drainage mauvais → incohérence
sol < 20cm + trophique ≥ 4           → incohérence
altitudeM > 1200m                    → zone subalpine, diagnostic indicatif (fix F3)
divergence flore↔hydrique (±2 pts)   → avertissement
divergence flore↔trophique (±2 pts)  → avertissement
prob. hydromorphie > 50%             → alerte nappe
prob. perturbation > 40%             → flore perturbée
prob. compaction > 40%               → compaction sol
flore hygrophile + drainage déclaré BON → vérifier nappe
essence INCOMPATIBLE                 → alerte par essence
```

### `evaluateCompatibility(essence, station)` — utilisée par SuperCorrelateur
Vérifie dans l'ordre :
1. `minHydric`/`maxHydric` vs `gradientHydrique`
2. `minTrophic`/`maxTrophic` vs `gradientTrophique`
3. Profondeur sol vs exigences
4. **Altitude** : si `altitudeM > essence.maxAltitude` → INCOMPATIBLE
5. Drainage vs `toleratesHydromorphy`
6. Texture vs exigences
7. Exposition (essences héliophiles vs ombragées)

> ⚠️ **Calibration** : `contrainteHydrique()` et `contrainteTrophique()` utilisent des seuils fixes (gradients 1–5) qui peuvent être imprécis selon le contexte régional.

---

## 9. SanityChecker.kt

**Chemin :** `domain/calculation/SanityChecker.kt`
**Rôle :** Détection d'erreurs de saisie et de résultats aberrants sur chaque tige et à l'échelle du peuplement.

### `checkTige(tige)` — Vérifications par arbre

| Vérification | Seuil WARN | Seuil ERROR |
|---|---|---|
| Diamètre | > 150 cm | ≤ 0 ou < 0.5 ou > 300 cm |
| Hauteur | > 50 m | ≤ 0 ou < 0.5 m ou > 65 m |
| H/D (élancement) | ratio > 1.2 | ratio > 2.0 |
| Coefficient de forme | hors [0.15 ; 0.85] | — |

> ✅ **Fix F2 appliqué** : Le check H/D n'est effectué QUE si `isResineuxCode(tige.essenceCode) == true`.
> L'élancement H/D est non pertinent pour les feuillus (forme naturellement plus trapue, port étalé).

**`isResineuxCode(code)` — Détection résineux (présente dans SanityChecker, SuperCorrelateurEngine, MartelageSummaryCards, PdfSynthesisExporter, MartelageCsvExporter) :**
```kotlin
code.uppercase().contains("PIN")     ||
code.uppercase().contains("SAPIN")   ||
code.uppercase().contains("EPICEA")  ||
code.uppercase().contains("DOUGLAS") ||
code.uppercase().contains("MELEZE")  ||
code.uppercase().contains("CEDRE")   ||
code.uppercase().contains("SEQUOIA") ||
code.uppercase().contains("THUYA")   ||
code.uppercase().contains("CYPR")
```
> ⚠️ **Note** : Cette détection est dupliquée dans 5 fichiers différents. Un utilitaire commun (`EssenceUtils.isResineux(code)`) devrait centraliser cette logique pour éviter les divergences.

### `checkStats(stats)` — Vérifications par peuplement

| Vérification | Seuil WARN | Seuil ERROR |
|---|---|---|
| G/ha | > 80 m²/ha | > 150 m²/ha |
| V/ha | > 1200 m³/ha | > 3000 m³/ha |
| N/ha | > 5000 t/ha | > 20 000 t/ha |
| Ratio V/G | hors [3 ; 35] | — |
| Ratio Dm/Dg | hors [0.7 ; 1.0] | — |

---

## 10. MartelageScreen.kt

**Chemin :** `presentation/screens/forestry/MartelageScreen.kt`
**Rôle :** Écran principal de martelage. Calcul des stats, affichage par onglets.

### Calcul de `MartelageStats`
Les statistiques sont calculées dans un `LaunchedEffect` réactif sur `tiges` + `surfaceM2`.

**Formules clés :**
```
g_i (m²) = π × (D_i / 200)²          [D en cm → rayon en m]
G_total   = Σ g_i
G/ha      = G_total / surfaceHa

V_i       = g_i × H_i × f_i           [f = coefficient de forme]
V/ha      = Σ V_i / surfaceHa

Dg (cm)   = 200 × √(G_total / (N × π)) [diamètre quadratique]
Dm (cm)   = Σ D_i / N                   [diamètre arithmétique]

CV(D)     = (écart-type D / Dm) × 100

H Lorey   = Σ(g_i × H_i) / Σ(g_i)     [H pondérée par G]
```

### Structure des onglets (après fix G1)
```
TabRow fixe (4 onglets, pas de scroll) :
  0 — Synthèse     : VolumeCard, BasalAreaCard, DensityCard, DataCompletenessCard
  1 — Valorisation : ProductBreakdownCard, PerEssenceTable (tri par N/G/V/CA/Nom)
  2 — Analyse      : SanityWarnings, HarvestSim, ClassDist, QualityDist, SpecialTrees,
                     Biodiversity, CorroborationReport, SylviculturalKPIs
  3 — Gestion      : TypelogiqueRapideCard, IbpMartelageCard, StandFertilityCard,
                     StandClassificationBannerCard, SuperCorrelateurBannerCard
```

### `TypelogiqueRapideCard` — Formulaire typologique rapide
Saisie G + %PB/%BM/%GB/%TGB avec sliders → calcul live du code CNPF.
Composables utilisés :
- `TypelogiqueRapideCard()` — card principale
- `TypeSliderRow()` — private composable pour chaque ligne de slider

---

## 11. MartelageSummaryCards.kt

**Chemin :** `presentation/screens/forestry/MartelageSummaryCards.kt`
**Rôle :** Toutes les cartes d'affichage des statistiques de martelage.

### `SylviculturalKPIsCard` — KPIs sylvicoles

**KPI H/D (Élancement) — fix F2 :**
```kotlin
// Affiché UNIQUEMENT si essence dominante est résineuse
val isResineuxDominant = dominantCode.uppercase().contains("PIN") || ...
if (dg != null && meanH != null && dg > 0.0 && isResineuxDominant) {
    val slend = meanH / (dg / 100.0)
    // Interprétation :
    // < 70  → Très stable
    // < 85  → Stable
    // < 100 → Normal
    // ≥ 100 → Élancé — risque vent
}
```

**Autres KPIs toujours affichés :**
- G/ha avec niveau (Faible / Modérée / Normale / Dense / Surpeuplement)
- Ratio V/G (hauteur de forme)
- Ratio H Lorey / H moy (hétérogénéité verticale)

---

## 12. StandClassificationScreen.kt

**Chemin :** `presentation/screens/forestry/StandClassificationScreen.kt`
**Rôle :** Écran de résultats de classification. 4 onglets : Résultats | Structure | Programme | Écologie.

### `ManagementProgramTab` — Onglet Programme (fix F1)

**Masquage des champs pour peuplements irréguliers :**
```kotlin
val isIrregulier = r.treatmentMode in setOf(
    TreatmentMode.FUTAIE_JARDINEE,
    TreatmentMode.FUTAIE_JARDINEE_GROUPES,
    TreatmentMode.FUTAIE_IRREGULIERE,
    TreatmentMode.FUTAIE_IRREGULIERE_BOUQUETS
)
// Si isIrregulier → targetDiamCm, targetAgeAns, targetNha NON affichés
// Un bandeau explicatif remplace ces valeurs :
// "Peuplement irrégulier : gestion continue par prélèvements périodiques."
```

**Sélecteur région SRGS :**
- `var selectedRegion by remember { mutableStateOf<SRGSRegion?>(null) }`
- OutlinedButton → DropdownMenu avec 14 régions + NATIONAL
- `StandTypologyDatabase.srgsItineraireFor(region, mode, essence, G, N)` → texte itinéraire

**Trajectoire densité :**
- Chaque étape affichée avec point coloré (bleu foncé = dernier)
- Affichage range : "Min–Max t/ha" (⚠ unité incorrecte pour jardineProgram)

---

## 13. Export (PDF / CSV)

### `PdfSynthesisExporter.kt`
- Section H/D conditionnée au résineux dominant (fix F2)
- Label modifié : "Élancement H/D (résineux)"

### `MartelageCsvExporter.kt`
- Section H/D conditionnée au résineux dominant (fix F2)
- Label modifié : "Élancement H/D (résineux)"

### `RipisylvePdfExporter.kt`
- Appel `score.generateSummary()` (fix session précédente)

---

## 14. Tableau récapitulatif des bugs identifiés

| Priorité | Fichier | Ligne | Description du bug | Impact |
|---|---|---|---|---|
| 🔴 CRITIQUE | `StandClassificationEngine.kt` | 284 | `detectDisturbance` utilise H/D sur TOUTES essences (feuillu inclus) → peut classifier faux STRESSE | Classification erronée |
| 🔴 CRITIQUE | `StandTypologyDatabase.kt` | 75–76 | Branche `cv < 20` identique feuillu/résineux (code mort) | Pas de distinction FR feuillu/résineux |
| 🟠 IMPORTANT | `StandTypologyDatabase.kt` | 106–141 | Match essence par `contains()` sur code → fragile si code libre | Mauvais programme assigné |
| 🟠 IMPORTANT | `SuperCorrelateurEngine.kt` | 581 | Filtre `essenceCode` case-sensitive → essences présentes recommandées | Recos incorrectes |
| 🟠 IMPORTANT | `StandClassificationEngine.kt` | 313 | `MELANGE_PIED_A_PIED` → `BOIS_OEUVRE` même pour taillis mélangé | Objectif incohérent |
| 🟡 MINEUR | `StandTypologyDatabase.kt` | 501 | `jardineProgram` trajectoire en G/ha mais UI affiche "t/ha" | Unité ambiguë |
| 🟡 MINEUR | `SuperCorrelateurEngine.kt` | 395 | Projection zone 2050 trop simpliste (4 zones seulement) | Recos partielles |
| 🟡 MINEUR | `StandClassificationEngine.kt` | 96–100 | `QUESTION_ZONE` toujours posée mais résultat peu utilisé | UX perturbante |
| 🟡 MINEUR | Multiple | — | `isResineuxCode()` dupliquée dans 5 fichiers | Maintenance difficile |
| 🟡 MINEUR | `StandTypologyDatabase.kt` | 107 | FJG/FE/FM/TSC/TV/TSFP jamais assignés → stockés dans `TreatmentMode` pour rien | Dead code enum |

---

## 15. Fixes appliqués lors de cette session

### F1 — Peuplement irrégulier : masquer diamètre/âge/densité cible
- **`StandClassificationScreen.kt`** : `isIrregulier` check, champs masqués, bandeau explicatif
- **`StandTypologyDatabase.kt`** : `jardineProgram()` → `targetDiamCm = null`

### F2 — H/D interdit pour les feuillus
Fichiers modifiés :
1. `SanityChecker.kt` : ajout `isResineuxCode()` + gate sur `checkTige()`
2. `MartelageSummaryCards.kt` : gate `isResineuxDominant` sur KPI élancement
3. `SuperCorrelateurEngine.kt` : `computeDendroScore()` + `buildPlanAction()` filtrés sur `isResineuxCode()`
4. `PdfSynthesisExporter.kt` : section H/D conditionnée
5. `MartelageCsvExporter.kt` : section H/D conditionnée

### F3 — Altitude > 1200 m
- **`StationDiagnosticEngine.kt`** : alerte automatique "Zone subalpine — diagnostic indicatif"

### F4 — Composition "mélangé par bouquets" sans données GPS
- **`StandClassificationEngine.kt`** `detectComposition()` : suppression de `MELANGE_BOUQUETS` basé sur nombre d'essences. Tout mélange → `MELANGE_PIED_A_PIED`.

### F5 — Trajectoire densité incohérente (montante)
- **`StandTypologyDatabase.kt`** : ajout `sanitizeDensityTrajectory()` helper
- Appliqué à : `cheneBourdOeuvreProgram`, `hetreBourdOeuvreProgram`, `douglasResineuxProgram`, `pinProgram`
- `jardineProgram` : nouvelle trajectoire G/ha cyclique (actuelle → −15–25% → retour équilibre)

### G1 — Onglets Martelage : TabRow fixe + réorganisation
- **`MartelageScreen.kt`** : `ScrollableTabRow` → `TabRow`, icônes supprimées
- Ordre : **Synthèse(0) | Valorisation(1) | Analyse(2) | Gestion(3)**

---

## ANNEXE — Enums ClimateZone utilisés dans le corrélateur

```kotlin
enum class ClimateZone {
    ATLANTIQUE,       // Bretagne, façade atlantique
    SEMI_OCEANIQUE,   // Bassin parisien, Centre, Normandie
    CONTINENTALE,     // Alsace, Lorraine, intérieur
    MONTAGNARDE,      // Vosges, Alpes, Massif Central, Pyrénées
    MEDITERRANEENNE,  // PACA, Languedoc, Corse
    SEMI_ARIDE,       // Zones très sèches
    UNKNOWN           // Pas de données GPS
}
```

`ClimateZone.detect(lat, lon, altM)` : détection par grille géographique + altitude.
**Seuil altitude 1200m** : aucune zone "subalpine" formelle — géré uniquement via alerte dans `StationDiagnosticEngine`.

---

*Document généré automatiquement le 16 mars 2026 par analyse statique du code source GeoSylva.*
*Fichiers source inclus dans ce ZIP pour review.*
