# GeoSylva — Méthodes de calcul

Ce document décrit les 7 méthodes de cubage et le système de prix
implémentés dans l'application. Chaque section montre la formule exacte
utilisée par le code (`TarifCalculator.kt`).

## Table des matières

1. [Schaeffer 1 entrée](#1-schaeffer-1-entrée)
2. [Schaeffer 2 entrées](#2-schaeffer-2-entrées)
3. [Algan](#3-algan)
4. [IFN Rapide](#4-ifn-rapide)
5. [IFN Lent](#5-ifn-lent)
6. [FGH](#6-fgh)
7. [Coefficient de forme](#7-coefficient-de-forme)
8. [Système de prix et qualité](#8-système-de-prix-et-qualité)

---

## 1. Schaeffer 1 entrée

**Entrée** : diamètre D₁₃₀ (cm) uniquement  
**Tarifs** : 1 → 16  
**Code** : `TarifMethod.SCHAEFFER_1E`

### Formule

Chaque numéro de tarif définit une paire de coefficients (a, b) dans l'implémentation actuelle :

```
C₁₃₀ = π × D₁₃₀
V = a + b × (C₁₃₀ / 100)²   (m³, convention du code)
```

Attention : cette écriture codée n'est pas encore démontrée équivalente aux tables historiques Schaeffer. Les 16 paires présentes dans `TarifData.kt` restent à comparer à une source primaire CTBA/ONF ; elles ne doivent pas être présentées comme validées sur la seule présence dans le code.

### Fonctionnement dans l'app

1. L'utilisateur choisit un tarif (1-16) ou l'app en suggère un
   depuis le mapping `essenceToIfnRapideNumero` (÷ 2, borné [1, 16]).
2. `TarifData.schaefferOneEntry[numero].volumeFromDiam(D)` → V.

### Quand l'utiliser ?

Peuplements réguliers (futaie monospécifique), inventaire rapide
sans mesure de hauteur.

---

## 2. Schaeffer 2 entrées

**Entrées** : D₁₃₀ (cm) + H (m)  
**Tarifs** : 1 → 8 dans le code actuel ; la source `PPtools/TarifSch2.R` recensée par l'audit décrit 13 variantes, à réconcilier
**Code** : `TarifMethod.SCHAEFFER_2E`

### Formule

```
C₁₃₀ = π × D₁₃₀
V = a + b × (C₁₃₀ / 100)² × H   (m³, convention du code)
```

Cette formule et ses huit jeux de coefficients doivent être comparés à une table primaire avant toute affirmation de conformité Schaeffer à deux entrées.

### Fonctionnement dans l'app

1. Numéro de tarif choisi par l'utilisateur ou issu de
   `essenceToIfnLentNumero[code]` (borné [1, 8]).
2. `TarifData.schaefferTwoEntry[numero].volumeFromDiam(D, H)` → V.

### Quand l'utiliser ?

Plus précis que le 1 entrée grâce à la hauteur. Adapté à tout
type de peuplement quand on mesure les hauteurs.

---

## 3. Algan

**Entrées** : D₁₃₀ (cm) + H (m)  
**Code** : `TarifMethod.ALGAN`

### Formule

Le code actuel utilise un jeu de coefficients `AlganCoefs(a, b, c)` par essence :

```
V = a × D₁₃₀ᵇ × Hᶜ   (m³, convention du code)
```

Cette forme est une équation de puissance de type Schumacher-Hall. Son
attribution aux tarifs Algan historiques n'est pas démontrée. Les triplets
présents dans `TarifData.alganCoefs` doivent être comparés aux équations
Vallet/EMERGE ou à une autre source primaire avant d'être renommés ou promus.

### Résolution d'essence

1. Recherche exacte du code essence dans `alganCoefs`.
2. Si absent, recherche par alias (`essenceCodeCandidates`).
3. Fallback : coefficients d'une essence proche de la même famille
   (résineux → Douglas ou Épicéa ; feuillus → Hêtre ou Chêne sessile).

### Quand l'utiliser ?

Chemin actuellement disponible pour des peuplements irréguliers ou mixtes.
La couverture large ne constitue pas une preuve de validité : si l'essence,
la population ou les coefficients ne sont pas qualifiés, le résultat doit être
marqué comme estimation non vérifiée.

---

## 4. IFN Rapide

**Entrée** : D₁₃₀ (cm) uniquement  
**Tarifs** : 1 → 36  
**Code** : `TarifMethod.IFN_RAPIDE`

### Formule

```
V = IFNRapideCoefs[numero].volumeM3(D₁₃₀)
```

Chaque tarif définit sa propre courbe V = f(D) dans le code actuel.
L'IGN documente la construction des tarifs IFN, mais l'origine exacte des
36 jeux de coefficients présents ici n'est pas établie dans le dépôt ; ils
restent à qualifier avant d'être présentés comme des tarifs IGN.

### Fonctionnement dans l'app

1. L'essence est mappée à un numéro IFN via
   `TarifData.essenceToIfnRapideNumero`.
2. Le numéro peut être modifié par l'utilisateur.

### Quand l'utiliser ?

Inventaires de type IGN, comparaisons avec les données nationales.
Pas de mesure de hauteur requise.

---

## 5. IFN Lent

**Entrées** : D₁₃₀ (cm) + H (m)  
**Tarifs** : 1 → 8  
**Code** : `TarifMethod.IFN_LENT`

### Formule

```
V = IFNLentCoefs[numero].volumeM3(D₁₃₀, H)
```

### Quand l'utiliser ?

Peuplements pour lesquels une hauteur est disponible. Le code actuel expose
huit jeux de coefficients, mais leur correspondance exacte avec une table
IFN/IGN primaire et leur domaine doivent encore être vérifiés.

---

## 6. FGH

**Entrées** : D₁₃₀ (cm) + H (m)  
**Code** : `TarifMethod.FGH`

### Formule

```
G = π/4 × (D₁₃₀ / 100)²          surface terrière (m²)
V = f × G × H                     (m³ bois fort tige)
```

- **f** : coefficient de forme par essence (stocké dans
  `TarifData.coefsFormeParEssence`), valeurs typiques 0.35 – 0.55.
- L'utilisateur peut fournir un `coefFormOverride`.

### Quand l'utiliser ?

Estimation rapide terrain. Précision acceptable (±15-20 %)
quand on connaît bien le coefficient de forme local.

---

## 7. Coefficient de forme

**Entrées** : D₁₃₀ (cm) + H (m)  
**Code** : `TarifMethod.COEF_FORME`

### Formule

Identique au FGH :

```
V = f × π/4 × (D₁₃₀ / 100)² × H
```

Le coefficient **f** est lu depuis `TarifData.coefsFormeParEssence`
ou fourni manuellement.

### Exemples de coefficients par essence

| Essence          | f     |
|------------------|-------|
| Chêne sessile    | 0.48  |
| Hêtre            | 0.42  |
| Douglas          | 0.44  |
| Épicéa           | 0.41  |
| Sapin pectiné    | 0.43  |
| Pin sylvestre    | 0.40  |
| Mélèze           | 0.42  |

---

## 8. Système de prix et qualité

### Classification qualité (norme APECF / NF EN 1316)

| Grade | Multiplicateur | Description |
|-------|---------------|-------------|
| **A** | ×2.50 | Excellente — tranchage, mérain, ébénisterie |
| **B** | ×1.50 | Bonne — sciage qualité, charpente premium |
| **C** | ×1.00 | Moyenne — sciage courant (prix de référence) |
| **D** | ×0.40 | Médiocre — bois industrie, chauffage |

### Exemple concret : Douglas BO

| Qualité | Prix base (€/m³) | Coef. qualité | Prix ajusté |
|---------|-------------------|---------------|-------------|
| A       | 120               | ×2.50         | 300 €/m³    |
| B       | 120               | ×1.50         | 180 €/m³    |
| C       | 120               | ×1.00         | 120 €/m³    |
| D       | 120               | ×0.40         | 48 €/m³     |

→ Un peuplement Douglas qualité D sera donc valorisé 2.5× moins
cher qu'un peuplement qualité C, et 6× moins cher que qualité A.

### Calcul du revenu par arbre (dans l'app)

```
1. Classer le produit (BO, BI, BCh, PATE…) selon le diamètre et les règles.
2. Chercher le prix dans la table des paramètres (PriceEntry) :
     essence × produit × diamètre × qualité
3. Si pas de prix spécifique → DefaultProductPrices.priceFor() :
     prix_spécifique_essence:produit × (multiplicateur_qualité / multiplicateur_C)
     ou : prix_générique_produit × multiplicateur_essence × multiplicateur_qualité
4. Revenu arbre = volume × prix €/m³
```

### Ventilation par produit (affichée dans Martelage)

L'écran Martelage affiche par essence :
- **Produit** (BO, BI, BCh, PATE)
- **Volume** en m³
- **Prix €/m³** ajusté à la qualité dominante
- **Valeur totale** estimée

Ces données sont calculées par `PriceCalculator.buildBreakdown()`
et affichées dans `ProductBreakdownCard`.

### Personnalisation des prix

L'utilisateur peut modifier les prix via :
- **Paramètres → Barèmes prix** : table complète (essence × produit × diam × qualité)
- **Préréglages régionaux** : 1 base nationale + 12 zones GRECO ; ils ne constituent pas 13 mercuriales régionales indépendantes

---

## Recommandations

| Situation | Méthode | Entrées |
|-----------|---------|----------|
| Inventaire rapide sans hauteur | Schaeffer 1E ou IFN Rapide | D seul |
| Inventaire standard | Schaeffer 2E ou IFN Lent | D + H |
| Peuplements irréguliers / mixtes | Équation par essence seulement si coefficients qualifiés ; sinon estimation avec réserve | D + H |
| Pin maritime, massif landais | Lapasse ou barème local si table et protocole disponibles | C130 + Hdec + produit |
| Estimation terrain rapide | FGH / Coef. forme avec f sourcé | D + H + f |

### Précision attendue

- **Schaeffer / IFN** : précision à établir par méthode, essence et domaine ; les coefficients actuels ne sont pas tous validés indépendamment.
- **Équation actuellement appelée Algan** : précision non généralisable ; elle dépend du jeu de coefficients et de sa population de calibration.
- **FGH / Coef. forme** : incertitude dominée par le choix et la variabilité de `f` ; aucune précision universelle ne doit être affichée.

### Sources

- Schaeffer/Algan historiques, Revue forestière française : https://doi.org/10.4267/2042/26629
- IGN, méthodologie IFN et DataIFN : https://inventaire-forestier.ign.fr/IMG/pdf/methodologie-2022.pdf ; https://inventaire-forestier.ign.fr/dataifn/
- CNPF, vente et cubage : https://nouvelle-aquitaine.cnpf.fr/gestion-durable-des-forets/coupes-et-travaux/la-vente-de-bois
- CNPF/IFC, estimation du volume sur pied : https://ifc.cnpf.fr/sites/socle/files/cnpf-old/498015_volume_pied_1.pdf
- Deleuze et al., EMERGE : https://hal.science/hal-03016051
- Vallet et al., équations de volume : https://hal.inrae.fr/hal-02664812
- `docs/recherche/01_cubage_volume/08_audit_formules_coefficients_2026-08-30.md`
- `docs/recherche/01_cubage_volume/09_registre_methodes_cubage_france_monde.md`

---

*Dernière mise à jour : 30 août 2026*
*Application : GeoSylva*
