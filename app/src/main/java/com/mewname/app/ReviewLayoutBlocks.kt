package com.mewname.app

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mewname.app.domain.AppLanguage
import org.json.JSONArray
import org.json.JSONObject

internal const val reviewBlockPrefix = "block:"
internal data class ReviewLayoutBlock(val id: String, val title: String = "", val width: Int = 100, val fields: List<String> = emptyList()) {
    val key get() = reviewBlockPrefix + id
    fun label(language: AppLanguage) = title.ifBlank { lt(language, "Bloco sem t\u00edtulo", "Untitled block", "Bloque sin t\u00edtulo") }
}
internal fun jsonReviewStrings(array: JSONArray?): List<String> = if (array == null) emptyList()
    else (0 until array.length()).mapNotNull { array.opt(it) as? String }
internal fun readReviewBlocks(array: JSONArray?): List<ReviewLayoutBlock> = if (array == null) emptyList()
    else (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let {
        ReviewLayoutBlock(it.optString("id"), it.optString("title"), it.optInt("width", 100), jsonReviewStrings(it.optJSONArray("fields")))
    } }
internal fun writeReviewBlocks(blocks: List<ReviewLayoutBlock>) = JSONArray().apply { blocks.forEach {
    put(JSONObject().put("id", it.id).put("title", it.title).put("width", it.width).put("fields", JSONArray(it.fields)))
} }
internal fun normalizeReviewBlocks(tab: ReviewLayoutTab): ReviewLayoutTab {
    val used = mutableSetOf<String>()
    val blocks = tab.blocks.filter { it.id.isNotBlank() }.distinctBy { it.id }.map {
        it.copy(title = it.title.trim().take(40), width = it.width.takeIf { w -> w in 5..100 && w % 5 == 0 } ?: 100,
            fields = it.fields.filter { f -> f in tab.fields && used.add(f) })
    }
    val valid = tab.fields.toSet() + blocks.map { it.key }
    return tab.copy(blocks = blocks, order = tab.order.filter { it in valid }.distinct())
}
/** Independent fields and blocks share one order; membership never removes a field from its tab. */
internal fun reviewTabItems(tab: ReviewLayoutTab): List<String> {
    val owner = tab.blocks.flatMap { b -> b.fields.map { it to b.key } }.toMap()
    return (tab.order + tab.fields + tab.blocks.map { it.key }).map { owner[it] ?: it }.distinct()
}
internal fun assignReviewBlock(tab: ReviewLayoutTab, field: String, blockId: String?): ReviewLayoutTab {
    if (field !in tab.fields || (blockId != null && tab.blocks.none { it.id == blockId })) return tab
    val order = reviewTabItems(tab).toMutableList()
    val previous = tab.blocks.firstOrNull { field in it.fields }
    if (previous != null && blockId == null && field !in order) order.add((order.indexOf(previous.key) + 1).coerceAtLeast(0), field)
    return normalizeReviewBlocks(tab.copy(order = order, blocks = tab.blocks.map {
        it.copy(fields = if (it.id == blockId) (it.fields + field).distinct() else it.fields - field)
    }))
}
internal fun deleteReviewBlock(tab: ReviewLayoutTab, blockId: String): ReviewLayoutTab {
    val block = tab.blocks.firstOrNull { it.id == blockId } ?: return tab
    return tab.copy(blocks = tab.blocks.filterNot { it.id == blockId },
        order = reviewTabItems(tab).flatMap { if (it == block.key) block.fields else listOf(it) }.distinct())
}

/** The inner grid uses percentages of the block's content width, with automatic height. */
@Composable internal fun ReviewTabGrid(tab: ReviewLayoutTab, visible: List<String>,
    onChange: ((ReviewLayoutTab) -> Unit)? = null, onOpen: ((String) -> Unit)? = null,
    onEditBlock: ((String) -> Unit)? = null, onReorderBlock: ((List<String>) -> Unit)? = null, content: @Composable RowScope.(String) -> Unit) {
    val layout = LocalReviewLayout.current
    val language = appLanguage()
    val blocks = tab.blocks.associateBy { it.key }
    val keys = reviewTabItems(tab).filter { key ->
        blocks[key]?.let { onChange != null || it.fields.any { f -> f in visible } } ?: (key in visible)
    }
    ReviewScreenGrid(keys, onReorder = onChange?.let { save -> { ordered ->
        save(tab.copy(order = mergeReviewVisibleOrder(reviewTabItems(tab), ordered)))
    } }, onOpen = onOpen, widthOverride = { key, available -> blocks[key]?.let { block ->
        val minWidth = block.fields.filter { it in visible }.maxOfOrNull { layout.minimumFieldWidth(it) } ?: 40f
        (((available + 6f) * block.width / 100f) - 6f).coerceAtLeast(minWidth + if (layout.compact) 12f else 20f).coerceAtMost(available)
    } }) { key ->
        val block = blocks[key]
        if (block == null) content(key) else GlassSection(
            Modifier.fillMaxWidth().then(if (onEditBlock != null) Modifier.clickable(
                onClickLabel = lt(language, "Editar bloco", "Edit block", "Editar bloque"),
                onClick = { onEditBlock(block.id) }) else Modifier)
        ) {
            if (block.title.isNotBlank()) {
                Row(Modifier.fillMaxWidth().heightIn(min = GlassLabelHeight), verticalAlignment = Alignment.CenterVertically) {
                    Text(block.title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Normal)
                }
            }
            val children = block.fields.filter { it in visible }
            if (children.isEmpty() && onEditBlock != null) {
                Text(lt(language, "Adicionar tags", "Add tags", "Agregar etiquetas"),
                    Modifier.fillMaxWidth().padding(vertical = 8.dp), style = MaterialTheme.typography.labelSmall)
            } else {
                // The outer grid owns long presses here. An inner drag detector would
                // compete with moving the whole block; reorder its tags in the editor.
                ReviewScreenGrid(children, nested = true, onOpen = onOpen, onReorder = onReorderBlock, content = content)
            }
        }
    }
}

