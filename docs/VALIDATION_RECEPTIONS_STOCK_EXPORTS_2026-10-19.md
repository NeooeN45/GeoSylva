# Réceptions, stocks et exports — vérification du 19 octobre 2026

Cette tranche implémente le [plan de réception](PLAN_RECEPTION_STOCK_EXPORTS_2026-10-19.md)
pour la démonstration du 21 octobre. Les décisions actuelles du fondateur priment
sur les documents historiques : deux parcours distincts, usage local personnel,
Missions réservé à Entreprise, connexion FORLOG après les exports réels.

## Comportement livré par le code

- Dossier chantier organisé en bois/dépôts, réceptions, mouvements/contrôles et documents.
- Réception versionnée : brouillon durable, contrôle des sources, validation locale
  explicite. La personne qui contrôle peut être l'auteur du relevé.
- Mesure géométrique source, quantité physique déclarée et convention/version
  distinctes. Aucun volume payable déduit silencieusement.
- Crédit de stock et résultat de commande enregistrés dans une même transaction.
  Rejouer la commande ne recrédite pas le bois. La remesure reste une observation.
- Reprise de l'unité/convention du stock dans le carnet, sans recopier sa quantité.
  Plusieurs bases imposent un choix explicite. Corrections de réception protégées.
- Annulation motivée sans dépendances ; historique conservé et base corrigée à zéro.
- Instantané d'export cohérent, JSON et cinq CSV, manifeste SHA-256 et notice.
  Vérification des références, fichiers et soldes avant l'enregistrement Android.
- Contrôles de compte avant capture, pendant écriture et au retour de l'écriture.
  Tentative de suppression/vidage d'une sortie interrompue ; avertissement visible
  si le fournisseur de documents refuse son nettoyage.

Les journaux d'un dépôt partagé ne sont pas implicitement exportés avec un chantier.
Les photos sont référencées mais leurs fichiers ne sont pas joints à ce lot.

## Persistance et sécurité

Room **45 → 46** ajoute uniquement quatre tables de réception. La comparaison des
schémas générés ne trouve aucune table antérieure modifiée ou supprimée. La purge
respecte l'ordre des références et les accès sont revérifiés dans les transactions.

La [correction SQLCipher](SECURITE_REPRISE_CLE_SQLCIPHER_2026-10-19.md) conserve une
copie privée de la clé jusqu'à l'ouverture effective de Room. La clé enveloppée
est enregistrée durablement avant emploi. Les bases historiques reconnues peuvent
être reprises vers cette clé après vérification intégrale d'une copie chiffrée.
Les fichiers non reconnus ou illisibles ne sont pas supprimés.

## Vérifications exécutées

- Compilation debug et APK de tests : réussies ; sources figées pendant la compilation.
- **1 822 tests JVM**, zéro échec, zéro erreur, zéro test ignoré.
- Android Lint : zéro erreur/fatal, **997 avertissements**. Écart au bilan précédent :
  10 suggestions de pluriels, 3 ressources inutilisées et 1 suggestion orthographique
  sur le mot français « existant ». Les avertissements restent tracés, non masqués.
- **12 tests Android SQLCipher réussis**, dont reprise après interruption avant/après
  remplacement, conservation des données, clé incorrecte, métadonnées partielles et
  refus d'écriture de clé. Bases et préférences TEST isolées.
- **44 tests Android métier et migrations réussis** : réceptions, sources figées,
  idempotence, accès, crédits et dépendances, objets bois, carnet physique, migrations.
- **6 tests Android UI réussis** sur les réceptions, le dossier chantier, la préparation
  d'export et les outils terrain ; captures examinées. Total : **62 tests distincts**.
- L'un de ces tests est ensuite étendu à **l'enregistrement SAF réel** : choix de
  Téléchargements, création du ZIP, relecture par ContentResolver, contrôle du nom,
  de la taille, du chantier, du bois et des deux parcelles, puis suppression du seul
  fichier TEST. Réussi après correction d'un sélecteur de test incompatible avec
  le resource-id de DocumentsUI API 28 ; aucun changement produit pour cette reprise.
- Contrôle de secrets des 1 381 fichiers suivis/nouveaux : aucun résultat Gitleaks.

Preuves de travail : `/workspace/.onboarding/receipt-delivery-2026-10-19/`.
Compilation validée : `build-20261019T102643Z` ; clés : `android-20261019T102948Z.log`,
métier : `android-20261019T103240Z.log`, UI : `android-20261019T103520Z.log`.
SAF réel : `android-20261019T105151Z.log` ; test recompilé dans
`build-20261019T104904Z`. Le passage intermédiaire en échec est conservé sous
`android-20261019T104415Z.log`. L'APK principal reste identique par SHA-256.

Livrables sous `/workspace/outputs/2026-10-19/receipts/` : APK, guide, captures,
`VALIDATION.json`, `PREUVES_VALIDATION.zip`, `SOURCE.json` et bundle Git incrémental.
Le commit local et sa base sont inscrits dans SOURCE.json après sauvegarde.
Le dépôt GitHub étant encore public, aucune source de ce lot n'est poussée.

## APK et essai utilisateur

Le package **`com.forestry.counter.preview.receptions`**, version
**`3.0.0-test-receptions`**, est installé à côté des versions précédentes. Il est
signé pour le développement ; sa signature APK v2 a été vérifiée. Android 8 minimum.
Les architectures ARM 32/64 bits et x86 32/64 bits sont présentes.
Taille : **115 498 485 octets**. SHA-256 :
`4dec205372c51ca2c4abc51ebf7b88b998cc177a74cf2bbf60a2497a112f1d76`.
L'APK n'inclut pas de configuration active Google/GSIE/FORLOG et n'est pas une
distribution de production.

Le [guide téléphone](RECETTE_RECEPTIONS_TELEPHONE_2026-10-19.md) décrit un scénario
entièrement simulé : réception de 100 m³ apparents, enlèvements de 25 puis 30,
solde attendu de 45, constat de 42 et écart de −3. Aucune donnée client n'est utilisée.

## Limites conservées explicitement

La validation locale n'est ni une signature contractuelle ni une acceptation serveur.
Les bases achat, prestations et vente, les factures et la connexion FORLOG restent
distinctes et à développer. Les procédures natives FORLOG n'ont pas été confirmées
par cette tranche.

Après annulation, une nouvelle réception du même bois attend le futur circuit de
rectification. Il ne faut pas recréer la pile pour contourner cette limite. Un
constat du restant ne modifie pas automatiquement le stock théorique.

Les empreintes ne constituent pas une signature de confiance. Les grands historiques
nécessiteront une capture/export paginés ; des limites de taille bornent déjà la
sérialisation et la lecture des archives. La caméra, le stockage choisi par l'utilisateur
et les performances sur téléphone réel doivent encore être essayés par le fondateur.
Le lancement normal atteint le choix de métier puis la connexion avec accès hors ligne.
Il reste lent sur le simulateur logiciel sans accélération ; aucune mesure de performance
sur téléphone réel n'est déduite de ces captures.
