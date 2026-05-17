package com.forestry.counter.domain.usecase.autecology

import com.forestry.counter.domain.model.ClimateZone

/**
 * Base de données des pathogènes et ravageurs forestiers.
 * Sources : DSF (Département Santé des Forêts), INRAE, EFSA, CNPF.
 *
 * Chaque entrée définit un agent biotique avec :
 *  - ses hôtes principaux (codes essences)
 *  - les zones à risque élevé
 *  - la dynamique face au changement climatique
 *  - les seuils de déclenchement et symptômes
 *  - les actions de gestion recommandées
 */
object PathoEntomoDatabase {

    enum class TypeAgent { CHAMPIGNON, INSECTE, BACTERIE, OOMYCETE, VIRUS, NEMATODE }
    enum class NiveauMenace { CATASTROPHIQUE, FORT, MODERE, FAIBLE }
    enum class TendanceCC { EN_HAUSSE, STABLE, EN_BAISSE }

    data class PathogenEntry(
        val code: String,
        val nomFr: String,
        val nomLatin: String,
        val type: TypeAgent,
        val menace: NiveauMenace,
        val tendanceCC: TendanceCC,
        val essencesHotes: List<String>,
        val zonesRisqueEleve: Set<ClimateZone>,
        val symptomes: String,
        val facteursDeclenchement: List<String>,
        val actionsCNPF: List<String>,
        val signalementObligatoire: Boolean = false
    )

