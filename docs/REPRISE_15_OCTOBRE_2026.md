# Reprise GeoSylva — 15 octobre 2026

## État établi après réapparition des sessions

L’objectif reste une démonstration le mercredi **21 octobre 2026**, centrée sur la réception du bois, les stocks et les exports ; le connecteur FORLOG viendra ensuite. Les décisions explicites récentes de l’utilisateur priment sur la documentation ancienne.

Les outils de lecture des conversations fonctionnent désormais depuis cette session. Les journaux ont permis de récupérer les sources qui n’étaient pas présentes dans GitHub. Le diagnostic initial de 38 versions manquantes est donc **dépassé** : tous les fichiers de production manquants du manifeste du 10 octobre ont été retrouvés avec leur SHA-256 historique. Les dernières sources de synthèse du 12 octobre, les dépôts de bois du 13, la migration Room 42→43 et la suppression du Tableau de bord abandonné ont été réintégrés. Les deux documents complets BOIS-01 et plan de codage du 14 octobre sont aussi restaurés.

La base Git reste `c59ba7cccd3b5a69ae81e5007bb7d5f263aeacb7`. Les fichiers restaurés sont des modifications locales sauvegardées séparément avant chaque intégration. Une récupération de source n’est pas une affirmation que toutes les fonctions prévues étaient déjà codées.

Sur les **1 129 entrées du manifeste historique**, **1 127 versions exactes** ont été retrouvées ; les deux restantes sont des tests JVM reconstitués.

Deux tests JVM, `ObservationQualificationEditorControllerTest` et `ObservationQualificationFormStateTest`, ont été **reconstitués**, et non retrouvés à l’identique. Ils conservent les 55 scénarios et les assertions historiques disponibles ; les portions manquantes sont documentées et ont reçu une revue indépendante. Leurs nouvelles empreintes restent distinctes des empreintes historiques.

## Qualification et nouveaux noyaux

- Compilation complète `:app:assembleDebug` réussie sur le code Android restauré.
- Simulateur démarré sans effacement de ses anciennes données.
- **106 tests JVM isolés réussis**, sans échec, erreur ni test ignoré, sur les vraies sources des nouveaux noyaux et du calcul de réception récupéré.
- **1 745 tests JVM de l’application complète réussis**, sans échec, erreur ni test ignoré. Les 106 tests isolés sont inclus dans ce total, pas ajoutés.
- Variante Android séparée `com.forestry.counter.preview`, version `3.0.0-test`, compilée et installée ; APK des tests Android compilé. URL GSIE et configuration Google volontairement absentes de cette recette locale.
- **15 tests Android ciblés réussis** : migration 42→43 (1), repository des dépôts (11), interface (3). Deux premiers échecs UI venaient d’une fenêtre ANR de System UI ; après fermeture de cette fenêtre, les trois scénarios UI ont été relancés et ont réussi sans changement de code.
- Les tests Room utilisent des bases TEST et ne valident pas à eux seuls la migration chiffrée SQLCipher. Ils ne certifient ni Google ni un serveur GSIE opérationnel.
- [Preuve détaillée de validation](VALIDATION_REPRISE_2026-10-15.md), APK et captures disponibles dans `/workspace/outputs/2026-10-15/`.

Les modules présents sont :

| Module | Fonction | Vérification isolée |
| --- | --- | --- |
| `domain/calculation/reception` | Huber, Smalian, Newton, volume apparent d’une pile rectangulaire | 28 tests |
| `domain/wood` : mesures brutes | Identité physique, mesures, unités, provenance et diagnostics explicites | 28 tests |
| `domain/wood` : historique | Nouvelle mesure indépendante, correction conservant l’original, sélection explicite du relevé courant | 26 tests |
| `domain/journey` | Parcours métier et accès aux fonctions ; Missions Entreprise sous conditions cumulatives | 24 tests |

L’historique est une projection métier immuable, pas encore une persistance Room. Corriger une observation ne choisit pas silencieusement sa nouvelle version. Remesurer ne crée aucun mouvement de stock ni quantité payable. Les transactions, contrôles d’accès, auteurs et reçus d’idempotence durables restent à intégrer. La politique de parcours attend des preuves de droits fournies par un adaptateur de confiance et n’est pas encore branchée à toute la navigation.

