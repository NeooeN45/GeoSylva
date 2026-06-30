---
date: 2026-06-29
updated: 2026-06-29
project: GeoSylva
version: 2.3.0
db_version: 29
tags: [context, project-state]
---

# GeoSylva — État du projet

## Identité

- **Projet** : GeoSylva — app Android de gestion forestière française
- **Fondateur** : Camil (auto-entrepreneur, Poitiers, Nouvelle-Aquitaine)
- **Version** : 2.3.0 (versionCode 9)
- **DB** : version 29
- **Stack** : Kotlin 1.9.23, Compose BOM 2024.09.00, minSdk 26, targetSdk 35

## Architecture

- **Pattern** : MVVM + Repository + Hilt DI
- **DB** : Room (SQLite) — 29 entités, 23 DAOs
- **Maps** : MapLibre GL + WMS IGN (Géoportail)
- **GIS** : Lambert93 (EPSG:2154), WKT, SRTM embarqué
- **Réseau** : Retrofit + OkHttp, APIs IGN/INRAE/Cerema/Open-Meteo

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

**Phase 0 — Blocages production** (priorité absolue, ~14 j-h) :
1. SQLCipher (chiffrement DB — CRITICAL RGPD)
2. `kotlin.incremental=true` (build perf)
3. Documentation RGPD
4. + 9 autres actions (voir MASTER_PLAN §3.2)

## Financement

- **Crédits cloud gratuits** : ~600 000$ (NVIDIA + MS + Google + AWS) — à postuler
- **Potentiel aides** : 1.2M€ - 2.5M€ sur 24 mois (après passage SASU/EURL)
- **Recommandation critique** : passer en SASU/EURL (débloque 80% des aides)
