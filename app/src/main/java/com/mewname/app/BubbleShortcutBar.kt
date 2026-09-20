package com.mewname.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One accessible switch target across the entire row, including its decorative thumb. */
@Composable
internal fun BubbleShortcutBar(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val appearance = LocalAppAppearance.current
    val shape = RoundedCornerShape(10.dp)
    val track by animateColorAsState(
        if (checked) Color(0xFF39BCCD) else if (appearance.dark) Color(0xFF636774) else Color(0xFFB7C3D5),
        label = "shortcutTrack")
    val thumbOffset by animateDpAsState(if (checked) 18.dp else 0.dp, label = "shortcutThumb")
    val background = if (appearance.glass) Color(0xFFE5E9F6) else appearance.card.first()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .alpha(if (enabled) 1f else 0.6f)
            .clip(shape)
            .background(background)
            .border(0.5.dp, appearance.border, shape)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, Modifier.weight(1f), color = appearance.text,
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Box(Modifier.size(40.dp, 22.dp).clip(RoundedCornerShape(50)).background(track).padding(3.dp)) {
            Box(Modifier.offset(x = thumbOffset).size(16.dp)
                .shadow(1.dp, RoundedCornerShape(50))
                .background(Color.White, RoundedCornerShape(50)))
        }
    }
}