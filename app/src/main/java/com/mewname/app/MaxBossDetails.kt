package com.mewname.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mewname.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

internal fun maxBossPokemon(metadata: RaidMetadata, name: String): RaidPokemon? {
    val id = maxPortraitId(name)
    return metadata.resolve(id) ?: metadata.resolve(id.removeSuffix("_GIGANTAMAX"))
}

internal fun maxBossUrl(id: String): String {
    val maxId = if (id.endsWith("_GIGANTAMAX")) "GIGANTAMAX_" + id.removeSuffix("_GIGANTAMAX")
        else "DYNAMAX_" + id
    return "https://www.pokebattler.com/max/" + maxId
}

internal fun maxCatalogMoves(pokemon: RaidPokemon, fast: Boolean): List<String> =
    (if (fast) pokemon.fast else pokemon.charged)
        .filterNot { it in pokemon.elite || it in setOf("RETURN", "FRUSTRATION") }.distinct()

private data class MaxBossInfo(
    val boss: RaidPokemon,
    val capture: RaidPokemon?,
    val metadata: RaidMetadata,
    val weaknesses: List<TypeMatchupEntry>,
    val moveNames: Map<String, String>
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MaxBossDetails(name: String?) {
    val context = LocalContext.current
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    val result by produceState<Result<MaxBossInfo>?>(null, name, language) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                val metadata = RaidCounterRepository(context.applicationContext).metadata()
                val boss = requireNotNull(name?.let { maxBossPokemon(metadata, it) })
                val localized = GameTextRepository.moveTranslations(context.applicationContext, language)
                val labels = listOf(AssetPaths.FAST_MOVES, AssetPaths.CHARGED_MOVES).flatMap { path ->
                    val array = JSONArray(context.assets.open(path).bufferedReader().use { it.readText() })
                    (0 until array.length()).map { index ->
                        val move = array.getJSONObject(index)
                        raidKey(move.getString("name")) to (localized[move.getInt("move_id")] ?: move.getString("name"))
                    }
                }.toMap()
                // Gigantamax shares capture stats with the underlying species; preserve regional forms.
                val captureId = raidCaptureId(boss.id.removeSuffix("_GIGANTAMAX"))
                MaxBossInfo(boss, captureId?.let(metadata::resolve), metadata,
                    GameInfoRepository.matchupAgainst(context.applicationContext, boss.types).filter { it.multiplier > 1 },
                    labels)
            }
        }
    }
    val info = result?.getOrNull()
    if (result == null) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        return
    }
    if (info == null) {
        Text(tr("Detalhes deste chefe indisponíveis.", "Boss details unavailable.", "Detalles del jefe no disponibles."),
            style = MaterialTheme.typography.bodySmall)

        return
    }
    Text(tr("Golpes possíveis", "Possible moves", "Ataques posibles"), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(true, false).forEach { fast ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (fast) tr("Rápidos", "Fast", "Rápidos") else tr("Carregados", "Charged", "Cargados"),
                    style = MaterialTheme.typography.labelLarge)
                val moves = maxCatalogMoves(info.boss, fast)
                if (moves.isEmpty()) Text("—")
                moves.forEach { id ->
                    RaidMoveRow(info.metadata.move(id), info.moveNames[raidKey(id)] ?: raidName(id.removeSuffix("_FAST")))
                }
            }
        }
    }
    Text(tr("Golpes do catálogo da espécie; a combinação usada nesta Batalha Max pode variar.",
        "Species catalog moves; this Max Battle's moveset may vary.",
        "Ataques del catálogo de la especie; la combinación de este Combate Max puede variar."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(tr("Fraquezas", "Weaknesses", "Debilidades"), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        info.weaknesses.forEach { TypeBadge(it.attackType, language, showAssetIcon = true, inlinePrefix = String.format(java.util.Locale.US, "%.2f×", it.multiplier)) }
    }
    Text(tr("PC de captura", "Catch CP", "PC de captura"), style = MaterialTheme.typography.titleSmall)
    val capture = info.capture
    if (capture != null && capture.stamina > 0 && capture.attack > 0 && capture.defense > 0) {
        RaidCpRow("", "67%", "100%")
        RaidCpRow(tr("Normal · Nv. 20", "Normal · Lv. 20", "Normal · Nv. 20"),
            raidCp(capture, 10, false).toString(), raidCp(capture, 15, false).toString())
        Text(tr("Referência: IVs 10/10/10 e 15/15/15, nível 20.",
            "Reference: 10/10/10 and 15/15/15 IVs, level 20.",
            "Referencia: IV 10/10/10 y 15/15/15, nivel 20."), style = MaterialTheme.typography.bodySmall)
    } else Text(tr("Faixa de captura indisponível.", "Catch range unavailable.", "Intervalo de captura no disponible."))
}
