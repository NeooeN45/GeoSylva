# Socle cartographique Mapbox Géosylva

Bibliothèque commune de rendu et d’interface, indépendante des métiers et de
leurs repositories. Premier lot du 3 novembre 2026, Maps/Compose **11.34.1 NDK27**,
AGP/Kotlin/Compose existants conservés.

- `mapbox/SharedMapboxCanvas` : une surface Compose, caméra observée, commandes
  identifiées, styles, DEM et ornements natifs hors des panneaux.
- `ui/MapWorkspace` : capsule de contexte, actions et panneau unique
  Fond/Couches/Relief. État et callbacks fournis par le host.
- `:cartography-preview` : application de recette séparée ; **`:app` ne dépend
  pas encore de cette bibliothèque**.

Le host fournit le jeton public et les seuls objets dont l’accès est autorisé.
Le moteur ne choisit pas un dossier, n’interroge aucun DAO et ne demande pas le
GPS. Sans jeton public, aucune MapView n’est créée. Le jeton n’est jamais inclus
dans un message d’erreur ou un journal applicatif.

La caméra initiale ne commande pas les recompositions suivantes. Un déplacement
explicite porte un ID unique et les gestes sont observés depuis le SDK. Les
sessions de style sont isolées ; leurs callbacks obsolètes sont ignorés et la
caméra observée est conservée entre sessions. Le DEM et l’exagération sont
indépendants de l’inclinaison. `onReliefChanged` confirme les propriétés relues
sur le style natif, pas la simple intention de l’interface. `Ready` exige
chargement SDK, relief concordant et bornes de caméra acceptées.

Une entrée de catalogue n’est ni une source raccordée, ni une région hors ligne
prête. La qualification des couches, le stockage terrain et les droits restent
dans les services Géosylva. Les vignettes sont injectables ; la bibliothèque ne
présente pas une image illustrative comme une tuile téléchargée.

Le host conserve le DEM activé en vue verticale pour permettre le passage en
perspective par geste. Le bouton 2D ramène le pitch à zéro sans couper le relief.
Le pitch est borné de 0° à 60° ; l’exagération va de 1× à 3× et ne mesure aucune
pente physique.

Avant raccord à `:app`, traiter la collision GeoJSON et la parité des usages :
[audit de raccordement](../docs/AUDIT_RACCORDEMENT_TERRAIN_MAPBOX_2026-11-03.md).
Le rendu Mapbox réel, les courses de chargement SDK, les régions hors ligne et
le Samsung S25 Ultra nécessitent une recette avec le compte Géosylva configuré.
