# Parcours GeoSylva — point du dimanche 18 octobre 2026

Objectif : démonstration le **mercredi 21 octobre**. Les trois journées de préparation sont dimanche 18, lundi 19 et mardi 20. L’ordre ci-dessous préserve la séparation des données et la qualification de chaque fonction ; il ne promet pas que toutes les extensions seront terminées à cette date.

Les décisions récentes du fondateur priment. La restauration du 15 est conservée, avec ses preuves de compilation et de tests. Les schémas représentent la cible discutée ; leur légende distingue le code raccordé du travail restant.

## Complément : chantier et passages successifs

Le [schéma chantier/parcelles/suivi daté](CHANTIERS_PARCELLES_SUIVI_DATE_BOIS_2026-10-18.md)
formalise la précision du fondateur. La cible utilise le chantier comme entrée,
avec plusieurs parcelles possibles, et conserve les contrôles et enlèvements
partiels successifs. L’accès direct aux dépôts reste la tranche locale actuellement
raccordée. Les [formulaires C02/C03](FORMULAIRES_BOIS_C02_C03_2026-10-18.md) sont
vérifiés sur Android ; réception commerciale, mouvements et exports bois ne sont pas
inclus dans ce seul lot.

## 1. Deux parcours d’entrée

Vert : parcours déjà présent dans la base restaurée. Bleu : navigation et formulaires du 18 octobre, composants vérifiés sur Android. Orange : raccord à développer avant de le présenter comme disponible. Gris : extension ultérieure. Voir la [validation de navigation](VALIDATION_PARCOURS_2026-10-18.md) et celle des [formulaires](FORMULAIRES_BOIS_C02_C03_2026-10-18.md) pour la portée exacte des essais, qui ne constituent pas encore une recette complète du parcours bois.

```mermaid
flowchart TD
    A[Choix du métier] --> G[Accueil Gestion forestière]
    A --> B[Accueil Chantiers et bois]
    G --> F[Forêt → parcelle → placette]
    F --> R[Inventaire / martelage / combiné]
    R --> S[Synthèse et rapports forestiers]
    B --> D[Dépôts de bois]
    D --> O[Piles et grumes identifiées]
    O --> M[Relevés manuels et calcul provisoire]
    M -. à raccorder .-> E[Récapitulatif et exports bois]
    M -. contrôle et convention propres .-> RC[Réception contractuelle]
    B --> C[Carte et packs hors ligne]
    G --> C
    B -. à développer .-> PR[Prospection et estimation]
    PR -. décision explicite .-> CH
    M -. pièces documentaires futures .-> PH[Photos liées à l’objet et au relevé]
    B -. prochain raccord .-> CH[Dossier chantier lié aux parcelles]
    CH -. offre et habilitations Entreprise .-> MI[Missions]
    classDef ready fill:#e0f2e6,stroke:#2f6a45,color:#14321e
    classDef current fill:#e2efff,stroke:#376998,color:#163754
    classDef next fill:#fff0d5,stroke:#9b6a18,color:#553707
    classDef future fill:#eeeeee,stroke:#777777,color:#333333
    class A,G,B,O,M current
    class F,R,S,D,C ready
    class CH,E next
    class RC,MI,PR,PH future
```

La carte existante permet la consultation de couches. Le guidage forestier spécialisé reste à réaliser. Le dépôt existe avec création, édition, position optionnelle et archivage ; la consultation/restauration des archives reste à compléter. Les objets bois, relevés, corrections et choix du relevé de référence sont désormais persistés et utilisables dans les formulaires. Ce parcours calcule une géométrie provisoire ; il ne constitue pas une réception contractuelle.

Le [complément du 18 octobre](PROSPECTION_ESTIMATION_PHOTOS_BOIS_2026-10-18.md) ajoute
la prospection, l’estimation et les photos documentaires. L’éventuel calcul depuis
des photos sera évalué séparément ; aucune fonction photographique n’est annoncée disponible.

## 2. Une seule identité pour le bois, plusieurs relevés conservés

```mermaid
flowchart LR
    D[Dépôt] --> B[Objet physique : pile ou grume]
    B --> O1[Observation initiale]
    B --> O2[Nouvelle mesure du même bois]
    O1 --> C[Correction avec motif : nouvelle version]
    O1 -. original conservé .-> H[Historique complet]
    O2 --> H
    C --> H
    H --> CH[Choix explicite du relevé courant]
    CH --> Q[Contrôles : méthode, unités, données requises]
    Q --> CALC[Calcul géométrique traçable]
    CALC -. à raccorder .-> EX[Récapitulatif courant et export]
```

