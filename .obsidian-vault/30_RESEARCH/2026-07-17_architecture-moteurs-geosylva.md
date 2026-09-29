Subagent deb01bdf completed successfully:

# Architecture des Moteurs de Calcul Scientifique GeoSylva — Spécification Complète

**Date** : 2026-07-17  
**Statut** : Spécification d'architecture pour refonte §7.7  
**Version** : v0.9.1 (Frozen)

---

## 1. État des lieux — moteurs actuels

### 1.1 ForestryCalculator.kt (God Object)

**Lignes de code** : 760 lignes  
**Responsabilités** (7+) :
1. Calcul de volume via tarifs (Schaeffer, Algan, IFN)
2. Calcul de surface terrière (G)
3. Calcul de coefficient de forme (F)
4. Résolution de méthodes de tarif par essence
5. Chargement/sauvegarde de paramètres (ParameterRepository)
6. Calcul de synthèse martelage (statistiques par classe)
7. Calcul de prix (via PriceCalculator)
8. Classification de produits (via ProductClassifier)
9. Validation de données (via SanityChecker)

**Coefficients hardcodés** : 0 (délègue à TarifData.kt)

**Tests existants** : Non visible dans le fichier (à vérifier dans tests/)

**Problèmes identifiés** :
- **Couplage** : Dépend directement de ParameterRepository (couche data)
- **Complexité** : 760 lignes, multiples responsabilités
- **Sourcing** : Aucun coefficient hardcodé (bon), mais logique de résolution dispersée
- **Testabilité** : Difficile à tester unitairement à cause du couplage Repository
- **Dépendances** : Importe tarifs, quality, pricing — architecture en couches non respectée

---

### 1.2 ExpertForestryCalculator.kt

**Lignes de code** : 663 lignes  
**Responsabilités** :
1. Calcul de Hdom (hauteur dominante ONF)
2. Calcul de l'Indice de Station (IS)
3. Tables de production (chêne, hêtre) — Décourt & Pardé (1980)
4. Modèle de Richards (croissance en diamètre)
5. Schumacher-Hall (cubage)
6. Surface terrière
7. Recommandations sylvicoles
8. Simulation de croissance

**Coefficients hardcodés** : ~150 coefficients
- Tables de production chêne : 3 stations × 8 âges = 24 entrées × 6 valeurs = 144 coefficients
- Tables de production hêtre : 2 stations × 8 âges = 16 entrées × 6 valeurs = 96 coefficients
- Paramètres Richards par essence (non comptés ici, dans SylvicultureDatabase)

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Sourcing** : Tables de production hardcodées (Décourt & Pardé 1980) — bien sourcées en commentaires
- **Couplage** : Dépend de ForestryCalculator (couplage circulaire potentiel)
- **Complexité** : 663 lignes, mélange calcul + tables de données
- **Validation** : Bornes IS (5-30) coercées sans warning explicite

---

### 1.3 EnhancedForestryCalculator.kt

**Lignes de code** : 385 lignes  
**Responsabilités** :
1. Calcul de volume enhanced (Schumacher-Hall prioritaire)
2. Analyse de peuplement (combine base + expert)
3. Calcul de paramètres de plantation optimaux
4. Simulation de croissance
5. Évaluation de qualité de peuplement

**Coefficients hardcodés** : 0 (délègue à ExpertForestryCalculator)

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Couplage** : Dépend de ForestryCalculator ET ExpertForestryCalculator
- **Complexité** : 385 lignes, orchestrateur de 3 calculateurs
- **Responsabilité** : Mélange calcul + orchestration + UI-ready results

---

### 1.4 TarifCalculator.kt

**Lignes de code** : 273 lignes  
**Responsabilités** :
1. Calcul de volume pour 7 méthodes (Schaeffer 1E/2E, Algan, IFN Rapide/Lent, FGH, CoefForme)
2. Résolution de numéro de tarif recommandé
3. Fallback Algan par famille d'essence
4. Coefficient de forme par défaut

**Coefficients hardcodés** : 0 (délègue à TarifData.kt)

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Architecture** : Bonne séparation (data dans TarifData.kt)
- **Fallback** : Logique de fallback par famille d'essence (conifères vs feuillus) — simple mais efficace
- **Validation** : Vérifie diamCm > 0, mais pas les bornes supérieures

---

### 1.5 TarifData.kt

**Lignes de code** : 624 lignes  
**Responsabilités** :
1. Stockage des coefficients Schaeffer 1E (16 tarifs)
2. Stockage des coefficients Schaeffer 2E (8 tarifs)
3. Stockage des coefficients Algan (~60 essences)
4. Stockage des coefficients IFN Rapide (36 tarifs)
5. Stockage des coefficients IFN Lent (8 tarifs)
6. Stockage des coefficients de forme par essence
7. Mapping essence → numéro tarif IFN

**Coefficients hardcodés** : ~200 coefficients
- Schaeffer 1E : 16 tarifs × 2 coefficients = 32
- Schaeffer 2E : 8 tarifs × 2 coefficients = 16
- Algan : ~60 essences × 3 coefficients = 180
- IFN Rapide : 36 tarifs × 3 coefficients = 108
- IFN Lent : 8 tarifs × 3 coefficients = 24
- Coefficients forme : ~20 essences × 1 coefficient = 20
- Mapping essence → IFN : ~30 mappings

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Sourcing** : Excellent — chaque section a références bibliographiques (Schaeffer 1949, Algan 1958, IFN, Pardé & Bouchon 1988)
- **Structure** : Object Kotlin (singleton) — difficile à mocker pour tests
- **Extensibilité** : Ajouter une essence = modifier le code source (pas de data file externe)

---

### 1.6 TarifModels.kt

**Lignes de code** : 245 lignes  
**Responsabilités** :
1. Définition de l'enum TarifMethod (7 méthodes)
2. Data classes pour les coefficients (SchaefferOneEntryCoefs, etc.)
3. Méthodes de calcul de volume pour chaque type de coefficient
4. Configuration TarifSelection

**Coefficients hardcodés** : 0 (data classes pures)

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Architecture** : Très bonne — séparation modèle/méthode
- **Validation** : CoerceAtLeast(0.0) sur les volumes — garde-fou minimal

---

### 1.7 VolumeConversion.kt

**Lignes de code** : 66 lignes  
**Responsabilités** :
1. Conversion stère ↔ m³
2. Détection conifères vs feuillus
3. Coefficients par catégorie (0.7 feuillus, 0.65 résineux)

**Coefficients hardcodés** : 2 coefficients constants + 13 keywords conifères

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Sourcing** : Source ONF citée
- **Précision** : Coefficients constants — pas de variation par essence
- **Architecture** : Object Kotlin — testable mais difficile à mocker

---

### 1.8 ProPricingEngine.kt

**Lignes de code** : 303 lignes  
**Responsabilités** :
1. Calcul de prix avec 8 coefficients (composition multiplicative)
2. Breakdown transparent de chaque coefficient
3. Fallback sur DefaultProductPrices
4. Calcul rapide (quickPrice)
5. Calcul uniquement depuis PriceEntry (sans fallback)

**Coefficients hardcodés** : 0 (délègue à PricingCoefficients.kt, WoodDefect.kt, etc.)

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Architecture** : Excellente — séparation claire des responsabilités
- **Transparence** : Breakdown complet avec sources — très bon pour audit
- **Validation** : Plafond dépréciation défauts à 90% (MAX_TOTAL_DEPRECIATION)

---

### 1.9 PricingContext.kt

**Lignes de code** : 227 lignes  
**Responsabilités** :
1. Data class de contexte de calcul
2. Enums : SalePosition, Accessibility, SaleSeason, Certification
3. Coefficients hardcodés dans les enums

