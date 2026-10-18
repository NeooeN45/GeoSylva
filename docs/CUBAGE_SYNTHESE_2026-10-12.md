# Cubage en Synthèse — recette locale du 12 octobre 2026

Le correctif rend explicite l'absence d'une méthode applicable qualifiée dans
la Synthèse. Il conserve les relevés et les versions clôturées ; il ne livre
pas un nouveau tarif régional. Le cadrage produit et les dernières directives
du Fondateur restent inchangés.

## Comportement du moteur 3

`SynthesisReportCalculator` produit explicitement
`geosylva-observation-synthesis-3`. Dans cette tranche, aucun catalogue de
méthodes et coefficients qualifiés n'est raccordé à ce chemin. Les nouveaux
volumes globaux, par essence et par hectare restent donc indisponibles
(`null`), avec zéro tige calculée. Ce zéro décrit la couverture du calcul,
pas une quantité de bois nulle.

| Situation | Résultat présenté |
| --- | --- |
| Méthode absente | Motif `MISSING_TARIF`, méthode à choisir. |
| Choix de méthode enregistré | Motif `UNQUALIFIED_TARIF`, référence, coefficients, domaine et mesures compatibles à qualifier. |
| Diamètre invalide | Motif propre conservé ; aucune dimension de remplacement. |
| Hauteur brute, attribuée à une classe ou moyenne | Donnée conservée ; elle ne débloque pas le cubage et ne devient pas une mesure individuelle qualifiée. |
| Région, métier, GPS, pack téléchargé, numéro ou coefficient saisi | Informations conservées ; aucune qualification automatique d'une équation. |

Les comptages, diamètres et valeurs monétaires saisis restent disponibles
avec leur portée antérieure. Une valeur enregistrée ne fournit pas un prix
local ni une quantité payable. Aucun montant, coefficient territorial ou
volume de remplacement n'est fabriqué.

La carte Material 3 explique cette situation dans Gestion forestière et
Bois et valorisation. Le motif FR/EN est partagé avec les PDF standard,
illustrés et les trois rapports spécialisés. Les libellés distinguent
**hauteurs renseignées**, **méthodes appliquées** et **moteur de Synthèse**.
Le JSON compact du PDF illustré s'applique seulement au moteur 3.

## Conservation de l'historique

Le défaut de désérialisation demeure
`geosylva-observation-synthesis-2`. Un moteur omis dans un ancien document
n'est pas remplacé implicitement par le moteur 3. Les résultats clôturés
restent ceux de leur capture ; aucun calcul actuel ne remplace leurs volumes.
Les nouvelles notices et la présentation compacte n'altèrent pas le contenu
des rapports moteur 2. Les gardes de lecture/export, formats historiques et
schéma **Room 42** restent inchangés.

La fixture native historique v2 est explicitement TEST, avec quatre tiges,
volumes et comptes cohérents ; son assertion **quatre pages** est conservée.
Les fixtures v3 sont séparées et vérifient des pages A4 lisibles, sans imposer
le même nombre de pages. Aucun de ces jeux n'est une archive terrain ou une
donnée d'utilisateur.

L'ancien `ForestryCalculator`, ses usages et les réglages de tarifs restent
hors périmètre. La réussite de cette recette ne les qualifie pas et ne
démontre pas une calibration forestière, une précision empirique ou une
validation commerciale. H/D, S %, nouveaux services GSIE et synchronisation
ne sont pas activés par ce correctif.

## Aide « Repères de cubage »

Les trois onglets Sources, Choisir une méthode et Formules présentent les
mesures attendues, familles de tarifs et domaines d'emploi. Les références
CNPF/CRPF, FAO et Australian National University ont été relues dans les
sections consignées ; le lien historique Schaeffer reste un repère distinct.
La note des sources distingue les documents consultés, les limites d'accès
et les références historiques.

H/D utilise hauteur totale et diamètre du même arbre, avec une convention
documentée. Le seuil universel « stable si <85 » est retiré de cette aide.
Les formules de Huber, Smalian et Newton concernent des grumes mesurées aux
sections prévues, sans devenir des tarifs d'arbres sur pied. Une erreur dans
la formule de Newton sur une page ANU est signalée dans la note ; la formule
présentée s'appuie sur le tableau FAO effectivement consulté.

