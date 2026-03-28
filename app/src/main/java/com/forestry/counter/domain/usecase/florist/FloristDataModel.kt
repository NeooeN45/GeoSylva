package com.forestry.counter.domain.usecase.florist

// ═══════════════════════════════════════════════════════════════════════════════
//  FloristDataModel.kt — Référentiel taxonomique & écologique des espèces
//  végétales pour GeoSylva  (conforme Flora Gallica / standards botaniques FR)
// ═══════════════════════════════════════════════════════════════════════════════

// ─── 1. TAXONOMIE ─────────────────────────────────────────────────────────────

data class Taxonomie(
    val nomScientifique: String,
    val auteur: String,
    val famille: String,
    val genre: String,
    val espece: String,
    val sousEspece: String? = null,
    val variete: String? = null,
    val synonymes: List<String> = emptyList(),
    val nomFrancais: String,
    val nomsVernaculaires: List<String> = emptyList()
)

// ─── 2. CLASSIFICATION ────────────────────────────────────────────────────────

enum class GroupeBiologique(val labelFr: String) {
    ANGIOSPERME_DICOT("Angiosperme dicotylédone"),
    ANGIOSPERME_MONOCOT("Angiosperme monocotylédone"),
    GYMNOSPERME("Gymnosperme (Conifère)"),
    FOUGERE("Fougère (Ptéridophyte)"),
    BRYOPHYTE("Bryophyte (Mousse)"),
    AUTRE("Autre")
}

enum class TypeBiologique(val labelFr: String, val codeRaunkiaer: String) {
    PHANEROPHYTE("Phanérophyte (arbre/arbuste >50 cm)", "Ph"),
    NANOPHANEROPHYTE("Nano-phanérophyte (arbuste <50 cm)", "NPh"),
    CHAMEPHYTE("Chaméphyte (sous-arbuste)", "Ch"),
    HEMICRYPTOPHYTE("Hémicryptophyte (herbacée vivace)", "H"),
    GEOPHYTE("Géophyte (bulbeuse/rhizomateuse)", "G"),
    THEROPHYTE("Thérophyte (annuelle)", "Th"),
    HYDROPHYTE("Hydrophyte (aquatique)", "Hy"),
    HELOPHYTE("Hélophyte (semi-aquatique)", "He")
}

enum class StrateVegetale(val labelFr: String, val hauteurApprox: String) {
    ARBRE("Arbre", "> 7 m"),
    ARBUSTE("Arbuste", "0.5 – 7 m"),
    SOUS_ARBUSTE("Sous-arbuste", "0.1 – 0.5 m"),
    HERBACEE("Herbacée", "< 1 m"),
    LIANE("Liane", "variable"),
    MOUSSE("Mousse/Bryophyte", "< 5 cm")
}

enum class StatutIndigene(val labelFr: String) {
    INDIGENE("Indigène"),
    ARCHAEOPHYTE("Archéophyte (introduit avant 1500)"),
    NEOPHYTE("Néophyte (introduit après 1500)"),
    NATURALISEE("Naturalisée"),
    CULTIVEE("Cultivée")
}

enum class StatutInvasif(val labelFr: String) {
    NON_INVASIVE("Non invasive"),
    ENVAHISSANTE_POTENTIELLE("Potentiellement envahissante"),
    ENVAHISSANTE("Espèce envahissante"),
    ENVAHISSANTE_MAJEURE("Invasive majeure")
}

enum class StatutProtection(val labelFr: String) {
    COMMUNE("Commune, sans protection"),
    ASSEZ_COMMUNE("Assez commune"),
    PEU_COMMUNE("Peu commune"),
    RARE("Rare"),
    TRES_RARE("Très rare"),
    PROTEGEE_REGIONALE("Protégée en région"),
    PROTEGEE_NATIONALE("Protégée en France")
}

