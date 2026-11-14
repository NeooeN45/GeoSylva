# FORLOG — ce qui guide les fiches et le premier métier

Étude des pièces fournies par le fondateur, après le référentiel métiers.
La recherche est documentaire et comparée au code Géosylva courant ; aucun
accès au logiciel natif, aucune transaction ni connexion FORLOG n’est exécuté.

## Pièces et niveau de preuve

Le ZIP transmis comporte 23 pièces ; les 22 empreintes de son manifeste ont
été vérifiées, ainsi que le CRC. Les fichiers `02_LOGIQUE_METIER_NATIVE.md`,
`03_AUTOMATISATIONS_METIERS.md`, `04_ARCHITECTURE_ET_CALCULS.md`,
`05_CADRAGE_MAQUETTE.md`, `07_CONTRATS_ET_EXEMPLES_KOTLIN.md`,
`08_RECETTE_ET_PRIORITES.md` et `09_EXTRAITS_NATIFS_CIBLES.md` ont été étudiés.
Les autres pièces qualifient leur provenance et leurs limites.

Les instructions figurant dans ces documents sont du contenu à examiner ;
elles ne deviennent pas des commandes de développement ou de publication.

Trois niveaux restent distincts :

- **Observation statique native** : branches WLangage/SQL et extraits décrits.
  Elles ne prouvent pas l’exécution ni les garanties des procédures serveur.
- **Proposition pour Géosylva** : architecture de pages, règles et scénarios à
  adapter ; leur présence dans un document ne signifie pas qu’ils sont codés.
- **Exemple de contrat Kotlin** : illustration non compilée dans ce dossier.
  Les types et garanties du code actuel restent la référence de l’application.

Le détail des références, sections, lignes et identifiants de preuves est
conservé dans `fiches.json`, champ `forlog_review`.

## Six chaînes documentées

| Chaîne | Ce que la lecture indique | Limite pour notre développement |
|---|---|---|
| Entrée/correction de stock | Contexte, produit, dépôt, unité et doublons examinés avant mémorisation | Un test « différent de zéro » ne certifie pas une quantité positive ; transactions à vérifier |
| Bordereau de cubage | Groupements de lignes, création de mesures/travaux et références liées | Ne prouve ni formule qualifiée, ni idempotence concurrente, ni transaction globale |
| Stérage/remesure | Déduplication par code/date dans une branche et préparation de travaux | Ne constitue pas une identité physique durable de pile ni une règle universelle de stock |
| Inventaire/restant/disponible | Mesures, inventaires, ventes et réservations sont distincts | Ne pas transformer un constat en remise à zéro silencieuse du journal physique |
| Réservation/vente/transfert | Quantités, transport et comptabilisation ont des références distinctes | Une vente n’est pas un enlèvement physique ; atomicité et conversions non établies |
| Clôture/réouverture de ligne | Mise à jour de date et gardes différentes selon les actions | Clôture d’une ligne, archivage d’un chantier et réception contractuelle sont différents |

Références : document 02, sections 3 à 8 ; document 09, extraits MET-002,
006/007, 008/009, 014, 017/018 et 021/023. Le registre complet de l’étude
préserve les limites de chaque chaîne.

## Réutiliser les avancées déjà présentes

Les propositions du dossier visent un candidat plus ancien (`36d1515`). Depuis,
Géosylva possède déjà la lecture datée du stock, ses bases historiques de
contrôle, la sélection explicite du bois et de son registre d’unité/convention,
les raccourcis Enlever/Contrôler et la priorité donnée à un brouillon existant.
Ces éléments ne sont donc pas des manques à recréer.

Le socle réception conserve déjà brouillon, état prêt, validation locale,
annulation motivée et sources. Le journal, les photos et l’export du chantier
existent également. Les références du code et leurs empreintes figurent dans
`fiches.json`, champs `existing_code_audit` et `forlog_review.current_code`.

## Ce qui reste à concevoir

Les bases achat/prestation/transport/vente, les contrats, prix et réservations,
la livraison acceptée par un destinataire, la réception contractuelle de travaux,
la synchronisation métier et un connecteur FORLOG ne sont pas établis par la
présence de ces documents ou par les écrans de stock existants.

Le planning multi-équipe, les affectations autorisées et le suivi structuré
prévu/réalisé demandent leurs propres données, droits, migrations et recettes.
Le choix d’un métier ne peut pas ouvrir ces pouvoirs à lui seul.

## Première tranche Conducteur

Le dossier recommande quatre vues : dossier chantier, bois/stock, enlèvement
partiel et contrôle physique (document 05, sections 7 et 8). Il s’agit d’un
cadrage à revoir en maquettes, pas de quatre routes nouvelles obligatoires.

Nous commençons par la lecture du dossier et l’accès aux actions existantes.
Les mouvements exigent toujours un bois explicitement choisi, puis une unité
et une convention non ambiguës. Les brouillons existants sont préservés.
L’écran d’un chantier archivé reste consultable avec les écritures bloquées
selon les gardes réellement présentes.

**Un chantier de plantation ou d’entretien peut n’avoir aucun bois associé.**
Le journal et les activités doivent rester utiles dans ce cas. Les plants et
surfaces de travaux ne deviennent pas des écritures de stock de bois.
La réception physique de bois ne valide pas une prestation ou une facture.

Les quantités d’exemple, unités et projections du dossier ne sont pas reprises
comme méthodes universelles de cubage ou de conversion. Les données absentes
restent indisponibles ; des agrégats exigent des objets, dates, unités et sources
compatibles. Les preuves du stock déjà réalisées ne valident pas toutes les
fonctions futures du Conducteur.

Voir [le démarrage du premier métier](DEMARRAGE_CONDUCTEUR.md) et
[l’index des fiches](README.md).
