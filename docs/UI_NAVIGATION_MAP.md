# Carte de navigation GeoSylva 3.0 — toutes les pages et leurs liens

La [carte des parcours actuels](developpement/PARCOURS_ACTUELS.md) suit le code
de la version de construction. Le diagramme ci-dessous conserve la référence
de conception v0.8.0.

> Diagramme Mermaid de l'organisation et des liens entre toutes les pages.
> Généré depuis §29 de GEOSYLVA_3_SPECIFICATION_FONCTIONNELLE.md v0.8.0.
> Visualisable dans tout éditeur supportant Mermaid (GitHub, VS Code, etc.).

## Légende

- **Vert** = écran conservé (enrichi)
- **Orange** = écran transformé
- **Bleu** = nouvel écran
- **Gris** = écran supprimé (référence)
- `→` = navigation directe
- `⇢` = ouverture automatique (post-action)

---

## Vue d'ensemble — flux de démarrage + bottom nav

```mermaid
flowchart TD
    SPLASH["Splash<br/>(nouveau)"]:::new
    ONBOARD["Onboarding<br/>(conservé)"]:::kept
    LOGIN["Connexion Quintessences<br/>(refonte Login)"]:::transformed
    WORKSPACE["Sélection workspace<br/>(nouveau)"]:::new
    SETTINGS_OLD["Settings<br/>(supprimé)"]:::deleted

    SPLASH -->|premier démarrage| ONBOARD
    SPLASH -->|session valide| ACCUEIL
    ONBOARD --> LOGIN
    LOGIN -->|plusieurs espaces| WORKSPACE
    LOGIN -->|un seul espace| ACCUEIL
    WORKSPACE --> ACCUEIL

    subgraph BOTTOMNAV["Bottom navigation — 5 entrées"]
        ACCUEIL["Accueil<br/>(nouveau)"]:::new
        MISSIONS["Missions<br/>(nouveau)"]:::new
        CARTE["Carte<br/>(refonte complète)"]:::transformed
        DONNEES["Données<br/>(nouveau)"]:::new
        COMPTE["Compte<br/>(refonte Settings)"]:::transformed
    end

    classDef new fill:#2d6a4f,color:#fff,stroke:#1b4332,stroke-width:2px
    classDef kept fill:#52b788,color:#fff,stroke:#2d6a4f,stroke-width:2px
    classDef transformed fill:#e76f51,color:#fff,stroke:#9c3d28,stroke-width:2px
    classDef deleted fill:#6c757d,color:#fff,stroke:#495057,stroke-width:1px,stroke-dasharray:5 5
```

---

## Accueil — tableau de bord et descendants

```mermaid
flowchart TD
    ACCUEIL["Accueil<br/>(nouveau)"]:::new

    ACCUEIL --> CREER["Créer ou démarrer<br/>(bouton principal)"]:::new
    ACCUEIL --> PROJETS["Tous les projets<br/>(nouveau)"]:::new
    ACCUEIL --> REPRISE["Reprendre<br/>(dernière mission/placette/martelage)"]:::new
    ACCUEIL --> AUJ["Aujourd'hui<br/>(missions, alertes, sync)"]:::new

    CREER --> NEW_PROJET["Nouveau projet"]:::new
    CREER --> NEW_MISSION["Nouvelle mission"]:::new
    CREER --> NEW_FORET["Nouvelle forêt"]:::new
    CREER --> NEW_INVENT["Nouvel inventaire"]:::new
    CREER --> NEW_MARTEL["Nouveau martelage"]:::new
    CREER --> NEW_OBS["Observation rapide"]:::new

    PROJETS --> FICHE_PROJET["Fiche projet<br/>(nouveau)"]:::new
    FICHE_PROJET --> P_GENERAL["Vue générale"]:::new
    FICHE_PROJET --> P_FORETS["Forêts"]:::new
    FICHE_PROJET --> P_MISSIONS["Missions"]:::new
    FICHE_PROJET --> P_DOCS["Documents"]:::new
    FICHE_PROJET --> P_CARTE["Carte"]:::new
    FICHE_PROJET --> P_EQUIPE["Équipe"]:::new
    FICHE_PROJET --> P_HIST["Historique"]:::new

    P_FORETS --> FICHE_FORET

    NEW_FORET --> CREATE_FORET["Création forêt guidée<br/>(nouveau)"]:::new
    CREATE_FORET -->|après création| FICHE_FORET

    classDef new fill:#2d6a4f,color:#fff,stroke:#1b4332,stroke-width:2px
    classDef kept fill:#52b788,color:#fff,stroke:#2d6a4f,stroke-width:2px
    classDef transformed fill:#e76f51,color:#fff,stroke:#9c3d28,stroke-width:2px
```

