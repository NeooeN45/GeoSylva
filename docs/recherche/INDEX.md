# INDEX — Base de connaissances recherche GeoSylva

## 0. Architecture et livraison V2

Les documents de référence pour la mise à niveau de GeoSylva sont regroupés dans :

- ../00_ARCHITECTURE_V2_DOCUMENTATION_INDEX.md — index, priorité des sources et état réel ;
- ../MARTELAGE_V2_SPECIFICATION.md — session de martelage, états et parcours terrain ;
- ../SYNTHESE_V2_SPECIFICATION.md — synthèse structurée, preuves et incertitudes ;
- ../MIGRATION_CUBAGE_MARTELAGE_SYNTHESE_V2.md — migration progressive et portes de retrait ;
- contrat GSIE — GSIE/API/docs/GEOSYLVA_CUBAGE_OFFLINE_FIRST_V1.md.

Les documents de recherche ci-dessous alimentent ces spécifications, mais ne valent pas
qualification scientifique automatique. Les statuts Draft, Review et Validé doivent rester
visibles lors de toute intégration.

Voir `_METHODOLOGIE.md` pour les règles de production de ces documents.

## 1. Cubage / Volume (01_cubage_volume/)

- **`../REFERENTIEL_CUBAGE_VENTE_FRANCE.md`** — Référentiel maître, statut : Draft.
  3 points clés :
  1. Sépare volume sur pied, volume commercial, volume mobilisable, écorce, m³ réel, m³ apparent et stère.
  2. Recommande une méthode selon l'objet mesuré et impose source, version, domaine de validité et incertitude.
  3. Présente les limites des prix FBF : moyenne de panel, sur pied, sans ventilation universelle par qualité/département.

- **`../DONNEES_ENTREES_SORTIES_CUBAGE_VENTE.md`** — Contrat central des données nécessaires par résultat, statut : Draft.
  3 points clés :
  1. Distingue les données automatiques, saisies, capteurs, fichiers, GSIE et données dérivées.
  2. Définit les entrées minimales pour inventaire, cubage, produits, biomasse, carbone et valorisation.
  3. Liste les données que GeoSylva ne doit jamais déduire sans preuve et les états d'erreur attendus.

- **`01_cubage_volume/06_cubage_forestier_global_france.md`** — Panorama mondial et adaptation française, statut : Draft.
  3 points clés :
  1. Compare tarifs, équations, cubage direct de grumes, biomasse, LiDAR et IA.
  2. Documente Huber, Smalian, Newton, cône et V = G × H × f.
  3. Rappelle que la méthode et le tarif doivent respecter la population de calibration.

- **`01_cubage_volume/07_essences_produits_qualites_france.md`** — Essences, produits et qualités, statut : Draft.
  3 points clés :
  1. Distingue essences à prix suivi, essences secondaires/niche et taxons arborés.
  2. Relie les produits à la qualité, aux dimensions et aux défauts sans transformer A-D en prix normatif.
  3. Prépare le mapping des codes locaux vers les noms scientifiques et les zones.

- **`01_cubage_volume/08_audit_formules_coefficients_2026-08-30.md`** — Audit des formules et coefficients, statut : Draft.
  3 points clés :
  1. Identifie la contradiction Schaeffer/code-documentation et les coefficients non revalidés.
  2. Sépare la forme de type Schumacher-Hall actuellement nommée Algan.
  3. Interdit toute correction automatique avant retour aux sources primaires.

- **`01_cubage_volume/09_registre_methodes_cubage_france_monde.md`** — Registre méthode par méthode et variantes régionales, statut : Draft.
  3 points clés :
  1. Sépare formules géométriques, tarifs sur pied, barèmes à tables et modèles statistiques.
  2. Identifie Chaudé, Lapasse, Algan-Monnin, Bouchon, IFN/IGN, EMERGE et Schumacher-Hall.
  3. Décrit le statut de qualification avant traduction en Kotlin.

- **`01_cubage_volume/methodes_cubage_registry.json`** — Registre machine-readable préparatoire, statut : Draft.

