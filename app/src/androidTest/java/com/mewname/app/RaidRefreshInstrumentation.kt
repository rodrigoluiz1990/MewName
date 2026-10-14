package com.mewname.app

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.platform.ComposeView
import com.mewname.app.domain.AppLanguage
import com.mewname.app.domain.RaidCounterRepository
import java.io.File

/** Exercises the real details composable on a connected device with an unidentified boss. */
class RaidRefreshInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun find(label: String): AccessibilityNodeInfo? {
        uiAutomation.clearCache()
        return nodes(uiAutomation.rootInActiveWindow).firstOrNull {
            it.text?.toString() == label || it.contentDescription?.toString() == label
        }
    }
    private fun await(label: String): AccessibilityNodeInfo {
        val deadline = android.os.SystemClock.elapsedRealtime() + 45000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            find(label)?.let { return it }
            Thread.sleep(200)
        }
        error("UI did not show: $label")
    }
    private fun click(label: String) {
        var node: AccessibilityNodeInfo? = await(label)
        while (node != null && !node.isClickable) node = node.parent
        check(node?.isEnabled == true && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Cannot click $label" }
    }
    override fun onStart() {
        val output = Bundle()
        try {
            val activity = startActivitySync(targetContext.packageManager.getLaunchIntentForPackage(targetContext.packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val catalog = File(targetContext.filesDir, "raid-counters-v1/catalog.json")
            val results = StringBuilder()
            for (overlay in listOf(true, false)) {
                runOnMainSync {
                    activity.setContentView(ComposeView(activity).apply { setContent {
                        androidx.compose.runtime.CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PT_BR) {
                            AppAppearanceTheme { RaidDetailsScreen("", bubble = overlay, onBack = {}) }
                        }
                    } })
                }
                waitForIdleSync()
                await("Atualizar raids disponíveis")
                Thread.sleep(500)
                val before = catalog.lastModified()
                val button = await("Atualizar raids disponíveis")
                val bounds = android.graphics.Rect().also { button.getBoundsInScreen(it) }
                check(bounds.centerX() > activity.resources.displayMetrics.widthPixels / 2) { "Refresh is not on the right" }
                click("Atualizar raids disponíveis")
                val deadline = android.os.SystemClock.elapsedRealtime() + 45000
                while (catalog.lastModified() <= before && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(200)
                check(catalog.lastModified() > before) { "Catalog was not updated over the network" }
                await("Atualizar raids disponíveis")
                await("Selecionar raid")
                val choices = RaidCounterRepository(targetContext).catalog()
                check(choices.isNotEmpty())
                val shot = uiAutomation.takeScreenshot()!!
                File(targetContext.cacheDir, "raid-refresh-$overlay.png").outputStream().use {
                    shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                shot.recycle()
                click("Selecionar raid")
                Thread.sleep(500)
                check(nodes(uiAutomation.rootInActiveWindow).count { it.text != null } > 3) { "Raid selector did not show options" }
                results.appendLine("PASS overlay=$overlay: refresh on right; online catalog updated (${choices.size} choices); selector usable")
            }
            kotlinx.coroutines.runBlocking {
                com.mewname.app.ocr.OcrEngine().use { engine ->
                    val fixture = File(targetContext.cacheDir, "raid-test-ponyta.jpg")
                    val read = engine.extract(targetContext, android.net.Uri.fromFile(fixture))
                    try {
                        val data = com.mewname.app.domain.OcrPokemonParser().parse(targetContext, read)
                        check(data.pokemonName == "Ponyta" && data.cp == 1521)
                        check(data.attIv == 12 && data.defIv == 14 && data.staIv == 15)
                        check(data.isFavorite && data.isLucky)
                        results.appendLine("PASS device OCR: EXIF Ponyta CP1521 IV12/14/15 favorite/lucky")
                    } finally { read.bitmap?.recycle() }
                }
            }
            output.putString("stream", results.toString())
            finish(-1, output)
        } catch (error: Exception) {
            output.putString("stream", error.stackTraceToString())
            finish(0, output)
        }
    }
}