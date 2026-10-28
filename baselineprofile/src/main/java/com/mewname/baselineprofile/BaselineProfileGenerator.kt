package com.mewname.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Collects the code used to start the app and open the main screens, so release builds ship it
 * precompiled. Run on a device with the app already set up; it never clears app data.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        HOME_SCREENS.forEach { label -> openAndReturn(label) }
        openPokedexDetail()
    }

    private fun MacrobenchmarkScope.openAndReturn(label: String) {
        val item = device.wait(Until.findObject(By.textStartsWith(label)), WAIT_MS) ?: return
        item.click()
        device.waitForIdle()
        Thread.sleep(SETTLE_MS)
        device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
        device.waitForIdle()
        device.pressBack()
        device.wait(Until.hasObject(By.textStartsWith(HOME_SCREENS.first())), WAIT_MS)
    }

    private fun MacrobenchmarkScope.openPokedexDetail() {
        device.wait(Until.findObject(By.text("Pokedex")), WAIT_MS)?.click() ?: return
        device.waitForIdle()
        Thread.sleep(SETTLE_MS)
        device.click((device.displayWidth * .18f).toInt(), (device.displayHeight * .26f).toInt())
        device.waitForIdle()
        Thread.sleep(SETTLE_MS)
        device.pressBack()
        device.waitForIdle()
        device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
        device.waitForIdle()
        device.pressBack()
    }

    private companion object {
        const val TARGET_PACKAGE = "com.mewname.app"
        const val WAIT_MS = 5_000L
        const val SETTLE_MS = 1_500L

        // Portuguese labels of the home grid; the device under test uses the app in Portuguese.
        val HOME_SCREENS = listOf(
            "Calendário", "Definir nomes", "Coleções", "Raids", "Equipe GO Rocket",
            "Análise de Equipe PvP", "Missões diárias", "Pesquisas de Campo", "Ovos",
            "Efeitos de Aventura", "Ataques Legados", "Ataques rápidos", "Codigos Promocionais", "Tipos"
        )
    }
}
