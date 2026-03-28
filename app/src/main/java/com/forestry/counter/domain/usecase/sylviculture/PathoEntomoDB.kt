package com.forestry.counter.domain.usecase.sylviculture

/**
 * Base de données pathologie & entomologie forestière française — 100% offline.
 *
 * Sources : DSF (Département de la Santé des Forêts / DGAL),
 *           INRAE Pathologie forestière, CNPF fiches maladies,
 *           FREDON France, EPPO (Organisation européenne de protection des plantes).
 */
object PathoEntomoDB {

    enum class TypeAgent { INSECTE, CHAMPIGNON, BACTERIE, PHYTOPLASME, VIRUS, NEMATODE, OOMYCETE, MAMMIFERE }
    enum class NiveauRisque { FAIBLE, MODERE, ELEVE, TRES_ELEVE, CATASTROPHIQUE }
    enum class TypeDegat { DEFOLIATION, ECORCE_GALERIES, POURRIDIES_RACINES, CHANCRE, FEUILLES_TACHES, DESSECHEMENT_CIMES, CHUTE_AIGUILLES, GALLES, BOIS_INTERNE }

    data class Pathogene(
        val id: String,
        val nomCommun: String,
        val nomSci: String,
        val typeAgent: TypeAgent,
        val essencesCibles: List<String>,      // IDs SylvicultureDatabase
        val niveauRisque: NiveauRisque,
        val typesDegat: List<TypeDegat>,
        val symptomes: String,
        val facteursFavorisants: List<String>,
        val periodeRisque: String,             // saison de détection/contamination
        val methodesLutte: List<String>,
        val estOrganismeReglemente: Boolean = false,
        val notesDSF: String = ""
    )

    // ─── Lookup ───────────────────────────────────────────────────────────────

    fun findByEssence(essenceId: String): List<Pathogene> =
        ALL.filter { p -> p.essencesCibles.any { it.equals(essenceId, ignoreCase = true) } }
            .sortedByDescending { it.niveauRisque.ordinal }

    fun findByNom(query: String): List<Pathogene> {
        val q = query.lowercase()
        return ALL.filter { it.nomCommun.lowercase().contains(q) || it.nomSci.lowercase().contains(q) }
    }

    fun findByRisque(niveau: NiveauRisque): List<Pathogene> =
        ALL.filter { it.niveauRisque == niveau }.sortedBy { it.nomCommun }

    // ─── Base de données ─────────────────────────────────────────────────────

