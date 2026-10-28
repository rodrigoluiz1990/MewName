package com.mewname.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import org.json.JSONArray
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal object BubbleActionSettings {
    val defaults = setOf("capture", "pokemon", "friends", "stop")
    val keys = defaults.toList() + bubbleAppShortcuts.map { it.key }
    fun order(context: Context): List<String> {
        val stored = context.getSharedPreferences("bubble_actions", 0).getString("order", "[]")
        val array = runCatching { JSONArray(stored) }.getOrElse { JSONArray() }
        return normalizeOrder((0 until array.length()).map { array.optString(it) })
    }
    private fun normalizeOrder(order: List<String>) = (order.filter { it in keys } + keys).distinct()
    fun saveOrder(context: Context, order: List<String>) {
        context.getSharedPreferences("bubble_actions", 0).edit()
            .putString("order", JSONArray(normalizeOrder(order)).toString()).apply()
    }
    fun selectedInOrder(context: Context): List<String> {
        val selected = selected(context)
        return order(context).filter { it in selected }
    }
    fun selected(context: Context): Set<String> {
        val stored = context.getSharedPreferences("bubble_actions", 0).getStringSet("selected", null)
        return stored?.intersect(keys.toSet())?.takeIf { it.isNotEmpty() } ?: defaults
    }
    fun save(context: Context, selected: Set<String>) {
        val valid = selected.intersect(keys.toSet())
        if (valid.isNotEmpty()) context.getSharedPreferences("bubble_actions", 0)
            .edit().putStringSet("selected", valid).apply()
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun BubbleActionPreferences() {
    val context = LocalContext.current
    val language = appLanguage()
    var selected by remember { mutableStateOf(BubbleActionSettings.selected(context)) }
    var order by remember { mutableStateOf(BubbleActionSettings.order(context)) }
    val listState = rememberLazyListState()
    var dragged by remember { mutableStateOf<String?>(null) }
    var dragTop by remember { mutableFloatStateOf(0f) }
    var dragHeight by remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    val edge = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun reorderAtPointer() {
        val key = dragged ?: return
        val center = dragTop + dragHeight / 2
        val current = order.indexOf(key)
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            val index = order.indexOf(item.key)
            index >= 0 && index != current &&
                if (index > current) center > item.offset + item.size / 2 && center < item.offset + item.size
                else center < item.offset + item.size / 2 && center > item.offset
        } ?: return
        order = order.toMutableList().apply { add(order.indexOf(target.key), removeAt(current)) }
    }
    LaunchedEffect(dragged) {
        if (dragged == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val info = listState.layoutInfo
            val distance = when {
                pointerY < info.viewportStartOffset + edge -> -12f
                pointerY > info.viewportEndOffset - edge -> 12f
                else -> 0f
            }
            if (distance != 0f) listState.scrollBy(distance)
            reorderAtPointer()
        }
    }
    // The list's own scroll sees pointer events before this modifier and would take the drag after the long press.
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = LocalNavigationContentPadding.current), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = dragged == null,
        modifier = Modifier.fillMaxSize().pointerInput(listState) {
            detectDragGesturesAfterLongPress(
                onDragStart = { point ->
                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                        point.y >= it.offset && point.y < it.offset + it.size
                    }
                    dragged = order.firstOrNull { it == item?.key }
                    if (dragged != null && item != null) {
                        dragTop = item.offset.toFloat(); dragHeight = item.size.toFloat(); pointerY = point.y
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    }
                },
                onDrag = { change, amount ->
                    if (dragged != null) {
                        change.consume(); pointerY = change.position.y; dragTop += amount.y
                        reorderAtPointer()
                    }
                },
                onDragEnd = { if (dragged != null) BubbleActionSettings.saveOrder(context, order); dragged = null },
                onDragCancel = { dragged = null; order = BubbleActionSettings.order(context) }
            )
        }) {
        items(order, key = { it }) { key ->
            val label = when (key) {
                "capture" -> lt(language, "Ler tela", "Read screen", "Leer pantalla")
                "pokemon" -> lt(language, "Filtros Pokémon", "Pokémon filters", "Filtros Pokémon")
                "friends" -> lt(language, "Filtros de amigos", "Friend filters", "Filtros de amigos")
                "stop" -> lt(language, "Remover sobreposição", "Remove overlay", "Quitar superposición")
                else -> bubbleAppShortcuts.first { it.key == key }.let { lt(language, it.pt, it.en, it.es) }
            }
            val change: (Boolean) -> Unit = { checked ->
                selected = if (checked) selected + key else selected - key
                BubbleActionSettings.save(context, selected)
            }
            val enabled = key !in selected || selected.size > 1
            val isDragging = dragged == key
            val offset = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.offset ?: 0
            fun move(delta: Int): Boolean {
                val index = order.indexOf(key)
                if (index + delta !in order.indices) return false
                order = order.toMutableList().apply { add(index + delta, removeAt(index)) }
                BubbleActionSettings.saveOrder(context, order)
                return true
            }
            AppToggleRow(label = label, checked = key in selected, enabled = enabled, onCheckedChange = change,
                // The others slide to their new place; the held row follows the finger instead.
                modifier = (if (isDragging) Modifier else Modifier.animateItemPlacement()).zIndex(if (isDragging) 1f else 0f).graphicsLayer {
                    translationY = if (isDragging) dragTop - offset else 0f
                    val scale = if (isDragging) 1.03f else 1f
                    scaleX = scale; scaleY = scale
                    shadowElevation = if (isDragging) 12.dp.toPx() else 0f
                }.semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(lt(language, "Mover para cima", "Move up", "Mover arriba")) { move(-1) },
                        CustomAccessibilityAction(lt(language, "Mover para baixo", "Move down", "Mover abajo")) { move(1) }
                    )
                })
        }
    }
}