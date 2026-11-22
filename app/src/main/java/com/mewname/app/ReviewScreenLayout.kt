package com.mewname.app

import android.content.SharedPreferences
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mewname.app.domain.AppLanguage
import org.json.JSONArray
import org.json.JSONObject

internal val ReviewScreenContentPadding = 12.dp
internal val ReviewScreenMaxWidth = 560.dp

internal data class ReviewLayoutTab(val id: String, val title: String = "", val fields: List<String>,
    val blocks: List<ReviewLayoutBlock> = emptyList(), val order: List<String> = emptyList()) {
    fun label(language: AppLanguage) = title.ifBlank { when (id) {
        "general" -> lt(language, "Geral", "General", "General")
        "extras" -> "Extras"
        "pvp" -> "PvP"
        else -> lt(language, "Aba", "Tab", "Pestaña")
    } }
}
internal val defaultReviewScreenTabs = listOf(
    ReviewLayoutTab("general", fields = listOf("POKEMON_NAME", "gender", "LEVEL", "CP", "POKEDEX_NUMBER",
        "UNIQUE_FORM", "UNOWN_LETTER", "VIVILLON_PATTERN", "IV_PERCENT", "IV_ATTACK", "IV_DEFENSE", "IV_STAMINA", "MASTER_IV_BADGE", "form", "EVOLVE", "PURIFY")),
    ReviewLayoutTab("pvp", fields = listOf("pvp", "pvp_moves")),
    ReviewLayoutTab("extras", fields = listOf("TYPE", "FAVORITE", "LUCKY", "size", "ADVENTURE", "LEGACY", "LEGACY_MOVE_NAME", "background", "evolution")))
internal val reviewScreenKeys = defaultReviewScreenTabs.flatMap { it.fields }
internal val reviewIvFields = listOf("IV_ATTACK", "IV_DEFENSE", "IV_STAMINA")
internal fun expandReviewField(key: String) = if (key == "IV_COMBINATION") reviewIvFields else listOf(key)
internal fun reviewScreenKey(groupKey: String) = when (groupKey) {
    "PVP_LEAGUE", "PVP_RANK" -> "pvp"
    "IV_COMBINATION" -> "IV_ATTACK"
    else -> groupKey
}
internal fun reviewScreenGroup(key: String) = reviewTagGroups.firstOrNull {
    if (key in reviewIvFields || key == "IV_COMBINATION") it.key == "IV_COMBINATION" else reviewScreenKey(it.key) == key
}
internal fun normalizeReviewTabs(tabs: List<ReviewLayoutTab>): List<ReviewLayoutTab> {
    val seen = mutableSetOf<String>()
    val result = tabs.filter { it.id.isNotBlank() }.distinctBy { it.id }.map { tab ->
        tab.copy(title = tab.title.trim().take(40), fields = tab.fields.flatMap(::expandReviewField).filter { it in reviewScreenKeys && seen.add(it) })
    }.toMutableList()
    if (result.isEmpty()) return defaultReviewScreenTabs
    defaultReviewScreenTabs.forEach { default ->
        val missing = default.fields.filter { seen.add(it) }
        val index = result.indexOfFirst { it.id == default.id }.takeIf { it >= 0 } ?: 0
        result[index] = result[index].copy(fields = result[index].fields + missing)
    }
    return result.map(::normalizeReviewBlocks)
}
internal fun moveReviewField(tabs: List<ReviewLayoutTab>, field: String, tabId: String): List<ReviewLayoutTab> {
    if (field !in reviewScreenKeys || tabs.none { it.id == tabId }) return tabs
    if (tabs.firstOrNull { field in it.fields }?.id == tabId) return tabs
    return normalizeReviewTabs(tabs.map { it.copy(fields = it.fields.filterNot { key -> key == field } +
        if (it.id == tabId) listOf(field) else emptyList()) })
}
internal fun readReviewTabs(prefs: SharedPreferences): List<ReviewLayoutTab>? = runCatching {
    val raw = prefs.getString("review_screen_tabs", null) ?: return null
    val array = JSONArray(raw)
    normalizeReviewTabs((0 until array.length()).map { index ->
        val tab = array.getJSONObject(index)
        val fields = tab.getJSONArray("fields")
        ReviewLayoutTab(tab.getString("id"), tab.optString("title"), (0 until fields.length()).map { fields.getString(it) },
            readReviewBlocks(tab.optJSONArray("blocks")), jsonReviewStrings(tab.optJSONArray("order")))
    })
}.getOrNull()
internal fun saveReviewTabs(prefs: SharedPreferences, tabs: List<ReviewLayoutTab>) {
    val array = JSONArray()
    normalizeReviewTabs(tabs).forEach { tab -> array.put(JSONObject().put("id", tab.id).put("title", tab.title).put("fields", JSONArray(tab.fields)).put("blocks", writeReviewBlocks(tab.blocks)).put("order", JSONArray(tab.order))) }
    prefs.edit().putString("review_screen_tabs", array.toString()).apply()
}
internal fun reviewScreenLabel(key: String, language: AppLanguage): String = when (key) {
    "pvp" -> lt(language, "Liga e ranking PvP", "PvP league and ranking", "Liga y ranking PvP")
    "IV_ATTACK" -> lt(language, "IV Atq", "IV Atk", "IV Atq")
    "IV_DEFENSE" -> "IV Def"
    "IV_STAMINA" -> "IV HP"
    "pvp_moves" -> lt(language, "Ataques PvP", "PvP moves", "Ataques PvP")
    else -> reviewTagGroups.first { it.key == key }.label(language)
}
/** Appearance-only changes must also enable the same grid used by the preview. No write during composition. */
internal fun readReviewLayoutTabs(prefs: SharedPreferences): List<ReviewLayoutTab>? = readReviewTabs(prefs)
    ?: defaultReviewScreenTabs.takeIf {
        prefs.all.keys.any { key -> key in setOf("review_compact", "review_gender_segmented", "review_form_segmented",
            "review_tag_order", "review_hidden_fields", "review_hidden_options", "review_field_spans",
            "review_field_width_percent", "review_field_heights") ||
            listOf("review_selector_", "review_icon_", "review_toggle_", "review_circle_", "review_color_").any(key::startsWith) }
    }

