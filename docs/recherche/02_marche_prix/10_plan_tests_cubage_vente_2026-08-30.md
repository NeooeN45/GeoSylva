# Plan de tests du contrat cubage, produits et vente

**Domaine** : `docs/recherche/02_marche_prix/`  
**Identifiant** : GEOSYLVA-QA-CUBAGE-VENTE-010  
**Date** : 2026-08-30  
**Statut** : Draft  
**Agent** : Consortium QA GeoSylva

## 1. Objet

Ce plan transforme le document `DONNEES_ENTREES_SORTIES_CUBAGE_VENTE.md` en critères de vérification. Les tests doivent vérifier le comportement, les unités, les erreurs explicites et la traçabilité ; les tests qui recopient uniquement une constante du code ne prouvent pas sa validité scientifique.

## 2. Tests d'entrée

| Identifiant | Cas | Résultat attendu |
|---|---|---|
| QA-01 | diamètre positif + méthode 1 entrée | calcul possible sans hauteur |
| QA-02 | méthode 2 entrées sans hauteur | `MISSING_REQUIRED_DATA` |
| QA-03 | diamètre nul, négatif, NaN ou infini | rejet explicite |
| QA-04 | hauteur négative ou irréaliste | rejet explicite |
| QA-05 | essence absente ou inconnue | blocage ou état non qualifié, jamais fallback silencieux en vente |
| QA-06 | D et C incohérents | avertissement ou rejet selon tolérance |
| QA-07 | surface de placette absente | résultat local, pas de valeur par hectare |
| QA-08 | surface nulle/négative | rejet |
| QA-09 | volume ou prix négatif | rejet |
| QA-10 | unité inconnue | rejet dans le chemin métier |

## 3. Tests de cubage

| Identifiant | Cas | Résultat attendu |
|---|---|---|
| QA-11 | Huber sur grume cylindrique | volume égal à `L × πD²/4` |
| QA-12 | Smalian sur grume | volume égal à la moyenne des sections extrêmes |
| QA-13 | Newton sur grume | volume égal à l'intégration à trois sections |
| QA-14 | tronc de cône | résultat cohérent avec la formule analytique |
| QA-15 | coefficient de forme fourni | `V = G × H × f` avec unité contrôlée |
| QA-16 | Schaeffer 1E sans hauteur | méthode appelée si numéro valide |
| QA-17 | Schaeffer 2E sans hauteur | erreur ou absence de résultat |
| QA-18 | IFN numéro hors table | erreur explicite |
| QA-19 | Algan/équation sans coefficient sourcé | état `UNVERIFIED_PARAMETER` |
| QA-20 | méthode hors domaine D/H | résultat marqué hors domaine, pas d'extrapolation silencieuse |
| QA-21 | avec/sans écorce | aucune fusion des conventions |
| QA-22 | recalcul après nouvelle version de tarif | ancien résultat conservé, nouveau résultat versionné |

Les valeurs attendues des formules géométriques sont dans `vecteurs_tests_cubage.json`.

## 4. Tests de conversions

| Identifiant | Cas | Résultat attendu |
|---|---|---|
| QA-23 | cm → m | conversion exacte |
| QA-24 | ha → m² | facteur 10 000 |
| QA-25 | tonne → kg | facteur 1 000, nature de masse conservée |
| QA-26 | stère → m³ réel avec facteur fourni | résultat + source du facteur |
| QA-27 | conversion sans facteur d'empilage | `MISSING_REQUIRED_DATA` ou avertissement fort |
| QA-28 | tonne verte → tonne sèche sans humidité | refus |
| QA-29 | €/MWh PCI → €/stère sans PCI/essence/humidité | refus |
| QA-30 | unité de dimension différente | `UNIT_DIMENSION_MISMATCH` |
| QA-31 | `convertOrSame` dans le chemin de vente | interdit ; le chemin métier doit échouer |
| QA-32 | aller-retour d'une conversion connue | valeur conservée dans la tolérance documentée |

## 5. Tests produits et qualité

| Identifiant | Cas | Résultat attendu |
|---|---|---|
| QA-33 | grade estimé A/B/C/D | grade marqué `estimated` |
| QA-34 | grade normatif sans référence | rejet de la revendication normative |
| QA-35 | défaut inconnu | rejet de l'ingestion ou état incomplet |
| QA-36 | défaut visuel et produit incompatible | avertissement produit |
| QA-37 | grosse tige avec pourriture | pas de produit premium automatique |
| QA-38 | essence sans marché documenté | produit non documenté, pas prix inventé |
| QA-39 | conifère classé avec catégorie `Conifère` | produits compatibles seulement |
| QA-40 | tilleul/peuplier/alisier | codes canoniques et alias correctement résolus |
| QA-41 | deux défauts | cumul et plafond vérifiés |
| QA-42 | seuil de diamètre franchi | seuil indicatif, aptitude non garantie |

