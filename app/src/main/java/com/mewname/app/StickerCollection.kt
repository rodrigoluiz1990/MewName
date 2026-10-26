package com.mewname.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class StickerCollectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("personal_collection_v1", Context.MODE_PRIVATE)
    fun collected(): Set<String> = prefs.getStringSet("stickers", emptySet()).orEmpty().toSet()
    fun mark(id: String, collected: Boolean) {
        val updated = collected().toMutableSet()
        if (collected) updated.add(id) else updated.remove(id)
        prefs.edit().putStringSet("stickers", updated).apply()
    }
}

@Composable
internal fun StickerCollectionContent(showFilters: Boolean, onFilterActive: (Boolean) -> Unit) {
    val context = LocalContext.current
    val language = appLanguage()
    val store = remember { StickerCollectionStore(context) }
    var collected by remember { mutableStateOf(store.collected()) }
    var query by rememberSaveable { mutableStateOf("") }
    var onlyCollected by rememberSaveable { mutableStateOf(false) }
    val files by produceState<List<String>?>(null) {
        value = withContext(Dispatchers.IO) { context.assets.list("stickers").orEmpty().filter { it.endsWith(".webp") }.sorted() }
    }
    fun label(file: String) = file.removePrefix("sticker_").removeSuffix(".webp").replace('_', ' ')
    val visible = remember(files, query, collected, onlyCollected) {
        files.orEmpty().filter { label(it).contains(query.trim(), true) && (!onlyCollected || it in collected) }
    }
    SideEffect { onFilterActive(query.isNotBlank() || onlyCollected) }
    if (showFilters) AppFilterPanel(query.isNotBlank() || onlyCollected, onClear = { query = ""; onlyCollected = false }) {
        AppGlassTextField(query, { query = it }, Modifier.fillMaxWidth(), search = true, singleLine = true,
            label = { Text(lt(language, "Buscar adesivo", "Search sticker", "Buscar pegatina")) })
        AppToggleRow(lt(language, "Mostrar somente coletados", "Collected only", "Solo obtenidos"), onlyCollected, { onlyCollected = it })
    }
    if (files == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoadingIndicator() }
    else LazyVerticalGrid(columns = GridCells.Adaptive(96.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = LocalNavigationContentPadding.current + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (visible.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(lt(language, "Nenhum adesivo encontrado.", "No stickers found.", "No se encontraron pegatinas."))
        }
        items(visible, key = { it }) { file ->
            CollectionTile(label(file), file in collected, onClick = {
                store.mark(file, file !in collected)
                collected = store.collected()
            }) {
                AsyncImage("file:///android_asset/stickers/$file", label(file), Modifier.size(64.dp))
            }
        }
    }
}