/** Reserve the measured title, margins and additional rows before assigning height to a control. */
internal fun reviewControlHeightForBlock(requested: Int, minimum: Int, overhead: Float, rows: Int = 1): Int =
    (minimum + kotlin.math.floor((requested - overhead.coerceAtLeast(0f) - minimum) / rows.coerceAtLeast(1)).toInt()).coerceAtLeast(minimum)

@Composable private fun ReviewSizedField(field: String, width: Float, layout: ReviewLayout, content: @Composable RowScope.() -> Unit) {
    val density = LocalDensity.current
    val scale = density.fontScale.coerceAtLeast(1f)
    val minimum = layout.minimumFieldHeight(field)
    val requested = layout.fieldHeight(field)
    // Measure the content at its safe minimum first. Never subcompose it twice (text fields have state).
    var overhead by remember(field, width, layout, density.density, scale) { mutableStateOf<Float?>(null) }
    var rows by remember(field, width, layout, density.density, scale) { mutableStateOf(1) }
    val control = overhead?.let { reviewControlHeightForBlock(requested, minimum, it, rows) } ?: minimum
    Box(Modifier.fillMaxWidth().heightIn(min = (requested * scale).dp), contentAlignment = Alignment.BottomStart) {
        CompositionLocalProvider(LocalReviewControlHeight provides control) {
            Row(Modifier.fillMaxWidth().onSizeChanged { size ->
                val measured = size.height / density.density / scale
                val base = overhead
                if (base == null) overhead = (measured - minimum).coerceAtLeast(0f)
                else if (control > minimum) {
                    // Groups can wrap into several rows; share the height increase across those rows.
                    rows = kotlin.math.round((measured - base - minimum) / (control - minimum)).toInt().coerceAtLeast(1)
                }
            }, verticalAlignment = Alignment.Bottom, content = content)
        }
    }
}

