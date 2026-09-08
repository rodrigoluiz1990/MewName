package com.mewname.app

import android.content.Intent
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

import com.mewname.app.domain.AppLanguage
import com.mewname.app.domain.AssetPaths
import com.mewname.app.domain.MasterIvBadgeCatalog
import com.mewname.app.domain.LoadState
import com.mewname.app.domain.NameGenerator
import com.mewname.app.domain.PokemonFamilySuggester
import com.mewname.app.domain.PokemonMove
import com.mewname.app.domain.PokemonMoveRepository
import com.mewname.app.domain.PokemonMoveSet
import com.mewname.app.domain.PvpRankCalculator
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
import com.mewname.app.model.MasterIvBadgeDebugInfo
import com.mewname.app.model.NamingConfig
import com.mewname.app.model.NamingField
import com.mewname.app.model.NormalizedDebugRect
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.PokemonSize
import com.mewname.app.model.PvpLeague
import com.mewname.app.model.PvpLeagueRankInfo
import com.mewname.app.model.PvpSpeciesRankInfo
import com.mewname.app.model.SpecialBackgroundType
import com.mewname.app.model.VivillonPattern
import com.mewname.app.model.effectiveBlocks
import com.mewname.app.model.hasVisibleSizeSymbol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

private enum class ReviewTab {
    BASIC,
    PVP,
    EXTRAS
}

internal enum class ReviewIvMode {
    NORMAL,
    SHADOW,
    PURIFIED
}


internal data class ReviewPokemonMoveOption(
    val value: String,
    val label: String
)
internal data class ReviewOptionPicker(
    val title: String,
    val options: List<String>,
    val selectedValue: String?,
    val message: String? = null,
    val verticalOptions: Boolean = false,
    val latestState: (() -> ReviewOptionPicker)? = null,
    val onOptionSelected: (String) -> Unit
)

internal fun ReviewOptionPicker.current(): ReviewOptionPicker = latestState?.invoke() ?: this

internal fun liveReviewOptionPicker(state: androidx.compose.runtime.State<ReviewOptionPicker>): ReviewOptionPicker =
    state.value.copy(latestState = { state.value })
internal val LocalReviewOptionPicker = staticCompositionLocalOf<((ReviewOptionPicker) -> Unit)?> { null }

