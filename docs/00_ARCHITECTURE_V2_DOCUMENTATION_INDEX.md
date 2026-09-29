# GeoSylva — Index documentaire V2

**Statut :** Review  
**Date :** 2026-09-01  
**Périmètre :** cubage, martelage, synthèse, synchronisation offline-first

## 1. Objet

Ce document est le point d'entrée de la documentation de mise à niveau de GeoSylva. Il sépare les documents de recherche, les contrats, l'état réel du code et les décisions d'architecture.

Une documentation de recherche ne vaut pas qualification d'une méthode. Une méthode présente dans le code ne vaut pas validation scientifique. Une fonctionnalité décrite dans un document prospectif n'est pas considérée comme implémentée.

## 2. Règle de priorité

En cas de contradiction :

1. une décision validée et son RFC associé priment pour l'architecture ;
2. une source primaire vérifiée prime pour une formule ou un coefficient ;
3. un test de référence indépendant prime pour le comportement calculatoire ;
4. le code actuel est une preuve de comportement observé, jamais une preuve de validité scientifique ;
5. un document prospectif reste une cible tant qu'un critère d'acceptation n'est pas satisfait.

## 3. Carte documentaire

### 3.1 Documents de pilotage V2

| Document | Rôle | Statut | Décision |
|---|---|---|---|
| `MARTELAGE_V2_SPECIFICATION.md` | parcours, données, états et critères du martelage | Review | référence fonctionnelle |
| `SYNTHESE_V2_SPECIFICATION.md` | document de synthèse lisible, traçable et versionné | Review | référence fonctionnelle |
| `MIGRATION_CUBAGE_MARTELAGE_SYNTHESE_V2.md` | découpage d’implémentation et portes de retrait de l’ancien code | Review | référence de livraison |
| `CUBAGE_OFFLINE_FIRST.md` | intégration du calcul local dans GeoSylva | Review | référence mobile |

### 3.2 Contrats GeoSylva–GSIE

| Document | Rôle | Statut |
|---|---|---|
| `../../../02_RFC/RFC-0042-contrat-offline-first-cubage-geosylva-gsie.md` | responsabilité locale et distante du cubage | Review |
| `../../../03_DECISIONS/DEC-000078-offline-first-cubage-geosylva.md` | décision d’architecture | Validé |
| `../../../GSIE/API/docs/GEOSYLVA_CUBAGE_OFFLINE_FIRST_V1.md` | routes et enveloppes API | Review |
| `docs/contracts/` | contrats machine-readable à créer avec l’implémentation | à compléter |

### 3.3 Référentiel scientifique et données

| Document | Rôle | Statut réel |
|---|---|---|
| `REFERENTIEL_CUBAGE_VENTE_FRANCE.md` | vocabulaire, choix de méthode et limites commerciales | Draft |
| `DONNEES_ENTREES_SORTIES_CUBAGE_VENTE.md` | entrées et sorties par type de résultat | Draft |
| `methodes_calcul_volume.md` | description du code historique | à ne pas confondre avec validation |
| `recherche/01_cubage_volume/09_registre_methodes_cubage_france_monde.md` | qualification méthode par méthode | Draft |
| `recherche/01_cubage_volume/08_audit_formules_coefficients_2026-08-30.md` | contradictions et risques du code | Draft, bloquant pour plusieurs méthodes |
| `recherche/02_marche_prix/08_modele_donnees_cubage_prix.md` | prix, provenance et historique | Draft |
| `recherche/02_marche_prix/10_plan_tests_cubage_vente_2026-08-30.md` | plan QA QA-01 à QA-70 | Draft |
| `recherche/02_marche_prix/vecteurs_tests_cubage.json` | vecteurs numériques indépendants | Draft |

### 3.4 Document prospectif

`VOLUME_CALCULATION_NEXT_GEN.md` contient une vision large : modèles statistiques, capteurs, IA, 3D, carbone et futures fonctions de martelage. Il sert de réserve de conception et de feuille de route, mais ne doit pas être utilisé comme contrat V1. Les fonctions futures devront être promues dans les spécifications V2/V3 avec des critères testables.

## 4. État réel constaté au 2026-09-01

### Présent

