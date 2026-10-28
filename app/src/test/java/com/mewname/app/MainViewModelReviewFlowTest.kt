package com.mewname.app

import android.content.Context
import android.net.Uri
import android.os.Looper
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout
import com.mewname.app.domain.AppLanguage
import com.mewname.app.domain.CatalogLoadFeedback
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.model.NamingBlock
import com.mewname.app.model.NamingBlockType
import com.mewname.app.model.NamingConfig
import com.mewname.app.model.NamingField
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.PvpLeague
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MainViewModelReviewFlowTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun clearPreferences() {
        context.getSharedPreferences("mewname_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `generates suggested name from a complete OCR result`() {
        val viewModel = configuredViewModel(
            PokemonScreenData(
                pokemonName = "Zacian",
                cp = 3210,
                pvpLeague = PvpLeague.GREAT,
                pvpRank = 1
            )
        )

        viewModel.processImage(context, Uri.parse("content://mewname/complete"))
        val state = awaitProcessed(viewModel)

        assertEquals("Zacian\nCP 3210", state.rawText)
        assertEquals("Zacian", state.parsedData?.pokemonName)
        assertEquals("Zacian3210", generatedName(state))
        assertNotNull(state.pendingReview)
    }

    @Test
    fun `keeps incomplete OCR data in review and regenerates after user choices`() {
        val viewModel = configuredViewModel(
            PokemonScreenData(
                pokemonName = "Zacian",
                cp = null,
                pvpLeague = PvpLeague.GREAT,
                pvpRank = 1
            )
        )

        viewModel.processImage(context, Uri.parse("content://mewname/incomplete"))
        val pendingState = awaitProcessed(viewModel)
        val review = pendingState.pendingReview

        assertNotNull(review)
        assertTrue(NamingField.CP in review!!.fields)
        assertEquals("Zacian", generatedName(pendingState))

        viewModel.applyReview(review.data.copy(pokemonName = "Zamazenta", cp = 2800))
        val appliedState = viewModel.uiState.value

        assertNull(appliedState.pendingReview)
        assertEquals("Zamazenta", appliedState.parsedData?.pokemonName)
        assertEquals(2800, appliedState.parsedData?.cp)
        assertEquals("Zamazenta2800", generatedName(appliedState))
    }

    @Test
    fun `timeout explains the failure and allows a subsequent successful read`() {
        var attempts = 0
        val viewModel = MainViewModel(object : ReviewOcrPipeline {
            override suspend fun process(context: Context, uri: Uri, onStatusChange: (String) -> Unit): ReviewOcrResult {
                if (attempts++ == 0) withTimeout(0L) { awaitCancellation() }
                return ReviewOcrResult("Pikachu", null, PokemonScreenData(pokemonName = "Pikachu"))
            }
        })
        viewModel.setAppLanguage(context, AppLanguage.PT_BR)
        viewModel.processImage(context, Uri.parse("content://mewname/timeout"))
        val failed = awaitProcessed(viewModel)
        assertEquals(CatalogLoadFeedback.ocrTimeoutMessage(AppLanguage.PT_BR), failed.error)
        assertNull(failed.processingStatusMessage)
        assertNull(failed.pendingReview)

        viewModel.processImage(context, Uri.parse("content://mewname/retry"))
        val success = awaitProcessed(viewModel)
        assertEquals("Pikachu", success.parsedData?.pokemonName)
        assertNull(success.error)
    }

    @Test
    fun `voluntary cancellation does not show a timeout error`() {
        var started = false
        val viewModel = MainViewModel(object : ReviewOcrPipeline {
            override suspend fun process(context: Context, uri: Uri, onStatusChange: (String) -> Unit): ReviewOcrResult {
                started = true
                awaitCancellation()
            }
        })
        viewModel.processImage(context, Uri.parse("content://mewname/cancel"))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(started)
        assertTrue(viewModel.uiState.value.isProcessing)
        viewModel.cancelImageProcessing()
        shadowOf(Looper.getMainLooper()).idle()
        val state = viewModel.uiState.value
        assertEquals(false, state.isProcessing)
        assertEquals(lt(state.appLanguage, "Processo cancelado.", "Process cancelled.", "Proceso cancelado."), state.error)
        assertNull(state.processingStatusMessage)
        assertNull(state.pendingReview)
    }

    @Test
    fun cancelledReadFinishingDoesNotClearTheNewRead() {
        val releaseCleanup = kotlinx.coroutines.CompletableDeferred<Unit>()
        var attempts = 0
        val viewModel = MainViewModel(object : ReviewOcrPipeline {
            override suspend fun process(context: Context, uri: Uri, onStatusChange: (String) -> Unit): ReviewOcrResult {
                val attempt = ++attempts
                try {
                    awaitCancellation()
                } finally {
                    if (attempt == 1) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        releaseCleanup.await()
                        onStatusChange("Old read")
                    }
                }
            }
        })
        viewModel.processImage(context, Uri.parse("content://mewname/first"))
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.cancelImageProcessing()
        viewModel.processImage(context, Uri.parse("content://mewname/second"))
        shadowOf(Looper.getMainLooper()).idle()
        releaseCleanup.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(viewModel.uiState.value.isProcessing)
        org.junit.Assert.assertNotEquals("Old read", viewModel.uiState.value.processingStatusMessage)
        viewModel.processImage(context, Uri.parse("content://mewname/third"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, attempts)
        viewModel.cancelImageProcessing()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(false, viewModel.uiState.value.isProcessing)
    }
    private fun configuredViewModel(data: PokemonScreenData): MainViewModel {
        val pipeline = FakeReviewOcrPipeline(data)
        val viewModel = MainViewModel(reviewOcrPipeline = pipeline)
        val config = NamingConfig(
            id = FLOW_CONFIG_ID,
            name = "Fluxo de revisao",
            maxLength = 30,
            blocks = listOf(
                NamingBlock(type = NamingBlockType.VARIABLE, field = NamingField.POKEMON_NAME),
                NamingBlock(type = NamingBlockType.VARIABLE, field = NamingField.CP)
            )
        )
        context.getSharedPreferences("mewname_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(SAVED_PRESETS_KEY, encodeSavedPresets(listOf(config)))
            .commit()
        viewModel.loadConfigs(context)
        return viewModel
    }

    private fun generatedName(state: UiState): String {
        return state.generatedResults.first { it.configId == FLOW_CONFIG_ID }.generatedName
    }

    private fun awaitProcessed(viewModel: MainViewModel): UiState {
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            val state = viewModel.uiState.value
            if (!state.isProcessing && (state.parsedData != null || state.error != null)) {
                return state
            }
            Thread.sleep(10)
        }
        throw AssertionError("Timed out waiting for the OCR review flow")
    }

    private class FakeReviewOcrPipeline(
        private val data: PokemonScreenData
    ) : ReviewOcrPipeline {
        override suspend fun process(
            context: Context,
            uri: Uri,
            onStatusChange: (String) -> Unit
        ): ReviewOcrResult {
            onStatusChange("OCR simulado")
            return ReviewOcrResult(
                rawText = "Zacian\nCP 3210",
                bitmap = null,
                data = data
            )
        }
    }

    private companion object {
        const val FLOW_CONFIG_ID = "review-flow"
    }
}