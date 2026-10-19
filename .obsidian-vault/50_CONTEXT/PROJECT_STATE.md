---
date: 2026-06-29
updated: 2026-10-19
project: GeoSylva
version: 3.0.0
db_version: 46
tags: [context, project-state]
---

# GeoSylva — État du projet

## Réceptions, stocks et exports — 19 octobre 2026

Cette section actualise les bilans historiques ci-dessous. Room 46 ajoute quatre
tables de réception. Les brouillons, contrôles et validations locales sont durables ;
rejouer une validation ou remesurer un bois ne crée pas un second crédit de stock.
Les enlèvements et constats datés réutilisent l'unité/convention versionnée du stock.
Le ZIP GeoSylva comprend un instantané JSON, cinq CSV, une notice et un manifeste
d'empreintes, vérifiés avant enregistrement Android. Les photos sont référencées.

1 822 tests JVM et 62 tests Android distincts réussis ; Lint sans erreur,
997 avertissements documentés. Le correctif SQLCipher et sa reprise non destructive
font partie des tests Android. Voir `docs/VALIDATION_RECEPTIONS_STOCK_EXPORTS_2026-10-19.md`
et `docs/RECETTE_RECEPTIONS_TELEPHONE_2026-10-19.md` à la racine du dépôt.
L'APK TEST utilise `com.forestry.counter.preview.receptions`, distinct des anciennes
versions. Il n'active ni Google/GSIE ni FORLOG ; Missions reste Entreprise.

Le 19 octobre, GitHub indique encore le dépôt public. Ne pas pousser ces nouvelles
sources avant une mise en privé vérifiée. La sauvegarde locale comprend un commit,
un bundle et les preuves de livraison sous `/workspace/outputs/2026-10-19/receipts/`.
REA 6.7.0 est installé et son transport MCP testé ; le rechargement du client reste
nécessaire pour exposer ses outils nativement. Aucune procédure FORLOG décompilée.

## Qualification FORLOG — 19 octobre 2026

Les deux archives reçues ont été contrôlées : CRC valides et 128 empreintes de
livrables concordantes. Le dossier comporte 32 fiches d'actions et 108 références
de preuve. Les schémas reçus confirment leurs comptages ; les originaux FORLOG de
2,9 Go ne sont pas disponibles ici. Les 18 composants métier natifs prioritaires
restent binaires : aucune équivalence de calcul, stock ou droit FORLOG n'est certifiée.

La [qualification](../../docs/QUALIFICATION_FORLOG_2026-10-19.md) compare les
propositions au code local sauvegardé le 18 octobre. Identités bois, observations,
photos, chantiers multi-parcelles, archives et carnet physique existent déjà.
Le [prochain lot](../../docs/PLAN_RECEPTION_STOCK_EXPORTS_2026-10-19.md) prépare
réception métier, lien sans double crédit avec le stock, exports dédiés et recette.
L'usage personnel hors ligne et les calculs géométriques existants sont conservés ;
Missions reste réservé à Entreprise et n'est pas activé sans accès serveur fiable.
Les codecs locaux ne sont pas remplacés par la sérialisation de l'audit.

Cette tranche est documentaire : aucun écran, calcul, schéma Room ou connecteur
modifié. Les sources/extraits client restent hors dépôt. La sauvegarde métier du
18 octobre est désormais le commit local `576a256b7bfa22e2bbd23571b78c076bf78ee43f`,
non poussé ; cette précision actualise les mentions « sans commit » des comptes
rendus historiques. Le passage en privé reste non vérifié depuis le refus 403.

Vérification du socle existant : 42 tests JVM ciblés réussis (soldes physiques,
historique des observations, cas de référence de cubage), avec compilation debug.
Pas de nouvelle recette UI, d'APK livré ou de validation de connecteur dans cette revue.

## Sécurisation du dépôt — 18 octobre 2026

