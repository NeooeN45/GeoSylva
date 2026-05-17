package com.forestry.counter.domain.usecase.pack

// ══════════════════════════════════════════════════════════════════════════════
//  RegionalPackContent — Contenu métier différencié par territoire
//
//  Principe :
//   - National  : règles génériques France entière (toujours chargé)
//   - Régional  : sur-couche régionale (essences prioritaires, flore locale,
//                  seuils ajustés, SRGS applicable)
//   - Deptl     : sur-couche fine (espèces rares protégées, Plans de gestion locaux)
//
//  Mécanisme de résolution : Local > Régional > National (PackResolver.resolve)
//
//  Sources :
//   - SRGS régionaux (Schémas régionaux de gestion sylvicole) publiés par région
//   - Listes floristiques INPN / MNHN par région administrative
//   - Référentiels CRPF régionaux pour essences et seuils
//   - Plans régionaux de l'agriculture durable (PRAD)
// ══════════════════════════════════════════════════════════════════════════════

object RegionalPackContent {

    // ─── Contenu régional ─────────────────────────────────────────────────────

    data class RegionalContent(
        val regionCode: String,
        val regionName: String,
        /** Essences recommandées par ordre de priorité — adaptées au contexte régional */
        val essencesPrioritaires: List<String>,
        /** Essences à éviter ou surveiller (vulnérabilité climatique, invasivité) */
        val essencesAEviter: List<String>,
        /** Espèces floristiques indicatrices importantes pour cette région */
        val especesIndicatricesRegionales: List<String>,
        /** Associations végétales typiques de la région (phytosociologie) */
        val associationsTypiques: List<String>,
        /** Seuils ou règles régionaux différents du national */
        val reglesSpecifiques: List<String>,
        /** Référence SRGS applicable */
        val srgsReference: String?,
        /** Altitude min de changement de contexte bioclimatique pour cette région */
        val altitudeMontagnardMin: Int,
        /** Risques climatiques régionaux prioritaires (source DRIAS) */
        val risquesClimatiquesRegionaux: List<String>
    )

    /** Résout le contenu pour une région donnée. Fallback national si non défini. */
    fun forRegion(regionCode: String?): RegionalContent =
        REGIONAL_CONTENTS[regionCode] ?: NATIONAL_DEFAULT

    /** Résout les essences prioritaires en combinant national + régional (régional en premier). */
    fun resolveEssencesPrioritaires(regionCode: String?, deptCode: String?): List<String> {
        val dept = DEPARTMENTAL_OVERRIDES[deptCode]
        val regional = REGIONAL_CONTENTS[regionCode]
        return buildList {
            dept?.essencesPrioritaires?.let { addAll(it) }
            regional?.essencesPrioritaires?.let { addAll(it) }
            addAll(NATIONAL_DEFAULT.essencesPrioritaires)
        }.distinct().take(20)
    }

    /** Espèces indicatrices contextuelles (régionales + nationales fusionnées). */
    fun resolveSpeciesIndicatrices(regionCode: String?): List<String> {
        val regional = REGIONAL_CONTENTS[regionCode]
        return buildList {
            regional?.especesIndicatricesRegionales?.let { addAll(it) }
            addAll(NATIONAL_DEFAULT.especesIndicatricesRegionales)
        }.distinct()
    }

    // ─── Contenu national de base ─────────────────────────────────────────────

    val NATIONAL_DEFAULT = RegionalContent(
        regionCode = "FR",
        regionName = "France — Socle national",
        essencesPrioritaires = listOf(
            "Chêne pédonculé", "Chêne sessile", "Hêtre commun",
            "Douglas de Menzies", "Pin sylvestre", "Épicéa commun",
            "Frêne commun", "Merisier", "Charme commun", "Érable sycomore",
            "Aulne glutineux", "Saule blanc", "Bouleau verruqueux",
            "Châtaignier", "Robinier faux-acacia"
        ),
        essencesAEviter = listOf(
            "Peuplier hybride (zones humides non adaptées)",
            "Pin laricio sur sols hydromorphes"
        ),
        especesIndicatricesRegionales = listOf(
            "Anémone sylvie", "Jacinthe des bois", "Fougère aigle",
            "Ronce commune", "Lierre terrestre", "Ortie dioïque",
            "Digitale pourpre", "Sureau noir"
        ),
        associationsTypiques = listOf(
            "Chênaie-charmaie (Carpinion betuli)",
            "Hêtraie calcicole (Cephalanthero-Fagion)",
            "Hêtraie atlantique (Ilici-Fagion)"
        ),
        reglesSpecifiques = emptyList(),
        srgsReference = null,
        altitudeMontagnardMin = 600,
        risquesClimatiquesRegionaux = listOf(
            "Sécheresse estivale croissante",
            "Dépérissement du hêtre en plaine",
            "Vulnérabilité des épicéas (scolytes)"
        )
    )

