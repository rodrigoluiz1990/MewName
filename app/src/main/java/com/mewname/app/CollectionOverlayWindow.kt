package com.mewname.app

import android.view.Gravity
import android.view.WindowManager
import android.graphics.PixelFormat

internal object CollectionOverlayWindow {
    const val HEADER_DP = 72
    const val HANDLE_DP = 24
    fun minimumHeight(density: Float) = (HANDLE_DP * density).toInt()
    fun clampHeight(height: Int, screenHeight: Int, density: Float): Int =
        height.coerceIn(minimumHeight(density), maxOf(minimumHeight(density), (screenHeight * 0.94f).toInt()))

    fun draggedHeight(startHeight: Int, startY: Float, currentY: Float, screenHeight: Int, density: Float): Int =
        clampHeight((startHeight + startY - currentY).toInt(), screenHeight, density)

    fun params(screenHeight: Int, density: Float) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT, clampHeight((screenHeight * 0.55f).toInt(), screenHeight, density),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        dimAmount = 0f
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }
    fun resize(params: WindowManager.LayoutParams, height: Int, screenHeight: Int, density: Float) {
        params.height = clampHeight(height, screenHeight, density)
        params.flags = if (params.height == minimumHeight(density))
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        else params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
    }
}
