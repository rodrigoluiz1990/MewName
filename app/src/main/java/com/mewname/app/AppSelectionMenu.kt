package com.mewname.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun <T> AppSelectionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    val appearance = LocalAppAppearance.current
    val shape = RoundedCornerShape(18.dp)
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            surface = appearance.background.first(), surfaceTint = appearance.background.first()),
        shapes = MaterialTheme.shapes.copy(extraSmall = shape)
    ) {
    DropdownMenu(expanded, onDismiss, Modifier.heightIn(max = 300.dp)
        .clip(shape).background(Brush.verticalGradient(appearance.background))
        .border(.75.dp, appearance.border, shape)) {
        options.forEach { option ->
            val chosen = option == selected
            DropdownMenuItem(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (chosen) appControlFill(true) else Color.Transparent)
                    .semantics { this.selected = chosen },
                text = { Text(label(option), fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal) },
                colors = MenuDefaults.itemColors(textColor = appControlInk(), trailingIconColor = appControlInk()),
                trailingIcon = { if (chosen) Icon(AppIcons.Selected, null, Modifier.size(20.dp)) else Spacer(Modifier.size(20.dp)) },
                onClick = { onDismiss(); onSelect(option) }
            )
        }
    }
}
}