    // ─── Contenus régionaux différenciés ──────────────────────────────────────

    private val REGIONAL_CONTENTS: Map<String, RegionalContent> = mapOf(

        // ── Île-de-France ──────────────────────────────────────────────────
        "11" to RegionalContent(
            regionCode = "11", regionName = "Île-de-France",
            essencesPrioritaires = listOf(
                "Chêne sessile", "Chêne pédonculé", "Hêtre commun",
                "Charme commun", "Tilleul à grandes feuilles", "Bouleau verruqueux",
                "Pin sylvestre", "Douglas de Menzies", "Érable plane"
            ),
            essencesAEviter = listOf("Robinier (forêts anciennes)", "Ailante glanduleux"),
            especesIndicatricesRegionales = listOf(
                "Jacinthe des bois", "Ornithogale des Pyrénées", "Anémone sylvie",
                "Stellaire holostée", "Millet diffus", "Mélique uniflore"
            ),
            associationsTypiques = listOf(
                "Chênaie-charmaie francilienne (Carpinion)",
                "Hêtraie de Fontainebleau (Luzulo-Fagion)",
                "Boulaie-tremblaie des sables de Fontainebleau"
            ),
            reglesSpecifiques = listOf(
                "Forêts anciennes protégées : TGBT interdits",
                "Présence d'Orchidées rares en lisière calcaire (obligation de signalement)"
            ),
            srgsReference = "SRGS Île-de-France 2006",
            altitudeMontagnardMin = 999,
            risquesClimatiquesRegionaux = listOf(
                "Sécheresse chênaies sur sables (Fontainebleau)",
                "Dépérissement charmes sur argile à silex"
            )
        ),

        // ── Grand Est ──────────────────────────────────────────────────────
        "44" to RegionalContent(
            regionCode = "44", regionName = "Grand Est",
            essencesPrioritaires = listOf(
                "Sapin pectiné", "Épicéa commun", "Hêtre commun",
                "Chêne sessile", "Douglas de Menzies", "Mélèze d'Europe",
                "Pin sylvestre", "Sapin de Nordmann (expérimental)", "Érable sycomore"
            ),
            essencesAEviter = listOf(
                "Épicéa commun (basses altitudes < 400 m, stress scolytes)",
                "Sapin pectiné (versants chauds xérophiles)"
            ),
            especesIndicatricesRegionales = listOf(
                "Myrtille commune", "Luzule blanche", "Aspérule odorante",
                "Prénanthe pourpre", "Fougère femelle", "Violette des forêts",
                "Oxalide petite-oseille", "Calamagrostide velue"
            ),
            associationsTypiques = listOf(
                "Sapinière-hêtraie vosgienne (Luzulo-Fagetum vosgiacum)",
                "Hêtraie calcicole lorraine (Cephalanthero-Fagion)",
                "Chênaie sessiliflore acide (Luzulo-Quercetum)"
            ),
            reglesSpecifiques = listOf(
                "SRGS Grand Est : maintien de la sapinière en Vosges (alt > 500 m)",
                "Rémanents non regroupés obligatoires en sylviculture mélangée"
            ),
            srgsReference = "SRGS Grand Est (fusion Alsace-Champagne-Lorraine) 2023",
            altitudeMontagnardMin = 500,
            risquesClimatiquesRegionaux = listOf(
                "Scolytes sur épicéas (crise 2018-2023)",
                "Défoliation sécheresse sapins Vosges",
                "Remontée des chênaies en altitude"
            )
        ),

        // ── Auvergne-Rhône-Alpes ───────────────────────────────────────────
        "84" to RegionalContent(
            regionCode = "84", regionName = "Auvergne-Rhône-Alpes",
            essencesPrioritaires = listOf(
                "Sapin pectiné", "Épicéa commun", "Mélèze d'Europe",
                "Pin sylvestre", "Hêtre commun", "Douglas de Menzies",
                "Chêne sessile", "Cèdre de l'Atlas (méditerranéen)", "Pin laricio de Corse"
            ),
            essencesAEviter = listOf(
                "Épicéa commun (< 700 m, versants chauds)",
                "Sapin pectiné (versants sud < 1000 m en Alpes méridionales)"
            ),
            especesIndicatricesRegionales = listOf(
                "Myrtille commune", "Violette de Rivinus", "Aspérule odorante",
                "Fétuque hétérophylle", "Calamagrostide velue", "Mélampyre des prés",
                "Cicerbite des Alpes", "Brachypode penné"
            ),
            associationsTypiques = listOf(
                "Sapinière-pessière d'altitude (Abieti-Piceion)",
                "Hêtraie montagnarde (Fagion sylvaticae)",
                "Mélèzin alpin (Rhododendro-Laricion)",
                "Chênaie pubescente méridionale (Quercion pubescenti-petraeae)"
            ),
            reglesSpecifiques = listOf(
                "Forêts de protection : pas de coupe rase dans les Alpes (protection RTM)",
                "Présence de zones Natura 2000 : espèces protégées à signaler",
                "PEFC requis pour forêts > 25 ha en Haute-Savoie, Isère, Savoie"
            ),
            srgsReference = "SRGS Auvergne-Rhône-Alpes 2020",
            altitudeMontagnardMin = 700,
            risquesClimatiquesRegionaux = listOf(
                "Feux de forêts (Drôme, Ardèche, Alpes méridionales)",
                "Sécheresse estivale extrême versants sud",
                "Dépérissement frêne (chalarose) en vallées"
            )
        ),

        // ── Nouvelle-Aquitaine ─────────────────────────────────────────────
        "75" to RegionalContent(
            regionCode = "75", regionName = "Nouvelle-Aquitaine",
            essencesPrioritaires = listOf(
                "Pin maritime", "Chêne pédonculé", "Chêne liège (Sud-Aquitaine)",
                "Douglas de Menzies", "Chêne sessile", "Hêtre commun",
                "Pin laricio de Corse", "Aulne glutineux", "Frêne commun"
            ),
            essencesAEviter = listOf(
                "Épicéa commun (inadapté au climat atlantique chaud)",
                "Pin sylvestre (risque scolytes en plaine landaise)"
            ),
            especesIndicatricesRegionales = listOf(
                "Molinie bleue", "Fougère aigle", "Bruyère cendrée",
                "Callune vulgaire", "Genêt à balai", "Ajonc d'Europe",
                "Sphaigne (zones humides)", "Oseille des bois"
            ),
            associationsTypiques = listOf(
                "Pinède maritime landaise (Molinio-Pinion)",
                "Chênaie pédonculée atlantique (Molinio-Quercetum)",
                "Chênaie-charmaie périgordine (Brachypodio-Carpinetum)",
                "Aulnaie-frênaie ripicole (Alno-Padion)"
            ),
            reglesSpecifiques = listOf(
                "Forêt des Landes : plan de reboisement post-tempête obligatoire",
                "Zones humides : diagnostic ripisylve requis avant tout travaux en bordure Garonne/Dordogne",
                "Chêne liège : protection spécifique en Pyrénées-Atlantiques"
            ),
            srgsReference = "SRGS Nouvelle-Aquitaine 2021 (ex-Aquitaine + Poitou-Charentes + Limousin)",
            altitudeMontagnardMin = 800,
            risquesClimatiquesRegionaux = listOf(
                "Tempêtes atlantiques (Klaus 2009 — risque récurrent)",
                "Incendies forêts Landes (sécheresse estivale)",
                "Dépérissement pin maritime chaleur/sécheresse"
            )
        ),

        // ── Occitanie ──────────────────────────────────────────────────────
        "76" to RegionalContent(
            regionCode = "76", regionName = "Occitanie",
            essencesPrioritaires = listOf(
                "Chêne pubescent", "Chêne vert", "Cèdre de l'Atlas",
                "Pin noir d'Autriche", "Pin sylvestre", "Sapin de Céphalonie",
                "Hêtre commun", "Sapin pectiné (montagne)", "Pin laricio"
            ),
            essencesAEviter = listOf(
                "Épicéa commun (inadapté méditerranéen)",
                "Frêne commun (chalarose + sécheresse)"
            ),
            especesIndicatricesRegionales = listOf(
                "Kermès (Quercus coccifera)", "Ciste à feuilles de sauge",
                "Doradille (Asplenium spp.)", "Brachypode penné",
                "Aphyllante de Montpellier", "Coronille arbrisseau",
                "Romarin", "Lavande officinale"
            ),
            associationsTypiques = listOf(
                "Chênaie pubescente supra-méditerranéenne (Quercion pubescenti-petraeae)",
                "Garrigue à Quercus coccifera (Quercion ilicis)",
                "Hêtraie-sapinière pyrénéenne (Galio rotundifolii-Abietion)",
                "Pinède de pin sylvestre caussenarde"
            ),
            reglesSpecifiques = listOf(
                "DFCI (Défense des Forêts Contre l'Incendie) : plans obligatoires dpts 30, 34, 66",
                "Zones Natura 2000 nombreuses : habitats 9340, 9150 fréquents",
                "Cèdre : expansion naturelle surveillée (Aigoual, Ventoux)"
            ),
            srgsReference = "SRGS Occitanie 2019",
            altitudeMontagnardMin = 900,
            risquesClimatiquesRegionaux = listOf(
                "Incendies (risque extrême dpts méditerranéens)",
                "Dépérissement chêne pubescent sécheresse",
                "Invasion Dendrolimus pini (processionnaire du pin)"
            )
        ),

        // ── Bourgogne-Franche-Comté ────────────────────────────────────────
        "27" to RegionalContent(
            regionCode = "27", regionName = "Bourgogne-Franche-Comté",
            essencesPrioritaires = listOf(
                "Chêne sessile", "Chêne pédonculé", "Hêtre commun",
                "Douglas de Menzies", "Épicéa commun", "Sapin pectiné",
                "Pin sylvestre", "Érable sycomore", "Frêne commun"
            ),
            essencesAEviter = listOf(
                "Frêne commun (chalarose généralisée)",
                "Épicéa commun (basses altitudes Côte-d'Or)"
            ),
            especesIndicatricesRegionales = listOf(
                "Anémone pulsatille", "Garance voyageuse", "Aspérule odorante",
                "Mélique uniflore", "Orchis militaire", "Hellébore fétide",
                "Ornithogale des Pyrénées", "Laîche des bois"
            ),
            associationsTypiques = listOf(
                "Chênaie-charmaie calcicole (Carpinion betuli — Côte-d'Or)",
                "Hêtraie calcicole bourguignonne (Cephalanthero-Fagion)",
                "Sapinière-hêtraie jurassienne (Abieti-Fagetum jurassicum)",
                "Chênaie pédonculée à Molinie (Molinio-Quercetum)"
            ),
            reglesSpecifiques = listOf(
                "Futaie irrégulière recommandée en Morvan (CRPF Bourgogne)",
                "Îlots de vieillissement obligatoires > 50 ha (charte forestière locale)",
                "Chênaie de la Côte viticole : protections paysagères"
            ),
            srgsReference = "SRGS Bourgogne-Franche-Comté 2019",
            altitudeMontagnardMin = 600,
            risquesClimatiquesRegionaux = listOf(
                "Sécheresse chênaies Morvan (2018–2022)",
                "Dépérissement chêne pédonculé sur sols lourds",
                "Scolytes épicéas Jura"
            )
        ),

        // ── Bretagne ──────────────────────────────────────────────────────
        "53" to RegionalContent(
            regionCode = "53", regionName = "Bretagne",
            essencesPrioritaires = listOf(
                "Chêne pédonculé", "Hêtre commun", "Douglas de Menzies",
                "Épicéa de Sitka", "Pin maritime", "Mélèze du Japon",
                "Aulne glutineux", "Saule marsault", "Frêne commun"
            ),
            essencesAEviter = listOf(
                "Épicéa commun (préférer Sitka pour résistance tempêtes)",
                "Frêne (chalarose)"
            ),
            especesIndicatricesRegionales = listOf(
                "Digitale pourpre", "Ajonc d'Europe", "Bruyère cendrée",
                "Jacée des prés", "Scille printanière", "Jacinthe des bois",
                "Cresson des fontaines (ripisylve)", "Renoncule à feuilles de lierre"
            ),
            associationsTypiques = listOf(
                "Chênaie sessile atlantique (Lonicero-Quercetum petraeae)",
                "Chênaie pédonculée à Molinie (Molinio-Quercetum roboris)",
                "Aulnaie-frênaie atlantique (Carici pendulae-Fraxinetum)"
            ),
            reglesSpecifiques = listOf(
                "Bocage : haies bocagères protégées (Loi Paysage)",
                "Zones humides : 75 % du territoire breton en SDAGE Loire-Bretagne",
                "Ripisylve : diagnostic obligatoire sur cours d'eau classés liste 1"
            ),
            srgsReference = "SRGS Bretagne 2010",
            altitudeMontagnardMin = 999,
            risquesClimatiquesRegionaux = listOf(
                "Tempêtes atlantiques (Lothar 99, Klaus 2009, Ciarán 2023)",
                "Déracinement épicéas sol superficiel",
                "Submersion des aulnaies côtières (montée niveau mer)"
            )
        ),

        // ── Pays de la Loire ───────────────────────────────────────────────
        "52" to RegionalContent(
            regionCode = "52", regionName = "Pays de la Loire",
            essencesPrioritaires = listOf(
                "Chêne pédonculé", "Chêne sessile", "Pin maritime",
                "Douglas de Menzies", "Hêtre commun", "Châtaignier",
                "Aulne glutineux", "Robinier (plantations dégradées)"
            ),
            essencesAEviter = listOf("Frêne commun (chalarose)"),
            especesIndicatricesRegionales = listOf(
                "Bruyère à balai", "Callune vulgaire", "Molinie bleue",
                "Oseille des bois", "Laîche des renards", "Jonc épars"
            ),
            associationsTypiques = listOf(
                "Chênaie pédonculée sur sol hydromorphe (Molinio-Quercetum)",
                "Pinède maritime bocagère"
            ),
            reglesSpecifiques = listOf(
                "Zones bocagères : protections Loi Paysage",
                "Val de Loire UNESCO : pas de plantations résineuses en plaine alluviale"
            ),
            srgsReference = "SRGS Pays de la Loire 2015",
            altitudeMontagnardMin = 999,
            risquesClimatiquesRegionaux = listOf(
                "Inondations Loire (zones inondables)",
                "Sécheresse chênaies sur argile"
            )
        ),

        // ── Provence-Alpes-Côte d'Azur ─────────────────────────────────────
        "93" to RegionalContent(
            regionCode = "93", regionName = "Provence-Alpes-Côte d'Azur",
            essencesPrioritaires = listOf(
                "Chêne pubescent", "Chêne vert", "Cèdre de l'Atlas",
                "Pin sylvestre", "Pin noir d'Autriche", "Mélèze d'Europe",
                "Sapin de Céphalonie", "Lariciosylvestre (hybride naturel)"
            ),
            essencesAEviter = listOf(
                "Épicéa commun (inadapté)",
                "Pin d'Alep (zones à risque incendie fort)"
            ),
            especesIndicatricesRegionales = listOf(
                "Romarin", "Lavande officinale", "Aphyllante de Montpellier",
                "Kermès (Quercus coccifera)", "Cytise en arbre",
                "Buis commun", "Daphné lauréole", "Hellébore fétide"
            ),
            associationsTypiques = listOf(
                "Chênaie verte méso-méditerranéenne (Quercion ilicis)",
                "Chênaie pubescente provençale",
                "Mélèzin subalpin (Rhododendro-Laricion)"
            ),
            reglesSpecifiques = listOf(
                "DFCI obligatoire : débroussaillement 50 m autour habitations",
                "Réserves biologiques ONF nombreuses : diagnostic espèces protégées requis",
                "Natura 2000 : 40 % du territoire régional"
            ),
            srgsReference = "SRGS PACA 2016",
            altitudeMontagnardMin = 800,
            risquesClimatiquesRegionaux = listOf(
                "Risque incendie EXTRÊME (DFCI plan régional)",
                "Aridification versants sud < 1000 m",
                "Dépérissement pin sylvestre chaleur (Var, Alpes-Maritimes)"
            )
        ),

        // ── Hauts-de-France ────────────────────────────────────────────────
        "32" to RegionalContent(
            regionCode = "32", regionName = "Hauts-de-France",
            essencesPrioritaires = listOf(
                "Chêne pédonculé", "Chêne sessile", "Hêtre commun",
                "Érable sycomore", "Frêne commun", "Aulne glutineux",
                "Merisier", "Bouleau verruqueux", "Pin sylvestre"
            ),
            essencesAEviter = listOf(
                "Frêne commun (chalarose avancée dans la région)",
                "Épicéa commun (tempêtes + scolytes)"
            ),
            especesIndicatricesRegionales = listOf(
                "Jacinthe des bois", "Anémone sylvie", "Ail des ours",
                "Ficaire fausse-renoncule", "Laîche espacée",
                "Sanicle d'Europe", "Aspérule odorante"
            ),
            associationsTypiques = listOf(
                "Chênaie-charmaie septentrionale (Endymio-Carpinetum)",
                "Aulnaie-frênaie septentrionale (Alno-Padion)",
                "Hêtraie neutrophile (Endymio-Fagetum)"
            ),
            reglesSpecifiques = listOf(
                "Ripisylve : méthode CRPF Hauts-de-France applicable (Forêt-Entreprise n°242)",
                "Bocage : inventaire haies obligatoire Somme, Nord-Pas-de-Calais",
                "Zones humides Marquenterre : diagnostic faune/flore requis"
            ),
            srgsReference = "SRGS Hauts-de-France 2020",
            altitudeMontagnardMin = 999,
            risquesClimatiquesRegionaux = listOf(
                "Tempêtes (exposé vents de NO)",
                "Dépérissement frênes chalarose",
                "Sécheresse croissante chênaies (été 2022)"
            )
        ),

        // ── Normandie ─────────────────────────────────────────────────────
        "28" to RegionalContent(
            regionCode = "28", regionName = "Normandie",
            essencesPrioritaires = listOf(
                "Hêtre commun", "Chêne sessile", "Chêne pédonculé",
                "Douglas de Menzies", "Érable sycomore", "Charme commun",
                "Aulne glutineux", "Frêne commun", "Merisier"
            ),
            essencesAEviter = listOf("Frêne commun (chalarose)"),
            especesIndicatricesRegionales = listOf(
                "Jacinthe des bois", "Anémone sylvie", "Campanule à feuilles rondes",
                "Sceau de Salomon multiflore", "Canche flexueuse",
                "Asaret d'Europe", "Mercuriale vivace"
            ),
            associationsTypiques = listOf(
                "Hêtraie normande atlantique (Ilici-Fagion)",
                "Chênaie-charmaie normande",
                "Aulnaie-frênaie de bord de Seine"
            ),
            reglesSpecifiques = listOf(
                "Hêtraies à if (Taxus baccata) : protection stricte basse Normandie",
                "Forêts domaniales normandes : régime RTM après tempête Lothar/Martin"
            ),
            srgsReference = "SRGS Normandie 2019 (ex-HN + BN)",
            altitudeMontagnardMin = 999,
            risquesClimatiquesRegionaux = listOf(
                "Tempêtes atlantiques",
                "Dépérissement hêtre (sécheresse sol argileux)",
                "Chalarose du frêne"
            )
        ),

        // ── Centre-Val de Loire ────────────────────────────────────────────
        "24" to RegionalContent(
            regionCode = "24", regionName = "Centre-Val de Loire",
            essencesPrioritaires = listOf(
                "Chêne sessile", "Chêne pédonculé", "Pin sylvestre",
                "Douglas de Menzies", "Hêtre commun", "Châtaignier",
                "Merisier", "Bouleau verruqueux", "Aulne glutineux"
            ),
            essencesAEviter = listOf(
                "Frêne commun (chalarose)",
                "Épicéa commun (inadapté sologne)"
            ),
            especesIndicatricesRegionales = listOf(
                "Molinie bleue", "Bruyère cendrée", "Myrtille commune",
                "Fougère aigle", "Airelle des marais", "Bourdaine",
                "Laîche des renards", "Scorzonère des prés"
            ),
            associationsTypiques = listOf(
                "Chênaie pédonculée de Sologne (Molinio-Quercetum)",
                "Chênaie sessile sur sables de Beauce",
                "Pinède sylvestre sur Sologne"
            ),
            reglesSpecifiques = listOf(
                "Forêt de Sologne : PSG obligatoire > 10 ha",
                "Forêt d'Orléans (domaniale) : zones de quiétude chiroptères"
            ),
            srgsReference = "SRGS Centre-Val de Loire 2006",
            altitudeMontagnardMin = 999,
            risquesClimatiquesRegionaux = listOf(
                "Sécheresse chênaies Sologne (2018–2022)",
                "Dépérissement chêne pédonculé sol hydromorphe asséché"
            )
        ),

        // ── Corse ─────────────────────────────────────────────────────────
        "94" to RegionalContent(
            regionCode = "94", regionName = "Corse",
            essencesPrioritaires = listOf(
                "Pin laricio de Corse", "Chêne vert", "Châtaignier",
                "Hêtre commun", "Pin maritime", "Aulne de Corse",
                "Sapin de Corse (Abies nebrodensis — endémique critique)"
            ),
            essencesAEviter = listOf(
                "Eucalyptus globulus (invasif)",
                "Robinier faux-acacia (invasif en Haute-Corse)"
            ),
            especesIndicatricesRegionales = listOf(
                "Bruyère arborescente (Erica arborea)",
                "Arbousier commun", "Maquis à ciste",
                "Asphodèle cerise", "Helléborine de Müller"
            ),
            associationsTypiques = listOf(
                "Pinède de Pin laricio corse (Veronico-Pinetum laricis)",
                "Hêtraie corse (Galio rotundifolii-Fagetum corsicum)",
                "Maquis mésoméditerranéen à Erica arborea"
            ),
            reglesSpecifiques = listOf(
                "DFCI : débroussaillement obligatoire 100 m habitations",
                "Sapin de Corse (Abies nebrodensis) : espèce protégée, signalement obligatoire",
                "Plan régional de la forêt corse : sylviculture proche de la nature"
            ),
            srgsReference = "SRGS Corse 2015",
            altitudeMontagnardMin = 700,
            risquesClimatiquesRegionaux = listOf(
                "Incendies extrêmes (2017 : 10 000 ha brûlés)",
                "Aridification maquis côtier",
                "Dépérissement pin maritime côtier"
            )
        )
    )

