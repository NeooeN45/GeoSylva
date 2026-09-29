# GeoSylva — Spécification fonctionnelle Martelage V2

**Statut :** Review  
**Date :** 2026-09-01  
**Dépendances :** référentiel cubage, contrat offline-first, modèle de données local

## 1. Finalité

Le martelage V2 est une session de terrain permettant d'observer, mesurer, qualifier et décider pour chaque tige, puis de produire une synthèse reproductible. Il doit fonctionner sans connexion et préserver les décisions humaines sans les transformer silencieusement en recommandations automatiques.

Le martelage n'est pas un écran de statistiques. C'est un dossier opérationnel versionné.

## 2. Parcours utilisateur

```text
Choisir le contexte
  → ouvrir ou créer une session
  → préparer les références locales
  → saisir ou vérifier les tiges
  → attribuer une décision de martelage
  → contrôler les incohérences
  → calculer localement
  → valider la session
  → consulter la synthèse
  → synchroniser si possible
```

Une session peut rester exploitable localement sans être synchronisée. Une session synchronisée peut recevoir une vérification GSIE, mais la décision terrain originale reste conservée.

## 3. États métier

```text
DRAFT
  → IN_PROGRESS
  → NEEDS_REVIEW
  → LOCALLY_CALCULATED
  → VALIDATED_BY_OPERATOR
  → QUEUED_FOR_SYNC
  → SYNCING
  → SYNCED
  → SERVER_REVIEW_REQUIRED
  → ARCHIVED
```

États d'erreur :

```text
BLOCKED_MISSING_DATA
BLOCKED_UNQUALIFIED_METHOD
SYNC_FAILED_RETRYABLE
SYNC_FAILED_FINAL
CONFLICT
```

Un état d'erreur ne supprime jamais le brouillon, les mesures ou un calcul antérieur.

## 4. Modèle fonctionnel

### 4.1 Session

La session contient :

- `session_id` stable ;
- propriétaire et opérateur ;
- forêt, parcelle, placette et géométrie ;
- objectif : inventaire, martelage, vente, amélioration ou autre ;
- date de début, dernière modification et date de validation ;
- révision courante ;
- méthode de cubage choisie ou état non choisi ;
- références locales disponibles et âge du cache ;
- état métier et état de synchronisation ;
- historique des révisions.

### 4.2 Observation de tige

Le modèle existant `Tige` fournit déjà une base avec essence, diamètre, hauteur, GPS, note, catégorie, qualité et défauts. V2 doit l'envelopper dans une observation de session qui ajoute :

- l'identifiant de session ;
- le protocole et l'instrument ;
- le statut de chaque mesure : `MEASURED`, `ESTIMATED`, `IMPORTED`, `MISSING` ;
- l'heure et l'opérateur ;
- la précision GPS et la source de localisation ;
- les preuves jointes ;
- la révision de l'observation ;
- la décision de martelage ;
- les raisons et les commentaires de l'opérateur.

### 4.3 Décision de martelage

La décision humaine est distincte de la recommandation calculée. Les valeurs minimales sont :

```text
KEEP
IMPROVE
HARVEST
BIODIVERSITY
DEAD_OR_DYING
PARASITE_OR_RISK
UNDECIDED
```

Les codes existants `DEPERISSANT`, `ARBRE_BIO`, `MORT` et `PARASITE` sont conservés comme catégories d'observation, mais ils ne doivent pas être assimilés automatiquement à une décision de récolte.

Chaque décision possède :

- auteur ;
- date ;
- justification facultative ou obligatoire selon la catégorie ;
- preuve éventuelle ;
- statut `HUMAN`, `RULE_ASSISTED` ou `REVIEWED` ;
- révision précédente.

## 5. Écrans et responsabilités

### 5.1 En-tête de session

Affiche contexte, surface, opérateur, date, nombre de tiges, état local, état de synchronisation et âge des références.

### 5.2 Liste des tiges

