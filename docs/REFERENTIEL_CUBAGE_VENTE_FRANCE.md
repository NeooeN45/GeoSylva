# Référentiel du cubage forestier et de la vente des bois en France — GEOSYLVA-REF-CUBAGE-VENTE-001 v0.1.0

| Champ | Valeur |
|---|---|
| **Identifiant** | GEOSYLVA-REF-CUBAGE-VENTE-001 |
| **Statut** | Draft |
| **Agent** | Consortium recherche et documentation GeoSylva |
| **Version** | 0.1.0 |
| **Date** | 2026-08-30 |
| **Périmètre** | France métropolitaine ; méthodes internationales utiles à la comparaison |
| **Public prioritaire** | Développeurs et utilisateurs professionnels de GeoSylva |
| **Complément** | Corpus détaillé, registre des méthodes et contrat de données dans `docs/recherche/01_cubage_volume/` et `docs/recherche/02_marche_prix/` |

> Ce document est un référentiel de conception et de recherche. Il ne remplace ni un barème contractuel, ni une norme AFNOR achetée, ni une expertise forestière, ni un conseil juridique ou fiscal.

## 1. Résumé

Le cubage forestier ne désigne pas une formule unique. La méthode dépend de l'objet mesuré : arbre sur pied, grume, lot empilé, biomasse ou produit transformé. La vente dépend en plus de l'essence, de la qualité, des dimensions, de la découpe, de l'accessibilité, de la provenance, du stade de livraison, de la certification, de l'humidité et de la conjoncture.

GeoSylva doit donc produire des résultats **typés, sourcés, datés et accompagnés d'une incertitude**. Une valeur numérique sans définition de volume, unité, écorce, date et source est inapte à une comparaison commerciale.

## 2. Principes non négociables

1. **Définir avant de calculer** : nature du volume, unité, écorce, découpe, population et usage.
2. **Ne jamais extrapoler silencieusement** un tarif hors de son essence, diamètre, hauteur, région ou type de peuplement.
3. **Séparer estimation et mesurage** : un arbre sur pied donne généralement un volume estimé ; une grume accessible peut être cubée directement.
4. **Séparer science et prix** : une norme de classement ne fixe pas automatiquement un multiplicateur de prix.
5. **Historiser** : une nouvelle observation de prix ferme la validité de la précédente ; elle ne l'écrase pas.
6. **Tracer la provenance** : chaque coefficient, prix, conversion et classement porte une référence et un niveau de preuve.
7. **Dégradation gracieuse hors-ligne** : le calcul local reste utilisable, mais la précision et le statut commercial doivent être affichés.
8. **Aucune promesse parcellaire sans donnée parcellaire** : une moyenne nationale ou régionale ne devient pas un prix communal par interpolation non prouvée.

## 3. Vocabulaire canonique

| Terme | Définition opérationnelle | Ne pas confondre avec |
|---|---|---|
| `volume_sur_pied` | Estimation d'un arbre ou peuplement non abattu à partir de mesures indirectes | volume réellement mesuré après abattage |
| `volume_bois_fort_tige` | Volume de la tige principale jusqu'à une découpe conventionnelle, couramment 7 cm pour l'IFN | volume total aérien avec branches |
| `volume_commercial` | Fraction correspondant à une découpe, une qualité et un produit vendable | volume biologique total |
| `volume_mobilisable` | Fraction techniquement, économiquement et réglementairement exploitable | stock sur pied |
| `volume_sur_ecorce` | Volume calculé avec l'écorce | volume sous écorce |
| `m3_reel` | Volume de bois plein, sans vides d'empilage | m³ apparent ou stère |
| `m3_apparent` | Encombrement extérieur d'un lot de bois avec ses vides | volume de matière ligneuse |
| `stere` | Ancienne unité pratique correspondant à un m³ apparent de bûches de 1 m correctement empilées | quantité fixe de bois plein |
| `tonne_verte` | Masse à une humidité donnée, non normalisée sans préciser cette humidité | tonne sèche |
| `tonne_seche` | Masse anhydre ou rapportée à une référence sèche explicitement définie | tonne livrée humide |
| `prix_sur_pied` | Prix observé avant abattage et façonnage, selon le périmètre de la source | prix bord de route ou rendu usine |
| `prix_bord_route` | Prix après abattage/façonnage au point de dépôt défini | prix sur pied |
| `prix_rendu` | Prix comprenant un transport et un point de livraison définis | prix départ forêt |
| `provenance` | Zone et statut de production/vente : pays, région, massif, département, forêt publique/privée | essence ou certification |

