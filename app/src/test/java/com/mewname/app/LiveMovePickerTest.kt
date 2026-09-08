package com.mewname.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.junit.Assert.*
import org.junit.Test

class LiveMovePickerTest {
    @Test
    fun `opening while loading observes completion and uses the new selection callback`() {
        var selected: String? = null
        val state = mutableStateOf(ReviewOptionPicker("Ataque carregado", emptyList(), null, "Carregando ataques...") {
            error("Loading callback must not be retained")
        })
        val opened = liveReviewOptionPicker(state)
        assertEquals("Carregando ataques...", opened.current().message)
        var invalidated = false
        val observer = SnapshotStateObserver { it() }
        observer.start()
        try {
            observer.observeReads(Any(), { _: Any -> invalidated = true }) { opened.current() }
            state.value = ReviewOptionPicker("Ataque carregado", listOf("Espada Colossal [L]"), null) {
                selected = if (it == "Espada Colossal [L]") "Behemoth Blade" else null
            }
            Snapshot.sendApplyNotifications()
            assertTrue("The open modal must be invalidated when loading completes", invalidated)
            assertNull(opened.current().message)
            assertEquals(listOf("Espada Colossal [L]"), opened.current().options)
            opened.current().onOptionSelected("Espada Colossal [L]")
            assertEquals("Behemoth Blade", selected)
        } finally {
            observer.stop()
            observer.clear()
        }
    }

    @Test
    fun `open picker also observes errors instead of retaining loading message`() {
        val state = mutableStateOf(ReviewOptionPicker("Ataque rapido", emptyList(), null, "Carregando ataques...") {})
        val opened = liveReviewOptionPicker(state)
        state.value = state.value.copy(message = "Falha ao carregar ataques")
        assertEquals("Falha ao carregar ataques", opened.current().message)
    }
}