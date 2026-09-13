package com.mewname.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun appControlFill(selected: Boolean = false, excluded: Boolean = false): Color {
    val dark = LocalAppAppearance.current.dark
    return when {
        excluded -> if (dark) Color(0xFF5D3640) else Color(0xFFF1DCE1).copy(alpha = 0.85f)
        selected -> if (dark) Color(0xFF385976) else Color(0xFFBCD6EB).copy(alpha = 0.85f)
        dark -> Color(0xFFBAD7EE).copy(alpha = 0.10f)
        else -> Color.White.copy(alpha = 0.42f)
    }
}

@Composable
internal fun appControlInk(): Color =
    if (LocalAppAppearance.current.dark) Color(0xFFDDEDF9) else Color(0xFF35566E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppChoiceChip(
    selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, excluded: Boolean = false
) {
    if (!LocalAppButtonStyle.current) {
        FilterChip(selected, onClick, label, modifier = modifier, enabled = enabled,
            colors = if (excluded) FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
            ) else FilterChipDefaults.filterChipColors())
        return
    }
    val ink = appControlInk()
    FilterChip(
        selected = selected, onClick = onClick, label = label,
        modifier = modifier.heightIn(min = 40.dp), enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.75.dp, if (selected) {
            if (excluded) Color(0xFFBF8591) else Color(0xFF89B4D5)
        } else LocalAppAppearance.current.border),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = appControlFill(), labelColor = ink,
            selectedContainerColor = appControlFill(true, excluded),
            selectedLabelColor = if (excluded && !LocalAppAppearance.current.dark) Color(0xFF7F3447) else ink,
            disabledContainerColor = appControlFill().copy(alpha = 0.10f),
            disabledLabelColor = ink.copy(alpha = 0.45f)
        )
    )
}

@Composable
internal fun AppInfoChip(onClick: () -> Unit, label: @Composable () -> Unit) {
    if (!LocalAppButtonStyle.current) {
        AssistChip(onClick = onClick, label = label)
        return
    }
    Surface(shape = RoundedCornerShape(10.dp), color = appControlFill(),
        contentColor = appControlInk(), border = BorderStroke(0.5.dp, LocalAppAppearance.current.border)) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            ProvideTextStyle(MaterialTheme.typography.labelSmall) { label() }
        }
    }
}

@Composable
internal fun AppCollectionCheckbox(
    checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier
) {
    if (!LocalAppButtonStyle.current) {
        Checkbox(checked, onCheckedChange, modifier = modifier)
        return
    }
    Checkbox(checked, onCheckedChange, modifier = modifier,
        colors = CheckboxDefaults.colors(
            checkedColor = Color(0xFF6D9CC0),
            uncheckedColor = Color(0xFF89A6BD),
            checkmarkColor = Color.White
        ))
}

@Composable
internal fun AppValueSlider(
    value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    if (!LocalAppButtonStyle.current) {
        Slider(value, onValueChange, modifier = modifier, valueRange = valueRange)
        return
    }
    Surface(modifier, shape = RoundedCornerShape(18.dp),
        color = appControlFill(), border = BorderStroke(0.75.dp, LocalAppAppearance.current.border)) {
        Slider(value, onValueChange, valueRange = valueRange, modifier = Modifier.padding(horizontal = 12.dp),
            colors = SliderDefaults.colors(thumbColor = Color(0xFF8BB6D7),
                activeTrackColor = Color(0xFF8BB6D7), inactiveTrackColor = Color(0xFF9FBDD4).copy(alpha = 0.25f)))
    }
}

@Composable
internal fun AppAddButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    FloatingActionButton(onClick, shape = RoundedCornerShape(20.dp),
        containerColor = appControlFill(true), contentColor = appControlInk(),
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        content = content)
}

@Composable
internal fun AppLoadingIndicator(modifier: Modifier = Modifier) {
    if (!LocalAppButtonStyle.current) {
        CircularProgressIndicator(modifier = modifier)
        return
    }
    CircularProgressIndicator(modifier = modifier, color = Color(0xFF7BA8CA), strokeWidth = 3.dp)
}
@Composable
internal fun AppStatusMessage(text: String, error: Boolean = false) {
    if (!LocalAppButtonStyle.current) {
        Text(text)
        return
    }
    val accent = if (error) {
        if (LocalAppAppearance.current.dark) Color(0xFFF0B7C2) else Color(0xFF854759)
    } else appControlInk()
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        color = appControlFill(excluded = error), contentColor = accent,
        border = BorderStroke(0.75.dp, LocalAppAppearance.current.border)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(if (error) "!" else "—", style = MaterialTheme.typography.titleMedium)
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}