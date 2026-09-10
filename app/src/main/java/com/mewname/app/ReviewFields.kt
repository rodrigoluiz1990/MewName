package com.mewname.app

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mewname.app.domain.AppLanguage
import com.mewname.app.domain.PvpRankCalculator
@Composable
internal fun ReviewTextRow(
    content: @Composable RowScope.() -> Unit
) {
    if (LocalGlassReviewStyle.current) {
        GlassFieldRow(content = content)
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

@Composable
internal fun CompactField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean = false,
    active: Boolean = false,
    struckThrough: Boolean = false,
    onClick: (() -> Unit)? = null,
    headerTrailing: (@Composable (() -> Unit))? = null,
    modifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (LocalGlassReviewStyle.current) 0.dp else 3.dp), modifier = modifier) {
        FieldLabelRow(label = label, trailing = headerTrailing)
        if (onClick != null) {
            CompactSelectableField(
                value = value,
                active = active,
                struckThrough = struckThrough,
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 0.dp)
            )
        } else {
            CompactTextInput(
                value = value,
                onValueChange = onValueChange,
                readOnly = readOnly,
                active = active,
                textDecoration = if (struckThrough) TextDecoration.LineThrough else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 0.dp)
            )
        }
    }
}

@Composable
internal fun CompactSelectableField(
    value: String,
    active: Boolean,
    struckThrough: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.glassFieldSize().clickable(onClick = onClick),
        shape = RoundedCornerShape(if (LocalGlassReviewStyle.current) GlassFieldCorner else 8.dp),
        color = if (LocalGlassReviewStyle.current) glassFieldColor(active) else if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
        },
        tonalElevation = 0.dp,
        border = if (LocalGlassReviewStyle.current) glassFieldBorder(active) else androidx.compose.foundation.BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.78f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (LocalGlassReviewStyle.current) GlassFieldHeight else 34.dp)
                .padding(horizontal = 8.dp, vertical = if (LocalGlassFieldGroup.current) 2.5.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                value,
                modifier = if (LocalGlassReviewStyle.current) Modifier.fillMaxWidth() else Modifier,
                textAlign = if (LocalGlassReviewStyle.current) TextAlign.Center else TextAlign.Start,
                style = TextStyle(
                    fontSize = if (LocalGlassReviewStyle.current) 12.sp else 10.sp,
                    fontWeight = if (LocalGlassReviewStyle.current && value.isNotBlank() && value != "-") FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (struckThrough) TextDecoration.LineThrough else null
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun IvValueButton(
    label: String,
    value: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    headerTrailing: (@Composable (() -> Unit))? = null,
    modifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (LocalGlassReviewStyle.current) 0.dp else 2.dp), modifier = modifier) {
        FieldLabelRow(label = label, trailing = headerTrailing)
        Surface(
            modifier = Modifier
                .fillMaxWidth().glassFieldSize()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(if (LocalGlassReviewStyle.current) GlassFieldCorner else 8.dp),
            color = if (LocalGlassReviewStyle.current) glassFieldColor(selected) else MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            tonalElevation = 0.dp,
            border = if (LocalGlassReviewStyle.current) glassFieldBorder(selected) else androidx.compose.foundation.BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        ) {
            if (LocalGlassReviewStyle.current) {
                GlassFieldValue(value?.toString().orEmpty(), small = true, trailing = {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Selecionar $label", modifier = Modifier.size(14.dp))
                })
            } else Row(
                modifier = Modifier
                    .fillMaxWidth().glassFieldSize()
                    .defaultMinSize(minHeight = if (LocalGlassReviewStyle.current) GlassFieldHeight else 34.dp)
                    .padding(horizontal = 10.dp, vertical = if (LocalGlassFieldGroup.current) 3.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (LocalGlassReviewStyle.current) Spacer(Modifier.width(18.dp))
                Text(
                    value?.toString().orEmpty(),
                    modifier = if (LocalGlassReviewStyle.current) Modifier.weight(1f) else Modifier,
                    textAlign = if (LocalGlassReviewStyle.current) TextAlign.Center else TextAlign.Start,
                    style = TextStyle(fontSize = if (LocalGlassFieldGroup.current) 14.sp else 11.sp, color = MaterialTheme.colorScheme.onSurface)
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Selecionar $label",
                    modifier = Modifier.size(18.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IvPickerModal(
    title: String,
    currentValue: Int?,
    onValueSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ReviewModalCard(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        modifier = Modifier
            .widthIn(max = 280.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (LocalGlassReviewStyle.current) ReviewModalHeader(title, onDismiss)
            else Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..15).chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                    ) {
                        row.forEach { option ->
                            if (LocalGlassReviewStyle.current) {
                                ToggleChip(option.toString(), currentValue == option, { onValueSelected(option) }, compact = true, modifier = Modifier.weight(1f))
                            } else FilterChip(
                                selected = currentValue == option,
                                onClick = { onValueSelected(option) },
                                label = { Text(option.toString(), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
            if (!LocalGlassReviewStyle.current) Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Fechar")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SelectionModalDialog(
    title: String,
    options: List<String>,
    selectedValue: String? = null,
    message: String? = null,
    onDismiss: () -> Unit,
    onOptionSelected: (String) -> Unit,
    verticalOptions: Boolean = false
) {
    ReviewModalCard(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        modifier = Modifier
            .widthIn(max = 340.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ReviewModalHeader(title, onDismiss)
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
            } else if (verticalOptions || LocalGlassReviewStyle.current) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    options.forEach { option ->
                        CompactSelectableField(
                            value = option,
                            active = option == selectedValue,
                            onClick = { onOptionSelected(option) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                FlowRow(
                    modifier = Modifier.widthIn(max = 260.dp).heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    options.forEach { option ->
                        if (LocalGlassReviewStyle.current) ToggleChip(option, option == selectedValue, { onOptionSelected(option) }, compact = true)
                        else FilterChip(selected = option == selectedValue, onClick = { onOptionSelected(option) }, label = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                    }
                }
            }
        }
    }
}
@Composable
internal fun LevelPickerDialog(context: Context, language: AppLanguage, pokemonName: String?, level: Double?, attack: Int?, defense: Int?, stamina: Int?, rankCalculator: PvpRankCalculator, onDismiss: () -> Unit, onLevelSelected: (Double) -> Unit) {
    val initialLevel = level ?: 1.0
    var previewLevel by remember(level) { mutableStateOf(initialLevel) }
    val cp = if (pokemonName != null && attack != null && defense != null && stamina != null) rankCalculator.estimateCpAtLevel(context, pokemonName, attack, defense, stamina, previewLevel) else null
    val hp = if (pokemonName != null && stamina != null) rankCalculator.estimateHpAtLevel(context, pokemonName, stamina, previewLevel) else null
    val cost = powerUpCostBetweenLevels(initialLevel, previewLevel)
    ReviewModalCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)), modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (LocalGlassReviewStyle.current) ReviewModalHeader(lt(language, "Selecionar n\u00EDvel", "Select level", "Seleccionar nivel"), onDismiss)
                else Text(lt(language, "Selecionar n\u00EDvel", "Select level", "Seleccionar nivel"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { previewLevel = (previewLevel - 0.5).coerceAtLeast(1.0) }, enabled = previewLevel > 1.0) { Text("-") }
                    Text(lt(language, "N\u00EDvel ${previewLevel.formatLevelDebug()}", "Level ${previewLevel.formatLevelDebug()}", "Nivel ${previewLevel.formatLevelDebug()}"), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { previewLevel = (previewLevel + 0.5).coerceAtMost(51.0) }, enabled = previewLevel < 51.0) { Text("+") }
                }
                Text(lt(language, "CP ${cp ?: "-"} \u2022 PS ${hp ?: "-"}", "CP ${cp ?: "-"} \u2022 HP ${hp ?: "-"}", "PC ${cp ?: "-"} \u2022 PS ${hp ?: "-"}"), style = MaterialTheme.typography.bodyMedium)
                Text(lt(language, "Poeira ${cost.stardust} \u2022 Doces ${cost.candy}", "Dust ${cost.stardust} \u2022 Candy ${cost.candy}", "Polvo ${cost.stardust} \u2022 Caramelos ${cost.candy}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onDismiss) { Text(lt(language, "Cancelar", "Cancel", "Cancelar")) }
                    TextButton(onClick = { onLevelSelected(previewLevel) }) { Text(lt(language, "Confirmar", "Confirm", "Confirmar")) }
                }
            }
    }
}

@Composable
internal fun CompactTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    active: Boolean = false,
    textDecoration: TextDecoration? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.glassFieldSize(),
        shape = RoundedCornerShape(if (LocalGlassReviewStyle.current) GlassFieldCorner else 8.dp),
        color = if (LocalGlassReviewStyle.current) glassFieldColor(active) else if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
        },
        tonalElevation = 0.dp,
        border = if (LocalGlassReviewStyle.current) glassFieldBorder(active) else androidx.compose.foundation.BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.78f) else MaterialTheme.colorScheme.outline
        )
    ) {
        if (LocalGlassReviewStyle.current && readOnly) {
            GlassFieldValue(value, trailing)
        } else Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (LocalGlassReviewStyle.current) GlassFieldHeight else 34.dp)
                .padding(horizontal = 8.dp, vertical = if (LocalGlassFieldGroup.current) 2.5.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (LocalGlassReviewStyle.current && trailing != null) Spacer(Modifier.width(18.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = readOnly,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = if (LocalGlassReviewStyle.current) 12.sp else 10.sp,
                    fontWeight = if (LocalGlassReviewStyle.current && value.isNotBlank() && value != "-") FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = if (LocalGlassReviewStyle.current) TextAlign.Center else TextAlign.Start,
                    textDecoration = textDecoration
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }
    }
}

@Composable
internal fun PokemonSuggestionField(
    label: String,
    value: String,
    suggestions: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    headerTrailing: (@Composable (() -> Unit))? = null,
    useOptionModal: Boolean = true,
    modifier: Modifier = Modifier
) {
    val optionPicker = LocalReviewOptionPicker.current
    Column(verticalArrangement = Arrangement.spacedBy(if (LocalGlassReviewStyle.current) 0.dp else 2.dp), modifier = modifier) {
        FieldLabelRow(label = label, trailing = headerTrailing)
        Box {
            CompactTextInput(
                value = value,
                onValueChange = onValueChange,
                readOnly = suggestions.isNotEmpty(),
                active = expanded,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = suggestions.isNotEmpty()) {
                        if (useOptionModal) {
                            optionPicker?.invoke(
                                ReviewOptionPicker(label, suggestions, value) { selected ->
                                    onValueChange(selected)
                                    onExpandedChange(false)
                                }
                            ) ?: onExpandedChange(!expanded)
                        } else {
                            onExpandedChange(!expanded)
                        }
                    },
                trailing = {
                    if (suggestions.isNotEmpty()) {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Open suggestions",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            if (suggestions.isNotEmpty() && expanded) {
                if (useOptionModal) {
                    SelectionModalDialog(
                        title = label,
                        options = suggestions,
                        selectedValue = value,
                        onDismiss = { onExpandedChange(false) },
                        onOptionSelected = {
                            onValueChange(it)
                            onExpandedChange(false)
                        }
                    )
                } else {
                    StandardDropdownOptions(
                        options = suggestions,
                        onDismiss = { onExpandedChange(false) },
                        onSelected = {
                            onValueChange(it)
                            onExpandedChange(false)
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun MarkerCheckboxField(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        FieldLabelRow(label = label)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .clickable { onCheckedChange(!checked) },
            contentAlignment = Alignment.Center
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
internal fun SelectionDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    headerTrailing: (@Composable (() -> Unit))? = null,
    useOptionModal: Boolean = true,
    modifier: Modifier = Modifier
) {
    val optionPicker = LocalReviewOptionPicker.current
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(if (LocalGlassReviewStyle.current) 0.dp else 2.dp), modifier = modifier) {
        FieldLabelRow(label = label, trailing = headerTrailing)
        Box {
            CompactTextInput(
                value = value,
                onValueChange = {},
                readOnly = true,
                active = expanded,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (useOptionModal) {
                            optionPicker?.invoke(ReviewOptionPicker(label, options, value, onOptionSelected = onSelected)) ?: run { expanded = !expanded }
                        } else {
                            expanded = !expanded
                        }
                    },
                trailing = {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Open options",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            if (expanded) {
                if (useOptionModal) {
                    SelectionModalDialog(
                        title = label,
                        options = options,
                        selectedValue = value,
                        onDismiss = { expanded = false },
                        onOptionSelected = {
                            onSelected(it)
                            expanded = false
                        }
                    )
                } else {
                    StandardDropdownOptions(
                        options = options,
                        onDismiss = { expanded = false },
                        onSelected = {
                            onSelected(it)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StandardDropdownOptions(
    options: List<String>,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 260.dp).heightIn(max = 200.dp)
    ) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                onClick = { onSelected(option) }
            )
        }
    }
}

@Composable
internal fun MoveDropdownField(
    label: String,
    selectedValue: String?,
    options: List<ReviewPokemonMoveOption>,
    emptyLabel: String,
    onSelected: (String?) -> Unit,
    ratingLabel: String? = null,
    statusMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val optionPicker = LocalReviewOptionPicker.current
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label ?: emptyLabel
    val modalMessage = statusMessage
    val pickerOptions = if (modalMessage == null) listOf(emptyLabel) + options.map { it.label } else emptyList()
    val currentPicker = rememberUpdatedState(
        ReviewOptionPicker(label, pickerOptions, selectedLabel, modalMessage, verticalOptions = true) { optionLabel ->
            onSelected(if (optionLabel == emptyLabel) null else options.firstOrNull { it.label == optionLabel }?.value)
        }
    )
    Column(verticalArrangement = Arrangement.spacedBy(if (LocalGlassReviewStyle.current) 0.dp else 2.dp), modifier = modifier) {
        FieldLabelRow(label = label, trailing = {
            LocalFieldLogMarker.current?.invoke(label)
            ratingLabel?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        })
        CompactTextInput(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            active = expanded,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    optionPicker?.invoke(liveReviewOptionPicker(currentPicker)) ?: run { expanded = !expanded }
                },
            trailing = {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Abrir opções",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        if (expanded) {
            SelectionModalDialog(
                title = label,
                options = pickerOptions,
                selectedValue = selectedLabel,
                message = modalMessage,
                verticalOptions = true,
                onDismiss = { expanded = false },
                onOptionSelected = { optionLabel ->
                    if (optionLabel == emptyLabel) {
                        onSelected(null)
                    } else {
                        onSelected(options.firstOrNull { it.label == optionLabel }?.value)
                    }
                    expanded = false
                }
            )
        }
    }
}

@Composable
internal fun LabeledToggleChipField(
    label: String,
    chipLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
    headerSelected: Boolean,
    onHeaderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (LocalGlassReviewStyle.current) 0.dp else 2.dp), modifier = modifier) {
        FieldHeaderRow(
            label = label,
            selected = headerSelected,
            onMarkerClick = onHeaderClick
        )
        ToggleChip(
            label = chipLabel,
            selected = selected,
            onClick = onClick,
            compact = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun FieldHeaderSpacer(
    label: String,
    selected: Boolean,
    onMarkerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (LocalGlassReviewStyle.current) 0.dp else 2.dp), modifier = modifier) {
        FieldHeaderRow(
            label = label,
            selected = selected,
            onMarkerClick = onMarkerClick
        )
        Spacer(modifier = Modifier.height(34.dp))
    }
}
