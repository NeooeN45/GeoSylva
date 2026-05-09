# Contribuer à GeoSylva

Merci de votre intérêt pour GeoSylva ! Ce guide décrit les conventions et le processus pour contribuer au projet.

## Prérequis

- **Android Studio** Hedgehog (2023.1) ou plus récent
- **JDK 17** (Temurin recommandé)
- **Android SDK 35** (minSdk 26)
- **Git** avec accès au dépôt

## Structure du projet

```
app/src/main/java/com/forestry/counter/
├── data/              # Couche données : Room DB, DAOs, repositories, workers
├── domain/            # Couche métier : calculs, modèles, use cases, GPS
│   ├── calculation/   # ForestryCalculator, TarifCalculator, SanityChecker, etc.
│   ├── model/         # Entités métier (Tige, Essence, Parcelle, etc.)
│   ├── repository/    # Interfaces des repositories
│   ├── usecase/       # Import/Export, PDF, Shapefile, QGIS
│   └── location/      # GPS (GpsAverager, GpsParcelTracer, TreeNavigator)
├── presentation/      # Couche UI : écrans Compose, composants, navigation
│   ├── screens/       # Écrans par fonctionnalité (forestry/, group/, settings/)
│   ├── components/    # Composants réutilisables
│   └── utils/         # Utilitaires UI
└── ForestryCounterApplication.kt
```

## Conventions de code

### Kotlin

- **Style** : suivre les [conventions Kotlin officielles](https://kotlinlang.org/docs/coding-conventions.html)
- **Imports** : préférer les imports explicites (éviter `import .*`)
- **Paramètres inutilisés** : préfixer avec `_` (ex: `_unusedParam`)
- **Permissions** : toujours vérifier avec `ContextCompat.checkSelfPermission()` avant les appels GPS/caméra — ne jamais utiliser `@SuppressLint("MissingPermission")`
- **Gestion d'erreurs** : ne jamais utiliser `catch (_: Throwable) {}` silencieux — logger au minimum avec `Log.w()`
- **Coroutines** : ne jamais utiliser `runBlocking` sur le thread principal

### Architecture

- **Clean Architecture** : respecter la séparation data/domain/presentation
- **Logique métier** : dans `domain/`, jamais dans les Composables
- **Repositories** : interface dans `domain/repository/`, implémentation dans `data/`
- **Calculs** : dans `domain/calculation/` (ForestryCalculator, MartelageStatsCalculator, etc.)

### Compose UI

- **Material 3** : utiliser les composants Material 3
- **i18n** : toutes les chaînes visibles par l'utilisateur dans `res/values/strings.xml` et `res/values-fr/strings.xml`
- **Accessibilité** : `contentDescription` sur toutes les icônes, cibles tactiles ≥ 48dp

## Process de contribution

### 1. Créer une branche

```bash
git checkout -b feature/nom-descriptif
# ou
git checkout -b fix/description-du-bug
```

### 2. Développer

- Écrire des tests unitaires pour la logique métier
- Vérifier que les tests existants passent : `./gradlew :app:testDebugUnitTest`
- Vérifier le lint : `./gradlew :app:lintDebug`
- Vérifier la compilation : `./gradlew :app:assembleDebug`

### 3. Committer

Format des messages de commit :

```
type: description courte

Corps optionnel avec plus de détails.
```

Types : `feat`, `fix`, `refactor`, `test`, `chore`, `ci`, `docs`

### 4. Pull Request

- Décrire les changements et leur motivation
- Lier les issues concernées
- La CI doit passer (tests + build)
- Attendre la review avant de merger

## Tests

### Tests unitaires

```bash
./gradlew :app:testDebugUnitTest
```

Fichiers de test dans `app/src/test/java/`. Couvrent :
- Calculs dendrométriques (ForestryCalculator, TarifCalculator)
- Validation (SanityChecker)
- Qualité bois (ProductClassifier, WoodQualityGrade)
- Coordonnées (Lambert93Converter, WktUtils)
- Export (QgisExportHelper)
- Migrations DB (DatabaseMigrationsTest)

### Lint

```bash
./gradlew :app:lintDebug
```

Un fichier `lint-baseline.xml` capture les issues pré-existantes. Seules les **nouvelles** erreurs lint bloqueront la CI.

## Base de données Room

- **Version actuelle** : 15 (10 entités, 10 DAOs, 14 migrations)
- Chaque modification de schéma nécessite une nouvelle migration dans `DatabaseMigrations.kt`
- Les schémas JSON sont exportés dans `app/schemas/` (configuré via KSP)
- Tester les migrations avec `DatabaseMigrationsTest`

## Fichiers importants

| Fichier | Rôle |
|---|---|
| `ForestryCalculator.kt` | Calculs dendrométriques (cubage, G, volume) |
| `TarifCalculator.kt` | 7 méthodes de tarifs (Schaeffer, Algan, IFN, FGH, Coef) |
| `SanityChecker.kt` | 30+ garde-fous de cohérence |
| `MartelageStatsCalculator.kt` | Agrégats de martelage et biodiversité |
| `GpsAverager.kt` | Moyennage GPS avec rejet MAD |
| `DatabaseMigrations.kt` | Migrations Room v1→v15 |
| `CanonicalEssences.kt` | 95+ essences forestières canoniques |

## Licence

GeoSylva est sous licence **AGPL-3.0**. Toute contribution sera sous la même licence.
