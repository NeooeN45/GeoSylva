# État d’intégration des maquettes et des deux parcours prioritaires

Point de situation du **15 novembre 2026**, établi par lecture du code pendant la
stabilisation. Il distingue une fonctionnalité présente, son raccordement dans
l’application et la conformité à la maquette approuvée. Ce suivi ne constitue pas
une recette du téléphone.

## Décompte et errata du 16 novembre 2026

Le [décompte maquette → écran](DECOMPTE_MAQUETTES_ECRANS_2026-11-16.md), son
[CSV](DECOMPTE_MAQUETTES_ECRANS_2026-11-16.csv) et ses
[preuves structurées](PREUVES_MAQUETTES_ECRANS_2026-11-16.json) sont établis sur
`main` à `77a5d72020c0f54d8c35e3513b02019919c247d4`.
Ils inventorient 117 repères, dont 107 références de travail : 78 raccordées,
3 sans raccord dans le parcours cible, 25 non codées et une à rattacher.
Ce ne sont ni 117 écrans indépendants, ni des validations au pixel.

Les archives de conception permettent désormais d'identifier les numéros,
variantes et décisions. Le lot de conservation du même jour a récupéré quatre
originaux de pièces jointes sur les 107 références de travail ; 103 restent
introuvables. Le contrôle
des appels corrige 56 (composant défini mais jamais appelé) et lève les doutes
d'identité documentaire sur 64 et 90B. Les trois lignes sont rectifiées plus bas.
Les constats du 15 novembre et leurs résultats d'essai gardent leur portée
historique ; aucun nouveau test ou essai sur appareil n'est ajouté par cet audit.

## Conservation des originaux — 16 novembre 2026

Le [bilan de récupération](../maquettes/RECUPERATION_2026-11-16.md), le
[manifeste SHA-256](../maquettes/MANIFESTE_IMAGES.json) et la
[liste des 103 manquantes](../maquettes/MAQUETTES_INTROUVABLES_2026-11-16.csv)
conservent les preuves : 54 (Technicien), 72 (commun), dossier chantier02 et fiche
du bois01 (Conducteur). Aucun fichier n'est recréé ou retouché ; les 107
conformités visuelles restent non vérifiées. Les états de codage sont inchangés.

Règle obligatoire : **dans la même session**, toute maquette créée ou modifiée
est enregistrée sous `docs/maquettes/` avec son identifiant, inscrite au manifeste
avec empreinte et dimensions, liée à la décision du Fondateur qui la retient ou
l'écarte, puis poussée sur une branche distante avant la fin de session. Un
choix en attente reste `non finalisée`. Contrôler contenu et métadonnées pour le
dépôt public ; garder hors Git et signaler tout secret, donnée personnelle ou
donnée réelle de client. Préserver les variantes et la sauvegarde distante datée.
**Une maquette qui n'existe que dans une conversation n'est pas une référence.**

Le [contrôle CI](../../.github/workflows/maquettes.yml) exécute
`python3 tools/check_mockup_images.py` : chaque référence de travail doit avoir
son original et une empreinte conforme. Les absences historiques sont listées
sans échec ; incohérences et fausses présences bloquent. Quand le stock est
complété, ajouter `--strict` à la commande du workflow dans un lot relu. Voir
les [consignes de conservation](../maquettes/README.md).

## Ce qui est vérifié et ce qui ne l’est pas

- Le passage du **14 novembre** a produit un APK debug sur la source
  `33d904901a320cb3439e25a2af4d2efdfebe444a` et **2 083 tests JVM réussis**
  (2 036 application et 47 cartographie). Voir la
  [preuve datée](../recovery/APPLICATION_VALIDATION_2026-11-14.json).
- Le passage du **15 novembre** réussit les tâches `test lint assembleDebug
  bundleRelease :app:assembleDebugAndroidTest` : **2 046 tests applicatifs** et
  **47 cartographiques**, dans chacune des variantes debug et release, plus
  **7 tests du vérificateur d'échange**. Lint ne rapporte aucune erreur ; ses
  avertissements existants ne sont pas tous corrigés. Les résultats natifs et
  les limites sont consignés dans le [bilan courant](VALIDATION_STABILISATION_2026-11-15.md).
