# AGENTS.md — Système multi-agent GeoSylva

**Document de référence pour l'environnement de travail IA**
**Date** : 2026-06-29
**Projet** : GeoSylva

---

## Vue d'ensemble

Ce document décrit le système complet mis en place pour :
1. **Brainstorming structuré** — méthodologie de divergence/convergence
2. **Multi-agent orchestration** — délégation entre outils IA (Devin, Claude, GPT, GLM, Kimi, Gemini)
3. **Mémoire persistante cross-tool** — knowledge graph (MCP) + vault Obsidian
4. **Crew d'agents spécialisés foresterie** — 7 experts domaines pour GeoSylva

---

## 1. Skills disponibles

### Skills globales (`~/.config/devin/skills/`)

Disponibles dans **tous les projets**.

| Skill | Commande | Description |
|-------|----------|-------------|
| **brainstorm** | `/brainstorm [sujet]` | Brainstorming structuré : 6 techniques (First Principles, SCAMPER, Anti-Solution, Cross-Pollination, Contrainte Extrême, Perspective Shift). Génère 15+ idées, clusterise, recommande top 3. |
| **multi-agent** | `/multi-agent [tâche]` | Orchestration multi-IA. Génère des handoff prompts prêts à coller pour Devin/Claude/GPT/GLM/Kimi/Gemini. 4 modes : handoff simple, fan-out parallèle, list, intégration résultat. |
| **memory-sync** | `/memory-sync [action]` | Mémoire persistante. 2 layers : MCP memory (knowledge graph) + Obsidian vault (Markdown). Actions : recall, save, search, sync, status. |

### Skills projet (`.devin/skills/`)

Spécifiques à **GeoSylva**.

| Skill | Commande | Description |
|-------|----------|-------------|
| **forest-crew** | `/forest-crew [agent] [tâche]` | Crew de 7 agents spécialisés : Dendromètre (foresterie), Cartographe (GIS), Sentinel (RGPD), SylvIA (IA forestière), Bûcheron (business), Ingénieur (Android), Capteur (hardware IoT). |

### Skills existantes (compatibles, déjà installées)

Ces skills continuent de fonctionner :

| Skill | Source | Description |
|-------|--------|-------------|
| model-economy | `.claude/skills/` | Routage vers l'exécuteur le moins cher |
| composition-patterns | `.claude/skills/` | Patterns de composition React |
| react-best-practices | `.claude/skills/` | Performance React/Next.js |
| web-design-guidelines | `.claude/skills/` | Review UI/accessibilité |
| skill-architecture | `.codeium/windsurf/skills/` | Patterns d'architecture |
| skill-debug-pro | `.codeium/windsurf/skills/` | Debugging systématique |
| skill-ui-premium | `.codeium/windsurf/skills/` | Design system premium |
| skill-vibe-coding | `.codeium/windsurf/skills/` | Vibe coding flow state |
| microsoft-foundry | `.agents/skills/` | Deploy Foundry agents |

---

## 2. Multi-agent orchestration

### Modèles disponibles et leurs forces

| Outil | Modèle | Force | Quand l'utiliser |
|-------|--------|-------|------------------|
| **Devin CLI (local)** | GLM-5.2 High | Tokens illimités, sub-agents, outils natifs | Travail long, gourmand, itératif (DEFAUT) |
| **Devin Cloud** | Devin SWE | VM complète, browser, long-running | CI/CD, Docker, scraping, migrations |
| **Claude Code** | Opus 4.8 / Sonnet | Coding excellence, agentic | Code complexe, refactoring, debugging |
| **Claude Chat** | Opus 4.8 | Reasoning, analyse, écriture | Analyse, stratégie, rédaction |
| **ChatGPT** | GPT-5 | Large knowledge, multimodal | Recherche large, vision, données récentes |
| **GLM** | GLM-5.2 | Fast, multilingual, cheap | Tâches bulk, traduction, résumés |
| **Kimi** | Kimi 2.7 | Long context (2M tokens) | Documents longs, codebases entiers |
| **Gemini** | Gemini 2.5 Pro | Multimodal, Google ecosystem | Vision, Sheets, Docs, YouTube |

