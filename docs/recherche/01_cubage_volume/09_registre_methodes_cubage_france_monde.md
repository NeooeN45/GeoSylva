# Registre des méthodes de cubage forestier — France et comparaisons internationales

**Domaine** : `docs/recherche/01_cubage_volume/`  
**Identifiant** : GEOSYLVA-REG-METHODES-CUBAGE-009  
**Date** : 2026-08-30  
**Statut** : Draft  
**Agent** : Consortium dendrométrie, méthodes régionales et QA

## 1. Finalité

Ce registre identifie les méthodes une par une afin de préparer une traduction Kotlin contrôlée. Une méthode n'est déclarée « implémentable » que si sa formule ou sa table, ses unités, sa définition du volume, son domaine et ses tests de référence sont disponibles.

Les noms proches ne sont pas fusionnés automatiquement. En particulier, un tarif historique, une formule géométrique, une équation statistique et un coefficient de forme ne sont pas le même objet.

## 2. Statuts de qualification

| Statut | Signification |
|---|---|
| `A_FORMULE_VERIFIEE` | formule mathématique vérifiée et unités établies |
| `B_TABLE_SOURCEE` | table publiée identifiée, transcription à contrôler |
| `C_SOURCE_PARTIELLE` | méthode attestée, formule/table incomplète |
| `D_MODELE_A_CALIBRER` | modèle possible, paramètres propres au domaine |
| `E_NON_QUALIFIE` | nom ou attribution insuffisamment documenté |
| `F_HORS_PERIMETRE` | méthode non adaptée au produit ou à la juridiction visée |

## 3. Registre synthétique

| ID canonique | Méthode | Objet | Entrées | Périmètre | Statut actuel |
|---|---|---|---|---|---|
| `GEOM_CYLINDER` | Cylindre | grume/arbre ou segment | diamètre, longueur | universel, estimation | A_FORMULE_VERIFIEE |
| `GEOM_CONE` | Tronc de cône | grume/segment | diamètres bouts, longueur | universel, forme linéaire | A_FORMULE_VERIFIEE |
| `LOG_HUBER` | Huber | grume/billon | diamètre médian, longueur | cubage direct | A_FORMULE_VERIFIEE |
| `LOG_SMALIAN` | Smalian | grume/billon | diamètres extrêmes, longueur | cubage direct | A_FORMULE_VERIFIEE |
| `LOG_NEWTON` | Newton/Simpson | grume/billon | trois sections, longueur | cubage direct | A_FORMULE_VERIFIEE |
| `LOG_SECTIONS` | Sections multiples | grume/tige abattue | profil de sections | cubage de précision | D_MODELE_A_CALIBRER |
| `ALGAN_1E` | Algan historique | arbre sur pied | D ou C à 1,30 m | France, tarifs numérotés | C_SOURCE_PARTIELLE |
| `SCHAEFFER_FAST` | Schaeffer rapide | arbre/peuplement sur pied | D/C, numéro | France, peuplements hétérogènes | B_TABLE_SOURCEE |
| `SCHAEFFER_SLOW` | Schaeffer lent | arbre/peuplement sur pied | D/C, numéro | France, futaie homogène | B_TABLE_SOURCEE |
| `SCHAEFFER_INTERMEDIATE` | Schaeffer intermédiaire | sur pied | D/C, numéro | France, variante historique | C_SOURCE_PARTIELLE |
| `SCHAEFFER_VERY_SLOW` | Schaeffer très lent | sur pied | D/C, numéro | France, variante historique | C_SOURCE_PARTIELLE |
| `CHAUDE_DMM` | Chaudé à décroissance variable | arbre sur pied | C130, Hdec, DMM | forêt privée, France | C_SOURCE_PARTIELLE |
| `LAPASSE_PIN_MARITIME` | Lapasse | pin maritime sur pied | C130, Hdec, catégorie produit | Landes de Gascogne | C_SOURCE_PARTIELLE |
| `BOUCHON_TAillis` | Bouchon/Nys/Ranger | taillis | D130, coefficients locaux | taillis, région/sol | C_SOURCE_PARTIELLE |
| `ALGAN_MONNIN` | Algan-Monnin | grume de taillis | D130, hauteur de découpe | France, 4–12 m selon source | C_SOURCE_PARTIELLE |
| `IFN_BRUT` | Tarif brut IFN/IGN | arbre sur pied | D, H, Hdec, décroissance | inventaire national | B_TABLE_SOURCEE |
| `IFN_POLYNOMIAL_LOCAL` | Polynômes actuels GeoSylva | arbre sur pied | D ou D+H | application locale | E_NON_QUALIFIE |
| `SCHUMACHER_HALL` | Équation de puissance | arbre sur pied | D, H, essence | modèle calibré | D_MODELE_A_CALIBRER |
| `EMERGE_COHERENT` | EMERGE/Vallet | volume par découpe/biomasse | D, H, hauteur de décrochement | France, groupes d'essences | C_SOURCE_PARTIELLE |
| `STERE_EMPILAGE` | Bois empilé | pile de bûches | dimensions, longueur, empilage | énergie, contrat | C_SOURCE_PARTIELLE |
| `BIOMASS_VOLUME_DENSITY` | Volume–biomasse | compartiment | volume, densité, facteurs | carbone/énergie | D_MODELE_A_CALIBRER |