## 4. Choix de méthode par situation

| Situation | Méthode prioritaire | Entrées minimales | Niveau de résultat |
|---|---|---|---|
| Inventaire rapide homogène | Tarif 1 entrée Schaeffer ou IFN, après calibration locale | D130 ou C130 | Estimation de peuplement |
| Peuplement mélangé ou irrégulier | Tarif 2 entrées, équation par essence ou échantillon calibré | D130, H, essence | Estimation avec incertitude |
| Expertise ou vente de précision | Cubage direct d'arbres témoins/grumes et tarif validé | D, H, découpe, qualité | Estimation commerciale contrôlée |
| Grume ébranchée | Huber, Smalian ou Newton selon protocole | L, sections de bouts et éventuellement milieu | Cubage direct |
| Bois empilé | Mesure de l'encombrement puis facteur documenté | longueur, dimensions de pile, longueur des bûches, empilage | m³ apparent puis m³ réel |
| Bois-énergie | Masse ou volume avec humidité et PCI précisés | masse, humidité, essence, forme du combustible | tonne ou MWh PCI |
| Biomasse/carbone | Volume + densité + facteurs d'expansion | volume, essence, densité, facteurs, compartiment | estimation scientifique, pas prix |
| LiDAR/photogrammétrie | Modèle calibré sur terrain | nuage de points, géométrie, placettes de référence | estimation automatisée avec métriques de validation |

## 5. Formules de référence

### 5.1 Surface terrière

Pour un diamètre `D` en mètres :

```text
G_arbre = π / 4 × D²
G_hectare = (Σ G_arbre × coefficient_d_expansion) / surface_de_placette_ha
```

Pour un diamètre en centimètres : `D_m = D_cm / 100`. La surface terrière n'est pas un volume.

### 5.2 Volume sur pied par coefficient de forme

```text
V = G × H × f
f = V / (G × H)
```

`f` dépend notamment de l'essence, de l'âge, de la station, de la sylviculture, de la hauteur considérée et de la découpe. Une valeur par essence non accompagnée d'un domaine de validité est un défaut de traçabilité.

### 5.3 Cubage d'une grume

Avec `L` en m et les diamètres en m :

```text
A = π × D² / 4
Huber   : V = L × A_m
Smalian : V = L × (A_b + A_s) / 2
Newton  : V = L × (A_b + 4 × A_m + A_s) / 6
Cône    : V = π × L × (D_b² + D_b × D_s + D_s²) / 12
```

La méthode choisie et la convention d'écorce doivent figurer dans le résultat.

### 5.4 Tarifs et équations

Formes rencontrées :

```text
V = f(D)
V = f(D, H)
V = a × D^b × H^c
V = a + b × D² × H
```

Les coefficients ne sont pas interchangeables. GeoSylva doit stocker `method_code`, `method_version`, les unités, le domaine de validité et la source avec les paramètres.

### 5.5 Biomasse et carbone

Forme générale documentée par l'IGN/INRAE :

```text
M_sèche = V × densité
C = V_IFN × DEN × FEB × FER × CAR
```

`DEN`, `FEB`, `FER` et `CAR` sont des paramètres de compartiment et de convention, non des constantes universelles. Les facteurs d'expansion et le taux de carbone doivent être versionnés.

## 6. Essences et produits

Le périmètre commercial doit couvrir au minimum :

