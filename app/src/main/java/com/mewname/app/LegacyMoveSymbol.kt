package com.mewname.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.mewname.app.model.NamingConfig
import com.mewname.app.model.defaultSymbols

/** Same precedence as the naming review: first nonblank configured symbol, then default. */
internal fun legacyMoveSymbol(configs: List<NamingConfig>): String =
    configs.firstNotNullOfOrNull { it.symbols["LEGACY"]?.takeIf(String::isNotBlank) }
        ?: defaultSymbols()["LEGACY"].orEmpty()

@Composable
internal fun configuredLegacyMoveSymbol(): String {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.getSharedPreferences("mewname_prefs", Context.MODE_PRIVATE) }
    var raw by remember(prefs) { mutableStateOf(prefs.getString(SAVED_PRESETS_KEY, null)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == SAVED_PRESETS_KEY) raw = prefs.getString(SAVED_PRESETS_KEY, null)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        raw = prefs.getString(SAVED_PRESETS_KEY, null)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return remember(raw) {
        legacyMoveSymbol(runCatching { raw?.let { decodeSavedPresets(it).configs } }.getOrNull().orEmpty())
    }
}
