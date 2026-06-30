# Recherche : Écosystème Multi-Agent & System Prompts

**Date** : 2026-06-29
**Type** : Research
**Status** : Synthèse complète
**Sources** : 3 sous-agents parallèles + fetch direct

---

## TL;DR

48 repositories analysés à travers 3 axes de recherche. Les découvertes majeures permettent de :
1. **Améliorer nos 7 agents forest-crew** avec des patterns éprouvés (AGENTS.md format, Tool Registry, ReAct loop)
2. **Créer de nouvelles skills** avec skill-creator et patterns officiels Anthropic
3. **Intégrer des outils GIS forestiers** via GeoAgent (QGIS, STAC, APIs IGN)
4. **Structurer l'orchestration multi-agent** via patterns GeoFaham/CrewAI/LangGraph

---

## 1. System Prompts Leaks (3 repos clés)

| Repo | Stars | Licence | Pertinence |
|------|-------|---------|------------|
| `x1xhlol/system-prompts-and-models-of-ai-tools` | 141K | GPL-3.0 | ⭐⭐⭐⭐⭐ |
| `asgeirtj/system_prompts_leaks` | 47K | CC0-1.0 | ⭐⭐⭐⭐⭐ |
| `jujumilk3/leaked-system-prompts` | 14.6K | ? | ⭐⭐⭐⭐ |

### Ce qu'on extrait
- **Claude Fable 5 prompt** (3740 lignes) : structure complète d'un system prompt production-grade
- **Devin AI prompt** : patterns d'agent autonome (planification, exécution, handoff)
- **Cursor/Windsurf prompts** : conventions de rules et context engineering
- **Patterns de refusal handling, safety, tool-use** adaptables pour Sentinel

### Action : Étudier le Claude Fable 5 prompt pour la structure de nos AGENTS.md forestiers

---

## 2. Claude Code Skills & Agents (10 repos clés)

| Repo | Stars | Licence | Pertinence |
|------|-------|---------|------------|
| `anthropics/skills` | 155K | Apache 2.0 | ⭐⭐⭐⭐⭐ |
| `wshobson/agents` | 37.3K | MIT | ⭐⭐⭐⭐⭐ |
| `hesreallyhim/awesome-claude-code` | 41.2K | CC0-1.0 | ⭐⭐⭐⭐⭐ |
| `K-Dense-AI/scientific-agent-skills` | 29.5K | MIT | ⭐⭐⭐⭐⭐ |
| `Jeffallan/claude-skills` | 10.2K | MIT | ⭐⭐⭐⭐⭐ |
| `VoltAgent/awesome-claude-code-subagents` | 8.5K | MIT | ⭐⭐⭐⭐⭐ |
| `alirezarezvani/claude-skills` | 18.9K | MIT | ⭐⭐⭐⭐⭐ |
| `daymade/claude-code-skills` | 1.2K | MIT | ⭐⭐⭐⭐ |
| `levnikolaevich/claude-code-skills` | 500 | MIT | ⭐⭐⭐⭐ |
| `rohitg00/awesome-claude-code-toolkit` | 2.2K | Apache 2.0 | ⭐⭐⭐⭐⭐ |

### Découvertes clés

**`anthropics/skills` (OFFICIEL)** : Standard Agent Skills avec frontmatter YAML, templates officiels, spec multi-plateforme (Cursor, Codex, Gemini). À adopter comme format canonique pour nos skills.

**`K-Dense-AI/scientific-agent-skills`** : 170+ skills scientifiques dont :
- **Geospatial skills** → Cartographe (GPS, cartes, CRS)
- **Time series forecasting** → SylvIA (prédictions croissance)
- **Data analysis patterns** → Dendromètre (données écologiques)

**`daymade/claude-code-skills`** : Contient `skill-creator` (méta-skill pour créer des skills). À installer pour accélérer la création de skills forestières.

**`VoltAgent/awesome-claude-code-subagents`** : 100+ subagents avec templates role/goal/backstory. Modèle pour structurer forest-crew.

**`wshobson/agents`** : 191 agents, 158 skills, 106 commands, 16 multi-agent orchestrators. Compatible multi-harness (Claude, Codex, Cursor, Gemini, Copilot).

