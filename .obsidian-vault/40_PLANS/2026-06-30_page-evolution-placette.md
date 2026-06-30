# Plan : Page Évolution détaillée d'une placette

**Date** : 2026-06-30
**Statut** : Plan — à implémenter
**Fichier concerné** : `PlacetteDetailScreen.kt` → `PlacetteEvolutionTab`

---

## Contexte

L'onglet "Évolution" a été réimplémenté dans `PlacetteDetailScreen` (TabRow Essences/Évolution).
Actuellement, `PlacetteEvolutionTab` affiche une vue simple : cartes par année avec stats de base
(nombre de tiges, essences, diamètre moyen, répartition par catégorie de martelage).

L'objectif est de transformer le **bouton/clic sur une année** en une **page détaillée** riche
d'informations, accessible depuis l'onglet Évolution.

---

## Architecture proposée

```
PlacetteDetailScreen
└── TabRow
    ├── Tab 0 : Essences (existant)
    └── Tab 1 : Évolution
        ├── Vue liste années (PlacetteEvolutionTab — existant, simplifié)
        │   └── Clic sur une année → navigation
        └── Page détail année (PlacetteEvolutionDetailScreen — NOUVEAU)
            ├── Header : année + stats globales
            ├── Section 1 : Graphique diamètres (distribution par classes)
            ├── Section 2 : Évolution temporelle (comparaison années précédentes)
            ├── Section 3 : Tableau par essence
            ├── Section 4 : Catégories de martelage (détail)
            ├── Section 5 : Indicateurs dendrométriques
            ├── Section 6 : Biodiversité (IBP si dispo)
            └── Section 7 : Export / partage
```

---

## Sections détaillées de la page Évolution

### 1. Header — Stats globales de l'année

- Année (grand, centré)
- Nombre total de tiges
- Nombre d'essences
- Surface terrière (G) si surface placette disponible
- Volume estimé (si hauteurs renseignées)
- Diamètre moyen (Dm) + diamètre quadratique (Dg)
- Hauteur moyenne (Hm) + Hauteur de Lorey (Hg) si hauteurs dispo

### 2. Graphique — Distribution des diamètres par classes

- Histogramme : classes de 5 cm (10-15, 15-20, 20-25, …)
- Axe X : classes de diamètre
- Axe Y : nombre de tiges
- Couleurs par essence (légende)
- Comparaison avec année précédente (overlay ou bouton toggle)

**Bibliothèque** : `Vico` (Compose-native, déjà utilisé dans le projet ?) ou canvas custom

### 3. Évolution temporelle — Comparaison années

- Line chart : évolution des indicateurs clés sur les années disponibles
  - Nombre de tiges
  - Dm
  - G (surface terrière)
  - Nombre d'essences (diversité)
- Permet de voir la dynamique sylvicole

### 4. Tableau par essence

| Essence | N tiges | Dm (cm) | G (m²) | V (m³) | % du peuplement |
|---------|---------|---------|--------|--------|-----------------|
| Chêne   | 12      | 22.5    | 0.47   | 5.2    | 40%             |
| Hêtre   | 8       | 18.0    | 0.20   | 1.8    | 18%             |
| …       |         |         |        |        |                 |

- Tri par % du peuplement (décroissant)
- Couleurs des essences (colorHex ou catégorie)
- Clic sur une essence → détail de cette essence pour l'année

### 5. Catégories de martelage — Détail

- Cartes colorées par catégorie (AVENIR, RESERVE, ENLEVER, DEPERIR, BIODIV)
- Pour chaque catégorie :
  - Nombre de tiges
  - Volume correspondant
  - Valeur estimée (si prix configurés)
- Graphique camembert/donut de la répartition

### 6. Indicateurs dendrométriques

- Dg (diamètre quadratique) : `sqrt(sum(d²) / n)`
- G (surface terrière) : `sum(π × (d/2)²)` / surface placette
- N/ha (densité) : nombre de tiges / surface (ha)
- V/ha (volume par ha) : volume total / surface (ha)
- Coefficient d'élancement moyen (H/D)
- Structure : distribution diamètres → régulier/irrégulier/jardiné

