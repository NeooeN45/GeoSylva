# VIBE_CODING.md — GeoSylva Vibe Coding Workflow

> Speed is a feature. Stay in flow state. Think in outcomes, not implementations.
> The agent writes code. You steer direction, own architecture, validate results.
> A bad implementation shipped beats a perfect implementation planned forever.

## The Core Loop

```
1. OUTCOME    → "Users can reset their password via email link"
2. SPEC       → Write SPEC.md (acceptance criteria, edge cases, schema)
3. EXECUTE    → New session, clean context, implement the spec
4. VALIDATE   → Run tests + build + manual smoke test
5. SHIP       → Commit, PR, done
```

**Never skip step 2.** The spec is the contract. Vibe coding without a spec is chaos.

---

## GeoSylva-Specific Workflow

### Session Start
```bash
# Load context
/memory-sync recall                    # charge l'état du projet
cat .obsidian-vault/50_CONTEXT/PROJECT_STATE.md  # état actuel
cat MASTER_PLAN.md | head -100         # vision et phases
```

### Feature Implementation
1. **Outcome prompt** (not how prompt):
   ```
   ❌ "Write a function that validates email with regex"
   ✅ "Users should see an inline error if they type an invalid email on blur"
   ```

2. **Constraint prompt** for GeoSylva:
   ```
   "Build [X]. Constraints:
   - Offline-first (no network assumption)
   - Room + SQLCipher for storage
   - Compose + Material 3
   - Lambert93 (EPSG:2154) for all coordinates
   - Follow Clean Architecture (domain/data/presentation)
   - Tests alongside code (domain 80% coverage)
   - No new dependency without justification"
   ```

3. **Forest-crew delegation** for domain-specific work:
   ```
   /forest-crew dendrometre "Validate volume calculation for chêne with C130=120cm, h=25m"
   /forest-crew cartographe "Convert WGS84 GPS to Lambert93 for parcelle boundary"
   /forest-crew sylvia "Classify species from Sentinel-2 image of parcelle"
   /forest-crew sentinel "Check RGPD compliance for new GPS tracking feature"
   ```

4. **Multi-agent for parallel work**:
   ```
   /multi-agent "Implement SQLCipher encryption" → Claude/GPT for implementation
   /multi-agent "Write RGPD privacy policy" → Claude for legal text
   ```

### Context Management
- `/clear` between every distinct task — stale context is actively harmful
- One session = one concern (backend, frontend, tests, docs)
- `@file` only the files that will change — not the whole codebase
- If agent loops on same error 3 times: stop, `/clear`, reframe the problem
- Save good plans to `PLAN_<feature>.md` before implementation

### Parallel Agents (Windsurf Spaces)
```
Space: "Feature/sqlcipher-encryption"
├── Cascade A → data layer (SQLCipher + Room + Keystore)
├── Cascade B → security layer (DatabaseEncryptionService)
└── Cascade C → tests (encryption, migration, key rotation)
```
**Rule**: one agent per layer, never two agents on the same file.

---

## GeoSylva Architecture Constraints

### Always
- **Clean Architecture**: domain knows nothing of Android/Room
- **Offline-first**: local DB is source of truth, sync is background
- **Lambert93**: internal storage EPSG:2154, display can reproject
- **SQLCipher**: database encryption mandatory (RGPD)
- **Compose + Material 3**: all new UI
- **Hilt**: dependency injection (after migration)
- **Tests alongside code**: domain 80%, overall 60%

### Never
- No hardcoded strings (use strings.xml)
- No `collectAsState()` without lifecycle (use `collectAsStateWithLifecycle()`)
- No network calls on main thread
- No plaintext PII storage
- No HTTP (HTTPS only, certificate pinning)
- No new dependency without justification
- No commented-out code (delete it, git remembers)

---

## Prompting Patterns for GeoSylva

### The Outcome Prompt
```
"Users can capture tree diameter via BLE caliper and it auto-creates a tige entry"
```

### The Constraint Prompt
```
"Implement BLE caliper connection. Constraints:
- Nordic Kotlin BLE Library
- Foreground service for persistent connection
- Auto-reconnect with exponential backoff
- Buffer measurements offline
- Battery-optimized scan intervals
- Follow existing ForestryCounterApplication patterns"
```

### The Describe-The-Diff Prompt
```
"In GpsAverager.kt, the averageLocation() function should:
- Before: return simple mean of all points
- After: MAD outlier rejection + inverse-variance weighted average"
```

### The Persona Prompt
```
"Act as a senior Android engineer who has seen this system break in production.
Review the Room migration 27→28 and tell me what will fail first and why."
```

### The Forest-Crew Prompt
```
/forest-crew dendrometre "Review the tarif selection logic in TarifCalculator.kt
for mixed oak-beech stands. Are we using the correct tarifs for each species?"
```

### The Interview Prompt (for complex features)
```
"I want to build [on-device species classification]. Interview me using AskUserQuestion.
Dig into: model size constraints, accuracy requirements, offline behavior,
training data, deployment strategy. Skip the obvious. When done, write SPEC.md."
```

---

## Iteration Velocity

- **Commit early, commit often** — git is your undo button
- **Feature flags** for incomplete features — ship the flag off, not dead code
- **Revert freely** — Cascade's step-revert (arrow right on any step)
- **Turbo Mode** + allowed commands list = zero interruptions on safe commands
- **Plan Mode** (Shift+Tab x2) before any refactor touching >3 files

---

## Verification Checklist

Before marking any task complete:
```bash
./gradlew assembleDebug          # must pass
./gradlew test                   # must pass
./gradlew lint                   # must pass (fix warnings)
```

For features touching:
- **Database**: test migration from previous version
- **Network**: test with mitmproxy (no self-signed certs accepted)
- **Security**: verify with hex editor (no plaintext PII)
- **Performance**: profile on low-end device (2GB RAM, API 26)
- **Offline**: disable network, app must not crash
- **BLE**: test with real device, not just mock

---

## Session End
```bash
# Persist context for next session
/memory-sync save
# Update project state
# Update .obsidian-vault/50_CONTEXT/PROJECT_STATE.md
```

---

## What Vibe Coding Is NOT

- It is NOT "accept everything Cascade generates without reading"
- It is NOT "skip tests because the AI wrote it"
- It is NOT "no architecture because we move fast"
- Speed comes from clear intent + good context, not from blind acceptance.
