---
date: 2026-10-18
project: GeoSylva
tags: [session, bois, contrats, prospection, photos]
---

# Prospection et photos cadrées, contrats bois préparés pour la persistance

Le fondateur demande d’ajouter la prospection de chantier, l’estimation du bois,
les photos lors de l’enregistrement des piles et la possibilité future d’estimer
un volume depuis ces photos. Il confirme la progression dans l’ordre.

Le cadrage place la prospection avant la décision/contrat de chantier ; une
prospection ne crée ni autorisation, ni stock. Les photos documentaires seront
liées aux objets et observations, conservées durablement avec contrôle des
fichiers, de la portée et de la purge. Le calcul photographique reste expérimental :
échelle et profondeur nécessaires, apparent/solide distincts, validation terrain
et commerciale propre. Aucun benchmark concurrent ou modèle IA n’est prétendu exécuté.

Code : `WoodObjectCommands`, `WoodContractCodec`, longueur de pièce et bornes Unicode
des bruts. Six intentions immuables, déclarations capturées séparément du header,
documents JSON v1 et empreintes complètes ; relecture stricte sans perte silencieuse.
Ces contrats sont des valeurs de domaine, sans sauvegarde effective ni autorisation.

Validation : 115 tests ciblés sur sept suites, tous réussis ; 15 nouveaux cas de
codec. Compilation de production et tests réussie, schéma Room43 identique.
Les tests JVM complets et Android du lot de navigation restent des résultats
antérieurs ; pas de nouveau rendu UI ou APK par ce lot.

- [Nouveau besoin et critères de réalisation](../../docs/PROSPECTION_ESTIMATION_PHOTOS_BOIS_2026-10-18.md)
- [Contrat C01 et résultats](../../docs/CONTRATS_BOIS_C01_2026-10-18.md)
- Sauvegarde préalable : `/workspace/.onboarding/wood-contracts-2026-10-18/before.zip`.
- Preuves/source archive : `/workspace/outputs/2026-10-18/wood-contracts/`, local seulement.

Suite : C02 (repository, migration additive, accès/purge, journal durable de
commandes et réponses), C03/C04 (formulaires et calcul), exports réels puis
qualification Android. Photos documentaires après socle fiable ; prospection
et calcul photographique restent des lots distincts à raccorder.
