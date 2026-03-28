package com.forestry.counter.domain.usecase.sylviculture

/**
 * Catalogue des types forestiers français — habitats Natura 2000 + types stationnels.
 *
 * Sources : EUR28 (Habitats Natura 2000), CORINE Biotopes,
 *           Cahiers habitats ONF/MNHN, Flore Forestière Française (Rameau, Mansion, Dumé),
 *           Catalogues de stations ONF par massif.
 */
object ForestHabitatCatalog {

    data class HabitatForestier(
        val id: String,
        val codeEur28: String,          // ex: "9130"
        val codeCorine: String,         // ex: "41.13"
        val nomFr: String,
        val nomSci: String,
        val regionsFrance: List<String>,

        // Conditions stationnelles
        val phMin: Double,
        val phMax: Double,
        val altitudeMin: Int,
        val altitudeMax: Int,
        val climateTypes: List<String>, // codes ClimateType
        val humusTypes: List<String>,   // Mull, Moder, Mor, Anmoor...
        val drainageTypes: List<String>,

        // Composition floristique
        val essencesArborescentes: List<String>,   // IDs SylvicultureDatabase
        val especesIndicatrices: List<String>,     // noms sci espèces indicatrices (FloristDatabase)
        val especesExcluantes: List<String>,       // espèces incompatibles (signal de mauvaise détermination)

        // Statut
        val estPrioritaire: Boolean = false,       // habitat prioritaire Natura 2000
        val etatConservation: String = "Favorable",
        val notesSylvicoles: String
    )

    // ─── Lookup ───────────────────────────────────────────────────────────────

    fun findById(id: String): HabitatForestier? = ALL.find { it.id.equals(id, ignoreCase = true) }

    fun findByCodeEur28(code: String): HabitatForestier? = ALL.find { it.codeEur28 == code }

    /** Retourne les habitats compatibles avec un contexte pH + altitude + ClimateType. */
    fun matchHabitats(ph: Double, altM: Double, climateType: String): List<HabitatForestier> =
        ALL.filter { h ->
            ph in h.phMin..h.phMax &&
            altM.toInt() in h.altitudeMin..h.altitudeMax &&
            (h.climateTypes.isEmpty() || h.climateTypes.any { it.equals(climateType, ignoreCase = true) })
        }.sortedBy { it.codeEur28 }

    /** Retourne les habitats compatibles avec une liste d'espèces observées. */
    fun matchByFlora(observedSpeciesIds: List<String>): List<Pair<HabitatForestier, Int>> =
        ALL.map { h ->
            val matches = h.especesIndicatrices.count { ind ->
                observedSpeciesIds.any { obs -> obs.equals(ind, ignoreCase = true) }
            }
            Pair(h, matches)
        }.filter { it.second > 0 }.sortedByDescending { it.second }

    // ─── Base de données ─────────────────────────────────────────────────────