internal val reviewHeightOptions = (24..120 step 4).toList()
internal val reviewSpanOptions = listOf(-1) + (5..100 step 5).toList()
internal fun defaultReviewSpan(key: String): Int = when (key) {
    "gender", "LEVEL", "IV_PERCENT", "IV_ATTACK", "IV_DEFENSE", "IV_STAMINA" -> 25
    "EVOLVE", "PURIFY" -> 35
    "background", "evolution", "size", "pvp", "pvp_moves" -> 100
    else -> 50
}
internal fun readReviewSpans(prefs: SharedPreferences): Map<String, Int> = runCatching {
    val percentages = prefs.contains("review_field_width_percent")
    val json = JSONObject(prefs.getString(if (percentages) "review_field_width_percent" else "review_field_spans", "{}").orEmpty())
    reviewScreenKeys.mapNotNull { key ->
        val stored = json.optInt(key)
        val width = if (percentages || stored == -1) stored
            else if (stored in listOf(1, 2, 3, 4, 6, 8, 9, 12)) ((stored * 100f / 12f / 5f) + .5f).toInt() * 5
            else 0
        width.takeIf { it in reviewSpanOptions }?.let { key to it }
    }.toMap()
}.getOrDefault(emptyMap())
internal fun saveReviewSpan(prefs: SharedPreferences, key: String, span: Int) {
    if (key !in reviewScreenKeys || span !in reviewSpanOptions) return
    if (readReviewTabs(prefs) == null) saveReviewTabs(prefs, defaultReviewScreenTabs)
    val spans = readReviewSpans(prefs) + (key to span)
    prefs.edit().apply {
        putString("review_field_width_percent", JSONObject(spans).toString())
        remove("review_field_spans")
        if (span == -1) reviewSingleIconSelector(key)?.let { selector ->
            putString("review_selector_${selector.name}", ReviewSelectorStyle.ICON.name)
            putBoolean("review_toggle_${selector.name}", false)
        }
    }.apply()
}
internal fun readReviewHeights(prefs: SharedPreferences): Map<String, Int> = runCatching {
    val json = JSONObject(prefs.getString("review_field_heights", "{}").orEmpty())
    reviewScreenKeys.mapNotNull { key -> json.optInt(key).takeIf { it in reviewHeightOptions }?.let { key to it } }.toMap()
}.getOrDefault(emptyMap())
internal fun saveReviewHeight(prefs: SharedPreferences, key: String, height: Int) {
    if (key !in reviewScreenKeys || height !in reviewHeightOptions) return
    if (readReviewTabs(prefs) == null) saveReviewTabs(prefs, defaultReviewScreenTabs)
    prefs.edit().putString("review_field_heights", JSONObject(readReviewHeights(prefs) + (key to height)).toString()).apply()
}
internal fun reviewSingleIconSelector(key: String): ReviewSelector? = reviewScreenGroup(key)?.selectors
    ?.singleOrNull()?.takeIf { it.group != 6 }
internal fun reviewScreenWidths(keys: List<String>, layout: ReviewLayout, available: Float, gap: Float = 6f): Map<String, Float> =
    keys.associateWith { key ->
        val span = layout.fieldSpan(key)
        val width = if (span == -1) layout.iconSize(reviewSingleIconSelector(key)!!) + 8f
            else (available + gap) * span / 100f - gap
        width.coerceAtLeast(layout.minimumFieldWidth(key)).coerceIn(1f, available.coerceAtLeast(1f))
    }
internal fun reviewScreenRows(keys: List<String>, widths: Map<String, Float>, available: Float, gap: Float = 6f): List<List<String>> {
    val rows = mutableListOf<MutableList<String>>()
    var used = 0f
    keys.forEach { key ->
        val size = widths.getValue(key)
        if (rows.isEmpty() || used + gap + size > available + .1f) { rows.add(mutableListOf()); used = 0f }
        if (rows.last().isNotEmpty()) used += gap
        rows.last().add(key); used += size
    }
    return rows
}

/** Reorder only after the pointer enters another slot; the current slot prevents oscillation. */
internal fun reviewDragOrder(order: List<String>, dragged: String, point: Offset, slots: Map<String, Rect>): List<String> {
    if (slots[dragged]?.contains(point) == true) return order
    val target = order.firstOrNull { it != dragged && slots[it]?.contains(point) == true } ?: return order
    val from = order.indexOf(dragged)
    val to = order.indexOf(target)
    if (from < 0 || to < 0) return order
    return order.toMutableList().apply { add(to, removeAt(from)) }
}

