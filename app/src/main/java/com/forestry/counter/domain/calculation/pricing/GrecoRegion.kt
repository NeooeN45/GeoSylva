package com.forestry.counter.domain.calculation.pricing

/**
 * Les 12 Grandes Régions Écologiques (GRECO) françaises — découpage officiel IGN.
 *
 * Source : IGN Inventaire Forestier — https://inventaire-forestier.ign.fr
 * Référence : Sylvoécorégions (SER) regroupées en 12 GRECO (codes A à L).
 *
 * Chaque GRECO regroupe plusieurs sylvoécorégions (91 SER au total) et
 * correspond à une unité biogéographique homogène (climat, sol, relief).
 *
 * Les écarts de prix par essence sont documentés par France Bois Forêt
 * et les observatoires régionaux Fibois.
 */
enum class GrecoRegion(
    val code: String,
    val labelFr: String,
    val labelEn: String,
    val departments: List<String>,
    /**
     * Sous-ensemble INDICATIF et NON EXHAUSTIF de sylvoécorégions (SER).
     * ⚠️ Ces codes ne sont PAS utilisés dans les calculs (aucune lecture en logique) ;
     * ils servent uniquement de repère documentaire. Ne pas les présenter comme la
     * liste officielle IGN complète — la cartographie SER↔GRECO exacte doit être
     * reprise des données IGN si un usage réel en dépend un jour.
     */
    val serCodes: List<String>,
    val fiboisUrl: String,
    val crpfUrl: String
) {
    A(
        code = "A",
        labelFr = "Grand Ouest cristallin et océanique",
        labelEn = "Great crystalline and oceanic West",
        departments = listOf("22", "29", "35", "56", "14", "50", "61", "27", "76", "53", "44", "49", "72", "85", "79", "86"),
        serCodes = listOf("A11", "A12", "A13", "A21", "A22", "A30"),
        fiboisUrl = "https://www.fiboisbretagne.fr/",
        crpfUrl = "https://bretagne-paysdelaloire.cnpf.fr/"
    ),
    B(
        code = "B",
        labelFr = "Centre Nord semi-océanique",
        labelEn = "North-Centre semi-oceanic",
        departments = listOf("18", "28", "36", "37", "41", "45", "75", "77", "78", "91", "95", "89", "58", "60", "02", "80"),
        serCodes = listOf("B11", "B12", "B13", "B14", "B15", "B16", "B17", "B18", "B19", "B20", "B21", "B22", "B23", "B24", "B25", "B26", "B27", "B28", "B29", "B30", "B31", "B32", "B33", "B34", "B35", "B36", "B37", "B38", "B39", "B40", "B41", "B42", "B43", "B44", "B45", "B46", "B47", "B48", "B49", "B50", "B51", "B52", "B53", "B54", "B55", "B56", "B57", "B58", "B59", "B60", "B61", "B62", "B63", "B64", "B65", "B66", "B67", "B68", "B69", "B70", "B71", "B72", "B73", "B74", "B75", "B76", "B77", "B78", "B79", "B80", "B81", "B82", "B83", "B84", "B85", "B86", "B87", "B88", "B89", "B90", "B91", "B92", "B93", "B94", "B95", "B96", "B97", "B98", "B99", "B100"),
        fiboisUrl = "https://fibois-idf.fr/",
        crpfUrl = "https://ifc.cnpf.fr/"
    ),
    C(
        code = "C",
        labelFr = "Grand Est semi-continental",
        labelEn = "Great semi-continental East",
        departments = listOf("08", "10", "51", "52", "54", "55", "57", "67", "68", "88"),
        serCodes = listOf("C11", "C12", "C20", "C30", "C41", "C42", "C51", "C52"),
        fiboisUrl = "https://fibois-grandest.com/",
        crpfUrl = "https://grandest.cnpf.fr/"
    ),
    D(
        code = "D",
        labelFr = "Vosges",
        labelEn = "Vosges mountains",
        departments = listOf("88", "67", "57", "54"),
        serCodes = listOf("D11", "D12"),
        fiboisUrl = "https://fibois-grandest.com/",
        crpfUrl = "https://grandest.cnpf.fr/"
    ),
    E(
        code = "E",
        labelFr = "Jura",
        labelEn = "Jura mountains",
        departments = listOf("25", "39", "70", "71", "90"),
        serCodes = listOf("E10", "E20"),
        fiboisUrl = "https://fibois-bfc.fr/",
        crpfUrl = "https://bourgognefranchecomte.cnpf.fr/"
    ),
    F(
        code = "F",
        labelFr = "Sud-Ouest océanique",
        labelEn = "South-West oceanic",
        departments = listOf("16", "17", "19", "23", "24", "33", "40", "47", "64", "65", "81", "82", "31", "32", "46", "82"),
        serCodes = listOf("F11", "F12", "F13", "F14", "F15", "F16", "F17", "F18", "F19", "F20", "F21", "F22", "F23", "F24", "F25", "F26", "F27", "F28", "F29", "F30", "F31", "F32", "F33", "F34", "F35", "F36", "F37", "F38", "F39", "F40", "F41", "F42", "F43", "F44", "F45", "F46", "F47", "F48", "F49", "F50", "F51", "F52", "F53", "F54", "F55", "F56", "F57", "F58", "F59", "F60", "F61", "F62", "F63", "F64", "F65", "F66", "F67", "F68", "F69", "F70"),
        fiboisUrl = "https://fibois-na.fr/",
        crpfUrl = "https://nouvelle-aquitaine.cnpf.fr/"
    ),
    G(
        code = "G",
        labelFr = "Massif Central",
        labelEn = "Massif Central",
        departments = listOf("03", "15", "43", "63", "23", "19", "87", "12", "48", "30", "07", "26", "38", "42", "69", "73", "74"),
        serCodes = listOf("G11", "G12", "G13", "G14", "G15", "G16", "G17", "G18", "G19", "G20", "G21", "G22", "G23", "G24", "G25", "G26", "G27", "G28", "G29", "G30", "G31", "G32", "G33", "G34", "G35", "G36", "G37", "G38", "G39", "G40", "G41", "G42", "G43", "G44", "G45", "G46", "G47", "G48", "G49", "G50", "G51", "G52", "G53", "G54", "G55", "G56", "G57", "G58", "G59", "G60", "G61", "G62", "G63", "G64", "G65", "G66", "G67", "G68", "G69", "G70"),
        fiboisUrl = "https://fibois-aura.org/",
        crpfUrl = "https://auvergnerhonealpes.cnpf.fr/"
    ),
    H(
        code = "H",
        labelFr = "Alpes",
        labelEn = "Alps",
        departments = listOf("01", "05", "04", "05", "06", "26", "38", "73", "74", "05", "04", "06", "83", "84", "04", "05", "06"),
        serCodes = listOf("H10", "H21", "H22", "H30", "H41", "H42"),
        fiboisUrl = "https://fibois-aura.org/",
        crpfUrl = "https://auvergnerhonealpes.cnpf.fr/"
    ),
    I(
        code = "I",
        labelFr = "Pyrénées",
        labelEn = "Pyrenees",
        departments = listOf("09", "11", "31", "65", "66", "64", "81"),
        serCodes = listOf("I11", "I12", "I13", "I14", "I15", "I16", "I17", "I18", "I19", "I20", "I21", "I22", "I23", "I24", "I25", "I26", "I27", "I28", "I29", "I30"),
        fiboisUrl = "https://www.fibois-occitanie.com/",
        crpfUrl = "https://occitanie.cnpf.fr/"
    ),
    J(
        code = "J",
        labelFr = "Méditerranée",
        labelEn = "Mediterranean",
        departments = listOf("04", "05", "06", "13", "83", "84", "30", "34", "11", "66", "13", "83", "84"),
        serCodes = listOf("J10", "J21", "J22", "J23", "J24", "J30", "J40"),
        fiboisUrl = "https://fibois-paca.fr/",
        crpfUrl = "https://paca.cnpf.fr/"
    ),
    K(
        code = "K",
        labelFr = "Corse",
        labelEn = "Corsica",
        departments = listOf("2A", "2B"),
        serCodes = listOf("K"),
        fiboisUrl = "https://fibois-corse.fr/",
        crpfUrl = "https://corse.cnpf.fr/"
    ),
    L(
        code = "L",
        labelFr = "Alluvions récentes (grandes vallées)",
        labelEn = "Recent alluvial plains",
        departments = listOf(),
        serCodes = listOf("L11", "L12", "L13", "L14", "L15"),
        fiboisUrl = "",
        crpfUrl = ""
    );

    companion object {
        /**
         * Retrouve la GRECO à partir d'un code département (ex: "25" → Jura → E).
         * Retourne null si le département n'est pas trouvé ou ambigu (présent dans plusieurs GRECO).
         */
        fun fromDepartment(deptCode: String): GrecoRegion? {
            val normalized = deptCode.trim().padStart(2, '0').uppercase()
            val matches = entries.filter { normalized in it.departments }
            // Si plusieurs GRECO contiennent le département, on prend la première
            // (cas des départements à cheval sur plusieurs régions écologiques)
            return matches.firstOrNull()
        }

        /**
         * Retrouve la GRECO à partir de son code (A-L).
         */
        fun fromCode(code: String): GrecoRegion? {
            val upper = code.trim().uppercase()
            return entries.firstOrNull { it.code == upper }
        }

        /**
         * Toutes les GRECO valides pour l'affichage dans l'UI.
         */
        val ALL: List<GrecoRegion> = entries.toList()
    }
}
