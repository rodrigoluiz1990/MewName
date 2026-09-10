package com.mewname.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mewname.app.domain.AppLanguage
import com.mewname.app.model.NamingField

data class ReviewLogRequest(
    val fields: Set<NamingField>,
    val includeOcr: Boolean = false,
    val includeNames: Boolean = false
)

internal val reviewLogFieldGroups = listOf(
    listOf(NamingField.POKEMON_NAME, NamingField.GENDER, NamingField.UNIQUE_FORM, NamingField.UNOWN_LETTER,
        NamingField.VIVILLON_PATTERN, NamingField.POKEDEX_NUMBER),
    listOf(NamingField.CP, NamingField.LEVEL, NamingField.IV_PERCENT, NamingField.IV_COMBINATION,
        NamingField.MASTER_IV_BADGE, NamingField.PVP_LEAGUE, NamingField.PVP_RANK),
    NamingField.entries.filter { it !in setOf(NamingField.POKEMON_NAME, NamingField.GENDER,
        NamingField.UNIQUE_FORM, NamingField.UNOWN_LETTER, NamingField.VIVILLON_PATTERN, NamingField.POKEDEX_NUMBER,
        NamingField.CP, NamingField.LEVEL, NamingField.IV_PERCENT, NamingField.IV_COMBINATION,
        NamingField.MASTER_IV_BADGE, NamingField.PVP_LEAGUE, NamingField.PVP_RANK) }
)

@Composable
internal fun ReviewLogSelectionModal(
    language: AppLanguage,
    selected: Set<NamingField>, onSelectionChange: (Set<NamingField>) -> Unit,
    includeOcr: Boolean, onIncludeOcrChange: (Boolean) -> Unit,
    includeNames: Boolean, onIncludeNamesChange: (Boolean) -> Unit,
    onDismiss: () -> Unit, onExport: () -> Unit
) {
    ReviewModalCard(Modifier.widthIn(max = 440.dp).fillMaxWidth().heightIn(max = 560.dp).clickable { }) {
        Column(Modifier.padding(12.dp)) {
            ReviewModalHeader(lt(language, "Exportar log", "Export log", "Exportar registro"), onDismiss)
            Row {
                TextButton(onClick = { onSelectionChange(NamingField.entries.toSet()) }) {
                    Text(lt(language, "Selecionar todos", "Select all", "Seleccionar todos"))
                }
                TextButton(onClick = { onSelectionChange(emptySet()); onIncludeOcrChange(false); onIncludeNamesChange(false) }) {
                    Text(lt(language, "Limpar", "Clear", "Limpiar"))
                }
            }
            Text(lt(language, "${selected.size} campos selecionados", "${selected.size} fields selected", "${selected.size} campos seleccionados"),
                style = MaterialTheme.typography.bodySmall)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                val titles = listOf(
                    lt(language, "Identificação", "Identification", "Identificación"),
                    lt(language, "Valores e cálculos", "Values and calculations", "Valores y cálculos"),
                    lt(language, "Ataques e atributos", "Moves and attributes", "Ataques y atributos")
                )
                reviewLogFieldGroups.forEachIndexed { index, fields ->
                    Text(titles[index], style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    LogToggleGrid(fields.map { field ->
                        val label = when (field) {
                            NamingField.LEVEL -> lt(language, "Nível e custos", "Level and costs", "Nivel y costes")
                            NamingField.LEGACY_MOVE_NAME -> lt(language, "Ataques rápido e carregado", "Fast and charged moves", "Ataques rápido y cargado")
                            else -> field.localizedLabel(language)
                        }
                        LogToggleOption(label, field in selected) {
                            onSelectionChange(if (it) selected + field else selected - field)
                        }
                    })
                }
                Text(lt(language, "Informações adicionais", "Additional information", "Información adicional"),
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                LogToggleGrid(listOf(
                    LogToggleOption(lt(language, "OCR completo e coordenadas", "Full OCR and coordinates", "OCR completo y coordenadas"), includeOcr, onIncludeOcrChange),
                    LogToggleOption(lt(language, "Composição dos nomes sugeridos", "Suggested name composition", "Composición de nombres sugeridos"), includeNames, onIncludeNamesChange)
                ))
            }
            Button(onClick = onExport, enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(lt(language, "Exportar selecionados", "Export selected", "Exportar seleccionados"))
            }
        }
    }
}

private data class LogToggleOption(val label: String, val checked: Boolean, val onChange: (Boolean) -> Unit)

@Composable
private fun LogToggleGrid(options: List<LogToggleOption>) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { option ->
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxHeight().defaultMinSize(minHeight = 52.dp)
                            .toggleable(value = option.checked, role = Role.Checkbox, onValueChange = option.onChange),
                        shape = RoundedCornerShape(if (LocalGlassReviewStyle.current) GlassFieldCorner else 10.dp),
                        color = if (LocalGlassReviewStyle.current) glassFieldColor(option.checked) else if (option.checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        contentColor = if (option.checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        border = if (LocalGlassReviewStyle.current) glassFieldBorder(option.checked) else BorderStroke(if (option.checked) 2.dp else 1.dp,
                            if (option.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Box(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(option.label, style = MaterialTheme.typography.labelLarge, fontWeight = if (LocalGlassReviewStyle.current) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium, textAlign = TextAlign.Center)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}