package com.mewname.app

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CollectionOverlayPanel(screenHeight: Int, onResize: (Int) -> Unit,
    onClose: () -> Unit, registerBackAction: ((() -> Unit) -> Unit)) {
    val density = LocalDensity.current
    var height by remember { mutableIntStateOf(CollectionOverlayWindow.clampHeight((screenHeight * 0.55f).toInt(), screenHeight, density.density)) }
    var startHeight by remember { mutableIntStateOf(height) }
    var startY by remember { mutableFloatStateOf(0f) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun resize(next: Int) {
        val bounded = CollectionOverlayWindow.clampHeight(next, screenHeight, density.density)
        if (height != bounded) {
            height = bounded
            onResize(bounded)
        }
    }
    val collapsed = height == CollectionOverlayWindow.minimumHeight(density.density)
    val visibleBodyHeight = maxOf(0.dp, with(density) { height.toDp() } - CollectionOverlayWindow.HEADER_DP.dp)
    // Preserve the mounted content and its scroll/search state, even at header-only height.
    val bodyHeight = maxOf(visibleBodyHeight, 240.dp)
    AppSectionCard(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(48.dp)
            .semantics {
                contentDescription = "Arraste para ajustar a altura da coleção"
                customActions = listOf(
                    CustomAccessibilityAction("Aumentar painel") { resize(height + (64 * density.density).toInt()); true },
                    CustomAccessibilityAction("Diminuir painel") { resize(height - (64 * density.density).toInt()); true }
                )
            }
            .pointerInteropFilter { event ->
                // Screen coordinates stay stable while the top edge of the window moves.
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startHeight = height
                        startY = event.rawY
                        focus.clearFocus()
                        keyboard?.hide()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        resize(CollectionOverlayWindow.draggedHeight(startHeight, startY, event.rawY, screenHeight, density.density))
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                    else -> false
                }
            }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 36.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)))
        }
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            Text("Coleções", Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
                style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Default.Close, "Fechar coleção")
            }
        }
        Box(Modifier.fillMaxWidth().height(visibleBodyHeight).clipToBounds()
            .then(if (collapsed) Modifier.clearAndSetSemantics { } else Modifier)) {
            Box(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).height(bodyHeight)) {
                PersonalCollectionScreen(onBack = onClose, overlay = true, panel = true,
                    registerBackAction = registerBackAction)
            }
        }
    }
}
