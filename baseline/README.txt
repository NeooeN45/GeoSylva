BASE SOURCE COMPLÈTE DU 19 OCTOBRE 2026

Archive : GeoSylva-recovery-base-2026-10-19-36d1515.zip
Commit : 36d1515b6bd53217bd8d85a6fe0a2b11a549dc58
SHA256 : 76e446c3ca6fdcc73656e7d82e06405089b1eaf0c0759ff3e08dc17e29b214a6
État : Room 46, version 3.0.0, code 11 ; 1389 fichiers suivis.

Ce ZIP contient le source HEAD complet de cette ancienne base, sans .git ni
bundle historique. Ses 1389 fichiers internes ne sont pas ajoutés aux compteurs
de variantes récupérées. La récupération du 13 novembre reste à assembler,
compiler et qualifier ; cette base n'est pas l'application restaurée de novembre.

Les SHA256 et tailles de chaque fichier sont dans base-source-manifest.json.
Le contrôle public a confirmé deux fixtures PEM à en-tête BEGIN PUBLIC KEY,
sans clé privée. BASELINE_VERIFICATION.checkpoint5.json décrit les contrôles,
les motifs de contact/documentation et fixtures synthétiques examinés sans
afficher leurs valeurs. Aucun bundle Git historique de base n'est inclus.

RESTAURATION DANS UN DOSSIER NEUF, DEPUIS LA RACINE DU DÉPÔT D'ARCHIVES

    python3 tools/restore_baseline.py --destination /tmp/geosylva-base-neuve --make-gradlew-executable

Le dossier de destination doit être absent ou vide. Le script contrôle le SHA
du ZIP, refuse les chemins dangereux et liens symboliques, extrait les fichiers
et revérifie les 1389 SHA256 ainsi que les permissions appliquées.

Attention aux modes : ce ZIP ne fournit AUCUNE permission Unix de fichier ni
exécutable déclaré. Le script applique donc explicitement 0644 aux fichiers et
0755 aux dossiers, puis l'option ci-dessus rend gradlew exécutable séparément.
Il ne prétend pas avoir retrouvé des modes historiques absents de l'archive.
Si des modes Unix sont présents dans une autre archive, ils sont conservés.

Après une extraction manuelle, rendre au minimum le lanceur Gradle exécutable :

    chmod +x /chemin/extrait/GeoSylva-recovery-base-2026-10-19/gradlew

Sans l'option --make-gradlew-executable, le script garde gradlew en 0644 pour
ce ZIP et le signale dans son rapport. Aucun script extrait n'est exécuté.