@Volatile
internal var reviewEvolutionStageOrderCache: Map<String, Int>? = null


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
    val language = appLanguage()
    val generator = remember { NameGenerator() }
    val pvpRankCalculator = remember { PvpRankCalculator() }
    val masterIvBadgeCatalog = remember { MasterIvBadgeCatalog() }
    var draft by remember(initialData, fields) {
        mutableStateOf(
            initialData.copy(
                pokemonName = (initialData.pokemonName ?: initialData.candyFamilyName)?.let { com.mewname.app.domain.pokemonDisplayName(it) }
            ).recalculateIvPercent()
        )
    }
    var baseAttackIv by remember(initialData) { mutableStateOf(initialData.attIv) }
    var baseDefenseIv by remember(initialData) { mutableStateOf(initialData.defIv) }
    var baseStaminaIv by remember(initialData) { mutableStateOf(initialData.staIv) }
    var ivMode by remember(initialData) {
        mutableStateOf(
            when {
                initialData.isPurified -> ReviewIvMode.PURIFIED
                initialData.isShadow -> ReviewIvMode.SHADOW
                else -> ReviewIvMode.NORMAL
            }
        )
    }
    var pokemonExpanded by remember { mutableStateOf(false) }
    var activeIvPicker by remember { mutableStateOf<String?>(null) }
    var showLevelPicker by remember { mutableStateOf(false) }
    var showIvHelp by remember { mutableStateOf(false) }
    var showPokemonHelp by remember { mutableStateOf(false) }
    var showAdventureHelp by remember { mutableStateOf(false) }
    var showLegacyHelp by remember { mutableStateOf(false) }
    var showBackgroundHelp by remember { mutableStateOf(false) }
    var showSizeHelp by remember { mutableStateOf(false) }
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
    // Extras are always reviewable; presets only decide whether they are emitted in a name.
    val extrasFields = remember(fields) {
        fields.toSet() + setOf(
            NamingField.SPECIAL_BACKGROUND,
            NamingField.ADVENTURE_EFFECT,
            NamingField.LEGACY_MOVE,
            NamingField.EVOLUTION_TYPE
        )
    }
    val pokemonSuggestions = remember(draft.candyFamilyName, draft.pokemonName) {
        familySuggester.suggestionsFor(context, draft.candyFamilyName, draft.pokemonName)
    }
    val uniqueFormOptions = remember(draft.pokemonName) { UniquePokemonCatalog.optionsFor(draft.pokemonName) }
    val pvpFamilyCandidates = remember(draft.candyFamilyName, draft.pokemonName) {
        familySuggester.familyMembersFor(context, draft.candyFamilyName, draft.pokemonName)
            .ifEmpty {
                listOfNotNull(draft.pokemonName, draft.candyFamilyName)
            }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
    var derivedData by remember(
        draft.pokemonName,
        draft.candyFamilyName,
        draft.level,
        draft.cp,
        ivMode,
        pvpFamilyCandidates,
        baseAttackIv,
        baseDefenseIv,
        baseStaminaIv
    ) {
        mutableStateOf(draft.applyIvMode(ivMode))
    }
    var derivedLoading by remember { mutableStateOf(false) }
    LaunchedEffect(
        draft.pokemonName,
        draft.candyFamilyName,
        draft.level,
        draft.cp,
        ivMode,
        pvpFamilyCandidates,
        baseAttackIv,
        baseDefenseIv,
        baseStaminaIv
    ) {
        val displayData = draft.applyIvMode(ivMode)
        val rankingInput = displayData.copy(
            attIv = baseAttackIv,
            defIv = baseDefenseIv,
            staIv = baseStaminaIv
        ).recalculateIvPercent()
        derivedData = displayData
        derivedLoading = false
        if (baseAttackIv != null && baseDefenseIv != null && baseStaminaIv != null && pvpFamilyCandidates.isNotEmpty()) {
            derivedLoading = true
            try {
                val rankingResult = withContext(Dispatchers.Default) {
                    buildDerivedReviewData(
                        context = context.applicationContext,
                        data = rankingInput,
                        familyMembers = pvpFamilyCandidates,
                        rankCalculator = pvpRankCalculator,
                        masterIvBadgeCatalog = masterIvBadgeCatalog
                    )
                }
                val hasRecalculatedRanks = rankingResult.familyPvpRanks.isNotEmpty()
                derivedData = displayData.copy(
                    familyPvpRanks = rankingResult.familyPvpRanks.ifEmpty { displayData.familyPvpRanks },
                    pvpLeagueRanks = rankingResult.pvpLeagueRanks.ifEmpty { displayData.pvpLeagueRanks },
                    pvpLeague = if (hasRecalculatedRanks) rankingResult.pvpLeague else displayData.pvpLeague,
                    pvpRank = if (hasRecalculatedRanks) rankingResult.pvpRank else displayData.pvpRank,
                    pvpPokemonName = if (hasRecalculatedRanks) rankingResult.pvpPokemonName else displayData.pvpPokemonName,
                    masterIvBadgeMatch = rankingResult.masterIvBadgeMatch ?: displayData.masterIvBadgeMatch,
                    masterIvBadgeDebugInfo = rankingResult.masterIvBadgeDebugInfo ?: displayData.masterIvBadgeDebugInfo
                )
            } finally {
                derivedLoading = false
            }
        }
    }
    // Move and presentation changes stay responsive while rankings keep their last valid result.
    val displayedData = draft.applyIvMode(ivMode)
    val reviewData = displayedData.copy(
        familyPvpRanks = derivedData.familyPvpRanks.ifEmpty { displayedData.familyPvpRanks },
        pvpLeagueRanks = derivedData.pvpLeagueRanks.ifEmpty { displayedData.pvpLeagueRanks },
        pvpLeague = displayedData.pvpLeague ?: derivedData.pvpLeague,
        pvpRank = displayedData.pvpRank ?: derivedData.pvpRank,
        pvpPokemonName = displayedData.pvpPokemonName ?: derivedData.pvpPokemonName,
        masterIvBadgeMatch = displayedData.masterIvBadgeMatch ?: derivedData.masterIvBadgeMatch,
        masterIvBadgeDebugInfo = displayedData.masterIvBadgeDebugInfo ?: derivedData.masterIvBadgeDebugInfo
    )
    fun sameRankSpecies(left: String?, right: String?): Boolean = left != null && right != null &&
        pvpRankCalculator.canonicalName(context, left) == pvpRankCalculator.canonicalName(context, right)
    val pvpRankSpecies = remember(reviewData.familyPvpRanks, reviewData.pvpLeagueRanks) {
        reviewRankSpecies(context, reviewData, pvpRankCalculator)
    }
    val selectedPvpSpecies = reviewMoveSpecies(draft)
    var moveSetState by remember(selectedPvpSpecies, language) {
        mutableStateOf<LoadState<PokemonMoveSet>>(LoadState.Loading)
    }
    val moveSet = (moveSetState as? LoadState.Success)?.value
        ?: PokemonMoveSet(emptyList(), emptyList())
    val moveStatusMessage = PokemonMoveLoadFeedback.message(
        state = moveSetState,
        pokemonName = selectedPvpSpecies,
        language = language
    )
    LaunchedEffect(context, selectedPvpSpecies, language) {
        moveSetState = LoadState.Loading
        val pokemonName = selectedPvpSpecies?.trim().orEmpty()
        if (pokemonName.isBlank()) {
            moveSetState = LoadState.Empty
        } else {
            try {
                val loadedMoves = withContext(Dispatchers.IO) {
                    PokemonMoveRepository.load(context, pokemonName, language)
                }
                if (loadedMoves.fastMoves.isEmpty() && loadedMoves.chargedMoves.isEmpty()) {
                    moveSetState = LoadState.Empty
                } else {
                    moveSetState = LoadState.Success(loadedMoves)
                    // Options are usable before optional statistics finish loading.
                    try {
                        val enriched = withContext(Dispatchers.IO) {
                            PokemonMoveRepository.enrich(context, loadedMoves, language)
                        }
                        moveSetState = LoadState.Success(enriched)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        PokemonMoveLoadFeedback.reportFailure(pokemonName, exception)
                    }

                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                PokemonMoveLoadFeedback.reportFailure(pokemonName, exception)
                moveSetState = LoadState.Error(exception)
            }
        }
    }
    val fastMoveRating = remember(draft.selectedFastMove, moveSet.fastMoves) {
        moveSet.fastMoves.firstOrNull { it.name == draft.selectedFastMove }?.rating
    }
    val chargedMoveRating = remember(draft.selectedChargedMove, moveSet.chargedMoves) {
        moveSet.chargedMoves.firstOrNull { it.name == draft.selectedChargedMove }?.rating
    }
    val previewUsesLegacyMove = remember(configs) {
        configs.any { config ->
            config.effectiveBlocks().any { block ->
                block.field == NamingField.LEGACY_MOVE || block.field == NamingField.LEGACY_MOVE_NAME
            }
        }
    }
    val legacyMoveSymbol = remember(configs) {
        configs.firstNotNullOfOrNull { config ->
            config.symbols["LEGACY"]?.takeIf { it.isNotBlank() }
        } ?: com.mewname.app.model.defaultSymbols()["LEGACY"].orEmpty()
    }
    LaunchedEffect(selectedPvpSpecies, moveSetState) {
        if (moveSetState !is LoadState.Success && moveSetState !is LoadState.Empty) return@LaunchedEffect
        val nextFastMove = draft.selectedFastMove?.takeIf { selected ->
            moveSet.fastMoves.any { it.name == selected }
        }
        val nextChargedMove = draft.selectedChargedMove?.takeIf { selected ->
            moveSet.chargedMoves.any { it.name == selected }
        }
        if (nextFastMove != draft.selectedFastMove || nextChargedMove != draft.selectedChargedMove) {
            draft = draft.copy(
                selectedFastMove = nextFastMove,
                selectedChargedMove = nextChargedMove
            )
        }
    }
    fun selectPvpLeague(league: PvpLeague?) {
        val selectedSpeciesRank = league?.let { selected ->
            reviewData.familyPvpRanks
                .filter { it.league == selected && it.eligible && it.rank != null }
                .minByOrNull { it.rank ?: Int.MAX_VALUE }
        }
        val selectedLeagueRank = if (selectedSpeciesRank == null && league != null) {
            reviewData.pvpLeagueRanks.firstOrNull { it.league == league && it.eligible }
        } else {
            null
        }
        draft = draft.copy(
            pvpLeague = league,
            pvpRank = selectedSpeciesRank?.rank ?: selectedLeagueRank?.rank,
            pvpPokemonName = selectedSpeciesRank?.pokemonName ?: selectedLeagueRank?.pokemonName
        )
    }
    val bestMasterLabel = lt(language, "Melhor combinacao", "Best combination", "Mejor combinacion")
    val otherMasterLabel = lt(language, "Outra combinacao", "Other combination", "Otra combinacion")
    val unknownLabel = lt(language, "Nao identificado", "Not identified", "No identificado")
    val generatedSuggestions = remember(reviewData, configs) {
        val reviewedDraft = reviewData.recalculateIvPercent().normalizeReviewData()
        configs.mapNotNull { config ->
            generator.generate(reviewedDraft, config)
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let { config.name to it }
        }
    }
    val hasPvpTab = listOf(NamingField.PVP_LEAGUE, NamingField.PVP_RANK).any { it in fields }
    val hasIvModeField = listOf(NamingField.MASTER_IV_BADGE, NamingField.SHADOW, NamingField.PURIFIED, NamingField.PURIFY_MARKER).any { it in fields }
    val hasExtrasTab = listOf(
        NamingField.TYPE,
        NamingField.FAVORITE,
        NamingField.LUCKY,
        NamingField.SIZE,
        NamingField.SPECIAL_BACKGROUND,
        NamingField.ADVENTURE_EFFECT,
        NamingField.LEGACY_MOVE,
        NamingField.LEGACY_MOVE_NAME,
        NamingField.EVOLUTION_TYPE
    ).any { it in fields }
    val reviewTabs = remember(fields, hasPvpTab, hasExtrasTab) {
        buildList {
            add(ReviewTab.BASIC)
            if (hasPvpTab) add(ReviewTab.PVP)
            if (hasExtrasTab) add(ReviewTab.EXTRAS)
        }
    }
    var selectedReviewTab by remember(fields) { mutableStateOf(ReviewTab.BASIC) }
    var activeOptionPicker by remember(language) { mutableStateOf<ReviewOptionPicker?>(null) }
    val activeReviewTab = selectedReviewTab.takeIf { it in reviewTabs } ?: ReviewTab.BASIC
    val selectedReviewTabIndex = reviewTabs.indexOf(activeReviewTab).coerceAtLeast(0)
    LaunchedEffect(ivMode, baseAttackIv, baseDefenseIv, baseStaminaIv) {
        val effectiveAttack = effectiveIvForMode(baseAttackIv, ivMode)
        val effectiveDefense = effectiveIvForMode(baseDefenseIv, ivMode)
        val effectiveStamina = effectiveIvForMode(baseStaminaIv, ivMode)
        if (draft.attIv != effectiveAttack || draft.defIv != effectiveDefense || draft.staIv != effectiveStamina) {
            draft = draft.copy(
                attIv = effectiveAttack,
                defIv = effectiveDefense,
                staIv = effectiveStamina
            ).recalculateIvPercent()
        }
    }
    val maxCardHeight = (LocalConfiguration.current.screenHeightDp.dp - 32.dp).coerceAtLeast(360.dp)
    val confirmReviewedData = {
        onConfirm(reviewData.recalculateIvPercent().normalizeReviewData())
    }

    Box(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalReviewOptionPicker provides { activeOptionPicker = it }) {
        Card(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = maxCardHeight)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SuggestedNamesBlock(
                    suggestions = generatedSuggestions,
                    language = language,
                    onSuggestionSelected = { confirmReviewedData() },
                    onCancel = onCancel,
                    onExportLog = onExportLog?.let { export -> { export(selectedDebugFields) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 1.dp
                ) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedReviewTabIndex,
                        edgePadding = 0.dp,
                        divider = {}
                    ) {
                        reviewTabs.forEach { tab ->
                            Tab(
                                selected = activeReviewTab == tab,
                                onClick = { selectedReviewTab = tab },
                                text = {
                                    Text(
                                        text = when (tab) {
                                            ReviewTab.BASIC -> lt(language, "Nome e IV", "Name and IV", "Nombre e IV")
                                            ReviewTab.PVP -> lt(language, "Liga e ranking", "League and rank", "Liga y ranking")
                                            ReviewTab.EXTRAS -> lt(language, "Extras", "Extras", "Extras")
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
            if (activeReviewTab == ReviewTab.BASIC) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ReviewTextRow {
                    if (NamingField.EVOLVE_MARKER in fields) {
                        Row(
                            modifier = Modifier.weight(2f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            PokemonSuggestionField(
                                label = NamingField.POKEMON_NAME.localizedLabel(language),
                                value = draft.pokemonName.orEmpty(),
                                suggestions = pokemonSuggestions,
                                expanded = pokemonExpanded,
                                onExpandedChange = { pokemonExpanded = it },
                                onValueChange = { draft = draft.copy(pokemonName = it.ifBlank { null }) },
                                useOptionModal = false,
                                modifier = Modifier.weight(1f)
                            )
                            MarkerCheckboxField(
                                label = NamingField.EVOLVE_MARKER.localizedLabel(language),
                                checked = draft.shouldEvolve,
                                onCheckedChange = { draft = draft.copy(shouldEvolve = it) },
                                modifier = Modifier.width(52.dp)
                            )
                        }
                    } else {
                        PokemonSuggestionField(
                            label = NamingField.POKEMON_NAME.localizedLabel(language),
                            value = draft.pokemonName.orEmpty(),
                            suggestions = pokemonSuggestions,
                            expanded = pokemonExpanded,
                            onExpandedChange = { pokemonExpanded = it },
                            onValueChange = { draft = draft.copy(pokemonName = it.ifBlank { null }) },
                            useOptionModal = false,
                            modifier = Modifier.weight(2f)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        FieldLabelRow(label = NamingField.GENDER.localizedLabel(language))
                        WeightedToggleRow(
                            items = listOf(
                                WeightedToggleItem(
                                    label = "♂",
                                    selected = draft.gender == Gender.MALE,
                                    onClick = {
                                        draft = draft.withSelectedGender(
                                            if (draft.gender == Gender.MALE) Gender.GENDERLESS else Gender.MALE
                                        )
                                    }
                                ),
                                WeightedToggleItem(
                                    label = "♀",
                                    selected = draft.gender == Gender.FEMALE,
                                    onClick = {
                                        draft = draft.withSelectedGender(
                                            if (draft.gender == Gender.FEMALE) Gender.GENDERLESS else Gender.FEMALE
                                        )
                                    }
                                )
                            )
                        )
                    }
                    CompactField(
                        label = NamingField.LEVEL.localizedLabel(language),
                        value = draft.level?.formatLevelDebug().orEmpty(),
                        onValueChange = { draft = draft.copy(level = it.replace(",", ".").toDoubleOrNull()) },
                        readOnly = true,
                        onClick = { showLevelPicker = true },
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
                                label = NamingField.CP.localizedLabel(language),
                                value = draft.cp?.toString().orEmpty(),
                                onValueChange = { draft = draft.copy(cp = it.toIntOrNull()) },
                                modifier = Modifier.weight(0.9f)
                            )
                        }
                    }
                }
                if (uniqueFormOptions.isNotEmpty()) {
                    SelectionDropdownField(
                        label = NamingField.UNIQUE_FORM.localizedLabel(language),
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
                            useOptionModal = false,
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
            }

            if (activeReviewTab == ReviewTab.BASIC) {
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
            if (hasIvModeField) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (NamingField.MASTER_IV_BADGE in fields) {
                        SelectionDropdownField(
                            label = "IV Master",
                            value = when (reviewData.masterIvBadgeMatch) {
                                true -> bestMasterLabel
                                false -> otherMasterLabel
                                null -> "-"
                            },
                            options = listOf("-", bestMasterLabel, otherMasterLabel),
                            onSelected = { value ->
                                draft = draft.copy(
                                    masterIvBadgeMatch = when (value) {
                                        bestMasterLabel -> true
                                        otherMasterLabel -> false
                                        else -> null
                                    }
                                )
                            },
                            headerTrailing = {
                                UnownHeaderIcon(
                                    selected = NamingField.MASTER_IV_BADGE in selectedDebugFields,
                                    onClick = {
                                        selectedDebugFields = selectedDebugFields.toggleField(NamingField.MASTER_IV_BADGE)
                                    },
                                    contentDescription = "Selecionar log de IV Master"
                                )
                            },
                            useOptionModal = false,
                            modifier = Modifier.weight(1.45f)
                        )
                    }
                    SelectionDropdownField(
                        label = lt(language, "Forma", "Form", "Forma"),
                        value = ivMode.localizedLabel(language),
                        options = ReviewIvMode.entries.map { it.localizedLabel(language) },
                        onSelected = { value ->
                            ReviewIvMode.entries.firstOrNull { it.localizedLabel(language) == value }?.let { selected ->
                                ivMode = selected
                                draft = draft.copy(
                                    isShadow = selected == ReviewIvMode.SHADOW,
                                    isPurified = selected == ReviewIvMode.PURIFIED
                                )
                            }
                        },
                            useOptionModal = false,
                        modifier = Modifier.weight(1f)
                    )
                    if (NamingField.PURIFY_MARKER in fields) {
                        MarkerCheckboxField(
                            label = NamingField.PURIFY_MARKER.localizedLabel(language),
                            checked = draft.shouldPurify,
                            onCheckedChange = { draft = draft.copy(shouldPurify = it) },
                            modifier = Modifier.width(56.dp)
                        )
                    }
                }
                    if (NamingField.MASTER_IV_BADGE in selectedDebugFields) {
                        reviewData.masterIvBadgeDebugInfo?.let { info ->
                            Text(
                                text = buildString {
                                    append("${lt(language, "Detectado", "Detected", "Detectado")}: ")
                                    append(
                                        when (info.isBestMatch) {
                                            true -> bestMasterLabel
                                            false -> otherMasterLabel
                                            null -> "-"
                                        }
                                    )
                                    if (info.expectedAttack != null && info.expectedDefense != null && info.expectedStamina != null) {
                                        append(" | ${lt(language, "Esperado", "Expected", "Esperado")}: ${info.expectedAttack}/${info.expectedDefense}/${info.expectedStamina}")
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
                NamingField.LUCKY
            ).any { it in fields }
            if (activeReviewTab == ReviewTab.EXTRAS) {
            if (hasAttributeSection) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FieldHeaderRow(
                        label = lt(language, "Atributos", "Attributes", "Atributos"),
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
                                label = NamingField.FAVORITE.localizedLabel(language),
                                selected = draft.isFavorite,
                                onClick = { draft = draft.copy(isFavorite = !draft.isFavorite) },
                                compact = true,
                                modifier = Modifier.weight(1f)
                            )
                            cellsInFirstRow++
                        }
                        if (NamingField.LUCKY in fields) {
                            ToggleChip(
                                label = NamingField.LUCKY.localizedLabel(language),
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
            }

            if (activeReviewTab == ReviewTab.EXTRAS) {
                // Keep every extra control in one measured block so no section is dropped by the overlay layout.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldHeaderRow(
                        label = NamingField.SIZE.localizedLabel(language),
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
                            WeightedToggleItem(label, draft.size == size) {
                                draft = draft.copy(size = if (draft.size == size) PokemonSize.NORMAL else size)
                            }
                        }
                    )
                    if (showSizeHelp) {
                        SizeHelpPanel(size = draft.size, info = draft.sizeDebugInfo)
                    }
                    ReviewTextRow {
                        SelectionDropdownField(
                            label = NamingField.SPECIAL_BACKGROUND.localizedLabel(language),
                            value = draft.specialBackgroundSelectionLabel(language),
                            options = specialBackgroundSelectionOptions(language),
                            onSelected = { value ->
                                val type = specialBackgroundTypeFromSelection(value, language)
                                draft = if (type == null) {
                                    draft.copy(hasSpecialBackground = false, specialBackgroundType = null)
                                } else {
                                    draft.copy(hasSpecialBackground = true, specialBackgroundType = type)
                                }
                            },
                            useOptionModal = false,
                            modifier = Modifier.weight(1f)
                        )
                        LabeledToggleChipField(
                            label = lt(language, "Marcadores", "Markers", "Marcadores"),
                            chipLabel = NamingField.ADVENTURE_EFFECT.localizedLabel(language),
                            selected = draft.hasAdventureEffect,
                            onClick = { draft = draft.copy(hasAdventureEffect = !draft.hasAdventureEffect) },
                            headerSelected = NamingField.ADVENTURE_EFFECT in selectedDebugFields,
                            onHeaderClick = {
                                selectedDebugFields = selectedDebugFields.toggleField(NamingField.ADVENTURE_EFFECT)
                                showAdventureHelp = !showAdventureHelp
                            },
                            modifier = Modifier.weight(1f)
                        )
                        LabeledToggleChipField(
                            label = NamingField.LEGACY_MOVE.localizedLabel(language),
                            chipLabel = draft.legacyDebugInfo?.matchedLegacyMove
                                ?.takeIf { it.isNotBlank() }
                                ?: NamingField.LEGACY_MOVE.localizedLabel(language),
                            selected = draft.hasLegacyMove,
                            onClick = { draft = draft.copy(hasLegacyMove = !draft.hasLegacyMove) },
                            headerSelected = NamingField.LEGACY_MOVE in selectedDebugFields,
                            onHeaderClick = {
                                selectedDebugFields = selectedDebugFields.toggleField(NamingField.LEGACY_MOVE)
                                showLegacyHelp = !showLegacyHelp
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    FieldHeaderRow(
                        label = NamingField.EVOLUTION_TYPE.localizedLabel(language),
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
                            WeightedToggleItem(lt(language, "Estagio 1", "Stage 1", "Etapa 1"), EvolutionFlag.STAGE1 in draft.evolutionFlags) {
                                draft = draft.copy(evolutionFlags = draft.evolutionFlags.toggleEvolutionStage(EvolutionFlag.STAGE1))
                            },
                            WeightedToggleItem(lt(language, "Estagio 2", "Stage 2", "Etapa 2"), EvolutionFlag.STAGE2 in draft.evolutionFlags) {
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
                }
            }

            if (false) {
                if (NamingField.SIZE in fields) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FieldHeaderRow(
                        label = NamingField.SIZE.localizedLabel(language),
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
            }
            if (activeReviewTab == ReviewTab.PVP) {
                if (NamingField.PVP_LEAGUE in fields || NamingField.PVP_RANK in fields) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (derivedLoading) {
                            Text(
                                lt(language, "Recalculando rankings...", "Recalculating rankings...", "Recalculando rankings..."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (pvpRankSpecies.isEmpty()) {
                            if (NamingField.PVP_RANK in fields) {
                                CompactField(
                                    label = "",
                                    value = reviewData.pvpRank?.toString().orEmpty(),
                                    onValueChange = { draft = draft.copy(pvpRank = it.toIntOrNull()) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            val leagueRows = listOf(
                                PvpLeague.LITTLE to "Copinha",
                                PvpLeague.GREAT to "Great",
                                PvpLeague.ULTRA to "Ultra",
                                PvpLeague.MASTER to "Master"
                            )
                            val leagueColumnWidth = 68.dp
                            val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val speciesColumnWidths = pvpRankSpecies.associateWith { pokemonName ->
                                val titleWidth = textMeasurer.measure(
                                    com.mewname.app.domain.pokemonDisplayName(pokemonName),
                                    style = MaterialTheme.typography.labelSmall,
                                    softWrap = false
                                ).size.width
                                val rankWidth = leagueRows.maxOf { (league, _) ->
                                    val speciesRank = reviewData.familyPvpRanks.firstOrNull { sameRankSpecies(it.pokemonName, pokemonName) && it.league == league }
                                    val leagueRank = reviewData.pvpLeagueRanks.firstOrNull { it.league == league && sameRankSpecies(it.pokemonName, pokemonName) }
                                    val rank = if (speciesRank != null) speciesRank.rank else leagueRank?.rank
                                    textMeasurer.measure(rank?.toString() ?: "—", style = TextStyle(fontSize = 10.sp)).size.width
                                }
                                with(density) { maxOf(titleWidth, rankWidth).toDp() } + 16.dp
                            }
                            val tableWidth = speciesColumnWidths.values.fold(leagueColumnWidth) { total, width -> total + width } + (2.dp * pvpRankSpecies.size)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Column(
                                    modifier = Modifier.width(tableWidth),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Row(
                                            modifier = Modifier.width(leagueColumnWidth),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = NamingField.PVP_LEAGUE.localizedLabel(language),
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            UnownHeaderIcon(
                                                selected = selectedDebugFields.any { it == NamingField.PVP_LEAGUE || it == NamingField.PVP_RANK },
                                                onClick = {
                                                    selectedDebugFields = selectedDebugFields.toggleAll(setOf(NamingField.PVP_LEAGUE, NamingField.PVP_RANK))
                                                    showPvpHelp = !showPvpHelp
                                                },
                                                contentDescription = "Logs PvP"
                                            )
                                        }
                                        pvpRankSpecies.forEach { pokemonName ->
                                            Text(
                                                text = com.mewname.app.domain.pokemonDisplayName(pokemonName),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.width(speciesColumnWidths.getValue(pokemonName))
                                            )
                                        }
                                    }
                                    leagueRows.forEach { (league, leagueLabel) ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            CompactSelectableField(
                                                value = leagueLabel,
                                                active = reviewData.pvpLeague == league,
                                                onClick = { selectPvpLeague(league) },
                                                modifier = Modifier.width(leagueColumnWidth)
                                            )
                                            pvpRankSpecies.forEach { pokemonName ->
                                                val speciesRank = reviewData.familyPvpRanks.firstOrNull { sameRankSpecies(it.pokemonName, pokemonName) && it.league == league }
                                                val leagueRank = reviewData.pvpLeagueRanks.firstOrNull { it.league == league && sameRankSpecies(it.pokemonName, pokemonName) }
                                                val rank = if (speciesRank != null) speciesRank.rank else leagueRank?.rank
                                                val eligible = speciesRank?.eligible ?: leagueRank?.eligible ?: false
                                                val rankedPokemon = speciesRank?.pokemonName ?: leagueRank?.pokemonName ?: pokemonName
                                                CompactSelectableField(
                                                    value = rank?.toString() ?: "—",
                                                    active = reviewData.pvpLeague == league && reviewData.pvpRank == rank && sameRankSpecies(reviewData.pvpPokemonName, rankedPokemon),
                                                    struckThrough = (speciesRank != null || leagueRank != null) && !eligible,
                                                    onClick = {
                                                        if (eligible && rank != null) {
                                                            draft = draft.copy(pvpLeague = league, pvpRank = rank, pvpPokemonName = rankedPokemon)
                                                        } else {
                                                            showPvpHelp = true
                                                        }
                                                    },
                                                    modifier = Modifier.width(speciesColumnWidths.getValue(pokemonName))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        if (showPvpHelp) {
                            PvpHelpPanel(leagueRanks = reviewData.pvpLeagueRanks, speciesRanks = reviewData.familyPvpRanks)
                        }
                    }
                }

                Text(
                    text = lt(language, "Ataques de", "Moves for", "Ataques de") + " " +
                        selectedPvpSpecies?.let { com.mewname.app.domain.pokemonDisplayName(it) }.orEmpty(),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                    val fastMoveOptions = remember(moveSet.fastMoves, legacyMoveSymbol) {
                        moveDropdownOptions(moveSet.fastMoves, legacyMoveSymbol)
                    }
                    val chargedMoveOptions = remember(moveSet.chargedMoves, legacyMoveSymbol) {
                        moveDropdownOptions(moveSet.chargedMoves, legacyMoveSymbol)
                    }
                    MoveDropdownField(
                        label = lt(language, "Ataque rapido", "Fast move", "Ataque rapido"),
                        selectedValue = draft.selectedFastMove,
                        options = fastMoveOptions,
                        emptyLabel = unknownLabel,
                        onSelected = { moveName ->
                            draft = draft.copy(selectedFastMove = moveName).applySelectedLegacyMove(
                                selectedMoveName = moveName,
                                availableMoves = moveSet.fastMoves,
                                previewUsesLegacyMove = previewUsesLegacyMove
                            )
                        },
                        modifier = Modifier.weight(1f),
                        ratingLabel = fastMoveRating?.label,
                        statusMessage = moveStatusMessage
                    )
                    MoveDropdownField(
                        label = lt(language, "Ataque carregado", "Charged move", "Ataque cargado"),
                        selectedValue = draft.selectedChargedMove,
                        options = chargedMoveOptions,
                        emptyLabel = unknownLabel,
                        onSelected = { moveName ->
                            draft = draft.copy(selectedChargedMove = moveName).applySelectedLegacyMove(
                                selectedMoveName = moveName,
                                availableMoves = moveSet.chargedMoves,
                                previewUsesLegacyMove = previewUsesLegacyMove
                            )
                        },
                        modifier = Modifier.weight(1f),
                        ratingLabel = chargedMoveRating?.label,
                        statusMessage = moveStatusMessage
                    )
                }
            }
            if (activeReviewTab == ReviewTab.BASIC) {
            if (isVivillonReviewFamily(draft.pokemonName)) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    FieldHeaderRow(
                        label = NamingField.VIVILLON_PATTERN.localizedLabel(language),
                        selected = NamingField.VIVILLON_PATTERN in selectedDebugFields,
                        onMarkerClick = {
                            selectedDebugFields = selectedDebugFields.toggleField(NamingField.VIVILLON_PATTERN)
                            showVivillonHelp = !showVivillonHelp
                        }
                    )
                    SelectionDropdownField(
                        label = "",
                        value = draft.vivillonPattern?.label ?: unknownLabel,
                        options = listOf(unknownLabel) + VivillonPattern.entries.map { it.label },
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
            }

            val hasBooleanSection = listOf(
                NamingField.SPECIAL_BACKGROUND,
                NamingField.ADVENTURE_EFFECT,
                NamingField.LEGACY_MOVE,
                NamingField.LEGACY_MOVE_NAME
            ).any { it in extrasFields }
            if (false) {
            if (hasBooleanSection) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                        val markerFields = linkedSetOf(
                            NamingField.SPECIAL_BACKGROUND,
                            NamingField.ADVENTURE_EFFECT,
                            NamingField.LEGACY_MOVE,
                            NamingField.LEGACY_MOVE_NAME
                        ).intersect(extrasFields)
                        val markerSectionSelected = selectedDebugFields.any { it in markerFields }
                        val toggleMarkerSection = {
                            selectedDebugFields = selectedDebugFields.toggleAll(markerFields)
                            showBackgroundHelp = !showBackgroundHelp
                        }
                        if (NamingField.SPECIAL_BACKGROUND in extrasFields) {
                            SelectionDropdownField(
                                label = NamingField.SPECIAL_BACKGROUND.localizedLabel(language),
                                value = draft.specialBackgroundSelectionLabel(language),
                                options = specialBackgroundSelectionOptions(language),
                                onSelected = { value ->
                                    val selectedType = specialBackgroundTypeFromSelection(value, language)
                                    draft = if (selectedType == null) {
                                        draft.copy(
                                            hasSpecialBackground = false,
                                            specialBackgroundType = null
                                        )
                                    } else {
                                        draft.copy(
                                            hasSpecialBackground = true,
                                            specialBackgroundType = selectedType
                                        )
                                    }
                                },
                            useOptionModal = false,
                                modifier = Modifier.width(112.dp)
                            )
                        }
                        if (NamingField.ADVENTURE_EFFECT in extrasFields) {
                            LabeledToggleChipField(
                                label = lt(language, "Marcadores", "Markers", "Marcadores"),
                                chipLabel = NamingField.ADVENTURE_EFFECT.localizedLabel(language),
                                selected = draft.hasAdventureEffect,
                                onClick = { draft = draft.copy(hasAdventureEffect = !draft.hasAdventureEffect) },
                                headerSelected = markerSectionSelected,
                                onHeaderClick = toggleMarkerSection,
                                modifier = Modifier.width(112.dp)
                            )
                        } else if (NamingField.LEGACY_MOVE in extrasFields) {
                            FieldHeaderSpacer(
                                label = lt(language, "Marcadores", "Markers", "Marcadores"),
                                selected = markerSectionSelected,
                                onMarkerClick = toggleMarkerSection,
                                modifier = Modifier.width(112.dp)
                            )
                        }
                        if (NamingField.LEGACY_MOVE in extrasFields) {
                            val legacyMoveLabel = draft.legacyDebugInfo
                                ?.matchedLegacyMove
                                ?.takeIf { it.isNotBlank() }
                                ?: NamingField.LEGACY_MOVE.localizedLabel(language)
                            LabeledToggleChipField(
                                label = NamingField.LEGACY_MOVE.localizedLabel(language),
                                chipLabel = legacyMoveLabel,
                                selected = draft.hasLegacyMove,
                                onClick = { draft = draft.copy(hasLegacyMove = !draft.hasLegacyMove) },
                                headerSelected = NamingField.LEGACY_MOVE in selectedDebugFields,
                                onHeaderClick = {
                                    selectedDebugFields = selectedDebugFields.toggleField(NamingField.LEGACY_MOVE)
                                    showLegacyHelp = !showLegacyHelp
                                },
                                modifier = Modifier.width(112.dp)
                            )
                        }
                    }
                    if (NamingField.LEGACY_MOVE_NAME in extrasFields) {
                        CompactField(
                            label = NamingField.LEGACY_MOVE_NAME.localizedLabel(language),
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

            if (NamingField.EVOLUTION_TYPE in extrasFields) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FieldHeaderRow(
                        label = NamingField.EVOLUTION_TYPE.localizedLabel(language),
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
                            WeightedToggleItem(lt(language, "Estagio 1", "Stage 1", "Etapa 1"), EvolutionFlag.STAGE1 in draft.evolutionFlags) {
                                draft = draft.copy(evolutionFlags = draft.evolutionFlags.toggleEvolutionStage(EvolutionFlag.STAGE1))
                            },
                            WeightedToggleItem(lt(language, "Estagio 2", "Stage 2", "Etapa 2"), EvolutionFlag.STAGE2 in draft.evolutionFlags) {
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

                }


            }
        }
        }
        activeOptionPicker?.current()?.let { picker ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clickable { activeOptionPicker = null }
                    .padding(12.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                SelectionModalDialog(
                    title = picker.title,
                    options = picker.options,
                    selectedValue = picker.selectedValue,
                    message = picker.message,
                    onDismiss = { activeOptionPicker = null },
                    onOptionSelected = { option ->
                        picker.onOptionSelected(option)
                        activeOptionPicker = null
                    },
                    verticalOptions = picker.verticalOptions
                )
            }
        }
        if (activeIvPicker != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { activeIvPicker = null }
                    .padding(12.dp),
                contentAlignment = Alignment.BottomCenter
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
                        when (activeIvPicker) {
                            "atk" -> baseAttackIv = displayIvToBaseIv(selected, ivMode)
                            "def" -> baseDefenseIv = displayIvToBaseIv(selected, ivMode)
                            else -> baseStaminaIv = displayIvToBaseIv(selected, ivMode)
                        }
                        draft = when (activeIvPicker) {
                            "atk" -> draft.copy(attIv = effectiveIvForMode(baseAttackIv, ivMode))
                            "def" -> draft.copy(defIv = effectiveIvForMode(baseDefenseIv, ivMode))
                            else -> draft.copy(staIv = effectiveIvForMode(baseStaminaIv, ivMode))
                        }.recalculateIvPercent()
                        activeIvPicker = null
                    },
                    onDismiss = { activeIvPicker = null }
                )
            }
        }
        if (showLevelPicker) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showLevelPicker = false }
                    .padding(12.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                LevelPickerDialog(
                context = context,
                language = language,
                pokemonName = draft.pokemonName ?: draft.candyFamilyName,
                level = draft.level,
                attack = draft.attIv,
                defense = draft.defIv,
                stamina = draft.staIv,
                rankCalculator = pvpRankCalculator,
                onDismiss = { showLevelPicker = false },
                onLevelSelected = { level ->
                    draft = draft.copy(level = level)
                    showLevelPicker = false
                }
                )
            }
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
private fun SuggestedNamesBlock(
    suggestions: List<Pair<String, String>>,
    language: AppLanguage,
    onSuggestionSelected: (String) -> Unit,
    onCancel: () -> Unit,
    onExportLog: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.26f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    lt(language, "Nomes sugeridos", "Suggested names", "Nombres sugeridos"),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (onExportLog != null) {
                    TextButton(onClick = onExportLog, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                        Text(lt(language, "Exportar log", "Export log", "Exportar log"), fontSize = 11.sp)
                    }
                }
                TextButton(onClick = onCancel, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                    Text(lt(language, "Cancelar", "Cancel", "Cancelar"), fontSize = 11.sp)
                }
            }
            if (suggestions.isEmpty()) {
                Text(
                    lt(
                        language,
                        "Nenhum nome foi gerado com os dados atuais.",
                        "No name was generated with the current data.",
                        "No se genero ningun nombre con los datos actuales."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { (configName, generatedName) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    clipboard.setText(AnnotatedString(generatedName))
                                    Toast.makeText(context, "Copiado!", Toast.LENGTH_SHORT).show()
                                    onSuggestionSelected(generatedName)
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.50f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    configName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    generatedName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
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
private fun PvpHelpPanel(
    leagueRanks: List<PvpLeagueRankInfo>,
    speciesRanks: List<PvpSpeciesRankInfo> = emptyList()
) {
    val context = LocalContext.current
    val orderedLeagues = listOf(PvpLeague.LITTLE, PvpLeague.GREAT, PvpLeague.ULTRA, PvpLeague.MASTER)
    val orderedRanks = orderedLeagues.mapNotNull { league ->
        leagueRanks.firstOrNull { it.league == league }
    }
    val bestBySpecies = speciesRankCardsForLeague(context, speciesRanks, selectedLeague = null)
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