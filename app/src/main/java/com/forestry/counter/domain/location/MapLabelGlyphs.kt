package com.forestry.counter.domain.location

/**
 * Glyphes Open Sans redistribués avec l'APK ; voir fonts/provenance.json,
 * NOTICE.txt et les licences embarquées. Aucune requête vers un serveur de démo.
 *
 * Le SDK Android 10.3.1 percent-décode le chemin asset avant lecture : le nom
 * exact « Open Sans Regular » correspond au dossier et au FontStack des PBF.
 * Les caractères absents de cette police ne deviennent pas disponibles par
 * la seule présence des 256 fichiers de plages Unicode.
 */
internal object MapLabelGlyphs {
    const val FONT_STACK = "Open Sans Regular"
    const val URI_TEMPLATE = "asset://fonts/{fontstack}/{range}.pbf"
}
