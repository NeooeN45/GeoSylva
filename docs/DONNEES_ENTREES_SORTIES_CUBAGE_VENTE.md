# Données nécessaires au fonctionnement de GeoSylva — Cubage, qualité, produits et valorisation

| Champ | Valeur |
|---|---|
| **Identifiant** | GEOSYLVA-SPEC-DONNEES-CUBAGE-VENTE-001 |
| **Statut** | Draft |
| **Agent** | Consortium recherche et documentation GeoSylva |
| **Version** | 0.1.0 |
| **Date** | 2026-08-30 |
| **Public** | Développeurs, concepteurs UX et utilisateurs professionnels |
| **Périmètre** | Inventaire forestier métropolitain, cubage, biomasse, qualité, produits et estimation de valeur |
| **Principe** | Offline-first, données typées, provenance obligatoire, incertitude explicite |

> Ce document définit les données nécessaires pour calculer et expliquer un résultat. Il ne rend pas un résultat automatiquement contractuel, normatif, fiscal ou certifiant.

## 1. Résumé

GeoSylva doit connaître à la fois **ce qui est mesuré**, **sur quel objet**, **avec quelle méthode**, **dans quelle unité**, **à quelle date**, **pour quel produit** et **selon quelle source de prix**. L'application peut calculer localement des estimations à partir de DHP/D130, circonférence, hauteur, essence, surface et qualité ; elle ne peut pas déduire de manière fiable un défaut interne, un prix local ou une certification uniquement à partir d'une photographie ou d'un GPS.

Chaque résultat doit donc indiquer :

```text
résultat = valeur + unité + objet + méthode + version + source + qualité des entrées + incertitude + avertissements
```

## 2. Catégories de données

| Catégorie | Origine | Exemple | Obligatoire ? |
|---|---|---|---:|
| Contexte de mission | utilisateur + application | parcelle, placette, date, opérateur | oui |
| Données de référence | cache local ou paquet qualifié | essence, tarif, prix, facteur | selon résultat |
| Mesure directe | utilisateur ou capteur | D130, C130, hauteur, longueur | selon méthode |
| Donnée dérivée | calcul GeoSylva | diamètre, G/ha, région, volume | produite par l'app |
| Observation qualitative | utilisateur/photo assistée | rectitude, nœuds, roulure, santé | qualité/produit |
| Donnée de vente | utilisateur/source marché | stade, prix, unité, contrat | valorisation |
| Preuve | application/utilisateur/source | photo, document, URL, checksum | oui pour résultat réutilisable |
| Incertitude | calcul/métadonnée | intervalle, précision GPS, hors domaine | oui pour estimation |

## 3. Ce que l'application peut récupérer automatiquement

### 3.1 Depuis le téléphone et la base locale

GeoSylva peut fournir automatiquement, lorsque l'autorisation et la donnée existent :

- identifiant de la parcelle, de la placette, de la tige et de la session ;
- date/heure et fuseau de la mesure ;
- position GPS, précision horizontale et altitude ;
- système de coordonnées utilisé pour l'export ;
- disponibilité des capteurs : accéléromètre, gyroscope, rotation, caméra ;
- mesure de hauteur issue du clinomètre numérique, avec protocole et stabilité ;
- photographie ou URI de preuve si l'utilisateur l'a capturée ;
- essence sélectionnée dans le catalogue local et ses alias ;
- valeurs déjà enregistrées pour la tige, la parcelle ou la placette ;
- surface de placette et facteurs d'expansion connus ;
- région administrative, GRECO ou SER calculée depuis une position qualifiée ;
- version des paramètres locaux, du tarif, de la table de prix et de la base ;
- résultats antérieurs et relation de remplacement via l'historique local ;
- état hors-ligne, âge du cache et statut de synchronisation.

Ces informations sont des **métadonnées d'acquisition**. Elles ne remplacent pas les mesures forestières manquantes.

### 3.2 Données que l'application peut calculer

À partir d'entrées valides, l'application peut dériver :