- `MartelageScreen.kt` concentre encore une grande quantité de logique d'affichage, d'état et de calcul.
- `MartelageViewModel.kt` expose principalement les flux de tiges et de parcelles ; le calcul dépend encore d'états locaux de l'écran.
- `MartelageModels.kt` contient déjà les agrégats de placette, parcelle et global, les classes de diamètre, Hdom, G, V, produits, qualité et contrôles de cohérence.
- `ForestryCalculator.kt` orchestre le calcul des synthèses par essence.
- `TarifCalculator.kt` expose sept méthodes historiques ou génériques.
- les exports PDF, CSV, XLSX, GeoJSON et Shapefile existent déjà.
- Room, DataStore, WorkManager et une couche réseau GSIE existent déjà.

### Risques à traiter avant de qualifier le nouveau moteur

- sélection par défaut d'Algan lorsque aucun tarif n'est configuré ;
- coefficients ou numéros de tarifs par défaut ;
- fallback vers une essence proche ;
- fallback vers une catégorie de produit ou une essence générique ;
- conversion stère/m³ au moyen de coefficients fixes non contextualisés ;
- conversion d'une donnée absente en zéro ;
- état et paramètres de calcul conservés dans des préférences plutôt que dans une session versionnée ;
- mélange entre résultat mesuré, estimation, prix et recommandation ;
- méthode ou coefficient affiché comme officiel alors que l'audit le classe non qualifié ;
- calcul déclenché depuis un composable au lieu d'un cas d'usage testable.

## 5. Architecture cible

```text
Écrans Compose
      ↓ événements et état de présentation
ViewModel
      ↓ cas d’usage
SessionRepository ─── Room/SQLCipher
      ↓ snapshot immuable
CubageEngine local
      ↓ résultats typés et contrôlés
SynthesisAssembler local
      ↓ file de synchronisation
GSIE BFF
      ↓ archivage, vérification et enrichissement
Résultat distant et synthèse réconciliée
```

Le calcul local est obligatoire pour le cubage terrain. Les analyses nécessitant des données absentes du téléphone restent identifiées comme serveur. Le moteur local ne doit pas être confondu avec un GSIE embarqué.

## 6. Portes de qualité

Une méthode ne peut apparaître dans le choix « qualifié » que si :

- sa formule ou sa table primaire est identifiée ;
- ses unités et sa convention d'écorce sont explicites ;
- son domaine de validité est connu ;
- ses résultats de référence sont testés indépendamment ;
- l'absence de coefficient ou d'essence produit un blocage explicite ;
- la méthode, sa version et sa source sont enregistrées dans le résultat.

Une page ne peut être déclarée terminée que si :

- elle fonctionne après redémarrage ;
- ses états hors ligne et synchronisés sont visibles ;
- ses erreurs sont corrigeables ou expliquées ;
- ses résultats sont reproductibles ;
- ses exports conservent les métadonnées scientifiques ;
- les tests du parcours et du contrat sont verts.

## 7. Découpage de livraison

### Lot A — Contrats et sessions

Créer `MartelageSession`, `MeasurementSet`, `MarkingDecision`, `CubageRun` et leurs états. Aucun retrait de l'ancien calcul tant que le nouveau chemin ne peut pas lire les anciennes données nécessaires.

### Lot B — Moteur local contrôlé

Extraire les calculs purs, supprimer les fallbacks silencieux et produire des erreurs typées. Commencer par les formules géométriques et les méthodes réellement qualifiées.

### Lot C — Martelage

Faire du ViewModel le propriétaire de l'état, déplacer les calculs dans des cas d'usage et connecter la session persistante.

### Lot D — Synthèse

Assembler un document structuré distinguant observation, calcul, décision, recommandation, preuve et incertitude.

### Lot E — Synchronisation

Synchroniser le paquet complet vers GSIE avec idempotence, révision et réconciliation explicite.

### Lot F — Retrait progressif

Désactiver les anciens calculateurs par méthode après comparaison des résultats, puis supprimer uniquement les fichiers sans données, tests ou modèles encore utilisés.

## 8. Règle de modification documentaire

Toute nouvelle méthode, donnée de marché ou règle de martelage doit modifier au minimum :

- le registre de méthode ou de source ;
- le contrat de données ;
- les cas de test ;
- la spécification de la page concernée ;
- le journal de décision si le comportement change.
