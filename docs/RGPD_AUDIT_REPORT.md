# Audit RGPD — GeoSylva

**Date de l'audit :** 29 juin 2026
**Auditeur :** Agent de vérification RGPD (intégrité projet)
**Périmètre :** Application Android GeoSylva v2.3.0 (branche `main`, commit `787e1f4`)
**Référentiel :** Règlement (UE) 2016/679 (RGPD), Loi Informatique et Libertés, CNIL

---

## 1. Synthèse exécutive

| Indicateur | Valeur |
|---|---|
| Niveau de conformité RGPD global | **NON CONFORME — corrections prioritaires requises** |
| Non-conformités critiques | **3** |
| Non-conformités majeures | **5** |
| Non-conformités mineures | **4** |
| Données personnelles traitées | Oui (nom, email, observateur, GPS, données cadastrales) |
| Transferts hors UE | Non (stockage 100 % local) |
| Sous-traitants tiers | Oui (IGN, OSM, MapLibre — tuiles de carte) |

L'application traite des **données personnelles identifiables** (PII) mais la politique de confidentialité les nie, le chiffrement de la base locale est **désactivé**, et aucun mécanisme de consentement/droits RGPD n'est implémenté. Trois corrections critiques sont à traiter avant toute mise en production.

---

## 2. Inventaire des traitements de données personnelles

### 2.1 Données personnelles identifiées dans le code

