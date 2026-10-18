package com.forestry.counter.presentation.screens.onboarding

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import com.forestry.counter.R

internal data class ProfessionOption(
    val code: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    companion object {
        const val OTHER_CODE = "autre"

        // Historical preferences retain this ambiguous declaration unchanged.
        // Never infer ONF or CNPF membership from an old combined selection.
        const val LEGACY_PUBLIC_FORESTER_CODE = "forestier_onf_cnpf"
    }
}

/** Declared profession only: no role, organisation membership or permissions. */
internal val PROFESSION_OPTIONS = listOf(
    ProfessionOption(
        "forestier_onf",
        R.string.profession_onf,
        Icons.Filled.Forest,
    ),
    ProfessionOption(
        "forestier_cnpf",
        R.string.profession_cnpf,
        Icons.Filled.Forest,
    ),
    ProfessionOption(
        "forestier_ddt",
        R.string.profession_ddt,
        Icons.Filled.Engineering,
    ),
    ProfessionOption(
        "expert_forestier",
        R.string.profession_expert_forestier,
        Icons.Filled.WorkspacePremium,
    ),
    ProfessionOption(
        "exploitant_forestier",
        R.string.profession_exploitant,
        Icons.Filled.Agriculture,
    ),
    ProfessionOption(
        "conducteur_travaux_forestiers",
        R.string.profession_conducteur_travaux,
        Icons.Filled.Engineering,
    ),
    ProfessionOption(
        "commerce_bois",
        R.string.profession_commerce_bois,
        Icons.Filled.Storefront,
    ),
    ProfessionOption(
        "technicien_forestier",
        R.string.profession_technicien,
        Icons.Filled.Engineering,
    ),
    ProfessionOption(
        "proprietaire_forestier",
        R.string.profession_proprietaire,
        Icons.Filled.HomeWork,
    ),
    ProfessionOption(
        "etudiant",
        R.string.profession_etudiant,
        Icons.Filled.School,
    ),
    ProfessionOption(
        "chercheur_enseignant",
        R.string.profession_chercheur,
        Icons.Filled.Science,
    ),
    ProfessionOption(
        ProfessionOption.OTHER_CODE,
        R.string.profession_autre,
        Icons.Filled.MoreHoriz,
    ),
)
