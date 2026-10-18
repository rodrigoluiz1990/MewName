package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun roundTripPreservesTypesAndReplacesUserData() {
        val prefs = context.getSharedPreferences("mewname_prefs", 0)
        prefs.edit().clear().putString("name", "Treinador").putInt("count", 42)
            .putLong("time", 9000000000L).putBoolean("enabled", true)
            .putFloat("scale", 1.25f).putStringSet("choices", setOf("a", "b")).commit()
        val codes = PromoCodeRedemptions(context)
        codes.setRedeemed("TEST", true)
        val backup = UserBackup.export(context)
        prefs.edit().clear().putString("extra", "remove me").commit()
        codes.setRedeemed("TEST", false)
        UserBackup.restore(context, backup)
        assertEquals("Treinador", prefs.getString("name", null))
        assertEquals(42, prefs.getInt("count", 0))
        assertEquals(9000000000L, prefs.getLong("time", 0))
        assertTrue(prefs.getBoolean("enabled", false))
        assertEquals(1.25f, prefs.getFloat("scale", 0f), 0f)
        assertEquals(setOf("a", "b"), prefs.getStringSet("choices", null))
        assertFalse(prefs.contains("extra"))
        assertTrue(codes.isRedeemed("TEST"))
    }
    @Test fun invalidBackupDoesNotChangeData() {
        val prefs = context.getSharedPreferences("daily_missions", 0)
        prefs.edit().putString("missions", "original").commit()
        val backup = JSONObject(UserBackup.export(context))
        backup.getJSONObject("preferences").getJSONObject("daily_missions")
            .getJSONObject("missions").put("type", "unknown")
        assertTrue(runCatching { UserBackup.restore(context, backup.toString()) }.isFailure)
        assertEquals("original", prefs.getString("missions", null))
    }
    @Test fun rejectsUnknownVersionAndFilePathsBeforeWriting() {
        val backup = JSONObject(UserBackup.export(context))
        backup.put("version", 99)
        assertTrue(runCatching { UserBackup.validate(backup.toString()) }.isFailure)
        backup.put("version", 1).getJSONObject("pictures").put("../outside.png", "AAAA")
        assertTrue(runCatching { UserBackup.validate(backup.toString()) }.isFailure)
    }
    @Test fun documentRoundTripVerifiesWrittenContent() {
        val file = java.io.File(context.cacheDir, "backup-test.json")
        val uri = android.net.Uri.fromFile(file)
        context.getSharedPreferences("daily_missions", 0).edit().putString("missions", "saved missions").commit()
        val bytes = UserBackup.writeDocument(context, uri)
        assertEquals(file.length(), bytes.toLong())
        val text = UserBackup.readDocument(context, uri)
        context.getSharedPreferences("daily_missions", 0).edit().putString("missions", "changed").commit()
        UserBackup.restore(context, text)
        assertEquals("saved missions", context.getSharedPreferences("daily_missions", 0).getString("missions", null))
    }
    @Test fun emptyDocumentIsRejectedBeforeRestore() {
        val file = java.io.File(context.cacheDir, "empty-backup.json").apply { writeText("") }
        assertTrue(runCatching { UserBackup.readDocument(context, android.net.Uri.fromFile(file)) }.isFailure)
    }
}