### Action : Adopter le format Agent Skills standard (anthropics/skills) pour toutes nos skills futures

---

## 3. Patterns Multi-Agent Forestier/Scientifique (10 repos clés)

| Repo | Stars | Pertinence Forest-Crew |
|------|-------|------------------------|
| `opengeos/GeoAgent` | 390 | 🔴 Très élevée (GIS, QGIS, STAC) |
| `microsoft/GeoFaham` | - | 🔴 Très élevée (5 agents EO) |
| `K-Dense-AI/scientific-agents` | 75 | 🔴 Très élevée (503 profils AGENTS.md) |
| `JamesBrockUoB/ForestChat` | - | 🟠 Élevée (VLM foresterie) |
| `mbzuai-oryx/OpenEarthAgent` | - | 🟠 Élevée (104 outils EO) |
| `opendatalab/Earth-Agent` | - | 🟠 Élevée (ReAct + 104 outils) |
| `crewAIInc/crewAI` | 54.5K | 🟡 Moyenne (role/goal/backstory) |
| `Pratik25priyanshu20/Swim-Agents` | - | 🟡 Moyenne (pipeline env) |
| `baratadiego/ecological-agent-skills` | - | 🟡 Moyenne (skills écologie) |
| `juaquicar/GeoAgents` | - | 🔴 Très élevée (Planning/Verification) |

### Patterns clés à adapter

#### Pattern 1 : AGENTS.md (K-Dense-AI/scientific-agents)
Format standard pour system prompts d'experts :
```markdown
# AGENTS.md — [Expert Name] Agent
You are an experienced [domain] specialist...
## How You Frame A Problem
## Tools and Data You Reach For
## How You Stress-Test Claims
## How You Report Findings
```
→ **Adapter pour nos 7 agents forest-crew**

#### Pattern 2 : Multi-Agent Orchestration (GeoFaham)
Architecture 5 agents proche de nos 7 :
- Orchestrator → coordination
- Vector Agent → Cartographe (PostGIS, GeoPackage)
- Maps Agent → Capteur (OSM, POI)
- STAC Agent → SylvIA (satellite)
- Raster Ops Agent → SylvIA (NDVI, NBR)

#### Pattern 3 : Tool Registry (OpenEarthAgent/Earth-Agent)
104 outils EO catégorisés :
- Spectral : NDVI, NBR, NDWI
- GIS : distance, aire, statistiques zonales
- Perceptual : détection, segmentation
- GeoTIFF : opérations raster
→ **Créer FOREST_TOOL_REGISTRY**

#### Pattern 4 : Planning/Verification (GeoAgents)
Workflow multi-phase :
1. Planning → 2. Execution → 3. Verification → 4. Replan → 5. Explanation
→ **Pour Dendromètre (validation IBP) et SylvIA (validation ML)**

#### Pattern 5 : ReAct Loop (Earth-Agent)
Reasoning + Action itératif avec memory update
→ **Pour SylvIA (analyse satellite step-by-step)**

#### Pattern 6 : CrewAI YAML Config
```yaml
dendrometre:
  role: "Forest Mensuration Specialist"
  goal: "Ensure scientific compliance in dendrometric calculations"
  backstory: "You are an expert forest mensurationist..."
  tools: [compute_volume, validate_ibp, apply_tarif]
  llm: "gpt-4o"
```
→ **Format de config pour forest-crew**

#### Pattern 7 : RAG Knowledge Base (SWIM Platform)
- Vector embeddings (documentation IGN, INRAE)
- Cosine-similarity retrieval
- Context injection automatique
→ **Pour assistant forestier IA futur**

---

## 4. Plan d'Intégration pour GeoSylva

### Phase 1 : Immédiat (cette session)

1. **Créer 7 fichiers AGENTS.md** pour forest-crew (inspirés K-Dense-AI format)
   - Dendromètre : adapter carbon-cycle-scientist + precision-agriculture
   - Cartographe : créer profil GIS/foresterie française
   - SylvIA : adapter ForestChat + ecological-agent-skills
   - Bûcheron : créer profil business/financement
   - Ingénieur : adapter civil-engineer → Android/Kotlin
   - Capteur : créer profil hardware/IoT/BLE
   - Sentinel : créer profil RGPD/sécurité

