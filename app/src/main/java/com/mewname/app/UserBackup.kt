package com.mewname.app

import android.content.Context
import android.util.Base64
import com.mewname.app.domain.LearnedBackgroundStore
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

/** Portable user data only: caches and OCR diagnostics are not exported. */
internal object UserBackup {
    private val stores = setOf("mewname_prefs", "personal_collection_v1", "daily_missions",
        "promo_code_redemptions", "trainer_profile", "home_layout", "bubble_actions",
        "pvp_calculation", "raid_counter_preferences")
    private val pictures = setOf("profile-avatar.png", "profile-qr.png")
    const val MAX_BYTES = 32 * 1024 * 1024
    private const val LEARNED_BACKGROUNDS = "learnedBackgrounds"

    fun export(context: Context): String {
        val data = JSONObject()
        stores.forEach { store ->
            val values = JSONObject()
            context.getSharedPreferences(store, 0).all.forEach { (key, value) ->
                if (!key.contains("log", true) && value != null) {
                    val type = when (value) {
                        is String -> "string"; is Boolean -> "boolean"; is Int -> "int"
                        is Long -> "long"; is Float -> "float"; is Set<*> -> "set"
                        else -> error("Unsupported preference")
                    }
                    values.put(key, JSONObject().put("type", type).put("value",
                        if (value is Set<*>) JSONArray(value.toList()) else value))
                }
            }
            data.put(store, values)
        }
        val images = JSONObject()
        pictures.forEach { name ->
            val file = File(context.filesDir, name)
            if (file.isFile) images.put(name, Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
        }
        val learned = JSONObject()
        LearnedBackgroundStore.export(context).forEach { (name, bytes) ->
            learned.put(name, Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
        // Optional section: older versions ignore it, and backups without it keep the crops already learned.
        return JSONObject().put("app", "MewName").put("version", 1)
            .put("createdAt", System.currentTimeMillis()).put("preferences", data).put("pictures", images)
            .put(LEARNED_BACKGROUNDS, learned).toString()
            .also { require(it.toByteArray().size <= MAX_BYTES) { "Backup too large" } }
    }

    fun writeDocument(context: Context, uri: android.net.Uri): Int {
        val text = export(context)
        validate(text)
        val bytes = text.toByteArray(Charsets.UTF_8)
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { output ->
            output.write(bytes)
            output.flush()
        }
        // Verify actual content rather than trusting a provider's cached size column.
        check(readDocument(context, uri) == text) { "Backup verification failed" }
        return bytes.size
    }

    fun readDocument(context: Context, uri: android.net.Uri): String {
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val count = input.read(chunk)
                if (count < 0) break
                require(buffer.size() + count <= MAX_BYTES) { "Backup too large" }
                buffer.write(chunk, 0, count)
            }
            buffer.toByteArray()
        }
        return bytes.toString(Charsets.UTF_8).also(::validate)
    }

    private data class Decoded(
        val preferences: Map<String, Map<String, Any>>,
        val pictures: Map<String, ByteArray>,
        /** Null for backups made before learned backgrounds existed. */
        val learnedBackgrounds: Map<String, ByteArray>?
    )

    private fun requireImage(bytes: ByteArray, maxSide: Int) {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth in 1..maxSide && options.outHeight in 1..maxSide)
    }
    private fun decode(text: String): Decoded {
        require(text.toByteArray().size <= MAX_BYTES)
        val root = JSONObject(text)
        require(root.getString("app") == "MewName" && root.getInt("version") == 1)
        val data = root.getJSONObject("preferences")
        require(data.keys().asSequence().toSet() == stores) { "Incomplete backup" }
        val prefs = stores.associateWith { name ->
            val obj = data.getJSONObject(name)
            obj.keys().asSequence().associateWith { key ->
                val entry = obj.getJSONObject(key)
                val value = entry.get("value")
                when (entry.getString("type")) {
                    "string" -> { require(value is String); value }
                    "boolean" -> { require(value is Boolean); value }
                    "int" -> { require(value is Number && value.toLong() in Int.MIN_VALUE..Int.MAX_VALUE); value.toInt() }
                    "long" -> { require(value is Number); value.toLong() }
                    "float" -> { require(value is Number && value.toFloat().isFinite()); value.toFloat() }
                    "set" -> { require(value is JSONArray); (0 until value.length()).map {
                        val item = value.get(it); require(item is String); item
                    }.toSet() }
                    else -> error("Unknown preference type")
                }
            }
        }
        val imageData = root.getJSONObject("pictures")
        val images = imageData.keys().asSequence().associateWith { name ->
            require(name in pictures)
            Base64.decode(imageData.getString(name), Base64.DEFAULT).also {
                require(it.size <= 8 * 1024 * 1024)
                requireImage(it, 8192)
            }
        }
        val learned = root.optJSONObject(LEARNED_BACKGROUNDS)?.let { section ->
            section.keys().asSequence().associateWith { name ->
                require(LearnedBackgroundStore.isEntryName(name)) { "Invalid learned background" }
                Base64.decode(section.getString(name), Base64.DEFAULT).also {
                    require(it.size <= 1024 * 1024)
                    requireImage(it, 2048)
                }
            }
        }
        return Decoded(prefs, images, learned)
    }

    fun validate(text: String) { decode(text) }

    @Synchronized fun restore(context: Context, text: String) {
        val incoming = decode(text) // Fully validate before changing anything.
        val previous = decode(export(context))
        try { apply(context, incoming) }
        catch (error: Exception) {
            runCatching { apply(context, previous) }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

    private fun apply(context: Context, data: Decoded) {
        data.preferences.forEach { (name, values) ->
            val prefs = context.getSharedPreferences(name, 0)
            // Preserve diagnostics that are intentionally outside the backup.
            val logs = prefs.all.filterKeys { it.contains("log", true) }
            val editor = prefs.edit().clear()
            (values + logs).forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            check(editor.commit()) { "Could not save preferences" }
        }
        pictures.forEach { name ->
            val file = File(context.filesDir, name)
            val bytes = data.pictures[name]
            if (bytes == null) { check(!file.exists() || file.delete()) }
            else {
                val atomic = android.util.AtomicFile(file)
                val output = atomic.startWrite()
                try { output.write(bytes); atomic.finishWrite(output) }
                catch (error: Exception) { atomic.failWrite(output); throw error }
            }
        }
        data.learnedBackgrounds?.let { LearnedBackgroundStore.replaceAll(context, it) }
    }
}