## 4. Méthodes géométriques de grumes

### 4.1 Cylindre

```text
A = π × D² / 4
V = A × L
```

Le diamètre doit être celui de la section définie par le protocole. Cette formule est une approximation d'une grume réelle si une seule section est utilisée.

### 4.2 Tronc de cône

```text
V = π × L × (D_b² + D_b × D_s + D_s²) / 12
```

Elle suppose une variation linéaire du diamètre entre les bouts. Elle ne doit pas être appelée « tarif » : elle ne dépend pas d'un échantillon statistique.

### 4.3 Huber, Smalian et Newton

Avec `A = πD²/4` :

```text
Huber   : V = L × A_m
Smalian : V = L × (A_b + A_s) / 2
Newton  : V = L × (A_b + 4A_m + A_s) / 6
```

| Méthode | Mesures | Exactitude théorique |
|---|---|---|
| Huber | section médiane + longueur | cylindre et paraboloïde dans les hypothèses classiques |
| Smalian | sections extrêmes + longueur | paraboloïde dans les hypothèses classiques ; biais sur cône |
| Newton | trois sections + longueur | cylindre, paraboloïde et cône dans les hypothèses classiques |

Pour des grumes non régulières, la méthode par sections multiples est préférable si le protocole, l'écorce et le traitement des défauts sont définis.

## 5. Tarifs historiques français sur pied

### 5.1 Algan historique

Les tarifs Algan sont une famille de tarifs à une entrée, historiquement conçus pour couvrir des situations forestières variées avec une numérotation et un étalonnage communs. Ils s'utilisent à partir d'une grosseur à 1,30 m, diamètre ou circonférence selon la table. Les travaux historiques expliquent que les tarifs peuvent s'appliquer aux peuplements mûrs ou jardinés, mais que le choix du numéro doit suivre la forme et l'hétérogénéité de la population.

**À ne pas confondre** : l'équation actuelle `a × D^b × H^c` de GeoSylva est une forme de puissance à deux entrées. Elle doit être enregistrée sous son attribution scientifique réelle, souvent rapprochée de Schumacher-Hall, et non appelée Algan sans source spécifique.

### 5.2 Schaeffer rapide, lent, intermédiaire et très lent

Les travaux de Schaeffer décrivent une famille de tarifs gradués selon la vitesse de croissance du volume avec le diamètre. Le rapide est adapté aux populations plus hétérogènes ; le lent aux futaies plus homogènes lorsque la progression des tarifs Algan paraît trop forte. Les variantes intermédiaire et très lent doivent rester des variantes distinctes tant que leurs paramètres historiques exacts n'ont pas été vérifiés.

Formes historiques couramment reproduites, à vérifier contre l'édition utilisée :

```text
Schaeffer rapide : V = M / 1400 × (D - 5) × (D - 10)
Schaeffer lent   : V = M / 1800 × D × (D - 5)
```

