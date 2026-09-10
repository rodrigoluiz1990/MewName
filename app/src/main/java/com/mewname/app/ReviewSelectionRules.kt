package com.mewname.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mewname.app.domain.*
import com.mewname.app.model.*
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt
@Composable
internal fun ReviewSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
}

internal val LocalHideReviewLogMarkers = androidx.compose.runtime.staticCompositionLocalOf { false }
internal val LocalFieldLogMarker = androidx.compose.runtime.staticCompositionLocalOf<(@Composable (String) -> Unit)?> { null }
@Composable
internal fun FieldLabelRow(
    label: String,
    trailing: (@Composable (() -> Unit))? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (LocalGlassReviewStyle.current) GlassLabelHeight else 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (LocalGlassReviewStyle.current) FontWeight.Normal else FontWeight.SemiBold)
        }
        if (trailing != null) trailing() else LocalFieldLogMarker.current?.invoke(label)
    }
}

@Composable
internal fun FieldHeaderRow(
    label: String,
    selected: Boolean,
    onMarkerClick: () -> Unit
) {
    FieldLabelRow(
        label = label,
        trailing = {
            UnownHeaderIcon(
                selected = selected,
                onClick = onMarkerClick,
                contentDescription = "Selecionar log de $label"
            )
        }
    )
}

@Composable
internal fun UnownHeaderIcon(
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String
) {
    if (LocalHideReviewLogMarkers.current) return
    IconButton(onClick = onClick, modifier = Modifier.size(20.dp)) {
        UnownQuestionIcon(
            selected = selected,
            modifier = Modifier.size(16.dp),
            contentDescription = contentDescription
        )
    }
}

internal data class WeightedToggleItem(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit
)

internal fun Float.formatDebug(): String = String.format("%.3f", this)
internal fun Double.formatDebugDouble(): String = String.format("%.3f", this)
internal fun Double.formatLevelDebug(): String = if (this % 1.0 == 0.0) this.toInt().toString() else this.toString().replace(".", ",")

internal fun leagueDisplayName(league: PvpLeague): String {
    return when (league) {
        PvpLeague.LITTLE -> "Little League"
        PvpLeague.GREAT -> "Great League"
        PvpLeague.ULTRA -> "Ultra League"
        PvpLeague.MASTER -> "Master League"
    }
}

internal fun leagueShortName(league: PvpLeague): String {
    return when (league) {
        PvpLeague.LITTLE -> "CP"
        PvpLeague.GREAT -> "GL"
        PvpLeague.ULTRA -> "UL"
        PvpLeague.MASTER -> "ML"
    }
}

internal fun sizeDisplayName(size: PokemonSize): String {
    return when (size) {
        PokemonSize.XXS -> "XXS"
        PokemonSize.XS -> "XS"
        PokemonSize.NORMAL -> "Normal"
        PokemonSize.XL -> "XL"
        PokemonSize.XXL -> "XXL"
    }
}

internal fun PokemonScreenData.displayTypes(): String {
    return listOfNotNull(type1, type2).joinToString("/").ifBlank { "" }
}

