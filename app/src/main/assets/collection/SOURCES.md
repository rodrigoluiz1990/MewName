# Collection category icons

Original Pokémon GO category icons from PokeMiners/pogo_assets. Downloaded 2026-09-26 without changes. The UI trims transparent margins and tints these white masks to match the theme. Icons fit within 22 dp; compact controls are 34 dp. Bitmap decoding and transparent-margin trimming run on Dispatchers.IO.

- shiny.png: https://raw.githubusercontent.com/PokeMiners/pogo_assets/master/Images/Filters/ic_shiny_white.png
- lucky.png: https://raw.githubusercontent.com/PokeMiners/pogo_assets/master/Images/Filters/ic_lucky_icon.png
- shadow.png: https://raw.githubusercontent.com/PokeMiners/pogo_assets/master/Images/Filters/ic_shadow_filter.png
- purified.png: https://raw.githubusercontent.com/PokeMiners/pogo_assets/master/Images/Filters/ic_purified_filter.png

Source repository: https://github.com/PokeMiners/pogo_assets
Artwork belongs to the respective Pokémon GO rights holders; PokeMiners is the source of extracted assets, not the artwork author. Availability in the repository is not a license grant.

The lucky icon was compared with the symbol in the top-right corner of the user's in-game Pokédex. IV percentages retain app-specific labels. The generic IV Master category was removed.
## League and transformation icons (2026-09-26)

- gigantamax.png: https://archives.bulbagarden.net/media/upload/b/be/GO_Gigantamax_icon.png
- dynamax.png: https://archives.bulbagarden.net/media/upload/6/6e/GO_Dynamax_icon.png
- great.png: https://raw.githubusercontent.com/PokeMiners/pogo_assets/master/Images/Combat/pogo_great_league.png
- master.png: https://raw.githubusercontent.com/PokeMiners/pogo_assets/master/Images/Combat/pogo_master_league.png
- ultra.png: https://raw.githubusercontent.com/PokeMiners/pogo_assets/master/Images/Combat/pogo_ultra_league.png
- mega.png: https://raw.githubusercontent.com/PokeMiners/pogo_assets/master/Images/Evolution/mega_evolution_icon.png

League, Mega and Max symbols preserve their original colors. Dynamax and Gigantamax are Pokemon GO assets archived by Bulbagarden; see https://archives.bulbagarden.net/wiki/File:GO_Dynamax_icon.png and https://archives.bulbagarden.net/wiki/File:GO_Gigantamax_icon.png .

XXS, XXL and the battle-league pennant now use original sprites extracted from the installed game APK; see below.

## XXS, XXL and battle-league pennant — extracted 2026-09-26

Source: user's installed Pokemon GO base.apk, package com.nianticlabs.pokemongo, versionName 0.429.1, versionCode 2026091602. Extracted with UnityPy after joining the Android .split asset chunks. No game code or save data was modified. Only these PNG exports are included in the app; the APK and extraction tools are temporary local files.

All three sprites are from assets/bin/Data/sharedassets1.assets:

- xxs.png: Sprite icon_variant_XXS, path ID 3077 (minus/ruler symbol).
- xxl.png: Sprite icon_variant_XXL, path ID 2353 (plus/ruler symbol).
- little.png: Sprite GlobalLeaguePennant, path ID 2472. This is the general Battle League emblem requested for the app's Copinha collection, not the game's dedicated Little Cup emblem.

Visually checked against the three user references. Original sprite colors/transparency preserved; display fits 22 dp in the existing 34 dp buttons. Original artwork belongs to the Pokemon GO rights holders.
- background.png: original sprite ic_special_background, sharedassets1.assets path ID 2536, Pokemon GO APK 0.429.1. White floral emblem tinted to the active theme. Full local sprite gallery: reference-assets/pokemon-go-0.429.1/index.html.

## Colored collection variants and normalized visible height

The current shiny.png, lucky.png, shadow.png and purified.png were replaced with the original colored APK sprites from reference-assets/pokemon-go-0.429.1/icones-colecao/:
- shadow.png: icon_variant_Shadow_3079.png
- lucky.png: icon_variant_Lucky_2357.png
- purified.png: icon_variant_Purified_2369.png
- shiny.png: icon_variant_Shiny_2372.png

These four variants retain their original colors, without theme tint. Earlier filter-mask sources above describe the replaced assets. Images are trimmed asynchronously and displayed at 22 dp visible height with preserved aspect ratio. The CP pennant uses 20 dp for optical balance. Buttons remain 34 dp. Background flowers alone retain theme tint.

- Current mega.png: icon_variant_Mega_2343.png from reference-assets/pokemon-go-0.429.1/icones-colecao (APK 0.429.1, sharedassets1.assets, Sprite path ID 2343). Original colored variant, no tint; 22 dp visible height in the existing 34 dp control. Replaces the earlier Evolution/mega_evolution_icon.png asset.


## Little Cup emblem replacement (2026-09-30)

Current little.png: https://raw.githubusercontent.com/PokeMiners/pogo_assets/master/Images/Combat/GBL_littlecup.png

Original 512 x 512 RGBA asset, copied without image edits. Matches the user reference with three colored silhouettes. Replaces GlobalLeaguePennant above. Runtime transparent-margin trimming is retained; the image fits a 30 x 20 dp area, preserving aspect ratio and the existing compact button size.