- **35 cas natifs ciblés** sont vérifiés sur l'émulateur logiciel API28, dont
  sept de migrations et huit Mapbox. Le dernier lot Mapbox passe intégralement ;
  la suite instrumentée complète n'a pas été exécutée.
- Ces résultats ne prouvent ni les essais S25 Ultra, ni une journée hors connexion,
  ni la conformité visuelle des 100 maquettes.
- Aucun pourcentage d’intégration n’est annoncé : le catalogue original des
  100 images et de leurs variantes doit encore être rapproché de chaque route,
  état, capture et décision de conformité.
- Les sources sous `docs/recovery/pending-integration/` sont préservées ; leur
  présence ne signifie pas qu’un utilisateur peut ouvrir leur interface.

## Technicien public et privé

Le parcours de gestion existant est conservé : forêts, parcelles, placettes,
relevés, saisie des tiges, martelage, diagnostics et Synthèse ont des routes dans
[ForestryFlowNavGraph][forest-routes], [ForestDetailNavGraph][creation-routes] et
[DiagnosticNavGraph][diagnostic-routes]. Le choix de profil distingue notamment
ONF, CNPF, DDT, expert et technicien ; cela ne qualifie pas à lui seul toutes les
spécificités d’un métier ou d’un organisme.

| Partie | État constaté | Reste avant de la déclarer terminée |
| --- | --- | --- |
| Parcours existant | Raccordé ; l’architecture de navigation Technicien est préservée. | Recette complète sur l’APK courant, droits, reprise et mise à jour sans perte. |
| Synthèse | `MartelageScreen` appelle les trois sections **Gestion, Bois, Écologie**, les indicateurs dendrométriques, la distribution des diamètres, des détails et les exports. | Reproduire et comparer la version visuelle retenue. Les composants `AnalysisMetric` et `SummaryLine` n’ont pas une puce d’explication pour chaque donnée : cette exigence reste incomplète. |
| Calculs de Synthèse et exports | La politique actuelle rend le volume indisponible si le tarif n’est pas qualifié ; les nouveaux exports d’observations utilisent la même politique. | Qualifier les méthodes activables par source, essence, domaine et mesures ; vérifier les résultats et les exports de référence. |
| Diagnostics et sous-pages | Menu, sélection de portée, diagnostics sylvicoles, station, ripisylve et IBP existent et sont reliés aux routes. | Revue et améliorations visuelles convenues, données manquantes, aides et recette métier. Leur seule présence ne prouve pas la nouvelle interface validée. |
| Hauteur par caméra et GPS | Des outils de hauteur existent (`TreeHeightMeasureTool`, `TreeHeightMeasureDialog`). | Vérifier le parcours guidé retenu, ses conditions de calcul et sa précision sur le S25 avec une référence terrain. Aucune fiabilité physique de 100 % n’est établie. |

**Conclusion de suivi : le métier Technicien n’est pas finalisé.** Sa base métier
est développée et raccordée ; Synthèse, Diagnostics, interfaces et qualification
terrain restent à clore suivant les décisions prises ensemble.

## Conducteur de travaux forestiers