- circonférence à partir du diamètre ou diamètre à partir de la circonférence ;
- surface terrière individuelle et par hectare ;
- nombre de tiges par hectare ;
- diamètre moyen, diamètre quadratique et distributions par classe ;
- hauteur moyenne, hauteur dominante ou hauteur de Lorey si les définitions sont renseignées ;
- volume selon une méthode choisie et son domaine de validité ;
- volume par produit si les règles de découpe sont disponibles ;
- biomasse et carbone selon les facteurs versionnés ;
- prix de référence et valeur estimée si une observation comparable existe ;
- écart entre méthodes, fourchette et avertissement hors domaine ;
- niveau de complétude des données et liste des champs manquants.

Une donnée dérivée doit toujours conserver les identifiants des entrées et de la méthode. Elle ne doit pas être réintroduite comme une mesure primaire.

## 4. Ce que l'utilisateur doit renseigner

### 4.1 Contexte forestier

| Donnée | Exemple | Résultats concernés | Niveau |
|---|---|---|---|
| parcelle/placette | identifiant local | tous | obligatoire |
| surface inventoriée | 0,1 ha | G/ha, N/ha, V/ha | obligatoire pour extrapoler |
| type de peuplement | futaie régulière, irrégulière, taillis | choix de méthode | recommandé |
| structure | pur, mélangé, régulier, irrégulier | choix de tarif | recommandé |
| âge ou classe d'âge | 45 ans | croissance, biomasse, méthode | recommandé |
| essence principale et mélange | Douglas 70 %, hêtre 30 % | tarif, produit, prix | obligatoire par tige |
| qualité de l'identification | certaine, probable, groupe | confiance du calcul | obligatoire |
| pente et distance de débardage | 18 %, 450 m | mobilisable, valeur nette | valorisation |
| destination de la coupe | éclaircie, amélioration, définitive | découpe et valeur | recommandé |

### 4.2 Mesures d'un arbre sur pied

| Donnée | Unité | Obligatoire pour | Source |
|---|---|---|---|
| diamètre à 1,30 m `D130` | cm | tarifs et surface terrière | compas, ruban ou utilisateur |
| circonférence à 1,30 m `C130` | cm ou m | tarifs utilisant C | ruban ou conversion contrôlée |
| hauteur totale `Htot` | m | tarifs à deux entrées, biomasse | clinomètre, LiDAR ou saisie |
| hauteur à la découpe `Hdec` | m | volume commercial | utilisateur/instrument |
| diamètre fin bout ou décroissance | cm, cm/m | profil et produit | utilisateur/échantillon |
| longueur de fût commercial | m | produit et vente | utilisateur |
| coefficient de forme | sans unité | méthode FGH/coefficient | source ou override validé |
| nombre de tiges représentées | entier | inventaire par classe | utilisateur |
| facteur d'expansion | sans unité | passage placette → hectare | protocole d'inventaire |
| état de mesure | mesuré, estimé, importé | confiance | application |

Règles : `D130` n'est pas automatiquement le diamètre médian de la grume ; `Htot` n'est pas `Hdec` ; `Hm` n'est pas `Hdom`. Ces grandeurs doivent rester séparées dans le modèle.

### 4.3 Mesures d'une grume ou d'un billon

| Donnée | Unité | Obligatoire |
|---|---|---:|
| identifiant du lot et du billon | texte | oui |
| essence | code scientifique/local | oui |
| longueur `L` | m | oui |
| diamètre gros bout | cm ou m | selon méthode |
| diamètre fin bout | cm ou m | Smalian/cône |
| diamètre médian | cm ou m | Huber |
| sections intermédiaires | cm ou m | intégration multi-sections |
| écorce | sur/sous écorce | oui |
| défauts et parties retirées | texte + mesure | volume net/qualité |
| méthode de cubage | code + version | oui |
| instrument et opérateur | texte | recommandé |
| photographie des bouts | URI | recommandé |

### 4.4 Qualité et défauts

L'utilisateur doit renseigner, lorsque le produit ou le prix l'exige :