**Coefficients hardcodés** : ~15 coefficients (dans les enums)
- SalePosition : 3 positions × coefficient
- Accessibility : 4 niveaux × coefficient
- SaleSeason : 4 saisons × coefficient
- Certification : 4 certifications × coefficient

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Sourcing** : Chaque enum a source documentée (ONF, CNPF, CIBE, FBF)
- **Architecture** : Très bonne — data classes pures + enums avec métadonnées
- **Extensibilité** : Ajouter un niveau = modifier l'enum (pas de data file)

---

### 1.10 PricingResult.kt

**Lignes de code** : 89 lignes  
**Responsabilités** :
1. Data class de résultat avec breakdown
2. Méthode summary() pour affichage
3. Calcul du coefficient total

**Coefficients hardcodés** : 0

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Architecture** : Très bonne — résultat structuré et auditable

---

### 1.11 PricingCoefficients.kt

**Lignes de code** : 86 lignes  
**Responsabilités** :
1. Coefficients régionaux par essence × région
2. Coefficients de taille de lot

**Coefficients hardcodés** : ~15 coefficients
- essenceRegionCoefficients : 13 mappings essence×région
- LotSizeCoefficients : 5 seuils de volume

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Sourcing** : Sources FBF, observatoires Fibois citées
- **Architecture** : Object Kotlin — testable
- **Validation** : Régions sans source publique → coefficient national assumé (warning dans doc)

---

### 1.12 WoodDefect.kt

**Lignes de code** : 366 lignes  
**Responsabilités** :
1. Catalogue de 40+ défauts du bois
2. Plages de dépréciation par défaut
3. Calcul de dépréciation par sévérité
4. Cumul des dépréciations avec plafond

**Coefficients hardcodés** : ~80 coefficients
- 40+ défauts × plage de dépréciation (min, max)
- 4 catégories de défauts
- Plafond MAX_TOTAL_DEPRECIATION = 0.90

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Sourcing** : Excellent — chaque défaut a référence normative (NF EN 1310, NF EN 1316-1, etc.)
- **Architecture** : Enum avec métadonnées — très bon
- **Validation** : Plafond à 90% — garde-fou explicite

---

### 1.13 WoodQualityGrade.kt

**Lignes de code** : 632 lignes  
**Responsabilités** :
1. Enum WoodQualityGrade (A/B/C/D)
2. Enum ForestProduct (15+ produits)
3. QualityAssessment (évaluation rapide)
4. ProductClassifier (classification produit)
5. DefaultProductPrices (prix par défaut)

**Coefficients hardcodés** : ~50 coefficients
- WoodQualityGrade : 4 grades × multiplicateur
- ForestProduct : 15+ produits × métadonnées
- DefaultProductPrices : ~30 prix par défaut

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Sourcing** : Sources ONF, NF EN 1316/1927 citées
- **Architecture** : Très bonne — enums avec métadonnées
- **Extensibilité** : Ajouter un produit = modifier l'enum

---

### 1.14 PriceCalculator.kt

**Lignes de code** : 218 lignes  
**Responsabilités** :
1. Coefficients de qualité par essence (A/B/C/D)
2. Lookup de prix de base dans PriceEntry
3. Calcul de prix ajusté
4. Ventilation par produit

**Coefficients hardcodés** : ~100 coefficients
- qualityCoefficients : ~25 essences × 4 grades = 100 coefficients

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Sourcing** : Excellent — sources NF EN 1316-1, NF EN 1927, FBF, ONF citées
- **Architecture** : Object Kotlin — testable
- **Validation** : Wildcard "*" pour essences non listées

---

### 1.15 AdvancedCalculationEngine.kt

**Lignes de code** : 772 lignes  
**Responsabilités** :
1. Calcul de biomasse (IPCC 2006)
2. Calcul de carbone
3. Calcul de CO2-équivalent
4. Décomposition biomasse (fût, aérienne, racinaire)
5. Stockage dans AdvancedCalculationEntity

**Coefficients hardcodés** : ~50 coefficients
- carbonFraction = 0.50
- co2ConversionFactor = 3.67
- rootToShootRatio = 0.25
- BEF : 1.65 (feuillus), 1.45 (résineux)
- volumeCoefficients : ~20 essences × 3 coefficients
- woodDensity : ~20 essences
- coniferCodes : 30 codes

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Sourcing** : Sources IPCC 2006, Cairns et al. 1997 citées
- **Architecture** : Couche data (AdvancedCalculationEntity) — mélange calcul + persistance
- **Validation** : Bornes non explicites

---

### 1.16 PeuplementAvantCoupeCalculator.kt

**Lignes de code** : 304 lignes  
**Responsabilités** :
1. Calcul de statistiques par classe de diamètre
2. Reproduction tableau Excel "Peuplement avant coupe"
3. Calcul de volume total, volume trituration, bois d'œuvre
4. Calcul de surface terrière par classe

**Coefficients hardcodés** : ~15 coefficients
- allowedClasses : 12 classes (20,25,30,...,75)
- pctBoisNonTrituTable : ~12 pourcentages par classe

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Architecture** : Calculateur spécifique pour un tableau Excel — très ciblé
- **Sourcing** : Sources non documentées
- **Validation** : Bornes non explicites

---

### 1.17 SanityChecker.kt

**Lignes de code** : 301 lignes  
**Responsabilités** :
1. Validation des données d'entrée (tige)
2. Validation des volumes calculés
3. Validation des revenus calculés
4. Validation des agrégats (N/ha, G/ha, V/ha)
5. Cohérence H/D, V/G

**Coefficients hardcodés** : ~20 bornes
- DIAM_MIN_CM = 0.5, DIAM_MAX_CM = 300.0
- HEIGHT_MIN_M = 0.5, HEIGHT_MAX_M = 65.0
- VOL_TREE_MAX_M3 = 30.0
- G_HA_WARN_HIGH = 80.0, etc.

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Architecture** : Très bonne — séparation validation
- **Sourcing** : Bornes basées sur extrêmes réels dendrométrie française
- **Validation** : 3 niveaux (ERROR, WARNING, INFO)

---

### 1.18 ParameterModels.kt

**Lignes de code** : 74 lignes  
**Responsabilités** :
1. Data classes pour paramètres (CoefVolumeRange, HeightDefaultRange, etc.)
2. ProductRule (règles de classification produit)
3. PriceEntry (entrée de prix)

**Coefficients hardcodés** : 0

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Architecture** : Très bonne — data classes pures

---

### 1.19 MartelageModels.kt

**Lignes de code** : 681 lignes  
**Responsabilités** :
1. computeHdom (hauteur dominante ONF)
2. computeStructureTriangle (triangle des structures ONF)
3. computeMartelageStats (agrégats martelage)
4. PerEssenceStats, MartelageStats

**Coefficients hardcodés** : ~10 seuils ONF
- ONF_THRESHOLD_PERCHE = 7.5
- ONF_THRESHOLD_PB = 17.5
- ONF_THRESHOLD_BM = 27.5
- ONF_THRESHOLD_GB = 47.5

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Architecture** : Mélange fonctions pures + orchestration
- **Sourcing** : Seuils ONF officiels — bien sourcés
- **Couplage** : Dépend de ForestryCalculator

---

### 1.20 UnitConverter.kt

**Lignes de code** : 71 lignes  
**Responsabilités** :
1. Conversion entre unités (via UnitCatalog)
2. Validation de compatibilité
3. Formatage avec unité

**Coefficients hardcodés** : 0 (délègue à UnitCatalog.kt)

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Architecture** : Très bonne — séparation catalogue/convertisseur

---

### 1.21 UnitCatalog.kt

**Lignes de code** : 102 lignes  
**Responsabilités** :
1. Catalogue d'unités (m, cm, ha, m³, kg, etc.)
2. Dimensions (length, area, volume, mass, etc.)
3. Facteurs de conversion vers unité de base

**Coefficients hardcodés** : ~20 unités × facteur

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Architecture** : Très bonne — catalogue extensible
- **Sourcing** : Pas de source (standard SI)

---

### 1.22 FertilityReference.kt

