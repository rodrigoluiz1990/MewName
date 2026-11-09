package com.mewname.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EventRemindersTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = EventReminders(context)
    private val now = Instant.parse("2026-11-07T12:00:00Z").toEpochMilli()
    private fun event(start: String = "2026-11-21T14:00:00", end: String = "2026-11-21T17:00:00") = CalendarEvent(
        "event", "Community Day", "community", "Community Day", CalendarParser.date(start), CalendarParser.date(end),
        null, "https://example.com", start, end)
    @Before fun clean() { store.preferences.edit().clear().commit() }
    @Test fun localTimeFollowsTimezoneButGlobalTimeDoesNot() {
        val brazil = ZoneId.of("America/Sao_Paulo")
        val utc = ZoneId.of("UTC")
        assertEquals(3 * 3600000L, reminderTime("2026-11-21T14:00:00", brazil) - reminderTime("2026-11-21T14:00:00", utc))
        assertEquals(reminderTime("2026-11-21T14:00:00Z", brazil), reminderTime("2026-11-21T14:00:00Z", utc))
        assertEquals(reminderTime("2026-11-21T00:00:00", utc), reminderTime("2026-11-21", utc))
    }
    @Test fun schedulingAgainReplacesAlarmAndCancelRemovesIt() {
        val json = event().reminderJson()
        assertTrue(store.schedule(json, now))
        assertTrue(store.schedule(json, now))
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        assertEquals(1, alarms.scheduledAlarms.size)
        store.cancel(json.getString("id"))
        assertFalse(store.contains(json.getString("id")))
        assertEquals(0, alarms.scheduledAlarms.size)
    }
    @Test fun restoreRearmsSavedReminderWithoutDuplicating() {
        val json = event().reminderJson()
        store.preferences.edit().putString(json.getString("id"), json.toString()).commit()
        store.restore(now)
        store.restore(now)
        assertEquals(1, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
    }
    @Test fun deliveredOnlyOnceAndTapContainsEventDetails() {
        val json = event().reminderJson()
        assertTrue(store.schedule(json, now))
        val due = reminderTime(json.getString("start"))
        store.deliver(json.getString("id"), due)
        store.deliver(json.getString("id"), due)
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        assertEquals(1, manager.allNotifications.size)
        val intent = shadowOf(manager.allNotifications.single().contentIntent).savedIntent
        assertEquals("CALENDAR", intent.getStringExtra("BUBBLE_DESTINATION"))
        assertEquals(json.toString(), intent.getStringExtra("CALENDAR_EVENT"))
        assertFalse(store.contains(json.getString("id")))
    }
    @Test fun staleAndPastEventsDoNotNotify() {
        val json = event().reminderJson()
        val due = reminderTime(json.getString("start"))
        assertFalse(store.schedule(json, due))
        store.schedule(json, now)
        store.deliver(json.getString("id"), due + 2 * 3600000L)
        assertEquals(0, shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.size)
    }
    @Test fun eventRoundTripPreservesGlobalTimeAndNullableFields() {
        val original = event("2026-11-21T14:00:00Z", "2026-11-21T17:00:00Z")
        val restored = reminderEvent(original.reminderJson())
        assertEquals(original.sourceStart, restored.sourceStart)
        assertEquals(original.start, restored.start)
        assertNull(restored.image)
    }
    @Test fun blockedNotificationsDoNotCreateReminder() {
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        val json = event().reminderJson()
        assertFalse(store.schedule(json, now))
        assertFalse(store.contains(json.getString("id")))
    }
    @Test fun exactArtworkMatchUsesLocalFileButUnrelatedEventKeepsRemoteImage() {
        val artwork = EventArtworkCatalog(context)
        assertEquals("file:///android_asset/events/dynamax-trails-2026.jpg",
            artwork.resolve("https://example.com/art.jpg", "https://leekduck.com/events/dynamax-trails-2026/"))
        assertEquals("https://example.com/other.jpg", artwork.resolve("https://example.com/other.jpg", "https://example.com/unrelated"))
    }
    @Test fun removingEventFromSuccessfulFeedCancelsReminder() {
        val json = event().reminderJson()
        store.schedule(json, now)
        store.reconcile(emptyList(), 2026)
        assertFalse(store.contains(json.getString("id")))
    }
}
