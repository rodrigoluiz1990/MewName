package com.mewname.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverlayResultsPresenterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun selectingANameCopiesTheSelectedValueAndClosesOnlyOnce() {
        val copied = mutableListOf<String>()
        var closed = 0
        val root = OverlayResultsPresenter(context).createView(
            listOf("Primeiro" to "Pikachu", "Segundo" to "Raichu"), { copied += it }, { closed++ }
        )
        action(root, "Raichu").performClick()
        assertEquals(listOf("Raichu"), copied)
        assertEquals(1, closed)
    }

    @Test fun reviewAndCloseUseTheirOwnCallbacks() {
        var reviewed = 0
        var closed = 0
        val root = OverlayResultsPresenter(context).createView(
            emptyList(), { fail("Unexpected copy") }, { closed++ }, { reviewed++ }
        )
        action(root, "Revisar dados").performClick()
        assertEquals(1, reviewed)
        assertEquals(0, closed)
        action(root, "Fechar").performClick()
        assertEquals(1, reviewed)
        assertEquals(1, closed)
    }

    @Test fun reviewActionIsAbsentWithoutReviewCallback() {
        val root = OverlayResultsPresenter(context).createView(emptyList(), {}, {})
        assertFalse(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "Revisar dados" })
    }

    private fun action(root: View, label: String): View = descendants(root).first { candidate ->
        candidate.isClickable && descendants(candidate).filterIsInstance<TextView>().any { it.text.toString() == label }
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
