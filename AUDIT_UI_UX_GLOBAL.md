# Audit UI/UX Global — GeoSylva v2.3.0

**Date** : 2026-06-30
**Méthode** : 5 sous-agents en parallèle, 40 écrans audités (tous les fichiers `presentation/screens/*.kt`)
**Référentiels** : Material Design 3, WCAG 2.1 AA, Web Interface Guidelines (Vercel)

---

## Synthèse exécutive

| Sévérité | Nombre | Définition |
|-----------|--------|------------|
| **CRITIQUE** | 25 | Cassera l'UI sur certains devices (touch targets < 48dp, TopBar surchargée, overflow non géré) |
| **MAJEUR** | 188 | UX dégradée (accessibilité, texte coupé, responsive) |
| **MINEUR** | 29 | Polish (hardcoded strings, fontSize limite) |
| **TOTAL** | **242** | |

### Répartition par type de problème

| Type | Description | Nombre | % |
|------|-------------|--------|---|
| **D** | Accessibilité (icônes cliquables sans `contentDescription`) | ~120 | 50% |
| **A** | Texte coupé / overflow (Text sans `maxLines`/`overflow=Ellipsis`) | ~70 | 29% |
| **B** | Touch targets < 48dp / boutons mal positionnés | ~30 | 12% |
| **E** | Responsive (hardcoded dp, Row sans weight) | ~15 | 6% |
| **C** | Organisation (TextField sans singleLine, scroll) | ~7 | 3% |

### Écrans les plus problématiques

| Écran | Lignes | Problèmes | Densité |
|-------|--------|-----------|---------|
| `MapScreen.kt` | 3249 | 27 | Élevée |
| `SettingsScreen.kt` | 1583 | 37 | Très élevée |
| `StationDiagnosticScreen.kt` | 1980 | 30 | Élevée |
| `MartelageSummaryCards.kt` | 1997 | 12 | Moyenne |
| `IbpEvaluationScreen.kt` | 1702 | 22 | Élevée |
| `GroupScreen.kt` | 1362 | 16 | Moyenne |
| `GroupsScreen.kt` | 1023 | 12 | Moyenne |
| `PriceTablesEditorScreen.kt` | 653 | 12 | Élevée |

---

## 1. PROBLÈMES CRITIQUES (25) — à corriger en priorité absolue

### 1.1 Touch targets < 48dp (Material guideline violée)

| Fichier | Ligne | Élément | Taille actuelle |
|---------|-------|---------|-----------------|
| `MapScreen.kt` | 2159-2163 | IconButton Close (tree info card) | 28dp |
| `MapScreen.kt` | 3216 | MapToolButton (zoom/nord) | 36dp |
| `DashboardScreen.kt` | 680 | Box badge qualité | 26dp |
| `GroupsScreen.kt` | 552 | Surface sélection couleur | 40dp |
| `GroupsScreen.kt` | 775 | IconButton menu | 24dp |
| `GroupsScreen.kt` | 842-843 | Box clickable sélection | 20dp |
| `GroupScreen.kt` | 306 | Surface sélection couleur | 34dp |
| `IbpEvaluationScreen.kt` | 1268 | Icon | 14dp |
| `IbpEvaluationScreen.kt` | 1697 | Icon | 14dp |
| `IbpHistoryScreen.kt` | 250-252 | IconButton sans taille explicite | défaut 24dp |

### 1.2 TopBar surchargées (>3 actions)

| Fichier | Ligne | Actions | Problème |
|---------|-------|---------|----------|
| `CalculatorScreen.kt` | 133-146 | 3 IconButton (Functions, Upload x2) | 2 icônes Upload identiques = confusion |
| `IbpEvaluationScreen.kt` | 236-262 | 5 IconButton (Guide, Résultat, PDF, Save, Delete) | Overflow sur petits écrans |

### 1.3 Patterns non standard

| Fichier | Ligne | Problème |
|---------|-------|----------|
| `GroupsScreen.kt` | 196-199 | Row avec 2 IconButtons imbriqués dans un IconButton — touch target < 48dp |
| `IbpEvaluationScreen.kt` | 641 | Row avec 2 OutlinedTextField sans `weight` — overflow sur petits écrans |

### 1.4 Texte coupé critique (overflow garanti sur certains devices)

| Fichier | Ligne | Texte | Contexte |
|---------|-------|-------|----------|
| `PlacetteDetailScreen.kt` | 828-830 | `Text(name)`, `Text(code)` | Row avec weight, essence |
| `PlacetteDetailScreen.kt` | 939-941 | `Text(e.name)`, `Text(e.code)` | LazyVerticalGrid |
| `MartelageScreen.kt` | 1880 | `Text(label + suffix)` | supportingText |
| `MartelageSummaryCards.kt` | 93, 162, 386, 492, 919, 1033, 1252, 1555, 1666 | 9 titres de cards | Sans maxLines |

---

## 2. PROBLÈMES MAJEURS (188) — par catégorie

### 2.1 Accessibilité — Icônes cliquables sans `contentDescription` (~120 cas)

