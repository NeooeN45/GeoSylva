package com.forestry.counter.domain.model

/** Static reference data for IBP criteria (CNPF official method). */
object IbpCriterionData {

    // ── A – Essences autochtones ─────────────────────────────────────
    val speciesGenres: List<String> = listOf(
        "Alisier / Cormier / Sorbier (Sorbus)",
        "Arbousier (Arbutus)",
        "Aulne (Alnus)",
        "Bouleau (Betula)",
        "Charme / Charme houblon (Carpinus / Ostrya)",
        "Châtaignier (Castanea)",
        "Chêne à feuilles caduques (pédonculé, sessile, pubescent…)",
        "Chêne à feuilles persistantes (yeuse, liège…)",
        "Épicéa (Picea)",
        "Érable (Acer)",
        "Frêne (Fraxinus)",
        "Genévrier thurifère (Juniperus thurifera)",
        "Hêtre (Fagus)",
        "If (Taxus)",
        "Merisier / Cerisier à grappes (Prunus avium/padus)",
        "Mélèze (Larix)",
        "Micocoulier (Celtis)",
        "Noyer commun (Juglans regia)",
        "Orme (Ulmus)",
        "Peuplier / Tremble (Populus)",
        "Pin (Pinus)",
        "Poirier / Pommier (Pyrus / Malus)",
        "Sapin (Abies)",
        "Saule (Salix)",
        "Tilleul (Tilia)"
    )

    /** Auto-compute score A from selected genres count and growth conditions. */
    fun scoreA(selectedGenres: List<String>, conditions: IbpGrowthConditions): Int {
        val n = selectedGenres.size
        return when (conditions) {
            IbpGrowthConditions.SUBALPINE -> when {
                n == 0 -> 0
                n in 1..2 -> 2
                else -> 5
            }
            else -> when {
                n <= 1 -> 0
                n == 2 -> 1
                n <= 4 -> 2
                else -> 5
            }
        }
    }

    // ── B – Structure verticale ──────────────────────────────────────
    val strataLayers: List<String> = listOf(
        "Herbacée et semi-ligneuse (< 0,5 m)",
        "Ligneux très bas (< 1,5 m)",
        "Ligneux bas (1,5 à 7 m)",
        "Ligneux intermédiaire (7 à 20 m)",
        "Ligneux haut (> 20 m)"
    )

    /** Auto-compute score B from number of strata present. */
    fun scoreB(selectedStrata: List<String>): Int = when (selectedStrata.size) {
        0, 1 -> 0
        2 -> 1
        3, 4 -> 2
        else -> 5
    }

    // ── C – Bois morts sur pied ──────────────────────────────────────
    val deadwoodStandingGuide: List<String> = listOf(
        "BMg (grosse dim.) : D > 37,5 cm — seuil 3/ha → 5 pts, 1/ha → 2 pts",
        "BMm (dim. moy.) : 17,5 < D < 37,5 cm — seuil 1/ha → 2 pts (si BMg < 1/ha)",
        "Compter tous bois morts ≥ 1 m de haut, sur pied ou tombants < 45°"
    )

    // ── D – Bois morts au sol ────────────────────────────────────────
    val deadwoodGroundGuide: List<String> = listOf(
        "BMg (grosse dim.) : D > 37,5 cm — seuil 3/ha → 5 pts, 1/ha → 2 pts",
        "BMm (dim. moy.) : 17,5 < D < 37,5 cm — seuil 1/ha → 2 pts (si BMg < 1/ha)",
        "Compter troncs au sol, souches < 1 m, parties couchées > 45°"
    )

    // ── E – Très gros bois vivants ───────────────────────────────────
    val bigTreesGuide: List<String> = listOf(
        "TGB (très gros bois) : D > 67,5 cm — seuil 5/ha → 5 pts, 1/ha → 2 pts",
        "GB (gros bois) : 47,5 < D < 67,5 cm — seuil 1/ha → 2 pts (si TGB < 1/ha)",
        "Sur stations très peu fertiles : réduire seuils de 20 cm (D > 47,5 pour TGB)"
    )