internal fun parseTypesForReview(value: String): List<String> {
    return value.split("/", ",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { it.uppercase() }
        .distinct()
        .take(2)
}

internal data class PvpRankCard(
    val pokemonName: String,
    val league: PvpLeague,
    val rank: Int?,
    val eligible: Boolean,
    val value: String,
    val label: String,
    val struckThrough: Boolean = false
)

internal fun speciesRankCardsForLeague(
    context: Context,
    speciesRanks: List<PvpSpeciesRankInfo>,
    selectedLeague: PvpLeague?
): List<PvpRankCard> {
    if (speciesRanks.isEmpty()) return emptyList()
    val orderedSpecies = speciesRanks.map { it.pokemonName }.distinct()
    val familyOrder = orderedSpecies.withIndex().associate { it.value to it.index }
    val stageOrder = loadReviewEvolutionStageOrder(context)
    return orderedSpecies.mapNotNull { pokemonName ->
        val ranksForSpecies = speciesRanks.filter { it.pokemonName == pokemonName }
        val best = if (selectedLeague != null) {
            ranksForSpecies.firstOrNull { it.league == selectedLeague }
        } else {
            ranksForSpecies
                .filter { it.eligible && it.rank != null }
                .minWithOrNull(compareBy<PvpSpeciesRankInfo> { it.rank ?: Int.MAX_VALUE }.thenBy { it.league.ordinal })
                ?: ranksForSpecies.firstOrNull()
        } ?: return@mapNotNull null
        PvpRankCard(
            pokemonName = pokemonName,
            league = best.league,
            rank = best.rank,
            eligible = best.eligible,
            label = pokemonDisplayName(pokemonName),
            value = if (best.eligible && best.rank != null) {
                if (selectedLeague != null) "Rank ${best.rank}" else "${leagueShortName(best.league)} • ${best.rank}"
            } else {
                leagueShortName(best.league)
            },
            struckThrough = !best.eligible
        )
    }.sortedWith(
        compareBy<PvpRankCard> { reviewEvolutionStageRank(stageOrder, it.pokemonName) }
            .thenBy { familyOrder[it.pokemonName] ?: Int.MAX_VALUE }
            .thenBy { it.label }
    )
}

internal fun moveDropdownOptions(
    moves: List<PokemonMove>,
    legacyMoveSymbol: String
): List<ReviewPokemonMoveOption> {
    return moves.map { move ->
        ReviewPokemonMoveOption(
            value = move.name,
            label = if (move.legacy && legacyMoveSymbol.isNotBlank()) {
                "${move.localizedName} $legacyMoveSymbol"
            } else {
                move.localizedName
            }
        )
    }
}

internal fun loadReviewEvolutionStageOrder(context: Context): Map<String, Int> {
    reviewEvolutionStageOrderCache?.let { return it }
    return try {
        val jsonArray = JSONArray(
            context.assets.open(AssetPaths.POKEMON_EVOLUTION_STAGES).bufferedReader().use { it.readText() }
        )
        buildMap {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                val normalizedName = normalizeReviewKey(item.optString("name"))
                val order = when (item.optString("stage").uppercase(Locale.US)) {
                    "BABY" -> 0
                    "BASIC" -> 1
                    "STAGE1" -> 2
                    "STAGE2" -> 3
                    else -> 4
                }
                if (normalizedName.isNotBlank()) {
                    put(normalizedName, order)
                }
            }
        }.also { reviewEvolutionStageOrderCache = it }
    } catch (_: Exception) {
        emptyMap()
    }
}

internal fun reviewEvolutionStageRank(stageOrder: Map<String, Int>, pokemonName: String): Int {
    val normalizedName = normalizeReviewKey(pokemonName)
    return stageOrder[normalizedName] ?: 4
}

internal data class PowerUpCost(
    val stardust: Int,
    val candy: Int
)

private val reviewPowerUpCosts = listOf(
    1.0 to PowerUpCost(200, 1),
    3.0 to PowerUpCost(400, 1),
    5.0 to PowerUpCost(600, 1),
    7.0 to PowerUpCost(800, 1),
    9.0 to PowerUpCost(1000, 1),
    11.0 to PowerUpCost(1300, 1),
    13.0 to PowerUpCost(1600, 1),
    15.0 to PowerUpCost(1900, 2),
    17.0 to PowerUpCost(2200, 2),
    19.0 to PowerUpCost(2500, 2),
    21.0 to PowerUpCost(3000, 2),
    23.0 to PowerUpCost(3500, 3),
    25.0 to PowerUpCost(4000, 3),
    26.0 to PowerUpCost(4000, 4),
    27.0 to PowerUpCost(4500, 4),
    29.0 to PowerUpCost(5000, 4),
    31.0 to PowerUpCost(6000, 6),
    33.0 to PowerUpCost(7000, 8),
    35.0 to PowerUpCost(8000, 10),
    37.0 to PowerUpCost(9000, 12),
    39.0 to PowerUpCost(10000, 15),
    41.0 to PowerUpCost(11000, 15),
    43.0 to PowerUpCost(12000, 15),
    45.0 to PowerUpCost(13000, 15),
    47.0 to PowerUpCost(14000, 15),
    49.0 to PowerUpCost(15000, 17)
)

internal fun powerUpCostAtLevel(level: Double): PowerUpCost {
    return reviewPowerUpCosts.lastOrNull { level >= it.first }?.second ?: reviewPowerUpCosts.first().second
}

