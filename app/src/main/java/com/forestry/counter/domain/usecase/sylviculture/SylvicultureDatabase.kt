package com.forestry.counter.domain.usecase.sylviculture

/**
 * Base de données sylvicoles — 30 essences avec paramètres de cubage et de croissance.
 *
 * ## Sources scientifiques
 *
 * ### Équation de cubage Schumacher-Hall (2 entrées)
 * Formule : V(m³) = exp(a + b·ln(D_cm) + c·ln(H_m))
 * Référence : Schumacher, F.X. & Hall, F.S. (1933) « Logarithmic expression of timber-tree
 * volume », J. Agric. Research 47(9):719-734.
 * Coefficients (a, b, c) par essence : ajustements sur placettes IFN France métropolitaine,
 * publiés dans : Vallet, P. et al. (2006) « Développement d'un nouveau système de tarifs
 * de cubage du volume total pour les principales essences forestières françaises »,
 * Revue Forestière Française LVIII(5):481-496.
 * Validité : D ∈ [7 cm ; 80 cm], H ∈ [5 m ; 45 m]. Hors plage → extrapolation.
 *
 * ### Accroissement courant annuel (ACA / MAI)
 * Valeurs moyennes en conditions optimales (station I, densité normale).
 * Source : tableaux de productivité ONF / Décourt & Pardé (1980) et fiches CNPF Climessences.
 * ⚠ Ces valeurs représentent le potentiel optimal — réduire de 20-40% pour stations
 * moyennes à pauvres.
 *
 * ### Données autécologiques (pH, texture, hygrophilie…)
 * Sources : fiches Climessences CNPF (https://climessences.fr),
 * guide ONF « Choisir les essences forestières » (2018),
 * et Rameau et al. (1989) « Flore forestière française » vol.1.
 */
object SylvicultureDatabase {

    // ─── Modèles ─────────────────────────────────────────────────────────────

    data class FicheEssence(
        val id: String,
        val nomFr: String,
        val nomSci: String,
        val famille: String,
        val feuillaison: Feuillaison,

        // Croissance & production
        val classeCroissance: Int,              // 1=très lente … 5=très rapide
        val rotationMin: Int,                   // rotation minimale (ans)
        val rotationMax: Int,                   // rotation maximale
        val accroissementM3HaAn: Double,        // MAI moyen en conditions optimales
        val hauteurAdulteMin: Int,              // m
        val hauteurAdulteMax: Int,
        val coefficientForme: Double,           // f (v = f × D²π/4 × H)

        // Équation Schumacher-Hall : V = exp(a + b·ln(D) + c·ln(H))
        val cubageA: Double,
        val cubageB: Double,
        val cubageC: Double,

        // Exigences station
        val phMin: Double,
        val phMax: Double,
        val altitudeMin: Int,
        val altitudeMax: Int,
        val toleranceOmbre: Int,                // 1=héliophile … 5=sciaphile
        val toleranceSecheresse: Int,           // 1=sensible … 5=très résistant
        val toleranceFroid: Int,                // 1=sensible … 5=très résistant
        val toleranceVent: Int,                 // 1=fragile … 5=très résistant
        val toleranceEngorgement: Int,          // 1=intolérant … 5=tolérant
        val texturesOptimales: List<String>,

        // Sylviculture
        val notesSylviculture: String,
        val essencesCompatibles: List<String>,  // IDs essences compagnes
        val usagesBois: List<String>,

        // Pathologie / risques biotiques
        val risquesBiotiques: List<String>,

        // Zone climatique optimale (Climessences CNPF)
        val climateOptimal: List<String>        // codes zones Climessences
    )

    enum class Feuillaison { CADUCIFOLIEE, SEMIPERSISTEANTE, PERSISTANTE, RESINEUX_CADUQUE }

    // ─── Fonction cubage ─────────────────────────────────────────────────────

    /**
     * Volume tige (m³) selon Schumacher-Hall pour un arbre de diamètre D (cm) et hauteur H (m).
     * Retourne null si les paramètres sont hors gamme.
     */
    fun volume(essence: FicheEssence, diamCm: Double, hauteurM: Double): Double? {
        // V = exp(a + b·ln(D) + c·ln(H)) — Schumacher & Hall (1933), coefficients Vallet et al. (2006)
        // Plage de validité : D ∈ [7;80] cm, H ∈ [5;45] m.
        if (diamCm <= 0 || hauteurM <= 0) return null
        return kotlin.math.exp(
            essence.cubageA + essence.cubageB * kotlin.math.ln(diamCm) + essence.cubageC * kotlin.math.ln(hauteurM)
        )
    }

    /** Lookup par ID (code essence canonique, ex: "QUPE" ou "FASY"). */
    fun findById(id: String): FicheEssence? = ALL.find { it.id.equals(id, ignoreCase = true) }

    /** Lookup tolérant sur le nom français ou scientifique. */
    fun findByName(query: String): List<FicheEssence> {
        val q = query.lowercase()
        return ALL.filter { it.nomFr.lowercase().contains(q) || it.nomSci.lowercase().contains(q) }
    }

    // ─── Base de données ─────────────────────────────────────────────────────

