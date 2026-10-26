package com.mewname.app

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/** Shared modal surface; header/actions stay reachable while long content scrolls. */
@Composable
internal fun AppModalPanel(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    closeEnabled: Boolean = true,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val appearance = LocalAppAppearance.current
    Surface(modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .85f).dp),
        shape = RoundedCornerShape(24.dp), color = appearance.background.first(),
        contentColor = appearance.text, border = BorderStroke(.75.dp, appearance.border)) {
        Column(Modifier.background(Brush.verticalGradient(appearance.background)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppModalHeader(title, onClose, closeEnabled)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { actions() }
        }
    }
}