`D` est en cm et `M` représente le volume de l'arbre étalon de diamètre 45 cm, dans la convention historique. Les formes et unités doivent être verrouillées par une source primaire avant codage. Les coefficients `(a,b)` actuellement codés sous `V = a + bC²` ne sont pas prouvés équivalents par cette seule écriture.

### 5.3 Chaudé à décroissance métrique moyenne

Le tarif Chaudé est un barème de volume commercial des arbres sur pied. Il utilise la circonférence à 1,30 m, la hauteur de bille ou hauteur de découpe et la décroissance métrique moyenne, exprimée en cm/m. Le barème comporte des tables selon la grosseur et des valeurs de DMM ; les sources CNPF décrivent une édition à 24 tables et un usage de 20 tarifs dans certaines fiches pédagogiques.

Procédure conceptuelle :

```text
mesurer C130
estimer Hdec
estimer ou mesurer DMM par classe de grosseur
sélectionner la table Chaudé compatible
lire le volume commercial à la découpe
conserver le numéro de table et la DMM
```

La table Chaudé n'est pas remplaçable par une moyenne régionale arbitraire. Son domaine et ses tables doivent être acquis ou transcrits depuis une édition autorisée.

### 5.4 Lapasse pour le pin maritime

Le barème de Lapasse est spécifiquement associé au pin maritime et au massif landais. Les notices et documents professionnels le décrivent pour les pins sur pied, avec des volumes de grume destinés au sciage, au bois de mine et à la papeterie. Des documents régionaux précisent les peuplements non gemmés, la circonférence, la hauteur de découpe et la séparation des produits.

```text
essence = Pinus pinaster
bassin = Landes de Gascogne / Sud-Ouest selon édition
entrées = C130 + Hdec + catégorie de produit
sorties = volume par produit, selon table
```

La table Lapasse doit être codée comme une **méthode à table**, pas comme un coefficient universel du pin maritime. Une variation de découpe à 9 cm, la gemme, le tri et l'acheteur peuvent modifier le volume ou le produit retenu.

### 5.5 Algan-Monnin, Bouchon et tarifs de taillis

« Monnin » apparaît dans l'appellation historique **Algan-Monnin** pour des formules de cubage de réserves de taillis-sous-futaie. Ce n'est pas nécessairement une méthode générique indépendante. Les supports de dendrométrie citent également des tarifs de Bouchon, Nys et Ranger pour des taillis à base de chêne, ainsi que des tarifs locaux dépendant du sol pour le charme.

Ces méthodes doivent conserver : essence/groupe, structure de taillis, découpe, sol ou station, région, coefficients et source. Il ne faut pas en généraliser les coefficients à une futaie ou à une région différente.

### 5.6 Formule historique de Bouvard

Bouvard est cité dans les cours de dendrométrie pour une estimation du volume total de l'arbre, avec une hauteur totale et un diamètre à 1,30 m. La source accessible ne permet pas encore de transcrire sans ambiguïté l'expression, ses constantes et sa convention de volume. Le registre la conserve donc comme méthode identifiée mais non qualifiée ; aucune formule ne doit être inventée à partir d'un extrait OCR incomplet.

## 6. IFN/IGN et modèles à différentes découpes

### 6.1 Tarif brut IFN

L'IFN explique un tarif brut sous forme de cases de diamètre et de hauteur, avec effectif, volume centré moyen et écart-type. Les variables utilisées peuvent inclure circonférence à 1,30 m, hauteur totale, hauteur de découpe et décroissance. Le tarif brut est une représentation des observations ; il ne doit pas être remplacé sans preuve par une famille de polynômes génériques.

### 6.2 EMERGE et Vallet

EMERGE vise des modèles cohérents entre volume de tige, découpes, biomasse et bois énergie. La hauteur de décrochement ajoute une information sur le profil de tige. Les équations Vallet et al. concernent des espèces ou groupes d'espèces et doivent être associées à leur publication, à leurs unités et à leur domaine.

Un modèle EMERGE n'est pas un tarif Chaudé et ne donne pas automatiquement un prix. Il produit une grandeur volumique ou biomasse à laquelle une règle de produit et une source de marché doivent être appliquées séparément.

## 7. Variantes et méthodes internationales

### 7.1 Méthodes de grumes

