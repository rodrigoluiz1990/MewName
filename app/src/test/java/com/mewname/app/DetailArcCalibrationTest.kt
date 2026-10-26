package com.mewname.app

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DetailArcCalibrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun calibrationNeedsIndependentLevelsAndInvalidatesOnTrainerChange() {
        context.getSharedPreferences("ocr_detail_arc", 0).edit().clear().commit()
        val trainer = context.getSharedPreferences("trainer_profile", 0)
        trainer.edit().putString("level", "73").commit()
        val multipliers = PvpRankCalculator().levelMultipliers()
        val start = multipliers.first().second
        val end = multipliers.last().second
        fun arc(level: Double) = DetailArcReading((multipliers.first { it.first == level }.second-start)/(end-start), .01, "same")
        val calibration = DetailArcCalibration(context, 1220, 2712)
        assertNull(calibration.levels(arc(20.0), multipliers))
        calibration.observe(arc(10.0), 10.0, multipliers)
        calibration.observe(arc(20.0), 20.0, multipliers)
        assertNull(calibration.levels(arc(20.0), multipliers))
        calibration.observe(arc(30.0), 30.0, multipliers)
        val levels = calibration.levels(arc(20.0), multipliers)!!
        assertTrue(20.0 in levels)
        assertFalse(40.0 in levels)
        assertNull(calibration.levels(arc(20.0).copy(geometry="other"), multipliers))
        trainer.edit().putString("level", "74").commit()
        assertNull(DetailArcCalibration(context, 1220, 2712).levels(arc(20.0), multipliers))
    }
    @Test fun blankOrBrightScreenDoesNotInventArc() {
        val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
        assertNull(OcrLevelArcReader.detect(bitmap))
        bitmap.eraseColor(android.graphics.Color.WHITE)
        assertNull(OcrLevelArcReader.detect(bitmap))
        bitmap.recycle()
    }
    @Test fun detailAnchorsDoNotOverrideVisibleAppraisalLabels() {
        val bounds = android.graphics.Rect(0, 0, 1000, 2000)
        fun line(text: String, y: Int) = com.mewname.app.ocr.OcrTextLine(text, android.graphics.Rect(200, y, 800, y+40))
        val detail = listOf(line("PC 500", 180), line("100/120 HP", 950), line("NOVO ATAQUE", 1700))
        assertTrue(OcrNumericReader.isDetailWithoutAppraisal(detail.joinToString(" ") { it.text }, detail, bounds))
        val appraisal = detail + listOf(line("ATAQUE", 1400), line("DEFESA", 1550), line("PS", 1700))
        assertFalse(OcrNumericReader.isDetailWithoutAppraisal(appraisal.joinToString(" ") { it.text }, appraisal, bounds))
    }
    @Test fun allowedLevelsRejectConflictingEvidence() {
        val calculator = PvpRankCalculator()
        val cp = calculator.estimateCpAtLevel(context, "Psyduck", 12, 15, 15, 20.0)!!
        val hp = calculator.estimateHpAtLevel(context, "Psyduck", 15, 20.0)!!
        assertTrue(calculator.resolveIvEvidence(context, "Psyduck", cp, hp, allowedLevels=setOf(20.0)).matches > 0)
        assertEquals(0, calculator.resolveIvEvidence(context, "Psyduck", cp, hp, allowedLevels=setOf(1.0)).matches)
    }
}
