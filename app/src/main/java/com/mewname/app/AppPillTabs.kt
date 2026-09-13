package com.mewname.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** App-only capsule tabs; the bubble review keeps its own tab components. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun AppPillTabRow(
    selectedTabIndex: Int,
    edgePadding: Dp = 0.dp,
    divider: @Composable () -> Unit = {},
    tabs: @Composable () -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = Modifier.clip(RoundedCornerShape(18.dp)),
        edgePadding = edgePadding,
        containerColor = if (LocalAppAppearance.current.dark) Color(0xFF282530) else Color(0xFFECE2F3),
        contentColor = LocalAppAppearance.current.text,
        indicator = {},
        divider = divider,
        tabs = tabs
    )
}

@Composable
internal fun AppPillTab(selected: Boolean, onClick: () -> Unit, text: @Composable () -> Unit) {
    val dark = LocalAppAppearance.current.dark
    val shape = RoundedCornerShape(16.dp)
    val fill = if (selected) {
        if (dark) Color(0xFF4A4356) else Color.White
    } else Color.Transparent
    val foreground = when {
        selected && dark -> Color(0xFFF8F4FF)
        selected -> Color(0xFF44364F)
        dark -> Color(0xFFBEB5CC)
        else -> Color(0xFF81758F)
    }
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
            .height(40.dp).clip(shape)
            .background(fill),
        selectedContentColor = foreground,
        unselectedContentColor = foreground,
        text = {
            ProvideTextStyle(MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )) { text() }
        }
    )
}