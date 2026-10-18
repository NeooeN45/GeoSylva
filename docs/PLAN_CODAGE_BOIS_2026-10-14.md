# GeoSylva — plan de codage du premier parcours bois

## Actualisation du 18 octobre

N01/N02 sont raccordés et vérifiés : [validation navigation](VALIDATION_PARCOURS_2026-10-18.md).
Le [complément prospection, estimation et photos](PROSPECTION_ESTIMATION_PHOTOS_BOIS_2026-10-18.md)
ajoute ces besoins à la cible. Les photos documentaires suivent le socle des objets/relevés ;
le cubage photographique reste expérimental, sans conversion ou précision présumée.

C01 est raccordé au lot C02/C03 vérifié localement et sur Android : Room 44, objets et
relevés immuables persistés, reçus idempotents, formulaire manuel pile/grume,
historique et calcul géométrique. Voir la [validation du lot](FORMULAIRES_BOIS_C02_C03_2026-10-18.md)
pour ses 1 776 tests JVM, 36 tests Android distincts et ses limites ; les tableaux
du 14 restent historiques. C04 est raccordé au calcul individuel ; le récapitulatif
multi-objets, les exports C05 et les rapports C06 restent à réaliser.

**Complément prioritaire du fondateur** : le chantier est lié à une ou plusieurs
parcelles ONF/cadastrales/autres ; les enlèvements partiels et contrôles se saisissent
par date. Voir le [modèle et le schéma](CHANTIERS_PARCELLES_SUIVI_DATE_BOIS_2026-10-18.md).
Le formulaire local actuel ne livre pas encore ces associations ni les mouvements.

## Ordre courant — complément deux parcours

Le [contrat des deux expériences](DEUX_PARCOURS_HIERARCHIE_ARCHIVES_TEST_2026-10-14.md) complète ce plan : N01 politique de navigation/liaisons, N02 deux accueils et chemins, S01 contexte entreprise/capacités puis S02 chantier/hiérarchie/archives. C01/C02 locaux restent préparés ; N01 précède la qualification visuelle C03, et les autres tranches s’ajoutent avec leurs dépendances réelles.

Accord courant : **page/onglet Missions réservé à l’offre Entreprise**. S01 inclut droit d’offre valide côté serveur en plus du contexte/appartenance/capacités ; N01 le masque pour gratuit/individuel. Aucun seed de scénario ne simule ce droit comme acquis ; la recette doit le créer/vérifier via le vrai service d’abonnement de TEST.

Le Fondateur n’a aucun relevé et autorise explicitement la simulation cohérente. Le [jeu de scénarios TEST](SCENARIOS_SYNTHETIQUES_DEUX_PARCOURS_2026-10-14.json) prépare gestion et chantier, avec corrections/partiels et valeurs économiques hypothétiques signalées ; il n’est pas importé. Les mesures/calculs proviendront des vrais modules, les capacités de vrais comptes/appartenances de recette. Pas d’injection dans une base de production, pas de réaffectation automatique entre espaces.

L’APK TEST des deux expériences vient **après validation visuelle demandée par le Fondateur**, connexions et parcours qualifiés et retours traités ; les builds/captures internes nécessaires à cette revue sont une étape de développement. C08 ne constitue donc pas une autorisation de livrer prématurément une version prétendant que toute la hiérarchie et les connexions fonctionnent.

| Champ | État |
| --- | --- |
| Date | 14 octobre 2026 |
| Objectif | Rendre réalisable le parcours local dépôt → pile/grume → relevés/corrections → calcul provisoire → exports réels. |
| Cadre | [Quatre parcours et 22 actions](CONTRAT_ACTIONS_METIER_FORLOG_2026-10-14.md), sans automatisme mesure/stock/commerce. |
| Réalisation détaillée | [Spécification technique et arbitrages](SPEC_BOIS_01_PRET_A_CODER_2026-10-14.md). |
| Livré actuellement | Dépôts et entrées métier ; Room 43, [recette antérieure](RECETTE_DEPOTS_BOIS_2026-10-13.md). |
| Nouveau code dans ce dossier | Aucun ; il prépare les modifications et leurs preuves, pas leur livraison. |

## Ce qui peut commencer maintenant