**Pattern problématique récurrent :**
```kotlin
// AVANT (problématique)
IconButton(onClick = { ... }) {
    Icon(Icons.Default.Close, contentDescription = null)
}

// APRÈS (correct)
IconButton(onClick = { ... }) {
    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
}
```

**Fichiers les plus affectés :**

| Fichier | Nombre d'icônes sans description |
|---------|----------------------------------|
| `MapScreen.kt` | 22 |
| `SettingsScreen.kt` | 30 |
| `StationDiagnosticScreen.kt` | 10 |
| `MartelageScreen.kt` | 6 |
| `PriceTablesEditorScreen.kt` | 8 |
| `GroupsScreen.kt` | 5 |
| `IbpEvaluationScreen.kt` | 5 |
| `PlacetteDetailScreen.kt` | 4 |
| `EssenceDiamScreen.kt` | 3 |
| `RipisylveDiagnosticScreen.kt` | 4 |
| Autres (Diagnostic, TarifDocs, PackManager, etc.) | ~23 |

**Note** : Les icônes **décoratives** (dans des cards non cliquables) avec `contentDescription = null` sont **correctes**. Le problème concerne uniquement les icônes **interactives** (dans IconButton, DropdownMenuItem cliquable, Surface clickable, AssistChip).

### 2.2 Texte coupé — Text sans `maxLines`/`overflow=Ellipsis` (~70 cas)

**Pattern problématique :**
```kotlin
// AVANT (problématique dans Row/Card contrainte)
Row {
    Text(essence.name)  // peut déborder
    Text(essence.code)
}

// APRÈS (correct)
Row {
    Text(essence.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    Text(essence.code, maxLines = 1)
}
```

**Hotspots principaux :**

| Fichier | Lignes | Contexte |
|---------|--------|----------|
| `MartelageSummaryCards.kt` | 93, 162, 386, 492, 919, 1033, 1252, 1555, 1666 | Titres de cards |
| `IbpEvaluationScreen.kt` | 93, 100, 565, 760, 899, 1027, 1085, 1344, 1351, 1425, 1642, 1651 | Scores, labels, critères |
| `IbpDiagnosticScreen.kt` | 71, 234, 255, 330, 337, 396, 423 | Actions, détails |
| `IbpReferenceScreen.kt` | 93, 100, 131, 150, 185 | Références IBP |
| `IbpHistoryScreen.kt` | 229, 231, 237, 262 | Historique |
| `SettingsScreen.kt` | 428, 466, 880, 891, 909, 920 | ListItems, dropdowns |
| `PriceTablesEditorScreen.kt` | 478, 485, 534, 564, 594 | Tableaux de prix |
| `MapScreen.kt` | 2005, 2524, 2556, 2576 | Légendes, sélecteurs |
| `StationDiagnosticScreen.kt` | 522-578 (5 TextButton) | Labels dropdown |
| `TarifDocumentationScreen.kt` | 41, 141, 142, 179 | Tabs, listes |
| `PackManagerScreen.kt` | 62, 164, 165 | Tabs, packs |
| `SuperCorrelateurScreen.kt` | 417, 612 | Recommandations |
| `RipisylveDiagnosticScreen.kt` | 191, 211, 241 | Modes, labels |
| `StandClassificationScreen.kt` | 122 | Classes |
| `FormulasScreen.kt` | 117, 118, 143 | Formules |
| `CalculatorScreen.kt` | 169, 227, 228, 271, 328 | Expressions, résultats |
| `GroupScreen.kt` | 683 | TopAppBar |
| `EssenceDiamScreen.kt` | 519, 1013 | Compteurs, listes |

### 2.3 Touch targets < 48dp (~30 cas)

**Material Design 3 exige 48dp minimum.** Les IconButton sans `modifier = Modifier.size(48.dp)` utilisent la taille par défaut (48dp) — c'est OK. Le problème concerne les IconButton avec `size()` explicite < 48dp.

| Fichier | Lignes | Taille |
|---------|--------|--------|
| `IbpEvaluationScreen.kt` | 771 | 32dp |
| `IbpEvaluationScreen.kt` | 648, 659, 678 | 18dp (LeadingIcon) |
| `IbpEvaluationScreen.kt` | 1034, 1529 | 16dp |
| `IbpProjectsScreen.kt` | 368 | 36dp |
| `IbpHistoryScreen.kt` | 267, 273 | 16dp |
| `PriceTablesEditorScreen.kt` | 390, 403, 407, 474 | 18-20dp |
| `PackManagerScreen.kt` | 171 | 32dp |
| `GroupsScreen.kt` | 780 | 20dp |
| `RipisylveDiagnosticScreen.kt` | 222-226 | défaut 24dp |

### 2.4 Tailles de police < 12sp (~20 cas)

WCAG recommande 12sp minimum pour du texte lisible.