**Lignes de code** : 326 lignes  
**Responsabilités** :
1. Référentiels de fertilité par essence
2. Seuils de hauteur par classe (I, II, III, IV)
3. Âge de référence (H50 ou H100)
4. Zones climatiques préférées
5. Diamètre d'exploitation typique

**Coefficients hardcodés** : ~100 coefficients
- 8 essences × 4 seuils × métadonnées
- expectedHAtHarvestByCls : 8 essences × 4 classes = 32 coefficients

**Tests existants** : Non visible

**Problèmes identifiés** :
- **Sourcing** : Excellent — guides ONF, CNPF, CRPF cités
- **Architecture** : Object Kotlin — testable
- **Extensibilité** : Ajouter une essence = modifier le code

---

## Résumé de l'état des lieux

| Moteur | Lignes | Coefficients | Tests | Problèmes principaux |
|--------|--------|--------------|-------|---------------------|
| ForestryCalculator | 760 | 0 | ? | God object, couplage Repository |
| ExpertForestryCalculator | 663 | ~150 | ? | Tables hardcodées, couplage ForestryCalculator |
| EnhancedForestryCalculator | 385 | 0 | ? | Orchestrateur complexe |
| TarifCalculator | 273 | 0 | ? | Bonne séparation |
| TarifData | 624 | ~200 | ? | Object singleton, extensibilité limitée |
| TarifModels | 245 | 0 | ? | Très bonne architecture |
| VolumeConversion | 66 | 15 | ? | Coefficients constants |
| ProPricingEngine | 303 | 0 | ? | Excellente architecture |
| PricingContext | 227 | ~15 | ? | Très bonne architecture |
| PricingResult | 89 | 0 | ? | Très bonne architecture |
| PricingCoefficients | 86 | ~15 | ? | Bonne architecture |
| WoodDefect | 366 | ~80 | ? | Excellente architecture |
| WoodQualityGrade | 632 | ~50 | ? | Très bonne architecture |
| PriceCalculator | 218 | ~100 | ? | Bonne architecture |
| AdvancedCalculationEngine | 772 | ~50 | ? | Couche data mélangée |
| PeuplementAvantCoupeCalculator | 304 | ~15 | ? | Spécifique Excel |
| SanityChecker | 301 | ~20 | ? | Très bonne architecture |
| ParameterModels | 74 | 0 | ? | Très bonne architecture |
| MartelageModels | 681 | ~10 | ? | Mélange fonctions/orchestration |
| UnitConverter | 71 | 0 | ? | Très bonne architecture |
| UnitCatalog | 102 | ~20 | ? | Très bonne architecture |
| FertilityReference | 326 | ~100 | ? | Object singleton, extensibilité limitée |

**Total coefficients hardcodés** : ~830 coefficients

**Observations** :
- Le sourcing est **excellent** sur la plupart des moteurs (références bibliographiques citées)
- L'architecture est **globalement bonne** sur les moteurs récents (pricing, quality)
- Le **God object** ForestryCalculator doit être décomposé en priorité
- Les **coefficients hardcodés** dans des objects Kotlin sont difficiles à étendre sans modifier le code
- Les **tests** ne sont pas visibles dans les fichiers lus (à vérifier dans le dossier tests/)

---

## 2. Architecture cible — 9 domaines (§7.7)

### 2.1 domain/taxonomy/

#### SpeciesResolver

**Interface** :
```kotlin
interface SpeciesResolver {
    /**
     * Résout une essence GeoSylva vers TAXREF et GBIF.
     * @param geoSylvaCode Code essence GeoSylva (ex: "CH_SESSILE")
     * @return SpeciesResolution avec TAXREF ID, GBIF ID, nom scientifique
     */
    fun resolve(geoSylvaCode: String): SpeciesResolution
    
    /**
     * Résout une essence depuis un code externe (TAXREF, GBIF).
     * @param externalCode Code externe + source
     * @return Code GeoSylva correspondant ou null
     */
    fun resolveFromExternal(externalCode: String, source: ExternalSource): String?
}

data class SpeciesResolution(
    val geoSylvaCode: String,
    val taxrefId: String?,
    val gbifId: String?,
    val scientificName: String?,
    val commonNameFr: String?,
    val commonNameEn: String?,
    val synonyms: List<String> = emptyList(),
    val confidence: ResolutionConfidence
)

enum class ResolutionConfidence { EXACT, LIKELY, AMBIGUOUS, UNKNOWN }
enum class ExternalSource { TAXREF, GBIF, INPN, WIKIPEDIA }
```

**Dépendances** :
- Aucune (moteur autonome)
- Repository de taxonomie (optionnel pour données externes)

**Algorithmes** :
- Table de mapping GeoSylva → TAXREF (hardcodée ou chargée depuis JSON)
- Table de mapping TAXREF → GBIF (via API ou cache local)
- Résolution de synonymes (aliases)

**Validation de domaine** :
- geoSylvaCode non vide
- Codes TAXREF/GBIF valides (format numérique)
- Confidence != AMBIGUOUS → warning

**Incertitude** :
- Confidence score basé sur :
  - Match exact du code
  - Match via synonymes
  - Match partiel (Levenshtein distance)

**Provenance** :
- Source de chaque mapping (table interne, API TAXREF, API GBIF)
- Date de dernière mise à jour
- Version de la base TAXREF/GBIF utilisée

**Tests requis** :
- Unitaires : résolution exacte, synonymes, cas inconnus
- Propriétés : transitivité (GeoSylva → TAXREF → GBIF)
- Bordures : codes vides, codes invalides

**Mapping avec code existant** :
- Remplace : EssenceAliases (partiellement)
- Nouveau : mapping TAXREF/GBIF
- Coefficients à migrer : table des aliases

---

#### SpeciesGroupResolver

**Interface** :
```kotlin
interface SpeciesGroupResolver {
    /**
     * Résout le groupe d'une essence (feuillus/résineux, dur/tendre).
     * @param geoSylvaCode Code essence GeoSylva
     * @return SpeciesGroup avec catégorie et sous-catégorie
     */
    fun resolveGroup(geoSylvaCode: String): SpeciesGroup
}

data class SpeciesGroup(
    val category: SpeciesCategory,    // FEUILLU, RESINEUX
    val subcategory: SpeciesSubcategory?,  // DUR, TENDRE, null si non applicable
    val family: String?,               // Famille botanique (Fagaceae, Pinaceae, etc.)
    val commercialGroup: String?       // Groupe commercial (chêne, hêtre, douglas, etc.)
)

enum class SpeciesCategory { FEUILLU, RESINEUX }
enum class SpeciesSubcategory { DUR, TENDRE }
```

**Dépendances** :
- SpeciesResolver (pour nom scientifique)
- Repository de taxonomie (optionnel)

**Algorithmes** :
- Table de mapping essence → groupe (hardcodée ou JSON)
- Inférence depuis la famille botanique (si SpeciesResolver disponible)

**Validation de domaine** :
- geoSylvaCode non vide
- Category toujours résolue (fallback FEUILLU)
- Subcategory nullable (pas applicable pour tous)

**Incertitude** :
- N/A (classification déterministe)

**Provenance** :
- Source de chaque mapping (table interne, taxonomie externe)
- Règles d'inférence documentées

**Tests requis** :
- Unitaires : résolution pour essences courantes
- Propriétés : consistance (tous les chênes = FEUILLU + DUR)
- Bordures : essences inconnues → fallback

**Mapping avec code existant** :
- Remplace : logique de détection conifères dans VolumeConversion.kt
- Remplace : logique de filtrage par catégorie dans WoodQualityGrade.kt
- Coefficients à migrer : liste des conifères (13 keywords)

---

#### TraitResolver

