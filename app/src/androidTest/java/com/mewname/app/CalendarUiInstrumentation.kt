package com.mewname.app

import android.app.Instrumentation
import android.os.Bundle
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.platform.ComposeView
import com.mewname.app.domain.CalendarRepository
import java.time.LocalDate

class CalendarUiInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun has(text: String): Boolean {
        uiAutomation.clearCache()
        return nodes(uiAutomation.rootInActiveWindow).any {
        it.text?.toString()?.contains(text) == true || it.contentDescription?.toString()?.contains(text) == true
    }
    }
    override fun onStart() {
        val result = Bundle()
        try {
            uiAutomation.serviceInfo = uiAutomation.serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
            val today = LocalDate.now()
            val repo = CalendarRepository(targetContext)
            val catalog = repo.cached(today.year) ?: repo.refresh(today.year)
            check(catalog.events.any { it.occursOn(today) })
            val activity = startActivitySync(targetContext.packageManager.getLaunchIntentForPackage(targetContext.packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fun show(compact: Boolean) {
                runOnMainSync { activity.setContentView(ComposeView(activity).apply { setContent {
                    androidx.compose.runtime.CompositionLocalProvider(LocalAppLanguage provides com.mewname.app.domain.AppLanguage.PT_BR) {
                        AppAppearanceTheme { CalendarScreen(onBack = {}, compactToday = compact) }
                    }
                } }) }
                waitForIdleSync(); Thread.sleep(1500)
            }
            show(false)
            repeat(5) { if (!has("Semana anterior")) Thread.sleep(200) }
            check(has("Semana anterior")) { "Default week view missing" }
            val dateLabel = today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, dd MMMM", java.util.Locale("pt", "BR")))
                .replaceFirstChar { it.titlecase() }
            check(has(dateLabel))
            val dateNodes = nodes(uiAutomation.rootInActiveWindow).filter { it.contentDescription?.toString()?.contains(" de ") == true }
            val before = dateNodes.map { it.contentDescription.toString() }
            val bounds = android.graphics.Rect().also { dateNodes.first().getBoundsInScreen(it) }
            val screenshot = uiAutomation.takeScreenshot()!!
            val width = screenshot.width
            screenshot.recycle()
            uiAutomation.executeShellCommand("input swipe ${width * 8 / 10} ${bounds.centerY()} ${width * 2 / 10} ${bounds.centerY()} 450").use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
            waitForIdleSync(); Thread.sleep(600)
            check(has(dateLabel)) { "Scrolling changed the selected day's events" }
            val after = nodes(uiAutomation.rootInActiveWindow).filter { it.contentDescription?.toString()?.contains(" de ") == true }
                .map { it.contentDescription.toString() }
            check(before != after) { "Day strip did not scroll" }
            show(true)
            repeat(5) { if (has("Semana anterior") || !has("Fechar")) Thread.sleep(200) }
            check(!has("Semana anterior")) { "Compact view still shows calendar grid" }
            val title = catalog.events.filter { it.occursOn(today) }.sortedBy { it.start }.first().title
            var event = nodes(uiAutomation.rootInActiveWindow).firstOrNull { it.text?.toString()?.contains(title) == true }
                ?: error("Today's event missing: $title")
            while (event != null && !event.isClickable) event = event.parent
            check(event?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
            waitForIdleSync(); Thread.sleep(800)
            repeat(5) { if (!has("Início:")) Thread.sleep(200) }
            check(has("Início:")) { "Event detail did not open in overlay window type" }
            check(has("Fechar")) { "Close icon missing" }
            val detailShot = uiAutomation.takeScreenshot()!!
            java.io.File(targetContext.filesDir, "calendar-detail-layer.png").outputStream().use {
                detailShot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            detailShot.recycle()
            result.putString("stream", "PASS: continuous day scrolling preserves selection; calendar remains behind event details.\n")
            finish(-1, result)
        } catch (failure: Exception) {
            result.putString("stream", failure.stackTraceToString()); finish(0, result)
        }
    }
}