Les lots C01–C08 ci-dessous sont des choix techniques propres à GeoSylva, assez précis pour commencer leur réalisation. Ils réutilisent la stack Kotlin/Compose Material 3/Room/SQLCipher et les noyaux existants ; aucun changement de dépendance n’est nécessaire pour les commencer. Les commandes de compilation et les tests de socle sont disponibles dans l’environnement actuel.

**Prêt à coder ne signifie pas fonctionnel.** Chaque lot reste à implémenter, migrer, compiler et vérifier. La qualification visuelle sur Android et l’APK arrivent avec le code, pas avec les documents de préparation.

| Lot | Livrable concret | Dépendances | Critère de sortie |
| --- | --- | --- | --- |
| C01 — Contrats purs | Identité d’objet, raw readings, observation et correction, commandes/résultats typés ; sérialisation v1, empreinte complète et parser décimal. | Spécification et invariants ci-dessous. | Les cas partiels/invalides, IDs et empreintes ne produisent ni valeur fictive ni effet métier. |
| C02 — Persistance | Migration additive 43 → 44, entités/DAOs et repository canonique ; relecture réelle, garde compte/purge, archivage et historique. | C01 ; ne pas augmenter une version déjà changée par un travail concurrent sans refaire le raccord. | Migration avec données antérieures conservées ; idempotence/CAS/parents/scope et effacement vérifiés sur vraie Room. |
| C03 — Parcours terrain | Accès « Bois de ce dépôt », liste, fiche et formulaires pile/grume ; nouveau relevé, correction exacte, sélection explicite et historique. | C01/C02. | Persistance après fermeture, absence de double objet/remesure, conflits et grandes polices éprouvés sur Android. |
| C04 — Raccord géométrique | Adapter pur au noyau Huber/Smalian/Newton/enveloppe, diagnostics et résultats provisoires ; récapitulatif compatible. | C01/C02 ; UI C03 pour rendre les états. | Cas analytiques et champs/positions/écorce conformes ; une effective au plus par objet, aucun total des remesures. |
| C05 — Snapshots et fichiers | Capture autorisée cohérente, CSV/GeoJSON et archive JSON/manifeste/empreintes ; relecture/validation des fichiers. | C02/C04. | Textes et unités exacts, non-localisés conservés, snapshot ancien inchangé après nouvelles observations. |
| C06 — Rapport PDF | Rendu de relevé de bois A4 avec tableaux, couverture des calculs, méthodes, positions disponibles et pagination. | C05. | PDF réellement ouvert ; contenu et pages inspectés FR/EN, longues valeurs non masquées. |
| C07 — Remise Android | Sélecteur SAF, garde conservée avant/après sélection, vérification du fichier remis et états explicites. | C05/C06. | Fichier final relu et hash comparé ; accès changé/annulation/destination non relisible correctement affichés. |
| C08 — Qualification et APK | Recette consolidée sur base réelle, parcours visuels, build TEST isolé et téléchargement vérifié. | C01–C07. | Preuves du même code/binaire, défauts restants documentés, APK installable ; pas d’acceptation FORLOG supposée. |

Ordre historique de préparation : **C01 puis C02**, avant les écrans C03. Le complément daté en tête indique l’avancement actuel. C03 et C04 peuvent ensuite progresser séparément si leurs contrats sont stabilisés. C05 exige des objets réels déjà persistés ; un fichier depuis une fixture ou un dépôt de données remplacé ne prouve pas le parcours de réception.

## Points d’accroche et propriétaires de fichiers