### Workflow multi-agent

```
1. User décrit une tâche à déléguer
2. Devin analyse → recommande l'outil optimal
3. Devin génère un handoff prompt autosuffisant
4. Devin sauvegarde dans .devin/handoffs/[timestamp]_[outil]_[sujet].md
5. Devin instruit l'user : "Ouvre [outil] dans une nouvelle fenêtre, colle ce prompt"
6. User exécute dans l'autre outil
7. User sauvegarde le résultat dans .devin/handoffs/results/[filename]_result.md
8. User revient : "résultat [filename]"
9. Devin intègre le résultat dans le projet
```

### Fan-out parallèle

Pour les tâches découpables en sous-tâches indépendantes :
- Devin génère 2-5 handoffs en parallèle
- Crée un fichier orchestrateur `.devin/handoffs/[timestamp]_ORCHESTRATOR.md`
- L'user lance les handoffs en parallèle dans plusieurs fenêtres
- Devin intègre tous les résultats au retour

### Règles de délégation

- **Si Devin CLI local suffit** (tokens illimités) → faire directement, pas de handoff
- **Si < 50 lignes de code** → faire soi-même
- **Si la tâche nécessite une VM/browser** → `/handoff` vers Devin Cloud
- **Si c'est du code complexe avec spec claire** → Claude Code
- **Si c'est de l'analyse/recherche large** → ChatGPT ou Gemini
- **Si c'est un document très long** → Kimi (2M context)

---

## 3. Mémoire persistante

### Architecture 2 layers

```
Layer 1 : MCP memory (knowledge graph)
├── Entités : Project, Decision, Session, Pattern, Issue, Entity, Handoff
├── Relations : depends_on, blocks, part_of, uses, decided_on, supersedes
└── Avantage : queryable, structuré, rapide

Layer 2 : Obsidian vault (.obsidian-vault/)
├── 00_INBOX/        → Notes non classées
├── 10_SESSIONS/     → Logs de sessions IA
├── 20_DECISIONS/    → Décisions ADR-style
├── 30_RESEARCH/     → Résultats de recherche
├── 40_PLANS/        → Plans et roadmaps
├── 50_CONTEXT/      → État projet, architecture
├── 60_HANDOFFS/     → Handoffs multi-agent et résultats
├── 70_KNOWLEDGE/    → Knowledge base (patterns, solutions)
└── 99_TEMPLATES/    → Templates (session, decision, research)
```

### Cross-tool continuity

Le vault Obsidian est en **Markdown pur** → lisible par :
- Devin CLI (via filesystem ou MCP obsidian)
- Claude Code (via filesystem)
- ChatGPT / Gemini / Kimi (via copier-coller)
- Humain (via Obsidian app)

**Aucune perte de contexte** entre sessions, même en changeant d'outil IA.

### Setup Obsidian Local REST API (optionnel)

Pour que Devin puisse écrire directement dans Obsidian via MCP :

1. Installer Obsidian → https://obsidian.md
2. Settings → Community plugins → "Local REST API"
3. Install + Enable
4. Ouvrir le vault `.obsidian-vault/` dans Obsidian
5. Le MCP `obsidian` de Devin pourra alors écrire directement (port 27124)

**Sans le plugin** : Devin utilise le filesystem (read/write) — fonctionnel, juste moins intégré.

### Commandes memory-sync

| Commande | Action |
|----------|--------|
| `/memory-sync recall` | Charge le contexte au début d'une session |
| `/memory-sync save` | Persiste la session/décision dans MCP + Obsidian |
| `/memory-sync search [terme]` | Recherche dans MCP + vault |
| `/memory-sync sync` | Vérifie cohérence MCP ↔ Obsidian |
| `/memory-sync status` | Affiche l'état de la mémoire |

