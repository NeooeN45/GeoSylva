---
date: 2026-10-18
project: GeoSylva
tags: [session, android, chantiers, carnet, gestion-forestiere]
---

# Carnet terrain commun et chantiers

Le fondateur demande d’implémenter les améliorations terrain et de les appliquer à Gestion forestière lorsqu’elles répondent au même besoin. Cette décision complète le cadrage du 18 octobre. Le remplacement possible du mobile FORLOG demeure une orientation, pas une migration convenue.

Implémentation : Room 45 additive, carnets forêt/parcelle/placette et chantier/dépôt/bois, brouillons durables, photos originales chiffrées, notes et accès, états des lieux et incidents, activité et matériel, qualité observée des bois. Les chantiers portent plusieurs références de parcelles, des liens explicites aux dépôts et bois et un archivage réversible. Piles/grumes : saisie en série, recherche, mesures et mouvements datés, contrôles du reste avec écart sans ajustement automatique.

Les contrôles d’accès/purge et les transactions existent dans le repository. Le métier choisi ne donne pas de droit sur les données. Missions reste réservé à Entreprise et indisponible sans adaptateur de droits vérifié. Aucun calcul géométrique n’alimente automatiquement le stock ou une quantité commerciale.

Lire [la portée et les diagrammes](../../docs/CARNET_TERRAIN_CHANTIERS_2026-10-18.md) et [la validation](../../docs/VALIDATION_CARNET_CHANTIERS_2026-10-18.md). Les preuves et incidents des tests sont conservés. État vérifié : 1 788 tests JVM et 37 scénarios Android distincts passés au fil des reprises. Compilation et Lint réussis après correction de 31 erreurs antérieures au lot ; 983 avertissements restent documentés. Aucun contrôle Lint désactivé.

Livraison locale dans `/workspace/outputs/2026-10-18/field-tools/`, avec APK TEST, captures, parcours de recette et archive/manifeste de sources. Préserver aussi les archives antérieures : plusieurs changements restaurés non commités précèdent ce travail. Aucun reset, force-push, nettoyage ou effacement des bases utilisateur effectué.

Suites : recette du fondateur sur téléphone, exports explicites des nouveaux événements/photos, réception contractuelle et bases commerciales distinctes, puis synchronisation avec contrat et acquittement FORLOG validés. Les imports machine et le cubage photographique demeurent des chantiers ultérieurs.
