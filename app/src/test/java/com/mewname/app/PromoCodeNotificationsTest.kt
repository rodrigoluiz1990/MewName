package com.mewname.app

import android.app.NotificationManager
import android.app.job.JobScheduler
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PromoCodeNotificationsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = PromoCodeNotifications(context)
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private fun entry(code: String, expired: Boolean = false, expires: Long? = null) =
        LeekEntry(code, "Reward", "Codes", code = code, expired = expired, expires = expires)
    private fun snapshot(vararg entries: LeekEntry) = LeekCatalog(entries.toList(), "", 100)
    @Before fun clean() {
        store.preferences.edit().clear().commit()
        PromoCodeRedemptions(context).preferences.edit().clear().commit()
        manager.cancelAll()
        shadowOf(manager).setNotificationsEnabled(true)
        context.getSystemService(JobScheduler::class.java).cancelAll()
        store.setEnabled(true)
    }
    @Test fun firstSnapshotSeedsWithoutAlertAndNextOnlyNotifiesNewCodes() {
        store.accept(snapshot(entry("OLD")), 100)
        assertEquals(0, shadowOf(manager).allNotifications.size)
        store.accept(snapshot(entry("OLD"), entry("NEW")), 100)
        assertEquals(1, shadowOf(manager).allNotifications.size)
        val notification = shadowOf(manager).allNotifications.single()
        assertEquals("NEW", notification.extras.getString("android.text"))
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals("PROMO_CODES", intent.getStringExtra("BUBBLE_DESTINATION"))
        manager.cancelAll()
        store.accept(snapshot(entry(" new "), entry("old")), 100)
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }
    @Test fun excludesExpiredRedeemedAndDuplicateCodes() {
        val result = newPromoCodes(listOf(entry("A", expired = true), entry("B", expires = 100),
            entry("C"), entry("D"), entry(" d "), entry(" "), entry("KNOWN")), setOf("KNOWN"), setOf("C"), 100)
        assertEquals(listOf("D"), result.map { it.code })
    }
    @Test fun disappearingCodeDoesNotBecomeNewWhenItReturns() {
        store.accept(snapshot(entry("A")), 100)
        store.accept(snapshot(entry("B")), 100)
        manager.cancelAll()
        store.accept(snapshot(entry("A"), entry("B")), 100)
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }
    @Test fun blockedNotificationIsRetriedWithoutLosingTheNewCode() {
        store.accept(snapshot(entry("A")), 100)
        shadowOf(manager).setNotificationsEnabled(false)
        store.accept(snapshot(entry("B")), 100)
        assertFalse("B" in store.preferences.getStringSet("known", emptySet()).orEmpty())
        shadowOf(manager).setNotificationsEnabled(true)
        store.accept(snapshot(entry("B")), 100)
        assertEquals(1, shadowOf(manager).allNotifications.size)
    }
    @Test fun disablingCancelsJobAndStopsNotifications() {
        store.accept(snapshot(entry("A")), 100)
        store.setEnabled(false)
        store.accept(snapshot(entry("B")), 100)
        assertNull(context.getSystemService(JobScheduler::class.java).getPendingJob(PromoCodeNotifications.JOB_ID))
        assertEquals(0, shadowOf(manager).allNotifications.size)
        store.setEnabled(true)
        store.accept(snapshot(entry("B")), 100)
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }
    @Test fun periodicJobIsPersistentNetworkBoundAndNotDuplicated() {
        store.schedule()
        val jobs = context.getSystemService(JobScheduler::class.java).allPendingJobs
        assertEquals(1, jobs.size)
        assertTrue(jobs.single().isPersisted)
        assertEquals(86400000L, jobs.single().intervalMillis)
        assertEquals(android.app.job.JobInfo.NETWORK_TYPE_ANY, jobs.single().networkType)
    }
    @Test fun replacesPreviousThreeHourSchedule() {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        scheduler.schedule(android.app.job.JobInfo.Builder(PromoCodeNotifications.JOB_ID,
            android.content.ComponentName(context, PromoCodeNotificationService::class.java))
            .setPeriodic(10800000L).build())
        store.schedule()
        assertEquals(86400000L, scheduler.getPendingJob(PromoCodeNotifications.JOB_ID)!!.intervalMillis)
        assertEquals(1, scheduler.allPendingJobs.size)
    }
    @Test fun redeemedCodeDoesNotNotify() {
        store.accept(snapshot(entry("A")), 100)
        PromoCodeRedemptions(context).setRedeemed("B", true)
        store.accept(snapshot(entry("B")), 100)
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }
}
