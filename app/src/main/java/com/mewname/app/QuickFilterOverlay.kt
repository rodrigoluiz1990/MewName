package com.mewname.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close

import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class QuickFilterTerm(val value: String, val joiner: String = "&", val mode: Int = 0) {
    fun cycle() = copy(mode = (mode + 1) % 3) // normal, negated, disabled
}
internal fun parseQuickFilter(value: String): List<QuickFilterTerm> {
    var joiner = "&"
    return Regex("([^&,;]+)([&,;]?)").findAll(value).map { match ->
        val raw = match.groupValues[1].trim()
        val term = QuickFilterTerm(raw.removePrefix("!"), joiner, if (raw.startsWith("!")) 1 else 0)
        joiner = match.groupValues[2].ifBlank { "&" }
        term
    }.toList()
}
internal fun buildQuickFilter(terms: List<QuickFilterTerm>): String = terms.filter { it.mode != 2 }.mapIndexed { index, term ->
    (if (index == 0) "" else term.joiner) + (if (term.mode == 1) "!" else "") + term.value
}.joinToString("")

@Composable
internal fun QuickFilterOverlay(initial: String, onCopy: (String) -> Unit, onMove: (Float, Float) -> Unit,
    onBack: () -> Unit, onClose: () -> Unit, onMoveFinished: () -> Unit) {
    val context = LocalContext.current
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    var terms by remember(initial) { mutableStateOf(parseQuickFilter(initial)) }
    var copied by remember { mutableStateOf(false) }
    val preview = buildQuickFilter(terms)
    LaunchedEffect(preview) { copied = false }
    val appearances = remember(initial, language) { terms.map { quickFilterAppearance(context, it.value, language) } }
    Surface(shape = RoundedCornerShape(20.dp), color = androidx.compose.ui.graphics.Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(.75.dp, LocalAppAppearance.current.border)) {
        Column(Modifier.background(Brush.verticalGradient(LocalAppAppearance.current.card)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(AppIcons.Back, tr("Filtros salvos", "Saved filters", "Filtros guardados"), Modifier.size(20.dp))
                }
                Box(Modifier.weight(1f).height(32.dp).pointerInput(Unit) {
                    detectDragGestures(onDragEnd = onMoveFinished, onDragCancel = onMoveFinished) { change, delta -> change.consume(); onMove(delta.x, delta.y) }
                }, contentAlignment = Alignment.Center) {
                    Surface(Modifier.width(44.dp).height(4.dp), shape = RoundedCornerShape(2.dp), color = appControlInk().copy(alpha = .5f)) {}
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(AppIcons.Close, tr("Fechar", "Close", "Cerrar"), Modifier.size(20.dp))
                }
            }
            Surface(shape = RoundedCornerShape(12.dp), color = appControlFill(selected = true), contentColor = appControlInk(),
                border = androidx.compose.foundation.BorderStroke(.75.dp, appControlInk().copy(alpha = .30f))) {
                Row(Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(preview.ifBlank { "—" }, Modifier.weight(1f).horizontalScroll(rememberScrollState()), maxLines = 1,
                        style = MaterialTheme.typography.bodySmall, color = appControlInk())
                    IconButton(onClick = { onCopy(preview); copied = true }, enabled = preview.isNotBlank(), modifier = Modifier.size(40.dp)) {
                        Icon(if (copied) AppIcons.Selected else AppIcons.Copy,
                            if (copied) tr("Copiado", "Copied", "Copiado") else tr("Copiar", "Copy", "Copiar"), Modifier.size(20.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                terms.forEachIndexed { index, term ->
                    val (label, icon) = appearances[index]
                    val stateLabel = when (term.mode) {
                        1 -> tr("Negada", "Negated", "Negada")
                        2 -> tr("Desativada", "Disabled", "Desactivada")
                        else -> tr("Normal", "Normal", "Normal")
                    }
                    Surface(shape = RoundedCornerShape(10.dp), color = appControlFill(term.mode != 2, term.mode == 1),
                        modifier = Modifier.width(if (icon != null) 40.dp else 64.dp).height(40.dp)
                            .alpha(if (term.mode == 2) .45f else 1f)
                            .semantics { contentDescription = "$label: $stateLabel" }
                            .clickable { terms = terms.toMutableList().apply { set(index, term.cycle()) } }) {
                        Box(Modifier.padding(4.dp), contentAlignment = Alignment.Center) {
                            if (icon != null) {
                                FilterTagIcon(icon, Modifier.size(28.dp))
                                if (term.mode != 0) Surface(modifier = Modifier.align(Alignment.TopEnd),
                                    shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surface) {
                                    Text(if (term.mode == 1) "!" else "−", Modifier.padding(horizontal = 2.dp),
                                        style = MaterialTheme.typography.labelSmall, color = appControlInk())
                                }
                            } else Text((if (term.mode == 1) "! " else if (term.mode == 2) "− " else "") + label,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (index < terms.lastIndex) TextButton(onClick = {
                        terms = terms.toMutableList().apply { val next = get(index + 1); set(index + 1, next.copy(joiner = if (next.joiner == "&") "," else "&")) }
                    }, modifier = Modifier.width(28.dp).height(40.dp), contentPadding = PaddingValues(0.dp)) { Text(terms[index + 1].joiner) }
                }
            }
        }
    }
}