data class Classification(
    val groupeBiologique: GroupeBiologique,
    val typeBiologique: TypeBiologique,
    val strateVegetale: StrateVegetale,
    val statutIndigene: StatutIndigene = StatutIndigene.INDIGENE,
    val statutInvasif: StatutInvasif = StatutInvasif.NON_INVASIVE,
    val statutProtection: StatutProtection = StatutProtection.COMMUNE
)

// ─── 3. MORPHOLOGIE ───────────────────────────────────────────────────────────

enum class TypeFeuille(val labelFr: String) {
    SIMPLE("Simple"),
    COMPOSEE_PENNEE("Composée pennée"),
    COMPOSEE_PALMATISEE("Composée palmatisée"),
    ECAILLEUSE("Écailleuse/aciculaire"),
    AIGUILLE("Aiguille"),
    AUTRE("Autre")
}

enum class DispositionFeuille(val labelFr: String) {
    ALTERNEE("Alternée"),
    OPPOSEE("Opposée"),
    VERTICILLEE("Verticillée"),
    EN_ROSETTE("En rosette basale")
}

enum class TypeFruit(val labelFr: String) {
    GLAND("Gland"),
    FAÎNE("Faîne"),
    CAPSULE("Capsule"),
    BAIE("Baie"),
    DRUPE("Drupe"),
    SAMARE("Samare ailée"),
    STROBIL("Strobil/Cône"),
    AKENE("Akène"),
    SILIQUE("Silique"),
    NOIX("Noix"),
    AUTRE("Autre")
}

enum class ModeDispersion(val labelFr: String) {
    ANEMOCHORE("Anémochore (vent)"),
    ZOOCHORE("Zoochore (animaux)"),
    BAROCHORE("Barochore (gravité)"),
    HYDROCHORE("Hydrochore (eau)"),
    MYRMECOCHORE("Myrméco­chore (fourmis)"),
    AUTOCHORE("Autochore (projection)")
}

data class Morphologie(
    val port: String,
    val tailleMoyenne: String,
    val descriptionGenerale: String,
    val typeFeuille: TypeFeuille,
    val dispositionFeuille: DispositionFeuille,
    val descriptionFeuilles: String,
    val typeInflorescence: String,
    val couleurFleurs: String,
    val periodeFloraison: String,
    val typeFruit: TypeFruit,
    val modeDispersion: ModeDispersion,
    val periodeFructification: String,
    val systemRacinaire: String,
    val particularitesMorpho: String = ""
)

// ─── 4. ÉCOLOGIE & AUTÉCOLOGIE ────────────────────────────────────────────────

enum class NiveauLumiere(val labelFr: String, val valeur: Int) {
    PLEIN_OMBRE("Très ombragé (sciaphile strict)", 1),
    OMBRE("Ombragé (sciaphile)", 2),
    DEMI_OMBRE("Demi-ombre", 3),
    LUMIERE_PARTIELLE("Lumière partielle", 4),
    PLEIN_SOLEIL("Plein soleil (héliophile)", 5)
}

enum class NiveauHumidite(val labelFr: String) {
    TRES_SEC("Très sec (xérique)"),
    SEC("Sec"),
    FRAIS("Frais/mésophile"),
    HUMIDE("Humide"),
    TRES_HUMIDE("Très humide/marécageux")
}

data class EcologieStation(
    val gradientHydriqueMin: Int,      // 1=très sec, 5=très humide
    val gradientHydriqueOptimal: Int,
    val gradientHydriqueMax: Int,
    val gradientTrophiqueMin: Int,     // 1=oligotrophe, 5=eutrophe
    val gradientTrophiqueOptimal: Int,
    val gradientTrophiqueMax: Int,
    val phMin: Double = 4.0,
    val phOptimal: Double = 6.0,
    val phMax: Double = 8.0,
    val tolereCalcaire: Boolean = false,
    val tolereHydromorphie: Boolean = false,
    val texturesSolPreferees: List<String> = emptyList(),
    val profondeurSolMinCm: Int = 20,
    val besoinLumiere: NiveauLumiere = NiveauLumiere.DEMI_OMBRE,
    val altitudeMinM: Int = 0,
    val altitudeOptimaleM: Int = 300,
    val altitudeMaxM: Int = 1800,
    val resistanceFroidMin: Double = -20.0,
    val sensibiliteGelTardif: Boolean = false,
    val climatsOptimaux: List<String> = emptyList(),
    val climatsToléres: List<String> = emptyList()
)

