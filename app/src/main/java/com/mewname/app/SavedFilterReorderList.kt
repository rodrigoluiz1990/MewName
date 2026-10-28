package com.mewname.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Saved filter cards, reordered by holding one: it follows the finger while the others slide to open the gap where
 * it will land; the list itself only changes on release, so nothing jumps under the finger. Shared by the Filters
 * screen and the bubble's filter window. [onDraggingChange] lets a scrolling parent stop scrolling during a drag.
 */
@Composable
internal fun SavedFilterReorderList(
    filters: List<SavedFilterEntry>,
    onReorder: ((List<SavedFilterEntry>) -> Unit)?,
    spacing: Dp = 8.dp,
    onDraggingChange: (Boolean) -> Unit = {},
    card: @Composable (entry: SavedFilterEntry, modifier: Modifier) -> Unit
) {
    val language = appLanguage()
    // Keyed by content: the builders pass a mutable state list whose identity never changes.
    val current = filters.toList()
    var order by remember(current) { mutableStateOf(current) }
    val tops = remember { mutableStateMapOf<String, Float>() }
    val heights = remember { mutableStateMapOf<String, Int>() }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val reorder by rememberUpdatedState(onReorder)
    val draggingChanged by rememberUpdatedState(onDraggingChange)
    val gapPx = with(LocalDensity.current) { spacing.toPx() }
    fun dropIndex(id: String): Int = savedFilterDropIndex(order.map { it.id }, id,
        (tops[id] ?: 0f) + dragOffset + (heights[id] ?: 0) / 2f) { key -> tops[key]?.let { it + (heights[key] ?: 0) / 2f } }
    val from = draggedId?.let { id -> order.indexOfFirst { it.id == id } } ?: -1
    val target = draggedId?.let(::dropIndex) ?: -1
    val gap = draggedId?.let { (heights[it] ?: 0) + gapPx } ?: 0f
    fun finish() {
        draggedId = null; dragOffset = 0f
        draggingChanged(false)
    }
    // Runs from the gesture, so it reads the current state rather than this composition's values.
    fun drop() {
        val id = draggedId
        if (id != null) {
            val start = order.indexOfFirst { it.id == id }
            val end = dropIndex(id)
            if (start >= 0 && end != start) {
                order = order.toMutableList().apply { add(end, removeAt(start)) }
                reorder?.invoke(order)
            }
        }
        finish()
    }
    fun move(entry: SavedFilterEntry, delta: Int): Boolean {
        val index = order.indexOf(entry)
        if (index + delta !in order.indices) return false
        order = order.toMutableList().apply { add(index + delta, removeAt(index)) }
        reorder?.invoke(order)
        return true
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        order.forEachIndexed { index, entry -> key(entry.id) {
            val dragging = draggedId == entry.id
            val shift = when {
                from < 0 || dragging -> 0f
                index in target until from -> gap
                index in (from + 1)..target -> -gap
                else -> 0f
            }
            // Snaps on release, when the reordered layout already puts each card where it was shown.
            val slide by animateFloatAsState(shift, if (draggedId == null) snap() else spring(), label = "savedFilterSlide")
            card(entry, Modifier.fillMaxWidth()
                // Measured only at rest: during a drag the layout does not change, and the drawn offset must not
                // feed back into the positions.
                .onGloballyPositioned { if (draggedId == null) { tops[entry.id] = it.positionInParent().y; heights[entry.id] = it.size.height } }
                .zIndex(if (dragging) 1f else 0f)
                .graphicsLayer {
                    translationY = if (dragging) dragOffset else slide
                    val scale = if (dragging) 1.03f else 1f
                    scaleX = scale; scaleY = scale
                    shadowElevation = if (dragging) 12.dp.toPx() else 0f
                    alpha = if (dragging) .96f else 1f
                }
                // Restarted when the list changes, so the gesture never holds a previous list's state.
                .then(if (onReorder == null) Modifier else Modifier.pointerInput(entry.id, current) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggedId = entry.id; dragOffset = 0f
                            draggingChanged(true)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragEnd = { drop() },
                        onDragCancel = { finish() }
                    ) { change, amount -> change.consume(); dragOffset += amount.y }
                }.semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(lt(language, "Mover para cima", "Move up", "Mover arriba")) { move(entry, -1) },
                        CustomAccessibilityAction(lt(language, "Mover para baixo", "Move down", "Mover abajo")) { move(entry, 1) }
                    )
                }))
        } }
    }
}
