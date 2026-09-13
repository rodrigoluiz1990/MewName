package com.mewname.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Shared home-style section. Draw the background once, without an elevated transparent layer. */
@Composable
internal fun AppSectionCard(
    modifier: Modifier = Modifier,
    colors: CardColors? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val appearance = LocalAppAppearance.current
    if (colors != null) {
        // Explicit colours communicate state, such as validation failures or selected values.
        Card(modifier = modifier, colors = colors, shape = shape, content = content)
        return
    }
    CompositionLocalProvider(LocalContentColor provides appearance.text) {
        Column(
            modifier.clip(shape)
                .background(Brush.linearGradient(appearance.card))
                .border(BorderStroke(0.75.dp, appearance.border), shape),
            content = content
        )
    }
}