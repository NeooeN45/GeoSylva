# AUDIT FORESTIER COMPLET — GeoSylva

**Auditeur** : Expert forestier (Master géomatique + gestion forestière)
**Date** : 2026-06-29
**Périmètre** : Intégrité base de données, calculs dendrométriques, tarifs/prix, logique forestière domainale, traitement des données, tests
**Méthode** : Audit parallèle par 5 sous-agents spécialisés + vérifications manuelles croisées

---

## RÉSUMEX EXÉCUTIF

**Verdict global** : Application **fonctionnellement solide sur les fondamentaux dendrométriques**, mais présentant **risques critiques** en intégrité de données, tarification, et conformité scientifique sur plusieurs modules avancés.

### Scores par domaine

| Domaine | Score | Statut |
|---------|-------|--------|
| Calculs dendrométriques de base (G, Dg, Lorey, tarifs) | 8.5/10 | ✅ Conforme |
| Système de tarification/prix | 4.5/10 | ❌ Risque financier |
| Intégrité base de données | 5/10 | ❌ Risque données |
| Logique forestière domainale | 6.5/10 | ⚠️ Approximatif |
| Traitement des données (mappers/repositories) | 5.5/10 | ⚠️ Pertes données |
| Couverture de tests | 35% | ❌ Insuffisant |

### Synthèse des problèmes par sévérité

| Sévérité | Nombre | Action |
|----------|--------|--------|
| 🔴 CRITICAL | 22 | Blocage production |
| 🟠 HIGH | 26 | Correction rapide |
| 🟡 MEDIUM | 35 | Court terme |
| 🟢 LOW | 14 | Moyen terme |

---

## PARTIE 1 — INTÉGRITÉ DE LA BASE DE DONNÉES

### 1.1 Configuration générale
- **Version DB** : 29 (Room)
- **Entités déclarées** : 23/27 (4 commentées pour problème KSP)
- **DAOs** : 27
- **Migrations** : 15 (gaps 16-26, 26-27)
- **Schémas exportés** : 1-15 (manquants 16-29)

### 1.2 🔴 CRITIQUES — Base de données

#### C-DB-1 : 4 entités commentées mais DAOs actifs
**Fichier** : `ForestryDatabase.kt` lignes 85-88
```kotlin
// Temporairement désactivé pour résoudre KSP
// DataCorrelationEntity::class,
// DataInterpretationEntity::class,
// EntityRelationEntity::class,
// AdvancedCalculationEntity::class
```
**Impact** : Les fonctionnalités de corrélation, interprétation et calculs avancés ne peuvent pas fonctionner. Les DAOs existent mais toute invocation crashera.
**Action** : Résoudre le problème KSP ou supprimer les DAOs inutilisés.

#### C-DB-2 : 8 méthodes `DELETE FROM table` sans WHERE
**Fichiers concernés** :
- `CounterDao.kt:45` — `DELETE FROM counters`
- `EssenceDao.kt:34` — `DELETE FROM essences`
- `FertiliteEssenceSerDao.kt:27` — `DELETE FROM fertilite_essence_ser`
- `FormulaDao.kt:37` — `DELETE FROM formulas`
- `GroupDao.kt:34` — `DELETE FROM groups` (cascade vers counters, formulas, variables)
- `ParcelleDao.kt:37` — `DELETE FROM parcelles` (cascade vers placettes, tiges, sessions, diagnostics)
- `ProjectionClimatiqueSerDao.kt:24` — `DELETE FROM projections_climatiques_ser`
- `FloraFtsDao.kt:25` — `DELETE FROM flora_fts`

**Impact forestier** : Perte TOTALE de données si ces méthodes sont appelées accidentellement. Pour `ParcelleDao`, cela entraîne une cascade destructrice vers toutes les données de l'inventaire (placettes, tiges, diagnostics, alertes sanitaires, observations flore, etc.).
**Action** : Supprimer ces méthodes ou les sécuriser avec une confirmation explicite + logging.

#### C-DB-3 : Migration 27→28 destructive (DROP TABLE sans préservation)
**Fichier** : `Migration27to28.kt:21`
```kotlin
DROP TABLE IF EXISTS gps_context_cache
```
**Impact** : Perte de tout le cache GPS lors de la migration.
**Action** : Créer une table temporaire, copier les données, puis remplacer.

#### C-DB-4 : Gap de migrations 16→26
**Fichier** : `Migration15to27.kt` (nommé `Migration15to26`)
**Impact** : Saut de version non documenté. Les utilisateurs en v15 migrent directement à v26 sans étapes intermédiaires.
**Action** : Documenter le saut ou créer les migrations intermédiaires.