/** One flat layout keeps each composable alive when its item moves between rows. */
@Composable internal fun ReviewScreenGrid(keys: List<String>, modifier: Modifier = Modifier,
    onReorder: ((List<String>) -> Unit)? = null, onOpen: ((String) -> Unit)? = null,
    nested: Boolean = false, widthOverride: ((String, Float) -> Float?)? = null,
    content: @Composable RowScope.(String) -> Unit) {
    val config = LocalReviewLayout.current
    var dragged by remember { mutableStateOf<String?>(null) }
    var previewOrder by remember { mutableStateOf<List<String>?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    var height by remember { mutableIntStateOf(0) }
    var slots by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    val current by rememberUpdatedState(keys)
    val save by rememberUpdatedState(onReorder)
    val scroll = rememberScrollState()
    val edge = with(LocalDensity.current) { 36.dp.toPx() }
    fun contentPoint() = pointer + Offset(0f, if (nested || onReorder == null) 0f else scroll.value.toFloat())
    fun updateOrder() {
        val item = dragged ?: return
        previewOrder = reviewDragOrder(previewOrder ?: current, item, contentPoint(), slots)
    }
    fun finish(commit: Boolean) {
        val ordered = previewOrder
        if (commit && ordered != null && ordered != current && ordered.toSet() == current.toSet()) save?.invoke(ordered)
        dragged = null; previewOrder = null
    }
    LaunchedEffect(keys) { if (dragged != null) finish(false) }
    LaunchedEffect(dragged) {
        while (dragged != null) {
            withFrameNanos { }
            if (!nested) {
                val delta = when { pointer.y < edge -> -edge / 6; pointer.y > height - edge -> edge / 6; else -> 0f }
                if (delta != 0f) { scroll.scrollBy(delta); updateOrder() }
            }
        }
    }
    val order = previewOrder ?: keys
    val slotColor = MaterialTheme.colorScheme.primary.copy(alpha = .35f)
    BoxWithConstraints(modifier.fillMaxWidth().then(if (onReorder == null || nested) Modifier else Modifier.heightIn(max = 400.dp))
        .onSizeChanged { height = it.height }
        .then(if (onReorder == null) Modifier else Modifier.pointerInput(nested) {
            detectDragGesturesAfterLongPress(onDragStart = { point ->
                pointer = point
                val contentPoint = contentPoint()
                val item = current.firstOrNull { slots[it]?.contains(contentPoint) == true }
                dragged = item
                previewOrder = if (item != null) current else null
                grabOffset = item?.let { contentPoint - slots.getValue(it).topLeft } ?: Offset.Zero
            }, onDragEnd = { finish(true) }, onDragCancel = { finish(false) }) { change, _ ->
                if (dragged != null) { change.consume(); pointer = change.position; updateOrder() }
            }
        })) {
        val widths = reviewScreenWidths(order, config, maxWidth.value).mapValues { (key, value) -> widthOverride?.invoke(key, maxWidth.value) ?: value }
        val rows = reviewScreenRows(order, widths, maxWidth.value)
        androidx.compose.ui.layout.Layout(
            modifier = Modifier.fillMaxWidth()
                .then(if (onReorder == null || nested) Modifier else Modifier.verticalScroll(scroll, enabled = dragged == null))
                .drawBehind {
                    slots[dragged]?.let { rect ->
                        drawRoundRect(slotColor, rect.topLeft, rect.size, androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    }
                },
            content = {
                order.forEach { field -> key(field) {
                    val target = slots[field]?.topLeft ?: Offset.Zero
                    val animated by androidx.compose.animation.core.animateOffsetAsState(target,
                        animationSpec = if (dragged != null) androidx.compose.animation.core.spring(stiffness = 600f)
                            else androidx.compose.animation.core.snap(), label = "review-item-placement")
                    Box(Modifier.width(widths.getValue(field).dp)
                        .zIndex(if (dragged == field) 2f else 0f)
                        .graphicsLayer {
                            val translation = if (dragged == field) contentPoint() - grabOffset - target else animated - target
                            translationX = translation.x; translationY = translation.y
                            if (dragged == field) { alpha = .94f; shadowElevation = 6.dp.toPx() }
                        }.semantics {
                            if (onReorder != null) customActions = listOf(-1, 1).map { delta ->
                                CustomAccessibilityAction(if (delta < 0) "Mover antes" else "Mover depois") {
                                    val from = current.indexOf(field); val to = from + delta
                                    if (to in current.indices) { save?.invoke(current.toMutableList().apply { add(to, removeAt(from)) }); true } else false
                                }
                            }
                        }) {
                        if (field.startsWith(reviewBlockPrefix)) Row(Modifier.fillMaxWidth()) { content(field) }
                        else ReviewSizedField(field, widths.getValue(field), config) { content(field) }
                        if (onOpen != null && !field.startsWith(reviewBlockPrefix)) Box(Modifier.matchParentSize().clickable { onOpen(field) })
                    }
                } }
            }) { measurables, constraints ->
                val measured = order.zip(measurables).associate { (field, measurable) ->
                    field to measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                }
                val placements = mutableMapOf<String, Rect>()
                val gap = 6.dp.roundToPx()
                var y = 0
                rows.forEachIndexed { rowIndex, row ->
                    val rowHeight = row.maxOfOrNull { measured.getValue(it).height } ?: 0
                    var x = 0
                    row.forEach { field ->
                        val child = measured.getValue(field)
                        val top = y + rowHeight - child.height
                        placements[field] = Rect(x.toFloat(), top.toFloat(), (x + child.width).toFloat(), (top + child.height).toFloat())
                        x += child.width + gap
                    }
                    y += rowHeight + if (rowIndex < rows.lastIndex) gap else 0
                }
                if (slots != placements) slots = placements
                layout(constraints.maxWidth, constraints.constrainHeight(y)) {
                    order.forEach { field ->
                        val rect = placements.getValue(field)
                        measured.getValue(field).placeRelative(rect.left.toInt(), rect.top.toInt())
                    }
                }
            }
    }
}

@Composable internal fun ReviewScreenPreviewField(key: String, onReorder: ((List<ReviewSelector>) -> Unit)? = null) {
    val language = appLanguage()
    if (key == "pvp") {
        Column {
            PvpQuickControls(ReviewIvMode.NORMAL, com.mewname.app.domain.PvpCalculationOptions(), {}, {})
            GlassSection {
                Row {
                    ReviewDataLabel(com.mewname.app.model.NamingField.PVP_LEAGUE.localizedLabel(language), Modifier.width(68.dp))
                    Text("Pikachu", Modifier.width(72.dp), style = MaterialTheme.typography.labelSmall)
                }
                listOf("Copinha", "Great", "Ultra", "Master").forEach { league ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        CompactSelectableField(league, false, onClick = {}, modifier = Modifier.width(68.dp))
                        CompactSelectableField("\u2014", false, onClick = {}, modifier = Modifier.width(72.dp))
                    }
                }
            }
        }
        return
    }
    if (key == "pvp_moves") {
        GlassFieldRow(weights = listOf(1f, 1f, .65f), legacySpacing = 2.dp) {
            MoveDropdownField(lt(language, "Ataque rapido", "Fast move", "Ataque rapido"), null, emptyList(), "\u2014", {}, modifier = Modifier.weight(1f))
            MoveDropdownField(lt(language, "Ataque carregado", "Charged move", "Ataque cargado"), null, emptyList(), "\u2014", {}, modifier = Modifier.weight(1f))
            Column(Modifier.weight(.65f)) {
                FieldLabelRow("PvE (est.)")
                CompactTextInput("\u2014", {}, readOnly = true, modifier = Modifier.fillMaxWidth())
            }
        }
        return
    }
    if (key in reviewIvFields) {
        val label = when (key) { "IV_ATTACK" -> lt(language, "Atq", "Atk", "Atq"); "IV_DEFENSE" -> "Def"; else -> "HP" }
        IvValueButton(label, 15, false, {}, modifier = Modifier.fillMaxWidth())
        return
    }
    val group = reviewTagGroups.first { it.key == key }
    val selector = group.selectors.first()
    if (key == "POKEMON_NAME") {
        PokemonSuggestionField(group.label(language), "Pikachu", emptyList(), false, {}, {},
            useOptionModal = true, modifier = Modifier.fillMaxWidth())
    } else if (key in listOf("UNIQUE_FORM", "UNOWN_LETTER", "VIVILLON_PATTERN", "MASTER_IV_BADGE")) {
        val field: @Composable RowScope.() -> Unit = {
            SelectionDropdownField(group.label(language), if (key == "UNOWN_LETTER") "A" else "\u2014", emptyList(), {},
                useOptionModal = true, modifier = Modifier.weight(1f))
        }
        if (key == "MASTER_IV_BADGE") Row(Modifier.fillMaxWidth(), content = field)
        else GlassFieldRow(content = field)
    } else if (selector.group == 6) {
        CompactField(if (key == "IV_PERCENT") "IV %" else group.label(language), when (selector) {
            ReviewSelector.LEVEL -> "35"
            ReviewSelector.CP -> "850"
            ReviewSelector.POKEDEX_NUMBER -> "25"
            ReviewSelector.IV_PERCENT -> "100"
            else -> "\u2014"
        }, {}, readOnly = true, onClick = if (key == "LEVEL") ({}) else null)
    } else if (group.selectors.size == 1) ReviewSelectorButton(selector, group.label(language), false, {}, Modifier.fillMaxWidth())
    else {
        val selected = remember(group.key) { mutableStateMapOf<ReviewSelector, Boolean>() }
        val content: @Composable () -> Unit = {
            FieldLabelRow(group.label(language))
            ReviewSelectorGrid(group.selectors.map { option -> WeightedToggleItem(option.label(language), selected[option] ?: false) {
                val next = !(selected[option] ?: false)
                if (group.key in listOf("gender", "size", "background", "form")) selected.clear()
                selected[option] = next
            } }, group.selectors, onReorder)
        }
        if (key in listOf("form", "size", "background", "evolution")) GlassSection { content() }
        else Column(verticalArrangement = Arrangement.spacedBy(if (key == "gender") 0.dp else 2.dp)) { content() }
    }
}

@Composable internal fun ReviewScreenPreview(layout: ReviewLayout, prefs: SharedPreferences, onConfigure: (String) -> Unit) {
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    val tabs = layout.tabs ?: defaultReviewScreenTabs
    var selectedId by remember { mutableStateOf(tabs.first().id) }
    val selected = tabs.firstOrNull { it.id == selectedId } ?: tabs.first()
    var tabEditor by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var moving by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var blockEditor by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth().padding(horizontal = ReviewScreenContentPadding), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Pr\u00e9via geral", "Full preview", "Vista previa general"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
        IconButton(onClick = { title = ""; tabEditor = "new" }) { Icon(AppIcons.Add, tr("Criar aba", "Create tab", "Crear pesta\u00f1a"), Modifier.size(18.dp)) }
        IconButton(onClick = { title = selected.label(language); tabEditor = selected.id }) { Icon(AppIcons.Edit, tr("Renomear aba", "Rename tab", "Renombrar pesta\u00f1a"), Modifier.size(18.dp)) }
        if (tabs.size > 1) IconButton(onClick = { deleting = true }) { Icon(AppIcons.Delete, tr("Excluir aba", "Delete tab", "Eliminar pesta\u00f1a"), Modifier.size(18.dp)) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val previewWidth = minOf(maxWidth, ReviewScreenMaxWidth) - ReviewScreenContentPadding * 2
        blockEditor?.let { id -> ReviewBlockEditor(selected, id, prefs, previewWidth, onClose = { blockEditor = null }) }
        AppSectionCard(Modifier.widthIn(max = ReviewScreenMaxWidth).fillMaxWidth()) {
            Column(Modifier.padding(horizontal = ReviewScreenContentPadding, vertical = 4.5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ReviewScreenTabs(tabs.map { it.label(language) }, tabs.indexOf(selected), { selectedId = tabs[it].id })
                CompositionLocalProvider(LocalReviewLayout provides layout, LocalGlassReviewStyle provides true,
                    LocalHideReviewLogMarkers provides true, LocalReviewSelectorCustomization provides true) {
                    ReviewTabGrid(selected, selected.fields.filter(layout::fieldVisible),
                        onChange = { updated -> saveReviewTabs(prefs, tabs.map { if (it.id == updated.id) updated else it }) },
                        onEditBlock = { blockEditor = it },
                        onOpen = { field -> if (reviewScreenGroup(field) != null) onConfigure(field) else moving = field }) { key -> ReviewScreenPreviewField(key) }
                }
            }
        }
    }
    val hidden = selected.fields.filterNot(layout::fieldVisible)
    if (hidden.isNotEmpty()) {
        Text(tr("Tags ocultas", "Hidden tags", "Etiquetas ocultas"), Modifier.padding(horizontal = ReviewScreenContentPadding), style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = ReviewScreenContentPadding), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            hidden.forEach { field -> AppActionButton(onClick = { if (reviewScreenGroup(field) != null) onConfigure(field) else moving = field }) { Text(reviewScreenLabel(field, language)) } }
        }
    }
    AppActionButton(onClick = { blockEditor = "new" }, modifier = Modifier.padding(horizontal = ReviewScreenContentPadding)) {
        Text(tr("Criar bloco", "Create block", "Crear bloque"))
    }
    tabEditor?.let { id ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { tabEditor = null }) {
            AppModalPanel(title = if (id == "new") tr("Nova aba", "New tab", "Nueva pestaña") else tr("Renomear aba", "Rename tab", "Renombrar pestaña"),
                onClose = { tabEditor = null }, actions = {
                    AppActionButton(onClick = {
                        if (title.isNotBlank()) {
                            val updated = if (id == "new") {
                                val added = ReviewLayoutTab(java.util.UUID.randomUUID().toString(), title.trim(), emptyList())
                                selectedId = added.id
                                tabs + added
                            } else tabs.map { if (it.id == id) it.copy(title = title.trim()) else it }
                            saveReviewTabs(prefs, updated); tabEditor = null
                        }
                    }) { Text(tr("Salvar", "Save", "Guardar")) }
                }) {
                AppGlassTextField(title, { title = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(tr("Nome da aba", "Tab name", "Nombre de pestaña")) })
            }
        }
    }
    if (deleting) androidx.compose.ui.window.Dialog(onDismissRequest = { deleting = false }) {
        AppModalPanel(title = tr("Excluir aba?", "Delete tab?", "¿Eliminar pestaña?"), onClose = { deleting = false }, actions = {
            AppActionButton(onClick = {
                val remaining = tabs.filterNot { it.id == selected.id }.toMutableList()
                remaining[0] = remaining[0].copy(fields = remaining[0].fields + selected.fields,
                    blocks = remaining[0].blocks + selected.blocks, order = reviewTabItems(remaining[0]) + reviewTabItems(selected))
                saveReviewTabs(prefs, remaining); selectedId = remaining[0].id; deleting = false
            }) { Text(tr("Excluir", "Delete", "Eliminar")) }
        }) { Text(tr("Os campos serão movidos para a primeira aba restante.", "Fields will move to the first remaining tab.", "Los campos pasarán a la primera pestaña restante.")) }
    }
    moving?.let { field ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { moving = null }) {
            AppModalPanel(title = reviewScreenLabel(field, language), onClose = { moving = null }) {
                AppToggleRow(tr("Exibir tag", "Show tag", "Mostrar etiqueta"), field !in layout.hiddenFields,
                    { visible -> setReviewFieldVisible(prefs, field, visible) })
                ReviewFieldDimensionsControl(field, layout, prefs)
                ReviewBlockPicker(field, layout, prefs)
                reviewScreenGroup(field)?.let { ReviewIconSizeControl(it.selectors.first(), layout, prefs) }
                Text(tr("Mover para aba", "Move to tab", "Mover a pestaña"), fontWeight = FontWeight.Bold)
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    tabs.forEach { tab ->
                        AppChoiceChip(field in tab.fields, {
                            saveReviewTabs(prefs, moveReviewField(tabs, field, tab.id)); selectedId = tab.id; moving = null
                        }, label = { Text(tab.label(language)) }, modifier = Modifier.fillMaxWidth())
                    }
                }

            }
        }
    }
}