Socle CI de sécurité, fichiers sensibles contrôlés, fixtures publiques vérifiées
par empreinte et procédure de contribution actualisée. Aucun changement des
calculs, de Room ou des écrans dans ce lot. Le fondateur a confirmé la mise en
privé ; l’API GitHub renvoie 403 avec le connecteur actuel, donc aucune
confidentialité nouvelle n’est annoncée. Les sources métier restent sauvegardées
séparément et ne doivent pas être publiées avant cette bascule.


## Carnet terrain partagé et chantiers — 18 octobre 2026

Le fondateur autorise l’implémentation des améliorations proposées et leur application
pertinente à Gestion forestière. Le [lot commun](../../docs/CARNET_TERRAIN_CHANTIERS_2026-10-18.md)
ajoute les carnets forêt/parcelle/placette et chantier/dépôt/bois, photos originales,
accès/consignes, états avant/après, temps/activité/matériel, qualité bois, brouillons,
saisie de grumes en série, recherche, archives de chantier et liens parcellaires multiples.
Les événements physiques sont distincts du cubage et des quantités commerciales.
Room 45 est additive, avec purge et contrôles de compte. Les Missions restent Entreprise.

Compilation et Lint réussis (zéro erreur ; 983 avertissements documentés),
**1 788 tests JVM et 37 tests Android distincts réussis** sur plusieurs passages ciblés.
Voir [la validation](../../docs/VALIDATION_CARNET_CHANTIERS_2026-10-18.md) pour les
incidents corrigés, les limites et les preuves. APK TEST, captures, guide d’essai et
archive/manifeste dans `/workspace/outputs/2026-10-18/field-tools/`.
La sauvegarde est locale, sans commit/push GitHub ; les archives antérieures sont conservées.
Les associations ne créent ni géométrie ni droit d’accès. Pas de connecteur FORLOG,
de paiement, de réception contractuelle ou d’estimation photo prétendument opérationnel.

Les comptes rendus ci-dessous conservent l’état historique de chaque lot. Le présent
complément fait foi pour les fonctionnalités désormais raccordées (notamment brouillons,
photos, chantiers et mouvements physiques).

## Recherche d'usage et complémentarité FORLOG — 18 octobre 2026

Le fondateur confirme que **FORLOG possède un outil mobile existant**, puis précise
que **GeoSylva le remplacera probablement**. Cette dernière orientation remplace
sa première réponse « à compléter » et l'ancienne hypothèse d'absence de mobile.
Le remplacement n'est pas encore une bascule convenue avec le partenaire et ne
concerne pas l'ensemble du logiciel de gestion FORLOG. Architecture, périmètre client
et API restent non confirmés. Préparer une matrice de couverture des tâches actuelles,
un pilote, la reprise des références/données et l'absence de doubles imports.
GeoSylva reste autonome ; les autorités et droits d'écriture seront explicites par donnée.

