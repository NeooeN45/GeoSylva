# Prix des bois, provenance et actualisation trimestrielle en France métropolitaine

**Domaine** : `docs/recherche/02_marche_prix/`  
**Identifiant** : GEOSYLVA-RECH-PRIX-006  
**Date** : 2026-08-30  
**Statut** : Draft  
**Agent** : Consortium recherche marchés et prix

## 1. Règle de comparabilité

Deux prix ne sont comparables que si les deux observations partagent au minimum : essence, produit, stade de vente, unité, convention d'écorce, TVA, qualité, dimensions, humidité lorsque pertinente, provenance et période. Une comparaison « chêne 2024 » sans ces champs mélange potentiellement plusieurs marchés.

## 2. Sources et périmètres

| Source | Produit | Fréquence | Granularité | Format / limite |
|---|---|---|---|---|
| France Bois Forêt / ASFFOR / EFF / La Forestière | bois d'œuvre sur pied forêt privée | annuelle | nationale et groupes d'essences | PDF/HTML ; moyenne de ventes groupées |
| ONF via Observatoire FBF | bois vendus sur pied forêt publique | trimestrielle ou selon publication | catégories publiques, à qualifier | PDF ; méthodologie distincte de FBF privé |
| Agreste/SSP | grumes, trituration, énergie | semestre ou trimestre | 7 essences grumes et régions forestières | indices et enquêtes ; pas toujours prix bruts |
| CEEB/CIBE | sciages, bois énergie professionnel | trimestrielle | filière et produits | mercuriales, accès variable |
| ADEME | combustibles domestiques | annuelle/série | nationale, combustible | API JSON ; prix en €/MWh PCI |
| CNPF/FIBOIS | prix ou repères régionaux | variable | région, bassin ou bulletin | couverture inégale et méthodologies différentes |
| ventes observées | lots, adjudications, contrats | événementielle | lot, massif, propriétaire | accès et droits à qualifier |

## 3. Valeurs FBF 2024-2025

| Essence/groupe | 2024 €/m³ sur pied | 2025 €/m³ sur pied | Variation publiée | Source |
|---|---:|---:|---:|---|
| Toutes essences | 90 | 86 | -4 % | FBF indicateur 2026 |
| Chêne | 228 | 190 | -17 % | plaquette FBF 2026 |
| Hêtre | 56 | 49 | -12 % | plaquette FBF 2026 |
| Frêne | 158 | 155 | -2 % | plaquette FBF 2026 |
| Châtaignier | 119 | 97 | -18 % | plaquette FBF 2026 |
| Douglas | 89 | 93 | +4 % | plaquette FBF 2026 |
| Épicéa commun | 54 | 69 | +28 % | plaquette FBF 2026 |
| Épicéa de Sitka | 60 | 65 | +10 % | plaquette FBF 2026 |
| Sapin pectiné | 47 | 53 | +13 % | plaquette FBF 2026 |
| Pin maritime | 56 | 53 | -5 % | plaquette FBF 2026 |
| Pin laricio | 40 | 45 | +12 % | plaquette FBF 2026 |
| Pin sylvestre | 36 | 44 | +22 % | plaquette FBF 2026 |
| Peuplier | 73 | 62 | -15 % | plaquette FBF 2026 |

Ces chiffres sont des moyennes de panel pour du bois d'œuvre sur pied en forêt privée. Ils ne donnent pas une valeur par qualité, par diamètre ou par département. Le PDF et le communiqué sont les sources de la table ; toute intégration doit conserver l'édition et l'année de constatation séparément.

## 4. Provenance et couverture géographique

### 4.1 Hiérarchie

```text
pays → région administrative → département → massif → GRECO/SER → parcelle/lot
```

Le système doit distinguer :

- provenance biologique de l'essence ou du matériel forestier ;
- provenance du lot vendu ;
- lieu de récolte ;
- propriétaire public/privé ;
- lieu de livraison ;
- pays de transformation ;
- certification et chaîne de contrôle.

### 4.2 Limite de granularité

Les données publiques ne fournissent pas systématiquement un prix par département. En l'absence d'une observation locale qualifiée, GeoSylva doit afficher la portée réelle : « moyenne nationale », « région administrative », « bassin », « forêt publique » ou « lot observé ». Une interpolation spatiale peut produire une carte, mais elle doit être nommée comme estimation et non comme prix observé.

## 5. Couverture par marché, produit et qualité

| Marché | Produits représentés | Qualité disponible | Provenance disponible | Usage GeoSylva |
|---|---|---|---|---|
| Bois sur pied privé | bois d'œuvre, groupes d'essences | moyenne de panel, pas grille universelle A-D | France métropolitaine et périmètre du panel | référence nationale datée |
| Bois sur pied public | bois d'œuvre et autres catégories selon publication | à qualifier par édition | forêt domaniale/communale et périmètre ONF | série distincte, ne pas fusionner avec FBF privé |
| Grumes/billons bord de route | 7 essences et 2-3 catégories selon enquête | catégories Agreste | 5 régions forestières | prix/indice semestriel si source accessible |
| Sciages/placages | sciages, plots, plateaux, produits transformés | qualités CEEB propres au produit | filière/bassin, granularité variable | mercuriale, pas prix d'un arbre sur pied |
| Trituration/panneaux | bois industrie, pâte, panneaux | catégorie industrielle | nationale/régionale selon Agreste ou CEEB | prix industriel séparé |
| Bois énergie domestique | bûches, granulés, bûchettes | longueur, conditionnement, livré/non livré | principalement nationale | €/MWh PCI ; conversion séparée |
| Bois énergie professionnel | plaquettes et combustibles industriels | spécifications humidité/PCI | filière et période CEEB/CIBE | indice professionnel |
| Lots observés | tous produits selon transaction | contrat, classement ou description du lot | lot, massif, parcelle si publié | données événementielles à qualifier |