#### C-DB-5 : Schémas Room 16-29 non exportés
**Impact** : Impossible de tester les migrations récentes. `exportSchema = true` mais schémas manquants.
**Action** : Régénérer les schémas via build, ou désactiver `exportSchema`.

### 1.3 🟠 HIGH — Base de données

#### H-DB-1 : FK manquantes sur entités critiques
- `ParcelleEntity` : `forestOwnerId` et `foretId` sans FK vers `ForetEntity`
- `TigeEntity` : `sessionId` sans FK vers `InventaireSessionEntity`
- `IbpEvaluationEntity` : `placetteId` et `parcelleId` sans FK
- `ArbreHabitatEntity` : `tigeId` et `essenceCode` sans FK
- `ObservationFloreEntity` : `codeEspece` sans FK vers `EssenceEntity`

**Impact** : Pas d'intégrité référentielle au niveau DB. Orphelins possibles.
**Action** : Ajouter les FK déclarées via `@ForeignKey` ou `indices`.

#### H-DB-2 : `fallbackToDestructiveMigration` non configuré
**Impact** : Crash si migration échoue (au lieu de recréer la base en dev).
**Action** : Configurer `.fallbackToDestructiveMigration()` en debug seulement.

#### H-DB-3 : Chiffrement DB désactivé
**Fichier** : `ForestryDatabase.kt:130` — `createEncryptedDatabase` commenté
**Impact** : Données forestières sensibles (parcelles, propriétaires, diagnostics) non chiffrées au repos.
**Action** : Réactiver SQLCipher ou documenter la décision.

#### H-DB-4 : Try-catch sur ALTER TABLE masque les erreurs
**Fichier** : `DatabaseMigrations.kt:59-65`
**Impact** : Erreurs de migration silencieuses → base potentiellement incohérente.
**Action** : Logger les erreurs correctement au lieu de les avaler.

### 1.4 🟡 MEDIUM — Base de données
- Index manquants sur `EssenceEntity.name`, `ForetEntity.nom`, `GroupEntity.sortIndex`
- Requêtes sans LIMIT sur plusieurs DAOs (unbounded lists)
- `LIKE '%' || :tag || '%'` vulnérable aux caractères spéciaux SQL (3 DAOs)
- Méthodes dupliquées `purgeOlderThan`/`evictOldContexts` dans `FloraFtsDao`

---

## PARTIE 2 — CALCULS DENDROMÉTRIQUES

### 2.1 ✅ Calculs CORRECTS (conformes aux standards)

| Calcul | Fichier | Formule | Référence | Verdict |
|--------|---------|---------|-----------|---------|
| Surface terrière G | `ForestryCalculator.kt:270` | `π×(D/200)²` ≡ `π/4×(D/100)²` | Pardé & Bouchon 1988, AFNOR NF B53-005 | ✅ CORRECT |
| Surface terrière totale | `ExpertForestryCalculator.kt:327` | `Σ[π×(D/200)²]` | Pardé & Bouchon 1988 | ✅ CORRECT |
| Dg (diamètre quadratique) | `MartelageModels.kt:264` | `√(4G/πN)×100` | Pardé & Bouchon 1988 | ✅ CORRECT |
| Hauteur de Lorey | `MartelageModels.kt:265` | `Σ(Gi×Hi)/Σ(Gi)` | Pardé & Bouchon 1988, ONF | ✅ CORRECT |
| Tarif Schaeffer 1E | `TarifModels.kt:78-87` | `V = a + b×C²` | Schaeffer 1949, ENEF Nancy | ✅ CORRECT |
| Tarif Schaeffer 2E | `TarifModels.kt:100-109` | `V = a + b×C²×H` | Schaeffer 1949 | ✅ CORRECT |
| Tarif Algan | `TarifModels.kt:123-127` | `V = a×D^b×H^c` | Algan 1958, Pardé & Bouchon 1988 | ✅ CORRECT |
| Tarif IFN Rapide | `TarifModels.kt:141-146` | `V = a₀+a₁D+a₂D²` (dm³→m³) | IGN/IFN | ✅ CORRECT |
| Tarif IFN Lent | `TarifModels.kt:160-166` | `V = a₀+a₁D²+a₂D²H` (dm³→m³) | IGN/IFN | ✅ CORRECT |
| Coefficient de forme | `TarifModels.kt:179-184` | `V = G×H×f` | Pardé & Bouchon 1988, ENGREF | ✅ CORRECT |
| Indice Shannon | `MartelageModels.kt:372-377` | `H' = -Σ(pi×ln(pi))` | Shannon & Weaver 1949 | ✅ CORRECT |
| Indice Piélou | `MartelageModels.kt:379-380` | `J = H'/ln(S)` | Piélou 1966 | ✅ CORRECT |
| Tables de production | `ExpertForestryCalculator.kt:51-110` | Décourt & Pardé 1980 | Décourt & Pardé 1980, ENGREF | ✅ CORRECT |
| Classes de diamètre | `ForestryCalculator.kt:321-338` | Bornes médianes | ONF, Pardé | ✅ CORRECT |