| Zone | Fichiers à créer ou adapter | Responsabilité |
| --- | --- | --- |
| Domaine | Nouveaux modèles et `WoodObjectRepository` sous `domain/model` et `domain/repository`. | Pas d’Android/Room/token dans le contrat de données ; commandes immuables et résultats explicites. |
| Données | `WoodObjectEntity`, `WoodObservationEntity`, registre de commandes, DAO et migration définis dans l’annexe persistance ; `WoodObjectRepositoryImpl`. | Transaction réelle, sélection effective et scope, pas de tige/martelage transformé en objet récolté. |
| Accès/purge | Garde partagée bois/dépôts, `WoodDepotRepositoryImpl`, `DeleteAllUserDataUseCase` et injection dans `ForestryCounterApplication`. | Effacement ne laisse pas une commande en attente réintroduire le bois ; archives de fichiers incluses. |
| Base | `ForestryDatabase`, `DatabaseMigrations` et schéma Room généré 44. | Migration additive, pas de destructive fallback ; schémas anciens conservés. |
| UI/navigation | `WoodDepotsScreen`, `ForestryNavigation`, `ForestryFlowNavGraph`, nouveaux écrans/VM de bois et ressources `wood_objects.xml` FR/EN. | Routes UUID sans payload ; barre principale conservée, état de session et brouillon séparés. |
| Calcul | `WoodRawDecimalParser`, `WoodObservationVolumeAdapter`, readmodel récapitulatif. | Reprendre le noyau géométrique existant sans changer sa formule ni interpréter une méthode régionale depuis le GPS. |
| Export | Codecs et snapshot/service/rendu propres sous `domain/usecase/export/wood` ; archive store privé. | Formats GeoSylva, entrée/version réellement choisie, aucun import client prétendu. |
| Recette | JVM sous les packages métier correspondants ; Room/native/UI/export sous `androidTest` ; preuves hors checkout. | Fixtures TEST identifiées, données utilisateur intactes, garde de session/purge et relecture à la destination. |

Les agents travaillent sur des ensembles de fichiers distincts ; les contrats de domaine et la migration ont un propriétaire unique. Une modification de contrat arrive avant le travail dépendant. Root garde le contrôle des builds/ADB/APK pour éviter des compilations ou installations concurrentes.

## Recette à écrire et exécuter pendant le codage

Les cas suivants sont des exigences de réalisation ; ils ne sont pas présentés comme déjà passés.

| ID | Niveau | Cas discriminant |
| --- | --- | --- |
| R01 | JVM | UUID canonique et type d’objet immutable ; numéro déclaré non unique globalement. |
| R02 | JVM | Brut vide/absent/invalide/zéro, virgule/point, unité inconnue et conversion numérique non représentable ; pas de faux zéro. |
| R03 | JVM | Empreinte inclut action, scope, IDs/parents/révisions et contenu ; un changement significatif ne se rejoue pas sous la même commande. |
| R04 | Room réel | 43 → 44 conserve les données anciennes et indices ; contraintes/corruption/clé parent/cross-scope réellement exercées. |
| R05 | Room réel | Double clic/rejeu/interruption : une opération appliquée, reçu exact et ancienne observation intacte. |
| R06 | Room réel | Remesure = même objet, deux observations ; aucune entrée de stock ou base commerciale implicite. |
| R07 | Room réel | Correction exact/motif/filiation ; ancienne effective signalée corrigée, pas remplacée ; sélection d’une tête explicite. |
| R08 | Room réel | CAS concurrent conserve les deux saisies ; jamais d’UPDATE du payload d’observation ancienne. |
| R09 | Room réel | A → B et A → déconnexion → A, appel retardé et résultat post-commit inconnu ; relecture par commande sous le scope d’origine. |
| R10 | Room réel | Purge pendant une écriture/rendu retardés : aucun bois ou fichier ressuscité, ancien accès inutilisable ; erreurs d’effacement visibles. |
| R11 | Room réel | Parent archivé : écriture refusée, historique encore consultable/exportable sous droits ; parent étranger ne divulgue rien. |
| R12 | JVM/Room | Noyau analytique et adapter : entrées propres à chaque méthode, zéros conditionnels, positions, protocole, écorce, underflow/overflow. |
| R13 | JVM/Room | Courant : au plus une observation effective par objet ; corrigée/manquante exclue avec motif/couverture ; groupes compatibles seulement. |
| R14 | JVM | Série historique jamais sommée ; correction/tri n’est pas sélection effective ; archives anciennes identiques. |
| R15 | Fichiers réels | CSV Unicode/quotes/retours de ligne/null/texte risqué, GeoJSON avec geometry null, manifeste/taille/hash ; altération de colonne détectée. |
| R16 | Android/PDF | PDF réel réouvert, pagination/dates/noms longs/FR/EN/positions et manquants inspectés ; pas de carte ou quantité fictive. |
| R17 | Android/SAF | Destination relisible/hash identique vs écrite non vérifiable vs annulation/échec/partiel ; droits revérifiés. |
| R18 | Android UI | Création/relevé/correction dans le bon dépôt, clavier et texte 200 %, retours/brouillons/conflits, métiers contextualisés sans droit implicite. |
| R19 | Régression | Martelages, synthèses, migrations anciennes, données/clôtures/PDF et suppression des autres données conservés. |
| R20 | APK | Package TEST et signature vérifiés, installation/lancement, parcours qualifiés et téléchargement complet identique au binaire livré. |

