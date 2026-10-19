# Qualification du dossier FORLOG et comparaison avec GeoSylva

Date : 19 octobre 2026. Statut : revue de preuves et préparation du développement.
Référence reçue : `AUD-FORLOG-GEOSYLVA-20261019`, version 1.0.0 Draft.
Code GeoSylva examiné : `576a256b7bfa22e2bbd23571b78c076bf78ee43f`, Room 45.

## Conclusion

Le dossier est exploitable pour spécifier le parcours autonome de GeoSylva.
Il ne démontre pas une équivalence de calculs ou de transactions avec FORLOG.
Les instructions du fondateur restent prioritaires sur ses propositions.
Le [plan de réalisation](PLAN_RECEPTION_STOCK_EXPORTS_2026-10-19.md) réutilise
le socle mobile existant et concentre les ajouts sur réception et export.

Les archives sources et extraits client sont conservés hors du dépôt. Aucun code
FORLOG, outil joint, base cliente ou client WinDev n'a été exécuté. Aucun extrait
client n'est recopié dans l'application. Aucun connecteur n'est livré par cette revue.

## Vérification des fichiers reçus

| Archive | Taille compressée | Fichiers | Empreintes du manifeste vérifiées |
| --- | ---: | ---: | ---: |
| Synthèse prioritaire | 230 424 octets | 40 | 39/39 |
| Dossier complet | 392 181 octets | 90 | 89/89 |

Les manifestes n'incluent pas leur propre empreinte. Les CRC ZIP sont valides ;
aucun fichier listé ne manque et aucun fichier supplémentaire n'a été trouvé.
Les fichiers communs correspondent, sauf les deux fichiers de périmètre attendus :
`MANIFESTE.json` et `ARCHIVE_LIRE.md`.

SHA-256 des archives, respectivement :

- `1e1d26f0daacbaddc77252c5d5cc453bdd53ffe1009b12555959a751026dc65a`
- `6eb4354f11b7108a22c6587393f3f5157f7d5960063d47ebf62334b84210078a`

La reconstruction des comptages sur les exports reçus donne 221 tables métier /
2 144 rubriques, 8 tables d'accès / 56 rubriques, et 10 tables WDGPU / 47 rubriques.
Les rubriques composées sont incluses ; ce ne sont pas toutes des colonnes physiques.
Le catalogue contient **32 fiches d'actions** et **108 références de preuve** :
87 de structure, 14 documentaires, 7 de code cartographique.

Les cinq empreintes d'opérations synthétiques sont recalculées et concordent.
Le CSV synthétique conserve les soldes 100 → 100 → 75 → 0 ; les six exemples
d'imputation d'avoirs sont arithmétiquement cohérents. Ce sont des vérifications
de fichiers et d'oracles, pas des tests d'une intégration FORLOG.

L'archive originale de 2,9 Go, les XDD originaux et les documents complets ne sont
pas présents ici. Leur provenance déclarée et leurs empreintes n'ont donc pas
été authentifiées indépendamment dans cette session. Les 18 composants natifs
prioritaires restent signalés binaires dans le dossier reçu.

## Ce que le dossier apporte au produit

1. Une observation physique peut alimenter plusieurs bases commerciales, dont
   les quantités, unités, justificatifs et validations restent distincts.
2. Une pesée peut être ventilée entre plusieurs chantiers ou catégories de bois.
   Cette ventilation ne produit pas de stock supplémentaire.
3. Un enlèvement, une livraison et l'acceptation du destinataire sont trois faits
   distincts. Un refus ou changement de destination doit être conservé.
4. Le réimport doit conserver les références externes. Un numéro de ticket seul
   ne doit pas être supposé unique entre toutes les entreprises et bascules.
5. Les conventions d'écorce, réfactions, conversions, unités et arrondis sont
   importantes, mais leurs règles natives ne sont pas établies dans les fichiers lisibles.
6. La vente, le paiement d'une prestation et la mesure terrain ne doivent pas
   partager une quantité modifiable unique qui réécrirait toutes les opérations.

Ces points s'appuient sur les preuves documentaires ACT-010 à ACT-014 et les
structures qualifiées dans le dossier ; les comportements natifs restent à confirmer.
La mention documentaire de certification ne prouve ni certification d'un lot ni
formule de taux applicable. Aucune conversion masse/énergie n'est déduite d'un libellé.

## Écart avec le code mobile réellement présent

Les constats ci-dessous proviennent d'une lecture du code. Les comptes rendus de
tests du 18 octobre restent des preuves historiques distinctes de cette revue.

