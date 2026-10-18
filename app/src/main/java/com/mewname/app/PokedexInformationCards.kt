package com.mewname.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mewname.app.domain.*
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

private fun infoNumber(value: Double, language: AppLanguage): String = NumberFormat.getNumberInstance(
    when (language) { AppLanguage.PT_BR -> Locale.forLanguageTag("pt-BR"); AppLanguage.ES -> Locale.forLanguageTag("es"); else -> Locale.US }
).apply { maximumFractionDigits = 3 }.format(value)

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
}

@Composable
internal fun PokedexInformationCard(entry: PokedexCatalogEntry, info: JSONObject?, candyColors: Pair<Int, Int>? = null) {
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    val unknown = tr("Não disponível", "Unavailable", "No disponible")
    fun release(field: String) = if (info?.has(field) == true) {
        if (info.optBoolean(field)) tr("Sim", "Yes", "Sí") else tr("Não", "No", "No")
    } else tr("Sem confirmação", "Unconfirmed", "Sin confirmar")
    fun number(key: String, unit: String): String = info?.takeIf { it.has(key) }?.optDouble(key)
        ?.takeIf { it.isFinite() }?.let { infoNumber(it, language) + unit } ?: unknown
    fun allowed(key: String, inverted: Boolean = false): String = if (info?.has(key) == true)
        if (info.optBoolean(key) != inverted) tr("Permitido", "Allowed", "Permitido") else tr("Não permitido", "Not allowed", "No permitido") else unknown
    val generation = when (entry.number) { in 1..151 -> 1; in 152..251 -> 2; in 252..386 -> 3; in 387..493 -> 4; in 494..649 -> 5; in 650..721 -> 6; in 722..809 -> 7; in 810..905 -> 8; in 906..1025 -> 9; else -> null }
    val region = when {
        entry.name.contains("Hisui", true) || entry.number in 899..905 -> "Hisui"
        entry.name.contains("Alola", true) -> "Alola"
        entry.name.contains("Galar", true) -> "Galar"
        entry.name.contains("Paldea", true) -> "Paldea"
        else -> mapOf(1 to "Kanto", 2 to "Johto", 3 to "Hoenn", 4 to "Sinnoh", 5 to "Unova", 6 to "Kalos", 7 to "Alola", 8 to "Galar", 9 to "Paldea")[generation]
    }
    AppSectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(tr("Informações da Pokédex", "Pokédex information", "Información de Pokédex"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            InfoRow(tr("Número nacional", "National number", "Número nacional"), "#${entry.number}")
            InfoRow(tr("Altura média", "Average height", "Altura media"), number("pokedexHeightM", " m"))
            InfoRow(tr("Peso médio", "Average weight", "Peso medio"), number("pokedexWeightKg", " kg"))
            InfoRow(tr("Região", "Region", "Región"), region ?: unknown)
            InfoRow(tr("Geração da espécie", "Species generation", "Generación de la especie"), generation?.toString() ?: unknown)
            InfoRow(tr("Troca", "Trading", "Intercambio"), allowed("isTradable"))
            InfoRow(tr("Distância por doce", "Buddy candy distance", "Distancia por caramelo"), number("kmBuddyDistance", " km"))
            InfoRow(tr("Lançado no GO", "Released in GO", "Lanzado en GO"), release("released"))
            InfoRow(tr("Brilhante lançado", "Shiny released", "Variocolor lanzado"), release("shinyReleased"))
            InfoRow(tr("Transferência para HOME", "HOME transfer", "Transferencia a HOME"), allowed("disableTransferToPokemonHome", true))
            if (info?.optBoolean("disableTransferToPokemonHomeIfShiny") == true)
                InfoRow(tr("HOME — brilhante", "HOME — shiny", "HOME — variocolor"), tr("Não permitido", "Not allowed", "No permitido"))
            InfoRow(tr("Taxa base de fuga", "Base flee rate", "Tasa base de huida"), info?.takeIf { it.has("baseFleeRate") }?.let { infoNumber(it.getDouble("baseFleeRate") * 100, language) + "%" } ?: unknown)
            val candy = tr("doces", "Candy", "caramelos")
            val dust = tr("poeira estelar", "Stardust", "polvo estelar")
            ResourceInfoRow(tr("Recompensa base de captura", "Base catch rewards", "Recompensa base de captura")) {
                if (info?.has("catchCandy") == true) {
                    ResourceAmount(infoNumber(info.optDouble("catchCandy"), language), candy) { PokedexCandyIcon(candyColors) }
                    ResourceAmount(infoNumber(info.optDouble("catchStardust"), language), dust) { AssetImageIcon("mission_icons/stardust_painted.png", null, Modifier.size(22.dp)) }
                } else Text(unknown)
            }
            val cost = info?.optJSONObject("thirdMove")
            ResourceInfoRow(tr("Segundo ataque carregado", "Second charged move", "Segundo ataque cargado")) {
                if (cost != null) {
                    ResourceAmount(infoNumber(cost.optDouble("candyToUnlock"), language), candy) { PokedexCandyIcon(candyColors) }
                    ResourceAmount(infoNumber(cost.optDouble("stardustToUnlock"), language), dust) { AssetImageIcon("mission_icons/stardust_painted.png", null, Modifier.size(22.dp)) }
                } else Text(unknown)
            }
            ResourceInfoRow(tr("Megaenergia por doce de caminhada", "Mega Energy per walking candy", "Megaenergía por caramelo al caminar")) {
                if (info?.has("buddyWalkedMegaEnergyAward") == true)
                    ResourceAmount(number("buddyWalkedMegaEnergyAward", ""), tr("Megaenergia", "Mega Energy", "Megaenergía")) { PokedexMegaEnergyIcon(null) }
                else Text(unknown)
            }
        }
    }
}