Tester d’abord le lot touché. Après la modification de la base ou de l’injection, exécuter les vérifications existantes pertinentes et celles exigées par le dépôt ; étendre selon les erreurs ou risques constatés. Un nombre cible de tests ne remplace pas ces comportements discriminants.

## Préparation des parcours suivants

Le premier lot fournit des références stables aux futures opérations. Leur codage vient après les critères ci-dessous ; la présence de cette feuille de route ne les annonce pas prêts ou fonctionnels.

| Actions | Préparation disponible | Précondition avant activer le comportement |
| --- | --- | --- |
| A01/A02/A04 | Parcours dossier/contrat/prescriptions/accès, version et rôle définis. | Raccord aux objets dossier/contrat réellement présents et circuit applicable, documents/provenance/droits. |
| A05/A06 | Équipe sans référent obligatoire, remise habilitée/participants, contrôle selon type et réalisé séparé. | Espace entreprise et permissions réels ; modèles/version et cycle de mission persistant, pas une affiliation déclarée. |
| A10 | Contrôle technique et décision commerciale distincts, responsables/version source. | Contrat de contrôle/décision et permissions effectivement qualifiés ; pas de validation depuis une sauvegarde. |
| A11/A12/A15 | Inventaire ≠ mouvement ; opérations explicites, unités/transit/partiel/correction et filiation. | Modèle local de balance/compensation, règles de disponibilité et essais stock réels ; effets Forlog à établir séparément. |
| A14/A16/A17 | Prévu transport, enlèvement, pesée, destination et écarts distincts. | Référentiels de destination, contrat de tickets/import/mesurage, pas de conversion masse/volume implicite. |
| A03/A13/A18/A19 | Bases achat/prestation/transport/vente, prévu/réalisé, HT/TTC et coût manquant définis. | Conventions/prix/postes/taxes/émission documentaire applicables ; validation économique et client avant quantité payable ou facture définitive. |
| A20 | Références et preuves rattachées, statut déclaré/vérifié. | Obligations/licences/systèmes de traçabilité réellement qualifiés, pas de certification déduite du schéma. |
| A22 | Enveloppe/cible d’échange, IDs/espace/destination/hash/version et réponses/reprises proposés. | Contrat client consommé, source maître par donnée, authentification et recette indépendante. |

Le [complément WinDev](PROMPT_EXPORT_PROCEDURES_FORLOG_2026-10-14.md) reste centré sur Q01–Q05 et CASE_02/08. Ces absences bloquent l’affirmation d’un comportement client confirmé ; elles n’empêchent pas de coder les objets/relevés/calculs provisoires et exports propres à GeoSylva.

## État de préparation technique vérifié

Le 14 octobre, le runner existant avec Gradle **8.2**, JDK **17.0.20.1**, cache et SDK conservés a exécuté **54 tests existants dans quatre suites**, sans échec, erreur ou ignoré : `HarvestedWoodCalculatorTest`, `HarvestedWoodReferenceCasesTest`, `WoodDepotTest`, `WoodDepotsViewModelTest`. Mode hors ligne, deux workers, compilation valide réutilisée. Ces tests ne qualifient ni les nouvelles tables, ni un relevé Room de bois, ni les écrans/export futurs.

[Preuve de cette exécution](../../.onboarding/forlog-ready-to-code-2026-10-14/EXISTING_TESTS_PROOF.json). Aucun nouveau fichier d’application, migration, APK ou service externe n’est livré par cette préparation. Le contrôle public Google/GSIE antérieur garde sa date ; aucun nouveau login ou accès Forlog n’est prétendu.
