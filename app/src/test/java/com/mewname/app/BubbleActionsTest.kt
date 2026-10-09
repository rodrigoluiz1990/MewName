package com.mewname.app

import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSettings
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class BubbleActionsTest {
    @Test fun everySelectedShortcutUsesTrainerOrderAndCollectionOpensAnOverlay() {
        ShadowSettings.setCanDrawOverlays(true)
        val service = Robolectric.buildService(OverlayService::class.java).get()
        ReflectionHelpers.setField(service, "windowManager",
            service.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        val prefs = service.getSharedPreferences("bubble_actions", 0)
        prefs.edit().clear().commit()
        try {
            val order = listOf("app_collections") + BubbleActionSettings.keys.reversed().filter { it != "app_collections" }
            BubbleActionSettings.saveOrder(service, order)
            BubbleActionSettings.save(service, order.toSet())
            ReflectionHelpers.callInstanceMethod<Unit>(service, "showFloatingButton")
            val bubble = ReflectionHelpers.getField<View>(service, "floatingButton")
            val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 20f, 20f, 0)
            bubble.dispatchTouchEvent(event); event.recycle()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(450))
            val menu = ReflectionHelpers.getField<FrameLayout>(service, "bubbleMenuView")
            assertEquals(order.size + 1, menu.childCount)
            assertEquals(order, (0 until order.size).map { menu.getChildAt(it).tag })
            menu.getChildAt(0).performClick()
            assertNull(ReflectionHelpers.getField<View?>(service, "bubbleMenuView"))
            val collection = ReflectionHelpers.getField<View>(service, "resultsView")
            assertNotNull(collection)
            assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                (collection.layoutParams as WindowManager.LayoutParams).type)
            assertNull(shadowOf(service).nextStartedActivity)
        } finally {
            ReflectionHelpers.callInstanceMethod<Unit>(service, "shutdownOverlays")
            prefs.edit().clear().commit()
            ShadowSettings.setCanDrawOverlays(false)
        }
    }
    @Test fun longPressOpensActionsAfter450MillisAndOutsideTapDismisses() {
        ShadowSettings.setCanDrawOverlays(true)
        val service = Robolectric.buildService(OverlayService::class.java).get()
        ReflectionHelpers.setField(service, "windowManager",
            service.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        try {
            ReflectionHelpers.callInstanceMethod<Unit>(service, "showFloatingButton")
            val bubble = ReflectionHelpers.getField<View>(service, "floatingButton")
            val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 20f, 20f, 0)
            bubble.dispatchTouchEvent(event)
            event.recycle()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(449))
            assertNull(ReflectionHelpers.getField<View?>(service, "bubbleMenuView"))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
            val menu = ReflectionHelpers.getField<FrameLayout>(service, "bubbleMenuView")
            assertNotNull(menu)
            assertEquals(5, menu.childCount)
            menu.performClick()
            assertNull(ReflectionHelpers.getField<View?>(service, "bubbleMenuView"))
            assertFalse(menu.isAttachedToWindow)
        } finally {
            ReflectionHelpers.callInstanceMethod<Unit>(service, "shutdownOverlays")
            ShadowSettings.setCanDrawOverlays(false)
        }
    }
}