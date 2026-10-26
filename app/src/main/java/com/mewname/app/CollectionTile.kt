package com.mewname.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CollectionTile(
    label: String, collected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null,
    portrait: @Composable () -> Unit
) {
    AppSectionCard(modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .semantics { toggleableState = if (collected) ToggleableState.On else ToggleableState.Off }) {
        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().heightIn(min = 80.dp), contentAlignment = Alignment.Center) {
                portrait()
                if (collected) Icon(AppIcons.Selected, null, Modifier.align(Alignment.TopEnd).size(22.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
            Text(label, Modifier.fillMaxWidth().padding(top = 2.dp), style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center, minLines = 2)
        }
    }
}