// ─── 5. VALEUR INDICATRICE ÉCOLOGIQUE ─────────────────────────────────────────

enum class IndicHumidite(val labelFr: String, val codeEllenberg: Int) {
    XEROPHYTE("Xérophyte strict — indicateur sol très sec", 1),
    XEROMESOPHYTE("Xéro-mésophyte — sol sec à frais", 2),
    MESOPHYTE("Mésophyte — milieu intermédiaire", 3),
    MESOHYGROPHYTE("Méso-hygrophyte — sol frais à humide", 4),
    HYGROPHYTE("Hygrophyte — sol humide", 5),
    HYGROPHYTE_STRICT("Hygrophyte strict — sol très humide", 6),
    HYDROPHYTE("Hydrophyte — milieu inondé ou submerge", 7)
}

enum class IndicFertilite(val labelFr: String, val codeEllenberg: Int) {
    OLIGOTROPHE("Oligotrophe — sol très pauvre", 1),
    MESOTROPHIE_FAIBLE("Méso-oligotrophe — sol pauvre", 2),
    MESOTROPHE("Mésotrophe — sol moyennement fertile", 3),
    MESOTROPHIE_ELEVEE("Méso-eutrophe — sol assez fertile", 4),
    EUTROPHE("Eutrophe — sol fertile", 5),
    NITROPHILE("Nitrophile — sol très riche en azote", 6)
}

enum class IndicAcidite(val labelFr: String, val codeEllenberg: Int) {
    ACIDOPHILE_STRICT("Acidophile strict — pH < 4.5", 1),
    ACIDOPHILE("Acidophile — pH 4.5–5.5", 2),
    ACIDICLINE("Acidicline — pH 5.5–6.5", 3),
    NEUTROPHILE("Neutrophile — pH 6.5–7.5", 4),
    BASOPHILE("Basiphile / Calcicole — pH > 7.5", 5)
}

data class ValeurIndicatrice(
    val ellenbergH: IndicHumidite,
    val ellenbergN: IndicFertilite,
    val ellenbergR: IndicAcidite,
    val indicateurPerturbation: Boolean = false,
    val indicateurCompaction: Boolean = false,
    val indicateurFertilisation: Boolean = false,
    val indicateurHydromorphie: Boolean = false,
    val significationEcologique: String = ""
)

// ─── 6. HABITAT ───────────────────────────────────────────────────────────────

enum class TypeMilieu(val labelFr: String) {
    FORET_FEUILLUE("Forêt feuillue"),
    FORET_RESINEUSE("Forêt résineuse"),
    FORET_MIXTE("Forêt mixte"),
    LISIERE("Lisière forestière"),
    CLAIRIERE("Clairière"),
    PRAIRIE_FORESTIERE("Prairie forestière"),
    ZONE_HUMIDE("Zone humide"),
    RIPISYLVE("Ripisylve / bord de cours d'eau"),
    LANDE("Lande"),
    MILIEU_ROCHEUX("Milieu rocheux / falaise"),
    MONTAGNARD("Étage montagnard/subalpin"),
    TOURBIERE("Tourbière"),
    TALUS_HAIES("Talus / haies"),
    MILIEU_PERTURBE("Milieu perturbé / friches")
}

data class Habitat(
    val milieuxPrincipaux: List<TypeMilieu>,
    val milieuxSecondaires: List<TypeMilieu> = emptyList(),
    val associationsVegetales: List<String> = emptyList(),
    val alliancesPhytosociologiques: List<String> = emptyList(),
    val etagesVegetation: List<String> = emptyList(),
    val descriptionHabitat: String = ""
)

