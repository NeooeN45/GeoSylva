# Export local de chantier GeoSylva — format 1

`FieldRepository.exportSnapshot(site, access)` capture le dossier dans une
transaction autorisée. `GeoSylvaFieldExporter.write` produit ensuite une archive
ZIP ; `verify` relit ses fichiers sans écrire dans le système de fichiers et
renvoie le manifeste et l'instantané décodé. Les flux restent ouverts pour
l'appelant. Exécuter ces opérations sur le dispatcher IO.

L'interface doit revérifier l'accès après la fabrication et immédiatement avant
de partager ou de copier vers le document choisi avec Android SAF. Une
déconnexion invalide le résultat en attente. Ce module pur n'accorde aucun droit
et ne fait aucun appel réseau. L'archive contient des informations métier et doit
être partagée délibérément ; le chiffrement de la base locale ne s'étend pas au ZIP.

## Contenu

| Fichier | Contenu |
| --- | --- |
| `manifest.json` | Format, ID, date UTC, périmètre, versions, tailles et SHA-256 |
| `snapshot.json` | Source complète : chantier, parcelles, objets, mesures, historique, réceptions, événements, références photos et soldes |
| `ledger.csv` | Événements immuables, corrections, dates, auteurs, unités et conventions |
| `balances.csv` | Solde local par objet/unité/convention, valeur constatée à cette date et écart |
| `receipts.csv` | Une ligne par réception/version/ligne ; référence exacte à la mesure et à l'entrée physique |
| `observations.csv` | Mesures d'origine et corrections, JSON canonique préservé et empreinte |
| `parcels.csv` | Références et sources des parcelles du chantier |
| `README.txt` | Conventions de lecture pour le destinataire |

Les photos restent des références (identifiant, cible, type, taille, légende,
SHA-256). Le format 1 ne transporte pas leurs octets. Les champs de création et
de modification des dépôts/objets ainsi que l'historique des commandes bois
restent dans le JSON.

Les nombres sont des chaînes décimales exactes. Une valeur inconnue est `null`
dans les soldes JSON et possède une colonne `*_known=false` dans les CSV.
Une mesure brute incomplète conserve ses champs inconnus. Aucun volume n'est
converti en quantité payable, aucune unité ni convention n'est fusionnée.

Les CSV sont UTF-8 avec BOM, séparateur `;`, cellules entre guillemets et CRLF.
Les préfixes susceptibles d'être interprétés comme formules de tableur sont
protégés par une apostrophe. Les textes d'origine restent inchangés dans le JSON.
Les colonnes numériques doivent être importées comme texte pour éviter les
arrondis du tableur. `receipts.csv` contient l'historique : additionner toutes ses
versions compterait plusieurs fois le même bois.

## Vérifications et limites

Le lecteur accepte uniquement les huit noms ci-dessus, une seule fois chacun.
Il borne les octets compressés et réellement décompressés (par défaut 16 Mio par
fichier et 32 Mio de contenu total), refuse chemins, dossiers, fichiers ajoutés
et doublons. Il vérifie le JSON déterministe, les empreintes, les références,
les lignées de correction et les sources des réceptions.

Avant la construction des fichiers, le producteur sérialise l'instantané vers
un simple compteur, sans conserver de buffer. Il refuse au-delà de 8 Mio de JSON
(ou du quart de la limite totale configurée, si inférieur). Cette borne
conservatrice réserve de la mémoire pour les projections CSV et les chaînes
intermédiaires sur téléphone ; elle peut refuser un dossier qui tiendrait dans
32 Mio une fois compressé. Le contrôle final des tailles reste appliqué.
Cette protection ne borne pas le chargement préalable de l'instantané Room :
les très grands dossiers nécessiteront un export paginé depuis la base.

Les CSV sont reconstruits à partir du JSON puis comparés aux fichiers reçus.
Les soldes sont recalculés avec le même moteur métier `FieldBalance`, à la date
de capture, sans fusion d'unités/conventions. C'est une relecture indépendante
des fichiers, pas un second moteur mathématique indépendant. Une remesure et
un constat du restant ne créent aucun crédit. Une divergence fait échouer la
vérification ; le module ne répare et n'importe aucune donnée.

Les empreintes ne sont pas une signature et ne prouvent ni l'identité de
l'émetteur ni l'intégrité face à quelqu'un qui reconstruit entièrement une
archive cohérente. Ce format n'est pas le contrat d'échange FORLOG ; il ne
constitue ni une réception commerciale, ni un accusé de synchronisation.