**Interface** :
```kotlin
interface TraitResolver {
    /**
     * Résout les traits fonctionnels d'une essence.
     * @param geoSylvaCode Code essence GeoSylva
     * @return FunctionalTraits avec autécologie, traits de bois, etc.
     */
    fun resolveTraits(geoSylvaCode: String): FunctionalTraits
}

data class FunctionalTraits(
    val lightRequirement: LightRequirement,    // Héliophile, Sciaphile, etc.
    val soilMoisture: SoilMoisture,            // Xérophile, Mésophile, Hygrophile
    val soilFertility: SoilFertility,          // Oligotrophe, Mésotrophe, Eutrophe
    val woodDensity: Double?,                  // g/cm³ (infradensité)
    val durability: Durability?,               // Classe de durabilité naturelle
    val growthRate: GrowthRate?,              // Vitesse de croissance
    val shadeTolerance: ShadeTolerance?,      // Tolérance à l'ombre
    val frostResistance: FrostResistance?      // Résistance au gel
)

enum class LightRequirement { HELIOPHILE, SCIAPHILE, INTERMEDIATE }
enum class SoilMoisture { XEROPHILE, MESOPHILE, HYGROPHILE }
enum class SoilFertility { OLIGOTROPHE, MESOTROPHE, EUTROPHE }
enum class Durability { CLASS_1, CLASS_2, CLASS_3, CLASS_4, CLASS_5 }
enum class GrowthRate { FAST, MEDIUM, SLOW }
enum class ShadeTolerance { INTOLERANT, INTERMEDIATE, TOLERANT }
enum class FrostResistance { HARDY, MODERATE, SENSITIVE }
```

**Dépendances** :
- SpeciesResolver (pour nom scientifique)
- Repository de traits (TRY database, LEDA, etc.)

**Algorithmes** :
- Table de traits par essence (hardcodée ou chargée depuis JSON)
- Inférence depuis le groupe (feuillus vs résineux) pour valeurs par défaut

**Validation de domaine** :
- geoSylvaCode non vide
- Traits nullable → fallback sur groupe
- woodDensity dans plage réaliste (0.3–1.2 g/cm³)

**Incertitude** :
- Confidence score basé sur :
  - Données directes (essence listée)
  - Inférence depuis groupe
  - Valeurs par défaut génériques

**Provenance** :
- Source de chaque trait (TRY database, LEDA, littérature)
- Date de dernière mise à jour

**Tests requis** :
- Unitaires : résolution pour essences courantes
- Propriétés : consistance (densité réaliste)
- Bordures : essences inconnues → fallback

**Mapping avec code existant** :
- Nouveau : aucun équivalent dans le code actuel
- Coefficients à migrer : woodDensity depuis AdvancedCalculationEngine.kt

---

### 2.2 domain/measurement/

#### MeasurementNormalizer

**Interface** :
```kotlin
interface MeasurementNormalizer {
    /**
     * Normalise une mesure brute (corrections, arrondis, conversions).
     * @param raw Mesure brute
     * @return Mesure normalisée
     */
    fun normalize(raw: RawMeasurement): NormalizedMeasurement
    
    /**
     * Normalise une liste de mesures (batch).
     */
    fun normalizeBatch(rawList: List<RawMeasurement>): List<NormalizedMeasurement>
}

data class RawMeasurement(
    val value: Double,
    val unit: String,
    val type: MeasurementType,
    val timestamp: Long?,
    val deviceId: String?,
    val metadata: Map<String, Any> = emptyMap()
)

data class NormalizedMeasurement(
    val value: Double,
    val unit: String,           // Unité standard (cm, m, m³, etc.)
    val type: MeasurementType,
    val timestamp: Long?,
    val deviceId: String?,
    val corrections: List<Correction> = emptyList(),
    val quality: MeasurementQuality,
    val metadata: Map<String, Any> = emptyMap()
)

enum class MeasurementType { DIAMETER, HEIGHT, VOLUME, BASAL_AREA, DENSITY }
enum class MeasurementQuality { HIGH, MEDIUM, LOW, REJECTED }

data class Correction(
    val type: CorrectionType,
    val description: String,
    val beforeValue: Double,
    val afterValue: Double
)

enum class CorrectionType { UNIT_CONVERSION, ROUNDING, OUTLIER_REMOVAL, CALIBRATION }
```

**Dépendances** :
- UnitConverter
- MeasurementValidator

**Algorithmes** :
- Conversion vers unité standard (via UnitConverter)
- Arrondi à précision appropriée (diamètre: 0.1 cm, hauteur: 0.1 m)
- Détection et correction des outliers (via SanityChecker)
- Calibration device-specific (si metadata disponible)

**Validation de domaine** :
- value > 0 (sauf pour certaines corrections)
- unit dans UnitCatalog
- type valide
- quality != REJECTED → mesure utilisable

**Incertitude** :
- Quality score basé sur :
  - Nombre de corrections appliquées
  - Écart par rapport aux valeurs attendues
  - Fiabilité du device (si metadata disponible)

**Provenance** :
- Chaque correction tracée (type, description, avant/après)
- Source des règles de correction

**Tests requis** :
- Unitaires : normalisation simple, conversions
- Propriétés : idempotence (normaliser deux fois = même résultat)
- Bordures : valeurs extrêmes, unités invalides

**Mapping avec code existant** :
- Nouveau : aucun équivalent direct
- Partiel : logique de conversion dans UnitConverter
- Coefficients à migrer : règles d'arrondi

---

#### UnitConverter (existant)

**Statut** : Déjà implémenté dans UnitConverter.kt + UnitCatalog.kt  
**Architecture** : Très bonne — à conserver tel quel  
**Modifications** :
- Ajouter des unités forestières spécifiques (stère, m³/ha, tiges/ha)
- Ajouter des conversions de densité (g/cm³ ↔ t/m³)

**Mapping avec code existant** :
- Conserver : UnitConverter.kt, UnitCatalog.kt
- Étendre : ajouter unités manquantes

---

#### MeasurementValidator

**Interface** :
```kotlin
interface MeasurementValidator {
    /**
     * Valide une mesure (cohérence, plages, relations).
     * @param measurement Mesure à valider
     * @return ValidationResult avec warnings/errors
     */
    fun validate(measurement: NormalizedMeasurement): ValidationResult
    
    /**
     * Valide un ensemble de mesures (cohérence croisée).
     */
    fun validateSet(measurements: List<NormalizedMeasurement>): ValidationResult
}

data class ValidationResult(
    val isValid: Boolean,
    val warnings: List<ValidationWarning>,
    val errors: List<ValidationError>
)

data class ValidationWarning(
    val code: String,
    val message: String,
    val severity: WarningSeverity
)

data class ValidationError(
    val code: String,
    val message: String,
    val field: String?
)

enum class WarningSeverity { INFO, WARNING, ERROR }
```

**Dépendances** :
- UnitConverter
- SanityChecker (réutilise les bornes existantes)

**Algorithmes** :
- Validation des plages (via SanityChecker)
- Cohérence H/D (élancement)
- Cohérence V/G (hauteur de forme)
- Cohérence temporelle (timestamp croissant)

**Validation de domaine** :
- value dans plage valide ( SanityChecker)
- unit compatible avec type
- timestamp plausible (pas dans le futur)

**Incertitude** :
- N/A (validation binaire)

**Provenance** :
- Source de chaque règle de validation
- Référence normative (si applicable)

**Tests requis** :
- Unitaires : validation simple
- Propriétés : transitivité (validateSet = validate individuel + cohérence)
- Bordures : valeurs limites, incohérences

**Mapping avec code existant** :
- Remplace : SanityChecker.kt (partiellement)
- Réutilise : bornes et logique de SanityChecker
- Coefficients à migrer : toutes les bornes de SanityChecker

---

### 2.3 domain/dendrometry/

#### BasalAreaEngine

