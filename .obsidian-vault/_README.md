# GeoSylva — Obsidian Vault

Vault Obsidian pour **mémoire persistante cross-tool** du projet GeoSylva.

## Pourquoi ce vault ?

Ce vault assure la **continuité** entre :
- Sessions Devin CLI (GLM-5.2)
- Sessions Claude Code (Opus 4.8 / Sonnet)
- Sessions ChatGPT / Gemini / Kimi (via copier-coller)
- Lecture humaine dans Obsidian

**Tous les outils IA peuvent lire ces notes Markdown** → pas de perte de contexte entre sessions.

## Structure

```
.obsidian-vault/
├── 00_INBOX/           # Notes non classées (à trier)
├── 10_SESSIONS/        # Logs de sessions (une note par session IA)
├── 20_DECISIONS/       # Décisions importantes (ADR-style)
├── 30_RESEARCH/        # Résultats de recherche (sub-agents, web)
├── 40_PLANS/           # Plans et roadmaps
├── 50_CONTEXT/         # Contexte projet (état, architecture)
├── 60_HANDOFFS/        # Handoffs multi-agent et résultats
├── 70_KNOWLEDGE/       # Knowledge base (patterns, solutions, crew analyses)
└── 99_TEMPLATES/       # Templates de notes
```

## Comment l'utiliser

### Avec Devin CLI (GLM-5.2)
La skill `memory-sync` gère automatiquement :
- `/memory-sync recall` — charge le contexte au début d'une session
- `/memory-sync save` — persiste à la fin ou après une décision

### Avec Claude Code
Claude Code peut lire/écrire dans ce vault directement (filesystem).
Au début d'une session : "Lis `.obsidian-vault/10_SESSIONS/` pour le contexte"

### Avec ChatGPT / Gemini / Kimi
Copier le contenu de `50_CONTEXT/PROJECT_STATE.md` + dernière session de `10_SESSIONS/`
pour donner le contexte. Coller le résultat dans `00_INBOX/` pour intégration.

### Avec Obsidian (lecture humaine)
1. Ouvrir Obsidian
2. "Open vault" → sélectionner ce dossier `.obsidian-vault/`
3. Activer le plugin "Local REST API" pour permettre à Devin d'écrire directement

## Setup Obsidian Local REST API (optionnel, pour écriture directe)

1. Installer Obsidian → https://obsidian.md
2. Settings → Community plugins → Browse → "Local REST API"
3. Install + Enable
4. Settings → Local REST API → noter le port (27124 par défaut) + API key
5. Ouvrir ce vault dans Obsidian
6. Le MCP `obsidian` de Devin pourra alors écrire directement

**Sans le plugin** : Devin utilise le filesystem (read/write tools) pour gérer le vault.
C'est fonctionnel, juste moins intégré.

## Conventions

- **Nommage** : `YYYY-MM-DD_HHmm_[sujet].md` pour sessions
- **Frontmatter YAML** : chaque note commence par `---\ndate: ...\ntool: ...\nmodel: ...\n---`
- **Liens Obsidian** : utiliser `[[note]]` pour cross-références
- **Tags** : `#session`, `#decision`, `#research`, `#plan`, `#handoff`, `#knowledge`