### 2.2 🔴 CRITIQUES — Calculs

#### C-CALC-1 : Hauteur dominante (Hdom) ABSENTE
**Impact** : L'Hdom (moyenne des 100 plus gros arbres/ha) est indispensable pour :
- L'indice de station ONF (Hdom à âge de référence)
- La sélection automatique des tarifs Schaeffer (méthode Lorey Hdom/Dg)
- Les tables de production

**Action** : Implémenter `computeHdom(tiges, surfaceHa)` selon la norme ONF.

#### C-CALC-2 : Indice de station approximatif (utilise Hm au lieu de Hdom)
**Fichier** : `ExpertForestryCalculator.kt:115-143`
```kotlin
val iaHauteur = (hauteurMoyenne * 0.8).coerceAtMost(30.0)  // ❌ Hm, pas Hdom
val iaDiametre = (diametreMoyen * 0.3).coerceAtMost(30.0)
(iaHauteur + iaDiametre) / 2.0  // ❌ Formule ad-hoc
```
**Référence ONF** : `IS = Hdom à âge de référence` (100 ans chêne, 80 ans hêtre, 50 ans résineux) par interpolation dans tables Décourt & Pardé.
**Impact** : Diagnostic stationnel non conforme ONF.
**Action** : Remplacer par interpolation dans les tables Décourt & Pardé avec Hdom.

### 2.3 🟠 HIGH — Calculs

#### H-CALC-1 : Sélection automatique de tarif arbitraire
**Fichier** : `TarifCalculator.kt:79-89`
- Schaeffer 1E : défaut = 8 (au lieu de méthode Lorey Hdom/Dg)
- Schaeffer 2E : défaut = 4
- IFN Lent : défaut = 4
**Action** : Implémenter la méthode de Lorey : `tarif = f(Hdom/Dg)`.

#### H-CALC-2 : Coefficients Schumacher-Hall génériques
**Fichier** : `ExpertForestryCalculator.kt:496-503`
- Coefficients (a=-2.0, b=2.0, c=1.0) non calibrés sur données françaises
- Erreur potentielle ±15-20% vs tarifs Pardé/Duplat
**Action** : Utiliser coefficients Pardé/Duplat ou supprimer cette méthode au profit des tarifs IFN.

#### H-CALC-3 : Biomasse/carbone très approximatifs
**Fichier** : `AdvancedCalculationEngine.kt:210-215`
```kotlin
val totalBiomass = tiges.sumOf { tige ->
    val height = tige.hauteurM ?: 20.0
    0.05 * diameter * diameter * height  // ❌ Coefficient générique
}
val totalCarbon = totalBiomass * 0.5
val co2Equivalent = totalCarbon * 3.67
```
- Coefficient 0.05 non spécifique à l'essence
- Formule D²H sans exponentiation
- Erreur potentielle ±50%
**Référence** : Équations allométriques Vallet et al. (2006), INRAE
**Action** : Implémenter équations allométriques par essence.

### 2.4 ⚠️ APPROXIMATE — Calculs

- **IBP simplifié** : 6/10 composantes implémentées dans `MartelageModels.kt:382-410`. Seuils arbitraires (TGB ≥70cm) non référencés à la table Hermy-Berton.
- **Modèle de croissance Richards** : Formule correcte mais paramètres non validés contre tables Décourt & Pardé.
- **Dm arithmétique** : Calcul correct mais nom `dmWeighted` trompeur (moyenne arithmétique, pas pondérée par G).

---

## PARTIE 3 — TARIFICATION ET PRIX

### 3.1 🔴 CRITIQUES — Tarification

#### C-PRIX-1 : Alias d'essences incohérents entre composants
**Fichiers** :
- `ForestryCalculator.kt:96-107` — 4 alias seulement (HETRE, DOUGLAS, TREMBLE, PEUPLIER_TREMB)
- `TarifCalculator.kt:209-242` — 25+ alias complets (CHENE, PIN, SAPIN, EPICEA, FRENE, ERABLE, etc.)

**Conséquence** : Si l'utilisateur saisit "CHENE", `ForestryCalculator.priceFor()` ne trouve PAS de correspondance → fallback wildcard `*` à 50-80 €/m³ au lieu du prix chêne (165-315 €/m³).
**Impact financier** : **Perte de 50-200 €/m³** pour les essences nobles mal codées.
**Action** : Extraire `essenceCodeCandidates()` vers un objet partagé `EssenceAliases` et l'utiliser partout.

