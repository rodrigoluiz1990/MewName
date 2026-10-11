package com.mewname.app

import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView

/** Owns window operations and composition disposal; the service decides when to stop. */
internal class OverlayWindowHost(
    private val context: Context,
    private val windowManager: () -> WindowManager,
    private val attachedOverlays: MutableSet<View>,
    private val isClosing: () -> Boolean,
    private val onPermissionLost: (String) -> Unit
) {
    private companion object { const val TAG = "OverlayWindowHost" }
    fun addOverlayView(view: View, params: WindowManager.LayoutParams): Boolean {
        if (isClosing()) {
            (view as? ComposeView)?.disposeComposition()
            return false
        }
        if (!Settings.canDrawOverlays(context)) {
            onPermissionLost("Permissao de sobreposicao revogada")
            return false
        }
        return try {
            windowManager().addView(view, params)
            attachedOverlays.add(view)
            true
        } catch (error: WindowManager.BadTokenException) {
            Log.w(TAG, "Android recusou uma janela de sobreposicao", error)
            onPermissionLost("Android recusou a sobreposicao")
            false
        } catch (error: SecurityException) {
            Log.w(TAG, "Android recusou uma janela de sobreposicao", error)
            onPermissionLost("Android recusou a sobreposicao")
            false
        }
    }

    fun updateOverlayViewLayout(view: View?, params: WindowManager.LayoutParams): Boolean {
        view ?: return false
        if (isClosing()) return false
        return try {
            windowManager().updateViewLayout(view, params)
            true
        } catch (error: WindowManager.BadTokenException) {
            Log.w(TAG, "Android recusou a atualizacao da sobreposicao", error)
            onPermissionLost("Android recusou a sobreposicao")
            false
        } catch (error: SecurityException) {
            Log.w(TAG, "Android recusou a atualizacao da sobreposicao", error)
            onPermissionLost("Android recusou a sobreposicao")
            false
        }
    }

    fun detachOverlay(view: View) {
        try {
            (view as? ComposeView)?.disposeComposition()
        } catch (error: Exception) {
            Log.w(TAG, "Falha ao descartar composicao da sobreposicao", error)
        }
        try {
            windowManager().removeViewImmediate(view)
            attachedOverlays.remove(view)
        } catch (error: IllegalArgumentException) {
            // Android may already have detached the window after permission revocation.
            attachedOverlays.remove(view)
            Log.d(TAG, "Janela de sobreposicao ja removida", error)
        } catch (error: Exception) {
            Log.e(TAG, "Falha ao remover janela de sobreposicao", error)
        }
    }

}