---

## Forêt → Parcelle → Placette → Tige (chaîne canonique)

```mermaid
flowchart TD
    FICHE_FORET["Fiche forêt<br/>(refonte Forets/Groups)"]:::transformed

    FICHE_FORET --> F_RESUME["Résumé"]:::new
    FICHE_FORET --> F_PARCELLES["Parcelles"]:::kept
    FICHE_FORET --> F_PEUPlements["Peuplements"]:::new
    FICHE_FORET --> F_MISSIONS["Missions"]:::new
    FICHE_FORET --> F_CARTE["Carte<br/>(Map par parcelle conservé)"]:::kept
    FICHE_FORET --> F_DOCS["Documents de gestion<br/>(nouveau)"]:::new
    FICHE_FORET --> F_HIST["Historique"]:::new

    F_PARCELLES -->|ajouter| CREATE_PARCELLE["Création parcelle guidée<br/>(nouveau)"]:::new
    F_PARCELLES --> FICHE_PARCELLE

    CREATE_PARCELLE -->|après création| FICHE_PARCELLE["Fiche parcelle<br/>(Parcelles enrichi)"]:::kept

    FICHE_PARCELLE --> PA_RESUME["Résumé"]:::kept
    FICHE_PARCELLE --> PA_PEUP["Peuplement<br/>(ancien StandClassification)"]:::transformed
    FICHE_PARCELLE --> PA_PLACETTES["Placettes<br/>(conservé)"]:::kept
    FICHE_PARCELLE --> PA_INVENT["Inventaires"]:::new
    FICHE_PARCELLE --> PA_MARTEL["Martelages"]:::new
    FICHE_PARCELLE --> PA_TRAVAUX["Travaux / Chantier<br/>(nouveau)"]:::new
    FICHE_PARCELLE --> PA_DIAG["Diagnostic stationnel<br/>(refonte DiagnosticMenu)"]:::transformed
    FICHE_PARCELLE --> PA_RIP["Ripisylve<br/>(déplacé)"]:::transformed
    FICHE_PARCELLE --> PA_IBP["Biodiversité / IBP<br/>(déplacé)"]:::transformed
    FICHE_PARCELLE --> PA_SANTE["Santé"]:::new
    FICHE_PARCELLE --> PA_CARTE["Carte"]:::kept
    FICHE_PARCELLE --> PA_DOCS["Documents"]:::new
    FICHE_PARCELLE --> PA_HIST["Historique"]:::new

    PA_PLACETTES -->|ajouter| CREATE_PLACETTE["Création placette guidée<br/>(nouveau)"]:::new
    PA_PLACETTES --> FICHE_PLACETTE

    CREATE_PLACETTE -->|après création| FICHE_PLACETTE["Fiche placette<br/>(PlacetteDetail enrichi)"]:::kept

    FICHE_PLACETTE --> PL_RESUME["Résumé"]:::kept
    FICHE_PLACETTE --> PL_TIGES["Tiges"]:::kept
    FICHE_PLACETTE --> PL_ESSENCES["Essences<br/>(EssenceDiam enrichi)"]:::kept
    FICHE_PLACETTE --> PL_MARTEL["Martelage"]:::new
    FICHE_PLACETTE --> PL_EVOL["Évolution<br/>(PlacetteEvolution enrichi)"]:::kept
    FICHE_PLACETTE --> PL_SANTE["Santé"]:::new
    FICHE_PLACETTE --> PL_BIO["Biodiversité / IBP<br/>(IbpEvaluation déplacé)"]:::transformed
    FICHE_PLACETTE --> PL_CALCULS["Calculs<br/>(Dashboard enrichi)"]:::kept
    FICHE_PLACETTE --> PL_CARTE["Carte"]:::kept
    FICHE_PLACETTE --> PL_PJ["Pièces jointes"]:::new
    FICHE_PLACETTE --> PL_HIST["Historique"]:::new

    PL_TIGES -->|ajouter| SAISIE_TIGE["Saisie d'une tige<br/>(nouveau)"]:::new
    PL_MARTEL -->|démarrer| PREP_MARTEL
    PL_MARTEL -->|mesurer caméra| TREEVISION["TreeVision caméra<br/>(nouveau)"]:::new

    classDef new fill:#2d6a4f,color:#fff,stroke:#1b4332,stroke-width:2px
    classDef kept fill:#52b788,color:#fff,stroke:#2d6a4f,stroke-width:2px
    classDef transformed fill:#e76f51,color:#fff,stroke:#9c3d28,stroke-width:2px
```