Lire [les priorités de la démonstration](PRIORITES_DEMONSTRATION_21_OCTOBRE_2026.md), [le plan BOIS](PLAN_CODAGE_BOIS_2026-10-14.md) et [la spécification BOIS-01](SPEC_BOIS_01_PRET_A_CODER_2026-10-14.md).

## Décisions à préserver pendant la reconstruction

1. **Gestion forestière** : forêt → parcelle → placette → relevés inventaire/martelage/combiné → synthèses. Chaque placette conserve ses relevés datés et leurs versions.
2. **Chantier et valorisation** : dossier chantier → accès, zones et dépôts → piles/grumes → observations et réception → mouvements → exports et bases commerciales. Ne pas transformer artificiellement une placette en dépôt.
3. Les outils proposés dans Explorer, l'accueil, les menus et les liens directs suivent le métier et les droits réels. Missions est réservée à l'offre Entreprise et aux personnes habilitées.
4. Archiver masque les dossiers des listes actives en conservant leurs données, documents et historique. Restaurer un dossier ne redémarre pas automatiquement les travaux.
5. Objet physique, observations, bases commerciales et mouvements de stock sont distincts. Une nouvelle mesure du même bois ne crée pas une nouvelle entrée de stock. Une mesure peut servir à plusieurs bases commerciales, avec leurs unités, conventions, justifications et validations propres.
6. Sans convention commerciale applicable, un calcul géométrique peut être présenté comme provisoire ; il ne devient pas une quantité payable. Prix, coûts ou résultats inconnus restent inconnus.
7. Les données simulées sont autorisées pour une version TEST explicitement identifiée, isolée de la production. Elles ne doivent pas fabriquer de droits, de synchronisation réussie ou de comptes entreprise.
8. Conserver les symboles métier demandés ; retirer l'ancien Tableau de bord sans supprimer la nouvelle Synthèse. Les changements visuels importants devront être montrés sur Android.

## Ordre de reprise

1. Préserver les archives originales et reconstituer une base Android cohérente, avec compilation et vérification des migrations et données existantes.
2. Retrouver ou reconstruire les dépôts, profils bois et protections de compte/purge ; rétablir les corrections de synthèse avant tout rapport.
3. Brancher les mesures brutes et leur versionnement à la persistance et aux pages de réception.
4. Ajouter les mouvements explicites et exports vérifiés, puis la valorisation avec ses bases séparées.
5. Recetter les deux parcours sur Android avec le jeu TEST et produire l'APK après cette validation.

## Pièces de récupération

Les premiers constats et archives partielles restent dans `/workspace/.onboarding/recovery-2026-10-15/` à titre historique. Ils ne doivent plus être lus comme l’état actuel.

La récupération depuis les conversations et ses preuves se trouvent dans `/workspace/.onboarding/history-recovery-2026-10-15/` :

- `base10`, `numeric10`, `ui10`, `fixture10` : sources retrouvées et comparées aux empreintes historiques ;
- `synthesis`, `depot` : sources des 12–13 octobre et preuves de reconstruction par contexte exact des patchs ;
- `test10/RECONSTRUCTION_PROOF.json` et `RECONSTRUCTION_REVIEW.md` : distinction des deux tests réécrits ;
- `integration/BASE_RESTORE_PROOF.json`, `INTEGRATION_PROOF.json` et archives `BEFORE_*.zip` : modifications intégrées et sauvegardes préalables ;
- `integration/assemble-debug.log` : compilation Android réussie ;
- `specs` : documents complets et fragments d’annexes explicitement séparés. Les fragments ne sont pas publiés comme des spécifications complètes.

Les archives locales ne constituent pas une sauvegarde distante. Une copie historique ne doit jamais remplacer automatiquement un état plus récent retrouvé ultérieurement. Aucun historique Git n’a été réécrit, aucune donnée utilisateur ni aucun compte n’a été effacé pendant la reprise.
