# C01 — commandes et format local des relevés bois

## Code ajouté le 18 octobre

`domain/wood/WoodObjectCommands.kt` définit les déclarations d’identité, le contenu
d’un relevé et six intentions immuables : créer, modifier l’identité déclarative,
ajouter un relevé, corriger un relevé exact, sélectionner le relevé courant, archiver.
`WoodContractCodec.kt` fige ces valeurs en JSON UTF-8 v1 et calcule leur SHA-256.
Le noyau `RawWoodObservation` conserve aussi la longueur de pièce des piles et borne
les codes de méthode/convention/forme. Les chaînes UTF-16 mal formées sont refusées
avant tout remplacement par l’encodeur UTF-8.

Ces modèles et ce codec sont **du code de domaine non encore raccordé à Room ou à
un formulaire**. Ils ne constituent ni un journal durable, ni une synchronisation,
ni une autorisation, ni une nouvelle page disponible dans l’application.

## Choix de représentation

| Élément | Règle v1 |
| --- | --- |
| Format d’observation | `wood-observation-payload`, `schemaVersion=1`, parser `wood-raw-decimal-1`. |
| Format de commande | `wood-object-command`, `schemaVersion=1`, action exacte et contexte complet. |
| Bruts | Texte et unité exacts, y compris absence, vide, valeur invalide et code inconnu. Pas de cache numérique fourni par l’interface. |
| Dimensions pile | `pileLength`, `pileDepth`, `meanPileHeight`, `pieceLength`. La longueur de pièce n’est pas une profondeur de pile par défaut. |
| Dimensions grume | `segmentLength`, `diameterAtStart`, `diameterAtMiddle`, `diameterAtEnd`. Sections hors méthode conservées dans le noyau, jamais supprimées pour réussir un calcul. |
| Déclarations | Description, origine, essence, qualité et position capturées dans l’observation ; les futures modifications de l’objet ne les changent pas. Aucune essence ou qualité déclarée ne vaut référentiel vérifié. |
| Temps | Date du fait facultative avec instant, zone IANA et offset cohérents. Date de saisie et auteur réels relèvent du commit du futur repository. Pas de date de nouvelle tentative dans l’empreinte. |
| Commande | UUID d’opération, dépôt, objet, portée, compte éventuel, auteur, action, révisions, relevés, motif et intention de sélection inclus. Aucune donnée d’accès secrète dans le document. |
| Correction | Source et révision exactes, nouvel UUID et motif requis. Aucun flag de sélection automatique ; choisir le courant reste une opération distincte. |
| Création groupée | Première observation facultative ; sélection interdite si aucune observation n’est fournie ; type de mesure concordant avec le type physique. Atomicité réelle à réaliser en C02. |
| Taille | Document limité à 128 Kio ; champs bornés sans troncature. Limites comptées en points de code Unicode. |
| Relecture | Empreinte vérifiée, UTF-8 strict, version contrôlée, validation du domaine, puis comparaison à la représentation canonique v1. |

La représentation canonique est **propre à GeoSylva**, avec ordre fixe des champs,
valeurs nulles explicites et encodage compact. Ce n’est pas un import JSON général
ni une revendication RFC 8785. Une variante avec champs inconnus, clés dupliquées,
champs d’une autre méthode, UUID non canoniques ou espaces de présentation est
refusée au lieu d’être réécrite avec perte d’information. Les codes métier inconnus
dans un champ prévu restent conservés, mais le calcul ne les interprète pas.

SHA-256 permet de comparer et contrôler les octets ; **ce n’est pas une signature
ou une preuve d’autorisation**. Le repository devra comparer la portée et l’auteur
aux accès réels, vérifier les parents/révisions et conserver commande/réponse dans
la même transaction. Un ancien résultat perdu devra être relu sous son identifiant,
pas recréé sous un nouvel identifiant. Aucun effet de stock ou de paiement ici.

## Validation de ce lot

**115 tests ciblés passent, dans sept suites, sans échec, erreur ou test ignoré** :
15 nouveaux tests du codec, 10 tests des bruts, 18 tests de l’adaptation des calculs,
26 tests d’historique, 28 tests des formules/cas de référence et 18 tests du
ViewModel de dépôt. La compilation de production et des tests est réussie avec
le runner Gradle existant, hors ligne et sans nouvelle dépendance.

Les nouveaux cas couvrent la représentation v1 exacte, les six intentions,
les identités/portées/révisions et contenus liés à l’empreinte, les corrections,
les bruts manquants/invalides/zéro, les unités inconnues, le document altéré,
les champs inconnus ou ambigus, les tailles excessives, les caractères mal formés
et la distinction entre longueur de pièce et profondeur de pile.

Preuves : `/workspace/outputs/2026-10-18/wood-contracts/VALIDATION.json` et
résultats XML conservés dans ce même dossier. Les essais Android de navigation
du lot précédent restent datés et ne sont pas présentés comme une validation de
ces nouveaux modèles. Ce lot ne modifie aucun écran ni le schéma Room43
(empreinte identique à la sauvegarde de navigation). Aucun APK supplémentaire
n’est livré par ce lot de domaine ; la persistance reste à qualifier en C02.

## Suite C02

Créer le repository canonique, les entités et la migration additive ; vérifier
atomiquement la portée et les révisions, conserver les réponses d’opérations,
partager la garde d’accès/purge avec les dépôts, et prouver la relecture après
fermeture. Qualifier ensuite les écrans et exports. Les photos documentaires
nécessiteront un stockage privé, un cycle de fichier fiable et une extension
versionnée du contrat ; le cubage photographique ne bloque pas le socle.
