package com.forestry.counter.domain.usecase.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager

/**
 * Moteur de détection de couverture mobile et d'état réseau.
 *
 * IMPORTANT : distingue toujours :
 *   - détection réelle de l'appareil  (NetworkState)
 *   - présomption cartographique locale (CoveragePresumption)
 *   - conseils offline résultants
 *
 * Ne jamais présenter la couverture théorique comme certaine.
 */
object MobileCoverageEngine {

    // ─── Modèles de sortie ────────────────────────────────────────────────────

    data class CoverageReport(
        val networkState: NetworkState,
        val coveragePresumption: CoveragePresumption,
        val syncQuality: SyncQuality,
        val offlineAdvised: Boolean,
        val advices: List<String>,
        val details: String
    )

    data class NetworkState(
        val type: NetworkType,
        val radioGeneration: RadioGeneration,
        val signalLevel: SignalLevel,       // basé sur type réseau
        val isRoaming: Boolean,
        val labelFr: String
    )

    data class CoveragePresumption(
        val level: CoverageLevel,
        val confidence: PresumptionConfidence,
        val source: String,
        val note: String
    )

    enum class NetworkType(val labelFr: String) {
        NONE("Aucun réseau"),
        WIFI("Wi-Fi"),
        MOBILE_WEAK("Mobile faible"),
        MOBILE_CORRECT("Mobile correct"),
        MOBILE_STRONG("Mobile fort"),
        ETHERNET("Câble/Ethernet")
    }

    enum class RadioGeneration(val labelFr: String) {
        NONE("Pas de réseau"),
        G2("2G (GPRS/EDGE — très lent)"),
        G3("3G (UMTS/HSPA)"),
        G4("4G (LTE)"),
        G5("5G"),
        WIFI("Wi-Fi"),
        UNKNOWN("Génération inconnue")
    }

    enum class SignalLevel(val labelFr: String) {
        NONE("Absent"),
        WEAK("Faible"),
        ACCEPTABLE("Acceptable"),
        GOOD("Bon"),
        EXCELLENT("Excellent")
    }

    enum class CoverageLevel(val labelFr: String) {
        LIKELY_NONE("Couverture probablement absente"),
        UNCERTAIN("Couverture incertaine"),
        PARTIAL("Couverture partielle probable"),
        LIKELY_PRESENT("Couverture probable"),
        GOOD("Bonne couverture probable")
    }

    enum class PresumptionConfidence(val labelFr: String) {
        DETECTED("Détecté sur l'appareil"),
        ESTIMATED("Estimé depuis état réseau"),
        UNKNOWN("Non déterminable hors ligne")
    }

    enum class SyncQuality(val labelFr: String, val icon: String) {
        IMPOSSIBLE("Synchronisation impossible", "🔴"),
        DEGRADED("Synchro dégradée — très lent", "🟠"),
        LIMITED("Synchro limitée — données légères seulement", "🟡"),
        POSSIBLE("Synchro possible", "🟢"),
        GOOD("Bonne synchro", "🟢")
    }

    // ─── Analyse principale ───────────────────────────────────────────────────

    /**
     * Analyse l'état réseau réel de l'appareil et produit un rapport complet.
     *
     * @param context       Android Context (nécessaire pour ConnectivityManager)
     * @param lat           Latitude GPS courante (facultatif, pour présomption géo)
     * @param lon           Longitude GPS courante (facultatif)
     */
    fun analyze(context: Context, lat: Double? = null, lon: Double? = null): CoverageReport {
        val networkState = detectNetworkState(context)
        val presumption  = estimateCoveragePresumption(networkState, lat, lon)
        val syncQuality  = computeSyncQuality(networkState)
        val offlineAdvised = shouldAdviseOffline(networkState, syncQuality)
        val advices      = buildAdvices(networkState, syncQuality, offlineAdvised)
        val details      = buildDetails(networkState, presumption, syncQuality)

        return CoverageReport(
            networkState      = networkState,
            coveragePresumption = presumption,
            syncQuality       = syncQuality,
            offlineAdvised    = offlineAdvised,
            advices           = advices,
            details           = details
        )
    }

