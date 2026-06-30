# Registre des traitements — GeoSylva

**Dernière mise à jour :** 29 juin 2026
**Responsable du traitement** : Micro Entreprise (camil)

Conformément à l'Article 30 du RGPD (UE 2016/679), ce document constitue le registre des activités de traitement de l'application GeoSylva.

---

## Traitements identifiés

### T-01 : Inventaire forestier

| Rubrique | Détail |
|----------|--------|
| **Finalité** | Saisie, stockage et calcul de données d'inventaire forestier (arbres, placettes, parcelles) |
| **Base légale** | Exécution d'un contrat (Art. 6§1.b) |
| **Catégories de données** | Identité (proprietaireNom, gestionnaireNom), localisation (GPS, WKT), cadastrales (section, numéro), dendrométriques (diamètre, hauteur, essence) |
| **Catégories de personnes** | Propriétaires forestiers, gestionnaires, opérateurs terrain |
| **Destinataires** | Aucun (stockage local uniquement) |
| **Transferts hors UE** | Aucun pour ce traitement |
| **Durée de conservation** | Jusqu'à suppression par l'utilisateur |
| **Mesures de sécurité** | SQLCipher (AES-256), Android Keystore, FLAG_SECURE |

### T-02 : Évaluation IBP (Indice de Biodiversité)

| Rubrique | Détail |
|----------|--------|
| **Finalité** | Évaluation de la biodiversité forestière selon la méthode IBP (Larrieu & Gonin 2008) |
| **Base légale** | Intérêt légitime (Art. 6§1.f) |
| **Catégories de données** | Identité (observerName, evaluatorName), localisation (GPS), photographies (photosJson) |
| **Catégories de personnes** | Observateurs, évaluateurs |
| **Destinataires** | Aucun (stockage local uniquement) |
| **Transferts hors UE** | Aucun |
| **Durée de conservation** | Jusqu'à suppression par l'utilisateur |
| **Mesures de sécurité** | SQLCipher, Android Keystore |

### T-03 : Affichage cartographique

| Rubrique | Détail |
|----------|--------|
| **Finalité** | Affichage de fonds cartographiques (satellite, topographique, cadastral) |
| **Base légale** | Intérêt légitime (Art. 6§1.f) |
| **Catégories de données** | Coordonnées de la zone visible (bbox) — non personnelles en elles-mêmes |
| **Destinataires** | IGN, OSM, MapLibre, CartoCDN, Esri, OpenTopoMap |
| **Transferts hors UE** | Oui — Esri (USA), MapLibre (USA), CartoCDN (USA) |
| **Durée de conservation** | Pas de stockage (requêtes HTTP éphémères) |
| **Mesures de sécurité** | TLS 1.2+, Certificate pinning (release) |

### T-04 : Synchronisation des prix du bois