    private val ENTRIES: List<PathogenEntry> = listOf(

        // ── CHAMPIGNONS ──────────────────────────────────────────────────────────

        PathogenEntry(
            code = "CHALAROSE_FRÊNE",
            nomFr = "Chalarose du frêne",
            nomLatin = "Hymenoscyphus fraxineus",
            type = TypeAgent.CHAMPIGNON,
            menace = NiveauMenace.CATASTROPHIQUE,
            tendanceCC = TendanceCC.EN_HAUSSE,
            essencesHotes = listOf("FREX"),
            zonesRisqueEleve = setOf(ClimateZone.ATLANTIQUE, ClimateZone.SEMI_OCEANIQUE, ClimateZone.CONTINENTALE),
            symptomes = "Nécroses corticales, dépérissement des tiges, chancres à la base, mortalité progressive",
            facteursDeclenchement = listOf("Peuplements denses en zone humide", "Été doux et humide"),
            actionsCNPF = listOf(
                "Identifier et marquer les individus tolérants (< 20% de dépérissement)",
                "Conserver les souches vigoureuses pour régénération naturelle",
                "Éviter les blessures favorisant l'entrée du pathogène",
                "Envisager remplacement progressif par érable, aulne, chêne"
            ),
            signalementObligatoire = true
        ),

        PathogenEntry(
            code = "GRAPHIOSE_ORME",
            nomFr = "Graphiose de l'orme",
            nomLatin = "Ophiostoma novo-ulmi",
            type = TypeAgent.CHAMPIGNON,
            menace = NiveauMenace.CATASTROPHIQUE,
            tendanceCC = TendanceCC.EN_HAUSSE,
            essencesHotes = listOf("ULGL", "ULMO", "ULSC"),
            zonesRisqueEleve = setOf(ClimateZone.ATLANTIQUE, ClimateZone.SEMI_OCEANIQUE, ClimateZone.CONTINENTALE),
            symptomes = "Jaunissement et flétrissement en tête, galeries sous l'écorce, dépérissement en 1–5 ans",
            facteursDeclenchement = listOf("Présence du scolyte vecteur (Scolytus ssp.)", "Arbres affaiblis"),
            actionsCNPF = listOf(
                "Abattre et incinérer les arbres morts ou très dépérissants sans délai",
                "Éviter les blessures de tronc — vecteur insecte",
                "Préférer Ulmus laevis (orme blanc) ou espèces résistantes en replantation"
            ),
            signalementObligatoire = true
        ),

        PathogenEntry(
            code = "ARMILLAIRE",
            nomFr = "Armillaire (pourridié-agaric)",
            nomLatin = "Armillaria mellea / ostoyae",
            type = TypeAgent.CHAMPIGNON,
            menace = NiveauMenace.FORT,
            tendanceCC = TendanceCC.EN_HAUSSE,
            essencesHotes = listOf("PIAB", "PISY", "ABAL", "PSME", "QUPE", "FASY"),
            zonesRisqueEleve = setOf(ClimateZone.MONTAGNARDE, ClimateZone.SEMI_OCEANIQUE, ClimateZone.CONTINENTALE),
            symptomes = "Dépérissement progressif, rhizomorphes noirs sous l'écorce, fructifications en touffes à la base",
            facteursDeclenchement = listOf("Arbres stressés par sécheresse", "Peuplements équiennes âgés", "Coupes avec maintien de souches"),
            actionsCNPF = listOf(
                "Éviter les coupes rases avec maintien de souches volumineuses",
                "Dessoucher les foyers d'infection si possible",
                "Favoriser les essences moins sensibles (chênes, mélèze)"
            )
        ),

        PathogenEntry(
            code = "FOMES",
            nomFr = "Fomès (polypore du pied)",
            nomLatin = "Heterobasidion annosum",
            type = TypeAgent.CHAMPIGNON,
            menace = NiveauMenace.FORT,
            tendanceCC = TendanceCC.STABLE,
            essencesHotes = listOf("PIAB", "PISY", "PIPN", "ABAL", "PSME"),
            zonesRisqueEleve = setOf(ClimateZone.MONTAGNARDE, ClimateZone.CONTINENTALE, ClimateZone.SEMI_OCEANIQUE),
            symptomes = "Pourriture rouge du cœur, mortalité en plages, carpophores blancs/brun à la base",
            facteursDeclenchement = listOf("Sols calcaires ou sableux drainants", "Coupes avec contamination des souches fraîches"),
            actionsCNPF = listOf(
                "Traiter les souches fraîches avec Phlebiopsis gigantea (biocontrôle)",
                "Réduire la densité pour limiter le contact entre racines",
                "Favoriser la diversification en essences feuillues"
            )
        ),

        PathogenEntry(
            code = "ENCRE_CHÂTAIGNIER",
            nomFr = "Encre du châtaignier",
            nomLatin = "Phytophthora cinnamomi / cambivora",
            type = TypeAgent.OOMYCETE,
            menace = NiveauMenace.FORT,
            tendanceCC = TendanceCC.EN_HAUSSE,
            essencesHotes = listOf("CASA"),
            zonesRisqueEleve = setOf(ClimateZone.ATLANTIQUE, ClimateZone.MEDITERRANEENNE, ClimateZone.SEMI_OCEANIQUE),
            symptomes = "Écoulement noirâtre au collet, décoloration de l'aubier, dépérissement de la cime",
            facteursDeclenchement = listOf("Sols compactés ou hydromorphes", "Températures hivernales douces", "Matériel végétal infecté"),
            actionsCNPF = listOf(
                "Utiliser uniquement du matériel végétal certifié",
                "Éviter les travaux du sol en périodes humides",
                "Proscrire le brûlage en lisière — spores hygrophiles"
            )
        ),

        PathogenEntry(
            code = "CHANCRE_CHÂTAIGNIER",
            nomFr = "Chancre du châtaignier",
            nomLatin = "Cryphonectria parasitica",
            type = TypeAgent.CHAMPIGNON,
            menace = NiveauMenace.MODERE,
            tendanceCC = TendanceCC.STABLE,
            essencesHotes = listOf("CASA"),
            zonesRisqueEleve = setOf(ClimateZone.ATLANTIQUE, ClimateZone.SEMI_OCEANIQUE, ClimateZone.MEDITERRANEENNE),
            symptomes = "Chancres orangés sur l'écorce, anneaux de mycélium, mortalité des tiges au-dessus du chancre",
            facteursDeclenchement = listOf("Blessures d'élagage ou de taille", "Peuplements denses"),
            actionsCNPF = listOf(
                "Inoculer les souches hypovirulentes (biocontrôle européen)",
                "Éviter les blessures inutiles",
                "Couper les rameaux atteints à 30 cm sous le chancre"
            )
        ),

        PathogenEntry(
            code = "PHYTOPHTHORA_AULNE",
            nomFr = "Phytophthora de l'aulne",
            nomLatin = "Phytophthora uniformis × alni",
            type = TypeAgent.OOMYCETE,
            menace = NiveauMenace.FORT,
            tendanceCC = TendanceCC.EN_HAUSSE,
            essencesHotes = listOf("ALGL"),
            zonesRisqueEleve = setOf(ClimateZone.ATLANTIQUE, ClimateZone.SEMI_OCEANIQUE),
            symptomes = "Nécrose brune du cambium au niveau des racines et collet, cime clairsemée, petits chatons",
            facteursDeclenchement = listOf("Crues fréquentes", "Températures hivernales douces", "Peuplements en galeries riveraines"),
            actionsCNPF = listOf(
                "Signalement obligatoire au DSF",
                "Ne pas déplacer des plants depuis zones infectées",
                "Envisager remplacement par saule ou peuplier en cas de mortalité > 30%"
            ),
            signalementObligatoire = true
        ),

        PathogenEntry(
            code = "RHABDOCLINE",
            nomFr = "Rhabdocline du Douglas",
            nomLatin = "Rhabdocline pseudotsugae",
            type = TypeAgent.CHAMPIGNON,
            menace = NiveauMenace.MODERE,
            tendanceCC = TendanceCC.STABLE,
            essencesHotes = listOf("PSME"),
            zonesRisqueEleve = setOf(ClimateZone.ATLANTIQUE, ClimateZone.SEMI_OCEANIQUE),
            symptomes = "Taches jaunes devenant brun-rouille sur aiguilles, chute prématurée des aiguilles de l'année précédente",
            facteursDeclenchement = listOf("Printemps humides et frais", "Peuplements denses avec mauvaise ventilation"),
            actionsCNPF = listOf(
                "Sélectionner des provenances résistantes (Idaho, Côte Est USA)",
                "Favoriser la diversité structurelle pour aérer le peuplement",
                "Éclaircie précoce pour limiter l'humidité sous couvert"
            )
        ),

        PathogenEntry(
            code = "OÏDIUM_CHÊNE",
            nomFr = "Oïdium du chêne",
            nomLatin = "Erysiphe alphitoides",
            type = TypeAgent.CHAMPIGNON,
            menace = NiveauMenace.FAIBLE,
            tendanceCC = TendanceCC.EN_HAUSSE,
            essencesHotes = listOf("QUPE", "QURO", "QURU", "QUPU"),
            zonesRisqueEleve = setOf(ClimateZone.SEMI_OCEANIQUE, ClimateZone.ATLANTIQUE),
            symptomes = "Feutrage blanc sur jeunes feuilles et pousses, crispation, brunissement en été",
            facteursDeclenchement = listOf("Jeunes sujets < 5 ans", "Été chaud et sec suivi de pluies automnales", "Régénération dense"),
            actionsCNPF = listOf(
                "Pas d'intervention généralement nécessaire sur adultes",
                "En pépinière : traitements fongicides biologiques (soufre) si atteinte > 50%",
                "Assurer une bonne luminosité dès la régénération"
            )
        ),

        // ── INSECTES ─────────────────────────────────────────────────────────────

        PathogenEntry(
            code = "IPS_TYPOGRAPHUS",
            nomFr = "Scolyte typographe (épicéa)",
            nomLatin = "Ips typographus",
            type = TypeAgent.INSECTE,
            menace = NiveauMenace.CATASTROPHIQUE,
            tendanceCC = TendanceCC.EN_HAUSSE,
            essencesHotes = listOf("PIAB"),
            zonesRisqueEleve = setOf(ClimateZone.MONTAGNARDE, ClimateZone.CONTINENTALE, ClimateZone.SEMI_OCEANIQUE),
            symptomes = "Trous d'entrée de 2mm avec sciure rousse, galeries en H sous l'écorce, aiguilles roussissantes, mortalité en tache",
            facteursDeclenchement = listOf(
                "Sécheresse prolongée (2 étés successifs)",
                "Chablis non évacués",
                "Peuplements équiennes > 60 ans",
                "Températures > 18°C en mai–juin"
            ),
            actionsCNPF = listOf(
                "Évacuer les chablis en moins de 4 semaines (avant émergence)",
                "Poser des pièges phéromonaux de surveillance dès mars",
                "Abattre immédiatement les arbres attaqués (détection précoce)",
                "Réduire la densité des épicéas purs, diversifier les essences",
                "Proscrire les nouveaux peuplements d'épicéa purs en dessous de 600m"
            ),
            signalementObligatoire = false
        ),

        PathogenEntry(
            code = "PROCESSIONNAIRE_PIN",
            nomFr = "Processionnaire du pin",
            nomLatin = "Thaumetopoea pityocampa",
            type = TypeAgent.INSECTE,
            menace = NiveauMenace.MODERE,
            tendanceCC = TendanceCC.EN_HAUSSE,
            essencesHotes = listOf("PIPI", "PISY", "PIPN", "PILA", "PIHA"),
            zonesRisqueEleve = setOf(ClimateZone.MEDITERRANEENNE, ClimateZone.SEMI_OCEANIQUE, ClimateZone.CONTINENTALE),
            symptomes = "Nids soyeux blancs en hiver dans la cime, processions de chenilles au sol, défoliation partielle",
            facteursDeclenchement = listOf("Hivers doux (expansion vers le nord)", "Peuplements exposés au soleil", "Jeunes peuplements < 15 ans"),
            actionsCNPF = listOf(
                "Traitement au Bacillus thuringiensis kurstaki (août–octobre, stades L1–L3)",
                "Piégeage phéromonal des mâles adultes",
                "Perches à mésanges (prédation naturelle)",
                "Pas d'abattage préventif — défoliation rarement mortelle"
            )
        ),

        PathogenEntry(
            code = "BUPESTRE_HÊTRE",
            nomFr = "Bupestre du hêtre",
            nomLatin = "Agrilus viridis",
            type = TypeAgent.INSECTE,
            menace = NiveauMenace.MODERE,
            tendanceCC = TendanceCC.EN_HAUSSE,
            essencesHotes = listOf("FASY"),
            zonesRisqueEleve = setOf(ClimateZone.SEMI_OCEANIQUE, ClimateZone.CONTINENTALE),
            symptomes = "Galeries sinueuses sous l'écorce, trous ovales en D à l'émergence, dépérissement de branches",
            facteursDeclenchement = listOf("Arbres stressés par sécheresse", "Blessures récentes", "Peuplements exposés au sud"),
            actionsCNPF = listOf(
                "Éviter les blessures en période de vol (mai–juillet)",
                "Abattre rapidement les arbres dépérissants > 50% cime morte",
                "Irriguer les arbres de valeur en période de sécheresse"
            )
        ),

        PathogenEntry(
            code = "AGRILE_CHÊNE",
            nomFr = "Agrile du chêne",
            nomLatin = "Agrilus biguttatus",
            type = TypeAgent.INSECTE,
            menace = NiveauMenace.MODERE,
            tendanceCC = TendanceCC.EN_HAUSSE,
            essencesHotes = listOf("QUPE", "QURO"),
            zonesRisqueEleve = setOf(ClimateZone.SEMI_OCEANIQUE, ClimateZone.CONTINENTALE),
            symptomes = "Chancres et suintements sur le tronc, galeries sous l'écorce, dépérissement du houppier",
            facteursDeclenchement = listOf("Arbres âgés > 100 ans affaiblis", "Sécheresses répétées"),
            actionsCNPF = listOf(
                "Surveiller les vieux chênes isolés après sécheresse",
                "Abattre les sujets très dépérissants pour limiter la pression parasitaire"
            )
        ),

        PathogenEntry(
            code = "PUCERON_DOUGLAS",
            nomFr = "Puceron lanigère du Douglas",
            nomLatin = "Adelges cooleyi",
            type = TypeAgent.INSECTE,
            menace = NiveauMenace.FAIBLE,
            tendanceCC = TendanceCC.STABLE,
            essencesHotes = listOf("PSME"),
            zonesRisqueEleve = setOf(ClimateZone.ATLANTIQUE, ClimateZone.SEMI_OCEANIQUE),
            symptomes = "Galles allongées sur les épicéas hôtes alternants, fumagin sur Douglas, déformations foliaires",
            facteursDeclenchement = listOf("Présence d'épicéa à proximité", "Printemps chauds et secs"),
            actionsCNPF = listOf(
                "Éviter les plantations mixtes Douglas–Épicéa en peuplement dense",
                "Traitement biologique possible sur jeunes plants en pépinière"
            )
        ),

        PathogenEntry(
            code = "MARSSONINA_PEUPLIER",
            nomFr = "Marssonina du peuplier",
            nomLatin = "Marssonina brunnea",
            type = TypeAgent.CHAMPIGNON,
            menace = NiveauMenace.MODERE,
            tendanceCC = TendanceCC.STABLE,
            essencesHotes = listOf("POHY", "PONI"),
            zonesRisqueEleve = setOf(ClimateZone.ATLANTIQUE, ClimateZone.SEMI_OCEANIQUE),
            symptomes = "Taches brun-noir sur feuilles, défoliation précoce dès juillet-août, affaiblissement progressif",
            facteursDeclenchement = listOf("Été humide", "Peuplements denses", "Clones sensibles"),
            actionsCNPF = listOf(
                "Choisir des clones résistants certifiés (I-214 remplacé par Koster, Oudenberg)",
                "Ramasser et détruire les feuilles tombées en automne",
                "Éclaircie pour améliorer la ventilation"
            )
        ),

        PathogenEntry(
            code = "TYPOGRAPHE_SAPIN",
            nomFr = "Scolyte du sapin",
            nomLatin = "Pityokteines curvidens",
            type = TypeAgent.INSECTE,
            menace = NiveauMenace.FORT,
            tendanceCC = TendanceCC.EN_HAUSSE,
            essencesHotes = listOf("ABAL", "ABNO"),
            zonesRisqueEleve = setOf(ClimateZone.MONTAGNARDE),
            symptomes = "Galeries en étoile sous l'écorce, sciure blanche, jaunissement de la cime",
            facteursDeclenchement = listOf("Sécheresses répétées", "Arbres blessés ou affaiblis", "Tempêtes"),
            actionsCNPF = listOf(
                "Évacuer les chablis et arbres dépérissants en moins de 8 semaines",
                "Réduire la densité pour renforcer la vigueur individuelle",
                "Diversifier avec sapin de Nordmann plus résistant"
            )
        ),

        PathogenEntry(
            code = "SCLERODERRIS",
            nomFr = "Brûlure de Scleroderris",
            nomLatin = "Gremmeniella abietina",
            type = TypeAgent.CHAMPIGNON,
            menace = NiveauMenace.MODERE,
            tendanceCC = TendanceCC.STABLE,
            essencesHotes = listOf("PISY", "PIPN"),
            zonesRisqueEleve = setOf(ClimateZone.MONTAGNARDE, ClimateZone.CONTINENTALE),
            symptomes = "Brunissement des pousses terminales en fin d'hiver, chancres sur tiges, dépérissement en manchon",
            facteursDeclenchement = listOf("Hivers à enneigement prolongé", "Peuplements denses en altitude"),
            actionsCNPF = listOf(
                "Sélectionner des provenances résistantes (Russie, Pologne pour pin sylvestre)",
                "Éviter les densités excessives en altitude"
            )
        )
    )