internal fun powerUpCostBetweenLevels(currentLevel: Double, targetLevel: Double): PowerUpCost {
    if (targetLevel <= currentLevel) return PowerUpCost(0, 0)
    var stardust = 0
    var candy = 0
    var level = currentLevel
    while (level < targetLevel) {
        val next = (level + 0.5).coerceAtMost(targetLevel)
        if (next <= level) break
        val cost = powerUpCostAtLevel(level)
        stardust += cost.stardust
        candy += cost.candy
        level = next
    }
    return PowerUpCost(stardust, candy)
}

internal fun normalizeReviewKey(text: String): String {
    return Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace(Regex("[^A-Za-z0-9]+"), " ")
        .trim()
        .uppercase(Locale.US)
}

internal fun isVivillonReviewFamily(name: String?): Boolean {
    val normalized = name?.trim()?.uppercase() ?: return false
    return normalized in setOf("SCATTERBUG", "SPEWPA", "VIVILLON")
}

internal fun debugRectSummary(
    label: String,
    rect: NormalizedDebugRect?,
    bitmap: Bitmap
): String? {
    rect ?: return null
    val left = (rect.left * bitmap.width).roundToInt()
    val top = (rect.top * bitmap.height).roundToInt()
    val right = (rect.right * bitmap.width).roundToInt()
    val bottom = (rect.bottom * bitmap.height).roundToInt()
    val width = (right - left).coerceAtLeast(0)
    val height = (bottom - top).coerceAtLeast(0)
    return "$label px: x=$left y=$top w=$width h=$height"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReviewChipSection(
    label: String,
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ReviewSectionTitle(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            options.forEachIndexed { index, option ->
                FilterChip(
                    modifier = Modifier.heightIn(min = 27.dp),
                    selected = option.second,
                    onClick = { onSelect(index) },
                    label = { Text(option.first, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                )
            }
        }
    }
}

@Composable
internal fun WeightedToggleRow(items: List<WeightedToggleItem>) {
    if (LocalGlassReviewStyle.current && items.map { it.label } == listOf("♂", "♀")) {
        GlassGenderSegments(items)
        return
    }
    GlassFieldRow(legacySpacing = 2.dp) {
        items.forEach { item ->
            ToggleChip(
                label = item.label,
                selected = item.selected,
                onClick = item.onClick,
                compact = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun ToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(if (LocalGlassReviewStyle.current) GlassFieldCorner else 10.dp)
    val containerColor = if (LocalGlassReviewStyle.current) glassFieldColor(selected) else if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.58f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    }
    Surface(
        modifier = modifier
            .glassFieldSize().heightIn(min = if (LocalGlassReviewStyle.current) GlassFieldHeight else 34.dp)
            .clickable(onClick = onClick),
        shape = shape,
        color = containerColor,
        tonalElevation = 0.dp,
        border = if (LocalGlassReviewStyle.current) glassFieldBorder(selected) else androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (LocalGlassReviewStyle.current) GlassFieldHeight else 34.dp)
                .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                fontWeight = if (LocalGlassReviewStyle.current) FontWeight.Bold else FontWeight.Normal,
                style = if (LocalGlassReviewStyle.current) MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp) else if (compact) MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp) else MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun Set<EvolutionFlag>.toggle(flag: EvolutionFlag): Set<EvolutionFlag> {
    return if (flag in this) this - flag else this + flag
}

internal fun Set<EvolutionFlag>.toggleEvolutionStage(flag: EvolutionFlag): Set<EvolutionFlag> {
    val stageFlags = setOf(EvolutionFlag.BABY, EvolutionFlag.STAGE1, EvolutionFlag.STAGE2)
    return if (flag in this) {
        this - flag
    } else {
        (this - stageFlags) + flag
    }
}

internal fun Set<NamingField>.toggleField(field: NamingField): Set<NamingField> {
    return if (field in this) this - field else this + field
}

internal fun Set<NamingField>.toggleAll(fields: Set<NamingField>): Set<NamingField> {
    return if (fields.all { it in this }) this - fields else this + fields
}

internal fun pokemonDebugFields(): Set<NamingField> = linkedSetOf(
    NamingField.POKEMON_NAME,
    NamingField.CP,
    NamingField.LEVEL,
    NamingField.GENDER,
    NamingField.UNIQUE_FORM
)

internal fun ivDebugFields(): Set<NamingField> = linkedSetOf(
    NamingField.IV_PERCENT,
    NamingField.IV_COMBINATION
)

internal fun attributeDebugFields(): Set<NamingField> = linkedSetOf(
    NamingField.TYPE,
    NamingField.FAVORITE,
    NamingField.LUCKY,
    NamingField.SHADOW,
    NamingField.PURIFIED
)

internal fun ReviewIvMode.localizedLabel(language: AppLanguage): String {
    return when (this) {
        ReviewIvMode.NORMAL -> lt(language, "Normal", "Normal", "Normal")
        ReviewIvMode.SHADOW -> lt(language, "Sombroso", "Shadow", "Oscuro")
        ReviewIvMode.PURIFIED -> lt(language, "Purificado", "Purified", "Purificado")
    }
}

internal fun PokemonScreenData.applyIvMode(mode: ReviewIvMode): PokemonScreenData {
    return copy(
        isShadow = mode == ReviewIvMode.SHADOW,
        isPurified = mode == ReviewIvMode.PURIFIED
    ).recalculateIvPercent()
}

internal fun PokemonScreenData.withSelectedGender(gender: Gender): PokemonScreenData {
    if (gender != Gender.MALE && gender != Gender.FEMALE) {
        return copy(gender = gender)
    }

    val stage = nidoranEvolutionStage(pokemonName) ?: nidoranEvolutionStage(candyFamilyName)
        ?: return copy(gender = gender)
    val targetFamily = if (gender == Gender.FEMALE) {
        listOf("Nidoran♀", "Nidorina", "Nidoqueen")
    } else {
        listOf("Nidoran♂", "Nidorino", "Nidoking")
    }

    return copy(
        pokemonName = targetFamily[stage],
        candyFamilyName = targetFamily.first(),
        gender = gender,
        pvpLeague = null,
        pvpRank = null,
        pvpPokemonName = null,
        pvpLeagueRanks = emptyList(),
        familyPvpRanks = emptyList(),
        masterIvBadgeMatch = null,
        masterIvBadgeDebugInfo = null,
        selectedFastMove = null,
        selectedChargedMove = null,
        hasLegacyMove = false,
        legacyDebugInfo = null
    )
}

private fun nidoranEvolutionStage(name: String?): Int? {
    return when (name?.trim()?.uppercase()) {
        "NIDORAN", "NIDORANF", "NIDORANM", "NIDORAN♀", "NIDORAN♂" -> 0
        "NIDORINA", "NIDORINO" -> 1
        "NIDOQUEEN", "NIDOKING" -> 2
        else -> null
    }
}
internal fun effectiveIvForMode(value: Int?, mode: ReviewIvMode): Int? {
    return if (mode == ReviewIvMode.PURIFIED) {
        value?.plus(2)?.coerceAtMost(15)
    } else {
        value
    }
}

internal fun displayIvToBaseIv(value: Int, mode: ReviewIvMode): Int {
    return if (mode == ReviewIvMode.PURIFIED) {
        (value - 2).coerceIn(0, 15)
    } else {
        value
    }
}

internal fun buildDerivedReviewData(
    context: Context,
    data: PokemonScreenData,
    familyMembers: List<String>,
    rankCalculator: PvpRankCalculator,
    masterIvBadgeCatalog: MasterIvBadgeCatalog
): PokemonScreenData {
    val attack = data.attIv ?: return data
    val defense = data.defIv ?: return data
    val stamina = data.staIv ?: return data
    val familyRanks = runCatching {
        rankCalculator.calculateFamilySpeciesLeagueRanks(
            context = context,
            pokemonNames = familyMembers,
            atk = attack,
            def = defense,
            sta = stamina,
            currentPokemonName = data.pokemonName ?: data.candyFamilyName,
            currentLevel = data.level, currentCp = data.cp
        )
    }.getOrElse { emptyList() }
    val leagueRanks = bestLeagueRanksFromSpecies(familyRanks)
    val selectedLeague = data.pvpLeague
        ?.takeIf { league -> familyRanks.any { it.league == league && it.eligible && it.rank != null } }
        ?: bestLeagueFromRanks(familyRanks, leagueRanks)
    val selectedSpeciesRank = selectedLeague?.let { league ->
        val eligibleRanksForLeague = familyRanks
            .filter { it.league == league && it.eligible && it.rank != null }
        val preservedSelection = data.pvpPokemonName?.let { selectedPokemonName ->
            eligibleRanksForLeague.firstOrNull { rankInfo ->
                rankInfo.pokemonName == selectedPokemonName &&
                    (data.pvpRank == null || rankInfo.rank == data.pvpRank)
            } ?: eligibleRanksForLeague.firstOrNull { rankInfo ->
                rankInfo.pokemonName == selectedPokemonName
            }
        }
        preservedSelection
            ?: eligibleRanksForLeague.minWithOrNull(
                compareBy<PvpSpeciesRankInfo> { it.rank ?: Int.MAX_VALUE }.thenBy { it.pokemonName }
            )
    }
    val selectedLeagueRank = if (selectedSpeciesRank == null && selectedLeague != null) {
        leagueRanks.firstOrNull { it.league == selectedLeague && it.eligible }
    } else {
        null
    }
    val masterResult = runCatching {
        masterIvBadgeCatalog.resolve(
            context = context,
            familyMembers = familyMembers,
            ivPercent = data.ivPercent,
            attack = attack,
            defense = defense,
            stamina = stamina
        )
    }.getOrNull()
    return data.copy(
        familyPvpRanks = familyRanks,
        pvpLeagueRanks = leagueRanks,
        pvpLeague = selectedLeague,
        pvpRank = selectedSpeciesRank?.rank ?: selectedLeagueRank?.rank,
        pvpPokemonName = selectedSpeciesRank?.pokemonName ?: selectedLeagueRank?.pokemonName,
        masterIvBadgeMatch = masterResult?.isBestMatch,
        masterIvBadgeDebugInfo = masterResult?.let { result ->
            MasterIvBadgeDebugInfo(
                supportedIvPercent = result.notes != "iv_percent_fora_do_escopo",
                familyMembers = familyMembers,
                expectedAttack = result.expectedAttack,
                expectedDefense = result.expectedDefense,
                expectedStamina = result.expectedStamina,
                isBestMatch = result.isBestMatch,
                notes = result.notes
            )
        }
    )
}

internal fun bestLeagueRanksFromSpecies(speciesRanks: List<PvpSpeciesRankInfo>): List<PvpLeagueRankInfo> {
    return listOf(PvpLeague.LITTLE, PvpLeague.GREAT, PvpLeague.ULTRA, PvpLeague.MASTER).mapNotNull { league ->
        val best = selectBestFamilyOption(speciesRanks.filter { it.league == league }) ?: return@mapNotNull null
        PvpLeagueRankInfo(
            league = best.league,
            pokemonName = best.pokemonName,
            eligible = best.eligible,
            rank = best.rank,
            bestCp = best.bestCp,
            bestLevel = best.bestLevel,
            bestStatProduct = best.bestStatProduct,
            stadiumUrl = best.stadiumUrl,
            description = best.description
        )
    }
}

internal fun selectBestFamilyOption(options: List<PvpSpeciesRankInfo>): PvpSpeciesRankInfo? {
    val eligible = options.filter { it.eligible && it.bestStatProduct != null }
    if (eligible.isNotEmpty()) {
        return eligible.maxWithOrNull(
            compareBy<PvpSpeciesRankInfo> { it.bestStatProduct ?: Double.NEGATIVE_INFINITY }
                .thenByDescending { it.bestLevel ?: 0.0 }
                .thenByDescending { it.bestCp ?: 0 }
        )
    }
    return options.firstOrNull()
}

internal fun bestLeagueFromRanks(
    speciesRanks: List<PvpSpeciesRankInfo>,
    leagueRanks: List<PvpLeagueRankInfo>
): PvpLeague? {
    return speciesRanks
        .filter { it.eligible && it.rank != null }
        .minWithOrNull(compareBy<PvpSpeciesRankInfo> { it.rank ?: Int.MAX_VALUE }.thenBy { it.league.ordinal })
        ?.league
        ?: leagueRanks
            .filter { it.eligible && it.rank != null }
            .minWithOrNull(compareBy<PvpLeagueRankInfo> { it.rank ?: Int.MAX_VALUE }.thenBy { it.league.ordinal })
            ?.league
}

internal fun PokemonScreenData.normalizeReviewData(): PokemonScreenData {
    val att = attIv
    val def = defIv
    val sta = staIv
    val normalized = if (ivPercent == null && att != null && def != null && sta != null) {
        copy(ivPercent = ((att + def + sta) * 100f / 45f).roundToInt())
    } else {
        this
    }
    return if (normalized.hasSpecialBackground) {
        normalized.copy(specialBackgroundType = normalized.specialBackgroundType ?: SpecialBackgroundType.SPECIAL)
    } else {
        normalized.copy(specialBackgroundType = null)
    }
}

internal fun PokemonScreenData.recalculateIvPercent(): PokemonScreenData {
    val att = attIv
    val def = defIv
    val sta = staIv
    return if (att != null && def != null && sta != null) {
        copy(ivPercent = ((att + def + sta) * 100f / 45f).roundToInt())
    } else {
        this
    }
}

internal fun PokemonScreenData.applySelectedLegacyMove(
    selectedMoveName: String?,
    availableMoves: List<PokemonMove>,
    previewUsesLegacyMove: Boolean
): PokemonScreenData {
    if (!previewUsesLegacyMove) return this
    val selectedMove = selectedMoveName?.let { moveName ->
        availableMoves.firstOrNull { it.name == moveName }
    } ?: return this
    if (!selectedMove.legacy) return this
    return copy(
        hasLegacyMove = true,
        legacyDebugInfo = (legacyDebugInfo ?: LegacyDebugInfo()).copy(
            matchedLegacyMove = selectedMove.name
        )
    )
}

internal fun specialBackgroundSelectionOptions(language: AppLanguage): List<String> {
    return listOf(
        "-",
        SpecialBackgroundType.SPECIAL.localizedLabel(language),
        SpecialBackgroundType.GO_FEST.localizedLabel(language),
        SpecialBackgroundType.WILD_AREA.localizedLabel(language),
        SpecialBackgroundType.LOCATION.localizedLabel(language),
        SpecialBackgroundType.COMMUNITY_DAY.localizedLabel(language)
    )
}

internal fun specialBackgroundTypeFromSelection(
    value: String,
    language: AppLanguage
): SpecialBackgroundType? {
    return when (value) {
        SpecialBackgroundType.SPECIAL.localizedLabel(language) -> SpecialBackgroundType.SPECIAL
        SpecialBackgroundType.GO_FEST.localizedLabel(language) -> SpecialBackgroundType.GO_FEST
        SpecialBackgroundType.WILD_AREA.localizedLabel(language) -> SpecialBackgroundType.WILD_AREA
        SpecialBackgroundType.LOCATION.localizedLabel(language) -> SpecialBackgroundType.LOCATION
        SpecialBackgroundType.COMMUNITY_DAY.localizedLabel(language) -> SpecialBackgroundType.COMMUNITY_DAY
        else -> null
    }
}

internal fun PokemonScreenData.specialBackgroundSelectionLabel(language: AppLanguage): String {
    if (!hasSpecialBackground) return "-"
    return (specialBackgroundType ?: SpecialBackgroundType.SPECIAL).localizedLabel(language)
}



internal fun reviewRankSpecies(context: Context, data: PokemonScreenData, calculator: PvpRankCalculator): List<String> {
    val names = if (data.familyPvpRanks.isNotEmpty()) data.familyPvpRanks.map { it.pokemonName }
        else data.pvpLeagueRanks.mapNotNull { it.pokemonName }
    return names.filter { it.isNotBlank() }.map { calculator.canonicalName(context, it) }.distinct()
}

internal fun reviewMoveSpecies(data: PokemonScreenData): String? =
    data.pokemonName?.takeIf { it.isNotBlank() } ?: data.candyFamilyName?.takeIf { it.isNotBlank() }
internal fun PokemonScreenData.withReviewSpecies(name: String): PokemonScreenData = copy(
    pokemonName = name, pvpPokemonName = null, pvpRank = null,
    familyPvpRanks = emptyList(), pvpLeagueRanks = emptyList(),
    selectedFastMove = null, selectedChargedMove = null,
    hasLegacyMove = false, legacyDebugInfo = null
)