    // ── F – Dendromicrohabitats (dmh) ────────────────────────────────
    val dmhTypes: List<String> = listOf(
        "1 – Loges de pic (cavité creusée par un picidé)",
        "2 – Cavités à terreau (Ø ≥ 10 cm ou > 30 cm si semi-ouverte)",
        "3 – Orifices et galeries d'insectes saproxyliques (Ø ≥ 2 cm)",
        "4 – Concavités, décrochements d'écorce (Ø ≥ 10 cm, prof. > 10 cm)",
        "5 – Aubier apparent (S ≥ 600 cm² ou écorce décollée > 5 cm)",
        "6 – Aubier et bois de cœur apparents (cime brisée ≥ 20 cm, fente > 1 cm)",
        "7 – Agglomérations de gourmandes ou de rameaux (balais de sorcière > 50 cm)",
        "8 – Loupes et chancres (Ø ≥ 20 cm)",
        "9 – Sporophores de champignons pérennes (Polypore, Ø > 5 cm)",
        "10 – Sporophores de champignons éphémères (Polypore annuel, Agaric)",
        "11 – Plantes et lichens épiphytiques (diversifiés ou abondants)",
        "12 – Nids d'oiseaux ou de mammifères (gros nid visible)",
        "13 – Microsols (accumulation de matière organique sur le tronc ou branches)",
        "14 – Coulées de sève et de résine (coulée active > 20 cm)",
        "15 – Épiphytes particuliers ou source/suintement sur le tronc"
    )

    // ── G – Milieux ouverts florifères ───────────────────────────────
    val openHabitatGuide: List<String> = listOf(
        "Surface trouées ou clairières : mesurer (m²) puis diviser par surface totale (%)",
        "Longueur lisières internes (m) × 2 m = surface équivalente (m²)",
        "Peuplements peu denses ou à feuillage clair entrent dans le calcul",
        "0 pt : 0 % de surface MO | 2 pts : < 1 % ou > 5 % | 5 pts : 1 à 5 %"
    )

    // ── H – Continuité temporelle ────────────────────────────────────
    val forestContinuityOptions: List<String> = listOf(
        "0 pt – Forêt récente : terrain entièrement défriché puis reboisé (après 1950)",
        "2 pts – État boisé partiellement continu : défrichement local, reboisement partiel",
        "5 pts – Forêt ancienne : état boisé continu depuis au moins le XIXe siècle (carte d'état-major)"
    )

    // ── I – Milieux aquatiques ───────────────────────────────────────
    val aquaticTypes: List<String> = listOf(
        "Ruisselet, fossé humide non entretenu ou petit canal (< 1 m)",
        "Petit cours d'eau (1 à 8 m de large)",
        "Rivière, fleuve, estuaire ou delta (> 8 m de large)",
        "Bras mort ou méandre abandonné",
        "Mer ou océan (en bordure de peuplement)",
        "Lac ou plan d'eau profond (> 2 m)",
        "Étang, lagune ou plan d'eau peu profond (≤ 2 m)",
        "Mare ou autre petit point d'eau (< 100 m²)",
        "Tourbière, bas-marais ou lande humide",
        "Zone marécageuse, aulnaie ou saulaie marécageuse",
        "Source ou suintement permanent"
    )

    /** Auto-compute score I/J from count of selected types. */
    fun scoreIJ(selectedTypes: List<String>): Int = when (selectedTypes.size) {
        0 -> 0
        1 -> 2
        else -> 5
    }

