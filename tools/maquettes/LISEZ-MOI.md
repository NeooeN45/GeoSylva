# Retrouver les maquettes de Géosylva sur un PC Windows

Le lecteur parcourt l’export ChatGPT sur ton ordinateur. Il fonctionne hors connexion et laisse le ZIP d’origine intact. Le paquet Windows x64 inclut Python.

1. Télécharge le paquet `Geosylva-recuperation-Windows-x64` du projet. Dans l’Explorateur Windows, clique droit sur ce paquet et choisis **Extraire tout**.
2. Dans le dossier extrait, double-clique sur **Ouvrir.cmd**. Sélectionne le ZIP de ton export ChatGPT, encore compressé. Garde la fenêtre ouverte pendant la lecture.
3. À la fin, l’Explorateur ouvre un nouveau dossier `Geosylva-…` à côté de ton export. Transmets **rapport.json**, puis les lots **originaux-001.zip**, **originaux-002.zip**, etc., s’il y en a. Tu peux les envoyer séparément.

Un lot `originaux` contient des fichiers dont les octets correspondent à l’empreinte historique d’une maquette. Chaque lot inclut les identifiants et les empreintes dans `preuves.json`. Une image déjà conservée à l’identique est comptée dans le rapport. Le lecteur conserve les originaux tels quels, même si une image seule dépasse la taille cible de 24 Mio.

Les lots **candidats-a-verifier** restent sur ton PC pour examen. Ils peuvent contenir des pièces jointes du même fil, des variantes ou des images dont le format a changé. Un nom ou un lien vers un message permet d’enquêter. Seule une preuve de correspondance avec la version choisie permet de verser l’image au dépôt public. Si le lecteur ne crée aucun lot, le rapport seul suffit pour la suite.

## Lecture depuis le code source

Avec Python 3.10 ou plus récent, depuis la racine du dépôt, utilise par exemple la commande suivante. L’option `--destination` permet d’écrire le résultat sur un autre disque.

```powershell
python tools/maquettes/recuperer.py "D:\Exports\chatgpt.zip" --destination "D:\Geosylva"
```

Le lecteur reconnaît PNG, JPEG, GIF et WebP par leur contenu, même si le nom de fichier a changé. Il cherche aussi les médias référencés dans les index JSON de la conversation Géosylva. Les avertissements du rapport signalent une lecture partielle ou l’absence de cet index. La recherche par empreinte fonctionne même si le ZIP fournit uniquement des images. Un export qui ne contient que des liens vers des images distantes ne fournit pas leurs octets.

Les résultats restent sur le PC. Le rapport contient des identifiants et des empreintes, sans le texte des conversations. Il peut être transmis séparément des images. Avant publication sur le dépôt public, vérifier le contenu visible, les métadonnées et la décision du Fondateur pour chaque fichier.
