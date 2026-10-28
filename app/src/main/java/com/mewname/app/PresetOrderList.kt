package com.mewname.app

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.time.LocalDate

import com.mewname.app.model.NamingConfig

internal fun orderedNamingConfigs(configs: List<NamingConfig>, ids: List<String>): List<NamingConfig> {
    if (ids.size != configs.size || ids.toSet() != configs.map { it.id }.toSet()) return configs
    val byId = configs.associateBy { it.id }
    return ids.map { byId.getValue(it) }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PresetOrderList(configs: List<NamingConfig>, onOrder: (List<String>) -> Unit,
    modifier: Modifier, content: @Composable (NamingConfig, Modifier) -> Unit) {
    var ordered by remember(configs) { mutableStateOf(configs) }
    val commit by rememberUpdatedState(onOrder)
    val listState = rememberLazyListState()
    var draggedId by remember { mutableStateOf<String?>(null) }
    var draggedTop by remember { mutableFloatStateOf(0f) }
    var draggedHeight by remember { mutableIntStateOf(0) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    fun moveDraggedPreset() {
        val id = draggedId ?: return
        val visible = listState.layoutInfo.visibleItemsInfo
        val ids = ordered.map { it.id }
        val to = reorderStepTarget(ids, id, draggedTop + draggedHeight / 2f) { key ->
            visible.firstOrNull { it.key == key }?.let { it.offset + it.size / 2f }
        } ?: return
        val from = ids.indexOf(id)
        if (from >= 0 && from != to) {
            ordered = ordered.toMutableList().apply { add(to, removeAt(from)) }
        }
    }
    LaunchedEffect(draggedId) {
        while (draggedId != null) {
            val layout = listState.layoutInfo
            val top = layout.viewportStartOffset + edge
            val bottom = layout.viewportEndOffset - layout.afterContentPadding - edge
            val speed = when {
                pointerY < top -> -((top - pointerY) / edge).coerceIn(0f, 1f) * 20f
                pointerY > bottom -> ((pointerY - bottom) / edge).coerceIn(0f, 1f) * 20f
                else -> 0f
            }
            if (speed != 0f) { listState.scrollBy(speed); moveDraggedPreset() }
            delay(16)
        }
    }
        LazyColumn(modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                    held.position.y >= it.offset && held.position.y < it.offset + it.size
                } ?: return@awaitEachGesture
                draggedId = item.key as? String
                draggedTop = item.offset.toFloat()
                draggedHeight = item.size
                pointerY = held.position.y
                try {
                    while (true) {
                        // Claim movement before the list's ordinary scrolling recognizer.
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) { change.consume(); break }
                        draggedTop += change.positionChange().y
                        pointerY = change.position.y
                        change.consume()
                        moveDraggedPreset()
                    }
                } finally { draggedId = null; commit(ordered.map { it.id }) }
            }
        }, state = listState, contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, bottom = LocalNavigationContentPadding.current + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ordered, key = { it.id }) { config ->
                val dragging = draggedId == config.id
                // The others slide to their new place; the held card follows the finger instead.
                content(config, (if (dragging) Modifier else Modifier.animateItemPlacement()).zIndex(if (dragging) 1f else 0f).graphicsLayer {
                    translationY = if (dragging) draggedTop -
                        (listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == config.id }?.offset ?: draggedTop.toInt()) else 0f
                    val scale = if (dragging) 1.03f else 1f
                    scaleX = scale; scaleY = scale
                    shadowElevation = if (dragging) 12.dp.toPx() else 0f
                    alpha = if (dragging) .95f else 1f
                })
            }
        }
}
