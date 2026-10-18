package com.mewname.app

import android.content.*
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import androidx.compose.ui.graphics.toArgb
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.time.Duration
import java.time.ZonedDateTime

/** Small non-modal window; never participates in screen capture. */
internal class DailyMissionOverlay(
    private val context: Context,
    private val add: (View, WindowManager.LayoutParams) -> Boolean,
    private val remove: (View) -> Unit,
    private val update: (View, WindowManager.LayoutParams) -> Boolean = { _, _ -> false }
) {
    private val store = DailyMissionStore(context)
    private val appearancePreferences = context.getSharedPreferences("home_layout", Context.MODE_PRIVATE)
    private val appearanceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "style") render() }
    private val handler = Handler(Looper.getMainLooper())
    private var window: View? = null
    private var hidden = false
    private var started = false
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "missions") render() }
    private val midnight = Runnable { render(); schedule() }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { render(); schedule() }
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun start() {
        if (started) return
        started = true
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        appearancePreferences.registerOnSharedPreferenceChangeListener(appearanceListener)
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_CONFIGURATION_CHANGED); addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_SCREEN_ON)
        }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        render(); schedule()
    }
    private fun schedule() {
        handler.removeCallbacks(midnight)
        val now = ZonedDateTime.now()
        val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        handler.postDelayed(midnight, Duration.between(now, next).toMillis().coerceAtLeast(1000))
    }
    fun hideForCapture(value: Boolean) {
        hidden = value
        window?.visibility = if (value) View.INVISIBLE else View.VISIBLE
    }
    private fun render() {
        window?.let(remove); window = null
        if (!started) return
        val pending = store.all().filterNot { it.completed() }
        if (pending.isEmpty()) return
        val appearance = AppAppearance.read(appearancePreferences)
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(2), dp(2), dp(2)) }
        for (mission in pending) {
            val button = ImageButton(context).apply {
                contentDescription = mission.name
                tooltipText = mission.name
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(dp(6), dp(6), dp(6), dp(6))
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                val bitmap = runCatching { context.assets.open(mission.icon).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
                } }.getOrNull()
                if (bitmap != null) setImageBitmap(bitmap) else setImageResource(android.R.drawable.ic_menu_agenda)
                setOnClickListener {
                    store.complete(mission.id, true)
                    Toast.makeText(context, "✓ ${mission.name}", Toast.LENGTH_SHORT).show()
                }
            }
            column.addView(button, LinearLayout.LayoutParams(dp(36), dp(36)))
        }
        val scroll = ScrollView(context).apply {
            addView(column)
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // Reuse the same translucent card palette and rim as the app's glass panels.
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                appearance.card.map { it.toArgb() }.toIntArray()).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1).coerceAtLeast(1), appearance.border.toArgb())
            }
            clipToOutline = true
            visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        }
        val params = WindowManager.LayoutParams(dp(40), dp(24 + 4 + 36 * pending.size.coerceAtMost(5)),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT }
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = if (android.os.Build.VERSION.SDK_INT >= 30) {
            val metrics = manager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            android.graphics.Point(metrics.bounds.width() - insets.left - insets.right,
                metrics.bounds.height() - insets.top - insets.bottom)
        } else android.graphics.Point(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)
        val maxX = (size.x - params.width).coerceAtLeast(0)
        val maxY = (size.y - params.height).coerceAtLeast(0)
        params.x = (store.preferences.getFloat("position_x", 1f) * size.x).toInt().coerceIn(0, maxX)
        params.y = (store.preferences.getFloat("position_y", if (size.y > 0) dp(48).toFloat() / size.y else 0f) * size.y).toInt().coerceIn(0, maxY)
        val handle = android.widget.FrameLayout(context).apply {
            contentDescription = "Arrastar caixa de missões"
            tooltipText = "Arraste para mover"
            addView(View(context).apply {
                background = GradientDrawable().apply { setColor(appearance.text.copy(alpha = .42f).toArgb()); cornerRadius = dp(2).toFloat() }
            }, android.widget.FrameLayout.LayoutParams(dp(16), dp(3), Gravity.CENTER))
        }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
        val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y; dragging = false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) dragging = true
                    if (dragging) {
                        params.x = (startX + dx).toInt().coerceIn(0, maxX)
                        params.y = (startY + dy).toInt().coerceIn(0, maxY)
                        update(root, params)
                    }
                }
                android.view.MotionEvent.ACTION_UP -> if (dragging) {
                    store.preferences.edit().putFloat("position_x", if (size.x > 0) params.x.toFloat() / size.x else 0f)
                        .putFloat("position_y", if (size.y > 0) params.y.toFloat() / size.y else 0f).apply()
                } else handle.performClick()
                android.view.MotionEvent.ACTION_CANCEL -> {
                    params.x = startX; params.y = startY; update(root, params)
                }
            }
            true
        }
        root.addView(handle, LinearLayout.LayoutParams(dp(40), dp(24)))
        root.addView(scroll, LinearLayout.LayoutParams(dp(40), 0, 1f))
        if (add(root, params)) window = root
    }
    fun close() {
        if (!started) return
        started = false
        handler.removeCallbacksAndMessages(null)
        store.preferences.unregisterOnSharedPreferenceChangeListener(listener)
        appearancePreferences.unregisterOnSharedPreferenceChangeListener(appearanceListener)
        context.unregisterReceiver(receiver)
        window?.let(remove); window = null
    }
}