| Partie | État constaté | Reste avant de la déclarer terminée |
| --- | --- | --- |
| Parcours métier | Profil Conducteur résolu vers le parcours opérations. Accueil et routes Chantiers, Bois et Carnet sont présents. | Recette des enchaînements réels et spécialisation complète du métier. |
| Chantiers | **Raccord ajouté le 15 novembre** : `WorksitesScreen` appelle les six composants du dossier retenu, avec les données du dépôt local, le carnet, les bois, les réceptions, les exports et le menu de gestion. Quatre tests natifs de ce dossier passent. | Comparer les captures à la proposition 02. L'action Carte ouvre le choix des parcelles liées accessibles, puis la carte commune ; elle ne représente pas encore toutes les parcelles du chantier sur une seule carte. |
| Carnet terrain | Événements physiques, notes, accès, activités, incidents, photos et brouillons utilisent le dépôt local. Les vrais composants de quantité et de dates sont raccordés à `FieldJournalScreen`. Les retours de caméra/galerie sont liés à l'identifiant du brouillon d'origine, avec dix nouveaux tests JVM. | Recetter les photos sur le téléphone et terminer l’ergonomie validée. `JournalHistory` est un composant autonome ; un parcours complet de consultation de l’historique n’est pas établi par sa seule définition. |
| Bois et dépôts | Objets pile/grume, identité, observations manuelles, corrections et sélection du relevé de référence existent. Le formulaire `PileMeasurementForm` est effectivement appelé par `WoodForm`. | La **fiche du bois retenue** `WoodFileCard` reste sous `pending-integration` : elle ne doit pas être comptée comme interface accessible. |
| Réceptions et mouvements | `ReceiptsScreen` et `ReceiptsViewModel` sont raccordés ; le code local sépare réception physique, observations et mouvements. Les soldes locaux existants ne sont pas des quantités commerciales automatiquement payables. | Recette actuelle des validations, doublons, corrections, unités/conventions et reprise après arrêt. Terminer les contrats de références figées de contrôle et leur présentation. |
| Stock daté et nouvelle fiche de suivi | La logique locale antérieure existe. Les nouveaux contrats `stockSnapshot`, bases figées de contrôle et état de la fiche bois ne sont pas intégralement raccordés. | Compléter les contrats réels et leurs tests, puis activer la fiche retenue ; aucun stock ne doit être inventé pour remplir l’interface. |
| Vente et services externes | Des fondations locales sont communes au bois et au chantier. | Vente contractuelle, facturation, connecteur FORLOG et Missions serveur ne sont pas établis comme parcours opérationnels livrés. |

**Conclusion de suivi : le métier Conducteur est commencé avec une base locale
réelle ; il n’est pas finalisé et toutes ses maquettes ne sont pas intégrées.**
La frontière exacte des deux sources en attente est consignée dans
[WOOD_FIELD_INTEGRATION_BOUNDARIES.json](../recovery/WOOD_FIELD_INTEGRATION_BOUNDARIES.json).
Ce document du 14 novembre conserve son état historique ; les composants de
quantité et de dates ont évolué le 15 novembre.

## Cartes communes et premières familles de maquettes

| Famille retenue | État du raccordement |
| --- | --- |
| Cartes Terrain et Carte libre ; fonds, couches, relief, sources et outils | Le même `MapRechercheScreen` et le même moteur Mapbox sont utilisés pour ces contextes. Les panneaux communs existent. Le catalogue d’outils reste explicitement à compléter, conformément à la demande utilisateur. |
| Mode **Maps**, itinéraire, étapes, démarrage du guidage, GPS perdu/retrouvé et arrivée (notamment références 13 et 48–53) | Les contrats de recherche, d’itinéraires et de radars existent ; **l’entrée Maps appelle encore `CarteModeStubScreen`**. Les écrans de navigation approuvés ne constituent pas encore un parcours accessible complet. |
| Carte libre immersive, couches et source/détails | Les panneaux de la carte commune sont raccordés ; conformité des styles et captures à vérifier. |
| Accueil à trois modes et illustrations retenues | Le sélecteur existe. Le helper de la référence 21 et son visuel source approuvé ne sont pas intégrés ; ne pas assimiler le sélecteur actuel à cette version finale. |
| Hors connexion : emprise affichée | `OfflineViewPackPreparation` est ouvert depuis la carte, avec contrôleur commun et dialogues de préparation. Essais de téléchargement, stockage et reprise à faire sur l’APK courant. |
| Gestion Mapbox des zones et packs, régions/départements, mises à jour et sous-pages | **Raccord ajouté le 15 novembre et compilation vérifiée** : Réglages → Packs ouvre `OfflinePackManagerScreen` avec le contrôleur commun. L'action de fichiers existante ouvre toujours `PackManagerScreen`, le gestionnaire administratif signé ; le retour restaure l’état mémorisé de la page précédente. La recette des téléchargements reste à faire. |

