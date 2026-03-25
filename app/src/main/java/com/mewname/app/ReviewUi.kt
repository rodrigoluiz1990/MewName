package com.mewname.app

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import com.mewname.app.model.EvolutionFlag
import com.mewname.app.model.Gender
import com.mewname.app.model.AdventureEffectDebugInfo
import com.mewname.app.model.BackgroundDebugInfo
import com.mewname.app.model.CandyDebugInfo
import com.mewname.app.model.IvDebugInfo
import com.mewname.app.model.LegacyDebugInfo
import com.mewname.app.model.NamingConfig
import com.mewname.app.model.NamingField
import com.mewname.app.model.NormalizedDebugRect
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.PokemonSize
import com.mewname.app.model.PvpLeague
import com.mewname.app.model.PvpLeagueRankInfo
import com.mewname.app.model.VivillonPattern
import com.mewname.app.model.hasVisibleSizeSymbol
import com.mewname.app.model.visibleEvolutionFlags
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewEditorCard(
    initialData: PokemonScreenData,
    fields: List<NamingField>,
    configs: List<NamingConfig> = listOf(NamingConfig()),
    bitmap: Bitmap? = null,
    onConfirm: (PokemonScreenData) -> Unit,
    onExportLog: (() -> Unit)? = null,
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
    var vivillonExpanded by remember { mutableStateOf(false) }
    var pokemonExpanded by remember { mutableStateOf(false) }
    var activeIvPicker by remember { mutableStateOf<String?>(null) }
    var showIvHelp by remember { mutableStateOf(false) }
    var showCandyHelp by remember { mutableStateOf(false) }
    var showAdventureHelp by remember { mutableStateOf(false) }
    var showLegacyHelp by remember { mutableStateOf(false) }
    var showBackgroundHelp by remember { mutableStateOf(false) }
    var showPvpHelp by remember { mutableStateOf(false) }
    val familySuggester = remember { PokemonFamilySuggester() }
    val visibleSizeOptions = remember(configs) {
        listOf(
            PokemonSize.XXS,
            PokemonSize.XS,
            PokemonSize.NORMAL,
            PokemonSize.XL,
            PokemonSize.XXL
        ).filter { it == PokemonSize.NORMAL || configs.hasVisibleSizeSymbol(it) }
    }
    val visibleEvolutionFlags = remember(configs) { configs.visibleEvolutionFlags() }
    val pokemonSuggestions = remember(initialData.candyFamilyName, initialData.pokemonName) {
        familySuggester.suggestionsFor(context, initialData.candyFamilyName, initialData.pokemonName)
    }

    Box(modifier = modifier.widthIn(max = 560.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Dados Detectados", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }

            when {
                NamingField.POKEMON_NAME in fields && NamingField.CP in fields -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ReviewSectionTitle("Pokémon")
                            IconButton(onClick = { showCandyHelp = !showCandyHelp }, modifier = Modifier.size(20.dp)) {
                                UnownQuestionIcon(modifier = Modifier.size(16.dp), contentDescription = "Ajuda doces")
                            }
                        }
                        ReviewTextRow {
                            PokemonSuggestionField(
                                label = "",
                                value = draft.pokemonName.orEmpty(),
                                suggestions = pokemonSuggestions,
                                expanded = pokemonExpanded,
                                onExpandedChange = { pokemonExpanded = it },
                                onValueChange = { draft = draft.copy(pokemonName = it.ifBlank { null }) },
                                modifier = Modifier.weight(1f)
                            )
                            CompactField(
                                label = "CP",
                                value = draft.cp?.toString().orEmpty(),
                                onValueChange = { draft = draft.copy(cp = it.toIntOrNull()) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (showCandyHelp) {
                            CandyHelpPanel(info = draft.candyDebugInfo)
                        }
                    }
                }
                NamingField.POKEMON_NAME in fields -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ReviewSectionTitle("Pokémon")
                            IconButton(onClick = { showCandyHelp = !showCandyHelp }, modifier = Modifier.size(20.dp)) {
                                UnownQuestionIcon(modifier = Modifier.size(16.dp), contentDescription = "Ajuda doces")
                            }
                        }
                        PokemonSuggestionField(
                            label = "",
                            value = draft.pokemonName.orEmpty(),
                            suggestions = pokemonSuggestions,
                            expanded = pokemonExpanded,
                            onExpandedChange = { pokemonExpanded = it },
                            onValueChange = { draft = draft.copy(pokemonName = it.ifBlank { null }) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (showCandyHelp) {
                            CandyHelpPanel(info = draft.candyDebugInfo)
                        }
                    }
                }
                NamingField.CP in fields -> {
                    CompactField(
                        label = "CP",
                        value = draft.cp?.toString().orEmpty(),
                        onValueChange = { draft = draft.copy(cp = it.toIntOrNull()) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (NamingField.IV_PERCENT in fields || NamingField.IV_COMBINATION in fields) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ReviewSectionTitle("IV")
                        IconButton(onClick = { showIvHelp = !showIvHelp }, modifier = Modifier.size(20.dp)) {
                            UnownQuestionIcon(modifier = Modifier.size(16.dp), contentDescription = "Ajuda IV")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        if (NamingField.IV_PERCENT in fields) {
                            CompactField(
                                label = "IV %",
                                value = draft.ivPercent?.toString().orEmpty(),
                                onValueChange = { draft = draft.copy(ivPercent = it.toIntOrNull()?.coerceIn(0, 100)) },
                                modifier = Modifier.weight(0.72f)
                            )
                        }
                        IvValueButton(
                            label = "Atk",
                            value = draft.attIv,
                            selected = activeIvPicker == "atk",
                            onClick = { activeIvPicker = if (activeIvPicker == "atk") null else "atk" },
                            modifier = Modifier.weight(0.6f)
                        )
                        IvValueButton(
                            label = "Def",
                            value = draft.defIv,
                            selected = activeIvPicker == "def",
                            onClick = { activeIvPicker = if (activeIvPicker == "def") null else "def" },
                            modifier = Modifier.weight(0.6f)
                        )
                        IvValueButton(
                            label = "HP",
                            value = draft.staIv,
                            selected = activeIvPicker == "hp",
                            onClick = { activeIvPicker = if (activeIvPicker == "hp") null else "hp" },
                            modifier = Modifier.weight(0.6f)
                        )
                    }
                    if (showIvHelp) {
                        IvHelpPanel(info = draft.ivDebugInfo, bitmap = bitmap)
                    }
                }
            }

            if (NamingField.LEVEL in fields) {
                CompactField(
                    label = "Nível",
                    value = draft.level?.toString().orEmpty(),
                    onValueChange = { draft = draft.copy(level = it.replace(",", ".").toDoubleOrNull()) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (NamingField.GENDER in fields) {
                ReviewChipSection(
                    label = "Gênero",
                    options = listOf(
                        "Desconhecido" to (draft.gender == Gender.UNKNOWN),
                        "Macho" to (draft.gender == Gender.MALE),
                        "Fêmea" to (draft.gender == Gender.FEMALE)
                    ),
                    onSelect = { index ->
                        draft = draft.copy(
                            gender = when (index) {
                                1 -> Gender.MALE
                                2 -> Gender.FEMALE
                                else -> Gender.UNKNOWN
                            }
                        )
                    }
                )
            }

            if (NamingField.SIZE in fields) {
                ReviewChipSection(
                    label = "Tamanho",
                    options = visibleSizeOptions.map { size ->
                        when (size) {
                            PokemonSize.XXS -> "XXS" to (draft.size == PokemonSize.XXS)
                            PokemonSize.XS -> "XS" to (draft.size == PokemonSize.XS)
                            PokemonSize.NORMAL -> "Normal" to (draft.size == PokemonSize.NORMAL)
                            PokemonSize.XL -> "XL" to (draft.size == PokemonSize.XL)
                            PokemonSize.XXL -> "XXL" to (draft.size == PokemonSize.XXL)
                        }
                    },
                    onSelect = { index ->
                        draft = draft.copy(
                            size = visibleSizeOptions.getOrNull(index) ?: PokemonSize.NORMAL
                        )
                    }
                )
            }

            if (NamingField.PVP_LEAGUE in fields) {
                ReviewChipSection(
                    label = "Liga PvP",
                    options = listOf(
                        "Nenhuma" to (draft.pvpLeague == null),
                        "Copinha" to (draft.pvpLeague == PvpLeague.LITTLE),
                        "Great" to (draft.pvpLeague == PvpLeague.GREAT),
                        "Ultra" to (draft.pvpLeague == PvpLeague.ULTRA),
                        "Master" to (draft.pvpLeague == PvpLeague.MASTER)
                    ),
                    onSelect = { index ->
                        draft = draft.copy(
                            pvpLeague = when (index) {
                                1 -> PvpLeague.LITTLE
                                2 -> PvpLeague.GREAT
                                3 -> PvpLeague.ULTRA
                                4 -> PvpLeague.MASTER
                                else -> null
                            }
                        )
                    }
                )
            }

            if (NamingField.PVP_RANK in fields) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ReviewSectionTitle("Ranking PvP")
                        IconButton(onClick = { showPvpHelp = !showPvpHelp }, modifier = Modifier.size(20.dp)) {
                            UnownQuestionIcon(modifier = Modifier.size(16.dp), contentDescription = "Ajuda PvP")
                        }
                    }
                    CompactField(
                        label = "Ranking PvP",
                        value = draft.pvpRank?.toString().orEmpty(),
                        onValueChange = { draft = draft.copy(pvpRank = it.toIntOrNull()) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showPvpHelp) {
                        PvpHelpPanel(leagueRanks = draft.pvpLeagueRanks)
                    }
                }
            }

            if (NamingField.VIVILLON_PATTERN in fields) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReviewSectionTitle("Padrão Vivillon")
                    Box {
                        CompactTextInput(
                            value = draft.vivillonPattern?.label ?: "Não identificado",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vivillonExpanded = !vivillonExpanded },
                            trailing = {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Abrir padrão Vivillon",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                        DropdownMenu(
                            expanded = vivillonExpanded,
                            onDismissRequest = { vivillonExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.92f)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Não identificado", style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    draft = draft.copy(vivillonPattern = null)
                                    vivillonExpanded = false
                                }
                            )
                            VivillonPattern.entries.forEach { pattern ->
                                DropdownMenuItem(
                                    text = { Text(pattern.label, style = MaterialTheme.typography.bodySmall) },
                                    onClick = {
                                        draft = draft.copy(vivillonPattern = pattern)
                                        vivillonExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            val hasBooleanSection = listOf(
                NamingField.SPECIAL_BACKGROUND,
                NamingField.ADVENTURE_EFFECT,
                NamingField.LEGACY_MOVE
            ).any { it in fields }
            if (hasBooleanSection) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ReviewSectionTitle("Marcadores")
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (NamingField.SPECIAL_BACKGROUND in fields) {
                                IconButton(onClick = { showBackgroundHelp = !showBackgroundHelp }, modifier = Modifier.size(20.dp)) {
                                    UnownQuestionIcon(modifier = Modifier.size(16.dp), contentDescription = "Ajuda background")
                                }
                            }
                            if (NamingField.ADVENTURE_EFFECT in fields) {
                                IconButton(onClick = { showAdventureHelp = !showAdventureHelp }, modifier = Modifier.size(20.dp)) {
                                    UnownQuestionIcon(modifier = Modifier.size(16.dp), contentDescription = "Ajuda efeito aventura")
                                }
                            }
                            if (NamingField.LEGACY_MOVE in fields) {
                                IconButton(onClick = { showLegacyHelp = !showLegacyHelp }, modifier = Modifier.size(20.dp)) {
                                    UnownQuestionIcon(modifier = Modifier.size(16.dp), contentDescription = "Ajuda legado")
                                }
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (NamingField.SPECIAL_BACKGROUND in fields) {
                            ToggleChip("Fundo especial", draft.hasSpecialBackground) {
                                draft = draft.copy(hasSpecialBackground = !draft.hasSpecialBackground)
                            }
                        }
                        if (NamingField.ADVENTURE_EFFECT in fields) {
                            ToggleChip("Efeito aventura", draft.hasAdventureEffect) {
                                draft = draft.copy(hasAdventureEffect = !draft.hasAdventureEffect)
                            }
                        }
                        if (NamingField.LEGACY_MOVE in fields) {
                            ToggleChip("Ataque legado", draft.hasLegacyMove) {
                                draft = draft.copy(hasLegacyMove = !draft.hasLegacyMove)
                            }
                        }
                    }
                    if (showBackgroundHelp) {
                        BackgroundHelpPanel(info = draft.backgroundDebugInfo)
                    }
                    if (showAdventureHelp) {
                        AdventureEffectHelpPanel(info = draft.adventureEffectDebugInfo)
                    }
                    if (showLegacyHelp) {
                        LegacyHelpPanel(info = draft.legacyDebugInfo)
                    }
                }
            }

            if (NamingField.EVOLUTION_TYPE in fields && visibleEvolutionFlags.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewSectionTitle("Evolução")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (EvolutionFlag.BABY in visibleEvolutionFlags) if (EvolutionFlag.BABY in visibleEvolutionFlags) ToggleChip("Baby", EvolutionFlag.BABY in draft.evolutionFlags) {
                            draft = draft.copy(evolutionFlags = draft.evolutionFlags.toggle(EvolutionFlag.BABY))
                        }
                        if (EvolutionFlag.STAGE1 in visibleEvolutionFlags) ToggleChip("Estágio 1", EvolutionFlag.STAGE1 in draft.evolutionFlags) {
                            draft = draft.copy(evolutionFlags = draft.evolutionFlags.toggle(EvolutionFlag.STAGE1))
                        }
                        if (EvolutionFlag.STAGE2 in visibleEvolutionFlags) ToggleChip("Estágio 2", EvolutionFlag.STAGE2 in draft.evolutionFlags) {
                            draft = draft.copy(evolutionFlags = draft.evolutionFlags.toggle(EvolutionFlag.STAGE2))
                        }
                        if (EvolutionFlag.MEGA in visibleEvolutionFlags) ToggleChip("Mega", EvolutionFlag.MEGA in draft.evolutionFlags) {
                            draft = draft.copy(evolutionFlags = draft.evolutionFlags.toggle(EvolutionFlag.MEGA))
                        }
                        if (EvolutionFlag.GIGANTAMAX in visibleEvolutionFlags) ToggleChip("Gigantamax", EvolutionFlag.GIGANTAMAX in draft.evolutionFlags) {
                            draft = draft.copy(evolutionFlags = draft.evolutionFlags.toggle(EvolutionFlag.GIGANTAMAX))
                        }
                        if (EvolutionFlag.DYNAMAX in visibleEvolutionFlags) ToggleChip("Dynamax", EvolutionFlag.DYNAMAX in draft.evolutionFlags) {
                            draft = draft.copy(evolutionFlags = draft.evolutionFlags.toggle(EvolutionFlag.DYNAMAX))
                        }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            if (onExportLog != null) {
                                TextButton(onClick = onExportLog, modifier = Modifier.weight(1f)) {
                                    Text("Exportar log", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Button(
                                onClick = { onConfirm(draft.recalculateIvPercent().normalizeReviewData()) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Continuar", fontSize = 13.sp, maxLines = 1)
                            }
                            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                                Text("Cancelar", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun CandyHelpPanel(info: CandyDebugInfo?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Leitura dos doces", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("Linhas na regiao: ${info?.regionLineCount ?: 0}", style = MaterialTheme.typography.bodySmall)
            Text("Linha encontrada: ${info?.matchedLine ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Familia extraida: ${info?.extractedFamilyRaw ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text("Familia resolvida: ${info?.resolvedFamilyName ?: "-"}", style = MaterialTheme.typography.bodySmall)
            if (!info?.regionLines.isNullOrEmpty()) {
                Text("Linhas lidas:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                info?.regionLines?.forEach { line ->
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
            Text("Golpe legado encontrado: ${info?.matchedLegacyMove ?: "-"}", style = MaterialTheme.typography.bodySmall)
            if (!info?.extractedMoves.isNullOrEmpty()) {
                Text("Golpes extraidos:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                info?.extractedMoves?.forEach { move ->
                    Text("- $move", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!info?.moveRegionLines.isNullOrEmpty()) {
                Text("Linhas da area de movimentos:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
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
private fun ReviewTextRow(
    content: @Composable RowScope.() -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        content()
    }
}

@Composable
private fun CompactField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        CompactTextInput(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(min = 0.dp)
        )
    }
}

@Composable
private fun IvValueButton(
    label: String,
    value: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
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
                    .padding(horizontal = 10.dp, vertical = 7.dp),
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
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 30.dp)
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
    modifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
        Box {
            CompactTextInput(
                value = value,
                onValueChange = onValueChange,
                readOnly = suggestions.isNotEmpty(),
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
                    modifier = Modifier.fillMaxWidth(0.92f)
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
private fun PvpHelpPanel(leagueRanks: List<PvpLeagueRankInfo>) {
    val context = LocalContext.current
    val orderedLeagues = listOf(PvpLeague.LITTLE, PvpLeague.GREAT, PvpLeague.ULTRA, PvpLeague.MASTER)
    val orderedRanks = orderedLeagues.mapNotNull { league ->
        leagueRanks.firstOrNull { it.league == league }
    }
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
private fun ReviewSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
}

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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReviewSectionTitle(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, option ->
                FilterChip(
                    selected = option.second,
                    onClick = { onSelect(index) },
                    label = { Text(option.first, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                )
            }
        }
    }
}

@Composable
private fun ToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
    )
}

private fun Set<EvolutionFlag>.toggle(flag: EvolutionFlag): Set<EvolutionFlag> {
    return if (flag in this) this - flag else this + flag
}

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