    // ─── Sur-couches départementales ──────────────────────────────────────────
    // Espèces et règles spécifiques à certains départements forestiers clés

    data class DeptContent(
        val deptCode: String,
        val essencesPrioritaires: List<String>,
        val especesProtegees: List<String>,       // Espèces protégées à signaler
        val reglesSpecifiques: List<String>
    )

    private val DEPARTMENTAL_OVERRIDES: Map<String, DeptContent> = mapOf(
        "63" to DeptContent("63",
            essencesPrioritaires = listOf("Douglas de Menzies", "Sapin pectiné", "Épicéa commun", "Hêtre commun"),
            especesProtegees = listOf("Listère ovale", "Orchis moucheron", "Platanthère chlorantha"),
            reglesSpecifiques = listOf("Puy de Dôme : forêts de protection RTM versants")
        ),
        "88" to DeptContent("88",
            essencesPrioritaires = listOf("Sapin pectiné", "Épicéa commun", "Hêtre commun", "Douglas de Menzies"),
            especesProtegees = listOf("Orchis tachetée", "Lycopode en massue"),
            reglesSpecifiques = listOf("Vosges : maintien mélange sapin-épicéa-hêtre recommandé CRPF")
        ),
        "73" to DeptContent("73",
            essencesPrioritaires = listOf("Mélèze d'Europe", "Arolle", "Sapin pectiné", "Épicéa commun"),
            especesProtegees = listOf("Soldanelle des Alpes", "Arnica des montagnes", "Lys martagon"),
            reglesSpecifiques = listOf("Savoie : forêts de protection RTM — pas de coupe rase < 1800 m")
        ),
        "06" to DeptContent("06",
            essencesPrioritaires = listOf("Pin sylvestre", "Chêne pubescent", "Cèdre de l'Atlas", "Pin laricio"),
            especesProtegees = listOf("Fritillaire involucrata", "Tulipe sylvestre", "Violette de Valdérie"),
            reglesSpecifiques = listOf("DFCI obligatoire", "Natura 2000 : 65 % du département")
        ),
        "48" to DeptContent("48",
            essencesPrioritaires = listOf("Pin sylvestre", "Sapin pectiné", "Épicéa commun", "Hêtre commun"),
            especesProtegees = listOf("Narcisse trompette", "Arnica des montagnes"),
            reglesSpecifiques = listOf("Lozère : causse calcaire / mont Lozère : distinction bioclimatique forte")
        ),
        "40" to DeptContent("40",
            essencesPrioritaires = listOf("Pin maritime", "Chêne pédonculé", "Aulne glutineux"),
            especesProtegees = listOf("Drosera rotundifolia (tourbières)", "Osmonde royale"),
            reglesSpecifiques = listOf("Landes : forêt de production — plan reboisement systématique tempête")
        )
    )
}
