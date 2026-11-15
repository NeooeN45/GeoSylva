# Stabilisation du 15 novembre 2026

Ce lot prépare **Géosylva 3.0.0 (11) pour des essais locaux**. Il ne constitue
pas la version de production 3.1, prévue le 28 février 2027. Le
[bilan JSON](VALIDATION_STABILISATION_2026-11-15.json) identifie le commit,
les empreintes des artefacts, les passages et leurs limites.

Sources vérifiées : `8ea8cde4b9c7a283e4404c45f39298ed5375af26`. Les commits
documentaires suivants ne modifient pas le code de ce lot.

## Changements livrés dans ce lot

- Les retours de galerie et de caméra conservent l'identité du brouillon qui les
  a déclenchés. Une photo différée ne peut plus être affectée au nouveau brouillon
  après changement ou révocation de l'accès. Dix tests JVM couvrent ces situations.
- Le carnet réutilise les composants de quantité et de dates récupérés, avec leurs
  vraies unités et conventions. Aucun stock ni contrat de contrôle n'est inventé.
- Le dossier chantier appelle les composants retenus pour l'en-tête, le carnet,
  les bois, les réceptions, le thème et les options. L'action Carte ouvre la
  sélection des parcelles liées accessibles, puis la carte commune.
- Réglages → Packs ouvre le gestionnaire Mapbox. Les fichiers administratifs
  restent accessibles par son action dédiée, avec retour et état mémorisé.
- La demande de localisation de la carte demande ensemble les autorisations
  précise et approximative ; le tracé GPS conserve son exigence de précision.
- Le module de prévisualisation séparé dispose à nouveau d'une activité réelle
  utilisant le moteur commun, de quatre styles, du relief et de retours d'erreur.
- Les fixtures de migrations Android et les traductions manquantes sont réparées.

## Vérifications de construction

JDK 17, Gradle 8.2, SDK Platform 35, Build-Tools 34.0.0. Android CLI officiel a
été utilisé pour l'émulateur et l'inspection ; sa commande de build annoncée
n'étant pas disponible dans la version installée, la construction utilise Gradle.

```shell
./gradlew test lint assembleDebug bundleRelease :app:assembleDebugAndroidTest
```

Ce passage réussit, y compris R8 et la réduction des ressources. Les modifications
ultérieures du seul test de geste Android ont leurs compilations ciblées dans le
JSON. Les sources de production et les tests JVM restent inchangés depuis ce passage.

| Vérification | Résultat |
| --- | --- |
| Application JVM debug | 2 046 réussis, aucun échec, erreur ou test ignoré |
| Application JVM release | Les mêmes 2 046 cas réussissent dans cette variante |
| Cartographie JVM | 47 réussis dans chacune des deux variantes |
| Vérificateur d'échange | 7 réussis |
| Total sans compter deux fois les variantes | **2 100 cas distincts** ; 4 193 exécutions |
| Lint debug, quatre modules | Aucune erreur ; avertissements existants : application 972, Mapbox 3, UI 18, prévisualisation 15 |
| APK debug | Produit, signature debug vérifiée ; configuration Mapbox conservée hors Git |
| Bundle release | Produit et optimisé, **non signé pour la production** |

## Suivi CI après la publication du bilan