@Composable internal fun ReviewScreenTabs(labels: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val style = MaterialTheme.typography.labelLarge
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widths = labels.map { label -> with(density) { measurer.measure(label, style, softWrap = false).size.width.toDp() } + 32.dp }
        val fits = widths.sumOf { it.value.toDouble() }.toFloat() + (labels.size - 1).coerceAtLeast(0) * 4 <= maxWidth.value
        val extra = if (fits && labels.isNotEmpty()) (maxWidth.value - widths.sumOf { it.value.toDouble() }.toFloat() - (labels.size - 1) * 4) / labels.size else 0f
        Row(Modifier.fillMaxWidth().then(if (fits) Modifier else Modifier.horizontalScroll(rememberScrollState())), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { index, label ->
                AppChoiceChip(index == selected, { onSelected(index) }, modifier = Modifier.width(widths[index] + extra.dp),
                    label = { Text(label, Modifier.fillMaxWidth(), style = style, maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center) })
            }
        }
    }
}

/** Keep hidden fields in their saved slots while visible fields are rearranged. */
internal fun <T> mergeReviewVisibleOrder(original: List<T>, reordered: List<T>): List<T> {
    val changed = reordered.filter { it in original }.distinct()
    val iterator = changed.iterator()
    return original.map { if (it in changed) iterator.next() else it }
}
internal fun setReviewFieldVisible(prefs: SharedPreferences, key: String, visible: Boolean) {
    val keys = expandReviewField(key).filter { it in reviewScreenKeys }
    if (keys.isEmpty()) return
    if (readReviewTabs(prefs) == null) saveReviewTabs(prefs, defaultReviewScreenTabs)
    val hidden = prefs.getStringSet("review_hidden_fields", emptySet()).orEmpty().flatMap(::expandReviewField).toMutableSet()
    if (visible) hidden.removeAll(keys.toSet()) else hidden.addAll(keys)
    prefs.edit().putStringSet("review_hidden_fields", hidden).apply()
}
internal fun setReviewOptionVisible(prefs: SharedPreferences, option: ReviewSelector, visible: Boolean) {
    if (readReviewTabs(prefs) == null) saveReviewTabs(prefs, defaultReviewScreenTabs)
    val hidden = prefs.getStringSet("review_hidden_options", emptySet()).orEmpty().toMutableSet()
    if (visible) hidden.remove(option.name) else hidden.add(option.name)
    prefs.edit().putStringSet("review_hidden_options", hidden).apply()
}