2. **Améliorer forest-crew/SKILL.md** avec :
   - Pattern Planning/Verification (GeoAgents)
   - Tool Registry concept (OpenEarthAgent)
   - ReAct loop pour SylvIA

### Phase 2 : Court terme (1-2 semaines)

3. **Installer skill-creator** (daymade) pour accélérer création skills
4. **Étudier anthropics/skills** pour adopter le format standard
5. **Intégrer GeoAgent** pour Cartographe (QGIS, STAC, APIs IGN)
6. **Créer FOREST_TOOL_REGISTRY** (inspiré OpenEarthAgent)

### Phase 3 : Moyen terme (1-3 mois)

7. **Implémenter orchestration** inspirée GeoFaham/CrewAI
8. **Setup RAG Knowledge Base** (documentation IGN, INRAE)
9. **Intégrer scientific skills** (K-Dense-AI) pour analyses écologiques
10. **Configurer hooks de sécurité** (karanb192) pour protection secrets

### Phase 4 : Long terme (3-6 mois)

11. **Cross-platform** (wshobson/agents patterns)
12. **Human-in-the-loop** avancé (LangGraph)
13. **Marketplace interne** (daymade pattern)
14. **Contribuer à la communauté** avec nos patterns forestiers

---

## 5. Repos à Cloner Prioritairement

```bash
# Tier 1 - Étude immédiate
git clone https://github.com/anthropics/skills          # Standard officiel
git clone https://github.com/K-Dense-AI/scientific-agents  # 503 profils AGENTS.md
git clone https://github.com/opengeos/GeoAgent           # GIS agent layer

# Tier 2 - Inspiration patterns
git clone https://github.com/daymade/claude-code-skills  # skill-creator
git clone https://github.com/VoltAgent/awesome-claude-code-subagents
git clone https://github.com/crewAIInc/crewAI            # YAML agent config

# Tier 3 - Référence
git clone https://github.com/asgeirtj/system_prompts_leaks
git clone https://github.com/wshobson/agents
```

---

## 6. Licence Compatibility

| Licence | Compatible commercial ? | Repos |
|---------|------------------------|-------|
| MIT | ✅ Oui | anthropics, wshobson, K-Dense-AI, VoltAgent, daymade, levnikolaevich, crewAI |
| Apache 2.0 | ✅ Oui | anthropics/skills, rohitg00, ag2ai |
| CC0-1.0 | ✅ Oui (public domain) | asgeirtj, hesreallyhim, PatrickJS |
| GPL-3.0 | ⚠️ Conditions | x1xhlol (étude seulement, pas de code reuse) |
| Non spécifiée | ⚠️ Vérifier | GeoFaham, ForestChat, OpenEarthAgent, Earth-Agent |

---

## 7. Impact sur MASTER_PLAN.md

Cette recherche impacte :
- **Forest-crew** : passage de 1 SKILL.md à 7 AGENTS.md + SKILL.md orchestrateur
- **Skills futures** : adoption format Agent Skills standard (anthropics)
- **SylvIA** : patterns VLM + ReAct + Tool Registry pour analyse satellite
- **Cartographe** : intégration GeoAgent pour QGIS/APIs IGN
- **Dendromètre** : pattern Planning/Verification pour validation IBP
- **Phase 0** : pas d'impact direct (fixes techniques prioritaires)

---

## Conclusion

L'écosystème open-source multi-agent est extrêmement riche et directement applicable à GeoSylva. Les 3 axes de recherche convergent vers une architecture claire :

1. **Format standard** : Agent Skills (anthropics) + AGENTS.md (K-Dense-AI)
2. **Orchestration** : CrewAI YAML config + GeoFaham 5-agent pattern
3. **Outils** : GeoAgent (GIS) + OpenEarthAgent (104 outils EO) + FOREST_TOOL_REGISTRY custom
4. **Patterns** : Planning/Verification (GeoAgents) + ReAct (Earth-Agent) + RAG (SWIM)

**Prochaine étape recommandée** : Créer les 7 fichiers AGENTS.md pour forest-crew en s'inspirant du format K-Dense-AI/scientific-agents.