    // ── J – Milieux rocheux ──────────────────────────────────────────
    val rockyTypes: List<String> = listOf(
        "Falaise ou paroi rocheuse haute (> hauteur des arbres adultes)",
        "Falaise ou paroi rocheuse basse (≤ hauteur des arbres adultes)",
        "Dalle ou roche affleurante à nu",
        "Lapiaz ou grande diaclase fraîche",
        "Grotte, gouffre ou cavité souterraine accessible",
        "Éboulis instable (blocs en mouvement)",
        "Amoncellement de blocs stables (éboulis stable, tas de pierres, murette > 20 m)",
        "Chaos de gros blocs (> 2 m de Ø)",
        "Gros blocs épars (> 20 cm) ou affleurements rocheux divers",
        "Banc de galets (hors lit mineur actif)",
        "Dépôt de sables fins peu végétalisé (alluvions, dune)"
    )

    // ── Count keys for raw field measurements ───────────────────────
    const val KEY_BMS_BMG = "bms_bmg"   // C – BMg/ha standing (D > 37.5 cm)
    const val KEY_BMS_BMM = "bms_bmm"   // C – BMm/ha standing (17.5–37.5 cm)
    const val KEY_BMC_BMG = "bmc_bmg"   // D – BMg/ha on ground (D > 37.5 cm)
    const val KEY_BMC_BMM = "bmc_bmm"   // D – BMm/ha on ground (17.5–37.5 cm)
    const val KEY_GB_TGB  = "gb_tgb"    // E – TGB/ha (D > 67.5 cm)
    const val KEY_GB_GB   = "gb_gb"     // E – GB/ha (47.5–67.5 cm)
    const val KEY_DMH_N   = "dmh_n"     // F – Trees with dmh/ha
    const val KEY_VS_PCT  = "vs_pct"    // G – % open flowering habitat

    // ── Auto-scoring from raw counts ────────────────────────────────

    /** C – Bois morts sur pied: score from BMg/ha and BMm/ha standing. (Larrieu & Gonin 2008: 0/2/5) */
    fun scoreCFromCounts(bmgPied: Float, bmmPied: Float): Int = when {
        bmgPied >= 3f -> 5
        bmgPied >= 1f -> 2
        bmmPied >= 1f -> 2
        else          -> 0
    }

    /** D – Bois morts au sol: score from BMg/ha and BMm/ha on ground. (Larrieu & Gonin 2008: 0/2/5) */
    fun scoreDFromCounts(bmgSol: Float, bmmSol: Float): Int = when {
        bmgSol >= 3f -> 5
        bmgSol >= 1f -> 2
        bmmSol >= 1f -> 2
        else         -> 0
    }

    /** E – TGB: score from TGB/ha and GB/ha, adjusted for growth conditions. (Larrieu & Gonin 2008: 0/2/5) */
    fun scoreEFromCounts(tgb: Float, gb: Float, conditions: IbpGrowthConditions): Int {
        // On subalpine/poor sites, apply reduced seuils (−1 diameter class ~ ×0.8)
        val tgbHigh = if (conditions == IbpGrowthConditions.SUBALPINE) 4f else 5f
        val tgbMin  = if (conditions == IbpGrowthConditions.SUBALPINE) 0.8f else 1f
        return when {
            tgb >= tgbHigh -> 5
            tgb >= tgbMin  -> 2
            gb  >= 1f      -> 2
            else           -> 0
        }
    }

    /** F – DMH: score from count of trees bearing at least one dmh per ha. (Larrieu & Gonin 2008: 0/2/5) */
    fun scoreFFromCounts(dmhCount: Float): Int = when {
        dmhCount >= 5f -> 5
        dmhCount >= 2f -> 2
        else           -> 0
    }

    /** G – Milieux ouverts: score from % of open flowering habitat. */
    fun scoreGFromPct(pct: Float): Int = when {
        pct in 1f..5f -> 5
        pct > 0f      -> 2
        else          -> 0
    }

    // ── Detailed field protocols (IBP v3.2) ─────────────────────────