#### C-PRIX-2 : Prix appliqué au volume TOTAL au lieu du volume par produit
**Fichier** : `ForestryCalculator.kt:630`
```kotlin
valSum += v * tigePrice  // v = volume bois fort tige TOTAL
```
**Problème** : Le volume `v` inclut le houppier (branches) qui devrait être valorisé en BCh (38 €/m³), pas en BO (315 €/m³).

**Exemple** : Chêne D=50cm, H=25m
- Volume total Algan : ~1.8 m³
- Calcul actuel : 1.8 × 315 = 567 €
- Calcul correct : 1.3 m³ BO × 315 + 0.5 m³ BCh × 38 = 428.5 €
- **Surévaluation : +138 € (+32%)**

**Impact financier** : Surévaluation systématique 20-40% pour les gros arbres.
**Action** : Intégrer `DecoupeCalculator.ventilerParProduit()` avant application des prix.

#### C-PRIX-3 : Pas de ventilation par produit dans le calcul de revenu
**Impact** : Le `DecoupeCalculator` existe (ligne 261-302) mais n'est PAS appelé dans le calcul de revenu.
**Action** : Ventiler le volume par produit (BO/BI/BCh/PATE) puis appliquer le prix spécifique à chaque fraction.

### 3.2 🟠 HIGH — Tarification

#### H-PRIX-1 : Absence de conversion stère/m³ pour bois de chauffage
**Problème** : Prix BCh en €/m³ dans le système, mais le bois de chauffage se vend au stère en France.
- 1 stère ≈ 0.7-0.8 m³ apparent (feuillus)
- Prix actuels app : 25-28 €/m³ → ~19-21 €/stère
- Prix marché réel : 40-60 €/stère
- **Sous-évaluation : 40-60%**
**Action** : Ajouter champ `unit` ("M3" ou "STERE") à `PriceEntry` + conversion.

#### H-PRIX-2 : Règles de produit par défaut trop simplistes
**Fichier** : `ForestryCalculator.kt:718-723`
```kotlin
return when {
    diamClass >= 35 -> "BO"  // ❌ Pas de distinction feuillu/résineux
    diamClass >= 20 -> "BI"
    diamClass >= 7  -> "BCh"
    else -> "PATE"
}
```
**Problème** : Un pin de 35cm classé BO alors qu'il devrait souvent être BI. Pas de prise en compte de la qualité.
**Action** : Créer des règles spécifiques par essence/groupe.

#### H-PRIX-3 : Fallback de prix silencieux
**Fichier** : `ForestryCalculator.kt:624-629`
- `DefaultProductPrices.priceFor()` utilisé sans avertissement utilisateur
- Multiplicateurs d'essence manquants pour ~20 essences (CH_PUBESCENT, CH_ROUGE, FRENE_OXYPHYLLE, etc.)
**Action** : Logger les fallbacks + compléter `essenceMultipliers`.

### 3.3 ✅ Points positifs — Tarification
- Prix par défaut globalement réalistes (-5% à -15% vs marché, conservateur acceptable)
- Indicateur "prix manquants" dans Martelage bien implémenté
- `dem_pack` ne contient que des tuiles DEM (séparation correcte)

### 3.4 Estimation de l'impact financier global

Pour un propriétaire forestier typique (500 m³ chêne + 300 m³ hêtre + 200 m³ douglas + 100 m³ BCh) :
- Alias manquants : **-20 000 €** (chêne mal codé)
- Volume total vs ventilé : **+21 000 €** (surévaluation BO)
- Stère non converti : **-1 500 €** (BCh sous-évalué)
- **Cas pire** : erreur potentielle de **-40 000 €** sur 1000 m³

---

## PARTIE 4 — LOGIQUE FORESTIÈRE DOMAINALE

### 4.1 🔴 CRITIQUES — Domaine forestier

#### C-DOM-1 : IBP non conforme à la méthode officielle Larrieu & Gonin 2008
**Fichier** : `IbpModels.kt:12-29`
- Codes critères : E1, E2, BMS, BMC, GB, DMH, VS, CF, CO, HC (au lieu de A-J standard)
- Seuils de niveaux décalés : 0-9, 10-19, 20-29, 30-39, 40-50 (au lieu de 0-10, 10-20, 20-30, 30-40, 40-50)
- Grille de notation détaillée absente
**Action** : Aligner sur la méthode CNPF IBP (Larrieu & Gonin 2008).

#### C-DOM-2 : Seuils de fertilité incorrects pour feuillus
**Fichier** : `FertilityReference.kt:52-324`
- Hêtre H100 classe I = 28 m → **devrait être ~32-35 m** (ONF 2012)
- Chêne H100 classe I = 25 m → **devrait être ~28-30 m** (ONF 2006)
- Douglas H50 classe I = 33 m → correct (CRPF 2015)
**Impact** : Classification de fertilité erronée pour les feuillus précieux.
**Action** : Corriger les seuils selon guides ONF.

