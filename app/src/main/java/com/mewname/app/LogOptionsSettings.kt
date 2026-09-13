package com.mewname.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext

internal object LogOptionsSettings {
    internal const val KEY = "show_log_options"
    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences("diagnostic_options", Context.MODE_PRIVATE)
    fun enabled(context: Context): Boolean = preferences(context).getBoolean(KEY, false)
    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY, enabled).apply()
    }
}

@Composable
internal fun logOptionsEnabled(): Boolean {
    val context = LocalContext.current
    val prefs = remember(context) { LogOptionsSettings.preferences(context) }
    var enabled by remember(prefs) { mutableStateOf(prefs.getBoolean(LogOptionsSettings.KEY, false)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            enabled = prefs.getBoolean(LogOptionsSettings.KEY, false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        enabled = prefs.getBoolean(LogOptionsSettings.KEY, false)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return enabled
}

@Composable
internal fun LogOptionsPreference() {
    val context = LocalContext.current
    val language = appLanguage()
    GlassSwitchField(
        label = lt(language, "Mostrar opções de log", "Show log options", "Mostrar opciones de registro"),
        checked = logOptionsEnabled(),
        onCheckedChange = { LogOptionsSettings.setEnabled(context, it) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    )
}