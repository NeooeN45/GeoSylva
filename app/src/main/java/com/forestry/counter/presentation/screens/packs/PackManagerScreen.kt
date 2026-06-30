package com.forestry.counter.presentation.screens.packs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.R
import com.forestry.counter.domain.model.pack.GeoPackDescriptor
import com.forestry.counter.domain.model.pack.PackLevel
import com.forestry.counter.domain.model.pack.PackStatus
import com.forestry.counter.domain.usecase.pack.PackResolver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackManagerScreen(
    onNavigateBack: () -> Unit
) {
    val allPacks = remember { listOf(PackResolver.EMBEDDED_NATIONAL_PACK) + PackResolver.REGIONAL_CATALOG }
    var selectedRegion by remember { mutableStateOf("Tout") }
    val regions = remember { listOf("Tout") + allPacks.mapNotNull { it.codeINSEE }.filter { it != "FR" }.distinct().sorted() }

    val filtered = remember(selectedRegion, allPacks) {
        if (selectedRegion == "Tout") allPacks
        else allPacks.filter { it.codeINSEE == selectedRegion || it.level == PackLevel.SOCLE_NATIONAL }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.packs_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            StorageSummaryBanner(allPacks)
            NationalPackBanner(allPacks.firstOrNull { it.level == PackLevel.SOCLE_NATIONAL })
            ScrollableTabRow(
                selectedTabIndex = regions.indexOf(selectedRegion).coerceAtLeast(0),
                edgePadding = 16.dp
            ) {
                regions.forEach { region ->
                    Tab(
                        selected = selectedRegion == region,
                        onClick = { selectedRegion = region },
                        text = { Text(if (region == "Tout") stringResource(R.string.pack_all) else region, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered.filter { it.level != PackLevel.SOCLE_NATIONAL }) { pack ->
                    PackCard(pack)
                }
                if (filtered.none { it.level != PackLevel.SOCLE_NATIONAL }) {
                    item {
                        Text(
                            stringResource(R.string.pack_no_regional),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageSummaryBanner(packs: List<GeoPackDescriptor>) {
    val installedCount = packs.count { it.status == PackStatus.INSTALLED }
    val totalSizeMb = packs.filter { it.status == PackStatus.INSTALLED }.sumOf { it.sizeKb / 1024 }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null,
                    modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.pack_installed_summary_format, installedCount, totalSizeMb),
                    style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.pack_all_format, packs.size), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NationalPackBanner(nationalPack: GeoPackDescriptor?) {
    if (nationalPack == null) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Public, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(nationalPack.name, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(stringResource(R.string.pack_national_offline_format, nationalPack.features.floraSpeciesCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            }
            StatusChip(PackStatus.INSTALLED)
        }
    }
}

@Composable
private fun PackCard(pack: GeoPackDescriptor) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = packLevelColor(pack.level).copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(packLevelIcon(pack.level), contentDescription = null,
                            modifier = Modifier.size(20.dp), tint = packLevelColor(pack.level))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(pack.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${pack.codeINSEE ?: stringResource(R.string.pack_national_label)} · ${pack.sizeKb / 1024} Mo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatusChip(pack.status)
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(48.dp)) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(if (expanded) R.string.cd_collapse else R.string.cd_expand), modifier = Modifier.size(18.dp))
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.pack_built_format, pack.version, pack.buildDate), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                val featureList = buildList {
                    if (pack.features.hasFloraDatabase) add(stringResource(R.string.pack_feature_flora_format, pack.features.floraSpeciesCount))
                    if (pack.features.hasStationRules) add(stringResource(R.string.pack_feature_station_rules))
                    if (pack.features.hasRegionalSRGS) add(stringResource(R.string.pack_feature_regional_srgs))
                    if (pack.features.hasDriasProjets) add(stringResource(R.string.pack_feature_drias))
                }
                featureList.forEach { feature ->
                    PackFeatureRow(feature)
                }
                Spacer(Modifier.height(8.dp))
                when (pack.status) {
                    PackStatus.AVAILABLE -> OutlinedButton(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.pack_install))
                    }
                    PackStatus.UPDATE_PENDING -> Button(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Update, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.pack_update_available))
                    }
                    PackStatus.INSTALLED -> TextButton(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.pack_uninstall), color = MaterialTheme.colorScheme.error) }
                    PackStatus.ERROR -> OutlinedButton(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.pack_error_retry))
                    }
                    PackStatus.EMBEDDED, PackStatus.DOWNLOADING -> {}
                }
            }
        }
    }
}

@Composable
private fun PackFeatureRow(feature: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Check, contentDescription = null,
            modifier = Modifier.size(14.dp), tint = Color(0xFF388E3C))
        Spacer(Modifier.width(6.dp))
        Text(feature, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatusChip(status: PackStatus) {
    val (label, color) = when (status) {
        PackStatus.INSTALLED       -> stringResource(R.string.pack_status_installed) to Color(0xFF2E7D32)
        PackStatus.EMBEDDED        -> stringResource(R.string.pack_status_embedded) to Color(0xFF1565C0)
        PackStatus.AVAILABLE       -> stringResource(R.string.pack_status_available) to Color(0xFF1565C0)
        PackStatus.UPDATE_PENDING  -> stringResource(R.string.pack_update_available) to Color(0xFFE65100)
        PackStatus.DOWNLOADING     -> "…" to Color(0xFF6A1B9A)
        PackStatus.ERROR           -> stringResource(R.string.pack_status_error) to Color(0xFFC62828)
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

private fun packLevelColor(level: PackLevel): Color = when (level) {
    PackLevel.SOCLE_NATIONAL  -> Color(0xFF1565C0)
    PackLevel.REGIONAL        -> Color(0xFF388E3C)
    PackLevel.DEPARTEMENTAL   -> Color(0xFFE65100)
}

private fun packLevelIcon(level: PackLevel) = when (level) {
    PackLevel.SOCLE_NATIONAL -> Icons.Default.Public
    PackLevel.REGIONAL       -> Icons.Default.Map
    PackLevel.DEPARTEMENTAL  -> Icons.Default.LocationOn
}
