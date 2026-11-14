ASSEMBLAGE SOURCE IMMUABLE — WIP / NON_COMPILÉ / NON_COMPLET_13NOV

Commit source : c4e107548913cf82e58e2b10d920a8bffc15d33a
Arbre Git : bfc4e8894a54422310ceeb4534d2d6847b0757b9
ZIP : WIP_NON_COMPILE_NON_COMPLET_13NOV-c4e107548913.zip
SHA256 : 0c481faf7cca750f9e0711e10eb5ce8ae738de2c6987d2b0b9a4e7dbbdedbf33

Le ZIP a été produit par git archive à partir de CE commit immuable. Il contient
1525 fichiers suivis : base assemblée et ajouts, sans .git, ancien historique Git,
configuration privée non suivie ni sorties Android futures. Le reçu de réparation
distingue 145 captures observées exactes et une réparation nominale de contrat.
Cette réparation est identifiée comme telle, pas comme une ligne historique
retrouvée. Le test Room encore masqué a été retiré de l'assemblage.

Ce snapshot n'est pas compilé ni une récupération complète du 13 novembre.
Aucun APK ou résultat de test applicatif n'est qualifié par son archivage.
Les nouvelles variantes historiques conservées ailleurs dans le checkpoint
ne remplacent pas implicitement les fichiers de ce commit gelé.

WIP_SOURCE_TREE_c4e107548913.json contient, pour chaque chemin, le mode Git,
le blob Git original, les SHA256 source/export et les tailles. Les 1525 fichiers
internes n'augmentent pas les compteurs de variantes historiques récupérées.

RESTAURER LES OCTETS ET MODES EXACTS DU COMMIT DANS UN DOSSIER NEUF

    python3 tools/restore_assembly_wip.py --destination /tmp/geosylva-wip-neuf

Le script vérifie le ZIP, tous les fichiers et le manifeste avant d'écrire.
Il refuse un dossier non vide. Il restaure les modes du manifeste Git, car
le ZIP émis dans cet environnement ne fournit aucun mode Unix. Le seul écart
d'export Git est gradlew.bat, converti LF→CRLF : le script rétablit les LF puis
vérifie l'identité de chaque blob Git, sans accès au dépôt source original.

Le mode Git de gradlew est 0644. Pour rendre ensuite ce lanceur exécutable :

    python3 tools/restore_assembly_wip.py --destination /tmp/autre-dossier-neuf --make-gradlew-executable

L'option applique 0755 APRÈS vérification des modes Git et le signale séparément.
Aucun script ou code applicatif extrait n'est exécuté par le restaurateur.