    // ─── Détection réseau réel ────────────────────────────────────────────────

    private fun detectNetworkState(context: Context): NetworkState {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return noNetwork()
        val network = cm.activeNetwork ?: return noNetwork()
        val caps    = cm.getNetworkCapabilities(network) ?: return noNetwork()

        val isWifi    = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isMobile  = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

        return when {
            isWifi     -> buildWifiState()
            isEthernet -> NetworkState(
                NetworkType.ETHERNET, RadioGeneration.WIFI, SignalLevel.EXCELLENT,
                false, "Connexion câble"
            )
            isMobile   -> buildMobileState(context)
            else       -> noNetwork()
        }
    }

    private fun noNetwork() = NetworkState(
        NetworkType.NONE, RadioGeneration.NONE, SignalLevel.NONE, false, "Aucun réseau détecté"
    )

    private fun buildWifiState() = NetworkState(
        NetworkType.WIFI, RadioGeneration.WIFI, SignalLevel.GOOD, false, "Wi-Fi connecté"
    )

    private fun buildMobileState(context: Context): NetworkState {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val isRoaming = tm?.isNetworkRoaming ?: false

        val (gen, type, signal) = when (tm?.networkType) {
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT  -> Triple(RadioGeneration.G2, NetworkType.MOBILE_WEAK,   SignalLevel.WEAK)
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B -> Triple(RadioGeneration.G3, NetworkType.MOBILE_CORRECT, SignalLevel.ACCEPTABLE)
            TelephonyManager.NETWORK_TYPE_LTE     -> Triple(RadioGeneration.G4, NetworkType.MOBILE_STRONG,  SignalLevel.GOOD)
            TelephonyManager.NETWORK_TYPE_NR      -> Triple(RadioGeneration.G5, NetworkType.MOBILE_STRONG,  SignalLevel.EXCELLENT)
            else                                  -> Triple(RadioGeneration.UNKNOWN, NetworkType.MOBILE_WEAK, SignalLevel.WEAK)
        }

        val label = buildString {
            append(gen.labelFr)
            if (isRoaming) append(" (roaming)")
        }
        return NetworkState(type, gen, signal, isRoaming, label)
    }

    // ─── Présomption couverture ───────────────────────────────────────────────

    private fun estimateCoveragePresumption(
        state: NetworkState,
        lat: Double?,
        lon: Double?
    ): CoveragePresumption {
        // Si réseau détecté : présomption déduite directement
        if (state.type != NetworkType.NONE) {
            val level = when (state.type) {
                NetworkType.WIFI, NetworkType.ETHERNET -> CoverageLevel.GOOD
                NetworkType.MOBILE_STRONG              -> CoverageLevel.GOOD
                NetworkType.MOBILE_CORRECT             -> CoverageLevel.LIKELY_PRESENT
                NetworkType.MOBILE_WEAK                -> CoverageLevel.PARTIAL
                else                                   -> CoverageLevel.UNCERTAIN
            }
            return CoveragePresumption(
                level      = level,
                confidence = PresumptionConfidence.DETECTED,
                source     = "Détection directe appareil",
                note       = "Couverture confirmée par l'appareil au moment de la mesure."
            )
        }

        // Sans réseau : estimation géographique très grossière
        val geoLevel = estimateCoverageFromGeo(lat, lon)
        return CoveragePresumption(
            level      = geoLevel,
            confidence = PresumptionConfidence.ESTIMATED,
            source     = "Estimation géographique approximative",
            note       = "⚠ Estimation non fiable — aucun réseau détecté. " +
                         "La couverture réelle peut différer selon l'opérateur et la topographie."
        )
    }

