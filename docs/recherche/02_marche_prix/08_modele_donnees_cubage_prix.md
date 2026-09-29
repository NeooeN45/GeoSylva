# Modèle de données du cubage, des produits et des prix pour GeoSylva

**Domaine** : `docs/recherche/02_marche_prix/`  
**Identifiant** : GEOSYLVA-ARCH-DATA-008  
**Date** : 2026-08-30  
**Statut** : Draft  
**Agent** : Consortium architecture des données GeoSylva

## 1. Objectif

Ce modèle prépare une base locale offline-first, importable en JSON/CSV et compatible avec une future synchronisation GSIE. Il n'impose pas de migration Room dans cette tranche et ne remplace pas le modèle existant. Il formalise les distinctions actuellement absentes ou ambiguës.

## 2. Entités

```text
SpeciesReference
  └── local_code, scientific_name, taxonomic_id, aliases, market_status

Measurement
  └── tree/log/stack, variable, value, unit, protocol, protocol_version, instrument_id, date, source

CubageMethod
  └── method_code, formula_ref, input_units, domain, version, evidence

CubageRun
  └── measurements, method, volume_kind, bark_condition, result, uncertainty

WoodProduct
  └── product_code, form, destination, dimensions, quality_constraints

QualityAssessment
  └── grade, defects, standard_ref, assessment_type, target_product

Provenance
  └── country, region, department, massif, SER/GRECO, ownership, evidence_kind, evidence_attachment_id, evidence

PriceObservation
  └── product, provenance, sale_stage, value, unit, period, source

PriceRule
  └── selection criteria, value, valid_from, valid_to, supersession
```

## 3. Contrat minimal des mesures

| Champ | Type | Obligatoire | Règle |
|---|---|---:|---|
| `measurement_id` | string | oui | stable et unique |
| `subject_type` | enum | oui | `tree`, `log`, `stack`, `plot` |
| `variable` | enum | oui | `diameter`, `circumference`, `height`, `length`, `section`, `mass`, `humidity` |
| `value` | number | oui | fini, positif selon variable |
| `unit` | enum | oui | registre fermé |
| `bark_condition` | enum | pour volume | `with_bark`, `without_bark`, `unknown` |
| `protocol` | string | oui | protocole de mesure |
| `protocol_version` | string | recommandé | version du protocole |
| `instrument_id` | string | recommandé | instrument ou capteur |
| `measured_at` | datetime | oui | fuseau explicite |
| `source_reference` | string | oui | document, opérateur ou capteur |
| `evidence_level` | enum | oui | A à F, selon politique de preuve |

`assessment_type` distingue `contractual`, `normative`, `expert` et `estimated`. Un grade estimé ne doit jamais être affiché comme un classement normatif.

## 4. Contrat de volume

Le champ `volume_kind` est obligatoire :

```text
standing_estimate
stem_wood_fort_tige
commercial_stem
log_gross
log_net
stack_apparent
biomass_dry
biomass_wet
mobilisable_estimate
```

Le champ `volume_unit` ne doit pas être déduit du nom : `m3_real`, `m3_apparent`, `stere`, `dm3` et `m3_ha` sont distincts. La conversion est une opération enregistrée avec son facteur, sa source et son domaine.

## 5. Contrat de prix

### 5.1 Observation

Une observation représente une valeur publiée ou mesurée à une date donnée. Elle n'est pas modifiée lors d'une nouvelle publication.

### 5.2 Règle

Une règle représente un prix de référence ou une grille applicable à une période. Elle doit avoir `valid_from`, et `valid_to` lorsqu'elle est fermée, ainsi qu'une référence à la règle remplacée.

### 5.3 Sélection

La priorité recommandée est :

```text
essence scientifique exacte
→ produit exact
→ stade exact
→ qualité exacte
→ dimensions compatibles
→ provenance la plus précise disponible
→ période valide
→ source la plus fiable
→ fallback explicitement annoncé
```

