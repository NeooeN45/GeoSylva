# Politique de confidentialité — GeoSylva

**Dernière mise à jour :** 29 juin 2026

## Introduction

GeoSylva (« l'Application ») est une application Android de gestion forestière de terrain.
Cette politique de confidentialité explique quelles données l'Application traite, à quelle fin, sur quelle base légale, combien de temps elles sont conservées, et quels sont vos droits.

**Responsable du traitement** : Micro Entreprise (camil)
**Contact RGPD** : [à renseigner — email du DPO ou responsable]

---

## 1. Données personnelles collectées

L'Application collecte les catégories de données personnelles suivantes :

### 1.1 Identité et contact

| Champ | Finalité | Base légale | Stockage |
|-------|----------|-------------|----------|
| `proprietaireNom` | Identification du propriétaire forestier | Exécution d'un contrat (Art. 6§1.b) | Appareil uniquement |
| `proprietaireEmail` | Contact propriétaire pour rapports | Exécution d'un contrat (Art. 6§1.b) | Appareil uniquement |
| `gestionnaireNom` | Identification du gestionnaire forestier | Exécution d'un contrat (Art. 6§1.b) | Appareil uniquement |
| `operateurNom` | Identification de l'opérateur terrain | Intérêt légitime (Art. 6§1.f) | Appareil uniquement |
| `observerName` | Identification de l'observateur IBP | Intérêt légitime (Art. 6§1.f) | Appareil uniquement |
| `evaluatorName` | Identification de l'évaluateur IBP | Intérêt légitime (Art. 6§1.f) | Appareil uniquement |

### 1.2 Localisation (GPS)

| Champ | Finalité | Base légale | Stockage |
|-------|----------|-------------|----------|
| `latitude`, `longitude` | Géolocalisation des arbres et placettes | Consentement (Art. 6§1.a) | Appareil uniquement |
| `gpsWkt`, `centerWkt`, `referenceGpsWkt` | Géométries GPS des parcelles/placettes | Consentement (Art. 6§1.a) | Appareil uniquement |
| `latKey`, `lonKey` | Cache de contexte GPS | Intérêt légitime (Art. 6§1.f) | Appareil uniquement (cache temporaire) |

### 1.3 Données cadastrales

| Champ | Finalité | Base légale | Stockage |
|-------|----------|-------------|----------|
| `codeInseeCommune`, `nomCommune` | Localisation administrative | Exécution d'un contrat (Art. 6§1.b) | Appareil uniquement |
| `sectionCadastrale`, `numeroCadastral` | Identification de la parcelle cadastrale | Exécution d'un contrat (Art. 6§1.b) | Appareil uniquement |
| `geometrieIgnWkt`, `natureCadastraleCode` | Géométrie et nature de la parcelle | Exécution d'un contrat (Art. 6§1.b) | Appareil uniquement |

### 1.4 Photographies

| Champ | Finalité | Base légale | Stockage |
|-------|----------|-------------|----------|
| `photosJson` (Station/Ripisylve) | Documentation visuelle des stations | Consentement (Art. 6§1.a) | Appareil uniquement |
| `photoUri` (Tige) | Documentation visuelle des arbres | Consentement (Art. 6§1.a) | Appareil uniquement |

**Attention** : Les photographies peuvent contenir des personnes identifiables. L'utilisateur est responsable d'obtenir le consentement des personnes photographiées.

### 1.5 Données techniques

| Champ | Finalité | Base légale | Stockage |
|-------|----------|-------------|----------|
| Modèle de l'appareil | Diagnostic technique | Intérêt légitime (Art. 6§1.f) | Appareil uniquement |
| Stack trace (crash) | Diagnostic de bugs | Intérêt légitime (Art. 6§1.f) | Appareil uniquement (jamais envoyé) |

---

## 2. Stockage et sécurité

### 2.1 Chiffrement

- **Base de données** : chiffrée avec SQLCipher (AES-256, clé dérivée via Android Keystore)
- **Fichiers sensibles** : stockés dans le stockage interne de l'Application (scoped storage Android 10+)
- **Clés cryptographiques** : stockées dans Android Keystore (hardware-backed si disponible)

### 2.2 Pas de transfert de données hors appareil

**Aucune donnée personnelle n'est transmise à un serveur ou un service cloud.**
Toutes les données personnelles restent exclusivement sur l'appareil de l'utilisateur.

### 2.3 Export de données

L'utilisateur peut exporter ses données (CSV, XLSX, JSON, ZIP) via l'Application. Cette opération est **initiée et contrôlée par l'utilisateur**. Les fichiers exportés contiennent les données personnelles saisies. L'utilisateur est responsable de la sécurisation des fichiers exportés.

---

## 3. Utilisation du réseau

L'Application utilise une connexion internet **uniquement** pour :

| Usage | Données envoyées | Service | Hébergement |
|-------|------------------|---------|-------------|
| Tuiles cartographiques | Coordonnées de la zone visible (bbox) | IGN Géoportail (`data.geopf.fr`) | France (IGN) |
| Tuiles cartographiques | Coordonnées de la zone visible | OpenStreetMap | UE (Royaume-Uni) |
| Tuiles cartographiques | Coordonnées de la zone visible | MapLibre demo tiles | USA |
| Tuiles cartographiques | Coordonnées de la zone visible | CartoCDN | USA |
| Tuiles cartographiques | Coordonnées de la zone visible | Esri ArcGIS Online | **USA** |
| Tuiles topographiques | Coordonnées de la zone visible | OpenTopoMap | UE (Allemagne) |
| Synchronisation prix bois | Aucune donnée personnelle envoyée (HTTP GET uniquement) | URL configurée par l'utilisateur | Variable |

### 3.1 Transferts hors UE

Les services **Esri ArcGIS Online**, **MapLibre demo tiles** et **CartoCDN** sont hébergés aux États-Unis.
Ces transferts ne concernent que les **coordonnées de la zone cartographique visible** (bbox), qui ne constituent pas des données personnelles en elles-mêmes, mais peuvent être combinées avec d'autres données pour identifier une zone forestière.

**Garanties appropriées (Art. 46 RGPD)** : Les transferts vers ces fournisseurs sont couverts par les **Standard Contractual Clauses (SCC)** adoptées par la Commission européenne (Décision d'exécution 2021/914 du 4 juin 2021). Le Privacy Shield ayant été invalidé par l'arrêt Schrems II (CJUE, 16 juillet 2020), les SCC constituent la garantie appropriée. Une analyse d'impact du transfert (TIA) a été effectuée pour vérifier la compatibilité avec les lois américaines applicable aux données transférées (coordonnées bbox uniquement). L'utilisateur est informé que ces services peuvent loguer les adresses IP et les requêtes.

---

## 4. Sous-traitants

L'Application utilise les services tiers suivants pour l'affichage cartographique :

| Sous-traitant | Service | Données traitées | Localisation |
|---------------|---------|------------------|--------------|
| IGN | Géoportail WMS/WMTS | Coordonnées bbox | France (UE) |
| OpenStreetMap Foundation | Tuiles OSM | Coordonnées bbox | Royaume-Uni (UE) |
| MapLibre | Demo tiles | Coordonnées bbox | USA |
| CartoCDN | Tuiles raster | Coordonnées bbox | USA |
| Esri | ArcGIS Online | Coordonnées bbox | USA |
| OpenTopoMap | Tuiles topographiques | Coordonnées bbox | Allemagne (UE) |

Aucun de ces sous-traitants n'a accès aux données personnelles stockées sur l'appareil.

---

## 5. Durée de conservation

| Catégorie | Durée de conservation | Suppression |
|-----------|----------------------|-------------|
| Données forestières (arbres, placettes, parcelles) | Jusqu'à suppression par l'utilisateur | Manuelle via l'Application |
| Données d'identité (noms, emails) | Jusqu'à suppression par l'utilisateur | Manuelle via l'Application |
| Cache GPS | 30 jours (purge automatique) | Automatique via `purgeOlderThan` |
| Photographies | Jusqu'à suppression par l'utilisateur | Manuelle via l'Application |
| Préférences utilisateur | Jusqu'à désinstallation | Automatique à la désinstallation |
| Logs de crash | Jusqu'à désinstallation | Automatique à la désinstallation |

**Désinstallation** : La désinstallation de l'Application supprime toutes les données stockées sur l'appareil.

---

## 6. Vos droits RGPD

Conformément au RGPD (UE 2016/679), vous disposez des droits suivants :

| Droit | Article | Implémentation dans GeoSylva |
|-------|---------|------------------------------|
| **Accès** (Art. 15) | Consulter vos données | Export CSV/XLSX/JSON dans les Paramètres |
| **Rectification** (Art. 16) | Corriger vos données | Édition dans l'Application (parcelles, placettes, tiges) |
| **Effacement** (Art. 17) | Supprimer vos données | « Effacer toutes mes données » dans les Paramètres + suppression individuelle |
| **Portabilité** (Art. 20) | Récupérer vos données | Export JSON/CSV (format machine-readable) |
| **Limitation** (Art. 18) | Restreindre le traitement | Désactivation GPS/caméra dans les permissions Android |
| **Opposition** (Art. 21) | S'opposer au traitement | Désactivation des permissions Android |
| **Consentement** (Art. 7) | Retirer votre consentement | Révocation des permissions Android à tout moment |

### Exercice de vos droits

Pour exercer vos droits, contactez : **[email du responsable]**

Vous pouvez également déposer une plainte auprès de la **CNIL** (Commission Nationale de l'Informatique et des Libertés) :
- Site web : https://www.cnil.fr/fr/plaintes
- Adresse : 3 Place de Fontenoy, TSA 80715, 75334 PARIS CEDEX 07

---

## 7. Décisions automatisées

L'Application génère des **recommandations sylvicoles** (diagnostic de station, indice de biodiversité IBP, projections climatiques) basées sur des algorithmes. Ces recommandations sont :

- **Assistives** : elles assistent le forestier dans sa décision mais ne la remplacent pas
- **Documentées** : chaque recommandation indique son niveau de confiance et ses sources
- **Non contraignantes** : l'utilisateur reste seul responsable de ses décisions sylvicoles

Conformément à l'Article 22 du RGPD, l'utilisateur peut contester toute recommandation et demander une intervention humaine (consultation d'un expert forestier).

---

## 8. Données des enfants

L'Application est un outil professionnel forestier. Elle n'est pas destinée aux enfants de moins de 15 ans et ne collecte pas sciemment de données les concernant.

---

## 9. Modifications de cette politique

Cette politique peut être mise à jour. La date de « Dernière mise à jour » en haut de ce document indique la version applicable.

---

## 10. Contact

Pour toute question relative à cette politique de confidentialité :
**Email** : [à renseigner]
**Responsable** : Micro Entreprise (camil)

---

*Cette politique de confidentialité s'applique à l'application Android GeoSylva.*
