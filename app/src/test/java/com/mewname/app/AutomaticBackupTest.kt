package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomaticBackupTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private class MemoryFolder : BackupFolder {
        val files = linkedMapOf<String, String>()
        var failWrite = false
        var failDelete = false
        private var sequence = 0
        override fun snapshots() = files.keys.toList()
        override fun create() = "copy-${sequence++}".also { files[it] = "" }
        override fun read(id: String) = files.getValue(id)
        override fun write(id: String, text: String) { check(!failWrite); files[id] = text }
        override fun delete(id: String) { check(!failDelete); files.remove(id) }
    }
    @Test fun replacementKeepsOneValidRestorableCopy() {
        val store = MemoryFolder()
        val prefs = context.getSharedPreferences("daily_missions", 0)
        prefs.edit().putString("test", "old").commit()
        AutomaticBackup.replace(store, UserBackup.export(context))
        prefs.edit().putString("test", "new").commit()
        AutomaticBackup.replace(store, UserBackup.export(context))
        assertEquals(1, store.files.size)
        val text = AutomaticBackup.latest(store)!!
        prefs.edit().clear().commit()
        UserBackup.restore(context, text)
        assertEquals("new", prefs.getString("test", null))
    }
    @Test fun failedWritePreservesOldBackup() {
        val store = MemoryFolder()
        val text = UserBackup.export(context)
        AutomaticBackup.replace(store, text)
        store.failWrite = true
        assertTrue(runCatching { AutomaticBackup.replace(store, text) }.isFailure)
        assertEquals(listOf(text), store.files.values.toList())
    }
    @Test fun invalidSnapshotNeverDeletesPreviousCopy() {
        val store = MemoryFolder()
        AutomaticBackup.replace(store, UserBackup.export(context))
        val before = store.files.toMap()
        assertTrue(runCatching { AutomaticBackup.replace(store, "invalid") }.isFailure)
        assertEquals(before, store.files)
    }
    @Test fun failedCleanupKeepsRecoverableData() {
        val store = MemoryFolder()
        val text = UserBackup.export(context)
        AutomaticBackup.replace(store, text)
        store.failDelete = true
        // The verified new copy counts as success; the stale one stays until a later run can remove it.
        AutomaticBackup.replace(store, text)
        assertNotNull(AutomaticBackup.latest(store))
        assertEquals(2, store.files.size)
        store.failDelete = false
        AutomaticBackup.replace(store, text)
        assertEquals(1, store.files.size)
    }
    @Test fun freshInstallDoesNotScheduleUntilFolderIsChosen() {
        context.getSharedPreferences("automatic_backup", 0).edit().clear().commit()
        assertFalse(AutomaticBackup.enabled(context))
        assertFalse(AutomaticBackup.writeIfDue(context))
        assertTrue(AutomaticBackup.due(1000, 1000 + AutomaticBackup.DAY))
        assertFalse(AutomaticBackup.due(1000, 1001))
    }
}