- **`01_cubage_volume/02_tarifs_ifn_emerge.md`** — Statut : brouillon (vérifié pour la partie
  « test d'accès EMERGE », non vérifié pour les coefficients IFN eux-mêmes).
  3 points clés :
  1. Les 36 tarifs IFN Rapide + 8 tarifs IFN Lent de `TarifData.kt` ne sont adossés à aucune
     source primaire retrouvée en accès libre (contrairement à Schaeffer/Algan) — leur
     progression quasi-géométrique suggère une génération algorithmique à vérifier.
  2. Test réel effectué le 2026-07-02 sur l'URL de données EMERGE
     (geodata.inrae.fr/.../27f18f57-...) : accès **Restricted/Restricted** confirmé (HTTP 200
     mais notice de métadonnées uniquement, pas de fichier téléchargeable) — ne pas intégrer
     EMERGE tel quel, contacter l'INRAE/LERFoB si besoin produit réel.
  3. Alternative ouverte identifiée : DataIFN (données brutes IFN, Licence Ouverte Etalab 2.0,
     https://inventaire-forestier.ign.fr/dataifn/) permettrait de recalculer des tarifs
     traçables, mais nécessite un travail de data science hors périmètre de cette recherche.

- **Fichier** : `01_cubage_volume/01_tarifs_schaeffer_algan.md`
  **Statut** : brouillon (sources scientifiques/pédagogiques tierces recoupées, tables ONF/IGN
  officielles papier non consultées — vérification manuelle requise)
  **3 points clés** :
  1. Formule sourcée et vérifiée des tarifs Schaeffer/Algan 1 entrée : `V=(5/70000)×(8+num)×(D-5)×(D-10)`
     (D en cm), avec 4 familles (rapide/intermédiaire/lent/très lent) — diffère structurellement de
     `V=a+b×C²` codé dans `TarifData.kt` (écart numérique constaté jusqu'à +142 % sur un cas test).
  2. Les tarifs Algan historiques ne sont **pas** essence-spécifiques ; les coefficients
     `TarifData.alganCoefs` (par essence, forme `a×D^b×H^c`) ressemblent plutôt aux équations
     nationales de volume par essence (Vallet et al. 2006) — attribution "Algan (1958)" à vérifier.
  3. Guide de décision Schaeffer 1E/2E/Algan selon peuplement régulier/irrégulier, disponibilité de
     la hauteur, et usage (terrain rapide vs inventaire/expertise de précision).

- **`03_coefficients_forme_biomasse.md`** — Statut : brouillon (vérifié partiellement — tableau
  chiffré Pardé & Bouchon 1988 non extrait faute d'accès au PDF exploitable, cf. limites).
  3 points clés :
  1. La bibliothèque **LERFoB Forest Tools** existe réellement (https://github.com/CWFC-CCFB/lerfobforesttools,
     LGPL-3.0) et sa classe `FrenchCommercialVolume2020Predictor` existe aussi (pas une hallucination) ;
     mais elle dépend lourdement de `javax.swing`/`java.awt` (via `repicea.gui`) et d'autres briques
     desktop (Access, jaxb, jfreechart) → **non intégrable directement dans l'APK Android** (blocage
     technique, pas seulement de licence).
  2. **LGPL-3.0 n'interdit pas l'usage commercial** (contrairement à GPL/AGPL) et n'oblige pas à
     ouvrir le code de GeoSylva ; le risque réel se limiterait à un fork/modification de la lib
     elle-même. Recommandation : **réimplémenter les formules en Kotlin natif** plutôt qu'importer
     le jar, ce qui évite à la fois le blocage AWT et l'ambiguïté LGPL/linkage statique mobile.
  3. Le modèle le plus récent réellement utilisé par les professionnels français pour
     volume/biomasse par essence est le **projet EMERGE** (Vallet et al. 2006, Deleuze et al. 2013),
     postérieur à Pardé & Bouchon 1988 ; pour le carbone, la référence officielle reste
     CARBOFOR (2004) + méthodologie IGN 2021 (FER = 1.30 conifères / 1.28 feuillus, carbone = 47.5 %
     de la biomasse sèche).

- **`04_tables_production_croissance.md`** — statut : brouillon (recherche documentaire, valeurs
  numériques des tables non revérifiées contre l'ouvrage source payant/indisponible en ligne).
  3 points clés :
  1. Le bug signalé « Hm au lieu de Hdom » dans `ExpertForestryCalculator.kt` est **partiellement
     infirmé** sur le calcul brut de Hdom (`computeHdom()` est correct : moyenne des 100 plus gros
     arbres/ha) mais **confirmé sous une autre forme** : le champ `ProductionData.hauteurMoyenne`
     contient en réalité des valeurs de Hdom (tables Décourt & Pardé) mais est comparé à une vraie
     Hm terrain dans `EnhancedForestryCalculator.kt` (ligne 86, `hauteurConforme`), faussant le
     diagnostic de conformité ONF affiché à l'utilisateur.
  2. L'indice de station (`calculateIndiceDeStation()`) reste une approximation IS ≈ Hdom courante
     (âge ignoré, `@Suppress("UNUSED_PARAMETER")`) — limite déjà documentée dans le code, pas un
     bug caché, mais son impact (sous-estimation potentielle de la fertilité) mérite d'être remonté
     dans l'UI utilisateur, pas seulement en commentaire Kotlin.
  3. Aucun modèle de croissance dynamique français (CAPSIS, Fagacées, SimCoP Douglas, Forêts-21)
     n'expose d'API publique ni de licence open-source claire permettant une intégration mobile
     directe à court terme — rester sur les tables statiques Décourt & Pardé, en les complétant si
     possible par les faisceaux de courbes Hdom~âge (Duplat & Tran-Ha 1997 chêne, Duplat et al.
     hêtre) pour un vrai indice de fertilité.

- **Fichier** : `01_cubage_volume/05_normes_qualite_bois.md`
  **Statut** : brouillon (texte intégral des normes NF EN 1316/1927 non consulté — accès payant AFNOR)
  **3 points clés** :
  1. NF EN 1316-1 (chêne/hêtre) et NF EN 1927-1/2 (résineux) fixent des **classes qualitatives A-D**
     (codes normalisés Q-A…Q-D, F-A…F-D selon essence), mais **aucune norme ne fixe de coefficient de
     prix** — les multiplicateurs ×2.5/×1.5/×1.0/×0.4 de GeoSylva sont des estimations de marché, pas
     des valeurs normatives ; à reformuler dans `REFERENTIELS_FORESTIERS_EXTERNES.md` §2.5.
  2. L'architecture GeoSylva (`WoodQualityGrade` + `WoodDefect` + coefficients par essence dans
     `PriceCalculator`) est structurellement alignée avec l'esprit des normes (classes + catalogue de
     singularités NF EN 1310), mais une **incohérence de ratio A/D** existe entre le multiplicateur
     générique `WoodQualityGrade` (6.25) et le wildcard `PriceCalculator["*"]` (3.27) pour les
     essences non listées spécifiquement — à harmoniser.
  3. Comparé aux ordres de grandeur de marché documentés (chêne 50-450 €/m³, jusqu'à 2 500 €/m³
     d'exception ; FBF moyenne 2024 ~228 €/m³), le ratio A/D générique de GeoSylva est plausible pour
     une essence « moyenne » mais surestimé pour le Douglas/résineux courants (marché ≈×3 vs ×6.25
     générique) et potentiellement sous-estimé pour les feuillus précieux d'exception.

## 2. Marché / Prix (02_marche_prix/)

- **`02_marche_prix/06_prix_provenance_actualisation.md`** — Prix, provenance et mise à jour trimestrielle, statut : Draft.
  3 points clés :
  1. Définit les conditions de comparabilité d'un prix et sépare FBF, ONF, Agreste, CEEB et ADEME.
  2. Documente les valeurs FBF 2024-2025 avec leur portée réelle : bois d'œuvre sur pied, moyenne de panel.
  3. Propose un pipeline détecter → qualifier → extraire → normaliser → valider → historiser.

- **`02_marche_prix/07_vente_tracabilite_normes_france.md`** — Vente, mesurage, contrats, normes et traçabilité, statut : Draft.
  3 points clés :
  1. Liste les clauses nécessaires pour relier lot, volume, qualité, prix, risques et réception.
  2. Sépare classement de grume, classement mécanique de sciage, certification et provenance.
  3. Signale les obligations EUTR/EUDR et les points nécessitant une validation juridique à jour.

- **`02_marche_prix/08_modele_donnees_cubage_prix.md`** — Modèle de données offline-first, statut : Draft.
  3 points clés :
  1. Propose Measurement, CubageRun, PriceObservation et PriceRule historisée.
  2. Mappe le modèle vers `Tige`, `Essence`, `TarifMethod`, `PriceEntry` et `CalculationRunEntity`.
  3. Interdit les conversions d'unités ou les fallbacks sans source et domaine explicites.

- **`02_marche_prix/prix_observation.schema.json`** — JSON Schema d'import, statut : Draft.
- **`02_marche_prix/vecteurs_tests_cubage.json`** — Vecteurs numériques géométriques, statut : Draft.
- **`02_marche_prix/catalogue_essences_commerciales_france.csv`** — Catalogue structuré de départ, statut : Draft.
  Les cellules vides signifient « non disponible ou non vérifié » ; elles ne valent jamais zéro.

- **`02_marche_prix/09_matrice_sources_prix_regions_2026-08-30.md`** — Couverture des marchés et régions, statut : Draft.
  3 points clés :
  1. Distingue les observations régionales des coefficients de modèle GRECO/région.
  2. Sépare les séries FBF, ONF, Agreste, CEEB/CIBE et ADEME.
  3. Définit les champs nécessaires à l'actualisation trimestrielle.

- **`02_marche_prix/10_plan_tests_cubage_vente_2026-08-30.md` — Plan QA du contrat cubage/vente, statut : Draft.
  3 points clés :
  1. Couvre entrées, unités, méthodes, produits, qualité, prix, biomasse et capteurs.
  2. Exige des attendus indépendants des constantes du code.
  3. Marque explicitement les tests à écrire, sans prétendre qu'ils sont tous implémentés.

Les prix régionaux existants restent documentés dans `02_prix_regionaux.md` ; ce nouveau corpus ne les remplace pas.

- **`02_marche_prix/01_prix_national_fbf.md`** — Statut : brouillon complété le 2026-08-30 (sources officielles FBF/CNPF/La Forestière recoupées ; les valeurs 2024-2025 par groupe d'essences sont confirmées dans la plaquette officielle et reprises dans `06_prix_provenance_actualisation.md`). Les ventilations plus fines par qualité, diamètre et provenance restent à vérifier.
  **3 points clés** :
  1. L'indicateur FBF (co-produit La Forestière/ASFFOR/EFF, panel = ventes groupées d'Experts
     Forestiers de France, ~60 ventes/an, ~3000 lots, €/m³ sur pied HT) donne un prix moyen toutes
     essences de **90 €/m³ en 2024** (+7 %) puis **86 €/m³ en 2025** (-4 %) ; les résineux atteignent
     en 2025 leur plus haut niveau depuis la création de l'indice (2004), avec 4 essences (douglas,
     épicéa commun, pin laricio, pin sylvestre) à leur record historique, tandis que les feuillus
     (chêne notamment) reculent.
  2. Écarts significatifs détectés entre les prix FBF 2024 par essence (chêne 228 €/m³, douglas
     89 €/m³, pin maritime 56 €/m³, peuplier 73 €/m³, châtaignier 119 €/m³, frêne 158 €/m³, hêtre
     56 €/m³) et les valeurs codées en dur dans `RegionalPricePresets.kt` (qualité C, sur pied) :
     sous-évaluation de -19 % (douglas) à -60 % (chêne), à l'exception du hêtre (+7 %, cohérent) —
     à recalibrer en priorité pour douglas/pin maritime/chêne.
  3. Le libellé `YEAR = 2025` dans `RegionalPricePresets.kt` semble confondre le numéro d'édition de
     l'indicateur FBF avec l'année réelle des prix constatés (l'« indicateur 2025 » porte sur les
     prix 2024) — à corriger ; la comparaison chiffrée reste en outre limitée par l'absence de
     ventilation qualité A/B/C/D connue du panel FBF (prix moyen probablement tiré vers le haut par
     des lots de qualité supérieure, non strictement comparable à une « qualité C »).

- **`02_marche_prix/03_onf_cooperatives_bois_energie.md`** — Statut : brouillon (sources
  officielles/tierces recoupées par recherche web + un test d'API réel réussi ; PDF ONF non lus
  en détail, cf. limites du document).
  3 points clés :
  1. **ventesdebois.onf.fr** est une Single Page Application (JS, hash-routing `/vel/#/...`) sans
     API/export CSV/JSON identifié ; un test réel a renvoyé HTTP 401 sur `robots.txt` et un
     contenu vide (`Loading...`) sur les pages — scraping **non recommandé** (faisabilité
     technique faible + risque légal/CGU non maîtrisé) ; l'indice ONF trimestriel reste
     accessible uniquement en **PDF** via l'Observatoire France Bois Forêt.
  2. **Les coopératives forestières** (UCFF/12 coopératives, Alliance Forêts Bois, UNISYLVA,
     Coforêt, Forestarn...) ne publient **aucun indicateur de prix chiffré public** — uniquement
     des services d'estimation individualisés ; à écarter comme source de données de pricing.
  3. **Découverte clé** : l'**ADEME publie un dataset ouvert avec une vraie API JSON sans clé**
     (`https://data.ademe.fr/data-fair/api/v1/datasets/prix-bois-domestique/lines`, licence
     Etalab, testée avec succès le 2026-07-02, série 2005-2024, prix bûches/granulés en €/MWh
     PCI) — c'est la meilleure source technique de ce document pour un futur module « bois de
     chauffage », à condition de sourcer un facteur de conversion €/MWh↔€/stère avant affichage.

- **`02_marche_prix/05_marche_carbone_forestier.md`** — Statut : brouillon (sources officielles LBC
  et étude sectorielle InfoCC/I4CE recoupées ; prix de marché non "cotés", détail des rabais LBC
  et facteurs ADEME Base Carbone bois non extraits intégralement — vérification manuelle requise).
  3 points clés :
  1. Le Label Bas-Carbone compte **4 méthodes forestières** (pas 2) : Boisement, Reconstitution de
     peuplements dégradés, **Balivage** (conversion taillis feuillu → futaie sur souches,
     10-30 ans, feuillus uniquement) et Gestion Forestière à Stock Continu (GFSC) — Balivage/GFSC
     sont les plus pertinentes pour un propriétaire *forêt existante* déjà inventoriée avec
     GeoSylva, contrairement à Boisement qui suppose un terrain non boisé depuis 10 ans.
  2. Prix du crédit carbone LBC forestier : **20 à 70 €/tCO2e** (I4CE, bilan 2025), moyenne LBC
     toutes méthodes confondues ≈31-35 €/tCO2e — **3 à 4× plus élevé** que les standards
     internationaux (Verra/Gold Standard ≈5-8 €/t) ; **aucun prix de marché "coté"**, tout se
     négocie de gré à gré (le ministère confirme une fourchette officielle de 8 à 125 €/tCO2e).
  3. La physique du calcul volume→carbone est déjà sourcée dans
     `01_cubage_volume/03_coefficients_forme_biomasse.md` (formule CARBOFOR/IGN) — le LBC n'ajoute
     pas de formule alternative, seulement un calcul **différentiel** (projet − scénario de
     référence) non capturable par un simple inventaire ponctuel GeoSylva. Une fonctionnalité
     "ordre de grandeur carbone" (non contractuelle) est faisable à court terme ; un vrai
     simulateur de dossier LBC ne l'est pas (nécessite simulation de croissance à 30 ans absente
     de l'app).

- **`02_marche_prix/04_valeur_fonciere_forestiere.md`** — Statut : brouillon (sources officielles
  SAFER/Cerema recoupées, moyenne SAFER contestée par un tiers non revérifiée officiellement).
  3 points clés :
  1. La SAFER publie chaque année (étude « Le prix des terres », gratuite, sans clé) un prix moyen
     national des forêts (**4 850 €/ha en 2024, +2,2 %**, 90 % des ventes entre 730 et 14 570 €/ha),
     mais uniquement à la granularité de **7 grandes régions forestières** — pas de commune/
     département comme pour les terres agricoles, et pas d'API/fichier structuré identifié.
  2. Cette moyenne SAFER est **activement contestée** par un expert forestier (Pierre Aussedat) qui
     calcule une moyenne brute à **13 585 €/ha** (montant total/surface totale, ×2,8 le chiffre
     officiel) — écart non expliqué en détail par la SAFER dans le document public consulté
     `[À VÉRIFIER MANUELLEMENT]` : tout affichage GeoSylva doit montrer une fourchette, jamais un
     chiffre unique. Aucun « indice Fransylva » chiffré et public n'a été trouvé (lacune confirmée).
  3. Le foncier forestier **est identifiable dans DVF géolocalisées** (open data gratuit,
     data.gouv.fr, Licence Ouverte 2.0) via le champ `nature_culture` / codes DGFiP B/BF/BM/BO/BP/
     BR/BS/BT (classe "Bois") — donnée brute exploitable à la parcelle, contrairement au modèle
     enrichi DVF+/DV3F qui reste réservé aux organismes à mission de service public (non accessible
     à GeoSylva en tant qu'entreprise privée).

- **`02_marche_prix/02_prix_regionaux.md`** — Statut : brouillon (recherche web multi-sources,
  aucun PDF officiel FBF/CNPF téléchargé et lu intégralement — vérification manuelle requise avant
  intégration produit).
  3 points clés :
  1. Correction factuelle : `RegionalPricePresets.kt` contient en réalité **13 presets organisés par
     GRECO** (NATIONAL + 12 zones écologiques IGN A-L), pas "7 presets régionaux" comme indiqué dans
     la commande initiale — le système de prix de l'app est structuré par région écologique, pas par
     région administrative.
  2. Sur les 13 régions administratives françaises, seules **Bretagne, Pays de la Loire (délégation
     CNPF fusionnée), Nouvelle-Aquitaine et Occitanie (via Fibois Observabois)** disposent d'un
     observatoire régional de prix du bois sur pied exploitable en accès libre ; le Grand Est a un
     statut incertain (observatoire annoncé "à venir" en 2018, non revérifié) ; **7 régions
     (Auvergne-Rhône-Alpes, PACA, Bourgogne-Franche-Comté, Centre-Val de Loire, Île-de-France,
     Normandie, Corse) n'ont aucun observatoire de prix régional identifié en accès libre.**
  3. Piège identifié : plusieurs "observatoires" Fibois régionaux trouvés (AURA, Bourgogne-Franche-
     Comté, Normandie, Île-de-France, Grand Est) ne portent PAS sur le prix du bois sur pied mais sur
     le bois énergie/bûche, le coût des machines forestières, ou la construction bois — à ne pas
     confondre lors d'une future intégration.

## 3. Climat (03_climat/)
_(à compléter par les agents de la vague 3 — relance après bug de persistance)_

- **`03_climat/01_metéo_france_api.md`** — Statut : brouillon (tests API réels réussis le 2026-07-01
  sur l'API mobile `webservice.meteofrance.com` ; API officielle `public-api.meteofrance.fr` testée
  en échec 401 sans token — inscription non effectuée, flow OAuth2 à valider manuellement).
  3 points clés :
  1. **Deux API distinctes** : l'API officielle Open Data (`public-api.meteofrance.fr`, OAuth2 Bearer
     token obligatoire, gratuit depuis le 01/01/2024, quotas 60–100 req/min) et l'API « mobile » non
     documentée (`webservice.meteofrance.com`, token statique embarqué dans l'app officielle
     `__Wj7d...kj8__`, zéro inscription, 14 jours de prévisions humanisées par point GPS résolu en
     commune INSEE) — à ne pas confondre ; l'officielle est seule sûre pour la production commerciale.
  2. **Tests réels documentés** : sans token, l'officielle renvoie HTTP 401
     (`{"code":"900902","message":"Missing Credentials"}`) sur `DPObs/liste-stations` et
     `DPVigilance/v1/cartevigilance/encours` ; avec le token mobile, `forecast` (Lyon, HTTP 200,
     14 jours daily + horaire, `insee`/`dept`/`sun`/`wind.gust`), `v3/warning/currentphenomenons`
     (HTTP 200, vigilance tous départements, phénomènes 1–9 / couleurs 1–4) et `v2/observation`
     (HTTP 200, GeoJSON Feature gridded au point GPS) fonctionnent — payloads extraits in extenso.
  3. **Recommandation GeoSylva** : POC via API mobile (vigilance départementale + prévisions 7 j +
     obs temps réel par point GPS parcelle, unique apport vs Open-Meteo déjà intégré = vigilance
     officielle FR + Météo des forêts), puis migration production vers API officielle (DPVigilance,
     Météo des forêts saisonnière juin–sept, DPObs par station) via proxy backend OAuth2 ; la Météo
     des forêts (danger feux, 4 niveaux, J+1/J+2, départemental) est l'apport le plus spécifique au
     forestier mais reste grossière (pas d'IFM maillé public).

- **`03_climat/05_indices_bioclimatiques_forestiers.md`** — Statut : brouillon (formules sourcées
  Persée/Wikipédia/Climessences/INRAE ; seuils par essence derrière compte Climessences, non extraits).
  **3 points clés** :
  1. GeoSylva calcule **déjà** l'indice de De Martonne (P/(T+10)) dans `ClimateContextService.kt`
     (l. 112) et stocke une ETP Turc annuelle dans `NormalesClimatiques.kt` — mais l'ETP n'est
     **pas exploitée** dans un bilan hydrique, et De Martonne est calculé sur 1 an ERA5 2023 (pas
     sur normales 30 ans). `BioClimaticRiskDatabase.kt` est un catalogue qualitatif, pas des indices
     calculés. Wacussel, Emberger Q2, bilan P−ETP, DHYa, BILJOU = **non implémentés**.
  2. L'indice à implémenter en **priorité absolue** est le **DHYa de Climessences** (ONF/CNPF) :
     bilan mensuel P−ETP avec tampon RUM, boucle 3 ans, ETP via **Hargreaves**
     (`0,0023·(Tmoy+17,8)·(Tmax−Tmin)^0,5·Ra`). Standard professionnel français de fait pour le
     choix d'essence sous changement climatique ; méthodologie publique et réimplémentable en
     Kotlin offline. Lacune data : `NormalesClimatiques.kt` n'a que T/P annuels + JJA/DJF, pas les
     12 mois — à étoffer (Météo-France normales 1991-2020 ou data.gouv.fr ETP Safran Hargreaves 8 km,
     Licence Ouverte 2.0).
  3. **Climessences n'expose aucune API ouverte** (test webfetch 2026-07-01 : site Drupal avec
     login obligatoire, export CSV/GEOJSON par espèce derrière compte, pas de REST/JSON public) ;
     BILJOU (INRAE) similaire (simulation à accès restreint, fiches publiques). Recommandation :
     réimplémenter DHYa+Hargreaves en Kotlin natif, et extraire **manuellement** les seuils par
     essence via un compte Climessences pour alimenter une table `EssenceBioclimThresholds`. Ne pas
     crawler. BILJOU journalier écarté (trop gourmand en données pour mobile offline).

- **`03_climat/02_drias_projections_climatiques.md`** — Statut : brouillon (sources officielles
  Météo-France/DRIAS/INRAE/Gouvernement recoupées ; test d'accès réel au portail effectué le
  2026-07-02 ; origine point par point des deltas embarqués dans `ProjectionClimatiqueSerData.kt`
  non vérifiée — cf. limites).
  3 points clés :
  1. **DRIAS-2020** (référence actuelle) = 12 couples GCM/RCM Euro-Cordex (ALADIN63, RACMO22E…)
     reprojétés/corrigés sur grille **SAFRAN 8 km**, pas de temps **journalier** 1950-2100,
     scénarios **RCP 2.6/4.5/8.5** (CMIP5) — **PAS SSP** ; ~24 indicateurs (températures, jours
     de gel/chaleur, précipitations, ETP, **bilan hydrique**) aux fréquences mensuelle/saisonnière/
     annuelle. Approche alternative **TRACC** (+2/+2,7/+4 °C France à 2030/2050/2100), doctrine
     officielle d'adaptation.
  2. **Test d'accès réel** : consultation cartes/documentation **libre sans compte**, mais
     **téléchargement = compte personnel requis** (formulaire web, gratuit) et **aucune API
     REST/JSON publique** (fichiers NetCDF) → accès programmatique direct depuis l'APK Android
     **impossible** ; stratégie viable = pré-traitement hors-ligne + embarquement d'un sous-ensemble
     compact (déjà fait dans `ProjectionClimatiqueSerData.kt`).
  3. **Écart critique identifié** : `ProjectionClimatiqueSerData.kt` étiquette ses scénarios
     `SSP1-2.6/SSP2-4.5/SSP5-8.5` (AR6) et cite « DRIAS Météo-France », alors que DRIAS-2020 livre
     du **RCP** (AR5) — les valeurs chiffrées des deltas ne sont pas sourcées point par point
     `[À VÉRIFIER MANUELLEMENT]` ; recommandation : re-sourcer/re-étiqueter, ajouter une vue TRACC
     dans `StationDiagnosticScreen.kt`, et créer un compte DRIAS pour remplacer les deltas en dur
     par des valeurs traçables téléchargées.

- **`03_climat/04_open_meteo_comparatif.md`** — Statut : brouillon (tests d'API réels réussis le
  2026-07-02 sur Forecast + Archive ; licence commerciale et quota "fair use" à vérifier manuellement).
  3 points clés :
  1. Open-Meteo est **déjà intégré** dans `ClimateContextService.kt` (endpoint `archive-api.open-meteo.com`,
     2 variables only : `temperature_2m_mean` + `precipitation_sum`, année 2023 figée, modèle "Best Match"
     par défaut) — mais **n'exploite aucune variable forestièrement clé** (ET₀ FAO-56, soil moisture/temp,
     vent/gel) qui sont pourtant disponibles gratuitement ; le calcul du mois par index `(i*12)/n` est
     une approximation à corriger.
  2. **Test réel d'API documenté** : Forecast (Paris, 7 j, 0.17 ms) + Archive hourly avec `models=era5`
     (ET₀ horaire 0.01-0.60 mm/h, soil moisture 0.17 m³/m³, vent 15-21 km/h — toutes variables forestières
     renseignées) ; ⚠ **découverte critique** : `et0_fao_evapotranspiration_sum` et `wind_speed_10m_max`
     renvoient `null` en daily avec le modèle "Best Match" par défaut → il faut ajouter `&models=era5`
     (ou `era5-land`) pour les débloquer.
  3. **Comparatif Open-Meteo / Météo-France / ERA5** : Open-Meteo = proxy sans clé sur ERA5 (meilleur
     compromis intégration mobile, déjà opérationnel) ; Météo-France = meilleure précision locale FR
     (stations + AROME 1.3 km, clé requise, à intégrer en Priorité 1 pour temps réel/alertes) ;
     ERA5 direct (Copernicus CDS) = source ultime mais inadapté à un appel mobile direct (grilles
     mondiales) — aucun intérêt à court-circuiter Open-Meteo. Recommandation : étendre
     `ClimateContextService` (modèle era5 explicite + ET₀/soil/vent + normale 30 ans au lieu de 2023 seule).

- **`03_climat/03_copernicus_era5.md`** — Statut : brouillon (catalogue CDS consulté par
  webfetch réel réussi, code source cdsapi examiné ; aucun téléchargement de données
  effectué faute de compte/clé — vérification manuelle requise).
  **3 points clés** :
  1. **ERA5-Land** (0.1° ≈ **9 km**, horaire, **1950→présent**, ~50 variables de surface dont
     humidité volumique du sol sur 4 couches 0-289 cm et `potential_evaporation`) est
     préférable à ERA5 (0.25° ≈ 31 km) pour la climatologie communale française : une
     commune moyenne (~15 km²) couvre ~1-2 pixels ERA5-Land vs ~0,2 pixel ERA5. Licence
     CC-BY (ERA5 confirmé ; ERA5-Land à confirmer).
  2. **Faisabilité Android : NON en appel direct.** Le seul client officiel est `cdsapi`
     (Python) ; l'API REST HTTP existe sous le capot (PUT `/tasks/services/...`, polling
     `/jobs/{id}`, download) mais est explicitement « non supportée » par ECMWF, asynchrone
     (job + polling minutes→heures), renvoie des fichiers GRIB/NetCDF volumineux (grille
     entière, pas de point unique), et exigerait d'embarquer la clé CDS dans l'APK (faille
     de sécurité). **Un backend intermédiaire Python/FastAPI est nécessaire** : il
     télécharge ERA5-Land via cdsapi, pré-calcule la climatologie par commune (normales 30
     ans, ETP, bilan hydrique), et sert une API JSON légère à l'app.
  3. **Recommandation séquencée** : court terme, exploiter **Open-Meteo** (déjà intégré,
     dérivé d'ERA5, API REST sans clé) pour la série historique par point GPS ; moyen terme,
     backend ERA5-Land pour les variables sol détaillées et la série 1950→ ; long terme,
     croiser avec **DRIAS** (projections 2050/2100) pour l'aptitude future des essences.

## 4. Sol / RHU (04_sol_rhu/)
_(à compléter par les agents de la vague 4)_

- **`04_sol_rhu/01_inrae_gissol_bdgsf.md`** — Statut : brouillon (tests d'accès WMS/WFS réels
  réussis sur geodata.inrae.fr/geoserver ; granularité 1:1M = indication régionale, pas parcellaire).
  **3 points clés** :
  1. **BDGSF exposée en WFS sans clé** sur `geodata.inrae.fr/geoserver/inra_bdgsf/wfs` — couche
     `bdgsf_classe_ru` (RU en classes) et `geometrie_bdgsf` (type de sol WRB) requêtables par BBOX
     WGS84 (Test 3.5-B validé) ; intégration directe possible côté Kotlin (OkHttp + GeoJSON).
  2. **Profondeur non exposée en WFS** (téléchargement shapefile DOI 10.15454/7ZDND6 uniquement) →
     nécessite un index local (Room/GeoPackage) dans l'app ; pH et matière organique non disponibles
     par point via GisSol (BDAT = synthèses régionales agrégées seulement).
  3. **Piège CRS** : natif Lambert II étendu (EPSG:27582) — utiliser exclusivement le paramètre
     `BBOX=...,EPSG:4326` qui délègue la reprojection à GeoServer ; éviter les filtres CQL spatiaux.
     Préférer le téléchargement bulk + index local pour un usage intensif (robustesse offline).

- **`04_sol_rhu/03_methode_calcul_rhu.md`** — Statut : brouillon (sources officielles/scientifiques
  INRAE/CNPF/Climessences recoupées ; table Biljou extraite du HTML et vérifiée arithmétiquement ;
  mapping TextureSol→coef U et bornes de classes RUM à valider manuellement).
  **3 points clés** :
  1. La formule opérationnelle forestière est `RUM = Σ (épaisseur_cm × coef_U_texture × (1 −
     pierrosité%))` — la table de référence des coef U (mm/cm par classe de texture du triangle de
     Jamagne) est fournie par Biljou© INRAE Nancy (S=0,70 ; Lm=1,75 ; LA=1,95 ; A=1,75 ; AL=1,80 ;
     AS=1,70…), citée par la plaquette CNPF 2024 et cohérente avec GIS Sol/Arvalis.
  2. **Lacune code identifiée** : `EmbeddedSoilService.kt` interpole un RUM codé en dur par IDW
     (~110 points) mais **aucun calcul** n'existe à partir des saisies terrain (texture+profondeur+
     pierrosité) pourtant présentes dans `StationObservation.kt` — un `ComputeRumUseCase` est
     proposé (pseudocode Kotlin + mapping `TextureSol`→coef U) pour combler ce manque en priorité
     haute.
  3. Le RUM est **paramètre d'entrée obligatoire** du DHYa (Climessences, modèle IKS) : Climessences
     v2 utilise la carte Dobarco et al. 2021 (90 m, FPT Al Majou 2008, base DoneSol, RUM moyen
     France = 104 mm) comme tampon du bilan hydrique P−ETP mensuel sur 3 ans ; GeoSylva a les
     normales climatiques mais manque l'ETP mensuelle et la boucle pour reproduire le DHYa (à
     différer jusqu'à la vague climat, dossier `03_climat/` encore vide).

