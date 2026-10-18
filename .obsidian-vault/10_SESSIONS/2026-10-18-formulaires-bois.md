---
date: 2026-10-18
project: GeoSylva
scope: wood-manual-forms
tags: [session, wood, android, persistence]
---

# Formulaires manuels, persistance et suivi des bois

## Demande et décision

Le fondateur demande des formulaires manuels réellement enregistrables, puis
précise que le chantier sera lié à plusieurs parcelles possibles (ONF/cadastre/autres)
et que les passages incomplets du grumier et contrôles seront saisis progressivement,
par date. La dernière précision fait foi et prépare la tranche suivante ; elle n'est
pas présentée comme déjà codée.

## Code réalisé

- C02 : migration additive Room 43 → 44, objets/observations/sélections/commandes ;
  contenu brut et empreinte, historique immuable, reçus idempotents, contrôle de révision.
- Garde d'accès et de purge partagée avec les dépôts ; contrôle de session et de
  propriétaire ; effacement transactionnel dans l'ordre des dépendances puis fichiers.
- C03 : formulaires pile/grume, identification, mesure partielle, date effective,
  correction avec motif, référence explicitement choisie et historique consultable.
- C04 individuel : raccord aux noyaux géométriques existants ; saisies incompatibles
  conservées et signalées. Pas de conversion apparente/solide ou quantité payable implicite.
- Material 3, ressources FR/EN, navigation liée au métier sans accorder de droits,
  bouton accessible avec clavier et grandes polices. Menus et dates suivent l'écran.

Pas de dépendance ni configuration externe nouvelle. Le skill d'onboarding avait été
appliqué à l'environnement ; cette tranche utilise la chaîne de build existante.

## Qualification

Build debug et tests Android compilés, APK interne `com.forestry.counter.preview`
installé sur le simulateur existant. **1 776 tests JVM / 121 suites** sans échec,
erreur ou ignoré ; **36 tests Android distincts** réussis sur plusieurs exécutions.

Les preuves couvrent notamment migration conservatrice, relecture après fermeture,
SQLCipher (migration puis relevés/sélection/purge), erreurs de révision et d'identité,
idempotence, correction, purge concurrente, formulaire partiel et police 200 %.
Les quatre tests UI de formulaires passent dans le dernier lancement commun.

Incidents consignés : signature d'initialisation JUnit corrigée ; choix affichés
en anglais dans un écran français corrigés à la source ; un délai initial de sauvegarde
partielle dépassé puis scénario identique réussi deux fois sans assouplir ses assertions
ou son délai. La cause initiale du délai n'est pas établie. Données et bases de TEST
dédiées ; aucune réinitialisation des données de l'utilisateur.

## Preuves et reprise

- `/workspace/outputs/2026-10-18/wood-forms/VALIDATION.json`
- Journaux build/instrumentation, XML JVM et captures dans ce même dossier.
- `GeoSylva-sources-reprises-2026-10-18.zip` et `SOURCE_ARCHIVE_PROOF.json` :
  sauvegarde locale des sources avec manifeste, CRC et SHA-256 vérifiés.
- Les 25 schémas Room antérieurs sont identiques à ceux du lot C01.
- Branche `work`, base Git `c59ba7cccd3b5a69ae81e5007bb7d5f263aeacb7` ; nombreux
  changements restaurés antérieurs préservés. Aucun commit/push ni sauvegarde distante.

## Limites et prochain raccord

Les brouillons restent en mémoire tant qu'ils ne sont pas enregistrés. Ce lot ne
livre ni chantier/parcelles, ni réception contractuelle, ni stock/enlèvement, ni
export de bois, ni photos, ni synchronisation des nouveaux objets ou connecteur FORLOG.

Prochaine tranche : dossier chantier, liens explicites vers parcelles et bois,
archives et conservation des objets existants « à rattacher ». Puis journal daté
des enlèvements et constats du restant, corrections justifiées, récapitulatif et
exports vérifiables. Voir les diagrammes mis à jour et
`docs/CHANTIERS_PARCELLES_SUIVI_DATE_BOIS_2026-10-18.md`.
