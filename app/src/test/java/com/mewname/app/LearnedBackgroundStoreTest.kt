package com.mewname.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mewname.app.domain.BackgroundReferenceMatcher
import com.mewname.app.domain.LearnedBackgroundStore
import com.mewname.app.domain.NameGenerator
import com.mewname.app.model.BackgroundDebugInfo
import com.mewname.app.model.NamingConfig
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.SpecialBackgroundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Copying a nickname with the special background tag teaches this device the card of that read. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LearnedBackgroundStoreTest {
    private val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private val config = NamingConfig()
    private val special = PokemonScreenData(pokemonName = "Kyurem", candyFamilyName = "Kyurem", cp = 2013,
        hasSpecialBackground = true, specialBackgroundType = SpecialBackgroundType.GO_TOUR,
        backgroundDebugInfo = BackgroundDebugInfo(ownTypeDistance = 3.1, specialReferenceDistance = 2.0,
            specialReferenceName = "background_refs/location_cards/go_tour/sb_GoTour2025_black.png"))

    @Before fun clearStore() {
        LearnedBackgroundStore.root(context).deleteRecursively()
    }

    private fun capture(name: String = "kyurem-special.jpg"): Bitmap =
        javaClass.getResourceAsStream("/ocr/backgrounds/$name")!!.use { BitmapFactory.decodeStream(it) }

    private fun copy(read: PokemonScreenData, merged: PokemonScreenData = read, copied: PokemonScreenData = merged,
                     name: String = NameGenerator().generate(copied, config)) {
        LearnedBackgroundStore.remember(capture(), read, merged)
        LearnedBackgroundStore.onNameCopied(context, copied, name, listOf(config))
    }

    private fun learned(waitMillis: Long = 0): List<String> {
        val deadline = System.currentTimeMillis() + waitMillis
        while (true) {
            val files = LearnedBackgroundStore.files(context).map { it.first }
            if (files.isNotEmpty() || System.currentTimeMillis() >= deadline) return files
            Thread.sleep(20)
        }
    }

    @Test fun taggedCopyLearnsTheCardInItsCategory() {
        copy(special)
        val files = learned(waitMillis = 3000)
        assertEquals(1, files.size)
        assertTrue(files.single(), files.single().startsWith("${LearnedBackgroundStore.SOURCE_PREFIX}go_tour/"))
    }

    @Test fun learnedCropBecomesTheClosestReference() {
        copy(special)
        learned(waitMillis = 3000)
        val bitmap = capture()
        val result = BackgroundReferenceMatcher().debugSpecialBackground(context, bitmap)
        assertTrue(result.bestSpecialReferenceName!!, result.bestSpecialReferenceName!!.startsWith(LearnedBackgroundStore.SOURCE_PREFIX))
        assertTrue("${result.bestSpecialDistance}", result.bestSpecialDistance!! < 1.0)
    }

    @Test fun nameWithoutTheTagLearnsNothing() {
        copy(special, name = "Kyurem")
        Thread.sleep(300)
        assertEquals(emptyList<String>(), learned())
    }

    @Test fun frameCloseToTheOwnTypeBackgroundIsNotLearned() {
        val nearType = special.copy(backgroundDebugInfo = special.backgroundDebugInfo!!.copy(ownTypeDistance = 1.7))
        copy(nearType)
        Thread.sleep(300)
        assertEquals(emptyList<String>(), learned())
    }

    @Test fun cardCarriedFromAnEarlierReadIsNotLearned() {
        // This read saw the type background; the card came from the previous read of the same Pokémon.
        val read = special.copy(hasSpecialBackground = false, specialBackgroundType = null)
        copy(read, merged = special)
        Thread.sleep(300)
        assertEquals(emptyList<String>(), learned())
    }

    @Test fun cropsFromInternalStorageMoveToTheReadableFolder() {
        val legacy = File(context.filesDir, "learned-backgrounds/go_fest/learned_1.png")
        legacy.parentFile!!.mkdirs()
        legacy.outputStream().use { capture().compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertEquals(listOf("${LearnedBackgroundStore.SOURCE_PREFIX}go_fest/learned_1.png"), learned())
        assertTrue(!legacy.exists())
    }

    @Test fun restoreInterruptedAfterMovingCropsAsideRecoversThem() {
        LearnedBackgroundStore.replaceAll(context, mapOf("go_fest/learned_1.png" to java.io.ByteArrayOutputStream().also {
            capture().compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()))
        val root = LearnedBackgroundStore.root(context)
        // State left by a process death between moving the folder aside and putting the new one in place.
        assertTrue(root.renameTo(File(root.parentFile, root.name + ".old")))
        assertEquals(listOf("${LearnedBackgroundStore.SOURCE_PREFIX}go_fest/learned_1.png"), learned())
    }

    @Test fun cardMarkedInReviewIsLearned() {
        // The app missed the card and the user marked it before copying.
        val read = special.copy(hasSpecialBackground = false, specialBackgroundType = null)
        copy(read, merged = read, copied = special)
        assertEquals(1, learned(waitMillis = 3000).size)
    }
}
