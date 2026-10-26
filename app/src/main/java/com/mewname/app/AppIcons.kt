package com.mewname.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Font Awesome Free 7.3.1. Icons CC BY 4.0; assets/licenses/fontawesome-7.3.1.txt.
 * Keep the existing layout/touch bounds. FA paths fill their viewport, unlike Material icons:
 * inset the artwork to 20 dp, or 18 dp for refresh/plus, within the nominal 24 dp height.
 * Explicit 16/18/20 dp compact callers retain their layout and inherit the same optical inset.
 */
internal object AppIcons {
    val Copy: ImageVector by lazy {
        ImageVector.Builder("FA_copy", 21.dp, 24.dp, 448f, 512f)
            .addGroup(scaleX = 0.83333333f, scaleY = 0.83333333f, translationX = 37.33333333f, translationY = 42.66666667f)
            .addPath(addPathNodes("M384 336l-192 0c-8.8 0-16-7.2-16-16l0-256c0-8.8 7.2-16 16-16l133.5 0c4.2 0 8.3 1.7 11.3 4.7l58.5 58.5c3 3 4.7 7.1 4.7 11.3L400 320c0 8.8-7.2 16-16 16zM192 384l192 0c35.3 0 64-28.7 64-64l0-197.5c0-17-6.7-33.3-18.7-45.3L370.7 18.7C358.7 6.7 342.5 0 325.5 0L192 0c-35.3 0-64 28.7-64 64l0 256c0 35.3 28.7 64 64 64zM64 128c-35.3 0-64 28.7-64 64L0 448c0 35.3 28.7 64 64 64l192 0c35.3 0 64-28.7 64-64l0-16-48 0 0 16c0 8.8-7.2 16-16 16L64 464c-8.8 0-16-7.2-16-16l0-256c0-8.8 7.2-16 16-16l16 0 0-48-16 0z"), fill = SolidColor(Color.Black))
            .clearGroup()
            .build()
    }
    val Delete: ImageVector by lazy {
        ImageVector.Builder("FA_trash-can", 21.dp, 24.dp, 448f, 512f)
            .addGroup(scaleX = 0.83333333f, scaleY = 0.83333333f, translationX = 37.33333333f, translationY = 42.66666667f)
            .addPath(addPathNodes("M166.2-16c-13.3 0-25.3 8.3-30 20.8L120 48 24 48C10.7 48 0 58.7 0 72S10.7 96 24 96l400 0c13.3 0 24-10.7 24-24s-10.7-24-24-24l-96 0-16.2-43.2C307.1-7.7 295.2-16 281.8-16L166.2-16zM32 144l0 304c0 35.3 28.7 64 64 64l256 0c35.3 0 64-28.7 64-64l0-304-48 0 0 304c0 8.8-7.2 16-16 16L96 464c-8.8 0-16-7.2-16-16l0-304-48 0zm160 72c0-13.3-10.7-24-24-24s-24 10.7-24 24l0 176c0 13.3 10.7 24 24 24s24-10.7 24-24l0-176zm112 0c0-13.3-10.7-24-24-24s-24 10.7-24 24l0 176c0 13.3 10.7 24 24 24s24-10.7 24-24l0-176z"), fill = SolidColor(Color.Black))
            .clearGroup()
            .build()
    }
    val Add: ImageVector by lazy {
        ImageVector.Builder("FA_plus", 21.dp, 24.dp, 448f, 512f)
            .addGroup(scaleX = 0.75000000f, scaleY = 0.75000000f, translationX = 56.00000000f, translationY = 64.00000000f)
            .addPath(addPathNodes("M256 64c0-17.7-14.3-32-32-32s-32 14.3-32 32l0 160-160 0c-17.7 0-32 14.3-32 32s14.3 32 32 32l160 0 0 160c0 17.7 14.3 32 32 32s32-14.3 32-32l0-160 160 0c17.7 0 32-14.3 32-32s-14.3-32-32-32l-160 0 0-160z"), fill = SolidColor(Color.Black))
            .clearGroup()
            .build()
    }
    val Back: ImageVector by lazy {
        ImageVector.Builder("FA_arrow-left", 24.dp, 24.dp, 512f, 512f)
            .addGroup(scaleX = 0.83333333f, scaleY = 0.83333333f, translationX = 42.66666667f, translationY = 42.66666667f)
            .addPath(addPathNodes("M9.4 233.4c-12.5 12.5-12.5 32.8 0 45.3l160 160c12.5 12.5 32.8 12.5 45.3 0s12.5-32.8 0-45.3L109.3 288 480 288c17.7 0 32-14.3 32-32s-14.3-32-32-32l-370.7 0 105.4-105.4c12.5-12.5 12.5-32.8 0-45.3s-32.8-12.5-45.3 0l-160 160z"), fill = SolidColor(Color.Black))
            .clearGroup()
            .build()
    }
    val Refresh: ImageVector by lazy {
        ImageVector.Builder("FA_arrow-rotate-right", 24.dp, 24.dp, 512f, 512f)
            .addGroup(scaleX = 0.75000000f, scaleY = 0.75000000f, translationX = 64.00000000f, translationY = 64.00000000f)
            .addPath(addPathNodes("M436.7 74.7L448 85.4 448 32c0-17.7 14.3-32 32-32s32 14.3 32 32l0 128c0 17.7-14.3 32-32 32l-128 0c-17.7 0-32-14.3-32-32s14.3-32 32-32l47.9 0-7.6-7.2c-.2-.2-.4-.4-.6-.6-75-75-196.5-75-271.5 0s-75 196.5 0 271.5 196.5 75 271.5 0c8.2-8.2 15.5-16.9 21.9-26.1 10.1-14.5 30.1-18 44.6-7.9s18 30.1 7.9 44.6c-8.5 12.2-18.2 23.8-29.1 34.7-100 100-262.1 100-362 0S-25 175 75 75c99.9-99.9 261.7-100 361.7-.3z"), fill = SolidColor(Color.Black))
            .clearGroup()
            .build()
    }
    val Next: ImageVector by lazy {
        ImageVector.Builder("FA_angle-right", 12.dp, 24.dp, 256f, 512f)
            .addGroup(scaleX = 0.83333333f, scaleY = 0.83333333f, translationX = 21.33333333f, translationY = 42.66666667f)
            .addPath(addPathNodes("M247.1 233.4c12.5 12.5 12.5 32.8 0 45.3l-160 160c-12.5 12.5-32.8 12.5-45.3 0s-12.5-32.8 0-45.3L179.2 256 41.9 118.6c-12.5-12.5-12.5-32.8 0-45.3s32.8-12.5 45.3 0l160 160z"), fill = SolidColor(Color.Black))
            .clearGroup()
            .build()
    }
    val Search: ImageVector by lazy {
        ImageVector.Builder("FA_magnifying-glass", 24.dp, 24.dp, 512f, 512f)
            .addGroup(scaleX = 0.83333333f, scaleY = 0.83333333f, translationX = 42.66666667f, translationY = 42.66666667f)
            .addPath(addPathNodes("M416 208c0 45.9-14.9 88.3-40 122.7L502.6 457.4c12.5 12.5 12.5 32.8 0 45.3s-32.8 12.5-45.3 0L330.7 376C296.3 401.1 253.9 416 208 416 93.1 416 0 322.9 0 208S93.1 0 208 0 416 93.1 416 208zM208 352a144 144 0 1 0 0-288 144 144 0 1 0 0 288z"), fill = SolidColor(Color.Black))
            .clearGroup()
            .build()
    }
    val Close: ImageVector by lazy {
        ImageVector.Builder("FA_xmark", 18.dp, 24.dp, 384f, 512f)
            .addGroup(scaleX = 0.83333333f, scaleY = 0.83333333f, translationX = 32.00000000f, translationY = 42.66666667f)
            .addPath(addPathNodes("M55.1 73.4c-12.5-12.5-32.8-12.5-45.3 0s-12.5 32.8 0 45.3L147.2 256 9.9 393.4c-12.5 12.5-12.5 32.8 0 45.3s32.8 12.5 45.3 0L192.5 301.3 329.9 438.6c12.5 12.5 32.8 12.5 45.3 0s12.5-32.8 0-45.3L237.8 256 375.1 118.6c12.5-12.5 12.5-32.8 0-45.3s-32.8-12.5-45.3 0L192.5 210.7 55.1 73.4z"), fill = SolidColor(Color.Black))
            .clearGroup()
            .build()
    }
    val Edit: ImageVector by lazy {
        ImageVector.Builder("FA_pen", 24.dp, 24.dp, 512f, 512f)
            .addGroup(scaleX = 0.83333333f, scaleY = 0.83333333f, translationX = 42.66666667f, translationY = 42.66666667f)
            .addPath(addPathNodes("M352.9 21.2L308 66.1 445.9 204 490.8 159.1C504.4 145.6 512 127.2 512 108s-7.6-37.6-21.2-51.1L455.1 21.2C441.6 7.6 423.2 0 404 0s-37.6 7.6-51.1 21.2zM274.1 100L58.9 315.1c-10.7 10.7-18.5 24.1-22.6 38.7L.9 481.6c-2.3 8.3 0 17.3 6.2 23.4s15.1 8.5 23.4 6.2l127.8-35.5c14.6-4.1 27.9-11.8 38.7-22.6L412 237.9 274.1 100z"), fill = SolidColor(Color.Black))
            .clearGroup()
            .build()
    }
    val List: ImageVector by lazy {
        ImageVector.Builder("FA_list", 24.dp, 24.dp, 512f, 512f)
            .addGroup(scaleX = 0.83333333f, scaleY = 0.83333333f, translationX = 42.66666667f, translationY = 42.66666667f)
            .addPath(addPathNodes("M40 48C26.7 48 16 58.7 16 72l0 48c0 13.3 10.7 24 24 24l48 0c13.3 0 24-10.7 24-24l0-48c0-13.3-10.7-24-24-24L40 48zM192 64c-17.7 0-32 14.3-32 32s14.3 32 32 32l288 0c17.7 0 32-14.3 32-32s-14.3-32-32-32L192 64zm0 160c-17.7 0-32 14.3-32 32s14.3 32 32 32l288 0c17.7 0 32-14.3 32-32s-14.3-32-32-32l-288 0zm0 160c-17.7 0-32 14.3-32 32s14.3 32 32 32l288 0c17.7 0 32-14.3 32-32s-14.3-32-32-32l-288 0zM16 232l0 48c0 13.3 10.7 24 24 24l48 0c13.3 0 24-10.7 24-24l0-48c0-13.3-10.7-24-24-24l-48 0c-13.3 0-24 10.7-24 24zM40 368c-13.3 0-24 10.7-24 24l0 48c0 13.3 10.7 24 24 24l48 0c13.3 0 24-10.7 24-24l0-48c0-13.3-10.7-24-24-24l-48 0z"), fill = SolidColor(Color.Black))
            .clearGroup()
            .build()
    }
    val Selected: ImageVector by lazy {
        ImageVector.Builder("FA_circle-check", 24.dp, 24.dp, 512f, 512f)
            .addGroup(scaleX = 0.83333333f, scaleY = 0.83333333f, translationX = 42.66666667f, translationY = 42.66666667f)
            .addPath(addPathNodes("M256 512a256 256 0 1 1 0-512 256 256 0 1 1 0 512zM374 145.7c-10.7-7.8-25.7-5.4-33.5 5.3L221.1 315.2 169 263.1c-9.4-9.4-24.6-9.4-33.9 0s-9.4 24.6 0 33.9l72 72c5 5 11.8 7.5 18.8 7s13.4-4.1 17.5-9.8L379.3 179.2c7.8-10.7 5.4-25.7-5.3-33.5z"), fill = SolidColor(Color.Black))
            .clearGroup()
            .build()
    }
    val Minus: ImageVector by lazy {
        ImageVector.Builder("FA_minus", 21.dp, 24.dp, 448f, 512f)
            .addGroup(scaleX = 0.75000000f, scaleY = 0.75000000f, translationX = 56.00000000f, translationY = 64.00000000f)
            .addPath(addPathNodes("M0 256c0-17.7 14.3-32 32-32l384 0c17.7 0 32 14.3 32 32s-14.3 32-32 32L32 288c-17.7 0-32-14.3-32-32z"), fill = SolidColor(Color.Black))
            .clearGroup()
            .build()
    }
}
