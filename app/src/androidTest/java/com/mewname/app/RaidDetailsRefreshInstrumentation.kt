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
class RaidDetailsRefreshInstrumentation : Instrumentation() {
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
        val catalog = File(targetContext.filesDir, "raid-counters-v1/catalog.json")
        val originalCatalog = catalog.takeIf { it.exists() }?.readBytes()
        val results = StringBuilder()
        var resultCode = 0
        try {
            val activity = startActivitySync(targetContext.packageManager.getLaunchIntentForPackage(targetContext.packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fun show(boss: String, overlay: Boolean, level: Int? = null) {
                runOnMainSync {
                    activity.setContentView(ComposeView(activity).apply { setContent {
                        androidx.compose.runtime.CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PT_BR) {
                            AppAppearanceTheme { RaidDetailsScreen(boss, bubble = overlay, detectedRaidLevel = level, onBack = {}) }
                        }
                    } })
                }
                waitForIdleSync()
            }
            fun ready(label: String) {
                val end = android.os.SystemClock.elapsedRealtime() + 60000
                while (android.os.SystemClock.elapsedRealtime() < end) {
                    var node = find(label)
                    while (node != null && !node.isClickable) node = node.parent
                    if (node?.isEnabled == true) return
                    Thread.sleep(200)
                }
                error("Refresh did not become enabled: $label")
            }
            for (overlay in listOf(true, false)) {
                // Simulate an empty current catalog; restore the original bytes in finally.
                catalog.parentFile!!.mkdirs()
                catalog.writeText("{}")
                show("", overlay, 5)
                await("Não foi possível identificar o chefe nem encontrar raids atuais deste nível.")
                ready("Atualizar raids disponíveis")
                val button = await("Atualizar raids disponíveis")
                val bounds = android.graphics.Rect().also { button.getBoundsInScreen(it) }
                check(bounds.centerX() > activity.resources.displayMetrics.widthPixels / 2)
                click("Atualizar raids disponíveis")
                val deadline = android.os.SystemClock.elapsedRealtime() + 60000
                while (catalog.readText() == "{}" && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(200)
                check(catalog.readText() != "{}") { "Empty catalog was not refreshed" }
                ready("Atualizar raids disponíveis")
                await("Selecionar raid")
                check(RaidCounterRepository(targetContext).catalog().isNotEmpty())
                results.appendLine("PASS overlay=$overlay: empty catalog recovered online; top-right refresh; choices available")
            }
            for (overlay in listOf(true, false)) {
                show("YVELTAL", overlay)
                await("Movimentos")
                ready("Atualizar")
                check(find("Golpes possíveis do chefe") == null)
                check(find("IVs: 10/10/10 e 15/15/15") == null)
                check(find("Atualizar esta raid") == null)
                val repo = RaidCounterRepository(targetContext)
                val before = repo.cached("YVELTAL", "RAID_LEVEL_5", repo.initialLevel("YVELTAL", "RAID_LEVEL_5"))?.fetchedAt ?: 0L
                click("Atualizar")
                Thread.sleep(500)
                ready("Atualizar")
                val after = repo.cached("YVELTAL", "RAID_LEVEL_5", repo.initialLevel("YVELTAL", "RAID_LEVEL_5"))?.fetchedAt ?: 0L
                check(after > before) { "Known raid report was not refreshed" }
                uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(targetContext.cacheDir, "raid-details-refresh-$overlay.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
                results.appendLine("PASS overlay=$overlay: known boss refreshed online from top icon; revised title and no footer refresh")
            }
            output.putString("stream", results.toString())
            resultCode = -1
        } catch (error: Exception) {
            output.putString("stream", results.toString() + error.stackTraceToString())

        } finally {
            if (originalCatalog != null) {
                catalog.writeBytes(originalCatalog)
                check(catalog.readBytes().contentEquals(originalCatalog))
            } else catalog.delete()
        }
        finish(resultCode, output)
    }
}
