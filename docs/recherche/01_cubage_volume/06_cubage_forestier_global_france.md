# Méthodes de cubage forestier : panorama mondial et application à la France

**Domaine** : `docs/recherche/01_cubage_volume/`  
**Identifiant** : GEOSYLVA-RECH-CUBAGE-006  
**Date de recherche** : 2026-08-30  
**Statut** : Draft  
**Agent** : Consortium recherche cubage forestier  
**Public** : développeur et professionnel forestier

## 1. Objet et avertissement

Le mot « cubage » recouvre des méthodes de mesure directe et des méthodes d'estimation. Une méthode peut être exacte pour une forme géométrique théorique et néanmoins produire un biais sur un arbre réel. La validité dépend toujours du protocole de mesure, de la population de calibration et de la définition du volume.

Les normes AFNOR sont citées pour orienter la qualification ; le texte intégral et l'édition en vigueur doivent être consultés avant toute revendication de conformité.

## 2. Taxonomie mondiale

| Famille | Entrées | Exemple | Usage |
|---|---|---|---|
| Géométrique simple | diamètre/circonférence, longueur | cylindre, cône | estimation grossière ou segment |
| Sections successives | plusieurs diamètres | Smalian, Huber, Newton, Simpson | grumes et tiges abattues |
| Tarif à une entrée | D ou C | Schaeffer 1E, IFN rapide | peuplement homogène |
| Tarif à deux entrées | D/C + H | Schaeffer 2E, IFN lent | hauteur disponible, hétérogénéité réduite |
| Équation allométrique | D + H + essence | Schumacher-Hall, puissance | arbre/peuplement calibré |
| Profil de tige | D, H, décroissance, découpe | Chaudé, Lapasse, profil polynomial | volume commercial par découpe |
| Biomasse | volume + densité + facteurs | équations nationales, IPCC | carbone et énergie |
| Télédétection | nuage de points/images | ALS, TLS, ULS, SfM, QSM | inventaire spatial ou structure 3D |
| Apprentissage automatique | données terrain + capteurs | k-NN, gradient boosting, réseaux de points | estimation calibrée et incertitude |

## 3. Arbre sur pied

### 3.1 Cylindre corrigé

```text
G = π / 4 × (D_cm / 100)²
V = G × H × f
```

Le coefficient `f` corrige la décroissance du tronc et doit être interprété comme un paramètre de domaine. Les valeurs « typiques » ne sont pas des constantes réglementaires.

### 3.2 Tarifs à une entrée

Un tarif à une entrée donne un volume moyen conditionnel à une seule mesure. Il est donc attaché à une population : essence ou groupe, station, type de peuplement, période, découpe et convention d'écorce. L'IGN rappelle qu'un tarif possède un domaine de validité défini par l'échantillon qui l'a construit.

Conséquence pour GeoSylva : le choix d'un numéro Schaeffer/IFN doit pouvoir être remplacé par l'utilisateur et accompagné d'une justification ou d'un échantillon de calibration. Un mapping fixe essence → numéro ne doit pas être présenté comme une loi universelle.

### 3.3 Tarifs à deux entrées et équations par essence

Les formes courantes sont :

```text
V = a + b × C² × H
V = a × D^b × H^c
V = exp(a + b × ln(D) + c × ln(H))
```

La formule de Schumacher-Hall est souvent utilisée sous forme logarithmique pour ajuster le volume. La transformation et le biais de retransformation doivent être documentés si elle est implémentée.

### 3.4 Hauteur de découpe et profil commercial

Le volume vendu n'est pas nécessairement le volume de la tige entière. La hauteur de découpe, le diamètre fin bout, les défauts et la longueur minimale de produit déterminent le volume commercial. Le projet EMERGE a précisément étudié des volumes cohérents à différentes découpes et la hauteur de décrochement.

## 4. Grumes et sections

Soit `L` la longueur, `D_b` le diamètre au gros bout, `D_m` le diamètre médian et `D_s` le diamètre au fin bout, tous en mètres :

```text
A_x = π × D_x² / 4
Huber   = L × A_m
Smalian = L × (A_b + A_s) / 2
Newton  = L × (A_b + 4A_m + A_s) / 6
Tronc de cône = π × L × (D_b² + D_bD_s + D_s²) / 12
```

| Méthode | Point fort | Risque |
|---|---|---|
| Huber | Une section médiane ; efficace pour beaucoup de billons | biais si la forme réelle s'écarte du paraboloïde |
| Smalian | Mesures aux deux bouts ; simple sur chantier | tendance à surestimer un cône |
| Newton | trois sections ; exacte pour plusieurs formes classiques | plus de mesures et protocole plus strict |
| Sections multiples | réduit l'erreur par intégration du profil | temps, coût et besoin d'instruments |

