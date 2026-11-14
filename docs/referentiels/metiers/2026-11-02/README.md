# Référentiel des métiers et activités — France métropolitaine

Recherche du 2 novembre 2026 pour préparer la gestion de chantier forestier et
les futures spécialisations de Géosylva. Le fondateur a validé le rendu de la
Synthèse Technicien et demande maintenant ce catalogue, puis l’étude des pièces
FORLOG, avant de choisir les premiers parcours à concevoir en maquettes.

## Résultat et portée

**80 fiches**, réparties en **9 familles** : **32 métiers**, **25 fonctions**,
**21 spécialités** et **2 statuts**. Ce classement est une proposition de
conception : ce n’est ni une nomenclature officielle ni 80 nouveaux métiers
disponibles dans l’application. Il couvre l’amont forestier, les chantiers, le
commerce, la logistique et les premières transformations. Les domaines connexes
ou moins documentés sont conservés dans **24 axes complémentaires** à étudier.

La recherche comporte **72 URL sources distinctes** : **67 pages ouvertes** et
**5 sources limitées à leur extrait de recherche**, signalées comme telles.
Les fiches se répartissent en **60 documentées**, **18 partiellement
documentées** et **2 propositions de fonction à confirmer**. Le niveau concerne
la documentation du métier ou de l’activité ; il ne certifie ni une personne,
ni un calcul, ni la disponibilité d’un écran.

La couverture est étendue et contrôlée par familles et échanges de chantier.
Elle reste évolutive : les variantes régionales, les responsabilités propres à
chaque entreprise et les métiers aval détaillés demanderont des compléments.

| Famille proposée | Fiches | Exemples présents |
|---|---:|---|
| Gestion forestière et maîtrise d’ouvrage | 8 | Technicien, gestionnaire, ingénieur, expert, propriétaire |
| Conception et pilotage de chantiers | 5 | Conducteur de travaux, responsable d’exploitation, chef de chantier, chef d’équipe, direction d’ETF |
| Sylviculture, reboisement et travaux des milieux | 13 | Ouvrier sylvicole, pépiniériste, arboriste, semences, travaux de restauration |
| Récolte et mobilisation des bois | 10 | Bûcheron, débardeur, conducteur d’engins, abatteuse, porteur, câble, traction animale |
| Achat et vente de bois | 7 | Exploitant, commis/acheteur, négociant, technico-commercial, administration des ventes |
| Logistique, transport et réception | 8 | Grumier, exploitation transport, réceptionnaire/cubeur, pont-bascule, parc à bois |
| Transformation et valorisation | 9 | Scierie, classement, séchage, merrandage, affûtage, bois-énergie |
| Expertise, données et écosystèmes | 11 | Inventaire, géomatique, biodiversité, santé des forêts, DFCI, RTM, carbone |
| Appui, sécurité, formation et recherche | 9 | Maintenance, prévention, formation, enseignement, recherche, appui de gestion |

Les exemples mêlent volontairement métiers, fonctions et spécialités, avec un
type explicite dans les fiches. Ils ne représentent pas autant de professions
indépendantes. DFCI désigne ici la prévention ; RTM la restauration des terrains
en montagne.

## Lecture des fiches

- [Catalogue détaillé avec index par famille](CATALOGUE_DETAILLE.md).
- [Tableau CSV filtrable](catalogue.csv), séparateur point-virgule et UTF-8 avec BOM.
- [Référentiel JSON complet](catalogue.json), avec sources, architecture proposée,
  inventaire de l’existant, règles de collecte et points à approfondir.

Une fiche décrit les missions, les synonymes, les étapes du chantier, les données
nécessaires, les livrables, les interlocuteurs, le matériel, le besoin hors ligne,
les conditions de calcul et les limites. Les sources appuient les missions et
la nature des activités. **Les besoins logiciels, champs, associations entre
activités et règles de collecte sont des propositions produit**, à confirmer
avec les utilisateurs et les contrats de données ; les sources ne prescrivent
pas toutes ces fonctions de Géosylva.

Une version Excel à neuf feuilles est produite dans le dossier de livraison :
`/workspace/outputs/2026-11-02/referentiel-metiers/livraison/GeoSylva-referentiel-metiers-2026-11-02.xlsx`.
Elle comprend les activités, familles, spécialités, sources, profils existants,
collecte des données, extensions et grille de choix après FORLOG. Les textes
longs peuvent être lus en agrandissant la ligne ou dans la barre de formule.

## Organisation proposée pour l’application

1. Choisir un thème, ou rechercher directement un terme habituel.
2. Choisir son activité principale dans une liste courte.
3. Préciser, si utile, une spécialité ou un contexte matériel.

La multi-activité pourra être ajoutée depuis le profil. Le premier démarrage ne
doit pas demander de parcourir toutes les fiches. Les synonymes servent à
retrouver un choix ; ils ne doivent jamais attribuer silencieusement un métier.

Le métier, la fonction sur un chantier, l’organisation, le statut, la spécialité
et le contexte matériel sont des dimensions distinctes. ONF, CNPF et DDT restent
des contextes d’organisation. Un propriétaire ou un étudiant n’est pas traité
comme une profession technique. Les rattachements proposés d’une spécialité
peuvent être multiples ; ils ne sont pas des prérequis de qualification.

