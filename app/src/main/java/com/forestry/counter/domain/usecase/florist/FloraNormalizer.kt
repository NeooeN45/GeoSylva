package com.forestry.counter.domain.usecase.florist

/**
 * Moteur de normalisation et de recherche tolérante pour les noms d'espèces végétales.
 *
 * Gère : fautes d'orthographe, noms vernaculaires, synonymes, phonétique française,
 * saisie partielle, variantes accentuées, saisie dyslexique.
 *
 * Stratégie de score composite :
 *   1. Correspondance exacte (normalisée)      → 1.0
 *   2. Commence par (préfixe ≥ 3 car.)         → 0.90
 *   3. Levenshtein normalisé                   → proportionnel
 *   4. Phonétique française                    → 0.60–0.70
 *   5. Contient (sous-chaîne ≥ 4 car.)         → 0.70–0.75
 */
object FloraNormalizer {

    // ─── Types de résultats ──────────────────────────────────────────────────

    data class FloraSuggestion(
        val espece: EspeceVegetale,
        val matchedOn: String,
        val matchType: MatchType,
        val score: Float,           // 0.0–1.0
        val confidence: ConfidenceLevel
    )

    enum class MatchType(val labelFr: String) {
        EXACT_SCIENTIFIC("Nom scientifique exact"),
        EXACT_FRENCH("Nom français exact"),
        VERNACULAR("Nom vernaculaire"),
        SYNONYM("Synonyme"),
        FUZZY_SCIENTIFIC("Approx. scientifique"),
        FUZZY_FRENCH("Approx. nom français"),
        PHONETIC("Correspondance phonétique"),
        PARTIAL("Correspondance partielle")
    }

    enum class ConfidenceLevel(val labelFr: String, val colorToken: String) {
        HIGH("Forte", "green"),
        MEDIUM("Moyenne", "amber"),
        LOW("Faible", "orange"),
        UNCERTAIN("Incertaine", "red")
    }

    // ─── Index de recherche (construit à la première utilisation) ────────────

    private data class IndexEntry(
        val espece: EspeceVegetale,
        val text: String,
        val textNorm: String,
        val type: MatchType,
        val phoneticKey: String
    )

    private val searchIndex: List<IndexEntry> by lazy { buildIndex() }

    private fun buildIndex(): List<IndexEntry> {
        val entries = mutableListOf<IndexEntry>()
        FloristDatabase.species.forEach { sp ->
            val add = { text: String, type: MatchType ->
                val n = normalize(text)
                if (n.length >= 2)
                    entries += IndexEntry(sp, text, n, type, frenchPhonetic(n))
            }
            add(sp.taxonomie.nomScientifique, MatchType.EXACT_SCIENTIFIC)
            add(sp.taxonomie.nomFrancais,     MatchType.EXACT_FRENCH)
            sp.taxonomie.nomsVernaculaires.forEach { add(it, MatchType.VERNACULAR) }
            sp.taxonomie.synonymes.forEach         { add(it, MatchType.SYNONYM)   }
        }
        return entries
    }

    // ─── Point d'entrée principal ────────────────────────────────────────────

