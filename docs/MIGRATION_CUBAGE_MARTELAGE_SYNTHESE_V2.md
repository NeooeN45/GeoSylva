# GeoSylva — Plan de migration Cubage, Martelage et Synthèse V2

**Statut :** Review  
**Date :** 2026-09-01  
**Objectif :** remplacer progressivement l'ancien chemin de calcul sans perte de données ni rupture de session

## 1. Règle de migration

La migration est progressive. L'ancien code peut être archivé, mais il ne doit pas être supprimé avant d'avoir conservé :

- les modèles Room nécessaires ;
- les tests de référence ;
- les documents de méthode ;
- les exports historiques utiles ;
- les résultats et leurs versions ;
- la branche d'archive du dépôt.

Une suppression de fichier ne constitue pas une migration de données.

## 2. Cartographie ancien → nouveau

| Existant | Cible V2 | Action |
|---|---|---|
| `MartelageScreen.kt` | écrans Compose fins + `MartelageViewModel` | déplacer l'état et les calculs hors du composable |
| `MartelageViewModel.kt` | orchestrateur de session | ajouter session, révisions, validation et synchronisation |
| `MartelageModels.kt` | agrégats de synthèse | conserver les calculs valides, retourner des résultats typés |
| `ForestryCalculator.kt` | use cases et `CubageEngine` | extraire par méthode, supprimer les fallbacks silencieux |
| `TarifCalculator.kt` | registre de méthodes versionnées | conserver seulement les méthodes qualifiées ou explicitement expérimentales |
| `TarifData.kt` | paquet de paramètres versionné | ajouter source, domaine, unité et statut de qualification |
| `VolumeConversion.kt` | service de conversion contextualisé | exiger facteur, essence, humidité et convention lorsque nécessaires |
| `UserPreferences` | préférences d'interface uniquement | ne plus y stocker une session métier complète |
| `PdfSynthesisExporter.kt` | export d'un `SynthesisDocument` | exporter les statuts, versions et avertissements |
| `GeoSylvaAnalysisRepository.kt` | synchronisation du paquet cubage | ajouter session, calcul local et idempotence |

## 3. Phases

### Phase 0 — Gel documentaire

- adopter le présent index comme point d'entrée ;
- figer les termes `Session`, `Measurement`, `CubageRun`, `SynthesisDocument` ;
- relier chaque méthode à son statut de qualification ;
- créer les vecteurs de référence indépendants ;
- identifier les anciennes données à conserver.

### Phase 1 — Noyau de calcul local

- définir une interface de méthode versionnée ;
- normaliser les unités en entrée ;
- valider les domaines avant calcul ;
- retourner `valid`, `warning` ou `blocked` au lieu de `0.0` ou d'un fallback ;
- implémenter d'abord Huber, Smalian, Newton, tronc de cône et coefficient de forme lorsqu'ils sont documentés ;
- isoler Schaeffer, Algan, IFN, Chaudé, Lapasse, Bouvard et les tarifs locaux selon leur qualification réelle.

### Phase 2 — Session locale

- créer les tables Room pour session, révision, mesures, calculs et synchronisation ;
- chiffrer les données conformément au stockage existant ;
- rendre les écritures transactionnelles ;
- conserver les calculs immuables ;
- reprendre après fermeture ou redémarrage de l'application.

### Phase 3 — Martelage V2

- faire du ViewModel le propriétaire de l'état ;
- remplacer les paramètres métier stockés uniquement dans DataStore ;
- relier les observations à une session ;
- enregistrer les décisions humaines et leurs révisions ;
- déplacer `computeMartelageStats` vers un cas d'usage testable ;
- afficher les blocs bloqués et les avertissements.

### Phase 4 — Synthèse V2

- créer `SynthesisDocument` ;
- transformer les agrégats existants en blocs typés ;
- ajouter preuves, sources, versions et incertitudes ;
- adapter PDF/CSV/XLSX ;
- refuser les conclusions sans preuve.

### Phase 5 — Synchronisation GSIE

- envoyer session et calcul complet ;
- appliquer l'idempotence ;
- gérer `accepted`, `synced`, `review_required`, `rejected` et `retryable_error` ;
- conserver la vérification serveur comme résultat séparé ;
- traiter les divergences sans écrasement.

### Phase 6 — Retrait de l'ancien chemin

- activer méthode par méthode le nouveau moteur ;
- comparer les résultats sur les vecteurs de référence ;
- comparer les exports ;
- surveiller les divergences en pilote ;
- supprimer uniquement le code sans dépendance résiduelle ;
- conserver la branche d'archive et le journal de migration.

## 4. Portes de retrait

Un ancien calculateur ne peut être supprimé que si :

- aucun écran ni export ne l'appelle ;
- les données nécessaires sont migrées ;
- les résultats de référence concordent dans la tolérance documentée ;
- les méthodes non concordantes sont explicitement isolées ;
- les états bloqués sont testés ;
- le redémarrage et la reprise de session passent ;
- la synchronisation et le conflit de révision passent ;
- la documentation de la méthode et de sa version est présente.

## 5. Tests de migration

### Calcul

- QA-01 à QA-22 ;
- vecteurs Huber, Smalian, Newton et cône ;
- tests de bornes et d'unités ;
- absence de fallback d'essence ;
- absence de valeur par défaut non marquée.

### Session et hors ligne

- création et reprise après redémarrage ;
- modification créant une nouvelle révision ;
- calcul local sans réseau ;
- mise en file puis synchronisation ;
- retry borné et idempotent ;
- conflit entre deux révisions.

### Martelage

- décision humaine conservée ;
- catégories spéciales visibles ;
- calcul par placette, parcelle et global ;
- données manquantes visibles ;
- tests de charge avec nombreuses tiges.

### Synthèse et exports

- aucune conclusion sans preuve ;
- distinction volume réel/apparent/stère ;
- distinction estimation/mésurage ;
- prix non comparable refusé ;
- avertissements et versions présents dans les exports.

## 6. Risques et réponses

| Risque | Réponse obligatoire |
|---|---|
| l'ancien code contient une règle non documentée | conserver le comportement dans un cas de référence et le qualifier avant retrait |
| nouvelle formule incompatible avec les résultats historiques | versionner, comparer, expliquer la différence, ne pas écraser |
| source hors ligne périmée | afficher âge, version et statut du cache |
| utilisateur sans connexion | calcul local et file de synchronisation durable |
| session interrompue | transaction locale et reprise par identifiant stable |
| divergence serveur | objet de réconciliation distinct |
| méthode non qualifiée | blocage ou statut expérimental explicite |
| export simplifié | interdire la perte des métadonnées scientifiques |

## 7. Définition de terminé

Le lot V2 est terminé lorsque le parcours suivant est démontré sur émulateur et appareil de test :

```text
créer une session
→ saisir des tiges sans réseau
→ calculer localement
→ fermer et rouvrir l'application
→ consulter la synthèse
→ corriger une mesure
→ obtenir une nouvelle version
→ retrouver l'ancien calcul
→ se reconnecter
→ synchroniser une seule fois malgré plusieurs retries
→ afficher l'accusé GSIE et une éventuelle divergence
```