**Interface** :
```kotlin
interface BasalAreaEngine {
    /**
     * Calcule la surface terrière d'un arbre (G = π/4 × D²).
     * @param diamCm Diamètre à 1,30 m en cm
     * @return Surface terrière en m²
     */
    fun computeTreeBasalArea(diamCm: Double): Double
    
    /**
     * Calcule la surface terrière totale d'un peuplement.
     * @param diamCmList Liste des diamètres
     * @return Surface terrière totale en m²
     */
    fun computeStandBasalArea(diamCmList: List<Double>): Double
    
    /**
     * Calcule la surface terrière par hectare.
     * @param diamCmList Liste des diamètres
     * @param surfaceM2 Surface en m²
     * @return Surface terrière en m²/ha
     */
    fun computeBasalAreaPerHa(diamCmList: List<Double>, surfaceM2: Double): Double
}
```

**Dépendances** :
- Aucune (formule pure)

**Algorithmes** :
- G = π/4 × (D/100)² (D en cm, G en m²)
- Somme pour le peuplement
- Division par surface (en ha) pour G/ha

**Validation de domaine** :
- diamCm > 0
- surfaceM2 > 0
- G ≥ 0

**Incertitude** :
- Incertitude de mesure du diamètre (propagation)
- Incertitude de la surface (si mesurée)

**Provenance** :
- Formule normalisée AFNOR NF B53-005

**Tests requis** :
- Unitaires : calcul simple
- Propriétés : linéarité (G total = Σ G individuel)
- Bordures : diamètre = 0, surface = 0

**Mapping avec code existant** :
- Remplace : ForestryCalculator.computeG()
- Remplace : MartelageModels.computeStructureTriangle (partiellement)
- Coefficients à migrer : aucun (formule pure)

---

#### DiameterEngine

**Interface** :
```kotlin
interface DiameterEngine {
    /**
     * Calcule le diamètre moyen (arithmétique).
     */
    fun computeMeanDiameter(diamCmList: List<Double>): Double
    
    /**
     * Calcule le diamètre moyen pondéré par la surface terrière (Dg).
     * Dg = sqrt(Σ(D² × G) / ΣG)
     */
    fun computeBasalAreaWeightedDiameter(diamCmList: List<Double>): Double
    
    /**
     * Calcule le diamètre dominant (D100).
     * Moyenne des 100 plus gros arbres par hectare.
     */
    fun computeDominantDiameter(diamCmList: List<Double>, surfaceHa: Double): Double?
    
    /**
     * Calcule le diamètre à une hauteur donnée (taper function).
     * @param d130 Diamètre à 1,30 m
     * @param height Hauteur de mesure
     * @param targetHeight Hauteur cible
     * @param essenceCode Code essence (pour coefficients de défilement)
     * @return Diamètre à la hauteur cible
     */
    fun computeDiameterAtHeight(d130: Double, height: Double, targetHeight: Double, essenceCode: String): Double?
}
```

**Dépendances** :
- BasalAreaEngine
- SpeciesResolver (pour coefficients de défilement)

**Algorithmes** :
- Moyenne arithmétique : ΣD / n
- Dg : sqrt(Σ(D² × G) / ΣG)
- D100 : tri décroissant, prendre 100×surfaceHa, moyenne
- Taper function : modèle de défilement par essence (ex: D = D130 × (1 - a × (h/H)^b))

**Validation de domaine** :
- diamCm > 0
- surfaceHa > 0
- targetHeight ≤ height
- D ≥ 0

**Incertitude** :
- Incertitude de mesure
- Incertitude du modèle de défilement

**Provenance** :
- Dg : formule standard ONF
- D100 : définition ONF
- Taper function : coefficients par essence (Pardé & Bouchon 1988)

**Tests requis** :
- Unitaires : chaque calcul
- Propriétés : Dg ≥ Dm (théorique)
- Bordures : liste vide, surface = 0

**Mapping avec code existant** :
- Remplace : MartelageModels.computeHdom (partiellement)
- Remplace : ForestryCalculator (calculs de diamètre)
- Nouveau : taper function
- Coefficients à migrer : coefficients de défilement (à sourcer)

---

#### HeightEngine

**Interface** :
```kotlin
interface HeightEngine {
    /**
     * Calcule la hauteur moyenne (arithmétique).
     */
    fun computeMeanHeight(heightMList: List<Double>): Double
    
    /**
     * Calcule la hauteur dominante (Hdom).
     * Moyenne des hauteurs des 100 plus gros arbres par hectare.
     */
    fun computeDominantHeight(tiges: List<Tige>, surfaceHa: Double): Double?
    
    /**
     * Calcule la hauteur de Lorey (HLorey).
     * HLorey = Σ(G × H) / ΣG
     */
    fun computeLoreyHeight(diamCmList: List<Double>, heightMList: List<Double>): Double
    
    /**
     * Calcule la hauteur à un diamètre donné (inverse taper function).
     */
    fun computeHeightAtDiameter(d130: Double, targetDiam: Double, essenceCode: String): Double?
    
    /**
     * Estime la hauteur depuis le diamètre (modèle allométrique).
     * @param diamCm Diamètre à 1,30 m
     * @param essenceCode Code essence
     * @return Hauteur estimée en m
     */
    fun estimateHeightFromDiameter(diamCm: Double, essenceCode: String): Double?
}
```

**Dépendances** :
- BasalAreaEngine
- DiameterEngine
- SpeciesResolver (pour coefficients allométriques)

**Algorithmes** :
- Hdom : tri par diamètre décroissant, prendre 100×surfaceHa, moyenne des hauteurs
- HLorey : Σ(G × H) / ΣG
- H/D : modèle allométrique par essence (ex: H = a × D^b)
- Inverse taper : inversion du modèle de défilement

**Validation de domaine** :
- heightM > 0
- surfaceHa > 0
- H ≥ 0

**Incertitude** :
- Incertitude de mesure
- Incertitude du modèle allométrique

**Provenance** :
- Hdom : définition ONF
- HLorey : formule standard
- H/D : coefficients par essence (tables de production ONF)

**Tests requis** :
- Unitaires : chaque calcul
- Propriétés : Hdom ≥ Hm (théorique)
- Bordures : hauteurs manquantes (null)

**Mapping avec code existant** :
- Remplace : ExpertForestryCalculator.computeHdom()
- Remplace : MartelageModels.computeHdom()
- Remplace : EnhancedForestryCalculator.estimateHauteurFromDiameter()
- Coefficients à migrer : coefficients allométriques (déjà dans ExpertForestryCalculator)

---

#### DensityEngine

**Interface** :
```kotlin
interface DensityEngine {
    /**
     * Calcule la densité de tiges (tiges/ha).
     * @param nTiges Nombre de tiges
     * @param surfaceM2 Surface en m²
     * @return Densité en tiges/ha
     */
    fun computeDensity(nTiges: Int, surfaceM2: Double): Double
    
    /**
     * Calcule la densité par classe de diamètre.
     */
    fun computeDensityByClass(diamCmList: List<Double>, classes: List<Int>, surfaceM2: Double): Map<Int, Double>
    
    /**
     * Calcule l'indice de densité (Reineke, SDI, etc.).
     * @param nTiges Nombre de tiges
     * @param dg Diamètre moyen pondéré par G
     * @param surfaceM2 Surface en m²
     * @return Indice de densité
     */
    fun computeDensityIndex(nTiges: Int, dg: Double, surfaceM2: Double): Double
}
```

**Dépendances** :
- DiameterEngine (pour Dg)

**Algorithmes** :
- Densité : N / surface (en ha)
- Par classe : groupement par classe, comptage, division
- SDI (Reineke) : N × (Dg/25)^1.605

**Validation de domaine** :
- nTiges ≥ 0
- surfaceM2 > 0
- dg > 0
- Densité ≥ 0

**Incertitude** :
- Incertitude de comptage
- Incertitude de surface

**Provenance** :
- SDI : Reineke (1933)

**Tests requis** :
- Unitaires : chaque calcul
- Propriétés : Σ densité par classe = densité totale
- Bordures : surface = 0, nTiges = 0

**Mapping avec code existant** :
- Remplace : PeuplementAvantCoupeCalculator (partiellement)
- Remplace : MartelageModels (partiellement)
- Nouveau : SDI
- Coefficients à migrer : exposant Reineke (1.605)

---

#### SamplingStatisticsEngine