Un fallback ne doit jamais être enregistré comme une observation réelle.

## 6. Mapping vers l'existant

| Existant | Correspondance proposée | Action immédiate |
|---|---|---|
| `Tige.essenceCode` | `local_species_code` | conserver et mapper vers taxonomie |
| `Tige.diamCm` | `Measurement(diameter, cm)` | conserver l'unité et le protocole |
| `Tige.hauteurM` | `Measurement(height, m)` | distinguer Hm, Hdom et hauteur de découpe |
| `Tige.produit` | `product_code` | normaliser les codes |
| `Tige.qualite`/`qualiteDetail` | `QualityAssessment` | ne pas assimiler automatiquement à une norme |
| `Tige.defauts` | `defects[]` | conserver la liste et la sévérité |
| `Tige.photoUri` | `evidence_attachment` | lier au lot ou à la tige |
| `TarifMethod` | `CubageMethod` | versionner et sourcer |
| `PrixProduit`/`PriceEntry` | `PriceRule` ou `PriceObservation` | ajouter période, source et portée |
| `CalculationRunEntity` | `CubageRun` | réutiliser l'append-only |
| `PriceSyncWorker` | import de versions | ne plus remplacer sans historique |

## 7. Règles de qualité des données

- `unit` et `quantity_kind` sont obligatoires ; aucune chaîne libre pour une unité canonique.
- `m3_real` et `m3_apparent` ne sont jamais convertis sans `conversion_factor_source`.
- `with_bark` et `without_bark` ne sont jamais fusionnés dans une moyenne.
- `year` seul est insuffisant : stocker publication et période d'observation.
- Les prix publics FBF, ONF, Agreste, CEEB et ADEME restent des séries séparées.
- `region_scope` doit refléter la portée de la source, pas la précision de l'interface.
- Tout résultat calculé conserve les identifiants de ses entrées et la version de la méthode.
- Une source commerciale est utilisable comme contexte ou borne indicative, jamais comme équivalent automatique d'une source officielle.

## 8. Import JSON/CSV

Le fichier JSON Schema associé est `prix_observation.schema.json`. Le CSV `catalogue_essences_commerciales_france.csv` est un catalogue de départ, pas une table de prix complète. Les cellules vides signifient « non disponible ou non vérifié », pas zéro.

Pipeline local recommandé :

```text
lecture bornée → validation de schéma → contrôle des doublons
→ validation des unités et dates → contrôle source/licence
→ import atomique d'une nouvelle version → rapport de rejet
```

## 9. Recommandation architecturale

Ne pas créer un nouveau moteur GSIE dans cette documentation. Pour GeoSylva, le modèle peut être implémenté de manière additive dans le domaine, puis synchronisé plus tard selon le contrat GSIE applicable. Toute exposition serveur ou promotion de coefficients doit être précédée d'une qualification scientifique et juridique indépendante.

## 10. Sources internes

- `app/src/main/java/com/forestry/counter/domain/model/Tige.kt`
- `app/src/main/java/com/forestry/counter/domain/model/Essence.kt`
- `app/src/main/java/com/forestry/counter/domain/calculation/tarifs/TarifModels.kt`
- `app/src/main/java/com/forestry/counter/domain/calculation/tarifs/TarifData.kt`
- `app/src/main/java/com/forestry/counter/domain/calculation/pricing/PricingContext.kt`
- `app/src/main/java/com/forestry/counter/domain/calculation/pricing/ProPricingEngine.kt`
- `docs/VOLUME_CALCULATION_NEXT_GEN.md`
- `docs/recherche/01_cubage_volume/06_cubage_forestier_global_france.md`
- `docs/recherche/02_marche_prix/06_prix_provenance_actualisation.md`

## 11. Limites

Le modèle ne fournit pas de coefficients économiques ni de prix manquants. Il prépare leur ingestion future et rend explicites les conditions qui empêchent une comparaison valide.