- **Feuillus** : chênes sessile et pédonculé, hêtre, châtaignier, frêne, peupliers, charme, robinier, bouleaux, aulnes, érables, merisier, noyer, tilleuls, ormes, saules, chênes méditerranéens, fruitiers et essences de niche.
- **Résineux** : douglas, épicéa commun et de Sitka, sapin pectiné, pins maritime/sylvestre/laricio/noir/Alep/pignon/Weymouth/cembro/mugo, mélèzes, cèdres, cyprès, if, genévriers, séquoias et autres conifères introduits.

Produits à distinguer dans le modèle : bois d'œuvre, grume longue, sciage qualité, sciage standard, placage/tranchage, déroulage, merrain, charpente, bardage, palette, traverse, piquet, poteau, trituration/pâte, bois bûche, plaquette et autre bois énergie.

La liste détaillée, les synonymes et la couverture des données sont dans `catalogue_essences_commerciales_france.csv`. Elle ne doit pas être confondue avec l'autorité taxonomique TAXREF ni avec le dataset interne d'indigénat arboré (`GSIE/DATASETS/indigenat_especes_arborees_2026/`) : un taxon arboré n'est pas automatiquement une essence commercialisée.

## 7. Prix : ce qui est connu et ce qui ne l'est pas

L'indicateur France Bois Forêt 2026, basé sur les ventes groupées de bois sur pied en forêt privée en 2025, fournit notamment :

| Essence ou agrégat | Prix 2024 | Prix 2025 | Unité et périmètre |
|---|---:|---:|---|
| Toutes essences | 90 | 86 | €/m³, bois d'œuvre sur pied, moyenne du panel |
| Chêne | 228 | 190 | €/m³, moyenne du panel |
| Hêtre | 56 | 49 | €/m³, moyenne du panel |
| Frêne | 158 | 155 | €/m³, moyenne du panel |
| Châtaignier | 119 | 97 | €/m³, moyenne du panel |
| Douglas | 89 | 93 | €/m³, moyenne du panel |
| Épicéa commun | 54 | 69 | €/m³, moyenne du panel |
| Épicéa de Sitka | 60 | 65 | €/m³, moyenne du panel |
| Sapin pectiné | 47 | 53 | €/m³, moyenne du panel |
| Pin maritime | 56 | 53 | €/m³, moyenne du panel |
| Pin laricio | 40 | 45 | €/m³, moyenne du panel |
| Pin sylvestre | 36 | 44 | €/m³, moyenne du panel |
| Peuplier | 73 | 62 | €/m³, moyenne du panel |

Ces valeurs ne constituent ni un prix par qualité A/B/C/D, ni un prix par département, ni un prix bord de route. Le périmètre géographique doit reprendre l'intitulé exact de chaque publication ; lorsque la source indique « France hexagonale », cette portée ne doit pas être reformulée en « France métropolitaine ». Il ne doit pas leur être appliqué de multiplicateur inventé pour produire une précision commerciale artificielle.

Pour le bois énergie domestique, l'ADEME publie une série en €/MWh PCI, différente des prix en €/m³ ou €/stère. Toute conversion doit ajouter l'humidité, le type de combustible, l'essence ou le mélange et le facteur de conversion sourcé.

## 8. Cadre de données GeoSylva

Le résultat minimal doit conserver :

```text
species_id / local_species_code
product_code
method_code / method_version
volume_kind
volume_value / volume_unit
bark_condition
measurement_protocol
quality_grade / defects
provenance_scope
sale_stage
price_value / price_unit / vat_status
valid_from / valid_to
source_reference / evidence_level
uncertainty / sample_size
```

Le modèle détaillé et le schéma JSON sont dans `08_modele_donnees_cubage_prix.md` et `prix_observation.schema.json`.

## 9. Intégration actuelle dans GeoSylva

Les mesures réellement disponibles dans `Tige.kt` comprennent notamment : `essenceCode`, `diamCm`, `hauteurM`, localisation, précision, produit, coefficient de forme, valeur, numéro de tarif, catégorie, qualité, défauts, photographie et métadonnées de source/version. `Essence.kt` porte le code local, le nom, la catégorie, la densité, l'usage, la croissance et des remarques.

