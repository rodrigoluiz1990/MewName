package com.mewname.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AssetImageIcon(
    assetPath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallbackSize: Dp = 18.dp
) {
    val context = LocalContext.current
    val bitmap = remember(assetPath) {
        runCatching {
            context.assets.open(assetPath).use(BitmapFactory::decodeStream)
        }.getOrNull()
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier
        )
    } else {
        Icon(
            painter = painterResource(android.R.drawable.ic_menu_report_image),
            contentDescription = contentDescription,
            modifier = modifier.size(fallbackSize),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun UnownQuestionIcon(
    modifier: Modifier = Modifier,
    contentDescription: String? = "Ajuda"
) {
    AssetImageIcon(
        assetPath = "Unown_Qu.png",
        contentDescription = contentDescription,
        modifier = modifier
    )
}

@Composable
fun UnownExclamationIcon(
    modifier: Modifier = Modifier,
    contentDescription: String? = "Alerta"
) {
    AssetImageIcon(
        assetPath = "Unown_Ex.png",
        contentDescription = contentDescription,
        modifier = modifier
    )
}
