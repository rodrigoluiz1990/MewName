package com.mewname.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

private data class AdventureArt(val banner: String?, val portrait: String)

private val adventureArt = mapOf(
    "Roar of Time" to AdventureArt("RoarofTime", "ic_fieldmove_dialga.png"),
    "Spacial Rend" to AdventureArt("SpacialRend", "ic_fieldmove_palkia.png"),
    "Sunsteel Strike" to AdventureArt("SunsteelStrike", "ic_duskmane_aeportrait.png"),
    "Moongeist Beam" to AdventureArt("MoongeistBeam", "ic_dawnwing_aeportrait.png"),
    "Freeze Shock" to AdventureArt("FreezeShock", "ic_b_kyurem_aeportrait.png"),
    "Ice Burn" to AdventureArt("IceBurn", "ic_w_kyurem_aeportrait.png"),
    "Behemoth Blade" to AdventureArt("BehemothBlade", "zacian_AEportrait.png"),
    "Behemoth Bash" to AdventureArt("BehemothBash", "zamazenta_AEportrait.png"),
    "Dynamic Punch+" to AdventureArt("DynamicPunch", "ic_mega_mewtwo_x_aeportrait.png"),
    "Future Sight+" to AdventureArt("FutureSight", "ic_mega_mewtwo_y_aeportrait.png"),
    "Dynamax Cannon" to AdventureArt("DynamaxCannon", "Eternatus_AEportrait.png")
)

@Composable
internal fun AdventureEffectArt(
    move: String,
    pokemonName: String,
    description: @Composable () -> Unit
) {
    val art = adventureArt[move]
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
        if (art?.banner != null) AsyncImage(
            model = "file:///android_asset/adventure_effects/tx_fieldmove_${art.banner}.png",
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (art != null) AsyncImage(
                model = "file:///android_asset/adventure_effects/${art.portrait}",
                contentDescription = null,
                modifier = Modifier.width(96.dp).height(132.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center
            )
            Column(
                Modifier.weight(1f)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f), RoundedCornerShape(8.dp))
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(pokemonName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                description()
            }
        }
    }
}