La [recherche d'améliorations métier](../../docs/AMELIORATIONS_USAGES_METIERS_BOIS_2026-10-18.md)
croise sept pages publiques consultées et une relecture ciblée du CNPEF archivé.
Elle propose saisie en série, brouillons durables, recherche par repère, journal
de pile, accès/consignes, photos et états des lieux, puis temps/matériel et imports
machine. Dictionnaire de données, priorités et critères de recette documentés.
Il s'agit de propositions, pas de code livré ni d'une promesse pour mercredi.
Les profils bûcheron, débardeur/câbliste et chauffeur grumier restent à développer.
Missions demeure Entreprise. Aucun changement du binaire ou des tests par cette étude.

## Formulaires bois vérifiés — 18 octobre 2026

Ce complément remplace le « prochaine étape C02/C03 » des notes historiques
ci-dessous. Lire [le bilan du lot](../../docs/FORMULAIRES_BOIS_C02_C03_2026-10-18.md).

Le parcours **Dépôt → Piles et grumes** permet création/modification d'identité,
relevés manuels même incomplets, relecture, historique, correction motivée et choix
explicite d'un relevé de référence. Les calculs individuels réutilisent les noyaux
réels : enveloppe apparente pour les piles, Huber/Smalian/Newton pour les grumes.
Les conventions et protocoles sont explicites. Remesurer ne crée pas du stock ; une
correction conserve l'original et ne remplace pas silencieusement la référence.

Room 44, migration additive 43 → 44, quatre tables et commandes idempotentes.
Garde d'accès commune aux dépôts et bois, révision contrôlée, session/compte contrôlés,
purge protégée jusqu'au nettoyage des fichiers. Les données enregistrées survivent
à la fermeture ; les brouillons non enregistrés ne sont pas autosauvegardés sur disque.
La migration et les relevés/choix/purge passent aussi dans une base SQLCipher de TEST.

Compilation réussie, **1 776 tests JVM et 36 tests Android distincts réussis** sur
plusieurs passages ciblés. Les quatre tests de formulaire passent ensemble dans
le dernier passage UI. Les incidents initiaux et leur traitement sont documentés.
Les 25 anciens schémas Room de l'archive C01 sont inchangés. Captures Android,
journaux, résultats et archive avec manifeste sous
`/workspace/outputs/2026-10-18/wood-forms/`. Sauvegarde locale uniquement ; aucun
commit/push GitHub de ce lot. Les sauvegardes des lots précédents sont conservées.

**Décision du fondateur à appliquer ensuite** : partir du chantier, lié à une ou
plusieurs parcelles ONF, cadastrales ou d'un autre référentiel ; rattacher les bois
explicitement ; saisir les passages partiels du grumier et les contrôles du restant
par date. Le [modèle détaillé](../../docs/CHANTIERS_PARCELLES_SUIVI_DATE_BOIS_2026-10-18.md)
distingue un enlèvement validé d'un constat de restant et d'une remesure.
Ces associations et mouvements ne sont pas encore implémentés. Missions demeure
réservé à Entreprise. Les réceptions commerciales, stocks, exports bois, photos et
synchronisation GSIE/FORLOG restent à raccorder ; aucune configuration Google faite.

## Complément du 18 octobre — prospection, photos et contrats bois

Le fondateur ajoute prospection de chantier, estimation des bois et photos des
piles/grumes ; étudier ensuite le cubage photographique, sans changer l’ordre.
Voir [le cadrage](../../docs/PROSPECTION_ESTIMATION_PHOTOS_BOIS_2026-10-18.md).

Les [contrats C01](../../docs/CONTRATS_BOIS_C01_2026-10-18.md) sont enrichis :
modèles immuables d’identité et de relevé, six intentions de commande, JSON local
v1 exact et SHA-256 de tout le contenu, validation stricte à la relecture.
Longueur de pièce conservée sans substitution à la profondeur de pile.
115 tests ciblés passent (sept suites, dont 15 nouveaux tests). Aucune migration,
écriture Room, photographie ou estimation par image n’est encore raccordée par
ce lot. Prochaine étape : C02, garde d’accès/purge, commandes/réponses persistées
dans une transaction, puis C03/C04 et exports. Le détail de navigation ci-dessous
reste valable ; son ancien « compléter C01 » est précisé par ce complément.

Preuves et nouvelle archive locale : `/workspace/outputs/2026-10-18/wood-contracts/`.
La sauvegarde de navigation du même jour reste conservée séparément. Aucun
commit/push GitHub effectué.

## Développement du 18 octobre — navigation métier

Lire [les diagrammes et le plan des trois jours](../../docs/DIAGRAMMES_DEMONSTRATION_21_OCTOBRE_2026.md).
Le nouvel accueil Chantiers et bois donne accès aux dépôts, à la carte et aux packs.
Explorer filtre les outils raccordés selon le parcours. Les raccourcis Missions,
y compris le mini-menu et la visite guidée, sont retirés tant que module et droits
Entreprise ne sont pas raccordés. La route restaurée Missions ne rend plus son
ancienne page d’attente. La route dépôt vérifie également sa présence dans le parcours.
Aucun droit de données n’est déduit du métier ; les repositories gardent leurs contrôles.
Le faux rafraîchissement et les catégories non raccordées sont retirés d’Explorer.
Base Room43 inchangée (empreinte comparée au manifeste du 15). Compilation réussie,
1 754 tests JVM et huit tests Android distincts réussis après reprise. Les cinq
nouveaux tests d’entrée et la création/relecture de dépôt passent dans le dernier
run ; les deux autres tests dépôt étaient verts au premier. Voir la
[validation du 18](../../docs/VALIDATION_PARCOURS_2026-10-18.md) pour les deux
incidents initiaux et les limites. Captures finales sous
`/workspace/outputs/2026-10-18/android/`. Sauvegarde de sources locale avec manifeste,
sans commit/push GitHub du travail repris et de ce lot.

Prochaine tranche : compléter C01 (bruts/contrat/empreinte), C02 (objets et
observations persistés, droits et transactions), puis C03/C04 (formulaires et
calculs) et exports. Stocks, prix et quantités payables restent des modèles distincts.

## Reprise vérifiée du 15 octobre 2026

Lire d’abord [le bilan de récupération](../../docs/REPRISE_15_OCTOBRE_2026.md) et
[les priorités pour mercredi 21 octobre](../../docs/PRIORITES_DEMONSTRATION_21_OCTOBRE_2026.md).
Les conversations sont de nouveau lisibles. Tous les fichiers de production
manquants du manifeste du 10 sont récupérés à SHA identique ; synthèse du 12,
dépôts de bois et migration 43 du 13 sont réintégrés. Deux tests JVM incomplets
ont été reconstitués et revus, sans revendiquer leur ancienne empreinte.
La compilation Android complète et celle de la variante TEST passent ;
1 745 tests JVM passent, dont les 106 tests des noyaux isolés.
15 tests Android ciblés de migration, repository et UI sont réussis après
reprise d’un incident System UI du simulateur. Voir
[la preuve de validation](../../docs/VALIDATION_REPRISE_2026-10-15.md). Les notes plus anciennes ci-dessous décrivent leur date, pas un état
actuel de production. La version du code restauré est 3.0.0, base Room 43.

## Identité

- **Projet** : GeoSylva — app Android de gestion forestière française
- **Fondateur** : Camil (auto-entrepreneur, Poitiers, Nouvelle-Aquitaine)
- **Version** : 3.0.0 (versionCode 11)
- **DB** : version 44
- **Stack** : Kotlin 1.9.23, Compose BOM 2024.09.00, minSdk 26, targetSdk 35

## Architecture

- **Pattern** : MVVM + Repository + injection manuelle dans `Application`
- **DB** : Room + SQLCipher — version 44
- **Maps** : MapLibre GL + WMS IGN (Géoportail)
- **GIS** : Lambert93 (EPSG:2154), WKT, SRTM embarqué
- **Réseau** : OkHttp durci + Retrofit pour l’identité GSIE, APIs IGN/INRAE/Cerema/Open-Meteo

## Identité Quintessences — 2026-08-03

- écrans Compose de connexion et de gestion du compte ;
- profil modifiable, vérification e-mail et récupération de mot de passe ;
- fournisseurs local, Google et professionnel en développement ;
- Google Credential Manager avec nonce serveur ;
- jetons GSIE conservés dans un coffre chiffré ;
- mode développeur après huit pressions sur la version ;
- diagnostic API, identité, build et appareil strictement en lecture seule ;
- aucune fonction forestière locale bloquée en l’absence de compte.
- 513 tests unitaires verts, Lint sans erreur bloquante et APK vérifié sur
  émulateur contre l'API locale.
- protocole public préparé : HTTPS + JWT GSIE derrière Cloudflare Tunnel ;
- aucun token Cloudflare Access, certificat mTLS ou token de tunnel dans
  l'APK ; activation réelle en attente du domaine et du secret externe.

## État des audits (2026-06-29)

- **224 issues** identifiées (40 CRITICAL, 58 HIGH, 80 MEDIUM, 46 LOW)
- **Phase 0** : 12 actions bloquantes (~14 j-h) — **pas encore commencée**
- Voir `MASTER_PLAN.md` §3.2 pour détail

## Documents de référence

| Document | Rôle |
|----------|------|
| `MASTER_PLAN.md` | Vision + plan + écosystème (source de vérité) |
| `AI_CONTEXT.md` | Contexte technique du code |
| `RESEARCH_OPPORTUNITIES.md` | 150+ opportunités (APIs, IA, financement, hardware) |
| `AUDIT_FORESTIER_COMPLET.md` | Audit vague 1 (101 issues) |
| `AUDIT_GLOBAL_GEOSYLVA.md` | Audit vague 2 (123 issues) |
| `.obsidian-vault/30_RESEARCH/2026-07-17_audit-fiabilite-donnees-dendrometriques.md` | Audit fiabilité données dendrométriques (5 subagents, 60+ sources) |
| `.obsidian-vault/30_RESEARCH/2026-07-17_audit-approfondi-algorithmique-moteurs.md` | Audit approfondi algorithmique moteurs (4 subagents, 12 fichiers Kotlin, 5 faiblesses structurelles) |

## Système de skills

### Skills globales (`~/.config/devin/skills/`)
- `/brainstorm [sujet]` — brainstorming structuré (6 techniques)
- `/multi-agent [tâche]` — orchestration multi-IA (Devin/Claude/GPT/GLM/Kimi/Gemini)
- `/memory-sync [action]` — mémoire persistante (MCP + Obsidian)

### Skills projet (`.devin/skills/`)
- `/forest-crew [agent] [tâche]` — 7 agents spécialisés GeoSylva

## Système de mémoire

- **MCP `memory`** : knowledge graph (entités, relations, décisions)
- **Obsidian vault** : `.obsidian-vault/` (9 dossiers, templates, session logs)
- **Handoffs** : `.devin/handoffs/` (prompts multi-agent + résultats)

## Phase actuelle

**Phase d’intégration GSIE, sans abandon de l’offline-first** : le cycle
d’identité mobile local est livré. Les prochaines tranches sont la
activation Cloudflare/OAuth/SMTP publique, le centre de comptes web, puis la
synchronisation métier versionnée avec reprise réseau.

## Audit moteurs internes (2026-07-17)

Deux audits complémentaires produits dans `.obsidian-vault/30_RESEARCH/` :

1. **Audit fiabilité données dendrométriques** (5 subagents, 60+ sources) :
   - 3 problèmes critiques de sourcing (Algan non sourcé, Schumacher-Hall référence inexistante, incohérence unités densité)
   - Sources canoniques recommandées : XyloDensMap, GCubeR/EMERGE, FBF/ONF/CEEB, IPCC 2019, IGN IFN
   - Plan d'action P0-P3 priorisé

2. **Audit approfondi algorithmique moteurs** (4 subagents, 12 fichiers Kotlin, ~3000 lignes de tests) :
   - 5 faiblesses structurelles majeures identifiées :
     - **S1** ForestryCalculator God object (760 lignes, 7 responsabilités) — violation SRP
     - **S2** Pas de pattern Strategy (ajout méthode cubage = 5 fichiers à modifier) — violation OCP
     - **S3** Composition multiplicative aveugle ProPricingEngine (8 coef., amplitude 592×, pas de garde-fou)
     - **S4** Facteurs de prix structurels absents (diamètre, volume unitaire, conjoncture marché)
     - **S5** Coefficients hardcodés (pas de repository, pas de cache, pas de sync GSIE, pas de versioning)
   - Refonte proposée : pattern Strategy étendu (15-20 méthodes cubage), modulateurs bornés (remplacer multiplicative aveugle), repository coefficients (cache offline + sync GSIE canal 1), injection Hilt, facade ForestryOrchestrator
   - Migration incrémentale en 6 phases (feature flags, tests de régression, rollback)
   - Plan de refonte P0-P3 priorisé (38 actions)

Statut : **Draft** — à valider par le Fondateur avant refonte du code Kotlin.

## Financement

- **Crédits cloud gratuits** : ~600 000$ (NVIDIA + MS + Google + AWS) — à postuler
- **Potentiel aides** : 1.2M€ - 2.5M€ sur 24 mois (après passage SASU/EURL)
- **Recommandation critique** : passer en SASU/EURL (débloque 80% des aides)
