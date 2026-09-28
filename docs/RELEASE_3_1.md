# GeoSylva V3.1 — préparation de publication

## Version et priorité

Instruction de Camille Perraudeau du 2026-09-28 : **V3.0 est la refonte actuelle**.
**V3.1 sera la version fonctionnelle publiée sur Google Play avec abonnement
Quintessences**. Ce document prépare la livraison ; il ne certifie pas une
publication ou un abonnement déjà disponible.

Ordre écosystème : GeoSylva, GSIE, Artemis, Ignis, Hub, conversation
environnementale, autres applications mobiles, puis drones avec IA.
Le serveur nécessaire à GeoSylva est développé pendant son jalon V3.1.

## Contrat commun avec le serveur

Le cadrage serveur est maintenu dans
[Quintessences — développement](https://github.com/NeooeN45/Quintessences/tree/docs/trajectoire-simulation-territoriale/GSIE/API/docs/development).
Les références sont `RELEASE_GATES.md`, `BASELINE_GEOSYLVA.md`,
`CONTRACT_HANDOFF.md` et les lots B00–B09 applicables à GeoSylva.
Les contrats de routes sont produits par FastAPI ; ne pas inventer une
route serveur à partir d'une maquette Android.

## Portes de sortie

- Parcours terrain hors ligne, calculs et exports du périmètre qualifiés.
- Sélection d'une zone et conservation de sa provenance, précision et date.
- Compte Quintessences, récupération et isolation entre utilisateurs.
- Synchronisation des données métier promises, avec conflits et reprise.
- Restauration après réinstallation des objets et de leurs versions.
- Analyses raccordées au serveur, avec sources et limites affichées.
- Abonnement : achat, récupération, droits serveur et cycle de vie qualifiés.
- Migration depuis V3.0, build signé, piste de test Play et exploitation serveur.

Choix du Fondateur du 2026-09-28 : **terrain hors ligne gratuit ; synchronisation
et analyses avec abonnement Quintessences**. Le prix et les modalités de
récupération des données déjà stockées après expiration restent à décider.
Ne pas effacer les
relevés à l'expiration de l'abonnement. La mention « synchronisé » ne vaut
sauvegarde complète que si tous les objets promis sont effectivement persistés.

## État des copies examiné le 2026-09-28

Le checkout distant utilisé pour ce document part de `c59ba7c`.
La copie de travail Android inspectée part de `b795b2e` avec des modifications
locales importantes. Les travaux de cette copie doivent être inventoriés et
réconciliés avant publication du code ; ce document ne les remplace pas.
La comparaison ciblée trouve 29 noms de champs de parcelle alignés avec
le serveur et 38 tables locales. Types et restauration restent à qualifier.
Le client d'analyse local vise des routes non retrouvées côté serveur.

## Prochain travail Android

Établir la matrice objet local → contrat serveur → restauration. Identifier
les données utilisateur à conserver et les caches/référentiels rechargeables.
Coordonner B02/B03 avec Codex avant de changer Room ou les DTO.
Claude reprend ensuite les écrans de compte, zone, états de synchronisation,
restauration, rapport et abonnement selon les contrats acceptés.
