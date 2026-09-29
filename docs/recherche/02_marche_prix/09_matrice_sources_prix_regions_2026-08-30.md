# Matrice des sources de prix par région et marché

**Domaine** : `docs/recherche/02_marche_prix/`  
**Identifiant** : GEOSYLVA-AUDIT-PRIX-009  
**Date** : 2026-08-30  
**Statut** : Draft  
**Agent** : Consortium audit des marchés régionaux

## 1. Objet

Cette matrice sépare la région administrative, la GRECO, la SER, le massif et le périmètre réel d'une source de prix. Elle empêche d'afficher comme observation départementale un coefficient qui n'est qu'un paramètre de modèle.

## 2. Régions administratives métropolitaines

| Région | Source publique de prix fin identifiée | Stade/produit | Statut de couverture |
|---|---|---|---|
| Auvergne-Rhône-Alpes | Fibois/CNPF, publications ponctuelles ; bois bûche distinct | variable | à qualifier, pas de série homogène confirmée |
| Bourgogne-Franche-Comté | documents CNPF/Fibois ; marchés bois énergie et Douglas/chêne selon source | variable | à qualifier |
| Bretagne | CNPF Bretagne-Pays de la Loire, bulletins et prix indicatifs | bois sur pied, certaines essences | source régionale exploitable selon édition |
| Centre-Val de Loire | CNPF IFC, méthode de vente | méthodologie, pas barème régional public confirmé | non disponible en série ouverte |
| Corse | marché local, chêne-liège et énergie notamment | niche/local | non disponible en série homogène |
| Grand Est | Fibois/ONF/CNPF selon publication | résineux et bois énergie | fraîcheur et périmètre à vérifier |
| Hauts-de-France | Observabois, accès conditionnel selon données | variable | accès restreint ou à qualifier |
| Île-de-France | sources bois construction, pas nécessairement prix de grumes | produit transformé | non comparable au sur pied |
| Normandie | publications ponctuelles ; pas de série libre homogène confirmée | variable | à qualifier |
| Nouvelle-Aquitaine | CNPF/Fibois, pin maritime, peuplier et indicateurs nationaux | sur pied/bord de route selon source | source régionale partielle |
| Occitanie | Fibois/Observabois et indicateurs FBF relayés | Douglas/résineux/énergie | source régionale partielle |
| Pays de la Loire | délégation CNPF avec Bretagne ; bulletins selon édition | bois sur pied, certaines essences | source régionale partielle |
| Provence-Alpes-Côte d'Azur | sources locales et énergie ; peu de barèmes bois d'œuvre ouverts | niche/énergie | non disponible en série homogène |

## 3. Sources nationales par marché

| Source | Périmètre réel | Fréquence | Utilisable pour |
|---|---|---|---|
| FBF | ventes groupées sur pied en forêt privée, groupes d'essences | annuelle | référence nationale datée, pas prix parcellaire |
| ONF/FBF | ventes de forêt publique selon séries publiées | trimestrielle ou édition | marché public, séparé de FBF privé |
| Agreste/SSP | grumes, trituration et énergie selon enquête | semestre/trimestre | indices et catégories par essence/région |
| CEEB/CIBE | sciages et bois énergie professionnels | trimestrielle | mercuriales et indices selon accès |
| ADEME | combustibles domestiques | série annuelle | €/MWh PCI par combustible, pas €/stère automatique |
| CNPF/Fibois | repères et bulletins régionaux | variable | contexte local, avec vérification de méthode |

## 4. Écart entre code et donnée observée

Le code GeoSylva combine actuellement :

- presets nationaux et 12 GRECO ;
- coefficients `FrenchRegion` ;
- coefficients essence × région ;
- règles de qualité, accessibilité, saison, certification, lot et position.

Ces éléments ne sont pas tous des observations de prix. Un coefficient tel que `1.15` est une règle de modèle tant qu'il n'est pas associé à une série, une période, un échantillon et une méthode statistique. Il ne doit pas être exporté comme « prix régional observé ».

## 5. Données à acquérir pour compléter les prix

Pour chaque publication :

```text
source_id
publication_date
observation_period
species_or_group
product_code
quality_class
diameter_class
length_class
sale_stage
volume_measure
bark_condition
vat_status
region_scope
department_if_published
forest_type
price_value
price_unit
sample_size
methodology_url
licence
checksum_or_archive_reference
```

Pour les régions sans source publique structurée, l'application doit afficher « prix régional non disponible » ou la dernière moyenne nationale clairement étiquetée.

## 6. Décision d'intégration

- Ne pas remplacer automatiquement les 13 presets actuels.
- Ne pas créer de coefficient par département sans observation qualifiée.
- Ne pas fusionner FBF privé, ONF public, Agreste, CEEB et ADEME.
- Ajouter les données par nouvelle version historisée.
- Prévoir une revue trimestrielle de disponibilité, même lorsque la source n'est publiée qu'une fois par an.
- Conserver la portée géographique exacte de la publication, y compris la distinction « France hexagonale »/« France métropolitaine » lorsqu'elle apparaît dans la source.

## 7. Sources

- France Bois Forêt, indicateur 2026 : https://franceboisforet.fr/2026/05/26/prix-de-vente-des-bois-sur-pied-en-foret-privee-indicateur-2026/
- Observatoire FBF/ONF : https://observatoire.franceboisforet.com/donnees-de-la-filiere/amont-forestier/office-national-des-forets/
- Agreste, enquête prix des bois : https://www.mesdemarches.agriculture.gouv.fr/demarches/proprietaire-ou-operateur/repondre-aux-enquetes-statistiques-87/article/enquete-sur-les-prix-des-bois
- ADEME : https://data.ademe.fr/datasets/prix-bois-domestique
- CNPF, vente de bois : https://nouvelle-aquitaine.cnpf.fr/gestion-durable-des-forets/coupes-et-travaux/la-vente-de-bois
- Fichiers internes : `RegionalPricePresets.kt`, `PricingCoefficients.kt`, `FrenchRegion.kt`, `GrecoRegion.kt`, `01_prix_national_fbf.md`, `02_prix_regionaux.md`.

## 8. Limites

Les sources régionales sont hétérogènes, certaines payantes ou non structurées. Cette matrice décrit la disponibilité et la méthode de collecte ; elle ne remplit pas les valeurs manquantes.