---

## 4. Forest Crew — 7 agents spécialisés

### Les 7 agents

| Agent | Rôle | Expertise | Quand l'invoquer |
|-------|------|-----------|------------------|
| 🌲 **Dendromètre** | Conformité scientifique | Cubage, IBP, tarifs, biomasse | Calculs dendrométriques |
| 🗺️ **Cartographe** | Couche géospatiale | Lambert93, WKT, GeoPackage, IGN | Problèmes GIS, APIs IGN |
| 🔐 **Sentinel** | Sécurité/RGPD | SQLCipher, RGPD, données perso | Avant modif DB, audit RGPD |
| 🤖 **SylvIA** | IA forestière | Mistral, QLoRA, RAG, vision | Features IA, choix de modèle |
| 💼 **Bûcheron** | Business/financement | BPI, French Tech, ADEME | Dossiers financement, pitchs |
| 🔧 **Ingénieur** | Android/Kotlin | Compose, Room, Hilt, Coroutines | Architecture code, perf |
| 📡 **Capteur** | Hardware IoT | BLE, NMEA, compas forestier | Intégration hardware |

### Modes d'invocation

- **Agent unique** : `/forest-crew dendromètre valide formule Bouchon`
- **Crew entière** : `/forest-crew analyse cette feature` → tous les agents répondent
- **Liste** : `/forest-crew list` → affiche les 7 agents

---

## 5. Structure des dossiers

```
GeoSylva-new/
├── .devin/
│   ├── skills/
│   │   └── forest-crew/
│   │       └── SKILL.md          # 7 agents spécialisés
│   └── handoffs/
│       ├── [timestamp]_[outil]_[sujet].md   # Handoff prompts
│       └── results/
│           └── [filename]_result.md          # Résultats retournés
│
├── .obsidian-vault/
│   ├── _README.md               # Guide du vault
│   ├── 00_INBOX/
│   ├── 10_SESSIONS/             # Logs de sessions
│   ├── 20_DECISIONS/            # ADR
│   ├── 30_RESEARCH/
│   ├── 40_PLANS/
│   ├── 50_CONTEXT/
│   │   └── PROJECT_STATE.md     # État actuel du projet
│   ├── 60_HANDOFFS/
│   ├── 70_KNOWLEDGE/
│   └── 99_TEMPLATES/
│       ├── session.md
│       ├── decision.md
│       └── research.md
│
├── AGENTS.md                    # Ce fichier
├── MASTER_PLAN.md               # Vision + plan + écosystème
├── AI_CONTEXT.md                # Contexte technique du code
├── RESEARCH_OPPORTUNITIES.md    # 150+ opportunités
├── AUDIT_FORESTIER_COMPLET.md   # Audit vague 1
└── AUDIT_GLOBAL_GEOSYLVA.md     # Audit vague 2
```

Skills globales (tous projets) :
```
~/.config/devin/skills/
├── brainstorm/
│   └── SKILL.md
├── multi-agent/
│   └── SKILL.md
└── memory-sync/
    └── SKILL.md
```

---

## 6. Workflow recommandé

### Début de session

```
1. /memory-sync recall          # Charger le contexte
2. Lire MASTER_PLAN.md          # Savoir où on en est
3. Identifier la tâche du jour
```

### Pendant la session

```
- Tâche simple → faire directement (tokens illimités)
- Tâche complexe → /brainstorm [sujet] pour explorer
- Tâche domain-specific → /forest-crew [agent] [tâche]
- Tâche trop grosse → /multi-agent [tâche] pour déléguer
- Décision importante → /memory-sync save pour persister
```

### Fin de session

```
1. /memory-sync save            # Persister session + décisions
2. Mettre à jour 50_CONTEXT/PROJECT_STATE.md si besoin
3. Noter les TODO dans la session log
```

