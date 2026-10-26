package com.mewname.app

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal enum class MissionPlacement {
    FREE, ABOVE, BELOW, BOTH;

    fun <T> split(pending: List<T>): Pair<List<T>, List<T>> = when (this) {
        FREE, BELOW -> emptyList<T>() to pending
        ABOVE -> pending to emptyList()
        BOTH -> pending.take((pending.size + 1) / 2) to pending.drop((pending.size + 1) / 2)
    }

    companion object {
        const val KEY = "overlay_placement"
        fun read(preferences: SharedPreferences): MissionPlacement =
            entries.firstOrNull { it.name == preferences.getString(KEY, FREE.name) } ?: FREE
    }
}

@Composable
internal fun MissionOverlayPreferences() {
    val context = LocalContext.current
    val prefs = remember { DailyMissionStore(context).preferences }
    val language = appLanguage()
    var placement by remember { mutableStateOf(MissionPlacement.read(prefs)) }
    var expanded by remember { mutableStateOf(false) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == MissionPlacement.KEY) placement = MissionPlacement.read(prefs)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun label(mode: MissionPlacement) = when (mode) {
        MissionPlacement.FREE -> lt(language, "Livre", "Free", "Libre")
        MissionPlacement.ABOVE -> lt(language, "Acima do botão", "Above the button", "Encima del botón")
        MissionPlacement.BELOW -> lt(language, "Abaixo do botão", "Below the button", "Debajo del botón")
        MissionPlacement.BOTH -> lt(language, "Acima e abaixo", "Above and below", "Encima y debajo")
    }
    fun save(mode: MissionPlacement) {
        placement = mode
        prefs.edit().putString(MissionPlacement.KEY, mode.name).apply()
    }
    AppToggleRow(
        label = lt(language, "Vincular ao botão de leitura", "Attach to the reading button", "Vincular al botón de lectura"),
        checked = placement != MissionPlacement.FREE,
        onCheckedChange = { save(if (it) MissionPlacement.ABOVE else MissionPlacement.FREE) }
    )
    if (placement != MissionPlacement.FREE) {
        Text(lt(language, "Posição das missões", "Mission position", "Posición de las misiones"),
            style = MaterialTheme.typography.titleSmall)
        Box {
            AppActionButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(label(placement))
                Spacer(Modifier.width(8.dp))
                Icon(AppIcons.Next, null, Modifier.size(18.dp).rotate(90f))
            }
            AppSelectionMenu(expanded, { expanded = false },
                MissionPlacement.entries.filter { it != MissionPlacement.FREE }, placement, ::label, ::save)
        }
    }
    Text(if (placement == MissionPlacement.FREE)
        lt(language, "Arraste a alça da caixa para mover as missões independentemente.",
            "Drag the panel handle to move missions independently.", "Arrastra el asa del panel para mover las misiones de forma independiente.")
    else lt(language, "As missões acompanham o botão de leitura. Em cima e embaixo, as pendentes são divididas; se houver uma missão extra, ela fica acima.",
        "Missions follow the reading button. Above and below splits pending missions, with any extra mission above.",
        "Las misiones siguen al botón de lectura. Encima y debajo divide las pendientes; la misión extra queda arriba."),
        style = MaterialTheme.typography.bodyMedium)
}