Un même chantier doit conserver ses données communes, avec des vues adaptées à
l’activité : carte, documents, opérations, observations, réceptions, mouvements
de bois et paramètres. Le constat, la validation interne et la réception
contractuelle restent distincts. Le choix d’une activité ne confère aucun droit
de voir, modifier, affecter, valider ou signer des données.

Exemple : débardeur et conducteur d’engins peuvent désigner la même personne.
Le porteur et le skidder peuvent relever des deux activités ; le câble et la
traction animale ne sont pas forcés sous la conduite d’engins. Ces lectures
ne doivent pas dupliquer opérateur, production ou mouvement de bois.

## Existant à conserver

L’audit statique confirme **12 choix actuels** et **2 familles de parcours**.
Exploitant, Conducteur de travaux et Commerce partagent une grande partie du
socle chantier : dépôts, objets bois, mesures, réceptions, journaux, stocks et
exports. Ces sous-pages existent, même si leurs noms de fonctionnalités fines
ne sont pas tous dans la liste de navigation de premier niveau.

Les 12 codes actuels, le texte libre et le code combiné historique ONF/CNPF
doivent être conservés. Le regroupement futur ne doit pas réinterpréter une
affiliation ou perdre une préférence. La déclaration de métier est distincte
des autorisations sur les données. Missions entreprise reste non raccordé ;
Diagnostics est actuellement présenté dans le parcours Gestion, même si sa
mutualisation entre métiers est une cible du fondateur.

Le parcours Technicien est conservé conformément au cadrage validé. Cette
recherche ne modifie aucun écran, code de métier, permission, calcul, contrat
serveur ou schéma Room. La version du référentiel est indépendante de la version
de l’application, qui reste en 3.0 construction.

## Collecte et calculs à prévoir

Le JSON et l’Excel décrivent onze familles de données : identité/périmètre,
consignes et coordination, mesures, cubage, production, économie, stocks,
géographie/météo, écologie, documents et droits.

Pour chaque famille, il faudra préciser ce qui vient d’une saisie, d’un import,
d’un instrument ou d’un calcul. Les unités, dates, auteurs, sources et versions
doivent accompagner les données. Une donnée absente reste manquante ; elle ne
devient pas zéro. Les conversions stères/m³/tonnes et les méthodes de cubage ne
deviennent pas universelles à partir d’un exemple ou d’un intitulé de métier.

Un GPS ne prouve ni une limite de propriété ni une autorisation de circulation.
Les prescriptions et documents de chantier demandent des responsables identifiés.
Les calculs doivent rester indisponibles si leurs entrées ou leur domaine
d’application ne sont pas qualifiés. Les sources réglementaires orientent
l’étude ; aucun moteur de conformité n’est défini dans ce catalogue.

## Étape suivante : FORLOG, puis choix des premiers parcours

L’archive transmise est identifiée et ses **23 pièces** inventoriées. Leur
contenu n’est pas réanalysé dans cette étape. Les documents historiques de revue
ne sont pas une preuve de nouvelles fonctions implémentées.

La prochaine comparaison portera notamment sur les pièces `02_LOGIQUE_METIER_NATIVE.md`,
`03_AUTOMATISATIONS_METIERS.md`, `04_ARCHITECTURE_ET_CALCULS.md`,
`05_CADRAGE_MAQUETTE.md`, `07_CONTRATS_ET_EXEMPLES_KOTLIN.md` et
`08_RECETTE_ET_PRIORITES.md`. Distinguer les faits observés dans FORLOG, les
propositions de conception et les contrats effectivement vérifiés.

Cinq groupes seront comparés : préparer/piloter un chantier, suivre les travaux
sylvicoles, suivre récolte/débardage, partager les flux jusqu’à la réception,
préparer achats/ventes. La grille contient onze critères à renseigner, sans score
inventé : fréquence terrain, valeur partagée, couverture actuelle, provenance
FORLOG, disponibilité des données, hors ligne, autorisations, calculs, matériel,
périmètre ergonomique et accès à des testeurs.

Le choix du premier groupe sera fait avec le fondateur après cette étude, puis
les pages seront conçues une par une en maquettes. Ce catalogue ne sélectionne
pas encore des écrans à développer.

## Sources et contrôles

Les liens et limites de lecture figurent dans chaque fiche et dans la feuille
Sources. Exemples de références primaires consultées :

- [FNEDT — travaux forestiers](https://www.fnedt.org/les-metiers-travaux-forestiers/).
- [France compétences — RNCP37562, bûcheronnage manuel et débardage](https://www.francecompetences.fr/recherche/rncp/37562/).
- [Onisep — assistance commerciale](https://www.onisep.fr/ressources/univers-metier/metiers/assistant-commercial-assistante-commerciale).
- [Légifrance — organisation des travaux forestiers et sylvicoles](https://www.legifrance.gouv.fr/codes/section_lc/LEGITEXT000006071367/LEGISCTA000023271002/).

Contrôles : identifiants uniques, références et associations valides, champs
obligatoires, types et niveaux de preuve, conservation des codes existants,
absence de modification des sources applicatives auditées, relecture métier et
architecture. Les limites recensées restent visibles dans le référentiel.

Régénération des exports :

```bash
python3 scripts/export_profession_catalogue.py
python3 scripts/export_profession_catalogue.py --output /tmp/geosylva-metiers --xlsx
```

L’option Excel nécessite `openpyxl`. Aucun test Android n’est relancé pour ce lot
documentaire ; les contrôles portent sur le catalogue et les exports.
