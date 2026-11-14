CHECKPOINT 8 — SOURCES RÉCUPÉRÉES, COMPILATION NON CERTIFIÉE

Commit immuable : ff8be9bb9b5f4b0441ea1ea1c2ce52b7cde16971
Arbre Git : 6734a7ef384a4533dbb8bf3eb2cbca0180c7382c
1 612 fichiers suivis ; aucun .git, ancien historique Git ou fichier non suivi.
Les trois commits nouveaux concernent le contrat Room FieldControlBasis,
la géométrie/portée des tiges et les interfaces cartographiques récupérées.
La reconstruction par contrat du stockage reste distincte du code historique perdu.

Ce gel ne certifie ni compilation complète, ni APK, ni Android/S25 Ultra,
ni rendu visuel ou production. La preuve bibliothèque du checkpoint7 reste
liée à son ancien état. La seule preuve SQLite incluse est celle déjà suivie
sous docs/recovery/CONTROL_BASIS_STORAGE_RECONSTRUCTION.json dans ce commit ;
aucun log SQLite local ou résultat Android supplémentaire n'est ajouté.

Restauration dans un dossier neuf, sans dépôt applicatif d'origine :

    python3 tools/restore_assembly_wip.py --snapshot checkpoint8 --destination /tmp/geosylva-checkpoint8-neuf

Le contrôle vérifie SHA256, chaque blob et mode Git et retrouve l'arbre exact.
Il inverse uniquement la conversion LF→CRLF de gradlew.bat lors de l'export.
Aucun script applicatif n'est exécuté. Les anciens snapshots sont conservés.