    val protocolA: List<String> = listOf(
        "Compter le nombre de GENRES d'arbres autochtones présents (≥ 1 individu ≥ 7,5 cm de diamètre à 1,3 m).",
        "Un seul genre par espèce : Quercus pedonculé = Quercus sessile = 1 genre « Chêne ».",
        "Plaine/Colline : 0 pt = 0–1 genre | 2 pts = 2–4 genres | 5 pts = ≥ 5 genres.",
        "Subalpin : seuils réduits — 0 pt = 0 genre | 2 pts = 1–2 genres | 5 pts = ≥ 3 genres.",
        "Attention : espèces introduites (Douglas, Mélèze du Japon…) ne comptent PAS."
    )

    val protocolB: List<String> = listOf(
        "Observer les 5 strates en un point central représentatif de la placette.",
        "Une strate est présente si ≥ 10 % de couverture dans cette strate.",
        "0 pt = 1 strate | 2 pts = 2, 3 ou 4 strates | 5 pts = 5 strates.",
        "Astuce : couvrir l'ensemble de la placette (pas un seul point de vue).",
        "La strate herbacée compte même si clairsemée sous couvert dense."
    )

    val protocolC: List<String> = listOf(
        "Recenser les bois morts sur pied (ou inclinés < 45°) de hauteur ≥ 1 m.",
        "BMg (grosse dimension) : diamètre de la base > 37,5 cm.",
        "BMm (moyenne dimension) : diamètre 17,5–37,5 cm.",
        "Seuil BMg : ≥ 3/ha → 5 pts | ≥ 1/ha → 2 pts.",
        "Si BMg < 1/ha : seuil BMm ≥ 1/ha → 2 pts, sinon 0 pt.",
        "Mesurer le diamètre à la base (pas à 1,3 m) pour les chicots."
    )

    val protocolD: List<String> = listOf(
        "Recenser les bois morts au sol (inclinés > 45°) et les souches (hauteur < 1 m).",
        "BMg (grosse dimension) : diamètre moyen > 37,5 cm.",
        "BMm (moyenne dimension) : diamètre moyen 17,5–37,5 cm.",
        "Seuil BMg : ≥ 3/ha → 5 pts | ≥ 1/ha → 2 pts.",
        "Si BMg < 1/ha : seuil BMm ≥ 1/ha → 2 pts, sinon 0 pt.",
        "Mesurer le diamètre au milieu du tronc couché."
    )

    val protocolE: List<String> = listOf(
        "TGB (très gros bois) : arbres VIVANTS de diamètre à 1,3 m > 67,5 cm.",
        "GB (gros bois) : arbres VIVANTS de diamètre 47,5–67,5 cm.",
        "Seuil TGB : ≥ 5/ha → 5 pts | ≥ 1/ha → 2 pts.",
        "Si TGB < 1/ha : seuil GB ≥ 1/ha → 2 pts, sinon 0 pt.",
        "Station peu fertile / subalpin : seuils réduits d'environ 20 cm.",
        "Compter tous les individus vivants, toutes espèces confondues."
    )

    val protocolF: List<String> = listOf(
        "Identifier les arbres porteurs d'au moins 1 dendromicrohabitat (dmh) parmi les 15 types.",
        "Un arbre compte une seule fois même s'il porte plusieurs dmh.",
        "Seuil : ≥ 5 arbres porteurs/ha → 5 pts | 2–4 arbres/ha → 2 pts | < 2/ha → 0 pt.",
        "Parcourir systématiquement toute la placette pour ne rien manquer.",
        "En cas de doute sur un type, noter l'arbre et vérifier après."
    )

    val protocolG: List<String> = listOf(
        "Mesurer la surface totale des trouées, clairières et larges lisières internes.",
        "Longueur de lisière interne (m) × 2 m = surface équivalente (m²).",
        "Calculer le % de la surface totale parcourue : Surface MO / Surface totale × 100.",
        "Seuil optimal : 1–5 % → 5 pts | toute autre valeur > 0 → 2 pts | 0 % → 0 pt.",
        "Les peuplements à feuillage très clair et lumineux entrent dans le calcul."
    )