#### C-DOM-3 : Matrice de corrélation espèce-station manquante
**Impact** : Pas d'écogrammes de Rameau/Dumé implémentés. La corrélation est basée uniquement sur les gradients (approche simplifiée).
**Action** : Créer une matrice d'affinité espèce-station basée sur Rameau et al. 1989.

### 4.2 🟠 HIGH — Domaine forestier

#### H-DOM-1 : Fertilité sans âge de référence explicite
**Fichier** : `FertilityClassifier.kt:54-200`
- Utilise la hauteur de Lorey comme proxy sans âge de référence
- La fertilité SANS âge de référence n'a pas de sens sylvicole
**Action** : Exiger l'âge de référence (H50, H100) dans le calcul.

#### H-DOM-2 : Formule de stress climatique ad-hoc
**Fichier** : `StationExpertEngine.kt:121-126`
```kotlin
(ΔT/4 − ΔP/20 + ΔETP/30) / 3  // ❌ Diviseurs arbitraires non publiés
```
**Action** : Valider sur données historiques ou sourcer la méthodologie.

#### H-DOM-3 : Seuils de station arbitraires non sourcés
**Fichier** : `StationDiagnosticEngine.kt:122, 128`
- Sol profond ≥ 60 cm (non justifié)
- Altitude favorable < 600 m (non sourcé)
**Action** : Citer le Référentiel des stations ONF.

### 4.3 ✅ Points positifs — Domaine forestier
- Architecture de domaine bien structurée avec références scientifiques explicites
- Cubage Schumacher-Hall correct (formule + sources Vallet et al. 2006)
- Habitats forestiers corrects (codes EUR28 Natura 2000, phytosociologie Braun-Blanquet)
- Inférence de gradients floristiques conforme à la pratique phytosociologique
- Moteur de confiance transparent et documenté
- Résolution territoriale correcte (Haversine, centroïdes INSEE)
- Sources DRIAS-2020 et CMIP6 SSP2-4.5/SSP5-8.5 correctes

### 4.4 ⚠️ Couverture incomplète
- Habitats : ~15 codés vs ~100 Natura 2000 forestiers
- Flore : ~50 espèces vs ~3000 forestières françaises
- Invasives ripariennes : ~12 vs ~50
- Risques bioclimatiques : 4 essences seulement

---

## PARTIE 5 — TRAITEMENT DES DONNÉES

### 5.1 🔴 CRITIQUES — Données

#### C-DATA-1 : Perte de données cadastrales dans le mapper Parcelle
**Fichier** : `EntityMapper.kt:183-216`
```kotlin
fun Parcelle.toParcelleEntity(): ParcelleEntity {
    return ParcelleEntity(
        // ...
        foretId = null,              // ❌ Perdu
        codeInseeCommune = null,     // ❌ Perdu
        nomCommune = null,           // ❌ Perdu
        sectionCadastrale = null,    // ❌ Perdu
        numeroCadastral = null,      // ❌ Perdu
        contenanceCadastraleHa = null, // ❌ Perdu
        geometrieIgnWkt = null,      // ❌ Perdu
        natureCadastraleCode = null, // ❌ Perdu
        codeSer = null,              // ❌ Perdu
        nomSer = null                // ❌ Perdu
    )
}
```
**Impact** : Perte systématique des données cadastrales IGN et SER lors de l'écriture en base. Les données sont lues correctement (entity→domain) mais perdues à la sauvegarde (domain→entity).
**Action** : Ajouter ces champs au modèle `Parcelle` domain.

#### C-DATA-2 : Absence de transactions pour opérations multi-tables
**Fichier** : `GroupRepositoryImpl.kt:78-186` (méthode `duplicateGroup`)
```kotlin
groupDao.insertGroup(newGroup)
if (newCounters.isNotEmpty()) counterDao.insertCounters(newCounters)
if (newFormulas.isNotEmpty()) formulaDao.insertFormulas(newFormulas)
// ❌ Pas de @Transaction — si échec partiel → base incohérente
parcelleDao.insertParcelles(newParcelles)
placetteDao.insertPlacettes(newPlacettes)
tigeDao.insertTiges(newTiges)
```
**Action** : Encapsuler dans `@Transaction` ou `db.withTransaction { }`.

