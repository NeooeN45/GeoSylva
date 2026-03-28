package com.forestry.counter.domain.geo

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Carte géologique simplifiée France — 100% offline, embarquée.
 *
 * ~90 zones géologiques représentatives couvrant la France métropolitaine.
 * Interpolation IDW (6 voisins) pour toute position.
 *
 * Sources : BRGM Carte géologique France 1/1 000 000 (CC-BY),
 *           Carte pédogéologique INRAE, Référentiel Pédologique 2008.
 *
 * Utilisation : enrichissement du GpsContext pour le typage écologique
 * (roche mère → pH attendu, texture, type de sol dominant).
 */
object GeologyEmbeddedService {

    data class GeologyContext(
        val rocheMere: String,              // ex: "Granite", "Calcaire jurassique"
        val typeLithologie: Lithologie,
        val phIndicatif: Double,            // pH surface attendu (indicatif)
        val textureIndicative: String,      // SABLEUSE, LIMONEUSE, ARGILEUSE, GRAVELEUSE
        val typeSolDominant: String,        // Sol brun, Podzol, Rendzine, Gley...
        val source: String = "embedded_geology"
    )

    enum class Lithologie {
        GRANITE_GNEISS,     // socle cristallin acide
        BASALTE_VOLCANIQUE, // roches volcaniques basiques
        CALCAIRE,           // calcaires jurassiques/crétacés
        CRAIE,              // craie (calcaire tendre)
        GRES,               // grès (siliceux)
        SCHISTE_ARDOISE,    // métamorphiques
        ALLUVIONS,          // dépôts fluviaux récents
        LŒSS_LIMON,         // limons éoliens
        ARGILE,             // argiles, marnes
        SABLE_MARIN,        // sables littoraux / éoliens
        FLYSCH_MIXTE,       // alternances calcaire/schiste (Pyrénées)
        DOLOMIE,            // dolomies
        OPHIOLITE_ULTRAMAFIQUE // péridotites, serpentinites
    }

    // ─── Données embarquées ───────────────────────────────────────────────────
    // Format : floatArrayOf(lat, lon, lithologieCode, phIndicatif×10, textureCode)
    // Lithologie : enum ordinal (0=GRANITE...), Texture: 0=SABLEUSE 1=LIMONEUSE 2=ARGILEUSE 3=GRAVELEUSE

