package com.forestry.counter.presentation.screens.forestry

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.data.service.MartelageGpsService
import com.forestry.counter.domain.model.InventaireSession
import com.forestry.counter.domain.model.MartelageStates

/** Autorisation contextuelle ; la notification permet de continuer écran éteint ou hors de cette page. */
@Composable
fun MartelageGpsTracking(session: InventaireSession?) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var permitted by remember { mutableStateOf(MartelageGpsService.hasPermission(context)) }
    val status by MartelageGpsService.currentStatus.collectAsStateWithLifecycle()
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitted = MartelageGpsService.hasPermission(context)
    }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permitted = MartelageGpsService.hasPermission(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(session?.sessionId, session?.martelageState, permitted) {
        if (session?.placetteId != null && session.martelageState == MartelageStates.RUNNING && permitted) {
            MartelageGpsService.start(context, session.sessionId)
        } else if (session != null && status.sessionId == session.sessionId) {
            MartelageGpsService.stop(context, session.sessionId)
        }
    }
    if (session?.placetteId != null && session.martelageState == MartelageStates.RUNNING) {
        Column {
            Text(if (permitted && status.sessionId == session.sessionId) status.message else "GPS non actif")
            if (!permitted || status.sessionId != session.sessionId || !status.recording) {
                TextButton(onClick = {
                    if (permitted) {
                        MartelageGpsService.start(context, session.sessionId)
                        return@TextButton
                    }
                    request.launch(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
                    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { Text("Activer le tracé GPS") }
            }
        }
    }
}
