# GeoSylva — Spécification fonctionnelle Synthèse V2

**Statut :** Review  
**Date :** 2026-09-01  
**Dépendances :** Martelage V2, contrat cubage offline-first, référentiel des données

## 1. Finalité

La synthèse V2 est un document scientifique et opérationnel lisible par un forestier, mais vérifiable par un expert. Elle ne doit pas être une phrase générée à partir de chiffres agrégés. Elle doit présenter les faits, les calculs, les décisions, les recommandations, les limites et les preuves dans des blocs séparés.

## 2. Principe de lecture

```text
Contexte
  → couverture des données
  → observations
  → calculs
  → décisions de martelage
  → conclusions
  → recommandations
  → incertitudes et contradictions
  → sources et versions
```

L'utilisateur doit pouvoir savoir immédiatement :

- ce qui a été mesuré ;
- ce qui a été calculé localement ;
- ce qui a été vérifié par GSIE ;
- ce qui est une interprétation ;
- ce qui manque pour conclure ;
- ce qui est à confirmer sur le terrain.

## 3. Modèle de document

### 3.1 Enveloppe

```text
SynthesisDocument
 ├── document_id
 ├── session_id
 ├── calculation_run_ids
 ├── schema_version
 ├── generated_at
 ├── scope
 ├── status
 ├── coverage
 ├── sections
 ├── warnings
 ├── contradictions
 ├── sources
 └── versions
```

Le document référence les calculs immuables ; il ne recopie pas les mesures sans identifiant de provenance.

### 3.2 Types de bloc

```text
CONTEXT
OBSERVATION
CALCULATION
MARKING_DECISION
CONCLUSION
RECOMMENDATION
UNCERTAINTY
CONTRADICTION
SOURCE
```

Chaque bloc possède un identifiant stable, un statut, une date, une source et les identifiants des preuves qui le soutiennent.

## 4. Sections de la page

### 4.1 Résumé exécutif

Le résumé présente les éléments essentiels en quelques lignes : périmètre, nombre de tiges, couverture du cubage, statut de validation et alertes critiques. Il ne doit pas présenter une recommandation bloquée comme certaine.

### 4.2 Contexte

Afficher parcelle, placette, surface, date, opérateur, objectif, méthode sélectionnée, références locales et âge du cache.

### 4.3 Couverture et qualité

Afficher :

- nombre de tiges attendues et observées ;
- pourcentage de mesures exploitables ;
- tiges sans hauteur ;
- essences non résolues ;
- valeurs hors domaine ;
- références manquantes ou périmées ;
- qualité GPS et preuves disponibles.

La couverture ne doit pas être résumée par un seul score opaque.

### 4.4 Observations

Présenter les mesures et observations qualitatives sans les mélanger avec des estimations. Une photo peut soutenir une observation, mais ne prouve pas l'absence d'un défaut interne.

### 4.5 Calculs

Présenter pour chaque résultat :

- valeur ;
- unité ;
- objet mesuré ;
- méthode et version ;
- paramètres ;
- convention d'écorce ;
- empreinte des entrées ;
- état : valide, avertissement, hors domaine ou bloqué ;
- incertitude ;
- source de la méthode.

Le cubage local GeoSylva et une vérification serveur GSIE sont affichés comme deux résultats distincts.

### 4.6 Décisions de martelage

Afficher les décisions de l'opérateur par catégorie et par essence. Les décisions humaines ne doivent pas être réécrites sous forme de recommandations algorithmiques.

### 4.7 Produits et valorisation

Afficher la ventilation par produit uniquement si les règles de découpe, la qualité, le stade de vente, la provenance, la date et l'unité sont compatibles. Sinon, présenter « non calculable » avec la cause.

Un prix moyen national ne doit pas être présenté comme un prix parcellaire. Les unités €/m³, €/stère, €/MWh PCI, tonne verte et tonne sèche restent distinctes.

### 4.8 Conclusions

Une conclusion doit référencer au moins une observation ou un calcul. Elle doit exposer son niveau de confiance, sa portée et ses limites.

### 4.9 Recommandations

Une recommandation est séparée d'une conclusion et identifie :

- sa règle ou son moteur ;
- les faits utilisés ;
- ses hypothèses ;
- son niveau de confiance ;
- les conditions qui l'invalident ;
- l'action de vérification proposée.

Une recommandation ne peut pas être produite à partir d'un coefficient non qualifié ou d'une donnée hors domaine sans avertissement bloquant.

### 4.10 Incertitudes et contradictions

Les contradictions sont conservées avec :

- identifiants des assertions concernées ;
- gravité ;
- origine ;
- explication ;
- impact sur le résultat ;
- statut de résolution.

Une contradiction critique doit empêcher l'affichage d'une conclusion comme validée.

### 4.11 Sources et reproductibilité

La page doit permettre de remonter à la source, la version, la citation, le rôle de la source et le résultat concerné. Le même paquet d'entrée, la même méthode et la même version doivent produire le même résultat.

## 5. États d'affichage

```text
LOCAL_ONLY
LOCAL_CALCULATED
SYNC_PENDING
SERVER_ACCEPTED
SERVER_VERIFIED
PARTIAL
REVIEW_REQUIRED
BLOCKED
```

Le statut `LOCAL_CALCULATED` signifie « calculé dans GeoSylva », pas « vérifié par GSIE ». Le statut `SERVER_VERIFIED` ne doit pas masquer le résultat local d'origine.

## 6. Règles de non-régression

- une donnée absente reste absente ; elle n'est jamais affichée comme zéro sans justification ;
- une surface inconnue interdit les valeurs par hectare ;
- une hauteur estimée possède un indicateur visible ;
- une méthode non qualifiée ne devient pas officielle par son seul nom ;
- une source de prix non comparable ne produit pas de valeur ;
- une divergence entre calcul local et serveur reste visible ;
- une modification de mesure crée une nouvelle version ;
- un export reprend le statut, les preuves, les unités et les versions.

## 7. Exports

PDF, CSV, XLSX, GeoJSON et Shapefile doivent conserver, dans leurs métadonnées lorsque le format le permet :

- identifiant de session ;
- identifiant du calcul ;
- date ;
- méthode et version ;
- unité et convention d'écorce ;
- statut de validation ;
- avertissements ;
- sources principales.

Un export ne doit jamais supprimer les alertes pour améliorer la lisibilité.

## 8. Critères d'acceptation

- aucun bloc `CONCLUSION` sans preuve ;
- aucune recommandation sans faits référencés ;
- distinction visible entre mesure, calcul et estimation ;
- calcul local visible même sans réseau ;
- synchronisation et vérification visibles séparément ;
- état partiel lisible et non ambigu ;
- contradictions critiques bloquantes ;
- résultats reproductibles à version identique ;
- tests d'export avec métadonnées ;
- lecture correcte sur petit, moyen et grand nombre de tiges.

## 9. Écart actuel à résorber

Les agrégats existants dans `MartelageStats` et l'exporteur PDF constituent une base utile, mais ils doivent être encapsulés dans un document de synthèse versionné. Les valeurs de volume, de produit et de revenu actuellement calculées par `ForestryCalculator` ne doivent pas être publiées comme scientifiquement validées tant que leur méthode et leurs paramètres ne sont pas qualifiés.