| Donnée | Entité / Champ | Article RGPD | Sensibilité |
|---|---|---|---|
| Nom du propriétaire forestier | `ForetEntity.proprietaireNom` (NOT NULL) | Art. 4 §1 — donnée identifiante | Standard |
| Email du propriétaire | `ForetEntity.proprietaireEmail` | Art. 4 §1 — identifiant de contact | Standard |
| Nom du gestionnaire | `ForetEntity.gestionnaireNom` | Art. 4 §1 | Standard |
| Nom de l'observateur | `StationEntity.observerName` (NOT NULL), `RipisylveEntity.observerName` (NOT NULL) | Art. 4 §1 | Standard |
| Coordonnées GPS précises | `StationEntity.latitude/longitude`, `PlacetteEntity`, `GpsContextCacheEntity` | Art. 4 §1 — données de localisation | Standard (potentiellement sensibles si croisées) |
| Données cadastrales | `ParcelleEntity.codeInseeCommune`, `sectionCadastrale`, `numeroCadastral`, `geometrieIgnWkt` | Art. 4 §1 — permet d'identifier une parcelle et son propriétaire | Standard |
| Numéro de PSG | `ForetEntity.psgNumero` | Art. 4 §1 — identifiant de gestion forestière | Standard |
| Photos de diagnostic | `StationEntity.photosJson` (URI + légende) | Art. 4 §1 — peut contenir des personnes | Standard (à vérifier au cas par cas) |
| Préférences utilisateur | `UserPreferencesManager` (DataStore) | Art. 4 §1 | Standard |
| Logs de crash | `CrashLogger` (modèle d'appareil, stack trace) | Art. 4 §1 — métadonnées | Faible |

### 2.2 Finalités déclarées

- Gestion forestière de terrain (inventaire, martelage, diagnostic station, IBP).
- Géolocalisation des arbres et placettes.
- Export de données (CSV, XLSX, JSON, PDF, Shapefile).

### 2.3 Base légale (Art. 6)

**Non documentée.** Aucun mécanisme de consentement explicite ni d'évaluation d'intérêt légitime n'est implémenté pour la collecte des PII (nom, email, observateur). L'onboarding (`OnboardingScreen.kt`) est purement fonctionnel (présentation des features) et ne contient **aucune page de consentement RGPD**.

---

## 3. Non-conformités

### 3.1 CRITIQUES (à corriger avant mise en production)

#### C1 — Politique de confidentialité mensongère sur les PII
- **Article :** Art. 13 (information transparente), Art. 5 §1.a (loyauté)
- **Fichier :** `PRIVACY_POLICY.md` ligne 23
- **Constat :** La politique indique `No personal identification data (name, email, phone number)`. C'est **faux** : `ForetEntity` collecte `proprietaireNom` (obligatoire), `proprietaireEmail`, `gestionnaireNom`, et `StationEntity`/`RipisylveEntity` collectent `observerName` (obligatoire).
- **Risque :** Information trompeuse de la personne concernée — sanction CNIL jusqu'à 4 % du CA ou 20 M€.
- **Recommandation :** Réécrire la politique en listant explicitement les PII collectées, leur finalité, la base légale, la durée de conservation, et les droits exerçables.

#### C2 — Base de données locale non chiffrée
- **Article :** Art. 32 (sécurité du traitement), Art. 5 §1.f (intégrité et confidentialité)
- **Fichiers :** `ForestryCounterApplication.kt` lignes 136-142, `ForestryDatabase.kt` lignes 130-146, `app/build.gradle.kts` ligne 219
- **Constat :** SQLCipher est **commenté/désactivé** (`net.sqlcipher:android-database-sqlcipher:4.2.0 // Temporairement désactivé`). La base `forestry_counter.db` est créée via `Room.databaseBuilder` standard, **en clair** sur le stockage Android. `DatabaseEncryptionService` existe mais n'est jamais appelé. La méthode `createEncryptedDatabase` est commentée.
- **Risque :** Sur un appareil rooté ou via ADB, les PII (noms, emails, GPS) sont lisibles directement. Les règles de backup (`backup_rules.xml`, `data_extraction_rules.xml`) excluent la base du cloud, ce qui atténue mais ne supprime pas le risque.
- **Recommandation :** Réactiver SQLCipher (ou `androidx.sqlite.SupportOpenHelperFactory` chiffré) et migrer la base existante. Documenter la rotation de clé.

#### C3 — Absence de consentement RGPD à la collecte des PII
- **Article :** Art. 6 (licéité), Art. 7 (conditions du consentement), Art. 13 (information)
- **Fichier :** `OnboardingScreen.kt` (aucune page consentement)
- **Constat :** L'utilisateur saisit des noms et emails de propriétaires forestiers sans aucune information RGPD préalable, sans recueil de consentement, sans mention de la base légale. L'observateur est un champ obligatoire (`observerName TEXT NOT NULL`) sans alternative.
- **Risque :** Traitement illicite. Pour un usage B2B/professionnel, l'intérêt légitime (Art. 6 §1.f) pourrait s'appliquer mais doit être **documenté** (balance des intérêts).
- **Recommandation :** Ajouter une page d'information RGPD dans l'onboarding (finalités, PII collectées, droits, base légale, contact DPO). Pour l'email du propriétaire (non strictement nécessaire à la gestion forestière), évaluer sa minimisation (voir M1).

---

### 3.2 MAJEURES

#### M1 — Absence de minimisation pour l'email du propriétaire
- **Article :** Art. 5 §1.c (minimisation)
- **Fichier :** `ForetEntity.kt` ligne 18 (`proprietaireEmail: String?`)
- **Constat :** L'email du propriétaire forestier n'est pas nécessaire au cœur fonctionnel (inventaire, martelage, calculs). Il est nullable donc techniquement optionnel, mais aucune justification de finalité n'est documentée.
- **Recommandation :** Soit supprimer le champ, soit documenter une finalité explicite (ex. envoi du PSG par email) et l'assortir d'un consentement.

#### M2 — Droit à l'effacement (Art. 17) non implémenté de façon centralisée
- **Article :** Art. 17 (droit à l'effacement)
- **Fichiers :** `SettingsScreen.kt` (section "Privacy" ne contient que `CrashLogger.clearLogs`)
- **Constat :** Il n'existe pas de fonction « Effacer toutes mes données » dans les paramètres. Les DAO offrent des suppressions par entité (`deleteById`, `deleteAllParcelles`), mais aucune action utilisateur unique ne permet d'exercer le droit à l'effacement. La désinstallation de l'app supprime les données (correct), mais le RGPD exige un moyen in-app pour les utilisateurs qui ne désinstallent pas.
- **Recommandation :** Ajouter dans `SettingsScreen` une action « Effacer toutes mes données » (avec confirmation) qui vide toutes les tables contenant des PII et purge les photos associées.

#### M3 — `PriceSyncWorker` utilise un client HTTP non sécurisé
- **Article :** Art. 32 (sécurité), Art. 5 §1.f (confidentialité)
- **Fichier :** `PriceSyncWorker.kt` ligne 33 (`OkHttpClient()`)
- **Constat :** Le worker crée un `OkHttpClient` brut **sans** passer par `SecureHttpClient`, donc sans timeout configuré, sans respect de la `network_security_config`, et l'URL est **configurable par l'utilisateur** (risque d'exfiltration vers un serveur arbitraire). Bien qu'aucune PII ne soit envoyée (HTTP GET simple), l'absence de validation de l'URL et de TLS strict est une faille.
- **Recommandation :** Remplacer par `SecureHttpClient.createSecureClient(context)` et valider le schéma HTTPS de l'URL saisie.

#### M4 — Certificate pinning désactivé
- **Article :** Art. 32 (sécurité)
- **Fichier :** `SecureHttpClient.kt` lignes 47-56
- **Constat :** Le pinning est commenté. La sécurité repose uniquement sur la validation CA système, ce qui expose aux attaques MITM via CA compromis/étatiques. Les requêtes de tuiles de carte transmettent les coordonnées GPS de la zone visible.
- **Recommandation :** Activer le pinning pour les domaines `SECURE_DOMAINS` (IGN, OSM, MapLibre) avec hashes SHA-256 + pin de backup, et planifier la rotation.

#### M5 — Absence d'information sur les sous-traitants (tuiles de carte)
- **Article :** Art. 13 §1.e, Art. 28 (sous-traitants), Art. 44+ (transferts)
- **Fichiers :** `PRIVACY_POLICY.md` section "Third-Party Services", `WmsLayerManager.kt`
- **Constat :** Les tuiles IGN (data.geopf.fr — France, OK), OSM (tile.openstreetmap.org — Royaume-Uni/UE, OK), MapLibre (demotiles.maplibre.org — hébergement à vérifier), CartoCDN, ArcGIS Online (Esri — États-Unis ⚠️). Les requêtes transmettent l'IP de l'utilisateur et les coordonnées de la zone visualisée. La politique mentionne ces services mais ne qualifie pas leur statut (sous-traitant vs destinataire) ni les garanties pour les transferts hors UE (Esri/USA).
- **Recommandation :** Documenter le statut de chaque service, les garanties (CGU, DPA), et pour Esri/USA les clauses contractuelles types (SCC) ou la Privacy Shield (invalide — utiliser SCC).

---

### 3.3 MINEURES

#### m1 — Logs de crash potentiellement riches en PII
- **Article :** Art. 5 §1.c (minimisation), Art. 32
- **Fichier :** `CrashLogger.kt`
- **Constat :** Les stack traces peuvent contenir des PII (noms de propriétaires, emails) si une exception se produit pendant leur traitement. Le logger capture aussi `Build.MANUFACTURER`/`MODEL` (metadonnées acceptables). Pas de transmission automatique (opt-in via settings), ce qui est correct.
- **Recommandation :** Ajouter un filtre/redaction des PII dans les stack traces avant écriture, ou avertir l'utilisateur que les logs peuvent contenir des données contextuelles.

#### m2 — Index sur `proprietaireNom` sans finalité documentée
- **Article :** Art. 5 §1.c
- **Fichier :** `ForetEntity.kt` ligne 10, `ForetDao.kt` ligne 29
- **Constat :** Un index est créé sur `proprietaireNom` et une recherche LIKE est exposée. La finalité de recherche par nom de propriétaire n'est pas documentée.
- **Recommandation :** Documenter la finalité (ex. filtrer ses forêts) ou supprimer l'index si non utilisé.

#### m3 — Durée de conservation non documentée
- **Article :** Art. 5 §1.e (limitation de conservation), Art. 13 §2.a
- **Fichier :** `PRIVACY_POLICY.md` section "Data Retention"
- **Constat :** La politique dit « jusqu'à suppression par l'utilisateur » mais ne définit pas de durée recommandée ni de mécanisme de purge automatique des données inactives (ex. forêts non modifiées depuis X ans).
- **Recommandation :** Définir une durée de conservation (ex. durée du contrat de gestion + 5 ans) et un mécanisme d'alerte/purge.

#### m4 — Contact DPO / responsable du traitement non identifié
- **Article :** Art. 13 §1.b, Art. 37-39 (DPO)
- **Fichier :** `PRIVACY_POLICY.md` ligne 90 (`[Contact the project owner]`)
- **Constat :** Le contact est un placeholder. Aucun responsable du traitement nommé, aucun DPO désigné (non obligatoire mais recommandé pour un usage professionnel).
- **Recommandation :** Nommer le responsable du traitement (Micro Entreprise — Camil) et fournir une adresse email RGPD dédiée.

---

## 4. Points conformes (à conserver)

| Point | Statut | Référence |
|---|---|---|
| Stockage 100 % local (pas de backend propriétaire) | Conforme | Art. 5 §1.c |
| Permissions Android minimales et justifiées | Conforme | Manifest |
| `allowBackup="false"` + exclusion DB du backup cloud | Conforme | Art. 32 |
| `network_security_config` HTTPS-only (cleartext interdit sauf localhost debug) | Conforme | Art. 32 |
| Pas d'analytics/crash reporting tiers (Firebase, etc.) | Conforme | Art. 5 §1.c |
| Pas de publicité / ad identifiers | Conforme | Art. 5 §1.c |
| Pas de login/compte (donc pas de mots de passe à gérer) | Conforme | — |
| Export via Storage Access Framework (contrôle utilisateur) | Conforme | Art. 20 (portabilité) |
| `EncryptedSharedPreferences` pour les préférences sensibles | Conforme | Art. 32 |
| R8/ProGuard activé en release | Conforme | Art. 32 |
| Keystore de signature exclu du version control | Conforme | Art. 32 |
| Logs HTTP limités au mode DEBUG (`isDebugBuild()`) | Conforme | Art. 5 §1.c |
| `SECURITY.md` présent (bonnes pratiques contributeurs) | Conforme | — |

---

## 5. Plan de remédiation prioritaire

### Phase 1 — Avant mise en production (critique)
1. **C1** — Réécrire `PRIVACY_POLICY.md` avec liste exacte des PII (proprietaireNom, proprietaireEmail, gestionnaireNom, observerName, GPS, données cadastrales).
2. **C2** — Réactiver le chiffrement SQLCipher de la base + migration des données existantes.
3. **C3** — Ajouter une page d'information/consentement RGPD dans `OnboardingScreen`.

### Phase 2 — Sous 30 jours (majeur)
4. **M1** — Décider sur l'email propriétaire (suppression ou finalité documentée + consentement).
5. **M2** — Implémenter « Effacer toutes mes données » dans `SettingsScreen`.
6. **M3** — Remplacer `OkHttpClient()` par `SecureHttpClient` dans `PriceSyncWorker` + validation HTTPS.
7. **M4** — Activer le certificate pinning pour les domaines cartographiques.
8. **M5** — Documenter les sous-traitants et les transferts (SCC pour Esri/USA).

### Phase 3 — Sous 90 jours (mineur)
9. **m1** — Redaction des PII dans les crash logs.
10. **m2** — Documenter ou supprimer l'index `proprietaireNom`.
11. **m3** — Définir et documenter la durée de conservation.
12. **m4** — Nommer le responsable du traitement + contact RGPD.

---

## 6. Registre des traitements (Art. 30) — ébauche

| Champ | Valeur |
|---|---|
| Responsable du traitement | Micro Entreprise (Camil) — à formaliser |
| Finalité | Gestion forestière de terrain (inventaire, martelage, diagnostic, IBP) |
| Données | PII : nom/email propriétaire, nom gestionnaire, nom observateur, GPS, données cadastrales |
| Base légale | À documenter (intérêt légitime Art. 6 §1.f probable pour usage pro) |
| Destinataires | Aucun (stockage local) ; sous-traitants tuiles : IGN, OSM, MapLibre, Esri |
| Transferts hors UE | Esri (USA) — SCC requises |
| Durée de conservation | À définir |
| Mesures de sécurité | À compléter après chiffrement (C2) |

---

## 7. Conclusion

GeoSylva présente une **architecture privacy-friendly par défaut** (stockage local, pas de compte, pas d'analytics, permissions minimales, backup exclu) qui est un excellent point de départ. Cependant, le projet traite en réalité des **données personnelles identifiables** que la politique de confidentialité nie, sans chiffrement au repos et sans mécanisme de consentement. Ces trois points (C1, C2, C3) doivent être corrigés **avant toute diffusion** pour éviter une sanction CNIL.

Une fois les 3 corrections critiques et les 5 majeures appliquées, le projet atteindrait un niveau de conformité RGPD solide pour une application professionnelle B2B.

---

*Rapport généré le 29/06/2026 — à conserver dans le registre de conformité du projet.*
