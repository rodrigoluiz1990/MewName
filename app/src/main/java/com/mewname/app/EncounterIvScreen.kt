package com.mewname.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.mewname.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun EncounterIvScreen(reading: EncounterReading, onClose: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val calculator = remember { PvpRankCalculator() }
    var name by remember { mutableStateOf(reading.name) }
    var cp by remember { mutableStateOf(reading.cp.toString()) }
    var level by remember { mutableStateOf("") }
    var request by remember { mutableStateOf(Triple(name, cp, level)) }
    var rows by remember { mutableStateOf<List<PvpRankCalculator.StatProduct>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(request) {
        rows = null
        error = null
        val value = request.second.toIntOrNull()
        val lv = request.third.replace(',', '.').toDoubleOrNull()
        if (value == null || value < 10 || (request.third.isNotBlank() && (lv == null || lv !in 1.0..51.0 || lv * 2 != kotlin.math.floor(lv * 2)))) {
            error = "Confira o PC e o nível (1 a 51, em passos de 0,5)."
            rows = emptyList()
        } else {
            try {
                rows = withContext(Dispatchers.Default) { calculator.encounterPossibilities(context, request.first, value, lv) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error = "Não foi possível calcular. Confira a espécie e a forma."; rows = emptyList() }
        }
    }
    AppSectionCard(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(52.dp)) {
            Text("IVs possíveis na captura", Modifier.align(Alignment.Center).padding(horizontal = 48.dp), style = MaterialTheme.typography.titleMedium)
            IconButton(onClose, Modifier.align(Alignment.CenterEnd)) { Icon(AppIcons.Close, "Fechar") }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                CompositionLocalProvider(LocalGlassReviewStyle provides true) {
                    GlassFieldRow(weights = listOf(2f, 1f, 1f)) {
                        CompactField(label = "Nome", value = name, onValueChange = { name = it }, modifier = Modifier.weight(2f))
                        CompactField(label = "PC", value = cp, onValueChange = { cp = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        CompactField(label = "Nível", value = level, onValueChange = { level = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                }
            }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(rows?.let { "${it.size} combinações" } ?: "Calculando...", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = {
                    focus.clearFocus()
                    keyboard?.hide()
                    request = Triple(name.trim(), cp.trim(), level.trim())
                }, enabled = rows != null) { Text("Recalcular") }
            } }
            if (rows == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { message -> item { Text(message) } }
            rows?.let { values ->
                if (values.isEmpty() && error == null) item { Text("Nenhuma combinação encontrada.", style = MaterialTheme.typography.bodySmall) }
                if (values.isNotEmpty()) {
                    item { EncounterIvTableRow("IVs", "IV %", "Nível", header = true) }
                    items(values) { iv ->
                        EncounterIvTableRow("${iv.atk}/${iv.def}/${iv.sta}",
                            "${kotlin.math.round((iv.atk + iv.def + iv.sta) * 1000.0 / 45) / 10}%",
                            if (iv.level % 1.0 == 0.0) iv.level.toInt().toString() else iv.level.toString())
                    }
                }
            }
        }
    }
}

@Composable
private fun EncounterIvTableRow(ivs: String, percent: String, level: String, header: Boolean = false) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Row(Modifier.fillMaxWidth(0.85f).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(ivs, percent, level).forEach { value ->
            Text(value, Modifier.weight(1f), textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                fontFamily = if (header) FontFamily.Default else FontFamily.Monospace,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}
}
