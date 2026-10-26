package com.mewname.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage

// One readable representative per item; legacy asset paths stay valid for saved missions.
private fun baseMissionIcons(context: Context): List<String> {
    val items = listOf(
        "pokeball_sprite.png", "greatball_sprite.png", "ultraball_sprite.png", "masterball_sprite.png",
        "premierball_sprite.png", "beastball_sprite.png", "wildball_sprite.png", "pokestop.png", "GiftBox.png", "daily_research_box.png",
        "Item_PostcardBook_Inventory.png", "buddy_gift.png", "buddy_crown_icon.png", "ic_RTrade_device.png",
        "Buddy_SingleHeart_Full.png", "ic_buddy_battleColor.png", "ic_buddy_cameraColor.png", "ic_buddy_poffin.png",
        "dailyWalking_incense.png", "Incense_0.png", "Incense_1.png", "sparkly_incense.png",
        "Egg_2km.png", "Egg_5km.png", "Egg_7km.png", "Egg_10km.png", "Egg_12km_TR.png", "Egg_Special.png", "EggIncubatorSpecial_Activated.png",
        "EggIncubatorIAP_Empty.png", "EggIncubatorSuper_Empty.png", "EggIncubatorUnlimited_Empty.png",
        "EggIncubatorTimed_Empty.png", "EggIncubatorTimed_Activated.png",
        "EggIncubatorIAP_Activated.png", "EggIncubatorSuper_Activated.png", "EggIncubatorUnlimited_Activated.png",
        "Item_0101.png", "Item_0102.png", "Item_0103.png", "Item_0104.png", "Item_0201.png", "Item_0202.png",
        "Item_0701.png", "Item_0702.png", "Item_0703.png", "Item_0704.png", "Item_0705.png", "Item_0706.png",
        "Item_1201.png", "Item_1202.png", "Item_1203.png", "Item_1204.png", "Item_1301.png",
        "Item_1401.png", "Item_1402.png", "Item_1406.png",
        "Item_1403.png", "Item_1404.png", "Item_1408.png", "Item_COIN_01.png",
        "Item_Leader_MapCompass.png", "Item_Giovanni_MapCompass.png", "Item_Meteorite.png",
        "Item_ShadowFragment.png", "Item_ShadowGem.png", "item_route_cell.png", "item_route_cube.png",
        "item_1350.png", "item_1351.png", "item_kyuremBlackFusionResource.png", "item_kyuremWhiteFusionResource.png",
        "sword_fusionResource.png", "shield_fusionResource.png",
        "candy_rgb.png", "RareXLCandy_PSD.png", "stardust_painted.png", "starpiece.png", "luckyegg.png",
        "TroyKey.png", "TroyKey_glacial.png", "TroyKey_magnetic.png", "TroyKey_moss.png", "TroyKey_rainy.png", "TroyKey_sparkly.png",
        "Bag_Dragon_Scale_Sprite.png", "Bag_King's_Rock_Sprite.png", "Bag_Metal_Coat_Sprite.png",
        "Bag_Sinnoh_Stone_Sprite.png", "Bag_Sun_Stone_Sprite.png", "Bag_Unova_Stone_Sprite.png", "Bag_Up-Grade_Sprite.png",
        "Bag_Other_Stone_A_Sprite.png",
        "item_maple_a.png", "item_maple_b.png", "item_maple_c.png",
        "itemstorageupgrade.1.png", "pokemonstorageupgrade.1.png", "Item_postcardstorageupgrade.1.png",
        "WonderBox.png", "mp_pack.png", "lucky_friend_applicator.png",
        "single_stat_increase.png", "triple_stat_increase.png"
    )
    val available = context.assets.list("mission_icons").orEmpty().toSet()
    return items.filter { it in available }.map { "mission_icons/$it" } +
        listOf("dynamax", "gigantamax", "mega", "shiny", "shadow", "purified", "great", "ultra", "master")
            .map { "collection/$it.png" }
}
private fun rawMissionIconLabel(path: String) = path.substringAfterLast('/').substringBeforeLast('.')
    .removePrefix("item_").removePrefix("icon-").replace('_', ' ').replace('-', ' ')
