package com.mewname.app

import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mewname.app.domain.PvpCalculationOptions
import com.mewname.app.domain.PvpCalculationSettings

@Composable
internal fun pvpCalculationOptions(): PvpCalculationOptions {
    val context = LocalContext.current
    val prefs = remember(context) { PvpCalculationSettings.preferences(context) }
    var options by remember(prefs) { mutableStateOf(PvpCalculationSettings.read(context)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            options = PvpCalculationSettings.read(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        options = PvpCalculationSettings.read(context)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return options
}

@Composable
internal fun PvpCalculationPreferences() {
    val context = LocalContext.current
    val language = appLanguage()
    val options = pvpCalculationOptions()
    AppSectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(lt(language, "Nível máximo", "Maximum level", "Nivel máximo"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(40, 50).forEach { level ->
                    AppChoiceChip(selected = options.maxLevel == level,
                        onClick = { PvpCalculationSettings.save(context, options.copy(maxLevel = level)) },
                        label = { Text(level.toString()) })
                }
            }
        }
    }
    AppSectionCard(Modifier.fillMaxWidth()) {
        AppToggleRow(
            label = lt(language, "Considerar melhor companheiro", "Include Best Buddy", "Incluir mejor compañero"),
            checked = options.bestBuddy,
            onCheckedChange = { PvpCalculationSettings.save(context, options.copy(bestBuddy = it)) },
            modifier = Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 48.dp))
    }
}