Le gestionnaire s’active actuellement dans la portée autorisée `MapDataScope.Free`.
Les préparations depuis une forêt, une parcelle ou une placette gardent une autre
clé de contexte : cette bibliothèque n’est pas une agrégation globale de tous les
packs du propriétaire. Un changement de portée invalide les travaux de l’ancien
contexte dans le contrôleur. La conservation des packs, l’affichage de la bonne
bibliothèque et la reprise des transferts lors de ces transitions doivent donc
être vérifiés. Ce raccord ne modifie ni les droits ni les dépôts pour contourner
cette séparation.

Les références 1–53 ne disposent pas encore dans ce dépôt d’un catalogue unique
de leurs images approuvées permettant une correspondance fiable une par une.
Les numéros ci-dessus ne remplacent pas ce rapprochement.

## Correspondance technique des références 54 à 100

**R** = écran ou état raccordé par lecture des routes et appels ; son exécution
complète reste à vérifier. **À réconcilier** = la référence exacte ou sa
correspondance n’est pas suffisamment établie. **Conformité photo : non vérifiée
pour chacune des lignes ci-dessous.** Ces statuts ne signifient pas « maquette
terminée au pixel près ».

| N° | Interface décidée | Correspondance constatée | État |
| --- | --- | --- | --- |
| 54 | Ajouter une forêt | `ForestMapCreationScreen` via `ForestDetailNavGraph`. | R |
| 55 | Choisir une parcelle | `ParcelMapCreationScreen` via le même graphe. | R |
| 56 | Source et détails | `ForestryCreationSourcePanel` réutilise `ForestrySourceDetails`, mais aucun appel de production n'est trouvé au contrôle du 16 novembre. | Composant compilé, non raccordé ; ancien R corrigé |
| 57 | Placer les placettes | `CreatePlacetteWizard` → `PlacetteMapCreationScreen`. | R |
| 58 | Paramètres des placettes | Panneau `PARAMETERS`, `PlotParameterEditor`. | R |
| 59 | Grille automatique | Mode `AUTOMATIC_GRID`, aperçu et génération par le ViewModel. | R |
| 60 | Réglages de répartition | Panneau `DISTRIBUTION`, `PlotDistributionEditor`. | R |
| 61 | Liste des placettes | Panneau `LIST` du même hôte. | R |
| 62 | Fiche d’une placette planifiée | Panneau de détail et empreinte sélectionnée. | R |
| 63 | Empreinte en bordure | Prévisualisation hors contour, avertissement et confirmation dédiés. | R |
| 64 | Arbre sélectionné sur la carte | Les échanges d'origine retrouvés identifient explicitement la sélection du relevé ; `MapTreeWorkspace` ouvre `TreeWorkspacePanel.RECORD`. | R ; identité documentaire retrouvée le 16 novembre, pixels non vérifiés |
| 65 | Boussole vers l’arbre | `MapTreeWorkspace` → `TreeWorkspacePanel.GUIDANCE`. | R |
| 66 | Approche du point enregistré | États de guidage du même panneau, avec relevé et positions distinctes. | R |
| 67 | Compas perturbé | État de direction indisponible du guidage ; comportement matériel à vérifier. | R |
| 68 | Position GPS perdue | État de position non courante et suppression du guidage actuel. | R |
| 69 | Essences affichées | Panneau `SPECIES` et projection filtrée. | R |
| 70 | Précision enregistrée | Panneau `ACCURACY`, seuil et exclusions. | R |
| 71 | Aucun point après filtrage | Panneau `EMPTY` et actions de rétablissement. | R |
| 72 | Mesurer une distance | `MapManualMeasureUi`, mode distance. | R |
| 73 | Mesurer une surface | Même hôte, mode surface. | R |
| 74 | Enregistrer une mesure | `MeasurePanel.SAVE`. | R |
| 75 | Mesures enregistrées | `ManualMeasureAuxiliary.SAVED`. | R |
| 76 | Détail d’une mesure | `ManualMeasureAuxiliary.DETAIL`. | R |
| 77 | Calcul et détails | `ManualMeasureStoredCalculation` et aide de la mesure. | R |
| 78 | Reprendre une mesure | Action de reprise par copie du ViewModel, depuis le détail. | R |
| 79 | Partager une mesure | `ManualMeasureAuxiliary.SHARE`, résumé et partage Android. | R |
| 80 | Unités de mesure | Panneau d’unités du même outil. | R |
| 81 | Importer une couche | `MapLayerHost` → panneau `IMPORT`. | R |
| 82 | Formats disponibles | Panneau `FORMATS`. | R |
| 83 | Aperçu d’import | Panneau `PREVIEW` avant ajout. | R |
| 84 | Données de la couche | Panneau `DATA`, lié à la sélection cartographique. | R |
| 85 | Attributs d’un objet | Panneau `ATTRIBUTES`. | R |
| 86 | Source et champs | Panneau `SOURCE`. | R |
| 87 | Gérer une couche | Panneau `MANAGE`. | R |
| 88 | Modifier les attributs | Panneau `EDIT`, dépôt et opération versionnée. | R |
| 89 | Filtrer les objets | Panneau `FILTER`. | R |
| 90 | Apparence et couleur — 90B | Panneau `APPEARANCE` ; l'édition ajoutant la palette et la réponse « Parfait » identifient la variante 90B dans les échanges retrouvés. | R ; identité documentaire retrouvée le 16 novembre, pixels non vérifiés |
| 91 | Couleur personnalisée | Panneau `COLOR`, `CustomLayerColorPanel`. | R |
| 92 | Préparer un tracé GPS | `MapTraceHost` → `TraceScreen.PREPARATION`. | R |
| 93 | Tracé en cours | `TraceScreen.CAPTURE`, état `RECORDING`. | R |
| 94 | Réception GPS dégradée | État `WAITING_FOR_QUALITY` du même hôte. | R |
| 95 | Reprise du tracé | Reprise avec portions séparées et information de reprise. | R |
| 96 | Pause manuelle | État `MANUALLY_PAUSED`. | R |
| 97 | Vérifier le tracé | `TraceScreen.REVIEW`. | R |
| 98 | Enregistrer le tracé | `TraceScreen.SAVE`. | R |
| 99 | Tracé enregistré | `TraceScreen.SAVED`. | R |
| 100 | Calcul et réception | `TraceScreen.DETAILS`, longueurs par portion et passages non relevés. | R |