    /**
     * Recherche des espèces correspondant à la saisie tolérante.
     *
     * @param input         Saisie brute (peut contenir des fautes)
     * @param maxResults    Nombre max de résultats (défaut 8)
     * @param contextMilieu Milieu pour booster les espèces contextuelles
     * @param contextIds    Espèces déjà saisies (poids contextuel associatif)
     */
    fun search(
        input: String,
        maxResults: Int = 8,
        contextMilieu: TypeMilieu? = null,
        contextIds: List<String> = emptyList()
    ): List<FloraSuggestion> {
        if (input.isBlank() || input.length < 2) return emptyList()

        val query       = normalize(input)
        val phoneticQ   = frenchPhonetic(query)
        val best        = mutableMapOf<String, FloraSuggestion>()

        for (entry in searchIndex) {
            val raw = computeMatchScore(query, entry.textNorm, phoneticQ, entry.phoneticKey)
            if (raw < 0.35f) continue

            val matchType = resolveMatchType(raw, entry.type)
            val confidence = resolveConfidence(raw)
            val existing = best[entry.espece.id]
            if (existing == null || raw > existing.score) {
                best[entry.espece.id] = FloraSuggestion(
                    espece     = entry.espece,
                    matchedOn  = entry.text,
                    matchType  = matchType,
                    score      = raw,
                    confidence = confidence
                )
            }
        }

        var sorted = best.values.sortedByDescending { it.score }

        // Boost contextuel milieu
        if (contextMilieu != null) {
            sorted = sorted.sortedByDescending { s ->
                if (contextMilieu in s.espece.habitat.milieuxPrincipaux) s.score + 0.06f
                else s.score
            }
        }

        // Boost associatif : espèces compagnes de celles déjà saisies
        if (contextIds.isNotEmpty()) {
            val compagnes = contextIds.flatMap { id ->
                FloristDatabase.findById(id)?.interactions?.especesCompagnes ?: emptyList()
            }.toSet()
            sorted = sorted.sortedByDescending { s ->
                if (s.espece.taxonomie.nomScientifique in compagnes) s.score + 0.04f
                else s.score
            }
        }

        return sorted.take(maxResults)
    }

    /**
     * Autocomplétion rapide — retourne uniquement les noms français.
     */
    fun quickSuggest(
        input: String,
        maxResults: Int = 5,
        contextMilieu: TypeMilieu? = null
    ): List<String> =
        search(input, maxResults, contextMilieu).map { it.espece.taxonomie.nomFrancais }

    /**
     * Résolution d'une saisie unique — retourne la meilleure suggestion ou null.
     */
    fun bestMatch(input: String, contextMilieu: TypeMilieu? = null): FloraSuggestion? =
        search(input, 1, contextMilieu).firstOrNull()

    // ─── Calcul du score composite ───────────────────────────────────────────

    private fun computeMatchScore(
        query: String, target: String,
        phoneticQ: String, phoneticT: String
    ): Float {
        if (query == target) return 1.0f
        if (query.isEmpty() || target.isEmpty()) return 0.0f

        // Préfixe
        if (target.startsWith(query) && query.length >= 3) return 0.92f
        if (query.startsWith(target) && target.length >= 3) return 0.88f

        // Levenshtein normalisé
        val maxLen  = maxOf(query.length, target.length).toFloat()
        val lev     = levenshtein(query, target)
        val levScore = (1.0f - lev / maxLen).coerceIn(0f, 1f)

        // Phonétique française
        val phoneticScore = when {
            phoneticQ == phoneticT                    -> 0.72f
            phoneticQ.length >= 4 && phoneticT.length >= 4 &&
                phoneticQ.take(4) == phoneticT.take(4) -> 0.62f
            else                                       -> 0.0f
        }

        // Contient (sous-chaîne)
        val containsScore = when {
            query.length >= 4 && target.contains(query) -> 0.78f
            target.length >= 4 && query.contains(target) -> 0.74f
            else                                          -> 0.0f
        }

        // Token overlap (pour les noms composés : "fougere aigle" vs "fougere royale")
        val tokenScore = tokenOverlapScore(query, target)

        return maxOf(levScore, phoneticScore, containsScore, tokenScore)
    }

    private fun tokenOverlapScore(q: String, t: String): Float {
        val qTokens = q.split(" ").filter { it.length >= 3 }.toSet()
        val tTokens = t.split(" ").filter { it.length >= 3 }.toSet()
        if (qTokens.isEmpty() || tTokens.isEmpty()) return 0f
        val overlap = qTokens.intersect(tTokens).size.toFloat()
        return (overlap / maxOf(qTokens.size, tTokens.size)) * 0.80f
    }

    private fun resolveMatchType(score: Float, entryType: MatchType): MatchType = when {
        score >= 0.99f -> entryType
        score >= 0.80f -> when (entryType) {
            MatchType.EXACT_SCIENTIFIC -> MatchType.FUZZY_SCIENTIFIC
            MatchType.EXACT_FRENCH     -> MatchType.FUZZY_FRENCH
            else                       -> entryType
        }
        score >= 0.60f -> MatchType.PHONETIC
        else           -> MatchType.PARTIAL
    }