- rectitude et courbure ;
- fourche, cannelure, ovalisation et conicité ;
- diamètre, nombre et type de nœuds ;
- roulure, gélivure, gerces et fentes ;
- cœur excentré, cœur rouge ou coloration ;
- pourriture, échauffure, bleuissement et dégâts d'insectes ;
- branches, élagage et longueur sans défaut ;
- état sanitaire et dégâts de récolte ;
- grade interne A/B/C/D ou classement normatif réellement utilisé ;
- produit visé et défauts incompatibles avec ce produit.

Une photographie peut documenter une observation, mais ne prouve pas automatiquement l'absence de défaut interne. Le grade interne de GeoSylva doit être présenté comme une **évaluation** tant qu'un classement normatif ou un mesurage contradictoire n'est pas enregistré.

### 4.5 Données nécessaires à la vente

| Donnée | Exemple | Pourquoi |
|---|---|---|
| stade de vente | sur pied, bord de route, rendu usine | prix non comparable sinon |
| produit | grume, sciage, déroulage, pâte, bûche, plaquette | marché et unité |
| qualité | classe/grade + méthode | prix et destination |
| volume et convention | m³ réel, sur écorce | base de facturation |
| prix et date | 93 €/m³, année 2025 | historique |
| provenance | région, massif, département, lot | portée géographique |
| type de propriété | privée, domaniale, communale | source/marché |
| certification | aucune, PEFC, FSC, autre | allégation distincte |
| transport inclus | oui/non, point de livraison | prix départ/rendu |
| humidité | % et base humide/sèche | bois énergie |
| contrat | bloc, mesure, adjudication, gré à gré | responsabilité et preuve |
| source | FBF, ONF, Agreste, CEEB, ADEME, observation | fiabilité |

## 5. Matrice résultat → données requises

### 5.1 Résultats dendrométriques

| Résultat | Obligatoire | Recommandé | Si absent |
|---|---|---|---|
| nombre de tiges | comptage + surface | essence et classe de diamètre | résultat local sans N/ha |
| surface terrière `G` | D130/C130 + nombre + surface | facteur d'expansion | pas d'extrapolation hectare |
| diamètre moyen | liste ou effectifs par classes | poids d'échantillonnage | approximation par classes |
| hauteur moyenne | hauteurs valides | méthode et précision | pas de hauteur inventée |
| hauteur dominante | hauteurs + définition Hdom + surface | nombre de tiges/ha | Hdom non calculable |
| volume sur pied | D + méthode compatible | H, essence, station, calibration | volume non produit ou estimation dégradée |
| volume par hectare | volume individuel + surface | facteur d'expansion | volume de l'échantillon seulement |

### 5.2 Résultats de cubage

| Résultat | Données minimales | Données de confiance |
|---|---|---|
| Schaeffer/IFN 1 entrée | D130 ou C130, numéro, unités | peuplement homogène et calibration |
| Schaeffer/IFN 2 entrées | D130/C130 + H, numéro | essence, structure, domaine |
| Algan historique | D130 ou C130 + numéro de tarif selon la table | peuplement calibré, origine de la table |
| Algan-Monnin / taillis | D130 + Hdec + structure de taillis | essence, station/sol, découpe et variante exacte |
| Chaudé DMM | C130 + Hdec + DMM + numéro de barème | classe de grosseur, peuplement et table Chaudé |
| Lapasse pin maritime | C130 + Hdec + produit + état de gemmage | bassin landais, découpe et table Lapasse |
| Bouchon / tarifs locaux de taillis | D130 + découpe + station/sol | région, équation ou table primaire |
| Bouvard | D130 + Htot selon la source | formule complète et convention du volume |
| Schumacher-Hall / équation par essence | D + H + essence | coefficients et source vérifiés |
| FGH/coefficient de forme | D + H + f | f local, découpe et âge |
| Huber | L + diamètre médian | mesure médiane et écorce |
| Smalian | L + diamètres bouts | bouts préparés, ovalité |
| Newton | L + trois sections | protocole multi-section |
| profil commercial | D, Hdec, longueur, fin bout | décroissance et défauts |
| volume 3D | nuage de points calibré | échelle, géoréférencement, validation terrain |

### 5.3 Résultats produits et qualité

