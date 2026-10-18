# Vérification isolée des noyaux GeoSylva

Ce projet JVM compile directement les sources de l'application dans les packages
`domain/journey`, `domain/wood` et `domain/calculation/reception`, avec leurs tests
et références analytiques. Aucune copie des sources n'est utilisée.

Il permet de qualifier ces composants pendant la récupération du reste de
l'application. Il ne remplace pas la compilation Android, les tests Room ou la
recette de navigation, identité et synchronisation.

Depuis la racine du dépôt, avec Java 17 et Gradle 8.2 disponibles :

```sh
gradle -p tools/domain-check test --no-daemon --max-workers=2 --console=plain
```

Dans l'environnement cloud actuel, le lanceur conservé sélectionne les outils :

```sh
python3 /workspace/.onboarding/gradle.py -p tools/domain-check test --offline --no-daemon --max-workers=2 --console=plain
```

Le mode hors ligne exige que les dépendances aient déjà été résolues. Les rapports
JUnit sont écrits dans `build/test-results/test/` et le rapport HTML dans
`build/reports/tests/test/`. Résultat du 15 octobre : 80 tests, aucun échec ni
test ignoré. Les adaptateurs Android, base de données et serveur restent à intégrer.
