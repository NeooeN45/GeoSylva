# Reprise locale avec Claude et Flable 5.2

Le travail porte sur la version de construction **3.0.0** de Géosylva. La cible
de production **3.1** est le **28 février 2027** ; le candidat stabilisé est visé
au 14 février. La première tâche locale est de reproduire le fonctionnement du
commit reçu, puis d'apporter des corrections petites et vérifiables.

## Lire avant de modifier

1. `AGENTS.md`, `CLAUDE.md` et `.devin/AGENT_COORDINATION.md` : règles et revue.
2. `docs/production/ETAT_LIVRAISON_3_1.json` et `docs/recovery/STATUS.txt` : état
   courant et références de validation.
3. Le [guide d'essais locaux](ESSAIS_LOCAUX.md) et le bilan de la livraison reçue.
4. `docs/recovery/WOOD_FIELD_INTEGRATION_BOUNDARIES.json` et
   `docs/recovery/RESOURCE_CONTRACT_REPAIRS.json` : composants préservés mais non intégrés.
5. Les sources des écrans et services concernés ; les anciens plans et comptes
   rendus datés ne prouvent pas à eux seuls leur présence dans le code actuel.

Le [bilan du 15 novembre](VALIDATION_STABILISATION_2026-11-15.md) et son fichier
JSON identifient le lot de stabilisation courant. Le
[suivi des maquettes](SUIVI_INTEGRATION_MAQUETTES.md) distingue les raccords réels
des composants en attente et des validations visuelles manquantes.

Le bilan du **14 novembre 2026** donne un APK debug et **2 083 tests JVM réussis**
sur `33d904901a320cb3439e25a2af4d2efdfebe444a`. Il est conservé dans
`docs/recovery/APPLICATION_VALIDATION_2026-11-14.json`. Ne pas réattribuer ce résultat
à une autre révision ni le présenter comme un essai Windows, S25 Ultra ou une
validation visuelle des 100 maquettes.

## Repères dans le code

| Besoin | Point d'entrée |
| --- | --- |
| Assemblage des dépendances et arrêt des travaux avant purge | `app/src/main/java/com/forestry/counter/ForestryCounterApplication.kt` |
| Parcours, contextes et choix des cartes | `app/src/main/java/com/forestry/counter/presentation/navigation/` |
| Carte commune et raccords des outils | `app/src/main/java/com/forestry/counter/presentation/screens/forestry/MapRechercheScreen.kt` |
| Moteur Mapbox et projections | `cartography-mapbox/` |
| Présentation commune des panneaux | `cartography-ui/` |
| Autorité, propriétaires et mutations des données cartographiques | `app/src/main/java/com/forestry/counter/data/cartography/` |
| Base Room 49, DAO, entités et migrations | `app/src/main/java/com/forestry/counter/data/local/` et `app/schemas/` |
| Synthèse et calculs forestiers | `app/src/main/java/com/forestry/counter/domain/calculation/` |
| Qualification des volumes des exports | `app/src/main/java/com/forestry/counter/domain/usecase/export/ObservationExportCalculator.kt` |
| Chantiers, carnet, dépôts et bois | `app/src/main/java/com/forestry/counter/presentation/screens/field/` et `wood/` |

La carte, ses panneaux et ses services sont communs aux métiers. Conserver cette
réutilisation en faisant varier le contexte et les données autorisées. Un choix
de métier ne confère pas un droit de lecture ou d'écriture.

## Méthode de correction et de nettoyage

- Commencer par `git status --short` et `git log -1 --oneline`, puis établir une
  référence avec build debug, tests JVM et Lint selon `ESSAIS_LOCAUX.md`. Noter les
  erreurs ou avertissements existants avant d'attribuer une régression à un changement.
- Travailler sur une branche dédiée. Conserver les travaux locaux avant une mise
  à jour ; aucun reset forcé, effacement des données Android ou réécriture d'historique
  ne fait partie de ce guide.
- Traiter un problème ou une famille de fichiers à la fois. Pour retirer un code
  supposé mort, vérifier ses appels, ses tests et son rôle dans les contrats avant
  suppression. Conserver les provenances et les sources en attente de raccordement.
- Préserver le parcours Technicien public/privé hors des améliorations convenues
  de Synthèse et Diagnostics. Les 100 maquettes sont des références à reproduire ;
  une compilation ne vaut pas une comparaison de captures sur appareil.
- Ne pas remplacer les garde-fous scientifiques par un coefficient, une hauteur,
  une essence ou une valeur de substitution. Une méthode non qualifiée laisse le
  résultat indisponible et explique ce qui manque, dans les écrans comme les exports.
- Une évolution de Room doit préserver les données et l'historique ; fournir la
  migration et ses cas de reprise. Le contrôle SQLite hôte ne suffit pas à certifier
  une migration chiffrée Android.
- Ne pas supprimer, ignorer ou affaiblir une assertion pour obtenir un passage
  vert. Corriger la cause ou documenter le contrat manquant sans prétendre la fonction livrée.
- Exécuter les vérifications adaptées au diff. Pour un lot applicatif : build,
  tests JVM et Lint ; pour une modification d'interface : recette du parcours et
  captures. Pour GPS, caméra, permissions ou hors connexion : essais sur le S25 Ultra.
- Faire des commits Conventional Commits courts, avec le comportement corrigé et
  les résultats de validation. Vérifier le diff et les fichiers ignorés ; aucune
  clé, base utilisateur, configuration personnelle, APK ou sortie de build dans le code source.

Le mode Maps complet, les deux présentations stock/carnet en attente, le visuel 21
et les essais physiques restent des chantiers explicites. Ne pas les activer avec
des interfaces factices ou des résultats de stock inventés pour fermer une erreur
de compilation. Les [limites du guide local](ESSAIS_LOCAUX.md#limites-à-garder-visibles)
et l'état courant permettent de suivre leur résolution.

## Bilan de session attendu

Indiquer le commit de départ et celui testé, le problème reproduit, les fichiers
modifiés, le comportement obtenu, les commandes exécutées et leurs résultats.
Distinguer les essais locaux exécutés, les essais matériels et ce qui reste à
vérifier. Mettre à jour les consignes ou l'état du projet quand le comportement
change ; ne pas transformer un ancien bilan en preuve d'un nouveau passage.

## Sauvegarde obligatoire à la fin de chaque session

Règle demandée par le Fondateur le 15 novembre 2026 : **à la fin de chaque
session, pousser le travail sur une branche distante datée**. Un commit ou
un bundle conservé uniquement dans l'environnement de travail ne suffit pas.

1. Examiner `git status --short`, y compris les fichiers applicatifs non suivis,
   et enregistrer le travail dans des commits sur une branche dédiée. Un lot
   incomplet peut être sauvegardé, avec son état de validation indiqué ; cela
   ne le rend pas livrable. Les secrets, configurations personnelles, bases
   utilisateur, APK et sorties de build restent hors du dépôt source.
2. Vérifier l'URL du distant GitHub avec `git remote -v`. Dans le workspace de
   récupération, `origin` est une copie locale et le distant GitHub s'appelle
   `github` ; dans un clone normal, son nom peut être `origin`.
3. Créer une référence de sauvegarde unique, par exemple
   `backup/geosylva-AAAA-MM-JJ-HHMMSSZ`, horodatée en UTC. Pousser explicitement
   `HEAD` vers cette référence distante, sans `--force`, sans réécriture
   d'historique et sans réutilisation destructive d'une ancienne sauvegarde.
4. Relire la référence distante avec `git ls-remote` et vérifier que son SHA
   correspond à `git rev-parse HEAD`. Inscrire dans le bilan de session le nom
   de branche, l'URL, le SHA et les vérifications réussies ou encore en échec.
5. Si le push est refusé ou impossible, produire un bundle local comme recours,
   consigner le blocage et signaler explicitement que la sauvegarde distante
   n'est pas réalisée. Ne pas présenter cette situation comme protégée hors
   de l'environnement.

Cette instruction autorise la sauvegarde sur une branche datée en fin de
session. **Les changements destinés à `main` passent par une demande de fusion :
aucun push direct ni contournement de sa protection.** Une sauvegarde ne vaut
ni fusion, ni acceptation de la CI, ni mise en production. Les messages à
d'autres personnes et les déploiements restent soumis à leurs autorisations.
