package com.mewname.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mewname.app.domain.AppLanguage

internal fun greetingForHour(hour: Int, language: AppLanguage): String = when (hour) {
    in 5..11 -> lt(language, "Bom dia", "Good morning", "Buenos días")
    in 12..17 -> lt(language, "Boa tarde", "Good afternoon", "Buenas tardes")
    else -> lt(language, "Boa noite", "Good evening", "Buenas noches")
}

@Composable
internal fun HomeGreeting() {
    val language = appLanguage()
    var hour by remember { mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) }
    LaunchedEffect(Unit) {
        while (true) {
            hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            kotlinx.coroutines.delay(30_000)
        }
    }
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 22.dp, end = 22.dp, top = 36.dp, bottom = 18.dp)) {
        Text(greetingForHour(hour, language) + "!",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
            color = LocalAppAppearance.current.text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
) {
    CenterAlignedTopAppBar(
        title = { ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) { title() } },
        modifier = modifier, navigationIcon = navigationIcon, actions = actions, colors = colors,
        windowInsets = if (LocalProfilePanel.current) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets
    )
}