    private val POINTS = arrayOf(
        // === ARMORIQUE (granite/schiste) ===
        floatArrayOf(48.3f, -4.2f, 0f, 52f, 0f),   // Finistère — granite
        floatArrayOf(48.0f, -3.5f, 5f, 50f, 2f),   // Bretagne intérieure — schiste
        floatArrayOf(47.8f, -2.8f, 0f, 53f, 3f),   // Morbihan — granite
        floatArrayOf(48.5f, -1.8f, 5f, 54f, 2f),   // Ille-et-Vilaine — schiste
        floatArrayOf(47.2f, -1.5f, 5f, 55f, 2f),   // Vendée bocage — schiste
        // === MASSIF ARMORICAIN SUD ===
        floatArrayOf(46.6f, -1.8f, 0f, 52f, 0f),   // Vendée côte — granite
        floatArrayOf(47.0f, -0.8f, 5f, 54f, 2f),   // Maine — schiste

        // === NORMANDIE (limon sur calcaire) ===
        floatArrayOf(49.3f, -0.3f, 6f, 70f, 1f),   // Plaine de Caen — limon
        floatArrayOf(49.2f,  0.7f, 2f, 75f, 2f),   // Pays de Bray — calcaire
        floatArrayOf(48.9f,  1.8f, 6f, 72f, 1f),   // Pays d'Auge — limon
        floatArrayOf(49.5f,  1.0f, 6f, 73f, 1f),   // Pays de Caux — limon sur craie

        // === BASSIN PARISIEN (limon, craie, calcaire) ===
        floatArrayOf(48.8f,  2.3f, 6f, 77f, 1f),   // Paris — limon de plateau
        floatArrayOf(48.5f,  2.7f, 2f, 80f, 2f),   // Brie — argile à silex sur craie
        floatArrayOf(48.7f,  3.2f, 3f, 82f, 1f),   // Champagne — craie
        floatArrayOf(49.0f,  3.8f, 3f, 83f, 1f),   // Marne — craie
        floatArrayOf(48.3f,  3.5f, 3f, 81f, 1f),   // Haute-Marne — craie
        floatArrayOf(49.0f,  2.0f, 6f, 74f, 1f),   // Oise — limon
        floatArrayOf(49.2f,  2.8f, 6f, 75f, 1f),   // Aisne — limon
        floatArrayOf(48.2f,  2.0f, 2f, 78f, 2f),   // Gâtinais — calcaire
        floatArrayOf(47.5f,  2.8f, 2f, 80f, 2f),   // Berry — calcaire

        // === NORD / FLANDRES (limon) ===
        floatArrayOf(50.5f,  2.8f, 6f, 73f, 1f),   // Nord — limon flamand
        floatArrayOf(50.0f,  2.2f, 6f, 73f, 1f),   // Somme — limon

        // === ARDENNES (schiste) ===
        floatArrayOf(49.7f,  4.7f, 5f, 50f, 2f),   // Ardennes — schiste
        floatArrayOf(49.5f,  5.3f, 5f, 52f, 2f),   // Meuse — schiste/calcaire

        // === LORRAINE (calcaire/argile jurassique) ===
        floatArrayOf(49.0f,  6.2f, 2f, 73f, 2f),   // Lorraine — calcaire
        floatArrayOf(48.7f,  6.5f, 7f, 67f, 2f),   // Triasique Lorraine — argile
        floatArrayOf(48.2f,  6.0f, 2f, 71f, 2f),   // Côtes de Meuse — calcaire

        // === ALSACE (alluvions, grès vosges) ===
        floatArrayOf(48.5f,  7.6f, 6f, 70f, 1f),   // Plaine d'Alsace — alluvions Rhin
        floatArrayOf(47.7f,  7.3f, 6f, 68f, 1f),   // Haut-Rhin alluvial
        floatArrayOf(48.2f,  7.2f, 4f, 52f, 0f),   // Piémont Vosges — grès vosgien
        floatArrayOf(48.8f,  7.0f, 4f, 50f, 0f),   // Vosges Nord — grès

        // === VOSGES (granite/gneiss) ===
        floatArrayOf(48.0f,  6.9f, 0f, 49f, 3f),   // Vosges cristallines — granite
        floatArrayOf(47.8f,  6.7f, 5f, 51f, 2f),   // Vosges schiste

        // === JURA (calcaire) ===
        floatArrayOf(46.7f,  5.9f, 2f, 76f, 2f),   // Jura calcaire
        floatArrayOf(46.5f,  6.1f, 11f, 78f, 3f),  // Jura dolomie
        floatArrayOf(47.0f,  5.7f, 2f, 77f, 2f),   // Haute-Saône — calcaire

        // === BOURGOGNE (calcaire jurassique) ===
        floatArrayOf(47.3f,  4.7f, 2f, 81f, 2f),   // Côte d'Or — calcaire
        floatArrayOf(46.5f,  4.4f, 2f, 79f, 2f),   // Mâconnais — calcaire
        floatArrayOf(47.8f,  3.4f, 2f, 77f, 2f),   // Auxerrois — calcaire
        floatArrayOf(47.0f,  4.2f, 0f, 52f, 3f),   // Morvan — granite

        // === MASSIF CENTRAL NORD (granite/gneiss) ===
        floatArrayOf(46.0f,  2.8f, 0f, 53f, 3f),   // Creuse — granite
        floatArrayOf(45.8f,  3.1f, 0f, 54f, 3f),   // Puy-de-Dôme — granite
        floatArrayOf(46.5f,  2.5f, 0f, 52f, 3f),   // Allier — granite/gneiss
        // === MASSIF CENTRAL SUD (volcanique) ===
        floatArrayOf(45.3f,  3.0f, 1f, 57f, 3f),   // Haute-Loire — basalte
        floatArrayOf(44.7f,  3.2f, 1f, 56f, 2f),   // Cantal — basalte
        floatArrayOf(44.5f,  3.0f, 5f, 50f, 2f),   // Lozère — schiste
        floatArrayOf(44.3f,  2.7f, 2f, 72f, 2f),   // Causse Noir — calcaire
        floatArrayOf(44.0f,  3.0f, 2f, 80f, 3f),   // Causses — calcaire
        floatArrayOf(45.5f,  2.2f, 1f, 58f, 2f),   // Chaîne des Puys — basalte

        // === LIMOUSIN (granite) ===
        floatArrayOf(45.8f,  1.5f, 0f, 51f, 3f),   // Haute-Vienne — granite
        floatArrayOf(45.3f,  1.8f, 0f, 50f, 3f),   // Corrèze — granite

        // === PÉRIGORD / LOT (calcaire) ===
        floatArrayOf(44.8f,  0.6f, 2f, 75f, 3f),   // Périgord blanc — calcaire
        floatArrayOf(44.5f,  1.2f, 2f, 77f, 3f),   // Lot — calcaire
        floatArrayOf(44.2f,  2.5f, 2f, 79f, 3f),   // Causses Quercy — calcaire

        // === SOLOGNE / CENTRE (sable) ===
        floatArrayOf(47.5f,  1.7f, 6f, 56f, 0f),   // Sologne — sable
        floatArrayOf(47.0f,  2.2f, 6f, 58f, 0f),   // Berry sable — limon sableux

        // === GIRONDE / LANDES (sable éolien/maritime) ===
        floatArrayOf(44.8f, -0.7f, 9f, 52f, 0f),   // Landes — sable maritime
        floatArrayOf(44.3f, -1.0f, 9f, 49f, 0f),   // Landes profondes — sable
        floatArrayOf(44.7f,  0.3f, 9f, 53f, 0f),   // Gironde sable
        floatArrayOf(45.3f, -0.5f, 6f, 65f, 1f),   // Charente-Maritime — alluvions

        // === PYRÉNÉES ===
        floatArrayOf(43.2f, -0.4f, 0f, 60f, 3f),   // Pyrénées béarnaises — granite
        floatArrayOf(43.0f,  1.2f, 10f, 63f, 3f),  // Pyrénées centrales — flysch
        floatArrayOf(42.8f,  2.2f, 2f, 68f, 3f),   // Pyrénées calcaires
        floatArrayOf(42.6f,  2.8f, 0f, 58f, 3f),   // Pyrénées-Orientales — granite

        // === RHÔNE-ALPES ===
        floatArrayOf(45.8f,  4.8f, 6f, 68f, 1f),   // Lyon — alluvions Rhône
        floatArrayOf(45.2f,  5.5f, 2f, 70f, 2f),   // Chartreuse — calcaire
        floatArrayOf(45.3f,  5.8f, 0f, 58f, 3f),   // Belledonne — granite
        floatArrayOf(44.7f,  6.5f, 2f, 69f, 3f),   // Hautes-Alpes calcaires
        floatArrayOf(45.5f,  6.8f, 3f, 67f, 3f),   // Savoie — calcaire/craie
        floatArrayOf(46.0f,  6.6f, 2f, 72f, 3f),   // Haute-Savoie — calcaire Préalpes
        floatArrayOf(45.7f,  6.2f, 0f, 55f, 3f),   // Mont Blanc — granite
        floatArrayOf(44.3f,  6.3f, 2f, 72f, 3f),   // Alpes Provence — calcaire

        // === PROVENCE (calcaire) ===
        floatArrayOf(43.5f,  5.3f, 2f, 78f, 3f),   // Bouches-du-Rhône — calcaire
        floatArrayOf(43.7f,  5.0f, 2f, 77f, 2f),   // Vaucluse — calcaire
        floatArrayOf(43.5f,  6.2f, 2f, 75f, 3f),   // Var calcaire
        floatArrayOf(43.4f,  6.8f, 0f, 60f, 3f),   // Var Maures — granite

        // === LANGUEDOC ===
        floatArrayOf(43.8f,  3.5f, 2f, 74f, 3f),   // Causse Hérault — calcaire
        floatArrayOf(43.6f,  4.2f, 7f, 72f, 2f),   // Camargue — argile alluviale
        floatArrayOf(43.0f,  3.2f, 5f, 63f, 2f),   // Montagne Noire — schiste
        floatArrayOf(42.7f,  2.7f, 0f, 55f, 3f),   // Pyrénées-Orientales (Albères) — granite

        // === CORSE ===
        floatArrayOf(42.2f,  9.0f, 0f, 57f, 3f),   // Corse sud — granite
        floatArrayOf(42.5f,  9.2f, 5f, 58f, 2f),   // Corse est — schiste
        floatArrayOf(42.7f,  8.7f, 0f, 56f, 3f),   // Haute-Corse — granite
        floatArrayOf(41.8f,  8.9f, 0f, 60f, 3f)    // Cap Corse — schiste/granite
    )

