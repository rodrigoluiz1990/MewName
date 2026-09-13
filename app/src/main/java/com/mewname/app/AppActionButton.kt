package com.mewname.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Only the Activity theme opts in; shared composables used by overlay windows retain their controls.
internal val LocalAppButtonStyle = staticCompositionLocalOf { false }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AppActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondary: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    if (!LocalAppButtonStyle.current) {
        Button(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
        return
    }
    val appearance = LocalAppAppearance.current
    val shape = RoundedCornerShape(50)
    val fill = when {
        !enabled -> listOf(Color(0xFF9DACBB).copy(alpha = 0.14f), Color(0xFF9DACBB).copy(alpha = 0.08f))
        secondary -> if (appearance.dark) listOf(
            Color(0xFFB9D7EF).copy(alpha = 0.12f), Color(0xFFB9D7EF).copy(alpha = 0.04f)
        ) else listOf(Color.White.copy(alpha = 0.38f), Color(0xFFCCE0F1).copy(alpha = 0.12f))
        appearance.dark -> listOf(Color(0xFF5885AD).copy(alpha = 0.68f), Color(0xFF395E83).copy(alpha = 0.58f))
        else -> listOf(Color(0xFF87B3D8).copy(alpha = 0.80f), Color(0xFFBDD5E9).copy(alpha = 0.64f))
    }
    val foreground = if (appearance.dark) Color(0xFFEAF5FF) else Color(0xFF284E70)
    val rim = if (appearance.dark) Color(0xFFBDDDF5) else Color(0xFF789CB9)
    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp).background(Brush.linearGradient(fill), shape),
        shape = shape,
        border = BorderStroke(0.75.dp, Brush.verticalGradient(listOf(
            Color.White.copy(alpha = if (enabled) 0.6f else 0.15f),
            rim.copy(alpha = if (enabled) 0.30f else 0.12f)
        ))),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = foreground,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = foreground.copy(alpha = 0.45f)
        ),
        elevation = null,
        contentPadding = contentPadding
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(
            fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center
        )) { content() }
    }
    }
}

@Composable
internal fun AppSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    if (!LocalAppButtonStyle.current) {
        TextButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    } else {
        AppActionButton(onClick, modifier, enabled, secondary = true, contentPadding = contentPadding, content = content)
    }
}