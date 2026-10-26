package com.mewname.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shared modal convention: centered title and accessible close action at top right. */
@Composable
internal fun AppModalHeader(title: String, onClose: () -> Unit, closeEnabled: Boolean = true) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(title, Modifier.fillMaxWidth().padding(horizontal = 48.dp), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Box(Modifier.align(Alignment.TopEnd)) { AppModalCloseButton(onClose, closeEnabled) }
    }
}

@Composable
internal fun AppModalCloseButton(onClose: () -> Unit, enabled: Boolean = true) {
    val language = appLanguage()
    IconButton(onClick = onClose, enabled = enabled) {
        Icon(AppIcons.Close, lt(language, "Fechar", "Close", "Cerrar"))
    }
}
