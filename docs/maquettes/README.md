# Conservation des maquettes de GeoSylva

Les fichiers image de ce dossier sont les originaux récupérés, dans leur format
d'origine, sans retouche. Ils servent de références visuelles seulement quand
leur identité et la décision du Fondateur sont établies. Un prompt, une trace de
génération ou une image uniquement visible dans une conversation ne suffit pas.

- [Manifeste des images](MANIFESTE_IMAGES.json) : identifiant, chemin, SHA-256,
  dimensions, provenance et décision sourcée.
- [Bilan de récupération du 16 novembre](RECUPERATION_2026-11-16.md) : résultat,
  emplacements examinés, limites et suites proposées.
- [Originaux de travail introuvables](MAQUETTES_INTROUVABLES_2026-11-16.csv) :
  une ligne par référence manquante, métier, état de codage et marche à suivre.
- [Repères historiques exclus du travail](HORS_TRAVAIL_INTROUVABLES_2026-11-16.csv) :
  dix autres images manquantes, sans les ajouter aux 107 références de travail.
- [Décompte maquette → écran](../developpement/DECOMPTE_MAQUETTES_ECRANS_2026-11-16.md)
  et [registre de preuves](../developpement/PREUVES_MAQUETTES_ECRANS_2026-11-16.json).

## Règle obligatoire pour une nouvelle maquette ou une modification

Dans **la même session**, avant de terminer :

1. Enregistrer les octets de l'image sous `docs/maquettes/<famille>/`, avec un
   nom dérivé de son identifiant. Garder le format d'origine. Une variante reçoit
   son propre identifiant et son fichier : ne pas écraser un original retenu.
2. Inscrire l'image au manifeste avec SHA-256, largeur, hauteur, source de
   récupération/création, date et statut (`retenue`, `remplacée`, `non finalisée`).
   Mettre à jour la référence du décompte et ses preuves dans le même lot.
3. Lier l'image à la décision du Fondateur : tour/élément ou décision versionnée,
   citation exacte et variante concernée. Si le choix est en attente, le dire et
   utiliser `non finalisée` ; ne jamais inventer une validation. Une référence
   visuelle retenue exige cette décision et un original effectivement conservé.
4. Contrôler le contenu visible et les métadonnées avant tout ajout au dépôt
   public. Signaler toute donnée personnelle, secret ou donnée réelle de client
   et garder le fichier concerné hors Git jusqu'au traitement explicite du cas.
   Ne pas retoucher un original en silence. Ne pas publier d'URL signée ni de jeton.
5. Exécuter le contrôle ci-dessous, committer sur une branche dédiée et pousser
   sur une branche distante avant la fin de session. Vérifier le SHA distant et
   effectuer aussi la sauvegarde distante datée prévue dans
   [REPRISE_AGENTS](../developpement/REPRISE_AGENTS.md#sauvegarde-obligatoire-à-la-fin-de-chaque-session).

**Une maquette qui n'existe que dans une conversation n'est pas une référence.**
Les 107 lignes historiques restent un index de travail et de correspondance au
code ; les lignes dépourvues d'original ne qualifient aucune conformité visuelle.

## Contrôle automatique

Depuis la racine du dépôt, Python 3.10 ou plus récent, sans dépendance :

```sh
python3 -m unittest discover -s tools/tests -p 'test_check_mockup_images.py' -v
python3 tools/check_mockup_images.py
```

Le workflow [Conservation des maquettes](../../.github/workflows/maquettes.yml)
exécute ces commandes sur les changements concernés. Le contrôle lit toutes les
références de travail du registre : fichier présent, SHA-256 du manifeste,
dimensions de l'en-tête et cohérence du statut de présence. Les chemins sortant
de ce dossier, doublons et incohérences sont des erreurs bloquantes. Les fichiers
manquants sont listés avec métier et état de codage ; leur absence seule avertit
sans faire échouer la CI tant que le stock historique est incomplet.

Une fois les originaux complétés, ajouter `--strict` à la dernière commande du
workflow, dans une demande de fusion relue :

```sh
python3 tools/check_mockup_images.py --strict
```

Cette commande existe dès maintenant pour vérifier le manque ; elle sort `1`
tant qu'une image de travail manque. Ne pas retirer de référence, changer son
statut de travail ou abaisser un contrôle pour simuler un stock complet. Une
suppression ou un remplacement de référence exige une décision sourcée distincte.

Le contrôle de conservation ne détermine ni l'authenticité d'une décision, ni
l'absence de données sensibles, ni la conformité des écrans Android. Celle-ci
requiert une comparaison de captures avec l'original sur la version testée.

## Recherche complémentaire du 18 novembre 2026

Le nouvel examen de 9 563 occurrences d’images sur disque et de 713 dans des archives, complété par les empreintes de 132 images Git, retrouve les quatre originaux déjà au manifeste. Le total reste de **4 originaux conservés sur 107** pour **5 832 440 octets**. Les archives de conversation ont en revanche fourni l’empreinte de génération de 106 références, y compris les deux écrans de Synthèse Technicien. Pour la 99 « Tracé enregistré », le nom du fichier et l’identifiant de génération sont connus. L’index de recherche rattache les empreintes aux sorties historiques qui les attestent. [Index des preuves de recherche](../../tools/maquettes/index.json)

Le lecteur Windows compare localement les fichiers de l’export ChatGPT à ces preuves. Il produit des lots d’images et un rapport sans texte de conversation. Le contrôle du manifeste continue de compter les fichiers effectivement déposés. [Mode d’emploi Windows](../../tools/maquettes/LISEZ-MOI.md) · [Téléchargements du lecteur dans GitHub Actions](https://github.com/NeooeN45/GeoSylva/actions/workflows/recuperation-maquettes.yml)

### Résultat de l’examen de l’export sur Windows

Le rapport du Fondateur couvre le ZIP de 1,85 Go et le dossier extrait. L’empreinte de l’index utilisé correspond au commit `6476d56`. Les 107 identifiants et leurs empreintes concordent avec le registre et le manifeste. L’examen local indique **0 nouvel original et 0 candidat relié à une référence**. Le fil d’origine n’a été retrouvé ni par identifiant ni par titre dans les index examinés. Les **103 références manquantes** restent à rechercher dans la conversation d’origine ou dans une autre sauvegarde contenant les images.

La maquette 100 « Calcul et réception », placée avant la validation « Conserver et passer à l’intégration prévue », sert de premier essai de téléchargement depuis le fil. L’empreinte historique de cette image permet de contrôler les octets du fichier téléchargé. [Index des preuves de recherche](../../tools/maquettes/index.json)
