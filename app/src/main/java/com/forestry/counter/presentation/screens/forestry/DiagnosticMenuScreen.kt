package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.diagnostic.SylviculturalDiagnosticEngine
import com.forestry.counter.domain.repository.DiagnosticSylvicoleRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.StationEnvironnementaleRepository
import com.forestry.counter.domain.repository.TigeRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticMenuScreen(
    parcelleId: String,
    parcelleRepository: ParcelleRepository,
    tigeRepository: TigeRepository,
    stationRepository: StationEnvironnementaleRepository,
    diagnosticRepository: DiagnosticSylvicoleRepository,
    onNavigateBack: () -> Unit,
    onNavigateToDiagnosticResult: (diagnosticId: String) -> Unit
) {
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val snackbar   = remember { SnackbarHostState() }

    val parcelleFlow = remember(parcelleRepository, parcelleId) {
        parcelleRepository.getParcelleById(parcelleId)
    }
    val parcelle by parcelleFlow.collectAsStateWithLifecycle(initialValue = null)

    val stationFlow = remember(stationRepository, parcelleId) {
        stationRepository.getByParcelle(parcelleId)
    }
    val station by stationFlow.collectAsStateWithLifecycle(initialValue = null)

    val latestDiagnosticFlow = remember(diagnosticRepository, parcelleId) {
        diagnosticRepository.getByParcelle(parcelleId)
    }
    val diagnostics by latestDiagnosticFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val latestDiagnostic = diagnostics.firstOrNull()

    var isRunning by remember { mutableStateOf(false) }

    val engine = remember(stationRepository, tigeRepository, diagnosticRepository, context) {
        SylviculturalDiagnosticEngine(
            stationRepository    = stationRepository,
            tigeRepository       = tigeRepository,
            diagnosticRepository = diagnosticRepository,
            context              = context
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostic sylvicole") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // En-tête parcelle
            ParcelleHeaderCard(
                nomParcelle  = parcelle?.name ?: parcelleId.take(12),
                codeSer      = station?.codeSer,
                nomSer       = station?.nomSer,
                hasStation   = station != null
            )

            // Pré-requis
            PrerequisCard(
                hasStation   = station != null,
                hasTiges     = true
            )

            // Dernier diagnostic
            if (latestDiagnostic != null) {
                LastDiagnosticCard(
                    scoreGlobal  = latestDiagnostic.scoreGlobal,
                    dateMs       = latestDiagnostic.dateCreation,
                    onOpen       = { onNavigateToDiagnosticResult(latestDiagnostic.diagnosticId) }
                )
            }

            // Bouton lancer
            Button(
                onClick = {
                    isRunning = true
                    scope.launch {
                        runCatching {
                            engine.run(parcelleId)
                        }.onSuccess { result ->
                            isRunning = false
                            onNavigateToDiagnosticResult(result.diagnosticId)
                        }.onFailure { err ->
                            isRunning = false
                            snackbar.showSnackbar("Erreur : ${err.message ?: "inconnue"}")
                        }
                    }
                },
                enabled   = !isRunning,
                modifier  = Modifier.fillMaxWidth().height(56.dp),
                shape     = RoundedCornerShape(14.dp),
                colors    = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Analyse en cours…", style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (latestDiagnostic == null) "Lancer le diagnostic" else "Relancer l'analyse",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Explication algo
            AlgoInfoCard()
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Composants internes
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ParcelleHeaderCard(
    nomParcelle: String,
    codeSer: String?,
    nomSer: String?,
    hasStation: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(Icons.Filled.Forest, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(nomParcelle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (codeSer != null) {
                    Text("SER $codeSer — ${nomSer ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                } else {
                    Text("SER non résolue — localisation requise", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PrerequisCard(hasStation: Boolean, hasTiges: Boolean) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pré-requis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            PrerequisRow("Station environnementale (sol, climat, SER)", hasStation)
            PrerequisRow("Peuplement (placettes & tiges inventoriées)", hasTiges)
        }
    }
}

@Composable
private fun PrerequisRow(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LastDiagnosticCard(scoreGlobal: Int?, dateMs: Long, onOpen: () -> Unit) {
    val dateStr = remember(dateMs) {
        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(dateMs))
    }
    Card(
        onClick  = onOpen,
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Dernier diagnostic", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(dateStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            if (scoreGlobal != null) {
                val color = when {
                    scoreGlobal >= 80 -> MaterialTheme.colorScheme.primary
                    scoreGlobal >= 50 -> MaterialTheme.colorScheme.tertiary
                    else              -> MaterialTheme.colorScheme.error
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                ) {
                    Text("$scoreGlobal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = color)
                }
            }
        }
    }
}

@Composable
private fun AlgoInfoCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape  = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Algorithme CNPF déterministe", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Scoring multi-critères : fertilité SER × 40%, adéquation climatique × 20%, sol × 20%, résilience CC2050 × 10%, risques sanitaires × 10%. 20 essences évaluées.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
