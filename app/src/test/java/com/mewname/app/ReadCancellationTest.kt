package com.mewname.app

import android.os.Looper
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ReadCancellationTest {
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun oldCancellationCannotClearNewValidationOrReleaseItsJob() {
        val viewModel = MainViewModel()
        val oldCleanup = CompletableDeferred<Unit>()
        var attempts = 0
        var secondCancelled = false
        viewModel.startIvValidation("old") {
            attempts++
            try { awaitCancellation() }
            finally { withContext(NonCancellable) { oldCleanup.await() } }
        }
        idle()
        viewModel.cancelIvValidation()
        viewModel.startIvValidation("new") {
            attempts++
            try { awaitCancellation() } finally { secondCancelled = true }
        }
        idle()
        oldCleanup.complete(Unit)
        idle()
        assertTrue(viewModel.uiState.value.debugIvValidationRunning)
        assertNull(viewModel.uiState.value.debugIvValidationError)
        viewModel.startIvValidation("third") { attempts++; emptyList() }
        idle()
        assertEquals(2, attempts)
        viewModel.cancelIvValidation()
        idle()
        assertTrue(secondCancelled)
        assertFalse(viewModel.uiState.value.debugIvValidationRunning)
        assertEquals("Validacao cancelada.", viewModel.uiState.value.debugIvValidationError)
    }

    @Test
    fun staleSuccessAndFailureCannotOverwriteNewValidation() {
        for (fail in listOf(false, true)) {
            val viewModel = MainViewModel()
            val release = CompletableDeferred<Unit>()
            viewModel.startIvValidation("old") {
                try { awaitCancellation() } catch (_: CancellationException) {
                    withContext(NonCancellable) { release.await() }
                    if (fail) error("stale failure")
                    emptyList()
                }
            }
            idle()
            viewModel.cancelIvValidation()
            viewModel.startIvValidation("new") { awaitCancellation() }
            idle()
            release.complete(Unit)
            idle()
            assertTrue(viewModel.uiState.value.debugIvValidationRunning)
            assertNull(viewModel.uiState.value.debugIvValidationError)
            viewModel.cancelIvValidation()
            idle()
        }
    }

    @Test
    fun validationRecoversAfterTimeoutFailureAndSynchronousSuccess() {
        val viewModel = MainViewModel()
        viewModel.startIvValidation("timeout") { withTimeout(0L) { awaitCancellation() } }
        idle()
        assertFalse(viewModel.uiState.value.debugIvValidationRunning)
        assertNotNull(viewModel.uiState.value.debugIvValidationError)
        viewModel.startIvValidation("error") { error("read failed") }
        idle()
        assertEquals("read failed", viewModel.uiState.value.debugIvValidationError)
        repeat(2) {
            viewModel.startIvValidation("success") { emptyList() }
            idle()
            assertFalse(viewModel.uiState.value.debugIvValidationRunning)
            assertNull(viewModel.uiState.value.debugIvValidationError)
        }
    }

    @Test
    fun delayedCleanupCannotInvalidateTheNewReadOrItsCancellation() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val task = CurrentReadTask()
        val cleanup = CompletableDeferred<Unit>()
        lateinit var old: CurrentReadTask.Request
        lateinit var current: CurrentReadTask.Request
        var cleaned = 0
        var cancelled = false
        try {
            task.launch(scope) { request ->
                old = request
                try { awaitCancellation() } finally {
                    withContext(NonCancellable) { cleanup.await() }
                    if (request.isCurrent) cleaned++
                }
            }
            idle()
            task.cancel()
            assertFalse(old.isCurrent)
            task.launch(scope) { request ->
                current = request
                try { awaitCancellation() } finally { cancelled = true }
            }
            idle()
            cleanup.complete(Unit)
            idle()
            assertEquals(0, cleaned)
            assertTrue(current.isCurrent)
            task.launch(scope) { fail("Duplicate read admitted") }
            task.cancel()
            idle()
            assertTrue(cancelled)
            assertFalse(current.isCurrent)
        } finally { scope.cancel() }
    }

    @Test
    fun queuedOverlayProgressCannotChangeReplacementWindow() {
        val service = Robolectric.buildService(OverlayService::class.java).get()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val task = CurrentReadTask()
        val detail = TextView(ApplicationProvider.getApplicationContext())
        val detailField = OverlayService::class.java.getDeclaredField("loadingDetailView").apply { isAccessible = true }
        val update = OverlayService::class.java.getDeclaredMethod(
            "updateLoadingStatus", CurrentReadTask.Request::class.java, String::class.java, String::class.java
        ).apply { isAccessible = true }
        try {
            detailField.set(service, detail)
            task.launch(scope) { request ->
                update.invoke(service, request, null, "old status")
                awaitCancellation()
            }
            // Cancel before Handler drains the queued status update.
            task.cancel()
            detail.text = "new status"
            task.launch(scope) { awaitCancellation() }
            idle()
            assertEquals("new status", detail.text.toString())
            task.cancel()
            task.launch(scope) { request ->
                update.invoke(service, request, null, "current status")
                awaitCancellation()
            }
            idle()
            assertEquals("current status", detail.text.toString())
        } finally {
            task.cancel()
            scope.cancel()
            idle()
        }
    }
}