Aucune ligne ne doit être complétée par un prix d'une autre ligne uniquement parce que le nom de l'essence ou du produit est proche. Une donnée absente reste `unknown` ou `not_available`.

## 6. Modèle de prix par produit et qualité

Un prix est une observation ou une règle, pas une propriété intemporelle d'une essence.

```text
prix de base
× ajustement de produit
× ajustement de qualité si calibré
× ajustement dimensionnel si calibré
× ajustement de stade de vente si comparable
= estimation de valeur
```

Les coefficients d'accessibilité, saison, lot, certification et transport ne doivent être appliqués que si leur source mesure réellement cet effet dans un périmètre compatible. Les valeurs actuellement présentes dans `PricingContext.kt` et `ProPricingEngine.kt` doivent rester explicitement des paramètres de modèle tant qu'elles ne sont pas reliées à une estimation statistique et à une période de validité.

## 7. Actualisation trimestrielle

### 7.1 Calendrier

| Période | Vérification | Action |
|---|---|---|
| chaque trimestre | ONF, Agreste, CEEB/CIBE, sources régionales | détecter nouvelle publication, archiver URL et version |
| chaque semestre | enquête Agreste grumes | intégrer période et région si accès autorisé |
| chaque année, mai-juin | indicateur FBF | intégrer année de publication et année des ventes |
| chaque année | FCBA Memento et données structurelles | mettre à jour propriétés, pas les confondre avec prix |
| à chaque changement juridique | EUDR, fiscalité, transport, normes | créer une nouvelle note datée et revue humaine |

### 7.2 Pipeline

1. `DISCOVER` : identifier la source et sa période.
2. `QUALIFY` : vérifier licence, accès, périmètre et méthode.
3. `EXTRACT` : relever la valeur et le contexte sans transcription silencieuse.
4. `NORMALIZE` : unité, TVA, écorce, stade et qualité.
5. `VALIDATE` : tests de plausibilité, doublons et comparaison inter-périodes.
6. `PUBLISH_LOCAL` : nouvelle version append-only et signature/checksum si fichier.
7. `DISPLAY` : montrer source, date, portée et avertissement.

## 8. Prix énergie

L'ADEME expose des prix de combustibles domestiques en €/MWh PCI avec une série historique. Cette unité est énergétique. Pour calculer une valeur en €/stère ou €/tonne, il faut connaître le type de combustible, sa longueur, son humidité et son pouvoir calorifique. Une conversion générique serait susceptible de produire une erreur importante et ne doit pas être codée sans source.

Le CEEB/CIBE concerne des indices professionnels et des produits différents ; il ne doit pas être fusionné avec l'enquête ADEME domestique.

## 9. Recommandation pour GeoSylva

- Remplacer le couple ambigu `year` par `publication_date`, `observation_period` et `validity_start/end` dans le futur modèle.
- Conserver la source et la portée dans l'interface utilisateur.
- Refuser les prix sans unité, stade de vente, statut TVA et convention de volume.
- Mettre en évidence la différence entre `price_observation` et `price_rule`.
- Ne pas présenter les coefficients qualité comme imposés par NF EN 1316/1927 : les normes classent, le marché valorise.
- Préparer l'import JSON/CSV via la validation stricte déjà utilisée par le worker de synchronisation, mais avec historisation.

## 10. Sources

- FBF indicateur 2026 : https://franceboisforet.fr/2026/05/26/prix-de-vente-des-bois-sur-pied-en-foret-privee-indicateur-2026/
- Plaquette FBF 2026 : https://franceboisforet.fr/wp-content/uploads/2026/05/CP_PlaquettePrixDesBois_v4.pdf
- ONF/FBF : https://observatoire.franceboisforet.com/donnees-de-la-filiere/amont-forestier/office-national-des-forets/
- Agreste, enquête prix des bois : https://www.mesdemarches.agriculture.gouv.fr/demarches/proprietaire-ou-operateur/repondre-aux-enquetes-statistiques-87/article/enquete-sur-les-prix-des-bois
- ADEME : https://data.ademe.fr/datasets/prix-bois-domestique
- CIBE : https://cibe.fr/prix-du-bois-energie/
- CNPF Nouvelle-Aquitaine, vente et cubage : https://nouvelle-aquitaine.cnpf.fr/gestion-durable-des-forets/coupes-et-travaux/la-vente-de-bois
- Documents internes : `01_prix_national_fbf.md`, `02_prix_regionaux.md`, `03_onf_cooperatives_bois_energie.md`, `PricingContext.kt`, `ProPricingEngine.kt`.

## 11. Limites

Les prix par qualité fine, provenance départementale et produit transformé ne sont pas disponibles de façon homogène dans les sources ouvertes consultées. Les valeurs manquantes doivent rester manquantes ou être déclarées comme estimation séparée.