| Besoin | Présent dans GeoSylva | Complément nécessaire |
| --- | --- | --- |
| Objet pile/grume stable | `WoodObjectCommands`, identité UUID et commandes distinctes | Réutiliser cette identité dans la réception |
| Mesures datées et corrections | `WoodObservationHistory`, repository et formulaires bois | Lier une réception à une version précise ; ne pas suivre automatiquement la dernière mesure |
| Cubage géométrique | `HarvestedWoodCalculator` : Huber, Smalian, Newton, enveloppe rectangulaire | Conserver la qualification géométrique ; aucune équivalence tarifaire FORLOG |
| Chantiers et plusieurs parcelles | `WorksiteForm`, `FieldRepositoryImpl`, écran Chantiers | Pièces, conditions et réception propres au chantier |
| Archive et restauration | Filtre et actions dans `WorksitesScreen` et `WorksitesViewModel` | Motif/historique dédié et droits partagés à compléter ; ne pas annoncer la restauration absente |
| Photos et carnet commun | `FieldRepositoryImpl`, `FieldJournalViewModel` | Référencer ces pièces dans un export et figer leurs empreintes |
| Solde et enlèvements locaux | `FieldBalance`, quantités décimales, conventions séparées | Faire le lien avec une réception validée sans crédit supplémentaire |
| Remesure sans ajout de stock | Observation bois séparée du carnet physique | Conserver cette séparation dans tous les nouveaux écrans |
| Rejeu et corrections locales | Identifiants stables et transactions Room ; correction conservée | Recevabilité métier et compensation d'une réception déjà utilisée |
| Réception métier | Pas d'objet de réception avec son circuit de validation trouvé dans les modules examinés | Créer document, lignes, version et validation locale explicite |
| Export réception/stock | Exports forestiers présents ; pas d'export du nouveau carnet identifié | JSON/CSV dédiés et relus, avec manifeste et périmètre |
| Bases achat/prestation/vente | Pas de circuit complet dans le socle bois examiné | Modèle distinct, conventions et rapprochement ; phase suivante |
| Organisation et Missions | Politique de navigation prévue ; `serverAccess = null` | Accès serveur fiable et isolation à raccorder avant activation Entreprise |
| Synchronisation FORLOG | Aucun connecteur établi | Interface autorisée, mapping et recette bilatérale à obtenir |

Un `WoodCommandReceipt` est un **accusé technique local de commande**. Son nom
ne signifie pas « réception de bois acceptée commercialement ».
La validation de champs dans `FieldEntryForm` ne constitue pas davantage une
réception contractuelle ni une autorisation d'organisation délivrée par GSIE.

## Ajustements nécessaires avant d'adopter le contrat proposé

- **Espace personnel préservé.** L'enveloppe FORLOG peut imposer une organisation
  pour l'échange d'entreprise. Le socle local GeoSylva doit continuer à fonctionner
  dans l'espace appareil ou personnel. Aucun faux identifiant d'organisation à créer.
- **Unités explicites.** Les exemples `m3` ont une convention synthétique. Conserver
  dans le produit la différence volume solide / enveloppe apparente / stère / masse.
  Une convention libre saisie n'est pas une convention contractuelle approuvée.
- **Calculs existants conservés.** L'absence de formule FORLOG lisible ne rend pas
  indisponibles les formules géométriques déjà implémentées et qualifiées séparément.
- **Sérialisation distincte.** `WoodContractCodec` utilise un ordre fixe des champs
  et des octets canoniques locaux. Le contrat proposé trie les clés. Ne pas changer
  les empreintes stockées : ajouter un adaptateur d'échange versionné avec vecteurs
  communs Android/serveur, sans prétendre à une compatibilité immédiate.
- **Historique de validation.** Le carnet conserve ses anciennes corrections et
  recalcule l'effet courant. Une réception contractuelle exige en plus sa version
  figée et l'analyse de ses enlèvements/documents dépendants avant compensation.
- **Hors ligne.** Un solde local ne prouve pas le stock global disponible sur plusieurs
  appareils. Afficher le périmètre local ; la future acceptation partagée dépendra
  du contrôle de révision et de la transaction serveur.
- **Migration conservatrice.** Un ancien stock initial déclaré n'est pas transformé
  en réception certifiée ; garder son origine et empêcher un second crédit implicite.

## Points à transmettre à la session FORLOG

Les extraits JS fournis permettent de confirmer statiquement deux risques déjà
signalés par l'audit : conversion MultiPolygon limitée à la première composante
et tests de coordonnées qui excluent zéro. Les mises à jour de surface uniquement
si positive peuvent aussi laisser une ancienne valeur. Leur exécution effective
dans le client n'est pas prouvée. Aucune correction du logiciel client n'est réalisée ici.

Pour le connecteur, le prochain lot utile reste l'export texte de la chaîne
création/réception → recherche du même bois → stock → correction, avec appelants,
SQL, unités et version d'IDE. L'autre session dispose du guide ciblé reçu.
Cela ne bloque pas les écrans et exports propres à GeoSylva.

## Traçabilité de la session

Preuves de réception et vérification indépendante conservées sous
`/workspace/.onboarding/forlog-audit-2026-10-19/` ; bilan transmissible sous
`/workspace/outputs/2026-10-19/forlog-review/`.
Cette tranche modifie la documentation de cadrage, pas les données, calculs,
écrans ou schémas Room. Aucun nouveau binaire n'est annoncé comme livré.

Vérification ciblée réalisée : `:app:testDebugUnitTest` avec trois filtres de
classes, **42 tests réussis, aucun échec, erreur ou test ignoré** : `FieldBalanceTest`
(12), `WoodObservationHistoryTest` (26), `HarvestedWoodReferenceCasesTest` (4).
La compilation debug requise par ces tests a réussi. Une première tentative
avait été bloquée par la socket locale Gradle, puis un nom de tâche preview
inexistant a été corrigé. Les journaux sont conservés. Aucun test UI Android,
échange FORLOG ou service en production n'a été exécuté pour cette revue.
