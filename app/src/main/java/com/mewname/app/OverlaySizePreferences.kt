package com.mewname.app

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal object OverlaySizeSettings {
    const val BUBBLE = "overlay_bubble_percent"
    const val MISSIONS = "overlay_missions_percent"
    fun percent(prefs: SharedPreferences, key: String) = prefs.getInt(key, 100).coerceIn(75, 150)
    fun scale(prefs: SharedPreferences, key: String) = percent(prefs, key) / 100f
}

@Composable
internal fun OverlaySizePreferences() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("home_layout", 0) }
    val language = appLanguage()
    Text(lt(language, "Modo sobreposição", "Overlay mode", "Modo superposición"),
        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    listOf(
        OverlaySizeSettings.BUBBLE to lt(language, "Botão de leitura", "Reading button", "Botón de lectura"),
        OverlaySizeSettings.MISSIONS to lt(language, "Missões", "Missions", "Misiones")
    ).forEach { (key, label) ->
        var percent by remember(key) { mutableIntStateOf(OverlaySizeSettings.percent(prefs, key)) }
        DisposableEffect(prefs, key) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
                if (changed == key) percent = OverlaySizeSettings.percent(prefs, key)
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.weight(1f))
            Text("$percent%")
        }
        Slider(value = percent.toFloat(), onValueChange = {
            percent = (it / 5).roundToInt() * 5
        }, valueRange = 75f..150f, steps = 14,
            onValueChangeFinished = { prefs.edit().putInt(key, percent).apply() })
    }
    AppActionButton(onClick = {
        prefs.edit().putInt(OverlaySizeSettings.BUBBLE, 100)
            .putInt(OverlaySizeSettings.MISSIONS, 100).apply()
    }) { Text(lt(language, "Restaurar padrão", "Restore default", "Restaurar predeterminado")) }
}
