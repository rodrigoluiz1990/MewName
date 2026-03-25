package com.mewname.app

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.ArrayAdapter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mewname.app.domain.NameGenerator
import com.mewname.app.domain.OcrPokemonParser
import com.mewname.app.domain.PokemonReadSessionMerger
import com.mewname.app.model.EvolutionFlag
import com.mewname.app.model.Gender
import com.mewname.app.model.IvDebugInfo
import com.mewname.app.model.NamingConfig
import com.mewname.app.model.NamingField
import com.mewname.app.model.NormalizedDebugRect
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.PokemonSize
import com.mewname.app.model.PvpLeague
import com.mewname.app.model.VivillonPattern
import com.mewname.app.model.effectiveBlocks
import com.mewname.app.ocr.OcrEngine
import com.mewname.app.ocr.OcrResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.abs

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    companion object {
        private const val TAG = "OverlayService"
        const val ACTION_CAPTURE_PERMISSION_INVALID = "com.mewname.app.action.CAPTURE_PERMISSION_INVALID"
    }

    private data class BubbleLogSnapshot(
        val capturedAtMillis: Long,
        val bitmapWidth: Int,
        val bitmapHeight: Int,
        val rawText: String,
        val ocrLineLogs: List<String>,
        val parsedData: PokemonScreenData,
        val reviewableFields: List<NamingField>,
        val generatedResults: List<Pair<String, String>>,
        val reviewedData: PokemonScreenData? = null
    )

    private lateinit var windowManager: WindowManager
    private var floatingButton: View? = null
    private var resultsView: View? = null
    private var mediaProjection: MediaProjection? = null
    private var projectionData: Intent? = null
    private var activeImageReader: ImageReader? = null
    private var activeVirtualDisplay: VirtualDisplay? = null
    private var projectionCallbackRegistered = false
    private var isCaptureInProgress = false
    private var loadingView: View? = null
    private var dismissTargetView: View? = null
    private var bubbleDismissMode = false
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val overlayViewModelStore = ViewModelStore()
    
    private val parser = OcrPokemonParser()
    private val generator = NameGenerator()
    private val ocrEngine = OcrEngine()
    private val sessionMerger = PokemonReadSessionMerger()
    private var lastCapturedData: PokemonScreenData? = null
    private var lastBubbleLogSnapshot: BubbleLogSnapshot? = null
    private val bubbleLongPressTimeoutMillis = 3000L

    override fun onBind(intent: Intent?): IBinder? = null

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = overlayViewModelStore

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(Bundle())
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundService()
        showFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE") {
            stopSelf()
            return START_STICKY
        }
        
        val newProjectionData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("PROJECTION_DATA", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("PROJECTION_DATA")
        }
        if (newProjectionData != null) {
            projectionData = newProjectionData
            initializeProjectionSession()
            launchPokemonGo()
        }
        
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "overlay_service_channel"
        val channel = NotificationChannel(channelId, "MewName Overlay", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("MewName Modo Jogo")
            .setContentText("Bolinha ativa. Clique para capturar.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
    }

    private fun showFloatingButton() {
        floatingButton = LayoutInflater.from(this).inflate(R.layout.layout_floating_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val metrics = resources.displayMetrics
            x = (metrics.widthPixels - 180)
                .coerceAtLeast(0)
            y = (metrics.heightPixels / 2) - 200
        }

        floatingButton?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var longPressTriggered = false
            private val longPressRunnable = Runnable {
                longPressTriggered = true
                bubbleDismissMode = true
                showDismissTarget()
            }

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        longPressTriggered = false
                        mainHandler.postDelayed(longPressRunnable, bubbleLongPressTimeoutMillis)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val diffX = abs(event.rawX - initialTouchX)
                        val diffY = abs(event.rawY - initialTouchY)
                        if (!longPressTriggered && (diffX > 20 || diffY > 20)) {
                            mainHandler.removeCallbacks(longPressRunnable)
                        }
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingButton, params)
                        if (bubbleDismissMode) {
                            updateDismissTargetHighlight(isBubbleOverDismissTarget(params, v))
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        mainHandler.removeCallbacks(longPressRunnable)
                        val diffX = abs(event.rawX - initialTouchX)
                        val diffY = abs(event.rawY - initialTouchY)
                        if (bubbleDismissMode) {
                            val shouldDismiss = isBubbleOverDismissTarget(params, v)
                            hideDismissTarget()
                            bubbleDismissMode = false
                            if (shouldDismiss) {
                                stopSelf()
                            }
                        } else if (!longPressTriggered && diffX < 15 && diffY < 15) {
                            captureAndProcess()
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        mainHandler.removeCallbacks(longPressRunnable)
                        if (bubbleDismissMode) {
                            hideDismissTarget()
                            bubbleDismissMode = false
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingButton, params)
    }

    private fun showDismissTarget() {
        if (dismissTargetView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 36
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
            setBackgroundColor(Color.argb(80, 0, 0, 0))
        }

        val target = TextView(this).apply {
            text = "X"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(220, 210, 48, 48))
                setStroke(4, Color.WHITE)
            }
            layoutParams = LinearLayout.LayoutParams(124, 124)
        }

        container.addView(target)
        dismissTargetView = container
        windowManager.addView(container, params)
        updateDismissTargetHighlight(false)
    }

    private fun hideDismissTarget() {
        dismissTargetView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        dismissTargetView = null
    }

    private fun updateDismissTargetHighlight(isActive: Boolean) {
        val container = dismissTargetView as? LinearLayout ?: return
        val target = container.getChildAt(0) as? TextView ?: return
        target.scaleX = if (isActive) 1.18f else 1f
        target.scaleY = if (isActive) 1.18f else 1f
        target.alpha = if (isActive) 1f else 0.92f
        (target.background as? GradientDrawable)?.apply {
            setColor(if (isActive) Color.argb(235, 235, 58, 58) else Color.argb(220, 210, 48, 48))
        }
    }

    private fun isBubbleOverDismissTarget(
        bubbleParams: WindowManager.LayoutParams,
        bubbleView: View
    ): Boolean {
        val targetView = dismissTargetView ?: return false
        val location = IntArray(2)
        targetView.getLocationOnScreen(location)
        val targetRect = Rect(
            location[0],
            location[1],
            location[0] + targetView.width,
            location[1] + targetView.height
        )

        val bubbleCenterX = bubbleParams.x + (bubbleView.width / 2)
        val bubbleCenterY = bubbleParams.y + (bubbleView.height / 2)
        return targetRect.contains(bubbleCenterX, bubbleCenterY)
    }

    private fun launchPokemonGo() {
        val directIntent = packageManager.getLaunchIntentForPackage("com.nianticlabs.pokemongo")
        if (directIntent != null) {
            directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(directIntent)
            return
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val candidates = packageManager.queryIntentActivities(launcherIntent, 0)
        val bestMatch = candidates.firstOrNull { info ->
            val label = info.loadLabel(packageManager)?.toString().orEmpty()
            val packageName = info.activityInfo?.packageName.orEmpty()
            label.contains("Pokemon GO", ignoreCase = true) ||
                label.contains("Pokémon GO", ignoreCase = true) ||
                packageName.contains("pokemongo", ignoreCase = true)
        }

        if (bestMatch != null) {
            val fallbackIntent = packageManager.getLaunchIntentForPackage(bestMatch.activityInfo.packageName)
            if (fallbackIntent != null) {
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(fallbackIntent)
                return
            }
        }

        Toast.makeText(this, "Pokemon GO nao encontrado neste aparelho.", Toast.LENGTH_SHORT).show()
    }

    private fun captureAndProcess() {
        if (isCaptureInProgress) return

        val reader = activeImageReader ?: run {
            Toast.makeText(this, "Permissao de captura nao encontrada. Ative a bolha novamente.", Toast.LENGTH_SHORT).show()
            notifyCapturePermissionUnavailable()
            return
        }

        isCaptureInProgress = true
        Handler(Looper.getMainLooper()).postDelayed({
            val image = try {
                reader.acquireLatestImage()
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao obter imagem da sessao ativa", e)
                null
            }

            if (image == null) {
                isCaptureInProgress = false
                Toast.makeText(this, "Falha momentânea na captura. Tente novamente.", Toast.LENGTH_SHORT).show()
                return@postDelayed
            }

            try {
                val width = image.width
                val height = image.height
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val rawBitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                rawBitmap.copyPixelsFromBuffer(buffer)
                val bitmap = Bitmap.createBitmap(rawBitmap, 0, 0, width, height)
                rawBitmap.recycle()

                processCapturedBitmap(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao processar bitmap capturado", e)
                isCaptureInProgress = false
                Toast.makeText(this, "Falha ao processar a captura.", Toast.LENGTH_SHORT).show()
            } finally {
                image.close()
            }
        }, 180)
    }

    private fun initializeProjectionSession() {
        val data = projectionData ?: return
        if (mediaProjection != null && activeVirtualDisplay != null && activeImageReader != null) return

        cleanupCaptureResources(stopProjection = true)
        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            val projection = mpManager.getMediaProjection(Activity.RESULT_OK, data)
            mediaProjection = projection

            if (projection != null && !projectionCallbackRegistered) {
                projection.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        cleanupCaptureResources()
                        isCaptureInProgress = false
                        notifyCapturePermissionUnavailable()
                    }
                }, Handler(Looper.getMainLooper()))
                projectionCallbackRegistered = true
            }

            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi
            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            activeImageReader = imageReader
            activeVirtualDisplay = projection?.createVirtualDisplay(
                "CaptureSession",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao iniciar sessao de MediaProjection", e)
            cleanupCaptureResources()
            notifyCapturePermissionUnavailable()
        }
    }

    private fun processCapturedBitmap(bitmap: Bitmap) {
        showLoadingOverlay()
        serviceScope.launch {
            runCatching {
                ocrEngine.extract(bitmap)
            }.onSuccess { ocrResult ->
                val parsed = sessionMerger.mergeIfSamePokemon(
                    parser.parse(this@OverlayService, ocrResult),
                    lastCapturedData
                )
                lastCapturedData = parsed
                val savedConfigs = loadSavedConfigs()
                val generatedResults = savedConfigs.mapNotNull { config ->
                    val generatedName = generator.generate(parsed, config).trim()
                    generatedName.takeIf { it.isNotEmpty() }?.let { config.name to it }
                }

                val reviewFields = reviewableFields(savedConfigs)
                lastBubbleLogSnapshot = BubbleLogSnapshot(
                    capturedAtMillis = System.currentTimeMillis(),
                    bitmapWidth = bitmap.width,
                    bitmapHeight = bitmap.height,
                    rawText = ocrResult.fullText,
                    ocrLineLogs = buildOcrLineLogs(ocrResult),
                    parsedData = parsed,
                    reviewableFields = reviewFields,
                    generatedResults = generatedResults
                )
                val reviewRecommended = shouldOpenReview(parsed, reviewFields)
                showResultsOverlay(
                    results = generatedResults,
                    reviewRecommended = reviewRecommended,
                    onOpenReview = {
                        showReviewOverlay(parsed, reviewFields, savedConfigs, bitmap)
                    }
                )

                isCaptureInProgress = false
                removeLoadingOverlay()
            }.onFailure { error ->
                Log.e(TAG, "Falha no OCR da bolha", error)
                Toast.makeText(this@OverlayService, "Nao foi possivel ler a imagem.", Toast.LENGTH_SHORT).show()
                isCaptureInProgress = false
                removeLoadingOverlay()
            }
        }
    }

    private fun showLoadingOverlay() {
        removeLoadingOverlay()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(110, 0, 0, 0))
            addView(ProgressBar(this@OverlayService).apply {
                isIndeterminate = true
            })
            addView(TextView(this@OverlayService).apply {
                text = "Analisando imagem"
                setTextColor(Color.WHITE)
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 24, 0, 8)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@OverlayService).apply {
                text = "Lendo os dados detectados para gerar o nome sugerido."
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
            })
        }

        windowManager.addView(layout, params)
        loadingView = layout
    }

    private fun removeLoadingOverlay() {
        loadingView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        loadingView = null
    }

    private fun cleanupCaptureResources(stopProjection: Boolean = true) {
        try {
            activeVirtualDisplay?.release()
        } catch (_: Exception) {
        }
        activeVirtualDisplay = null

        try {
            activeImageReader?.setOnImageAvailableListener(null, null)
            activeImageReader?.close()
        } catch (_: Exception) {
        }
        activeImageReader = null

        if (stopProjection) {
            try {
                mediaProjection?.stop()
            } catch (_: Exception) {
            }
            mediaProjection = null
            projectionCallbackRegistered = false
        }
    }

    private fun loadSavedConfigs(): List<NamingConfig> {
        val prefs = getSharedPreferences("mewname_prefs", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("saved_presets", null) ?: return listOf(NamingConfig(name = "Padrão"))
        
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<NamingConfig>()
            for (i in 0 until jsonArray.length()) {
                list += jsonToNamingConfig(jsonArray.getJSONObject(i))
            }
            list
        } catch (e: Exception) {
            listOf(NamingConfig(name = "Padrão"))
        }
    }

    private fun showResultsOverlay(
        results: List<Pair<String, String>>,
        reviewRecommended: Boolean = false,
        onOpenReview: (() -> Unit)? = null
    ) {
        removeResultsOverlay()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.6f
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 34f
                setColor(Color.argb(208, 255, 255, 255))
            }
            setPadding(60, 60, 60, 60)
            elevation = 40f
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 24)
        }
        val title = TextView(this).apply {
            text = "Nomes sugeridos"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val helpBtn = ImageView(this).apply {
            runCatching {
                assets.open("Unown_Qu.png").use { stream ->
                    setImageBitmap(BitmapFactory.decodeStream(stream))
                }
            }.onFailure {
                setImageResource(android.R.drawable.ic_menu_help)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setPadding(4, 4, 4, 4)
            layoutParams = LinearLayout.LayoutParams(58, 58)
            contentDescription = "Ajuda"
        }
        titleRow.addView(title)
        titleRow.addView(helpBtn)
        layout.addView(titleRow)

        val helpTextView = TextView(this).apply {
            text = buildString {
                append(if (results.isEmpty()) {
                    "Nenhum nome foi gerado para esta captura."
                } else {
                    "Toque em uma opção para copiar o nome."
                })
                if (reviewRecommended) {
                    append("\n\nAlguns dados merecem revisão antes de usar o nome.")
                }
            }
            textSize = 13f
            setTextColor(Color.rgb(87, 96, 112))
            setPadding(0, 0, 0, 18)
            visibility = View.GONE
        }
        helpBtn.setOnClickListener {
            helpTextView.visibility = if (helpTextView.visibility == View.VISIBLE) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }
        layout.addView(helpTextView)

        results.forEach { (configName, generatedName) ->
            val btnLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 20, 0, 20)
                isClickable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener {
                    copyToClipboard(generatedName)
                    Toast.makeText(this@OverlayService, "Copiado!", Toast.LENGTH_SHORT).show()
                    windowManager.removeView(layout)
                    resultsView = null
                }
            }
            
            val label = TextView(this).apply {
                text = configName
                textSize = 12f
                setTextColor(Color.GRAY)
            }
            val value = TextView(this).apply {
                text = generatedName
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.BLACK)
            }
            
            btnLayout.addView(label)
            btnLayout.addView(value)
            layout.addView(btnLayout)
            
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
                setBackgroundColor(Color.LTGRAY)
            }
            layout.addView(divider)
        }

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 24, 0, 0)
        }

        fun actionButton(label: String, isLast: Boolean = false, onClick: () -> Unit): TextView {
            return TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(48, 63, 84))
                minHeight = 0
                minimumHeight = 0
                minimumWidth = 0
                setPadding(14, 22, 14, 22)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 28f
                    setColor(Color.argb(245, 248, 250, 255))
                    setStroke(2, Color.rgb(205, 214, 226))
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = if (isLast) 0 else 8
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        }

        if (onOpenReview != null) {
            actionsRow.addView(
                actionButton("Revisar dados") { onOpenReview() }
            )
        }

        actionsRow.addView(
            actionButton("Exportar log") { exportBubbleLog() }
        )

        actionsRow.addView(
            actionButton("Fechar", isLast = true) {
                windowManager.removeView(layout)
                resultsView = null
            }
        )

        layout.addView(actionsRow)

        windowManager.addView(layout, params)
        resultsView = layout
    }

    private fun showReviewOverlay(
        parsed: com.mewname.app.model.PokemonScreenData,
        fields: List<NamingField>,
        configs: List<NamingConfig>,
        bitmap: Bitmap?
    ) {
        removeResultsOverlay()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            dimAmount = 0.6f
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 16.dp)
                    ) {
                        ReviewEditorCard(
                            initialData = parsed,
                            fields = fields,
                            configs = configs,
                            bitmap = bitmap,
                            onExportLog = { exportBubbleLog() },
                            onCancel = { removeResultsOverlay() },
                            onConfirm = { reviewed ->
                                val results = configs.mapNotNull { config ->
                                    val generatedName = generator.generate(reviewed, config).trim()
                                    generatedName.takeIf { it.isNotEmpty() }?.let { config.name to it }
                                }
                                val reviewRecommended = shouldOpenReview(reviewed, fields)
                                lastBubbleLogSnapshot = lastBubbleLogSnapshot?.copy(
                                    reviewedData = reviewed,
                                    generatedResults = results
                                )
                                removeResultsOverlay()
                                showResultsOverlay(
                                    results = results,
                                    reviewRecommended = reviewRecommended,
                                    onOpenReview = {
                                        showReviewOverlay(reviewed, fields, configs, bitmap)
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        windowManager.addView(composeView, params)
        resultsView = composeView
    }

    private fun removeResultsOverlay() {
        resultsView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        resultsView = null
    }

    private fun reviewableFields(configs: List<NamingConfig>): List<NamingField> {
        return configs.flatMap { config ->
            config.effectiveBlocks()
                .filter { block -> block.type == com.mewname.app.model.NamingBlockType.VARIABLE }
                .mapNotNull { block -> block.field }
        }.distinct()
    }

    private fun shouldOpenReview(
        data: com.mewname.app.model.PokemonScreenData,
        fields: List<NamingField>
    ): Boolean {
        return fields.any { field ->
            when (field) {
                NamingField.POKEMON_NAME -> data.pokemonName.isNullOrBlank()
                NamingField.VIVILLON_PATTERN -> isVivillonFamily(data.pokemonName) && data.vivillonPattern == null
                NamingField.CP -> data.cp == null
                NamingField.IV_PERCENT -> data.ivPercent == null
                NamingField.IV_COMBINATION -> data.attIv == null || data.defIv == null || data.staIv == null
                NamingField.LEVEL -> data.level == null
                NamingField.GENDER -> data.gender == Gender.UNKNOWN
                NamingField.SIZE -> data.size == PokemonSize.NORMAL
                NamingField.PVP_LEAGUE -> data.pvpLeague == null
                NamingField.PVP_RANK -> data.pvpRank == null
                NamingField.EVOLUTION_TYPE -> data.evolutionFlags.isEmpty()
                else -> false
            }
        }
    }

    private fun isVivillonFamily(name: String?): Boolean {
        return when (name?.trim()?.uppercase()) {
            "SCATTERBUG", "SPEWPA", "VIVILLON" -> true
            else -> false
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Pokemon Name", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun exportBubbleLog() {
        val snapshot = lastBubbleLogSnapshot
        if (snapshot == null) {
            Toast.makeText(this, "Nenhum log da bolha disponivel ainda.", Toast.LENGTH_SHORT).show()
            return
        }

        val exportText = buildBubbleLogExport(snapshot)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "MewName - Log do modo bolha")
            putExtra(Intent.EXTRA_TEXT, exportText)
        }
        val chooser = Intent.createChooser(shareIntent, "Exportar log do modo bolha").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(chooser)
    }

    private fun buildBubbleLogExport(snapshot: BubbleLogSnapshot): String {
        return buildString {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            appendLine("MewName - Log do modo bolha")
            appendLine("Capturado em: ${formatter.format(Date(snapshot.capturedAtMillis))}")
            appendLine("Bitmap: ${snapshot.bitmapWidth}x${snapshot.bitmapHeight}")
            appendLine("Campos revisaveis: ${snapshot.reviewableFields.joinToString { it.name }}")
            appendLine()

            appendPokemonSection(
                title = "Dados detectados",
                data = snapshot.parsedData,
                bitmapWidth = snapshot.bitmapWidth,
                bitmapHeight = snapshot.bitmapHeight
            )

            snapshot.reviewedData?.let { reviewed ->
                appendLine()
                appendPokemonSection(
                    title = "Dados revisados",
                    data = reviewed,
                    bitmapWidth = snapshot.bitmapWidth,
                    bitmapHeight = snapshot.bitmapHeight
                )
            }

            if (snapshot.generatedResults.isNotEmpty()) {
                appendLine()
                appendLine("Sugestoes de nome")
                snapshot.generatedResults.forEach { (configName, generatedName) ->
                    appendLine("- $configName: $generatedName")
                }
            }

            appendLine()
            appendLine("OCR bruto")
            appendLine(snapshot.rawText.ifBlank { "-" })

            if (snapshot.ocrLineLogs.isNotEmpty()) {
                appendLine()
                appendLine("Linhas OCR")
                snapshot.ocrLineLogs.forEach { line ->
                    appendLine(line)
                }
            }
        }
    }

    private fun StringBuilder.appendPokemonSection(
        title: String,
        data: PokemonScreenData,
        bitmapWidth: Int,
        bitmapHeight: Int
    ) {
        appendLine(title)
        appendLine("Pokemon: ${data.pokemonName ?: "-"}")
        appendLine("Familia doce: ${data.candyFamilyName ?: "-"}")
        appendLine("CP: ${data.cp ?: "-"}")
        appendLine("IV: ${data.attIv ?: "-"}/${data.defIv ?: "-"}/${data.staIv ?: "-"}")
        appendLine("IV %: ${data.ivPercent ?: "-"}")
        appendLine("Nivel: ${data.level ?: "-"}")
        appendLine("Genero: ${data.gender.name}")
        appendLine("Tipo: ${listOfNotNull(data.type1, data.type2).joinToString("/").ifBlank { "-" }}")
        appendLine("PvP: ${data.pvpLeague?.name ?: "-"} | Rank: ${data.pvpRank ?: "-"}")
        if (data.pvpLeagueRanks.isNotEmpty()) {
            appendLine("PvP por liga:")
            data.pvpLeagueRanks.forEach { info ->
                appendLine(
                    "- ${info.league.name}: pokemon=${info.pokemonName ?: "-"} ${if (info.eligible) "rank=${info.rank ?: "-"} cp=${info.bestCp ?: "-"} nivel=${info.bestLevel ?: "-"}" else info.description}"
                )
                info.stadiumUrl?.let { url ->
                    appendLine("  Stadium: $url")
                }
            }
        }
        appendLine("Tamanho: ${data.size.name}")
        appendLine("Flags: ${buildFlagSummary(data)}")
        appendIvSection(data.ivDebugInfo, bitmapWidth, bitmapHeight)
        appendAuxiliaryDebugSection(data)
    }

    private fun StringBuilder.appendIvSection(
        info: IvDebugInfo?,
        bitmapWidth: Int,
        bitmapHeight: Int
    ) {
        appendLine("IV log")
        if (info == null) {
            appendLine("Appraisal detectada: false")
            appendLine("Leitura IV: sem debug disponivel")
            return
        }
        appendLine("Appraisal detectada: ${info.appraisalDetected}")
        appendLine("Barras detectadas: ${info.detectedBars}")
        appendLine("Confiavel: ${info.reliable}")
        appendLine("IV OCR: ${info.percentFromOcr ?: "-"}")
        appendLine("IV final: ${info.percentFinal ?: "-"}")
        appendLine("Atk ratio: ${info.attackRatio?.formatDebugValue() ?: "-"} -> ${info.attackDetected ?: "-"}")
        appendLine("Def ratio: ${info.defenseRatio?.formatDebugValue() ?: "-"} -> ${info.defenseDetected ?: "-"}")
        appendLine("HP ratio: ${info.staminaRatio?.formatDebugValue() ?: "-"} -> ${info.staminaDetected ?: "-"}")
        if (info.attackMeasurementDebug.isNotBlank()) appendLine("Atk dbg: ${info.attackMeasurementDebug}")
        if (info.defenseMeasurementDebug.isNotBlank()) appendLine("Def dbg: ${info.defenseMeasurementDebug}")
        if (info.staminaMeasurementDebug.isNotBlank()) appendLine("HP dbg: ${info.staminaMeasurementDebug}")
        debugRectSummary("Painel", info.appraisalPanelRect, bitmapWidth, bitmapHeight)?.let(::appendLine)
        debugRectSummary("Atk", info.attackBarRect, bitmapWidth, bitmapHeight)?.let(::appendLine)
        debugRectSummary("Def", info.defenseBarRect, bitmapWidth, bitmapHeight)?.let(::appendLine)
        debugRectSummary("HP", info.staminaBarRect, bitmapWidth, bitmapHeight)?.let(::appendLine)
        if (info.notes.isNotBlank()) appendLine("Obs IV: ${info.notes}")
    }

    private fun StringBuilder.appendAuxiliaryDebugSection(data: PokemonScreenData) {
        data.candyDebugInfo?.let { info ->
            appendLine("Candy dbg: linhas=${info.regionLineCount} familia=${info.resolvedFamilyName ?: "-"} raw=${info.extractedFamilyRaw ?: "-"}")
            if (info.notes.isNotBlank()) appendLine("Candy obs: ${info.notes}")
        }
        data.backgroundDebugInfo?.let { info ->
            appendLine(
                "Background dbg: texto=${info.textMatch} topo=${info.topRegionMatch} ref=${info.referenceDecision ?: "-"} nome=${info.referenceName ?: "-"} distancia=${info.referenceDistance?.formatDebugValue() ?: "-"} fallbackCor=${info.colorFallbackMatch}"
            )
            if (info.notes.isNotBlank()) appendLine("Background obs: ${info.notes}")
        }
        data.adventureEffectDebugInfo?.let { info ->
            appendLine(
                "Adventure dbg: pokemon=${info.matchedPokemon ?: "-"} keyword=${info.matchedKeyword ?: "-"} golpe=${info.matchedMove ?: "-"} efeito=${info.matchedEffectName ?: "-"}"
            )
            if (info.notes.isNotBlank()) appendLine("Adventure obs: ${info.notes}")
        }
        data.legacyDebugInfo?.let { info ->
            appendLine(
                "Legacy dbg: pokemon=${info.matchedAgainstPokemon ?: "-"} keyword=${info.matchedKeyword ?: "-"} golpe=${info.matchedLegacyMove ?: "-"}"
            )
            if (info.notes.isNotBlank()) appendLine("Legacy obs: ${info.notes}")
        }
    }

    private fun buildFlagSummary(data: PokemonScreenData): String {
        return buildList {
            if (data.isFavorite) add("favorite")
            if (data.isLucky) add("lucky")
            if (data.isShadow) add("shadow")
            if (data.isPurified) add("purified")
            if (data.hasSpecialBackground) add("special_background")
            if (data.hasAdventureEffect) add("adventure_effect")
            if (data.hasLegacyMove) add("legacy_move")
            addAll(data.evolutionFlags.map { it.name.lowercase(Locale.US) })
        }.ifEmpty { listOf("none") }.joinToString(", ")
    }

    private fun buildOcrLineLogs(ocrResult: OcrResult): List<String> {
        return ocrResult.blocks
            .flatMap { it.lines }
            .mapIndexed { index, line ->
                val rect = line.boundingBox
                val rectSummary = if (rect != null) {
                    "x=${rect.left} y=${rect.top} w=${rect.width()} h=${rect.height()}"
                } else {
                    "sem-rect"
                }
                "${index + 1}. [$rectSummary] ${line.text.replace('\n', ' ')}"
            }
    }

    private fun debugRectSummary(
        label: String,
        rect: NormalizedDebugRect?,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): String? {
        rect ?: return null
        val left = (rect.left * bitmapWidth).roundToInt()
        val top = (rect.top * bitmapHeight).roundToInt()
        val right = (rect.right * bitmapWidth).roundToInt()
        val bottom = (rect.bottom * bitmapHeight).roundToInt()
        val width = (right - left).coerceAtLeast(0)
        val height = (bottom - top).coerceAtLeast(0)
        return "$label px: x=$left y=$top w=$width h=$height"
    }

    private fun Float.formatDebugValue(): String = String.format(Locale.US, "%.3f", this)

    private fun Double.formatDebugValue(): String = String.format(Locale.US, "%.3f", this)

    private fun notifyCapturePermissionUnavailable() {
        sendBroadcast(Intent(ACTION_CAPTURE_PERMISSION_INVALID).setPackage(packageName))
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        overlayViewModelStore.clear()
        super.onDestroy()
        floatingButton?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        resultsView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        loadingView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        dismissTargetView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        cleanupCaptureResources()
    }
}
