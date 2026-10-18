package com.mewname.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mewname.app.domain.*

@Composable
internal fun MoveSearchPanel(
    query: String, onQuery: (String) -> Unit, label: String,
    category: MoveCategory?, onCategory: (MoveCategory?) -> Unit,
    type: String?, onType: (String?) -> Unit, types: List<String>, language: AppLanguage, showCategory: Boolean = true,
    order: MoveSort = MoveSort.NAME, onOrder: (MoveSort) -> Unit = {},
    reversed: Boolean = false, onReversed: (Boolean) -> Unit = {}, allowNationalDex: Boolean = false
) {
    val defaultOrder = if (allowNationalDex) MoveSort.NATIONAL_DEX else MoveSort.NAME
    AppFilterPanel(query.isNotBlank() || category != null || type != null || order != defaultOrder || reversed,
        onClear = { onQuery(""); onCategory(null); onType(null); onOrder(defaultOrder); onReversed(false) }) {
        AppGlassTextField(search = true, value = query, onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) })
        if (showCategory) MoveCategoryFilter(category, { onCategory(if (category == it) null else it) }, language)
        TypeSelectorSingle(types, type, onType, language, showAllOption = false)
        MoveSortControl(order, onOrder, reversed, onReversed, allowNationalDex)
    }
}

internal fun matchesMoveFilters(move: MoveCatalogEntry?, category: MoveCategory?, type: String?): Boolean =
    (category == null || move?.category == category) && (type == null || move?.type == type)