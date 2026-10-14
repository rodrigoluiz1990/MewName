package com.mewname.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Each arc compares the base stat with the largest value for that stat in the catalog. */
@Composable
internal fun PokedexBaseStats(stats: List<Int?>, maximums: List<Int>) {
    val appearance = LocalAppAppearance.current
    val language = appLanguage()
    val labels = listOf("ATK", "DEF", "HP")
    val descriptions = listOf(
        lt(language, "Ataque", "Attack", "Ataque"),
        lt(language, "Defesa", "Defense", "Defensa"),
        lt(language, "Pontos de saúde", "Hit points", "Puntos de salud")
    )
    val colors = if (appearance.dark) listOf(Color(0xFF80BFDF), Color(0xFFCECA80), Color(0xFFF18DB5))
        else listOf(Color(0xFF306C8C), Color(0xFF555820), Color(0xFF881642))
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        labels.forEachIndexed { index, label ->
            val value = stats.getOrNull(index)
            val color = colors[index]
            Box(Modifier.weight(1f).height(104.dp)
                .clearAndSetSemantics { contentDescription = "${descriptions[index]}: ${value ?: "—"}" }) {
                Canvas(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(70.dp)
                    .padding(start = 12.dp, end = 12.dp, top = 14.dp)) {
                    val stroke = 7.dp.toPx()
                    val diameter = minOf(size.width - stroke, (size.height - stroke) * 2).coerceAtLeast(0f)
                    val origin = Offset((size.width - diameter) / 2, stroke / 2)
                    val arcSize = Size(diameter, diameter)
                    val style = Stroke(stroke, cap = StrokeCap.Round)
                    drawArc(color.copy(alpha = 0.18f), 180f, 180f, false, origin, arcSize, style = style)
                    if (value != null && value > 0) {
                        val fraction = (value.toFloat() / maximums.getOrElse(index) { 1 }.coerceAtLeast(1)).coerceIn(0f, 1f)
                        drawArc(color, 180f, 180f * fraction, false, origin, arcSize, style = style)
                    }
                }
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value?.toString() ?: "—", color = color,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(label, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}