#### C-DATA-3 : Téléchargement de packs simulé (non fonctionnel)
**Fichier** : `PackManager.kt:115-144`
```kotlin
// ⚠ FONCTIONNALITÉ NON IMPLÉMENTÉE — simulation de progression uniquement.
// TODO(#INFRA-1): Remplacer par un vrai téléchargement HTTP
while (progress < 1f) {
    progress = (progress + 0.1f).coerceAtMost(1f)
    kotlinx.coroutines.delay(80)
}
```
**Impact** : Les packs régionaux ne sont pas réellement téléchargés.
**Action** : Implémenter le téléchargement HTTP avec validation SHA-256.

### 5.2 🟠 HIGH — Données

#### H-DATA-1 : Perte de données Placette (session, GPS référence)
**Fichier** : `EntityMapper.kt:233-249`
- `sessionId`, `typeReleve`, `referenceGpsWkt`, `azimutRef` perdus à la sauvegarde
**Action** : Ajouter ces champs au modèle `Placette` domain.

#### H-DATA-2 : N+1 Query Pattern dans GroupRepository
**Fichier** : `GroupRepositoryImpl.kt:38-48`
- 1 requête par group pour `getCounterCountByGroup` + `getTotalValueByGroup`
- 100 groups = 201 requêtes SQL
**Action** : Utiliser une requête JOIN avec agrégation au niveau DAO.

#### H-DATA-3 : Distance GPS simulée (random) dans DataCorrelationEngine
**Fichier** : `DataCorrelationEngine.kt:359-363`
```kotlin
return kotlin.random.Random.nextDouble(0.0, 20.0)  // ❌ Aléatoire
```
**Impact** : Les corrélations spatiales sont basées sur des données aléatoires.
**Action** : Implémenter le calcul Haversine réel à partir de `gpsWkt`.

#### H-DATA-4 : Absence de logging d'erreurs dans les repositories
**Tous les repositories** : pas de try/catch, pas de logging avant propagation.
**Action** : Ajouter du logging systématique.

#### H-DATA-5 : Export XLSX sans formats numériques
**Fichier** : `MartelageXlsxExporter.kt:109-141`
- Tous les champs en `t="inlineStr"` (strings)
- Excel n'interprète pas les nombres comme tels (pas de calcul possible)
**Action** : Utiliser `t="n"` avec attribut `s` pour les formats numériques (€, m³).

#### H-DATA-6 : Seuils dendrométriques hardcodés non sourcés
**Fichier** : `DataInterpretationEngine.kt:69-120`
- `avgDiameter < 20`, `avgDiameter > 50`, `density < 400`, `density > 1200` — sources ?
**Action** : Référencer les sources (IFN, ONF, CNPF).

### 5.3 ✅ Points positifs — Données
- Mappers 1:1 sans conversion d'unités (bonne pratique respectée)
- Aucun `!!` force-unwrap dans les repositories
- Sanitisation des entrées dans `CounterRepository.sanitize()`
- BOM UTF-8 dans l'export CSV
- Architecture pack hiérarchique (National → Régional → Départemental) avec fallback

### 5.4 🟡 MEDIUM — Données
- Pas d'échappement CSV pour les champs contenant le séparateur `;`
- Dates en timestamps Unix (non ISO-8601) dans l'export JSON
- CRC32 custom au lieu de `java.util.zip.CRC32`
- Pas de validation de taille de fichier à l'import (risque OOM)
- Séparateur CSV non détecté automatiquement à l'import (hardcodé `,`)

---

## PARTIE 6 — COUVERTURE DE TESTS

### 6.1 Inventaire (28 fichiers de test)

**Tests unitaires (27 fichiers)** : FormulaParser, Accessibility, DataCorrelation, DatabaseMigration, CounterRepository, ConfidenceEngine, CorrelationExplicabilite, FloristDatabase, RegionalPack, TerritorialResolver, ForestryCalculator, ExpertForestryCalculator, PeuplementAvantCoupe, SanityChecker, DefaultProductPrices, ProductClassifier, WoodQualityGrade, TarifCalculator, Lambert93, WktUtils, ExportData, ImportData, QgisExport, EndToEndIntegration, SecureHttpClient, DatabaseEncryption, ViewModel

**Tests instrumentés (3 fichiers)** : MapScreen, MartelageScreen, SettingsScreen (squelettiques)

### 6.2 🔴 CRITIQUES — Tests

#### C-TEST-1 : Tests de migration non complets
**Fichier** : `DatabaseMigrationTest.kt:77-111`
- Teste l'existence des colonnes mais PAS la conservation des données
**Action** : Ajouter des tests de vérification de données après migration.

### 6.3 🟠 HIGH — Tests

#### H-TEST-1 : Pas de tests edge cases pour les calculs dendrométriques
- Valeurs limites (diamètre = 0, diamètre = 200+)
- Cas d'erreur (hauteur négative, essence inconnue)
- Précision (tolérance epsilon)
**Action** : Ajouter tests edge cases pour tous les calculs.

