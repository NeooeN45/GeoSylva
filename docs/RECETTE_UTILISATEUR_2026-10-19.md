# Recette utilisateur GeoSylva — 19 octobre 2026

## Périmètre et méthode

Parcours prioritaire **conducteur de travaux**, en vue de la démonstration du
21 octobre. Simulateur Android 9/API 28, 720 × 1280, sans accélération matérielle ;
variante isolée `com.forestry.counter.preview.receptions`.

Les données du scénario ci-dessous ont été saisies par taps, clavier, retours et
défilements dans l'application. Aucune injection de données métier dans la base
ou les ViewModels n'a servi à ce parcours manuel. Les tests Android automatisés
complémentaires utilisent leurs propres bases TEST isolées.

Tous les lieux, références cadastrales et volumes du scénario sont **simulés**.
La référence `TEST-AB-12` n'est pas une parcelle cadastrale vérifiée. GPS et caméra
ont été refusés volontairement ; aucun compte, client FORLOG ou paiement réel.

## Résultats du parcours manuel

| Action réalisée depuis l'interface | Résultat constaté | Capture |
| --- | --- | --- |
| Choisir Conducteur de travaux, continuer hors ligne, refuser GPS/caméra | Formulaires accessibles ; Missions absent hors Entreprise | 01–08 |
| Tenter de sauvegarder un chantier sans nom | Sauvegarde refusée ; défaut de focus et message trop général identifiés puis corrigés | 09–15 |
| Créer `TEST Utilisateur 19 octobre` et sa référence de parcelle | Dossier conservé avec `CADASTRE / TEST-AB-12 / TEST Parcelle nord` | 18–19 |
| Créer un dépôt bord de route, puis une pile de chêne | Identité, étiquette, essence et qualité déclarée conservées | 22–26 |
| Relever une enveloppe rectangulaire 10 × 5 × 2 m | 100 m³ apparents ; distinction avec le payable affichée | 30–33 |
| Choisir la mesure de référence et associer le bois au chantier | Même bois retrouvé, sans création d'un nouvel objet | 37 |
| Mettre à jour l'APK en place et rouvrir | Chantier, parcelle, bois et mesure conservés | 41–42 |
| Préparer une réception, sauvegarder, quitter puis reprendre | Nom, mesure, quantité 100, unité et convention conservés | 51–52 |
| Consulter le carnet avant validation | Mesure seule, aucune entrée physique | 53 |
| Contrôler puis confirmer explicitement la réception | Une seule base de stock de 100 m³ apparents | 54–57 |
| Enlever 25, puis 30, avec deux dates de constat | Soldes respectifs 75 puis 45 ; historique conservé | 63, 66 |
| Constater 42 m³ apparents restants | Solde attendu 45, écart −3 ; aucun ajustement automatique du stock | 69 |
| Préparer le ZIP, choisir Downloads et enregistrer via Android | Fichier réel de 8 355 octets, relu indépendamment | 71–74 |
| Archiver puis retrouver le chantier | Retiré des actifs, présent dans les archives, détail et liens conservés | 78–80 |
| Rouvrir l’APK final après installation et les neuf essais automatiques | Chantier archivé et réception validée retrouvés après chargement | 89–94 |
| Changer de métier vers Technicien forestier | Accueil Inventaire/martelage et Explorer Forêts, Parcelles, Placettes, Diagnostics ; Missions absent | 81–85 |

La réception utilise la convention explicitement saisie
`TEST enveloppe rectangulaire [v:1]`. Dates de constat en `Etc/UTC`, distinctes des
dates d'enregistrement. Ce scénario ne définit aucune convention commerciale.

## Contrôle indépendant de l'export

Le ZIP extrait de Downloads a passé une vérification Python en lecture seule :
CRC, huit fichiers distincts, sept tailles et empreintes SHA-256, une parcelle,
un dépôt, un bois, une réception en 25 versions et quatre événements uniques.
Une seule version validée contient un crédit physique. Recalcul indépendant :
`100 − 25 − 30 = 45` ; le constat daté de `42` représente un écart de `−3`.

SHA-256 de l'archive :
`9b5bc7512308360bade630ac62bc65396b5910dfe319b993cc96a99848fa6727`.

`balances.csv` donne 45 à l'instant de capture. Ses colonnes de constat et d'écart
sont inconnues, car il n'existe aucun constat à cet instant exact. Le constat
antérieur de 42 et son horodatage sont conservés dans `ledger.csv` et
`snapshot.json`. Une future projection « dernier constat daté » rendrait cette
distinction plus pratique pour le destinataire. Aucun fichier n'a été transmis à
FORLOG ou GSIE ; les photos restent des références dans ce format d'export.

## Correctifs issus des essais

1. Clés stables des éléments de liste dans Chantier, Réceptions et Carnet : la
   disparition d'une erreur ne recrée plus le champ et ne lui retire plus le focus.
2. Nom de chantier manquant : message précis sous le champ, en français/anglais.
3. Menu flottant masqué dans les six routes de saisie terrain concernées : boutons
   de sauvegarde/validation dégagés, retours propres aux écrans conservés.
4. Ouverture du détail de chantier et retour au carnet en haut de page après
   sauvegarde ; aucun retour en haut pendant la frappe.
