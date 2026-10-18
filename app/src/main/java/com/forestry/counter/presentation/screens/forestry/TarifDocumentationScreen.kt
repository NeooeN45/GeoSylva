package com.forestry.counter.presentation.screens.forestry

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forestry.counter.R

/** Teaching references only: this screen neither selects nor qualifies a volume model. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TarifDocumentationScreen(onNavigateBack: () -> Unit) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        R.string.tarif_qualified_tab_sources,
        R.string.tarif_qualified_tab_choice,
        R.string.tarif_qualified_tab_formulas,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tarif_qualified_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(stringResource(title)) },
                    )
                }
            }
            Box(Modifier.weight(1f).widthIn(max = 840.dp).fillMaxWidth()) {
                when (selectedTab) {
                    0 -> QualifiedSourcesTab()
                    1 -> QualifiedChoiceTab()
                    2 -> QualifiedFormulasTab()
                }
            }
        }
    }
}

@Composable
private fun QualifiedSourcesTab() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            QualifiedIntroduction(
                R.string.tarif_qualified_sources_title,
                R.string.tarif_qualified_sources_description,
            )
        }
        item { QualifiedNote(R.string.tarif_qualified_sources_scope) }
        item {
            QualifiedSourceCard(
                R.string.tarif_qualified_source_inventory_title,
                R.string.tarif_qualified_source_inventory_detail,
                R.string.tarif_qualified_source_inventory_body,
                "https://www.cnpf.fr/sites/socle/files/2024-05/16_Inventaire_forestier_par_echantillonnage_systematique.pdf",
            )
        }
        item {
            QualifiedSourceCard(
                R.string.tarif_qualified_source_standing_title,
                R.string.tarif_qualified_source_standing_detail,
                R.string.tarif_qualified_source_standing_body,
                "https://www.cnpf.fr/sites/socle/files/cnpf-old/498015_volume_pied_1.pdf",
            )
        }
        item {
            QualifiedSourceCard(
                R.string.tarif_qualified_source_slenderness_title,
                R.string.tarif_qualified_source_slenderness_detail,
                R.string.tarif_qualified_source_slenderness_body,
                "https://www.cnpf.fr/sites/socle/files/2024-05/13.c_La_futaie_reguliere_diagnostic_et_gestions_envisagees.pdf",
            )
        }
        item {
            QualifiedSourceCard(
                R.string.tarif_qualified_source_schaeffer_title,
                R.string.tarif_qualified_source_schaeffer_detail,
                R.string.tarif_qualified_source_schaeffer_body,
                "https://hal.science/hal-03382015v1/document",
            )
        }
        item {
            QualifiedSourceCard(
                R.string.tarif_qualified_source_fao_title,
                R.string.tarif_qualified_source_fao_detail,
                R.string.tarif_qualified_source_fao_body,
                "https://www.fao.org/4/x6831e/X6831E13.htm#5791",
            )
        }
        item {
            QualifiedSourceCard(
                R.string.tarif_qualified_source_basal_title,
                R.string.tarif_qualified_source_basal_detail,
                R.string.tarif_qualified_source_basal_body,
                "https://fennerschool-associated.anu.edu.au/mensuration/BrackandWood1998/S_BA.HTM",
            )
        }
    }
}

@Composable
private fun QualifiedChoiceTab() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            QualifiedIntroduction(
                R.string.tarif_qualified_choice_title,
                R.string.tarif_qualified_choice_description,
            )
        }
        item {
            QualifiedExplanationCard(
                Icons.Default.Forest,
                R.string.tarif_qualified_one_entry_title,
                R.string.tarif_qualified_one_entry_body,
                R.string.tarif_qualified_one_entry_inputs,
            )
        }
        item {
            QualifiedExplanationCard(
                Icons.Default.Straighten,
                R.string.tarif_qualified_two_entries_title,
                R.string.tarif_qualified_two_entries_body,
                R.string.tarif_qualified_two_entries_inputs,
            )
        }
        item {
            QualifiedExplanationCard(
                Icons.Default.Place,
                R.string.tarif_qualified_domain_title,
                R.string.tarif_qualified_domain_body,
            )
        }
        item {
            QualifiedExplanationCard(
                Icons.Default.Straighten,
                R.string.tarif_qualified_measurements_title,
                R.string.tarif_qualified_measurements_body,
            )
        }
        item {
            QualifiedExplanationCard(
                Icons.Default.MenuBook,
                R.string.tarif_qualified_trace_title,
                R.string.tarif_qualified_trace_body,
            )
        }
        item { QualifiedNote(R.string.tarif_qualified_no_precision) }
    }
}

@Composable
private fun QualifiedFormulasTab() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            QualifiedIntroduction(
                R.string.tarif_qualified_formulas_title,
                R.string.tarif_qualified_formulas_description,
            )
        }
        item {
            QualifiedFormulaCard(
                R.string.tarif_qualified_basal_title,
                R.string.tarif_qualified_basal_formula,
                R.string.tarif_qualified_basal_body,
            )
        }
        item {
            QualifiedFormulaCard(
                R.string.tarif_qualified_quadratic_title,
                R.string.tarif_qualified_quadratic_formula,
                R.string.tarif_qualified_quadratic_body,
            )
        }
        item {
            QualifiedFormulaCard(
                R.string.tarif_qualified_per_hectare_title,
                R.string.tarif_qualified_per_hectare_formula,
                R.string.tarif_qualified_per_hectare_body,
            )
        }
        item {
            QualifiedFormulaCard(
                R.string.tarif_qualified_slenderness_title,
                R.string.tarif_qualified_slenderness_formula,
                R.string.tarif_qualified_slenderness_body,
            )
        }
        item { QualifiedNote(R.string.tarif_qualified_slenderness_limit) }
        item {
            QualifiedFormulaCard(
                R.string.tarif_qualified_logs_title,
                R.string.tarif_qualified_logs_formula,
                R.string.tarif_qualified_logs_body,
            )
        }
        item { QualifiedNote(R.string.tarif_qualified_logs_limit) }
    }
}

@Composable
private fun QualifiedIntroduction(@StringRes title: Int, @StringRes description: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QualifiedExplanationCard(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes inputs: Int? = null,
) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(24.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Text(
                    stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            if (inputs != null) {
                Text(
                    stringResource(inputs),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun QualifiedFormulaCard(@StringRes title: Int, @StringRes formula: Int, @StringRes body: Int) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    Icons.Default.Calculate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(formula),
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun QualifiedNote(@StringRes text: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Default.Info, contentDescription = null)
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun QualifiedSourceCard(
    @StringRes title: Int,
    @StringRes detail: Int,
    @StringRes body: Int,
    url: String,
) {
    val context = LocalContext.current
    val unavailableMessage = stringResource(R.string.tarif_qualified_source_unavailable)
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(detail),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = {
                    // Fixed HTTPS references only; no third-party document is bundled.
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addCategory(Intent.CATEGORY_BROWSABLE),
                        )
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, unavailableMessage, Toast.LENGTH_LONG).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(context, unavailableMessage, Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tarif_qualified_source_open), modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                stringResource(R.string.tarif_qualified_source_external),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