internal fun missionIconLabel(path: String): String {
    val name = path.substringAfterLast('/').lowercase()
    val labels = mapOf(
        "item_1350.png" to "Recurso de fusão lunar / Fusao / Lunar fusion resource / Fusión lunar",
        "item_1351.png" to "Recurso de fusão solar / Fusao / Solar fusion resource / Fusión solar",
        "item_kyuremblackfusionresource.png" to "Recurso de fusão Kyurem Preto / Fusao / Black Kyurem fusion / Fusión Kyurem Negro",
        "item_kyuremwhitefusionresource.png" to "Recurso de fusão Kyurem Branco / Fusao / White Kyurem fusion / Fusión Kyurem Blanco",
        "sword_fusionresource.png" to "Recurso de espada / Fusão / Fusao / Zacian / Sword resource / Espada",
        "shield_fusionresource.png" to "Recurso de escudo / Fusão / Fusao / Zamazenta / Shield resource / Escudo",
        "item_maple_a.png" to "Maçã vermelha / Maca / Red apple / Manzana roja",
        "item_maple_b.png" to "Maçã verde / Maca / Green apple / Manzana verde",
        "item_maple_c.png" to "Maçã dourada / Maca / Golden apple / Manzana dorada",
        "bag_other_stone_a_sprite.png" to "Moeda de Gimmighoul / Coin / Moneda",
        "item_1403.png" to "Passe especial preto / Black special pass / Pase especial negro",
        "item_1404.png" to "Passe especial roxo / Purple special pass / Pase especial morado",
        "item_1408.png" to "Passe especial azul / Blue special pass / Pase especial azul",
        "itemstorageupgrade.1.png" to "Expansão da bolsa de itens / Expansao / Armazenamento / Item bag storage / Bolsa de objetos",
        "pokemonstorageupgrade.1.png" to "Expansão de armazenamento Pokémon / Expansao / Pokemon storage / Almacenamiento Pokémon",
        "item_postcardstorageupgrade.1.png" to "Expansão de cartões-postais / Expansao / Armazenamento / Postcard storage / Almacenamiento de postales",
        "eggincubatortimed_empty.png" to "Incubadora temporária vazia / Temporaria / Empty timed incubator / Incubadora temporal vacía",
        "eggincubatortimed_activated.png" to "Incubadora temporária ativa / Temporaria / Active timed incubator / Incubadora temporal activa",
        "eggincubatoriap_activated.png" to "Incubadora ativa / Active incubator / Incubadora activa",
        "eggincubatorsuper_activated.png" to "Superincubadora ativa / Active super incubator / Superincubadora activa",
        "eggincubatorunlimited_activated.png" to "Incubadora ilimitada ativa / Active unlimited incubator / Incubadora ilimitada activa",
        "item_postcardbook_inventory.png" to "Álbum de cartões-postais / Cartao postal / Postcard book / Postales",
        "buddy_gift.png" to "Presente do companheiro / Buddy gift / Regalo del compañero",
        "buddy_crown_icon.png" to "Amizade com companheiro / Buddy friendship / Amistad con compañero",
        "egg_special.png" to "Ovo especial / Special egg / Huevo especial",
        "eggincubatorspecial_activated.png" to "Incubadora especial / Chocar ovo / Special incubator / Incubadora especial",
        "ic_rtrade_device.png" to "Troca / Dispositivo de troca / Trade device / Intercambio",
        "sparkly_incense.png" to "Baú de moedas / Bau / Coin chest / Cofre de monedas",
        "single_stat_increase.png" to "Tampinha prateada / Silver bottle cap / Chapa plateada",
        "triple_stat_increase.png" to "Tampinha dourada / Gold bottle cap / Chapa dorada",
        "daily_research_box.png" to "Caixa de tarefas diárias / Pesquisa / Research box / Investigación", "pokestop.png" to "PokéStop / Pokestop / Poképarada", "giftbox.png" to "Presente / Gift", "dailywalking_incense.png" to "Incenso diário / Daily incense",
        "pokeball_sprite.png" to "Poké Bola / Poké Ball", "greatball_sprite.png" to "Grande Bola / Great Ball",
        "ultraball_sprite.png" to "Ultra Bola / Ultra Ball", "masterball_sprite.png" to "Bola Mestra / Master Ball",
        "item_1401.png" to "Passe de reide / Raid pass", "item_1402.png" to "Passe premium / Premium pass",
        "item_1406.png" to "Passe remoto / Remote pass", "item_route_cell.png" to "Célula de Zygarde / Route cell",
        "tx_ar_photo_camera.png" to "Foto / Snapshot", "stardust_painted.png" to "Poeira Estelar / Stardust",
        "luckyegg.png" to "Ovo da Sorte / Lucky Egg", "item_leader_mapcompass.png" to "Radar Rocket",
        "ic_buddy_battlecolor.png" to "Batalhar com companheiro / Buddy battle",
        "ic_buddy_cameracolor.png" to "Foto do companheiro / Buddy snapshot",
        "ic_buddy_poffin.png" to "Alimentar companheiro / Feed buddy")
    return labels[name] ?: rawMissionIconLabel(path)
}
internal fun filterIconLabel(path: String): String = when (path) {
    "filter_icons/legendary.png", "filter_icons/legendary_color.png" -> "Lendário / Legendary / Legendario"
    "filter_icons/mythical.png", "filter_icons/mythical_color.png" -> "Mítico / Mythical / Singular / Mew"
    "filter_icons/ultra_beast.png" -> "Ultracriatura / Ultra Beast / Ultraente"
    "filter_icons/costume.png", "filter_icons/costume_color.png" -> "Traje / Fantasia / Costume / Disfraz / Evento"
    "filter_icons/unown.png" -> "Unown"
    "filter_icons/favorite.png" -> "Favorito / Favorite / Favorita / Estrela / Star"
    "filter_icons/favorite_empty.png" -> "Favorito desmarcado / Unmarked favorite / Favorito sin marcar"
    else -> missionIconLabel(path)
}
internal fun sharedTaskIcons(context: Context): List<String> {
    val items = baseMissionIcons(context).filterNot { it.startsWith("collection/") }.toMutableList()
    // Ultra raid artwork belongs beside the raid passes, not beside ordinary Poké Balls.
    val lastPass = items.indexOfLast { it.substringAfterLast('/') in setOf("Item_1401.png", "Item_1402.png", "Item_1403.png", "Item_1404.png", "Item_1406.png", "Item_1408.png") }
    items.add(if (lastPass >= 0) lastPass + 1 else items.size, "filter_icons/ultra_beast.png")
    return (items + listOf(
        "collection/mega.png", "collection/dynamax.png", "collection/gigantamax.png",
        "filter_icons/legendary_color.png", "filter_icons/mythical_color.png", "filter_icons/costume_color.png", "filter_icons/unown.png",
        "collection/shiny.png", "collection/shadow.png", "collection/purified.png",
        "filter_icons/favorite.png", "filter_icons/favorite_empty.png",
        "collection/great.png", "collection/ultra.png", "collection/master.png",
        "filter_icons/legendary.png", "filter_icons/mythical.png", "filter_icons/costume.png"
    )).distinct()
}

