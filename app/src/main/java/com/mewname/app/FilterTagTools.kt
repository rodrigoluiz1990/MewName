package com.mewname.app

import android.content.Context
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class CustomFilterTag(val id: String, val name: String, val value: String)
internal class FilterTagStore(context: Context) {
    private val prefs = context.getSharedPreferences("mewname_prefs", Context.MODE_PRIVATE)
    fun tags(): List<CustomFilterTag> = runCatching {
        val data = JSONArray(prefs.getString("custom_filter_tags", "[]"))
        (0 until data.length()).map { data.getJSONObject(it).let { row ->
            CustomFilterTag(row.getString("id"), row.getString("name"), row.getString("value"))
        } }
    }.getOrDefault(emptyList())
    fun save(id: String?, name: String, value: String): CustomFilterTag {
        val tag = CustomFilterTag(id ?: "custom:" + UUID.randomUUID(), name.trim(), value.trim())
        require(tag.name.isNotBlank() && tag.value.isNotBlank())
        write(tags().filterNot { it.id == tag.id } + tag)
        return tag
    }
    fun delete(id: String) {
        write(tags().filterNot { it.id == id })
        prefs.edit().remove("filter_tag_icon:$id").putStringSet("favorite_filter_tokens",
            prefs.getStringSet("favorite_filter_tokens", emptySet()).orEmpty() - id).apply()
    }
    private fun write(tags: List<CustomFilterTag>) {
        val data = JSONArray()
        tags.forEach { data.put(JSONObject().put("id", it.id).put("name", it.name).put("value", it.value)) }
        prefs.edit().putString("custom_filter_tags", data.toString()).apply()
    }
    fun icon(id: String): String? = if (prefs.contains("filter_tag_icon:$id"))
        prefs.getString("filter_tag_icon:$id", "").orEmpty().ifBlank { null } else defaultFilterIcon(id)
    fun icon(id: String, path: String?) { prefs.edit().putString("filter_tag_icon:$id", path.orEmpty()).apply() }

}

internal fun defaultFilterIcon(id: String): String? = when (id) {
    "legendary" -> "filter_icons/legendary_color.png"
    "mythical" -> "filter_icons/mythical_color.png"
    "ultrabeast" -> "filter_icons/ultra_beast.png"
    "costume" -> "filter_icons/costume_color.png"
    else -> null
}
@Composable
internal fun FilterTagEditor(title: String, initialName: String, initialValue: String, initialIcon: String?, custom: Boolean,
    onSave: (String, String, String?, Boolean) -> Unit, onDelete: (() -> Unit)?, onClose: () -> Unit, inline: Boolean = false, initialFavorite: Boolean = false, allowIcon: Boolean = true) {
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    val context = LocalContext.current
    // Preserve the actual search of older tags that had a separate display label.
    var name by rememberSaveable { mutableStateOf(if (custom) initialValue.ifBlank { initialName } else initialName) }
    var icon by rememberSaveable { mutableStateOf(initialIcon) }
    var favorite by rememberSaveable { mutableStateOf(initialFavorite) }
    var search by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val content: @Composable () -> Unit = {
        val appearance = LocalAppAppearance.current
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp), color = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = appearance.text, border = androidx.compose.foundation.BorderStroke(.75.dp, appearance.border)) {
            Column(Modifier.fillMaxWidth().then(if (allowIcon) Modifier.heightIn(max = 580.dp).height(540.dp) else Modifier)
                .then(Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(appearance.background))).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppModalHeader(title, onClose = onClose)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (allowIcon && icon != null) FilterTagIcon(icon!!, Modifier.size(40.dp), filterIconLabel(icon!!))
                    if (custom) AppGlassTextField(name, { name = it }, Modifier.weight(1f), singleLine = true,
                        label = { Text(tr("Tag", "Tag", "Etiqueta")) })
                    else Text(initialName, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Favorita", "Favorite", "Favorita"), Modifier.weight(1f))
                    AppToggle(checked = favorite, onCheckedChange = { favorite = it })
                }
                if (allowIcon) {
                AppGlassTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true, search = true,
                    label = { Text(tr("Buscar ícone", "Find icon", "Buscar icono")) })
                SharedTaskIconPicker(icon, search, { icon = it }, Modifier.fillMaxWidth().weight(1f))
                if (onDelete != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { confirmDelete = !confirmDelete }) { Text(tr("Excluir", "Delete", "Eliminar")) }
                }
                }
                if (confirmDelete) TextButton(onClick = { onDelete?.invoke() }) { Text(tr("Confirmar exclusão da tag", "Confirm tag deletion", "Confirmar eliminación")) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    AppActionButton(onClick = { onSave(name, name, icon, favorite) }, enabled = !custom || name.isNotBlank(), modifier = Modifier.widthIn(min = 140.dp)) {
                        Text(tr("Salvar", "Save", "Guardar"))
                    }
                }
            }
        }
    }
    if (inline) content() else androidx.compose.ui.window.Dialog(onDismissRequest = onClose) { content() }
}