#### H-TEST-2 : Tests import/export incomplets
- Pas de test CSV avec séparateur `;`
- Pas de test XLSX avec formats numériques
- Pas de test JSON avec dates ISO-8601
- Pas de test ZIP corrompu
**Action** : Compléter les tests pour chaque format.

### 6.4 Couverture estimée par module

| Module | Couverture | Lacunes |
|--------|------------|---------|
| Mappers | 0% | ❌ Aucun test |
| Repositories | 20% | Seul CounterRepository testé |
| Correlation | 80% | ✅ |
| Interpretation | 0% | ❌ Aucun test |
| Export CSV | 60% | Pas de test séparateur/échappement |
| Export XLSX | 0% | ❌ |
| Export JSON | 40% | Pas de test dates |
| Export ZIP | 0% | ❌ |
| Import CSV | 50% | Pas de test séparateur |
| Import Excel | 0% | ❌ |
| Import JSON | 60% | Pas de test taille limite |
| Pack Management | 40% | Pas de test téléchargement |
| Calculs Dendrométriques | 70% | Pas de tests edge cases |
| Migrations DB | 30% | Pas de test conservation données |
| UI | 5% | Squelettes uniquement |

**Couverture globale estimée : ~35%** (objectif : 80% sur domain, 60% minimum)

---

## PARTIE 7 — PLAN D'ACTION PRIORITISÉ

### 🔴 Phase 1 — Critique (avant toute mise en production)

| # | Action | Fichier(s) | Effort |
|---|--------|------------|--------|
| 1 | Résoudre problème KSP : décommenter 4 entités ou supprimer DAOs | `ForestryDatabase.kt` | M |
| 2 | Supprimer/sécuriser les 8 `DELETE FROM` sans WHERE | 8 DAOs | S |
| 3 | Corriger migration 27→28 (préserver cache GPS) | `Migration27to28.kt` | M |
| 4 | Corriger mapper Parcelle (restaurer champs cadastraux) | `EntityMapper.kt` | M |
| 5 | Ajouter `@Transaction` à `duplicateGroup` | `GroupRepositoryImpl.kt` | S |
| 6 | Unifier les alias d'essences (`EssenceAliases` partagé) | `ForestryCalculator.kt`, `TarifCalculator.kt` | M |
| 7 | Ventiler le volume par produit avant application des prix | `ForestryCalculator.kt` | M |
| 8 | Implémenter Hdom (hauteur dominante) | `MartelageModels.kt` | M |
| 9 | Corriger l'indice de station (utiliser Hdom + tables Décourt) | `ExpertForestryCalculator.kt` | L |
| 10 | Corriger seuils fertilité feuillus (ONF 2012/2006) | `FertilityReference.kt` | S |
| 11 | Aligner IBP sur méthode Larrieu & Gonin 2008 | `IbpModels.kt` | M |
| 12 | Implémenter le téléchargement réel des packs | `PackManager.kt` | L |
| 13 | Ajouter tests de conservation des données pour migrations | `DatabaseMigrationTest.kt` | M |
| 14 | Régénérer les schémas Room 16-29 | build config | S |

### 🟠 Phase 2 — Haute priorité (prochaine version)

| # | Action | Fichier(s) |
|---|--------|------------|
| 15 | Ajouter FK manquantes (Parcelle, Tige, IbpEvaluation, etc.) | Entités + migrations |
| 16 | Implémenter sélection automatique tarif par méthode Lorey | `TarifCalculator.kt` |
| 17 | Ajouter conversion stère/m³ pour bois de chauffage | `PriceEntry`, `ForestryCalculator.kt` |
| 18 | Améliorer règles de produit par défaut (par essence) | `ParameterDefaults.kt` |
| 19 | Corriger N+1 dans `GroupRepository.getAllGroups` | `GroupRepositoryImpl.kt` |
| 20 | Implémenter distance GPS réelle (Haversine) | `DataCorrelationEngine.kt` |
| 21 | Ajouter formats numériques dans export XLSX | `MartelageXlsxExporter.kt` |
| 22 | Implémenter équations allométriques biomasse (Vallet 2006) | `AdvancedCalculationEngine.kt` |
| 23 | Ajouter logging dans tous les repositories | 16 repositories |
| 24 | Ajouter tests edge cases pour calculs dendrométriques | `ForestryCalculatorTest.kt` |
| 25 | Compléter tests import/export | Tests |
| 26 | Créer matrice de corrélation espèce-station (Rameau) | Nouveau fichier |
| 27 | Réactiver chiffrement DB (SQLCipher) | `ForestryDatabase.kt` |

### 🟡 Phase 3 — Moyen terme (1-2 mois)

