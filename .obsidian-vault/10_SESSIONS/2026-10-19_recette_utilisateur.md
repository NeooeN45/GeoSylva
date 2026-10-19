# Recette utilisateur Android — 19 octobre 2026

Demande : tester GeoSylva comme un utilisateur, avec priorité au parcours
Conducteur de travaux pour la démonstration du mercredi 21 octobre.

Le bilan et les limites sont dans `docs/RECETTE_UTILISATEUR_2026-10-19.md`.
Le parcours manuel utilise uniquement taps, clavier, retours, défilements et
sélecteur Android, sur des données explicitement TEST. Les fixtures automatisées
sont distinctes ; elles ne remplacent pas cette vérification par l’interface.

## Résultats métier

Chantier avec référence de parcelle, dépôt bord de route, pile de chêne et mesure
10 × 5 × 2 m. Réception de 100 m³ apparents, deux sorties de 25 et 30, attendu 45,
constat 42 et écart −3. Pas de correction automatique ni de quantité commerciale
implicite. Un seul crédit initial. ZIP réellement enregistré dans Downloads et
relu indépendamment. Archivage et reprise conservés. Le changement de métier
adapte Accueil/Explorer ; Missions reste absent hors Entreprise.

## Correctifs et périmètre

Clés Compose stables pour conserver le focus, message du nom manquant, menu
flottant dégagé des formulaires terrain, retour en haut à l’ouverture/au retour
du carnet et visibilité des nouvelles erreurs. Cinq notices/crédits étaient
ignorés par `*.txt` : conservation exacte et exceptions Git ciblées.

La fixture ReceiptFlowUiTest a aussi été isolée : une base UUID par test,
destruction Activity/Compose puis annulation et attente des ViewModels avant
fermeture/suppression de Room. Les assertions et les règles métier sont inchangées.

## Intégration sans écraser le travail simultané

Checkout de recette : `/workspace/GeoSylva-user-tests`, branche
`fix/recette-utilisateur-2026-10-19`, base `d42240dcbac9b9f51441237f1ca0ae1de7208d81`.
Le dépôt `/workspace/GeoSylva` contient en parallèle un lot Maps d’une autre
session. Ses fichiers ne sont pas inclus dans cette recette. Les neuf fichiers
UX initiaux existent aussi dans le working tree partagé ; les comparer avant
fusion pour éviter doublons ou écrasements, surtout `MainScaffold.kt` où un hunk
Maps voisin est volontairement exclu du checkout de recette. Ne pas restaurer
aveuglément les fichiers complets du checkout isolé dans le dépôt partagé.

Les preuves et livrables locaux sont dans
`/workspace/outputs/2026-10-19/user-journey/`. Aucun push ni upload externe.
Ne pas effacer les données du package de recette
`com.forestry.counter.preview.receptions` sur `emulator-5554` : le chantier manuel
y est conservé. L’autre simulateur et les autres packages sont hors périmètre.

## Suite

Simplifier les formulaires et le rattachement chantier/dépôt/bois ; clarifier les
liens directs dans les compteurs et l’affichage des versions de brouillon.
Qualifier ensuite sur téléphone la fluidité, le clavier, les capteurs et photos.
Le Google/GSIE réel et le connecteur FORLOG ne sont pas validés par cette recette.

## Validation finale

1 822 tests JVM réussis. Cinq tests FieldToolsUiTest réussis, puis quatre tests
ReceiptFlowUiTest réussis après correction de la fixture : neuf scénarios distincts.
Lint : zéro erreur et 997 avertissements. APK utilisateur inchangé après correction
de la seule fixture. Empreinte :
`071cdb412e84956b0dbf77adb5eea8cc4fe3a2c167d042ec43b9cde90ba3a621`.
Les preuves conservent l’échec intermédiaire et la relance réussie.
