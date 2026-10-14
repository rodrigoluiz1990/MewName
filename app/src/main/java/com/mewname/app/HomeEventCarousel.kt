package com.mewname.app

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.*
import kotlin.math.abs

internal class HomeEventCarouselState {
    var index by mutableIntStateOf(0)
    var count by mutableIntStateOf(0)
    var imageHeight by mutableFloatStateOf(0f)
    var lastManual by mutableLongStateOf(0L)
    fun move(direction: Int, manual: Boolean = true) {
        if (count <= 1) return
        index = (index + direction + count) % count
        if (manual) lastManual = SystemClock.elapsedRealtime()
    }
}

internal fun Modifier.homeEventSwipe(state: HomeEventCarouselState, enabled: Boolean): Modifier =
    pointerInput(state, enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (state.count > 1 && down.position.y < state.imageHeight) {
                var distance = 0f
                val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                    distance += over
                    change.consume()
                }
                if (drag != null) {
                    val completed = horizontalDrag(drag.id) { change ->
                        distance += change.position.x - change.previousPosition.x
                        change.consume()
                    }
                    if (completed && abs(distance) >= 32 * density) state.move(if (distance < 0) 1 else -1)
                }
            }
        }
    }