### 7. Biodiversité (si évaluations IBP disponibles)

- Score IBP de l'année (si évaluation faite)
- Évolution du score IBP sur les années
- Lien vers le détail IBP

### 8. Export / Partage

- Bouton export PDF (résumé année)
- Bouton partage CSV/XLSX (tiges de l'année)
- Bouton impression (format A4 paysage)

---

## Données nécessaires

### Déjà disponibles (modèle `Tige`)

- `essenceCode` → jointure `Essence` pour nom, couleur, catégorie
- `diamCm` → calculs Dm, Dg, G, distribution
- `hauteurM` → calculs Hm, Hg, V
- `timestamp` → année
- `categorie` → martelage
- `qualite`, `defauts` → qualité bois
- `valueEur` → valeur
- `biomasseFusTonnes`, `carboneFusTonnes` → biomasse/carbone
- `classeKraft`, `etatSanitaire`, `vigueur` → sylviculture avancée
- `isTigeHabitat` → biodiversité

### À récupérer / calculer

- **Surface placette** : depuis `Placette` (rayon → surface) ou paramètre utilisateur
- **Évaluations IBP** : depuis `IbpEvaluation` (par placette + année)
- **Prix/produits** : depuis `PriceTable` (pour valeur estimée)
- **Hauteurs par essence** : custom ou tables ONF (déjà dans `ExpertForestryCalculator`)

---

## Navigation

### Option A : Page dédiée (recommandé)

- Nouveau composable `PlacetteEvolutionDetailScreen`
- Route navigation : `placette/{id}/evolution/{year}`
- Bouton retour vers `PlacetteDetailScreen` (tab Évolution)
- Avantage : plein écran, scroll fluide, partage/export natif

### Option B : Bottom sheet / Dialog plein écran

- `ModalBottomSheet` ou `FullScreenDialog`
- Pas de route navigation
- Plus simple mais moins flexible

**Recommandation** : Option A (page dédiée) pour cohérence avec les autres écrans
(Martelage, IBP, Station diag qui sont déjà des pages dédiées).

---

## Implémentation — Étapes

1. **ViewModel** : `PlacetteEvolutionViewModel`
   - Input : `placetteId`, `year`
   - Charge : tiges de l'année, tiges années précédentes (pour comparaison), surface placette, IBP
   - Calcule : tous les indicateurs (Dm, Dg, G, V, distribution, etc.)

2. **Composable** : `PlacetteEvolutionDetailScreen`
   - Scaffold avec TopAppBar (année + retour + export)
   - LazyColumn avec les 8 sections
   - Graphiques : canvas custom ou lib Vico

3. **Navigation** : ajouter route dans `ForestryNavigation`

4. **Clic année** : dans `PlacetteEvolutionTab`, rendre les cartes cliquables
   → `onNavigateToEvolutionDetail(placetteId, year)`

5. **Tests** : `PlacetteEvolutionViewModelTest` (calculs indicateurs)

---

## Dépendances potentielles

- **Graphiques** : `Vico` (Compose-native charts) — vérifier si déjà dans build.gradle
  - Sinon : canvas custom (déjà fait pour distribution diamètres dans MartelageScreen)
- **Export PDF** : `PdfDocument` Android natif (déjà utilisé ?)
- **Export XLSX** : `MartelageXlsxExporter` existe déjà — réutiliser

---

## Priorité

- **Phase 1** (MVP) : Header + tableau par essence + catégories martelage + distribution diamètres
- **Phase 2** : Évolution temporelle (line chart) + indicateurs dendrométriques avancés
- **Phase 3** : Biodiversité IBP + export PDF + partage

---

## Références codebase

- `ExpertForestryCalculator` : calculs Dg, G, V déjà implémentés
- `MartelageScreen` : graphique distribution diamètres (canvas custom)
- `IbpHistoryScreen` : évolution temporelle d'un score (pattern line chart)
- `MartelageXlsxExporter` / `MartelageCsvExporter` : export
- `PlacetteEvolutionTab` (actuel) : base de la vue liste années
