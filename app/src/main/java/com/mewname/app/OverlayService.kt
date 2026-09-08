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
import android.provider.Settings
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
import androidx.compose.foundation.layout.fillMaxSize
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
import com.mewname.app.domain.BattleAdvice
import com.mewname.app.domain.BattleAdvisor
import com.mewname.app.domain.BattleMode
import com.mewname.app.domain.NameGenerator
import com.mewname.app.domain.OcrPokemonParser
import com.mewname.app.domain.PokemonReadSessionMerger
import com.mewname.app.domain.UniquePokemonCatalog
import com.mewname.app.domain.ReviewPolicy
import com.mewname.app.model.EvolutionFlag
import com.mewname.app.model.EvolutionIconDebugInfo
import com.mewname.app.model.Gender
import com.mewname.app.model.IvDebugInfo
import com.mewname.app.model.LevelDebugInfo
import com.mewname.app.model.NamingConfig
import com.mewname.app.model.NamingField
import com.mewname.app.model.NormalizedDebugRect
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.PokemonSize
import com.mewname.app.model.PvpLeague
import com.mewname.app.model.AdventureEffectDebugInfo
import com.mewname.app.model.BackgroundDebugInfo
import com.mewname.app.model.LegacyDebugInfo
import com.mewname.app.model.VivillonPattern
import com.mewname.app.model.effectiveBlocks
import com.mewname.app.model.defaultNamingConfigs
import com.mewname.app.model.ensureBuiltInNamingConfigs
import com.mewname.app.ocr.OcrEngine
import com.mewname.app.ocr.OcrResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import kotlin.math.abs

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    companion object {
        private val bubbleActive = kotlinx.coroutines.flow.MutableStateFlow(false)
        val isBubbleActive: kotlinx.coroutines.flow.StateFlow<Boolean> = bubbleActive
        private const val TAG = "OverlayService"
        private const val CAPTURE_AFTER_HIDE_DELAY_MS = 260L
        private const val OVERLAY_PERMISSION_CHECK_INTERVAL_MS = 2_500L
        const val ACTION_CAPTURE_PERMISSION_INVALID = "com.mewname.app.action.CAPTURE_PERMISSION_INVALID"
        const val ACTION_OVERLAY_PERMISSION_INVALID = "com.mewname.app.action.OVERLAY_PERMISSION_INVALID"
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

    private data class BattleLogSnapshot(
        val capturedAtMillis: Long,
        val bitmapWidth: Int,
        val bitmapHeight: Int,
        val stage: String,
        val rawText: String,
        val ocrLineLogs: List<String>,
        val advice: BattleAdvice
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
    private var closingForPermissionLoss = false
    private var loadingView: View? = null
    private var dismissTargetView: View? = null
    private var bubbleDismissMode = false
    private var loadingTitleView: TextView? = null
    private var loadingDetailView: TextView? = null
    private val serviceJob = kotlinx.coroutines.SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val attachedOverlays = mutableSetOf<View>()
    private var captureProcessingJob: kotlinx.coroutines.Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val overlayPermissionWatchdog = object : Runnable {
        override fun run() {
            if (!Settings.canDrawOverlays(this@OverlayService)) {
                closeBubbleForPermissionLoss(overlayPermissionLost = true, reason = "Permissao de sobreposicao revogada")
                return
            }
            mainHandler.postDelayed(this, OVERLAY_PERMISSION_CHECK_INTERVAL_MS)
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val overlayViewModelStore = ViewModelStore()
    
    private val parser = OcrPokemonParser()
    private val generator = NameGenerator()
    private val ocrEngine = OcrEngine()
    private val sessionMerger = PokemonReadSessionMerger()
    private var lastCapturedData: PokemonScreenData? = null
    private var lastBubbleLogSnapshot: BubbleLogSnapshot? = null
    private var lastBattleLogSnapshot: BattleLogSnapshot? = null
    private val bubbleLongPressTimeoutMillis = 1500L

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
        if (!Settings.canDrawOverlays(this)) {
            closeBubbleForPermissionLoss(
                overlayPermissionLost = true,
                reason = "Permissao de sobreposicao indisponivel ao iniciar"
            )
            return
        }
        showFloatingButton()
        bubbleActive.value = !closingForPermissionLoss && floatingButton in attachedOverlays
        if (!closingForPermissionLoss) mainHandler.post(overlayPermissionWatchdog)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (closingForPermissionLoss) return START_NOT_STICKY
        if (intent?.action == "STOP_SERVICE") {
            stopOverlayService()
            return START_NOT_STICKY
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
        
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopOverlayService()
        super.onTaskRemoved(rootIntent)
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

    private fun addOverlayView(view: View, params: WindowManager.LayoutParams): Boolean {
        if (closingForPermissionLoss) {
            (view as? ComposeView)?.disposeComposition()
            return false
        }
        if (!Settings.canDrawOverlays(this)) {
            closeBubbleForPermissionLoss(overlayPermissionLost = true, reason = "Permissao de sobreposicao revogada")
            return false
        }
        return try {
            windowManager.addView(view, params)
            attachedOverlays.add(view)
            true
        } catch (error: WindowManager.BadTokenException) {
            Log.w(TAG, "Android recusou uma janela de sobreposicao", error)
            closeBubbleForPermissionLoss(overlayPermissionLost = true, reason = "Android recusou a sobreposicao")
            false
        } catch (error: SecurityException) {
            Log.w(TAG, "Android recusou uma janela de sobreposicao", error)
            closeBubbleForPermissionLoss(overlayPermissionLost = true, reason = "Android recusou a sobreposicao")
            false
        }
    }

    private fun updateOverlayViewLayout(view: View?, params: WindowManager.LayoutParams): Boolean {
        view ?: return false
        if (closingForPermissionLoss) return false
        return try {
            windowManager.updateViewLayout(view, params)
            true
        } catch (error: WindowManager.BadTokenException) {
            Log.w(TAG, "Android recusou a atualizacao da sobreposicao", error)
            closeBubbleForPermissionLoss(overlayPermissionLost = true, reason = "Android recusou a sobreposicao")
            false
        } catch (error: SecurityException) {
            Log.w(TAG, "Android recusou a atualizacao da sobreposicao", error)
            closeBubbleForPermissionLoss(overlayPermissionLost = true, reason = "Android recusou a sobreposicao")
            false
        }
    }

    private fun closeBubbleForPermissionLoss(overlayPermissionLost: Boolean, reason: String) {
        if (closingForPermissionLoss) return
        Log.w(TAG, reason)
        shutdownOverlays()
        val action = if (overlayPermissionLost) ACTION_OVERLAY_PERMISSION_INVALID else ACTION_CAPTURE_PERMISSION_INVALID
        sendBroadcast(Intent(action).setPackage(packageName))
        stopOverlayService()
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
                        updateOverlayViewLayout(floatingButton, params)
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
                                stopOverlayService()
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

        floatingButton?.let { addOverlayView(it, params) }
    }

    private fun showDismissTarget() {
        if (dismissTargetView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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
        addOverlayView(container, params)
        updateDismissTargetHighlight(false)
    }

    private fun hideDismissTarget() {
        dismissTargetView?.let {
            try {
                detachOverlay(it)
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

    private fun setBubbleHiddenForCapture(hidden: Boolean) {
        val bubble = floatingButton ?: return
        bubble.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        bubble.isEnabled = !hidden
    }

    private fun drainPendingCaptureFrames(reader: ImageReader) {
        while (true) {
            val staleImage = try {
                reader.acquireLatestImage()
            } catch (_: Exception) {
                null
            } ?: break
            staleImage.close()
        }
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

        if (!Settings.canDrawOverlays(this)) {
            closeBubbleForPermissionLoss(
                overlayPermissionLost = true,
                reason = "Permissao de sobreposicao revogada durante a captura"
            )
            return
        }

        val reader = activeImageReader ?: run {
            closeBubbleForPermissionLoss(
                overlayPermissionLost = false,
                reason = "Sessao de captura indisponivel"
            )
            return
        }

        isCaptureInProgress = true
        removeResultsOverlay()
        drainPendingCaptureFrames(reader)
        setBubbleHiddenForCapture(true)
        mainHandler.postDelayed({
            val image = try {
                reader.acquireLatestImage()
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao obter imagem da sessao ativa", e)
                null
            }
            setBubbleHiddenForCapture(false)

            if (image == null) {
                isCaptureInProgress = false
                Toast.makeText(this, "Falha momentanea na captura. Tente novamente.", Toast.LENGTH_SHORT).show()
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
        }, CAPTURE_AFTER_HIDE_DELAY_MS)
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
                        closeBubbleForPermissionLoss(overlayPermissionLost = false, reason = "Autorizacao de captura encerrada pelo Android")
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
            closeBubbleForPermissionLoss(overlayPermissionLost = false, reason = "Falha ao iniciar a captura")
        }
    }

    private fun processCapturedBitmap(bitmap: Bitmap) {
        showLoadingOverlay()
        updateLoadingStatus(detail = "Lendo tela de batalha")
        captureProcessingJob = serviceScope.launch {
            runCatching {
                withTimeoutOrNull(5500L) {
                    ocrEngine.extractBattlePreview(bitmap)
                }
            }.onSuccess { previewOcrResult ->
                if (previewOcrResult != null) {
                    val rawBattleAdvice = kotlinx.coroutines.withContext(Dispatchers.Default) {
                        BattleAdvisor.adviceForRaw(this@OverlayService, previewOcrResult.fullText)
                    }
                    rawBattleAdvice?.let { battleAdvice ->
                        lastBattleLogSnapshot = BattleLogSnapshot(
                            capturedAtMillis = System.currentTimeMillis(),
                            bitmapWidth = bitmap.width,
                            bitmapHeight = bitmap.height,
                            stage = "preview",
                            rawText = previewOcrResult.fullText,
                            ocrLineLogs = buildOcrLineLogs(previewOcrResult),
                            advice = battleAdvice
                        )
                        updateLoadingStatus(detail = "Montando sugestoes de batalha")
                        showBattleSuggestionsOverlay(battleAdvice)
                        isCaptureInProgress = false
                        removeLoadingOverlay()
                        return@onSuccess
                    }
                } else {
                    Log.w(TAG, "OCR preview de batalha excedeu o tempo limite")
                }
                updateLoadingStatus(detail = "Extraindo texto da imagem")
                val ocrResult = withTimeoutOrNull(14000L) {
                    ocrEngine.extract(bitmap)
                } ?: throw IllegalStateException("Tempo limite ao extrair texto da imagem")
                val parsed = withTimeoutOrNull(25_000L) {
                    kotlinx.coroutines.withContext(Dispatchers.Default) {
                        parser.parse(this@OverlayService, ocrResult) { step ->
                            updateLoadingStatus(detail = step)
                        }
                    }
                } ?: throw IllegalStateException("Tempo limite ao analisar os dados da imagem")
                updateLoadingStatus(detail = "Montando nomes sugeridos")
                val merged = sessionMerger.mergeIfSamePokemon(parsed, lastCapturedData)
                lastCapturedData = merged
                val savedConfigs = loadSavedConfigs()
                val generatedResults = savedConfigs.mapNotNull { config ->
                    val generatedName = generator.generate(merged, config).trim()
                    generatedName.takeIf { it.isNotEmpty() }?.let { config.name to it }
                }

                val reviewFields = ReviewPolicy.reviewableFields(savedConfigs)
                val needsReview = ReviewPolicy.shouldOpenReview(merged, reviewFields)
                val battleAdvice = BattleAdvisor.adviceFor(
                    context = this@OverlayService,
                    data = merged,
                    rawText = ocrResult.fullText
                )
                lastBubbleLogSnapshot = BubbleLogSnapshot(
                    capturedAtMillis = System.currentTimeMillis(),
                    bitmapWidth = bitmap.width,
                    bitmapHeight = bitmap.height,
                    rawText = ocrResult.fullText,
                    ocrLineLogs = buildOcrLineLogs(ocrResult),
                    parsedData = merged,
                    reviewableFields = reviewFields,
                    generatedResults = generatedResults
                )
                val hasIdentifiedPokemon = !merged.pokemonName.isNullOrBlank() || !merged.candyFamilyName.isNullOrBlank()
                if (battleAdvice != null) {
                    lastBattleLogSnapshot = BattleLogSnapshot(
                        capturedAtMillis = System.currentTimeMillis(),
                        bitmapWidth = bitmap.width,
                        bitmapHeight = bitmap.height,
                        stage = "full",
                        rawText = ocrResult.fullText,
                        ocrLineLogs = buildOcrLineLogs(ocrResult),
                        advice = battleAdvice
                    )
                    showBattleSuggestionsOverlay(battleAdvice)
                } else if (!hasIdentifiedPokemon && generatedResults.isEmpty()) {
                    showUnsupportedBubbleScreenOverlay()
                } else if (needsReview || generatedResults.isNotEmpty()) {
                    showReviewOverlay(merged, reviewFields, savedConfigs, bitmap)
                } else {
                    showResultsOverlay(generatedResults)
                }

                isCaptureInProgress = false
                removeLoadingOverlay()
            }.onFailure { error ->
                if (error !is CancellationException) {
                    Log.e(TAG, "Falha no OCR da bolha", error)
                    Toast.makeText(this@OverlayService, "Nao foi possivel ler a imagem.", Toast.LENGTH_SHORT).show()
                }
            }.also {
                // Always release the overlay after errors and cancellations.
                captureProcessingJob = null
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
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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
                loadingTitleView = this
            })
            addView(TextView(this@OverlayService).apply {
                text = "Preparando análise"
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                loadingDetailView = this
            })
        }

        layout.addView(TextView(this).apply {
            text = "Cancelar"
            setTextColor(Color.LTGRAY)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
            isClickable = true
            setOnClickListener { cancelCaptureProcessing() }
        })
        addOverlayView(layout, params)
        loadingView = layout
    }

    private fun cancelCaptureProcessing() {
        captureProcessingJob?.cancel()
        captureProcessingJob = null
        isCaptureInProgress = false
        removeLoadingOverlay()
        Toast.makeText(this, "Processo cancelado.", Toast.LENGTH_SHORT).show()
    }

    private fun removeLoadingOverlay() {
        loadingView?.let {
            try { detachOverlay(it) } catch (_: Exception) {}
        }
        loadingView = null
        loadingTitleView = null
        loadingDetailView = null
    }

    private fun updateLoadingStatus(title: String? = null, detail: String) {
        mainHandler.post {
            title?.let { loadingTitleView?.text = it }
            loadingDetailView?.text = detail
        }
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
        val jsonString = prefs.getString(SAVED_PRESETS_KEY, null) ?: return defaultNamingConfigs()
        return runCatching { decodeSavedPresets(jsonString).configs }
            .map(::ensureBuiltInNamingConfigs)
            .getOrElse { defaultNamingConfigs() }
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
                    detachOverlay(layout)
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
            actionButton("Fechar", isLast = true) {
                detachOverlay(layout)
                resultsView = null
            }
        )

        layout.addView(actionsRow)

        addOverlayView(layout, params)
        resultsView = layout
    }

    private fun showUnsupportedBubbleScreenOverlay() {
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
                setColor(Color.argb(232, 255, 255, 255))
            }
            setPadding(52, 52, 52, 52)
            elevation = 40f
        }

        layout.addView(TextView(this).apply {
            text = "Tela nao reconhecida"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 12)
        })

        layout.addView(TextView(this).apply {
            text = "A bolha pode ser usada nestas telas:"
            textSize = 14f
            setTextColor(Color.rgb(65, 72, 86))
            setPadding(0, 0, 0, 18)
        })

        fun supportedScreenRow(title: String, description: String): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12, 0, 12)
                addView(TextView(this@OverlayService).apply {
                    text = title
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.BLACK)
                })
                addView(TextView(this@OverlayService).apply {
                    text = description
                    textSize = 13f
                    setTextColor(Color.rgb(87, 96, 112))
                    setPadding(0, 4, 0, 0)
                })
            }
        }

        layout.addView(supportedScreenRow("Detalhes do Pokemon", "Gera nomes e permite revisar os dados lidos."))
        layout.addView(supportedScreenRow("Tela de Raid", "Sugere counters e copia o filtro para busca."))
        layout.addView(supportedScreenRow("Tela de Dynamax/Gigamax", "Sugere Pokemon validos para a batalha Max."))

        layout.addView(TextView(this).apply {
            text = "Abra uma dessas telas no Pokemon GO e toque na bolha novamente."
            textSize = 13f
            setTextColor(Color.rgb(87, 96, 112))
            setPadding(0, 18, 0, 22)
        })

        val closeButton = TextView(this).apply {
            text = "Fechar"
            gravity = Gravity.CENTER
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(48, 63, 84))
            setPadding(18, 24, 18, 24)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28f
                setColor(Color.argb(245, 248, 250, 255))
                setStroke(2, Color.rgb(205, 214, 226))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                detachOverlay(layout)
                resultsView = null
            }
        }
        layout.addView(closeButton)

        addOverlayView(layout, params)
        resultsView = layout
    }

    private fun showBattleSuggestionsOverlay(advice: BattleAdvice) {
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
                setColor(Color.argb(232, 255, 255, 255))
            }
            setPadding(52, 52, 52, 52)
            elevation = 40f
        }

        layout.addView(TextView(this).apply {
            text = if (advice.mode == BattleMode.MAX) "Sugestoes para Dynamax" else "Sugestoes para Raid"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 10)
        })

        layout.addView(TextView(this).apply {
            text = buildString {
                append("Chefe: ${advice.bossName ?: "nao identificado"}")
                if (advice.bossName.isNullOrBlank()) {
                    append("\nTente capturar a tela novamente com o nome do chefe visivel.")
                }
                if (advice.bossTypes.isNotEmpty()) {
                    append("\nTipos: ${advice.bossTypes.joinToString(" / ")}")
                }
                if (advice.weaknessTypes.isNotEmpty()) {
                    append("\nFraquezas: ${advice.weaknessTypes.joinToString(", ")}")
                }
            }
            textSize = 13f
            setTextColor(Color.rgb(87, 96, 112))
            setPadding(0, 0, 0, 20)
        })

        var currentCopyText = advice.copyText
        lateinit var filterTitle: TextView
        fun showCopiedFeedback(button: TextView? = null) {
            val originalTitle = filterTitle.text
            val originalButtonText = button?.text
            filterTitle.text = "Filtro copiado!"
            button?.text = "Copiado!"
            mainHandler.postDelayed({
                filterTitle.text = originalTitle
                if (originalButtonText != null) {
                    button.text = originalButtonText
                }
            }, 1800)
        }

        val filterBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 22f
                setColor(Color.rgb(241, 246, 255))
                setStroke(2, Color.rgb(207, 219, 239))
            }
            setPadding(24, 18, 24, 18)
            isClickable = true
            setOnClickListener {
                copyToClipboard(currentCopyText)
                showCopiedFeedback()
                Toast.makeText(this@OverlayService, "Filtro copiado!", Toast.LENGTH_SHORT).show()
            }
        }
        filterTitle = TextView(this).apply {
            text = "Copiar para buscar Pokemon"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(48, 63, 84))
        }
        filterBox.addView(filterTitle)
        val filterValueView = TextView(this).apply {
            text = currentCopyText.ifBlank { "-" }
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            setPadding(0, 8, 0, 0)
        }
        filterBox.addView(filterValueView)
        layout.addView(filterBox)

        serviceScope.launch(Dispatchers.Default) {
            val localizedCopyText = BattleAdvisor.copyTextFor(
                context = this@OverlayService,
                mode = advice.mode,
                suggestions = advice.suggestions
            )
            mainHandler.post {
                if (resultsView == layout && localizedCopyText.isNotBlank()) {
                    currentCopyText = localizedCopyText
                    filterValueView.text = localizedCopyText
                }
            }
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                520
            ).apply {
                topMargin = 20
            }
        }
        scrollView.addView(battleSuggestionColumns(advice))
        layout.addView(scrollView)

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
        lateinit var copyFilterButton: TextView
        copyFilterButton = actionButton("Copiar filtro") {
                copyToClipboard(currentCopyText)
                showCopiedFeedback(copyFilterButton)
                Toast.makeText(this@OverlayService, "Filtro copiado!", Toast.LENGTH_SHORT).show()
            }
        actionsRow.addView(copyFilterButton)
        actionsRow.addView(
            actionButton("Log") {
                exportBattleLog()
            }
        )
        actionsRow.addView(
            actionButton("Fechar", isLast = true) {
                detachOverlay(layout)
                resultsView = null
            }
        )
        layout.addView(actionsRow)

        addOverlayView(layout, params)
        resultsView = layout
    }

    private fun battleSuggestionColumns(advice: BattleAdvice): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            addView(
                battleSuggestionColumn(
                    title = "Atacantes",
                    entries = advice.suggestions.take(8)
                )
            )
            addView(
                battleSuggestionColumn(
                    title = "Defensores",
                    entries = advice.defenderSuggestions.take(8).ifEmpty { advice.suggestions.take(8) },
                    isLast = true
                )
            )
        }
    }

    private fun battleSuggestionColumn(
        title: String,
        entries: List<com.mewname.app.domain.BattleSuggestionEntry>,
        isLast: Boolean = false
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = if (isLast) 0 else 14
            }
            addView(TextView(this@OverlayService).apply {
                text = title
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.BLACK)
                setPadding(0, 0, 0, 8)
            })
            entries.forEachIndexed { index, entry ->
                addView(LinearLayout(this@OverlayService).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 10, 0, 10)
                    addView(LinearLayout(this@OverlayService).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(TextView(this@OverlayService).apply {
                            text = "${index + 1}."
                            textSize = 11f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.rgb(87, 96, 112))
                            layoutParams = LinearLayout.LayoutParams(30, LinearLayout.LayoutParams.WRAP_CONTENT)
                        })
                        entry.attackTypes.take(2).forEach { type ->
                            addView(typeIconView(type, 34))
                        }
                    })
                    addView(TextView(this@OverlayService).apply {
                        text = entry.name
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.BLACK)
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setPadding(0, 4, 0, 0)
                    })
                })
                addView(View(this@OverlayService).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
                    setBackgroundColor(Color.LTGRAY)
                })
            }
        }
    }

    private fun typeIconView(type: String, size: Int = 42): ImageView {
        val normalized = type.uppercase(Locale.US)
        val bitmap = runCatching {
            assets.open("types/POKEMON_TYPE_$normalized.png").use(BitmapFactory::decodeStream)
        }.getOrNull()
        return ImageView(this).apply {
            bitmap?.let(::setImageBitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(241, 246, 255))
                setStroke(2, Color.rgb(207, 219, 239))
            }
            setPadding(6, 6, 6, 6)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                rightMargin = 6
            }
        }
    }

    private fun showReviewOverlay(
        parsed: com.mewname.app.model.PokemonScreenData,
        fields: List<NamingField>,
        configs: List<NamingConfig>,
        bitmap: Bitmap?
    ) {
        removeResultsOverlay()

        // The review window must occupy the screen so nested Compose pickers can expand above the card.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
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
                val language = rememberSavedAppLanguage(this@OverlayService)
                androidx.compose.runtime.CompositionLocalProvider(LocalAppLanguage provides language) {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 16.dp)
                    ) {
                        ReviewEditorCard(
                            initialData = parsed,
                            fields = fields,
                            configs = configs,
                            bitmap = bitmap,
                            onExportLog = { selectedFields -> exportBubbleLog(selectedFields) },
                            onCancel = { removeResultsOverlay() },
                            onConfirm = { reviewed ->
                                lastCapturedData = reviewed
                                val results = configs.mapNotNull { config ->
                                    val generatedName = generator.generate(reviewed, config).trim()
                                    generatedName.takeIf { it.isNotEmpty() }?.let { config.name to it }
                                }
                                lastBubbleLogSnapshot = lastBubbleLogSnapshot?.copy(
                                    reviewedData = reviewed,
                                    generatedResults = results
                                )
                                removeResultsOverlay()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                }
            }
        }

        addOverlayView(composeView, params)
        resultsView = composeView
    }

    private fun removeResultsOverlay() {
        resultsView?.let {
            try { detachOverlay(it) } catch (_: Exception) {}
        }
        resultsView = null
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Pokemon Name", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun exportBubbleLog(selectedFields: Set<NamingField>? = null) {
        val snapshot = lastBubbleLogSnapshot
        if (snapshot == null) {
            Toast.makeText(this, "Nenhum log da bolha disponivel ainda.", Toast.LENGTH_SHORT).show()
            return
        }

        val exportText = buildBubbleLogExport(snapshot, selectedFields)
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

    private fun exportBattleLog() {
        val snapshot = lastBattleLogSnapshot
        if (snapshot == null) {
            Toast.makeText(this, "Nenhum log de batalha disponivel ainda.", Toast.LENGTH_SHORT).show()
            return
        }

        val exportText = buildBattleLogExport(snapshot)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "MewName - Log de batalha")
            putExtra(Intent.EXTRA_TEXT, exportText)
        }
        val chooser = Intent.createChooser(shareIntent, "Exportar log de batalha").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(chooser)
    }

    private fun buildBattleLogExport(snapshot: BattleLogSnapshot): String {
        return buildString {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            appendLine("MewName - Log de batalha")
            appendLine("Capturado em: ${formatter.format(Date(snapshot.capturedAtMillis))}")
            appendLine("Bitmap: ${snapshot.bitmapWidth}x${snapshot.bitmapHeight}")
            appendLine("Estagio OCR: ${snapshot.stage}")
            appendLine("Modo: ${if (snapshot.advice.mode == BattleMode.MAX) "Dynamax/Gigamax" else "Raid"}")
            appendLine("Chefe: ${snapshot.advice.bossName ?: "-"}")
            appendLine("Tipos: ${snapshot.advice.bossTypes.joinToString(" / ").ifBlank { "-" }}")
            appendLine("Fraquezas: ${snapshot.advice.weaknessTypes.joinToString(", ").ifBlank { "-" }}")
            appendLine("Filtro: ${snapshot.advice.copyText.ifBlank { "-" }}")
            appendLine()
            appendLine("Atacantes")
            snapshot.advice.suggestions.forEachIndexed { index, entry ->
                appendLine("${index + 1}. ${entry.name} [${entry.attackTypes.joinToString("/")}] termos=${entry.searchTerms.joinToString(",")}")
            }
            appendLine()
            appendLine("Defensores")
            snapshot.advice.defenderSuggestions.forEachIndexed { index, entry ->
                appendLine("${index + 1}. ${entry.name} [${entry.attackTypes.joinToString("/")}] termos=${entry.searchTerms.joinToString(",")}")
            }
            appendLine()
            appendLine("OCR bruto")
            appendLine(snapshot.rawText.ifBlank { "-" })
            if (snapshot.ocrLineLogs.isNotEmpty()) {
                appendLine()
                appendLine("Linhas OCR")
                snapshot.ocrLineLogs.forEach { line -> appendLine(line) }
            }
        }
    }

    private fun buildBubbleLogExport(
        snapshot: BubbleLogSnapshot,
        selectedFields: Set<NamingField>? = null
    ): String {
        val relevantFields = selectedFields?.toList() ?: snapshot.reviewableFields
        val filteredExport = selectedFields != null
        val includeAllWhenEmpty = selectedFields == null
        val includeScreenLogForFilteredExport = filteredExport && relevantFields.any {
            it == NamingField.MASTER_IV_BADGE || it == NamingField.SIZE || it in ivDebugFieldsForExport()
        }
        return buildString {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            appendLine("MewName - Log do modo bolha")
            appendLine("Capturado em: ${formatter.format(Date(snapshot.capturedAtMillis))}")
            appendLine("Bitmap: ${snapshot.bitmapWidth}x${snapshot.bitmapHeight}")
            appendLine("Campos revisaveis: ${snapshot.reviewableFields.joinToString { it.name }}")
            if (filteredExport) {
                appendLine("Blocos exportados: ${relevantFields.joinToString { it.name }}")
            }
            appendLine()

            appendPokemonSection(
                title = "Dados detectados",
                data = snapshot.parsedData,
                bitmapWidth = snapshot.bitmapWidth,
                bitmapHeight = snapshot.bitmapHeight,
                reviewableFields = relevantFields,
                includeAllWhenEmpty = includeAllWhenEmpty
            )

            snapshot.reviewedData?.let { reviewed ->
                appendLine()
                appendPokemonSection(
                    title = "Dados revisados",
                    data = reviewed,
                    bitmapWidth = snapshot.bitmapWidth,
                    bitmapHeight = snapshot.bitmapHeight,
                    reviewableFields = relevantFields,
                    includeAllWhenEmpty = includeAllWhenEmpty
                )
            }

            if (!filteredExport && snapshot.generatedResults.isNotEmpty()) {
                appendLine()
                appendLine("Sugestoes de nome")
                snapshot.generatedResults.forEach { (configName, generatedName) ->
                    appendLine("- $configName: $generatedName")
                }
            }

            if (!filteredExport || includeScreenLogForFilteredExport) {
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
    }

    private fun StringBuilder.appendPokemonSection(
        title: String,
        data: PokemonScreenData,
        bitmapWidth: Int,
        bitmapHeight: Int,
        reviewableFields: List<NamingField>,
        includeAllWhenEmpty: Boolean
    ) {
        val includeAll = includeAllWhenEmpty && reviewableFields.isEmpty()
        fun wants(vararg fields: NamingField): Boolean = includeAll || fields.any { it in reviewableFields }
        val includeMasterIv = wants(NamingField.MASTER_IV_BADGE)

        appendLine(title)
        if (wants(NamingField.POKEMON_NAME, NamingField.CP, NamingField.LEVEL, NamingField.GENDER, NamingField.UNIQUE_FORM, NamingField.VIVILLON_PATTERN, NamingField.TYPE, NamingField.FAVORITE, NamingField.LUCKY, NamingField.SHADOW, NamingField.PURIFIED)) {
            if (wants(NamingField.POKEMON_NAME)) {
                appendLine("Pokemon: ${data.pokemonName ?: "-"}")
                appendLine("Familia doce: ${data.candyFamilyName ?: "-"}")
            }
            if (wants(NamingField.UNIQUE_FORM)) appendLine("Forma unica: ${data.uniqueForm ?: "-"}")
            if (wants(NamingField.VIVILLON_PATTERN)) appendLine("Padrão Vivillon: ${data.vivillonPattern?.label ?: "-"}")
            if (wants(NamingField.CP)) appendLine("CP: ${data.cp ?: "-"}")
            if (wants(NamingField.LEVEL)) appendLine("Nivel: ${data.level ?: "-"}")
            if (wants(NamingField.GENDER)) appendLine("Genero: ${formatGenderForLog(data.gender)}")
            if (wants(NamingField.TYPE)) {
                appendLine("Tipo: ${listOfNotNull(data.type1, data.type2).joinToString("/").ifBlank { "-" }}")
            }
            if (wants(NamingField.FAVORITE)) appendLine("Favorito: ${formatBooleanForLog(data.isFavorite)}")
            if (wants(NamingField.LUCKY)) appendLine("Sortudo: ${formatBooleanForLog(data.isLucky)}")
            if (wants(NamingField.SHADOW)) appendLine("Sombrio: ${formatBooleanForLog(data.isShadow)}")
            if (wants(NamingField.PURIFIED)) appendLine("Purificado: ${formatBooleanForLog(data.isPurified)}")
        }
        if (wants(NamingField.IV_PERCENT, NamingField.IV_COMBINATION)) {
            appendLine("IV: ${data.attIv ?: "-"}/${data.defIv ?: "-"}/${data.staIv ?: "-"}")
            appendLine("IV %: ${data.ivPercent ?: "-"}")
        }
        if (includeMasterIv) {
            appendLine("IV Master: ${formatMasterIvBadgeForLog(data.masterIvBadgeMatch)}")
        }
        if (wants(NamingField.PVP_LEAGUE, NamingField.PVP_RANK)) {
            appendLine("PvP: ${data.pvpLeague?.name ?: "-"} | Rank: ${data.pvpRank ?: "-"} | Pokemon: ${data.pvpPokemonName ?: data.pokemonName ?: "-"}")
        }
        if (wants(NamingField.PVP_LEAGUE, NamingField.PVP_RANK) && data.pvpLeagueRanks.isNotEmpty()) {
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
        if (wants(NamingField.SIZE)) {
            appendLine("Tamanho: ${data.size.name}")
        }
        if (wants(NamingField.SPECIAL_BACKGROUND, NamingField.ADVENTURE_EFFECT, NamingField.EVOLVE_MARKER, NamingField.PURIFY_MARKER, NamingField.LEGACY_MOVE, NamingField.LEGACY_MOVE_NAME, NamingField.EVOLUTION_TYPE)) {
            appendLine("Flags: ${buildFlagSummary(data)}")
        }
        if (wants(NamingField.IV_PERCENT, NamingField.IV_COMBINATION) || includeMasterIv) {
            appendIvSection(data.ivDebugInfo, bitmapWidth, bitmapHeight)
        }
        appendAuxiliaryDebugSection(
            data = data,
            snapshotReviewFields = reviewableFields,
            bitmapWidth = bitmapWidth,
            bitmapHeight = bitmapHeight,
            includeAllWhenEmpty = includeAllWhenEmpty
        )
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

    private fun StringBuilder.appendSizeSection(
        data: PokemonScreenData,
        bitmapWidth: Int,
        bitmapHeight: Int
    ) {
        val info = data.sizeDebugInfo
        appendLine("Size dbg: detectado=${data.size.name} normalQuandoNenhumMarcado=${data.size == PokemonSize.NORMAL}")
        if (info == null) return
        if (info.candidateLines.isNotEmpty()) {
            appendLine("Size linhas: ${info.candidateLines.joinToString(" | ")}")
        }
        appendLine(
            "Size visual: tamanho=${info.visualSize?.name ?: "-"} ratio=${info.visualBadgeRatio?.formatDebugValue() ?: "-"} aspecto=${info.visualBadgeAspect?.formatDebugValue() ?: "-"} texto=${info.textMatch ?: "-"}"
        )
        debugRectSummary("Size selo", info.visualBadgeRect, bitmapWidth, bitmapHeight)?.let(::appendLine)
        if (info.notes.isNotBlank()) appendLine("Size obs: ${info.notes}")
    }

    private fun StringBuilder.appendAuxiliaryDebugSection(
        data: PokemonScreenData,
        snapshotReviewFields: List<NamingField>,
        bitmapWidth: Int,
        bitmapHeight: Int,
        includeAllWhenEmpty: Boolean
    ) {
        val includeAll = includeAllWhenEmpty && snapshotReviewFields.isEmpty()
        fun wants(field: NamingField) = includeAll || field in snapshotReviewFields
        val includeMasterIv = wants(NamingField.MASTER_IV_BADGE)
        data.levelDebugInfo?.let { info ->
            if (wants(NamingField.LEVEL)) {
                appendLine("Level dbg: ${formatLevelDebugSummary(info)}")
                if (info.notes.isNotBlank()) appendLine("Level obs: ${info.notes}")
            }
        }
        data.genderDebugInfo?.let { info ->
            if (wants(NamingField.GENDER)) {
                appendLine("Genero dbg: detectado=${formatGenderForLog(info.detectedGender)}")
                debugRectSummary("Genero", info.iconRect, bitmapWidth, bitmapHeight)?.let(::appendLine)
                if (info.notes.isNotBlank()) appendLine("Genero obs: ${info.notes}")
            }
        }
        data.attributeDebugInfo?.let { info ->
            if (
                wants(NamingField.TYPE) ||
                wants(NamingField.FAVORITE) ||
                wants(NamingField.LUCKY) ||
                wants(NamingField.SHADOW) ||
                wants(NamingField.PURIFIED)
            ) {
                appendLine(
                    "Atributos dbg: tipos=${info.detectedTypes.joinToString("/").ifBlank { "-" }} linhas=${info.typeRegionLines.joinToString(" | ").ifBlank { "-" }} favorito=${info.favoriteFilledMatch} ratio=${info.favoriteYellowRatio?.formatDebugValue() ?: "-"} purificadoTexto=${info.purifiedTextMatch} sortudoTexto=${data.backgroundDebugInfo?.luckyTextMatch ?: false} sortudoVisual=${data.backgroundDebugInfo?.luckyVisualMatch ?: false} sombraTexto=${data.backgroundDebugInfo?.shadowTextMatch ?: false} sombraParticulas=${data.backgroundDebugInfo?.shadowParticleMatch ?: false} sombraTextura=${data.backgroundDebugInfo?.shadowTextureMatch ?: false}"
                )
                if (info.notes.isNotBlank()) appendLine("Atributos obs: ${info.notes}")
            }
        }
        if (wants(NamingField.SIZE)) {
            appendSizeSection(data, bitmapWidth, bitmapHeight)
        }
        data.candyDebugInfo?.let { info ->
            if (wants(NamingField.POKEMON_NAME)) {
                val hasCandySignal = info.resolvedFamilyName != null || info.extractedFamilyRaw != null || info.notes.isNotBlank()
                if (hasCandySignal) {
                    appendLine("Candy dbg: linhas=${info.regionLineCount} familia=${info.resolvedFamilyName ?: "-"} raw=${info.extractedFamilyRaw ?: "-"}")
                }
                if (info.notes.isNotBlank()) appendLine("Candy obs: ${info.notes}")
            }
        }
        if (wants(NamingField.PVP_LEAGUE)) {
            val summary = data.pvpLeagueRanks.joinToString(" | ") { info ->
                "${info.league.name}:${info.pokemonName ?: "-"}#${info.rank ?: "-"}"
            }.ifBlank { "-" }
            appendLine("Liga PvP dbg: atual=${data.pvpLeague?.name ?: "-"} resumo=$summary")
        }
        data.masterIvBadgeDebugInfo?.let { info ->
            if (includeMasterIv) {
                appendLine(
                    "IV Master dbg: suportado=${info.supportedIvPercent} match=${formatMasterIvBadgeForLog(info.isBestMatch)} esperado=${info.expectedAttack ?: "-"}/${info.expectedDefense ?: "-"}/${info.expectedStamina ?: "-"} familia=${info.familyMembers.joinToString("/").ifBlank { "-" }}"
                )
                if (info.notes.isNotBlank()) appendLine("IV Master obs: ${info.notes}")
            }
        }
        if (wants(NamingField.PVP_RANK) && data.familyPvpRanks.isNotEmpty()) {
            val bestSpecies = data.familyPvpRanks
                .groupBy { it.pokemonName }
                .values
                .mapNotNull { ranks ->
                    ranks.filter { it.eligible && it.rank != null }
                        .minByOrNull { it.rank ?: Int.MAX_VALUE }
                        ?: ranks.firstOrNull()
                }
            if (bestSpecies.isNotEmpty()) {
                appendLine(
                    "Ranking PvP dbg: " + bestSpecies.joinToString(" | ") { info ->
                        "${info.pokemonName}:${info.league.name}#${info.rank ?: "-"}"
                    }
                )
            }
        }
        data.backgroundDebugInfo?.let { info ->
            if (wants(NamingField.SPECIAL_BACKGROUND) || data.hasSpecialBackground) {
                appendLine("Background dbg: ${formatBackgroundDebugSummary(info)}")
                if (info.notes.isNotBlank()) appendLine("Background obs: ${info.notes}")
            }
        }
        data.uniqueFormDebugInfo?.let { info ->
            if (wants(NamingField.UNIQUE_FORM) || !data.uniqueForm.isNullOrBlank()) {
                appendLine("Forma unica dbg: categoria=${info.category ?: "-"} melhor=${info.bestLabel ?: "-"} arquivo=${info.bestReferenceName ?: "-"} distancia=${info.bestDistance?.formatDebugValue() ?: "-"} aceita=${info.accepted}")
                debugRectSummary("Forma unica", info.bestCandidateRect, bitmapWidth, bitmapHeight)?.let(::appendLine)
                info.candidateRects.take(4).forEachIndexed { index, rect ->
                    debugRectSummary("Forma unica cand${index + 1}", rect, bitmapWidth, bitmapHeight)?.let(::appendLine)
                }
                if (info.notes.isNotBlank()) appendLine("Forma unica obs: ${info.notes}")
            }
        }
        data.vivillonDebugInfo?.let { info ->
            if (wants(NamingField.VIVILLON_PATTERN)) {
                appendLine(
                    "Vivillon dbg: detectado=${data.vivillonPattern?.label ?: "-"} melhor=${info.bestReferenceName ?: "-"} dist=${info.bestDistance?.formatDebugValue() ?: "-"} segunda=${info.secondReferenceName ?: "-"} dist2=${info.secondDistance?.formatDebugValue() ?: "-"} aceita=${info.accepted} refsDir=unique_pokemon_refs/vivillon"
                )
                debugRectSummary("Vivillon", info.bestCandidateRect, bitmapWidth, bitmapHeight)?.let(::appendLine)
                info.candidateRects.take(4).forEachIndexed { index, rect ->
                    debugRectSummary("Vivillon cand${index + 1}", rect, bitmapWidth, bitmapHeight)?.let(::appendLine)
                }
                if (info.notes.isNotBlank()) appendLine("Vivillon obs: ${info.notes}")
            }
        }
        data.adventureEffectDebugInfo?.let { info ->
            if (wants(NamingField.ADVENTURE_EFFECT) || data.hasAdventureEffect) {
                appendLine("Adventure dbg: ${formatAdventureDebugSummary(info)}")
                if (info.notes.isNotBlank()) appendLine("Adventure obs: ${info.notes}")
            }
        }
        data.legacyDebugInfo?.let { info ->
            if (wants(NamingField.LEGACY_MOVE) || wants(NamingField.LEGACY_MOVE_NAME) || data.hasLegacyMove) {
                if (wants(NamingField.LEGACY_MOVE_NAME)) {
                    appendLine("Ataque legado: ${info.matchedLegacyMove ?: "-"}")
                }
                appendLine("Legacy dbg: ${formatLegacyDebugSummary(info)}")
                if (info.notes.isNotBlank()) appendLine("Legacy obs: ${info.notes}")
            }
        }
        data.evolutionIconDebugInfo?.let { info ->
            if (wants(NamingField.EVOLUTION_TYPE) || info.detectedFlags.isNotEmpty()) {
                appendLine("Icons dbg: ${formatEvolutionIconDebugSummary(info)}")
                if (info.notes.isNotBlank()) appendLine("Icons obs: ${info.notes}")
            }
        }
    }

    private fun formatLevelDebugSummary(info: LevelDebugInfo): String {
        return buildList {
            add("fonte=${info.source.ifBlank { "-" }}")
            add("final=${info.finalLevel ?: "-"}")
            info.ocrLevel?.let { add("ocr=$it") }
            info.curveLevel?.let { add("curva=$it") }
            info.hpLevel?.let { add("hp=$it") }
            info.cp?.let { add("cp=$it") }
            info.maxHp?.let { add("hpMax=$it") }
            info.pokemonName?.takeIf { it.isNotBlank() }?.let { add("pokemon=$it") }
            if (listOf(info.attackIv, info.defenseIv, info.staminaIv).any { it != null }) {
                add("iv=${info.attackIv ?: "-"}/${info.defenseIv ?: "-"}/${info.staminaIv ?: "-"}")
            }
        }.joinToString(" ")
    }

    private fun formatBackgroundDebugSummary(info: BackgroundDebugInfo): String {
        val signals = buildList {
            if (info.textMatch) add("texto")
            if (info.topRegionMatch) add("topo")
            if (info.eventBadgeVisualMatch) add("selo")
            if (info.referenceDecision == true) add("referencia")
            if (info.colorFallbackMatch) add("cor")
            if (info.luckyTextMatch || info.luckyVisualMatch) add("sortudo")
            if (info.shadowTextMatch || info.shadowParticleMatch || info.shadowTextureMatch) add("sombra")
            if (info.shinyParticleMatch) add("shiny")
        }.ifEmpty { listOf("nenhum") }
        return buildList {
            add("sinais=${signals.joinToString(",")}")
            if (info.referenceDecision != null) add("ref=${info.referenceDecision}")
            info.referenceName?.let { add("nome=$it") }
            info.referenceDistance?.let { add("dist=${it.formatDebugValue()}") }
            info.specialReferenceName?.let { add("special=$it") }
            info.specialReferenceDistance?.let { add("specialDist=${it.formatDebugValue()}") }
        }.joinToString(" ")
    }

    private fun formatAdventureDebugSummary(info: AdventureEffectDebugInfo): String {
        return buildList {
            info.matchedPokemon?.let { add("pokemon=$it") }
            info.matchedMove?.let { add("golpe=$it") }
            info.matchedEffectName?.let { add("efeito=$it") }
            info.matchedKeyword?.let { add("keyword=$it") }
            if (isEmpty()) add("sem sinal conclusivo")
        }.joinToString(" ")
    }

    private fun formatLegacyDebugSummary(info: LegacyDebugInfo): String {
        return buildList {
            info.matchedAgainstPokemon?.let { add("pokemon=$it") }
            info.matchedLegacyMove?.let { add("golpe=$it") }
            info.matchedKeyword?.let { add("keyword=$it") }
            if (isEmpty()) add("sem sinal conclusivo")
        }.joinToString(" ")
    }

    private fun formatEvolutionIconDebugSummary(info: EvolutionIconDebugInfo): String {
        return buildList {
            add("flags=${info.detectedFlags.joinToString(", ").ifBlank { "-" }}")
            info.megaKeyword?.let { add("mega=$it") }
            info.gigantamaxKeyword?.let { add("giga=$it") }
            info.dynamaxKeyword?.let { add("dyna=$it") }
        }.joinToString(" ")
    }

    private fun formatGenderForLog(gender: Gender): String {
        return when (gender) {
            Gender.MALE -> "♂"
            Gender.FEMALE -> "♀"
            Gender.GENDERLESS, Gender.UNKNOWN -> "-"
        }
    }

    private fun buildFlagSummary(data: PokemonScreenData): String {
        return buildList {
            if (data.isFavorite) add("favorite")
            if (data.isLucky) add("lucky")
            if (data.isShiny) add("shiny")
            if (data.isShadow) add("shadow")
            if (data.isPurified) add("purified")
            if (data.hasSpecialBackground) add("special_background")
            if (data.hasAdventureEffect) add("adventure_effect")
            if (data.shouldEvolve) add("evolve")
            if (data.shouldPurify) add("purify")
            if (data.hasLegacyMove) add("legacy_move")
            addAll(data.evolutionFlags.map { it.name.lowercase(Locale.US) })
        }.ifEmpty { listOf("none") }.joinToString(", ")
    }

    private fun formatBooleanForLog(value: Boolean): String = if (value) "Sim" else "Nao"

    private fun formatMasterIvBadgeForLog(value: Boolean?): String = when (value) {
        true -> "Sim"
        false -> "Nao"
        null -> "-"
    }

    private fun ivDebugFieldsForExport(): Set<NamingField> = setOf(
        NamingField.IV_PERCENT,
        NamingField.IV_COMBINATION
    )

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
        closeBubbleForPermissionLoss(overlayPermissionLost = false, reason = "Permissao de captura indisponivel")
    }

    private fun detachOverlay(view: View) {
        try {
            (view as? ComposeView)?.disposeComposition()
        } catch (error: Exception) {
            Log.w(TAG, "Falha ao descartar composicao da sobreposicao", error)
        }
        try {
            windowManager.removeViewImmediate(view)
            attachedOverlays.remove(view)
        } catch (error: IllegalArgumentException) {
            // Android may already have detached the window after permission revocation.
            attachedOverlays.remove(view)
            Log.d(TAG, "Janela de sobreposicao ja removida", error)
        } catch (error: Exception) {
            Log.e(TAG, "Falha ao remover janela de sobreposicao", error)
        }
    }

    private fun shutdownOverlays() {
        closingForPermissionLoss = true
        bubbleActive.value = false
        mainHandler.removeCallbacksAndMessages(null)
        serviceJob.cancel()
        captureProcessingJob?.cancel()
        captureProcessingJob = null
        isCaptureInProgress = false
        // Dispose Compose first so its popup windows are dismissed with their owner.
        (attachedOverlays.toList() + listOfNotNull(resultsView, loadingView, dismissTargetView, floatingButton))
            .distinct().forEach(::detachOverlay)
        resultsView = null
        loadingView = null
        dismissTargetView = null
        floatingButton = null
        loadingTitleView = null
        loadingDetailView = null
        cleanupCaptureResources()
    }

    private fun stopOverlayService() {
        shutdownOverlays()
        stopSelf()
    }

    override fun onDestroy() {
        shutdownOverlays()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        overlayViewModelStore.clear()
        super.onDestroy()
    }
}