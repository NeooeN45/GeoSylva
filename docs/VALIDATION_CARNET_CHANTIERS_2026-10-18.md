# Validation — carnet terrain et chantiers

Lot du 18 octobre 2026, dans la reprise de GeoSylva pour la démonstration du 21 octobre. La portée fonctionnelle et les diagrammes sont dans [le cadrage du lot](CARNET_TERRAIN_CHANTIERS_2026-10-18.md).

## Vérifications effectuées

- Compilation des APK de prévisualisation et de tests : réussie.
- Tests JVM : **1 788 tests, zéro échec, erreur ou test ignoré**.
- Android : **37 tests distincts réussis sur plusieurs exécutions ciblées**, sur le simulateur Android 9 / API 28, x86_64. Ce résultat ne prétend pas couvrir tous les appareils Android.
- Lint : **réussi, zéro erreur**, après correction de 31 erreurs antérieures au lot. Il reste 983 avertissements : les 981 précédents et deux recommandations de pluriels pour les traductions ajoutées. Ils sont conservés dans le rapport ; aucun contrôle n’a été désactivé pour faire passer Lint.

| Vérification Android | Tests | Points couverts |
|---|---:|---|
| Migration 44 → 45 | 1 | Ajout des six tables, anciennes données conservées, ouverture SQLCipher |
| Repository du carnet | 9 | Solde, corrections, unités, répétition sans doublon, brouillons concurrents, changements de compte, liens et archives, photos chiffrées et purge |
| Repository des bois | 10 | Identité du bois, historique des observations, corrections, accès et stockage |
| Migration 43 → 44 puis ouverture actuelle | 2 | Conservation et compatibilité avec Room 45 |
| Entrées des parcours métier | 5 | Navigation selon le métier, indisponibilité des modules non raccordés |
| Interface du carnet et des chantiers | 3 | Brouillon forestier, écart de reste sur une pile, chantier à plusieurs parcelles puis archivage |
| Formulaires pile et grume | 7 | Saisie manuelle, relecture après réouverture, mesures incomplètes, Huber avec convention explicite, grande police/clavier, reprise de brouillon, saisie en série et conflit de révision |

Après les tests, l’activité principale de l’APK livré a été relancée et son affichage confirmé par Android. Sur ce simulateur logiciel, le premier affichage a pris environ 28,6 secondes et dépassé le délai de `am start -W` ; aucune exception AndroidRuntime n’a été relevée pendant ce lancement. La performance de démarrage doit être mesurée sur téléphone.

Les bases et valeurs de ces tests sont dédiées et portent la mention TEST. Les bases utilisateur n’ont pas été vidées pour obtenir ces résultats. Les tests de dépôt et de carnet utilisent les repositories réels ; les tests de stockage chiffré utilisent SQLCipher.

## Incidents rencontrés et traités

Le premier banc d’essai du carnet remplaçait le contexte Android pour afficher le français sans fournir le propriétaire des résultats d’activités. Le test fournit désormais ce propriétaire ; les trois scénarios passent. Le contexte normal de l’application dispose déjà de ce propriétaire.

Le simulateur a aussi rencontré une interruption native dans `libart.so` lors de l’enchaînement de suites. Il a été redémarré sans effacement des données ; le scénario de grande police a ensuite passé. Ce redémarrage ne constitue pas une preuve de compatibilité avec tous les appareils.

Deux clics d’enregistrement injectés pendant les transitions du clavier n’avaient déclenché aucune commande. Le banc d’essai attend désormais la fermeture du clavier avant ces clics ; la visibilité des actions avec clavier et grande police reste vérifiée séparément. La barre des actions de grume applique maintenant les marges du clavier au groupe de boutons entier.

Une lecture prématurée de l’arbre sémantique Compose a ensuite interrompu le test du brouillon. La transition vers le formulaire est attendue explicitement et le défilement redondant a été retiré du test. Le scénario ciblé passe. Les journaux d’échecs initiaux sont conservés avec ceux des reprises réussies.

Lint ne pouvait initialement pas démarrer hors ligne, faute de `lint-gradle:31.2.2` dans le cache. Après récupération, il a signalé 31 erreurs dans trois fichiers dont les empreintes étaient identiques à celles d’avant ce lot : constante d’ancrage cartographique, texte S % interprété comme format, traductions des réglages d’échantillonnage. La constante officielle est utilisée, les textes sans argument sont déclarés non formatés et les 29 traductions anglaises sont ajoutées. Aucun algorithme de cubage n’a été modifié par ces corrections.

## Livraison et limites

Les artefacts sont sous `/workspace/outputs/2026-10-18/field-tools/` : APK, parcours de recette, captures Android, preuves de vérification et archive des sources avec manifeste SHA-256. Les journaux détaillés d’exécution sont conservés sous `/workspace/.onboarding/field-tools-2026-10-18/`.

L’APK `com.forestry.counter.preview`, version `3.0.0-test`, s’installe à côté de la production. Il ne contient pas de configuration GSIE/Google opérationnelle. Le parcours local est accessible par « Continuer hors ligne ». La caméra physique, l’orientation des photos et la lisibilité au soleil restent à contrôler sur le téléphone.

Le carnet est local. Ses nouveaux événements et photos ne sont pas encore raccordés aux exports commerciaux, à la réception contractuelle ni à FORLOG. Les quantités physiques ne sont pas des quantités payables. Aucune estimation de volume par photographie n’est annoncée comme fonctionnelle.

Les sauvegardes des lots précédents sont conservées. La sauvegarde de ce lot est locale ; aucun commit ou push GitHub n’est revendiqué.
