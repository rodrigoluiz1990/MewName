package com.mewname.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun ProfileHeaderAction(kind: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f)
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(width = 40.dp, height = 48.dp).semantics { contentDescription = description }) {
        if (kind == "qr") {
            Canvas(Modifier.size(18.dp)) {
                val u = size.width / 21
                for ((x, y) in listOf(1f to 1f, 13f to 1f, 1f to 13f)) {
                    drawRect(color, Offset(x*u, y*u), Size(6*u, 6*u), style = Stroke(u))
                    drawRect(color, Offset((x+2)*u, (y+2)*u), Size(2*u, 2*u))
                }
                for ((x, y) in listOf(13f to 13f, 17f to 13f, 15f to 15f, 13f to 17f, 18f to 18f)) {
                    drawRect(color, Offset(x*u, y*u), Size(2*u, 2*u))
                }
            }
        } else {
            Icon(painterResource(if (kind == "edit") android.R.drawable.ic_menu_edit else android.R.drawable.ic_menu_camera),
                contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun ProfileMenuRow(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    val appearance = LocalAppAppearance.current
    OutlinedButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, appearance.border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = appearance.card.first(), contentColor = appearance.text),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f))
            Text("›", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
        }
    }
}
