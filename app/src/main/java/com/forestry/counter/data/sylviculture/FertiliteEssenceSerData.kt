package com.forestry.counter.data.sylviculture

import com.forestry.counter.data.local.entity.FertiliteEssenceSerEntity

/**
 * Données embarquées de fertilité par essence × SylvoÉcoRégion.
 * Source : Guides sylvicoles CNPF (20 essences principales), IFN SER nomenclature 86 SER.
 *
 * Structure des classes de station (1=Médiocre, 2=Moyen, 3=Bon, 4=Très bon, 5=Excellent)
 * hoRef100Ans = hauteur dominante à 100 ans (indice de fertilité principal)
 * accroissementRefM3HaAn = production biologique potentielle en m³/ha/an
 */
object FertiliteEssenceSerData {

    private const val SOURCE_CNPF = "Guide sylvicole CNPF"
    private const val SOURCE_ONF = "Guide technique ONF"
    private const val SOURCE_IFN = "Données IFN/CNPF estimées"

    fun buildAll(): List<FertiliteEssenceSerEntity> {
        val entries = mutableListOf<FertiliteEssenceSerEntity>()
        entries += buildCheneRouge()
        entries += buildHetre()
        entries += buildDouglas()
        entries += buildSapinPectine()
        entries += buildEpiceaCommun()
        entries += buildPinSylvestre()
        entries += buildPinMaritime()
        entries += buildPinNoir()
        entries += buildMeleze()
        entries += buildCedreAtlas()
        entries += buildChataignier()
        entries += buildRobinier()
        entries += buildChenesPedonculeSessile()
        entries += buildCheneVert()
        entries += buildNoyers()
        entries += buildPeuplier()
        entries += buildFeuillusPrecieux()
        entries += buildPinAlepDaricioPinaster()
        entries += buildEpiceaSitka()
        return entries
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CHÊNE ROUGE D'AMÉRIQUE (QURU) — Guide CNPF Quercus rubra
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildCheneRouge(): List<FertiliteEssenceSerEntity> = listOf(
        fer("QURU", "B11", "Ardenne primaire",          1, 20.0, 2.0, 4.0),
        fer("QURU", "B11", "Ardenne primaire",          2, 25.0, 2.8, 6.0),
        fer("QURU", "B11", "Ardenne primaire",          3, 30.0, 3.5, 8.0),
        fer("QURU", "B12", "Ardenne secondaire",        1, 18.0, 1.8, 3.5),
        fer("QURU", "B12", "Ardenne secondaire",        2, 23.0, 2.5, 5.5),
        fer("QURU", "B12", "Ardenne secondaire",        3, 28.0, 3.2, 7.5),
        fer("QURU", "C10", "Lorraine",                  1, 20.0, 2.0, 4.5),
        fer("QURU", "C10", "Lorraine",                  2, 25.0, 2.8, 6.5),
        fer("QURU", "C10", "Lorraine",                  3, 32.0, 3.8, 9.0),
        fer("QURU", "D11", "Boulonnais-Thiérache",      2, 26.0, 3.0, 7.0),
        fer("QURU", "D11", "Boulonnais-Thiérache",      3, 31.0, 3.8, 9.5),
        fer("QURU", "E10", "Normandie",                 2, 27.0, 3.2, 7.5),
        fer("QURU", "E10", "Normandie",                 3, 33.0, 4.0, 10.0),
        fer("QURU", "G10", "Massif central nord",       1, 17.0, 1.6, 3.0),
        fer("QURU", "G10", "Massif central nord",       2, 22.0, 2.4, 5.0),
        fer("QURU", "G10", "Massif central nord",       3, 28.0, 3.2, 7.0),
        fer("QURU", "H10", "Massif central sud",        1, 15.0, 1.5, 2.5),
        fer("QURU", "H10", "Massif central sud",        2, 20.0, 2.2, 4.5),
        fer("QURU", "I10", "Cévennes-Basses Cévennes",  1, 12.0, 1.2, 2.0),
        fer("QURU", "I10", "Cévennes-Basses Cévennes",  2, 18.0, 2.0, 3.5)
    )

    // ──────────────────────────────────────────────────────────────────────────
    // HÊTRE (FASY) — Guide CNPF Fagus sylvatica
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildHetre(): List<FertiliteEssenceSerEntity> = listOf(
        fer("FASY", "B11", "Ardenne primaire",          1, 22.0, 2.2, 4.0),
        fer("FASY", "B11", "Ardenne primaire",          2, 28.0, 3.0, 6.0),
        fer("FASY", "B11", "Ardenne primaire",          3, 35.0, 4.0, 8.5),
        fer("FASY", "B11", "Ardenne primaire",          4, 40.0, 5.0, 11.0),
        fer("FASY", "C10", "Lorraine",                  1, 20.0, 2.0, 3.5),
        fer("FASY", "C10", "Lorraine",                  2, 26.0, 2.8, 5.5),
        fer("FASY", "C10", "Lorraine",                  3, 32.0, 3.8, 8.0),
        fer("FASY", "C10", "Lorraine",                  4, 38.0, 4.8, 10.5),
        fer("FASY", "D11", "Boulonnais-Thiérache",      2, 27.0, 3.0, 6.0),
        fer("FASY", "D11", "Boulonnais-Thiérache",      3, 33.0, 4.0, 8.5),
        fer("FASY", "E10", "Normandie",                 2, 28.0, 3.2, 7.0),
        fer("FASY", "E10", "Normandie",                 3, 34.0, 4.2, 9.5),
        fer("FASY", "E10", "Normandie",                 4, 40.0, 5.5, 12.0),
        fer("FASY", "F10", "Île-de-France",             1, 18.0, 1.8, 3.0),
        fer("FASY", "F10", "Île-de-France",             2, 24.0, 2.6, 5.0),
        fer("FASY", "F10", "Île-de-France",             3, 30.0, 3.6, 7.5),
        fer("FASY", "G10", "Massif central nord",       1, 20.0, 2.0, 3.8),
        fer("FASY", "G10", "Massif central nord",       2, 27.0, 3.0, 6.2),
        fer("FASY", "G10", "Massif central nord",       3, 34.0, 4.0, 9.0),
        fer("FASY", "J10", "Alpes nord internes",       2, 26.0, 2.8, 5.5),
        fer("FASY", "J10", "Alpes nord internes",       3, 33.0, 3.8, 8.0),
        fer("FASY", "K10", "Alpes nord externes",       2, 28.0, 3.2, 6.5),
        fer("FASY", "K10", "Alpes nord externes",       3, 35.0, 4.5, 9.5),
        fer("FASY", "L10", "Alpes sud",                 1, 17.0, 1.6, 2.8),
        fer("FASY", "L10", "Alpes sud",                 2, 24.0, 2.8, 5.5),
        fer("FASY", "M10", "Pyrénées",                  2, 26.0, 3.0, 6.0),
        fer("FASY", "M10", "Pyrénées",                  3, 33.0, 4.0, 8.5),
        fer("FASY", "M10", "Pyrénées",                  4, 40.0, 5.2, 11.5)
    )

    // ──────────────────────────────────────────────────────────────────────────
    // DOUGLAS (PSME) — Guide CNPF Pseudotsuga menziesii
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildDouglas(): List<FertiliteEssenceSerEntity> = listOf(
        fer("PSME", "B11", "Ardenne primaire",          1, 25.0, 3.0, 8.0),
        fer("PSME", "B11", "Ardenne primaire",          2, 32.0, 4.0, 12.0),
        fer("PSME", "B11", "Ardenne primaire",          3, 38.0, 5.0, 16.0),
        fer("PSME", "B12", "Ardenne secondaire",        1, 22.0, 2.5, 7.0),
        fer("PSME", "B12", "Ardenne secondaire",        2, 29.0, 3.5, 11.0),
        fer("PSME", "B12", "Ardenne secondaire",        3, 36.0, 4.8, 15.0),
        fer("PSME", "C10", "Lorraine",                  1, 24.0, 2.8, 8.0),
        fer("PSME", "C10", "Lorraine",                  2, 31.0, 4.0, 12.5),
        fer("PSME", "C10", "Lorraine",                  3, 38.0, 5.2, 17.0),
        fer("PSME", "G10", "Massif central nord",       1, 22.0, 2.5, 7.5),
        fer("PSME", "G10", "Massif central nord",       2, 30.0, 4.0, 12.0),
        fer("PSME", "G10", "Massif central nord",       3, 37.0, 5.2, 16.5),
        fer("PSME", "G10", "Massif central nord",       4, 42.0, 6.5, 20.0),
        fer("PSME", "H10", "Massif central sud",        1, 20.0, 2.2, 6.0),
        fer("PSME", "H10", "Massif central sud",        2, 27.0, 3.5, 10.5),
        fer("PSME", "H10", "Massif central sud",        3, 34.0, 4.8, 14.5),
        fer("PSME", "J10", "Alpes nord internes",       2, 28.0, 3.5, 11.0),
        fer("PSME", "J10", "Alpes nord internes",       3, 35.0, 5.0, 15.0),
        fer("PSME", "K10", "Alpes nord externes",       2, 30.0, 4.0, 13.0),
        fer("PSME", "K10", "Alpes nord externes",       3, 38.0, 5.5, 17.0),
        fer("PSME", "K10", "Alpes nord externes",       4, 44.0, 7.0, 21.0),
        fer("PSME", "M10", "Pyrénées",                  2, 29.0, 3.8, 12.0),
        fer("PSME", "M10", "Pyrénées",                  3, 36.0, 5.2, 16.0),
        fer("PSME", "E10", "Normandie",                 2, 30.0, 4.0, 13.0),
        fer("PSME", "E10", "Normandie",                 3, 37.0, 5.5, 17.5),
        fer("PSME", "E10", "Normandie",                 4, 43.0, 7.0, 22.0)
    )

    // ──────────────────────────────────────────────────────────────────────────
    // SAPIN PECTINÉ (ABBA) — Guide CNPF Abies alba
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildSapinPectine(): List<FertiliteEssenceSerEntity> = listOf(
        fer("ABBA", "B11", "Ardenne primaire",          2, 26.0, 3.0, 7.0),
        fer("ABBA", "B11", "Ardenne primaire",          3, 33.0, 4.0, 10.0),
        fer("ABBA", "C10", "Lorraine",                  2, 24.0, 2.8, 6.5),
        fer("ABBA", "C10", "Lorraine",                  3, 31.0, 3.8, 9.5),
        fer("ABBA", "G10", "Massif central nord",       1, 18.0, 2.0, 4.5),
        fer("ABBA", "G10", "Massif central nord",       2, 25.0, 3.0, 7.5),
        fer("ABBA", "G10", "Massif central nord",       3, 32.0, 4.0, 10.5),
        fer("ABBA", "G10", "Massif central nord",       4, 38.0, 5.2, 13.5),
        fer("ABBA", "H10", "Massif central sud",        1, 16.0, 1.8, 4.0),
        fer("ABBA", "H10", "Massif central sud",        2, 23.0, 2.8, 7.0),
        fer("ABBA", "H10", "Massif central sud",        3, 30.0, 3.8, 10.0),
        fer("ABBA", "J10", "Alpes nord internes",       2, 26.0, 3.0, 7.5),
        fer("ABBA", "J10", "Alpes nord internes",       3, 33.0, 4.2, 11.0),
        fer("ABBA", "J10", "Alpes nord internes",       4, 40.0, 5.5, 14.0),
        fer("ABBA", "K10", "Alpes nord externes",       2, 28.0, 3.5, 9.0),
        fer("ABBA", "K10", "Alpes nord externes",       3, 35.0, 4.8, 12.0),
        fer("ABBA", "K10", "Alpes nord externes",       4, 42.0, 6.0, 15.5),
        fer("ABBA", "M10", "Pyrénées",                  2, 27.0, 3.2, 8.0),
        fer("ABBA", "M10", "Pyrénées",                  3, 34.0, 4.5, 11.5),
        fer("ABBA", "M10", "Pyrénées",                  4, 41.0, 5.8, 14.5)
    )

    // ──────────────────────────────────────────────────────────────────────────
    // ÉPICÉA COMMUN (PIAB) — Guide CNPF Picea abies
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildEpiceaCommun(): List<FertiliteEssenceSerEntity> = listOf(
        fer("PIAB", "B11", "Ardenne primaire",          1, 22.0, 2.5, 6.0),
        fer("PIAB", "B11", "Ardenne primaire",          2, 28.0, 3.5, 9.0),
        fer("PIAB", "B11", "Ardenne primaire",          3, 34.0, 4.8, 13.0),
        fer("PIAB", "B11", "Ardenne primaire",          4, 39.0, 6.0, 17.0),
        fer("PIAB", "B12", "Ardenne secondaire",        2, 26.0, 3.2, 8.0),
        fer("PIAB", "B12", "Ardenne secondaire",        3, 32.0, 4.5, 12.0),
        fer("PIAB", "C10", "Lorraine",                  1, 20.0, 2.2, 5.5),
        fer("PIAB", "C10", "Lorraine",                  2, 26.0, 3.2, 8.5),
        fer("PIAB", "C10", "Lorraine",                  3, 32.0, 4.5, 12.5),
        fer("PIAB", "G10", "Massif central nord",       1, 18.0, 2.0, 5.0),
        fer("PIAB", "G10", "Massif central nord",       2, 24.0, 3.0, 8.0),
        fer("PIAB", "G10", "Massif central nord",       3, 30.0, 4.2, 11.5),
        fer("PIAB", "J10", "Alpes nord internes",       2, 24.0, 3.0, 8.0),
        fer("PIAB", "J10", "Alpes nord internes",       3, 30.0, 4.2, 12.0),
        fer("PIAB", "J10", "Alpes nord internes",       4, 36.0, 5.5, 16.0),
        fer("PIAB", "K10", "Alpes nord externes",       2, 26.0, 3.5, 9.0),
        fer("PIAB", "K10", "Alpes nord externes",       3, 32.0, 4.8, 13.0),
        fer("PIAB", "M10", "Pyrénées",                  2, 24.0, 3.0, 8.0),
        fer("PIAB", "M10", "Pyrénées",                  3, 30.0, 4.2, 11.5)
    )

    // ──────────────────────────────────────────────────────────────────────────
    // PIN SYLVESTRE (PISY) — Guide CNPF Pinus sylvestris
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildPinSylvestre(): List<FertiliteEssenceSerEntity> = listOf(
        fer("PISY", "B11", "Ardenne primaire",          1, 18.0, 2.0, 3.5),
        fer("PISY", "B11", "Ardenne primaire",          2, 23.0, 2.8, 5.5),
        fer("PISY", "B11", "Ardenne primaire",          3, 28.0, 3.8, 7.5),
        fer("PISY", "C10", "Lorraine",                  1, 17.0, 1.8, 3.0),
        fer("PISY", "C10", "Lorraine",                  2, 22.0, 2.6, 5.0),
        fer("PISY", "C10", "Lorraine",                  3, 27.0, 3.5, 7.0),
        fer("PISY", "F10", "Île-de-France",             1, 15.0, 1.5, 2.5),
        fer("PISY", "F10", "Île-de-France",             2, 20.0, 2.2, 4.5),
        fer("PISY", "F10", "Île-de-France",             3, 25.0, 3.2, 6.5),
        fer("PISY", "G10", "Massif central nord",       1, 16.0, 1.6, 3.0),
        fer("PISY", "G10", "Massif central nord",       2, 21.0, 2.4, 5.0),
        fer("PISY", "G10", "Massif central nord",       3, 26.0, 3.4, 7.0),
        fer("PISY", "J10", "Alpes nord internes",       1, 17.0, 1.8, 3.0),
        fer("PISY", "J10", "Alpes nord internes",       2, 22.0, 2.6, 5.0),
        fer("PISY", "J10", "Alpes nord internes",       3, 27.0, 3.5, 7.0),
        fer("PISY", "L10", "Alpes sud",                 1, 15.0, 1.5, 2.5),
        fer("PISY", "L10", "Alpes sud",                 2, 20.0, 2.2, 4.0),
        fer("PISY", "M10", "Pyrénées",                  1, 17.0, 1.8, 3.0),
        fer("PISY", "M10", "Pyrénées",                  2, 22.0, 2.6, 5.0),
        fer("PISY", "M10", "Pyrénées",                  3, 27.0, 3.5, 7.0)
    )

    // ──────────────────────────────────────────────────────────────────────────
    // PIN MARITIME (PIPE) — Guide CNPF Pinus pinaster
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildPinMaritime(): List<FertiliteEssenceSerEntity> = listOf(
        fer("PIPE", "N10", "Landes de Gascogne",        1, 18.0, 2.0, 4.5, SOURCE_CNPF),
        fer("PIPE", "N10", "Landes de Gascogne",        2, 24.0, 3.0, 7.5, SOURCE_CNPF),
        fer("PIPE", "N10", "Landes de Gascogne",        3, 30.0, 4.2, 11.0, SOURCE_CNPF),
        fer("PIPE", "N10", "Landes de Gascogne",        4, 35.0, 5.5, 14.5, SOURCE_CNPF),
        fer("PIPE", "O10", "Périgord-Quercy",           1, 16.0, 1.8, 3.5),
        fer("PIPE", "O10", "Périgord-Quercy",           2, 21.0, 2.6, 5.5),
        fer("PIPE", "O10", "Périgord-Quercy",           3, 26.0, 3.5, 8.0),
        fer("PIPE", "H10", "Massif central sud",        1, 14.0, 1.5, 3.0),
        fer("PIPE", "H10", "Massif central sud",        2, 19.0, 2.2, 5.0),
        fer("PIPE", "H10", "Massif central sud",        3, 24.0, 3.2, 7.5),
        fer("PIPE", "P10", "Provence cristalline",      1, 15.0, 1.5, 2.5),
        fer("PIPE", "P10", "Provence cristalline",      2, 20.0, 2.2, 4.0),
        fer("PIPE", "M10", "Pyrénées",                  1, 15.0, 1.5, 3.0),
        fer("PIPE", "M10", "Pyrénées",                  2, 21.0, 2.5, 5.5),
        fer("PIPE", "M10", "Pyrénées",                  3, 27.0, 3.5, 8.0)
    )

    // ──────────────────────────────────────────────────────────────────────────
    // PIN NOIR D'AUTRICHE / LARICIO (PINI) — Guide CNPF
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildPinNoir(): List<FertiliteEssenceSerEntity> = listOf(
        fer("PINI", "G10", "Massif central nord",       1, 18.0, 2.0, 4.0),
        fer("PINI", "G10", "Massif central nord",       2, 24.0, 3.0, 7.0),
        fer("PINI", "G10", "Massif central nord",       3, 30.0, 4.0, 10.0),
        fer("PINI", "H10", "Massif central sud",        1, 17.0, 1.8, 3.5),
        fer("PINI", "H10", "Massif central sud",        2, 22.0, 2.8, 6.5),
        fer("PINI", "H10", "Massif central sud",        3, 28.0, 3.8, 9.5),
        fer("PINI", "L10", "Alpes sud",                 1, 16.0, 1.6, 3.0),
        fer("PINI", "L10", "Alpes sud",                 2, 21.0, 2.4, 5.5),
        fer("PINI", "L10", "Alpes sud",                 3, 26.0, 3.4, 8.0),
        fer("PINI", "P10", "Provence cristalline",      1, 15.0, 1.5, 2.5),
        fer("PINI", "P10", "Provence cristalline",      2, 20.0, 2.2, 5.0),
        fer("PINI", "I10", "Cévennes",                  1, 14.0, 1.4, 2.5),
        fer("PINI", "I10", "Cévennes",                  2, 19.0, 2.0, 4.5)
    )

    // ──────────────────────────────────────────────────────────────────────────
    // MÉLÈZE D'EUROPE (LADA) — Guide CNPF Larix decidua
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildMeleze(): List<FertiliteEssenceSerEntity> = listOf(
        fer("LADA", "J10", "Alpes nord internes",       1, 24.0, 2.5, 6.0),
        fer("LADA", "J10", "Alpes nord internes",       2, 30.0, 3.5, 9.5),
        fer("LADA", "J10", "Alpes nord internes",       3, 36.0, 4.8, 13.0),
        fer("LADA", "J10", "Alpes nord internes",       4, 42.0, 6.0, 16.0),
        fer("LADA", "K10", "Alpes nord externes",       1, 22.0, 2.2, 5.5),
        fer("LADA", "K10", "Alpes nord externes",       2, 28.0, 3.2, 9.0),
        fer("LADA", "K10", "Alpes nord externes",       3, 34.0, 4.5, 12.5),
        fer("LADA", "L10", "Alpes sud",                 1, 20.0, 2.0, 5.0),
        fer("LADA", "L10", "Alpes sud",                 2, 26.0, 3.0, 8.0),
        fer("LADA", "L10", "Alpes sud",                 3, 32.0, 4.2, 11.5),
        fer("LADA", "M10", "Pyrénées",                  1, 20.0, 2.0, 5.0),
        fer("LADA", "M10", "Pyrénées",                  2, 26.0, 3.0, 8.5),
        fer("LADA", "M10", "Pyrénées",                  3, 32.0, 4.2, 12.0),
        fer("LADA", "G10", "Massif central nord",       1, 18.0, 1.8, 4.5),
        fer("LADA", "G10", "Massif central nord",       2, 24.0, 2.8, 7.5),
        fer("LADA", "G10", "Massif central nord",       3, 30.0, 4.0, 10.5)
    )

    // ──────────────────────────────────────────────────────────────────────────
    // CÈDRE DE L'ATLAS (CEAT) — Guide CNPF Cedrus atlantica
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildCedreAtlas(): List<FertiliteEssenceSerEntity> = listOf(
        fer("CEAT", "G10", "Massif central nord",       1, 16.0, 1.5, 3.0),
        fer("CEAT", "G10", "Massif central nord",       2, 22.0, 2.5, 5.5),
        fer("CEAT", "G10", "Massif central nord",       3, 28.0, 3.5, 8.0),
        fer("CEAT", "H10", "Massif central sud",        1, 15.0, 1.4, 2.5),
        fer("CEAT", "H10", "Massif central sud",        2, 21.0, 2.4, 5.0),
        fer("CEAT", "H10", "Massif central sud",        3, 27.0, 3.4, 7.5),
        fer("CEAT", "I10", "Cévennes",                  1, 14.0, 1.3, 2.0),
        fer("CEAT", "I10", "Cévennes",                  2, 20.0, 2.2, 4.5),
        fer("CEAT", "I10", "Cévennes",                  3, 26.0, 3.2, 7.0),
        fer("CEAT", "L10", "Alpes sud",                 1, 14.0, 1.3, 2.0),
        fer("CEAT", "L10", "Alpes sud",                 2, 20.0, 2.2, 4.5),
        fer("CEAT", "L10", "Alpes sud",                 3, 26.0, 3.2, 7.0),
        fer("CEAT", "P10", "Provence",                  1, 15.0, 1.4, 2.5),
        fer("CEAT", "P10", "Provence",                  2, 21.0, 2.5, 5.0),
        fer("CEAT", "P10", "Provence",                  3, 27.0, 3.5, 7.5)
    )

    // ──────────────────────────────────────────────────────────────────────────
    // CHÂTAIGNIER (CASA) — Guide CNPF Castanea sativa
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildChataignier(): List<FertiliteEssenceSerEntity> = listOf(
        fer("CASA", "E10", "Normandie",                 2, 22.0, 2.5, 5.0),
        fer("CASA", "E10", "Normandie",                 3, 28.0, 3.5, 7.5),
        fer("CASA", "G10", "Massif central nord",       1, 18.0, 2.0, 4.0),
        fer("CASA", "G10", "Massif central nord",       2, 24.0, 3.0, 6.5),
        fer("CASA", "G10", "Massif central nord",       3, 30.0, 4.0, 9.0),
        fer("CASA", "H10", "Massif central sud",        1, 16.0, 1.8, 3.5),
        fer("CASA", "H10", "Massif central sud",        2, 22.0, 2.8, 6.0),
        fer("CASA", "H10", "Massif central sud",        3, 28.0, 3.8, 8.5),
        fer("CASA", "I10", "Cévennes",                  1, 15.0, 1.6, 3.0),
        fer("CASA", "I10", "Cévennes",                  2, 21.0, 2.6, 5.5),
        fer("CASA", "I10", "Cévennes",                  3, 27.0, 3.6, 8.0),
        fer("CASA", "P10", "Provence cristalline",      1, 14.0, 1.5, 2.5),
        fer("CASA", "P10", "Provence cristalline",      2, 19.0, 2.2, 4.5),
        fer("CASA", "M10", "Pyrénées",                  2, 22.0, 2.6, 5.5),
        fer("CASA", "M10", "Pyrénées",                  3, 28.0, 3.6, 8.0)
    )

    // ──────────────────────────────────────────────────────────────────────────
    // ROBINIER (ROPC) — Guide CNPF Robinia pseudoacacia
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildRobinier(): List<FertiliteEssenceSerEntity> = listOf(
        fer("ROPC", "F10", "Île-de-France",             1, 16.0, 2.5, 5.0),
        fer("ROPC", "F10", "Île-de-France",             2, 20.0, 3.5, 7.0),
        fer("ROPC", "F10", "Île-de-France",             3, 24.0, 4.5, 9.0),
        fer("ROPC", "C10", "Lorraine",                  1, 14.0, 2.2, 4.5),
        fer("ROPC", "C10", "Lorraine",                  2, 18.0, 3.0, 6.5),
        fer("ROPC", "O10", "Périgord-Quercy",           1, 15.0, 2.5, 5.0),
        fer("ROPC", "O10", "Périgord-Quercy",           2, 19.0, 3.5, 7.5),
        fer("ROPC", "O10", "Périgord-Quercy",           3, 23.0, 4.5, 9.5),
        fer("ROPC", "H10", "Massif central sud",        1, 14.0, 2.2, 4.0),
        fer("ROPC", "H10", "Massif central sud",        2, 18.0, 3.0, 6.5),
        fer("ROPC", "E10", "Normandie",                 2, 19.0, 3.2, 7.0),
        fer("ROPC", "E10", "Normandie",                 3, 23.0, 4.2, 9.0)
    )

    // ──────────────────────────────────────────────────────────────────────────
    // CHÊNES PÉDONCULÉ + SESSILE (QUPE/QUPES) — Guide CNPF
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildChenesPedonculeSessile(): List<FertiliteEssenceSerEntity> {
        val list = mutableListOf<FertiliteEssenceSerEntity>()
        for (code in listOf("QUPE", "QUPES")) {
            list += fer(code, "B11", "Ardenne primaire",     1, 18.0, 1.8, 2.8)
            list += fer(code, "B11", "Ardenne primaire",     2, 24.0, 2.6, 4.5)
            list += fer(code, "B11", "Ardenne primaire",     3, 30.0, 3.5, 6.5)
            list += fer(code, "C10", "Lorraine",             1, 17.0, 1.7, 2.5)
            list += fer(code, "C10", "Lorraine",             2, 23.0, 2.5, 4.2)
            list += fer(code, "C10", "Lorraine",             3, 29.0, 3.4, 6.2)
            list += fer(code, "D11", "Boulonnais-Thiérache", 2, 24.0, 2.6, 4.5)
            list += fer(code, "D11", "Boulonnais-Thiérache", 3, 30.0, 3.5, 6.5)
            list += fer(code, "E10", "Normandie",            2, 25.0, 2.8, 5.0)
            list += fer(code, "E10", "Normandie",            3, 31.0, 3.8, 7.0)
            list += fer(code, "F10", "Île-de-France",        1, 16.0, 1.6, 2.2)
            list += fer(code, "F10", "Île-de-France",        2, 22.0, 2.4, 4.0)
            list += fer(code, "F10", "Île-de-France",        3, 28.0, 3.3, 6.0)
            list += fer(code, "G10", "Massif central nord",  1, 16.0, 1.6, 2.2)
            list += fer(code, "G10", "Massif central nord",  2, 22.0, 2.4, 4.0)
            list += fer(code, "G10", "Massif central nord",  3, 28.0, 3.3, 6.0)
            list += fer(code, "O10", "Périgord-Quercy",      1, 17.0, 1.7, 2.5)
            list += fer(code, "O10", "Périgord-Quercy",      2, 23.0, 2.5, 4.5)
            list += fer(code, "O10", "Périgord-Quercy",      3, 29.0, 3.5, 6.5)
        }
        return list
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CHÊNE VERT (QUIL) — Guide CNPF Quercus ilex
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildCheneVert(): List<FertiliteEssenceSerEntity> = listOf(
        fer("QUIL", "H10", "Massif central sud",        1, 10.0, 0.8, 1.5),
        fer("QUIL", "H10", "Massif central sud",        2, 14.0, 1.2, 2.5),
        fer("QUIL", "I10", "Cévennes",                  1, 10.0, 0.8, 1.5),
        fer("QUIL", "I10", "Cévennes",                  2, 14.0, 1.2, 2.5),
        fer("QUIL", "P10", "Provence cristalline",      1, 9.0,  0.7, 1.2),
        fer("QUIL", "P10", "Provence cristalline",      2, 13.0, 1.0, 2.0),
        fer("QUIL", "Q10", "Provence calcaire",         1, 8.0,  0.6, 1.0),
        fer("QUIL", "Q10", "Provence calcaire",         2, 12.0, 0.9, 1.8),
        fer("QUIL", "R10", "Languedoc-Roussillon",      1, 9.0,  0.7, 1.2),
        fer("QUIL", "R10", "Languedoc-Roussillon",      2, 13.0, 1.0, 2.0)
    )

    // ──────────────────────────────────────────────────────────────────────────
    // NOYERS (JUGR) — Guide CNPF Juglans regia
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildNoyers(): List<FertiliteEssenceSerEntity> = listOf(
        fer("JUGR", "O10", "Périgord-Quercy",           1, 14.0, 1.5, 3.0),
        fer("JUGR", "O10", "Périgord-Quercy",           2, 18.0, 2.2, 5.0),
        fer("JUGR", "O10", "Périgord-Quercy",           3, 22.0, 3.0, 7.0),
        fer("JUGR", "F10", "Île-de-France",             1, 13.0, 1.4, 2.5),
        fer("JUGR", "F10", "Île-de-France",             2, 17.0, 2.0, 4.5),
        fer("JUGR", "C10", "Lorraine",                  1, 13.0, 1.4, 2.5),
        fer("JUGR", "C10", "Lorraine",                  2, 17.0, 2.0, 4.5),
        fer("JUGR", "G10", "Massif central nord",       1, 12.0, 1.2, 2.0),
        fer("JUGR", "G10", "Massif central nord",       2, 16.0, 1.8, 3.8),
        fer("JUGR", "K10", "Alpes nord externes",       1, 13.0, 1.4, 2.5),
        fer("JUGR", "K10", "Alpes nord externes",       2, 17.0, 2.0, 4.5)
    )

    // ──────────────────────────────────────────────────────────────────────────
    // PEUPLIER (POHY/POAL) — Guide CNPF
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildPeuplier(): List<FertiliteEssenceSerEntity> = listOf(
        fer("POHY", "E10", "Normandie",                 2, null, null, 12.0),
        fer("POHY", "E10", "Normandie",                 3, null, null, 18.0),
        fer("POHY", "E10", "Normandie",                 4, null, null, 25.0),
        fer("POHY", "F10", "Île-de-France",             2, null, null, 10.0),
        fer("POHY", "F10", "Île-de-France",             3, null, null, 15.0),
        fer("POHY", "F10", "Île-de-France",             4, null, null, 22.0),
        fer("POHY", "C10", "Lorraine",                  2, null, null, 10.0),
        fer("POHY", "C10", "Lorraine",                  3, null, null, 15.0),
        fer("POHY", "N10", "Landes de Gascogne",        2, null, null, 12.0),
        fer("POHY", "N10", "Landes de Gascogne",        3, null, null, 18.0),
        fer("POHY", "N10", "Landes de Gascogne",        4, null, null, 25.0)
    )

    // ──────────────────────────────────────────────────────────────────────────
    // FEUILLUS PRÉCIEUX — Frêne (FREX) + Érable sycomore (ACPS)
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildFeuillusPrecieux(): List<FertiliteEssenceSerEntity> {
        val list = mutableListOf<FertiliteEssenceSerEntity>()
        for (code in listOf("FREX", "ACPS")) {
            list += fer(code, "E10", "Normandie",            2, 22.0, 2.5, 5.0)
            list += fer(code, "E10", "Normandie",            3, 28.0, 3.5, 7.5)
            list += fer(code, "C10", "Lorraine",             2, 20.0, 2.2, 4.5)
            list += fer(code, "C10", "Lorraine",             3, 26.0, 3.2, 7.0)
            list += fer(code, "K10", "Alpes nord externes",  2, 22.0, 2.5, 5.0)
            list += fer(code, "K10", "Alpes nord externes",  3, 28.0, 3.5, 7.5)
            list += fer(code, "G10", "Massif central nord",  2, 20.0, 2.2, 4.5)
            list += fer(code, "G10", "Massif central nord",  3, 26.0, 3.2, 7.0)
            list += fer(code, "M10", "Pyrénées",             2, 21.0, 2.4, 5.0)
            list += fer(code, "M10", "Pyrénées",             3, 27.0, 3.4, 7.5)
        }
        return list
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PIN D'ALEP (PIHA) / LARICIO / PINASTER méditerranéen
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildPinAlepDaricioPinaster(): List<FertiliteEssenceSerEntity> = listOf(
        fer("PIHA", "P10", "Provence cristalline",      1, 12.0, 1.0, 1.5),
        fer("PIHA", "P10", "Provence cristalline",      2, 16.0, 1.5, 2.5),
        fer("PIHA", "Q10", "Provence calcaire",         1, 11.0, 0.9, 1.3),
        fer("PIHA", "Q10", "Provence calcaire",         2, 15.0, 1.3, 2.2),
        fer("PIHA", "R10", "Languedoc-Roussillon",      1, 11.0, 0.9, 1.3),
        fer("PIHA", "R10", "Languedoc-Roussillon",      2, 15.0, 1.3, 2.2),
        fer("PIHA", "I10", "Cévennes",                  1, 10.0, 0.8, 1.2),
        fer("PIHA", "I10", "Cévennes",                  2, 14.0, 1.2, 2.0)
    )

    // ──────────────────────────────────────────────────────────────────────────
    // ÉPICÉA DE SITKA (PIAB-S — utilisé code PISI) — adapté Guide ONF
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildEpiceaSitka(): List<FertiliteEssenceSerEntity> = listOf(
        fer("PISI", "D11", "Boulonnais-Thiérache",      2, 28.0, 4.0, 12.0, SOURCE_ONF),
        fer("PISI", "D11", "Boulonnais-Thiérache",      3, 35.0, 5.5, 17.0, SOURCE_ONF),
        fer("PISI", "D11", "Boulonnais-Thiérache",      4, 42.0, 7.0, 22.0, SOURCE_ONF),
        fer("PISI", "E10", "Normandie",                 2, 26.0, 3.8, 11.0, SOURCE_ONF),
        fer("PISI", "E10", "Normandie",                 3, 33.0, 5.2, 16.0, SOURCE_ONF),
        fer("PISI", "E10", "Normandie",                 4, 40.0, 6.8, 21.0, SOURCE_ONF),
        fer("PISI", "B11", "Ardenne primaire",          2, 25.0, 3.5, 10.0, SOURCE_ONF),
        fer("PISI", "B11", "Ardenne primaire",          3, 32.0, 5.0, 15.0, SOURCE_ONF),
        fer("PISI", "M10", "Pyrénées",                  2, 24.0, 3.5, 10.0, SOURCE_ONF),
        fer("PISI", "M10", "Pyrénées",                  3, 31.0, 5.0, 14.0, SOURCE_ONF)
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Accès rapide en mémoire (cache lazy)
    // ──────────────────────────────────────────────────────────────────────────
    private val cache: Map<String, List<FertiliteEssenceSerEntity>> by lazy {
        buildAll().groupBy { "${it.essenceCode}__${it.codeSer}" }
    }

    /**
     * Retourne la meilleure classe de station pour une essence dans une SER.
     * (La classe la plus haute disponible dans les données embarquées)
     */
    fun get(essenceCode: String, codeSer: String): FertiliteEssenceSerEntity? =
        cache["${essenceCode}__${codeSer}"]?.maxByOrNull { it.classeStation }

    /**
     * Retourne toutes les classes pour une essence × SER.
     */
    fun getAll(essenceCode: String, codeSer: String): List<FertiliteEssenceSerEntity> =
        cache["${essenceCode}__${codeSer}"] ?: emptyList()

    // ──────────────────────────────────────────────────────────────────────────
    // Helper
    // ──────────────────────────────────────────────────────────────────────────
    private fun fer(
        essenceCode: String,
        codeSer: String,
        nomSer: String,
        classeStation: Int,
        hoRef100Ans: Double?,
        gMaxRef: Double?,
        accroissement: Double?,
        source: String = SOURCE_CNPF
    ): FertiliteEssenceSerEntity = FertiliteEssenceSerEntity(
        fertiliteId = "${essenceCode}_${codeSer}_$classeStation",
        essenceCode = essenceCode,
        codeSer = codeSer,
        nomSer = nomSer,
        classeStation = classeStation,
        hoRef100Ans = hoRef100Ans,
        gMaxRef = gMaxRef,
        accroissementRefM3HaAn = accroissement,
        conditionsRequisesJson = null,
        itineraireSylvicoleJson = null,
        sourceGuide = source
    )
}