@Composable internal fun ReviewFieldWidthControl(field: String, layout: ReviewLayout, prefs: SharedPreferences) {
    val language = appLanguage()
    val span = layout.fieldSpan(field)
    val selector = reviewSingleIconSelector(field)
    val options = reviewSpanOptions.filter { it != -1 || (selector != null && layout.icon(selector) != null) }
    val index = options.indexOf(span)
    ReviewDimensionRow(lt(language, "Largura", "Width", "Ancho"),
        if (span == -1) lt(language, "Ajustar ao \u00edcone", "Fit icon", "Ajustar al icono") else "$span%",
        index > 0, index < options.lastIndex,
        { saveReviewSpan(prefs, field, options[index - 1]) },
        { saveReviewSpan(prefs, field, options[index + 1]) })
}

@Composable internal fun ReviewFieldDimensionsControl(field: String, layout: ReviewLayout, prefs: SharedPreferences) {
    ReviewFieldWidthControl(field, layout, prefs)
    val language = appLanguage()
    val height = layout.fieldHeight(field)
    val options = reviewHeightOptions.filter { it >= layout.minimumFieldHeight(field) }
    val index = options.indexOf(height)
    ReviewDimensionRow(lt(language, "Altura", "Height", "Altura"), "$height dp",
        index > 0, index < options.lastIndex,
        { saveReviewHeight(prefs, field, options[index - 1]) },
        { saveReviewHeight(prefs, field, options[index + 1]) })
}

/** Compact, shared adjustment row; keep the controls aligned at the trailing edge. */
@Composable internal fun ReviewDimensionRow(title: String, value: String, canDecrease: Boolean, canIncrease: Boolean,
    onDecrease: () -> Unit, onIncrease: () -> Unit) {
    val language = appLanguage()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = canDecrease, onClick = onDecrease) {
                Icon(AppIcons.Minus, "$title: " + lt(language, "Diminuir", "Decrease", "Reducir"), Modifier.size(18.dp))
            }
            Text(value, Modifier.widthIn(min = 48.dp, max = 104.dp), fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(enabled = canIncrease, onClick = onIncrease) {
                Icon(AppIcons.Add, "$title: " + lt(language, "Aumentar", "Increase", "Aumentar"), Modifier.size(18.dp))
            }
        }
    }
}
