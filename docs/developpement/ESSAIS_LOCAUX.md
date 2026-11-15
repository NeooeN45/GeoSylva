# Essayer Géosylva en local

Géosylva **3.0.0** est la version de construction. La production **3.1** est visée
au **28 février 2027**, avec un candidat stabilisé visé au 14 février.
Ce guide prépare les essais sur Windows, Linux, émulateur et Samsung S25 Ultra.
Les commandes Windows constituent une procédure à valider sur le PC de test ;
elles ne sont pas une certification Windows déjà exécutée.

## Récupérer les sources

Utiliser le dépôt de développement normal et relever le commit récupéré :

```shell
git clone https://github.com/NeooeN45/GeoSylva.git
cd GeoSylva
git switch main
git log -1 --oneline
```

Dans un clone existant, consulter `git status --short`, conserver ses modifications
locales dans un commit ou une branche, puis exécuter `git pull --ff-only` depuis
`main`. Les branches `backup/geosylva-recovery-*` contiennent des archives de
restauration ; elles ne remplacent pas le projet Android à ouvrir.

Installer **JDK 17**, puis, dans le SDK Manager Android, **Android SDK Platform 35**,
**Build-Tools 34.0.0** et **Platform-Tools**. Utiliser le SDK local configuré par
Android Studio ou `ANDROID_HOME`, et rendre `adb` accessible dans le terminal.
Le wrapper du dépôt fournit Gradle 8.2 ; aucun Gradle global n'est nécessaire.

## Configurer la carte

Dans `local.properties`, conserver les lignes existantes et ajouter le jeton
**public** Mapbox du projet :

```properties
# Remplacer cet exemple par son jeton public, sans guillemets.
MAPBOX_PUBLIC_ACCESS_TOKEN=pk.EXEMPLE_A_REMPLACER
```

Ce fichier est ignoré par Git. La variable d'environnement
`MAPBOX_PUBLIC_ACCESS_TOKEN` est aussi prise en charge et a priorité sur ce fichier.
Ne pas verser de jeton, de configuration personnelle ou de clé de signature dans
un commit ou un rapport. Recompiler l'APK après un changement de configuration.
Une réponse du service Mapbox ne valide pas à elle seule son rendu sur le téléphone.

## Construire et vérifier

Depuis la racine du dépôt, dans **PowerShell** :

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :cartography-mapbox:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Sous **Linux**, les mêmes vérifications :

```bash
java -version
./gradlew --version
./gradlew :app:assembleDebug :app:testDebugUnitTest :cartography-mapbox:testDebugUnitTest
./gradlew :app:lintDebug
```

Les deux premières commandes doivent confirmer JDK 17. L'APK se trouve dans
`app/build/outputs/apk/debug/app-debug.apk`. Les rapports JVM sont dans
`app/build/reports/tests/testDebugUnitTest/` et
`cartography-mapbox/build/reports/tests/testDebugUnitTest/` ; le rapport Lint est
dans `app/build/reports/lint-results-debug.html`.

Avec Python 3, `python3 tools/verify_room49_migrations.py` sous Linux, ou
`py -3 tools\verify_room49_migrations.py` sous Windows, compare aussi le SQL des
migrations 46 → 49 au schéma Room exporté. Ce contrôle SQLite local complète les
essais Android des migrations et ne les remplace pas.

Le [bilan du 15 novembre 2026](VALIDATION_STABILISATION_2026-11-15.md) donne les
résultats courants, le commit concerné et la portée des essais natifs.
Le [bilan du 14 novembre](../recovery/APPLICATION_VALIDATION_2026-11-14.json)
reste une preuve historique sur `33d904901a320cb3439e25a2af4d2efdfebe444a`.
Pour une nouvelle révision, noter ses propres commandes, résultats et rapports ;
un ancien bilan ne valide pas un nouveau commit.

## Lancer sur un appareil de test