    private fun resolveConfidence(score: Float): ConfidenceLevel = when {
        score >= 0.95f -> ConfidenceLevel.HIGH
        score >= 0.75f -> ConfidenceLevel.MEDIUM
        score >= 0.55f -> ConfidenceLevel.LOW
        else           -> ConfidenceLevel.UNCERTAIN
    }

    // ─── Normalisation de chaîne ─────────────────────────────────────────────

    /**
     * Normalise une chaîne : minuscules, suppression accents, nettoyage ponctuation.
     * Conserve lettres, chiffres, espaces, tirets.
     */
    fun normalize(input: String): String =
        input.trim().lowercase()
            .map { c ->
                when (c) {
                    'à', 'â', 'ä'       -> 'a'
                    'é', 'è', 'ê', 'ë'  -> 'e'
                    'î', 'ï'            -> 'i'
                    'ô', 'ö'            -> 'o'
                    'ù', 'û', 'ü'       -> 'u'
                    'ç'                 -> 'c'
                    'ñ'                 -> 'n'
                    else                -> c
                }
            }
            .joinToString("")
            .replace(Regex("[^a-z0-9 \\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    // ─── Phonétique française ────────────────────────────────────────────────

    /**
     * Clé phonétique simplifiée orientée français.
     * Réduit les sons composés et standardise la prononciation pour
     * permettre la correspondance malgré les variations orthographiques.
     *
     * Exemples :
     *   "fougère"  → "FUJR"
     *   "fougere"  → "FUJR"
     *   "fojere"   → "FUJR"
     */
    fun frenchPhonetic(input: String): String {
        var s = normalize(input).uppercase()

        // Supprimer articles en début
        s = s.replace(Regex("^(DE |DU |LA |LE |LES |AU |AUX |UN |UNE |DES )"), "")

        // Groupes consonantiques
        s = s.replace("PH", "F")
        s = s.replace("QU", "K")
        s = s.replace("GU", "G")
        s = s.replace("CK", "K")
        s = s.replace("TCH", "T")
        s = s.replace("CH", "S")
        s = s.replace("GN", "N")
        s = s.replace("SS", "S")
        s = s.replace("TH", "T")
        s = s.replace("RH", "R")
        s = s.replace("LL", "L")

        // Diphtongues et voyelles composées
        s = s.replace("EAU", "O")
        s = s.replace("AOU", "U")
        s = s.replace("OUI", "UI")
        s = s.replace("OIN", "ON")
        s = s.replace("OIE", "UA")
        s = s.replace("OE", "E")
        s = s.replace("AE", "E")
        s = s.replace("AI", "E")
        s = s.replace("EI", "E")
        s = s.replace("AU", "O")
        s = s.replace("OU", "U")
        s = s.replace("EU", "E")
        s = s.replace("OI", "UA")

        // Nasales
        s = s.replace("IN", "AN")
        s = s.replace("IM", "AM")
        s = s.replace("AIN", "AN")
        s = s.replace("AIM", "AM")
        s = s.replace("UN", "AN")
        s = s.replace("UM", "AM")
        s = s.replace("EN", "AN")
        s = s.replace("EM", "AM")
        s = s.replace("AN", "A")
        s = s.replace("AM", "A")

        // C/G contextuel
        s = s.replace(Regex("C([EI])"), "S$1")
        s = s.replace(Regex("G([EI])"), "J$1")
        s = s.replace("C", "K")

        // Suppressions finales muettes
        s = s.replace(Regex("[EHY]+$"), "")
        s = s.replace(Regex("[AEIOUY]+"), "E")   // fusionner voyelles restantes
        s = s.replace(Regex("[^A-Z]"), "")        // ne garder que les lettres

        // Dédoublonner consonnes adjacentes identiques
        s = Regex("(.)\\1").replace(s) { it.groupValues[1] }

        return s.take(8)
    }

    // ─── Distance de Levenshtein ─────────────────────────────────────────────

    /**
     * Distance de Levenshtein entre deux chaînes.
     * Complexité O(m×n) — acceptable pour des noms d'espèces courts.
     */
    fun levenshtein(s1: String, s2: String): Int {
        val m = s1.length; val n = s2.length
        if (m == 0) return n
        if (n == 0) return m
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (s1[i - 1] == s2[j - 1]) dp[i - 1][j - 1]
                else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
            }
        }
        return dp[m][n]
    }

