---
date: 2026-06-29
updated: 2026-07-17
project: GeoSylva
version_metadata: ../../docs/APP_VERSION.md
tags: [context, project-state]
---

# GeoSylva — État du projet

## Identité

- **Projet** : GeoSylva — app Android de gestion forestière française
- **Fondateur** : Camil (auto-entrepreneur, Poitiers, Nouvelle-Aquitaine)
- **Version applicative, versionCode et schéma Room** : voir [métadonnées générées](../../docs/APP_VERSION.md)
- **Stack** : Kotlin 1.9.23, Compose BOM 2024.09.00, minSdk 26, targetSdk 35

## Architecture

- **Pattern** : MVVM + Repository + injection manuelle dans `Application`
- **DB** : Room + SQLCipher — version actuelle dans [métadonnées générées](../../docs/APP_VERSION.md)
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
