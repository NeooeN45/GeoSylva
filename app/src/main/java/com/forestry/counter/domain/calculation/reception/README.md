# Noyau embarqué de mesurage des bois récoltés

| Champ | Valeur |
|---|---|
| Statut | Draft — code de domaine, parcours mobile non raccordé |
| Version du contrat | `wood-reception-volume-1` |
| Version des méthodes | `1.0.0` |
| Date | 2026-10-08 |
| Auteur | Codex |

Cette tranche fournit un calcul local à partir de mesures explicitement
déclarées. Elle n'ajoute ni écran, ni persistance, ni synchronisation, ni
intégration Forlog. Aucune donnée de démonstration n'est ajoutée au produit.

## Grumes

L'appelant choisit Huber, Smalian ou Newton et fournit une longueur en mètres,
des diamètres en centimètres aux positions attendues et une convention
**sur écorce** ou **sous écorce** obligatoire. La convention est conservée ;
aucun retrait d'écorce ni arrondi commercial n'est appliqué.

Pour une section circulaire, `S = π × (d_cm / 200)²` en m² :

| Méthode | Entrées nécessaires | Formule géométrique |
|---|---|---|
| Huber | Longueur et diamètre médian strictement positifs | `V = L × Sm` |
| Smalian | Longueur positive ; diamètres début/fin positifs ou nuls | `V = L × (S0/2 + S1/2)` |
| Newton | Longueur positive ; diamètres début/milieu/fin positifs ou nuls | `V = L × (S0/6 + 2Sm/3 + S1/6)` |

Les sections inutilisées sont refusées, même si leur diamètre vaut zéro.
Smalian et Newton exigent au moins une section non nulle. La sortie
`GEOMETRIC_LOG_M3` conserve la méthode et la convention : un volume sur écorce
peut inclure l'écorce ; il n'est pas rebaptisé volume de bois sans écorce.

## Piles

`RectangularStackMeasurement` déclare une **enveloppe rectangulaire** avec
longueur, largeur et hauteur moyenne en mètres strictement positives. Une
référence de protocole non vide est obligatoire et conservée exactement.
Le calcul `L × largeur × hauteur moyenne` produit `STACKED_ENVELOPE_M3`.
Cette grandeur inclut les vides de l'empilement. Aucune conversion en stère
ou en volume solide et aucun coefficient par essence ou région ne sont codés.
Déclarer un protocole ne prouve pas son authenticité ou son adéquation terrain.

## Validation et agrégation

NaN, infinis, données manquantes et valeurs interdites sont refusés. Chaque
section et chaque résultat attendu positif doit rester fini et strictement
positif. Un sous-débordement à zéro ou débordement intermédiaire d'une section
ou d'un produit est refusé, même si un autre ordre de calcul pourrait donner
un résultat représentable. La pile contrôle d'abord `L × largeur`, puis le
produit par la hauteur.
Les petites valeurs subnormales encore représentables restent conservées.

`aggregate` recalcule chaque entrée et expose une copie non modifiable de ses
résultats. Il accepte des méthodes de grumes différentes avec la même
convention d'écorce. Il refuse le mélange grumes/piles, sur/sous écorce et les
piles dont les références de protocole diffèrent. Chaque valeur binaire de la
somme est capturée exactement par `BigDecimal(Double)`, additionnée sans arrondi
intermédiaire, puis convertie une seule fois en `Double`. Le contrôle porte
sur cette somme finale ; une addition flottante intermédiaire débordée ne
provoque plus un rejet d'un total encore représentable.
Sans identifiant d'objet dans ce noyau, une entrée répétée est additionnée :
la déduplication métier et la filiation des bois appartiennent au futur module.

La cible de concordance serveur est `math.fsum` pour ces valeurs non négatives.
Les fixtures communes doivent vérifier cette concordance dans l'environnement
qualifié ; aucune identité binaire universelle entre plateformes n'est déduite.
La somme exacte ne récupère pas une section ou un produit déjà refusé et
n'ajoute aucune précision aux mesures physiques.

## Qualification et limites

Les tests décrivent des cylindres, cônes et paraboloïdes analytiques, des
domaines numériques invalides et des agrégats homogènes ou incompatibles.
Ces références prouvent une cohérence mathématique ; elles ne constituent
ni relevés terrain, ni certification normative ou précision commerciale.
Les sources formelles d'un protocole de réception, de son champ régional,
des arrondis et tolérances restent à qualifier avant adoption métier.

Le contrat devra être raccordé à des objets identifiés et localisés, des
révisions persistantes et une synchronisation GSIE autorisée. Quantités
contractuelles, stocks, achats, prestations, ventes, HT/TTC et Forlog restent
hors de cette tranche. Les preuves d'exécution des tests seront consignées
dans la qualification du lot ; aucun succès de build n'est déduit du code.
