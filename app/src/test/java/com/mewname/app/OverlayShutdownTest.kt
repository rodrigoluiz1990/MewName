package com.mewname.app

import android.content.Context
import android.view.WindowManager
import android.widget.FrameLayout
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class OverlayShutdownTest {
    @Test
    fun `shutdown detaches windows cancels work and rejects reopening even when called twice`() {
        val service = Robolectric.buildService(OverlayService::class.java).get()
        val manager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ReflectionHelpers.setField(service, "windowManager", manager)
        val windows = ReflectionHelpers.getField<MutableSet<android.view.View>>(service, "attachedOverlays")
        val view = FrameLayout(service)
        manager.addView(view, WindowManager.LayoutParams(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY))
        windows.add(view)
        ReflectionHelpers.setField(service, "resultsView", view)
        ReflectionHelpers.callInstanceMethod<Unit>(service, "shutdownOverlays")
        ReflectionHelpers.callInstanceMethod<Unit>(service, "shutdownOverlays")
        assertTrue(windows.isEmpty())
        assertFalse(view.isAttachedToWindow)
        assertNull(ReflectionHelpers.getField<android.view.View?>(service, "resultsView"))
        assertTrue(ReflectionHelpers.getField<Job>(service, "serviceJob").isCancelled)
        assertFalse(OverlayService.isBubbleActive.value)
        val added = ReflectionHelpers.callInstanceMethod<Boolean>(service, "addOverlayView",
            ReflectionHelpers.ClassParameter.from(android.view.View::class.java, FrameLayout(service)),
            ReflectionHelpers.ClassParameter.from(WindowManager.LayoutParams::class.java, WindowManager.LayoutParams()))
        assertFalse(added)
        assertTrue(windows.isEmpty())
    }
}