| Résultat | Données minimales | Données non déductibles automatiquement |
|---|---|---|
| produit principal | essence, diamètre, longueur, destination | défauts internes non visibles |
| sciage | dimensions, rectitude, nœuds, qualité | rendement industriel exact |
| placage/tranchage | essence, diamètre, fil, absence de défaut | aptitude réelle sans expertise |
| déroulage | essence, diamètre, longueur, forme, cœur | rendement usine |
| merrain | essence, diamètre, fil, roulure/fentes | qualité tonnellerie finale |
| piquet/poteau | essence, diamètre, longueur, durabilité | conformité industrielle |
| pâte/trituration | volume ou masse, essence/groupe, humidité | composition exacte si mélange |
| bûche | longueur, pile, essence, humidité | PCI sans mesure/source |
| plaquette | masse/volume, humidité, granulométrie | valeur énergétique sans PCI |

### 5.4 Résultats biomasse et carbone

| Résultat | Données minimales | Avertissement |
|---|---|---|
| biomasse fût | volume + densité + convention | densité dépend de l'essence et de l'état |
| biomasse aérienne | biomasse fût + FEB | FEB à sourcer par compartiment |
| biomasse racinaire | biomasse aérienne + FER | FER versionné et incertain |
| carbone | biomasse sèche + fraction carbone | ne pas utiliser masse humide directement |
| CO₂ équivalent | carbone + facteur de conversion | résultat scientifique, pas valeur marchande |

La provenance de chaque facteur doit être enregistrée. Le calcul doit refuser silencieusement aucune donnée : si un facteur est absent, il doit afficher `non_calculable` ou `estimation_degradee` avec la cause.

### 5.5 Résultats de prix et de valeur

| Résultat | Données minimales | Fallback autorisé |
|---|---|---|
| prix observé | valeur, unité, date, source, produit, stade | non |
| prix par essence | code essence + observation comparable | groupe d'essences explicitement signalé |
| prix régional | portée régionale et période | moyenne nationale annoncée |
| prix par qualité | qualité et méthodologie de classement | aucune conversion arbitraire |
| valeur du lot | volume comparable × prix comparable | estimation avec avertissement |
| valeur nette vendeur | valeur brute + coûts et responsabilités documentés | valeur brute seulement |
| prix bois énergie | combustible + unité + humidité/PCI | €/MWh PCI sans conversion en stère |
| prix contractualisable | contrat ou grille professionnelle | prix indicatif non contractuel |

## 6. Niveaux de fonctionnement

### Niveau 0 — Contrôle et inventaire minimal

Données : parcelle, essence, D130/C130, nombre de tiges, surface de placette.  
Résultats : comptage, classes de diamètre, surface terrière locale ou par hectare si la surface est connue.  
Limite : aucun prix et aucun volume commercial précis.

### Niveau 1 — Cubage local hors-ligne

Données : niveau 0 + méthode + numéro de tarif ou coefficient documenté.  
Résultats : volume estimé, méthode, version, source, avertissements.  
Limite : erreur liée au domaine de calibration et à l'absence éventuelle de hauteur.

### Niveau 2 — Cubage dendrométrique standard

Données : niveau 1 + hauteur + essence identifiée + structure du peuplement + hauteur de découpe si nécessaire.  
Résultats : volume à deux entrées, volume par produit indicatif et intervalle d'incertitude.  
Limite : le classement produit reste une estimation sans mesure de bille.

### Niveau 3 — Estimation de valorisation

Données : niveau 2 + qualité, défauts, dimensions, stade de vente, provenance, date et prix comparable.  
Résultats : ventilation par produit, prix unitaire sourcé, valeur brute, coûts explicités et valeur nette si calculable.  
Limite : pas de prix automatique si la source n'est pas comparable.

### Niveau 4 — Cubage direct commercial

Données : grumes/billons mesurés, longueurs, sections, écorce, défauts, protocole contradictoire et contrat.  
Résultats : volume mesuré selon méthode contractuelle et rapport de lot.  
Limite : la facturation relève du contrat et des parties.