---

## Martelage — saisie → synthèse automatique

```mermaid
flowchart TD
    PREP_MARTEL["Écran préparation martelage<br/>(nouveau)"]:::new
    SAISIE_MARTEL["Écran saisie terrain<br/>(PlacetteDetailScreen adapté)"]:::kept
    PAUSE["Pause<br/>(session active, saisie suspendue)"]:::new
    FIN_MARTEL["Fin — double validation<br/>(arrêter + confirmer)"]:::new
    SNAP["Instantané immuable"]:::new
    SYNTHESE["SynthèseMartelage<br/>(ancien Martelage transformé)"]:::transformed
    ANALYSE_GSIE["Analyse GSIE<br/>(refonte SuperCorrelateur)"]:::transformed

    PREP_MARTEL --> SAISIE_MARTEL
    SAISIE_MARTEL -->|pause| PAUSE
    PAUSE -->|reprendre| SAISIE_MARTEL
    SAISIE_MARTEL -->|terminer| FIN_MARTEL
    FIN_MARTEL --> SNAP
    SNAP -->|⇢ ouverture auto| SYNTHESE
    SYNTHESE -->|analyse disponible| ANALYSE_GSIE
    SYNTHESE -->|export| EXPORT["Export / validation"]:::new

    MARTEL_OLD["Ancien écran Martelage<br/>(saisie + synthèse mélangées)"]:::deleted

    classDef new fill:#2d6a4f,color:#fff,stroke:#1b4332,stroke-width:2px
    classDef kept fill:#52b788,color:#fff,stroke:#2d6a4f,stroke-width:2px
    classDef transformed fill:#e76f51,color:#fff,stroke:#9c3d28,stroke-width:2px
    classDef deleted fill:#6c757d,color:#fff,stroke:#495057,stroke-width:1px,stroke-dasharray:5 5
```

---

## Missions — liste, dashboard, parcours

```mermaid
flowchart TD
    MISSIONS["Missions<br/>(bottom nav 2ème entrée)"]:::new
    LISTE_MISSIONS["Liste des missions<br/>(filtres : aujourd'hui, à venir, en cours...)"]:::new
    DASH_MISSION["Tableau de bord mission<br/>(nouveau)"]:::new

    MISSIONS --> LISTE_MISSIONS
    LISTE_MISSIONS --> DASH_MISSION

    DASH_MISSION --> M_RESUME["Résumé"]:::new
    DASH_MISSION --> M_PARCOURS["Parcours guidé<br/>(Préparation → Arrivée → Collecte → Contrôle → Validation → Restitution → Sync)"]:::new
    DASH_MISSION --> M_DONNEES["Données"]:::new
    DASH_MISSION --> M_CARTE["Carte"]:::kept
    DASH_MISSION --> M_EQUIPE["Équipe"]:::new
    DASH_MISSION --> M_MATERIEL["Matériel"]:::new
    DASH_MISSION --> M_LIVRABLES["Livrables"]:::new
    DASH_MISSION --> M_SYNC["Synchronisation"]:::new
    DASH_MISSION --> M_HIST["Historique"]:::new

    M_PARCOURS -->|inventaire| FICHE_PLACETTE
    M_PARCOURS -->|martelage| PREP_MARTEL
    M_PARCOURS -->|diagnostic stationnel| PA_DIAG
    M_PARCOURS -->|diagnostic ripisylve| PA_RIP
    M_PARCOURS -->|IBP| PA_IBP
    M_PARCOURS -->|chantier| PA_TRAVAUX

    classDef new fill:#2d6a4f,color:#fff,stroke:#1b4332,stroke-width:2px
    classDef kept fill:#52b788,color:#fff,stroke:#2d6a4f,stroke-width:2px
    classDef transformed fill:#e76f51,color:#fff,stroke:#9c3d28,stroke-width:2px
```

