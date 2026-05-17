package com.forestry.counter.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forestry.counter.domain.usecase.florist.FloristDatabase
import com.forestry.counter.domain.usecase.florist.TypeMilieu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloraFamilyBrowserSheet(
    selectedIds: List<String>,
    contextMilieu: TypeMilieu? = null,
    onSpeciesToggled: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val species = remember(contextMilieu) {
        FloristDatabase.findIndicatrices(contextMilieu ?: com.forestry.counter.domain.usecase.florist.TypeMilieu.FORET_FEUILLUE)
            .take(100)
            .map { it.id to it.taxonomie.nomFrancais }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sélectionner des espèces", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxHeight(0.7f)) {
                items(species) { (id, name) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Checkbox(
                            checked = id in selectedIds,
                            onCheckedChange = { onSpeciesToggled(id) }
                        )
                    }
                }
            }
        }
    }
}