### Niveau 5 — Assistance capteurs et télédétection

Données : niveau approprié + capteurs calibrés, photos/nuage de points, échelle, géoréférencement et placettes de validation.  
Résultats : estimation automatisée avec métriques de validation et indicateur de hors-domaine.  
Limite : aucune performance de publication ne vaut validation de GeoSylva.

## 7. Provenance des données et confiance

### 7.1 Niveaux de preuve applicatifs

| Niveau | Nature | Usage |
|---|---|---|
| A | article scientifique ou équation publiée et vérifiée | modèle scientifique |
| B | statistique ou référentiel public officiel | prix, inventaire, données nationales |
| C | organisme professionnel ou source régionale | repère ou modèle à qualifier |
| D | donnée commerciale, estimation ou saisie personnelle | indicatif, jamais vérité nationale |
| E | donnée dérivée par GeoSylva | calcul traçable avec source amont |
| F | hypothèse ou donnée non vérifiée | affichage bloqué ou avertissement fort |

Le niveau de preuve décrit la source, pas la précision automatique du résultat. Une donnée officielle peut être trop agrégée pour une parcelle ; une mesure utilisateur peut être très précise mais ne pas être représentative du marché.

### 7.2 États de résultat

```text
VALIDATED_INPUTS
ESTIMATED_WITHIN_DOMAIN
ESTIMATED_OUTSIDE_DOMAIN
MISSING_REQUIRED_DATA
SOURCE_NOT_COMPARABLE
UNVERIFIED_PARAMETER
CONTRACTUAL_MEASUREMENT_REQUIRED
```

## 8. Validation à l'entrée

Avant tout calcul :

1. vérifier les valeurs finies et les unités ;
2. contrôler les bornes physiques et les valeurs nulles ;
3. vérifier la cohérence D/C ;
4. distinguer Htot, Hdec, Hm, Hdom et hauteur de Lorey ;
5. contrôler la surface et le facteur d'expansion ;
6. vérifier l'essence et son niveau d'identification ;
7. vérifier la méthode, la version et le domaine de validité ;
8. vérifier l'écorce et le type de volume ;
9. vérifier qualité, produit, stade et prix comparable ;
10. calculer la complétude et produire les avertissements.

Aucune valeur absente ne doit être remplacée silencieusement par une moyenne ou un coefficient générique.

## 9. Pseudocode fonctionnel

```text
collecter_contexte()
collecter_mesures()
collecter_qualite_et_produit_si_valorisation()
normaliser_unites()
valider_coherence_et_bornes()

si données_requises_absentes:
    retourner MISSING_REQUIRED_DATA + liste_des_champs

methode = selectionner_methode(
    essence,
    structure,
    disponibilité_hauteur,
    disponibilité_grumes,
    domaine_des_coefficients
)

volume = calculer(methode, mesures)
incertitude = estimer_incertitude(methode, qualité_des_mesures, domaine)

si valorisation_demandée:
    prix = rechercher_prix_comparable(
        essence, produit, qualité, stade, provenance, période, unité
    )
    si prix_absent:
        retourner volume + SOURCE_NOT_COMPARABLE
    valeur = volume_comparable × prix

retourner valeur, unité, méthode, source, version, incertitude, avertissements
```

## 10. Correspondance avec l'implémentation actuelle

| Contrat documentaire | Modèle ou composant actuel |
|---|---|
| tige individuelle | `domain/model/Tige.kt` |
| essence locale | `domain/model/Essence.kt`, `CanonicalEssences.kt` |
| méthode et coefficients | `domain/calculation/tarifs/TarifModels.kt`, `TarifData.kt` |
| calcul des tarifs | `domain/calculation/tarifs/TarifCalculator.kt` |
| produits et grades | `domain/calculation/quality/WoodQualityGrade.kt` |
| défauts | `domain/calculation/pricing/WoodDefect.kt` |
| prix par essence/produit | `domain/calculation/PriceCalculator.kt` |
| contexte de prix | `domain/calculation/pricing/PricingContext.kt` |
| breakdown transparent | `domain/calculation/pricing/PricingResult.kt`, `ProPricingEngine.kt` |
| conversion stère/m³ | `domain/calculation/tarifs/VolumeConversion.kt` |
| historique de calcul | `CalculationRunEntity` et DAO associés |
| import/export | `ImportDataUseCase`, `ExportDataUseCase`, `PriceSyncWorker` |