    val ALL: List<HabitatForestier> = listOf(

        // ══ HÊTRAIES ════════════════════════════════════════════════════════

        HabitatForestier(
            id = "HETRE_ACID", codeEur28 = "9110", codeCorine = "41.11",
            nomFr = "Hêtraie-chênaie acidiphile à Luzule (Luzulo-Fagetum)",
            nomSci = "Luzulo-Fagetum",
            regionsFrance = listOf("Vosges", "Ardennes", "Massif Central", "Bretagne", "Normandie"),
            phMin = 3.5, phMax = 5.5, altitudeMin = 200, altitudeMax = 1200,
            climateTypes = listOf("OCEANIQUE", "SEMI_OCEANIQUE", "CONTINENTAL", "MONTAGNARD"),
            humusTypes = listOf("Mor", "Moder"),
            drainageTypes = listOf("BON", "NORMAL"),
            essencesArborescentes = listOf("FASY", "QUPES", "BIPE"),
            especesIndicatrices = listOf(
                "Luzula luzuloides", "Luzula sylvatica", "Deschampsia flexuosa",
                "Vaccinium myrtillus", "Calluna vulgaris", "Pteridium aquilinum",
                "Sorbus aucuparia", "Polytrichum formosum"
            ),
            especesExcluantes = listOf("Mercurialis perennis", "Asperula odorata"),
            estPrioritaire = false,
            notesSylvicoles = "Peuplements acidiphiles sur grès, granites et schistes. Sol brun acide à podzolisé. Myrtille abondante = indicateur diagnostic fort. Production modeste du hêtre. Favoriser sylviculture douce et régénération naturelle."
        ),

        HabitatForestier(
            id = "HETRE_ATL", codeEur28 = "9120", codeCorine = "41.12",
            nomFr = "Hêtraie atlantique acidiphile à Houx",
            nomSci = "Quercion robori-petraeae, Ilici-Fagion",
            regionsFrance = listOf("Bretagne", "Normandie", "Pays de Loire", "Pyrénées"),
            phMin = 4.0, phMax = 6.0, altitudeMin = 0, altitudeMax = 800,
            climateTypes = listOf("OCEANIQUE", "SEMI_OCEANIQUE"),
            humusTypes = listOf("Moder", "Mull-Moder"),
            drainageTypes = listOf("BON", "NORMAL"),
            essencesArborescentes = listOf("FASY", "QUPES", "QUPE"),
            especesIndicatrices = listOf(
                "Ilex aquifolium", "Deschampsia flexuosa", "Blechnum spicant",
                "Luzula sylvatica", "Oxalis acetosella", "Polystichum setiferum",
                "Lonicera periclymenum"
            ),
            especesExcluantes = listOf("Mercurialis perennis", "Hepatica nobilis"),
            notesSylvicoles = "Hêtraie à Houx typique du domaine atlantique. Présence du Houx en sous-bois diagnostic. Gestion en futaie jardinée conseillée pour maintenir la strate arbustive."
        ),

        HabitatForestier(
            id = "HETRE_CALC", codeEur28 = "9130", codeCorine = "41.13",
            nomFr = "Hêtraie neutrophile (Asperulo-Fagetum)",
            nomSci = "Asperulo-Fagetum",
            regionsFrance = listOf("Ile-de-France", "Bourgogne", "Lorraine", "Normandie", "Alsace"),
            phMin = 5.5, phMax = 7.5, altitudeMin = 100, altitudeMax = 1000,
            climateTypes = listOf("OCEANIQUE", "SEMI_OCEANIQUE", "CONTINENTAL"),
            humusTypes = listOf("Mull", "Mull-Moder"),
            drainageTypes = listOf("BON", "NORMAL"),
            essencesArborescentes = listOf("FASY", "QUPES", "ACPS", "FREX"),
            especesIndicatrices = listOf(
                "Galium odoratum", "Mercurialis perennis", "Sanicula europaea",
                "Oxalis acetosella", "Dryopteris filix-mas", "Milium effusum",
                "Anemone nemorosa", "Carex sylvatica"
            ),
            especesExcluantes = listOf("Vaccinium myrtillus", "Calluna vulgaris"),
            notesSylvicoles = "Hêtraie sur limons et argiles à silex — la plus productive des hêtraies. Riche flore printanière. Gestion en futaie régulière ou jardinée. Riche en espèces hygrophiles en fond de vallon."
        ),

        HabitatForestier(
            id = "HETRE_CALC2", codeEur28 = "9150", codeCorine = "41.16",
            nomFr = "Hêtraie calcicole xérophile (Cephalanthero-Fagion)",
            nomSci = "Cephalanthero-Fagion",
            regionsFrance = listOf("Champagne", "Bourgogne", "Jura", "Alpes", "Pyrénées"),
            phMin = 6.5, phMax = 8.5, altitudeMin = 200, altitudeMax = 1400,
            climateTypes = listOf("CONTINENTAL", "SEMI_CONTINENTAL", "MONTAGNARD"),
            humusTypes = listOf("Mull calcique", "Mull"),
            drainageTypes = listOf("BON", "EXCESSIF"),
            essencesArborescentes = listOf("FASY", "ACPS"),
            especesIndicatrices = listOf(
                "Cephalanthera damasonium", "Cephalanthera longifolia",
                "Neottia nidus-avis", "Carex alba", "Sesleria albicans",
                "Hepatica nobilis", "Polygonatum odoratum", "Vincetoxicum hirundinaria"
            ),
            especesExcluantes = listOf("Vaccinium myrtillus", "Deschampsia flexuosa"),
            notesSylvicoles = "Hêtraie sur calcaire dur — sol peu profond. Orchidées nombreuses = indicateurs diagnostics. Production limitée. Valeur écologique élevée. Gestion conservatoire recommandée."
        ),

        // ══ CHÊNAIES-CHARMAIES ═══════════════════════════════════════════════

        HabitatForestier(
            id = "CHENAIE_CARM_ATL", codeEur28 = "9160", codeCorine = "41.24",
            nomFr = "Chênaie-charmaie sub-atlantique",
            nomSci = "Stellario-Carpinetum, Endymio-Carpinetum",
            regionsFrance = listOf("Ile-de-France", "Bretagne", "Normandie", "Pays de Loire", "Centre"),
            phMin = 5.0, phMax = 7.0, altitudeMin = 0, altitudeMax = 500,
            climateTypes = listOf("OCEANIQUE", "SEMI_OCEANIQUE"),
            humusTypes = listOf("Mull", "Mull-Moder"),
            drainageTypes = listOf("NORMAL", "IMPARFAIT"),
            essencesArborescentes = listOf("QUPE", "CABE", "FREX", "TICO"),
            especesIndicatrices = listOf(
                "Hyacinthoides non-scripta", "Stellaria holostea", "Lamiastrum galeobdolon",
                "Lonicera periclymenum", "Carpinus betulus", "Primula elatior",
                "Ranunculus ficaria", "Adoxa moschatellina"
            ),
            especesExcluantes = listOf("Vaccinium myrtillus", "Calluna vulgaris"),
            notesSylvicoles = "Forêt de plaine la plus commune de l'ouest de la France. Sol frais à légèrement hydromorphe. Taillis sous futaie traditionnel ou futaie régulière. Riche diversité floristique printanière."
        ),

        HabitatForestier(
            id = "CHENAIE_CARM_CONT", codeEur28 = "9170", codeCorine = "41.26",
            nomFr = "Chênaie-charmaie sub-continentale (Galio-Carpinetum)",
            nomSci = "Galio sylvatici-Carpinetum",
            regionsFrance = listOf("Lorraine", "Alsace", "Bourgogne", "Champagne", "Nord"),
            phMin = 5.5, phMax = 7.5, altitudeMin = 50, altitudeMax = 600,
            climateTypes = listOf("SEMI_CONTINENTAL", "CONTINENTAL"),
            humusTypes = listOf("Mull", "Mull calcique"),
            drainageTypes = listOf("BON", "NORMAL"),
            essencesArborescentes = listOf("QUPE", "QUPES", "CABE", "TICO", "ACPS"),
            especesIndicatrices = listOf(
                "Galium sylvaticum", "Carex digitata", "Hepatica nobilis",
                "Convallaria majalis", "Lilium martagon", "Melica uniflora",
                "Vincetoxicum hirundinaria"
            ),
            especesExcluantes = listOf("Hyacinthoides non-scripta", "Vaccinium myrtillus"),
            notesSylvicoles = "Chênaie-charmaie continentale sur limons calcaires. Très belle productivité du chêne pédonculé. Taillis sous futaie historique. Transition avec hêtraie possible en altitude."
        ),

        // ══ CHÊNAIES ACIDIPHILES ════════════════════════════════════════════

        HabitatForestier(
            id = "CHENAIE_ACID", codeEur28 = "9190", codeCorine = "41.51",
            nomFr = "Chênaie acidiphile à Molinie sur sables",
            nomSci = "Molinio-Quercetum, Betulo-Quercetum",
            regionsFrance = listOf("Landes", "Sologne", "Champagne pouilleuse", "Bretagne"),
            phMin = 3.5, phMax = 5.5, altitudeMin = 0, altitudeMax = 300,
            climateTypes = listOf("OCEANIQUE", "SEMI_OCEANIQUE", "SEMI_CONTINENTAL"),
            humusTypes = listOf("Mor", "Moder"),
            drainageTypes = listOf("NORMAL", "IMPARFAIT", "MAUVAIS"),
            essencesArborescentes = listOf("QUPE", "BIPE", "PISY"),
            especesIndicatrices = listOf(
                "Molinia caerulea", "Deschampsia flexuosa", "Calluna vulgaris",
                "Vaccinium myrtillus", "Potentilla erecta", "Genista anglica",
                "Carex pilulifera"
            ),
            especesExcluantes = listOf("Mercurialis perennis", "Hepatica nobilis"),
            notesSylvicoles = "Forêt sur sables acides pauvres et engorgés. Production limitée. Riche en Molinie et Callune. Intérêt faunistique (engoulevent, lézard des souches). Gestion conservatoire en forêt de protection."
        ),

        // ══ FORÊTS D'ÉBOULIS ET RAVIN ════════════════════════════════════════

        HabitatForestier(
            id = "TILIO_ACERION", codeEur28 = "9180", codeCorine = "41.4",
            nomFr = "Forêts de ravins et d'éboulis (Tilio-Acerion)",
            nomSci = "Tilio platyphylli-Acerion pseudoplatani",
            regionsFrance = listOf("Alpes", "Jura", "Massif Central", "Vosges", "Pyrénées"),
            phMin = 5.0, phMax = 8.0, altitudeMin = 300, altitudeMax = 1600,
            climateTypes = listOf("MONTAGNARD", "SEMI_CONTINENTAL"),
            humusTypes = listOf("Mull", "Mull calcique"),
            drainageTypes = listOf("BON", "EXCESSIF"),
            essencesArborescentes = listOf("ACPS", "TICO", "FREX", "ULSP"),
            especesIndicatrices = listOf(
                "Lunaria rediviva", "Aruncus dioicus", "Cicerbita alpina",
                "Actaea spicata", "Gymnocarpium dryopteris", "Polystichum aculeatum",
                "Impatiens noli-tangere"
            ),
            especesExcluantes = listOf("Calluna vulgaris", "Vaccinium myrtillus"),
            estPrioritaire = true,
            notesSylvicoles = "Habitat prioritaire Natura 2000. Forêts de haute valeur écologique sur éboulis frais. Productivité faible mais bois de haute qualité (sycomore). Gestion minimale, laisser évoluer naturellement."
        ),

        // ══ FORÊTS ALLUVIALES / RIPISYLVES ════════════════════════════════════

        HabitatForestier(
            id = "AULNAIE_FRESNAIE", codeEur28 = "91E0", codeCorine = "44.3",
            nomFr = "Aulnaies-frênaies riveraines (Alno-Padion)",
            nomSci = "Alno-Padion, Alnion incanae, Salicion albae",
            regionsFrance = listOf("toute France", "Bords de cours d'eau"),
            phMin = 5.5, phMax = 7.5, altitudeMin = 0, altitudeMax = 1500,
            climateTypes = listOf("OCEANIQUE", "SEMI_OCEANIQUE", "CONTINENTAL", "MONTAGNARD"),
            humusTypes = listOf("Anmoor", "Mull"),
            drainageTypes = listOf("MAUVAIS", "TRES_MAUVAIS", "IMPARFAIT"),
            essencesArborescentes = listOf("ALGL", "FREX", "SASP"),
            especesIndicatrices = listOf(
                "Carex remota", "Cardamine amara", "Chrysosplenium oppositifolium",
                "Festuca gigantea", "Circaea lutetiana", "Filipendula ulmaria",
                "Lycopus europaeus", "Solanum dulcamara", "Iris pseudacorus"
            ),
            especesExcluantes = listOf("Vaccinium myrtillus", "Calluna vulgaris"),
            estPrioritaire = true,
            notesSylvicoles = "Habitat prioritaire Natura 2000. Ripisylve de haute valeur écologique — corridor biologique. Protection des berges contre l'érosion. ATTENTION : chalarose du frêne + Phytophthora alni. Éviter les coupes rases. Favoriser l'aulne glutineux."
        ),

        HabitatForestier(
            id = "FORET_ALLUVIALE", codeEur28 = "91F0", codeCorine = "44.4",
            nomFr = "Forêts mixtes alluviales de Chêne-Orme-Frêne",
            nomSci = "Querco-Ulmetum minoris",
            regionsFrance = listOf("Loire", "Garonne", "Rhône", "grandes plaines alluviales"),
            phMin = 6.0, phMax = 8.0, altitudeMin = 0, altitudeMax = 400,
            climateTypes = listOf("OCEANIQUE", "SEMI_OCEANIQUE", "CONTINENTAL"),
            humusTypes = listOf("Mull", "Anmoor"),
            drainageTypes = listOf("NORMAL", "IMPARFAIT"),
            essencesArborescentes = listOf("QUPE", "FREX", "ULSP", "ACCA"),
            especesIndicatrices = listOf(
                "Ulmus minor", "Carex strigosa", "Stachys sylvatica",
                "Poa trivialis", "Rubus caesius", "Aegopodium podagraria"
            ),
            especesExcluantes = listOf("Vaccinium myrtillus", "Deschampsia flexuosa"),
            estPrioritaire = false,
            notesSylvicoles = "Forêt de grande valeur patrimoniale et cynégétique. Sol alluvial profond et riche. Production chêne pédonculé élevée. Vulnérabilité aux crues — pas de drainage. Espèces rares en sous-bois."
        ),

        // ══ FORÊTS MÉDITERRANÉENNES ══════════════════════════════════════════

        HabitatForestier(
            id = "SUBERERAIE", codeEur28 = "9330", codeCorine = "45.2",
            nomFr = "Forêts de Chêne liège",
            nomSci = "Quercetum suberis",
            regionsFrance = listOf("Var", "Landes", "Pyrénées-Orientales", "Corse"),
            phMin = 4.0, phMax = 6.5, altitudeMin = 0, altitudeMax = 700,
            climateTypes = listOf("MEDITERRANEEN", "SEMI_OCEANIQUE"),
            humusTypes = listOf("Moder", "Mor"),
            drainageTypes = listOf("BON", "EXCESSIF"),
            essencesArborescentes = listOf("QUCO", "QUIL", "ARBU"),
            especesIndicatrices = listOf(
                "Arbutus unedo", "Cistus monspeliensis", "Pistacia lentiscus",
                "Erica arborea", "Erica scoparia", "Lavandula stoechas"
            ),
            especesExcluantes = listOf("Fagus sylvatica", "Abies alba"),
            notesSylvicoles = "Forêt méditerranéenne sur sol siliceux acide. Production de liège. Écorçage tous les 9–12 ans. Fort risque incendie — débroussaillage et sylvopastoralisme recommandés."
        ),

        HabitatForestier(
            id = "YEUSE", codeEur28 = "9340", codeCorine = "45.3",
            nomFr = "Yeuseraie — Forêt de Chêne vert",
            nomSci = "Quercetum ilicis",
            regionsFrance = listOf("Languedoc", "Provence", "Corse", "Pyrénées-Orientales", "Gard"),
            phMin = 6.5, phMax = 8.5, altitudeMin = 0, altitudeMax = 900,
            climateTypes = listOf("MEDITERRANEEN"),
            humusTypes = listOf("Mull calcique", "Mull"),
            drainageTypes = listOf("BON", "EXCESSIF", "NORMAL"),
            essencesArborescentes = listOf("QUIL", "QUPU"),
            especesIndicatrices = listOf(
                "Pistacia lentiscus", "Ruscus aculeatus", "Smilax aspera",
                "Phillyrea latifolia", "Asparagus acutifolius", "Lonicera implexa",
                "Arisarum vulgare", "Brachypodium retusum"
            ),
            especesExcluantes = listOf("Fagus sylvatica", "Picea abies", "Vaccinium myrtillus"),
            notesSylvicoles = "Climax méditerranéen sur calcaire. Production faible mais haute valeur thermique. Gestion en taillis 20–25 ans. Résilience aux incendies par rejets. Fort potentiel trufficole (mycorhize Tuber melanosporum)."
        ),

        // ══ FORÊTS DE PIN MÉDITERRANÉEN ══════════════════════════════════════

        HabitatForestier(
            id = "PINERAIE_ALEP", codeEur28 = "9540", codeCorine = "42.84",
            nomFr = "Pinède de Pin d'Alep",
            nomSci = "Pino halepensis-Quercetum cocciferae",
            regionsFrance = listOf("Var", "Hérault", "Gard", "Bouches-du-Rhône", "Aude"),
            phMin = 7.0, phMax = 8.5, altitudeMin = 0, altitudeMax = 700,
            climateTypes = listOf("MEDITERRANEEN"),
            humusTypes = listOf("Mull calcique"),
            drainageTypes = listOf("BON", "EXCESSIF"),
            essencesArborescentes = listOf("PIHA", "QUPU", "QUIL"),
            especesIndicatrices = listOf(
                "Rosmarinus officinalis", "Cistus albidus", "Cistus monspeliensis",
                "Brachypodium retusum", "Fumana ericoides", "Phlomis lychnitis"
            ),
            especesExcluantes = listOf("Vaccinium myrtillus", "Fagus sylvatica"),
            notesSylvicoles = "Forêt pionnière méditerranéenne sur calcaire sec. Cône sérotineux = adaptation au feu. Fort risque incendie. Débroussaillage OBLI. Régénération naturelle abondante après incendie."
        ),

        // ══ FORÊTS MONTAGNARDES ═══════════════════════════════════════════════

        HabitatForestier(
            id = "PESSIERE_SUBALPINE", codeEur28 = "9410", codeCorine = "42.21",
            nomFr = "Pessière subalpine acidiphile (Vaccinio-Piceion)",
            nomSci = "Vaccinio-Piceion",
            regionsFrance = listOf("Alpes", "Vosges", "Jura", "Pyrénées"),
            phMin = 3.5, phMax = 5.5, altitudeMin = 1200, altitudeMax = 2200,
            climateTypes = listOf("MONTAGNARD", "SUBALPIN"),
            humusTypes = listOf("Mor", "Moder"),
            drainageTypes = listOf("BON", "NORMAL"),
            essencesArborescentes = listOf("PIAB", "ABBA", "LADA"),
            especesIndicatrices = listOf(
                "Vaccinium myrtillus", "Vaccinium vitis-idaea", "Oxalis acetosella",
                "Homogyne alpina", "Dryopteris dilatata", "Luzula sylvatica",
                "Sphagnum sp.", "Bazzania trilobata"
            ),
            especesExcluantes = listOf("Quercus robur", "Carpinus betulus"),
            notesSylvicoles = "Forêt de montagne sur substrat acide. Risque bostryche élevé. Futaie irrégulière recommandée. Vulnérabilité chablis importante. Sous forte pression climatique — dépérissement en cours en dessous de 1400 m."
        ),

        HabitatForestier(
            id = "SAPIN_HETRE", codeEur28 = "9130", codeCorine = "41.13",
            nomFr = "Sapinière-Hêtraie (Abieti-Fagetum)",
            nomSci = "Abieti-Fagetum",
            regionsFrance = listOf("Vosges", "Jura", "Alpes", "Pyrénées", "Massif Central"),
            phMin = 4.5, phMax = 6.5, altitudeMin = 600, altitudeMax = 1700,
            climateTypes = listOf("MONTAGNARD", "OCEANIQUE_MONTAGNARD"),
            humusTypes = listOf("Mull-Moder", "Moder"),
            drainageTypes = listOf("BON", "NORMAL"),
            essencesArborescentes = listOf("ABBA", "FASY", "PIAB", "ACPS"),
            especesIndicatrices = listOf(
                "Prenanthes purpurea", "Senecio ovatus", "Phegopteris connectilis",
                "Oxalis acetosella", "Galium rotundifolium", "Adenostyles alliariae",
                "Viola biflora"
            ),
            especesExcluantes = listOf("Quercus ilex", "Pistacia lentiscus"),
            notesSylvicoles = "Type forestier le plus productif de la montagne française. Futaie jardinée ou futaie régulière. Association sapin-hêtre très stable. Sensible à la sécheresse prolongée (sapin). Régénération naturelle abondante sous couvert."
        ),

        HabitatForestier(
            id = "MELEZEIN", codeEur28 = "9420", codeCorine = "42.3",
            nomFr = "Mélézein alpin (Larici-Pinetum)",
            nomSci = "Larici-Pinetum cembrae, Larici-Pinetum uncinati",
            regionsFrance = listOf("Alpes", "Briançonnais", "Queyras"),
            phMin = 4.5, phMax = 7.5, altitudeMin = 1500, altitudeMax = 2500,
            climateTypes = listOf("SUBALPIN", "MONTAGNARD"),
            humusTypes = listOf("Mor", "Moder"),
            drainageTypes = listOf("BON", "EXCESSIF"),
            essencesArborescentes = listOf("LADA", "PICE"),
            especesIndicatrices = listOf(
                "Rhododendron ferrugineum", "Vaccinium myrtillus", "Vaccinium uliginosum",
                "Calamagrostis villosa", "Salix helvetica", "Poa alpina"
            ),
            especesExcluantes = listOf("Quercus robur", "Fraxinus excelsior"),
            notesSylvicoles = "Forêt de protection RTM en altitude. Gestion conservatoire. Reboisement mélèze très utilisé en RTM. Forte valeur paysagère. Sensible aux herbivores (cervidés) — clôtures nécessaires."
        ),

        // ══ TOURBIÈRES BOISÉES ════════════════════════════════════════════════

        HabitatForestier(
            id = "TOURBIERE_BOISEE", codeEur28 = "91D0", codeCorine = "44.A1",
            nomFr = "Tourbières boisées à Sphaignes",
            nomSci = "Sphagno-Piceetum, Vaccinio uliginosi-Pinetum sylvestris",
            regionsFrance = listOf("Vosges", "Jura", "Pyrénées", "Massif Central", "Alsace"),
            phMin = 3.0, phMax = 4.5, altitudeMin = 500, altitudeMax = 1800,
            climateTypes = listOf("MONTAGNARD", "SEMI_OCEANIQUE"),
            humusTypes = listOf("Anmoor", "Tourbe"),
            drainageTypes = listOf("TRES_MAUVAIS", "MAUVAIS"),
            essencesArborescentes = listOf("PISY", "BIPE", "PIAB"),
            especesIndicatrices = listOf(
                "Sphagnum sp.", "Vaccinium oxycoccos", "Calluna vulgaris",
                "Andromeda polifolia", "Eriophorum vaginatum", "Molinia caerulea",
                "Drosera rotundifolia"
            ),
            especesExcluantes = listOf("Mercurialis perennis", "Galium odoratum"),
            estPrioritaire = true,
            notesSylvicoles = "Habitat prioritaire Natura 2000 de très haute valeur patrimoniale. Gestion STRICTEMENT conservatoire. Aucune intervention sylvicole. Rôle de captation carbone majeur. Réouverture possible par déboisement ciblé si embroussaillement excessif."
        )
    )
}