---

## Carte — refonte complète

```mermaid
flowchart TD
    CARTE["Carte principale<br/>(bottom nav 3ème entrée — refonte)"]:::transformed
    MAP_PARCELLE["Map par parcelle<br/>(conservé depuis fiches)"]:::kept

    CARTE --> C_MODES["Modes : Explorer / Mission / Éditer / Mesurer / Télécharger / Analyser"]:::new
    CARTE --> C_COUCHES["Panneau couches<br/>(Travail, Forêt, Parcellaire, Référentiels, IGN, GSIE, Personnel)"]:::new
    CARTE --> C_FICHE["Fiche d'objet<br/>(sélection → panneau inférieur)"]:::new
    CARTE --> C_OUTILS["Outils : distance, surface, profil, dessin, snapping, buffer, export"]:::new
    CARTE --> C_OFFLINE["Cartes hors ligne → QPIS"]:::new

    C_FICHE -->|ouvrir fiche| FICHE_FORET
    C_FICHE -->|ouvrir fiche| FICHE_PARCELLE
    C_FICHE -->|ouvrir fiche| FICHE_PLACETTE
    C_FICHE -->|démarrer mission| DASH_MISSION
    C_FICHE -->|ajouter territoire| CREATE_FORET
    C_FICHE -->|ajouter placette| CREATE_PLACETTE

    C_OFFLINE --> QPIS

    FICHE_FORET --> F_CARTE
    FICHE_PARCELLE --> PA_CARTE
    FICHE_PLACETTE --> PL_CARTE
    F_CARTE --> MAP_PARCELLE
    PA_CARTE --> MAP_PARCELLE
    PL_CARTE --> MAP_PARCELLE

    classDef new fill:#2d6a4f,color:#fff,stroke:#1b4332,stroke-width:2px
    classDef kept fill:#52b788,color:#fff,stroke:#2d6a4f,stroke-width:2px
    classDef transformed fill:#e76f51,color:#fff,stroke:#9c3d28,stroke-width:2px
```

---

## Données — navigateur global

```mermaid
flowchart TD
    DONNEES["Données<br/>(bottom nav 4ème entrée)"]:::new

    DONNEES --> D_PROJETS["Projets"]:::new
    DONNEES --> D_FORETS["Forêts"]:::kept
    DONNEES --> D_PARCELLES["Parcelles"]:::kept
    DONNEES --> D_PEUP["Peuplements"]:::new
    DONNEES --> D_PLACETTES["Placettes"]:::kept
    DONNEES --> D_TIGES["Tiges"]:::new
    DONNEES --> D_OBS["Observations"]:::new
    DONNEES --> D_MISSIONS["Missions"]:::new
    DONNEES --> D_TRAVAUX["Travaux"]:::new
    DONNEES --> D_DOCS["Documents"]:::new
    DONNEES --> D_CALCULS["Calculs"]:::new
    DONNEES --> D_PACKS["Packs"]:::new
    DONNEES --> D_METHODES["Méthodes → Centre scientifique"]:::new

    D_PROJETS --> FICHE_PROJET
    D_FORETS --> FICHE_FORET
    D_PARCELLES --> FICHE_PARCELLE
    D_PLACETTES --> FICHE_PLACETTE
    D_METHODES --> CENTRE_SCI

    DONNEES --> D_FONCTIONS["Recherche globale, filtres, export, import, archivage, doublons, sync, qualité"]:::new

    classDef new fill:#2d6a4f,color:#fff,stroke:#1b4332,stroke-width:2px
    classDef kept fill:#52b788,color:#fff,stroke:#2d6a4f,stroke-width:2px
```

---

## Compte — 16 sections (refonte Settings)

