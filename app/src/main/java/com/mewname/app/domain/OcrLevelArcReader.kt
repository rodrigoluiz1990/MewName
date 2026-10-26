package com.mewname.app.domain

import android.graphics.Bitmap
import android.content.Context
import kotlin.math.*
import org.json.JSONArray
import org.json.JSONObject

internal data class DetailArcReading(val fraction: Double, val tolerance: Double, val geometry: String)

/** Finds the semicircle from radial contrast, then a unique thick white marker on it.
 * Rejects bright/occluded backgrounds instead of guessing a level from screen coordinates. */
internal object OcrLevelArcReader {
    fun detect(source: Bitmap): DetailArcReading? {
        val width = 360
        val height = (source.height.toDouble() * width / source.width).roundToInt()
        if (height < width || source.width < 100) return null
        val image = Bitmap.createScaledBitmap(source, width, height, true)
        try {
            fun white(x: Double, y: Double): Double {
                val ix = x.roundToInt(); val iy = y.roundToInt()
                if (ix !in 0 until width || iy !in 0 until height) return 0.0
                val pixel = image.getPixel(ix,iy)
                val r = (pixel shr 16) and 255; val g = (pixel shr 8) and 255; val b = pixel and 255
                return if (minOf(r,g,b) >= 205 && maxOf(r,g,b)-minOf(r,g,b) < 40) 1.0 else 0.0
            }
            fun at(cx: Double, cy: Double, radius: Double, angle: Double) =
                white(cx-radius*cos(angle),cy-radius*sin(angle))
            var best = 0.0; var bestR = 0.0; var bestY = 0.0
            val cx = width / 2.0
            for (radius in 136..158 step 2) for (y in (height*.24).toInt()..(height*.34).toInt() step 2) {
                if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
                var score = 0.0; var hits = 0
                for (degrees in 8..172 step 4) {
                    val angle = Math.toRadians(degrees.toDouble())
                    val line = (-1..1).maxOf { at(cx,y.toDouble(),radius+it.toDouble(),angle) }
                    val background = (at(cx,y.toDouble(),radius-5.0,angle)+at(cx,y.toDouble(),radius+5.0,angle))/2
                    if (line > .5 && background < .5) hits++
                    score += line-background
                }
                if (hits >= 22 && score > best) { best=score; bestR=radius.toDouble(); bestY=y.toDouble() }
            }
            if (best < 22) return null
            val markers = (0..180).mapNotNull { degree ->
                val angle = Math.toRadians(degree.toDouble())
                val x = cx-bestR*cos(angle); val y=bestY-bestR*sin(angle)
                var fill=0.0; var count=0
                for (dx in -2..2) for (dy in -2..2) if(dx*dx+dy*dy<=4) {
                    fill+=white(x+dx,y+dy);count++
                }
                // Thin arc alone must not count as the marker.
                val radial = at(cx,bestY,bestR-3,angle)+at(cx,bestY,bestR+3,angle)
                if (fill/count >= .9 && radial >= 1.0) degree else null
            }
            if (markers.isEmpty()) return null
            val groups = mutableListOf<MutableList<Int>>()
            markers.forEach { d ->
                if (groups.isEmpty() || d-groups.last().last()>2) groups.add(mutableListOf())
                groups.last().add(d)
            }
            val marker = groups.singleOrNull() ?: return null
            if(marker.last()-marker.first()>7) return null
            val fraction = marker.average()/180.0
            return DetailArcReading(fraction, maxOf(3.0,(marker.last()-marker.first())/2.0+1)/180.0,
                "${(bestR/6).roundToInt()}-${(bestY/8).roundToInt()}")
        } finally { if (image !== source) image.recycle() }
    }
}

/** Empirical CPM scale calibrated only by exact CP/HP levels from closed appraisal screens.
 * No obsolete assumption about the trainer-level cap is used. Trainer/display changes invalidate it.
 * The CPM/angle relation is also described in nneonneo/pogo-iv-reader/read_screenshot.py;
 * the endpoint here is learned, never copied from that old game's level limits. */
internal class DetailArcCalibration(context: Context, width: Int, height: Int) {
    private val prefs = context.getSharedPreferences("ocr_detail_arc", Context.MODE_PRIVATE)
    private val trainer = context.getSharedPreferences("trainer_profile", Context.MODE_PRIVATE).getString("level", "").orEmpty()
    private val key = "v1:$width:$height:$trainer"
    private fun samples(): List<JSONObject> = runCatching {
        val array=JSONArray(prefs.getString(key,"[]")); (0 until array.length()).map { array.getJSONObject(it) }
    }.getOrDefault(emptyList())
    fun observe(arc: DetailArcReading, level: Double, multipliers: List<Pair<Double,Double>>) {
        if (trainer.isBlank() || arc.fraction !in .1.. .95) return
        val cpm=multipliers.firstOrNull { it.first==level }?.second ?: return
        val endpoint=multipliers.first().second+(cpm-multipliers.first().second)/arc.fraction
        if(endpoint !in .2..1.0) return
        val samples=samples().filterNot { it.optDouble("level")==level && it.optString("geometry")==arc.geometry }.takeLast(11)
        val next=JSONObject().put("level",level).put("endpoint",endpoint).put("geometry",arc.geometry)
        prefs.edit().putString(key,JSONArray(samples+next).toString()).apply()
    }
    fun levels(arc: DetailArcReading, multipliers: List<Pair<Double,Double>>): Set<Double>? {
        if(trainer.isBlank()) return null
        val samples=samples().filter { it.optString("geometry")==arc.geometry }
        if(samples.map { it.optDouble("level") }.distinct().size<3) return null
        val levels=samples.map { it.optDouble("level") }
        if(levels.max()-levels.min()<5) return null
        val endpoints=samples.map { it.optDouble("endpoint") }
        if(endpoints.max()-endpoints.min()>.008) return null
        val endpoint=endpoints.average(); val start=multipliers.first().second
        return multipliers.filter { (_, cpm) ->
            val fraction=(cpm-start)/(endpoint-start)
            abs(fraction-arc.fraction)<=arc.tolerance+.008
        }.map { it.first }.toSet().takeIf { it.isNotEmpty() }
    }
}