Permet recherche, filtres par essence, classe, décision, qualité, état de mesure et avertissement. Elle ne doit pas déclencher de calcul réseau.

### 5.3 Fiche tige

Permet de modifier une mesure et ses preuves. Toute modification crée une nouvelle révision logique et invalide le calcul local précédent pour cette session.

### 5.4 Décision de martelage

Permet la saisie explicite de la décision humaine. Une aide algorithmique peut signaler une incohérence, mais ne doit pas sélectionner ou enregistrer une décision sans action de l'opérateur.

### 5.5 Contrôle

Affiche les données manquantes, incohérences diamètre/circonférence, hauteurs non compatibles, GPS imprécis, essence non résolue, méthode non qualifiée et volume non calculable.

### 5.6 Résumé de session

Affiche les agrégats par placette, parcelle ou global, sans masquer la couverture ni les données exclues. Le résumé utilise la spécification `SYNTHESE_V2_SPECIFICATION.md`.

## 6. Calcul local dans une session

Le calcul est lancé par un cas d'usage, jamais directement dans un composable. Le cas d'usage reçoit un snapshot immuable :

```text
session revision
+ observations de tiges
+ méthode et version
+ paramètres et unités
+ règles de découpe
+ références locales
```

Le résultat est un `CubageRun` immuable. Une mesure modifiée ne met pas à jour le résultat existant ; elle crée un nouveau run lié au précédent.

### Interdictions V2

- aucune méthode implicite si le choix n'est pas documenté ;
- aucun fallback vers une essence proche dans un résultat de vente ;
- aucun coefficient de forme inventé ou non sourcé ;
- aucune hauteur par défaut sans drapeau explicite ;
- aucun produit par défaut si la règle de découpe est absente ;
- aucune valeur manquante convertie en zéro ;
- aucune présentation d'une méthode `E_NON_QUALIFIE` comme officielle.

## 7. Synthèse du martelage

La session doit pouvoir produire au minimum :

- nombre de tiges et surface couverte ;
- surface terrière et densité, avec unité et surface de référence ;
- distribution des diamètres ;
- hauteurs disponibles, Hm, Hdom ou Lorey uniquement lorsque définies ;
- volumes par méthode et par essence ;
- complétude du volume ;
- décisions de martelage par catégorie ;
- arbres remarquables, morts, dépérissants ou à conserver ;
- produits et prix seulement lorsqu'ils sont comparables ;
- avertissements, incertitudes et contradictions ;
- origine et version de chaque résultat.

## 8. Synchronisation

La synchronisation envoie la session, toutes les mesures utiles et le `CubageRun` local. Elle ne se limite pas aux agrégats affichés. Le paquet doit permettre à GSIE de vérifier le résultat sans demander une nouvelle saisie.

La même session peut être synchronisée plusieurs fois. La révision et les empreintes empêchent l'écrasement d'une version concurrente.

## 9. Critères d'acceptation Martelage V2

- création, modification et reprise d'une session après redémarrage ;
- fonctionnement complet sans réseau ;
- aucune perte après interruption pendant une mesure ;
- résultat local associé à une révision exacte ;
- distinction décision humaine/aide algorithmique ;
- contrôle visible des méthodes et références non qualifiées ;
- calcul absent ou bloqué expliqué à l'utilisateur ;
- synchro répétée idempotente ;
- conflit de révision visible et récupérable ;
- export conservant session, méthode, version, unités et preuves ;
- couverture des tests QA-01 à QA-22, QA-61 à QA-70 selon le périmètre activé.

## 10. Écart actuel à résorber

Le calcul et une partie importante de l'état du martelage sont encore pilotés depuis `MartelageScreen.kt`, tandis que `MartelageViewModel.kt` ne porte pas encore la session complète. La mise à niveau doit d'abord déplacer cette responsabilité vers un dépôt et des cas d'usage testables avant d'ajouter de nouvelles fonctions visuelles.