- **`04_sol_rhu/04_typologie_stations_cnpf.md`** — Statut : brouillon (sources officielles CNPF/IGN +
  scientifiques HAL/INRAE recoupées ; PDF IGN `TypoWeb_2008.pdf`/`L_IF_no04_typologie.pdf` non extraits
  en texte — liste exhaustive des catalogues par région à confirmer manuellement).
  **3 points clés** :
  1. **Emboîtement confirmé** : GRECO (12 dont 1 d'alluvions) > SER (91 dont 5 azonales) > région
     forestière (309 regroupées) > **station** (étendue homogène climat/relief/sol/flore) > **type de
     station** (unité conceptuelle). GRECO/SER déjà couverts par `GrecoDetector.kt` ; **station et type
     de station sont les niveaux manquants** dans GeoSylva. Méthode standard = phytoécologie (végétation
     indicatrice + sondage tarière + humus), **jamais sans observations pédologiques**.
  2. **Catalogues/guides CNPF en accès libre (PDF)** mais **sans base structurée ni API**, couverture
     **inégale et incomplète** (2-3 ans de travail par document, certaines régions sans guide récent) ;
     **écogrammes** (Flore forestière française, Rameau et al.) = diagramme **niveau hydrique × niveau
     trophique** avec 2 aires par essence (verte = production rapide, jaune = amplitude totale), méthode
     **graphique comparative** (pas de seuil chiffré universel) ; ouvrage **payant** (droits IDF à
     respecter pour extraction).
  3. **Faisabilité automatisation** : **faisable court terme** comme **assistant** (pré-diagnostic
     géolocalisé GRECO/SER + géologie BRGM WMS + capteurs altitude/exposition → « station probable à
     confirmer », puis fiche de relevés guidée + exécution de la clé régionale numérisée + superposition
     écogramme essence), mais **impossible sans saisie terrain humaine** (reconnaissance flore herbacée
     non fiable par photo, sondage tarière non automatisable). **Aucune pré-cartographie nationale
     ouverte** — approches prédictives régionales uniquement (Normandie WMS, Gégout massif vosgien au
     quart d'hectare) ; recommandé : 2-3 régions pilotes + table `EssenceEcogramAreas` extraite
     manuellement, dans `StationDiagnosticScreen.kt`.

- **`04_sol_rhu/05_geologie_brgm_roche_mere.md`** — Statut : brouillon (tests d'accès WMS/WFS BRGM
  réels et vérifiés 2026-07-03 ; mapping roche mère → sol → essences qualitatif, à affiner par CSR).
  **3 points clés** :
  1. Le BRGM diffuse la carte géologique de France sous **Licence Ouverte Etalab 2.0** (gratuit,
     sans clé, citation source obligatoire) via WMS/WFS sur `geoservices.brgm.fr/geologie` ; les
     couches WMS (`SCAN_H_GEOL50`, `GEOL50_HARM`) sont **non queryables** (GetFeatureInfo rejeté),
     mais le **WFS `LITHO_1M_SIMPLIFIEE`** est queryable — test réel réussi sur 2 points forestiers
     (Fontainebleau → « Calcaires, marnes et gypse » ; Morvan → « Basaltes et rhyolites »).
  2. **Précision 1/1M insuffisante** pour un diagnostic stationnel fin (le grès de Fontainebleau
     n'apparaît pas, noyé dans la lithologie régionale) ; pour GeoSylva, approche à 2 niveaux :
     (a) court terme, WFS live `LITHO_1M_SIMPLIFIEE` comme indication régionale de roche mère ;
     (b) moyen terme, Bd Charm-50 (1/50k vectorielle, téléchargement gratuit) en GeoPackage embarqué
     pour capter les formations locales.
  3. La roche mère (granite→sol acide filtrant→chêne sessile ; marne/argile→sol frais profond→chêne
     pédonculé ; calcaire→chlorose→éviter châtaignier/douglas) est une **couche explicative**
     complémentaire à BDGSF/SoilGrids (qui donnent le sol mesuré/prédit, couche décisionnelle) — à
     intégrer comme interprétation dans le diagnostic stationnel, pas comme substitut du sol.

- **`04_sol_rhu/02_alternatives_soilgrids_esdac_hwsd.md`** — Statut : brouillon (test d'API réel
  SoilGrids effectué mais valeurs `null` — service ISRIC partiellement dégradé à retester
  manuellement ; licences et conventions pF à valider).
  **3 points clés** :
  1. **Comparatif résolution** : SoilGrids 2.0 (ISRIC) = 250 m raster continu (pH, texture %,
     densité apparente, CEC, teneur en eau à 10/33/1500 kPa), CC-BY 4.0, API REST sans clé
     (`rest.isric.org/soilgrids/v2.0/properties/query`) ; ESDB ESDAC = 1 km classes (AWC_TOP/SUB),
     accès sur inscription sans API ; HWSD v2.0 (FAO/IIASA) = ~1 km, **CC BY-NC-SA 4.0
     (NonCommercial — incompatible app commerciale)** ; BDGSF INRAE = 1/1M vectoriel, RU en
     classes, licence ouverte, source primaire FR validée par expertise nationale.
  2. **Test API réel (2026-07-02)** : l'endpoint REST SoilGrids répond 200 OK avec JSON GeoJSON
     structuré (6 profondeurs 0-200 cm, Q0.05/Q0.5/Q0.95/mean) **sans clé**, mais renvoie `null`
     sur Paris (48.85N/2.35E) et Fontainebleau (48.4N/2.7E) — `query_time_s` 0.7–36 s (serveur
     actif). La doc ISRIC évoque un service en cours de restauration. `awc` n'est pas une
     propriété de base (HTTP 500) — produit dérivé (wv0033−wv1500) via WCS/GEE uniquement.
  3. **Recommandation combinaison** : **BDGSF en source primaire FR** (RU/AWC + profondeur en
     classes, référence pédologique française) **+ SoilGrids 250m en complément** (pH, texture %,
     densité, CEC continus, CC-BY 4.0 commercial OK). Exclure HWSD (licence NC) et ESDB
     (redondant avec BDGSF pour la France). Calcul AWC continu = `wv0033 − wv1500` SoilGrids, à
     calibrer contre la classe BDGSF (convention pF 33 kPa vs 10 kPa à valider manuellement).

## 5. APIs externes (05_apis_externes/)
_(à compléter par les agents de la vague 5)_

- **`05_apis_externes/01_apis_ign_carto_nature_urbanisme.md`** — Statut : vérifié (tests API réels
  effectués le 2026-07-02, codes HTTP + extraits de réponse documentés).
  **3 points clés** :
  1. Les 4 APIs IGN (Carto Nature, Carto Urbanisme, BD Ortho, Corine Land Cover) sont accessibles
     **SANS clé API** via les endpoints publics `data.geopf.fr` (WMTS/WMS/WFS) et `apicarto.ign.fr`
     (REST) — Licence Ouverte 2.0, usage commercial autorisé. BD Ortho est **déjà intégrée** dans
     GeoSylva (`MapScreen.kt:932`, `geopfLayer()`), les 3 autres sont à intégrer.
  2. **Tests réels confirmés** : WFS Natura 2000 (`patrinat_sic:sic`) retourne « Massif de
     Fontainebleau » sur un BBOX test (HTTP 200, 494 Ko JSON) ; API Carto REST Urbanisme
     (`/api/gpu/zone-urba?geom=...`) retourne `typezone:"N"` (zone naturelle) sur un point
     Fontainebleau (HTTP 200, 310 Ko JSON) ; WMS CLC `LANDCOVER.CLC18_FR` exige `STYLES=` vide
     (piège : `STYLE=normal` renvoie HTTP 400) ; WFS CLC retourne `code_18:"523"` et `"211"`.
  3. **Quotas Géoplateforme** (rate limiting par IP depuis 25/02/2025) : WMTS **illimité** (idéal
     pour `OfflineTileManager`), WMS-R 40 req/s, WFS 30 req/s, HTTP 429 + blocage 5 s au-delà.
     Recommandation : intégrer en P1 Carto Urbanisme REST (effort faible, alerte PLU sur parcelle)
     et Carto Nature WFS (alerte Natura 2000/ZNIEFF), en P2 Corine Land Cover (overlay contexte).

- **`05_apis_externes/04_apis_biodiversite_inpn_gbif.md`** — Statut : vérifié (tests d'API réels
  effectués le 2026-07-02 ; endpoint MNHN confirmé indisponible, miroir carmencarto + GBIF testés
  opérationnels).
  **3 points clés** :
  1. L'endpoint INPN WFS officiel `inpn-inspire.mnhn.fr/geoservices/ows` retourne **HTTP 403**
     suite à l'attaque cybernétique sur le MNHN (durée d'indisponibilité indéterminée) — les
     couches `DHFF_*_ESPECES` / `DHFF_*_HABITATS` (répartition espèces/habitats Natura 2000) ne
     sont donc **pas accessibles**. Le miroir `ws.carmencarto.fr/WFS/119/fxx_inpn` est
     **opérationnel** et expose les zonages d'espaces protégés (ZNIEFF I/II, ZSC/SIC, ZPS, RNN,
     réserves biologiques) en Lambert 93 — filtre BBOX WGS84 testé et fonctionnel (cas d'usage
     parcelle validé), sans clé API, Licence Ouverte Etalab 2.0.
  2. GBIF API (`api.gbif.org/v1`) testée opérationnelle sans clé : 324 591 occurrences de
     *Quercus robur* en FR, filtre `geometry=WKT POLYGON` fonctionnel (934 150 occurrences sur un
     rectangle test). ⚠️ **Critique** : les coordonnées des taxons menacés sont **volontairement
     floutées** (Lynx lynx → `coordinateUncertaintyInMeters: 26935m`, « to protect threatened
     taxon ») → GBIF est utilisable pour un **contexte régional** mais **pas** pour localiser une
     espèce protégée à l'échelle de la parcelle (risque juridique/éthique).
  3. Recommandation GeoSylva : intégrer en **P1** un client WFS sur le miroir carmencarto pour
     lever une alerte « zone réglementée » (ZNIEFF/Natura 2000/RNN) sur la parcelle via BBOX +
     intersection JTS côté client, avec cache GeoPackage offline ; intégrer GBIF en **P2** pour
     le contexte naturaliste (rayon 10 km) en croisant avec TAXREF/BDC (ZIP PatriNat) pour le
     statut de protection ; surveiller en **P3** la restauration de l'endpoint MNHN pour accéder
     aux couches habitats/espèces Natura 2000 manquantes.

- **`05_apis_externes/05_apis_foncier_hydrographie.md`** — Statut : vérifié (tests API réels
  effectués le 2026-07-02 via webfetch ; corrections de code à implémenter).
  3 points clés :
  1. **Bug DVF critique confirmé** : l'endpoint utilisé dans `StationDataAggregator.kt`
     (`apidf-preprod.cerema.fr/dvf_opendata/geomutations/?lat=...&lon=...&rayon=1000&nature_culture_code=B`)
     renvoie **HTTP 403** — les paramètres `lat/lon/rayon/nature_culture_code` ne sont pas
     supportés. L'API réelle fonctionne par `code_insee` + `codtypbien` (testé 200 OK, 112
     mutations sur La Bourgonce 88068). De plus le parsing lit `valeur_fonciere`/`surface_terrain`
     alors que l'API renvoie `valeurfonc`/`sterr` → double échec silencieux. À corriger en
     priorité HAUTE.
  2. **Cadastre reverse (IGN) fonctionnel mais incomplet** : `data.geopf.fr/geocodage/reverse?...&index=parcel`
     répond 200 OK et renvoie section/numéro/city, mais **pas les champs `contenance` ni `nature`**
     lus par `LocalisationResolverService.kt` → ces champs seront toujours `null` en production ;
    il faut compléter par un WFS GetFeature sur `CADASTRALPARCELS.PARCELLAIRE_EXPRESS`.
  3. **BD Topage® (Sandre) WFS opérationnel** : `services.sandre.eaufrance.fr/geo/topage`
     GetCapabilities + GetFeature `sa:CoursEau` testés 200 OK (GeoJSON avec cours d'eau métriques,
    Licence Ouverte 2.0, sans clé) — à intégrer pour la détection ripisylves (tronçons
    hydrographiques) et contraintes hydriques (bassins versants) sur parcelle.