Le bilan local ci-dessus a été rédigé avant la fin des contrôles GitHub du
commit `488aae439e7b352bebc0bff1e458dd230430b19a`. Il ne constitue pas un
passage vert de cette CI. Les journaux de la
[CI initiale](https://github.com/NeooeN45/GeoSylva/actions/runs/43709887359)
établissent les résultats suivants :

| Job initial | Résultat |
| --- | --- |
| Lint | Réussi |
| Unit Tests | Échec pendant `compileReleaseKotlin` : `OutOfMemoryError: Java heap space`, heap Gradle de 3 Gio |
| Build Debug APK | Ignoré à la suite de cet échec |
| Release Bundle Verification | Ignoré à la suite de cet échec |
| Quality Gate | Échec |
| Sécurité du dépôt | Réussie sur les deux références publiées |

Cet échec intervient pendant la compilation Kotlin ; il ne démontre pas
l'échec d'une assertion métier. Le démon Gradle héberge le compilateur avec
`kotlin.compiler.execution.strategy=in-process` : augmenter un éventuel heap
de démon Kotlin séparé n'aurait pas corrigé ce processus.

La correction de configuration proposée sur la branche
`ci/fiabilisation-memoire-2026-11-15` :

- porte le heap local par défaut à 4 Gio et celui des invocations CI à 6 Gio ;
- limite les invocations à un worker, sans compilation parallèle des projets ;
- isole debug et release dans deux runners, avec `fail-fast: false` ;
- conserve les tests Android JVM des deux variantes et les sept tests du
  vérificateur d'échange ; aucun test, filtre ou assertion n'est supprimé ;
- conserve les contrôles agrégés `Unit Tests` et `Quality Gate`, tous deux
  bloquants si une variante ou un build échoue.

**Résultat de la correction : en attente de l'exécution GitHub sur la demande
de fusion.** Le passage effectif et ses références seront ajoutés après
exécution ; aucun succès distant n'est déduit de la seule modification du YAML.

### Dependabot et registre Maven Mapbox

L'[échec Dependabot initial](https://github.com/NeooeN45/GeoSylva/actions/runs/43709991539)
est distinct de l'échec mémoire. Les journaux indiquent
`private_source_authentication_failure` pour `android-ndk27` et
`maps-compose-ndk27` sur le registre Maven Mapbox.

La configuration déclare désormais ce registre sous le nom `mapbox`, avec
l'utilisateur technique `mapbox` et une référence à
`${{ secrets.MAPBOX_DOWNLOADS_TOKEN }}`. Aucune valeur de jeton n'est versionnée.

À faire par le Fondateur dans les réglages :

1. Créer dans Mapbox un **jeton secret** avec le droit minimal
   **`Downloads:Read`**, conformément à la
   [documentation des jetons Mapbox](https://docs.mapbox.com/help/dive-deeper/access-tokens/).
   Le jeton public de rendu `pk.…` ne remplace pas cet identifiant de registre.
2. Dans GitHub, ouvrir le dépôt GeoSylva → **Settings → Secrets and variables →
   Dependabot → New repository secret**, puis enregistrer ce jeton sous le nom
   exact **`MAPBOX_DOWNLOADS_TOKEN`**. Un secret placé uniquement dans Actions
   ne suffit pas à l'authentification de Dependabot.
3. Après revue et fusion autorisée de la configuration, relancer la recherche
   de mises à jour Dependabot et vérifier son résultat. Dependabot utilise sa
   configuration de branche par défaut ; sa correction n'est pas démontrée
   par les seuls jobs de construction de cette PR.

Les dépendances ne sont pas exclues des mises à jour pour masquer l'échec.
Les jobs de construction restent indépendants de ce nouveau secret ; leur
succès ne doit pas être annoncé comme celui de Dependabot.

## Vérifications natives ciblées

Les essais utilisent un **émulateur logiciel Android 9 / API 28 isolé**. Il ne
contient pas la base utilisateur du téléphone. L'installation avec `adb install -r`
préserve les données compatibles ; aucune purge ni désinstallation n'est utilisée.

- **Sept tests de migrations SQLCipher réussissent**, sur des fixtures couvrant
  les migrations 43 → 49. Cela ne remplace pas la qualification d'une vraie base
  utilisateur sur le S25 Ultra.
- **35 cas natifs distincts sont vérifiés sur plusieurs passages** : sept de
  migrations, vingt de formulaires/dossier/couches et huit cartographiques.
  Les résultats de chaque passage, y compris les échecs initiaux, restent dans le JSON.
  Le dernier passage groupé des huit cas Mapbox réussit sans échec, erreur ni cas ignoré.
- Le pincement échouait dans le banc de test : les 34 événements atteignaient bien
  Android puis le SDK, avec deux pointeurs, mais le geste de 2,7 secondes restait
  sous le seuil natif de démarrage du zoom. Le test produit maintenant un geste
  franc en temps réel et n'attend pas un rendu pour chaque échantillon de mouvement.
  **L'assertion de variation du zoom du vrai SDK passe**, avec les réglages du moteur
  inchangés. Aucun test n'est supprimé, ignoré ou transformé en succès artificiel.

Ces passages ne constituent pas l'exécution de toute la suite instrumentée.
Le style Mapbox local de laboratoire vérifie le rendu natif, ses pixels, les
repères et les interactions ; il ne prouve pas le chargement du satellite réel,
la précision GPS ou le téléchargement des données sur le S25.

## Ce que ce lot ne clôt pas

Les métiers Technicien et Conducteur et les 100 maquettes ne sont pas déclarés
terminés. Le [suivi d'intégration](SUIVI_INTEGRATION_MAQUETTES.md) détaille notamment
les explications par donnée, Diagnostics, les contrats stock/contrôle des fiches
en attente, le mode Maps encore incomplet et les limites de portée des packs.
La fidélité photo, la mesure de hauteur, la journée sans connexion, la signature
de production et la recette physique restent à vérifier.

L'historique récupéré est conservé ; l'identité exacte des sources du 13 novembre
ne peut pas être certifiée. Pour reprendre : [essais locaux](ESSAIS_LOCAUX.md) et
[consignes pour les agents](REPRISE_AGENTS.md).