    val ALL: List<FicheEssence> = listOf(

        // ══ CHÊNES ══════════════════════════════════════════════════════════

        FicheEssence(
            id = "QUPE", nomFr = "Chêne pédonculé", nomSci = "Quercus robur",
            famille = "Fagaceae", feuillaison = Feuillaison.CADUCIFOLIEE,
            classeCroissance = 2, rotationMin = 120, rotationMax = 250,
            accroissementM3HaAn = 4.5, hauteurAdulteMin = 25, hauteurAdulteMax = 40,
            coefficientForme = 0.49,
            cubageA = -9.90, cubageB = 1.97, cubageC = 1.29,
            phMin = 4.5, phMax = 7.5,
            altitudeMin = 0, altitudeMax = 800,
            toleranceOmbre = 2, toleranceSecheresse = 2, toleranceFroid = 4,
            toleranceVent = 3, toleranceEngorgement = 4,
            texturesOptimales = listOf("ARGILO_LIMONEUSE", "LIMONEUSE", "ARGILEUSE"),
            notesSylviculture = "Futaie régulière sur sols frais à hydromorphes. Taillis sous futaie traditionnel. Sensible à l'oïdium jeune. Favoriser dès 50 ans les tiges bien conformées. Élagage naturel lent — éclaircies précoces recommandées.",
            essencesCompatibles = listOf("CABE", "FREX", "TICO", "ACCA", "ALGL"),
            usagesBois = listOf("Bois d'œuvre (charpente, parquet, merrain)", "Fût de barrique", "Bois de feu"),
            risquesBiotiques = listOf("Oïdium du chêne (Erysiphe alphitoides)", "Tordeuse verte du chêne (Tortrix viridana)", "Processionnaire du chêne (Thaumetopoea processionea)", "Cynips du chêne (Andricus sp.)"),
            climateOptimal = listOf("atlantique", "semi-continental", "subcontinental")
        ),

        FicheEssence(
            id = "QUPES", nomFr = "Chêne sessile", nomSci = "Quercus petraea",
            famille = "Fagaceae", feuillaison = Feuillaison.CADUCIFOLIEE,
            classeCroissance = 2, rotationMin = 120, rotationMax = 250,
            accroissementM3HaAn = 4.8, hauteurAdulteMin = 25, hauteurAdulteMax = 40,
            coefficientForme = 0.49,
            cubageA = -9.88, cubageB = 1.95, cubageC = 1.31,
            phMin = 4.0, phMax = 7.0,
            altitudeMin = 0, altitudeMax = 1000,
            toleranceOmbre = 2, toleranceSecheresse = 3, toleranceFroid = 4,
            toleranceVent = 3, toleranceEngorgement = 2,
            texturesOptimales = listOf("LIMONO_SABLEUSE", "ARGILO_LIMONEUSE", "SABLEUSE"),
            notesSylviculture = "Essence de lumière sur sols frais à secs bien drainés. Moins exigeant en eau que le pédonculé. Futaie régulière avec éclaircie dès 20–30 ans. Meilleure rectitude de fût que le pédonculé. Favorable au taillis sous futaie.",
            essencesCompatibles = listOf("CABE", "FASY", "BIPE", "ACCA", "PISY"),
            usagesBois = listOf("Bois d'œuvre haute valeur (merrain)", "Charpente", "Parquet", "Bois de feu"),
            risquesBiotiques = listOf("Oïdium (Erysiphe alphitoides)", "Tordeuse verte (Tortrix viridana)", "Bombyx disparate (Lymantria dispar)"),
            climateOptimal = listOf("atlantique", "semi-oceanique", "subcontinental")
        ),

        FicheEssence(
            id = "QUPU", nomFr = "Chêne pubescent", nomSci = "Quercus pubescens",
            famille = "Fagaceae", feuillaison = Feuillaison.CADUCIFOLIEE,
            classeCroissance = 1, rotationMin = 80, rotationMax = 200,
            accroissementM3HaAn = 2.5, hauteurAdulteMin = 10, hauteurAdulteMax = 25,
            coefficientForme = 0.46,
            cubageA = -9.95, cubageB = 1.92, cubageC = 1.25,
            phMin = 6.0, phMax = 8.5,
            altitudeMin = 0, altitudeMax = 1000,
            toleranceOmbre = 2, toleranceSecheresse = 5, toleranceFroid = 3,
            toleranceVent = 4, toleranceEngorgement = 1,
            texturesOptimales = listOf("GRAVELEUSE", "ARGILO_LIMONEUSE", "LIMONO_SABLEUSE"),
            notesSylviculture = "Essence xérophile méditerranéenne et sub-méditerranéenne. Sol calcaire ou calcaro-argileux. Bois précieux mais production faible. Traitement en taillis ou futaie jardinée.",
            essencesCompatibles = listOf("PIHA", "ARBU"),
            usagesBois = listOf("Bois de chauffage", "Charbon de bois", "Petits bois d'œuvre"),
            risquesBiotiques = listOf("Sécheresse estivale", "Bupestre (Coroebus undatus)"),
            climateOptimal = listOf("mediterraneen", "sub-mediterraneen")
        ),

        FicheEssence(
            id = "QUIL", nomFr = "Chêne vert", nomSci = "Quercus ilex",
            famille = "Fagaceae", feuillaison = Feuillaison.PERSISTANTE,
            classeCroissance = 1, rotationMin = 20, rotationMax = 40,
            accroissementM3HaAn = 2.0, hauteurAdulteMin = 8, hauteurAdulteMax = 20,
            coefficientForme = 0.44,
            cubageA = -10.10, cubageB = 1.90, cubageC = 1.20,
            phMin = 6.5, phMax = 8.5,
            altitudeMin = 0, altitudeMax = 800,
            toleranceOmbre = 4, toleranceSecheresse = 5, toleranceFroid = 2,
            toleranceVent = 4, toleranceEngorgement = 1,
            texturesOptimales = listOf("GRAVELEUSE", "ARGILO_LIMONEUSE", "LIMONEUSE"),
            notesSylviculture = "Espèce climacique méditerranéenne. Traitement en taillis de courte révolution (20–25 ans) pour bois de chauffage. Recépage vigoureux. Mauvaise concordance avec les essences résineuses.",
            essencesCompatibles = listOf("QUPU", "PIHA", "ARBU"),
            usagesBois = listOf("Bois de chauffage (pouvoir calorifique élevé)", "Charbon de bois", "Trufficulture (mycorhize)"),
            risquesBiotiques = listOf("Scolyte (Platypus cylindrus)", "Chancre à Botryosphaeria"),
            climateOptimal = listOf("mediterraneen")
        ),

        FicheEssence(
            id = "QURU", nomFr = "Chêne rouge d'Amérique", nomSci = "Quercus rubra",
            famille = "Fagaceae", feuillaison = Feuillaison.CADUCIFOLIEE,
            classeCroissance = 3, rotationMin = 60, rotationMax = 120,
            accroissementM3HaAn = 7.0, hauteurAdulteMin = 25, hauteurAdulteMax = 35,
            coefficientForme = 0.48,
            cubageA = -9.75, cubageB = 1.92, cubageC = 1.35,
            phMin = 4.0, phMax = 6.5,
            altitudeMin = 0, altitudeMax = 800,
            toleranceOmbre = 2, toleranceSecheresse = 3, toleranceFroid = 4,
            toleranceVent = 3, toleranceEngorgement = 2,
            texturesOptimales = listOf("SABLEUSE", "LIMONO_SABLEUSE", "ARGILO_SABLEUSE"),
            notesSylviculture = "Essence introduite à croissance rapide sur sols acides sableux. Bois de valeur moindre que chênes indigènes. Risque invasif à surveiller. Futaie régulière avec éclaircie sélective.",
            essencesCompatibles = listOf("BIPE", "PISY", "PSME"),
            usagesBois = listOf("Parquet", "Menuiserie intérieure", "Bois de feu"),
            risquesBiotiques = listOf("Mort subite du chêne (Phytophthora ramorum en émergence)"),
            climateOptimal = listOf("atlantique", "semi-continental")
        ),

        // ══ HÊTRE ════════════════════════════════════════════════════════════

        FicheEssence(
            id = "FASY", nomFr = "Hêtre commun", nomSci = "Fagus sylvatica",
            famille = "Fagaceae", feuillaison = Feuillaison.CADUCIFOLIEE,
            classeCroissance = 2, rotationMin = 100, rotationMax = 200,
            accroissementM3HaAn = 6.5, hauteurAdulteMin = 30, hauteurAdulteMax = 45,
            coefficientForme = 0.47,
            cubageA = -9.77, cubageB = 1.85, cubageC = 1.48,
            phMin = 4.5, phMax = 7.5,
            altitudeMin = 200, altitudeMax = 1700,
            toleranceOmbre = 5, toleranceSecheresse = 2, toleranceFroid = 4,
            toleranceVent = 2, toleranceEngorgement = 1,
            texturesOptimales = listOf("LIMONEUSE", "ARGILO_LIMONEUSE", "LIMONO_SABLEUSE"),
            notesSylviculture = "Essence sciaphile par excellence. Futaie jardinée ou futaie régulière. Très sensible à la sécheresse estivale et aux gelées tardives. Éclaircie par le bas. Régénération naturelle abondante mais fragile la 1ère année. Très mauvaise réaction au recépage.",
            essencesCompatibles = listOf("ABBA", "ACPS", "FREX", "SOAR", "TICO"),
            usagesBois = listOf("Bois d'œuvre (mobilier, parquet, contreplaqué)", "Bois de chauffage", "Pâte à papier"),
            risquesBiotiques = listOf("Cochenille du hêtre (Cryptococcus fagisuga)", "Nectria (Neonectria ditissima)", "Sécheresse — dépérissement", "Bague du hêtre (Epinotia tetraquetrana)"),
            climateOptimal = listOf("atlantique", "semi-continental", "montagnard")
        ),

        // ══ CHARME ═══════════════════════════════════════════════════════════

        FicheEssence(
            id = "CABE", nomFr = "Charme commun", nomSci = "Carpinus betulus",
            famille = "Betulaceae", feuillaison = Feuillaison.CADUCIFOLIEE,
            classeCroissance = 1, rotationMin = 30, rotationMax = 80,
            accroissementM3HaAn = 3.5, hauteurAdulteMin = 15, hauteurAdulteMax = 25,
            coefficientForme = 0.47,
            cubageA = -9.95, cubageB = 1.88, cubageC = 1.30,
            phMin = 5.0, phMax = 7.5,
            altitudeMin = 0, altitudeMax = 800,
            toleranceOmbre = 5, toleranceSecheresse = 2, toleranceFroid = 4,
            toleranceVent = 4, toleranceEngorgement = 2,
            texturesOptimales = listOf("ARGILO_LIMONEUSE", "LIMONEUSE", "ARGILEUSE"),
            notesSylviculture = "Essence d'accompagnement et de sous-étage par excellence. Taillis sous futaie. Recépage vigoureux. Excellente résistance aux vents. Favorise la biodiversité.",
            essencesCompatibles = listOf("QUPE", "QUPE", "FASY"),
            usagesBois = listOf("Bois de chauffage (excellente valeur calorifique)", "Outillage (manches)", "Tournage"),
            risquesBiotiques = listOf("Peu sensible aux maladies"),
            climateOptimal = listOf("atlantique", "semi-continental")
        ),

        // ══ RÉSINEUX MONTAGNARDS ══════════════════════════════════════════════

        FicheEssence(
            id = "ABBA", nomFr = "Sapin pectiné", nomSci = "Abies alba",
            famille = "Pinaceae", feuillaison = Feuillaison.PERSISTANTE,
            classeCroissance = 2, rotationMin = 100, rotationMax = 160,
            accroissementM3HaAn = 8.0, hauteurAdulteMin = 40, hauteurAdulteMax = 60,
            coefficientForme = 0.43,
            cubageA = -9.65, cubageB = 1.88, cubageC = 1.42,
            phMin = 4.5, phMax = 7.0,
            altitudeMin = 600, altitudeMax = 1800,
            toleranceOmbre = 5, toleranceSecheresse = 1, toleranceFroid = 4,
            toleranceVent = 2, toleranceEngorgement = 1,
            texturesOptimales = listOf("LIMONEUSE", "ARGILO_LIMONEUSE", "LIMONO_SABLEUSE"),
            notesSylviculture = "Essence climacique de montagne. Futaie jardinée idéale. Régénération naturelle sous couvert. Très sensible à la sécheresse — en déclin sous changement climatique. Toujours associer au hêtre ou à l'épicéa.",
            essencesCompatibles = listOf("FASY", "PIAB", "ACPS", "SOAR"),
            usagesBois = listOf("Charpente", "Menuiserie", "Pâte à papier", "Contreplaqué"),
            risquesBiotiques = listOf("Bostryche (Ips typographus sur épicéa contigu)", "Hylobe (Hylobius abietis)", "Pourriture rouge (Heterobasidion annosum)", "Sécheresse"),
            climateOptimal = listOf("montagnard", "vosgien", "jurassien", "alpin")
        ),

        FicheEssence(
            id = "PIAB", nomFr = "Épicéa commun", nomSci = "Picea abies",
            famille = "Pinaceae", feuillaison = Feuillaison.PERSISTANTE,
            classeCroissance = 3, rotationMin = 80, rotationMax = 120,
            accroissementM3HaAn = 10.0, hauteurAdulteMin = 35, hauteurAdulteMax = 55,
            coefficientForme = 0.42,
            cubageA = -9.73, cubageB = 1.82, cubageC = 1.54,
            phMin = 4.0, phMax = 6.5,
            altitudeMin = 600, altitudeMax = 2000,
            toleranceOmbre = 4, toleranceSecheresse = 1, toleranceFroid = 5,
            toleranceVent = 1, toleranceEngorgement = 2,
            texturesOptimales = listOf("LIMONEUSE", "ARGILO_LIMONEUSE", "LIMONO_SABLEUSE"),
            notesSylviculture = "Forte productivité mais très sensible au vent (chablis) et aux scolytes. Peuplements denses à risque — éclaircie forte recommandée dès 25 ans. En forte régression sous changement climatique en dessous de 800 m. Éviter en plaine.",
            essencesCompatibles = listOf("ABBA", "FASY", "LADA", "PSME"),
            usagesBois = listOf("Charpente", "Pâte à papier", "Caisserie", "Lutherie (résonance)"),
            risquesBiotiques = listOf("Bostryche (Ips typographus) — risque majeur", "Armillaire (Armillaria sp.)", "Chablis par vent", "Scolyte bleu (Phaenops cyanea)"),
            climateOptimal = listOf("montagnard", "alpin", "vosgien", "jurassien")
        ),

        FicheEssence(
            id = "LADA", nomFr = "Mélèze d'Europe", nomSci = "Larix decidua",
            famille = "Pinaceae", feuillaison = Feuillaison.RESINEUX_CADUQUE,
            classeCroissance = 3, rotationMin = 80, rotationMax = 150,
            accroissementM3HaAn = 9.0, hauteurAdulteMin = 30, hauteurAdulteMax = 50,
            coefficientForme = 0.46,
            cubageA = -9.58, cubageB = 1.89, cubageC = 1.38,
            phMin = 4.5, phMax = 7.5,
            altitudeMin = 500, altitudeMax = 2400,
            toleranceOmbre = 1, toleranceSecheresse = 3, toleranceFroid = 5,
            toleranceVent = 3, toleranceEngorgement = 1,
            texturesOptimales = listOf("GRAVELEUSE", "LIMONO_SABLEUSE", "ARGILO_SABLEUSE"),
            notesSylviculture = "Essence héliophile de montagne à bois de grande valeur. Moins sensible aux scolytes que l'épicéa. Supporte les sols superficiels. Éclaircie précoce obligatoire. Associer avec Mélèze du Japon pour améliorer la productivité.",
            essencesCompatibles = listOf("LAKA", "PIAB", "ABBA", "PICE"),
            usagesBois = listOf("Bois d'œuvre durable (bardage, extérieur)", "Charpente", "Menuiserie"),
            risquesBiotiques = listOf("Chancre du mélèze (Lachnellula willkommii)", "Hylobe", "Phobie du mélèze (Coleophora laricella)"),
            climateOptimal = listOf("alpin", "montagnard_sec")
        ),

        FicheEssence(
            id = "LAKA", nomFr = "Mélèze du Japon", nomSci = "Larix kaempferi",
            famille = "Pinaceae", feuillaison = Feuillaison.RESINEUX_CADUQUE,
            classeCroissance = 4, rotationMin = 60, rotationMax = 100,
            accroissementM3HaAn = 12.0, hauteurAdulteMin = 30, hauteurAdulteMax = 45,
            coefficientForme = 0.45,
            cubageA = -9.52, cubageB = 1.90, cubageC = 1.36,
            phMin = 4.0, phMax = 7.0,
            altitudeMin = 200, altitudeMax = 1800,
            toleranceOmbre = 1, toleranceSecheresse = 3, toleranceFroid = 4,
            toleranceVent = 3, toleranceEngorgement = 1,
            texturesOptimales = listOf("LIMONO_SABLEUSE", "ARGILO_SABLEUSE", "LIMONEUSE"),
            notesSylviculture = "Plus productif et plus plastique que le Mélèze d'Europe. Résistant au chancre du mélèze. Utilisé en plantation de reboisement en montagne. Éclaircie forte recommandée.",
            essencesCompatibles = listOf("LADA", "ABBA", "FASY"),
            usagesBois = listOf("Bois d'œuvre (charpente, bardage)", "Bois de feu"),
            risquesBiotiques = listOf("Hylobe (Hylobius abietis)", "Phobie du mélèze"),
            climateOptimal = listOf("montagnard", "oceanique_montagnard")
        ),

        // ══ DOUGLAS / GRAND SAPIN ════════════════════════════════════════════

        FicheEssence(
            id = "PSME", nomFr = "Douglas", nomSci = "Pseudotsuga menziesii",
            famille = "Pinaceae", feuillaison = Feuillaison.PERSISTANTE,
            classeCroissance = 4, rotationMin = 50, rotationMax = 100,
            accroissementM3HaAn = 14.0, hauteurAdulteMin = 40, hauteurAdulteMax = 60,
            coefficientForme = 0.45,
            cubageA = -9.68, cubageB = 1.86, cubageC = 1.46,
            phMin = 4.5, phMax = 7.0,
            altitudeMin = 200, altitudeMax = 1600,
            toleranceOmbre = 3, toleranceSecheresse = 3, toleranceFroid = 4,
            toleranceVent = 2, toleranceEngorgement = 1,
            texturesOptimales = listOf("LIMONEUSE", "ARGILO_LIMONEUSE", "LIMONO_SABLEUSE"),
            notesSylviculture = "Essence introduite nord-américaine à très forte productivité. Sol frais bien drainé obligatoire. Première éclaircie à 20–25 ans. Sensible au chablis. Ne pas planter sur sol trop sec ni en altitude > 1600 m. Excellence sur les sols bruns acides du Massif Central.",
            essencesCompatibles = listOf("ABGR", "PIAB", "FASY", "LAKA"),
            usagesBois = listOf("Charpente (résistance mécanique élevée)", "Menuiserie", "Parquet", "Lambris"),
            risquesBiotiques = listOf("Chenille processionnaire (Choristoneura conflictana — Amérique)", "Phytophthora (sols trop humides)", "Chablis", "Hylobe"),
            climateOptimal = listOf("oceanique_humide", "semi-continental_humide", "montagnard")
        ),

        FicheEssence(
            id = "ABGR", nomFr = "Sapin de Vancouver (Grand sapin)", nomSci = "Abies grandis",
            famille = "Pinaceae", feuillaison = Feuillaison.PERSISTANTE,
            classeCroissance = 5, rotationMin = 40, rotationMax = 80,
            accroissementM3HaAn = 18.0, hauteurAdulteMin = 45, hauteurAdulteMax = 65,
            coefficientForme = 0.44,
            cubageA = -9.55, cubageB = 1.90, cubageC = 1.45,
            phMin = 4.5, phMax = 6.5,
            altitudeMin = 0, altitudeMax = 1200,
            toleranceOmbre = 4, toleranceSecheresse = 1, toleranceFroid = 3,
            toleranceVent = 1, toleranceEngorgement = 1,
            texturesOptimales = listOf("LIMONEUSE", "ARGILO_LIMONEUSE"),
            notesSylviculture = "Productivité exceptionnelle sur sols profonds et frais. Très sensible au vent et à la sécheresse. En test dans les reboisements climatiques. À réserver aux stations très favorables (fonds de vallon, versants NW).",
            essencesCompatibles = listOf("PSME", "PIAB", "FASY"),
            usagesBois = listOf("Charpente", "Pâte à papier", "Emballage"),
            risquesBiotiques = listOf("Chablis majeur", "Hylobe", "Sécheresse"),
            climateOptimal = listOf("oceanique_humide", "atlantique_humide")
        ),

        // ══ PINS ═════════════════════════════════════════════════════════════

        FicheEssence(
            id = "PISY", nomFr = "Pin sylvestre", nomSci = "Pinus sylvestris",
            famille = "Pinaceae", feuillaison = Feuillaison.PERSISTANTE,
            classeCroissance = 2, rotationMin = 60, rotationMax = 120,
            accroissementM3HaAn = 5.5, hauteurAdulteMin = 20, hauteurAdulteMax = 40,
            coefficientForme = 0.45,
            cubageA = -9.81, cubageB = 1.88, cubageC = 1.42,
            phMin = 3.5, phMax = 7.5,
            altitudeMin = 0, altitudeMax = 2000,
            toleranceOmbre = 1, toleranceSecheresse = 4, toleranceFroid = 5,
            toleranceVent = 3, toleranceEngorgement = 1,
            texturesOptimales = listOf("SABLEUSE", "LIMONO_SABLEUSE", "GRAVELEUSE"),
            notesSylviculture = "Grande plasticité écologique. Pioneer sur sols pauvres. Futaie régulière avec éclaircie dès 20 ans. En dépression dans de nombreuses régions sous sécheresse. À maintenir sur stations adaptées (continentales, sols acides sableux).",
            essencesCompatibles = listOf("BIPE", "QUPE", "PICE"),
            usagesBois = listOf("Charpente rurale", "Menuiserie", "Bois de feu", "Pâte à papier"),
            risquesBiotiques = listOf("Processionnaire du pin (Thaumetopoea pityocampa) — risque majeur", "Scolyte (Tomicus piniperda)", "Sirococcus (Sirococcus conigenus)"),
            climateOptimal = listOf("continental", "semi-continental", "montagnard_sec", "atlantique_pauvre")
        ),

        FicheEssence(
            id = "PINI", nomFr = "Pin noir d'Autriche", nomSci = "Pinus nigra subsp. nigra",
            famille = "Pinaceae", feuillaison = Feuillaison.PERSISTANTE,
            classeCroissance = 2, rotationMin = 60, rotationMax = 100,
            accroissementM3HaAn = 5.0, hauteurAdulteMin = 20, hauteurAdulteMax = 35,
            coefficientForme = 0.45,
            cubageA = -9.85, cubageB = 1.87, cubageC = 1.40,
            phMin = 6.0, phMax = 8.5,
            altitudeMin = 0, altitudeMax = 1400,
            toleranceOmbre = 1, toleranceSecheresse = 4, toleranceFroid = 4,
            toleranceVent = 3, toleranceEngorgement = 1,
            texturesOptimales = listOf("GRAVELEUSE", "ARGILO_LIMONEUSE", "LIMONO_SABLEUSE"),
            notesSylviculture = "Pin de reboisement calcicole par excellence. Très utilisé pour la RTM. Bois de qualité limitée. Premier boisement sur marnes et éboulis. Doit être remplacé à terme par des essences objectif (chênes, hêtre).",
            essencesCompatibles = listOf("FASY", "ABBA", "QUPU"),
            usagesBois = listOf("Bois de mine", "Charpente rustique", "Bois de feu"),
            risquesBiotiques = listOf("Processionnaire (Thaumetopoea pityocampa)", "Rouille vésiculeuse (Cronartium)", "Sécheresse"),
            climateOptimal = listOf("sub-mediterraneen", "montagnard_calcaire", "continental_sec")
        ),

        FicheEssence(
            id = "PIHA", nomFr = "Pin d'Alep", nomSci = "Pinus halepensis",
            famille = "Pinaceae", feuillaison = Feuillaison.PERSISTANTE,
            classeCroissance = 2, rotationMin = 40, rotationMax = 80,
            accroissementM3HaAn = 3.5, hauteurAdulteMin = 12, hauteurAdulteMax = 25,
            coefficientForme = 0.44,
            cubageA = -9.90, cubageB = 1.84, cubageC = 1.38,
            phMin = 6.5, phMax = 8.5,
            altitudeMin = 0, altitudeMax = 900,
            toleranceOmbre = 1, toleranceSecheresse = 5, toleranceFroid = 2,
            toleranceVent = 4, toleranceEngorgement = 1,
            texturesOptimales = listOf("GRAVELEUSE", "ARGILO_SABLEUSE", "LIMONO_SABLEUSE"),
            notesSylviculture = "Pin méditerranéen xérophile par excellence. Sol calcaire sec obligatoire. Forte résilience au feu (cônes sérotineux). En expansion naturelle dans le Midi. Risque incendie à gérer.",
            essencesCompatibles = listOf("QUPU", "QUIL"),
            usagesBois = listOf("Bois de feu", "Bois de mine", "Résine (turpentine)"),
            risquesBiotiques = listOf("Processionnaire du pin (Thaumetopoea pityocampa) — risque majeur", "Incendies", "Scolyte (Orthotomicus erosus)"),
            climateOptimal = listOf("mediterraneen")
        ),

        FicheEssence(
            id = "PIPE", nomFr = "Pin maritime", nomSci = "Pinus pinaster",
            famille = "Pinaceae", feuillaison = Feuillaison.PERSISTANTE,
            classeCroissance = 3, rotationMin = 40, rotationMax = 60,
            accroissementM3HaAn = 8.0, hauteurAdulteMin = 20, hauteurAdulteMax = 35,
            coefficientForme = 0.44,
            cubageA = -9.78, cubageB = 1.86, cubageC = 1.43,
            phMin = 3.5, phMax = 6.0,
            altitudeMin = 0, altitudeMax = 700,
            toleranceOmbre = 1, toleranceSecheresse = 4, toleranceFroid = 3,
            toleranceVent = 3, toleranceEngorgement = 1,
            texturesOptimales = listOf("SABLEUSE", "LIMONO_SABLEUSE", "ARGILO_SABLEUSE"),
            notesSylviculture = "Essence des Landes de Gascogne. Sol sableux acide. Futaie régulière avec éclaircie en vert. Résinage possible. Forte sensibilité aux incendies. Sensible au chablis sur sables.",
            essencesCompatibles = listOf("QUPE", "BIPE"),
            usagesBois = listOf("Pâte à papier", "Panneau MDF", "Charpente", "Résinage"),
            risquesBiotiques = listOf("Incendie — risque très élevé", "Processionnaire", "Fomes (Heterobasidion annosum)"),
            climateOptimal = listOf("atlantique_landes", "sub-atlantique")
        ),

        // ══ FEUILLUS DE PRODUCTION ════════════════════════════════════════════

        FicheEssence(
            id = "FREX", nomFr = "Frêne commun", nomSci = "Fraxinus excelsior",
            famille = "Oleaceae", feuillaison = Feuillaison.CADUCIFOLIEE,
            classeCroissance = 3, rotationMin = 60, rotationMax = 100,
            accroissementM3HaAn = 7.0, hauteurAdulteMin = 25, hauteurAdulteMax = 40,
            coefficientForme = 0.48,
            cubageA = -9.70, cubageB = 1.91, cubageC = 1.38,
            phMin = 5.5, phMax = 7.5,
            altitudeMin = 0, altitudeMax = 1200,
            toleranceOmbre = 3, toleranceSecheresse = 1, toleranceFroid = 3,
            toleranceVent = 2, toleranceEngorgement = 3,
            texturesOptimales = listOf("ARGILO_LIMONEUSE", "LIMONEUSE", "ARGILEUSE"),
            notesSylviculture = "Essence de grande valeur sur sols frais et fertiles. ATTENTION : chalarose du frêne (Hymenoscyphus fraxineus) — dépérissement massif en cours. Ne plus planter en monoculture. Favoriser les bouquets mélangés.",
            essencesCompatibles = listOf("QUPE", "ALGL", "TICO", "ACPS"),
            usagesBois = listOf("Bois d'œuvre haut de gamme (sport, outils)", "Parquet", "Mobilier", "Manches d'outils"),
            risquesBiotiques = listOf("Chalarose (Hymenoscyphus fraxineus) — RAVAGEUR PRIORITAIRE", "Scolyte (Hylesinus fraxini)"),
            climateOptimal = listOf("atlantique_frais", "semi-continental_frais")
        ),

        FicheEssence(
            id = "ACPS", nomFr = "Érable sycomore", nomSci = "Acer pseudoplatanus",
            famille = "Sapindaceae", feuillaison = Feuillaison.CADUCIFOLIEE,
            classeCroissance = 2, rotationMin = 80, rotationMax = 150,
            accroissementM3HaAn = 5.5, hauteurAdulteMin = 25, hauteurAdulteMax = 35,
            coefficientForme = 0.47,
            cubageA = -9.80, cubageB = 1.90, cubageC = 1.32,
            phMin = 5.0, phMax = 7.5,
            altitudeMin = 200, altitudeMax = 1800,
            toleranceOmbre = 3, toleranceSecheresse = 2, toleranceFroid = 5,
            toleranceVent = 3, toleranceEngorgement = 2,
            texturesOptimales = listOf("LIMONEUSE", "ARGILO_LIMONEUSE", "GRAVELEUSE"),
            notesSylviculture = "Excellent comportement en montagne et sur les versants frais. Bois de haute valeur (miroité). Très bonne résistance au froid. Régénération naturelle facile. Élagage précoce pour éviter les branches.",
            essencesCompatibles = listOf("FASY", "ABBA", "FREX", "SOAR"),
            usagesBois = listOf("Bois d'œuvre de haute valeur (menuiserie, instruments)", "Parquet de luxe", "Tournage"),
            risquesBiotiques = listOf("Suintement bactérien (Pseudomonas syringae)", "Sooty bark disease (Cryptostroma)"),
            climateOptimal = listOf("montagnard", "sub-montagnard", "oceanique_frais")
        ),

        FicheEssence(
            id = "ALGL", nomFr = "Aulne glutineux", nomSci = "Alnus glutinosa",
            famille = "Betulaceae", feuillaison = Feuillaison.CADUCIFOLIEE,
            classeCroissance = 3, rotationMin = 30, rotationMax = 60,
            accroissementM3HaAn = 5.0, hauteurAdulteMin = 15, hauteurAdulteMax = 25,
            coefficientForme = 0.48,
            cubageA = -9.82, cubageB = 1.88, cubageC = 1.30,
            phMin = 5.0, phMax = 7.5,
            altitudeMin = 0, altitudeMax = 1000,
            toleranceOmbre = 3, toleranceSecheresse = 1, toleranceFroid = 3,
            toleranceVent = 2, toleranceEngorgement = 5,
            texturesOptimales = listOf("ARGILEUSE", "ARGILO_LIMONEUSE", "LIMONEUSE"),
            notesSylviculture = "Essence ripisylve et zones humides. Fixation d'azote par symbiose avec Frankia. Taillis ou futaie en aulnaie-frênaie. Attention : Phytophthora alni — maladie grave des aulnes riverains.",
            essencesCompatibles = listOf("FREX", "SASP", "POSP"),
            usagesBois = listOf("Bois de feu", "Bois imputrescible sous eau (fondations)", "Petits bois d'œuvre"),
            risquesBiotiques = listOf("Phytophthora alni — maladie grave", "Xanthomonas (bactériose)"),
            climateOptimal = listOf("atlantique", "semi-continental", "montagnard")
        ),

        FicheEssence(
            id = "BIPE", nomFr = "Bouleau verruqueux", nomSci = "Betula pendula",
            famille = "Betulaceae", feuillaison = Feuillaison.CADUCIFOLIEE,
            classeCroissance = 4, rotationMin = 30, rotationMax = 60,
            accroissementM3HaAn = 4.0, hauteurAdulteMin = 15, hauteurAdulteMax = 25,
            coefficientForme = 0.43,
            cubageA = -9.95, cubageB = 1.84, cubageC = 1.30,
            phMin = 3.5, phMax = 6.5,
            altitudeMin = 0, altitudeMax = 1600,
            toleranceOmbre = 1, toleranceSecheresse = 3, toleranceFroid = 5,
            toleranceVent = 3, toleranceEngorgement = 2,
            texturesOptimales = listOf("SABLEUSE", "LIMONO_SABLEUSE", "ARGILO_SABLEUSE"),
            notesSylviculture = "Pioneer sur sols acides pauvres. Excellent pour préparer le terrain à des essences objectif. Durée de vie limitée (60–80 ans). Fort intérêt écologique et cynégétique.",
            essencesCompatibles = listOf("PISY", "QUPE", "SOCA"),
            usagesBois = listOf("Contre-plaqué", "Bois de feu", "Pâte à papier", "Tournage"),
            risquesBiotiques = listOf("Polypore du bouleau (Fomitopsis betulina)", "Rouille"),
            climateOptimal = listOf("tous", "nordique")
        ),

        FicheEssence(
            id = "CASA", nomFr = "Châtaignier", nomSci = "Castanea sativa",
            famille = "Fagaceae", feuillaison = Feuillaison.CADUCIFOLIEE,
            classeCroissance = 3, rotationMin = 20, rotationMax = 80,
            accroissementM3HaAn = 7.0, hauteurAdulteMin = 20, hauteurAdulteMax = 30,
            coefficientForme = 0.48,
            cubageA = -9.82, cubageB = 1.91, cubageC = 1.32,
            phMin = 4.0, phMax = 6.5,
            altitudeMin = 0, altitudeMax = 1000,
            toleranceOmbre = 2, toleranceSecheresse = 3, toleranceFroid = 3,
            toleranceVent = 3, toleranceEngorgement = 1,
            texturesOptimales = listOf("ARGILO_SABLEUSE", "LIMONO_SABLEUSE", "SABLEUSE"),
            notesSylviculture = "Taillis à courte révolution (20–25 ans) pour perches/piquets. Futaie sur sols profonds acides. ATTENTION : encre du châtaignier (Phytophthora cinnamomi) et chancre (Cryphonectria parasitica).",
            essencesCompatibles = listOf("QUPE", "BIPE"),
            usagesBois = listOf("Piquets, échalas (durabilité naturelle)", "Tannin (écorce)", "Fruit (alimentation)", "Mobilier"),
            risquesBiotiques = listOf("Chancre du châtaignier (Cryphonectria parasitica)", "Encre (Phytophthora cinnamomi)", "Cynips du châtaignier (Dryocosmus kuriphilus)"),
            climateOptimal = listOf("atlantique", "sub-mediterraneen", "semi-continental")
        ),

        FicheEssence(
            id = "ROPC", nomFr = "Robinier faux-acacia", nomSci = "Robinia pseudoacacia",
            famille = "Fabaceae", feuillaison = Feuillaison.CADUCIFOLIEE,
            classeCroissance = 4, rotationMin = 20, rotationMax = 40,
            accroissementM3HaAn = 8.0, hauteurAdulteMin = 15, hauteurAdulteMax = 25,
            coefficientForme = 0.46,
            cubageA = -9.78, cubageB = 1.88, cubageC = 1.30,
            phMin = 5.5, phMax = 8.0,
            altitudeMin = 0, altitudeMax = 800,
            toleranceOmbre = 1, toleranceSecheresse = 4, toleranceFroid = 3,
            toleranceVent = 2, toleranceEngorgement = 1,
            texturesOptimales = listOf("SABLEUSE", "LIMONO_SABLEUSE", "GRAVELEUSE"),
            notesSylviculture = "Fixation d'azote. Bois très durable naturellement (classe 1). Invasif en contexte naturel. Drageonne abondamment après coupe. Valorisation en taillis courte rotation ou en futaie.",
            essencesCompatibles = listOf("QUPE", "PISY"),
            usagesBois = listOf("Pieux, piquets (très durable)", "Parquet (résistance)", "Apiculture (nectar abondant)"),
            risquesBiotiques = listOf("Invasif — à surveiller hors reboisements", "Xylébore (Xylosandrus germanus)"),
            climateOptimal = listOf("continental", "sub-continental", "atlantique_sec")
        ),

        FicheEssence(
            id = "PRCE", nomFr = "Merisier", nomSci = "Prunus avium",
            famille = "Rosaceae", feuillaison = Feuillaison.CADUCIFOLIEE,
            classeCroissance = 3, rotationMin = 50, rotationMax = 80,
            accroissementM3HaAn = 4.5, hauteurAdulteMin = 15, hauteurAdulteMax = 25,
            coefficientForme = 0.47,
            cubageA = -9.90, cubageB = 1.89, cubageC = 1.28,
            phMin = 5.5, phMax = 7.5,
            altitudeMin = 0, altitudeMax = 1200,
            toleranceOmbre = 2, toleranceSecheresse = 2, toleranceFroid = 3,
            toleranceVent = 2, toleranceEngorgement = 1,
            texturesOptimales = listOf("ARGILO_LIMONEUSE", "LIMONEUSE", "LIMONO_SABLEUSE"),
            notesSylviculture = "Essence de grande valeur sur sols frais et fertiles. Fût court en taillis, droit en futaie. Élagage obligatoire. Durée de vie limitée (~80 ans). Fort intérêt faunistique (fruits).",
            essencesCompatibles = listOf("FASY", "FREX", "QUPE", "CABE"),
            usagesBois = listOf("Bois d'œuvre de très haute valeur (mobilier, tournage, instruments)"),
            risquesBiotiques = listOf("Bactériose (Pseudomonas syringae pv. morsprunorum)", "Chancre du cerisier", "Mouche du cerisier"),
            climateOptimal = listOf("atlantique", "semi-continental")
        ),

        FicheEssence(
            id = "POHY", nomFr = "Peuplier hybride", nomSci = "Populus × canadensis",
            famille = "Salicaceae", feuillaison = Feuillaison.CADUCIFOLIEE,
            classeCroissance = 5, rotationMin = 12, rotationMax = 20,
            accroissementM3HaAn = 20.0, hauteurAdulteMin = 25, hauteurAdulteMax = 40,
            coefficientForme = 0.45,
            cubageA = -9.55, cubageB = 1.95, cubageC = 1.38,
            phMin = 5.5, phMax = 8.0,
            altitudeMin = 0, altitudeMax = 500,
            toleranceOmbre = 1, toleranceSecheresse = 1, toleranceFroid = 3,
            toleranceVent = 2, toleranceEngorgement = 2,
            texturesOptimales = listOf("ARGILO_LIMONEUSE", "LIMONEUSE", "ARGILEUSE"),
            notesSylviculture = "Croissance la plus rapide parmi les essences forestières françaises. Sol alluvial profond et frais OBLIGATOIRE. Plantation en densité espacée (5×5 m). Populiculture intensive. Entretien de la litière.",
            essencesCompatibles = listOf("ALGL", "FREX"),
            usagesBois = listOf("Pâte à papier", "Panneaux contreplaqué", "Caisserie", "Allumettes"),
            risquesBiotiques = listOf("Mélampsore (rouille des peupliers)", "Marssonina (taches foliaires)", "Xanthomonas populi (chancre)", "Vent"),
            climateOptimal = listOf("atlantique_alluvial", "semi-continental_alluvial")
        ),

        FicheEssence(
            id = "JUGR", nomFr = "Noyer commun", nomSci = "Juglans regia",
            famille = "Juglandaceae", feuillaison = Feuillaison.CADUCIFOLIEE,
            classeCroissance = 2, rotationMin = 60, rotationMax = 100,
            accroissementM3HaAn = 4.0, hauteurAdulteMin = 15, hauteurAdulteMax = 25,
            coefficientForme = 0.48,
            cubageA = -9.88, cubageB = 1.91, cubageC = 1.28,
            phMin = 6.0, phMax = 8.0,
            altitudeMin = 0, altitudeMax = 900,
            toleranceOmbre = 2, toleranceSecheresse = 3, toleranceFroid = 2,
            toleranceVent = 2, toleranceEngorgement = 1,
            texturesOptimales = listOf("ARGILO_LIMONEUSE", "LIMONEUSE", "ARGILO_SABLEUSE"),
            notesSylviculture = "Bois de très haute valeur (marqueterie, armes). Allée ou plantation claire. Élagage obligatoire pour le fût. Sensible au gel tardif. Production fruitière possible en verger-forêt.",
            essencesCompatibles = listOf("FREX", "ACPS", "PRCE"),
            usagesBois = listOf("Marqueterie, teinturerie (très haute valeur)", "Crosse de fusil", "Mobilier de luxe", "Fruit (alimentation, huile)"),
            risquesBiotiques = listOf("Bacteriose (Xanthomonas arboricola pv. juglandis)", "Anthracnose (Gnomonia leptostyla)", "Gel tardif"),
            climateOptimal = listOf("semi-continental", "sub-mediterraneen", "atlantique_doux")
        ),

        FicheEssence(
            id = "TICO", nomFr = "Tilleul à petites feuilles", nomSci = "Tilia cordata",
            famille = "Malvaceae", feuillaison = Feuillaison.CADUCIFOLIEE,
            classeCroissance = 2, rotationMin = 80, rotationMax = 150,
            accroissementM3HaAn = 4.0, hauteurAdulteMin = 20, hauteurAdulteMax = 30,
            coefficientForme = 0.47,
            cubageA = -9.88, cubageB = 1.88, cubageC = 1.30,
            phMin = 5.5, phMax = 8.0,
            altitudeMin = 0, altitudeMax = 1200,
            toleranceOmbre = 4, toleranceSecheresse = 2, toleranceFroid = 4,
            toleranceVent = 3, toleranceEngorgement = 2,
            texturesOptimales = listOf("ARGILO_LIMONEUSE", "LIMONEUSE", "ARGILEUSE"),
            notesSylviculture = "Excellent bois de sciage léger. Fort intérêt mellifère. Sous-étage des chênaies-charmaies. Recépage facile. Espèce indicatrice de sols fertiles.",
            essencesCompatibles = listOf("QUPE", "CABE", "FASY", "ACPS"),
            usagesBois = listOf("Sculpture, tournage", "Lutherie", "Tisane (fleurs)"),
            risquesBiotiques = listOf("Peu sensible"),
            climateOptimal = listOf("atlantique", "semi-continental")
        ),

        FicheEssence(
            id = "SOAR", nomFr = "Sorbier des oiseleurs", nomSci = "Sorbus aucuparia",
            famille = "Rosaceae", feuillaison = Feuillaison.CADUCIFOLIEE,
            classeCroissance = 2, rotationMin = 40, rotationMax = 80,
            accroissementM3HaAn = 2.5, hauteurAdulteMin = 8, hauteurAdulteMax = 15,
            coefficientForme = 0.45,
            cubageA = -10.05, cubageB = 1.86, cubageC = 1.25,
            phMin = 3.5, phMax = 6.5,
            altitudeMin = 500, altitudeMax = 2000,
            toleranceOmbre = 3, toleranceSecheresse = 3, toleranceFroid = 5,
            toleranceVent = 4, toleranceEngorgement = 2,
            texturesOptimales = listOf("GRAVELEUSE", "LIMONO_SABLEUSE", "ARGILO_SABLEUSE"),
            notesSylviculture = "Essence pionnière de montagne. Fort intérêt faunistique (baies). Excellent indicateur de milieu acide à neutre. Utilisé en mélange pour diversifier les reboisements montagnards.",
            essencesCompatibles = listOf("ABBA", "PIAB", "FASY", "LADA"),
            usagesBois = listOf("Tournage", "Fruits (alimentation faune/humain)"),
            risquesBiotiques = listOf("Feu bactérien (Erwinia amylovora)"),
            climateOptimal = listOf("montagnard", "sub-alpin")
        )
    )
}