Les états GPS/compas et les téléchargements exigent des essais réels : un branchement
de composant ne prouve ni la réception du capteur, ni la disponibilité des tuiles.
Le futur catalogue de styles prédéfinis par élément ajouté à la carte et le partage
de cartes composées restent à organiser ; les commandes de couleur actuelles ne
doivent pas être présentées comme ce futur système complet.

## Ordre de clôture recommandé

1. Reproduire le lot de stabilisation reçu en local, puis compléter la recette
   physique ; conserver les preuves natives distinctes des preuves historiques.
2. Recetter le dossier Conducteur nouvellement raccordé et terminer les contrats des
   fiches bois/contrôles avant d’activer les composants en attente.
3. Terminer la Synthèse Technicien avec les explications par donnée, puis les
   améliorations Diagnostics convenues en conservant le reste de son parcours.
4. Recetter le gestionnaire Mapbox hors ligne nouvellement raccordé, traiter
   explicitement ses portées, puis raccorder le parcours Maps complet.
5. Compléter la matrice **image approuvée → route → état → capture → écart → décision**,
   puis clore les parcours sur le S25 Ultra, y compris la journée sans connexion.

[forest-routes]: ../../app/src/main/java/com/forestry/counter/presentation/navigation/ForestryFlowNavGraph.kt
[creation-routes]: ../../app/src/main/java/com/forestry/counter/presentation/navigation/ForestDetailNavGraph.kt
[diagnostic-routes]: ../../app/src/main/java/com/forestry/counter/presentation/navigation/DiagnosticNavGraph.kt