Le [guide Android CLI officiel](https://developer.android.com/blog/posts/android-cli-build-android-apps-3x-faster-using-any-agent?hl=fr)
présente l'installation de l'outil `android`. Pour un émulateur, créer un appareil
API 35 dans Android Studio Device Manager, puis utiliser son nom dans la commande
de démarrage :

```shell
android --no-metrics emulator list
android --no-metrics emulator start NOM_AVD
adb devices -l
```

Pour le S25 Ultra, activer le débogage USB et autoriser le PC sur le téléphone,
puis vérifier sa présence avec `adb devices -l`. Remplacer `NUMERO_APPAREIL`
ci-dessous par le numéro réellement affiché ; conserver ce choix explicite si
plusieurs appareils sont connectés.

```shell
adb -s NUMERO_APPAREIL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s NUMERO_APPAREIL shell am start -n com.forestry.counter/.presentation.MainActivity
```

L'application utilise actuellement le paquet `com.forestry.counter`. L'option
`-r` conserve les données d'une installation compatible. Si Android refuse la
signature ou la version, conserver l'installation existante et employer un
émulateur ou un appareil de test dédié. Ne pas désinstaller l'application ni
utiliser `pm clear` pour contourner ce refus. Faire un export des données utiles
avant un essai de mise à jour ; une base Room 49 ne doit pas être ouverte ensuite
avec un ancien APK.

La suite instrumentée Android doit être lancée sur une installation de test
dédiée, sans données à conserver. Consulter d'abord le bilan courant de sa
compilation et des scénarios disponibles. Elle s'exécute séparément des tests JVM :

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Sous Linux : `./gradlew :app:connectedDebugAndroidTest`. Un échec de compilation
des fixtures ou l'absence d'appareil doit être rapporté comme tel ; aucun de ces
cas ne constitue un passage réussi des tests Android.
Les essais natifs publiés dans le bilan courant sont un lot ciblé, pas l'exécution
de toute la suite instrumentée. Les tests Mapbox de ce lot utilisent un style
local de laboratoire pour vérifier le SDK ; le satellite réel et les téléchargements
nécessitent une recette distincte avec réseau puis sans réseau.

## Recette courte sur des données TEST

Utiliser des dossiers et observations fictifs nommés `TEST`. Relever pour chaque
cas le commit, la version Android, l'appareil, le résultat et une capture si utile.

| Cas | Manipulation | Résultat à vérifier |
| --- | --- | --- |
| Technicien | Créer ou ouvrir une parcelle TEST, saisir des relevés, consulter la Synthèse et ses aides | Saisies et unités relues correctement ; tout volume non qualifié reste indisponible avec son motif |
| Carte | Ouvrir Carte → Recherche, choisir son contexte ; essayer aussi Libre | Bon contexte et bons points ; retour et réouverture sans données d'un autre dossier |
| Panneaux | Ouvrir Couches, Fonds et les aides ; refermer par le bouton puis le retour Android | Carte lisible, échelle masquée quand un panneau la recouvre, commandes utilisables avec le clavier et une grande police |
| Couche importée | Importer un petit GeoJSON TEST, consulter ses attributs, changer sa couleur puis sa visibilité | Aperçu avant ajout, données conservées, sélection liée aux objets effectivement affichés |
| Mesures | Dessiner une distance et une surface, enregistrer, rouvrir et partager | Points et unités conservés ; résultat cartographique distingué d'une mesure de terrain ou d'un bornage |
| Tracé GPS | Sur le téléphone, démarrer un court tracé, faire Pause, reprendre puis terminer et enregistrer | Permissions et qualité visibles ; portions et interruptions conservées sans relier artificiellement une zone non relevée |
| Hors connexion | Préparer une petite emprise depuis la carte, attendre sa vérification, couper les réseaux et rouvrir la zone | Fond aux zooms préparés, données locales et saisies disponibles ; absence de faux état « prêt » si la préparation échoue |
| Conducteur | Créer un chantier TEST, lier ses parcelles, ajouter une note au carnet, créer un dépôt et une pile, saisir ses dimensions | Dossier, note, identité et mesures retrouvés ; volume apparent distingué d'une quantité payable |
| Export et reprise | Enregistrer les saisies, exporter, fermer complètement l'application puis la relancer | Export réellement relisible, même dossier et mêmes mesures, aucun enregistrement ou mouvement ajouté en double |

Pour une couche d'essai, copier par exemple ce contenu fictif dans un fichier
local `test.geojson` :

```json
{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"nom":"Point TEST"},"geometry":{"type":"Point","coordinates":[2.0,47.0]}}]}
```

Une recette courte ne valide pas une journée terrain. Celle-ci doit être rejouée
sur le S25 Ultra avec les réseaux coupés après préparation, puis documenter
reprise, export, batterie et stockage. L'émulateur aide à vérifier les interfaces
et les états de repli ; il ne qualifie pas les capteurs physiques du téléphone.

## Limites à garder visibles

- Le mode **Maps** du sélecteur principal est encore un écran d'attente ; le
  parcours complet d'itinéraire et de guidage reste à raccorder.
- Les nouvelles présentations `WoodFileCard` et `RetainedJournalContent` sont
  préservées dans `docs/recovery/pending-integration/`, en attente de contrats
  cohérents de stock et de contrôle. Elles ne sont pas des écrans livrés.
- La fidélité aux 100 maquettes, le visuel d'accueil 21, les migrations SQLCipher
  sur appareil et les essais physiques S25 Ultra restent à qualifier. Consulter
  l'[état de livraison](../production/ETAT_LIVRAISON_3_1.json) et le bilan courant.
- Les services Google/GSIE, les jeux de données signés et le connecteur FORLOG
  doivent être qualifiés séparément ; les essais locaux n'établissent pas leur activation.

Pour signaler un problème : indiquer le commit, les étapes et données TEST, le
résultat attendu et celui obtenu. Joindre les rapports utiles en retirant les
jetons et les données personnelles ; ne pas envoyer automatiquement tout Logcat.