    private val INDEX_BY_CODE: Map<String, PathogenEntry> = ENTRIES.associateBy { it.code }

    private val INDEX_BY_HOTE: Map<String, List<PathogenEntry>> =
        buildMap<String, MutableList<PathogenEntry>> {
            ENTRIES.forEach { entry ->
                entry.essencesHotes.forEach { hote ->
                    getOrPut(hote) { mutableListOf() }.add(entry)
                }
            }
        }

    fun get(code: String): PathogenEntry? = INDEX_BY_CODE[code]

    fun getForEssence(essenceCode: String): List<PathogenEntry> =
        INDEX_BY_HOTE[essenceCode.uppercase()].orEmpty()

    fun getForEssenceInZone(essenceCode: String, zone: ClimateZone): List<PathogenEntry> =
        getForEssence(essenceCode).filter { zone in it.zonesRisqueEleve || it.zonesRisqueEleve.isEmpty() }
            .sortedByDescending { it.menace.ordinal }

    fun getCritical(): List<PathogenEntry> =
        ENTRIES.filter { it.menace == NiveauMenace.CATASTROPHIQUE }

    fun getMandatoryReports(): List<PathogenEntry> =
        ENTRIES.filter { it.signalementObligatoire }

    fun getIncreasingWithCC(): List<PathogenEntry> =
        ENTRIES.filter { it.tendanceCC == TendanceCC.EN_HAUSSE }
            .sortedByDescending { it.menace.ordinal }

    fun buildSummaryForEssences(
        essenceCodes: List<String>,
        zone: ClimateZone
    ): List<PathogenSummary> = essenceCodes.flatMap { code ->
        getForEssenceInZone(code, zone).map { p ->
            PathogenSummary(
                essenceCode = code,
                pathogenCode = p.code,
                nomFr = p.nomFr,
                menace = p.menace,
                tendanceCC = p.tendanceCC,
                actionPrioritaire = p.actionsCNPF.firstOrNull() ?: "",
                signalementObligatoire = p.signalementObligatoire
            )
        }
    }.distinctBy { it.pathogenCode }
        .sortedWith(compareByDescending<PathogenSummary> { it.menace.ordinal }
            .thenBy { it.nomFr })

    data class PathogenSummary(
        val essenceCode: String,
        val pathogenCode: String,
        val nomFr: String,
        val menace: NiveauMenace,
        val tendanceCC: TendanceCC,
        val actionPrioritaire: String,
        val signalementObligatoire: Boolean
    )
}
