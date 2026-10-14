package com.mewname.app

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import java.io.File

class SavedFiltersStyleInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    private fun nodes(n: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if (n == null) emptyList() else listOf(n) + (0 until n.childCount).flatMap { nodes(n.getChild(it)) }
    override fun onStart() {
        val prefs = targetContext.getSharedPreferences("home_layout", 0)
        val previousStyle = prefs.getString("style", null)
        val result = Bundle()
        try {
            val activity = startActivitySync(targetContext.packageManager.getLaunchIntentForPackage(targetContext.packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val filters = listOf(SavedFilterEntry("long", "!favorito&!lendário&!mítico&!ultracriatura&!brilhante&!sombroso&0*,1*,2*")) +
                (1..25).map { SavedFilterEntry("$it", "idade$it&3*&!favorito") }
            var copied: String? = null
            var closed = false
            fun show(style: String, empty: Boolean = false, friends: Boolean = false) {
                prefs.edit().putString("style", style).commit()
                runOnMainSync {
                    activity.setContentView(ComposeView(activity).apply { setContent {
                        androidx.compose.runtime.CompositionLocalProvider(LocalAppLanguage provides com.mewname.app.domain.AppLanguage.PT_BR) {
                            AppAppearanceTheme {
                                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                    SavedFiltersOverlay(friends, if (empty) emptyList() else filters,
                                        (activity.resources.displayMetrics.heightPixels / activity.resources.displayMetrics.density * .7f).dp,
                                        onCopy = { copied = it }, onClose = { closed = true })
                                }
                            }
                        }
                    } })
                }
                waitForIdleSync(); Thread.sleep(800)
                uiAutomation.clearCache()
            }
            fun click(text: String) {
                var node = nodes(uiAutomation.rootInActiveWindow).first { it.text?.toString() == text || it.contentDescription?.toString() == text }
                while (!node.isClickable) node = node.parent ?: error("No clickable parent")
                check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                waitForIdleSync()
            }
            val out = StringBuilder()
            for (style in listOf("GLASS", "DARK", "LIGHT")) {
                show(style)
                val deadline = android.os.SystemClock.elapsedRealtime() + 10000
                while (android.os.SystemClock.elapsedRealtime() < deadline) {
                    uiAutomation.clearCache()
                    if (nodes(uiAutomation.rootInActiveWindow).any { it.text?.toString() == filters.first().value }) break
                    Thread.sleep(200)
                }
                val shot = uiAutomation.takeScreenshot()!!
                File(targetContext.cacheDir, "filters-$style.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                shot.recycle()
                check(nodes(uiAutomation.rootInActiveWindow).any { it.isScrollable }) { "No scrollable filter list: " + nodes(uiAutomation.rootInActiveWindow).map { it.text } }
                click(filters.first().value)
                check(copied == filters.first().value)
                copied = null
                click("Fechar"); check(closed); closed = false
                out.appendLine("PASS $style: long filter, scrolling list, copy and close")
            }
            show("GLASS", empty = true, friends = true)
            check(nodes(uiAutomation.rootInActiveWindow).any { it.text?.toString() == "Nenhum filtro salvo" })
            check(nodes(uiAutomation.rootInActiveWindow).any { it.text?.toString() == "Filtros de amigos" })
            out.appendLine("PASS friends empty state")
            result.putString("stream", out.toString())
        } catch (e: Exception) { result.putString("stream", e.stackTraceToString()); result.putBoolean("failed", true) }
        finally {
            prefs.edit().apply { if (previousStyle == null) remove("style") else putString("style", previousStyle) }.commit()
        }
        finish(if (result.getBoolean("failed")) 0 else -1, result)
    }
}