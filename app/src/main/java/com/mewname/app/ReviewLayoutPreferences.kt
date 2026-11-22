package com.mewname.app

import com.mewname.app.model.NamingField
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.horizontalScroll
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val reviewIconSizes = listOf(12, 14, 16, 18, 20, 24)

internal enum class ReviewSelectorStyle { TEXT, ICON_TEXT, ICON }
internal enum class ReviewIconColor(val argb: Long) {
    BLUE(0xFF277FAD), GREEN(0xFF28836F), PURPLE(0xFF7955A6), PINK(0xFFAA477B), ORANGE(0xFFAD641F)
}
internal fun reviewIconHasOwnFrame(path: String) = path.startsWith("types/") || path.startsWith("filter_icons/search_icon_region_")
internal enum class ReviewSelector(val group: Int, val defaultIcon: String, val pt: String, val en: String, val es: String) {
    POKEMON_NAME(6, "", "", "", ""),
    UNOWN_LETTER(6, "", "", "", ""),
    UNIQUE_FORM(6, "", "", "", ""),
    VIVILLON_PATTERN(6, "", "", "", ""),
    POKEDEX_NUMBER(6, "", "", "", ""),
    CP(6, "", "", "", ""),
    IV_PERCENT(6, "", "", "", ""),
    IV_COMBINATION(6, "", "", "", ""),
    LEVEL(6, "", "", "", ""),
    TYPE(6, "", "", "", ""),
    MASTER_IV_BADGE(6, "", "", "", ""),
    PVP_LEAGUE(6, "", "", "", ""),
    PVP_RANK(6, "", "", "", ""),
    LEGACY_MOVE_NAME(6, "", "", "", ""),
    EVOLVE(0, "menu/glass/bulk_evolution.png", "Evoluir", "Evolve", "Evolucionar"),
    PURIFY(0, "collection/purified.png", "Purificar", "Purify", "Purificar"),
    LEGACY(0, "mission_icons/Item_1204.png", "Ataque legado", "Legacy move", "Ataque legado"),
    FAVORITE(1, "filter_icons/favorite.png", "Favorito", "Favorite", "Favorito"),
    LUCKY(1, "collection/lucky.png", "Sortudo", "Lucky", "Con suerte"),
    SHADOW(1, "collection/shadow.png", "Sombroso", "Shadow", "Oscuro"),
    PURIFIED(1, "collection/purified.png", "Purificado", "Purified", "Purificado"),
    ADVENTURE(1, "menu/glass/adventure.png", "Efeito de aventura", "Adventure effect", "Efecto aventura"),
    MALE(2, "filter_icons/app_gender_male.txt", "Masculino", "Male", "Masculino"),
    FEMALE(2, "filter_icons/app_gender_female.txt", "Feminino", "Female", "Femenino"),
    XXS(3, "collection/xxs.png", "XXS", "XXS", "XXS"),
    XS(3, "", "XS", "XS", "XS"),
    XL(3, "", "XL", "XL", "XL"),
    XXL(3, "collection/xxl.png", "XXL", "XXL", "XXL"),
    BG_SPECIAL(4, "collection/background.png", "SPECIAL", "SPECIAL", "SPECIAL"),
    BG_GO_FEST(4, "collection/background.png", "GO_FEST", "GO_FEST", "GO_FEST"),
    BG_WILD_AREA(4, "collection/background.png", "WILD_AREA", "WILD_AREA", "WILD_AREA"),
    BG_LOCATION(4, "collection/background.png", "LOCATION", "LOCATION", "LOCATION"),
    BG_MEGA_EVOLUTION(4, "collection/background.png", "MEGA_EVOLUTION", "MEGA_EVOLUTION", "MEGA_EVOLUTION"),
    BG_COMMUNITY_DAY(4, "collection/background.png", "COMMUNITY_DAY", "COMMUNITY_DAY", "COMMUNITY_DAY"),
    BG_ANNIVERSARY(4, "collection/background.png", "ANNIVERSARY", "ANNIVERSARY", "ANNIVERSARY"),
    BG_GO_TOUR(4, "collection/background.png", "GO_TOUR", "GO_TOUR", "GO_TOUR"),
    BG_SEASON(4, "collection/background.png", "SEASON", "SEASON", "SEASON"),
    BG_TEAM_LEADER(4, "collection/background.png", "TEAM_LEADER", "TEAM_LEADER", "TEAM_LEADER"),
    BABY(5, "", "Baby", "Baby", "Baby"),
    STAGE1(5, "", "Estagio 1", "Stage 1", "Etapa 1"),
    STAGE2(5, "", "Estagio 2", "Stage 2", "Etapa 2"),
    MEGA(5, "collection/mega.png", "Mega", "Mega", "Mega"),
    DYNAMAX(5, "collection/dynamax.png", "Dynamax", "Dynamax", "Dynamax"),
    GIGANTAMAX(5, "collection/gigantamax.png", "Gigantamax", "Gigantamax", "Gigantamax"),
    TERASTRAL(5, "", "Terastal", "Terastal", "Terastal");
    fun label(language: com.mewname.app.domain.AppLanguage): String = when (this) {
        POKEMON_NAME, UNOWN_LETTER, UNIQUE_FORM, VIVILLON_PATTERN, POKEDEX_NUMBER, CP, IV_PERCENT, IV_COMBINATION, LEVEL, TYPE, MASTER_IV_BADGE, PVP_LEAGUE, PVP_RANK, LEGACY_MOVE_NAME -> NamingField.valueOf(name).localizedLabel(language)
        FAVORITE -> com.mewname.app.model.NamingField.FAVORITE.localizedLabel(language)
        LUCKY -> com.mewname.app.model.NamingField.LUCKY.localizedLabel(language)
        LEGACY -> com.mewname.app.model.NamingField.LEGACY_MOVE.localizedLabel(language)
        ADVENTURE -> com.mewname.app.model.NamingField.ADVENTURE_EFFECT.localizedLabel(language)
        else -> if (name.startsWith("BG_")) com.mewname.app.model.SpecialBackgroundType.valueOf(name.removePrefix("BG_")).localizedLabel(language)
            else lt(language, pt, en, es)
    }
    companion object {
        fun forLabel(label: String, language: com.mewname.app.domain.AppLanguage): ReviewSelector? = when (label) {
            "♂" -> MALE
            "♀" -> FEMALE
            "IV %" -> IV_PERCENT
            "Atk", "Atq", "Def", "HP" -> IV_COMBINATION
            "PC", "CP" -> CP
            else -> entries.firstOrNull { it.label(language).equals(label, ignoreCase = true) }
        }
    }
}
internal fun reviewSelectorForField(field: NamingField): ReviewSelector = when (field) {
    NamingField.POKEMON_NAME -> ReviewSelector.POKEMON_NAME
    NamingField.UNOWN_LETTER -> ReviewSelector.UNOWN_LETTER
    NamingField.UNIQUE_FORM -> ReviewSelector.UNIQUE_FORM
    NamingField.VIVILLON_PATTERN -> ReviewSelector.VIVILLON_PATTERN
    NamingField.POKEDEX_NUMBER -> ReviewSelector.POKEDEX_NUMBER
    NamingField.CP -> ReviewSelector.CP
    NamingField.IV_PERCENT -> ReviewSelector.IV_PERCENT
    NamingField.IV_COMBINATION -> ReviewSelector.IV_COMBINATION
    NamingField.LEVEL -> ReviewSelector.LEVEL
    NamingField.TYPE -> ReviewSelector.TYPE
    NamingField.MASTER_IV_BADGE -> ReviewSelector.MASTER_IV_BADGE
    NamingField.PVP_LEAGUE -> ReviewSelector.PVP_LEAGUE
    NamingField.PVP_RANK -> ReviewSelector.PVP_RANK
    NamingField.LEGACY_MOVE_NAME -> ReviewSelector.LEGACY_MOVE_NAME
    NamingField.GENDER -> ReviewSelector.MALE
    NamingField.FAVORITE -> ReviewSelector.FAVORITE
    NamingField.LUCKY -> ReviewSelector.LUCKY
    NamingField.SHADOW -> ReviewSelector.SHADOW
    NamingField.PURIFIED -> ReviewSelector.PURIFIED
    NamingField.SPECIAL_BACKGROUND -> ReviewSelector.BG_SPECIAL
    NamingField.ADVENTURE_EFFECT -> ReviewSelector.ADVENTURE
    NamingField.EVOLVE_MARKER -> ReviewSelector.EVOLVE
    NamingField.PURIFY_MARKER -> ReviewSelector.PURIFY
    NamingField.SIZE -> ReviewSelector.XXS
    NamingField.LEGACY_MOVE -> ReviewSelector.LEGACY
    NamingField.EVOLUTION_TYPE -> ReviewSelector.BABY
}