/** Explicit reorder mode keeps drag gestures separate from long-press favorites. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun FilterTagReorder(ids: List<String>, label: (String) -> String, onOrder: (List<String>) -> Unit) {
    val state = rememberLazyGridState()
    val currentIds by rememberUpdatedState(ids)
    val update by rememberUpdatedState(onOrder)
    var dragged by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    fun move() {
        val id = dragged ?: return
        val center = offset + androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val target = state.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != id && center.x >= it.offset.x && center.x < it.offset.x + it.size.width &&
                center.y >= it.offset.y && center.y < it.offset.y + it.size.height
        }?.key as? String ?: return
        val from = currentIds.indexOf(id); val to = currentIds.indexOf(target)
        if (from >= 0 && to >= 0) update(currentIds.toMutableList().apply { add(to, removeAt(from)) })
    }
    LaunchedEffect(dragged) {
        while (dragged != null) {
            val end = state.layoutInfo.viewportEndOffset
            val scroll = when { position.y < 60 -> -12f; position.y > end - 60 -> 12f; else -> 0f }
            if (scroll != 0f) { state.scrollBy(scroll); move() }
            delay(16)
        }
    }
    // The grid's own scroll sees pointer events before this modifier and would take a vertical drag.
    LazyVerticalGrid(columns = GridCells.Fixed(3), state = state, userScrollEnabled = dragged == null,
        modifier = Modifier.fillMaxWidth().height((((ids.size + 2) / 3).coerceIn(1, 3) * 54 - 6).dp).pointerInput(Unit) {
            detectDragGesturesAfterLongPress(onDragStart = { point ->
                val item = state.layoutInfo.visibleItemsInfo.firstOrNull { point.x >= it.offset.x && point.x < it.offset.x+it.size.width && point.y >= it.offset.y && point.y < it.offset.y+it.size.height }
                dragged = item?.key as? String
                if (item != null) { offset = androidx.compose.ui.geometry.Offset(item.offset.x.toFloat(), item.offset.y.toFloat()); size = item.size; position = point }
            }, onDragEnd = { dragged = null }, onDragCancel = { dragged = null }) { change, amount ->
                if (dragged != null) { change.consume(); position = change.position; offset += amount; move() }
            }
        }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(ids, key = { it }) { id ->
            val active = id == dragged
            Surface(color = appControlFill(selected = true), contentColor = appControlInk(),
                border = androidx.compose.foundation.BorderStroke(if (active) 1.5.dp else .75.dp,
                    appControlInk().copy(alpha = if (active) .8f else .30f)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                // The others slide to their new place; the held tag follows the finger instead.
                modifier = (if (active) Modifier else Modifier.animateItemPlacement()).zIndex(if (active) 1f else 0f).graphicsLayer {
                    val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                    translationX = if (active && item != null) offset.x-item.offset.x else 0f
                    translationY = if (active && item != null) offset.y-item.offset.y else 0f
                    val scale = if (active) 1.06f else 1f
                    scaleX = scale; scaleY = scale
                    shadowElevation = if (active) 8.dp.toPx() else 0f
                }) {
                Box(Modifier.height(48.dp).padding(6.dp), contentAlignment = Alignment.Center) {
                    Text(label(id), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}
