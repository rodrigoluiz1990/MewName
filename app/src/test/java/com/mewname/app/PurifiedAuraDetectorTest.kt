package com.mewname.app
import android.graphics.Bitmap
import android.graphics.Color
import com.mewname.app.domain.PurifiedAuraDetector
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class PurifiedAuraDetectorTest {
 @Test fun purpleBackgroundAloneIsNotPurification() {
  val b=Bitmap.createBitmap(360,800,Bitmap.Config.ARGB_8888)
  b.eraseColor(Color.rgb(75,40,80));assertEquals(0,PurifiedAuraDetector.glints(b));b.recycle()
 }
 @Test fun whiteCrossGlintsAreDifferentFromYellowStars() {
  for (color in listOf(Color.WHITE,Color.YELLOW)) {
   val b=Bitmap.createBitmap(360,800,Bitmap.Config.ARGB_8888);b.eraseColor(Color.rgb(75,40,80))
   for ((x,y) in listOf(100 to 121,180 to 151,260 to 181)) for (d in -8..8) {
    b.setPixel(x+d,y,color);b.setPixel(x,y+d,color)
   }
   if(color==Color.WHITE) assertTrue(PurifiedAuraDetector.glints(b)>=3) else assertEquals(0,PurifiedAuraDetector.glints(b))
   b.recycle()
  }
 }
}
