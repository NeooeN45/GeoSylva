# Audit des formules et coefficients de cubage GeoSylva

**Domaine** : `docs/recherche/01_cubage_volume/`  
**Identifiant** : GEOSYLVA-AUDIT-CUBAGE-008  
**Date** : 2026-08-30  
**Statut** : Draft  
**Agent** : Consortium audit scientifique du cubage

## 1. Objet

Cet audit compare les formules annoncées dans la documentation, les formules implémentées dans le code et les références actuellement disponibles. Il ne constitue pas une validation scientifique des coefficients. Aucune valeur n'est corrigée automatiquement.

## 2. Résumé des constats

| Méthode | Formule actuelle | Constat | Statut recommandé |
|---|---|---|---|
| Schaeffer 1E | `V = a + b × C²` | attribution et coefficients non revalidés contre une table primaire ; documentation interne contradictoire | à qualifier |
| Schaeffer 2E | `V = a + b × C² × H` | même problème de source et de domaine | à qualifier |
| Algan | `V = a × D^b × H^c` | forme attribuée à Algan alors qu'elle correspond à une équation de puissance de type Schumacher-Hall | nomenclature à corriger avant usage |
| IFN rapide | polynôme `a0 + a1D + a2D²` | forme et 36 coefficients non établis comme copie d'un tarif IGN officiel | expérimental/non vérifié |
| IFN lent | `a0 + a1D² + a2D²H` | coefficients et mapping essence-numéro non revalidés | expérimental/non vérifié |
| FGH | `V = f × G × H` | formule correcte ; f constant par essence simplifie une variabilité réelle | estimation avec réserve |
| Coefficient de forme | `V = G × H × f` | formule correcte ; valeurs et domaine par diamètre/classe sociale à vérifier | estimation avec réserve |
| Schumacher-Hall | non exposée sous ce nom | la forme de puissance existe dans le chemin nommé Algan | décision de nomenclature requise |

## 3. Vérification documentaire/code

### 3.1 Schaeffer

Le code local utilise des coefficients `(a,b)` et une circonférence en mètres. Les recherches internes documentent aussi des formes historiques basées sur un numéro de tarif et un volume de référence à 45 cm. Ces deux représentations ne doivent pas être considérées équivalentes sans démonstration numérique et table de correspondance.

Action requise : obtenir une table primaire ou une reproduction autorisée, confirmer les unités, le diamètre minimal, le volume de référence, la découpe et le domaine d'application, puis comparer plusieurs valeurs indépendantes.

### 3.2 Algan et Schumacher-Hall

La forme `a × D^b × H^c` est une équation allométrique de puissance. Les tarifs Algan historiques décrits dans les sources de recherche sont des tables/formules historiques qui ne justifient pas automatiquement les centaines de coefficients actuels par essence.

Action requise :

1. décider si la méthode doit être nommée `SCHUMACHER_HALL` ;
2. conserver `ALGAN` uniquement si une table/formule Algan identifiable est fournie ;
3. attribuer chaque coefficient à sa publication exacte ;
4. retirer les fallbacks par essence proche du chemin de vente si la correspondance n'est pas prouvée.

### 3.3 IFN

L'IGN décrit les tarifs comme issus de données d'arbres échantillons et avec un domaine de validité. La série polynomiale actuellement codée doit être traitée comme une approximation interne tant que les coefficients ne sont pas rapprochés d'une table ou recalculés depuis DataIFN selon un protocole reproductible.

Action requise : conserver le numéro de tarif, la source, le domaine `D/H`, la campagne et la version. Ne jamais présenter les 36/8 séries actuelles comme « officielles IGN » sur leur seule présence dans le code.

### 3.4 FGH et coefficient de forme

La formule géométrique est correcte : `G = π/4 × (D/100)²`, puis `V = G × H × f`. Le facteur `f` varie toutefois avec l'essence, la dimension, la classe sociale, l'âge, la station et la découpe. Une table à une valeur par essence est une simplification, pas une constante biologique.

## 4. Contradictions internes à résoudre

| Fichier | Contradiction |
|---|---|
| `docs/methodes_calcul_volume.md` | décrit Schaeffer comme une puissance alors que `TarifModels.kt` utilise `a + bC²` |
| `docs/methodes_calcul_volume.md` | valeurs d'exemple des facteurs de forme différentes de `TarifData.kt` |
| `TarifData.kt` | en-têtes attribuent `aD^bH^c` à Algan sans séparer Schumacher-Hall |
| `PriceCalculator.kt`/`WoodQualityGrade.kt` | références normatives utilisées à côté de multiplicateurs économiques, sans démontrer qu'une norme fixe un prix |
| `TarifCalculator.kt` | fallback vers une essence proche lorsque l'essence demandée n'a pas de coefficient spécifique |

## 5. Tests exigés avant qualification

- comparer les formules à une source primaire sur plusieurs diamètres et hauteurs ;
- tester les bornes de D/H et refuser l'extrapolation silencieuse ;
- vérifier la cohérence diamètre/circonférence ;
- vérifier la convention sur/sous écorce et bois fort tige ;
- comparer les résultats à des arbres ou grumes de référence indépendants ;
- documenter l'erreur moyenne, le biais et l'intervalle de validité ;
- tester l'absence de fallback non sourcé pour une vente ;
- versionner toute correction de coefficient.

Les vecteurs géométriques indépendants se trouvent dans `../02_marche_prix/vecteurs_tests_cubage.json`. Ils vérifient les formules de sections, pas les coefficients empiriques.

## 6. Décision actuelle

À la date du 2026-08-30 :

- les formules FGH, coefficient de forme, Huber, Smalian, Newton et cône sont documentées comme formules géométriques ;
- les coefficients empiriques Schaeffer, IFN et Algan restent à qualifier ;
- aucune modification automatique de `TarifData.kt` n'est autorisée par cet audit ;
- les méthodes non qualifiées doivent être signalées dans l'interface si elles servent à une estimation de valeur ;
- une intégration GSIE nécessite une fiche scientifique qualifiée distincte.

## 7. Sources

- IGN, méthodologie IFN 2022 : https://inventaire-forestier.ign.fr/IMG/pdf/methodologie-2022.pdf
- IGN, DataIFN : https://inventaire-forestier.ign.fr/dataifn/
- CNPF, estimation des volumes sur pied : https://ifc.cnpf.fr/sites/socle/files/cnpf-old/498015_volume_pied_1.pdf
- Chevrou, construction d'un tarif de cubage : https://hal.science/hal-03424895/document
- Deleuze et al., EMERGE : https://hal.science/hal-03016051
- Diéguez-Aranda et al. : https://doi.org/10.4067/S0717-92002013000300007
- Fichiers internes : `TarifData.kt`, `TarifModels.kt`, `TarifCalculator.kt`, `docs/methodes_calcul_volume.md`, `docs/recherche/01_cubage_volume/01_tarifs_schaeffer_algan.md`, `02_tarifs_ifn_emerge.md`.

## 8. Limites

Les tables historiques papier, plusieurs normes AFNOR et certains coefficients par essence ne sont pas librement vérifiables dans leur intégralité. Le présent audit est une porte de qualification, pas une homologation des calculs.
