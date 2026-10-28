package com.mewname.app

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mewname.app.model.NamingBlock
import kotlinx.coroutines.delay

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun NamingPatternReorder(blocks: List<NamingBlock>, onChange: (List<NamingBlock>) -> Unit) {
    val language = appLanguage()
    val state = rememberLazyListState()
    val current by rememberUpdatedState(blocks)
    val update by rememberUpdatedState(onChange)
    var dragged by remember { mutableStateOf<String?>(null) }
    var left by remember { mutableStateOf(0f) }
    var width by remember { mutableStateOf(0) }
    var pointer by remember { mutableStateOf(0f) }
    val edge = with(LocalDensity.current) { 36.dp.toPx() }
    fun reorder() {
        val id = dragged ?: return
        val visible = state.layoutInfo.visibleItemsInfo
        val ids = current.map { it.id }
        val to = reorderStepTarget(ids, id, left + width / 2f) { key ->
            visible.firstOrNull { it.key == key }?.let { it.offset + it.size / 2f }
        } ?: return
        val from = ids.indexOf(id)
        update(current.toMutableList().apply { add(to, removeAt(from)) })
    }
    LaunchedEffect(dragged) {
        while (dragged != null) {
            val info = state.layoutInfo
            val delta = when {
                pointer < info.viewportStartOffset + edge -> -edge / 6
                pointer > info.viewportEndOffset - edge -> edge / 6
                else -> 0f
            }
            if (delta != 0f) { state.scrollBy(delta); reorder() }
            delay(16)
        }
    }
    if (blocks.isEmpty()) {
        Text(lt(language, "Nenhum campo adicionado ainda", "No fields added yet", "Todavía no se agregaron campos"),
            Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.labelLarge, color = appControlInk())
        return
    }
    // The row's own scroll handles pointer events before this modifier; left on, it takes the drag after the long
    // press and cancels the move. Edge auto-scroll keeps long patterns reachable while dragging.
    LazyRow(state = state, userScrollEnabled = dragged == null, modifier = Modifier.fillMaxWidth().height(48.dp).pointerInput(Unit) {
        detectDragGesturesAfterLongPress(onDragStart = { point ->
            val item = state.layoutInfo.visibleItemsInfo.firstOrNull { point.x >= it.offset && point.x < it.offset + it.size }
            dragged = item?.key as? String
            if (item != null) { left = item.offset.toFloat(); width = item.size; pointer = point.x }
        }, onDragEnd = { dragged = null }, onDragCancel = { dragged = null }) { change, amount ->
            if (dragged != null) { change.consume(); pointer = change.position.x; left += amount.x; reorder() }
        }
    }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        items(blocks, key = { it.id }) { block ->
            val active = dragged == block.id
            Surface(color = appControlFill(selected = true), contentColor = appControlInk(), shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(if (active) 1.5.dp else .75.dp,
                    appControlInk().copy(alpha = if (active) .8f else .30f)),
                // The others slide to their new place; the held chip follows the finger instead.
                modifier = (if (active) Modifier else Modifier.animateItemPlacement()).zIndex(if (active) 1f else 0f).graphicsLayer {
                    val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == block.id }
                    translationX = if (active && item != null) left - item.offset else 0f
                    val scale = if (active) 1.06f else 1f
                    scaleX = scale; scaleY = scale
                    shadowElevation = if (active) 8.dp.toPx() else 0f
                }) {
                Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(block.localizedLabel(language), Modifier.widthIn(max = 160.dp), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { update(current.filterNot { it.id == block.id }) }, modifier = Modifier.size(40.dp)) {
                        Icon(AppIcons.Close, lt(language, "Remover campo", "Remove field", "Eliminar campo"), Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