**Interface** :
```kotlin
interface SamplingStatisticsEngine {
    /**
     * Calcule les statistiques d'échantillonnage (placette → peuplement).
     * @param sampleData Données de l'échantillon
     * @param totalSurfaceM2 Surface totale du peuplement
     * @param sampleSurfaceM2 Surface de l'échantillon
     * @return Statistics avec extrapolation
     */
    fun computeStatistics(
        sampleData: SampleData,
        totalSurfaceM2: Double,
        sampleSurfaceM2: Double
    ): SamplingStatistics
    
    /**
     * Calcule l'intervalle de confiance d'une moyenne.
     * @param values Liste des valeurs
     * @param confidenceLevel Niveau de confiance (0.95 = 95%)
     * @return Intervalle [min, max]
     */
    fun computeConfidenceInterval(values: List<Double>, confidenceLevel: Double): ConfidenceInterval
}

data class SampleData(
    val nTiges: Int,
    val diamCmList: List<Double>,
    val heightMList: List<Double>,
    val volumeM3List: List<Double>
)

data class SamplingStatistics(
    val nTigesSample: Int,
    val nTigesExtrapolated: Int,
    val gSample: Double,
    val gExtrapolated: Double,
    val vSample: Double,
    val vExtrapolated: Double,
    val dmSample: Double,
    val dmExtrapolated: Double,
    val confidenceInterval: ConfidenceInterval?
)

data class ConfidenceInterval(
    val mean: Double,
    val lower: Double,
    val upper: Double,
    val level: Double
)
```

**Dépendances** :
- BasalAreaEngine
- DiameterEngine
- HeightEngine

**Algorithmes** :
- Extrapolation : ratio = totalSurface / sampleSurface
- Intervalle de confiance : t-distribution de Student

**Validation de domaine** :
- totalSurfaceM2 > sampleSurfaceM2 > 0
- confidenceLevel ∈ (0, 1)
- nTigesSample ≥ 1

**Incertitude** :
- Intervalle de confiance calculé
- Écart-type de l'échantillon

**Provenance** :
- Statistiques d'échantillonnage standard (Scheffer et al.)

**Tests requis** :
- Unitaires : extrapolation simple
- Propriétés : extrapolation linéaire (ratio constant)
- Bordures : sampleSurface = totalSurface (ratio = 1)

**Mapping avec code existant** :
- Nouveau : aucun équivalent direct
- Partiel : logique d'extrapolation dans MartelageModels
- Coefficients à migrer : aucun (formules statistiques standard)

---

### 2.4 domain/volume/

#### VolumeEquationRegistry

**Interface** :
```kotlin
interface VolumeEquationRegistry {
    /**
     * Enregistre une équation de volume.
     */
    fun register(equation: VolumeEquation)
    
    /**
     * Retourne toutes les équations disponibles.
     */
    fun getAll(): List<VolumeEquation>
    
    /**
     * Retourne les équations pour une essence donnée.
     */
    fun getByEssence(essenceCode: String): List<VolumeEquation>
    
    /**
     * Retourne une équation par ID.
     */
    fun getById(id: String): VolumeEquation?
}

data class VolumeEquation(
    val id: String,                    // ex: "ALGAN_1958_CH_SESSILE"
    val name: String,                  // ex: "Algan 1958 - Chêne sessile"
    val version: String,               // ex: "1.0"
    val method: VolumeMethod,          // SCHAEFFER_1E, ALGAN, etc.
    val formula: String,               // ex: "V = a × D^b × H^c"
    val variables: List<Variable>,     // D, H, etc.
    val coefficients: Map<String, Double>,  // a, b, c
    val validityDomain: ValidityDomain,
    val source: BibliographicSource,
    val uncertainty: UncertaintyInfo,
    val status: EquationStatus
)

data class Variable(
    val name: String,      // D, H, C, etc.
    val unit: String,      // cm, m, m
    val description: String
)

data class ValidityDomain(
    val diamMinCm: Double,
    val diamMaxCm: Double,
    val heightMinM: Double?,
    val heightMaxM: Double?,
    val essenceCodes: List<String>
)

data class BibliographicSource(
    val author: String,
    val year: Int,
    val title: String,
    val journal: String?,
    val doi: String?
)

data class UncertaintyInfo(
    val typicalError: Double,      // % d'erreur typique
    val rSquared: Double?,         // R² si disponible
    val sampleSize: Int?           // Taille de l'échantillon
)

enum class VolumeMethod {
    SCHAEFFER_1E, SCHAEFFER_2E, ALGAN, IFN_RAPIDE, IFN_LENT,
    SCHUMACHER_HALL, HUBER, SMALIAN, NEWTON, TARIFS_OWN
}

enum class EquationStatus { ACTIVE, DEPRECATED, EXPERIMENTAL }
```

**Dépendances** :
- Aucune (registre pur)

**Algorithmes** :
- Stockage en mémoire (Map<String, VolumeEquation>)
- Recherche par essence, par méthode, par ID

**Validation de domaine** :
- ID unique
- Coefficients présents pour toutes les variables
- ValidityDomain cohérent (min < max)

**Incertitude** :
- Stockée dans UncertaintyInfo

**Provenance** :
- Chaque équation a sa source bibliographique

**Tests requis** :
- Unitaires : enregistrement, recherche
- Propriétés : unicité des IDs
- Bordures : équation inconnue

**Mapping avec code existant** :
- Remplace : TarifData.kt (structure)
- Remplace : TarifModels.kt (énumérations)
- Nouveau : métadonnées complètes (source, incertitude, validité)
- Coefficients à migrer : tous les coefficients de TarifData.kt (~200)

---

#### VolumeMethodResolver

**Interface** :
```kotlin
interface VolumeMethodResolver {
    /**
     * Résout la méthode de volume appropriée pour un contexte donné.
     * @param context Contexte de calcul
     * @return VolumeMethod recommandée
     */
    fun resolve(context: VolumeMethodContext): VolumeMethodResolution
    
    /**
     * Résout la méthode avec fallback explicite.
     */
    fun resolveWithFallback(context: VolumeMethodContext, fallback: VolumeMethod): VolumeMethodResolution
}

data class VolumeMethodContext(
    val essenceCode: String,
    val diamCm: Double,
    val heightM: Double?,
    val region: FrenchRegion?,
    val standType: StandType?,      // régulier, irrégulier, taillis
    val userPreference: VolumeMethod?
)

data class VolumeMethodResolution(
    val method: VolumeMethod,
    val equationId: String?,
    val confidence: ResolutionConfidence,
    val reason: String,              // Pourquoi cette méthode ?
    val alternatives: List<VolumeMethod>
)

enum class StandType { REGULAR, IRREGULAR, COPPICE, MIXED }
```

**Dépendances** :
- VolumeEquationRegistry
- SpeciesResolver
- SpeciesGroupResolver

**Algorithmes** :
- Règles de résolution par essence (ex: Douglas → Algan)
- Règles par région (ex: IFN pour inventaires nationaux)
- Règles par type de peuplement (ex: Schaeffer pour peuplements réguliers)
- Fallback sur méthode par défaut (Algan)

**Validation de domaine** :
- essenceCode non vide
- diamCm > 0
- method toujours résolue (fallback)

**Incertitude** :
- Confidence basée sur :
  - Match exact essence
  - Match groupe
  - Fallback générique

**Provenance** :
- Source de chaque règle de résolution

**Tests requis** :
- Unitaires : résolution pour essences courantes
- Propriétés : transitivité (context → method → equation)
- Bordures : essence inconnue → fallback

**Mapping avec code existant** :
- Remplace : ForestryCalculator.resolveTarifMethod()
- Remplace : TarifCalculator.recommendedTarifNumero()
- Nouveau : règles plus riches (région, type de peuplement)
- Coefficients à migrer : règles de résolution actuelles

---

#### VolumeCalculationEngine