    private val TEXTURE_LABELS = arrayOf("SABLEUSE", "LIMONEUSE", "ARGILEUSE", "GRAVELEUSE")
    private val LITHOLOGIE_VALUES = Lithologie.values()

    // ─── Types de sol par lithologie ──────────────────────────────────────────

    private fun typeSol(litho: Lithologie, ph: Double): String = when (litho) {
        Lithologie.GRANITE_GNEISS     -> if (ph < 4.5) "Podzol / Cryptopodzol" else "Sol brun acide (Cambisol)"
        Lithologie.BASALTE_VOLCANIQUE -> "Andosol / Sol brun eutroph"
        Lithologie.CALCAIRE           -> if (ph > 7.5) "Rendzine (Leptosol calcaire)" else "Sol brun calcique"
        Lithologie.CRAIE              -> "Rendzine colluviale sur craie"
        Lithologie.GRES               -> "Sol brun acide / Podzol (grès siliceux)"
        Lithologie.SCHISTE_ARDOISE    -> "Sol brun acide (Cambisol)"
        Lithologie.ALLUVIONS          -> "Sol alluvial (Fluvisol)"
        Lithologie.LŒSS_LIMON         -> "Luvisol / Sol brun lessivé"
        Lithologie.ARGILE             -> "Vertisol / Luvisol argileux"
        Lithologie.SABLE_MARIN        -> "Arénosol / Podzol"
        Lithologie.FLYSCH_MIXTE       -> "Sol brun (Cambisol) mixte"
        Lithologie.DOLOMIE            -> "Rendzine dolomitique"
        Lithologie.OPHIOLITE_ULTRAMAFIQUE -> "Sol ferralitique magnésien"
    }

