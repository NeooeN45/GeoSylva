package com.forestry.counter.presentation.screens.forestry

/**
 * Sous-écran actif dans le hub Martelage.
 *
 * Remplace le [selectedTabIndex] (entier fragile) par une machine d'état
 * typée. [Hub] est l'état initial — les autres correspondent chacun à un
 * écran plein écran avec TopAppBar et flèche retour.
 *
 * Spec : §3 de docs/superpowers/specs/2026-09-02-martelage-hub-redesign-design.md
 */
enum class MartelageSubScreen {
    Hub,
    SyntheseDetaillee,
    CalculFin,
    Schemas,
    Carte,
}