| Fichier | Lignes | Taille | Contexte |
|---------|--------|--------|----------|
| `DashboardScreen.kt` | 584 | 8sp | Badge qualité |
| `MapScreen.kt` | 2056 | 10sp | Warning GPS |
| `StationDiagnosticScreen.kt` | 519, 581 | 10sp | Labels TextField |
| `StationDiagnosticScreen.kt` | 1009 | 11sp | FilterChip |
| `StationDiagnosticScreen.kt` | 1062, 1086 | 10sp | Raisons, labels |
| `StationDiagnosticScreen.kt` | 1160-1201 (6 cas) | 9sp | Labels sol (H, A, etc.) |

### 2.5 TextField sans `singleLine=true` (~7 cas)

| Fichier | Lignes |
|---------|--------|
| `GroupScreen.kt` | 207, 209, 381, 387, 395, 403, 432, 450 |
| `GroupsScreen.kt` | 946 |

### 2.6 Layout non responsive (~15 cas)

| Fichier | Ligne | Problème |
|---------|-------|----------|
| `CalculatorScreen.kt` | 178 | `width(140.dp)` fixe |
| `CalculatorScreen.kt` | 313 | `height(120.dp)` fixe |
| `GroupScreen.kt` | 962-966 | Card heights fixes (120/140/180dp) |
| `ParcellesScreen.kt` | 419 | `heightIn(max = 520.dp)` hardcoded |
| `PlacetteDetailScreen.kt` | 412 | `size(120.dp)` thumbnail |
| `PlacetteDetailScreen.kt` | 821 | `height(84.dp)` Card |
| `IbpEvaluationScreen.kt` | 650 | `width(140.dp)` fixe |
| `SettingsScreen.kt` | 932-937, 1246 | Row de boutons sans `weight` |
| `MapScreen.kt` | 2524, 2556, 2576 | Surface heights fixes (22-26dp) |

---

## 3. PROBLÈMES MINEURS (29)

- `contentDescription` hardcoded (ex: `"Retour"`, `"Export CSV"`) au lieu de `stringResource()` — ~15 cas
- Icônes décoratives sans `contentDescription = null` explicite — ~10 cas
- `fontSize` proche de la limite (10-12sp) — ~4 cas

---

## 4. Plan de correction recommandé

### Phase 1 — Quick wins critiques (1-2h)
Corriger les 25 problèmes CRITIQUES :
1. **Touch targets** : remplacer tous les `size(<48.dp)` par `size(48.dp)` sur les IconButton clickable
2. **TopBar surchargées** : fusionner les actions Upload de CalculatorScreen, déplacer 2 actions d'IbpEvaluationScreen dans un menu overflow
3. **Texte coupé critique** : ajouter `maxLines` + `overflow = TextOverflow.Ellipsis` sur les 12 Text critiques

### Phase 2 — Accessibilité massive (3-4h)
Corriger les ~120 icônes cliquables sans `contentDescription` :
1. Créer les string resources manquantes dans `strings.xml`
2. Ajouter `contentDescription = stringResource(R.string.xxx)` sur chaque icône interactive
3. Tester avec TalkBack

### Phase 3 — Texte coupé systématique (2-3h)
Ajouter `maxLines` + `overflow = TextOverflow.Ellipsis` sur les ~70 Text à risque :
1. Prioriser les Text dans Row/Card contraintes
2. Ajouter `Modifier.weight(1f)` où nécessaire

### Phase 4 — Polish (1-2h)
1. Augmenter les fontSize < 12sp à 12sp minimum
2. Ajouter `singleLine = true` sur les TextField appropriés
3. Remplacer les hardcoded strings par `stringResource()`
4. Rendre les hardcoded dp responsive (`fillMaxWidth` + `weight`)

### Phase 5 — Vérification
1. Build debug + tests
2. Test sur petit écran (320dp) et grand écran (tablet)
3. Test TalkBack sur 3-4 écrans clés
4. Test rotation paysage

---

## 5. Patterns positifs observés

- ✅ `contentDescription = null` correctement utilisé sur les icônes **décoratives**
- ✅ `weight()` utilisé dans beaucoup de Row
- ✅ `LazyColumn` / `LazyVerticalGrid` pour les listes
- ✅ `spacedBy()` pour les espacements cohérents
- ✅ `verticalScroll` présent sur les écrans longs
- ✅ `stringResource()` utilisé majoritairement (sauf ~15 hardcoded)

---

## 6. Recommandations structurelles

1. **Créer un composant `AccessibleIconButton`** wrapper qui force le `contentDescription` non-null
2. **Créer un composant `TruncatedText`** qui applique `maxLines=1, overflow=Ellipsis` par défaut
3. **Définir des constantes** : `TOUCH_TARGET_MIN = 48.dp`, `MIN_FONT_SIZE = 12.sp`
4. **Lint custom** : ktlint/detekt rule pour détecter `IconButton` sans `contentDescription`
5. **Refactoring MapScreen.kt** (3249 lignes) — trop monolithique, extraire les overlays en composants

---

*Audit généré par 5 sous-agents parallèles analysant 40 écrans, vérifié et consolidé le 2026-06-30.*
