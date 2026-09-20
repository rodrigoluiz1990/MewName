package com.mewname.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.TrainerTeamBackgroundDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrainerTeamBackgroundDetectorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `recognizes team palette on trainer style backgrounds`() {
        assertEquals("Instinct", TrainerTeamBackgroundDetector.detect(context, trainerBackground(0xFFFFF0B8.toInt(), 0xFFFFC400.toInt())))
        assertEquals("Mystic", TrainerTeamBackgroundDetector.detect(context, trainerBackground(0xFFD8F3F8.toInt(), 0xFF377DE5.toInt())))
        assertEquals("Valor", TrainerTeamBackgroundDetector.detect(context, trainerBackground(0xFFFFDCD7.toInt(), 0xFFF13A35.toInt())))
    }

    @Test
    fun `does not assign a team to a neutral profile`() {
        assertNull(TrainerTeamBackgroundDetector.detect(context, trainerBackground(0xFFF3F3F3.toInt(), 0xFF9E9E9E.toInt())))
    }

    private fun trainerBackground(fieldColor: Int, railColor: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(fieldColor)
        val paint = Paint().apply { color = railColor }
        canvas.drawRect(0f, 0f, 18f, 590f, paint)
        canvas.drawRect(382f, 0f, 400f, 590f, paint)
        paint.color = Color.rgb(55, 55, 62)
        canvas.drawRect(115f, 210f, 295f, 560f, paint)
        paint.color = Color.WHITE
        canvas.drawRect(20f, 590f, 380f, 800f, paint)
        return bitmap
    }
}