Les bouts ovales, méplats, écorces irrégulières et défauts doivent être traités selon la norme ou le contrat. Le calcul d'un diamètre circulaire à partir d'une seule direction n'est pas neutre.

## 5. Bois empilé et énergie

`m3_apparent = longueur_de_pile × largeur × hauteur` décrit l'encombrement, pas le bois plein. Le rapport entre m³ apparent et m³ réel dépend de la longueur des bûches, du diamètre, de la courbure, de la fente et de la qualité d'empilage.

Le CNPF rappelle des plages indicatives : un m³ apparent de bûches de 1 m contient généralement environ 0,6 à 0,8 m³ réel ; ces plages ne doivent pas être remplacées par un coefficient unique sans préciser le protocole. Pour une commande, la convention du vendeur et le contrat priment.

Pour les plaquettes et combustibles, la masse, le taux d'humidité et le pouvoir calorifique inférieur doivent être séparés. Un prix ADEME en €/MWh PCI ne peut pas devenir un prix €/stère sans une conversion documentée.

## 6. Biomasse et carbone

La chaîne de calcul est conceptuellement :

```text
volume du compartiment
  → densité ou infradensité
  → biomasse sèche
  → facteurs d'expansion branches/racines
  → fraction de carbone
```

Les facteurs peuvent varier selon essence, âge, compartiment, état hydrique et convention de volume. Le résultat est une estimation avec incertitude cumulée, pas une mesure contractuelle de bois d'œuvre.

## 7. Télédétection et IA

| Technologie | Mesure possible | Limites à afficher |
|---|---|---|
| ALS LiDAR aérien | hauteur de couvert, structure, volume de peuplement | résolution, pénétration, calibration terrain |
| TLS/MLS/ULS | profil du tronc, DHP/DHB, branches, volume 3D | occlusions, coût, couverture limitée |
| Photogrammétrie/SfM | géométrie 3D à partir d'images | texture, lumière, recouvrement et échelle |
| QSM | assemblage de cylindres ou segments | dépendance au nuage, branches et occlusions |
| Régression ML | volume/biomasse à partir de variables | biais de domaine, nécessité d'un jeu de test indépendant |
| Segmentation IA | arbre individuel, couronne, défaut | aucune précision commerciale sans validation spécifique |

Les modèles LiDAR/IA doivent fournir au minimum une métrique d'évaluation par population, une unité, un intervalle d'incertitude et un indicateur de hors-domaine. Les performances d'un article ne sont pas transférables automatiquement à un smartphone ou à une forêt française.

## 8. Adaptation française

| Besoin français | Références à privilégier |
|---|---|
| Inventaire national | IGN/IFN, DataIFN, méthodologie IGN |
| Estimation sur pied | CNPF, barèmes Chaudé, tarifs calibrés localement |
| Vente de grumes | NF B53-020, NF EN 1309-2 et contrat |
| Classement des singularités | NF EN 1310 et normes de classement d'essence |
| Chêne/hêtre | NF EN 1316 et sources FNB/ONF |
| Sapin/épicéa/pins | NF EN 1927 et règles de classement applicables |
| Bois énergie | ADEME, CIBE/CEEB, humidité et PCI |
| Volume/biomasse | EMERGE, Vallet et al., IGN/INRAE, avec domaine de validité |

Le CNPF identifie aussi le tarif Chaudé comme un barème utilisé en forêt privée et la formule du cylindre médian pour les grumes. Le pin maritime possède des pratiques de cubage et de découpe propres au bassin de production ; il ne faut pas les généraliser aux autres essences.

## 9. Matrice d'adaptation française

Le tableau suivant indique une **méthode candidate**, pas une prescription universelle. Le département ne suffit pas à choisir un tarif : la station, la sylviculture, la structure, la découpe et l'échantillon de calibration sont déterminants.

