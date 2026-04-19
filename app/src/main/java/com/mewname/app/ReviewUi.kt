package com.mewname.app

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.mewname.app.domain.PokemonFamilySuggester
import com.mewname.app.domain.UniquePokemonCatalog
import com.mewname.app.model.EvolutionFlag
import com.mewname.app.model.Gender
import com.mewname.app.model.AdventureEffectDebugInfo
import com.mewname.app.model.AttributeDebugInfo
import com.mewname.app.model.BackgroundDebugInfo
import com.mewname.app.model.CandyDebugInfo
import com.mewname.app.model.EvolutionIconDebugInfo
import com.mewname.app.model.GenderDebugInfo
import com.mewname.app.model.IvDebugInfo
import com.mewname.app.model.LegacyDebugInfo
import com.mewname.app.model.LevelDebugInfo
import com.mewname.app.model.NamingConfig
import com.mewname.app.model.NamingField
import com.mewname.app.model.NormalizedDebugRect
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.PokemonSize
import com.mewname.app.model.PvpLeague
import com.mewname.app.model.PvpLeagueRankInfo
import com.mewname.app.model.PvpSpeciesRankInfo
import com.mewname.app.model.VivillonPattern
import com.mewname.app.model.hasVisibleSizeSymbol
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewEditorCard(
    initialData: PokemonScreenData,
    fields: List<NamingField>,
    configs: List<NamingConfig> = listOf(NamingConfig()),
    bitmap: Bitmap? = null,
    onConfirm: (PokemonScreenData) -> Unit,
    onExportLog: ((Set<NamingField>) -> Unit)? = null,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var draft by remember(initialData, fields) {
        mutableStateOf(
            initialData.copy(
                pokemonName = initialData.pokemonName ?: initialData.candyFamilyName
            ).recalculateIvPercent()
        )
    }
    var pokemonExpanded by remember { mutableStateOf(false) }
    var activeIvPicker by remember { mutableStateOf<String?>(null) }
    var showIvHelp by remember { mutableStateOf(false) }
    var showPokemonHelp by remember { mutableStateOf(false) }
    var showAdventureHelp by remember { mutableStateOf(false) }
    var showLegacyHelp by remember { mutableStateOf(false) }
    var showBackgroundHelp by remember { mutableStateOf(false) }
    var showSizeHelp by remember { mutableStateOf(false) }
    var showLeagueHelp by remember { mutableStateOf(false) }
    var showPvpHelp by remember { mutableStateOf(false) }
    var showVivillonHelp by remember { mutableStateOf(false) }
    var showEvolutionHelp by remember { mutableStateOf(false) }
    var showAttributeHelp by remember { mutableStateOf(false) }
    var selectedDebugFields by remember { mutableStateOf<Set<NamingField>>(emptySet()) }
    val familySuggester = remember { PokemonFamilySuggester() }
    val visibleSizeOptions = remember(configs, draft.size) {
        listOf(PokemonSize.XXS, PokemonSize.XS, PokemonSize.XL, PokemonSize.XXL)
            .filter { configs.hasVisibleSizeSymbol(it) || it == draft.size }
            .ifEmpty { listOf(PokemonSize.XXS, PokemonSize.XS, PokemonSize.XL, PokemonSize.XXL) }
    }
    val pokemonSuggestions = remember(initialData.candyFamilyName, initialData.pokemonName) {
        familySuggester.suggestionsFor(context, initialData.candyFamilyName, initialData.pokemonName)
    }
    val uniqueFormOptions = remember(draft.pokemonName) { UniquePokemonCatalog.optionsFor(draft.pokemonName) }
    val familyRankCards = remember(draft.familyPvpRanks, draft.pvpLeague) {
        speciesRankCardsForLeague(draft.familyPvpRanks, selectedLeague = draft.pvpLeague)
    }
    fun selectPvpLeague(league: PvpLeague?) {
        val selectedSpeciesRank = league?.let { selected ->
            draft.familyPvpRanks
                .filter { it.league == selected && it.eligible && it.rank != null }
                .minByOrNull { it.rank ?: Int.MAX_VALUE }
        }
        val selectedLeagueRank = if (selectedSpeciesRank == null && league != null) {
            draft.pvpLeagueRanks.firstOrNull { it.league == league && it.eligible }
        } else {
            null
        }
        draft = draft.copy(
            pvpLeague = league,
            pvpRank = selectedSpeciesRank?.rank ?: selectedLeagueRank?.rank,
            pvpPokemonName = selectedSpeciesRank?.pokemonName ?: selectedLeagueRank?.pokemonName
        )
    }

    Box(modifier = modifier.widthIn(max = 560.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Dados Detectados", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ReviewTextRow {
                    PokemonSuggestionField(
                        label = "Nome",
                        value = draft.pokemonName.orEmpty(),
                        suggestions = pokemonSuggestions,
                        expanded = pokemonExpanded,
                        onExpandedChange = { pokemonExpanded = it },
                        onValueChange = { draft = draft.copy(pokemonName = it.ifBlank { null }) },
                        modifier = Modifier.weight(2f)
                    )
                    SelectionDropdownField(
                        label = "Sexo",
                        value = when (draft.gender) {
                            Gender.MALE -> "♂"
                            Gender.FEMALE -> "♀"
                            else -> "-"
                        },
                        options = listOf("-", "♂", "♀"),
                        onSelected = { value ->
                            draft = draft.copy(
                                gender = when (value) {
                                    "♂" -> Gender.MALE
                                    "♀" -> Gender.FEMALE
                                    else -> Gender.GENDERLESS
                                }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    CompactField(
                        label = "Nível",
                        value = draft.level?.formatLevelDebug().orEmpty(),
                        onValueChange = { draft = draft.copy(level = it.replace(",", ".").toDoubleOrNull()) },
                        headerTrailing = {
                            UnownHeaderIcon(
                                selected = selectedDebugFields.any { it in pokemonDebugFields() },
                                onClick = {
                                    selectedDebugFields = selectedDebugFields.toggleAll(pokemonDebugFields())
                                    showPokemonHelp = !showPokemonHelp
                                },
                                contentDescription = "Log do Pokémon"
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (NamingField.CP in fields) {
                    ReviewTextRow {
                        if (NamingField.CP in fields) {
                            CompactField(
                                label = "CP",
                                value = draft.cp?.toString().orEmpty(),
                                onValueChange = { draft = draft.copy(cp = it.toIntOrNull()) },
                                modifier = Modifier.weight(0.9f)
                            )
                        }
                    }
                }
                if (uniqueFormOptions.isNotEmpty()) {
                    SelectionDropdownField(
                        label = "Forma única",
                        value = draft.uniqueForm ?: "-",
                        options = buildList {
                            add("-")
                            addAll(uniqueFormOptions.map { it.label })
                        },
                        onSelected = { value ->
                            val selected = value.takeUnless { it == "-" }
                            draft = if (draft.pokemonName.equals("Unown", ignoreCase = true)) {
                                draft.copy(
                                    uniqueForm = selected,
                                    unownLetter = selected
                                )
                            } else {
                                draft.copy(uniqueForm = selected)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (showPokemonHelp) {
                    PokemonHelpPanel(
                        candyInfo = draft.candyDebugInfo,
                        levelInfo = draft.levelDebugInfo,
                        genderInfo = draft.genderDebugInfo,
                        uniqueFormInfo = draft.uniqueFormDebugInfo,
                        bitmap = bitmap
                    )
                }
            }

            if (NamingField.IV_PERCENT in fields || NamingField.IV_COMBINATION in fields) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        if (NamingField.IV_PERCENT in fields) {
                            CompactField(
                                label = "IV %",
                                value = draft.ivPercent?.toString().orEmpty(),
                                onValueChange = { draft = draft.copy(ivPercent = it.toIntOrNull()?.coerceIn(0, 100)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        IvValueButton(
                            label = "Atk",
                            value = draft.attIv,
                            selected = activeIvPicker == "atk",
                            onClick = { activeIvPicker = if (activeIvPicker == "atk") null else "atk" },
                            modifier = Modifier.weight(1f)
                        )
                        IvValueButton(
                            label = "Def",
                            value = draft.defIv,
                            selected = activeIvPicker == "def",
                            onClick = { activeIvPicker = if (activeIvPicker == "def") null else "def" },
                            modifier = Modifier.weight(1f)
                        )
                        IvValueButton(
                            label = "HP",
                            value = draft.staIv,
                            selected = activeIvPicker == "hp",
                            onClick = { activeIvPicker = if (activeIvPicker == "hp") null else "hp" },
                            headerTrailing = {
                                UnownHeaderIcon(
                                    selected = selectedDebugFields.any { it in ivDebugFields() },
                                    onClick = {
                                        selectedDebugFields = selectedDebugFields.toggleAll(ivDebugFields())
                                        showIvHelp = !showIvHelp
                                    },
                                    contentDescription = "Log de IV"
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (showIvHelp) {
                        IvHelpPanel(info = draft.ivDebugInfo, bitmap = bitmap)
                    }
                }
            }
            if (NamingField.MASTER_IV_BADGE in fields) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FieldHeaderRow(
                        label = "IV Master",
                        selected = NamingField.MASTER_IV_BADGE in selectedDebugFields,
                        onMarkerClick = {
                            selectedDebugFields = selectedDebugFields.toggleField(NamingField.MASTER_IV_BADGE)
                        }
                    )
                    SelectionDropdownField(
                        label = "",
                        value = when (draft.masterIvBadgeMatch) {
                            true -> "Melhor combinação"
                            false -> "Outra combinação"
                            null -> "-"
                        },
                        options = listOf("-", "Melhor combinação", "Outra combinação"),
                        onSelected = { value ->
                            draft = draft.copy(
                                masterIvBadgeMatch = when (value) {
                                    "Melhor combinação" -> true
                                    "Outra combinação" -> false
                                    else -> null
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (NamingField.MASTER_IV_BADGE in selectedDebugFields) {
                        draft.masterIvBadgeDebugInfo?.let { info ->
                            Text(
                                text = buildString {
                                    append("Detectado: ")
                                    append(
                                        when (info.isBestMatch) {
                                            true -> "Melhor combinação"
                                            false -> "Outra combinação"
                                            null -> "-"
                                        }
                                    )
                                    if (info.expectedAttack != null && info.expectedDefense != null && info.expectedStamina != null) {
                                        append(" | Esperado: ${info.expectedAttack}/${info.expectedDefense}/${info.expectedStamina}")
                                    }
                                    if (info.notes.isNotBlank()) {
                                        append(" | ${info.notes}")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            val hasAttributeSection = listOf(
                NamingField.TYPE,
                NamingField.FAVORITE,
                NamingField.LUCKY,
                NamingField.SHADOW,
                NamingField.PURIFIED
            ).any { it in fields }
            if (hasAttributeSection) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FieldHeaderRow(
                        label = "Atributos",
                        selected = selectedDebugFields.any { it in attributeDebugFields() },
                        onMarkerClick = {
                            selectedDebugFields = selectedDebugFields.toggleAll(attributeDebugFields().intersect(fields.toSet()))
                            showAttributeHelp = !showAttributeHelp
                        }
                    )
                    ReviewTextRow {
                        var cellsInFirstRow = 0
                        if (NamingField.TYPE in fields) {
                            CompactField(
                                label = "",
                                value = draft.displayTypes(),
                                onValueChange = { value ->
                                    val parsed = parseTypesForReview(value)
                                    draft = draft.copy(
                                        type1 = parsed.firstOrNull(),
                                        type2 = parsed.getOrNull(1)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            cellsInFirstRow++
                        }
                        if (NamingField.FAVORITE in fields) {
                            ToggleChip(
                                label = "Favorito",
                                selected = draft.isFavorite,
                                onClick = { draft = draft.copy(isFavorite = !draft.isFavorite) },
                                compact = true,
                                modifier = Modifier.weight(1f)
                            )
                            cellsInFirstRow++
                        }
                        if (NamingField.LUCKY in fields) {
                            ToggleChip(
                                label = "Sortudo",
                                selected = draft.isLucky,
                                onClick = { draft = draft.copy(isLucky = !draft.isLucky) },
                                compact = true,
                                modifier = Modifier.weight(1f)
                            )
                            cellsInFirstRow++
                        }
                        repeat((3 - cellsInFirstRow).coerceAtLeast(0)) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    ReviewTextRow {
                        var cellsInSecondRow = 0
                        if (NamingField.SHADOW in fields) {
                            ToggleChip(
                                label = "Sombrio",
                                selected = draft.isShadow,
                                onClick = { draft = draft.copy(isShadow = !draft.isShadow) },
                                compact = true,
                                modifier = Modifier.weight(1f)
                            )
                            cellsInSecondRow++
                        }
                        if (NamingField.PURIFIED in fields) {
                            ToggleChip(
                                label = "Purificado",
                                selected = draft.isPurified,
                                onClick = { draft = draft.copy(isPurified = !draft.isPurified) },
                                compact = true,
                                modifier = Modifier.weight(1f)
                            )
                            cellsInSecondRow++
                        }
                        repeat((3 - cellsInSecondRow).coerceAtLeast(0)) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    if (showAttributeHelp) {
                        AttributeHelpPanel(
                            info = draft.attributeDebugInfo,
                            backgroundInfo = draft.backgroundDebugInfo,
                            type1 = draft.type1,
                            type2 = draft.type2,
                            isFavorite = draft.isFavorite,
                            isLucky = draft.isLucky,
                            isShadow = draft.isShadow,
                            isPurified = draft.isPurified
                        )
                    }
                }
            }

            if (NamingField.SIZE in fields) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FieldHeaderRow(
                        label = "Tamanho",
                        selected = NamingField.SIZE in selectedDebugFields,
                        onMarkerClick = {
                            selectedDebugFields = selectedDebugFields.toggleField(NamingField.SIZE)
                            showSizeHelp = !showSizeHelp
                        }
                    )
                    WeightedToggleRow(
                        items = visibleSizeOptions.map { size ->
                            val label = when (size) {
                                PokemonSize.XXS -> "XXS"
                                PokemonSize.XS -> "XS"
                                PokemonSize.XL -> "XL"
                                PokemonSize.XXL -> "XXL"
                                PokemonSize.NORMAL -> "Normal"
                            }
                            WeightedToggleItem(
                                label = label,
                                selected = draft.size == size,
                                onClick = {
                                    draft = draft.copy(size = if (draft.size == size) PokemonSize.NORMAL else size)
                                }
                            )
                        }
                    )
                    if (showSizeHelp) {
                        SizeHelpPanel(size = draft.size, info = draft.sizeDebugInfo)
                    }
                }
            }

            if (NamingField.PVP_LEAGUE in fields) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FieldHeaderRow(
                        label = "Liga PvP",
                        selected = NamingField.PVP_LEAGUE in selectedDebugFields,
                        onMarkerClick = {
                            selectedDebugFields = selectedDebugFields.toggleField(NamingField.PVP_LEAGUE)
                            showLeagueHelp = !showLeagueHelp
                        }
                    )
                    WeightedToggleRow(
                        items = listOf(
                            WeightedToggleItem("Copinha", draft.pvpLeague == PvpLeague.LITTLE) {
                                selectPvpLeague(if (draft.pvpLeague == PvpLeague.LITTLE) null else PvpLeague.LITTLE)
                            },
                            WeightedToggleItem("Great", draft.pvpLeague == PvpLeague.GREAT) {
                                selectPvpLeague(if (draft.pvpLeague == PvpLeague.GREAT) null else PvpLeague.GREAT)
                            },
                            WeightedToggleItem("Ultra", draft.pvpLeague == PvpLeague.ULTRA) {
                                selectPvpLeague(if (draft.pvpLeague == PvpLeague.ULTRA) null else PvpLeague.ULTRA)
                            },
                            WeightedToggleItem("Master", draft.pvpLeague == PvpLeague.MASTER) {
                                selectPvpLeague(if (draft.pvpLeague == PvpLeague.MASTER) null else PvpLeague.MASTER)
                            }
                        )
                    )
                    if (showLeagueHelp) {
                        PvpHelpPanel(leagueRanks = draft.pvpLeagueRanks, speciesRanks = draft.familyPvpRanks)
                    }
                }
            }

            if (NamingField.PVP_RANK in fields) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FieldHeaderRow(
                        label = "Ranking PvP",
                        selected = NamingField.PVP_RANK in selectedDebugFields,
                        onMarkerClick = {
                            selectedDebugFields = selectedDebugFields.toggleField(NamingField.PVP_RANK)
                            showPvpHelp = !showPvpHelp
                        }
                    )
                    if (familyRankCards.isEmpty()) {
                        CompactField(
                            label = "",
                            value = draft.pvpRank?.toString().orEmpty(),
                            onValueChange = { draft = draft.copy(pvpRank = it.toIntOrNull()) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        ReviewTextRow {
                            familyRankCards.take(3).forEach { rankCard ->
                                CompactField(
                                    label = rankCard.label,
                                    value = rankCard.value,
                                    onValueChange = {},
                                    readOnly = true,
                                    active = draft.pvpLeague == rankCard.league &&
                                        draft.pvpRank == rankCard.rank &&
                                        draft.pvpPokemonName == rankCard.pokemonName,
                                    onClick = {
                                        if (rankCard.eligible) {
                                            draft = draft.copy(
                                                pvpLeague = rankCard.league,
                                                pvpRank = rankCard.rank,
                                                pvpPokemonName = rankCard.pokemonName
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat((3 - familyRankCards.take(3).size).coerceAtLeast(0)) {
                                CompactField(
                                    label = "",
                                    value = "",
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    if (showPvpHelp) {
                        PvpHelpPanel(leagueRanks = draft.pvpLeagueRanks, speciesRanks = draft.familyPvpRanks)
                    }
                }
            }

            if (isVivillonReviewFamily(draft.pokemonName)) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    FieldHeaderRow(
                        label = "Padrão Vivillon",
                        selected = NamingField.VIVILLON_PATTERN in selectedDebugFields,
                        onMarkerClick = {
                            selectedDebugFields = selectedDebugFields.toggleField(NamingField.VIVILLON_PATTERN)
                            showVivillonHelp = !showVivillonHelp
                        }
                    )
                    SelectionDropdownField(
                        label = "",
                        value = draft.vivillonPattern?.label ?: "Não identificado",
                        options = listOf("Não identificado") + VivillonPattern.entries.map { it.label },
                        onSelected = { value ->
                            draft = draft.copy(
                                vivillonPattern = VivillonPattern.entries.firstOrNull { it.label == value }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showVivillonHelp) {
                        VivillonHelpPanel(pattern = draft.vivillonPattern, info = draft.vivillonDebugInfo, bitmap = bitmap)
                    }
                }
            }

            val hasBooleanSection = listOf(
                NamingField.SPECIAL_BACKGROUND,
                NamingField.ADVENTURE_EFFECT,
                NamingField.LEGACY_MOVE,
                NamingField.LEGACY_MOVE_NAME
            ).any { it in fields }
            if (hasBooleanSection) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FieldHeaderRow(
                        label = "Marcadores",
                        selected = selectedDebugFields.any {
                            it in setOf(NamingField.SPECIAL_BACKGROUND, NamingField.ADVENTURE_EFFECT, NamingField.LEGACY_MOVE, NamingField.LEGACY_MOVE_NAME)
                        },
                        onMarkerClick = {
                            selectedDebugFields = selectedDebugFields.toggleAll(
                                linkedSetOf(
                                    NamingField.SPECIAL_BACKGROUND,
                                    NamingField.ADVENTURE_EFFECT,
                                    NamingField.LEGACY_MOVE,
                                    NamingField.LEGACY_MOVE_NAME
                                ).intersect(fields.toSet())
                            )
                            showBackgroundHelp = !showBackgroundHelp
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                        if (NamingField.SPECIAL_BACKGROUND in fields) {
                            ToggleChip(
                                label = "Fundo Especial",
                                selected = draft.hasSpecialBackground,
                                onClick = { draft = draft.copy(hasSpecialBackground = !draft.hasSpecialBackground) },
                                compact = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (NamingField.ADVENTURE_EFFECT in fields) {
                            ToggleChip(
                                label = "Efeito Aventura",
                                selected = draft.hasAdventureEffect,
                                onClick = { draft = draft.copy(hasAdventureEffect = !draft.hasAdventureEffect) },
                                compact = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (NamingField.LEGACY_MOVE in fields) {
                            val legacyMoveLabel = draft.legacyDebugInfo
                                ?.matchedLegacyMove
                                ?.takeIf { it.isNotBlank() }
                                ?: "Ataque Legado"
                            ToggleChip(
                                label = legacyMoveLabel,
                                selected = draft.hasLegacyMove,
                                onClick = { draft = draft.copy(hasLegacyMove = !draft.hasLegacyMove) },
                                compact = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (NamingField.LEGACY_MOVE_NAME in fields) {
                        CompactField(
                            label = "Nome do ataque legado",
                            value = draft.legacyDebugInfo?.matchedLegacyMove.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (showBackgroundHelp) {
                        BackgroundHelpPanel(info = draft.backgroundDebugInfo)
                    }
                    if (showAdventureHelp || showBackgroundHelp) {
                        AdventureEffectHelpPanel(info = draft.adventureEffectDebugInfo)
                    }
                    if (showLegacyHelp || showBackgroundHelp) {
                        LegacyHelpPanel(info = draft.legacyDebugInfo)
                    }
                }
            }

            if (NamingField.EVOLUTION_TYPE in fields) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FieldHeaderRow(
                        label = "Evolução",
                        selected = NamingField.EVOLUTION_TYPE in selectedDebugFields,
                        onMarkerClick = {
                            selectedDebugFields = selectedDebugFields.toggleField(NamingField.EVOLUTION_TYPE)
                            showEvolutionHelp = !showEvolutionHelp
                        }
                    )
                    WeightedToggleRow(
                        items = listOf(
                            WeightedToggleItem("Baby", EvolutionFlag.BABY in draft.evolutionFlags) {
                                draft = draft.copy(evolutionFlags = draft.evolutionFlags.toggleEvolutionStage(EvolutionFlag.BABY))
                            },
                            WeightedToggleItem("Estágio 1", EvolutionFlag.STAGE1 in draft.evolutionFlags) {
                                draft = draft.copy(evolutionFlags = draft.evolutionFlags.toggleEvolutionStage(EvolutionFlag.STAGE1))
                            },
                            WeightedToggleItem("Estágio 2", EvolutionFlag.STAGE2 in draft.evolutionFlags) {
                                draft = draft.copy(evolutionFlags = draft.evolutionFlags.toggleEvolutionStage(EvolutionFlag.STAGE2))
                            },
                            WeightedToggleItem("Mega", EvolutionFlag.MEGA in draft.evolutionFlags) {
                                draft = draft.copy(evolutionFlags = draft.evolutionFlags.toggle(EvolutionFlag.MEGA))
                            }
                        )
                    )
                    WeightedToggleRow(
                        items = listOf(
                            WeightedToggleItem("Dynamax", EvolutionFlag.DYNAMAX in draft.evolutionFlags) {
                                draft = draft.copy(evolutionFlags = draft.evolutionFlags.toggle(EvolutionFlag.DYNAMAX))
                            },
                            WeightedToggleItem("Gigantamax", EvolutionFlag.GIGANTAMAX in draft.evolutionFlags) {
                                draft = draft.copy(evolutionFlags = draft.evolutionFlags.toggle(EvolutionFlag.GIGANTAMAX))
                            },
                            WeightedToggleItem("Terastal", EvolutionFlag.TERASTRAL in draft.evolutionFlags) {
                                draft = draft.copy(evolutionFlags = draft.evolutionFlags.toggle(EvolutionFlag.TERASTRAL))
                            }
                        )
                    )
                    if (showEvolutionHelp) {
                        EvolutionIconHelpPanel(info = draft.evolutionIconDebugInfo)
                    }
                }
            }

                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 2.dp,
                    shadowElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            if (onExportLog != null) {
                                TextButton(onClick = { onExportLog(selectedDebugFields) }, modifier = Modifier.weight(0.9f)) {
                                    Text("Exportar log", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Button(
                                onClick = { onConfirm(draft.recalculateIvPercent().normalizeReviewData()) },
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Text("Continuar", fontSize = 14.sp, maxLines = 1)
                            }
                            TextButton(onClick = onCancel, modifier = Modifier.weight(0.9f)) {
                                Text("Cancelar", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        if (activeIvPicker != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeIvPicker = null }
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                IvPickerModal(
                    title = when (activeIvPicker) {
                        "atk" -> "Selecionar Atk"
                        "def" -> "Selecionar Def"
                        else -> "Selecionar HP"
                    },
                    currentValue = when (activeIvPicker) {
                        "atk" -> draft.attIv
                        "def" -> draft.defIv
                        else -> draft.staIv
                    },
                    onValueSelected = { selected ->
                        draft = when (activeIvPicker) {
                            "atk" -> draft.copy(attIv = selected)
                            "def" -> draft.copy(defIv = selected)
                            else -> draft.copy(staIv = selected)
                        }.recalculateIvPercent()
                        activeIvPicker = null
                    },
                    onDismiss = { activeIvPicker = null }
                )
            }
        }
    }
}

@Composable
private fun PokemonHelpPanel(
    candyInfo: CandyDebugInfo?,
    levelInfo: LevelDebugInfo?,
    genderInfo: GenderDebugInfo?,
    uniqueFormInfo: com.mewname.app.model.UniqueFormDebugInfo?,
    bitmap: Bitmap?
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Leitura do Pokémon", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("Nível final: ${levelInfo?.finalLevel?.formatLevelDebug() ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Fonte do nível: ${levelInfo?.source ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Nível OCR: ${levelInfo?.ocrLevel?.formatLevelDebug() ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Nível curva CP: ${levelInfo?.curveLevel?.formatLevelDebug() ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Pokémon base: ${levelInfo?.pokemonName ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("CP usado: ${levelInfo?.cp ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("IVs usados: ${levelInfo?.attackIv ?: "-"}/${levelInfo?.defenseIv ?: "-"}/${levelInfo?.staminaIv ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Gênero detectado: ${when (genderInfo?.detectedGender) {
                Gender.MALE -> "♂"
                Gender.FEMALE -> "♀"
                Gender.GENDERLESS -> "-"
                else -> "-"
            }}", style = MaterialTheme.typography.bodySmall)
            genderInfo?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text("Obs gênero: $notes", style = MaterialTheme.typography.bodySmall)
            }
            Text("Linhas na região de doces: ${candyInfo?.regionLineCount ?: 0}", style = MaterialTheme.typography.bodySmall)
            Text("Linha encontrada: ${candyInfo?.matchedLine ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Família extraída: ${candyInfo?.extractedFamilyRaw ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Família resolvida: ${candyInfo?.resolvedFamilyName ?: "-"}", style = MaterialTheme.typography.bodySmall)
            if (uniqueFormInfo != null) {
                Text("Forma única: ${uniqueFormInfo.bestLabel ?: "-"}", style = MaterialTheme.typography.bodySmall)
                Text("Categoria: ${uniqueFormInfo.category ?: "-"}", style = MaterialTheme.typography.bodySmall)
                Text("Melhor arquivo: ${uniqueFormInfo.bestReferenceName ?: "-"}", style = MaterialTheme.typography.bodySmall)
                Text("Distância: ${uniqueFormInfo.bestDistance?.formatDebugDouble() ?: "-"}", style = MaterialTheme.typography.bodySmall)
                Text("Aceita: ${uniqueFormInfo.accepted}", style = MaterialTheme.typography.bodySmall)
                uniqueFormInfo.notes.takeIf { it.isNotBlank() }?.let { notes ->
                    Text("Obs forma única: $notes", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!candyInfo?.regionLines.isNullOrEmpty()) {
                Text("Linhas lidas:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                candyInfo?.regionLines?.forEach { line ->
                    Text("- $line", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!levelInfo?.notes.isNullOrBlank()) {
                Text("Obs nível: ${levelInfo?.notes}", style = MaterialTheme.typography.bodySmall)
            }
            if (!candyInfo?.notes.isNullOrBlank()) {
                Text("Obs doces: ${candyInfo?.notes}", style = MaterialTheme.typography.bodySmall)
            }
            if (bitmap != null && (genderInfo?.iconRect != null || !uniqueFormInfo?.candidateRects.isNullOrEmpty())) {
                PokemonVisualDebugOverlay(bitmap = bitmap, genderInfo = genderInfo, uniqueFormInfo = uniqueFormInfo)
            }
        }
    }
}

@Composable
private fun AdventureEffectHelpPanel(info: AdventureEffectDebugInfo?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Leitura do efeito de aventura", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("Pokémon base: ${info?.matchedPokemon ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Keyword encontrada: ${info?.matchedKeyword ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Golpe encontrado: ${info?.matchedMove ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Efeito encontrado: ${info?.matchedEffectName ?: "-"}", style = MaterialTheme.typography.bodySmall)
            if (!info?.extractedMoves.isNullOrEmpty()) {
                Text("Golpes extraídos:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                info?.extractedMoves?.forEach { move ->
                    Text("- $move", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!info?.moveRegionLines.isNullOrEmpty()) {
                Text("Linhas da área de movimentos:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                info?.moveRegionLines?.forEach { line ->
                    Text("- $line", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!info?.upperBadgeLines.isNullOrEmpty()) {
                Text("Linhas da área superior:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                info?.upperBadgeLines?.forEach { line ->
                    Text("- $line", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!info?.notes.isNullOrBlank()) {
                Text("Obs: ${info?.notes}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LegacyHelpPanel(info: LegacyDebugInfo?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Leitura do ataque legado", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("Pokémon base: ${info?.matchedAgainstPokemon ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Keyword encontrada: ${info?.matchedKeyword ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Golpe legado sugerido/detectado: ${info?.matchedLegacyMove ?: "-"}", style = MaterialTheme.typography.bodySmall)
            if (!info?.extractedMoves.isNullOrEmpty()) {
                Text("Golpes extraidos:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                info?.extractedMoves?.forEach { move ->
                    Text("- $move", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!info?.moveRegionLines.isNullOrEmpty()) {
                Text("Linhas da área de movimentos:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                info?.moveRegionLines?.forEach { line ->
                    Text("- $line", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!info?.notes.isNullOrBlank()) {
                Text("Obs: ${info?.notes}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AttributeHelpPanel(
    info: AttributeDebugInfo?,
    backgroundInfo: BackgroundDebugInfo?,
    type1: String?,
    type2: String?,
    isFavorite: Boolean,
    isLucky: Boolean,
    isShadow: Boolean,
    isPurified: Boolean
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Log dos atributos", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Tipo final: ${listOfNotNull(type1, type2).joinToString("/").ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
            Text("Tipos detectados: ${info?.detectedTypes?.joinToString("/")?.ifBlank { "-" } ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Linhas do tipo: ${info?.typeRegionLines?.joinToString(" | ")?.ifBlank { "-" } ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Favorito: $isFavorite", style = MaterialTheme.typography.bodySmall)
            Text("Estrela amarela: ${info?.favoriteFilledMatch ?: false}", style = MaterialTheme.typography.bodySmall)
            Text("Razão amarela: ${info?.favoriteYellowRatio?.formatDebugDouble() ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Sortudo: $isLucky", style = MaterialTheme.typography.bodySmall)
            Text("Sombrio: $isShadow", style = MaterialTheme.typography.bodySmall)
            Text("Purificado: $isPurified", style = MaterialTheme.typography.bodySmall)
            Text(
                "Sinais extra: sortudoTexto=${backgroundInfo?.luckyTextMatch ?: false} sortudoVisual=${backgroundInfo?.luckyVisualMatch ?: false} sombraTexto=${backgroundInfo?.shadowTextMatch ?: false} sombraParticulas=${backgroundInfo?.shadowParticleMatch ?: false} sombraTextura=${backgroundInfo?.shadowTextureMatch ?: false}",
                style = MaterialTheme.typography.bodySmall
            )
            info?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text("Obs: $notes", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PokemonVisualDebugOverlay(
    bitmap: Bitmap,
    genderInfo: GenderDebugInfo?,
    uniqueFormInfo: com.mewname.app.model.UniqueFormDebugInfo?
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val aspectRatio = remember(bitmap) { bitmap.width.toFloat() / bitmap.height.toFloat() }
    val shape = RoundedCornerShape(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Mapa da leitura", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), shape)
                .heightIn(min = 160.dp)
                .aspectRatio(aspectRatio)
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Debug visual do Pokémon",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
            Canvas(modifier = Modifier.matchParentSize()) {
                uniqueFormInfo?.candidateRects?.forEach { rect ->
                    drawDebugRect(rect, Color(0xFFFFC107))
                }
                drawDebugRect(uniqueFormInfo?.bestCandidateRect, Color.Magenta)
                drawDebugRect(genderInfo?.iconRect, Color(0xFF42A5F5))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!uniqueFormInfo?.candidateRects.isNullOrEmpty()) {
                DebugLegendChip("Candidatos forma", Color(0xFFFFC107))
            }
            if (uniqueFormInfo?.bestCandidateRect != null) {
                DebugLegendChip("Melhor forma", Color.Magenta)
            }
            if (genderInfo?.iconRect != null) {
                DebugLegendChip("Ícone gênero", Color(0xFF42A5F5))
            }
        }
    }
}

@Composable
private fun BackgroundHelpPanel(info: BackgroundDebugInfo?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Leitura do background", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("Texto casou: ${info?.textMatch ?: false}", style = MaterialTheme.typography.bodySmall)
            Text("Topo/meta casou: ${info?.topRegionMatch ?: false}", style = MaterialTheme.typography.bodySmall)
            Text("Matcher referencia: ${info?.referenceDecision?.toString() ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Melhor referencia: ${info?.referenceName ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Distancia: ${info?.referenceDistance?.formatDebugDouble() ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Melhor special: ${info?.specialReferenceName ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Distancia special: ${info?.specialReferenceDistance?.formatDebugDouble() ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Fallback por cor: ${info?.colorFallbackMatch ?: false}", style = MaterialTheme.typography.bodySmall)
            if (!info?.topRegionLines.isNullOrEmpty()) {
                Text("Linhas do topo:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                info?.topRegionLines?.forEach { line ->
                    Text("- $line", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!info?.bottomRegionLines.isNullOrEmpty()) {
                Text("Linhas do rodape:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                info?.bottomRegionLines?.forEach { line ->
                    Text("- $line", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!info?.notes.isNullOrBlank()) {
                Text("Obs: ${info?.notes}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EvolutionIconHelpPanel(info: EvolutionIconDebugInfo?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Leitura dos ícones", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("Mega: ${info?.megaKeyword ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Gigantamax: ${info?.gigantamaxKeyword ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Dynamax: ${info?.dynamaxKeyword ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Flags detectadas: ${info?.detectedFlags?.joinToString(", ").orEmpty().ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
            if (!info?.titleLines.isNullOrEmpty()) {
                Text("Linhas do topo:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                info?.titleLines?.forEach { line ->
                    Text("- $line", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!info?.badgeLines.isNullOrEmpty()) {
                Text("Linhas do badge:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                info?.badgeLines?.forEach { line ->
                    Text("- $line", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!info?.centerLines.isNullOrEmpty()) {
                Text("Linhas centrais:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                info?.centerLines?.forEach { line ->
                    Text("- $line", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!info?.actionLines.isNullOrEmpty()) {
                Text("Linhas de ação:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                info?.actionLines?.forEach { line ->
                    Text("- $line", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!info?.notes.isNullOrBlank()) {
                Text("Obs: ${info?.notes}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ReviewTextRow(
    content: @Composable RowScope.() -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

@Composable
private fun CompactField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean = false,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
    headerTrailing: (@Composable (() -> Unit))? = null,
    modifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = modifier) {
        FieldLabelRow(label = label, trailing = headerTrailing)
        if (onClick != null) {
            CompactSelectableField(
                value = value,
                active = active,
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 0.dp)
            )
        } else {
            CompactTextInput(
                value = value,
                onValueChange = onValueChange,
                readOnly = readOnly,
                active = active,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 0.dp)
            )
        }
    }
}

@Composable
private fun CompactSelectableField(
    value: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
        },
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.78f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 34.dp)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                value,
                style = TextStyle(
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun IvValueButton(
    label: String,
    value: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    headerTrailing: (@Composable (() -> Unit))? = null,
    modifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = modifier) {
        FieldLabelRow(label = label, trailing = headerTrailing)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 34.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    value?.toString().orEmpty(),
                    style = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Selecionar $label",
                    modifier = Modifier.size(18.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IvPickerModal(
    title: String,
    currentValue: Int?,
    onValueSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 340.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (0..15).forEach { option ->
                    FilterChip(
                        selected = currentValue == option,
                        onClick = { onValueSelected(option) },
                        label = { Text(option.toString(), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Fechar")
            }
        }
    }
}

@Composable
private fun CompactTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    active: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
        },
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.78f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 34.dp)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = readOnly,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }
    }
}

@Composable
private fun PokemonSuggestionField(
    label: String,
    value: String,
    suggestions: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    headerTrailing: (@Composable (() -> Unit))? = null,
    modifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = modifier) {
        FieldLabelRow(label = label, trailing = headerTrailing)
        Box {
            CompactTextInput(
                value = value,
                onValueChange = onValueChange,
                readOnly = suggestions.isNotEmpty(),
                active = expanded,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = suggestions.isNotEmpty()) { onExpandedChange(!expanded) },
                trailing = {
                    if (suggestions.isNotEmpty()) {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Abrir sugestoes",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            if (suggestions.isNotEmpty()) {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) },
                    modifier = Modifier
                        .widthIn(min = 180.dp, max = 280.dp)
                        .heightIn(max = 220.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
                ) {
                    suggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) },
                            onClick = {
                                onValueChange(suggestion)
                                onExpandedChange(false)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    headerTrailing: (@Composable (() -> Unit))? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = modifier) {
        FieldLabelRow(label = label, trailing = headerTrailing)
        Box {
            CompactTextInput(
                value = value,
                onValueChange = {},
                readOnly = true,
                active = expanded,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                trailing = {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Abrir opções",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .widthIn(min = 120.dp, max = 220.dp)
                    .heightIn(max = 220.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun IvHelpPanel(
    info: IvDebugInfo?,
    bitmap: Bitmap?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(9.dp)
        ) {
            Text("Leitura do IV", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Text("Appraisal detectada: ${info?.appraisalDetected ?: false}", style = MaterialTheme.typography.bodySmall)
            Text("Barras detectadas: ${info?.detectedBars ?: 0}", style = MaterialTheme.typography.bodySmall)
            Text("Confiavel: ${info?.reliable ?: false}", style = MaterialTheme.typography.bodySmall)
            Text("Atk ratio: ${info?.attackRatio?.formatDebug() ?: "-"} -> ${info?.attackDetected ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Def ratio: ${info?.defenseRatio?.formatDebug() ?: "-"} -> ${info?.defenseDetected ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("HP ratio: ${info?.staminaRatio?.formatDebug() ?: "-"} -> ${info?.staminaDetected ?: "-"}", style = MaterialTheme.typography.bodySmall)
            info?.attackMeasurementDebug?.takeIf { it.isNotBlank() }?.let { debug ->
                Text("Atk dbg: $debug", style = MaterialTheme.typography.bodySmall)
            }
            info?.defenseMeasurementDebug?.takeIf { it.isNotBlank() }?.let { debug ->
                Text("Def dbg: $debug", style = MaterialTheme.typography.bodySmall)
            }
            info?.staminaMeasurementDebug?.takeIf { it.isNotBlank() }?.let { debug ->
                Text("HP dbg: $debug", style = MaterialTheme.typography.bodySmall)
            }
            Text("IV OCR: ${info?.percentFromOcr ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("IV final: ${info?.percentFinal ?: "-"}", style = MaterialTheme.typography.bodySmall)
            if (bitmap != null && info != null) {
                debugRectSummary("Painel", info.appraisalPanelRect, bitmap)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                debugRectSummary("Atk", info.attackBarRect, bitmap)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                debugRectSummary("Def", info.defenseBarRect, bitmap)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                debugRectSummary("HP", info.staminaBarRect, bitmap)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                IvDebugOverlay(bitmap = bitmap, info = info)
            }
            info?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text("Obs: $notes", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IvDebugOverlay(
    bitmap: Bitmap,
    info: IvDebugInfo
) {
    val hasAnyRect = info.appraisalPanelRect != null ||
        info.attackBarRect != null ||
        info.defenseBarRect != null ||
        info.staminaBarRect != null
    if (!hasAnyRect) return

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val aspectRatio = remember(bitmap) { bitmap.width.toFloat() / bitmap.height.toFloat() }
    val shape = RoundedCornerShape(12.dp)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Mapa da leitura", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), shape)
                .heightIn(min = 160.dp)
                .aspectRatio(aspectRatio)
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Debug da leitura do IV",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
            Canvas(modifier = Modifier.matchParentSize()) {
                drawDebugRect(info.appraisalPanelRect, Color.Magenta)
                drawDebugRect(info.attackBarRect, Color(0xFFFF9800))
                drawDebugRect(info.defenseBarRect, Color(0xFF42A5F5))
                drawDebugRect(info.staminaBarRect, Color(0xFF66BB6A))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DebugLegendChip("Painel", Color.Magenta)
            DebugLegendChip("Atk", Color(0xFFFF9800))
            DebugLegendChip("Def", Color(0xFF42A5F5))
            DebugLegendChip("HP", Color(0xFF66BB6A))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDebugRect(
    rect: NormalizedDebugRect?,
    color: Color
) {
    if (rect == null) return
    val left = rect.left * size.width
    val top = rect.top * size.height
    val width = (rect.right - rect.left) * size.width
    val height = (rect.bottom - rect.top) * size.height
    if (width <= 0f || height <= 0f) return

    drawRect(
        color = color.copy(alpha = 0.16f),
        topLeft = Offset(left, top),
        size = Size(width, height)
    )
    drawRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(width, height),
        style = Stroke(width = 3f)
    )
}

@Composable
private fun DebugLegendChip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, color, RoundedCornerShape(999.dp))
            )
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PvpHelpPanel(
    leagueRanks: List<PvpLeagueRankInfo>,
    speciesRanks: List<PvpSpeciesRankInfo> = emptyList()
) {
    val context = LocalContext.current
    val orderedLeagues = listOf(PvpLeague.LITTLE, PvpLeague.GREAT, PvpLeague.ULTRA, PvpLeague.MASTER)
    val orderedRanks = orderedLeagues.mapNotNull { league ->
        leagueRanks.firstOrNull { it.league == league }
    }
    val bestBySpecies = speciesRankCardsForLeague(speciesRanks, selectedLeague = null)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Log do ranking PvP", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (orderedRanks.isEmpty()) {
                Text("Nenhum ranking por liga foi calculado para este Pokémon.", style = MaterialTheme.typography.bodySmall)
            } else {
                if (bestBySpecies.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Melhor ranking por espécie", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        bestBySpecies.take(3).forEach { rankCard ->
                            Text("${rankCard.pokemonName}: ${rankCard.value}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                orderedRanks.forEach { info ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(leagueDisplayName(info.league), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        info.pokemonName?.let { pokemonName ->
                            Text("Melhor opção: $pokemonName", style = MaterialTheme.typography.bodySmall)
                        }
                        if (info.eligible) {
                            Text(
                                "Rank ${info.rank ?: "-"} | Melhor CP ${info.bestCp ?: "-"} | Nível ${info.bestLevel?.formatLevelDebug() ?: "-"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(info.description, style = MaterialTheme.typography.bodySmall)
                        info.stadiumUrl?.let { url ->
                            TextButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            ) {
                                Text("Abrir no Stadium")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VivillonHelpPanel(
    pattern: VivillonPattern?,
    info: com.mewname.app.model.VivillonDebugInfo?,
    bitmap: Bitmap?
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Log do padrão Vivillon", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Padrão detectado: ${pattern?.label ?: "Não identificado"}", style = MaterialTheme.typography.bodySmall)
            Text("Melhor referência: ${info?.bestReferenceName ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Distância: ${info?.bestDistance?.formatDebugDouble() ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Segunda referência: ${info?.secondReferenceName ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Distância 2: ${info?.secondDistance?.formatDebugDouble() ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Aceita: ${info?.accepted ?: false}", style = MaterialTheme.typography.bodySmall)
            if (bitmap != null) {
                debugRectSummary("Vivillon", info?.bestCandidateRect, bitmap)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Refs usadas: unique_pokemon_refs/vivillon", style = MaterialTheme.typography.bodySmall)
            info?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text("Obs: $notes", style = MaterialTheme.typography.bodySmall)
            }
            if (bitmap != null && info != null && info.candidateRects.isNotEmpty()) {
                VivillonDebugOverlay(bitmap = bitmap, info = info)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VivillonDebugOverlay(
    bitmap: Bitmap,
    info: com.mewname.app.model.VivillonDebugInfo
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val aspectRatio = remember(bitmap) { bitmap.width.toFloat() / bitmap.height.toFloat() }
    val shape = RoundedCornerShape(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Mapa da leitura", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), shape)
                .heightIn(min = 160.dp)
                .aspectRatio(aspectRatio)
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Debug da leitura do Vivillon",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
            Canvas(modifier = Modifier.matchParentSize()) {
                info.candidateRects.forEach { rect ->
                    drawDebugRect(rect, Color(0xFFFFC107))
                }
                drawDebugRect(info.bestCandidateRect, Color.Magenta)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DebugLegendChip("Candidatos", Color(0xFFFFC107))
            DebugLegendChip("Melhor área", Color.Magenta)
        }
    }
}

@Composable
private fun SizeHelpPanel(size: PokemonSize, info: com.mewname.app.model.SizeDebugInfo?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Log do tamanho", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Tamanho detectado: ${sizeDisplayName(size)}", style = MaterialTheme.typography.bodySmall)
            if (size == PokemonSize.NORMAL) {
                Text("Normal é usado quando nenhum dos tamanhos especiais está marcado.", style = MaterialTheme.typography.bodySmall)
            }
            if (info != null) {
                if (info.candidateLines.isNotEmpty()) {
                    Text("Linhas: ${info.candidateLines.joinToString(" | ")}", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Visual: ${info.visualSize?.name ?: "-"} ratio=${info.visualBadgeRatio?.let { "%.3f".format(it) } ?: "-"}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (info.notes.isNotBlank()) {
                    Text("Obs: ${info.notes}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ReviewSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun FieldLabelRow(
    label: String,
    trailing: (@Composable (() -> Unit))? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
        trailing?.invoke()
    }
}

@Composable
private fun FieldHeaderRow(
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
private fun UnownHeaderIcon(
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String
) {
    IconButton(onClick = onClick, modifier = Modifier.size(20.dp)) {
        UnownQuestionIcon(
            selected = selected,
            modifier = Modifier.size(16.dp),
            contentDescription = contentDescription
        )
    }
}

private data class WeightedToggleItem(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit
)

private fun Float.formatDebug(): String = String.format("%.3f", this)
private fun Double.formatDebugDouble(): String = String.format("%.3f", this)
private fun Double.formatLevelDebug(): String = if (this % 1.0 == 0.0) this.toInt().toString() else this.toString().replace(".", ",")

private fun leagueDisplayName(league: PvpLeague): String {
    return when (league) {
        PvpLeague.LITTLE -> "Little League"
        PvpLeague.GREAT -> "Great League"
        PvpLeague.ULTRA -> "Ultra League"
        PvpLeague.MASTER -> "Master League"
    }
}

private fun leagueShortName(league: PvpLeague): String {
    return when (league) {
        PvpLeague.LITTLE -> "CP"
        PvpLeague.GREAT -> "GL"
        PvpLeague.ULTRA -> "UL"
        PvpLeague.MASTER -> "ML"
    }
}

private fun sizeDisplayName(size: PokemonSize): String {
    return when (size) {
        PokemonSize.XXS -> "XXS"
        PokemonSize.XS -> "XS"
        PokemonSize.NORMAL -> "Normal"
        PokemonSize.XL -> "XL"
        PokemonSize.XXL -> "XXL"
    }
}

private fun PokemonScreenData.displayTypes(): String {
    return listOfNotNull(type1, type2).joinToString("/").ifBlank { "" }
}

private fun parseTypesForReview(value: String): List<String> {
    return value.split("/", ",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { it.uppercase() }
        .distinct()
        .take(2)
}

private data class PvpRankCard(
    val pokemonName: String,
    val league: PvpLeague,
    val rank: Int?,
    val eligible: Boolean,
    val value: String,
    val label: String
)

private fun speciesRankCardsForLeague(
    speciesRanks: List<PvpSpeciesRankInfo>,
    selectedLeague: PvpLeague?
): List<PvpRankCard> {
    if (speciesRanks.isEmpty()) return emptyList()
    val orderedSpecies = speciesRanks.map { it.pokemonName }.distinct()
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
            label = pokemonName,
            value = if (best.eligible && best.rank != null) {
                if (selectedLeague != null) "Rank ${best.rank}" else "${leagueShortName(best.league)} • ${best.rank}"
            } else {
                "Não elegível"
            }
        )
    }.sortedWith(
        compareBy<PvpRankCard> { if (it.eligible && it.rank != null) 0 else 1 }
            .thenBy { it.rank ?: Int.MAX_VALUE }
            .thenBy { it.league.ordinal }
            .thenBy { it.label }
    )
}

private fun isVivillonReviewFamily(name: String?): Boolean {
    val normalized = name?.trim()?.uppercase() ?: return false
    return normalized in setOf("SCATTERBUG", "SPEWPA", "VIVILLON")
}

private fun debugRectSummary(
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
private fun ReviewChipSection(
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
private fun WeightedToggleRow(items: List<WeightedToggleItem>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
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
private fun ToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    val containerColor = if (selected) {
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
            .heightIn(min = 34.dp)
            .clickable(onClick = onClick),
        shape = shape,
        color = containerColor,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 34.dp)
                .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = if (compact) MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp) else MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun Set<EvolutionFlag>.toggle(flag: EvolutionFlag): Set<EvolutionFlag> {
    return if (flag in this) this - flag else this + flag
}

private fun Set<EvolutionFlag>.toggleEvolutionStage(flag: EvolutionFlag): Set<EvolutionFlag> {
    val stageFlags = setOf(EvolutionFlag.BABY, EvolutionFlag.STAGE1, EvolutionFlag.STAGE2)
    return if (flag in this) {
        this - flag
    } else {
        (this - stageFlags) + flag
    }
}

private fun Set<NamingField>.toggleField(field: NamingField): Set<NamingField> {
    return if (field in this) this - field else this + field
}

private fun Set<NamingField>.toggleAll(fields: Set<NamingField>): Set<NamingField> {
    return if (fields.all { it in this }) this - fields else this + fields
}

private fun pokemonDebugFields(): Set<NamingField> = linkedSetOf(
    NamingField.POKEMON_NAME,
    NamingField.CP,
    NamingField.LEVEL,
    NamingField.GENDER,
    NamingField.UNIQUE_FORM
)

private fun ivDebugFields(): Set<NamingField> = linkedSetOf(
    NamingField.IV_PERCENT,
    NamingField.IV_COMBINATION
)

private fun attributeDebugFields(): Set<NamingField> = linkedSetOf(
    NamingField.TYPE,
    NamingField.FAVORITE,
    NamingField.LUCKY,
    NamingField.SHADOW,
    NamingField.PURIFIED
)

private fun PokemonScreenData.normalizeReviewData(): PokemonScreenData {
    val att = attIv
    val def = defIv
    val sta = staIv
    return if (ivPercent == null && att != null && def != null && sta != null) {
        copy(ivPercent = ((att + def + sta) * 100f / 45f).roundToInt())
    } else {
        this
    }
}

private fun PokemonScreenData.recalculateIvPercent(): PokemonScreenData {
    val att = attIv
    val def = defIv
    val sta = staIv
    return if (att != null && def != null && sta != null) {
        copy(ivPercent = ((att + def + sta) * 100f / 45f).roundToInt())
    } else {
        this
    }
}


