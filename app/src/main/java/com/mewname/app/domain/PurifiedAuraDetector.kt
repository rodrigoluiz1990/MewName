package com.mewname.app.domain

import android.graphics.Bitmap
import android.graphics.Color

/** White cross-shaped purification glints, not the purple background or yellow shiny stars. */
internal object PurifiedAuraDetector {
    fun glints(bitmap: Bitmap): Int {
        val scaled = Bitmap.createScaledBitmap(bitmap, 360, 800, true)
        try {
            fun brightness(x: Int, y: Int): Float {
                val c = scaled.getPixel(x,y)
                return (Color.red(c) + Color.green(c) + Color.blue(c)) / 765f
            }
            val hits = mutableListOf<Pair<Int,Int>>()
            for (y in 85..207 step 2) for (x in 60..289 step 2) {
                val c = scaled.getPixel(x,y)
                val rgb = listOf(Color.red(c),Color.green(c),Color.blue(c))
                if (rgb.max() - rgb.min() > 35 || brightness(x,y) < .8f) continue
                for (d in listOf(3,5,7)) {
                    val arms = (brightness(x+d,y)+brightness(x-d,y)+brightness(x,y+d)+brightness(x,y-d))/4
                    val diagonal = (brightness(x+d,y+d)+brightness(x-d,y+d)+brightness(x+d,y-d)+brightness(x-d,y-d))/4
                    if (arms-diagonal > .085f && arms > .63f && hits.all { (x-it.first)*(x-it.first)+(y-it.second)*(y-it.second)>400 }) {
                        hits.add(x to y)
                        break
                    }
                }
            }
            return hits.size
        } finally { if (scaled !== bitmap) scaled.recycle() }
    }
}