Le modèle actuel est riche mais plusieurs distinctions de ce contrat doivent encore être structurées avant une intégration complète : type de volume, période de prix, écorce, tonne verte/sèche, degré de certitude de l'essence, hauteur de découpe et historique des règles de prix.

## 11. Données jamais déduites automatiquement sans preuve

GeoSylva ne doit pas déduire seule :

- un prix départemental à partir d'un prix national ;
- un prix par qualité à partir d'une moyenne toutes qualités ;
- une certification PEFC/FSC à partir d'une localisation ;
- une origine française à partir d'un nom d'essence ;
- l'absence de défaut interne à partir d'une photo ;
- un volume commercial à partir du seul volume biologique ;
- une humidité, une tonne sèche ou un PCI sans mesure ou facteur sourcé ;
- un tarif adapté à un département sans calibration ou source explicite ;
- une conformité à une norme payante sans édition et protocole vérifiés ;
- une valeur contractuelle à partir d'un prix indicatif.

## 12. Sources

- IGN, méthodologie IFN : https://inventaire-forestier.ign.fr/IMG/pdf/methodologie-2022.pdf
- IGN, DataIFN : https://inventaire-forestier.ign.fr/dataifn/
- CNPF, vente de bois : https://nouvelle-aquitaine.cnpf.fr/gestion-durable-des-forets/coupes-et-travaux/la-vente-de-bois
- CNPF/IFC, estimation des volumes sur pied : https://ifc.cnpf.fr/sites/socle/files/cnpf-old/498015_volume_pied_1.pdf
- CNPF/IFC, estimer et vendre ses bois : https://ifc.cnpf.fr/sites/ifc/files/2024-03/Fiche%20Gestion%2021%20-%20Estimer%20et%20Vendre%20ses%20Bois.pdf
- France Bois Forêt, indicateur 2026 : https://franceboisforet.fr/2026/05/26/prix-de-vente-des-bois-sur-pied-en-foret-privee-indicateur-2026/
- Agreste, enquête prix des bois : https://www.mesdemarches.agriculture.gouv.fr/demarches/proprietaire-ou-operateur/repondre-aux-enquetes-statistiques-87/article/enquete-sur-les-prix-des-bois
- ADEME, prix des combustibles bois : https://data.ademe.fr/datasets/prix-bois-domestique
- Deleuze et al., EMERGE : https://hal.science/hal-03016051
- Vallet et al. : https://hal.inrae.fr/hal-02664812
- Documents internes : `docs/REFERENTIEL_CUBAGE_VENTE_FRANCE.md`, `docs/recherche/01_cubage_volume/09_registre_methodes_cubage_france_monde.md`, `docs/methodes_calcul_volume.md`, `docs/VOLUME_CALCULATION_NEXT_GEN.md`.

## 13. Critères d'acceptation

- [ ] Chaque sortie affiche sa définition de volume et son unité.
- [ ] Chaque estimation affiche sa méthode, sa version et sa source.
- [ ] Les champs manquants produisent une cause explicite.
- [ ] D130, C130, Htot, Hdec, Hm et Hdom ne sont pas confondus.
- [ ] Les prix sont comparés uniquement à convention équivalente.
- [ ] Les conversions stère/m³, humide/sec et €/MWh sont sourcées.
- [ ] Les prix régionaux conservent leur granularité réelle.
- [ ] Les résultats hors domaine sont marqués et non promus silencieusement.
- [ ] Le fonctionnement hors-ligne ne dépend pas d'un appel réseau.
- [ ] Les vecteurs de test couvrent les méthodes et unités utilisées.

## 14. Historique

| Version | Date | Modification |
|---|---|---|
| 0.1.0 | 2026-08-30 | Création du contrat de données d'entrée/sortie pour le cubage et la vente. |