// ─── 7. DISTRIBUTION GÉOGRAPHIQUE ─────────────────────────────────────────────

data class Distribution(
    val aireBiogeographique: String,
    val zoneClimatOriginaire: String,
    val presenceEurope: String = "",
    val repartitionFrance: String = "",
    val regionsEcologiquesFR: List<String> = emptyList()
)

// ─── 8. PHÉNOLOGIE & CYCLE DE VIE ─────────────────────────────────────────────

data class Phenologie(
    val periodeGermination: String = "",
    val moisFloraison: List<Int> = emptyList(),      // 1=jan, 12=déc
    val moisFructification: List<Int> = emptyList(),
    val dureeDeVie: String = "",
    val strategieReproduction: String = "",
    val capaciteColonisation: String = "",
    val vitesseCroissance: String = ""
)

// ─── 9. INTERACTIONS ÉCOLOGIQUES ─────────────────────────────────────────────

data class Interactions(
    val especesAssociees: List<String> = emptyList(),
    val especesConcurrentes: List<String> = emptyList(),
    val especesCompagnes: List<String> = emptyList(),
    val planteMellifere: Boolean = false,
    val planteRessourcesFaune: Boolean = false,
    val planteRefuge: Boolean = false,
    val mycorhizes: List<String> = emptyList(),
    val parasitesConnus: List<String> = emptyList()
)

// ─── 10. IMPORTANCE FORESTIÈRE ────────────────────────────────────────────────

enum class RoleForestier(val labelFr: String) {
    ESSENCE_DOMINANTE("Essence dominante forestière"),
    ESSENCE_ACCOMPAGNATRICE("Essence accompagnatrice"),
    ESSENCE_PIONNIERE("Essence pionnière"),
    ESSENCE_SOUS_BOIS("Essence de sous-bois"),
    ESSENCE_COLONISATRICE("Essence colonisatrice / lande"),
    INDICATRICE("Indicatrice écologique"),
    AUCUN("Sans rôle sylvicole particulier")
}

data class ImportanceForestiere(
    val roleForestier: RoleForestier = RoleForestier.AUCUN,
    val valeurBois: String = "",
    val usagesBois: List<String> = emptyList(),
    val sensibiliteSécheresse: Int = 3,  // 1 (insensible) – 5 (très sensible)
    val sensibiliteGel: Int = 3,
    val sensibilitePaturage: Int = 3,
    val sensibilitePietinement: Int = 3,
    val sensibiliteMaladies: String = "",
    val maladiesPrincipales: List<String> = emptyList(),
    val resistanceVent: Int = 3,
    val productivite: String = ""
)

// ─── 11. ENTITÉ PRINCIPALE ────────────────────────────────────────────────────

data class EspeceVegetale(
    val id: String,
    val taxonomie: Taxonomie,
    val classification: Classification,
    val morphologie: Morphologie,
    val ecologie: EcologieStation,
    val valeurIndicatrice: ValeurIndicatrice,
    val habitat: Habitat,
    val distribution: Distribution,
    val phenologie: Phenologie,
    val interactions: Interactions,
    val importanceForestiere: ImportanceForestiere,
    val notesComplementaires: String = ""
)

// ─── 12. RÉSULTAT DU DIAGNOSTIC FLORISTIQUE ───────────────────────────────────

data class DiagnosticFloristique(
    val especesObservees: List<String>,                   // IDs des espèces
    val gradientHydriqueDeduît: Double,                   // 1-7 (Ellenberg H)
    val gradientFertiliteDeduît: Double,                  // 1-6 (Ellenberg N)
    val indicateurAcidite: IndicAcidite,
    val probabiliteHydromorphie: Double,                  // 0-1
    val probabiliteCompaction: Double,                    // 0-1
    val probabilitePerturbation: Double,                  // 0-1
    val especesIndicatricesCles: List<EspeceVegetale>,
    val interpretationTextuelle: String
)
