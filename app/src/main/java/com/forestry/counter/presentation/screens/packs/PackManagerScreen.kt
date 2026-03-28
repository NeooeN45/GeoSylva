package com.forestry.counter.presentation.screens.packs

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.domain.model.pack.*
import com.forestry.counter.domain.usecase.pack.PackManager
import com.forestry.counter.domain.usecase.pack.PackResolver
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackManagerScreen(
    onBack: () -> Unit,
    currentLat: Double? = null,
    currentLon: Double? = null
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val manager = remember { PackManager.getInstance(context) }
    val state by manager.packState.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Régions", "Tout")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Packs territoriaux", fontWeight = FontWeight.Bold)
                        Text(
                            manager.contextSummary(currentLat, currentLon),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") }
                },
                actions = {
                    if (currentLat != null) {
                        IconButton(onClick = { scope.launch { manager.preloadForLocation(currentLat, currentLon!!) } }) {
                            Icon(Icons.Default.MyLocation, "Précharger ma zone")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            // ── Bandeau état global ─────────────────────────────────────────
            StorageSummaryBanner(
                installedCount = state.installed.count { it.level != PackLevel.SOCLE_NATIONAL },
                sizeMb         = manager.installedSizeMb()
            )

            // ── Bandeau socle national ──────────────────────────────────────
            NationalPackBanner(PackResolver.EMBEDDED_NATIONAL_PACK)

            Spacer(Modifier.height(8.dp))

            // ── Tabs ────────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title) })
                }
            }

            // ── Contenu ─────────────────────────────────────────────────────
            val visiblePacks = when (selectedTab) {
                0    -> state.allPacks.filter { it.level == PackLevel.REGIONAL }
                else -> state.allPacks.filter { it.level != PackLevel.SOCLE_NATIONAL }
            }

            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visiblePacks, key = { it.id }) { pack ->
                    PackCard(
                        pack       = pack,
                        progress   = state.downloadProgress[pack.id],
                        onInstall  = { scope.launch { manager.installPack(pack.id) } },
                        onRemove   = { manager.uninstallPack(pack.id) }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StorageSummaryBanner(installedCount: Int, sizeMb: Float) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier            = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Storage, null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$installedCount pack${if (installedCount > 1) "s" else ""} local installé${if (installedCount > 1) "s" else ""}",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "%.1f Mo utilisés".format(sizeMb),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun NationalPackBanner(pack: GeoPackDescriptor) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.1f)),
        border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.4f))
    ) {
        Row(
            modifier              = Modifier.padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Public, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(pack.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    StatusChip(PackStatus.EMBEDDED)
                }
                Text(
                    "${pack.features.floraSpeciesCount} espèces · ${pack.features.essencesCount} essences · ${pack.features.stationTypesCount} types station",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PackCard(
    pack: GeoPackDescriptor,
    progress: Float?,
    onInstall: () -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isInstalled = pack.status in listOf(PackStatus.INSTALLED, PackStatus.EMBEDDED)
    val isDownloading = progress != null
    val hasUpdate = pack.status == PackStatus.UPDATE_PENDING

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isInstalled) 2.dp else 0.dp),
        border = if (!isInstalled && !isDownloading)
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // En-tête
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(packLevelColor(pack.level).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        packLevelIcon(pack.level), null,
                        tint     = packLevelColor(pack.level),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            pack.name,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        StatusChip(pack.status)
                    }
                    Text(
                        "v${pack.version} · ${formatSize(pack.sizeKb)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Action principale
                if (isDownloading) {
                    CircularProgressIndicator(
                        progress    = { progress ?: 0f },
                        modifier    = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )
                } else if (!isInstalled) {
                    FilledTonalButton(
                        onClick       = onInstall,
                        modifier      = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Installer", style = MaterialTheme.typography.labelMedium)
                    }
                } else if (hasUpdate) {
                    FilledTonalButton(
                        onClick       = onInstall,
                        modifier      = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.Update, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("MAJ", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    IconButton(
                        onClick  = onRemove,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, "Supprimer",
                            tint     = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Détail expandable
            AnimatedVisibility(
                visible  = expanded,
                enter    = expandVertically(tween(200)) + fadeIn(),
                exit     = shrinkVertically(tween(200)) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(4.dp))
                    PackFeatureRow(Icons.Default.Grass, "${pack.features.floraSpeciesCount} espèces floristiques")
                    PackFeatureRow(Icons.Default.Forest, "${pack.features.essencesCount} essences")
                    PackFeatureRow(Icons.Default.Terrain, "${pack.features.stationTypesCount} types de station")
                    if (pack.features.hasRegionalSRGS) PackFeatureRow(Icons.Default.Description, "SRGS régional embarqué")
                    if (pack.features.hasGpsContextCache) PackFeatureRow(Icons.Default.GpsFixed, "Cache GPS contextes")
                    if (pack.features.hasFtsIndex) PackFeatureRow(Icons.Default.Search, "Recherche pleine-texte flore")
                    if (pack.features.hasDriasProjets) PackFeatureRow(Icons.Default.WbSunny, "Projections DRIAS locales")
                    if (pack.metaInfo.expertValidated) PackFeatureRow(Icons.Default.VerifiedUser, "Validé par expert terrain")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Mis à jour : ${pack.buildDate} · ${formatSize(pack.sizeKb)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PackFeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier              = Modifier.padding(vertical = 1.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StatusChip(status: PackStatus) {
    val (bg, fg, label) = when (status) {
        PackStatus.EMBEDDED        -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Embarqué")
        PackStatus.INSTALLED       -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), "Installé")
        PackStatus.AVAILABLE       -> Triple(Color(0xFFF3E5F5), Color(0xFF6A1B9A), "Disponible")
        PackStatus.UPDATE_PENDING  -> Triple(Color(0xFFFFF8E1), Color(0xFFE65100), "MAJ dispo")
        PackStatus.DOWNLOADING     -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), "…")
        PackStatus.ERROR           -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "Erreur")
    }
    Surface(color = bg, shape = RoundedCornerShape(20.dp)) {
        Text(
            label,
            color    = fg,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun packLevelColor(level: PackLevel): Color = when (level) {
    PackLevel.SOCLE_NATIONAL -> Color(0xFF2E7D32)
    PackLevel.REGIONAL       -> Color(0xFF1565C0)
    PackLevel.DEPARTEMENTAL  -> Color(0xFF6A1B9A)
}

private fun packLevelIcon(level: PackLevel) = when (level) {
    PackLevel.SOCLE_NATIONAL -> Icons.Default.Public
    PackLevel.REGIONAL       -> Icons.Default.Map
    PackLevel.DEPARTEMENTAL  -> Icons.Default.LocationOn
}

private fun formatSize(kb: Long): String = when {
    kb == 0L     -> "Embarqué"
    kb < 1024    -> "${kb} Ko"
    else         -> "%.1f Mo".format(kb / 1024f)
}