## 6. Tests prix et provenance

| Identifiant | Cas | Résultat attendu |
|---|---|---|
| QA-43 | observation FBF sur pied 2025 | source, édition, période et unité conservées |
| QA-44 | FBF moyenne comparée à prix bord de route | comparaison refusée |
| QA-45 | prix sans source | rejet |
| QA-46 | prix sans statut TVA | rejet ou `UNKNOWN`, jamais hypothèse |
| QA-47 | prix sans portée géographique | rejet d'affichage régional |
| QA-48 | prix d'essence groupe utilisé pour espèce | groupe explicitement affiché |
| QA-49 | prix régional absent | moyenne nationale annoncée, pas coefficient régional inventé |
| QA-50 | nouvel enregistrement de prix | ancienne règle conservée et clôturée |
| QA-51 | prix FBF privé vs ONF public | séries séparées |
| QA-52 | certification PEFC/FSC sans certificat | certification non validée |
| QA-53 | provenance déclarée sans preuve | origine déclarée, statut non vérifié |
| QA-54 | lot avec contrat contradictoire | état `CONTRACTUAL_MEASUREMENT_REQUIRED` |

## 7. Tests biomasse/carbone

| Identifiant | Cas | Résultat attendu |
|---|---|---|
| QA-55 | densité absente | biomasse non calculable |
| QA-56 | facteur d'expansion absent | résultat incomplet explicite |
| QA-57 | masse humide utilisée comme sèche | rejet |
| QA-58 | hauteur remplacée par une valeur par défaut | drapeau `height_estimated` obligatoire |
| QA-59 | carbone sans biomasse sèche | rejet |
| QA-60 | tiges vides | erreur explicite, pas biomasse égale à zéro silencieuse |

## 8. Tests capteurs, hors-ligne et export

| Identifiant | Cas | Résultat attendu |
|---|---|---|
| QA-61 | GPS précision ≤ 3 m | qualité affichée |
| QA-62 | GPS précision > 12 m | confiance faible et avertissement |
| QA-63 | photo manquante | absence de preuve affichée |
| QA-64 | absence réseau avec coefficients/prix locaux | calcul local possible |
| QA-65 | cache périmé | âge du cache affiché |
| QA-66 | import JSON valide | round-trip sans perte de champs |
| QA-67 | JSON malformé | exception contrôlée |
| QA-68 | CSV avec colonne inconnue | politique de compatibilité explicitée |
| QA-69 | export résultat hors domaine | avertissements exportés |
| QA-70 | export avec méthode/version/source | métadonnées présentes |

## 9. Critères de sortie

Avant d'intégrer une nouvelle méthode ou un prix dans l'interface par défaut :

- tous les tests d'entrée du groupe concerné passent ;
- les tests géométriques utilisent des attendus indépendants ;
- le domaine de validité est fourni ;
- la convention d'unité et d'écorce est explicite ;
- les erreurs et données absentes ne sont pas converties en zéro ;
- les résultats conservent méthode, version, source et incertitude ;
- les prix sont comparables sur essence, produit, stade, période et provenance ;
- le mode hors-ligne fonctionne avec un cache explicitement daté ;
- aucun test ne considère la présence d'un coefficient dans le code comme preuve de sa validité.

## 10. Sources internes

- `docs/DONNEES_ENTREES_SORTIES_CUBAGE_VENTE.md`
- `docs/REFERENTIEL_CUBAGE_VENTE_FRANCE.md`
- `docs/recherche/02_marche_prix/vecteurs_tests_cubage.json`
- `app/src/test/java/com/forestry/counter/domain/calculation/tarifs/`
- `app/src/test/java/com/forestry/counter/domain/calculation/`
- `app/src/main/java/com/forestry/counter/domain/calculation/tarifs/TarifCalculator.kt`
- `app/src/main/java/com/forestry/counter/domain/calculation/pricing/ProPricingEngine.kt`
- `app/src/main/java/com/forestry/counter/data/parameters/PriceSyncWorker.kt`

## 11. Limites

Ce plan décrit les tests à écrire ou à compléter. Il ne prétend pas que tous les cas QA-01 à QA-70 sont déjà implémentés dans le dépôt.