    val ALL: List<Pathogene> = listOf(

        // ══ SCOLYTES ════════════════════════════════════════════════════════

        Pathogene(
            id = "IPSTYPOG", nomCommun = "Grand bostryche typographe",
            nomSci = "Ips typographus",
            typeAgent = TypeAgent.INSECTE,
            essencesCibles = listOf("PIAB", "ABBA", "PISY"),
            niveauRisque = NiveauRisque.CATASTROPHIQUE,
            typesDegat = listOf(TypeDegat.ECORCE_GALERIES, TypeDegat.DESSECHEMENT_CIMES),
            symptomes = "Aiguilles roussissant de la cime vers la base. Galeries en I sous l'écorce avec sciure blanche-orangée. Poussière de vermoulure au pied de l'arbre. Plaquettes d'écorce tombées.",
            facteursFavorisants = listOf("Sécheresse estivale prolongée", "Chablis non enlevés", "Peuplements denses mal éclaircis", "Températures printanières > 16°C"),
            periodeRisque = "Avril–Septembre (2–3 générations/an en plaine)",
            methodesLutte = listOf(
                "Exploitation rapide des arbres colonisés avant envol (<6 semaines)",
                "Pièges à phéromones pour suivi des populations",
                "Élimination des chablis < 3 mois après tempête",
                "Peuplements mélangés et moins denses (prévention)",
                "Arbres-pièges (à écorcer avant débourrement)"
            ),
            estOrganismeReglemente = false,
            notesDSF = "Ravageur n°1 des pessières européennes. Pullulation massive depuis 2018. +300% populations en France. Surveillance obligatoire dans les forêts de montagne."
        ),

        Pathogene(
            id = "TOMICUS", nomCommun = "Scolyte des pousses du pin",
            nomSci = "Tomicus piniperda",
            typeAgent = TypeAgent.INSECTE,
            essencesCibles = listOf("PISY", "PINI", "PIHA", "PIPE"),
            niveauRisque = NiveauRisque.ELEVE,
            typesDegat = listOf(TypeDegat.ECORCE_GALERIES, TypeDegat.DESSECHEMENT_CIMES),
            symptomes = "Pousses terminales desséchées (larves creusent les pousses en maturation). Galeries maternelles longitudinales sous l'écorce du tronc.",
            facteursFavorisants = listOf("Arbres affaiblis par la sécheresse", "Rémanents non exportés"),
            periodeRisque = "Janvier–Mars (vol hivernal précoce)",
            methodesLutte = listOf(
                "Sortie des bois abattus avant janvier",
                "Élimination des chablis dès l'automne",
                "Arbres-pièges (grumes non écorcées déposées en lisière)"
            ),
            notesDSF = "Vecteur possible de la bleuissure du bois. Vol précoce caractéristique (dès 6°C)."
        ),

        Pathogene(
            id = "XYLOSAN", nomCommun = "Xylébore dissemblable",
            nomSci = "Xylosandrus germanus",
            typeAgent = TypeAgent.INSECTE,
            essencesCibles = listOf("QUPE", "QUPES", "FASY", "ROPC", "PRCE", "ACPS"),
            niveauRisque = NiveauRisque.MODERE,
            typesDegat = listOf(TypeDegat.ECORCE_GALERIES, TypeDegat.BOIS_INTERNE),
            symptomes = "Petits trous circulaires (1–2 mm) dans l'écorce. Tunnels dans le bois sans galeries sous écorce. Champignons ambrosia cultivés dans les galeries.",
            facteursFavorisants = listOf("Arbres stressés (sécheresse, blessures)", "Peuplements denses"),
            periodeRisque = "Avril–Juin",
            methodesLutte = listOf("Maintien de la vitalité des peuplements", "Évacuation rapide des bois morts"),
            notesDSF = "Espèce exotique envahissante en progression. Vecteur d'agents pathogènes fongiques (Raffaelea sp.)."
        ),

        // ══ CHENILLES DÉFOLIATRICES ══════════════════════════════════════════

        Pathogene(
            id = "PROCPINE", nomCommun = "Processionnaire du pin",
            nomSci = "Thaumetopoea pityocampa",
            typeAgent = TypeAgent.INSECTE,
            essencesCibles = listOf("PISY", "PINI", "PIHA", "PIPE", "LADA"),
            niveauRisque = NiveauRisque.ELEVE,
            typesDegat = listOf(TypeDegat.DEFOLIATION),
            symptomes = "Nids soyeux blanc-gris en bout de branches (hiver). Processions de chenilles en file au sol (janvier–avril). Défoliation sévère des pins. Poils urticants dangereux pour l'homme et les animaux.",
            facteursFavorisants = listOf("Hivers doux (> -16°C requis pour mortalité)", "Pinèdes denses", "Réchauffement climatique — expansion vers le nord"),
            periodeRisque = "Nids : Octobre–Janvier / Processions : Janvier–Avril",
            methodesLutte = listOf(
                "Bacillus thuringiensis var. kurstaki (traitement biologique, stade L2-L3)",
                "Pièges à phéromones pour suivi",
                "Échenillage mécanique (hiver, EPI obligatoire)",
                "Encouragement des mésanges (nichoirs)",
                "Nématodes (Steinernema carpocapsae) au sol"
            ),
            estOrganismeReglemente = true,
            notesDSF = "Risque sanitaire public (poils urticants). Extension de l'aire vers le nord (+100 km depuis 1990). Désormais présente jusqu'en Île-de-France."
        ),

        Pathogene(
            id = "PROCCHENE", nomCommun = "Processionnaire du chêne",
            nomSci = "Thaumetopoea processionea",
            typeAgent = TypeAgent.INSECTE,
            essencesCibles = listOf("QUPE", "QUPES", "QURU"),
            niveauRisque = NiveauRisque.ELEVE,
            typesDegat = listOf(TypeDegat.DEFOLIATION),
            symptomes = "Nids gris sur troncs et branches maîtresses. Défoliation partielle des chênes (mai–juillet). Poils urticants très allergènes (histamine).",
            facteursFavorisants = listOf("Printemps chauds et secs", "Forêts âgées", "Orées de forêt"),
            periodeRisque = "Mai–Juillet",
            methodesLutte = listOf(
                "Bacillus thuringiensis (stade L2)",
                "Destruction des nids (automne, EPI obligatoire)",
                "Pièges à phéromones",
                "Traitement chimique (diflubenzuron) si défoliation > 30%"
            ),
            estOrganismeReglemente = true,
            notesDSF = "Risque sanitaire majeur. Signalement obligatoire dans certaines régions."
        ),

        Pathogene(
            id = "TORTVERTE", nomCommun = "Tordeuse verte du chêne",
            nomSci = "Tortrix viridana",
            typeAgent = TypeAgent.INSECTE,
            essencesCibles = listOf("QUPE", "QUPES", "QUPU"),
            niveauRisque = NiveauRisque.MODERE,
            typesDegat = listOf(TypeDegat.DEFOLIATION),
            symptomes = "Enroulement et minagede feuilles de chêne (mai–juin). Défoliation totale possible lors des pullulations. Chenilles vertes de 15–20 mm.",
            facteursFavorisants = listOf("Printemps chauds", "Peuplements denses"),
            periodeRisque = "Mai–Juin",
            methodesLutte = listOf(
                "Bacillus thuringiensis (traitements aériens si nécessaire)",
                "Surveiller pendant 3 ans consécutifs avant décision"
            ),
            notesDSF = "Pullulations cycliques (~7 ans). Les chênes émettent généralement un reflux foliaire. Mortalité rare sans facteurs aggravants."
        ),

        Pathogene(
            id = "BOMBDISPAR", nomCommun = "Spongieuse (Bombyx disparate)",
            nomSci = "Lymantria dispar",
            typeAgent = TypeAgent.INSECTE,
            essencesCibles = listOf("QUPE", "QUPES", "QURU", "BIPE", "POHY"),
            niveauRisque = NiveauRisque.ELEVE,
            typesDegat = listOf(TypeDegat.DEFOLIATION),
            symptomes = "Défoliation totale des chênes et feuillus divers. Masses d'œufs beige-chamois sur troncs et pierres. Chenilles velues de grande taille (mai–juillet).",
            facteursFavorisants = listOf("Printemps chauds et secs", "Forêts ouvertes et lisières"),
            periodeRisque = "Mai–Juillet",
            methodesLutte = listOf(
                "Bacillus thuringiensis (stade L1-L2)",
                "Virus de la polyédrose (biologique)",
                "Pièges à phéromones (suivi mâles)"
            ),
            notesDSF = "Ravageur à surveiller en progression dans le quart NE de la France."
        ),

        // ══ MALADIES CRYPTOGAMIQUES ══════════════════════════════════════════

        Pathogene(
            id = "CHALAROSE", nomCommun = "Chalarose du frêne",
            nomSci = "Hymenoscyphus fraxineus",
            typeAgent = TypeAgent.CHAMPIGNON,
            essencesCibles = listOf("FREX"),
            niveauRisque = NiveauRisque.CATASTROPHIQUE,
            typesDegat = listOf(TypeDegat.DESSECHEMENT_CIMES, TypeDegat.CHANCRE),
            symptomes = "Nécrose des feuilles et tiges depuis les cimes. Chancres sur rameaux et tronc (taches brun-orangé). Écorce en nid d'abeilles sous la nécrose. Mortalité progressive en quelques années.",
            facteursFavorisants = listOf("Spores aérodirigées (contamination aérienne totale)", "Tous les frênes européens sont sensibles"),
            periodeRisque = "Juillet–Septembre (sporulation sur litière)",
            methodesLutte = listOf(
                "Aucun traitement efficace à l'échelle forestière",
                "Ne plus planter en monoculture",
                "Conserver les individus résistants (< 5% population) pour sélection",
                "Mélanger avec aulne, charme, chêne en remplacement"
            ),
            estOrganismeReglemente = true,
            notesDSF = "MALADIE PRIORITAIRE — ~90% de mortalité sur frênes adultes dans les zones atteintes. Présente dans toute la France. Abandon des reboisements en frêne recommandé."
        ),

        Pathogene(
            id = "PHYTALNUS", nomCommun = "Maladie de l'encre des aulnes",
            nomSci = "Phytophthora alni",
            typeAgent = TypeAgent.OOMYCETE,
            essencesCibles = listOf("ALGL"),
            niveauRisque = NiveauRisque.TRES_ELEVE,
            typesDegat = listOf(TypeDegat.POURRIDIES_RACINES, TypeDegat.CHANCRE),
            symptomes = "Écorce noirâtre et humide à la base du tronc (couleur encre). Feuilles petites et chlorotiques. Rameaux desséchés. Mortalité par sections de berge entière.",
            facteursFavorisants = listOf("Crues transportant les zoospores", "Blessures racinaires", "Réseaux hydrographiques connectés"),
            periodeRisque = "Toute l'année (sporanges persistants)",
            methodesLutte = listOf(
                "Éviter les travaux perturbant les racines",
                "Ne pas planter en zones déjà infectées",
                "Signalement obligatoire au DSF"
            ),
            estOrganismeReglemente = true,
            notesDSF = "Organisme réglementé. Progression rapide le long des cours d'eau. Pertes massives de ripisylves en France."
        ),

        Pathogene(
            id = "OIDIUMCHENE", nomCommun = "Oïdium du chêne",
            nomSci = "Erysiphe alphitoides",
            typeAgent = TypeAgent.CHAMPIGNON,
            essencesCibles = listOf("QUPE", "QUPES", "QUPU", "QUIL", "QURU"),
            niveauRisque = NiveauRisque.MODERE,
            typesDegat = listOf(TypeDegat.FEUILLES_TACHES, TypeDegat.DEFOLIATION),
            symptomes = "Feutrage blanc farineux sur jeunes feuilles (épiderme supérieur). Feuilles déformées, jaunissantes. Affaiblit fortement les plants de pépinière et les jeunes semis.",
            facteursFavorisants = listOf("Temps chaud et humide", "Ombrage", "Végétation dense (mauvaise aération)"),
            periodeRisque = "Mai–Septembre",
            methodesLutte = listOf(
                "Soufre (traitement préventif en pépinière)",
                "Aération des peuplements (éclaircie)",
                "Éviter l'ombrage trop fort en régénération"
            ),
            notesDSF = "Impact surtout sur régénération naturelle et jeunes plants. Les adultes résistent bien. Espèce introduite d'Amérique du Nord (détectée en Europe en 1907)."
        ),

        Pathogene(
            id = "NECTRIAHETRE", nomCommun = "Chancre du hêtre — Nectria",
            nomSci = "Neonectria ditissima",
            typeAgent = TypeAgent.CHAMPIGNON,
            essencesCibles = listOf("FASY", "FREX", "ACPS", "PRCE"),
            niveauRisque = NiveauRisque.MODERE,
            typesDegat = listOf(TypeDegat.CHANCRE),
            symptomes = "Chancres nécrotiques sur tronc et branches, entourés de bourrelet de cicatrisation ('chancre ouvert'). Écoulement de gomme. Masse de fructifications rouges en automne.",
            facteursFavorisants = listOf("Blessures mécaniques (récolte, gelée)", "Affaiblissement par cochenille (Cryptococcus)"),
            periodeRisque = "Toute l'année (sporulation printemps-automne)",
            methodesLutte = listOf(
                "Éviter les blessures lors des exploitations",
                "Récolte rapide des arbres chancres",
                "Lutte contre la cochenille (prévention)"
            )
        ),

        Pathogene(
            id = "ARMILLAIRE", nomCommun = "Armillaire — pourridié agaric",
            nomSci = "Armillaria mellea / ostoyae",
            typeAgent = TypeAgent.CHAMPIGNON,
            essencesCibles = listOf("PIAB", "ABBA", "PSME", "QUPE", "FASY", "PISY"),
            niveauRisque = NiveauRisque.ELEVE,
            typesDegat = listOf(TypeDegat.POURRIDIES_RACINES),
            symptomes = "Pourriture blanche des racines et collet. Mycelium blanc sous l'écorce. Rhizomorphes noirs en lacets sous l'écorce (cordons noirs = 'cordes de sorcière'). Champignons dorés en touffes à l'automne.",
            facteursFavorisants = listOf("Vieux souches en forêt (réservoir)", "Arbres affaiblis", "Sol trop humide"),
            periodeRisque = "Toute l'année (activité maximale automne-hiver)",
            methodesLutte = listOf(
                "Déchaumage des vieilles souches (prévention majeure)",
                "Désinfection des souches (urea)",
                "Éviter les cultures monospécifiques sur sols infectés",
                "Trichoderma (lutte biologique)"
            ),
            notesDSF = "Très présent en forêts résineuses de montagne. A. ostoyae = forme la plus agressive."
        ),

        Pathogene(
            id = "HETEROBASID", nomCommun = "Fomes — Pourridié des résineux",
            nomSci = "Heterobasidion annosum",
            typeAgent = TypeAgent.CHAMPIGNON,
            essencesCibles = listOf("PIAB", "PISY", "ABBA", "PSME", "PIPE"),
            niveauRisque = NiveauRisque.TRES_ELEVE,
            typesDegat = listOf(TypeDegat.POURRIDIES_RACINES, TypeDegat.BOIS_INTERNE),
            symptomes = "Pourriture rouge/blanche du cœur racinaire. Arbres penchés puis tombés ('étoiles de chablis'). Fructifications crustacées blanc-brun sous les souches. Difficile à détecter avant stade avancé.",
            facteursFavorisants = listOf("Souches fraîches non traitées (porte d'entrée principale)", "pH > 6 (calcaire)", "Sols compactés"),
            periodeRisque = "Printemps-Été (sporulation sur souches fraîches)",
            methodesLutte = listOf(
                "Traitement immédiat des souches fraîches (Rotstop = Phlebiopsis gigantea, ou urea)",
                "Application dans les 24h après coupe",
                "Déchaumage des souches sur sites très infestés"
            ),
            notesDSF = "Principale maladie des résineux en France. Traitement des souches = mesure obligatoire dans les forêts touchées."
        ),

        Pathogene(
            id = "CHANCRECHATA", nomCommun = "Chancre du châtaignier",
            nomSci = "Cryphonectria parasitica",
            typeAgent = TypeAgent.CHAMPIGNON,
            essencesCibles = listOf("CASA"),
            niveauRisque = NiveauRisque.CATASTROPHIQUE,
            typesDegat = listOf(TypeDegat.CHANCRE, TypeDegat.DESSECHEMENT_CIMES),
            symptomes = "Chancres orangés sur tronc et branches. Dessèchement brutal de branches entières (balais de sorcière). Fructifications orangées en coupelles. Écorce nécrosée entourant le chancre.",
            facteursFavorisants = listOf("Blessures (insectes, gel, taille)", "Souches de virus atténuant présentes ou absentes"),
            periodeRisque = "Toute l'année",
            methodesLutte = listOf(
                "Hypovirulence (vaccination au virus CHV1 — traitement biologique officiel)",
                "Taille et destruction des branches chancreuses",
                "Proscrire les déplacements de bois non traité"
            ),
            estOrganismeReglemente = true,
            notesDSF = "A détruit quasi-totalité des châtaigniers américains. En Europe, hypovirulence naturelle partielle. Présent partout en France."
        ),

        Pathogene(
            id = "ENCRECHATA", nomCommun = "Encre du châtaignier",
            nomSci = "Phytophthora cinnamomi / cambivora",
            typeAgent = TypeAgent.OOMYCETE,
            essencesCibles = listOf("CASA", "QUIL"),
            niveauRisque = NiveauRisque.TRES_ELEVE,
            typesDegat = listOf(TypeDegat.POURRIDIES_RACINES),
            symptomes = "Écoulement noir huileux à la base du tronc ('encre'). Pourriture racinaire. Dépérissement progressif. Mortalité sur souche sans possibilité de rejets viables.",
            facteursFavorisants = listOf("Sol compacté ou hydromorphe", "Plantations denses", "Déséquilibre sol"),
            periodeRisque = "Printemps-Automne (zoospores en présence d'eau libre)",
            methodesLutte = listOf(
                "Phosphonates (traitement préventif et curatif partiel)",
                "Drainage des sols",
                "Chaulage localisé",
                "Porte-greffes résistants (Castanea crenata)"
            )
        ),

        // ══ MALADIES RÉGLEMENTÉES / ÉMERGENTES ══════════════════════════════

        Pathogene(
            id = "MORTSUB", nomCommun = "Mort subite du chêne",
            nomSci = "Phytophthora ramorum",
            typeAgent = TypeAgent.OOMYCETE,
            essencesCibles = listOf("QURU", "QUIL", "CASA"),
            niveauRisque = NiveauRisque.TRES_ELEVE,
            typesDegat = listOf(TypeDegat.CHANCRE, TypeDegat.POURRIDIES_RACINES),
            symptomes = "Saignements brun-noir sur écorce du tronc. Dessèchement brutal des feuilles ('mort subite'). Nécrose cambiale extensive.",
            facteursFavorisants = listOf("Temps humide et chaud", "Plantes ornementales (Rhododendron, Viburnum) comme hôtes relais"),
            periodeRisque = "Printemps-Automne",
            methodesLutte = listOf(
                "Signalement obligatoire à la DGAL",
                "Abattage et destruction des arbres atteints",
                "Foyer de lutte obligatoire (arrêté préfectoral)"
            ),
            estOrganismeReglemente = true,
            notesDSF = "Organisme réglementé — déclaration obligatoire. Foyers limités en France pour l'instant."
        ),

        Pathogene(
            id = "DEPERJAP", nomCommun = "Dépérissement du frêne — xylella",
            nomSci = "Xylella fastidiosa",
            typeAgent = TypeAgent.BACTERIE,
            essencesCibles = listOf("QUPE", "FASY"),
            niveauRisque = NiveauRisque.TRES_ELEVE,
            typesDegat = listOf(TypeDegat.DESSECHEMENT_CIMES),
            symptomes = "Dessèchement des rameaux de la périphérie vers l'intérieur du houppier. Brûlure des feuilles. Arbre mort en 1–3 ans.",
            facteursFavorisants = listOf("Vecteurs cicadelles (Philaenus spumarius)", "Températures élevées"),
            periodeRisque = "Toute l'année (propagation par insectes vecteurs)",
            methodesLutte = listOf(
                "Organisme réglementé — déclaration immédiate obligatoire",
                "Abattage et destruction sous supervision officielle",
                "Zone tampon 100 m"
            ),
            estOrganismeReglemente = true,
            notesDSF = "Présent en Corse (sous-espèce multiplex). Surveillance renforcée en continent. Peut affecter 600+ espèces de plantes."
        ),

        Pathogene(
            id = "CYNIPS", nomCommun = "Cynips du châtaignier",
            nomSci = "Dryocosmus kuriphilus",
            typeAgent = TypeAgent.INSECTE,
            essencesCibles = listOf("CASA"),
            niveauRisque = NiveauRisque.ELEVE,
            typesDegat = listOf(TypeDegat.GALLES),
            symptomes = "Galles globuleuses vert-rose sur bourgeons et feuilles (mai–juin). Avortement des bourgeons. Chute de croissance significative (50% dans les cas graves). Aucune mortalité directe.",
            facteursFavorisants = listOf("Absence du parasitoïde Torymus sinensis"),
            periodeRisque = "Février–Juin (pontes dans bourgeons)",
            methodesLutte = listOf(
                "Lâchers de Torymus sinensis (parasitoïde spécifique — lutte biologique officielle)",
                "Résultats positifs 2–3 ans après lâchers",
                "Éviter les déplacements de plants de châtaignier"
            ),
            estOrganismeReglemente = true,
            notesDSF = "Programme national de lutte biologique en cours. Efficacité de Torymus confirmée dans plusieurs régions."
        ),

        // ══ MAMMIFÈRES / AUTRES ══════════════════════════════════════════════

        Pathogene(
            id = "GIBIER", nomCommun = "Abroutissement et frottures par grand gibier",
            nomSci = "Cervus elaphus / Capreolus capreolus / Sus scrofa",
            typeAgent = TypeAgent.MAMMIFERE,
            essencesCibles = listOf("ABBA", "PIAB", "FASY", "QUPE", "PSME", "ACPS", "FREX"),
            niveauRisque = NiveauRisque.ELEVE,
            typesDegat = listOf(TypeDegat.ECORCE_GALERIES, TypeDegat.DEFOLIATION),
            symptomes = "Abroutissement des jeunes pousses (cerf/chevreuil). Frottis d'écorce sur troncs de 1–15 cm de diamètre (cerf en rut). Boutis de sanglier (retournement sol = défense mycorhizes). Gainage des plants.",
            facteursFavorisants = listOf("Population cerf/chevreuil élevée", "Hiver rude (famine)", "Peuplements en régénération naturelle"),
            periodeRisque = "Abroutissement : Octobre–Avril / Frottis : Août–Septembre",
            methodesLutte = listOf(
                "Clôtures de protection (régénération et plantations)",
                "Gaines individuelles pour plants",
                "Régulation des populations (plan de chasse)",
                "Répulsifs olfactifs (efficacité limitée)"
            ),
            notesDSF = "Premier facteur de mise en échec des régénérations naturelles en France. Enjeu majeur pour l'adaptation des forêts au changement climatique."
        ),

        Pathogene(
            id = "HYLOBE", nomCommun = "Hylobe du pin",
            nomSci = "Hylobius abietis",
            typeAgent = TypeAgent.INSECTE,
            essencesCibles = listOf("PISY", "PIAB", "PSME", "ABBA", "ABGR", "LADA"),
            niveauRisque = NiveauRisque.ELEVE,
            typesDegat = listOf(TypeDegat.ECORCE_GALERIES),
            symptomes = "Annélation complète de l'écorce des jeunes plants à la base. Plants tués en quelques jours sur sol récemment boisé. Adultes (weevils) de 12–14 mm, museau courbé.",
            facteursFavorisants = listOf("Coupes récentes (adultes pondent sur souches fraîches)", "Plants de 1–3 ans"),
            periodeRisque = "Avril–Septembre",
            methodesLutte = listOf(
                "Traitement insecticide des plants avant plantation (perméthrine)",
                "Délai de replantation 2 ans après coupe (si possible)",
                "Pièges à phéromones pour suivi",
                "Nématodes (Steinernema) en conditions favorables"
            ),
            notesDSF = "Principal ravageur des reboisements résineux en France. Incontournable dans les coupes rases de résineux."
        )
    )
}