### Changement d'outil IA

```
1. Dans Devin : /memory-sync save
2. Ouvrir Claude Code / ChatGPT / autre
3. Donner le contexte : "Lis .obsidian-vault/50_CONTEXT/PROJECT_STATE.md
   et .obsidian-vault/10_SESSIONS/ (dernière note)"
4. L'autre outil a tout le contexte
5. À la fin, sauvegarder dans .obsidian-vault/00_INBOX/ pour intégration
```

---

## 7. Compatibilité avec skills existantes

### Skills Claude (`.claude/skills/`)

Devin CLI lit automatiquement les skills dans `.claude/skills/` (compatibilité).
Les skills `model-economy`, `composition-patterns`, `react-best-practices`,
`web-design-guidelines` continuent de fonctionner.

### Skills Windsurf (`.codeium/windsurf/skills/`)

Devin CLI lit aussi `.codeium/windsurf/skills/` (compatibilité).
Les skills `skill-architecture`, `skill-debug-pro`, `skill-ui-premium`,
`skill-vibe-coding` continuent de fonctionner.

### Skills .agents (`.agents/skills/`)

Standard `.agents` supporté. La skill `microsoft-foundry` continue de fonctionner.

### Priorité de résolution (si conflit de noms)

1. `.devin/skills/` (projet, Devin natif)
2. `.agents/skills/` (projet, standard .agents)
3. `.claude/skills/` (projet, compatibilité Claude)
4. `~/.config/devin/skills/` (global, Devin natif)
5. `~/.codeium/windsurf/skills/` (global, Windsurf)

---

## 8. MCP servers configurés

| Server | Statut | Usage |
|--------|--------|-------|
| **memory** | ✅ Actif | Knowledge graph (entités, relations) |
| **obsidian** | ⚠️ Nécessite Obsidian + Local REST API plugin | Notes Markdown |
| **git** | ✅ Actif | Opérations Git |

### Setup obsidian MCP (si souhaité)

1. Installer Obsidian
2. Installer plugin "Local REST API" (community plugins)
3. Ouvrir le vault `.obsidian-vault/` dans Obsidian
4. Le MCP obsidian se connectera automatiquement (port 27124)

**Sans Obsidian** : le système fonctionne en filesystem seul (read/write tools).
Le vault Markdown reste accessible à tous les outils IA.

---

## 9. Ajouter de nouvelles skills

### Skill globale

```bash
mkdir -p ~/.config/devin/skills/[nom]/
# Créer SKILL.md avec frontmatter + prompt
```

### Skill projet

```bash
mkdir -p .devin/skills/[nom]/
# Créer SKILL.md avec frontmatter + prompt
```

### Format SKILL.md

```yaml
---
name: [nom]
description: [description pour completions]
argument-hint: "[args]"
model: [sonnet|opus|swe|codex|glam]  # optionnel
subagent: [true|false]                # optionnel
allowed-tools:                        # optionnel
  - read
  - grep
  - exec
triggers:
  - user
  - model
---

[Prompt content — ce qui est injecté quand la skill est invoquée]
```

Voir `devin-for-terminal` skill pour la doc complète.

---

## 10. Ajouter des repos GitHub de crew agents

Pour intégrer des repos externes de skills/agents :

1. Cloner le repo dans `.agents/skills/` (standard .agents)
2. Ou copier les `SKILL.md` dans `.devin/skills/`
3. Les skills deviennent automatiquement disponibles via `/[nom]`

**Repos intéressants à explorer** (à ajouter selon besoins) :
- OpenForis (FAO) — workflows forestiers
- Geopaparazzi — patterns cartographie terrain
- Forest Sentry — patterns IA on-device

---

## Historique

| Date | Action |
|------|--------|
| 2026-06-29 | Création du système complet (4 skills + vault + AGENTS.md) |
