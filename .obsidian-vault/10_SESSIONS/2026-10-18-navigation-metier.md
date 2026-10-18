---
date: 2026-10-18
project: GeoSylva
tags: [session, parcours, android, demonstration]
---

# Navigation métier avant la démonstration du 21 octobre

Instruction courante : préparer une application pratique et fiable pour mercredi, avec des diagrammes lisibles des ajouts. Priorité au parcours local démontrable, sans présenter comme disponibles les futurs échanges FORLOG, droits Entreprise ou quantités commerciales.

Le raccord N01/N02 ajoute un accueil Chantiers et bois, filtre Explorer et les deux barres de navigation, adapte la visite guidée et garde les destinations indisponibles fermées. Les outils non raccordés ne sont plus affichés. Les préférences de métier ne confèrent aucun droit sur les données. La base restaurée et les données du simulateur sont conservées.

Validation : compilation app/test ; 1 754 tests JVM ; huit tests Android distincts réussis après correction du défilement d’un test et reprise d’un délai de sauvegarde non reproduit. Pas de modification de ce délai ni des assertions du test dépôt. Quatre captures des composants de production sont disponibles. Cette recette n’est pas une qualification de bout en bout de la réception des bois ou de la connexion serveur.

- [Diagrammes et ordre des trois jours](../../docs/DIAGRAMMES_DEMONSTRATION_21_OCTOBRE_2026.md)
- [Résultats et limites](../../docs/VALIDATION_PARCOURS_2026-10-18.md)
- Preuves et sources archivées : `/workspace/outputs/2026-10-18/`.
- Avant les modifications : `/workspace/.onboarding/journeys-2026-10-18/before.zip`.
- Sauvegarde locale, pas de commit/push GitHub effectué.

## Prochaines actions

1. Compléter les contrats C01 (bruts, sérialisation, identité et empreinte).
2. C02 : persister objets et observations avec contrôles de portée, révisions et commandes ; qualifier la migration additive depuis Room43.
3. C03/C04 : pages pile/grume, nouvelle mesure et correction explicite, calcul géométrique et couverture des résultats.
4. C05–C08 : exports réels, réouverture des fichiers, recette de scénario TEST identifié puis APK après revue visuelle. Le connecteur FORLOG et les opérations de stock/commerce gardent leur qualification propre.
