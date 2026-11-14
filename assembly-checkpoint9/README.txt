CHECKPOINT 9 — SNAPSHOT IMMUABLE, APPLICATION NON CERTIFIÉE

Commit : edbe6c2d1829ac1446bd1ab21643206c0016829c
Arbre Git : 387491c133286c9772c7832fd130d7d537fadcde
1 655 fichiers suivis, sans .git ni fichiers non suivis. Les corrections
postérieures à ce commit restent hors de ce gel. Le schéma Room49 exporté,
les sources et preuves de récupération/qualification suivies sont conservés.

Les preuves SQLite/KSP sont uniquement celles déjà suivies dans le commit.
Aucun nouvel essai SQL, build, diagnostic Android, APK ou log d'environnement
n'est ajouté par cette sauvegarde. Au moment de la demande, le diagnostic
applicatif3 est en cours : aucune application compilée, recette S25 Ultra,
fidélité visuelle ou mise en production n'est certifiée par ce checkpoint.

Restauration dans un dossier neuf, sans dépôt applicatif d'origine :

    python3 tools/restore_assembly_wip.py --snapshot checkpoint9 --destination /tmp/geosylva-checkpoint9-neuf

SHA256, blobs et modes Git sont vérifiés ; l'arbre exact doit être retrouvé.
Le seul export LF→CRLF de gradlew.bat est inversé. Aucun script applicatif
n'est exécuté. Les snapshots précédents et leurs qualifications sont conservés.