internal val LocalReviewSelectorCustomization = staticCompositionLocalOf { false }

internal data class ReviewTagGroup(val key: String, val selectors: List<ReviewSelector>) {
    fun label(language: com.mewname.app.domain.AppLanguage): String = when (key) {
        "gender" -> lt(language, "Gênero", "Gender", "Género")
        "size" -> lt(language, "Tamanho", "Size", "Tamaño")
        "background" -> lt(language, "Fundo especial", "Special background", "Fondo especial")
        "evolution" -> lt(language, "Evoluções", "Evolutions", "Evoluciones")
        "form" -> lt(language, "Sombroso / Purificado", "Shadow / Purified", "Oscuro / Purificado")
        else -> selectors.first().label(language)
    }
}
internal val reviewTagGroups = buildList {
    ReviewSelector.entries.filter { it.group == 6 }.forEach { add(ReviewTagGroup(it.name, listOf(it))) }
    listOf(ReviewSelector.EVOLVE, ReviewSelector.PURIFY, ReviewSelector.LEGACY, ReviewSelector.FAVORITE, ReviewSelector.LUCKY, ReviewSelector.ADVENTURE)
        .forEach { add(ReviewTagGroup(it.name, listOf(it))) }
    add(ReviewTagGroup("form", listOf(ReviewSelector.SHADOW, ReviewSelector.PURIFIED)))
    add(ReviewTagGroup("gender", listOf(ReviewSelector.MALE, ReviewSelector.FEMALE)))
    add(ReviewTagGroup("size", ReviewSelector.entries.filter { it.group == 3 }))
    add(ReviewTagGroup("background", ReviewSelector.entries.filter { it.group == 4 }))
    add(ReviewTagGroup("evolution", ReviewSelector.entries.filter { it.group == 5 }))
}
private val reviewGroupOwner = reviewTagGroups.flatMap { group -> group.selectors.map { it to group.selectors.first() } }.toMap()

