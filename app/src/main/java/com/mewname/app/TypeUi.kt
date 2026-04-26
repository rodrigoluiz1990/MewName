package com.mewname.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mewname.app.domain.AppLanguage
import java.util.Locale

data class TypeVisualSpec(
    val key: String,
    val shortLabel: String,
    val color: Color,
    val labelEn: String,
    val labelPt: String,
    val labelEs: String
)

private val typeVisuals = listOf(
    TypeVisualSpec("Bug", "BG", Color(0xFF8CB230), "Bug", "Inseto", "Bicho"),
    TypeVisualSpec("Dark", "DK", Color(0xFF58575F), "Dark", "Sombrio", "Siniestro"),
    TypeVisualSpec("Dragon", "DR", Color(0xFF0F6AC0), "Dragon", "Dragao", "Dragon"),
    TypeVisualSpec("Electric", "EL", Color(0xFFEED535), "Electric", "Eletrico", "Electrico"),
    TypeVisualSpec("Fairy", "FY", Color(0xFFED6EC7), "Fairy", "Fada", "Hada"),
    TypeVisualSpec("Fighting", "FG", Color(0xFFD04164), "Fighting", "Lutador", "Lucha"),
    TypeVisualSpec("Fire", "FR", Color(0xFFFD7D24), "Fire", "Fogo", "Fuego"),
    TypeVisualSpec("Flying", "FL", Color(0xFF748FC9), "Flying", "Voador", "Volador"),
    TypeVisualSpec("Ghost", "GH", Color(0xFF556AAE), "Ghost", "Fantasma", "Fantasma"),
    TypeVisualSpec("Grass", "GR", Color(0xFF62B957), "Grass", "Planta", "Planta"),
    TypeVisualSpec("Ground", "GD", Color(0xFFDD7748), "Ground", "Terrestre", "Tierra"),
    TypeVisualSpec("Ice", "IC", Color(0xFF61CEC0), "Ice", "Gelo", "Hielo"),
    TypeVisualSpec("Normal", "NM", Color(0xFF9DA0AA), "Normal", "Normal", "Normal"),
    TypeVisualSpec("Poison", "PS", Color(0xFFA552CC), "Poison", "Venenoso", "Veneno"),
    TypeVisualSpec("Psychic", "PY", Color(0xFFEA5D60), "Psychic", "Psiquico", "Psiquico"),
    TypeVisualSpec("Rock", "RK", Color(0xFFBAAB82), "Rock", "Pedra", "Roca"),
    TypeVisualSpec("Steel", "ST", Color(0xFF417D9A), "Steel", "Aco", "Acero"),
    TypeVisualSpec("Water", "WT", Color(0xFF4A90DA), "Water", "Agua", "Agua")
).associateBy { it.key }

fun typeSpec(type: String?): TypeVisualSpec? = type?.let { typeVisuals[it] }

fun localizedTypeLabel(type: String?, language: AppLanguage): String {
    val spec = typeSpec(type) ?: return type ?: "-"
    return when (language) {
        AppLanguage.EN -> spec.labelEn
        AppLanguage.PT_BR -> spec.labelPt
        AppLanguage.ES -> spec.labelEs
    }
}

private fun typeIconAssetPath(type: String): String = "types/POKEMON_TYPE_${type.uppercase(Locale.US)}.png"

@Composable
fun TypeBadge(
    type: String,
    language: AppLanguage,
    modifier: Modifier = Modifier,
    showAssetIcon: Boolean = false,
    stacked: Boolean = false,
    inlinePrefix: String? = null,
    filled: Boolean = false
) {
    val spec = typeSpec(type) ?: return
    if (stacked) {
        Column(
            modifier = modifier
                .background(spec.color.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                .border(1.dp, spec.color.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showAssetIcon) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(spec.color.copy(alpha = 0.10f), RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AssetImageIcon(
                        assetPath = typeIconAssetPath(type),
                        contentDescription = localizedTypeLabel(type, language),
                        modifier = Modifier.size(18.dp),
                        fallbackSize = 18.dp
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(spec.color, RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        spec.shortLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                listOfNotNull(inlinePrefix?.takeIf { it.isNotBlank() }, localizedTypeLabel(type, language))
                    .joinToString(" "),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        val containerColor = if (filled) spec.color else spec.color.copy(alpha = 0.14f)
        val textColor = if (filled) Color.White else Color.Unspecified
        val iconBg = if (filled) Color.White.copy(alpha = 0.22f) else spec.color.copy(alpha = 0.10f)
        Row(
            modifier = modifier
                .background(containerColor, RoundedCornerShape(999.dp))
                .border(1.dp, spec.color.copy(alpha = if (filled) 0.0f else 0.45f), RoundedCornerShape(999.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            if (showAssetIcon) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(iconBg, RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AssetImageIcon(
                        assetPath = typeIconAssetPath(type),
                        contentDescription = localizedTypeLabel(type, language),
                        modifier = Modifier.size(18.dp),
                        fallbackSize = 18.dp
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(spec.color, RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        spec.shortLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(if (showAssetIcon) 8.dp else 6.dp))
            Text(
                listOfNotNull(inlinePrefix?.takeIf { it.isNotBlank() }, localizedTypeLabel(type, language))
                    .joinToString(" "),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TypeIcon(
    type: String,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val spec = typeSpec(type) ?: return
    Box(
        modifier = modifier
            .size(30.dp)
            .background(spec.color.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
            .border(1.dp, spec.color.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
            .padding(5.dp),
        contentAlignment = Alignment.Center
    ) {
        AssetImageIcon(
            assetPath = typeIconAssetPath(type),
            contentDescription = localizedTypeLabel(type, language),
            modifier = Modifier.size(18.dp),
            fallbackSize = 18.dp
        )
    }
}

@Composable
fun TypeStatBar(
    type: String,
    language: AppLanguage,
    text: String,
    modifier: Modifier = Modifier
) {
    val spec = typeSpec(type) ?: return
    Row(
        modifier = modifier
            .background(spec.color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 5.dp, height = 26.dp)
                .background(spec.color, RoundedCornerShape(999.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${localizedTypeLabel(type, language)} $text",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
