package com.mewname.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mewname.app.domain.*
import org.json.JSONArray
import kotlin.math.abs

/** Main-app presentation only. Overlay move controls deliberately use their existing layout. */
internal fun loadMoveEffects(context: android.content.Context): Map<Int, Map<String, Double>> = buildMap {
    for (path in listOf("catalogs/pvp_fast_moves.json", "catalogs/pvp_charged_moves.json")) {
        val rows = JSONArray(context.assets.open(path).bufferedReader().use { it.readText() })
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val buffs = row.optJSONObject("buffs") ?: continue
            put(row.getInt("move_id"), buffs.keys().asSequence().associateWith { buffs.getDouble(it) })
        }
    }
}

@Composable
internal fun MainMoveCard(entry: MoveCatalogEntry, language: AppLanguage, effects: Map<String, Double>) {
    fun tr(pt: String, en: String, es: String) = when(language) {
        AppLanguage.PT_BR -> pt; AppLanguage.EN -> en; AppLanguage.ES -> es
    }
    var expanded by rememberSaveable(entry.moveId, entry.name) { mutableStateOf(false) }
    val fast = entry.category == MoveCategory.FAST
    val stages = effects.filterKeys { it.endsWith("stat_stage_change") }.filterValues { it != 0.0 }
    val positive = stages.values.any { it > 0 }
    val negative = stages.values.any { it < 0 }
    val effectLabel = when { positive && negative -> "Boost/Debuff"; positive -> "Boost"; negative -> "Debuff"; else -> null }
    val weather = when(entry.type) {
        "Grass", "Fire", "Ground" -> tr("☀ Sol", "☀ Sunny", "☀ Sol")
        "Water", "Electric", "Bug" -> tr("☂ Chuva", "☂ Rain", "☂ Lluvia")
        "Normal", "Rock" -> tr("⛅ Parcial", "⛅ Partly", "⛅ Parcial")
        "Fairy", "Fighting", "Poison" -> tr("☁ Nublado", "☁ Cloudy", "☁ Nublado")
        "Dragon", "Flying", "Psychic" -> tr("≋ Vento", "≋ Windy", "≋ Viento")
        "Ice", "Steel" -> tr("❄ Neve", "❄ Snow", "❄ Nieve")
        "Dark", "Ghost" -> tr("≋ Névoa", "≋ Fog", "≋ Niebla")
        else -> "—"
    }
    val shape = RoundedCornerShape(16.dp)
    val tint = typeSpec(entry.type)?.color ?: MaterialTheme.colorScheme.primary
    val glass = MaterialTheme.colorScheme.surface.copy(alpha = .72f)
    Column(Modifier.fillMaxWidth().clip(shape)
        .background(tint.copy(alpha = .24f)).border(1.dp, tint.copy(alpha = .55f), shape)
        .padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val category = if(fast) tr("Rápido", "Fast", "Rápido") else tr("Carregado", "Charged", "Cargado")
            Text("${entry.localizedName(language)} ($category)", Modifier.weight(1f), fontWeight = FontWeight.Bold,
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            effectLabel?.let { label ->
                Row(Modifier.clip(RoundedCornerShape(5.dp)).background(glass)
                    .semantics { stateDescription = if(expanded) tr("Expandido", "Expanded", "Expandido") else tr("Recolhido", "Collapsed", "Contraído") }
                    .clickable(role = Role.Button, onClickLabel = if(expanded)
                        tr("Ocultar efeito", "Hide effect", "Ocultar efecto") else
                        tr("Mostrar efeito", "Show effect", "Mostrar efecto")) { expanded = !expanded }
                    .padding(horizontal = 5.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(label, fontSize = 9.sp)
                    Text(if(expanded) "⌃" else "⌄", fontSize = 13.sp)
                }
            }
            Text(weather, Modifier.background(glass, RoundedCornerShape(7.dp)).padding(4.dp), fontSize = 10.sp)
            AssetImageIcon("types/POKEMON_TYPE_${entry.type.uppercase(java.util.Locale.ROOT)}.png",
                localizedTypeLabel(entry.type, language), Modifier.size(20.dp), fallbackSize = 20.dp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MoveMetricsPill("PvE", entry.power, entry.energyDelta, entry.duration?.let { "${it / 1000.0}s" }, Modifier.weight(1f))
            MoveMetricsPill("PvP", entry.pvpPower, entry.pvpEnergyDelta,
                if(fast) entry.pvpTurnDuration?.let { "${it}t" } else null, Modifier.weight(1f))
        }
        if(expanded && stages.isNotEmpty()) {
                Text(tr("Efeito PvP", "PvP effect", "Efecto PvP"), fontWeight = FontWeight.SemiBold)
                stages.forEach { (key, change) ->
                    val who = if(key.startsWith("attacker")) tr("Usuário", "User", "Usuario") else tr("Adversário", "Opponent", "Rival")
                    val stat = if(key.contains("attack_stat")) tr("ataque", "attack", "ataque") else tr("defesa", "defense", "defensa")
                    Text("$who · $stat: ${if(change>0) "+" else ""}${change.toInt()}", style = MaterialTheme.typography.bodySmall)
                }
                effects["activation_chance"]?.let {
                    Text(tr("Chance: ", "Chance: ", "Probabilidad: ") + "${(it*100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
        }
    }
}

@Composable
private fun MoveMetricsPill(label: String, power: Int?, energy: Int?, duration: String?, modifier: Modifier) {
    val pve = label == "PvE"
    @Composable fun Emblem() {
        AssetImageIcon(if(pve) "moves/pve.png" else "moves/pvp.png", label,
            Modifier.size(23.dp), fallbackSize = 23.dp,
            tint = if(pve) Color(0xFFE52028) else null)
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        if(pve) Emblem()
        Row(Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .78f))
            .height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly) {
            val language = appLanguage()
            fun tr(pt: String, en: String, es: String) = when(language) {
                AppLanguage.PT_BR -> pt; AppLanguage.EN -> en; AppLanguage.ES -> es
            }
            val values = listOf(
                Triple(R.drawable.ic_move_sword, "${power ?: "—"}", tr("Dano", "Damage", "Daño")),
                Triple(R.drawable.ic_move_bolt, "${energy?.let { abs(it) } ?: "—"}", tr("Energia", "Energy", "Energía"))
            ) + listOfNotNull(duration?.let { Triple(R.drawable.ic_move_clock, it, tr("Duração", "Duration", "Duración")) })
            values.forEachIndexed { index, (icon, value, description) ->
                if(index > 0) Box(Modifier.fillMaxHeight().width(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .14f)))
                Row(Modifier.padding(horizontal = 3.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(painterResource(icon), description,
                        Modifier.height(11.dp).width(if(icon == R.drawable.ic_move_bolt) 7.dp else 11.dp),
                        tint = MaterialTheme.colorScheme.onSurface)
                    Text(value, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        if(!pve) Emblem()
    }
}
