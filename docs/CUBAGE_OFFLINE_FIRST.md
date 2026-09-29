# GeoSylva — intégration du cubage offline-first

## Règle d’implémentation

L’écran ne déclenche jamais directement un appel réseau pour calculer un volume. Il transmet ses mesures au cas d’usage local de cubage. Le résultat local est affiché immédiatement si les contrôles sont satisfaits, puis une tâche de synchronisation est créée.

```text
Écran de cubage
    ↓
CubageViewModel
    ↓
CalculerCubageUseCase
    ├── MeasurementValidator
    ├── CubageMethodRegistry
    └── CubageEngine
    ↓
CubageSessionRepository
    ├── stockage local durable
    └── file de synchronisation
    ↓ connexion disponible
GSIE Cubage Sync API
```

## Séparation obligatoire

- Le moteur local reçoit des unités canoniques et renvoie un résultat versionné.
- L’interface convertit uniquement les unités d’affichage.
- Le dépôt gère la persistance et la synchronisation.
- Le client HTTP ne contient aucune formule.
- Le backend GSIE ne remplace pas le résultat local.

## Cycle de vie Android

Le calcul local est lancé dans le cycle de vie du cas d’usage, pas dans une coroutine liée directement à un composable. La synchronisation peut être relancée après destruction et recréation de l’activité.

Une annulation d’écran ne supprime pas une session déjà calculée. Une session non synchronisée reste visible dans une boîte d’envoi dédiée.

## Affichage du résultat

La page doit afficher au minimum :

- le résultat et son unité ;
- le type de cubage ;
- la méthode et sa version ;
- les mesures principales ;
- les paramètres utilisés ;
- les contrôles et avertissements ;
- l’incertitude ;
- la date du calcul ;
- le statut local ;
- le statut de synchronisation GSIE ;
- une éventuelle divergence serveur.

Un résultat local valide mais non synchronisé doit être présenté comme « calculé sur l’appareil — non vérifié par GSIE », jamais comme « validé par GSIE ».

## Tests minimums

- tests unitaires de chaque méthode avec cas de référence documentés ;
- tests de propriétés : valeurs finies, unités explicites, monotonicité lorsque la méthode l’exige ;
- tests de persistance après redémarrage ;
- tests de duplication et d’idempotence ;
- tests de reprise après erreur réseau ;
- tests de conflit de révision ;
- tests d’affichage des divergences ;
- tests de contrat avec les fixtures JSON GSIE ;
- tests sur émulateur avec locale française et réseau indisponible.