```mermaid
flowchart TD
    COMPTE["Compte<br/>(bottom nav 5ème entrée)"]:::transformed
    SETTINGS_OLD["Settings<br/>(supprimé)"]:::deleted
    ACCOUNT_OLD["Account<br/>(refondu dans Profil)"]:::deleted
    LOGIN_OLD["Login<br/>(refondu en Connexion)"]:::deleted
    PASS_OLD["PasswordRecovery<br/>(refondu dans Sécurité)"]:::deleted

    COMPTE --> C_PROFIL["Profil<br/>(ancien Account refondu)"]:::transformed
    COMPTE --> C_WORKSPACE["Workspace"]:::new
    COMPTE --> C_ORGA["Organisations"]:::new
    COMPTE --> C_ABO["Abonnement"]:::new
    COMPTE --> C_SEC["Sécurité<br/>(passkeys, Google, TOTP, récupération)"]:::transformed
    COMPTE --> C_APPAREILS["Appareils"]:::new
    COMPTE --> C_SYNC["Synchronisation → Centre sync"]:::new
    COMPTE --> C_PACKS["Packs → Gestionnaire QPIS"]:::transformed
    COMPTE --> C_TERRAIN["Terrain<br/>(pluie, gants, gaucher, haptique)"]:::new
    COMPTE --> C_CARTES["Cartes<br/>(fonds, caches, téléchargements)"]:::new
    COMPTE --> C_IA["IA<br/>(modèles, assistant vocal)"]:::new
    COMPTE --> C_CONF["Confidentialité<br/>(ancien PrivacyPolicy)"]:::kept
    COMPTE --> C_ACCESS["Accessibilité"]:::new
    COMPTE --> C_AIDE["Aide → Onboarding"]:::new
    COMPTE --> C_DEV["Développeur<br/>(ancien DeveloperOptions enrichi)"]:::kept
    COMPTE --> C_ABOUT["À propos"]:::new

    C_WORKSPACE --> WORKSPACE
    C_ORGA --> WORKSPACE
    C_SYNC --> CENTRE_SYNC
    C_PACKS --> QPIS
    C_AIDE --> ONBOARD
    C_SEC -->|mot de passe oublié| C_SEC

    classDef new fill:#2d6a4f,color:#fff,stroke:#1b4332,stroke-width:2px
    classDef kept fill:#52b788,color:#fff,stroke:#2d6a4f,stroke-width:2px
    classDef transformed fill:#e76f51,color:#fff,stroke:#9c3d28,stroke-width:2px
    classDef deleted fill:#6c757d,color:#fff,stroke:#495057,stroke-width:1px,stroke-dasharray:5 5
```

---

## Synchronisation, conflits, QPIS, centre scientifique, analyse GSIE

