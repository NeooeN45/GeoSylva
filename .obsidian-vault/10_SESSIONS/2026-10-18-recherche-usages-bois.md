---
date: 2026-10-18
project: GeoSylva
tags: [session, research, wood, ux]
---

# Améliorations des usages métier et des données

Demande : rechercher ce qui rendrait GeoSylva plus utile aux métiers du bois et
améliorer les données recueillies, en respectant la démonstration du mercredi 21.

Lecture des parcours, du contrat BOIS-01, du résolveur de professions et des modèles
de dépôt/relevé. Consultation effective des pages publiques FORLOG, Timbeter,
Fovea/palos, Skogforsk/StanForD, FNB/REGLOBOIS, ONF/CNPEF et CNPF/vente de bois.
Relecture ciblée du PDF CNPEF déjà téléchargé le 8 octobre, §5.3.6–5.4.

Question issue de la recherche : le site FORLOG décrit une saisie mobile.
Réponse initiale du fondateur : « Un outil existant à compléter », puis précision
plus récente : **« Géosylva vas surment remplacer cette outils »**. L'orientation
courante est donc un remplacement probable du mobile existant, avec liaison au
logiciel de gestion FORLOG, sans prétendre qu'une bascule est déjà convenue.
Ne plus présenter GeoSylva comme la première possibilité mobile de FORLOG.
Architecture, interfaces et périmètre exact restent à confirmer ; matrice de
couverture, pilote et reprise sans double import ajoutés au cadrage.

Livrables :
- `docs/AMELIORATIONS_USAGES_METIERS_BOIS_2026-10-18.md` : matrice métier/actions,
  parcours cible, données utiles, calculs et contrôles, priorités et recette future.
- `docs/RECHERCHE_USAGES_BOIS_SOURCES_2026-10-18.json` : références, portée et empreintes.
- Alignement des introductions des anciens plans UX/BOIS-01 et de la mémoire.
- Dossier de preuves locales : `/workspace/.onboarding/usages-bois-2026-10-18/`.

Priorités proposées : finir chantier/liens, produire des exports réels, simplifier
la saisie et qualifier les mouvements datés. Ensuite photos/accès/états avant-après,
temps et matériel, profils opérateur/transporteur. L'IA photo et les imports machine
demandent des évaluations séparées ; aucun chiffre de précision concurrent repris
comme garantie GeoSylva. Les dates seules nécessiteraient une évolution du contrat.

Aucun code applicatif ou dépendance changé ; aucune nouvelle exécution des tests
nécessaire pour cette étude documentaire. Aucun service privé, contact externe,
commit, push ou déploiement. Les recherches Google/Bing non exploitables ne servent
pas de preuves. Les textes externes complets restent hors du dépôt et des packs.
