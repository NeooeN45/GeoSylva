# Décompte maquette → écran — GeoSylva

Audit documentaire du **16 novembre 2026**, affichage des dates en Europe/Paris.
Code audité : `77a5d72020c0f54d8c35e3513b02019919c247d4` (`main` au départ).
Branche : `docs/decompte-maquettes-ecrans-2026-11-16`.

## Ajout du 19 novembre 2026

Deux propositions de [choix du métier](../maquettes/demarrage/README.md)
complètent le registre et le CSV. La 02 est retenue en thème clair, la 01 est
remplacée. Le registre courant compte 119 repères et 108 références de
travail. Les chiffres ci-dessous décrivent le périmètre historique du
16 novembre.

Le lancement concerne **Technicien forestier, public et privé, et Conducteur de
travaux forestiers**. Les interfaces communes sont comptées une seule fois.
Les autres métiers restent gelés. Ce lot ne change aucun code applicatif.

## A. Unité de compte et méthode

L'inventaire contient **117 repères** : les numéros cartographiques 1–100,
15 références Conducteur et deux références Synthèse antérieures, dont la
numérotation est indépendante. Ce ne sont pas 117 écrans Kotlin distincts.
Les variantes successives d'un même écran ne créent pas autant de fonctions.

Parmi ces 117 repères, **107 sont des références de travail** : 104 sélections
positives sourcées, une poursuite sans validation expresse (49), deux références
Synthèse attestées documentairement sans événement original récupéré. Les neuf
repères remplacés et le prototype 11 non finalisé restent visibles pour la
traçabilité, mais sont exclus de ce sous-ensemble. Il ne faut donc pas appeler
ces 107 lignes « 107 maquettes indépendantes validées au pixel près ».

Les échanges historiques, prompts, copies d'artefacts et décisions ont permis
de retrouver les identifiants, notamment **64** et **90B**. La recherche initiale
portait sur les images locales et 38 ZIP. Le lot de conservation du même jour
a ensuite récupéré **4 originaux de pièces jointes sur les 107 références de
travail** : 54, 72, dossier chantier02 et fiche du bois01. Le
[manifeste](../maquettes/MANIFESTE_IMAGES.json) et le
[bilan de recherche](../maquettes/RECUPERATION_2026-11-16.md) en donnent les preuves.
Les **103 autres images de travail manquent** ; les 107 conformités visuelles
des écrans restent non vérifiées. Une description ou une approbation dans
l'historique ne remplace pas une comparaison visuelle sur le téléphone.

### Sens des trois états

- **Codé et raccordé aux données** : composant compilé, appel effectif dans le
  parcours ciblé, et données, contenu informatif ou actions réellement fournis.
  Une page d'aide statique correcte n'a pas besoin d'un dépôt Room pour exister.
  Ce statut ne signifie ni conformité graphique, ni métier terminé.
- **Codé sans données** : composant compilé sans raccord dans le parcours ciblé.
  Il peut accepter des paramètres ou fonctionner ailleurs. Pour **41C**, la
  recherche IGN fonctionne dans la carte commune ; c'est son parcours Maps qui
  manque. Pour **56** et l'historique Conducteur, aucun appel de production du
  composant spécifique n'est établi.
- **Non codé** : aucune présentation spécifique compilée correspondant à la
  référence. Un contrat métier, un écran générique différent ou un fichier
  préservé `.kt.source` ne comptent pas comme cette maquette intégrée.

**Une ligne reste « — (à rattacher) » : Bois associés.** Une page opérationnelle
existe, mais sa correspondance avec la référence retenue n'est pas suffisamment
établie. Ce manque n'est pas converti artificiellement en « non codé » et n'est
pas un quatrième état de développement. Il reste séparé dans les totaux.

Pour les neuf repères remplacés, la ligne décrit l'équivalent fonctionnel actuel
de la famille et indique la décision séparément. Elle ne certifie pas que le
visuel écarté a été reproduit et ne demande pas de le redévelopper. Le métier est
une classification d'usage : commun désigne les destinataires et la réutilisation,
pas une preuve qu'un écran encore absent serait accessible ni un droit aux données.

### Portée des vérifications

**¹ Les vérifications du tableau sont historiques et limitées.** Aucun build,
test, essai sur appareil ou comparaison de captures n'a été exécuté dans ce lot.
Les sources et tests applicatifs et cartographiques sont identiques à ceux de
`8ea8cde4b9c7a283e4404c45f39298ed5375af26`, source du
[bilan du 15 novembre](VALIDATION_STABILISATION_2026-11-15.json).
L'audit a vérifié cette identité et lu les tests cités ; il ne réattribue pas
leur exécution au commit courant.
Un test simplement présent, sans exécution établie dans ces preuves, reste un
support dans le JSON et ne qualifie pas la cellule « Vérification ».

- **Test JVM** : état de modèle, calcul, contrat ou ViewModel, souvent avec dépôt
  simulé. L'agrégat historique de la suite est disponible ; tous les XML par
  classe ne sont pas versionnés. Ce n'est pas un test visuel de l'écran.
- **Test instrumenté** : cas nommé dans les preuves API28 du 15 novembre.
  Les huit tests Mapbox montent un hôte de composants, une scène fournie et un
  fond local uni ; ils ne montent pas `MapRechercheScreen` avec ses dépôts et
  ne qualifient pas le satellite réel. Les tests de couches montent aussi un
  état fourni ; les composants Conducteur sont testés isolément.
- **Aucune** : aucune preuve de la présentation spécifique. Les tests de modèles
  ou composants de support restent référencés dans le JSON, même lorsqu'une UI
  n'est pas codée ; ils ne rendent pas cette UI testée.
- **Essai sur appareil physique** : aucune preuve pour les références de cet
  audit. Aucun essai S25 Ultra, journée hors connexion ou pixel parfait n'est
  déclaré. La suite instrumentée complète n'a pas été exécutée.

Le fichier [CSV](DECOMPTE_MAQUETTES_ECRANS_2026-11-16.csv) donne une ligne par
repère avec route, données et limites. Le [JSON de preuves](PREUVES_MAQUETTES_ECRANS_2026-11-16.json)
ajoute les chemins/lignes, tests de support, décisions courtes et empreintes des
archives historiques. Les archives de conversation complètes, images, données
client et configurations personnelles ne sont pas versées au dépôt.
Floorplan est indisponible ; l'audit repose sur les appels et sources réels,
sans annoncer une carte de code créée ou actualisée.

### Correspondance des 117 repères