    private fun rocheMereLabel(litho: Lithologie): String = when (litho) {
        Lithologie.GRANITE_GNEISS     -> "Granite / Gneiss"
        Lithologie.BASALTE_VOLCANIQUE -> "Basalte / Roche volcanique"
        Lithologie.CALCAIRE           -> "Calcaire (jurassique / crétacé)"
        Lithologie.CRAIE              -> "Craie"
        Lithologie.GRES               -> "Grès (siliceux)"
        Lithologie.SCHISTE_ARDOISE    -> "Schiste / Ardoise"
        Lithologie.ALLUVIONS          -> "Alluvions fluviales"
        Lithologie.LŒSS_LIMON         -> "Limon / Lœss"
        Lithologie.ARGILE             -> "Argile / Marne"
        Lithologie.SABLE_MARIN        -> "Sable éolien / maritime"
        Lithologie.FLYSCH_MIXTE       -> "Flysch (calcaire-schiste)"
        Lithologie.DOLOMIE            -> "Dolomie"
        Lithologie.OPHIOLITE_ULTRAMAFIQUE -> "Péridotite / Serpentinite"
    }

    // ─── API publique ──────────────────────────────────────────────────────────

    /**
     * Retourne le contexte géologique interpolé (IDW 6 voisins).
     * Toujours disponible offline.
     */
    fun getGeologyContext(lat: Double, lon: Double): GeologyContext {
        data class Neighbor(val dist: Double, val lithoCode: Int, val ph: Double, val texCode: Int)

        val neighbors = POINTS.map { p ->
            val dlat = lat - p[0]; val dlon = lon - p[1]
            val dist = sqrt(dlat * dlat + dlon * dlon).coerceAtLeast(1e-6)
            Neighbor(dist, p[2].toInt(), p[3] / 10.0, p[4].toInt())
        }.sortedBy { it.dist }.take(6)

        var wSum = 0.0; var phW = 0.0
        val lithoCount = IntArray(LITHOLOGIE_VALUES.size)
        val texCount   = IntArray(TEXTURE_LABELS.size)

        for (n in neighbors) {
            val w = 1.0 / n.dist.pow(2)
            wSum  += w
            phW   += w * n.ph
            lithoCount[n.lithoCode.coerceIn(0, lithoCount.lastIndex)]++
            texCount[n.texCode.coerceIn(0, texCount.lastIndex)]++
        }

        val ph      = (phW / wSum * 10).toLong() / 10.0
        val litho   = LITHOLOGIE_VALUES[lithoCount.indices.maxByOrNull { lithoCount[it] } ?: 0]
        val texture = TEXTURE_LABELS[texCount.indices.maxByOrNull { texCount[it] } ?: 1]

        return GeologyContext(
            rocheMere        = rocheMereLabel(litho),
            typeLithologie   = litho,
            phIndicatif      = ph,
            textureIndicative = texture,
            typeSolDominant  = typeSol(litho, ph)
        )
    }
}
