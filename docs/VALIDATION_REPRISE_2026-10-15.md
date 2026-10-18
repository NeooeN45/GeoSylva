# Validation de la reprise — 15 octobre 2026

## Résultats exécutés sur cette base

| Vérification | Résultat |
| --- | --- |
| Compilation Android complète restaurée | Réussie (`assembleDebug`, 4 min 39 s) |
| Variante séparée GeoSylva Test | Application et APK instrumentation compilés, installés sur le simulateur |
| Tests JVM de l’application | **1 745 réussis**, zéro échec/erreur/ignoré ; 117 suites XML recomptées |
| Noyaux métier isolés | **106 réussis**, déjà inclus dans les 1 745 ci-dessus |
| Migration Room 42→43 | 1 test natif réussi : lignes anciennes et snapshots opaques conservés |
| Repository des dépôts | 11 tests natifs réussis : stockage, identité, révisions, archivage et lectures |
| Interface des dépôts | 3 tests natifs réussis : création/relecture, coordonnées/archivage, grands caractères |
| Schéma Room | 43 entités antérieures inchangées ; seul ajout `wood_depots` |
| Contrôle de différences Git | `git diff --check` sans erreur |

Deux tests JVM partiellement retrouvés ont été reconstitués : 55 scénarios historiques conservés et helpers manquants recréés/revus. Une erreur de constructeur dans un helper a été corrigée avant la dernière compilation ; les résultats ci-dessus proviennent de cette dernière exécution.

La première exécution native avait 13 réussites et 2 échecs UI. Une fenêtre Android « System UI isn't responding » prenait le focus. Après fermeture de cette fenêtre système, les trois tests UI ont été relancés : **3/3 réussis**, sans modification du code. Le total de **15 scénarios natifs distincts réussis** tient compte de cette reprise ; ce n’est pas une première exécution intégralement verte.

## APK de contrôle local

- Fichier : `/workspace/outputs/2026-10-15/GeoSylva-3.0.0-test-reprise-2026-10-15.apk`.
- Identifiant : `com.forestry.counter.preview`, séparé de l’application principale.
- Version : `3.0.0-test`, versionCode 11.
- Taille : 114678605 octets.
- SHA-256 : `e4a81963ea2feeb1e4b2e51a0454a9178cc4e3675509a655adb285255333dd3f`.
- GSIE, clé publique des packs distants et client Google : non configurés explicitement pour ce contrôle hors ligne. Ce fichier n’est pas la démonstration complète de réception/stock/export prévue pour mercredi.

Aucune base de l’application principale n’a été effacée. Les tests natifs utilisent des bases nommées TEST ou en mémoire. Les captures de `/workspace/outputs/2026-10-15/android/` montrent des données synthétiques marquées TEST et les composants de production restaurés, pas une maquette.

## Limites et étape suivante

Les tests Room ne prouvent pas le chemin de migration chiffrée SQLCipher. Ni Google, ni un serveur public GSIE, ni le connecteur FORLOG ne sont qualifiés ici. L’historique des observations de bois et la politique métier sont testés comme noyaux purs ; leur raccord aux transactions Room et à la navigation reste à développer.

L’étape suivante est décrite dans [le plan UX](PARCOURS_UX_DEMO_21_OCTOBRE_2026.md) : deux accueils/Explorer adaptés, accès Missions conforme, puis objets bois, mesures persistées, réception et exports. Les décisions scientifiques et les conventions commerciales restent explicites.

## Traçabilité et conservation

Les journaux, preuves de SHA historiques et sauvegardes préalables restent dans `/workspace/.onboarding/history-recovery-2026-10-15/`. La preuve de cette exécution est `/workspace/outputs/2026-10-15/VALIDATION_2026-10-15.json`.

Une archive des sources actuelles et son manifeste sont produits dans `/workspace/outputs/2026-10-15/`. Elle distingue les sources historiques et les tests reconstruits. **Cette archive locale ne constitue pas un push GitHub.** Conserver ce point de reprise dans l’environnement et sa sauvegarde avant remplacement du workspace.
