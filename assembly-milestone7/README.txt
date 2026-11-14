SOURCE IMMUTABLE DU JALON 7 — BIBLIOTHÈQUES UI/MAPBOX VÉRIFIÉES

Source : f2c1b508ee4357fc0ff4f4123daea9a98dfe3845
Arbre : b76efa6837aa526777f617c45fbfbb90da1efbe3
ZIP : LIBRARIES_VERIFIED_NOT_APP_NOT_S25-f2c1b508ee43.zip
SHA256 : 7264a2185c94c9cac4568f8ccd0fbac35b926f14aba8eef2d0761a2663044730
1579 fichiers suivis, sans .git, historique Git ancien ni sorties de build.

Les sources sont identiques au commit testé 2d2259f217a96f12a697ef45fbba85998c3931e9 ;
seules les pièces de documentation de récupération diffèrent. UI et Mapbox
ont compilé et les 47 tests JVM récupérés de 9 suites ont réussi, sans échec,
erreur ni test ignoré. Ces résultats ne qualifient pas l'application complète.
L'app est bloquée en compilation KSP : deux sources FieldControlBasis manquent.
Aucun APK complet, S25, appareil, rendu visuel ou usage production n'est validé.

ResourceRepair/ est un nouveau texte de réparation FR/EN, distinct de sources
historiques exactes. verification/milestone7/ contient les preuves de ce seul
build bibliothèque et du diagnostic applicatif demandé ; aucun secret/config
privée ou historique de conversation n'est publié.

Restaurer les octets et modes Git dans un dossier neuf, sans dépôt original :

    python3 tools/restore_assembly_wip.py --snapshot milestone --destination /tmp/geosylva-jalon7-neuf

Le restaurateur inverse le seul export LF→CRLF de gradlew.bat et vérifie les
1579 blobs/modes, puis retrouve exactement l'arbre Git ci-dessus. Les modes
proviennent du manifeste Git car le ZIP ne fournit pas de modes Unix. Ajouter
--make-gradlew-executable applique explicitement chmod +x après ce contrôle.