```mermaid
flowchart TD
    CENTRE_SYNC["Centre de synchronisation<br/>(nouveau)"]:::new
    CONFLITS["Résolution des conflits<br/>(nouveau)"]:::new
    QPIS["Gestionnaire QPIS<br/>(refonte PackManager)"]:::transformed
    CENTRE_SCI["Centre scientifique<br/>(ancien TarifDocs enrichi)"]:::transformed
    ANALYSE_GSIE["Analyse GSIE<br/>(refonte SuperCorrelateur)"]:::transformed

    CENTRE_SYNC --> CS_GENERAL["Vue générale"]:::new
    CENTRE_SYNC --> CS_ATTENTE["En attente"]:::new
    CENTRE_SYNC --> CS_ERREURS["Erreurs"]:::new
    CENTRE_SYNC --> CS_CONFLITS["Conflits"]:::new
    CENTRE_SYNC --> CS_APPAREILS["Appareils"]:::new
    CENTRE_SYNC --> CS_HIST["Historique"]:::new

    CS_CONFLITS --> CONFLITS
    CONFLITS -->|garder local / serveur / fusionner / dupliquer / reporter| CS_CONFLITS

    QPIS --> Q_RECOS["Recommandés"]:::new
    QPIS --> Q_INSTALLES["Installés"]:::new
    QPIS --> Q_TERRIT["Territoires"]:::new
    QPIS --> Q_SCIENT["Scientifiques"]:::new
    QPIS --> Q_IA["IA"]:::new
    QPIS --> Q_ORGA["Organisation"]:::new
    QPIS --> Q_STOCK["Stockage"]:::new
    QPIS --> Q_HIST["Historique"]:::new

    CENTRE_SCI --> CS_METHODES["Méthodes installées"]:::new
    CENTRE_SCI --> CS_PAGE["Page méthode<br/>(description, variables, formule, source, territoire, espèces, incertitude)"]:::new
    CENTRE_SCI -->|référence IBP| IBP_REF["IBP Reference<br/>(déplacé)"]:::transformed

    ANALYSE_GSIE --> AG_LOCAL["Analyse locale<br/>(hors ligne)"]:::new
    ANALYSE_GSIE --> AG_GSIE["Analyse GSIE<br/>(moteur, date, version, preuve, incertitude)"]:::new
    ANALYSE_GSIE --> AG_DIAG["Diagnostic"]:::new
    ANALYSE_GSIE --> AG_RECO["Recommandations<br/>(explicables, modifiables, refusables)"]:::new
    ANALYSE_GSIE --> AG_SCEN["Scénarios"]:::new
    ANALYSE_GSIE --> AG_SRC["Sources"]:::new

    SYNTHESE -->|analyse disponible| ANALYSE_GSIE
    PL_CALCULS -->|voir méthode| CENTRE_SCI
    FICHE_PARCELLE -->|analyse| ANALYSE_GSIE

    classDef new fill:#2d6a4f,color:#fff,stroke:#1b4332,stroke-width:2px
    classDef kept fill:#52b788,color:#fff,stroke:#2d6a4f,stroke-width:2px
    classDef transformed fill:#e76f51,color:#fff,stroke:#9c3d28,stroke-width:2px
```

---

## Diagnostics — nouvelle organisation (déplacés)

```mermaid
flowchart TD
    subgraph AVANT["Avant — NavGraphs séparés"]
        DIAG_MENU_OLD["DiagnosticMenu<br/>(route séparée)"]:::deleted
        DIAG_RESULT_OLD["DiagnosticResult<br/>(route séparée)"]:::deleted
        RIP_OLD["RipisylveDiagnostic<br/>(route séparée)"]:::deleted
        RIP_STD_OLD["RipisylveStandalone<br/>(route séparée)"]:::deleted
        IBP_PROJ_OLD["IbpProjects<br/>(route séparée)"]:::deleted
        IBP_STD_OLD["IbpStandalone<br/>(route séparée)"]:::deleted
        IBP_HIST_OLD["IbpHistory<br/>(route séparée)"]:::deleted
        IBP_EVAL_OLD["IbpEvaluation<br/>(route séparée)"]:::deleted
        IBP_REF_OLD["IbpReference<br/>(route séparée)"]:::deleted
        IBP_DIAG_OLD["IbpDiagnostic<br/>(route séparée)"]:::deleted
        IBP_CMP_OLD["IbpCompare<br/>(route séparée)"]:::deleted
    end

    subgraph APRES["Après — onglets fiches + protocoles missions"]
        PA_DIAG["Fiche parcelle > Diagnostic stationnel<br/>(onglet — refonte)"]:::transformed
        PA_RIP["Fiche parcelle > Ripisylve<br/>(onglet — déplacé)"]:::transformed
        PA_IBP["Fiche parcelle > Biodiversité / IBP<br/>(onglet — déplacé)"]:::transformed
        PL_BIO["Fiche placette > Biodiversité / IBP<br/>(sous-onglet — IbpEvaluation déplacé)"]:::transformed
        M_DIAG["Mission > Diagnostic stationnel<br/>(protocole Mission Engine)"]:::new
        M_RIP["Mission > Diagnostic ripisylve<br/>(protocole Mission Engine)"]:::new
        M_IBP["Mission > IBP<br/>(protocole Mission Engine)"]:::new
        IBP_REF["Centre scientifique > IBP Reference<br/>(déplacé)"]:::transformed
    end

    DIAG_MENU_OLD -.->|devient| PA_DIAG
    DIAG_RESULT_OLD -.->|devient| PA_DIAG
    RIP_OLD -.->|devient| PA_RIP
    RIP_STD_OLD -.->|devient| M_RIP
    IBP_PROJ_OLD -.->|devient| M_IBP
    IBP_STD_OLD -.->|devient| M_IBP
    IBP_HIST_OLD -.->|devient| PA_IBP
    IBP_EVAL_OLD -.->|devient| PL_BIO
    IBP_REF_OLD -.->|devient| IBP_REF
    IBP_DIAG_OLD -.->|devient| PA_IBP
    IBP_CMP_OLD -.->|devient| PL_BIO

    classDef new fill:#2d6a4f,color:#fff,stroke:#1b4332,stroke-width:2px
    classDef kept fill:#52b788,color:#fff,stroke:#2d6a4f,stroke-width:2px
    classDef transformed fill:#e76f51,color:#fff,stroke:#9c3d28,stroke-width:2px
    classDef deleted fill:#6c757d,color:#fff,stroke:#495057,stroke-width:1px,stroke-dasharray:5 5
```