5. Nouvelles erreurs du Carnet et des Réceptions rendues visibles immédiatement,
   y compris quand la validation est demandée depuis le bas d'un formulaire.

Trois tests de régression emploient un seul tap de focus suivi de vraies frappes
clavier. Les scénarios existants vérifient aussi les erreurs visibles, le retour
au journal, l'archivage et l'enregistrement dans Android DocumentsUI.

## Vérifications et traçabilité de compilation

Le parcours manuel a commencé sur le code `d42240d`, puis a repris sur l’APK
corrigé `6a885d296e7bd68ab4f231c4e9171c45df051beb92c71c4c24cc0f56dc486a4d`.
Les derniers ajustements de visibilité et défilement sont vérifiés séparément.

- JVM : **1 822 tests, aucun échec ni test ignoré**, dans la compilation isolée
  `build-20261019T115926Z` ; sources inchangées pendant cette exécution.
- APK final : compilation `build-20261019T121458Z`, réussie, **1 179 fichiers
  sources suivis et aucun changement pendant l’exécution**. Depuis les tests JVM,
  seuls les cinq fichiers de notices/crédits ci-dessous ont rejoint les sources.
- Lint : **0 erreur, 997 avertissements** ; ils restent une dette distincte de
  cette recette. Les 273 assets de l’APK intermédiaire sont conservés octet pour
  octet, de même que les crédits du fond vidéo.
- APK de recette : 115 503 057 octets, SHA-256
  `071cdb412e84956b0dbf77adb5eea8cc4fe3a2c167d042ec43b9cde90ba3a621`.
- Android : la suite du `android-20261019T122055Z` a exécuté neuf tests, dont huit
  réussis. Un échec `SQLiteDiskIOException` est survenu pendant l’ouverture de la
  base du test d’export. Le disque du simulateur disposait de 4,7 Go libres.
  L’examen de la fixture a identifié une base réutilisée entre tests, fermée et
  supprimée avant la destruction de l’activité et de ses ViewModels. La correction
  porte sur l’isolation et la fermeture des ressources du test, sans modifier les
  assertions ni le code métier. La relance complète de **ReceiptFlowUiTest :
  4/4 réussis** (`android-20261019T123137Z`) comprend le vrai sélecteur Android,
  l’enregistrement, la relecture et la vérification du ZIP. Avec les **5/5
  FieldToolsUiTest** inchangés de la première exécution, **neuf scénarios distincts
  passent**. L’échec initial et sa correction sont conservés dans les preuves.
- La recompilation des tests `build-20261019T122855Z` est réussie, sans changement
  des sources pendant son exécution. Seule la fixture Android a changé depuis la
  validation de l’APK utilisateur ; l’empreinte de cet APK reste inchangée.
- Nouvelle ouverture manuelle de cet APK : chantier archivé et réception validée
  retrouvés après chargement (captures 89–94).

La compilation du dépôt partagé a détecté des modifications cartographiques
concurrentes et quatre erreurs Lint dans ce nouveau lot. Elle est exclue des
preuves de livraison. Une branche `fix/recette-utilisateur-2026-10-19`, basée sur
`d42240d`, isole les neuf fichiers UX concernés dans `/workspace/GeoSylva-user-tests`.
Le travail cartographique du dépôt initial est préservé et n'est pas évalué ici.

## Conservation des notices et licences

La reconstruction depuis Git a révélé cinq notices ignorées par la règle globale
`*.txt` : Open Sans, MapLibre DemoTiles, leur NOTICE, les conditions du parcellaire
ONF et les crédits du fond vidéo d'inscription. Les fichiers originaux ont été
repris octet pour octet depuis le dépôt de travail et des exceptions ciblées ont
été ajoutées au suivi Git. Ce changement complète la recette de distribution.

## Améliorations à traiter ensuite

- Pendant le chargement des réceptions, un message « aucune réception » peut apparaître avant les données. Remplacer cet état temporaire par un chargement explicite.
- Formulaires encore longs : regrouper les données essentielles et les compléments.
- Rattachement dépôt/bois/chantier : réduire les retours nécessaires, en conservant
  une association explicite au bon chantier.
- Compteur « 0 dépôt / 1 bois » : il compte les liens directs alors que le bois
  possède un dépôt. Clarifier le libellé.
- Les sauvegardes automatiques exposent beaucoup de versions de brouillon ; rendre
  leur présentation moins technique sans supprimer la traçabilité.
- Une saisie ADB rapide a été tronquée sous charge de compilation ; elle a été
  complétée puis conservée à la reprise. Aucune course d'écrasement du formulaire
  n'a été trouvée à la lecture. Cela reste à qualifier avec un vrai clavier tactile.

Ces essais ne certifient pas la fluidité sur téléphone, les capteurs réels, la
capture photo, la navigation GPS, Google, GSIE, les paiements ou le connecteur
FORLOG. Le parcours complet de gestion forestière et une réception de grumes
n’ont pas été rejoués manuellement dans cette recette centrée sur une pile de
bois, sa réception et son suivi.

Les captures, actions horodatées, ZIP et vérificateur sont conservés dans
`/workspace/.onboarding/user-journey-2026-10-19/` et copiés dans le dossier de
livraison local ; ils ne contiennent que les données TEST de cette recette.
