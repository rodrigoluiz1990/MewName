package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class DailyMissionStoreTest {
    private fun store(): DailyMissionStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return DailyMissionStore(context).also { it.preferences.edit().clear().commit() }
    }
    @Test fun completionPersistsAndReturnsOnNextLocalDay() {
        val store = store()
        val mission = DailyMission(name = "Presente", icon = "mission_icons/GiftBox.png")
        store.save(mission)
        val day = LocalDate.of(2026, 10, 14)
        store.complete(mission.id, true, day)
        val restored = DailyMissionStore(ApplicationProvider.getApplicationContext()).all().single()
        assertTrue(restored.completed(day))
        assertFalse(restored.completed(day.plusDays(1)))
        store.complete(mission.id, false, day)
        assertFalse(store.all().single().completed(day))
    }
    @Test fun editingRetainsCompletionAndDeletionDoesNotAffectOthers() {
        val store = store()
        val first = DailyMission(name = "Reide", icon = "a", completedDay = LocalDate.now().toString())
        store.save(first); store.save(DailyMission(name = "Reide", icon = "b"))
        store.save(first.copy(name = "Reide diária", icon = "c"))
        assertEquals(2, store.all().size)
        assertTrue(store.all().first().completed())
        store.delete(first.id)
        assertEquals("b", store.all().single().icon)
    }
    @Test fun overlayDisappearsWhenDoneAndHidesDuringCapture() {
        val store = store()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mission = DailyMission(name = "Presente", icon = "mission_icons/GiftBox.png")
        store.save(mission)
        var attached: android.view.View? = null
        val overlay = DailyMissionOverlay(context, { view, params ->
            assertTrue(params.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
            attached = view; true
        }, { attached = null })
        overlay.start()
        assertNotNull(attached)
        overlay.hideForCapture(true)
        assertEquals(android.view.View.INVISIBLE, attached!!.visibility)
        overlay.hideForCapture(false)
        assertEquals(android.view.View.VISIBLE, attached!!.visibility)
        val column = ((attached as android.widget.LinearLayout).getChildAt(1) as android.widget.ScrollView).getChildAt(0) as android.widget.LinearLayout
        column.getChildAt(0).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNull(attached)
        assertTrue(store.all().single().completed())
        store.complete(mission.id, false)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNotNull(attached)
        overlay.close()
        assertNull(attached)
        store.save(DailyMission(name = "Após fechar", icon = "test"))
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNull(attached)
    }
    @Test fun draggingHandleKeepsMissionPendingClampsAndRestoresPosition() {
        val store = store()
        val context = ApplicationProvider.getApplicationContext<Context>()
        store.save(DailyMission(name = "Arrastar", icon = "mission_icons/GiftBox.png"))
        var attached: android.view.View? = null
        var params: android.view.WindowManager.LayoutParams? = null
        var updates = 0
        val overlay = DailyMissionOverlay(context, { view, layout -> attached = view; params = layout; true },
            { attached = null }, { _, _ -> updates++; true })
        overlay.start()
        val handle = (attached as android.widget.LinearLayout).getChildAt(0)
        fun touch(action: Int, x: Float, y: Float) {
            val event = android.view.MotionEvent.obtain(0, 1, action, x, y, 0)
            handle.dispatchTouchEvent(event); event.recycle()
        }
        touch(android.view.MotionEvent.ACTION_DOWN, 20f, 20f)
        touch(android.view.MotionEvent.ACTION_MOVE, -10000f, -10000f)
        touch(android.view.MotionEvent.ACTION_UP, -10000f, -10000f)
        assertTrue(updates > 0)
        assertEquals(0, params!!.x); assertEquals(0, params!!.y)
        assertFalse(store.all().single().completed())
        overlay.close(); overlay.start()
        assertEquals(0, params!!.x); assertEquals(0, params!!.y)
        assertFalse(store.all().single().completed())
        overlay.close()
    }
    @Test fun noArtificialMissionLimitAndEverySelectableIconDecodes() {
        val store = store()
        repeat(100) { store.save(DailyMission(name = "Missão $it", icon = "test")) }
        assertEquals(100, store.all().size)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val icons = missionIcons(context)
        assertEquals(icons.size, icons.distinct().size)
        assertTrue("mission_icons/pokestop.png" in icons)
        assertFalse("mission_icons/pokeball_store.100.png" in icons)
        icons.forEach { path -> context.assets.open(path).use {
            assertNotNull(path, android.graphics.BitmapFactory.decodeStream(it))
        } }
    }
}