package com.mewname.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Artwork cropped from the supplied menu sheet; labels remain localized app text. */
@Composable
internal fun HomeGlassMenuIcon(kind: String, iconSize: Dp = 48.dp) {
    if (kind == "moves") {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            HomeGlassMenuIcon("fast_moves", 36.dp)
            HomeGlassMenuIcon("charged_moves", 36.dp)
        }
    } else {
        AssetImageIcon(
            assetPath = "menu/glass/$kind.png",
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            fallbackSize = iconSize
        )
    }
}