Les méthodes locales actuelles sont déclarées dans `TarifModels.kt` et `TarifData.kt` : Schaeffer 1E/2E, Algan, IFN rapide/lent, FGH et coefficient de forme. Le présent référentiel ne valide pas automatiquement les coefficients numériques existants ; il définit les informations qui devront accompagner leur qualification.

L'application reste **offline-first**. Le réseau peut fournir une nouvelle version de barème ou de prix, mais ne doit pas être requis pour le calcul de terrain. Une grille distante doit être validée intégralement avant publication locale et ne doit jamais remplacer silencieusement une version historique.

## 10. Sources principales

### Sources officielles et scientifiques

- IGN, *Méthodologie de l'inventaire forestier national*, https://inventaire-forestier.ign.fr/IMG/pdf/methodologie-2022.pdf
- IGN, DataIFN, https://inventaire-forestier.ign.fr/dataifn/
- CNPF, *La vente de bois*, https://nouvelle-aquitaine.cnpf.fr/gestion-durable-des-forets/coupes-et-travaux/la-vente-de-bois
- CNPF/IFC, *Estimer et vendre ses bois*, https://ifc.cnpf.fr/sites/ifc/files/2024-03/Fiche%20Gestion%2021%20-%20Estimer%20et%20Vendre%20ses%20Bois.pdf
- CNPF/IFC, *Estimation des volumes de bois sur pied*, https://ifc.cnpf.fr/sites/socle/files/cnpf-old/498015_volume_pied_1.pdf
- France Bois Forêt, indicateur 2026, https://franceboisforet.fr/2026/05/26/prix-de-vente-des-bois-sur-pied-en-foret-privee-indicateur-2026/
- France Bois Forêt, communiqué et plaquette 2026, https://franceboisforet.fr/wp-content/uploads/2026/05/CP_PlaquettePrixDesBois_v4.pdf
- Agreste, enquête prix des grumes, https://www.mesdemarches.agriculture.gouv.fr/demarches/proprietaire-ou-operateur/repondre-aux-enquetes-statistiques-87/article/enquete-sur-les-prix-des-bois
- ADEME, prix des combustibles bois, https://data.ademe.fr/datasets/prix-bois-domestique
- Deleuze et al., projet EMERGE et volumes à différentes découpes, https://hal.science/hal-03016051
- Vallet et al. (2006), équations de volume, https://hal.inrae.fr/hal-02664812
- Chevrou (1988), construction d'un tarif de cubage, https://hal.science/hal-03424895/document
- AFNOR, NF B53-017, NF B53-020, NF EN 1309-2, NF EN 1310, NF EN 1316 et NF EN 1927 : textes normatifs à consulter dans leur édition en vigueur.

### Sources internes

- `docs/methodes_calcul_volume.md`
- `docs/VOLUME_CALCULATION_NEXT_GEN.md`
- `docs/recherche/01_cubage_volume/01_tarifs_schaeffer_algan.md`
- `docs/recherche/01_cubage_volume/02_tarifs_ifn_emerge.md`
- `docs/recherche/01_cubage_volume/03_coefficients_forme_biomasse.md`
- `docs/recherche/01_cubage_volume/05_normes_qualite_bois.md`
- `docs/recherche/02_marche_prix/01_prix_national_fbf.md`
- `docs/recherche/02_marche_prix/02_prix_regionaux.md`
- `docs/recherche/02_marche_prix/03_onf_cooperatives_bois_energie.md`
- `app/src/main/java/com/forestry/counter/domain/model/Tige.kt`
- `app/src/main/java/com/forestry/counter/domain/model/Essence.kt`
- `app/src/main/java/com/forestry/counter/domain/calculation/tarifs/TarifModels.kt`
- `app/src/main/java/com/forestry/counter/domain/calculation/tarifs/TarifData.kt`

## 11. Historique

| Version | Date | Modification |
|---|---|---|
| 0.1.0 | 2026-08-30 | Création du référentiel maître après recherche multi-agents et revue locale. |