@Composable
internal fun PokedexSizeCard(info: JSONObject?, sprite: String?) {
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    val bounds = pokemonSizeBounds(info)
    val context = LocalContext.current
    val spriteRequest = remember(context, sprite) {
        ImageRequest.Builder(context).data(sprite).transformations(CollectionSpriteTrim).build()
    }
    AppSectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Tamanhos", "Sizes", "Tamaños"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (bounds.isEmpty()) {
                Text(tr("Limites de tamanho indisponíveis para esta forma.", "Size bounds unavailable for this form.", "Límites de tamaño no disponibles para esta forma."))
            } else {
                val mean = info?.optDouble("pokedexHeightM", Double.NaN)?.takeIf { it.isFinite() && it > 0 }
                val heights = listOf(bounds[0].minimum, bounds[1].minimum, mean ?: (bounds[2].minimum + bounds[2].maximum) / 2, bounds[3].maximum, bounds[4].maximum)
                val scale = 100.0 / maxOf(heights.max(), 1.7)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    heights.forEachIndexed { index, height ->
                        Column(Modifier.width(104.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(bounds[index].label, style = MaterialTheme.typography.labelLarge)
                            Box(Modifier.height(110.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                AsyncImage(spriteRequest, null, Modifier.size((height * scale).toFloat().dp))
                            }
                            Text(infoNumber(height, language) + " m", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Column(Modifier.width(104.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Willow", style = MaterialTheme.typography.labelLarge)
                        Box(Modifier.height(110.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            // Billboard textures are packed wider than their in-game display proportions.
                            AsyncImage(
                                model = "file:///android_asset/pokedex_icons/professor_willow.png",
                                contentDescription = tr("Professor Willow", "Professor Willow", "Profesor Willow"),
                                modifier = Modifier.height((1.7 * scale).toFloat().dp)
                                    .width((0.95 * scale).toFloat().dp),
                                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds
                            )
                        }
                        Text(tr("Ref. ", "Ref. ", "Ref. ") + infoNumber(1.7, language) + " m", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text(tr("Limites de altura", "Height bounds", "Límites de altura"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth()) {
                    Text(tr("Tamanho", "Size", "Tamaño"), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                    Text(tr("Mínimo", "Minimum", "Mínimo"), Modifier.weight(1f).padding(end = 8.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium)
                    Text(tr("Máximo", "Maximum", "Máximo"), Modifier.weight(1f).padding(start = 8.dp), textAlign = TextAlign.Start, style = MaterialTheme.typography.labelMedium)
                }
                bounds.forEach { bound ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                    Row(Modifier.fillMaxWidth()) {
                        Text(bound.label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                        Text(infoNumber(bound.minimum, language) + " m", Modifier.weight(1f).padding(end = 8.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
                        Text(infoNumber(bound.maximum, language) + " m", Modifier.weight(1f).padding(start = 8.dp), textAlign = TextAlign.Start, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResourceInfoRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
}

@Composable
private fun ResourceAmount(amount: String, label: String, icon: @Composable () -> Unit) {
    Row(Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = .08f), RoundedCornerShape(8.dp))
        .padding(horizontal = 5.dp, vertical = 3.dp)
        .semantics(mergeDescendants = true) { contentDescription = "$amount $label" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        icon()
        Text(amount, style = MaterialTheme.typography.labelMedium)
    }
}