**Règles** : une correction conserve l’original ; la sélection courante n’est pas remplacée silencieusement. Une observation corrigée est signalée et exclue du récapitulatif tant qu’une tête valide n’est pas choisie. Absence, zéro et valeur invalide restent distincts. Les volumes apparents de piles ne sont pas additionnés aux volumes solides des grumes.

## 3. Mesure, stock et transaction commerciale restent distincts

```mermaid
flowchart TD
    M[Mesures brutes et provenance] --> V[Volume provisoire avec méthode et unités]
    V --> REC[Réception : contrôle des relevés]
    REC -. convention et validation propres .-> A[Base achat propriétaire]
    REC -. convention et validation propres .-> P[Base prestation : bûcheron / débardeur]
    REC -. convention et validation propres .-> T[Base transport]
    REC -. convention et validation propres .-> VE[Base vente]
    ACT[Opération de stock explicite] --> MV[Entrée / enlèvement / transfert / correction]
    MV --> ST[Situation du stock et écarts]
    A --> ECO[Coûts, recettes et marge qualifiés]
    P --> ECO
    T --> ECO
    VE --> ECO
```

Il n’y a volontairement **aucune flèche automatique du relevé vers une entrée de stock**. Une remesure ne crée pas du bois supplémentaire. Une quantité géométrique n’est pas payable par défaut. Les prix, taxes, conventions et validations commerciales doivent être connus ; le choix HT/TTC est explicite par devis.

## 4. Accès, sauvegarde et future synchronisation

```mermaid
flowchart TD
    U[Métier déclaré] --> UI[Outils et présentation adaptés]
    ID[Compte et espace actifs] --> AUTH[Contrôle réel des accès]
    UI --> SA[Saisie locale]
    AUTH --> SA
    SA --> TX[Transaction : révision attendue et portée]
    TX --> DB[Base locale : original et historique conservés]
    DB --> EXP[Exports vérifiés]
    DB -. futur raccord .-> GS[GSIE : synchronisation avec reprise sans doublon]
    GS -. contrat à valider .-> FO[FORLOG : accepté / rejeté / à corriger]
    ENT[Offre Entreprise + appartenance + habilitation] -. module raccordé .-> MIS[Missions]
```

Le métier ne donne pas de droits entreprise. Une affiliation déclarée à l’ONF n’est pas une autorisation d’accès. Missions reste invisible tant que son module et les preuves nécessaires ne sont pas disponibles. L’archivage conserve l’historique ; la suppression de compte reste une autre procédure.

## Ordre de réalisation

| Journée | Travail prioritaire | Preuve attendue |
| --- | --- | --- |
| **Dimanche 18** | Réalisé : navigation métier, accueil, Explorer ; objets bois et relevés manuels persistés, corrections, sélection et calcul individuel | Captures Android, 1 776 tests JVM et 36 tests Android distincts réussis ; périmètre local décrit dans la validation |
| **Lundi 19** | Chantier lié aux parcelles et bois, navigation et archives ; récapitulatif puis préparation des enlèvements datés | Rattachements explicites, données antérieures conservées, aucun droit déduit de la carte ; distinguer constat du restant et mouvement |
| **Mardi 20** | Récapitulatif, exports réels et recette ; mouvements uniquement si le modèle est qualifié | Fichiers réouverts et vérifiés, données TEST identifiées, APK installable |
| **Mercredi 21** | Démontrer le parcours effectivement qualifié | Version figée et limites connues ; aucune fausse synchronisation FORLOG |

Le périmètre minimal est **dépôt → pile/grume → mesures conservées → calcul explicable → export**. Les mouvements, coûts, recettes et marges suivent leurs critères de validation. Les systèmes d’IA et le connecteur FORLOG ne doivent pas retarder ou simuler ce premier parcours. Le serveur de comptes et sa configuration Google restent une qualification distincte.

Références : [plan UX](PARCOURS_UX_DEMO_21_OCTOBRE_2026.md), [BOIS-01](SPEC_BOIS_01_PRET_A_CODER_2026-10-14.md), [lots C01–C07](PLAN_CODAGE_BOIS_2026-10-14.md).