    /**
     * Estimation très grossière depuis la position GPS.
     * Basée sur : altitude (zones de montagne = couverture réduite).
     * À enrichir avec un pack de données de couverture local si disponible.
     */
    private fun estimateCoverageFromGeo(lat: Double?, lon: Double?): CoverageLevel {
        if (lat == null || lon == null) return CoverageLevel.UNCERTAIN

        // Zone France métropolitaine grossière
        val inFranceMetro = lat in 41.0..51.5 && lon in -5.5..9.5
        if (!inFranceMetro) return CoverageLevel.UNCERTAIN

        // Heuristique très simplifiée (à remplacer par un pack de données)
        // Zones montagneuses : lat/lon approx (Alpes, Pyrénées, Vosges, Massif Central)
        val likelyMountain = (lat in 44.0..46.5 && lon in 5.5..7.5) ||  // Alpes
                (lat in 42.5..43.5 && lon in -1.5..2.0) ||               // Pyrénées
                (lat in 47.5..48.5 && lon in 6.5..7.5) ||                // Vosges
                (lat in 44.5..46.5 && lon in 2.0..4.5)                   // Massif Central

        return if (likelyMountain) CoverageLevel.PARTIAL else CoverageLevel.LIKELY_PRESENT
    }

    // ─── Qualité de synchro ───────────────────────────────────────────────────

    private fun computeSyncQuality(state: NetworkState): SyncQuality = when (state.type) {
        NetworkType.NONE         -> SyncQuality.IMPOSSIBLE
        NetworkType.MOBILE_WEAK  -> if (state.radioGeneration == RadioGeneration.G2)
            SyncQuality.DEGRADED else SyncQuality.LIMITED
        NetworkType.MOBILE_CORRECT -> SyncQuality.POSSIBLE
        NetworkType.MOBILE_STRONG,
        NetworkType.WIFI,
        NetworkType.ETHERNET    -> SyncQuality.GOOD
    }

    // ─── Conseils ─────────────────────────────────────────────────────────────

    private fun shouldAdviseOffline(state: NetworkState, sync: SyncQuality): Boolean =
        state.type == NetworkType.NONE ||
        state.type == NetworkType.MOBILE_WEAK ||
        sync == SyncQuality.IMPOSSIBLE ||
        sync == SyncQuality.DEGRADED

    private fun buildAdvices(
        state: NetworkState,
        sync: SyncQuality,
        offlineAdvised: Boolean
    ): List<String> {
        val advices = mutableListOf<String>()

        when {
            state.type == NetworkType.NONE -> {
                advices += "Travail hors ligne — toutes les fonctions locales restent disponibles"
                advices += "Synchroniser avant de partir sur le terrain si possible"
                advices += "Les exports PDF/CSV fonctionnent sans réseau"
            }
            offlineAdvised -> {
                advices += "Réseau faible — préférer le travail hors ligne"
                advices += "Éviter le chargement de fonds de carte distants"
                advices += "La synchro sera possible dès retour en zone couverte"
            }
            state.type == NetworkType.MOBILE_CORRECT -> {
                advices += "Synchro possible — données légères recommandées"
                advices += "Fonds de carte volumineux : précharger en Wi-Fi"
            }
            state.type in listOf(NetworkType.MOBILE_STRONG, NetworkType.WIFI) -> {
                advices += "Bonne connexion — synchro et téléchargement de packs possibles"
                if (state.isRoaming) advices += "⚠ Roaming actif — vérifier les coûts de données"
            }
        }

        if (state.radioGeneration == RadioGeneration.G2) {
            advices += "2G détectée — très lent, ne pas tenter de télécharger des fonds de carte"
        }

        return advices
    }

    private fun buildDetails(
        state: NetworkState,
        presumption: CoveragePresumption,
        sync: SyncQuality
    ): String = buildString {
        append("Réseau : ${state.labelFr}. ")
        append("Synchro : ${sync.labelFr}. ")
        append("Couverture théorique : ${presumption.level.labelFr} (${presumption.confidence.labelFr}). ")
        if (presumption.note.isNotEmpty()) append(presumption.note)
    }
}