| Essence/groupe | Bassin ou situation | Méthode candidate | Mesures supplémentaires | Réserve |
|---|---|---|---|---|
| Chêne sessile/pédonculé | futaie régulière feuillue | Chaudé ou tarif 1E calibré ; 2E si hauteur disponible | D/C, H de découpe, échantillon de billes | distinguer roulure, qualité et découpe |
| Hêtre | futaie régulière ou mélangée | tarif 2E ou équation par essence | D, H, structure, qualité | cœur rouge et destination peuvent dominer la valeur |
| Douglas | plantations du Massif central, Morvan, Ouest | équation D-H ou tarif local validé | hauteur, longueur de fût, diamètre fin bout | ne pas transposer un coefficient d'un bassin à l'autre |
| Pin maritime | Landes de Gascogne | barème local, Chaudé ou Lapasse selon protocole | circonférence, hauteur de découpe, conicité | découpe marchande et qualité très sensibles |
| Épicéa/sapin | massif montagnard | tarif IFN/2E ou modèle local | H, défilement, dégâts sanitaires | distinguer arbre sain, scolyte et bois déclassé |
| Pin sylvestre/laricio/noir | montagne ou station sèche | tarif 2E/IFN ou calibration locale | D, H, forme, station | groupes commerciaux non interchangeables |
| Peuplier cultivé | peupleraie homogène | 1E si cultivar/station homogènes, sinon 2E | cultivar, âge, hauteur de découpe | la bille de déroulage et le diamètre fin bout sont critiques |
| Châtaignier/robinier | taillis ou futaie de plaine | 2E ou cubage par billons | D, H, roulure/durabilité | le rendement produit n'est pas déduit du seul volume |
| Mélèze/cèdre/autres résineux | plantations ou peuplements localisés | équation documentée ou tarif de groupe | D, H, essence certaine | fallback interdit pour une vente précise |
| Mélange d'essences | toutes régions | calcul par essence et par strate | inventaire séparé, surface, hauteur | ne pas appliquer une moyenne unique au mélange |

### 9.1 Décision de méthode

```text
si grume accessible : cubage direct selon protocole contractuel
sinon si hauteur + essence + domaine validés : tarif/équation à 2 entrées
sinon si peuplement homogène et calibration disponible : tarif à 1 entrée
sinon : estimation exploratoire avec incertitude élevée et avertissement
```

Aucune correspondance « département → numéro de tarif » n'est suffisamment universelle pour être codée sans calibration. La donnée locale doit provenir d'arbres témoins, de DataIFN ou d'une publication explicitement applicable.

## 10. Règles de sélection dans GeoSylva

1. Contrôler les unités et la plage de D/H avant calcul.
2. Déterminer si le peuplement est homogène, mélangé ou irrégulier.
3. Préférer un tarif 1E seulement si la population est compatible et la hauteur non disponible.
4. Préférer D + H, voire hauteur de découpe, lorsque la forme varie fortement.
5. Utiliser le cubage direct de grumes quand le bois est accessible.
6. Exiger `source`, `method_version`, `volume_kind`, `bark_condition` et `uncertainty` dans le résultat.
7. Refuser une conversion commerciale si la convention du prix n'est pas comparable au volume.
8. Afficher « estimation non contractuelle » hors protocole de vente validé.

## 11. Sources

- IGN, méthodologie IFN 2022 : https://inventaire-forestier.ign.fr/IMG/pdf/methodologie-2022.pdf
- DataIFN : https://inventaire-forestier.ign.fr/dataifn/
- CNPF, vente de bois : https://nouvelle-aquitaine.cnpf.fr/gestion-durable-des-forets/coupes-et-travaux/la-vente-de-bois
- CNPF/IFC, volume sur pied : https://ifc.cnpf.fr/sites/socle/files/cnpf-old/498015_volume_pied_1.pdf
- CNPF/IFC, estimer et vendre : https://ifc.cnpf.fr/sites/ifc/files/2024-03/Fiche%20Gestion%2021%20-%20Estimer%20et%20Vendre%20ses%20Bois.pdf
- Deleuze et al., EMERGE : https://hal.science/hal-03016051
- Chevrou, tarif de cubage : https://hal.science/hal-03424895/document
- Diéguez-Aranda et al., évaluation théorique des formules : https://doi.org/10.4067/S0717-92002013000300007
- AFNOR, normes NF B53-017, NF B53-020, NF EN 1309-2 et NF EN 1310 : https://www.afnor.org/
- Documents internes : `docs/methodes_calcul_volume.md`, `docs/VOLUME_CALCULATION_NEXT_GEN.md`, `TarifModels.kt`, `TarifData.kt`.

## 12. Recommandation pour GeoSylva

Conserver les sept calculateurs locaux comme stratégies additives, mais enrichir leurs résultats avec un contrat de volume explicite. La prochaine qualification scientifique doit prioriser les coefficients existants et les domaines d'application avant tout ajout de modèle IA.

## 13. Limites et vérifications manuelles

- Le texte intégral de plusieurs normes AFNOR est payant et n'a pas été reproduit ici.
- Les coefficients numériques présents dans le code doivent être confrontés aux sources primaires avant de devenir une référence GSIE.
- Les facteurs stère/m³ réel varient avec le protocole ; aucune valeur unique ne doit être affichée sans contexte.
- Les résultats LiDAR/IA cités dans la littérature ne constituent pas une validation de GeoSylva.
