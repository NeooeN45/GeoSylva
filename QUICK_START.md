# 🚀 Guide de Démarrage Rapide — GeoSylva 2.3.0

> Application Android professionnelle d'inventaire forestier, martelage et biodiversité. 100% hors-ligne.

---

## 1. Prérequis & Build

| Outil | Version requise |
|---|---|
| Android Studio | Ladybug (2024.2)+ |
| JDK | 17 |
| Compile / Target SDK | 35 |
| Min SDK | 26 (Android 8.0) |
| Gradle | 8.2+ |

```bash
# Cloner
git clone https://github.com/NeooeN45/GeoSylva-new.git

# Ouvrir dans Android Studio
File → Open → Sélectionner le dossier GeoSylva
```

## 2. Commandes de build

```bash
# Build debug (développement)
./gradlew assembleDebug

# Build release (APK signé — nécessite keystore.properties)
./gradlew assembleRelease
# → app/build/outputs/apk/release/

# Bundle Play Store (AAB)
./gradlew bundleRelease

# Tests unitaires
./gradlew testDebugUnitTest

# Vérification Kotlin rapide
./gradlew :app:compileDebugKotlin --no-daemon

# Nettoyage
./gradlew clean
```

---

## 3. Premier inventaire en 5 minutes

### Étape 1 — Créer une forêt (parcelle)
1. Écran d'accueil → **+** flottant
2. Nommer la parcelle (ex. "Forêt du Breuil")
3. Valider

### Étape 2 — Créer une placette
1. Ouvrir la parcelle → **+**
2. Nommer la placette et indiquer la **surface (ha)** — indispensable pour N/ha et G/ha
3. Optionnel : 📍 pour enregistrer les coordonnées GPS du centre

### Étape 3 — Saisir les tiges
1. Ouvrir la placette → **Inventaire**
2. Taper sur une essence pour ouvrir la grille de diamètres
3. Boutons **+/−** par classe de diamètre (20-25, 25-30 cm…)
4. Le **G/ha en temps réel** s'affiche pendant la saisie
5. Pour mesurer une hauteur : icône 📏 → clinomètre numérique intégré

### Étape 4 — Synthèse & Martelage
- Onglet **Martelage** → N/ha, G/ha, V/ha, Dg, Hdom, valorisation par essence
- Onglet **Écologie** → évaluation IBP biodiversité
- Bouton tableau de bord (📊) → graphiques donut/barres

---

## 4. Clinomètre numérique

1. Grille de saisie → icône hauteur sur une essence
2. Choisir la distance (chips 10/15/20/25 m ou saisie libre)
3. Pointer vers la **cime** → capture auto après 1,5 s de stabilité
4. Pointer vers la **base** si terrain en pente
5. Hauteur appliquée automatiquement à toutes les classes vides

| Capteur | Précision |
|---|---|
| Vecteur rotation | ±0,5° |
| Gyro + accéléromètre | ±1° |
| Accéléromètre seul | ±2° |
| Saisie manuelle | — |

---

## 5. IBP — Biodiversité (CNPF officiel)

**Accès** : onglet Écologie → *Évaluer l\'IBP*, ou menu → *Évaluations IBP*

| Score | Signification | Couleur |
|---|---|---|
| 0 pt | Absent | Rouge |
| 2 pts | Insuffisant | Ambre |
| 5 pts | Bon à excellent | Vert |

| Groupe | Critères | Max |
|---|---|---|
| A — Peuplement | E1, E2, GB, BMS, BMC, DMH, VS | 35 pts |
| B — Contexte | CF, CO, HC | 15 pts |
| **Total** | 10 critères | **50 pts** |

| Score | Niveau |
|---|---|
| 0–9 | Très faible |
| 10–19 | Faible |
| 20–29 | Moyen |
| 30–39 | Bon |
| 40–50 | Très bon |

Après les 10 critères, le résultat affiche le score /50, les **3 améliorations prioritaires** avec conseil actionnable, et un **radar chart** normalisé.

---

## 6. Cartographie

- **12 couches** : OSM, IGN, Satellite, Cadastre, Forêts, Topographique…
- **Tuiles hors-ligne** : bouton ☁ → télécharger la zone → 0% réseau requis ensuite
- **Outil de mesure** : tracé distances (m/km) et surfaces (m²/ares/ha), 8 couleurs
- **Import Shapefile** : superposer couches parcellaires

---

## 7. Exports

| Format | Usage |
|---|---|
| PDF | Rapport A4 complet dendrométrie + valorisation |
| PDF IBP | Rapport biodiversité avec niveaux et recommandations |
| Shapefile | .shp/.shx/.dbf/.prj pour QGIS / ArcGIS |
| GeoJSON | Lambert 93, intégration SIG |
| CSV / CSV-XY | Tabulaire avec coordonnées |
| XLSX | Multi-feuilles avec métadonnées |

---

## 8. Base de données (DB v13)

Migrations Room automatiques v1 → v13. Tables principales :

```
parcelles, placettes, tree_stems, essences
ibp_evaluations   ← scoreA, scoreB, growthConditions, answersJson (schemaV2)
```

---

## 9. Troubleshooting courant

| Symptôme | Solution |
|---|---|
| Apostrophe AAPT2 FR | Utiliser `\'` dans strings.xml |
| Crash release (ProGuard) | Vérifier keep rules Room + `IbpAnswers.serializer()` explicite |
| Gradle sync échoue | File → Invalidate Caches; `./gradlew clean build --refresh-dependencies` |
| IBP scores incohérents | `migrateToV2()` se déclenche auto au chargement |

---

## 10. Ressources

- [IBP CNPF officiel](https://www.cnpf.fr/nos-actions-nos-outils/outils-et-techniques/ibp-indice-de-biodiversite-potentielle)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [MapLibre GL Native Android](https://maplibre.org/maplibre-native/android/api/)
- [Material 3](https://m3.material.io/)

---

*GeoSylva 1.7.0 — Conçu par des forestiers, pour les forestiers. 🌲*
