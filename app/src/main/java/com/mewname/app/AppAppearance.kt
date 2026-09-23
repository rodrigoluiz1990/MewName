package com.mewname.app

import android.app.Activity
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

internal enum class AppearanceStyle { GLASS, DARK, LIGHT }
internal enum class HomeLayout { GRID, COMPACT, ATMOSPHERIC }
internal data class AppAppearance(
    val style: AppearanceStyle = AppearanceStyle.GLASS,
    val compact: Boolean = false,
    val homeLayout: HomeLayout = if (compact) HomeLayout.COMPACT else HomeLayout.GRID,
    // Set only within the atmospheric Home and its dock, never persisted.
    val atmosphericSurface: Boolean = false
) {
    val dark get() = atmosphericSurface || style == AppearanceStyle.DARK
    val glass get() = atmosphericSurface || style == AppearanceStyle.GLASS
    val background get() = when (style) {
        AppearanceStyle.GLASS -> listOf(Color(0xFFF3ECFB), Color(0xFFEBEFFB), Color(0xFFF8EDF5))
        AppearanceStyle.DARK -> listOf(Color(0xFF18181E), Color(0xFF18181E))
        AppearanceStyle.LIGHT -> listOf(Color(0xFFF3F4F8), Color(0xFFF3F4F8))
    }
    val card get() = if (atmosphericSurface) listOf(
        Color(0xFFBAC5F3).copy(alpha = .19f),
        Color(0xFF909DCD).copy(alpha = .10f)
    ) else when (style) {
        AppearanceStyle.GLASS -> listOf(Color.White.copy(alpha = .80f), Color(0xFFE8E0F7).copy(alpha = .66f), Color.White.copy(alpha = .54f))
        AppearanceStyle.DARK -> listOf(Color(0xFF2D2D35), Color(0xFF2D2D35))
        AppearanceStyle.LIGHT -> listOf(Color.White, Color.White)
    }
    val text get() = if (dark) Color(0xFFF5F3FA) else Color(0xFF24212C)
    val border get() = if (atmosphericSurface) Color.White.copy(alpha = .22f) else if (dark) Color(0xFF494750) else if (glass) Color.White.copy(alpha = .65f) else Color(0xFFE2E2EA)
    val navigation get() = if (atmosphericSurface) listOf(Color(0xFF262E50).copy(alpha = .94f), Color(0xFF1A213C).copy(alpha = .98f)) else if (glass) listOf(Color(0xFFF6F0FF).copy(alpha = .96f), Color(0xFFE7DFF8).copy(alpha = .88f)) else card

    companion object {
        fun read(prefs: SharedPreferences) = AppAppearance(
            runCatching { AppearanceStyle.valueOf(prefs.getString("style", "GLASS").orEmpty()) }.getOrDefault(AppearanceStyle.GLASS),
            prefs.getBoolean("compact", false),
            runCatching { HomeLayout.valueOf(prefs.getString("layout", null).orEmpty()) }
                .getOrDefault(if (prefs.getBoolean("compact", false)) HomeLayout.COMPACT else HomeLayout.GRID)
        )
    }
}
internal val LocalAppAppearance = staticCompositionLocalOf { AppAppearance() }

@Composable
internal fun AppAppearanceTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("home_layout", 0) }
    var appearance by remember(prefs) { mutableStateOf(AppAppearance.read(prefs)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> appearance = AppAppearance.read(prefs) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val view = LocalView.current
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !appearance.dark
                isAppearanceLightNavigationBars = !appearance.dark
            }
        }
    }
    CompositionLocalProvider(LocalAppAppearance provides appearance, LocalAppButtonStyle provides true) {
        MaterialTheme(colorScheme = if (appearance.dark) darkColorScheme(background = appearance.background.first(), surface = appearance.background.first()) else lightColorScheme(background = appearance.background.first(), surface = appearance.background.first()),
            shapes = Shapes(
                small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
            ), content = content)
    }
}

@Composable
internal fun AppearancePreferences() {
    val appearance = LocalAppAppearance.current
    val language = appLanguage()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("home_layout", 0) }
    val changeLanguage = LocalLanguageChange.current
    Text(lt(language, "Idioma", "Language", "Idioma"), style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.mewname.app.domain.AppLanguage.entries.forEach { option ->
            AppChoiceChip(selected = language == option, onClick = { changeLanguage(option) },
                modifier = Modifier.weight(1f), label = {
                    Text(appLanguageFlag(option) + " " + when (option) {
                        com.mewname.app.domain.AppLanguage.PT_BR -> "PT"
                        com.mewname.app.domain.AppLanguage.EN -> "EN"
                        com.mewname.app.domain.AppLanguage.ES -> "ES"
                    })
                })
        }
    }
    HorizontalDivider()
    Text(lt(language, "Aparência", "Appearance", "Apariencia"), style = MaterialTheme.typography.titleMedium)
    Text(lt(language, "Estilo visual", "Visual style", "Estilo visual"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppearanceStyle.entries.forEach { style ->
            AppChoiceChip(
                modifier = Modifier.weight(1f),
                selected = appearance.style == style,
                onClick = { prefs.edit().putString("style", style.name).apply() },
                label = { Text(when (style) {
                    AppearanceStyle.GLASS -> "Glass"
                    AppearanceStyle.DARK -> lt(language, "Escuro", "Dark", "Oscuro")
                    AppearanceStyle.LIGHT -> lt(language, "Claro", "Light", "Claro")
                }) }
            )
        }
    }
    Text(lt(language, "Organização da tela principal", "Home screen arrangement", "Organización de inicio"))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HomeLayout.entries.forEach { layout ->
            AppChoiceChip(
                modifier = Modifier.fillMaxWidth(),
                selected = appearance.homeLayout == layout,
                onClick = {
                    prefs.edit().putString("layout", layout.name)
                        .putBoolean("compact", layout == HomeLayout.COMPACT).apply()
                },
                label = { Text(when (layout) {
                    HomeLayout.GRID -> lt(language, "Grade", "Grid", "Cuadrícula")
                    HomeLayout.COMPACT -> lt(language, "Lista compacta", "Compact list", "Lista compacta")
                    HomeLayout.ATMOSPHERIC -> lt(language, "Atmosférico", "Atmospheric", "Atmosférico")
                }) }
            )
        }
    }
    if (appearance.homeLayout == HomeLayout.ATMOSPHERIC) {
        Text(lt(language, "Céu por horário, com atalhos em vidro. O estilo das outras telas é mantido.",
            "Sky by time of day, with glass shortcuts. Other screens keep their style.",
            "Cielo según la hora, con accesos de cristal. Las otras pantallas conservan su estilo."),
            style = MaterialTheme.typography.bodySmall)
    }
    Text(lt(language, "Alterações salvas automaticamente.", "Changes saved automatically.", "Cambios guardados automáticamente."),
        style = MaterialTheme.typography.bodySmall)
    HorizontalDivider()
    LogOptionsPreference()
}