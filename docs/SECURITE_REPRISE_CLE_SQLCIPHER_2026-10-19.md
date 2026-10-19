# Clé SQLCipher — correction et reprise des bases historiques

Date : 19 octobre 2026. Lot prioritaire avant distribution d'un nouvel APK.
Périmètre : ouverture locale SQLCipher 4.5.4, indépendant de la migration Room 46.

## Défaut corrigé

L'Application effaçait le tableau `dbKey` immédiatement après `Room.build()`.
Room ouvre la base plus tard. La `SupportFactory` SQLCipher 4.5.4 conservait la même
référence de tableau : la première ouverture pouvait donc utiliser les 32 octets
nuls au lieu de la clé aléatoire protégée par Android Keystore.

La présence de SQLCipher ne suffisait pas à établir que la clé utilisée était
secrète. Les anciens tests créaient une factory directement avec une clé TEST
et ne reproduisaient pas la durée de vie du tableau dans l'Application.

`SecureSqlCipherOpenHelperFactory` possède désormais sa propre copie jusqu'à la
première ouverture effective. La copie appelante est toujours effacée dans un
`finally`. L'ouverture réelle efface à son tour la copie privée ; fermer le helper
la détruit aussi. Aucun secret n'est placé dans une requête textuelle ou un log.

Le stockage de la clé enveloppée par Keystore utilise un `commit()` vérifié avant
de fournir la clé au constructeur de base. Les métadonnées partielles sont refusées,
et déchiffrer une clé existante n'autorise jamais la recréation d'une clé maîtresse
manquante. Un verrou commun protège les initialisations simultanées dans le processus.

## Reprise non destructive

`LegacySqlCipherRecovery` s'exécute au premier accès Room, sur son thread d'ouverture,
et non pendant la construction du graphe de dépendances dans `Application.onCreate`.

1. Le seul nom autorisé est `forestry_counter.db`, dans le répertoire privé Android.
2. Une base absente est créée normalement avec la vraie clé.
3. Une base existante lisible avec la vraie clé reste en place.
4. Sinon, la reprise historique est admise uniquement si la clé de 32 octets nuls
   ouvre une base dont la version et l'identité Room figurent dans la liste connue.
   Les identités proviennent des schémas exportés du dépôt. Les versions inconnues,
   bases non reconnues, fichiers vides et corruptions échouent en conservant les fichiers.
5. Après contrôle d'intégrité, les transactions WAL sont intégrées et le journal
   est fermé. Un verrou de démarrage et la transaction SQL sérialisent la reprise.
6. `sqlcipher_export` crée une nouvelle copie chiffrée avec la vraie clé, transmise
   comme paramètre binaire. Ni conversion UTF-8 des octets ni clé hexadécimale brute
   ne remplacent implicitement la passphrase existante.
7. La copie conserve `user_version`, `application_id`, le schéma et l'identité Room.
   La relecture compare les empreintes de toutes les lignes typées (y compris BLOB)
   et le schéma logique ; les tables internes FTS sont remplacées par la vérification
   de leurs contenus logiques. L'intégrité SQLite est vérifiée.
8. Après synchronisation du fichier, la copie validée remplace atomiquement l'original
   sur le même système de fichiers. Il n'existe aucun remplacement non atomique de secours.
9. La réouverture avec la vraie clé est contrôlée. Le résultat de récupération est
   conservé comme état local, sans clé ni donnée métier. Un état pending permet de
   terminer cette trace après interruption immédiatement après le remplacement.

Le gestionnaire de corruption SQLCipher utilisé par les sondes et par Room échoue
sans supprimer de base. La reprise ne modifie pas la clé sauvegardée dans Keystore.
Elle ne transmet aucune donnée sur le réseau et ne crée aucune réception commerciale.

## Interruptions et limites

- Avant le remplacement atomique, la base d'origine reste disponible. Une reprise
  ultérieure repart de cet original ; après le remplacement, la vraie clé fonctionne.
- Un processus tué peut laisser une copie temporaire chiffrée avec la vraie clé dans
  le répertoire privé. Elle n'est jamais promue sans vérification et n'écrase aucun
  autre fichier. Un échec ordinaire retire seulement les temporaires de sa propre opération.
- La reprise demande l'espace libre nécessaire à une copie complète. Un manque
  d'espace provoque un échec, jamais une réinitialisation.
- Le contrôle est borné à un million de lignes par table. Au-delà, le fichier est
  conservé pour une reprise supervisée ; pas d'approximation des comparaisons.
- Seules les identités historiques effectivement disponibles sont admises :
  30–36 et 39–45. Les versions sans schéma vérifiable ne sont pas devinées.
- Cette correction sécurise les ouvertures futures. Elle ne permet pas d'affirmer
  qu'une ancienne copie exportée ou extraite de l'appareil n'a jamais été exposée.
- Une clé Keystore perdue pour une base déjà correctement chiffrée ne peut pas être
  reconstruite. Le correctif conserve cette base et échoue explicitement.

## Vérification instrumentée

Classe : `com.forestry.counter.security.DatabaseKeyRecoveryTest`.
Tous les fichiers et valeurs sont synthétiques, dans `TEST-key-recovery.db` ; le
nom de production est remappé par un ContextWrapper uniquement dans les tests.

Scénarios couverts par les tests écrits :

- reproduction du défaut avec la factory historique et ouverture différée ;
- copie propre de la clé, premier démarrage et réouverture avec la vraie clé ;
- reprise d'une base Room 45 avec données, stock/enlèvement, photo BLOB et index FTS ;
- migration Room courante après reprise, sans perte des données ;
- mauvaise clé : fichier chiffré intact ;
- base étrangère, identité Room inconnue et fichier SQLite en clair : préservés ;
- interruption avant remplacement : original lisible, reprise possible ;
- interruption après remplacement : vraie clé lisible à la prochaine ouverture.

`DatabaseEncryptionKeyPersistenceTest` couvre aussi la persistance durable et la
réutilisation de la même clé, le refus des métadonnées partielles et les échecs de
commit. Les tests isolent leurs préférences et ne suppriment aucune clé Keystore.

Exécution du 19 octobre sur Android API 28 : **12 tests réussis**, aucune erreur,
dans `DatabaseKeyRecoveryTest` et `DatabaseEncryptionKeyPersistenceTest`.
Preuve : `/workspace/.onboarding/receipt-delivery-2026-10-19/android-20261019T102948Z.log`.
Ces tests utilisent le package TEST distinct de l'ancienne application de démonstration.
