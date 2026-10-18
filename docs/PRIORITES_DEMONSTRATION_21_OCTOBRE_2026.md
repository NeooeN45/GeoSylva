# Priorités de la démonstration du mercredi 21 octobre 2026

Décisions confirmées par le fondateur le 15 octobre. Ce document complète le
cadrage BOIS-01 et les deux parcours ; les décisions récentes priment sur les
anciens documents. Les étapes ci-dessous sont des objectifs à vérifier, pas des
fonctionnalités déclarées livrées.

## Ordre de travail

1. **Terminer la reprise en cours** : sources réunies, compilation Android,
   vérification de la base, des comptes et des protections existantes. Préserver
   les anciennes données et les archives ; ne pas remettre le Tableau de bord
   abandonné ou les photos métier supprimées.
2. **Adapter l'UX/UI des parcours** : Gestion forestière pour les techniciens ;
   Chantiers et valorisation pour les métiers du bois. Simplifier Explorer et les
   enchaînements, retirer les outils inutiles, conserver les commandes terrain
   accessibles et montrer les changements importants sur Android. Missions reste
   exclusivement Entreprise, sous droits réels et uniquement une fois le module
   effectivement disponible.
3. **Rendre la réception locale utilisable** : dépôt → pile/grume identifiée →
   mesures brutes et provenance → correction historisée → choix du relevé courant.
   Relier les nouveaux noyaux à Room et aux formulaires ; une projection Kotlin
   testée ne constitue pas encore une fonction mobile terminée.
4. **Fiabiliser les calculs embarqués** : dimensions, unités, protocoles et
   conventions explicites ; données manquantes visibles ; calcul géométrique
   provisoire distinct de toute quantité payable. Ne pas choisir un tarif de
   cubage par la seule région. Les données régionales suivent le lieu du relevé,
   pas le lieu où l'utilisateur consulte son dossier.
5. **Mouvements et exports réels** : inventaire, enlèvement et correction distincts,
   aucun stock créé automatiquement par une remesure ; exports cohérents avec les
   données persistées et versions choisies. Réception, stocks et exports sont la
   priorité Forlog ; son connecteur sera développé ensuite.
6. **Recette et APK TEST** : scénarios synthétiques identifiés, sauvegarde et
   relecture, changement de compte, saisies incomplètes, correction, archivage,
   reprise et export. Vérifier l'application Android réelle avant de qualifier
   une démonstration ou de remettre un APK.

## GSIE mobile et IA future

Le backend mobile, les algorithmes embarqués et les règles de cohérence sont un
chantier central. La future IA doit exploiter des données, méthodes et résultats
traçables. Sa conception continue, mais elle ne remplace pas la qualification des
calculs déterministes ni les fonctions de réception nécessaires pour mercredi.
Les fonctionnalités scientifiques non validées et la connexion Forlog ne seront
pas présentées comme opérationnelles.

## Hébergement des premiers comptes Quintessences

Préparer ensemble une relance à **Pierre Pluviaud, Datacampus** et comparer une
offre adaptée avec OVHcloud, puis éventuellement une autre offre européenne.
Le premier besoin est l'identité et les comptes GSIE ; il ne demande pas de GPU.

Comparer le coût total : VPS, stockage, sauvegardes séparées et restauration,
SMTP transactionnel, sécurité, disponibilité et administration. Distinguer
paiement mensuel, engagement, promotion, HT/TTC et coût de renouvellement.
La configuration minimale doit être compatible avec le serveur actuel, puis
validée sous charge ; elle ne constitue pas une promesse de capacité européenne.

Un comparatif sourcé et un brouillon de relance sont préparés sous
[Hébergement pilote GSIE](HEBERGEMENT_PILOTE_GSIE_2026-10-15.md).
Le découpage des écrans figure dans [le plan UX](PARCOURS_UX_DEMO_21_OCTOBRE_2026.md).
Aucun fournisseur n'a été contacté, aucun contrat ni VPS n'a été commandé.
