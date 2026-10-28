package com.mewname.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.mewname.app.model.NamingConfig
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.SpecialBackgroundType
import java.io.File
import kotlin.math.roundToInt

/**
 * Background crops the user confirmed as special by copying a nickname carrying the special background tag. They join
 * the calibration references on this device, so cards missing from the catalog are learned from use.
 */
object LearnedBackgroundStore {
    private const val TAG = "MewNameLearnedBg"
    private const val DIR = "learned-backgrounds"
    /** Matcher source names; "/calibration/" lets the closest crop name the category like the bundled ones. */
    const val SOURCE_PREFIX = "learned/calibration/"
    private const val MAX_FILES = 200
    private const val CROP_WIDTH = 320
    // Type-background reads stayed under 2.2 from the Pokémon's own type, and shadow ones under 2.9 from the shadow
    // background (POCO X7 Pro, 2026-10-28), so closer frames may not show the card at all.
    private const val MIN_OWN_TYPE_DISTANCE = 2.2
    private const val MIN_SHADOW_DISTANCE = 2.9
    // A frame this close to a reference of the same category adds nothing.
    private const val COVERED_DISTANCE = 0.9
    private val ENTRY_FILE = Regex("learned_\\d+\\.(png|webp)")

    private class Pending(val crop: Bitmap, val read: PokemonScreenData, val mergedSpecial: Boolean)

    @Volatile private var pending: Pending? = null
    @Volatile var version = 0
        private set

    /** Keeps the background of the latest read until its nickname is copied. */
    fun remember(bitmap: Bitmap, read: PokemonScreenData, merged: PokemonScreenData) {
        pending = runCatching { cropBackground(bitmap) }.getOrNull()?.let { Pending(it, read, merged.hasSpecialBackground) }
    }

    /** Saves the latest read's background when [copiedName] carries [data]'s special background tag. */
    fun onNameCopied(context: Context, data: PokemonScreenData, copiedName: String, configs: List<NamingConfig>) {
        val generator = NameGenerator()
        val tagged = configs.any { config ->
            generator.specialBackgroundSymbol(data, config)?.let { it in copiedName } == true
        }
        if (!tagged) return
        val read = pending ?: return
        val reason = rejection(read, data)
        if (reason != null) {
            Log.d(TAG, "not learned: $reason")
            return
        }
        pending = null
        val type = data.specialBackgroundType ?: SpecialBackgroundType.SPECIAL
        val appContext = context.applicationContext
        Thread { save(appContext, read.crop, type) }.start()
    }

    private fun rejection(read: Pending, data: PokemonScreenData): String? {
        if (read.read.cp != null && data.cp != null && read.read.cp != data.cp) return "another Pokémon"
        // Carried from an earlier read of the same Pokémon, this frame may show the type background instead.
        val markedInReview = !read.mergedSpecial
        if (!read.read.hasSpecialBackground && !markedInReview) return "card seen in an earlier read"
        val info = read.read.backgroundDebugInfo ?: return "no background measurements"
        val looksNormal = if (data.isShadow) {
            (info.shadowBackgroundDistance ?: 0.0) < MIN_SHADOW_DISTANCE
        } else {
            (info.ownTypeDistance ?: 0.0) < MIN_OWN_TYPE_DISTANCE
        }
        if (looksNormal) return "close to the normal background"
        val type = data.specialBackgroundType ?: SpecialBackgroundType.SPECIAL
        val nearest = info.specialReferenceName
        val nearestDistance = info.specialReferenceDistance
        if (nearest != null && nearestDistance != null && nearestDistance <= COVERED_DISTANCE &&
            SpecialBackgroundCatalog.type(nearest) == type) return "already covered by $nearest"
        return null
    }

    /**
     * App-specific external storage: still private to other apps and removed with the app, but readable over USB so
     * learned crops can be checked. Crops saved by the first build in internal storage move here.
     */
    fun root(context: Context): File {
        val root = File(context.getExternalFilesDir(null) ?: context.filesDir, DIR)
        val legacy = File(context.filesDir, DIR)
        if (legacy != root && legacy.isDirectory) {
            legacy.walkTopDown().filter { it.isFile }.forEach { file ->
                val target = File(root, file.relativeTo(legacy).path)
                target.parentFile?.mkdirs()
                if (!file.renameTo(target)) runCatching { file.copyTo(target, overwrite = true); file.delete() }
            }
            legacy.deleteRecursively()
        }
        return root
    }

    /** Learned crops as (matcher source name, file). */
    fun files(context: Context): List<Pair<String, File>> {
        return root(context).listFiles().orEmpty().filter { it.isDirectory }.flatMap { folder ->
            folder.listFiles { file -> isEntryName("${folder.name}/${file.name}") }.orEmpty().map { file ->
                "$SOURCE_PREFIX${folder.name}/${file.name}" to file
            }
        }.sortedBy { it.first }
    }

    /** Backup entries keyed by "category folder/file name". */
    fun export(context: Context): Map<String, ByteArray> =
        files(context).associate { (name, file) -> name.removePrefix(SOURCE_PREFIX) to file.readBytes() }

    fun isEntryName(name: String): Boolean {
        val folder = name.substringBefore('/', "")
        return folder in SpecialBackgroundType.entries.map(SpecialBackgroundCatalog::folder) &&
            ENTRY_FILE.matches(name.substringAfter('/'))
    }

    /** Replaces every learned crop with [entries], as restored from a backup. */
    @Synchronized fun replaceAll(context: Context, entries: Map<String, ByteArray>) {
        require(entries.keys.all(::isEntryName))
        val root = root(context)
        val staging = File(root.parentFile, "$DIR.restore").apply { deleteRecursively() }
        entries.forEach { (name, bytes) -> File(staging, name).apply { parentFile?.mkdirs() }.writeBytes(bytes) }
        root.deleteRecursively()
        if (entries.isNotEmpty()) check(staging.renameTo(root)) { "Could not restore learned backgrounds" }
        staging.deleteRecursively()
        pending = null
        version++
    }

    private fun save(context: Context, crop: Bitmap, type: SpecialBackgroundType) {
        runCatching {
            val folder = File(root(context), SpecialBackgroundCatalog.folder(type)).apply { mkdirs() }
            val file = File(folder, "learned_${System.currentTimeMillis()}.webp")
            val temp = File(file.path + ".tmp")
            // ~20 KB instead of ~120 KB as PNG, so 200 crops fit the user backup; the matcher only keeps 14x14.
            @Suppress("DEPRECATION")
            val format = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            temp.outputStream().use { check(crop.compress(format, 85, it)) }
            check(temp.renameTo(file))
            files(context).map { it.second }.sortedBy { it.lastModified() }.dropLast(MAX_FILES).forEach { it.delete() }
            version++
            Log.d(TAG, "learned ${file.parentFile?.name}/${file.name}")
        }.onFailure { Log.w(TAG, "could not save learned background", it) }
    }

    /** Same region the matcher compares, scaled down: its signature is only 14x14. */
    private fun cropBackground(bitmap: Bitmap): Bitmap {
        val left = (bitmap.width * 0.06f).roundToInt()
        val top = (bitmap.height * 0.06f).roundToInt()
        val width = (bitmap.width * 0.80f).roundToInt() - left
        val height = (bitmap.height * 0.33f).roundToInt() - top
        val region = Bitmap.createBitmap(bitmap, left, top, width, height)
        val scaled = Bitmap.createScaledBitmap(region, CROP_WIDTH, (height * CROP_WIDTH / width.toFloat()).roundToInt(), true)
        if (scaled != region) region.recycle()
        return scaled
    }
}