Huber, Smalian, Newton et l'intégration par sections sont transposables si la convention d'écorce, le diamètre et la longueur sont explicités. Pour les systèmes commerciaux anglo-saxons tels que Doyle, Scribner et International 1/4-inch, les unités (pouces, pieds, board feet), les tables et les éditions diffèrent selon la juridiction. Ils ne doivent pas être ajoutés à GeoSylva par une formule trouvée isolément sans source de la version et sans conversion contrôlée.

### 7.2 Tarifs statistiques internationaux

Les tarifs à une entrée et les équations de forme `V = f(D,H)` sont réutilisables comme patrons mathématiques seulement. Un coefficient construit en Allemagne, au Canada ou dans une forêt tropicale n'est pas transférable à la France sans calibration et validation hors échantillon.

## 8. Bois empilé, masse et biomasse

Pour une pile :

```text
m3_apparent = longueur × largeur × hauteur
m3_reel = m3_apparent × facteur_d_empilage
```

Le facteur dépend de la longueur, de la forme, du diamètre, de l'empilage, du fendage et de la convention contractuelle. GeoSylva doit demander le facteur ou afficher une plage avec une incertitude.

Pour la biomasse :

```text
biomasse_sèche = volume × densité
carbone = biomasse_sèche × fraction_carbone
```

Les facteurs d'expansion des branches et des racines sont distincts et versionnés. Le résultat carbone ne doit pas être confondu avec une valeur de bois vendable.

## 9. Registre minimal à traduire en code

Chaque méthode codée devra posséder :

```text
method_id
method_family
object_type
formula_or_table_reference
input_schema
input_units
output_volume_kind
bark_condition
species_scope
geographic_scope
validity_domain
selection_rule
uncertainty_model
source_reference
evidence_level
implementation_status
test_vector_ids
```

La traduction Kotlin doit utiliser des stratégies séparées, une validation d'entrée commune et une sortie commune versionnée. Le code ne doit pas déduire une méthode à partir d'un simple nom d'essence si la source exige une station, une structure ou un bassin.

## 10. Sources vérifiées ou à qualifier

- CNPF Nouvelle-Aquitaine, *La vente de bois* : https://nouvelle-aquitaine.cnpf.fr/gestion-durable-des-forets/coupes-et-travaux/la-vente-de-bois
- CNPF/IFC, *Estimation des volumes de bois sur pied* : https://ifc.cnpf.fr/sites/socle/files/cnpf-old/498015_volume_pied_1.pdf
- CNPF, tarif Chaudé : https://librairie.cnpf.fr/produit/186/9782916525181/tarif-de-cubage-a-decroissances-variables-pour-les-arbres-sur-pied
- AgroParisTech, tarif Lapasse : https://infodoc.agroparistech.fr/index.php?id=93126&lvl=notice_display
- Alliance Forêts Bois, pin maritime et barème Lapasse : https://www.allianceforetsbois.fr/proprietaires-forestiers/travaux-de-sylviculture/nos-essences/culture-du-pin-maritime-par-alliance-forets-bois/
- Schaeffer/Algan, RFF : https://doi.org/10.4267/2042/26629
- Tarifs adaptés et construction d'un tarif : https://hal.science/hal-03424895/document
- IFN/IGN, tarifs et tarif brut : http://hdl.handle.net/2042/42780
- IGN, méthodologie IFN : https://inventaire-forestier.ign.fr/IMG/pdf/methodologie-2022.pdf
- EMERGE : https://hal.science/hal-03016051
- Vallet et al. : https://hal.inrae.fr/hal-02664812
- Documents internes : `TarifData.kt`, `TarifModels.kt`, `TarifCalculator.kt`, `docs/methodes_calcul_volume.md`, `08_audit_formules_coefficients_2026-08-30.md`.

## 11. Limites

Les tables Chaudé et Lapasse sont des ouvrages ou barèmes à acquérir/transcrire ; leurs coefficients complets ne doivent pas être reconstruits à partir d'extraits de moteur de recherche. Les variantes intermédiaire/très lente de Schaeffer, les tables locales de taillis et les méthodes internationales commerciales nécessitent une vérification de l'édition exacte avant implémentation.
