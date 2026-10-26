package com.mewname.app

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MissionOverlayAttachmentTest {
    @Test fun linkedPanelsFollowBubbleHideDuringCaptureAndDisappearWhenCompleted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DailyMissionStore(context)
        store.preferences.edit().clear().putString(MissionPlacement.KEY, MissionPlacement.BOTH.name).commit()
        repeat(7) { store.save(DailyMission(id = "$it", name = "Task $it", icon = "mission_icons/pokeball_sprite.png")) }
        val panels = linkedMapOf<View, WindowManager.LayoutParams>()
        val overlay = DailyMissionOverlay(context,
            { view, params -> panels[view] = params; true }, { panels.remove(it) },
            { view, params -> panels[view] = params; true })
        val bubble = View(context).apply { layout(0, 0, 56, 56) }
        val anchor = WindowManager.LayoutParams().apply { x = 100; y = 200 }
        try {
            overlay.start()
            overlay.followBubble(bubble, anchor)
            assertEquals(3, panels.size)
            fun checkNoOverlap() {
                assertTrue(panels.values.any { it.y + it.height < anchor.y })
                assertTrue(panels.values.any { it.y > anchor.y + bubble.height })
                assertTrue(panels.values.all { it.x >= 0 && it.y >= 0 && it.height > 0 })
                val bar = panels.values.single { it.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0 }
                assertEquals(panels.values.minOf { it.y }, bar.y)
                assertEquals(panels.values.maxOf { it.y + it.height }, bar.y + bar.height)
                assertTrue(bar.y <= anchor.y && bar.y + bar.height >= anchor.y + bubble.height)
            }
            checkNoOverlap()
            anchor.y = -200
            overlay.followBubble(bubble, anchor)
            checkNoOverlap()
            anchor.y = 10000
            overlay.followBubble(bubble, anchor)
            checkNoOverlap()
            overlay.hideForCapture(true)
            assertTrue(panels.keys.all { it.visibility == View.INVISIBLE })
            overlay.hideForCapture(false)
            assertTrue(panels.keys.all { it.visibility == View.VISIBLE })
            // With one pending mission, BOTH places it above. The bar ends behind
            // the icon's lower edge, excluding the button's outer padding.
            store.all().drop(1).forEach { store.complete(it.id, true) }
            shadowOf(Looper.getMainLooper()).idle()
            overlay.followBubble(bubble, anchor)
            val singleBar = panels.values.single { it.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0 }
            val iconRadius = (bubble.width - (8 * context.resources.displayMetrics.density).toInt()).coerceAtLeast(1) / 2f
            assertEquals((anchor.y + bubble.height / 2 + iconRadius).toInt(), singleBar.y + singleBar.height)
            store.preferences.edit().putString(MissionPlacement.KEY, MissionPlacement.BELOW.name).commit()
            shadowOf(Looper.getMainLooper()).idle()
            overlay.followBubble(bubble, anchor)
            val belowBar = panels.values.single { it.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0 }
            assertEquals((anchor.y + bubble.height / 2 - iconRadius).toInt(), belowBar.y)
            store.preferences.edit().putString(MissionPlacement.KEY, MissionPlacement.FREE.name).commit()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, panels.size)
            store.all().forEach { store.complete(it.id, true) }
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(panels.isEmpty())
        } finally { overlay.close() }
        assertTrue(panels.isEmpty())
    }
}