---

## TreeVision, Travaux, Documents

```mermaid
flowchart TD
    PREP_MARTEL["Préparation martelage"]:::new
    SAISIE_MARTEL["Saisie martelage"]:::new
    TREEVISION["TreeVision caméra<br/>(nouveau — expérimental)"]:::new
    TV_RESULT["Page résultat TreeVision<br/>(diamètre, hauteur, incertitude, confiance)"]:::new

    PREP_MARTEL --> SAISIE_MARTEL
    SAISIE_MARTEL -->|mesurer caméra| TREEVISION
    TREEVISION -->|valider| TV_RESULT
    TV_RESULT -->|corriger| SAISIE_MARTEL

    PA_TRAVAUX["Chantier travaux<br/>(nouveau)"]:::new
    PA_TRAVAUX --> T_PRESC["Prescription"]:::new
    PA_TRAVAUX --> T_PLAN["Planification"]:::new
    PA_TRAVAUX --> T_CARTE["Carte"]:::kept
    PA_TRAVAUX --> T_EXEC["Exécution"]:::new
    PA_TRAVAUX --> T_CTRL["Contrôle"]:::new
    PA_TRAVAUX --> T_RECEPT["Réception"]:::new
    PA_TRAVAUX --> T_DOCS["Documents"]:::new
    PA_TRAVAUX --> T_HIST["Historique"]:::new

    F_DOCS["Documents de gestion<br/>(nouveau)"]:::new
    F_DOCS --> DOC_PSG["PSG"]:::new
    F_DOCS --> DOC_AMEN["Aménagement"]:::new
    F_DOCS --> DOC_COUPE["Programme de coupes"]:::new
    F_DOCS --> DOC_TRAV["Programme de travaux"]:::new
    F_DOCS --> DOC_CARTES["Cartes"]:::new
    F_DOCS --> DOC_BILAN["Bilans"]:::new
    F_DOCS --> DOC_AVEN["Avenants"]:::new

    classDef new fill:#2d6a4f,color:#fff,stroke:#1b4332,stroke-width:2px
    classDef kept fill:#52b788,color:#fff,stroke:#2d6a4f,stroke-width:2px
    classDef transformed fill:#e76f51,color:#fff,stroke:#9c3d28,stroke-width:2px
```

---

## Vue globale — toutes les pages sur un seul graphe

