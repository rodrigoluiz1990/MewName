package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class HomeEventCarouselTest {
    @Test fun carouselWrapsBothWaysAndPausesOnlyAfterManualNavigation() {
        val state = HomeEventCarouselState()
        state.count = 3
        state.move(-1)
        assertEquals(2, state.index)
        val manual = state.lastManual
        state.move(1, manual = false)
        assertEquals(0, state.index)
        assertEquals(manual, state.lastManual)
        state.count = 1
        state.move(1)
        assertEquals(0, state.index)
    }
    @Test fun activeImagesAreOrderedDeduplicatedAndExcludeOtherDays() {
        val today = LocalDate.of(2026, 10, 12)
        fun art(id: String, priority: Int, image: String = id) = HomeEventArt(id,id,image,today,today,priority)
        val result = HomeEventArtParser.active(listOf(art("week",300), art("community",500), art("max",450),
            art("duplicate",100,"week"), art("future",600).copy(start=today.plusDays(1),end=today.plusDays(1))),today)
        assertEquals(listOf("community","max","week"),result.map { it.title })
    }
    @Test fun preferencesPersistAndInvalidIntervalUsesDefault() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("carousel-test",0)
        prefs.edit().clear().commit()
        assertTrue(AppAppearance.read(prefs).eventAutoRotate)
        assertEquals(10,AppAppearance.read(prefs).eventIntervalSeconds)
        prefs.edit().putBoolean("event_auto_rotate",false).putInt("event_interval",30).commit()
        assertFalse(AppAppearance.read(prefs).eventAutoRotate)
        assertEquals(30,AppAppearance.read(prefs).eventIntervalSeconds)
        prefs.edit().putInt("event_interval",-1).commit()
        assertEquals(10,AppAppearance.read(prefs).eventIntervalSeconds)
    }
}