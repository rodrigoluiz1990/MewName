package com.mewname.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun AppGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    search: Boolean = false
) {
    if (!LocalAppButtonStyle.current) {
        OutlinedTextField(value, onValueChange, modifier = modifier, enabled = enabled,
            label = label, supportingText = supportingText, singleLine = singleLine)
        return
    }
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier,
        enabled = enabled, singleLine = singleLine,
        label = label, supportingText = supportingText,
        leadingIcon = if (search) { { Icon(Icons.Default.Search, contentDescription = null) } } else null,
        shape = RoundedCornerShape(20.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = appGlassInputColors()
    )
}

@Composable
internal fun AppGlassTextField(
    value: androidx.compose.ui.text.input.TextFieldValue,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false
) {
    if (!LocalAppButtonStyle.current) {
        OutlinedTextField(value, onValueChange, modifier = modifier, enabled = enabled,
            label = label, supportingText = supportingText, singleLine = singleLine)
        return
    }
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier,
        enabled = enabled, singleLine = singleLine, label = label, supportingText = supportingText,
        shape = RoundedCornerShape(20.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = appGlassInputColors()
    )
}

@Composable
private fun appGlassInputColors(): TextFieldColors {
    val appearance = LocalAppAppearance.current
    val ink = if (appearance.dark) Color(0xFFDBEAF6) else Color(0xFF35566E)
    val hint = if (appearance.dark) Color(0xFFB2C6D7) else Color(0xFF617B8F)
    val fill = if (appearance.dark) Color(0xFFB5D2E8).copy(alpha = 0.10f)
        else Color.White.copy(alpha = 0.44f)
    return OutlinedTextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            disabledContainerColor = fill.copy(alpha = 0.10f),
            focusedBorderColor = Color(0xFF89B4D5),
            unfocusedBorderColor = Color.White.copy(alpha = if (appearance.dark) 0.22f else 0.82f),
            disabledBorderColor = hint.copy(alpha = 0.20f),
            focusedTextColor = ink,
            unfocusedTextColor = ink,
            disabledTextColor = hint.copy(alpha = 0.55f),
            cursorColor = ink,
            focusedLabelColor = ink,
            unfocusedLabelColor = hint,
            focusedLeadingIconColor = hint,
            unfocusedLeadingIconColor = hint
        )
}