@Composable
internal fun FilterTagIcon(path: String, modifier: Modifier = Modifier, description: String? = null) {
    val monochrome = path in setOf("filter_icons/legendary.png", "filter_icons/mythical.png", "filter_icons/costume.png", "filter_icons/favorite_empty.png")
    AsyncImage("file:///android_asset/$path", description, modifier,
        colorFilter = if (monochrome) androidx.compose.ui.graphics.ColorFilter.tint(appControlInk()) else null)
}

@Composable
internal fun NoTagIcon(modifier: Modifier = Modifier) {
    AsyncImage("file:///android_asset/filter_icons/no_icon_pikachu.png",
        lt(appLanguage(), "Sem ícone", "No icon", "Sin icono"), modifier,
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(appControlInk().copy(alpha = .55f)))
}


internal fun missionIcons(context: Context) = sharedTaskIcons(context)
internal fun filterTagIcons(context: Context) = sharedTaskIcons(context)

@Composable
internal fun SharedTaskIconPicker(selected: String?, search: String, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icons = remember(context) { sharedTaskIcons(context) }
    val filtered = icons.filter { filterIconLabel(it).contains(search, true) }
    AppSectionCard(modifier) {
        LazyVerticalGrid(GridCells.Adaptive(56.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item(key = "no_icon") {
                Surface(onClick = { onSelect(null) }, color = appControlFill(selected.isNullOrBlank()),
                    border = if (selected.isNullOrBlank()) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    shape = RoundedCornerShape(14.dp)) {
                    NoTagIcon(Modifier.padding(8.dp).size(40.dp))
                }
            }
            if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(lt(appLanguage(), "Nenhum ícone encontrado", "No icons found", "No se encontraron iconos"), Modifier.padding(8.dp))
            }
            items(filtered, key = { it }) { path ->
                Surface(onClick = { onSelect(path) }, color = appControlFill(selected == path),
                    border = if (selected == path) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    shape = RoundedCornerShape(14.dp)) {
                    FilterTagIcon(path, Modifier.padding(8.dp).size(40.dp), filterIconLabel(path))
                }
            }
        }
    }
}