internal data class ReviewLayout(val compact: Boolean = false,
    val styles: Map<ReviewSelector, ReviewSelectorStyle> = emptyMap(),
    val icons: Map<ReviewSelector, String> = emptyMap(),
    val toggles: Map<ReviewSelector, Boolean> = emptyMap(),
    val circles: Set<ReviewSelector> = emptySet(),
    val colors: Map<ReviewSelector, ReviewIconColor> = emptyMap(),
    val segmentedGender: Boolean = true,
    val order: List<ReviewSelector> = emptyList(),
    val tabs: List<ReviewLayoutTab>? = null,
    val hiddenFields: Set<String> = emptySet(),
    val hiddenOptions: Set<ReviewSelector> = emptySet(),
    val spans: Map<String, Int> = emptyMap(),
    val iconSizes: Map<ReviewSelector, Int> = emptyMap(),
    val heights: Map<String, Int> = emptyMap(),
    val segmentedForm: Boolean = false) {
    fun segmented(key: String) = (key == "gender" && segmentedGender) || (key == "form" && segmentedForm)
    private fun visibleFieldIcons(key: String): List<ReviewSelector> = reviewScreenGroup(key)?.selectors.orEmpty()
        .filter { optionVisible(it) && icon(it) != null && (style(it) != ReviewSelectorStyle.TEXT || key == "gender") }
    fun minimumFieldWidth(key: String): Float {
        val icons = visibleFieldIcons(key)
        val content = if (segmented(key)) icons.sumOf { iconSize(it) + 8 }.toFloat()
            else icons.maxOfOrNull { iconSize(it) + if (usesToggle(it) && it.group != 6) 56f else if (style(it) == ReviewSelectorStyle.ICON_TEXT) 12f else 8f } ?: 1f
        // These fields wrap their controls in a GlassFieldRow/GlassSection card.
        val inset = if (key in setOf("form", "gender", "UNIQUE_FORM", "UNOWN_LETTER", "VIVILLON_PATTERN", "size", "background", "evolution", "pvp", "pvp_moves")) {
            if (compact) 12f else 20f
        } else 0f
        return content + inset
    }
    fun minimumFieldHeight(key: String): Int {
        val group = reviewScreenGroup(key)
        val toggle = group != null && group.selectors.first().group != 6 && !segmented(key) &&
            group.selectors.any { optionVisible(it) && usesToggle(it) }
        val iconMinimum = if (key == "gender" && segmentedGender) {
            reviewScreenGroup(key)?.selectors.orEmpty().filter(::optionVisible).maxOfOrNull { selector ->
                val path = icon(selector)
                val compactSymbol = style(selector) == ReviewSelectorStyle.TEXT || path == null ||
                    (taskIconSymbol(path) != null && !hasCircle(selector))
                // Compact glyphs use the full line box; raster/circled icons retain a small safety margin.
                iconSize(selector) + if (compactSymbol) 0 else 4
            } ?: 0
        } else (visibleFieldIcons(key).maxOfOrNull { iconSize(it) } ?: 0) + 8
        val needed = maxOf(if (toggle) 28 else 24, iconMinimum)
        return reviewHeightOptions.first { it >= needed }
    }
    fun fieldHeight(key: String) = (heights[key]?.takeIf { it in reviewHeightOptions } ?: 36).coerceAtLeast(minimumFieldHeight(key))
    fun iconSize(selector: ReviewSelector) = iconSizes[reviewGroupOwner.getValue(selector)]?.takeIf { it in reviewIconSizes } ?: 18
    fun fieldSpan(key: String): Int {
        val span = spans[key]?.takeIf { it in reviewSpanOptions } ?: return defaultReviewSpan(key)
        if (span == -1) {
            val selector = reviewSingleIconSelector(key) ?: return defaultReviewSpan(key)
            if (style(selector) != ReviewSelectorStyle.ICON || usesToggle(selector) || icon(selector) == null) return defaultReviewSpan(key)
        }
        return span
    }
    fun optionVisible(selector: ReviewSelector) = selector !in hiddenOptions
    fun fieldVisible(key: String): Boolean {
        if (key == "IV_COMBINATION") return reviewIvFields.any(::fieldVisible)
        if (key in hiddenFields) return false
        val group = reviewScreenGroup(key)
        return group == null || group.selectors.any(::optionVisible)
    }
    fun ordered(selectors: List<ReviewSelector>): List<ReviewSelector> =
        (order.filter { it in selectors } + selectors).distinct().filter(::optionVisible)
    fun usesToggle(selector: ReviewSelector): Boolean {
        val owner = reviewGroupOwner.getValue(selector)
        return toggles[owner] ?: (style(owner) == ReviewSelectorStyle.TEXT || icon(owner) == null)
    }
    fun customized(selector: ReviewSelector) = style(selector) != ReviewSelectorStyle.TEXT || reviewGroupOwner.getValue(selector) in toggles || reviewGroupOwner.getValue(selector) in iconSizes
    fun color(selector: ReviewSelector) = colors[reviewGroupOwner.getValue(selector)] ?: ReviewIconColor.BLUE
    fun hasCircle(selector: ReviewSelector) = reviewGroupOwner.getValue(selector) in circles
    fun icon(selector: ReviewSelector): String? {
        val path = icons[selector] ?: selector.defaultIcon
        if (path.isEmpty()) return null
        if (taskIconSymbol(path) != null) return path
        return path.takeIf { ".." !in it && it.matches(Regex("(?:mission_icons|collection|filter_icons|menu/glass|types|adventure_effects)/[^/\\\\]+\\.png")) }
            ?: selector.defaultIcon
    }
    fun style(selector: ReviewSelector) = styles[reviewGroupOwner.getValue(selector)] ?: ReviewSelectorStyle.TEXT
    companion object {
        fun read(prefs: SharedPreferences) = ReviewLayout(
            prefs.getBoolean("review_compact", false),
            ReviewSelector.entries.associateWith { selector ->
                runCatching { ReviewSelectorStyle.valueOf(prefs.getString("review_selector_${selector.name}", "TEXT")!!) }
                    .getOrDefault(ReviewSelectorStyle.TEXT)
            }, ReviewSelector.entries.mapNotNull { selector ->
                prefs.getString("review_icon_${selector.name}", null)?.let { selector to it }
            }.toMap(),
            ReviewSelector.entries.filter { prefs.contains("review_toggle_${it.name}") }
                .associateWith { prefs.getBoolean("review_toggle_${it.name}", true) },
            ReviewSelector.entries.filter { prefs.getBoolean("review_circle_${it.name}", false) }.toSet(),
            ReviewSelector.entries.associateWith { selector ->
                runCatching { ReviewIconColor.valueOf(prefs.getString("review_color_${selector.name}", "BLUE")!!) }
                    .getOrDefault(ReviewIconColor.BLUE)
            }, prefs.getBoolean("review_gender_segmented", true),
            prefs.getString("review_tag_order", "").orEmpty().split(",").mapNotNull { value ->
                ReviewSelector.entries.firstOrNull { it.name == value }
            }.distinct(), readReviewLayoutTabs(prefs),
            prefs.getStringSet("review_hidden_fields", emptySet()).orEmpty().flatMap(::expandReviewField).filter { it in reviewScreenKeys }.toSet(),
            prefs.getStringSet("review_hidden_options", emptySet()).orEmpty().mapNotNull { value ->
                ReviewSelector.entries.firstOrNull { it.name == value }
            }.toSet(), readReviewSpans(prefs),
            ReviewSelector.entries.filter { prefs.contains("review_icon_size_${it.name}") }.associateWith { prefs.getInt("review_icon_size_${it.name}", 18) }, readReviewHeights(prefs), prefs.getBoolean("review_form_segmented", false))
    }
}
internal val LocalReviewLayout = staticCompositionLocalOf { ReviewLayout() }

