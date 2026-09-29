# MASTER PLAN — GeoSylva

**Document de référence stratégique et opérationnel**
**Date de création** : 2026-06-29
**Dernière révision factuelle** : 2026-08-03 (première synchronisation de parcelles avec GSIE)
**Statut** : Actif — remplace tous les documents de planification précédents
**Fondateur** : Camil (auto-entrepreneur, Poitiers, Nouvelle-Aquitaine)

> ⚠️ **Note méthodologique (2026-07-01)** : les audits du 2026-06-29 (§2.4, §3) ont été
> comparés au code réel après ~20 commits supplémentaires. **9 des 12 actions "Phase 0"
> étaient déjà résolues** au moment de cette révision (voir §3.2). Ce document reflète
> maintenant l'état vérifié, pas seulement l'état déclaré. Voir `.devin/AGENT_COORDINATION.md`
> pour le protocole qui doit éviter que ce plan se re-périme silencieusement.

---

## 0. LIRE CE FICHIER EN PREMIER

Ce fichier est la **source de vérité unique** pour :
- La vision produit et business
- Le plan d'exécution technique
- Le financement et les aides
- L'écosystème de partenariats
- Les standards de qualité et de test

**Ordre de lecture pour une nouvelle session IA** :
1. Ce fichier (`MASTER_PLAN.md`)
2. `AI_CONTEXT.md` (contexte technique du code)
3. `.devin/AGENT_COORDINATION.md` (protocole multi-agents — **obligatoire si tu coordonnes ou reçois le rapport d'un autre agent**)
4. `RESEARCH_OPPORTUNITIES.md` (opportunités techniques, IA, financement, hardware — 150+ entrées)
5. `docs/REFERENTIELS_FORESTIERS_EXTERNES.md` (référentiels professionnels/scientifiques externes : ONF, IGN, CNPF, France Bois Forêt, SAFER, AFNOR — 18 sources, 15 actions priorisées)
6. `AUDIT_FORESTIER_COMPLET.md` (audit vague 1 — DB, calculs, tarifs, foresterie — **daté du 2026-06-29, vérifier le statut réel avant d'agir, voir §3**)
7. `AUDIT_GLOBAL_GEOSYLVA.md` (audit vague 2 — sécurité, GIS, UI, i18n, RGPD, perf — **idem**)
8. `docs/RGPD_AUDIT_REPORT.md` (audit RGPD initial)
9. Le code

**Règle impérative pour toute IA qui met à jour ce document** : ne jamais marquer un item
"FAIT" sur la seule foi d'un rapport texte. Vérifier dans le code (grep/read) avant de
changer un statut. C'est exactement l'erreur qui a rendu ce plan obsolète 2 jours après
sa création.

---

## 1. VISION

### 1.1 Énoncé de vision

> GeoSylva est la **plateforme forestière française** qui remplace le carnet de terrain et Excel par un workflow numérique complet : de la saisie sur le terrain à l'analyse IA, du compas Bluetooth au satellite, du mobile au desktop.

### 1.2 Mission

Fournir aux forestiers français (experts indépendants, coopératives, ONF, CNPF, propriétaires privés) un outil de terrain professionnel, conforme aux standards scientifiques français, respectueux du RGPD, et propulsé par une IA souveraine.

### 1.3 Ambition long terme (3-5 ans)

Devenir le standard de facto pour la gestion forestière de terrain en France, avec :
- **GeoSylva Mobile** (Android + iOS) — saisie terrain
- **QGISIA+** (plugin QGIS desktop) — analyse IA et cartographie
- **Partenariat Pixstart** — monitoring satellite
- **IA forestière française** — Mistral 7B fine-tuné sur données ONF/CNPF
- **Partenariats ONF / CNPF / Mistral AI** — légitimité et distribution

### 1.4 Ambition dendrométrique — devenir la référence de terrain

GeoSylva ne doit pas seulement afficher des volumes : elle doit devenir un **système de mesure forestière traçable**, capable de dire d'où vient chaque valeur, quelle est son incertitude et quelle mesure supplémentaire améliorerait le plus le résultat.

L'ambition est de fournir :

- un protocole terrain configurable par type de peuplement, objectif et région ;
- un moteur dendrométrique versionné, testable et auditable ;
- une estimation d'incertitude propagée jusqu'au volume et à la valeur ;
- un contrôle qualité actif qui demande une nouvelle mesure uniquement lorsqu'elle est utile ;
- un jumeau temporel de la parcelle pour comparer inventaires, croissance, mortalité et martelages ;
- une IA qui explique les calculs et propose des scénarios sans remplacer la décision du forestier ;
- une continuité complète entre GeoSylva Mobile, GSIE Serveur et GSIE PC.

### 1.5 Promesse produit

> **Chaque arbre mesuré une fois, chaque calcul explicable, chaque décision réversible, chaque parcelle comparable dans le temps.**

---

## 2. ÉTAT ACTUEL (vérifié 2026-07-17)

### 2.1 Version

> Les valeurs ci-dessous sont un instantané vérifié le 2026-07-17. Les métadonnées de build actuelles sont générées dans [docs/APP_VERSION.md](docs/APP_VERSION.md).

- **versionName** : 2.4.0
- **versionCode** : 10
- **DB version** : 32 (était 29 au 2026-06-29 — 3 migrations ajoutées depuis)
- **Kotlin** : 1.9.23 (toujours obsolète, cible 2.1.0 — Phase 1.14 non faite)
- **Compose BOM** : 2024.09.00
- **minSdk** : 26 (Android 8.0)
- **targetSdk / compileSdk** : 35
- **Build/tests vérifiés le 2026-07-01** : `compileDebugKotlin` OK, `testDebugUnitTest` OK — **467 tests, 0 échec, 0 erreur**

### 2.2 Architecture

- **Langage** : Kotlin (JVM 17)
- **UI** : Jetpack Compose + Material 3
- **Persistance** : Room (DB v32, migrations actives)
- **Préférences** : DataStore (non chiffré)
- **Navigation** : Navigation Compose (sealed class Screen, 5 sous-graphes)
- **Network** : OkHttp + SecureHttpClient (HTTPS, autorités Android, liste blanche, DNS public)
- **GIS** : Lambert-93 IGN NTG 71, WKT, Shapefile, GeoJSON, DEM SRTM
- **Exports** : CSV, XLSX (Apache POI), JSON, ZIP, Shapefile, GeoJSON, PDF
- **Maps** : MapLibre GL 10.3.1
- **Camera** : CameraX 1.3.3 (clinomètre numérique)
- **GPS** : FusedLocationProviderClient + LocationManager (legacy)

### 2.3 Couverture fonctionnelle

- Inventaire par essence et classe de diamètre (95+ essences)
- 7 méthodes de cubage (Schaeffer 1E/2E, Algan, IFN Rapide/Lent, FGH, coefficient forme)
- Martelage avec synthèse dendrométrique (G, Dg, Lorey, N/ha, V/ha)
- IBP CNPF officiel (10 critères, scoring 0/2/5, max 50 pts)
- Diagnostic station (floristique, gradient hydrique/trophique)
- Diagnostic ripisylve
- Cartographie interactive (12 couches, tuiles offline)
- Clinomètre numérique (capteurs téléphone)
- GPS de précision (moyennage MAD, inverse-variance)
- Export Shapefile/GeoJSON/CSV-XY/QGIS
- Diagnostic sylvicole avec recommandations
- Tables de prix éditables par essence/produit/classe
- Sauvegarde automatique WorkManager
- Onboarding 9 pages
- Base de refonte identifiée pour la page Âge / Martelage : inventaire, structure, volume, qualité, valeur, scénarios et contrôles de cohérence

**Limite actuelle à ne pas masquer** : les fonctionnalités dendrométriques sont riches, mais le moteur n'est pas encore certifié comme chaîne de mesure complète. Les domaines de validité des tarifs, l'âge de référence de l'indice de station, l'incertitude d'échantillonnage et la provenance détaillée des mesures doivent encore devenir des objets de première classe.

### 2.4 Santé du code

**Scores initiaux (audit 2026-06-29)** vs **re-vérification factuelle (2026-07-01)** —
seuls les domaines avec preuve concrète dans le code ont été remontés, les autres restent
non re-vérifiés en détail (ne pas supposer qu'ils sont restés identiques, juste qu'on n'a
pas encore la preuve du contraire) :

| Domaine | Score 06-29 | Score 07-01 | Statut | Preuve du changement |
|---------|-------|-------|--------|--------|
| Calculs dendrométriques de base | 8.5/10 | non re-vérifié | Conforme | — |
| Système de tarification/prix | 4.5/10 | **~7.5/10** | Nettement amélioré | Moteur pro 8 coefficients `ProPricingEngine.kt` implémenté (qualité NF EN 1316/1927, défauts NF EN 1310, région GRECO, accessibilité, saison, certification, lot, position), auto-détection GRECO via GPS, breakdown transparent, 100+ essences, 5 bugs critiques corrigés, 16 tests ajoutés (commits 0a14332, 270cd20, eec8ea0) — **coefficients régionaux GRECO (0.85-1.15) restent des estimations à calibrer avec données FBF réelles 2025** |
| Intégrité base de données | 5/10 | non re-vérifié | Risque données | DB passée de v29→v32, migrations ajoutées — à ré-auditer |
| Logique forestière domainale | 6.5/10 | non re-vérifié | Approximatif | — |
| Traitement des données (mappers) | 5.5/10 | **amélioré** | En progrès | Refactor repositories→modèles domaine commité le 2026-07-01 (voir `.devin/AGENT_COORDINATION.md` §6) |
| Sécurité / chiffrement / réseau | 5/10 | **~7/10** | Nettement amélioré | SQLCipher actif (`ForestryDatabase.kt:151`), HTTPS + confiance Android + protection DNS/SSRF (`SecureHttpClient.kt`), FLAG_SECURE actif (`MainActivity.kt`), injection SQL GeoPackage corrigée (whitelist regex) |
| GIS / géomatique | 7.5/10 | **~8/10** | Amélioré | 6 sous-phases carte/GPS terminées : attribution légale sources carto, perf `setGeoJson()`, tuiles offline parallèles 6 concurrent + retry backoff, cache HTTP MapLibre 50MB, suppression Helmert faux + 8 points contrôle Lambert93, compas TYPE_ROTATION_VECTOR + lissage passe-bas. `Lambert93.kt` dupliqué toujours présent (1.17 non fait). Scoreboard carte/GPS : Phase 1 80%, Phase 2-4 0%. |
| Présentation / UI / Compose | 6.5/10 | **~7/10** | Amélioré | `collectAsState()` entièrement migré vers `collectAsStateWithLifecycle()` (166 occurrences, 0 restante) |
| Internationalisation (FR/EN) | 4/10 | **~5/10** | Toujours insuffisant | `plurals.xml` créé FR/EN ; mais 71 `SimpleDateFormat` et 53 `€` codés en dur toujours présents |
| Build / CI / Gradle | 6/10 | non re-vérifié | Obsolète | Toujours pas de `.github/workflows/` (CI/CD absent) |
| RGPD / privacy | 3/10 | **~7/10** | Nettement amélioré | `RECORD_OF_PROCESSING_ACTIVITIES.md` créé (7 traitements documentés), page consentement onboarding ajoutée, transferts hors UE documentés, **`PRIVACY_POLICY.md` réécrit le 2026-07-01 après audit factuel vs code (8 erreurs corrigées, 6 services réseau manquants ajoutés, lacunes effacement/purge marquées à venir)** |
| Performance / mémoire / batterie | 6.5/10 | non re-vérifié | Risques OOM | Coil toujours absent des dépendances (0.11 non fait) |
| Misc (FormulaParser, WorkManager, DataStore, a11y) | 5.5/10 | non re-vérifié | À corriger | FormulaParser sans limites, DataStore non chiffré, `Flow.first()` migré à 2/103 seulement |
| Couverture de tests | 35% | non re-vérifié | Insuffisant | 467 tests passent (0 échec) mais % de couverture non recalculé |

**Total issues identifiées (2026-06-29)** : 224 (40 CRITICAL, 58 HIGH, 80 MEDIUM, 46 LOW)
**Échantillon CRITICAL re-vérifié (2026-07-01)** : au moins 4/18 CRITICAL de la vague 2
confirmées résolues (S-C1 SQLCipher, S-C2 cert pinning, S-C3 injection SQL, S-H4 FLAG_SECURE).
Les 36 CRITICAL restantes (vague 1 + reste vague 2) n'ont pas été ré-auditées une par une —
ne pas supposer qu'elles sont résolues sans vérification.

Voir :
- `AUDIT_FORESTIER_COMPLET.md` — vague 1 (101 issues, statut 2026-06-29 non rafraîchi)
- `AUDIT_GLOBAL_GEOSYLVA.md` — vague 2 (123 issues, statut 2026-06-29 non rafraîchi)
- `docs/REFERENTIELS_FORESTIERS_EXTERNES.md` — pour la fiabilité scientifique des calculs (cubage, IBP, GRECO)

### 2.5 Audit fonctionnel global — 2026-07-17

> Audit statique du code, des écrans, des moteurs, des entités, des exports, des tests et de la documentation. Les scores indiquent la maturité fonctionnelle observée, pas une certification scientifique ou terrain.

| Fonctionnalité | Maturité | Constat | Priorité de mise à niveau |
|---|---:|---|---:|
| Inventaire et saisie des tiges | 8/10 | Parcours riche et adapté au terrain ; historique détaillé des mesures encore absent | F-01 |
| Dendrométrie | 7/10 | Nombreux indicateurs et tarifs ; indice de station, domaines de validité et incertitudes à fiabiliser | F-02 |
| Martelage | 7,5/10 | Synthèse, qualité, valeur et simulations présentes ; cockpit multi-objectifs à construire | F-03 |
| Clinomètre | 8/10 | Capteurs, tangentes, moyenne et stabilité ; calibration et erreur instrumentale à renforcer | F-04 |
| GPS | 8/10 | Moyennage, MAD, précision et persistance ; protocole de mesure à documenter par tige | F-05 |
| Cartographie | 8/10 | Couches, tuiles offline, clustering, shapefile et mesures ; CRS et gros volumes à durcir | F-06 |
| IBP / biodiversité | 8/10 | Scoring, historique, radar et export ; impact du martelage sur l'IBP à intégrer | F-07 |
| Diagnostics stationnels | 7/10 | Station, flore, ripisylve, climat et corrélations ; certaines inférences restent heuristiques | F-08 |
| Prix et valorisation | 6,5/10 | Moteur à coefficients et breakdown ; millésime, valeur nette et produits à mieux séparer | F-09 |
| Exports | 8/10 | PDF, XLSX, CSV, GeoJSON et Shapefile ; passeport reproductible de parcelle manquant | F-10 |
| Offline-first | 7/10 | Données locales opérationnelles ; installation vérifiée, catalogue serveur à raccorder | F-11 |
| IA locale | 3/10 | `LocalBrainCore` est un moteur de règles/FTS, pas encore un LLM intégré | F-12 |
| GSIE Serveur / GSIE PC | 5/10 | Identité et première synchronisation de parcelles livrées ; pull, résolution des conflits et SDK complet restent à construire | F-13 |
| Tests métier | 7/10 | Nombreux tests unitaires ; peu de jeux réels, tests instrumentés et validations de précision | F-14 |
| Robustesse production | 6,5/10 | Base solide ; erreurs silencieuses, migrations, gros volumes et reprises réseau à durcir | F-15 |

#### Écarts fonctionnels prioritaires

- `PackManager.installPack()` réalise désormais un téléchargement et un checksum SHA-256 ; il reste à raccorder au catalogue serveur signé et à ajouter le rollback de migration de pack.
- `LocalBrainCore` fournit une intelligence locale déterministe, mais aucune orchestration LLM complète n'est encore branchée au parcours forestier.
- Les calculs dendrométriques doivent retourner `Success`, `Partial` ou `Failure`, au lieu de masquer certaines erreurs par des résultats vides.
- La page Âge / Martelage doit devenir le centre de décision, avec mesures, qualité, incertitudes, scénarios, valeur et sources.
- Les tests doivent évoluer de tests de formule vers des jeux de placettes de référence et des tests de concordance avec les sorties expertes.

---

## 3. PLAN D'EXÉCUTION TECHNIQUE

### 3.1 Standards de qualité

**Chaque correction ou feature doit** :
1. Passer `./gradlew testDebugUnitTest` avant et après
2. Passer `./gradlew lint` sans nouvelles erreurs
3. Passer `./gradlew assembleDebug` sans erreur
4. Être testée sur émulateur Android (API 26 + API 35)
5. Être testée sur Samsung S25 Ultra (appareil de référence)
6. Suivre les conventions de `CONTRIBUTING.md` et `global_rules.md`
7. Avoir un commit Conventional Commits (`type(scope): description`)
8. Ne pas introduire de dette technique supplémentaire

**Tests** :
- Tests unitaires : `./gradlew testDebugUnitTest`
- Tests instrumentés : `./gradlew connectedAndroidTest` (émulateur)
- Tests manuels : APK installé sur S25 Ultra, scénario métier complet
- Build release : `./gradlew assembleRelease` (vérifier ProGuard/R8)

### 3.2 Phase 0 — Blocages production (priorité absolue)

> **Objectif** : Rendre l'app déployable en production sans risque juridique ou sécurité.
> **Statut au 2026-07-01** : **9/12 FAIT**, 1 incertain, 2 non faits — voir colonne Statut.
> **Critère de sortie** : 0 CRITICAL restant, build release signé fonctionnel

| # | Action | Domaine | Issue | Effort | Statut 07-01 | Preuve |
|---|--------|---------|-------|--------|--------|--------|
| 0.1 | Activer `kotlin.incremental=true` dans gradle.properties | Build | B-C1 | 0.1j | ✅ FAIT | `gradle.properties:9` |
| 0.2 | Réactiver SQLCipher + clé Android Keystore + migration DB | Sécurité/RGPD | S-C1, R-C2 | 3j | ✅ FAIT | `ForestryDatabase.kt:151` (SupportFactory + DatabaseEncryptionService/Keystore) |
| 0.3 | Fiabiliser TLS et corriger SecureTileService | Sécurité | S-C2, R-M4 | 1j | ✅ FAIT | HTTPS obligatoire, autorités Android et aucun pin tiers statique ; placeholders retirés |
| 0.4 | Valider tableName dans GeoImportParser (whitelist regex) | Sécurité | S-C3 | 0.5j | ✅ FAIT | `GeoImportParser.kt:447-453` |
| 0.5 | Ajouter FLAG_SECURE sur MainActivity | Sécurité | S-H4 | 0.5j | ✅ FAIT | `MainActivity.kt:35-39` |
| 0.6 | Réécrire PRIVACY_POLICY.md (26 PII, base légale, transferts) | RGPD | R-C1 | 1j | ✅ FAIT | `PRIVACY_POLICY.md` réécrit le 2026-07-01 après audit factuel vs code : 8 erreurs corrigées (6 services réseau manquants, `operateurNom`/`psgNumero`/champs libres ajoutés, « Effacer toutes mes données » marqué à venir, purge auto cache GPS marquée à venir, §2.3 BackupWorker ZIP non chiffré ajouté, §3.2 PriceSyncWorker pas de cert pinning ajouté, contact RGPD renseigné) |
| 0.7 | Ajouter page consentement RGPD dans onboarding | RGPD | R-C3 | 2j | ✅ FAIT | `OnboardingScreen.kt:67,174-177,371-387` (page + bouton Decline + dialog) |
| 0.8 | Créer RECORD_OF_PROCESSING_ACTIVITIES.md | RGPD | R-C4 | 1j | ✅ FAIT | Fichier créé, 7 traitements (T-01 à T-07) |
| 0.9 | Documenter ou supprimer transferts Esri/USA (SCC) | RGPD | R-C5 | 1j | ✅ FAIT | `RECORD_OF_PROCESSING_ACTIVITIES.md:46` |
| 0.10 | Remplacer collectAsState() par collectAsStateWithLifecycle() (50+) | Perf/UI | P-C1, U-H1 | 2j | ✅ FAIT | 0 occurrence `collectAsState()` restante, 166 `collectAsStateWithLifecycle` |
| 0.11 | Downsampling images + intégrer Coil | Perf/UI | P-C2, U-H5 | 2j | ❌ PAS FAIT | Coil absent de `libs.versions.toml`/dépendances |
| 0.12 | Corriger test SecureHttpClientTest (méthode inexistante) | Sécurité | S-H1 | 0.5j | ✅ FAIT | `SecureHttpClientTest.kt` réécrit, toutes méthodes testées existent |

**Reste à faire pour clore la Phase 0** : 0.11 (Coil + downsampling images) — **0.6 est
désormais FAIT** (PRIVACY_POLICY.md réécrit le 2026-07-01 après audit factuel vs code).

**Note RGPD complémentaire** : l'audit 0.6 a révélé 2 lacunes à traiter en Phase 2 :
- Bouton « Effacer toutes mes données » centralisé (Phase 2.2 — les méthodes `deleteAll*()`
  existent par entité mais ne sont pas câblées dans l'UI Settings)
- Purge automatique du cache GPS (`purgeOlderThan()` existe dans `FloraFtsDao.kt:37` mais
  n'est jamais appelée — la politique §5 indique désormais honnêtement « à venir »)

### 3.3 Phase 1 — Corrections rapides high-impact

> **Objectif** : Qualité professionnelle (i18n propre, performance, UX).
> **Durée estimée** : 25 jours-homme
> **Statut au 2026-07-01** : **2/23 FAIT** (9%), 4 partiels, 4 incertains, 13 non faits — la
> Phase 0 a été priorisée à raison (sécurité/RGPD), mais la Phase 1 reste très largement à faire.
> **Critère de sortie** : 0 HIGH restant, i18n fonctionnel, pas de OOM

| # | Action | Domaine | Issue | Effort | Statut 07-01 |
|---|--------|---------|-------|--------|--------|
| 1.1 | Extraire 100+ chaînes FR codées en dur → strings.xml | i18n | I-C1 | 5j | ❌ PAS FAIT (53 `€` codés en dur restants) |
| 1.2 | Remplacer SimpleDateFormat(Locale.FRANCE) → DateFormat | i18n | I-C2 | 1j | ❌ PAS FAIT (71 occurrences `SimpleDateFormat`) |
| 1.3 | Remplacer € codé → NumberFormat.getCurrencyInstance() | i18n | I-C3 | 1j | ❌ PAS FAIT |
| 1.4 | Créer plurals.xml FR/EN | i18n | I-C4 | 1j | ✅ FAIT (`values/plurals.xml` + `values-fr/plurals.xml`, 4 plurals) |
| 1.5 | Compléter 22 chaînes manquantes en français | i18n | I-H1 | 0.5j | ⚠️ INCERTAIN — à revérifier avec `.devin/scratch/check_missing.py` |
| 1.6 | Ajouter key() aux LazyColumn (6 écrans) | UI/Perf | U-H4 | 1j | 🟡 PARTIEL (présent sur Parcelles/Placettes/IbpProjects/Martelage, absent ailleurs) |
| 1.7 | rememberSaveable pour formulaires (3 écrans) | UI | U-H2 | 1j | 🟡 PARTIEL (55 occurrences globales, pas vérifié sur les 3 écrans ciblés précisément) |
| 1.8 | contentDescription sur éléments interactifs | A11y | U-H3 | 2j | 🟡 PARTIEL (349 occurrences, ~100 encore à `null`) |
| 1.9 | Streaming exports (JsonWriter + CSV ligne par ligne) | Perf | P-C3 | 3j | 🟡 PARTIEL (CSV en ligne par ligne fait, export JSON pas encore en streaming `JsonWriter`) |
| 1.10 | Ajouter LIMIT/projection aux requêtes DAO | Perf | P-H3 | 2j | ⚠️ INCERTAIN — audit DAO non fait |
| 1.11 | Flow.first() → withTimeoutOrNull (30+ occurrences) | Perf | P-H2, U-H8 | 1j | ❌ PAS FAIT (2/103 migrés seulement) |
| 1.12 | BackupWorker : injecter DB + contraintes + idempotence | Misc | M-C2, M-H3, M-H4 | 1j | ❌ PAS FAIT (DB toujours créée en dur dans `doWork()`) |
| 1.13 | PriceSyncWorker : SecureHttpClient + timeout + backoff | Misc/Sécu | M-H5, M-H6, R-M3 | 1j | ❌ PAS FAIT (`OkHttpClient` standard, pas `SecureHttpClient`) |
| 1.14 | Mettre à jour build tools (AGP, Kotlin, KSP, Compose BOM) | Build | B-H1, B-H2 | 3j | 🟡 PARTIEL (Compose BOM à jour, AGP 8.2.2 et Kotlin 1.9.23 toujours obsolètes) |
| 1.15 | BlurView 2.0.5 → 3.2.0 | Build | B-H3 | 0.5j | ❌ PAS FAIT (version custom 2.0.6) |
| 1.16 | Créer workflow CI/CD release.yml | Build | B-H4 | 1j | ❌ PAS FAIT (aucun `.github/workflows/` — **CI/CD totalement absent, y compris tests/lint automatiques**) |
| 1.17 | Supprimer Lambert93.kt (unifier sur Lambert93Converter.kt) | GIS | G-M1 | 0.5j | ❌ PAS FAIT (duplication toujours présente) |
| 1.18 | Restaurer GeoPackageExporter (427 lignes, export OGC QGIS) | GIS/Export | APK-v2.1 | 3j | ❌ PAS FAIT |
| 1.19 | Restaurer AutecologyExpansion dans AutecologyStubs.kt | Domain | APK-v2.1 | 1j | ❌ PAS FAIT |
| 1.20 | Restaurer TappedDiagnosticInfo dans MapScreen | UI | APK-v2.1 | 1j | ❌ PAS FAIT |
| 1.21 | Restaurer ReferenceMode dans IbpDiagnosticScreen | IBP | APK-v2.1 | 0.5j | ❌ PAS FAIT |
| 1.22 | Évaluer EcologyFertilityTab vs DiagnosticMenu (restaurer ou confirmer remplacement) | UI | APK-v2.1 | 0.5j | ⚠️ INCERTAIN — fichiers non retrouvés, décision à formaliser |
| 1.23 | Évaluer CampaignData (historique campagnes martelage — restaurer si utile) | Domain | APK-v2.1 | 0.5j | ❌ PAS FAIT |

**Recommandation immédiate** : traiter en priorité 1.16 (CI/CD absent = aucun filet de
sécurité automatique sur les prochains commits des agents) et 1.1-1.3 (i18n, cohérent avec
le score encore faible 4→5/10).

### 3.4 Phase 2 — Consolidation

> **Objectif** : Standard "qualité ONF".
> **Durée estimée** : 30 jours-homme
> **Critère de sortie** : 0 HIGH/MEDIUM critique restant, pagination, tests 60%+

| # | Action | Domaine | Effort |
|---|--------|---------|--------|
| 2.1 | Implémenter Paging 3 pour grandes listes | Perf | 5j |
| 2.2 | Droit à l'effacement centralisé ("Effacer mes données") | RGPD | 2j |
| 2.3 | Politique de rétention + suppression automatique | RGPD | 3j |
| 2.4 | Documenter décision automatisée + avertissement UI | RGPD | 1j |
| 2.5 | Désigner DPO + coordonnées dans politique | RGPD | 0.5j |
| 2.6 | Version catalog libs.versions.toml | Build | 2j |
| 2.7 | Décommenter KSP Room args (schema export, incremental) | Build | 0.5j |
| 2.8 | Narrow ProGuard keep rules | Build | 1j |
| 2.9 | Jacoco coverage reporting | Build | 1j |
| 2.10 | Migrer Accompanist → Compose platform | Build | 2j |
| 2.11 | Timeout sur Flow GPS | Perf | 0.5j |
| 2.12 | Remplacer LocationManager par FusedLocationProviderClient (3 écrans) | Perf | 1j |
| 2.13 | Clustering marqueurs GPS sur carte | Perf | 3j |
| 2.14 | Détecteur root/debug | Sécurité | 1j |
| 2.15 | Auth biométrique optionnelle | Sécurité | 2j |
| 2.16 | Chiffrer DataStore (EncryptedSharedPreferences) | Sécurité | 1j |
| 2.17 | Sanitiser logs DEBUG (pas de coordonnées GPS) | Sécurité | 1j |
| 2.18 | ProGuard : supprimer Log.i/w/e en release | Sécurité | 0.1j |
| 2.19 | FormulaParser : limites longueur/complexité/timeout | Misc | 1j |
| 2.20 | Tests FormulaParser : edge cases | Misc | 1j |
| 2.21 | Extraire sous-fonctions DataInterpretationEngine | Misc | 1j |

### 3.5 Programme DENDRO-EXCELLENCE — moteur de référence terrain

> **Objectif** : faire de GeoSylva une chaîne de mesure dendrométrique professionnelle, explicable, comparable et supérieure aux simples écrans de cubage.
> **Déclencheur** : après la stabilisation sécurité/build de la Phase 0 et en parallèle de la Phase 1, sans attendre le cloud.
> **Principe** : l'IA enrichit les mesures et les scénarios ; elle ne remplace ni les formules validées ni la validation humaine.

#### 3.5.1 Socle scientifique et gouvernance des calculs

| ID | Action | Livrable | Priorité |
|---|---|---|---|
| D-01 | Créer un registre des méthodes dendrométriques | Méthode, formule, unité, source, domaine de validité, version, auteur | P0 |
| D-02 | Séparer volume bois fort, marchand, bois d'œuvre, industrie, énergie et rebut | `VolumeBreakdown` versionné et exportable | P0 |
| D-03 | Corriger l'indice de station | Hdom à âge de référence, interpolation et extrapolation explicitement signalée | P0 |
| D-04 | Corriger la rotation technique | AMA, ACA, intersection, rotation financière et sylvicole séparées | P0 |
| D-05 | Interdire les tables de production hors domaine silencieux | Warning bloquant ou validation experte | P0 |
| D-06 | Remplacer les `catch Throwable` silencieux | Résultats `Success / Partial / Failure` avec codes d'erreur | P0 |
| D-07 | Uniformiser les alias d'essences, produits et qualités | Référentiel canonique partagé par cubage, prix, exports et IA | P0 |
| D-08 | Créer des jeux de référence dorés | Cas chêne, hêtre, douglas, résineux, futaie régulière et irrégulière | P0 |

#### 3.5.2 Modèle de mesure et jumeau temporel

| ID | Action | Livrable | Priorité |
|---|---|---|---|
| D-09 | Créer `TreeMeasurement` séparé de `Tige` | Historique de chaque mesure, méthode, instrument, opérateur, précision | P0 |
| D-10 | Ajouter le protocole d'inventaire | Surface, rayon, relascope, placette, coefficient d'expansion, seuil de comptage | P0 |
| D-11 | Ajouter la provenance des hauteurs | Mesurée, estimée, tarifée, LiDAR, photo, dendromètre, confiance | P0 |
| D-12 | Ajouter les statuts écologiques et sylvicoles | Dominant, codominant, dominé, arbre d'avenir, habitat, sanitaire, réserve | P1 |
| D-13 | Créer les snapshots d'inventaire | Comparaison avant/après, croissance, mortalité, recrutement, prélèvement | P1 |
| D-14 | Construire le jumeau numérique de parcelle | Carte + peuplement + historique + simulations + sources | P1 |
| D-15 | Ajouter une piste d'audit immuable | Qui, quoi, quand, pourquoi, ancienne valeur, nouvelle valeur, validation | P1 |

#### 3.5.3 Incertitude et contrôle qualité actif

| ID | Action | Livrable | Priorité |
|---|---|---|---|
| D-16 | Propager l'incertitude des mesures | Intervalles sur Hdom, G/ha, V/ha, valeur et carbone | P0 |
| D-17 | Calculer l'erreur d'échantillonnage | Écart-type, coefficient de variation, marge d'erreur, nombre de placettes recommandé | P0 |
| D-18 | Détecter les valeurs aberrantes | Diamètre/hauteur/essence incohérents, doublons, unités erronées, GPS douteux | P0 |
| D-19 | Créer le score de complétude | Hauteurs, qualités, GPS, volumes, prix, produits, sources | P0 |
| D-20 | Ajouter la mesure à valeur d'information maximale | L'app recommande l'arbre ou la variable qui réduira le plus l'incertitude | P1 |
| D-21 | Créer un mode re-mesure | L'opérateur peut confirmer, corriger ou rejeter une anomalie avec justification | P1 |
| D-22 | Utiliser Monte-Carlo pour les scénarios | Fourchette de résultats, pas seulement une valeur moyenne | P2 |

#### 3.5.4 Expérience terrain radicalement améliorée

| ID | Action | Livrable | Priorité |
|---|---|---|---|
| D-23 | Refaire la page Âge / Martelage en cockpit | Inventaire, structure, volumes, valeur, martelage, IA, sources | P0 |
| D-24 | Concevoir une saisie en moins de trois secondes | Compas, diamètre, essence, qualité, décision, photo et GPS en parcours court | P0 |
| D-25 | Ajouter un mode gants/pluie/lumière forte | Grands contrôles, contraste, retour haptique/sonore, verrouillage anti-erreur | P1 |
| D-26 | Ajouter la commande vocale offline | « Chêne 45, qualité B, à conserver » → confirmation visuelle | P1 |
| D-27 | Ajouter le mode martelage carte + terrain | Navigation vers l'arbre, distance, orientation, statut et validation | P1 |
| D-28 | Ajouter le mode comparaison de scénarios | Avant coupe, scénario A/B/C, après coupe simulée, objectif atteint ou non | P1 |
| D-29 | Exporter un rapport professionnel audit-able | Méthodes, sources, valeurs, incertitudes, anomalies et signatures | P1 |

#### 3.5.5 Intelligence artificielle forestière contrôlée

| ID | Action | Livrable | Priorité |
|---|---|---|---|
| D-30 | Créer un orchestrateur d'outils GeoSylva | Le LLM appelle uniquement des outils typés et validés | P0 |
| D-31 | Assistant dendrométrique offline | Explication des calculs, aide au protocole, rapport local | P1 |
| D-32 | Détection d'anomalies par ML | Modèle léger on-device, retour explicable, pas de décision automatique | P1 |
| D-33 | IA multimodale optionnelle | Photo, audio, mesure, carte et contexte de parcelle | P2 |
| D-34 | Génération de scénarios de martelage | Scénario économique, sylvicole, biodiversité, résilience, compromis explicites | P1 |
| D-35 | RAG local versionné | Guides ONF/CNPF, référentiels régionaux, tarifs et règles embarqués | P1 |
| D-36 | Registre de preuve IA | Prompt, modèle, outils appelés, sources, version, réponse et validation humaine | P1 |
| D-37 | Escalade GSIE Serveur / GSIE PC | Calcul lourd ou modèle avancé seulement avec consentement et réseau | P2 |

#### 3.5.6 Idées différenciantes à fort potentiel

1. **Copilote de mesure** : GeoSylva ne se contente pas d'enregistrer ; il indique quelle mesure est la plus utile pour améliorer la précision du volume ou de la valeur.
2. **Jumeau de martelage** : chaque arbre possède un état avant coupe, un ou plusieurs scénarios et un état après validation, sans écrasement de l'historique.
3. **Score de confiance forestier** : une parcelle peut être « bonne pour décision », « exploitable avec réserves » ou « à compléter », selon la qualité réelle des données.
4. **Détection de dérive opérateur** : l'application repère qu'un opérateur mesure systématiquement des hauteurs ou diamètres différents d'un protocole attendu.
5. **Calibration locale collaborative** : les mesures validées sur le terrain peuvent améliorer les modèles régionaux sans transmettre les données sensibles brutes.
6. **Mode témoin** : une partie de la parcelle reste non martelée dans le jumeau pour comparer la dynamique future.
7. **Optimiseur multi-objectifs** : maximiser simultanément revenu net, croissance résiduelle, biodiversité, résilience et limitation du prélèvement.
8. **Rapport contradictoire** : l'IA génère à la fois les arguments favorables et défavorables à une intervention, avec les données qui départagent les scénarios.
9. **Passeport de parcelle** : export portable contenant données, méthodes, sources, historique, modèles et empreinte de vérification.
10. **Laboratoire de validation ONF/CNPF** : benchmark aveugle sur placettes de référence, comparaison aux mesures expertes et seuils d'acceptation publics.

#### 3.5.7 Critères de sortie DENDRO-EXCELLENCE

- Chaque valeur dendrométrique possède une méthode, une source, une unité, une version et un domaine de validité.
- Les résultats incomplets ou extrapolés sont visibles avant export.
- L'incertitude est affichée pour les indicateurs principaux.
- Un inventaire peut être entièrement réalisé en mode avion.
- Un scénario de martelage est réversible et historiquement traçable.
- Les résultats sont reproductibles à partir du passeport de parcelle.
- Le LLM ne peut pas modifier directement une mesure ou valider seul un martelage.
- Les jeux de référence et tests de non-régression couvrent chaque méthode de cubage.
- La comparaison terrain/GSIE Serveur/GSIE PC conserve les versions et les écarts.

### 3.6 Phase 3 — Excellence et écosystème

> **Objectif** : Au-delà du standard ONF, préparation de l'écosystème.
> **Durée estimée** : 25+ jours-homme

| # | Action | Domaine | Effort |
|---|--------|---------|--------|
| 3.1 | Algorithme géodésique pour surface polygone (Karney) | GIS | 2j |
| 3.2 | Intégrer PROJ.4 pour reprojection CRS généralisée | GIS | 3j |
| 3.3 | Interpolation DEM bilinéaire/bicubique | GIS | 2j |
| 3.4 | Étendre WktUtils (LINESTRING, POLYGON, SRID) | GIS | 2j |
| 3.5 | Audit accessibilité complet (touch targets, WCAG AA) | A11y | 3j |
| 3.6 | Couverture tests 35% → 60% (domain/business) | Tests | 10j |
| 3.7 | Lint rules pour détecter chaînes codées en dur | i18n | 1j |
| 3.8 | Profiling Android Profiler pour goulots résiduels | Perf | 2j |

### 3.7 Phase 4 — Extension écosystème (post-financement)

> **Objectif** : Transformer GeoSylva en plateforme.
> **Déclencheur** : financement obtenu (French Tech + France 2030 NA + CIR/JEI)

| # | Action | Domaine | Effort |
|---|--------|---------|--------|
| 4.1 | Sync cloud (Supabase/PostgreSQL+PostGIS) | Infra | 10j |
| 4.2 | Version web/desktop (Compose Multiplatform ou PWA) | Platform | 15j |
| 4.3 | Portage iOS (KMP ou Kotlin Multiplatform) | Platform | 20j |
| 4.4 | Intégration compas BLE (Masser/Codimex) | Hardware | 3j |
| 4.5 | Saisie vocale dendrométrique (Vosk FR offline) | IA/UX | 2j |
| 4.6 | IA forestière — API Mistral (assistant martelage) | IA | 5j |
| 4.7 | IA forestière — NVIDIA NIM self-hosted (Mistral 7B) | IA | 5j |
| 4.8 | IA forestière — fine-tuning sur données ONF/CNPF | IA | 10j |
| 4.9 | IA forestière — on-device (Qwen 2.5 3B / SmolLM3) | IA | 5j |
| 4.10 | Pipeline QGISIA+ × GeoSylva (terrain → desktop → terrain) | Écosystème | 10j |
| 4.11 | Partenariat Pixstart (satellite ↔ terrain, API sync) | Partenariat | 5j |
| 4.12 | Marketplace de tarifs (prix du bois par région, MAJ mensuelle) | Business | 5j |

---

### 3.8 Programme FUNCTIONAL-EXCELLENCE — application terrain complète

> **Objectif** : fermer les écarts fonctionnels identifiés par l'audit global et transformer GeoSylva en outil terrain fiable du premier geste à la décision et à l'export.

| ID | Action | Livrable | Critère de sortie |
|---|---|---|---|
| F-01 | Historique des mesures par arbre | `TreeMeasurement` + provenance + opérateur + instrument | Toute modification est réversible et historisée |
| F-02 | Fiabiliser la chaîne dendrométrique | Hdom, âge de référence, AMA/ACA, tarifs et validité | Aucun résultat hors domaine sans avertissement explicite |
| F-03 | Refaire le cockpit Âge / Martelage | Inventaire, structure, volume, prix, scénarios, IA, sources | Décision complète en moins de trois écrans principaux |
| F-04 | Professionnaliser le clinomètre | Calibration, pente, hauteur marchande, erreur | Mesure accompagnée d'une précision et d'un statut qualité |
| F-05 | Passeport GPS de la tige | Méthode, précision, nombre de lectures, rejet, correction | Position et incertitude exportées ensemble |
| F-06 | Durcir la cartographie terrain | CRS, gros volumes, géométries complexes, cache offline | Import/export fiable sur jeux SIG de référence |
| F-07 | Relier IBP et martelage | Impact de chaque scénario sur biodiversité et microhabitats | Chaque scénario affiche son impact écologique |
| F-08 | Encadrer les diagnostics heuristiques | Niveau indicatif, données manquantes, sources et limites | Aucune inférence territoriale présentée comme certitude |
| F-09 | Valeur économique complète | Produits séparés, prix datés, coûts, valeur nette, fourchette | Valeur brute et nette toujours distinguées |
| F-10 | Passeport de parcelle | Données, méthodes, sources, versions, erreurs, incertitudes | Inventaire reproductible sur un autre appareil |
| F-11 | Packs réellement offline | Téléchargement signé, checksum, reprise, suppression, rollback | Un pack installé fonctionne sans réseau et peut être vérifié |
| F-12 | Assistant IA contrôlé | LLM local + appels d'outils typés + RAG local | 100 % des réponses IA sourcées et validables |
| F-13 | GSIE Mobile ↔ Serveur ↔ PC | Identité et parcelles local→serveur livrées ; restent pull, résolution des conflits, calcul lourd et retours versionnés | Reprise réseau sans perte ni écrasement silencieux |
| F-14 | Jeux de données de référence | Placettes dorées et scénarios de martelage annotés | Non-régression scientifique automatisée |
| F-15 | Tests terrain réels | GPS, clinomètre, batterie, pluie, gants, gros inventaires | Validation sur appareils Android bas, moyen et haut de gamme |
| F-16 | Mode capture ultra-rapide | Parcours une main, raccourcis, voix offline, retour haptique | Tige standard saisie en moins de trois secondes |
| F-17 | Contrôle qualité actif | Anomalies, doublons, unités, valeurs impossibles, re-mesure | Les erreurs sont détectées avant export |
| F-18 | Comparaison temporelle | Avant/après, croissance, mortalité, recrutement, martelage | Deux inventaires comparables par parcelle |
| F-19 | Rapport contradictoire IA | Arguments pour/contre chaque intervention | Le forestier voit les compromis et les données manquantes |
| F-20 | Certification progressive | Revue expert, benchmark aveugle, seuils de qualité, journal | Version du moteur et statut de validation visibles |

#### Ordre de réalisation recommandé

```text
F-01/F-02/F-17
        ↓
F-03/F-09/F-10
        ↓
F-04/F-05/F-06/F-15
        ↓
F-11/F-13/F-18
        ↓
F-12/F-19/F-20
```

L'IA et les connexions GSIE ne doivent être activées à grande échelle qu'après fiabilisation de la chaîne de mesure et des erreurs. Le premier livrable produit doit rester utilisable en mode avion, exportable et vérifiable sans serveur.

## 4. FINANCEMENT ET AIDES

> **Détail exhaustif** : voir `RESEARCH_OPPORTUNITIES.md` §4 (35+ aides, 150+ entrées).
> **Potentiel total** : 350K€ - 1.2M€ sur 24 mois (après passage SASU/EURL) + ~600 000$ crédits cloud gratuits immédiats.

### 4.1 Crédits cloud gratuits (immédiat, auto-entrepreneur OK)

| Programme | Montant | Conditions | Statut |
|---|---|---|---|
| **NVIDIA Inception** | 100K$ AWS + 150K$ Nebius + NIM gratuit + 30% discount GPU | Startup IA, gratuit, pas d'equity | **Postuler maintenant** |
| **Microsoft for Startups** | 150 000$ Azure + support | Startup <5 ans, pas d'equity | **Postuler maintenant** |
| **Google for Startups Cloud** | 350 000$ GCP (AI startups) | Startup <5 ans | **Postuler maintenant** |
| **AWS Activate** | 200 000$ AWS | Startup <10 ans | **Postuler maintenant** |
| **HuggingFace Spaces** | Demo IA gratuite | Compte gratuit | Immédiat |
| **Total crédits cloud** | **~600 000$** | | |

### 4.2 Aides locales Poitiers / Nouvelle-Aquitaine

| Dispositif | Montant | Conditions | Échéance |
|---|---|---|---|
| **Neoloji Technopole** (Poitiers) | Accompagnement gratuit + pépinière 7€/m² | Projet innovant NA | Permanent |
| **France 2030 NA — Projets d'Avenir** | Jusqu'à 50% dépenses | EI/PME/ETI en NA | **30/09/2026** |
| **Région NA — Aide innovation start-up** | Subvention 45% dépenses | Start-up <5 ans NA | Permanent |
| **Région NA — Amorçage Start-Up** | Jusqu'à 3M€ | Start-up <5 ans, 50K€ fonds propres | Permanent |
| **ADEME Santé sols forestiers** | 150-250K€ | Collectifs acteurs forêt | Avril-Juillet 2026 |
| **POP Incub** (ADI NA) | Accompagnement gratuit | ESS/innovation sociale | 02/11/2025 |

### 4.3 Aides nationales

| Dispositif | Montant | Conditions | Échéance |
|---|---|---|---|
| **Bourse French Tech** | 30-50K€ | <1 an, innovation | Permanent |
| **Concours i-Lab** | Jusqu'à 600K€ | <2 ans, deep tech | **Février 2026** |
| **French Tech Seed** (OC) | 50-500K€ | <3 ans, après levée 25K€ | Permanent |
| **Prêt d'amorçage BPI** | Variable | PME <8 ans | Permanent |
| **French Tech Tremplin** | 15K€ + incubation | Conditions sociales | AAP 2026 |

### 4.4 Aides fiscales (après passage SASU/EURL)

| Dispositif | Avantage | Conditions |
|---|---|---|
| **CIR** | 30% dépenses R&D (20% PME depuis 02/2025, plafond 400K€/an) | Agrément CIR |
| **JEI** | Exonération charges + IS | <8 ans, R&D >20% charges (2025), PME |
| **CII** | 20% dépenses innovation (plafond 120K€) | PME, prototype/pilote |

### 4.5 Recommandation critique : passage en SASU/EURL

Le statut auto-entrepreneur bloque **80% des aides financières substantielles**. Le passage en SASU est fortement recommandé :
- **Coût** : ~200-500€ de formalités
- **Débloque** : Bourse French Tech, JEI, CIR/CII pleinement, aides régionales, Concours i-Lab, ADEME
- **Timing** : idéalement avant dépôt des premiers dossiers (mois 2)

### 4.6 Stratégie de financement séquencée

```
Semaine 1-2 : NVIDIA Inception + Microsoft + Google + AWS + HuggingFace
             → ~600 000$ crédits cloud gratuits
    ↓
Semaine 3   : Contact Neoloji Technopole (Poitiers)
    ↓
Semaine 4   : Préparation passage SASU/EURL
    ↓
Mois 2      : Bourse French Tech + Neoloji promo startups
    ↓
Mois 3-4    : France 2030 NA + Région NA aide innovation
    ↓
Mois 5-6    : CIR/JEI rescrit + ADEME sols forestiers (avril 2026)
    ↓
Mois 7-9    : Concours i-Lab (deadline février 2026)
    ↓
Mois 10-12  : Prêt d'amorçage BPI + Bordeaux Angels
    ↓
Mois 13-18  : French Tech Seed + Horizon Europe
```

---

## 5. ÉCOSYSTÈME ET PARTENARIATS

### 5.1 Écosystème produit

```
┌─────────────────────────────────────────────────────────────┐
│                    PLATEFORME GEOSYLVA                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  GeoSylva Mobile          QGISIA+ Desktop                    │
│  (Android + iOS)          (QGIS Plugin + IA)                 │
│  ├── Saisie terrain       ├── Analyse IA                     │
│  ├── Compas BLE           ├── Cartographie avancée           │
│  ├── Saisie vocale        ├── Détection couronnes            │
│  ├── GPS précision        ├── Rapport PSG                    │
│  ├── IBP CNPF             ├── Export vers GeoSylva           │
│  └── Export →             └── ← Import de GeoSylva           │
│                                                              │
│  Pixstart Satellite        IA Forestière                      │
│  ├── Monitoring continu   ├── Mistral 7B fine-tuné           │
│  ├── Alertes dégradation  ├── Assistant martelage            │
│  ├── Vue large échelle    ├── Diagnostic station auto        │
│  └── Calibration terrain  ├── Reconnaissance essence         │
│      ↑↓                    └── Prédiction accroissement       │
│      (API sync GeoSylva)                                     │
│                                                              │
├─────────────────────────────────────────────────────────────┤
│  PARTENAIRES : ONF · CNPF · Mistral AI · Masser · Codimex    │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 Partenariats ciblés

| Partenaire | Type | Synergie | Approche |
|---|---|---|---|
| **Pixstart** (FR, satellite) | Technique | Satellite ↔ terrain, calibration | Contact direct, complémentarité évidente |
| **Masser** (FI, compas digital) | Hardware | Intégration BLE native | API Bluetooth documentée |
| **Codimex** (IT, compas digital) | Hardware | Intégration BLE native | App Android existe déjà |
| **CNPF** | Standards | Label "compatible CNPF" | Présenter IBP CNPF officiel implémenté |
| **ONF** | Distribution | Traction + légitimité | Via expert ONF consultant ou France 2030 |
| **Mistral AI** | IA | IA forestière française souveraine | Pitch "3M propriétaires + souveraineté" |
| **3Liz / Oslandia** | SIG | Intégration QGIS / PostGIS | Éditeurs SIG français |
| **Coopératives forestières NA** | Marché | Traction utilisateurs | Vente directe |

### 5.3 Technologies open source à exploiter

> **Détail exhaustif** : voir `RESEARCH_OPPORTUNITIES.md` §2 (40+ libraries) et §3 (30+ techno IA).

**IA / LLM** :
- Mistral 7B Instruct (Apache 2.0) — fine-tuning forêt française (QLoRA, GPU 6GB)
- Llama 3.1 8B — via NVIDIA NIM gratuit
- SmolLM3 3B (Apache 2.0) — on-device offline terrain (15 tok/s Samsung S22)
- Qwen 2.5 7B — multilingue compact
- Vosk FR (50MB) — saisie vocale offline
- Android SpeechRecognizer — saisie vocale native (Android 13+)

**Vision — identification essences** :
- PlantNet API (CIRAD/INRAE FR) — 35 000+ espèces
- PureForest (IGN/HuggingFace) — 135 569 patches, 13 essences françaises
- BarkVN-50 — 5 678 images écorce, 50 espèces
- ONNX Runtime Android — inference on-device
- TensorFlow Lite — classification mobile
- MediaPipe — vision temps réel caméra

**GIS / Android** :
- JTS Topology Suite (EPL-2.0) — géométrie vectorielle, WKT/WKB
- Proj4J (EPL-2.0) — transformations CRS (WGS84 ↔ L93 ↔ UTM)
- Spatial K (MapLibre, BSD-3) — GeoJSON, GPX, Turf.js en Kotlin
- GeoPackage Android (Public Domain) — export OGC GeoPackage
- MapLibre GL Android (BSD-2) — cartes vectorielles

**Foresterie / Dendrométrie** :
- LERFoB Forest Tools (LGPL-3.0) — calculs volume/biomasse/carbone français (Bouchon, FrenchCommercialVolume2020)
- CAT Carbon Accounting (LGPL-3.0) — bilan carbone forestier par compartiment

**Bluetooth / Hardware** :
- Kotlin BLE Library (Nordic, MIT) — wrapper coroutines BLE
- Blessed Kotlin (MIT) — BLE compact Android 9+

**Performance / Data** :
- Paging 3 — listes paginées (10k+ tiges)
- Coil — chargement images + cache
- SQLCipher 4.16.0 — chiffrement DB (CRITICAL Phase 0)

**Export** :
- PdfBox Android (Apache 2.0) — génération rapports PDF
- Android GPX Parser (Apache 2.0) — import/export GPX

**Infra cloud** :
- Supabase Kotlin (Apache 2.0) — backend sync cloud (Postgres + Auth + Realtime)
- PostgreSQL + PostGIS — stockage spatial cloud
- Ollama / vLLM / llama.cpp — serving LLM self-hosted
- MinIO — stockage photos S3

**GIS / QGIS desktop** :
- GeoAI plugin — détection arbres IA (DeepForest, SAM3)
- Netflora plugin — inventaire forestier drone + IA
- TreeEyed plugin — monitoring arbres IA
- DeepForest — modèle Python détection couronnes

**Apps forestières inspiration** :
- OpenForis Arena Mobile (FAO, MIT) — workflow collecte moderne
- Geopaparazzi (GPL-3.0) — cartes offline + formulaires terrain
- Forest Sentry (MIT) — IA on-device santé plantes
- GeoVision (MIT) — interface GIS Compose moderne

### 5.4 Hardware IoT forestier

> **Détail exhaustif** : voir `RESEARCH_OPPORTUNITIES.md` §5 (21 devices, plan d'intégration BLE).

**Compas Bluetooth (priorité #1)** :
- Codimex E-1 Caliper (Pologne, ~350€) — abordable, app FR native
- Masser BT Caliper (Finlande, ~1 300€) — référence qualité
- Haglöf Digitech BT (Suède, ~1 600€) — premium, app Haglof Link gratuite

**Hypsomètres laser** :
- Nikon Forestry Pro II (~500€) — manuel, pas de BLE
- TruPulse 200i (~1 200€) — BLE + Classic, doc protocole disponible
- Haglöf Vertex Laser Geo 2 (~3 500€) — tout-en-un laser+ultrason+GPS+compas

**GPS externe** :
- Emlid Reach RX2 (~1 300€) — RTK centimétrique
- Garmin GPSMAP 65s (~450€) — multi-fréquence

**Autres** :
- DJI Mavic 3 Multispectral (~4 800€) — drone NDVI forestier
- FLIR ONE (~400€) — caméra thermique smartphone (stress hydrique)
- Netatmo Weather Station (~180€) — station météo connectée (FR)

**Plan d'intégration** :
```
Mois 1-2 : Acheter Codimex E-1 (350€) + reverse engineering BLE
Mois 3-4 : MVP intégration BLE (Kotlin BLE Library Nordic)
Mois 5-6 : Extension Masser + Haglöf + documentation
Mois 7-8 : GPS externe NMEA 0183
Mois 9-12 : Hypsomètres BLE (TruPulse, Vertex Laser)
```

**ROI** : 30% gain de temps inventaire, 50% réduction erreurs, amortissement 6-12 mois.

---

## 6. BUSINESS MODEL

### 6.1 Modèle hybride (recommandé)

| Offre | Prix | Cible | Revenu |
|---|---|---|---|
| **Licence de base** | 200-400€ perpétuel | Expert indépendant, bureau d'étude | One-shot |
| **Abonnement premium** | 15-30€/mois | Coopérative, gestionnaire | Récurrent |
| **Marketplace tarifs** | 10€/mois | Tous | Récurrent |
| **Services** | Sur devis | Formation, intégration, custom | One-shot |

### 6.2 Projections (réalistes)

| Période | Utilisateurs | CA | Statut |
|---|---|---|---|
| An 1 (beta + lancement) | 50-100 payants | 20 000-40 000€ | Traction initiale |
| An 2 (sync cloud + premium) | 200-300 | 50 000-80 000€ récurrent | Croissance |
| An 3 (web + iOS + IA) | 500-1000 | 100 000-200 000€ récurrent | Scale |

### 6.3 Valorisation (si vente)

- Sans traction : 30 000-80 000€
- Avec 100 utilisateurs payants : 100 000-200 000€
- Avec 500+ utilisateurs + partenariats : 300 000-500 000€

---

## 7. STANDARDS DE DÉVELOPPEMENT

### 7.1 Workflow par correction/feature

1. **Lire** le fichier à modifier + comprendre le contexte
2. **Vérifier** les callers et le contrat
3. **Implémenter** la correction
4. **Tester** : `./gradlew testDebugUnitTest`
5. **Lint** : `./gradlew lint`
6. **Build** : `./gradlew assembleDebug`
7. **Émulateur** : tester sur API 26 + API 35
8. **S25 Ultra** : test manuel scénario métier
9. **Commit** : Conventional Commits (`type(scope): description`)
10. **Documenter** : mettre à jour CHANGELOG.md si feature/fix user-facing

### 7.2 Conventions de code

Voir `CONTRIBUTING.md` et `global_rules.md` :
- Fonctions : max 30 lignes, max 3 paramètres
- Complexité cyclomatique : max 5
- Pas de code commenté, pas de magic numbers
- Pas de `!!` en Kotlin
- Error handling : log avant propager, jamais swallow
- Tests : Arrange → Act → Assert, un assert logique par test
- Commits : `type(scope): description` (max 72 chars)

### 7.3 Tests

| Type | Commande | Fréquence |
|---|---|---|
| Unitaires | `./gradlew testDebugUnitTest` | Après chaque modification |
| Lint | `./gradlew lint` | Avant commit |
| Build debug | `./gradlew assembleDebug` | Avant commit |
| Build release | `./gradlew assembleRelease` | Avant merge main |
| Instrumentés | `./gradlew connectedAndroidTest` | Avant release |
| Manuel S25 Ultra | Installation APK + scénario | Avant release |

### 7.4 Appareil de référence

- **Samsung Galaxy S25 Ultra**
  - Android 15 (API 35)
  - Écran 6.9" QHD+ 144Hz
  - GPS: GPS, GLONASS, Beidou, Galileo, QZSS, NavIC
  - Capteurs: accéléromètre, gyroscope, magnétomètre
  - Caméra: 200MP + téléobjectif
- **Émulateurs** :
  - API 26 (minSdk) — compatibilité
  - API 35 (targetSdk) — courant

---

## 8. PITCHS

### 8.1 Pitch investisseur (BPI, business angels)

> Marché de 3 millions de propriétaires forestiers français sans outil mobile professionnel. GeoSylva est une app Android fonctionnelle couvrant le workflow complet : inventaire, martelage, IBP CNPF, cartographie, exports SIG. 50 utilisateurs payants en beta. Éligible ADEME et France 2030. Recherche 200k€ pour iOS + sync cloud + IA forestière.

### 8.2 Pitch mission (ADEME, ONF, CNPF)

> Outil de terrain pour la gestion forestière durable, conforme aux standards CNPF, IA française souveraine, objectif 100 000 forestiers équipés d'ici 5 ans. Réduire la fracture numérique en foresterie française. Préserver les forêts françaises par une gestion data-driven.

### 8.3 Pitch tech (NVIDIA, Microsoft, Mistral)

> IA forestière française fine-tunée sur données ONF/CNPF, hébergée sur Azure avec NVIDIA NIM, modèle Mistral 7B souverain. Cas d'usage : assistant martelage, diagnostic station, reconnaissance essence. 3M de propriétaires forestiers, marché underserved.

---

## 9. SUIVI ET MÉTRIQUES

### 9.1 KPIs techniques

| KPI | Cible | Actuel (06-29) | Actuel (07-01) |
|---|---|---|---|
| CRITICAL issues | 0 | 40 | ≥4 confirmées résolues (SQLCipher, cert pinning, injection SQL, FLAG_SECURE) sur 40 — 36 non re-vérifiées individuellement |
| HIGH issues | 0 | 58 | non re-vérifié individuellement |
| Tests unitaires | 0 échec | non mesuré | **467 tests, 0 échec, 0 erreur** (`testDebugUnitTest`) |
| Couverture tests | 60%+ | 35% | non recalculée (35% à confirmer) |
| Build time | <2min | ~5min (incremental=false) | `kotlin.incremental=true` actif — build ~6-7min observé (KSP + compile, à profiler) |
| OOM crashes | 0 | Risque élevé | Coil toujours absent — risque inchangé |
| i18n chaînes en dur (€, dates) | 0 | 100+ | 53 `€` + 71 `SimpleDateFormat` codés en dur toujours présents |
| RGPD conformité | Conforme | Non conforme | Nettement amélioré (registre traitements, consentement onboarding, transferts documentés) — `PRIVACY_POLICY.md` reste à vérifier en détail |
| CI/CD | Tests+lint+build auto | Partiel | Workflow `.github/workflows/ci.yml` présent ; à vérifier après publication GitHub et à compléter par couverture réelle |
| Complétude dendrométrique | 100% des résultats avec provenance | Non mesuré | À instrumenter dans D-19 |
| Résultats avec incertitude | 100% des V/ha, G/ha, Hdom et valeurs | Absent/partiel | Objectif D-16/D-17 |
| Reproductibilité d'un inventaire | 100% depuis le passeport de parcelle | Non disponible | Objectif D-15/D-29 |
| Erreurs silencieuses de calcul | 0 | Présence de fallback/catch à auditer | Objectif D-06 |
| Temps de saisie d'une tige | < 3 secondes en mode rapide | Non mesuré | Objectif D-24 |
| Mesures à recontrôler utiles | Réduction maximale de l'incertitude | Non disponible | Objectif D-20 |
| Scénarios de martelage réversibles | 100% | Partiel | Objectif D-28 |
| Réponses IA sourcées | 100% | Non disponible | Objectif D-30/D-36 |

### 9.2 KPIs business

| KPI | Cible An 1 | Cible An 2 |
|---|---|---|
| Utilisateurs payants | 50-100 | 200-300 |
| CA | 20-40k€ | 50-80k€ |
| NPS | >40 | >50 |
| Churn | <10% | <5% |

---

## 10. HISTORIQUE DES DÉCISIONS

| Date | Décision | Raison |
|---|---|---|
| 2026-06-29 | Audit complet vague 1 (5 sous-agents) | Évaluer intégrité DB, calculs, tarifs, foresterie |
| 2026-06-29 | Audit complet vague 2 (8 sous-agents) | Évaluer sécurité, GIS, UI, i18n, build, RGPD, perf, misc |
| 2026-06-29 | Suppression de 16 documents faux/périmés | Documents clamaient SQLCipher activé, 91% tests, etc. — tout faux |
| 2026-06-29 | Création MASTER_PLAN.md | Source de vérité unique pour vision + exécution |
| 2026-06-29 | Réécriture AI_CONTEXT.md | Refléter l'état réel du code (v2.3.0, DB v29) |
| 2026-06-29 | Analyse APK v2.1.0 vs code v2.3.0 | 6 classes perdues identifiées (GeoPackageExporter, AutecologyExpansion, EcologyFertilityTab, CampaignData, TappedDiagnosticInfo, ReferenceMode). Code v2.3.0 largement supérieur (+13 entités, +6 repos). Restauration ajoutée Phase 1.18-1.23. |
| 2026-06-29 | Recherche opportunités (5 sous-agents) | 150+ opportunités identifiées : 31 APIs FR (11 intégrées, 20 à intégrer), 40+ libraries OS, 30+ techno IA, 35+ aides financement (~600K$ cloud + 1.2M€ aides), 21 devices IoT. Synthèse dans `RESEARCH_OPPORTUNITIES.md`. |
| 2026-07-01 | Sécurisation de ~35 fichiers non commités (`feature/pro-pricing-engine`) | Refactor repositories→modèles domaine + polish UI, vérifiés build+467 tests OK avant commit (3 commits distincts). Aucune perte de travail. |
| 2026-07-01 | Re-audit factuel de la Phase 0/1 du plan vs code réel | 9/12 items Phase 0 étaient déjà FAITS (SQLCipher, cert pinning, FLAG_SECURE, RGPD onboarding/registre, collectAsStateWithLifecycle...) contrairement à ce que le plan indiquait. Phase 1 très largement non faite (2/23). Voir §2.4, §3.2, §3.3. |
| 2026-07-01 | Création de `.devin/AGENT_COORDINATION.md` | Protocole d'admission des rapports d'agents externes (checklist build/tests/architecture/sécurité/i18n avant tout merge), pour éviter que ce plan (ou le code) ne se re-périme silencieusement. |
| 2026-07-01 | Création de `docs/REFERENTIELS_FORESTIERS_EXTERNES.md` + scaffold `docs/recherche/` | 18 sources officielles/scientifiques (ONF, IGN, CNPF, France Bois Forêt, SAFER, AFNOR NF EN 1316...) pour fiabiliser cubage/prix/IBP/GRECO, avec méthodologie de sourcing pour les recherches futures. |
| 2026-07-01 | Vitrine GitHub investisseurs (main, commit dba459e) | `INVESTORS.md` (dossier de pitch : marché, business model, roadmap), section « Opportunité & vision » dans le README, `SECURITY.md`, 3 templates d'issues, repo renommé GeoSylva-new→GeoSylva, 12 topics, Discussions activées. |
| 2026-07-01 | Moteur de prix pro 8 coefficients (feature/pro-pricing-engine) | `ProPricingEngine.kt` + 5 fichiers domain/calculation/pricing/ (~4586 lignes) : formule 8 coefficients (qualité NF EN 1316/1927, défauts NF EN 1310, région GRECO, accessibilité, saison, certification, lot, position), auto-détection GRECO via GPS, breakdown transparent dans `ProductBreakdownCard`, 100+ essences valorisables. 5 bugs critiques corrigés (divergence chemins de calcul, casse essence×GRECO, comparaison Double ==, cumul défauts multiplicatif, fallback incohérent). 16 tests unitaires ajoutés. Commits : 0a14332, 270cd20, eec8ea0. |
| 2026-07-01 | Carte/GPS — 6 sous-phases sur 50 (feature/pro-pricing-engine) | Attribution légale sources carto + User-Agent OSM conforme (e9c0c83), perf `setGeoJson()` au lieu de recréer source+layers (b35f007), tuiles offline parallèles 6 concurrent + retry backoff (7049a7a), cache HTTP MapLibre 50MB + retry (25f6394), suppression Helmert faux + 8 points contrôle Lambert93 (b583505), compas TYPE_ROTATION_VECTOR + lissage passe-bas + accuracy sensor (8de298f). 7 fichiers, +508/−244. Scoreboard : Phase 1 80%, Phase 2-4 0%. |
| 2026-07-01 | Audit + correction `PRIVACY_POLICY.md` (Phase 0.6 FAIT) | 8 erreurs factuelles corrigées vs code : 6 services réseau manquants ajoutés (API Géo, IGN géocodage reverse, Open-Meteo, OpenTopoData, INRAE BD GSFr, Cerema DVF), `operateurNom`/`psgNumero`/champs libres ajoutés aux PII, « Effacer toutes mes données » marqué à venir (non implémenté), purge auto cache GPS marquée à venir (`purgeOlderThan()` jamais appelée), §2.3 BackupWorker ZIP non chiffré ajouté, §3.2 PriceSyncWorker pas de cert pinning ajouté, contact RGPD renseigné (contact@geosylva.fr). |
| 2026-07-01 | Recherche sourcée vagues 1+2 (commit 81031ef) | 10 fiches de recherche dans `docs/recherche/` : 5 cubage/volume (tarifs Schaeffer/Algan, IFN/EMERGE, coefficients forme/biomasse, tables production, normes qualité) + 5 marché/prix (FBF national, prix régionaux, ONF/coopératives, valeur foncière, marché carbone). Chaque fiche source primaire (URL + date), distingue faits vérifiés vs [À VÉRIFIER MANUELLEMENT]. |
| 2026-07-17 | Audit dendrométrique approfondi | Confirmation d'un socle riche mais identification de priorités scientifiques : indice de station avec âge de référence, rotation AMA/ACA, domaines de validité, séparation des volumes, incertitude, provenance des mesures et erreur silencieuse dans les calculs. |
| 2026-07-17 | Programme DENDRO-EXCELLENCE ajouté au plan | GeoSylva évolue vers un moteur de mesure forestière traçable : protocole configurable, jumeau temporel de parcelle, contrôle qualité actif, mesure à valeur d'information maximale, IA outillée, GSIE Serveur/PC et laboratoire de validation. |
| 2026-08-03 | Première tranche cliente d’identité Quintessences | Écrans connexion/compte, local + Google Credential Manager, session chiffrée, diagnostic GSIE et mode développeur après huit pressions ; cœur terrain toujours hors ligne. |
| 2026-08-03 | Cycle du compte Quintessences complété | Profil, vérification e-mail et récupération livrés ; révocation des anciennes sessions ; 513 tests, Lint sans erreur et APK validé sur émulateur. |
| 2026-08-03 | Protocole de bordure Cloudflare validé | GeoSylva rejoint GSIE par HTTPS + JWT derrière Cloudflare Tunnel ; aucun secret Access, mTLS ou tunnel dans l'APK ; activation externe en attente du domaine et du token du Fondateur. |
| 2026-08-03 | Première synchronisation métier GeoSylva ↔ GSIE | Parcelles envoyées après activation explicite par une file SQLCipher/WorkManager ; idempotence, versions, tombstones et conflits visibles. Pull serveur→mobile et résolution guidée restent à produire. |

---

## 11. DOCUMENTS DE RÉFÉRENCE

| Document | Rôle | Statut |
|---|---|---|
| `MASTER_PLAN.md` (ce fichier) | Vision + plan + écosystème | **Actif** (révisé 2026-07-17) |
| `AI_CONTEXT.md` | Contexte technique historique ; build courant dans `docs/APP_VERSION.md` | À revalider (constats datés du 2026-07-17) |
| `.devin/AGENT_COORDINATION.md` | Protocole d'admission des rapports d'agents externes | **Actif** (créé 2026-07-01) |
| `docs/REFERENTIELS_FORESTIERS_EXTERNES.md` | 18 sources officielles/scientifiques pour fiabiliser cubage/prix/IBP/GRECO | **Actif** (créé 2026-07-01) |
| `docs/recherche/` | Scaffold + méthodologie pour les futures recherches multi-agents sourcées | **Actif** (créé 2026-07-01, dossiers vides à peupler) |
| `RESEARCH_OPPORTUNITIES.md` | Opportunités techniques, IA, financement, hardware (150+ entrées) | Actif (créé 2026-06-29) |
| `AUDIT_FORESTIER_COMPLET.md` | Audit vague 1 (101 issues) | Référence — **statut des items non rafraîchi, voir §2.4/§3 pour les items déjà re-vérifiés** |
| `AUDIT_GLOBAL_GEOSYLVA.md` | Audit vague 2 (123 issues) | Référence — idem |
| `docs/RGPD_AUDIT_REPORT.md` | Audit RGPD initial | Référence |
| `docs/methodes_calcul_volume.md` | Référence technique cubage | Référence |
| `README.md` | Présentation publique | À mettre à jour |
| `CHANGELOG.md` | Historique des versions | Actif |
| `CONTRIBUTING.md` | Standards de contribution | Actif |
| `COMMERCIAL_LICENSE.md` | Licence commerciale (complète la licence propriétaire par défaut, voir `LICENSE`) | Actif |
| `PRIVACY_POLICY.md` | Politique de confidentialité | **Actif** (réécrit 2026-07-01, audit factuel vs code — 8 erreurs corrigées) |
| `INVESTORS.md` | Dossier de pitch investisseurs | **Actif** (créé sur main, commit dba459e) |
| `SECURITY.md` | Politique de sécurité (responsible disclosure) | **Actif** (créé sur main, commit dba459e) |

---

*Document maintenu par Camil, fondateur de GeoSylva, avec revue factuelle par IA (build/tests/grep avant toute mise à jour de statut).*
*Dernière mise à jour : 2026-07-17 (audit dendrométrique + programme DENDRO-EXCELLENCE)*
