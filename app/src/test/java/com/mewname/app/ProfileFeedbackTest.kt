package com.mewname.app

import android.content.Context
import android.view.View
import android.view.WindowManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSettings
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class ProfileFeedbackTest {
    @Test fun confirmationIsAnAttachedOverlayAndCanBeDismissed() {
        ShadowSettings.setCanDrawOverlays(true)
        val service = Robolectric.buildService(OverlayService::class.java).get()
        ReflectionHelpers.setField(service, "windowManager", service.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        try {
            ReflectionHelpers.callInstanceMethod<Unit>(service, "showProfileUpdatedFeedback")
            val notice = ReflectionHelpers.getField<View>(service, "profileFeedbackView")
            val windows = ReflectionHelpers.getField<Set<View>>(service, "attachedOverlays")
            assertTrue(windows.contains(notice))
            notice.performClick()
            assertNull(ReflectionHelpers.getField<View?>(service, "profileFeedbackView"))
            assertFalse(windows.contains(notice))
        } finally {
            ReflectionHelpers.callInstanceMethod<Unit>(service, "shutdownOverlays")
            ShadowSettings.setCanDrawOverlays(false)
        }
    }
    @Test fun allAppShortcutsHaveExactlyOneDestinationAndUniqueKeys() {
        assertEquals(bubbleAppShortcuts.size, bubbleAppShortcuts.map { it.key }.distinct().size)
        bubbleAppShortcuts.forEach { assertTrue((it.screen != null) xor (it.url != null)) }
        assertTrue(bubbleAppShortcuts.any { it.screen == AppScreen.TRAINER_PROFILE })
        assertTrue(bubbleAppShortcuts.any { it.screen == AppScreen.MOVEDEX })
    }
}