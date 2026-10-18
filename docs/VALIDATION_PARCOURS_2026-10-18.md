# Navigation métier — validation du 18 octobre 2026

## Périmètre du lot

Adaptation N01/N02 du socle restauré le 15 octobre. Le nouvel accueil **Chantiers et bois** et Explorer utilisent la même politique de navigation. Conducteur de travaux, exploitation et commerce du bois accèdent aux dépôts, à la carte et aux packs ; le parcours gestion conserve ses entrées forestières raccordées. Un métier inconnu demande un choix explicite. Changer de métier ne migre, ne supprime et ne réaffecte aucun relevé.

Les catégories sans écran raccordé sont retirées d’Explorer. Missions est absente de la barre principale, du mini-menu, de la visite guidée et du détail de projet hérité ; une route Missions restaurée affiche une indisponibilité. La route des dépôts vérifie sa présence dans le parcours avant de construire son écran. Cette adaptation de navigation **ne remplace pas les permissions des repositories et du serveur**.

Le HomeViewModel du parcours gestion est maintenant conservé par le cycle de vie, au lieu d’être reconstruit à chaque recomposition. Les textes ajoutés sont externalisés en français et en anglais. Les cartes utilisent une hauteur libre et des listes défilables.

## Données et limites

- Schéma Room **43 inchangé** par ce lot, aucune migration ni modification de formule.
- Données utilisateur du simulateur conservées ; les tests de dépôt utilisent leur base Room isolée.
- Les tests d’entrée utilisent les composants de production et des callbacks observables. Ils ne prouvent pas à eux seuls un parcours complet de bout en bout, la configuration Google, les abonnements ou un échange FORLOG.
- La carte de consultation existe ; le guidage forestier spécialisé reste à développer.
- Les formulaires de piles/grumes, leur persistance, les exports bois et les mouvements de stock restent les prochaines tranches. Aucun stock ou tarif fictif n’est affiché par ce lot.
- Missions reste réservée à l’offre Entreprise ; son activation attend le module et l’adaptateur de droits authentifiés.

## Vérifications

Compilation avec le runner d’environnement existant, JDK 17, Gradle 8.2, caches conservés, mode hors ligne et deux workers. La variante de contrôle `com.forestry.counter.preview` est isolée, sans configuration de connexion serveur ou Google.

**JVM : 1 754 tests, 118 suites, zéro échec, erreur ou test ignoré.** Les neuf ajouts couvrent la navigation métier et la visite guidée filtrée. Les résultats se trouvent dans `app/build/test-results/testDebugUnitTest/`.

**Android : huit tests distincts réussis après reprise.** Le premier passage de huit tests a donné six succès et deux échecs : recherche d’un élément de LazyColumn non encore composé dans le nouveau test grande police, puis dépassement de cinq secondes lors de l’enregistrement dans un test de dépôt existant. Le premier test a été corrigé pour faire défiler le conteneur avant de rechercher et cliquer la carte, en conservant ses assertions. Le délai du test dépôt n’a pas été modifié.

Le second passage a réexécuté les cinq tests d’entrée et le test de création/relecture du dépôt : **6/6 réussis en 76,125 secondes**. Le dépassement du délai de sauvegarde ne s’est pas reproduit ; sa cause n’est pas établie. Les deux autres tests dépôt, inchangés, avaient réussi au premier passage : coordonnées décimales/confirmation d’archivage et formulaire grande police. La dernière compilation des APK app/test est réussie. Les quatre captures finales ont été récupérées et inspectées (accueil, Explorer gestion, Explorer bois et texte à 200 %).

L’installation ADB en streaming est restée bloquée sans mise à jour du paquet ; reprise réussie avec `--no-streaming -r`, sans effacement de données. Les premières captures ont aussi permis de corriger l’alignement des flèches dans les cartes.

Preuves de travail : `/workspace/.onboarding/journeys-2026-10-18/`. Captures et bilan JSON : `/workspace/outputs/2026-10-18/`. Les anciens résultats du 15 octobre sont conservés séparément. La sauvegarde des sources du 18 possède un manifeste et des empreintes vérifiées ; elle est locale, sans commit ni push GitHub de ce lot.

## Suite ordonnée

Voir les [diagrammes de démonstration](DIAGRAMMES_DEMONSTRATION_21_OCTOBRE_2026.md). Compléter les contrats C01 puis la persistance C02 avant de présenter les formulaires de réception comme fonctionnels. Chaque remesure conserve l’identité physique du bois ; chaque correction garde son original. Les quantités commerciales, mouvements et validations restent distincts du volume géométrique provisoire.
