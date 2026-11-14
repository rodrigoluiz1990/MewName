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
    @Test fun recurringTitleDoesNotTransferReminderToNextWeek() {
        val original = event().reminderJson()
        store.schedule(original, now)
        val next = event("2026-11-28T14:00:00", "2026-11-28T17:00:00")
        store.reconcile(listOf(next), 2026, now)
        assertFalse(store.contains(original.getString("id")))
        assertFalse(store.contains(next.reminderJson().getString("id")))
        assertEquals(0, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
    }
    @Test fun imageChangeKeepsChoiceWhileNotificationsAreDisabled() {
        val original = event().reminderJson()
        store.schedule(original, now)
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        manager.setNotificationsEnabled(false)
        store.reconcile(listOf(event().copy(image = "https://example.com/new.jpg")), 2026, now)
        assertTrue(store.contains(original.getString("id")))
        assertTrue(store.preferences.getString(original.getString("id"), "")!!.contains("new.jpg"))
        manager.setNotificationsEnabled(true)
        store.restore(now)
        assertEquals(1, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
    }
    @Test @Config(sdk = [31]) fun deniedExactAlarmPreservesExistingChoiceAndCanRearm() {
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val json = event().reminderJson()
        assertTrue(store.schedule(json, now))
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        store.reconcile(listOf(event().copy(image = "new")), 2026, now)
        assertTrue(store.contains(json.getString("id")))
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        store.restore(now)
        assertEquals(1, alarms.scheduledAlarms.size)
    }
    @Test fun permissionReturningWithinGracePeriodDeliversOnce() {
        val json = event().reminderJson()
        store.schedule(json, now)
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        manager.setNotificationsEnabled(false)
        val due = reminderTime(json.getString("start"))
        store.deliver(json.getString("id"), due)
        assertTrue(store.contains(json.getString("id")))
        manager.setNotificationsEnabled(true)
        store.restore(due + 1000)
        store.restore(due + 2000)
        assertEquals(1, manager.allNotifications.size)
    }
    @Test fun corruptRecordDoesNotPreventOtherRemindersFromRestoring() {
        val json = event().reminderJson()
        store.preferences.edit().putInt("wrong-type", 1).putString("bad-json", "{")
            .putString(json.getString("id"), json.toString()).commit()
        store.deliver("wrong-type", now)
        store.restore(now)
        assertEquals(setOf(json.getString("id")), store.preferences.all.keys)
        assertEquals(1, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
    }
    @Test fun newYearGlobalEventIsNotCancelledByPreviousYearRefresh() {
        val previous = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Sao_Paulo"))
            val json = event("2027-01-01T01:00:00Z", "2027-01-01T03:00:00Z").reminderJson()
            store.schedule(json, now)
            store.reconcile(emptyList(), 2026, now)
            assertTrue(store.contains(json.getString("id")))
        } finally { java.util.TimeZone.setDefault(previous) }
    }
    @Test fun backupRestoresFutureRemindersAndRemovesReplacedAlarms() {
        val original = event("2035-11-21T14:00:00", "2035-11-21T17:00:00").reminderJson()
        store.schedule(original)
        val backup = UserBackup.export(context)
        store.cancel(original.getString("id"))
        val extra = event("2035-11-28T14:00:00", "2035-11-28T17:00:00").reminderJson()
        store.schedule(extra)
        UserBackup.restore(context, backup)
        assertEquals(setOf(original.getString("id")), store.preferences.all.keys)
        assertEquals(1, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
    }
    @Test fun olderBackupWithoutRemindersLeavesChoicesAndAlarmsUntouched() {
        val json = event("2035-11-21T14:00:00", "2035-11-21T17:00:00").reminderJson()
        store.schedule(json)
        val backup = org.json.JSONObject(UserBackup.export(context))
        backup.getJSONObject("preferences").remove("calendar_reminders")
        UserBackup.restore(context, backup.toString())
        assertTrue(store.contains(json.getString("id")))
        assertEquals(1, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
    }
    @Test fun expiredBackupReminderDoesNotNotify() {
        val json = event("2020-01-01T14:00:00", "2020-01-01T17:00:00").reminderJson()
        store.preferences.edit().putString(json.getString("id"), json.toString()).commit()
        val backup = UserBackup.export(context)
        UserBackup.restore(context, backup)
        assertTrue(store.preferences.all.isEmpty())
        assertEquals(0, shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.size)
    }
    @Test fun backupKeepsFutureChoiceWithoutPermissionUntilReenabled() {
        val json = event("2035-11-21T14:00:00", "2035-11-21T17:00:00").reminderJson()
        store.schedule(json)
        val backup = UserBackup.export(context)
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        manager.setNotificationsEnabled(false)
        UserBackup.restore(context, backup)
        assertTrue(store.contains(json.getString("id")))
        assertEquals(0, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
        manager.setNotificationsEnabled(true)
        store.restore()
        assertEquals(1, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
    }
    @Test fun invalidReminderBackupIsRejectedBeforeChangingUserData() {
        val json = event().reminderJson()
        store.schedule(json, now)
        val backup = org.json.JSONObject(UserBackup.export(context))
        backup.getJSONObject("preferences").getJSONObject("calendar_reminders")
            .getJSONObject(json.getString("id")).put("value", "{}")
        assertTrue(runCatching { UserBackup.restore(context, backup.toString()) }.isFailure)
        assertTrue(store.contains(json.getString("id")))
    }

}
