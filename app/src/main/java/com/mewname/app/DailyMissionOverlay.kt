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
    private val update: (View, WindowManager.LayoutParams) -> Boolean = { _, _ -> false },
    private val refreshAnchor: () -> Unit = {}
) {
    private val store = DailyMissionStore(context)
    private val appearancePreferences = context.getSharedPreferences("home_layout", Context.MODE_PRIVATE)
    private val appearanceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "style") render() }
    private val handler = Handler(Looper.getMainLooper())
    private data class Panel(val view: View, val params: WindowManager.LayoutParams, val above: Boolean, val count: Int)
    private val windows = mutableListOf<Panel>()
    private var anchor = android.graphics.Rect()
    private var backdrop: View? = null
    private var backdropParams: WindowManager.LayoutParams? = null
    private var bubbleRadius = 0f
    private fun clearWindows() {
        windows.forEach { remove(it.view) }; windows.clear()
        backdrop?.let(remove); backdrop = null; backdropParams = null
    }
    private fun createBackdrop() {
        val appearance = AppAppearance.read(appearancePreferences)
        val glass = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            appearance.card.map { it.toArgb() }.toIntArray()).apply {
            setStroke(dp(1).coerceAtLeast(1), appearance.border.toArgb())
        }
        val params = WindowManager.LayoutParams(dp(40), 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT }
        val view = object : View(context) {
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                if (anchor.isEmpty) return
                // This decoration is above the reading window in Z order. Leave its
                // circular icon untouched and let all touches reach the real button.
                val hole = android.graphics.Path().apply {
                    addCircle((anchor.centerX() - params.x).toFloat(),
                        (anchor.centerY() - params.y).toFloat(), bubbleRadius,
                        android.graphics.Path.Direction.CW)
                }
                canvas.save()
                canvas.clipOutPath(hole)
                glass.cornerRadius = width / 2f
                glass.setBounds(0, 0, width, height)
                glass.draw(canvas)
                canvas.restore()
            }
        }.apply { visibility = if (hidden) View.INVISIBLE else View.VISIBLE }
        if (add(view, params)) { backdrop = view; backdropParams = params }
    }
    private fun placement() = MissionPlacement.read(store.preferences)
    private fun availableSize(): android.graphics.Point {
        if (android.os.Build.VERSION.SDK_INT < 30) return android.graphics.Point(
            context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = manager.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
        return android.graphics.Point(metrics.bounds.width() - insets.left - insets.right,
            metrics.bounds.height() - insets.top - insets.bottom)
    }
    fun followBubble(view: View, params: WindowManager.LayoutParams) {
        if (placement() == MissionPlacement.FREE || !started) return
        val size = availableSize()
        val width = view.width.takeIf { it > 0 } ?: dp(56)
        val height = view.height.takeIf { it > 0 } ?: dp(56)
        val top = if (windows.any { it.above }) dp(46) else 0
        val bottom = if (windows.any { !it.above }) dp(46) else 0
        params.x = params.x.coerceIn(0, (size.x - width).coerceAtLeast(0))
        params.y = params.y.coerceIn(top, (size.y - height - bottom).coerceAtLeast(top))
        val icon = view.findViewById<View>(R.id.floating_icon)
        bubbleRadius = (icon?.width?.takeIf { it > 0 } ?: (width - dp(8)).coerceAtLeast(1)) / 2f
        anchor.set(params.x, params.y, params.x + width, params.y + height)
        positionLinked()
    }
    private fun positionLinked() {
        if (placement() == MissionPlacement.FREE || anchor.isEmpty) return
        val size = availableSize()
        windows.forEach { panel ->
            val available = if (panel.above) anchor.top - dp(6) else size.y - anchor.bottom - dp(6)
            panel.params.height = dp(4 + 36 * panel.count.coerceAtMost(5)).coerceAtMost(available.coerceAtLeast(1))
            panel.params.x = (anchor.centerX() - panel.params.width / 2).coerceIn(0, (size.x - panel.params.width).coerceAtLeast(0))
            panel.params.y = if (panel.above) anchor.top - dp(6) - panel.params.height else anchor.bottom + dp(6)
            update(panel.view, panel.params)
        }
        backdropParams?.let { params ->
            val top = windows.filter { it.above }.minOfOrNull { it.params.y } ?: (anchor.centerY() - bubbleRadius).toInt()
            val bottom = windows.filter { !it.above }.maxOfOrNull { it.params.y + it.params.height } ?: (anchor.centerY() + bubbleRadius).toInt()
            params.width = dp(40)
            params.height = bottom - top
            params.x = (anchor.centerX() - params.width / 2).coerceIn(0, (size.x - params.width).coerceAtLeast(0))
            params.y = top
            backdrop?.let { update(it, params); it.invalidate() }
        }
    }
    private var hidden = false
    private var started = false
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "missions" || key == MissionPlacement.KEY) render() }
    private val midnight = Runnable { render(); schedule() }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Screen-on and clock broadcasts are frequent; rebuild the windows only when their content changed.
            render(force = intent?.action == Intent.ACTION_CONFIGURATION_CHANGED); schedule()
        }
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
        windows.forEach { it.view.visibility = if (value) View.INVISIBLE else View.VISIBLE }
        backdrop?.visibility = if (value) View.INVISIBLE else View.VISIBLE
    }
    private var rendered: Any? = null
    private fun render(force: Boolean = true) {
        val pending = if (started) store.all().filterNot { it.completed() } else emptyList()
        val content = Triple(pending, placement(), appearancePreferences.getString("style", null))
        if (!force && started && content == rendered) return
        rendered = content
        clearWindows()
        if (!started) return
        if (pending.isEmpty()) return
        if (placement() != MissionPlacement.FREE) createBackdrop()
        val (above, below) = placement().split(pending)
        if (above.isNotEmpty()) createPanel(above, true)
        if (below.isNotEmpty()) createPanel(below, false)
        refreshAnchor()
        positionLinked()
    }
    private fun createPanel(pending: List<DailyMission>, above: Boolean) {
        val linked = placement() != MissionPlacement.FREE
        val appearance = AppAppearance.read(appearancePreferences)
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(2), dp(2), dp(2)) }
        for (mission in pending) {
            val button = if (mission.icon.isBlank()) android.widget.TextView(context).apply {
                text = mission.name.trim().take(2).uppercase()
                textSize = 11f
                gravity = android.view.Gravity.CENTER
                setTextColor(appearance.text.toArgb())
                contentDescription = mission.name
                tooltipText = mission.name
                setOnClickListener {
                    store.complete(mission.id, true)
                    Toast.makeText(context, "✓ ${mission.name}", Toast.LENGTH_SHORT).show()
                }
            } else ImageButton(context).apply {
                contentDescription = mission.name
                tooltipText = mission.name
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(dp(6), dp(6), dp(6), dp(6))
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                val bitmap = runCatching { context.assets.open(mission.icon).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
                } }.getOrNull()
                if (bitmap != null) setImageBitmap(bitmap) else setImageResource(android.R.drawable.ic_menu_agenda)
                if (mission.icon in setOf("filter_icons/legendary.png", "filter_icons/mythical.png", "filter_icons/costume.png", "filter_icons/favorite_empty.png"))
                    setColorFilter(appearance.text.toArgb())
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
            background = if (linked) null else GradientDrawable(GradientDrawable.Orientation.TL_BR,
                appearance.card.map { it.toArgb() }.toIntArray()).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1).coerceAtLeast(1), appearance.border.toArgb())
            }
            clipToOutline = true
            visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        }
        val params = WindowManager.LayoutParams(dp(40), dp((if (linked) 0 else 24) + 4 + 36 * pending.size.coerceAtMost(5)),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT }
        val size = availableSize()
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
        if (!linked) root.addView(handle, LinearLayout.LayoutParams(dp(40), dp(24)))
        root.addView(scroll, LinearLayout.LayoutParams(dp(40), 0, 1f))
        if (add(root, params)) windows += Panel(root, params, above, pending.size)
    }
    fun close() {
        if (!started) return
        started = false
        handler.removeCallbacksAndMessages(null)
        store.preferences.unregisterOnSharedPreferenceChangeListener(listener)
        appearancePreferences.unregisterOnSharedPreferenceChangeListener(appearanceListener)
        context.unregisterReceiver(receiver)
        clearWindows()
    }
}