# CLAUDE.md — GeoSylva

> Règles locales pour les agents IA travaillant sur ce dépôt.

## Reprise actuelle — 15 novembre 2026

Lire `AGENTS.md`, puis `docs/developpement/REPRISE_AGENTS.md` et
`docs/production/ETAT_LIVRAISON_3_1.json`. Les consignes et résultats anciens
restent datés. Version 3.0.0 de construction, Room 49 et moteur Mapbox commun ;
production 3.1 visée au 28 février 2027. L'utilisateur autorise la mise à jour
des sources sur le dépôt public après validation ; conserver les secrets et les
fichiers de configuration personnels hors Git.

Les composants sous `docs/recovery/pending-integration/` sont des sources
préservées, pas des fonctions livrées. Un build, un test JVM, une capture Android
et une recette physique S25 sont quatre preuves distinctes.


---

## 1. Ce qu'est ce dépôt

GeoSylva est une application Android d'inventaire forestier et de
martelage, **spécialisation forestière de l'écosystème Quintessences**
(propulsé par le moteur GSIE). C'est un dépôt git **indépendant**
(voir `Quintessences/CLAUDE.md` §10 « Repos externes intégrés ») — pas
soumis à la Constitution/RFC/gouvernance GSIE au sens strict, mais
client de l'écosystème et attendu cohérent avec ses principes.

Documentation de référence :
- `README.md` — présentation, architecture, stack technique
- `docs/VOLUME_CALCULATION_NEXT_GEN.md` — vision moteur de volume nouvelle
  génération (LiDAR, photogrammétrie, IA on-device, mesh LoRa terrain)
- `MASTER_PLAN.md`, `RESEARCH_OPPORTUNITIES.md` — plans et pistes

## 2. Principe fondateur : offline-first

GeoSylva fonctionne **entièrement seule**, sans réseau. Les algorithmes
de cubage (tarifs, Schumacher-Hall, etc.) et les futurs modèles IA
on-device tournent localement. Aucune fonctionnalité cœur ne doit
dépendre d'une connexion internet.

## 3. Articulation avec GSIE — trois canaux réseau distincts

Le principe (confirmé par le Fondateur, 2026-07-17) distingue trois
canaux de communication, à ne jamais confondre dans le code ou la doc :

1. **GeoSylva ↔ GSIE serveur** : uniquement en bonne condition réseau
   (Wi-Fi/4G stable). Sert à **augmenter la capacité de réflexion** —
   déléguer aux moteurs GSIE lourds (Correlation, Reasoning, Diagnostic)
   ce que le téléphone ne peut pas calculer seul, et à récupérer des
   connaissances sourcées (coefficients scientifiques vérifiés, voir §4)
   depuis le Knowledge Engine. Ce n'est **pas** un simple canal de
   synchronisation de sauvegarde — c'est un canal d'amplification.
2. **Technicien ↔ technicien (Bluetooth)** : proximité immédiate,
   partage terrain instantané entre deux appareils.
3. **Technicien ↔ technicien / GSIE (LoRa mesh)** : portée longue,
   bas débit, sans infrastructure. Synchronisation de données entre
   techniciens sur le terrain, et à terme remontée vers un serveur/PC
   GSIE en LoRa. Rejoindre un mesh d'équipe se fait par QR code (clé
   partagée par le chef d'équipe — voir
   `docs/VOLUME_CALCULATION_NEXT_GEN.md` §17, `encodeTeamKeyQr`).

Ces trois canaux s'inspirent de l'architecture réseau générique posée
dans **RFC-0003** (`Quintessences/02_RFC/RFC-0003.md`, statut *Proposé*
— non encore activé, à valider par le Fondateur en Phase 2) — stack
GSIE-Net en couches (physique/transport/GSIE-Net/métier). À noter :
RFC-0003 décrit le Bluetooth comme lien **téléphone ↔ module radio
XIAO+SX1262** (Niveau 1), pas comme canal phone-to-phone entre deux
techniciens — le canal 2 ci-dessus est une spécificité GeoSylva non
encore ancrée dans le RFC. Le canal 3 (LoRa mesh) correspond au
Niveau 2 du RFC. Toute évolution de cette architecture réseau doit
rester cohérente avec RFC-0003 ; la valider/amender relève du
Fondateur (Quintessences), pas de ce dépôt.

## 4. Garde-fou scientifique — même discipline qu'ADR-007 (GSIE)

Le Knowledge Engine GSIE applique ADR-007 : aucun coefficient
scientifique n'est inventé, tout est sourcé. GeoSylva n'est pas
contractuellement lié à cet ADR, mais **doit appliquer le même principe
en pratique** : les coefficients dendrométriques (Schaeffer, Algan, IFN
dans `TarifData.kt`) sont déjà bien sourcés (citations en en-tête). Les
coefficients Schumacher-Hall par essence de
`ExpertForestryCalculator.getSchumacherHallParameters()` sont désormais
sourcés via `SylvicultureDatabase` (28 essences, coefficients Vallet et
al. 2006, *Revue Forestière Française* LVIII(5):481-496) — l'alias
historique `ABAL`→`ABBA` (Sapin pectiné) garantit que le code métier
hérité profite bien des coefficients sourcés plutôt que du repli
générique. Un repli générique non qualifié ne doit pas activer un résultat destiné au terrain
ou aux exports. Une citation dans un calculateur historique ne qualifie pas à elle
seule son domaine, ses mesures et son essence. La Synthèse et les exports des
relevés actuels partagent leur politique d’indisponibilité ; préserver ce garde-fou.

## 5. Conventions

- Documentation et commentaires en français, cohérent avec le reste de
  Quintessences.
- Ne pas dupliquer indéfiniment la science forestière en dur dans le
  code Kotlin — à terme, les coefficients sourcés devraient venir du
  Knowledge Engine GSIE (canal 1, §3), avec cache local pour le mode
  hors-ligne.
- Avant toute modification du moteur de volume, lire
  `docs/VOLUME_CALCULATION_NEXT_GEN.md` — c'est un document de vision
  (4458 lignes), pas un plan d'implémentation immédiat : introduire les
  nouvelles stratégies de façon additive (pattern Strategy), sans
  préserver les calculs existants vérifiés, leurs tests et les archives figées ;
  qualifier séparément toute activation dans un parcours utilisateur.

## 6. Rappel final

> GeoSylva doit rester utilisable seule, en forêt, sans réseau. Le
> serveur GSIE et le mesh LoRa sont des amplificateurs de capacité,
> jamais des dépendances.