    val protocolH: List<String> = listOf(
        "Vérifier si le terrain a été défriché puis reboisé depuis le milieu du XIXe siècle.",
        "Outil de référence : carte d'état-major (vers 1866) via Géoportail.",
        "Forêt ancienne = état boisé continu depuis au moins le XIXe siècle → 5 pts.",
        "Indices terrain d'ancienneté : sol grumeleux, géophytes vernaux, lierre épais, hêtraie-chênaie dense.",
        "Défrichement local partiel = 2 pts | entièrement défriché après 1950 = 0 pt."
    )

    val protocolI: List<String> = listOf(
        "Recenser tous les types de milieux aquatiques dans ou en bordure directe du peuplement.",
        "Compter le nombre de TYPES distincts (pas le nombre d'occurrences d'un même type).",
        "0 type → 0 pt | 1 type → 2 pts | ≥ 2 types → 5 pts.",
        "Un cours d'eau ne présente qu'1 type même s'il borde toute la parcelle.",
        "Une mare et un ruisseau = 2 types → 5 pts."
    )

    val protocolJ: List<String> = listOf(
        "Recenser tous les types de milieux rocheux dans ou en bordure directe du peuplement.",
        "Compter le nombre de TYPES distincts (pas le nombre d'occurrences).",
        "0 type → 0 pt | 1 type → 2 pts | ≥ 2 types → 5 pts.",
        "Attention : un même affleurement ne compte que pour 1 type.",
        "Un bloc isolé > 20 cm suffit pour le type « gros blocs épars »."
    )

    /** Return the field protocol list for a given criterion. */
    fun protocol(id: IbpCriterionId): List<String> = when (id) {
        IbpCriterionId.E1  -> protocolA
        IbpCriterionId.E2  -> protocolB
        IbpCriterionId.BMS -> protocolC
        IbpCriterionId.BMC -> protocolD
        IbpCriterionId.GB  -> protocolE
        IbpCriterionId.DMH -> protocolF
        IbpCriterionId.VS  -> protocolG
        IbpCriterionId.CF  -> protocolH
        IbpCriterionId.CO  -> protocolI
        IbpCriterionId.HC  -> protocolJ
    }

    /** Return the scoring thresholds summary for a given criterion (short, for UI chips). */
    fun thresholds(id: IbpCriterionId): List<Pair<String, Int>> = when (id) {
        IbpCriterionId.E1  -> listOf("0–1 genre" to 0, "2 genres" to 1, "3–4 genres" to 2, "≥5 genres" to 5)
        IbpCriterionId.E2  -> listOf("0–1 strate" to 0, "2 strates" to 1, "3–4 strates" to 2, "5 strates" to 5)
        IbpCriterionId.BMS -> listOf("Aucun" to 0, "BMm≥1/ha (BMg<1)" to 1, "BMg≥1/ha" to 2, "BMg≥3/ha" to 5)
        IbpCriterionId.BMC -> listOf("Aucun" to 0, "BMm≥1/ha (BMg<1)" to 1, "BMg≥1/ha" to 2, "BMg≥3/ha" to 5)
        IbpCriterionId.GB  -> listOf("TGB<1 & GB<1/ha" to 0, "GB≥1/ha (TGB<1)" to 1, "TGB≥1/ha" to 2, "TGB≥5/ha" to 5)
        IbpCriterionId.DMH -> listOf("<2 arbres/ha" to 0, "2 arbres/ha" to 1, "3–4 arbres/ha" to 2, "≥5 arbres/ha" to 5)
        IbpCriterionId.VS  -> listOf("0% surface MO" to 0, "<1% ou >5%" to 2, "1–5% surface MO" to 5)
        IbpCriterionId.CF  -> listOf("Forêt récente" to 0, "Boisé partiel" to 2, "Forêt ancienne" to 5)
        IbpCriterionId.CO  -> listOf("0 type" to 0, "1 type" to 2, "≥2 types" to 5)
        IbpCriterionId.HC  -> listOf("0 type" to 0, "1 type" to 2, "≥2 types" to 5)
    }
}