@Composable internal fun ReviewBlockPicker(field: String, layout: ReviewLayout, prefs: SharedPreferences) {
    val language = appLanguage()
    val tabs = layout.tabs ?: defaultReviewScreenTabs
    val tab = tabs.firstOrNull { field in it.fields } ?: return
    if (tab.blocks.isEmpty()) return
    val chosen = tab.blocks.firstOrNull { field in it.fields }?.id
    Text(lt(language, "Bloco", "Block", "Bloque"), fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        fun save(id: String?) = saveReviewTabs(prefs, tabs.map { if (it.id == tab.id) assignReviewBlock(it, field, id) else it })
        AppChoiceChip(chosen == null, { save(null) }, label = { Text(lt(language, "Sem bloco", "No block", "Sin bloque")) })
        tab.blocks.forEach { b -> AppChoiceChip(chosen == b.id, { save(b.id) }, label = { Text(b.label(language)) }) }
    }
}

@Composable internal fun ReviewBlockEditor(tab: ReviewLayoutTab, id: String, prefs: SharedPreferences, previewWidth: androidx.compose.ui.unit.Dp, onClose: () -> Unit) {
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    val original = tab.blocks.firstOrNull { it.id == id }
    var title by remember(tab.id, id) { mutableStateOf(original?.title.orEmpty()) }
    var width by remember(tab.id, id) { mutableIntStateOf(original?.width ?: 100) }
    var fields by remember(tab.id, id) { mutableStateOf(original?.fields.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun update(change: (ReviewLayoutTab) -> ReviewLayoutTab) {
        val current = ReviewLayout.read(prefs).tabs ?: defaultReviewScreenTabs
        saveReviewTabs(prefs, current.map { if (it.id == tab.id) change(it) else it })
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        AppModalPanel(title = tr(if (original == null) "Criar bloco" else "Editar bloco", if (original == null) "Create block" else "Edit block", if (original == null) "Crear bloque" else "Editar bloque"),
            onClose = onClose, actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (original != null) AppActionButton(onClick = { confirmDelete = true }) { Text(tr("Excluir bloco", "Delete block", "Eliminar bloque")) }
                AppActionButton(onClick = {
                    val block = ReviewLayoutBlock(original?.id ?: java.util.UUID.randomUUID().toString(), title, width, fields)
                    update { current ->
                        val revised = current.blocks.filterNot { it.id == block.id }.map { it.copy(fields = it.fields - fields.toSet()) } + block
                        val released = current.blocks.firstOrNull { it.id == block.id }?.fields.orEmpty() - fields.toSet()
                        val order = reviewTabItems(current).toMutableList()
                        val position = order.indexOf(block.key).takeIf { it >= 0 } ?: order.size
                        order.addAll(position, released)
                        current.copy(blocks = revised, order = order)
                    }
                    onClose()
                }) { Text(tr("Salvar", "Save", "Guardar")) }
                }
            }) {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppGlassTextField(title, { title = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(tr("T\u00edtulo (opcional)", "Title (optional)", "T\u00edtulo (opcional)")) })
                ReviewDimensionRow(tr("Largura", "Width", "Ancho"), "$width%", width > 5, width < 100, { width -= 5 }, { width += 5 })
                if (fields.isNotEmpty()) {
                    Text(tr("Ordem das tags", "Tag order", "Orden de etiquetas"), fontWeight = FontWeight.Bold)
                    val previewLayout = ReviewLayout.read(prefs)
                    val previewBlock = ReviewLayoutBlock(id, title, width, fields)
                    val previewTab = tab.copy(fields = fields, blocks = listOf(previewBlock), order = listOf(previewBlock.key))
                    // Keep the same available width as the main preview; a narrow dialog
                    // scrolls horizontally rather than resizing the configured controls.
                    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        Box(Modifier.width(previewWidth)) {
                            CompositionLocalProvider(LocalReviewLayout provides previewLayout, LocalGlassReviewStyle provides true,
                                LocalHideReviewLogMarkers provides true, LocalReviewSelectorCustomization provides true) {
                                ReviewTabGrid(previewTab, fields.filter(previewLayout::fieldVisible), onOpen = {},
                                    onReorderBlock = { fields = mergeReviewVisibleOrder(fields, it) }) { field -> ReviewScreenPreviewField(field) }
                            }
                        }
                    }
                }
                Text(tr("Tags", "Tags", "Etiquetas"), fontWeight = FontWeight.Bold)
                tab.fields.forEach { field ->
                    AppToggleRow(reviewScreenLabel(field, language), field in fields,
                        { enabled -> fields = if (enabled) (fields + field).distinct() else fields - field })
                }
            }
        }
    }
    if (confirmDelete && original != null) androidx.compose.ui.window.Dialog(onDismissRequest = { confirmDelete = false }) {
        AppModalPanel(title = tr("Excluir bloco?", "Delete block?", "\u00bfEliminar bloque?"), onClose = { confirmDelete = false }, actions = {
            AppActionButton(onClick = { update { deleteReviewBlock(it, original.id) }; onClose() }) { Text(tr("Excluir", "Delete", "Eliminar")) }
        }) { Text(tr("As tags permanecem na aba, com suas configura\u00e7\u00f5es.", "Tags stay on the tab with their settings.", "Las etiquetas permanecen en la pesta\u00f1a con sus ajustes.")) }
    }
}