@Composable internal fun rememberReviewLayout(): ReviewLayout {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("home_layout", 0) }
    var layout by remember(prefs) { mutableStateOf(ReviewLayout.read(prefs)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> layout = ReviewLayout.read(prefs) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        layout = ReviewLayout.read(prefs)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return layout
}

@Composable internal fun ReviewTagArtwork(selector: ReviewSelector, layout: ReviewLayout, modifier: Modifier = Modifier) {
    val path = layout.icon(selector) ?: return
    val size = layout.iconSize(selector).dp
    if (selector in listOf(ReviewSelector.MALE, ReviewSelector.FEMALE) &&
        taskIconSymbol(path) != null && !layout.hasCircle(selector)) {
        ReviewGenderGlyph(taskIconSymbol(path)!!, size.value, modifier.size(size))
        return
    }
    if (!layout.hasCircle(selector) || reviewIconHasOwnFrame(path)) {
        FilterTagIcon(path, modifier.size(size), symbolSize = (size.value * .75f).sp)
    } else {
        Box(modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape)
            .background(androidx.compose.ui.graphics.Color(layout.color(selector).argb)), contentAlignment = Alignment.Center) {
            FilterTagIcon(path, Modifier.size(size * .75f), tint =
                if (taskIconIsMonochrome(path)) androidx.compose.ui.graphics.Color.White else null,
                symbolSize = (size.value * .75f * .75f).sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun ReviewSelectorButton(selector: ReviewSelector, label: String, selected: Boolean,
    onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val layout = LocalReviewLayout.current
    if (!layout.optionVisible(selector)) return
    val style = layout.style(selector)
    val icon = layout.icon(selector)
    val toggle = layout.usesToggle(selector)
    val textOnly = style == ReviewSelectorStyle.TEXT || icon == null
    val context = LocalContext.current
    Row(modifier.then(if (toggle) Modifier.fillMaxWidth() else Modifier).height(GlassFieldHeight).clip(RoundedCornerShape(GlassFieldCorner)).background(appControlFill(selected))
        .combinedClickable(role = if (toggle) Role.Switch else Role.Checkbox, onClick = { onChange(!selected) },
            onLongClickLabel = label, onLongClick = { Toast.makeText(context, label, Toast.LENGTH_SHORT).show() })
        .semantics { contentDescription = label; toggleableState = if (selected)
            androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off }
        .padding(horizontal = if (!textOnly && style == ReviewSelectorStyle.ICON && !toggle) 4.dp else 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Row(if (toggle) Modifier.weight(1f) else Modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!textOnly) ReviewTagArtwork(selector, layout)
            if (textOnly || style == ReviewSelectorStyle.ICON_TEXT) {
                Text(label,
                    style = (if (textOnly) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall).copy(lineHeight = 14.sp),
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    fontWeight = if (textOnly) FontWeight.Medium else null)
            }
        }
        if (toggle) AppToggle(selected, null, Modifier.width(44.dp).height(GlassFieldHeight - 4.dp))
    }
}

/** Pack whole controls rather than forcing three narrow equal columns. */
internal fun reviewSelectorRows(widths: List<Float>, available: Float, gap: Float): List<List<Int>> {
    val rows = mutableListOf<MutableList<Int>>()
    var used = 0f
    widths.forEachIndexed { index, width ->
        val needed = width.coerceAtMost(available)
        if (rows.isEmpty() || (rows.last().isNotEmpty() && used + gap + needed > available)) {
            rows.add(mutableListOf())
            used = 0f
        }
        if (rows.last().isNotEmpty()) used += gap
        rows.last().add(index)
        used += needed
    }
    return rows
}

@Composable internal fun ReviewSelectorGrid(sourceItems: List<WeightedToggleItem>, sourceSelectors: List<ReviewSelector>,
    onReorder: ((List<ReviewSelector>) -> Unit)? = null) {
    val layout = LocalReviewLayout.current
    val selectors = layout.ordered(sourceSelectors)
    val items = selectors.map { sourceItems[sourceSelectors.indexOf(it)] }
    val saveOrder by rememberUpdatedState(onReorder)
    val currentSelectors by rememberUpdatedState(selectors)
    val bounds = remember { mutableStateMapOf<ReviewSelector, Rect>() }
    var dragged by remember { mutableStateOf<ReviewSelector?>(null) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var target by remember { mutableStateOf<ReviewSelector?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val dragModifier = if (onReorder == null) Modifier else Modifier.pointerInput(sourceSelectors) {
        detectDragGesturesAfterLongPress(onDragStart = { point ->
            dragged = bounds.entries.firstOrNull { it.value.contains(point + origin) }?.key
            offset = Offset.Zero
            target = dragged
        }, onDragCancel = { dragged = null; target = null; offset = Offset.Zero }, onDragEnd = {
            val from = currentSelectors.indexOf(dragged)
            val to = currentSelectors.indexOf(target)
            if (from >= 0 && to >= 0 && from != to) {
                saveOrder?.invoke(currentSelectors.toMutableList().apply { add(to, removeAt(from)) })
            }
            dragged = null; target = null; offset = Offset.Zero
        }) { change, amount ->
            if (dragged != null) {
                change.consume()
                offset += amount
                target = bounds.entries.firstOrNull { it.value.contains(change.position + origin) }?.key ?: target
            }
        }
    }
    if (sourceSelectors.isNotEmpty() && ((layout.segmentedGender && sourceSelectors.all { it == ReviewSelector.MALE || it == ReviewSelector.FEMALE }) ||
        (layout.segmentedForm && sourceSelectors.all { it == ReviewSelector.SHADOW || it == ReviewSelector.PURIFIED }))) {
        GlassGenderSegments(items,
            modifier = Modifier.onGloballyPositioned { origin = it.boundsInRoot().topLeft }.then(dragModifier),
            segmentModifier = { selector ->
                Modifier.onGloballyPositioned { bounds[selector] = it.boundsInRoot() }
                    .zIndex(if (dragged == selector) 1f else 0f)
                    .graphicsLayer {
                        if (dragged == selector) { translationX = offset.x; alpha = .85f }
                    }
            })
        return
    }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().onGloballyPositioned { origin = it.boundsInRoot().topLeft }.then(dragModifier)) {
        val widths = items.mapIndexed { index, item ->
            val selector = selectors[index]
            val icon = layout.icon(selector)
            val style = layout.style(selector)
            val textOnly = style == ReviewSelectorStyle.TEXT || icon == null
            val toggle = layout.usesToggle(selector)
            if (!textOnly && style == ReviewSelectorStyle.ICON) { if (toggle) layout.iconSize(selector) + 60f else (layout.iconSize(selector) + 16f).coerceAtLeast(36f) }
            else {
                val textStyle = if (textOnly) MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                    else MaterialTheme.typography.labelSmall
                val textWidth = with(density) { measurer.measure(item.label, textStyle, softWrap = false).size.width.toDp().value }
                textWidth + if (textOnly) { if (toggle) 56f else 16f } else { if (toggle) layout.iconSize(selector) + 60f else layout.iconSize(selector) + 20f }
            }
        }
        val rows = reviewSelectorRows(widths, maxWidth.value, 4f)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEach { indices ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    indices.forEach { index ->
                        val item = items[index]
                        val selector = selectors[index]
                        Box(Modifier.weight(widths[index].coerceAtLeast(1f))
                            .onGloballyPositioned { child -> bounds[selector] = child.boundsInRoot() }
                            .zIndex(if (dragged == selector) 1f else 0f)
                            .graphicsLayer {
                                if (dragged == selector) { translationX = offset.x; translationY = offset.y; alpha = .85f }
                            }) {
                            ReviewSelectorButton(selector, item.label, item.selected || (dragged != null && target == selector),
                                { item.onClick() }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun ReviewLayoutPreferences() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("home_layout", 0) }
    val layout = rememberReviewLayout()
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    var editingField by remember { mutableStateOf<String?>(null) }
    var editingIcon by remember { mutableStateOf<ReviewSelector?>(null) }
    var iconSearch by remember { mutableStateOf("") }
    var showOrganization by remember { mutableStateOf(false) }
    val modalHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * .65f).dp

    ReviewScreenPreview(layout, prefs, onConfigure = { editingField = it })
    ProfileMenuRow(tr("Organização", "Arrangement", "Organización"), onClick = { showOrganization = true })
    AppActionButton(onClick = {
        prefs.edit().remove("review_compact").remove("review_gender_segmented").remove("review_form_segmented").remove("review_tag_order").remove("review_screen_tabs").remove("review_hidden_fields").remove("review_hidden_options").remove("review_field_spans").remove("review_field_width_percent").remove("review_field_heights").apply {
            ReviewSelector.entries.forEach {
                remove("review_selector_${it.name}"); remove("review_icon_${it.name}")
                remove("review_icon_size_${it.name}"); remove("review_toggle_${it.name}"); remove("review_circle_${it.name}"); remove("review_color_${it.name}")
            }
        }.apply()
    }) { Text(tr("Restaurar padrão", "Restore default", "Restaurar predeterminado")) }

    if (showOrganization) androidx.compose.ui.window.Dialog(onDismissRequest = { showOrganization = false }) {
        AppModalPanel(title = tr("Organização", "Arrangement", "Organización"), onClose = { showOrganization = false }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false, true).forEach { compact ->
                    AppChoiceChip(layout.compact == compact, { prefs.edit().putBoolean("review_compact", compact).apply() },
                        modifier = Modifier.weight(1f), label = { Text(if (compact) tr("Compacto", "Compact", "Compacto") else tr("Atual", "Current", "Actual")) })
                }
            }
            CompositionLocalProvider(LocalReviewLayout provides layout, LocalGlassReviewStyle provides true) {
                GlassFieldRow {
                    CompactField(tr("Nível", "Level", "Nivel"), "35", {}, readOnly = true, modifier = Modifier.weight(1f))
                    CompactField("PC", "850", {}, readOnly = true, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    editingField?.takeIf { editingIcon == null }?.let { field ->
        val group = reviewScreenGroup(field) ?: return@let
        val selector = group.selectors.first()
        fun saveGroup(change: SharedPreferences.Editor.(ReviewSelector) -> Unit) {
            prefs.edit().apply { group.selectors.forEach { change(it) } }.apply()
        }
        androidx.compose.ui.window.Dialog(onDismissRequest = { editingField = null }) {
            AppModalPanel(title = reviewScreenLabel(field, language), onClose = { editingField = null }) {
                Column(Modifier.fillMaxWidth().heightIn(max = modalHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppToggleRow(tr("Exibir tag", "Show tag", "Mostrar etiqueta"), field !in layout.hiddenFields,
            { visible -> setReviewFieldVisible(prefs, field, visible) })
        ReviewFieldDimensionsControl(field, layout, prefs)
        ReviewIconSizeControl(selector, layout, prefs)
        val tabs = layout.tabs ?: defaultReviewScreenTabs
        if (tabs.size > 1) {
            Text(tr("Aba", "Tab", "Pesta\u00f1a"), fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tabs.forEach { tab ->
                    AppChoiceChip(field in tab.fields, { saveReviewTabs(prefs, moveReviewField(tabs, field, tab.id)) },
                        label = { Text(tab.label(language)) })
                }
            }
        }
        ReviewBlockPicker(field, layout, prefs)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ReviewSelectorStyle.entries.forEach { style ->
                AppChoiceChip(layout.style(selector) == style,
                    { saveGroup { putString("review_selector_${it.name}", style.name) } },
                    modifier = Modifier.weight(1f), label = { Text(when (style) {
                        ReviewSelectorStyle.TEXT -> tr("Texto", "Text", "Texto")
                        ReviewSelectorStyle.ICON_TEXT -> tr("Ícone + texto", "Icon + text", "Icono + texto")
                        ReviewSelectorStyle.ICON -> tr("Ícone", "Icon", "Icono")
                    }, style = MaterialTheme.typography.labelSmall) })
            }
        }
        if (selector.group != 6) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (group.key == "gender" || group.key == "form") {
                AppChoiceChip(layout.segmented(group.key), { prefs.edit().putBoolean("review_${group.key}_segmented", true).apply() },
                    modifier = Modifier.weight(1f), label = { Text(tr("Segmentado", "Segmented", "Segmentado"), style = MaterialTheme.typography.labelSmall) })
            }
            listOf(true, false).forEach { toggle ->
                AppChoiceChip(!layout.segmented(group.key) && layout.usesToggle(selector) == toggle,
                    { saveGroup {
                        putBoolean("review_toggle_${it.name}", toggle)
                        if (group.key == "gender" || group.key == "form") putBoolean("review_${group.key}_segmented", false)
                    } },
                    modifier = Modifier.weight(1f), label = { Text(if (toggle) tr("Com toggle", "With toggle", "Con interruptor")
                        else tr("Toque no botão", "Tap button", "Tocar botón"), style = MaterialTheme.typography.labelSmall) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(false, true).forEach { circle ->
                AppChoiceChip((layout.hasCircle(selector)) == circle,
                    { saveGroup { putBoolean("review_circle_${it.name}", circle) } },
                    modifier = Modifier.weight(1f), label = { Text(if (circle) tr("Círculo colorido", "Colored circle", "Círculo de color")
                        else tr("Ícone original", "Original icon", "Icono original"), style = MaterialTheme.typography.labelSmall) })
            }
        }
        if (layout.hasCircle(selector)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ReviewIconColor.entries.forEach { color ->
                    val colorLabel = when (color) {
                        ReviewIconColor.BLUE -> tr("Azul", "Blue", "Azul")
                        ReviewIconColor.GREEN -> tr("Verde", "Green", "Verde")
                        ReviewIconColor.PURPLE -> tr("Roxo", "Purple", "Morado")
                        ReviewIconColor.PINK -> tr("Rosa", "Pink", "Rosa")
                        ReviewIconColor.ORANGE -> tr("Laranja", "Orange", "Naranja")
                    }
                    AppChoiceChip(layout.color(selector) == color,
                        { saveGroup { putString("review_color_${it.name}", color.name) } },
                        modifier = Modifier.weight(1f).semantics { contentDescription = colorLabel }, label = {
                            Box(Modifier.size(20.dp).clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Color(color.argb)))
                        })
                }
            }
        }
                    HorizontalDivider()
                    group.selectors.forEach { option ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(option.label(language), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            AppActionButton(onClick = { iconSearch = ""; editingIcon = option }) {
                                layout.icon(option)?.let { ReviewTagArtwork(option, layout); Spacer(Modifier.width(6.dp)) }
                                Text(tr("Ícone", "Icon", "Icono"))
                            }
                            if (group.selectors.size > 1) AppToggle(layout.optionVisible(option),
                                { visible -> setReviewOptionVisible(prefs, option, visible) },
                                Modifier.semantics { contentDescription = tr("Exibir ", "Show ", "Mostrar ") + option.label(language) })
                        }
                    }
                    Text(tr("Prévia", "Preview", "Vista previa"), fontWeight = FontWeight.Bold)
                    CompositionLocalProvider(LocalReviewLayout provides layout, LocalGlassReviewStyle provides true,
                        LocalHideReviewLogMarkers provides true, LocalReviewSelectorCustomization provides true) {
                        val keys = listOf(field)
                        val visible = keys.filter(layout::fieldVisible)
                        if (visible.isEmpty()) {
                            Text(tr("Tag oculta", "Hidden tag", "Etiqueta oculta"), style = MaterialTheme.typography.labelSmall)
                        } else ReviewScreenGrid(visible) { field ->
                            ReviewScreenPreviewField(field, onReorder = if (group.selectors.size > 1) { ordered ->
                                val remaining = layout.order.filterNot { it in group.selectors }
                                prefs.edit().putString("review_tag_order", (remaining + mergeReviewVisibleOrder(
                                    (layout.order.filter { it in group.selectors } + group.selectors).distinct(), ordered)).joinToString(",") { it.name }).apply()
                            } else null)
                        }
                    }
                }
            }
        }
    }
    editingIcon?.let { selector ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { editingIcon = null }) {
            AppModalPanel(title = selector.label(language), onClose = { editingIcon = null }, actions = {
                AppActionButton(onClick = {
                    prefs.edit().remove("review_icon_${selector.name}").apply()
                    editingIcon = null
                }) { Text(tr("Usar padrão", "Use default", "Usar predeterminado")) }
            }) {
                AppGlassTextField(iconSearch, { iconSearch = it }, singleLine = true,
                    label = { Text(tr("Buscar ícone", "Search icons", "Buscar icono")) }, modifier = Modifier.fillMaxWidth())
                SharedTaskIconPicker(layout.icon(selector), iconSearch, { path ->
                    prefs.edit().putString("review_icon_${selector.name}", path.orEmpty()).apply()
                    editingIcon = null
                }, Modifier.fillMaxWidth().heightIn(max = modalHeight).height(300.dp))
            }
        }
    }
}

@Composable internal fun ReviewDataLabel(label: String, modifier: Modifier = Modifier) {
    val layout = LocalReviewLayout.current
    val selector = ReviewSelector.forLabel(label, appLanguage())?.takeIf { it.group == 6 }
    val showIcon = selector != null && layout.style(selector) != ReviewSelectorStyle.TEXT && layout.icon(selector) != null
    Row(modifier.semantics { contentDescription = label }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showIcon) ReviewTagArtwork(selector!!, layout)
        if (!showIcon || layout.style(selector!!) != ReviewSelectorStyle.ICON) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                fontWeight = if (LocalGlassReviewStyle.current) FontWeight.Normal else FontWeight.SemiBold)
        }
    }
}

@Composable internal fun ReviewIconSizeControl(selector: ReviewSelector, layout: ReviewLayout, prefs: SharedPreferences) {
    val language = appLanguage()
    val size = layout.iconSize(selector)
    val index = reviewIconSizes.indexOf(size)
    val owner = reviewGroupOwner.getValue(selector)
    fun save(value: Int) { prefs.edit().putInt("review_icon_size_${owner.name}", value).apply() }
    ReviewDimensionRow(lt(language, "\u00cdcone", "Icon", "Icono"), "$size dp",
        index > 0, index < reviewIconSizes.lastIndex,
        { save(reviewIconSizes[index - 1]) }, { save(reviewIconSizes[index + 1]) })
}
