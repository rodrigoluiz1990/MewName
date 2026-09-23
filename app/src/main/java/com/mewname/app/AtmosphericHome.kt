package com.mewname.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mewname.app.domain.AppLanguage
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/** This palette is scoped to Home; the selected app appearance remains untouched. */
@Composable
internal fun AtmosphericHomeTheme(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val appearance = LocalAppAppearance.current.copy(atmosphericSurface = true)
    CompositionLocalProvider(LocalAppAppearance provides appearance, LocalContentColor provides appearance.text) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFFD3C4FF),
                background = Color(0xFF151B32),
                surface = Color(0xFF252D49),
                onSurface = Color(0xFFF5F3FA),
                onSurfaceVariant = Color(0xFFCED3E6)
            ),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content
        )
    }
}

@Composable
private fun atmosphericTime(): LocalDateTime {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val time by produceState(LocalDateTime.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = LocalDateTime.now()
                delay(30_000)
            }
        }
    }
    return time
}

/** Offline, decorative sky. It represents time of day, not a weather forecast. */
@Composable
internal fun AtmosphericSky(modifier: Modifier = Modifier) {
    val hour = atmosphericTime().hour
    val night = hour < 5 || hour >= 19
    val colors = when (hour) {
        in 5..7 -> listOf(Color(0xFF333655), Color(0xFF9B788A), Color(0xFF353D61))
        in 8..16 -> listOf(Color(0xFF25486C), Color(0xFF6387AA), Color(0xFF354465))
        in 17..18 -> listOf(Color(0xFF343151), Color(0xFF9C667F), Color(0xFF393655))
        else -> listOf(Color(0xFF0D142B), Color(0xFF222C4C), Color(0xFF393957))
    }
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(colors))
        val light = Offset(size.width * .78f, size.height * .16f)
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFDFDCFF).copy(alpha = if (night) .12f else .19f), Color.Transparent),
                center = light, radius = size.width * .6f
            ), radius = size.width * .6f, center = light
        )
        if (night) {
            // Stable positions avoid flicker or continuous animation work.
            repeat(22) { index ->
                val x = ((index * 137 + 29) % 997) / 997f
                val y = ((index * 73 + 17) % 491) / 491f
                drawCircle(Color.White.copy(alpha = .18f + (index % 3) * .12f),
                    radius = if (index % 4 == 0) 1.1.dp.toPx() else .65.dp.toPx(),
                    center = Offset(x * size.width, (.025f + y * .30f) * size.height))
            }
            drawCircle(Color(0xFFE9E8FA).copy(alpha = .84f), 15.dp.toPx(), light)
            drawCircle(colors.first(), 13.dp.toPx(), light + Offset(7.dp.toPx(), -4.dp.toPx()))
        }
        // Soft cloud banks from radial gradients; no bitmap downloads or live blur.
        repeat(7) { index ->
            val center = Offset(
                size.width * (-.15f + (index % 4) * .40f),
                size.height * (.16f + index * .036f)
            )
            val width = size.width * .95f
            val height = size.height * .13f
            drawOval(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFD6DDF5).copy(alpha = if (night) .065f else .12f), Color.Transparent),
                    center = center, radius = width * .52f
                ),
                topLeft = center - Offset(width / 2, height / 2),
                size = Size(width, height)
            )
        }
        // Keep text and glass surfaces readable across all four sky palettes.
        drawRect(Brush.verticalGradient(
            0f to Color.Transparent,
            .25f to Color(0xFF11172C).copy(alpha = .12f),
            .65f to Color(0xFF151B32).copy(alpha = .86f),
            1f to Color(0xFF151B32)
        ))
    }
}

@Composable
internal fun HomeContentLayout(
    padding: PaddingValues,
    analysis: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val atmospheric = LocalAppAppearance.current.homeLayout == HomeLayout.ATMOSPHERIC
    key(atmospheric) {
        if (!atmospheric) {
            Column(
                Modifier.padding(top = padding.calculateTopPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp,
                        bottom = padding.calculateBottomPadding() + 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                content()
                analysis()
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                val viewportHeight = maxHeight
                val bottomGap = 12.dp + padding.calculateBottomPadding()
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = bottomGap)
                ) {
                    // The menu ends just above the dock. On small screens / large fonts
                    // this minimum height may grow naturally and the whole page scrolls.
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = (viewportHeight - bottomGap).coerceAtLeast(0.dp)),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        AtmosphericGreeting(Modifier.fillMaxWidth().padding(top = 24.dp))
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp),
                            content = content)
                    }
                    analysis()
                }
            }
        }
    }
}

internal data class HomeMenuAction(val icon: String, val title: String, val onClick: () -> Unit)

private val AtmosphericGroups = listOf(
    listOf("calendar", "promo", "names", "filters"),
    listOf("pokedex", "collections", "types"),
    listOf("raid", "rocket", "research", "eggs"),
    listOf("adventure", "legacy", "moves")
)

@Composable
internal fun AtmosphericActionGroups(actions: List<HomeMenuAction>) {
    val byIcon = actions.associateBy { it.icon }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val itemWidth = ((maxWidth - 12.dp) / 4).coerceAtMost(88.dp)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AtmosphericGroups.forEach { group ->
            val shape = RoundedCornerShape(22.dp)
            Row(
                Modifier.fillMaxWidth().clip(shape)
                    .background(Brush.linearGradient(LocalAppAppearance.current.card))
                    .border(.75.dp, LocalAppAppearance.current.border, shape)
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                group.forEach { icon ->
                    val action = byIcon.getValue(icon)
                    Column(
                        Modifier.width(itemWidth).fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button, onClick = action.onClick)
                            .heightIn(min = 70.dp)
                            .padding(horizontal = 3.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Top)
                    ) {
                        Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
                            HomeGlassMenuIcon(action.icon, iconSize = 30.dp)
                        }
                        Text(
                            action.title.lineSequence().joinToString(" "),
                            color = LocalAppAppearance.current.text,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
    }
}
@Composable
private fun AtmosphericGreeting(modifier: Modifier = Modifier) {
    val time = atmosphericTime()
    val language = appLanguage()
    val locale = when (language) {
        AppLanguage.PT_BR -> Locale.forLanguageTag("pt-BR")
        AppLanguage.EN -> Locale.ENGLISH
        AppLanguage.ES -> Locale.forLanguageTag("es")
    }
    val date = time.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
    Column(modifier.padding(start = 8.dp, end = 8.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.Bottom) {
        Text(greetingForHour(time.hour, language) + "!",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Light,
            color = Color(0xFFF5F3FA))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(date, style = MaterialTheme.typography.bodySmall, color = Color(0xFFD0D6EA),
                modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            HomeTemperature()
        }
    }
}

/** Account for panel padding and larger fonts instead of fixed percentage widths. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeActionsGrid(compact: Boolean, content: @Composable FlowRowScope.(Float) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val columns = if (compact) 1 else if (maxWidth < 300.dp || fontScale > 1.3f) 2 else 3
        val tileWidth = ((maxWidth - 12.dp * (columns - 1)) / columns / maxWidth - .001f).coerceIn(.01f, 1f)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = columns
        ) { content(if (compact) 1f else tileWidth) }
    }
}