- [Sources et limites de lecture](../../.onboarding/synthesis-cubage-safe-2026-10-12/sources/AIDES_CALCUL_SOURCES.md).
- [Index des accès et empreintes](../../.onboarding/synthesis-cubage-safe-2026-10-12/sources/INDEX_QUALIFICATION.json).
- [Revue de l'aide Android](../../.onboarding/synthesis-cubage-safe-2026-10-12/sources/AIDE_ANDROID_REVIEW.json).

## Validation exécutée

| Contrôle | Révision et résultat | Preuve |
| --- | --- | --- |
| Build final JVM, APK et APK d'instrumentation | **1 641 tests / 110 suites**, zéro échec, erreur ou ignoré ; 296,77 s ; entrées de build stables. | [Build du 12/10, campagne 141152](../../.onboarding/synthesis-cubage-safe-2026-10-12/build-20261012T141152Z/BUILD_PROOF.json). |
| Native UI et PDF | **32/32** sur APK `9633f72f…`, avant les dernières étiquettes FR/EN ; carte normale et police 200 %, trois onglets d'aide, garde des résultats/rapports historiques et exports TEST. | [Campagne native 140105](../../.onboarding/synthesis-cubage-safe-2026-10-12/native-20261012T140105Z/NATIVE_PROOF.json). |
| Native PDF final | **14/14** sur APK final `efe1f144…`, après les dernières étiquettes et la présentation compacte v3. | [Campagne native 142326](../../.onboarding/synthesis-cubage-safe-2026-10-12/native-20261012T142326Z/NATIVE_PROOF.json). |
| Textes PDF final | **8/8 PDF TEST v3**, français et anglais : motifs, absence de prétention de hauteur mesurée, version, valeurs saisies et lisibilité inspectés selon les assertions consignées. | [Contrôle PDF final](../../.onboarding/synthesis-cubage-safe-2026-10-12/native-20261012T142326Z/PDF_TEXT_CHECK.json). |
| APK autonome TEST | Manifeste, configuration et signature v2 vérifiés. | [Qualification APK final](../../.onboarding/synthesis-cubage-safe-2026-10-12/apk-final-efe1f144/PHONE_APK.json). |
| Téléchargement HTTPS V2 | **7/7 contrôles** : page, HEAD, plages, plage invalide, ETag et GET intégral ; 135 607 544 octets et SHA final identiques. | [Qualification publique V2](../../.onboarding/synthesis-cubage-safe-2026-10-12/apk-transfer/release/PUBLIC_DOWNLOAD_QUALIFICATION.json). |

Les deux campagnes natives sont distinctes et ne constituent pas 46 tests
exécutés sur un même APK. La première campagne native 133021 conserve son
échec de pagination ; elle ne reçoit pas le statut de la campagne corrigée.
La tentative de transfert V1 incomplète reste également conservée. Les
références source et ces tests ne remplacent aucune validation sur le terrain.

## APK à tester

[Télécharger GeoSylva Test](https://9d4f70e1.quintessences-landing.pages.dev/GeoSylva-test.apk).

- Application séparée : `com.forestry.counter.preview`, nom **GeoSylva Test**.
- Version : `3.0.0-test`, code **11**, minimum Android API **26**.
- Taille : **135 607 544 octets** ; signature APK **v2** vérifiée.
- SHA-256 : `efe1f14472445a2b1e59c6cb269663ccf6ba919e3907fd6e11b7ff97e4abd703`.
- GSIE, clé publique des packs et Google vides : variante locale hors ligne.
- Installation sur le téléphone utilisateur non vérifiée ; aucune signature
  ou publication Android de production revendiquée.

Le transfert V2 est qualifié ; le déploiement utilise un aperçu Pages et ne
modifie pas le site de production. [Preuve de l'aperçu](../../.onboarding/synthesis-cubage-safe-2026-10-12/apk-transfer/release/PREVIEW_DEPLOYMENT.json).
Le checkout local reste la branche `work`, base
`c59ba7cccd3b5a69ae81e5007bb7d5f263aeacb7`. Le WIP préexistant est conservé ;
aucun commit ou push GitHub n'est revendiqué pour cette tranche.
