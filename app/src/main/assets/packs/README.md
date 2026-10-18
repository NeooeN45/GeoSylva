# Contours administratifs pour la sélection des packs

Les deux couches de contours servent à choisir des régions et départements sur
la carte de préparation. Deux couches de points calculés portent leurs noms à
l'écran. Ces couches ne contiennent ni parcelles cadastrales, ni limites de
forêts, ni données métier des packs téléchargeables.

## Source et attribution

Source : **IGN, ADMIN EXPRESS COG CARTO PE, édition 2026**, distributions WFS
`ADMINEXPRESS-COG-CARTO-PE.2026:region` et
`ADMINEXPRESS-COG-CARTO-PE.2026:departement`, récupérées le **2026-10-01**.
L'édition annuelle explicite est utilisée à la place d'un alias `LATEST`.
Les réponses GeoServer portent respectivement les horodatages
`2026-10-01T20:59:20.442Z` et `2026-10-01T20:59:20.612Z` ; ils décrivent la réponse
du service, pas la date de levé de chaque frontière.

Attribution à afficher : **© IGN · ADMIN EXPRESS COG CARTO PE 2026 · Licence Ouverte 2.0**.

La licence `lov2` est vérifiée dans le
[catalogue officiel data.gouv.fr](https://www.data.gouv.fr/api/1/datasets/5808de39c751df1e0679df72/),
les métadonnées légales IGN et la
[page officielle des licences](https://www.data.gouv.fr/licences).
L'URL du PDF Etalab demeure refusée par le proxy réseau (`CONNECT 403`) ; aucune
désactivation de TLS ou méthode de contournement n'a été utilisée.
Les sources HTTPS utilisées ont répondu `200`, avec vérification TLS réussie.

## Couverture et contenu

| Fichier | Entités | Couverture | Taille en octets |
|---|---|---|---|
| `regions.geojson` | 18 régions | 13 métropolitaines et 5 ultramarines | 770475 |
| `departments.geojson` | 101 départements | 96 métropolitains et 971, 972, 973, 974, 976 | 1765730 |
| `regions-labels.geojson` | 18 points calculés | Une ancre par région | 3820 |
| `departments-labels.geojson` | 101 points calculés | Une ancre par département | 21546 |

Le produit **CARTO PE** est une généralisation cartographique officielle de
l'IGN adaptée à la petite échelle. Aucune simplification supplémentaire,
aucun arrondi et aucune reconstruction des contours ne sont appliqués ici.
Ces limites d'affichage ne doivent pas servir à mesurer une surface cadastrale.
La présence d'un contour administratif ne prouve pas qu'un pack métier existe,
qu'il est installé, ni qu'il couvre le territoire en question.

Les géométries sont conservées exactement : `MultiPolygon`, îles et trous.
Les coordonnées sont `[longitude, latitude]` en WGS84, demandées en `OGC:CRS84`.
Les contrôles géographiques incluent Paris, Ajaccio et les cinq territoires
ultramarins pour détecter notamment une permutation latitude/longitude.

## Propriétés normalisées

| Propriété | Source région | Source département |
|---|---|---|
| `territory_id` | `region:` + `code_insee` | `department:` + `code_insee` |
| `name` | `nom_officiel` | `nom_officiel` |
| `code` | `code_insee` | `code_insee` |
| `region_code` | `code_insee` | `code_insee_de_la_region` |

Les codes restent des chaînes, y compris `01`, `2A` et `2B`.
L'identifiant GeoJSON `id` est égal à `territory_id` et les entités sont triées
par code. Les propriétés inutilisées et les métadonnées de transport GeoServer
sont retirées ; les géométries ne sont pas modifiées.

## Ancres d'étiquettes calculées

Les fichiers `regions-labels.geojson` et `departments-labels.geojson` fournissent
**un seul `Point` par territoire**, avec les mêmes `id`, `territory_id`, noms,
codes et références régionales que les contours. Une source de points distincte
pour les étiquettes évite de répéter le même nom sur les îles d'un `MultiPolygon`.

Pour chaque territoire, le plus grand `Polygon` est choisi selon son **aire
géodésique sur l'ellipsoïde WGS84**, calculée avec `pyproj.Geod`. L'aire absolue
de l'anneau extérieur est diminuée des aires absolues des trous ; le classement
n'utilise pas des degrés carrés. Si deux composants ont exactement la même aire,
le premier dans la géométrie source est conservé.

Shapely calcule ensuite le `representative_point` de ce composant dans ses
coordonnées longitude/latitude. Le résultat est contrôlé à l'intérieur du
polygone choisi et de la géométrie administrative source. Aucune coordonnée
n'est saisie à la main et aucun arrondi n'est appliqué.

Ces points sont des **ancres de rendu dérivées par GeoSylva**, pas des données
de terrain distribuées par l'IGN, des centroïdes ou des mesures de précision.
Les contours IGN restent strictement inchangés, avec les mêmes empreintes.
La provenance précise les versions de Shapely, GEOS, pyproj et PROJ, la méthode,
le composant choisi et les contrôles réalisés.

## Traçabilité et contrôles

[provenance.json](provenance.json) contient les URL exactes, dates, tailles,
SHA-256 des réponses sources et fichiers embarqués, licence, couverture et
résultats de qualification. Les téléchargements bruts ne sont pas embarqués.

Contrôles réalisés avec Shapely : codes uniques et couverture complète attendue,
références département–région existantes, coordonnées finies dans les bornes,
anneaux fermés, géométries non vides et valides, conservation exacte des
géométries sources et des trous, points témoins situés dans les territoires
attendus. Le point intérieur représentatif de chaque département se trouve
dans sa région déclarée ; cela ne prétend pas démontrer une identité géométrique
des frontières indépendamment généralisées.

| Fichier | SHA-256 |
|---|---|
| `regions.geojson` | `813d23920f9dfbd06af5b71d9e610b6a97a0b9f0380788008bc2d24b180472bd` |
| `departments.geojson` | `3f25bf4c7b21d62b49ed6368b50f25ddbc6aa04fb4f62220f12d48e988860687` |
| `regions-labels.geojson` | `207cc8fc9781325145f157cb04f23add2e887b3a5f14e8d6551091c6e34d4333` |
| `departments-labels.geojson` | `109a9c4a409d918d5f5faa8c23bd873d94957665276e789087524cfac0e238f2` |

Les URL `source_url` de la provenance reproduisent les distributions utilisées.
Une édition annuelle servie à nouveau peut évoluer : contrôler ses empreintes
avant tout remplacement et conserver l'attribution de la nouvelle acquisition.