| Identifiant et maquette | Métier | Écran Kotlin | État | Vérification existante | Image originale locale |
|---|---|---|---|---| --- |
| cartographie/01 — Carte commune — première proposition — remplacée | commun | [MapRechercheScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapRechercheScreen.kt#L128) | codé et raccordé aux données | test instrumenté¹ | absente |
| cartographie/02 — Accueil Carte — trois thèmes — remplacée | commun | [CarteModeSelectorScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/CarteModeSelectorScreen.kt#L44) | codé et raccordé aux données | aucune | absente |
| cartographie/03 — Terrain forestier — première présentation — remplacée | commun | [MapRechercheScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapRechercheScreen.kt#L128) | codé et raccordé aux données | test instrumenté¹ | absente |
| cartographie/04 — Terrain forestier — carte immersive | commun | [MapRechercheScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapRechercheScreen.kt#L128) | codé et raccordé aux données | test instrumenté¹ | absente |
| cartographie/05 — Couches — première présentation — remplacée | commun | [MapWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/MapWorkspace.kt#L410) | codé et raccordé aux données | test instrumenté¹ | absente |
| cartographie/06 — Couches — catalogue — remplacée | commun | [MapWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/MapWorkspace.kt#L410) | codé et raccordé aux données | test instrumenté¹ | absente |
| cartographie/07 — Couches — aperçus illustrés | commun | [MapWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/MapWorkspace.kt#L410) | codé et raccordé aux données | test instrumenté¹ | absente |
| cartographie/08 — Fond — trois fonds — remplacée | commun | [MapWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/MapWorkspace.kt#L360) | codé et raccordé aux données | test instrumenté¹ | absente |
| cartographie/09 — Fond — catalogue | commun | [MapWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/MapWorkspace.kt#L360) | codé et raccordé aux données | test instrumenté¹ | absente |
| cartographie/10 — Relief | commun | [MapWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/MapWorkspace.kt#L471) | codé et raccordé aux données | test instrumenté¹ | absente |
| cartographie/11 — Mesurer — outil non finalisé — non finalisée | commun | [MapManualMeasureUi.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapManualMeasureUi.kt#L39) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/12 — Accès et itinéraires — remplacée | commun | aucun | non codé | aucune | absente |
| cartographie/13 — Maps — satellite 3D immersif | commun | aucun | non codé | aucune | absente |
| cartographie/14 — Examiner l’accès | commun | aucun | non codé | aucune | absente |
| cartographie/15 — Nouveau constat | commun | aucun | non codé | aucune | absente |
| cartographie/16 — Atlas et couches — remplacée | commun | [MainScaffold.kt](../../app/src/main/java/com/forestry/counter/presentation/navigation/MainScaffold.kt#L435) | codé et raccordé aux données | test instrumenté¹ | absente |
| cartographie/17 — Carte libre — satellite dégagé — remplacée | commun | [MainScaffold.kt](../../app/src/main/java/com/forestry/counter/presentation/navigation/MainScaffold.kt#L435) | codé et raccordé aux données | test instrumenté¹ | absente |
| cartographie/18B — Carte libre — satellite 3D immersif | commun | [MainScaffold.kt](../../app/src/main/java/com/forestry/counter/presentation/navigation/MainScaffold.kt#L435) | codé et raccordé aux données | test instrumenté¹ | absente |
| cartographie/19 — Détail d’une couche | commun | [MapRechercheScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapRechercheScreen.kt#L509) | codé et raccordé aux données | test instrumenté¹ | absente |
| cartographie/20 — Source et détails | commun | [MapWorkspaceSourceDetails.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapWorkspaceSourceDetails.kt#L27) | codé et raccordé aux données | test instrumenté¹ | absente |
| cartographie/21 — Accueil Carte — Maps, Terrain forestier, Carte libre | commun | aucun | non codé | aucune | absente |
| cartographie/22B — Enregistrer la vue affichée | commun | [MapRechercheScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapRechercheScreen.kt#L675) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/23B — Packs — carte immersive | commun | [OfflinePackManagerScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/packs/offline/OfflinePackManagerScreen.kt#L123) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/24 — Contenu du pack | commun | [OfflinePackManagerScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/packs/offline/OfflinePackManagerScreen.kt#L211) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/25 — Choisir les couches | commun | [OfflinePackPreparationPanel.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/packs/offline/OfflinePackPreparationPanel.kt#L64) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/26 — Estimation et confirmation | commun | [OfflinePackConfirmationSheet.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/packs/offline/OfflinePackConfirmationSheet.kt#L34) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/27 — Téléchargement en cours | commun | [OfflinePackDetailsPanel.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/packs/offline/OfflinePackDetailsPanel.kt#L86) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/28 — Pause et reprise | commun | [OfflinePackDetailsPanel.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/packs/offline/OfflinePackDetailsPanel.kt#L144) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/29 — En attente de Wi-Fi | commun | [OfflinePackDetailsPanel.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/packs/offline/OfflinePackDetailsPanel.kt#L149) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/30 — Disponible hors connexion | commun | [OfflinePackDetailsPanel.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/packs/offline/OfflinePackDetailsPanel.kt#L138) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/31 — Mes packs — Liste | commun | [OfflinePackManagerScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/packs/offline/OfflinePackManagerScreen.kt#L83) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/32 — Mes packs — Carte | commun | [OfflinePackManagerScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/packs/offline/OfflinePackManagerScreen.kt#L91) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/33 — Détail du pack | commun | [OfflinePackManagerScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/packs/offline/OfflinePackManagerScreen.kt#L171) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/34 — Couverture du pack | commun | [OfflinePackDetailsPanel.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/packs/offline/OfflinePackDetailsPanel.kt#L49) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/35 — Sources et versions | commun | [OfflinePackDetailsPanel.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/packs/offline/OfflinePackDetailsPanel.kt#L109) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/36 — Versions enregistrées | commun | [OfflinePackVersionsPanel.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/packs/offline/OfflinePackVersionsPanel.kt#L25) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/37 — Stockage des cartes | commun | [OfflinePackStoragePanels.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/packs/offline/OfflinePackStoragePanels.kt#L35) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/38 — Choisir les contenus | commun | [OfflinePackStoragePanels.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/packs/offline/OfflinePackStoragePanels.kt#L91) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/39 — Confirmation du retrait | commun | [OfflinePackStoragePanels.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/packs/offline/OfflinePackStoragePanels.kt#L145) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/40 — Retour au stockage | commun | [OfflinePackManagerScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/packs/offline/OfflinePackManagerScreen.kt#L109) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/41C — Rechercher une destination — style arrondi | commun | [MapWorkspaceHostDialogs.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapWorkspaceHostDialogs.kt#L75) | codé sans données | test JVM¹ | absente |
| cartographie/42B — Aperçu du lieu — version illustrée | commun | aucun | non codé | aucune | absente |
| cartographie/43 — Préparer l’itinéraire | commun | aucun | non codé | aucune | absente |
| cartographie/44 — Choisir le départ | commun | aucun | non codé | aucune | absente |
| cartographie/45 — Placer le départ sur la carte | commun | aucun | non codé | aucune | absente |
| cartographie/46 — Itinéraire prêt à calculer | commun | aucun | non codé | aucune | absente |
| cartographie/47 — Aperçu de l’itinéraire | commun | aucun | non codé | aucune | absente |
| cartographie/48 — Étapes du trajet | commun | aucun | non codé | aucune | absente |
| cartographie/49 — Démarrage du guidage — validation à confirmer | commun | aucun | non codé | aucune | absente |
| cartographie/50 — Guidage actif | commun | aucun | non codé | aucune | absente |
| cartographie/51B — Position perdue — sans jaune | commun | aucun | non codé | aucune | absente |
| cartographie/52 — Position retrouvée | commun | aucun | non codé | aucune | absente |
| cartographie/53B — Arrivée — pictogramme corrigé | commun | aucun | non codé | aucune | absente |
| cartographie/54 — Ajouter une forêt | technicien | [ForestMapCreationScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/ForestMapCreationScreen.kt#L26) | codé et raccordé aux données | test JVM¹ | [présente](../../docs/maquettes/cartographie/54.png) |
| cartographie/55 — Choisir une parcelle | technicien | [ParcelMapCreationScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/ParcelMapCreationScreen.kt#L21) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/56 — Source et détails de la parcelle | technicien | [ForestryCreationSourcePanel.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/ForestryCreationSourcePanel.kt#L20) | codé sans données | aucune | absente |
| cartographie/57 — Placer les placettes | technicien | [PlacetteMapCreationScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/PlacetteMapCreationScreen.kt#L41) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/58 — Paramètres des placettes | technicien | [PlacetteMapCreationScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/PlacetteMapCreationScreen.kt#L120) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/59 — Grille automatique | technicien | [PlacetteMapCreationScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/PlacetteMapCreationScreen.kt#L193) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/60 — Réglages de répartition | technicien | [PlacetteMapCreationScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/PlacetteMapCreationScreen.kt#L123) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/61 — Liste des placettes | technicien | [PlacetteMapCreationScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/PlacetteMapCreationScreen.kt#L126) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/62 — Fiche d’une placette planifiée | technicien | [PlacetteMapCreationScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/PlacetteMapCreationScreen.kt#L150) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/63 — Empreinte en bordure | technicien | [PlacetteMapCreationScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/PlacetteMapCreationScreen.kt#L172) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/64 — Arbre sélectionné sur la carte | commun | [MapTreeWorkspace.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapTreeWorkspace.kt#L132) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/65 — Boussole vers l’arbre | commun | [MapTreeWorkspace.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapTreeWorkspace.kt#L158) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/66 — Approche du point enregistré | commun | [TreeWorkspacePanel.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/trees/TreeWorkspacePanel.kt#L159) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/67 — Compas perturbé | commun | [TreeWorkspacePanel.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/trees/TreeWorkspacePanel.kt#L179) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/68 — Position GPS perdue | commun | [TreeWorkspacePanel.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/trees/TreeWorkspacePanel.kt#L189) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/69 — Essences affichées | commun | [MapTreeWorkspace.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapTreeWorkspace.kt#L145) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/70 — Précision enregistrée | commun | [MapTreeWorkspace.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapTreeWorkspace.kt#L151) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/71 — Aucun point après filtrage | commun | [MapTreeWorkspace.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapTreeWorkspace.kt#L179) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/72 — Mesurer une distance | commun | [MapManualMeasureUi.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapManualMeasureUi.kt#L74) | codé et raccordé aux données | test JVM¹ | [présente](../../docs/maquettes/cartographie/72.png) |
| cartographie/73 — Mesurer une surface | commun | [MapManualMeasureUi.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapManualMeasureUi.kt#L74) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/74 — Enregistrer une mesure | commun | [MapManualMeasureUi.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapManualMeasureUi.kt#L76) | codé et raccordé aux données | aucune | absente |
| cartographie/75 — Mesures enregistrées | commun | [MapManualMeasureUi.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapManualMeasureUi.kt#L92) | codé et raccordé aux données | aucune | absente |
| cartographie/76 — Détail d’une mesure | commun | [MapManualMeasureUi.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapManualMeasureUi.kt#L197) | codé et raccordé aux données | aucune | absente |
| cartographie/77 — Calcul et détails | commun | [ManualMeasureAuxiliaryPanels.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/ManualMeasureAuxiliaryPanels.kt#L409) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/78 — Reprendre une mesure | commun | [MapManualMeasureViewModel.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapManualMeasureViewModel.kt#L301) | codé et raccordé aux données | aucune | absente |
| cartographie/79 — Partager une mesure | commun | [MapManualMeasureUi.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapManualMeasureUi.kt#L202) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/80 — Unités de mesure | commun | [MapManualMeasureUi.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapManualMeasureUi.kt#L87) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/81 — Importer une couche | commun | [LayerWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/layers/LayerWorkspace.kt#L184) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/82 — Formats disponibles | commun | [LayerWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/layers/LayerWorkspace.kt#L193) | codé et raccordé aux données | test JVM, test instrumenté¹ | absente |
| cartographie/83 — Aperçu d’import | commun | [LayerWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/layers/LayerWorkspace.kt#L211) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/84 — Données de la couche | commun | [LayerWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/layers/LayerWorkspace.kt#L224) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/85 — Attributs de P-03 | commun | [LayerWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/layers/LayerWorkspace.kt#L251) | codé et raccordé aux données | test JVM, test instrumenté¹ | absente |
| cartographie/86 — Source et champs | commun | [LayerWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/layers/LayerWorkspace.kt#L260) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/87 — Couche ajoutée | commun | [LayerWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/layers/LayerWorkspace.kt#L305) | codé et raccordé aux données | test JVM, test instrumenté¹ | absente |
| cartographie/88 — Modifier les attributs | commun | [LayerWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/layers/LayerWorkspace.kt#L317) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/89 — Filtrer les objets | commun | [LayerWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/layers/LayerWorkspace.kt#L331) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/90B — Apparence — choix de couleur | commun | [LayerWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/layers/LayerWorkspace.kt#L352) | codé et raccordé aux données | test JVM, test instrumenté¹ | absente |
| cartographie/91 — Couleur personnalisée | commun | [LayerWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/layers/LayerWorkspace.kt#L107) | codé et raccordé aux données | test JVM, test instrumenté¹ | absente |
| cartographie/92 — Préparer un tracé GPS | commun | [TraceWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/trace/TraceWorkspace.kt#L169) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/93 — Tracé GPS en cours | commun | [TraceWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/trace/TraceWorkspace.kt#L193) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/94 — Réception GPS interrompue | commun | [TraceWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/trace/TraceWorkspace.kt#L193) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/95 — Reprise du tracé GPS | commun | [TraceWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/trace/TraceWorkspace.kt#L193) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/96 — Tracé GPS en pause | commun | [TraceWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/trace/TraceWorkspace.kt#L193) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/97 — Vérifier le tracé | commun | [TraceWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/trace/TraceWorkspace.kt#L217) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/98 — Enregistrer le tracé | commun | [TraceWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/trace/TraceWorkspace.kt#L228) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/99 — Tracé enregistré | commun | [TraceWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/trace/TraceWorkspace.kt#L258) | codé et raccordé aux données | test JVM¹ | absente |
| cartographie/100 — Calcul et réception | commun | [TraceWorkspace.kt](../../cartography-ui/src/main/java/com/quintessences/geosylva/cartography/ui/trace/TraceWorkspace.kt#L272) | codé et raccordé aux données | test JVM¹ | absente |
| conducteur/dossier-chantier/proposition-02 — Dossier chantier — fond clair uni | conducteur | [WorksitesScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/field/WorksitesScreen.kt#L67) | codé et raccordé aux données | test instrumenté¹ | [présente](../../docs/maquettes/conducteur/dossier-chantier-proposition-02.png) |
| conducteur/carnet-terrain/proposition-01 — Carnet terrain | conducteur | aucun | non codé | aucune | absente |
| conducteur/formulaire-note/proposition-01 — Nouvelle entrée — Note, version claire | conducteur | aucun | non codé | aucune | absente |
| conducteur/formulaire-activite/proposition-01 — Nouvelle entrée — Activité | conducteur | aucun | non codé | aucune | absente |
| conducteur/formulaire-acces/proposition-01 — Nouvelle entrée — Accès et restrictions | conducteur | aucun | non codé | aucune | absente |
| conducteur/detail-entree/proposition-01 — Détail de l’entrée | conducteur | aucun | non codé | aucune | absente |
| conducteur/correction-motivee/proposition-01 — Correction motivée | conducteur | aucun | non codé | aucune | absente |
| conducteur/historique-entree/proposition-01 — Historique de l’entrée | conducteur | [FieldJournalComponents.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/field/FieldJournalComponents.kt#L69) | codé sans données | test JVM, test instrumenté¹ | absente |
| conducteur/bois-associes/proposition-01 — Bois associés | conducteur | [WorksitesScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/field/WorksitesScreen.kt#L91) (candidat, identité à rattacher) | — (à rattacher) | aucune | absente |
| conducteur/fiche-bois/proposition-01 — Fiche du bois — stock à renseigner | conducteur | aucun | non codé | aucune | [présente](../../docs/maquettes/conducteur/fiche-bois-proposition-01.jpg) |
| conducteur/releve-pile/proposition-02 — Relevé de pile — version concise | conducteur | [PileMeasurementForm.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/wood/PileMeasurementForm.kt#L37) | codé et raccordé aux données | test JVM, test instrumenté¹ | absente |
| conducteur/stock-initial/proposition-01 — Stock initial | conducteur | [FieldQuantityEditor.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/field/FieldQuantityEditor.kt#L48) | codé et raccordé aux données | test JVM¹ | absente |
| conducteur/stock-renseigne/proposition-03 — Fiche du bois — stock renseigné et code couleur | conducteur | aucun | non codé | aucune | absente |
| conducteur/enlevement/proposition-01 — Enlever | conducteur | [FieldQuantityEditor.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/field/FieldQuantityEditor.kt#L48) | codé et raccordé aux données | test JVM¹ | absente |
| conducteur/controle/proposition-01 — Contrôler | conducteur | [FieldQuantityEditor.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/field/FieldQuantityEditor.kt#L48) | codé et raccordé aux données | test JVM, test instrumenté¹ | absente |
| technicien/synthese/proposition-02-a — Synthèse Technicien — Terrain clair A — référence documentaire | technicien | [MartelageScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MartelageScreen.kt#L140) | codé et raccordé aux données | test JVM¹ | absente |
| technicien/aide-surface-terriere/proposition-03 — Aide Surface terrière — référence documentaire | technicien | aucun | non codé | aucune | absente |

## B. Interfaces et messages provisoires

Une absence de données légitime n’est pas une fonctionnalité factice. La liste
ci-dessous sépare les écrans, les messages de disponibilité et les chaînes non
appelées ; les routes latentes ne sont pas dites accessibles.

| Interface ou message | Source | Entrée / accès actuel | Lancement | Qualification |
|---|---|---|---|---|
| Maps — navigation temps réel « en construction » | [MainScaffold.kt](../../app/src/main/java/com/forestry/counter/presentation/navigation/MainScaffold.kt#L426), [CarteModeStubScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/CarteModeStubScreen.kt#L36) | Carte → sélecteur → Maps | oui | fonctionnalité spécifique à venir |
| Carte de la forêt → « À venir » | [ForestDetailScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/ForestDetailScreen.kt#L154), [ComingSoonScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/common/ComingSoonScreen.kt#L46) | Technicien → Forêt → gestion → détails → onglet MAP | oui | fonctionnalité spécifique à venir |
| Documents de la forêt → « À venir » | [ForestDetailScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/ForestDetailScreen.kt#L155), [ComingSoonScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/common/ComingSoonScreen.kt#L46) | Technicien → Forêt → gestion → détails → onglet DOCUMENTS | oui | fonctionnalité spécifique à venir |
| Historique de la forêt → « À venir » | [ForestDetailScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/ForestDetailScreen.kt#L156), [ComingSoonScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/common/ComingSoonScreen.kt#L46) | Technicien → Forêt → gestion → détails → onglet HISTORY | oui | fonctionnalité spécifique à venir |
| Vue générale avec nom générique Projet et « prochains sprints » | [ProjectDetailScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/projects/ProjectDetailScreen.kt#L97), [ProjectsNavGraph.kt](../../app/src/main/java/com/forestry/counter/presentation/navigation/ProjectsNavGraph.kt#L51) | Route project/{projectId} enregistrée ; ProjectsScreen ouvre le détail, mais aucun parcours standard actuel ne mène à Projects (JourneyNavigation.categories exclut PROJECTS). | indéterminée | écran provisoire latent |
| Gestion des forêts du projet | [ProjectDetailScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/projects/ProjectDetailScreen.kt#L74), [ProjectsNavGraph.kt](../../app/src/main/java/com/forestry/counter/presentation/navigation/ProjectsNavGraph.kt#L51) | Route project/{projectId} enregistrée ; ProjectsScreen ouvre le détail, mais aucun parcours standard actuel ne mène à Projects (JourneyNavigation.categories exclut PROJECTS). | indéterminée | fonctionnalité spécifique à venir latente |
| Documents du projet | [ProjectDetailScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/projects/ProjectDetailScreen.kt#L76), [ProjectsNavGraph.kt](../../app/src/main/java/com/forestry/counter/presentation/navigation/ProjectsNavGraph.kt#L51) | Route project/{projectId} enregistrée ; ProjectsScreen ouvre le détail, mais aucun parcours standard actuel ne mène à Projects (JourneyNavigation.categories exclut PROJECTS). | indéterminée | fonctionnalité spécifique à venir latente |
| Carte du projet | [ProjectDetailScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/projects/ProjectDetailScreen.kt#L77), [ProjectsNavGraph.kt](../../app/src/main/java/com/forestry/counter/presentation/navigation/ProjectsNavGraph.kt#L51) | Route project/{projectId} enregistrée ; ProjectsScreen ouvre le détail, mais aucun parcours standard actuel ne mène à Projects (JourneyNavigation.categories exclut PROJECTS). | indéterminée | fonctionnalité spécifique à venir latente |
| Équipe du projet | [ProjectDetailScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/projects/ProjectDetailScreen.kt#L78), [ProjectsNavGraph.kt](../../app/src/main/java/com/forestry/counter/presentation/navigation/ProjectsNavGraph.kt#L51) | Route project/{projectId} enregistrée ; ProjectsScreen ouvre le détail, mais aucun parcours standard actuel ne mène à Projects (JourneyNavigation.categories exclut PROJECTS). | indéterminée | fonctionnalité spécifique à venir latente |
| Historique du projet | [ProjectDetailScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/projects/ProjectDetailScreen.kt#L79), [ProjectsNavGraph.kt](../../app/src/main/java/com/forestry/counter/presentation/navigation/ProjectsNavGraph.kt#L51) | Route project/{projectId} enregistrée ; ProjectsScreen ouvre le détail, mais aucun parcours standard actuel ne mène à Projects (JourneyNavigation.categories exclut PROJECTS). | indéterminée | fonctionnalité spécifique à venir latente |
| Missions du projet | [ProjectDetailScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/projects/ProjectDetailScreen.kt#L75), [ProjectsNavGraph.kt](../../app/src/main/java/com/forestry/counter/presentation/navigation/ProjectsNavGraph.kt#L51) | Route project/{projectId} enregistrée ; ProjectsScreen ouvre le détail, mais aucun parcours standard actuel ne mène à Projects (JourneyNavigation.categories exclut PROJECTS). Onglet Missions filtré car accès absent. | indéterminée | fonctionnalité spécifique à venir latente |
| Missions masqué et remplacé par un refus de parcours | [BottomNavDestination.kt](../../app/src/main/java/com/forestry/counter/presentation/navigation/BottomNavDestination.kt#L56), [JourneyNavigation.kt](../../app/src/main/java/com/forestry/counter/presentation/navigation/JourneyNavigation.kt#L11) | Ancienne route restaurée missions → JourneyUnavailableScreen ; onglet omis par JourneyNavigation.tabs | non | fondation serveur non raccordée et accès bloqué |
| Visite guidée Carte : « couches IGN… à venir » | [strings.xml](../../app/src/main/res/values/strings.xml#L1139), [CoachMarkController.kt](../../app/src/main/java/com/forestry/counter/presentation/coachmark/CoachMarkController.kt#L28) | Après onboarding/permissions, coachmarks filtrés aux onglets visibles ; Carte conservé | oui | message à venir devenu partiellement obsolète |
| Visite Missions : « tâches de terrain (à venir) » | [strings.xml](../../app/src/main/res/values/strings.xml#L1137), [CoachMarkController.kt](../../app/src/main/java/com/forestry/counter/presentation/coachmark/CoachMarkController.kt#L27) | Étape déclarée mais filtrée parce que Missions ne figure pas parmi destinations | non | message dormant |
| Fond local absent + préparation Mapbox « en cours de développement » | [map_workspace_host.xml](../../app/src/main/res/values/map_workspace_host.xml#L15), [MapRechercheScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapRechercheScreen.kt#L490) | Carte commune → Fonds → choix non AVAILABLE et non CONFIGURATION_REQUIRED | oui | message d’indisponibilité conditionnelle avec mention de développement |
| Gestion distincte des désignations « prochaine étape » | [survey_preparation.xml](../../app/src/main/res/values/survey_preparation.xml#L12), [SurveyPreparationDialogs.kt](../../app/src/main/java/com/forestry/counter/presentation/components/SurveyPreparationDialogs.kt#L118) | Technicien → démarrer ou éditer le contexte d’un relevé → objectif COMBINED | oui | limite fonctionnelle explicite |
| Couche ONF « indisponible pour le moment » | [geographic_creation.xml](../../app/src/main/res/values/geographic_creation.xml#L21), [ParcelMapCreationScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/forestry/ParcelMapCreationScreen.kt#L36) | Créer parcelle par carte, profil ONF et sourceAvailable(ONF)==false | oui | source désactivée conditionnellement |
| Distribution serveur « non disponible pour cette version » | [strings.xml](../../app/src/main/res/values/strings.xml#L1701), [PackManagerScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/packs/PackManagerScreen.kt#L577) | Gestionnaire administratif de packs signé → pack non embarqué et distributionAvailable=false | oui | capacité externe non disponible pour la configuration/version |
| Compte : fournisseur « En préparation » / développeur « En développement » | [AccountScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/account/AccountScreen.kt#L742), [IdentityUiComponents.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/account/IdentityUiComponents.kt#L121) | Réglages → Compte ; options développeur → capacités fournisseur ; état serveur ProviderAvailability.DEVELOPMENT | oui | message de capacité conditionnelle |
| Google : « indisponible dans cette version » | [account_management.xml](../../app/src/main/res/values/account_management.xml#L32), [AccountScreen.kt](../../app/src/main/java/com/forestry/counter/presentation/screens/account/AccountScreen.kt#L699) | Réglages → Compte → liaison Google ; client ou capacité non disponible | oui | capacité externe/configuration |
| Politique embarquée : « À venir », purge GPS, effacement centralisé, procédures serveur en développement | [privacy_policy.md](../../app/src/main/res/raw/privacy_policy.md#L14), [privacy_policy.md](../../app/src/main/res/raw/privacy_policy.md#L261) | Réglages → confidentialité → politique ; SettingsNavGraph:321-327 | oui | documentation visible partiellement historique |
| carte_mode_libre_stub_desc | [strings.xml](../../app/src/main/res/values/strings.xml#L347) | Aucune référence R.string de production retrouvée pour cette clé | non | ressource de texte dormante |
| map_coming_soon | [strings.xml](../../app/src/main/res/values/strings.xml#L351) | Aucune référence R.string de production retrouvée pour cette clé | non | ressource de texte dormante |
| explorer_coming_soon | [strings.xml](../../app/src/main/res/values/strings.xml#L2360) | Aucune référence R.string de production retrouvée pour cette clé | non | ressource de texte dormante |
| Calcul personnalisé non implémenté | [AdvancedCalculationEngine.kt](../../app/src/main/java/com/forestry/counter/data/calculation/AdvancedCalculationEngine.kt#L720) | Branche calculateCustom du moteur ; aucun appel depuis la présentation ou assemblage applicatif retrouvé | indéterminée | branche de calcul non implémentée sans écran appelant établi |

« indéterminée » signifie qu’une décision de périmètre ou d’exposition reste à
confirmer ; aucune affectation à un nouveau métier n’est faite ici. Les ressources
non appelées ne sont pas des écrans actuellement affichés.

Les états vides, le volume non qualifié indisponible, le refus d’un accès
non autorisé et la réception GPS invalide sont des comportements métier
nécessaires. Ils ne sont pas reclassés comme écrans « À venir ».

Les cinq fonctions de support réelles sans identifiant de maquette certifié
(réceptions, fiche bois actuelle, dépôts, carte chantier et export) sont décrites
dans le JSON. Leur existence n’ajoute pas cinq maquettes ni ne remplace les
présentations retenues en attente.

## C. Totaux vérifiables

### Inventaire complet : 117 repères

| Métier | Codé et raccordé aux données | Codé sans données | Non codé | À rattacher | Total |
|---|---:|---:|---:|---:|---:|
| technicien | 10 | 1 | 1 | 0 | 12 |
| conducteur | 5 | 1 | 8 | 1 | 15 |
| commun | 72 | 1 | 17 | 0 | 90 |
| hors périmètre | 0 | 0 | 0 | 0 | 0 |
| **Total** | 87 | 3 | 26 | 1 | 117 |

### Références de travail : 107 repères

| Métier | Codé et raccordé aux données | Codé sans données | Non codé | À rattacher | Total |
|---|---:|---:|---:|---:|---:|
| technicien | 10 | 1 | 1 | 0 | 12 |
| conducteur | 5 | 1 | 8 | 1 | 15 |
| commun | 63 | 1 | 16 | 0 | 80 |
| hors périmètre | 0 | 0 | 0 | 0 | 0 |
| **Total** | 78 | 3 | 25 | 1 | 107 |

Le deuxième tableau enlève seulement les neuf repères remplacés et 11 non
finalisée. Il conserve 49 comme référence de travail non expressément validée
et les deux références documentaires de Synthèse. Ce filtre ne constitue
pas une nouvelle décision de validation. Les dix lignes exclues sont :

| Repère | Qualification | État fonctionnel actuel |
|---|---|---|
| cartographie/01 | remplacee_ou_ecartee | codé et raccordé aux données |
| cartographie/02 | remplacee_ou_ecartee | codé et raccordé aux données |
| cartographie/03 | remplacee_ou_ecartee | codé et raccordé aux données |
| cartographie/05 | remplacee_ou_ecartee | codé et raccordé aux données |
| cartographie/06 | remplacee_ou_ecartee | codé et raccordé aux données |
| cartographie/08 | remplacee_ou_ecartee | codé et raccordé aux données |
| cartographie/11 | non_finalisee | codé et raccordé aux données |
| cartographie/12 | remplacee_ou_ecartee | non codé |
| cartographie/16 | remplacee_ou_ecartee | codé et raccordé aux données |
| cartographie/17 | remplacee_ou_ecartee | codé et raccordé aux données |

Le total « hors périmètre » est zéro dans le catalogue retrouvé : les autres
métiers n’ont pas de maquette identifiée dans cet inventaire. Cela ne signifie
pas qu’aucun écran de ces métiers existe dans le dépôt. Un même fichier/panneau
peut correspondre à plusieurs lignes ; ces totaux ne comptent pas des fichiers
Kotlin indépendants et ne mesurent pas une conformité visuelle.

## D. Ordre proposé — Technicien d'abord

Les durées sont des **lots de travail bornés**, à confirmer à leur ouverture,
pas une date de livraison ni une promesse de finir un domaine en une journée.
Si le prérequis manque, la preuve de fin est un blocage documenté et un
redécoupage, jamais une interface factice. Conserver l'architecture Technicien
existante ; les changements convenus portent surtout sur Synthèse et Diagnostics.
Les écrans communs restent uniques et reçoivent le contexte métier autorisé.

| Lot | Durée | Périmètre borné | Preuve de fin attendue |
|---|---|---|---|
| T01 — Références et recette | 0,5 j | Récupérer les PNG prioritaires, vérifier leurs variantes et préparer les scénarios Technicien public/privé. | Manifeste image/variante/écran ; sources manquantes explicitement bloquantes pour une validation pixel ; scénario de captures prêt. |
| T02 — Parcours existant | 0,5 j | Revoir les entrées forêts/parcelles/relevés et les trois onglets forêt « À venir », sans refondre le parcours. | Captures de l'APK candidat, liste des écarts et décision sur les trois onglets ; aucune ancienne validation réattribuée. |
| T03 — Sources 56 | 0,5 j | Raccorder le composant déjà compilé aux sélections forêt/parcelle et aux liens officiels. | Clic d'entrée instrumenté ; deux surfaces distinctes et valeurs absentes correctement affichées ; reprise du bon contexte. |
| T04 — Synthèse Gestion | 1 j | Une puce d'explication par donnée et la fiche d'aide retenue ; conserver les calculs existants. | Tests des valeurs/source/unité et ouverture de chaque aide ; captures comparées aux deux références Synthèse. |
| T05 — Synthèse Bois/Écologie | 1 j | Réutiliser le même mécanisme d'aide ; contrôler les exports et l'indisponibilité d'un volume non qualifié. | Jeux de référence écran/export cohérents ; aucun coefficient ou volume de substitution ; captures et validation métier. |
| T06 — Diagnostics : entrée | 0,5 j | Menu, portée et retour au bon relevé ; identifier les maquettes encore à concevoir. | Parcours instrumenté de sélection/reprise ; maquettes manquantes listées, sans modifier les autres écrans Technicien. |
| T07 — Station et IBP | 1 j | Revoir deux sous-pages existantes : données manquantes, provenance, aides et reprise. | Tests ciblés des états incomplets/complets, captures et relecture métier des limites ; aucun score inventé. |
| T08 — Ripisylve | 1 j | Relier le formulaire actuellement temporaire au dépôt existant et à un vrai dossier. | Test de sauvegarde/relecture puis reprise après fermeture ; pas de nouveau schéma ou formule déduit sans analyse. |
| T09 — Placettes | 1 j | Revoir 57–61 : paramètres, grille, répartition et liste ; conserver le protocole réel. | Captures de ces cinq états et scénario création/édition/reprise ; géométrie et paramètres conservés. |
| T10 — Bordure et arbre | 0,5 j | Revoir 62–64 et les avertissements de bordure, sans déplacement silencieux de l'échantillon. | Scénarios intérieur/bordure et sélection de l'arbre ; surfaces et limites distinctes. |
| T11 — Guidage vers la tige | 1 j | Revoir 65–71 : position, compas, perte de réception et filtres. | Tests des signaux fournis et essai S25 documenté ; direction masquée si invalide, aucun faux fix présenté. |
| T12 — Mesures cartographiques | 1 j | Revoir 72–80 : tracer, enregistrer, reprendre, partager et unités. | Parcours natif avec données TEST, persistance/relecture et partage relisible ; captures des états réellement ouverts. |
| T13 — Hors connexion : préparation | 0,5 j | Revoir 22–26 : vue, région/départements et contenu préparé. | Emprise/zoom/sélections conservés ; distinction Free/forêt/parcelle/placette prouvée. |
| T14 — Hors connexion : transfert | 1 j | Revoir 27–40 sur une zone bornée : téléchargement, interruption, reprise et suppression. | Essai réel avec octets/tuiles présents, reprise après arrêt et respect de la portée ; aucune donnée de démonstration présentée comme téléchargée. |
| T15 — Couches | 1 j | Revoir 81–91 avec un fichier TEST d'un format déjà activé ; attributs, filtre et couleur. | Import Android réel, modification/relecture et capture ; autorité conservée ; formats non activés refusés explicitement. |
| T16 — Tracé | 1 j | Revoir 92–100 : début, réception dégradée, pause, portions, sauvegarde et détails. | Tracé réel en deux portions sans segment inventé dans l'interruption ; reprise/relecture et permissions S25 documentées. |
| T17 — Hauteur : protocole | 0,5 j | Retrouver la référence des quatre étapes et définir calibration, distance et cible sans changer les formules arbitrairement. | Maquette et protocole identifiés, conditions/mesures requises listées ; instrument de comparaison et critères décidés avant qualification. |
| T18 — Hauteur : parcours | 1 j | Raccorder/adapter le parcours retenu après T17 et contrôler à quelle tige la mesure est appliquée. | Tests des entrées invalides et de la bonne cible ; comparaison avec instrument de référence sur S25. Si cet instrument manque, qualification explicitement ouverte. |
| T19 — Journée terrain | 1 j | Recette du parcours Technicien candidat, avec une journée sans connexion et reprise. | Journal daté des scénarios et résultats, captures, versions et données TEST ; anomalies ouvertes ou corrigées puis vérifiées. |

Le mode Maps étant commun aux deux métiers, son absence ne doit pas être masquée
par le guidage existant vers une tige. Après les parcours terrain prioritaires,
le traiter en tranches réutilisant le même moteur de carte :

| Lot commun | Durée | Périmètre | Preuve de fin attendue |
|---|---|---|---|
| M01 — Contrats Maps | 0,5 j | Confirmer les références 49 et les prérequis de données, recherche, itinéraire, accès et radars. | Sources/providers/limites documentés ; inconnues conservées, aucune praticabilité ou autorisation inventée. |
| M02 — Recherche 41 | 0,5 j | Raccorder la recherche réelle au parcours Maps, avec erreurs et retour. | Entrée Maps→recherche→sélection avec identifiant réel ; code commun conservé. |
| M03 — Itinéraire 42–45 | 1 j | Raccorder une demande/réponse et ses choix qualifiés au dépôt prévu. | Contrat testé, carte réelle et impossibilité de calcul explicite ; aucune route de démonstration substituée. |
| M04 — Étapes 46–48 | 1 j | Lier aperçu, sélection d'étape et caméra. | Sélection et géométrie cohérentes sur un itinéraire réel ; captures des états. |
| M05 — Guidage 49–53 | 1 j | Premier trajet borné avec arrêt, réception perdue/retrouvée et arrivée. | Tests des états et essai S25 du trajet ; redécouper avant ouverture si le fournisseur ou le guidage réel manque. |
| M06 — Accès 14–15 | 1 j | Fiche des trois faits séparés, constat, attribution au carnet et sauvegarde. | Sources/dates/valeurs inconnues conservées ; constat réellement relu dans le bon dossier et test des droits. |

Reprendre ensuite les présentations Conducteur en réutilisant leurs modèles et
dépôts opérationnels. Ces propositions de lots restent soumises aux mêmes
prérequis ; elles ne valident aucun nouveau contrat métier ni aucune quantité.

| Lot Conducteur | Durée | Périmètre borné | Preuve de fin attendue |
|---|---|---|---|
| C01 — Dossier et bois associés | 0,5 j | Comparer le dossier02 et rattacher la page associée à sa référence exacte. | Identifiants réels et retour au dossier vérifiés ; décision sur la correspondance encore incertaine, sans réécriture de sa logique. |
| C02 — Contrats manquants | 1 j | Examiner stock daté et bases figées de contrôle avant d'activer les présentations en attente. | Contrats et champs réels identifiés, tests des absences ; questions métier non tranchées consignées, aucun stock inventé. |
| C03 — Carnet et détail | 1 j | Raccorder les présentations de liste/détail aux entrées et photos existantes après C02. | Navigation, filtres, dates observée/enregistrée et reprise avec les mêmes identifiants ; captures. |
| C04 — Formulaires | 1 j | Raccorder les présentations Note/Activité/Accès à l'autosauvegarde existante. | Reprise de chaque brouillon, photo liée à son brouillon d'origine et enregistrement dans le bon dossier. |
| C05 — Correction et historique | 0,5 j | Ouvrir les présentations retenues depuis une entrée, sans écraser son original. | Historique consultable et correction motivée ; ordre stable, dates et original conservés. |
| C06 — Fiche du bois | 1 j | Raccorder la fiche01 puis son état stock renseigné, une fois C02 qualifié. | Relevé de référence réel, mesures/protocole et état daté cohérents ; absence de stock affichée sans substitution ; captures. |
| C07 — Quantités | 1 j | Revoir les composants déjà actifs Stock initial/Enlever/Contrôler. | Cas d'unités/conventions incompatibles, sorties futures et correction ; contrôle distinct d'un ajustement de stock ; aucune quantité commerciale payable déduite automatiquement. |
| C08 — Recette Conducteur | 1 j | Parcours candidat chantier→carnet→bois→mouvements→export et reprise. | Scénarios datés sur données TEST et appareil, captures, anomalies suivies ; modes hors connexion et droits explicitement vérifiés. |

Aucun autre métier n'est ouvert par ce planning. Les durées devront être
réévaluées si les images, contrats ou moyens de vérification manquent.

## E. Doublons, contradictions et décisions à conserver ouvertes

1. **Deux numérotations différentes.** Les propositions Conducteur/Synthèse ne
   sont pas les numéros du lot cartographique. Le registre des variantes contient
   34 occurrences (33 écartées, une non finalisée), dont dix occupent déjà des
   repères 1–100 : ce n'est pas un total à additionner aux 117.
2. **Choix successifs.** Dossier chantier 03 a été annulé au profit de 02 ; la
   fiche bois revient à 01 ; 51B remplace 51, 53B remplace l'ancien pictogramme,
   72 revient à la version de base et 90B ajoute la couleur. Les variantes
   remplacées ne sont pas des écrans supplémentaires à développer.
3. **11 reste ouvert.** La mesure et le panneau Outils existent ; le Fondateur
   a explicitement différé la liste complète des outils. Ce besoin reste ouvert,
   même si la ligne technique est raccordée.
4. **49 n'est pas une validation expresse.** Le titre et le numéro sont établis,
   mais la réponse retrouvée est « Continue ». Confirmer la référence de travail
   avant une recette graphique définitive ; ne pas transformer cette poursuite
   en une nouvelle décision.
5. **Bois associés reste à rattacher.** La page `associatedWood` est accessible,
   alimentée et testée isolément ; la correspondance avec la proposition retenue
   doit être vérifiée. Elle reste hors des trois sous-totaux d'état, sans effacer
   la fonction existante.
6. **Suivi ancien corrigé par les sources.** 56 était annoncée raccordée alors
   qu'aucun appel n'est présent. 64 et 90 étaient dites incertaines : leur identité
   documentaire est maintenant retrouvée. Cela ne prouve toujours pas les pixels.
   14 n'est pas le sélecteur de carnet : aucun écran compilé de la fiche
   « Dernier chemin » avec véhicule/droit de passage/état du chemin n'est trouvé.
   41 possède bien une recherche IGN en carte commune, sans parcours Maps cible.
7. **Présentations retenues et fonctions anciennes.** Les sources carnet et
   fiche bois sous `pending-integration` sont hors compilation ; les données et
   formulaires génériques restent actifs. Les deux faits sont conservés au lieu
   de déclarer soit « tout livré », soit « aucune fonction ».
8. **Identité documentaire et métier.** Les deux références Synthèse restent
   attestées par les documents retrouvés, sans événement initial local récupéré.
   Les prompts cartographiques ne nomment souvent aucun métier : la colonne
   métier classe leur usage prévu et leur réutilisation, sans inventer une
   validation d'un profil. Les projets et le calcul personnalisé latents restent
   à situer ; ils ne sont pas activés ni attribués à un futur métier par cet audit.
9. **Hauteur et photos originales.** Le choix de quatre étapes et les outils
   existants sont connus ; une image source numérotée de ce parcours n'a pas été
   retrouvée. Ce besoin est signalé hors du décompte 117, sans création d'une
   fausse maquette. Les originaux des autres références doivent aussi être
   récupérés avant toute déclaration de conformité au pixel.

### Registre complet des variantes remplacées ou non finalisées

Ces 34 occurrences ne sont pas un sous-total à additionner aux 117. Dix y
figurent déjà ; les 24 autres sont des variantes supplémentaires conservées
pour la traçabilité, pas des interfaces de lancement à recréer. Les sources
et décisions de chaque occurrence sont conservées dans le JSON.

| Identifiant | Titre | Qualification historique | Déjà dans les 117 |
|---|---|---|---|
| conducteur/dossier-chantier/proposition-01 | Dossier chantier | remplacee_ou_ecartee | non |
| conducteur/dossier-chantier/proposition-03 | Dossier chantier — photographie plus présente | remplacee_ou_ecartee | non |
| conducteur/dossier-chantier/proposition-04 | Dossier chantier — photographie discrète | remplacee_ou_ecartee | non |
| conducteur/formulaire-note/proposition-direction-b | Nouvelle entrée — Atelier sauge | remplacee_ou_ecartee | non |
| conducteur/fiche-bois/proposition-02 | Fiche du bois — mesure agrandie | remplacee_ou_ecartee | non |
| conducteur/fiche-bois/proposition-03 | Fiche du bois — mesure modérée | remplacee_ou_ecartee | non |
| conducteur/releve-pile/proposition-01 | Nouveau relevé manuel | remplacee_ou_ecartee | non |
| conducteur/stock-initial/proposition-01-initiale | Stock initial — aide redondante | remplacee_ou_ecartee | non |
| conducteur/stock-renseigne/proposition-01 | Fiche du bois — stock renseigné | remplacee_ou_ecartee | non |
| conducteur/stock-renseigne/proposition-02 | Fiche du bois — stock renseigné, version épurée | remplacee_ou_ecartee | non |
| cartographie/01 | Carte commune — première proposition | remplacee_ou_ecartee | oui |
| cartographie/02 | Accueil Carte — trois thèmes | remplacee_ou_ecartee | oui |
| cartographie/03 | Terrain forestier — première présentation | remplacee_ou_ecartee | oui |
| cartographie/05 | Couches — première présentation | remplacee_ou_ecartee | oui |
| cartographie/06 | Couches — catalogue | remplacee_ou_ecartee | oui |
| cartographie/08 | Fond — trois fonds | remplacee_ou_ecartee | oui |
| cartographie/09-initiale | Fond — catalogue initial | remplacee_ou_ecartee | non |
| cartographie/11 | Mesurer — outil non finalisé | non_finalisee | oui |
| cartographie/12 | Accès et itinéraires | remplacee_ou_ecartee | oui |
| cartographie/16 | Atlas et couches | remplacee_ou_ecartee | oui |
| cartographie/17 | Carte libre — satellite dégagé | remplacee_ou_ecartee | oui |
| cartographie/18A | Carte libre — atelier cartographique clair | remplacee_ou_ecartee | non |
| cartographie/22 | Préparer une zone hors connexion | remplacee_ou_ecartee | non |
| cartographie/23 | Packs par département | remplacee_ou_ecartee | non |
| cartographie/29/revision-initiale | En attente de Wi-Fi | remplacee_ou_ecartee | non |
| cartographie/41 | Rechercher une destination | remplacee_ou_ecartee | non |
| cartographie/41B | Rechercher une destination — style Maps | remplacee_ou_ecartee | non |
| cartographie/42 | Aperçu du lieu | remplacee_ou_ecartee | non |
| cartographie/50/revision-initiale | Guidage actif | remplacee_ou_ecartee | non |
| cartographie/51 | Position perdue | remplacee_ou_ecartee | non |
| cartographie/53 | Arrivée au point d’accès | remplacee_ou_ecartee | non |
| cartographie/72B | Mesurer une distance — style professionnel | remplacee_ou_ecartee | non |
| cartographie/72C | Mesurer une distance — icônes affinées | remplacee_ou_ecartee | non |
| cartographie/90 | Apparence | remplacee_ou_ecartee | non |

## Validation de ce lot documentaire

Contrôles réalisés : rapprochement des identifiants et sources, parcours/callers,
sources de données, identité des sources/tests avec la preuve historique,
unicité des lignes, calcul des totaux depuis le CSV/JSON, existence des chemins
et lignes cités, vérification des liens et contrôle du diff documentaire.
Les tests d'UI creux tels que `MartelageScreenTest` (contenus commentés) ne sont
pas utilisés comme preuve d'écran fonctionnel.

Aucun build, nouveau test JVM/instrumenté, lancement Android ou essai S25 n'est
réalisé pour ce lot de documentation. Le registre est un décompte sourcé, pas
une qualification de production ni une validation graphique des maquettes.