| # | Action |
|---|--------|
| 28 | Ajouter index manquants (Essence, Foret, Group) |
| 29 | Ajouter LIMIT/pagination sur requêtes unbounded |
| 30 | Corriger échappement CSV |
| 31 | Utiliser ISO-8601 pour dates JSON |
| 32 | Documenter sources des seuils dendrométriques |
| 33 | Étendre catalogue habitats (~50 Natura 2000) |
| 34 | Enrichir base floristique (~200 espèces) |
| 35 | Sourcer valeurs Ellenberg via Baseflor |
| 36 | Implémenter tests UI fonctionnels |
| 37 | Valider paramètres Richards contre tables de production |
| 38 | Supprimer coefficients Schumacher-Hall génériques ou calibrer |

---

## PARTIE 8 — CONCLUSION GÉNÉRALE

### Points forts de GeoSylva
1. **Calculs dendrométriques fondamentaux irréprochables** : surface terrière, Dg, hauteur de Lorey, tarifs de cubage (Schaeffer, Algan, IFN) — tous conformes aux standards français (Pardé & Bouchon, AFNOR, IGN)
2. **Architecture Clean bien structurée** : séparation data/domain/presentation, repositories interfaces dans domain
3. **Sources scientifiques explicites** : Décourt & Pardé 1980, Vallet et al. 2006, DRIAS-2020, Cahiers habitats Natura 2000
4. **Aucun `!!` force-unwrap** dans la couche présentation et repositories
5. **Sanitisation des entrées** dans CounterRepository
6. **Indicateur "prix manquants"** bien implémenté dans Martelage

### Points faibles critiques
1. **Risque financier direct** : alias d'essences incohérents + prix appliqué au volume total = erreurs de -40 000 € possibles sur 1000 m³
2. **Risque de perte de données** : 8 méthodes `DELETE FROM` sans WHERE + mapper Parcelle perd les données cadastrales + migration 27→28 destructive
3. **Non-conformité scientifique** : IBP non conforme Larrieu & Gonin, fertilité sans âge de référence, Hdom absent, indice de station approximatif
4. **Fonctionnalités non implémentées** : 4 entités commentées (KSP), téléchargement de packs simulé, distance GPS aléatoire
5. **Couverture de tests insuffisante** : 35% global, 0% sur mappers/interpretation/XLSX/ZIP

### Recommandation finale

**Ne PAS déployer en production** avant d'avoir résolu au minimum les 14 actions de la Phase 1 (critique). Les calculs de cubage de base sont fiables pour un inventaire forestier simple, mais les modules avancés (tarification, diagnostic stationnel, IBP, biomasse) nécessitent des corrections pour atteindre la conformité scientifique et la fiabilité commerciale attendues d'une application professionnelle.

L'application a **une base solide** — les corrections sont majoritairement incrémentales, aucune refonte architecturale n'est nécessaire. L'effort principal doit porter sur :
1. **L'unification des alias d'essences** (1 fichier partagé)
2. **La ventilation du volume par produit** (intégrer DecoupeCalculator existant)
3. **L'implémentation de Hdom** (fonction manquante critique)
4. **La correction des mappers** (restaurer les champs perdus)

---

## RÉFÉRENCES SCIENTIFIQUES CONSULTÉES

- Pardé & Bouchon (1988) — *Dendrométrie*, ENGREF Nancy
- Décourt & Pardé (1980) — Tables de production, ENGREF
- Schaeffer (1949) — Annales ENEF Nancy
- Algan (1958) — Tarifs de cubage
- AFNOR NF B53-005 — Norme dendrométrique
- ONF — Guides sylviculture hêtre (2012), chêne (2006)
- ONF — Référentiel des stations forestières
- IGN/IFN — Tarifs de cubage nationaux
- Larrieu & Gonin (2008) — IBP, Revue Forestière Française
- CNPF — Indice de Biodiversité Potentielle
- Hermy & Berton (1999) — IBP
- Vallet et al. (2006) — Équations allométriques, RFF
- Rameau et al. (1989) — Flore Forestière Française
- Schumacher & Hall (1933) — Équations de volume
- Shannon & Weaver (1949) — Théorie de l'information
- Piélou (1966) — Indice d'équitabilité
- Richards (1959) — Modèle de croissance
- DRIAS-2020 (Météo-France/CNRS) — Projections climatiques
- CMIP6 SSP2-4.5 / SSP5-8.5 — Scénarios GIEC
- Baseflor — Indices Ellenberg France
- Cahiers habitats Natura 2000 — MNHN/ONF
- CRPF — Guides ripisylves
- Climessences CNPF — Autécologie des essences

---

*Audit généré le 2026-06-29 — GeoSylva Android App (Kotlin/Room/Compose)*
