# Récupération des images — 16 novembre 2026

**4 images distinctes retrouvées et versées sur 107 références de travail ;
103 manquent encore.** Aucun original n'a été régénéré, converti ou retouché.
Poids exact ajouté par les images : **5 832 440 octets**, soit **5,56 Mio**
(hors documents et contrôle). La conformité visuelle reste **non vérifiée pour
les 107 références de travail** : récupérer une image ne constitue pas une recette Android.

## Base et périmètre

Branche `docs/conservation-maquettes-2026-11-16`, créée depuis `main` à
`77a5d72020c0f54d8c35e3513b02019919c247d4`. Au démarrage, la demande de fusion
[nº 19](https://github.com/NeooeN45/GeoSylva/pull/19) est ouverte, non fusionnée.
Son commit documentaire `9fb5fcf881c5f008c94925dc7239b6c6ac54ad94` a été repris
localement par cherry-pick (`8eceeb6`) pour disposer du décompte et du registre à
mettre à jour. Aucun code applicatif n'en provient ; aucune fusion distante et
aucun push sur `main` ne sont effectués. Les états de codage, tests Android et
décisions historiques de cet audit restent inchangés.

## Images retrouvées et preuve du rattachement

Les cinq pièces jointes identifiées dans les archives ont été relues avec
`codex_app.read_thread`. Les URL renouvelées ont permis cinq téléchargements
HTTP 200 ; deux donnent exactement les mêmes octets. Seuls les quatre fichiers
distincts sont versés. Les URL signées ne sont pas publiées.

| Identifiant | Métier | Original conservé | Dimensions | Octets | Preuve de sélection |
| --- | --- | --- | --- | ---: | --- |
| cartographie/54 | technicien | [54.png](cartographie/54.png) | 953 × 1651 | 2 079 785 | Tour `01a1ac0c-30c2-7686-9ba2-78ede2cf0e61`, même élément utilisateur que l'image : « cette maquette est bien je la garde… ». |
| cartographie/72 | commun | [72.png](cartographie/72.png) | 952 × 1653 | 2 245 792 | Tour `01a1ac7f-4d8b-742c-8806-0cb834b70b21` : « prend la version de base », pièce jointe du choix ; le renvoi précédent a le même SHA-256. |
| conducteur/dossier-chantier/proposition-02 | conducteur | [dossier-chantier-proposition-02.png](conducteur/dossier-chantier-proposition-02.png) | 887 × 1774 | 1 424 232 | Tour `01a19ca3-516f-71df-8149-6c606fcfbe59` : « non on vas prendre la 02 passont aux maquette suivante ». |
| conducteur/fiche-bois/proposition-01 | conducteur | [fiche-bois-proposition-01.jpg](conducteur/fiche-bois-proposition-01.jpg) | 640 × 1280 | 82 631 | Tour `01a19d0d-3d28-76b7-a395-34a831193ac8` : pièce jointe `1000098565.jpg`, « On vas prendre cette version ». |

Les quatre tours/éléments sont déjà cités dans les décisions de leurs références
du décompte. Le [manifeste](MANIFESTE_IMAGES.json) conserve les empreintes, les
pointeurs JSON, les SHA-256 des archives, les identifiants de pièce jointe et les
citations exactes. Aucun rattachement ne repose sur l'aspect ou sur le titre de
l'image. Le JPEG est l'original de la pièce jointe choisie, pas une conversion du
PNG disparu. L'identité binaire avec les PNG initialement produits par l'outil de
génération n'est pas démontrée et n'est pas revendiquée.

Avant versement public : inspection visuelle des quatre fichiers, contrôle des
métadonnées des cinq téléchargements. Aucune donnée personnelle, secret ou donnée
réelle de client décelée ; exemples et valeurs d'illustration visibles. Aucun
EXIF/GPS ; aucun champ texte PNG ; JPEG avec JFIF/ICC seulement. Le fond de la
54 représente une forêt publique nommée, avec mention de valeurs d'illustration ;
ce n'est pas un dossier réel de client. Aucun fichier n'est bloqué pour ce motif.

## Décompte des manquantes

| Métier | Références de travail | Images retrouvées | Introuvables |
| --- | ---: | ---: | ---: |
| Technicien | 12 | 1 | 11 |
| Conducteur | 15 | 2 | 13 |
| Commun | 80 | 1 | 79 |
| Hors périmètre | 0 | 0 | 0 |
| **Total** | **107** | **4** | **103** |

| Métier des introuvables | Codé et raccordé | Codé sans données | Non codé | À rattacher | Total |
| --- | ---: | ---: | ---: | ---: | ---: |
| Technicien | 9 | 1 | 1 | 0 | 11 |
| Conducteur | 4 | 1 | 7 | 1 | 13 |
| Commun | 62 | 1 | 16 | 0 | 79 |
| **Total** | **75** | **3** | **24** | **1** | **103** |

La [liste exhaustive des 103 manquantes](MAQUETTES_INTROUVABLES_2026-11-16.csv)
donne, pour chaque identifiant, métier, état de codage, archive/tour de génération
et marche à suivre. Obtenir le fichier original ou un export de sa pièce jointe,
vérifier son identité documentaire et son empreinte, puis compléter le manifeste.
Les deux Synthèses demandent un original ou un export de l'échange antérieur,
inaccessible ici ; la 49 garde sa réserve de validation ; les bois associés
gardent leur correspondance d'écran à confirmer.

Les dix repères du catalogue de 117 exclus du travail (neuf remplacés et la 11
non finalisée) n'ont pas non plus d'image : ils sont listés
[séparément](HORS_TRAVAIL_INTROUVABLES_2026-11-16.csv). Les 24 autres occurrences
de variantes déjà recensées hors des 117 n'ajoutent aucune référence de travail ;
aucun original n'a été identifié pour elles. Ne pas additionner des renvois ou
des variantes aux 107 pour annoncer un meilleur résultat.

Si un original reste introuvable, conserver `absente` et `non vérifiée`. Une
capture du code ou une recréation serait une nouvelle proposition nécessitant
un lot distinct et une nouvelle décision du Fondateur. Aucune n'est produite ici.

## Emplacements examinés et limites

Le [journal structuré](RECHERCHES_2026-11-16.json) donne les emplacements,
compteurs, branches/SHA, archives et limites. Les sources ont été lues sans
modification ; seuls les téléchargements, rapports et le nouveau worktree ont
reçu des écritures.

- **Conversations** : 239 archives JSON sous
  `/workspace/outputs/2026-11-14/recovery-history`, dont 83 fichiers `root*`.
  Le fil « Développement de Géosylva maquette » contient 139 traces uniques de
  génération accessibles. Elles donnent un chemin historique, pas les pixels.
  Les cinq pièces jointes image ont été récupérées par le service. Aucun PNG/JPEG
  de maquette n'est encodé en base64 dans ces archives ; l'unique `data:image`
  trouvé est le logo SVG d'une documentation Mapbox. L'accès au tour antérieur
  au plus ancien tour lisible échoue même avec une seule entrée :
  « Thread history exceeds the read size limit… A single oversized turn cannot
  be read by this tool ». Il reste donc une limite réelle sur l'historique amont.
- **Fichiers et anciens espaces** : `/workspace`, `/tmp`, `/home/agent` ;
  GeoSylva, GeoSylva-user-tests, recovery-geosylva, backups et worktrees,
  Quintessences, Forge, Hub, horizon-origin-web, `generated_images`, `attachments`,
  `outputs`, `.onboarding`, caches accessibles. 543 213 fichiers indexés ;
  9 478 images locales et 713 membres images d'archives examinés, soit 10 191
  occurrences, sans les nouveaux téléchargements. 126 ZIP/TAR de premier niveau
  et quatre archives imbriquées ; quatre fausses ZIP sont des fixtures pytest.
  17 400 fichiers de cache/sans extension examinés par signature. Aucun original
  rattachable. Les caches de credentials/cookies ne sont pas lus.
- **Git local et distant** : neuf bases d'objets, tous les blobs lus par
  signature, objets non référencés compris ; 132 blobs images distincts, aucune
  maquette identifiée. Un bare isolé reçoit les 15 branches distantes et le tag
  de GeoSylva. Douze bundles, 25 têtes, déjà couvertes par ces bases ; sept ZIP
  historiques distants identiques aux sauvegardes locales, examinés aussi.
- **GitHub** : une release en brouillon sans asset ; 34 artefacts Actions, dont
  31 ZIP de rapports téléchargés et inspectés (6 820 909 octets), aucune image.
  Les trois APK sont contrôlés par métadonnées seulement : sorties de build,
  sans valeur de preuve d'une maquette d'origine.
- **Hors d'accès** : `/root`, `/tmp/codex-daemon-1000`, anciens environnements
  supprimés, volumes Windows non montés, objets/branches supprimés sur GitHub.
  Huit chemins temporaires ont disparu pendant l'indexation ; erreurs conservées
  dans les preuves locales. `/mnt` et `/media` n'offrent pas d'ancien volume.
- **Archives utilisateur distantes** : les deux ZIP
  `DOCUMENTS_POUR_DEVELOPPEMENT_GEOSYLVA_2026-11-01.zip` et
  `DOSSIER_BASE_LOCALE_GEOSYLVA_QUINTESSENCES.zip` sont absents sous leur nom
  dans les fichiers et non reconnus parmi les membres d'archives. Leurs deux
  téléchargements Sediment échouent : « file could not be authorized or
  resolved ». Leur contenu reste non vérifié ; un renvoi des originaux ou un
  lien de téléchargement accessible permettrait de poursuivre cette piste.

Les inventaires bruts restent dans
`/workspace/outputs/2026-11-16/conservation-maquettes/` ; ils peuvent contenir des
chemins ou données techniques et ne sont pas publiés aveuglément. Le journal
versionné conserve les éléments utiles sans URL signée, jeton ni contenu client.

## Conservation et contrôle

La [règle de conservation](README.md#règle-obligatoire-pour-une-nouvelle-maquette-ou-une-modification)
est également inscrite dans `REPRISE_AGENTS.md` et dans le suivi des maquettes.
Le contrôle CI vérifie les fichiers et les empreintes ; les 103 absences sont
des avertissements. `--strict` les rend bloquantes une fois le stock complété.

Validation locale du lot : 24 tests du contrôle réussis ; contrôle du manifeste
en mode courant : code `0`, 4/107 présentes, 103 absences, zéro incohérence.
Mode strict : code `1` attendu pour ces 103 absences. Aucun build, test JVM,
essai instrumenté ou essai S25 de l'application n'est lancé dans ce lot.