    // ─── Correction orthographique contextuelle ──────────────────────────────

    /**
     * Table de corrections fréquentes pour la saisie de noms de plantes
     * (fautes communes dans le contexte forestier français).
     */
    private val commonMisspellings = mapOf(
        "fougere aigle"   to "Pteridium aquilinum",
        "fougere royale"  to "Osmunda regalis",
        "molinie"         to "Molinia caerulea",
        "callune"         to "Calluna vulgaris",
        "bruyere"         to "Erica cinerea",
        "myrtile"         to "Vaccinium myrtillus",
        "myrtille"        to "Vaccinium myrtillus",
        "ronce"           to "Rubus fruticosus",
        "fougere"         to "Dryopteris filix-mas",
        "lierre"          to "Hedera helix",
        "sureau"          to "Sambucus nigra",
        "chevre feuille"  to "Lonicera periclymenum",
        "chevre-feuille"  to "Lonicera periclymenum",
        "noisetier"       to "Corylus avellana",
        "aulne"           to "Alnus glutinosa",
        "saule"           to "Salix alba",
        "peuplier"        to "Populus tremula",
        "orme"            to "Ulmus minor",
        "if"              to "Taxus baccata",
        "houx"            to "Ilex aquifolium",
        "charme"          to "Carpinus betulus",
        "coudrier"        to "Corylus avellana",
        "genets"          to "Cytisus scoparius",
        "genet"           to "Cytisus scoparius",
        "ajonc"           to "Ulex europaeus",
        "folle avoine"    to "Avenula pubescens",
        "laiche"          to "Carex sylvatica",
        "carex"           to "Carex sylvatica",
        "farde"           to "Festuca altissima",
        "millet"          to "Milium effusum",
        "gaillet"         to "Galium odoratum",
        "aspérule"        to "Galium odoratum",
        "asperule"        to "Galium odoratum",
        "muguet"          to "Convallaria majalis",
        "anémone"         to "Anemone nemorosa",
        "anemone"         to "Anemone nemorosa",
        "primevere"       to "Primula vulgaris",
        "oxalide"         to "Oxalis acetosella",
        "oseille"         to "Oxalis acetosella",
        "veronique"       to "Veronica chamaedrys",
        "stellaire"       to "Stellaria holostea",
        "epilobe"         to "Epilobium angustifolium",
        "ortie"           to "Urtica dioica",
        "digitale"        to "Digitalis purpurea",
        "dryoptere"       to "Dryopteris filix-mas",
        "polystic"        to "Polystichum aculeatum",
        "athyrie"         to "Athyrium filix-femina"
    )

    /**
     * Cherche une correction directe dans la table de correspondances.
     * Retourne le nom scientifique corrigé ou null.
     */
    fun findDirectCorrection(input: String): String? {
        val n = normalize(input)
        return commonMisspellings[n]
    }

    /**
     * Recherche enrichie : essaie d'abord la correction directe, puis la recherche floue.
     */
    fun searchWithCorrection(
        input: String,
        maxResults: Int = 8,
        contextMilieu: TypeMilieu? = null
    ): List<FloraSuggestion> {
        // Correction directe
        val directCorrection = findDirectCorrection(input)
        if (directCorrection != null) {
            val directResults = search(directCorrection, maxResults, contextMilieu)
            if (directResults.isNotEmpty()) return directResults
        }
        return search(input, maxResults, contextMilieu)
    }
}