```mermaid
flowchart TD
    %% Démarrage
    SPLASH["Splash"]:::new --> ONBOARD["Onboarding"]:::kept
    SPLASH -->|session valide| ACCUEIL["Accueil"]:::new
    ONBOARD --> LOGIN["Connexion"]:::transformed
    LOGIN -->|multi-espace| WORKSPACE["Workspace"]:::new
    LOGIN -->|un espace| ACCUEIL
    WORKSPACE --> ACCUEIL

    %% Bottom nav
    ACCUEIL <--> MISSIONS["Missions"]:::new
    MISSIONS <--> CARTE["Carte"]:::transformed
    CARTE <--> DONNEES["Données"]:::new
    DONNEES <--> COMPTE["Compte"]:::transformed
    ACCUEIL <--> CARTE
    ACCUEIL <--> DONNEES
    ACCUEIL <--> COMPTE
    MISSIONS <--> DONNEES
    MISSIONS <--> COMPTE
    CARTE <--> COMPTE

    %% Accueil descendants
    ACCUEIL --> PROJETS["Projets"]:::new
    ACCUEIL --> CREER["Créer"]:::new
    PROJETS --> FICHE_PROJET["Fiche projet"]:::new
    CREER --> CREATE_FORET["Création forêt"]:::new

    %% Chaîne canonique
    FICHE_PROJET --> FICHE_FORET["Fiche forêt"]:::transformed
    CREATE_FORET --> FICHE_FORET
    FICHE_FORET --> FICHE_PARCELLE["Fiche parcelle"]:::kept
    FICHE_FORET -->|ajouter| CREATE_PARCELLE["Création parcelle"]:::new
    CREATE_PARCELLE --> FICHE_PARCELLE
    FICHE_PARCELLE --> FICHE_PLACETTE["Fiche placette"]:::kept
    FICHE_PARCELLE -->|ajouter| CREATE_PLACETTE["Création placette"]:::new
    CREATE_PLACETTE --> FICHE_PLACETTE

    %% Placette → tige → martelage
    FICHE_PLACETTE --> SAISIE_TIGE["Saisie tige"]:::new
    FICHE_PLACETTE --> PREP_MARTEL["Préparation martelage"]:::new
    PREP_MARTEL --> SAISIE_MARTEL["Saisie martelage"]:::new
    SAISIE_MARTEL -->|mesurer caméra| TREEVISION["TreeVision"]:::new
    SAISIE_MARTEL -->|⇢ auto| SYNTHESE["SynthèseMartelage"]:::transformed
    SYNTHESE --> ANALYSE_GSIE["Analyse GSIE"]:::transformed

    %% Diagnostics (onglets parcelle)
    FICHE_PARCELLE --> PA_DIAG["Diagnostic stationnel"]:::transformed
    FICHE_PARCELLE --> PA_RIP["Ripisylve"]:::transformed
    FICHE_PARCELLE --> PA_IBP["Biodiversité/IBP"]:::transformed
    FICHE_PARCELLE --> PA_TRAVAUX["Travaux"]:::new
    FICHE_FORET --> F_DOCS["Documents gestion"]:::new

    %% Missions
    MISSIONS --> DASH_MISSION["Dashboard mission"]:::new
    DASH_MISSION --> PREP_MARTEL
    DASH_MISSION --> PA_DIAG
    DASH_MISSION --> PA_RIP
    DASH_MISSION --> PA_IBP
    DASH_MISSION --> PA_TRAVAUX

    %% Carte
    CARTE --> FICHE_FORET
    CARTE --> FICHE_PARCELLE
    CARTE --> FICHE_PLACETTE
    CARTE --> CREATE_FORET
    CARTE --> QPIS["QPIS"]:::transformed

    %% Données
    DONNEES --> FICHE_PROJET
    DONNEES --> FICHE_FORET
    DONNEES --> FICHE_PARCELLE
    DONNEES --> FICHE_PLACETTE
    DONNEES --> CENTRE_SCI["Centre scientifique"]:::transformed

    %% Compte
    COMPTE --> WORKSPACE
    COMPTE --> CENTRE_SYNC["Centre sync"]:::new
    COMPTE --> QPIS
    COMPTE --> C_DEV["Développeur"]:::kept
    CENTRE_SYNC --> CONFLITS["Conflits"]:::new

    %% Cross-links
    FICHE_PLACETTE -->|calculs| CENTRE_SCI
    SYNTHESE --> ANALYSE_GSIE
    FICHE_PARCELLE --> ANALYSE_GSIE

    classDef new fill:#2d6a4f,color:#fff,stroke:#1b4332,stroke-width:2px
    classDef kept fill:#52b788,color:#fff,stroke:#2d6a4f,stroke-width:2px
    classDef transformed fill:#e76f51,color:#fff,stroke:#9c3d28,stroke-width:2px
```

---

## Récapitulatif — compteurs

| Catégorie | Nombre | Couleur |
|---|---|---|
| **Conservés** (enrichis) | 18 | Vert |
| **Transformés** | 10 | Orange |
| **Nouveaux** | 21 | Bleu foncé |
| **Supprimés** (référence) | 4 | Gris pointillé |
| **Total pages v0.8.0** | 49 actives | — |
| **Total routes v0.7.0** | 27 | — |