**Interface** :
```kotlin
interface VolumeCalculationEngine {
    /**
     * Calcule le volume d'un arbre.
     * @param request Requête de calcul
     * @return VolumeResult avec volume et métadonnées
     */
    fun calculate(request: VolumeCalculationRequest): VolumeResult
    
    /**
     * Calcule le volume pour une liste d'arbres (batch).
     */
    fun calculateBatch(requests: List<VolumeCalculationRequest>): List<VolumeResult>
}

data class VolumeCalculationRequest(
    val essenceCode: String,
    val diamCm: Double,
    val heightM: Double?,
    val method: VolumeMethod?,
    val equationId: String?,
    val coefFormOverride: Double?
)

data class VolumeResult(
    val volumeM3: Double,
    val method: VolumeMethod,
    val equationId: String,
    val equationName: String,
    val coefficients: Map<String, Double>,
    val validityCheck: ValidityCheck,
    val uncertainty: Double?,         // Incertitude absolue (m³)
    val relativeUncertainty: Double?,  // Incertitude relative (%)
    val source: BibliographicSource
)

data class ValidityCheck(
    val isValid: Boolean,
    val warnings: List<String>,
    val isExtrapolated: Boolean       // True si hors domaine de validité
)
```

**Dépendances** :
- VolumeEquationRegistry
- VolumeMethodResolver
- VolumeUncertaintyEngine

**Algorithmes** :
- Pattern Strategy : sélection de l'équation appropriée
- Calcul selon la formule (Schaeffer, Algan, Schumacher-Hall, etc.)
- Vérification du domaine de validité
- Calcul de l'incertitude (via VolumeUncertaintyEngine)

**Validation de domaine** :
- diamCm > 0
- heightM > 0 (si requis par la méthode)
- volumeM3 ≥ 0
- ValidityCheck : hors domaine → warning

**Incertitude** :
- Calculée par VolumeUncertaintyEngine

**Provenance** :
- Source de l'équation utilisée
- Coefficients appliqués

**Tests requis** :
- Unitaires : chaque méthode
- Propriétés : volume ≥ 0
- Bordures : diamètre = 0, hauteur manquante

**Mapping avec code existant** :
- Remplace : TarifCalculator.computeVolume()
- Remplace : ForestryCalculator.computeVolumeWithTarif()
- Remplace : ExpertForestryCalculator.schumacherHallVolume()
- Nouveau : métadonnées complètes, validité, incertitude
- Coefficients à migrer : tous les coefficients de TarifData.kt

---

#### VolumeUncertaintyEngine

**Interface** :
```kotlin
interface VolumeUncertaintyEngine {
    /**
     * Quantifie l'incertitude d'un calcul de volume.
     * @param context Contexte d'incertitude
     * @return UncertaintyResult
     */
    fun quantify(context: VolumeUncertaintyContext): UncertaintyResult
    
    /**
     * Propage l'incertitude à travers un calcul composé.
     * Méthode analytique (linéarisation) ou Monte Carlo.
     */
    fun propagate(inputs: List<UncertainValue>, operation: Operation): UncertainValue
}

data class VolumeUncertaintyContext(
    val equationId: String,
    val coefficients: Map<String, Double>,
    val inputs: Map<String, UncertainValue>,  // D, H avec incertitude
    val method: PropagationMethod
)

data class UncertaintyResult(
    val absoluteUncertainty: Double,    // m³
    val relativeUncertainty: Double,   // %
    val confidenceLevel: Double,        // 0.95 = 95%
    val breakdown: UncertaintyBreakdown
)

data class UncertainValue(
    val value: Double,
    val uncertainty: Double,            // Écart-type
    val distribution: DistributionType
)

enum class DistributionType { NORMAL, LOGNORMAL, UNIFORM }
enum class PropagationMethod { ANALYTICAL, MONTE_CARLO, BOOTSTRAP }
enum class Operation { ADDITION, MULTIPLICATION, POWER }

data class UncertaintyBreakdown(
    val coefficientUncertainty: Double,    // Incertitude des coefficients
    val measurementUncertainty: Double,     // Incertitude des mesures
    val modelUncertainty: Double,           // Incertitude du modèle
    val samplingUncertainty: Double?        // Incertitude d'échantillonnage
)
```

**Dépendances** :
- VolumeEquationRegistry

**Algorithmes** :
- **Méthode analytique** : δV/V = sqrt(Σ(∂V/∂xi × δxi/xi)²)
- **Monte Carlo** : échantillonnage des distributions d'entrée, calcul de V n fois, estimation de l'écart-type
- **Bootstrap** : rééchantillonnage des données d'entraînement

**Validation de domaine** :
- uncertainty ≥ 0
- confidenceLevel ∈ (0, 1)
- relativeUncertainty ≤ 1 (100%)

**Incertitude** :
- Breakdown par source (coefficients, mesure, modèle, échantillonnage)

**Provenance** :
- Source des incertitudes des coefficients (R², erreur standard)
- Source des incertitudes de mesure (précision des instruments)

**Tests requis** :
- Unitaires : propagation analytique simple
- Propriétés : incertitude ≥ 0
- Bordures : incertitude nulle (calcul exact)

**Mapping avec code existant** :
- Nouveau : aucun équivalent dans le code actuel
- Coefficients à migrer : incertitudes des coefficients (à sourcer)

---

### 2.5 domain/assortment/

#### StemSegmentationEngine

**Interface** :
```kotlin
interface StemSegmentationEngine {
    /**
     * Segment une tige en billons selon des règles de découpe.
     * @param stem Tige à segmenter
     * @param rules Règles de découpe
     * @return List de segments (billons)
     */
    fun segment(stem: Stem, rules: SegmentationRules): List<StemSegment>
    
    /**
     * Optimise la découpe pour maximiser la valeur.
     * Algorithme de type "cutting stock problem".
     */
    fun optimizeForValue(stem: Stem, priceCatalog: PriceCatalog): List<StemSegment>
}

data class Stem(
    val essenceCode: String,
    val diamCm: Double,
    val heightM: Double,
    val taperFunction: TaperFunction?,  // Fonction de défilement
    val defects: List<StemDefect>
)

data class StemSegment(
    val startIndex: Double,    // Hauteur de début (m)
    val endIndex: Double,      // Hauteur de fin (m)
    val lengthM: Double,
    val diamStartCm: Double,
    val diamEndCm: Double,
    val volumeM3: Double,
    val productClass: ProductClass?,
    val quality: WoodQualityGrade?
)

data class SegmentationRules(
    val minLengthM: Double,
    val maxLengthM: Double,
    val minDiamCm: Double,
    val maxTaper: Double,        // Défilement max (cm/m)
    val defectHandling: DefectHandling
)

enum class DefectHandling { IGNORE, CUT_AROUND, DOWNGRADE }
enum class ProductClass { LOG, PULP, FIREWOOD, ENERGY }
```

**Dépendances** :
- DiameterEngine (pour taper function)
- SpeciesResolver (pour défauts spécifiques)

**Algorithmes** :
- Segmentation simple : découpes à longueurs fixes
- Optimisation valeur : algorithme glouton ou programmation dynamique
- Gestion des défauts : contournement, déclassement

**Validation de domaine** :
- lengthM > 0
- diamStartCm ≥ diamEndCm (taper)
- volumeM3 ≥ 0

**Incertitude** :
- Incertitude sur la position des défauts
- Incertitude sur la taper function

**Provenance** :
- Source des règles de découpe (normes NF EN 1316/1927)

**Tests requis** :
- Unitaires : segmentation simple
- Propriétés : Σ volume segments = volume total
- Bordures : tige sans défauts, tige très courte

**Mapping avec code existant** :
- Nouveau : aucun équivalent direct
- Partiel : logique de "découpe" dans DecoupeCalculator (non lu)
- Coefficients à migrer : règles de découpe (à sourcer)

---

#### ProductClassifier