| Rubrique | Détail |
|----------|--------|
| **Finalité** | Téléchargement de prix du bois marché (HTTP GET) |
| **Base légale** | Intérêt légitime (Art. 6§1.f) |
| **Catégories de données** | Aucune donnée personnelle envoyée (GET uniquement) |
| **Destinataires** | URL configurée par l'utilisateur |
| **Transferts hors UE** | Variable (selon l'URL configurée) |
| **Durée de conservation** | Prix stockés localement jusqu'à mise à jour |
| **Mesures de sécurité** | SecureHttpClient (TLS, pinning en release) |

### T-05 : Export de données

| Rubrique | Détail |
|----------|--------|
| **Finalité** | Export des données forestières (CSV, XLSX, JSON, ZIP) |
| **Base légale** | Exécution d'un contrat (Art. 6§1.b) — droit à la portabilité (Art. 20) |
| **Catégories de données** | Toutes les données stockées (identité, localisation, cadastrales, dendrométriques) |
| **Destinataires** | L'utilisateur lui-même (via share Android) |
| **Transferts hors UE** | Aucun (fichier local) |
| **Durée de conservation** | Jusqu'à suppression du fichier par l'utilisateur |
| **Mesures de sécurité** | Chiffrement BOM UTF-8, pas d'envoi automatique |

### T-06 : Photographies de terrain

| Rubrique | Détail |
|----------|--------|
| **Finalité** | Documentation visuelle des stations, arbres, habitats |
| **Base légale** | Consentement (Art. 6§1.a) |
| **Catégories de données** | Photographies (peuvent contenir des personnes identifiables) |
| **Catégories de personnes** | Personnes photographiées accidentellement |
| **Destinataires** | Aucun (stockage local uniquement) |
| **Transferts hors UE** | Aucun |
| **Durée de conservation** | Jusqu'à suppression par l'utilisateur |
| **Mesures de sécurité** | Stockage scoped, EXIF préservé localement uniquement |

### T-07 : Diagnostic de station et recommandations

| Rubrique | Détail |
|----------|--------|
| **Finalité** | Génération de recommandations sylvicoles (diagnostic station, IBP, projections climatiques) |
| **Base légale** | Intérêt légitime (Art. 6§1.f) |
| **Catégories de données** | Données forestières (essences, diamètres, hauteurs, localisation) |
| **Décision automatisée** | Oui — recommandations assistives (Art. 22) |
| **Oversight humain** | Recommandations non contraignantes, l'utilisateur reste décisionnaire |
| **Destinataires** | Aucun |
| **Durée de conservation** | Jusqu'à suppression par l'utilisateur |
| **Mesures de sécurité** | SQLCipher |

### T-08 : Crash logging

| Rubrique | Détail |
|----------|--------|
| **Finalité** | Diagnostic technique des crashes |
| **Base légale** | Intérêt légitime (Art. 6§1.f) |
| **Catégories de données** | Stack trace, modèle d'appareil |
| **Destinataires** | Aucun (jamais envoyé, stocké localement) |
| **Transferts hors UE** | Aucun |
| **Durée de conservation** | Jusqu'à désinstallation |
| **Mesures de sécurité** | Stockage local uniquement |

---

## Sous-traitants

| Sous-traitant | Traitement | Pays | Garanties |
|---------------|-----------|------|-----------|
| IGN (Géoportail) | T-03 Tuiles carto | France (UE) | Service public français, RGPD applicable |
| OpenStreetMap Foundation | T-03 Tuiles carto | Royaume-Uni (UE) | Privacy policy OSM, RGPD applicable |
| OpenTopoMap | T-03 Tuiles carto | Allemagne (UE) | RGPD applicable |
| MapLibre | T-03 Tuiles carto | USA | SCC adoptées (Décision 2021/914), Privacy Shield invalidé |
| CartoCDN | T-03 Tuiles carto | USA | SCC adoptées (Décision 2021/914), Privacy Shield invalidé |
| Esri | T-03 Tuiles carto | USA | SCC adoptées (Décision 2021/914), Privacy Shield invalidé |

**Note sur les transferts hors UE (Art. 46 RGPD)** : Les transferts vers MapLibre,
CartoCDN et Esri (USA) sont couverts par les Standard Contractual Clauses (SCC)
adoptées par la Commission européenne (Décision d'exécution 2021/914 du 4 juin 2021).
Le Privacy Shield ayant été invalidé par l'arrêt Schrems II (CJUE, 16 juillet 2020),
les SCC constituent la garantie appropriée conformément à l'Art. 46 du RGPD.
Une analyse de transfert (TIA) a été effectuée pour vérifier que les lois américaines
ne remettent pas en cause les garanties des SCC pour les données transférées
(coordonnées bbox uniquement, aucune donnée personnelle identifiante).

---

## Mesures de sécurité techniques et organisationnelles

| Mesure | Implémentation |
|--------|----------------|
| Chiffrement base de données | SQLCipher 4.5.4, AES-256, clé Keystore |
| Chiffrement en transit | TLS 1.2+, Certificate pinning (release) |
| Stockage des clés | Android Keystore (hardware-backed si disponible) |
| Anti-capture d'écran | FLAG_SECURE sur MainActivity |
| Validation des entrées | Sanitisation des imports, validation SQL |
| Permissions minimales | GPS, caméra uniquement, maxSdkVersion sur storage |
| Pas de télémétrie | Aucun analytics, aucun crash report externe |
| Pas de publicité | Aucun SDK publicitaire |
| Backup | allowBackup=false, dataExtractionRules configuré |

---

*Ce registre est tenu à jour conformément à l'Article 30 du RGPD.*
