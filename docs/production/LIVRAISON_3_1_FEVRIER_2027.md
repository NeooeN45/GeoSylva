# Géosylva 3.1 — livraison en février 2027

Décision utilisateur du 14 novembre 2026 : terminer l’application pour février 2027.
Le jour exact et le canal de distribution restent à préciser. Cette échéance guide
le développement ; la production doit être vérifiée sur l’artefact réellement livré.

## Versions et règles de travail

- 3.0 : version de construction. 3.1 : version de production attendue.
- Préserver les développements existants et intégrer les 100 maquettes validées.
  Une source récupérée, une compilation et une interface vérifiée sont trois états distincts.
- Conserver le parcours Technicien public/privé, sauf les améliorations convenues de
  Synthèse, Diagnostics et de leurs sous-pages. Développer les autres métiers un par un.
- Réutiliser un moteur Mapbox, les pages communes et les services de données autorisées.
  Les métiers déterminent leur contexte et leurs outils ; ils ne créent pas une copie du moteur.
- Une donnée manquante ou une méthode non qualifiée doit produire un motif explicite,
  dans l’écran comme dans l’export. Aucun volume payable ou résultat par hectare implicite.
- Références terrain : France métropolitaine, Samsung S25 Ultra, une journée hors connexion.
- Commits cohérents fréquents et sauvegardes distantes vérifiées sur la branche dédiée.

## Jalons

| Période | Résultat attendu | Preuve de passage |
| --- | --- | --- |
| Novembre 2026 | Restaurer les contrats manquants, unifier Mapbox et obtenir l’APK complet | Build depuis un commit identifié, migrations testées, installation et parcours de base |
| Décembre 2026 | Intégrer les interfaces validées par parcours, fiabiliser le terrain | Comparaisons des maquettes, saisie/reprise, cartes et données pendant une journée sans réseau |
| Janvier 2027 | Terminer les parcours convenus, recette et corrections | Tests de bout en bout, revue des 100 maquettes, exports relus, candidat de production sur S25 Ultra |
| Février 2027 | Recette finale et distribution de la 3.1 | Candidat signé, mise à jour sans perte, validation utilisateur et sauvegarde restaurable |

La tranche de vente de bois et la couverture de chaque métier doivent rester explicites
dans la feuille de route métier. L’existence du stock, des réceptions et des chantiers
ne prouve pas celle des contrats de vente, de la facturation ou des services FORLOG.
Chaque ajout est estimé avant d’élargir le périmètre à livrer.

## Conditions de sortie

1. Build complet reproductible et application installable, puis variante de production signée.
2. Migrations des bases réellement utilisées, intégrité des historiques et purge cohérente.
3. Accès invalidés lors des changements de compte, droits, parents ou suppression des données.
4. Calculs activés documentés par source, version, essence, domaine et unités ; exports cohérents.
5. Journée hors connexion et reprise après fermeture/redémarrage sans perte ni duplication.
6. Parcours Technicien, chantier/bois et tranche de vente convenue recettés de bout en bout.
7. Les 100 maquettes reliées à leurs routes et états, avec captures et écarts vérifiés.
8. Essais S25 Ultra : GPS, compas, caméra, traces, permissions, batterie, stockage et mise à jour.

## Premier lot en cours

Réparer les dépendances de stockage et d’interface, raccorder les dépôts cartographiques,
les contrôleurs de traces et de téléchargement, leur purge et le service GPS Android.
Relancer ensuite le build complet et corriger ses erreurs observées.

La bibliothèque cartographique a passé 47 tests JVM avant ce lot. Ce résultat ne
constitue ni un test de l’application complète ni une validation du téléphone.
Les réparations nouvelles et les antécédents adoptés sont distingués dans `docs/recovery/`.