**Interface** :
```kotlin
interface ProductClassifier {
    /**
     * Classifie un segment en produit forestier.
     * @param segment Segment à classer
     * @param context Contexte de classification
     * @return ProductClassification
     */
    fun classify(segment: StemSegment, context: ClassificationContext): ProductClassification
    
    /**
     * Classifie une tige entière (meilleur produit principal).
     */
    fun classifyStem(stem: Stem, context: ClassificationContext): ProductClassification
}

data class ClassificationContext(
    val essenceCode: String,
    val region: FrenchRegion?,
    val marketConditions: MarketConditions?
)

data class ProductClassification(
    val product: ForestProduct,
    val grade: WoodQualityGrade,
    val confidence: Double,
    val alternatives: List<ForestProduct>
)

data class MarketConditions(
    val demand: Map<ForestProduct, DemandLevel>,
    val prices: Map<ForestProduct, Double>
)

enum class DemandLevel { HIGH, MEDIUM, LOW }
```

**Dépendances** :
- SpeciesResolver
- SpeciesGroupResolver
- WoodQualityGrade (existant)

**Algorithmes** :
- Règles de classification par essence × diamètre × qualité
- Table de correspondance (ex: Chêne A + D≥50cm → Mérain)
- Inférence depuis le groupe (feuillus vs résineux)

**Validation de domaine** :
- product toujours résolu (fallback BOIS_INDUSTRIE)
- grade toujours résolu (fallback C)
- confidence ∈ [0, 1]

**Incertitude** :
- Confidence basée sur :
  - Match exact des règles
  - Inférence depuis groupe
  - Fallback générique

**Provenance** :
- Source des règles de classification (normes NF EN 1316/1927)

**Tests requis** :
- Unitaires : classification pour essences courantes
- Propriétés : consistance (grade A → produits premium)
- Bordures : diamètre = 0, qualité inconnue

**Mapping avec code existant** :
- Remplace : WoodQualityGrade.ProductClassifier
- Remplace : ForestryCalculator (logique de classification)
- Nouveau : contexte enrichi (région, marché)
- Coefficients à migrer : règles de classification de WoodQualityGrade.kt

---

#### YieldLossEngine

**Interface** :
```kotlin
interface YieldLossEngine {
    /**
     * Calcule les pertes techniques pour un produit.
     * @param input Entrée (volume brut)
     * @param context Contexte de pertes
     * @return YieldLossResult
     */
    fun calculateLosses(input: YieldLossInput, context: YieldLossContext): YieldLossResult
    
    /**
     * Calcule le volume commercialisable.
     */
    fun calculateCommercialVolume(brutVolumeM3: Double, context: YieldLossContext): Double
}

data class YieldLossInput(
    val brutVolumeM3: Double,
    val essenceCode: String,
    val product: ForestProduct,
    val quality: WoodQualityGrade
)

data class YieldLossContext(
    val processingType: ProcessingType,
    val equipment: Equipment?
)

data class YieldLossResult(
    val brutVolumeM3: Double,
    val barkLossM3: Double,           // Écorce
    val slabbingLossM3: Double,       // Délutage
    val breakageLossM3: Double,       // Casse
    val otherLossM3: Double,          // Autres
    val commercialVolumeM3: Double,
    val yieldRatio: Double            // commercial / brut
)

enum class ProcessingType { SAWMILL, PULP, FIREWOOD, ENERGY }
enum class Equipment { MANUAL, MECHANIZED, HIGH_TECH }
```

**Dépendances** :
- SpeciesResolver
- SpeciesGroupResolver

**Algorithmes** :
- Pertes par type : pourcentage du volume brut
- Coefficients par essence × produit × qualité
- Table de rendement (ex: sciage chêne = 65% rendement)

**Validation de domaine** :
- brutVolumeM3 ≥ 0
- Chaque perte ≥ 0
- commercialVolumeM3 ≤ brutVolumeM3
- yieldRatio ∈ [0, 1]

**Incertitude** :
- Incertitude sur les coefficients de perte
- Variabilité selon l'équipement

**Provenance** :
- Source des coefficients (FCBA, ONF)

**Tests requis** :
- Unitaires : calcul simple
- Propriétés : Σ pertes + commercial = brut
- Bordures : volume = 0, pertes = 0

**Mapping avec code existant** :
- Nouveau : aucun équivalent direct
- Partiel : logique de "bois non trituration" dans PeuplementAvantCoupeCalculator
- Coefficients à migrer : coefficients de perte (à sourcer)

---

### 2.6 domain/valuation/

#### PriceCatalogResolver

**Interface** :
```kotlin
interface PriceCatalogResolver {
    /**
     * Résout le prix de référence pour un produit.
     * @param request Requête de prix
     * @return PriceResolution
     */
    fun resolve(request: PriceResolutionRequest): PriceResolution
    
    /**
     * Retourne le catalogue complet.
     */
    fun getCatalog(): PriceCatalog
}

data class PriceResolutionRequest(
    val essenceCode: String,
    val product: ForestProduct,
    val diamCm: Int,
    val quality: WoodQualityGrade?,
    val region: FrenchRegion?,
    val year: Int
)

data class PriceResolution(
    val pricePerM3: Double,
    val currency: String,
    val source: PriceSource,
    val confidence: ResolutionConfidence,
    val alternatives: List<PriceSource>
)

data class PriceCatalog(
    val entries: List<PriceEntry>,
    val version: String,
    val lastUpdate: String
)

data class PriceSource(
    val name: String,              // ex: "FBF 2025"
    val type: PriceSourceType,     // NATIONAL, REGIONAL, LOCAL
    val year: Int,
    val url: String?
)

enum class PriceSourceType { NATIONAL, REGIONAL, LOCAL, CUSTOM }
```

**Dépendances** :
- SpeciesResolver
- SpeciesGroupResolver

**Algorithmes** :
- Lookup dans PriceEntry (existant)
- Fallback sur DefaultProductPrices (existant)
- Résolution régionale (coefficients régionaux)

**Validation de domaine** :
- pricePerM3 ≥ 0
- year plausible (ex: 2000–2030)
- currency = "EUR"

**Incertitude** :
- Confidence basée sur :
  - Match exact (essence, produit, diamètre, qualité, région)
  - Match partiel
  - Fallback générique

**Provenance** :
- Source de chaque prix (FBF, ONF, observatoires régionaux)

**Tests requis** :
- Unitaires : résolution pour essences courantes
- Propriétés : prix ≥ 0
- Bordures : essence inconnue → fallback

**Mapping avec code existant** :
- Remplace : PriceCalculator.findBasePrice()
- Remplace : ProPricingEngine.findBasePrice()
- Nouveau : métadonnées enrichies (source, confidence)
- Coefficients à migrer : tous les PriceEntry

---

#### QualityAdjustmentEngine

**Interface** :
```kotlin
interface QualityAdjustmentEngine {
    /**
     * Calcule le coefficient d'ajustement qualité.
     * @param essenceCode Code essence
     * @param grade Grade qualité
     * @return Coefficient (ex: A = 2.5, C = 1.0)
     */
    fun getQualityCoefficient(essenceCode: String, grade: WoodQualityGrade): Double
    
    /**
     * Ajuste un prix selon la qualité.
     */
    fun adjustPrice(basePrice: Double, essenceCode: String, grade: WoodQualityGrade): Double
}
```

**Dépendances** :
- Aucune (coefficients hardcodés ou chargés)

**Algorithmes** :
- Table de coefficients par essence × grade (existant dans PriceCalculator)
- Fallback sur wildcard "*"

**Validation de domaine** :
- coefficient ≥ 0
- grade ∈ {A, B, C, D}

**Incertitude** :
- N/A (coefficient déterministe)

**Provenance** :
- Source des coefficients (NF EN 1316-1, NF EN 1927)

**Tests requis** :
- Unitaires : résolution pour essences courantes
- Propriétés : coefficient(A) ≥ coefficient(B) ≥ coefficient(C) ≥ coefficient(D)
- Bordures : essence inconnue → fallback

**Mapping avec code existant** :
- Rem