- **`05_apis_externes/02_synthese_apis_meteo_climat.md`** — Statut : brouillon
  (synthèse opérationnelle consolidant les 5 fiches `03_climat/01` à `05` ; pas de
  nouveau test API — s'appuie sur les tests documentés en vague 3).
  **3 points clés** :
  1. **Tableau comparatif unique** : seul appel Android direct sans clé = Open-Meteo
     (Archive/Forecast/Climate, déjà intégré) ; Météo-France officielle = via proxy
     backend OAuth2 (token non embarquable) ; ERA5/ERA5-Land (CDS) et DRIAS-2020 =
     backend obligatoire (Python-only/async/GRIB/NetCDF, clé CDS = faille de sécurité).
     Climessences/BILJOU = aucune API (extraction manuelle seuils DHYa).
  2. **Architecture recommandée** : app offline-first (Open-Meteo `models=era5` +
     données embarquées) en court terme ; backend Python/FastAPI moyen terme pour
     Météo-France (vigilance + Météo des forêts, apport unique), ERA5-Land (normales
     30 ans + humidité sol 4 couches) et DRIAS (deltas par SER traçables). Ordre :
     P0 étendre Open-Meteo → P1 Météo-France + DHYa Climessences Kotlin natif →
     P2 projections re-sourcées + backend ERA5-Land/DRIAS → P3 seuils essence manuels.
  3. **Variables minimales diagnostic stationnel** : Tmoy/Tmax/Tmin/P mensuelles
     (12 mois, Open-Meteo `models=era5`) + ET₀ (Hargreaves calculé côté app) + RUM
     (de `04_sol_rhu/`) pour alimenter DHYa (P1 absolue), De Martonne (à passer de
     2023 à normale 30 ans) et bilan P−ETP. Lacune bloquante : `NormalesClimatiques.kt`
     n'a que T/P annuels + JJA/DJF (pas les 12 mois) — à étoffer en P1.

- **`05_apis_externes/03_synthese_apis_sol_pedologie.md`** — Statut : brouillon (synthèse
  opérationnelle consolidant les 5 fichiers de `04_sol_rhu/`, pas de nouveau test d'API ;
  retest SoilGrids à faire manuellement).
  **3 points clés** :
  1. **Tableau comparatif unique** des APIs sol/géologie testées (BDGSF, SoilGrids, ESDAC,
     HWSD, BRGM) : seules **BDGSF (RU + WRB)** et **BRGM Lithologie 1/1M** sont intégrables
     en live sur Android sans clé (WFS testés OK, Licence Etalab 2.0) ; **SoilGrids**
     (CC-BY 4.0) est le complément pH/texture 250m mais a renvoyé `null` sur points FR le
     2026-07-02 (à retester) ; **HWSD exclu** (licence NonCommercial) et **ESDB exclu pour
     la France** (redondant avec BDGSF, pas d'API).
  2. **Schéma de chaîne de diagnostic stationnel** : point GPS → BRGM (roche mère) → BDGSF
     (RU/type sol WRB) → SoilGrids (pH/texture complément) → typologie station CNPF
     (écogramme NH×NT) → aptitude essence (aires verte/jaune). Hiérarchie de confiance :
     saisie terrain > BDGSF > SoilGrids > BRGM 1/1M ; tout pré-diagnostic est « à confirmer
     par relevé terrain ».
  3. **Variables sol minimales** : RU (BDGSF) + type WRB (BDGSF) + pH + texture % + densité
     (SoilGrids) + roche mère (BRGM) suffisent pour un pré-diagnostic sans saisie terrain ;
     profondeur/pierrosité/hydromorphie/calcaire/flore enrichissent mais exigent une saisie
     terrain (tarière + flore) — `ComputeRumUseCase` (coef U Biljou × profondeur effective
     × terre fine) surcharge la valeur IDW de `EmbeddedSoilService`.

## 6. Essences (06_essences/)
_(à compléter par les agents des vagues 6-8)_

## 7. Systèmes avancés (07_systemes_avances/)
_(à compléter par les agents de la vague 9)_
