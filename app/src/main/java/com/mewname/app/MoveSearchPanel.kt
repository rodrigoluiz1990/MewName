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
    type: String?, onType: (String?) -> Unit, types: List<String>, language: AppLanguage, showCategory: Boolean = true
) {
    AppSectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppGlassTextField(search = true, value = query, onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) })
            if (showCategory) MoveCategoryFilter(category, { onCategory(if (category == it) null else it) }, language)
            TypeSelectorSingle(types, type, onType, language, showAllOption = false)
            TextButton(modifier = Modifier.align(Alignment.End),
                onClick = { onQuery(""); onCategory(null); onType(null) },
                enabled = query.isNotBlank() || category != null || type != null) {
                Text(when (language) {
                    AppLanguage.PT_BR -> "Limpar filtros"
                    AppLanguage.EN -> "Clear filters"
                    AppLanguage.ES -> "Borrar filtros"
                })
            }
        }
    }
}

internal fun matchesMoveFilters(move: MoveCatalogEntry?, category: MoveCategory?, type: String?): Boolean =
    (category == null || move?.category == category) && (type == null || move?.type == type)