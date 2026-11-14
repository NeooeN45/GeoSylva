# Moteur cartographique Mapbox Géosylva

Bibliothèque commune de rendu utilisée par l’application `:app` et l’hôte de
recette `:cartography-preview`, Maps/Compose **11.34.1 NDK27**. Le raccord du
5 novembre remplace les hôtes MapLibre de Terrain/Libre, itinéraires, sélection
géographique et packs administratifs. Les composants Compose partagés résident
dans `:cartography-ui`, indépendant du SDK et des repositories.

`SharedMapboxCanvas` reçoit caméra, commandes identifiées, style, relief et un
`MapOverlayScene` déjà autorisé. Le moteur ne choisit pas de dossier, n’interroge
aucun DAO, ne demande pas de position et n’enregistre aucune mesure. Sans jeton
public configuré hors Git, aucune MapView n’est créée.

Les scènes portent des identités opaques, géométries GeoJSON, couleurs, contours,
labels et sélection. Les lignes multiparties gardent leurs ruptures. Les clics
asynchrones sont invalidés après changement de scène ou de génération ; la voie
`onCoordinateTap` permet aux outils de consommer immédiatement une coordonnée.
Le cache des labels locaux conserve les images actives et au plus 64 inutilisées.
Une source modifiée ne nécessite pas de recréer toutes les couches. Un échec
masque les couches concernées et remonte un état typé sans URL ou jeton brut.

Le cadrage distingue caméra observée, commande et emprise. Position, orientation,
inclinaison et marges natives sont conservées ensemble avec
`MapCameraPositionSaver`, y compris après rotation ou remplacement du style.
Un acquittement de commande signifie qu’elle est soumise, pas que les pixels
sont déjà affichés. `MapOverlayStatus.Applied` confirme l’acceptation native des
sources/couches ; la capture et la recette visuelle restent distinctes.

Le relief DEM est indépendant de l’inclinaison et son état est relu sur le SDK.
`Ready` exige chargement natif, relief concordant et bornes acceptées. Le pitch
est borné de 0° à 60° et l’exagération de 1× à 3× ; aucun ne mesure une pente
physique. Les styles JSON locaux et les styles distants partagent le même moteur.

Les hôtes conservent leurs données locales et affichent un mode vectoriel local
explicite hors réseau. Cela ne prétend pas avoir téléchargé le satellite, un
pack Mapbox ou les ressources d’une journée de terrain. Les fichiers raster
historiques restent conservés ; les nouveaux packs Mapbox restent à qualifier.

Les contrôles et limites du lot sont suivis dans
[la bascule applicative](../docs/BASCULE_MAPBOX_2026-11-05.md).
Les rapports du 3 novembre restent des preuves historiques du premier socle,
pas la validation du code actuel ou du Samsung S25 Ultra.
