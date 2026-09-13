package com.mewname.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mewname.app.domain.AppLanguage

internal val LocalLanguageChange = staticCompositionLocalOf<(AppLanguage) -> Unit> { {} }
internal val LocalProfilePanel = staticCompositionLocalOf { false }

@Composable
internal fun AppNavigationShell(
    profileRequested: Boolean,
    isHome: Boolean,
    onProfileRequestConsumed: () -> Unit,
    onGoToPresets: () -> Unit,
    onBubbleOptionVisibleChange: (Boolean) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val language = appLanguage()
    val appearance = LocalAppAppearance.current
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var panelDrag by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val dismissDistance = with(LocalDensity.current) { 72.dp.toPx() }
    val dismissVelocity = with(LocalDensity.current) { 1000.dp.toPx() }
    val panelOffset by animateFloatAsState(panelDrag,
        animationSpec = if (dragging) snap() else spring(), label = "profileDrag")
    LaunchedEffect(showProfile) {
        if (showProfile) { dragging = false; panelDrag = 0f }
    }
    LaunchedEffect(profileRequested) {
        if (profileRequested) { showProfile = true; onProfileRequestConsumed() }
    }
    BackHandler(showProfile) { showProfile = false }
    val bubbleActive by OverlayService.isBubbleActive.collectAsStateWithLifecycle()
    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onBubbleOptionVisibleChange(true)
            val intent = Intent(context, OverlayService::class.java).apply {
                putExtra("PROJECTION_DATA", result.data)
            }
            context.startForegroundService(intent)
        }
    }

    CompositionLocalProvider(LocalLanguageChange provides onLanguageChange) {
        val navigationSpace = HomeNavigationContentHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(Modifier.fillMaxSize().background(appearance.background.first())) {
            // On Home, allow the list to draw beside the raised bubble button.
            // Its scrollable bottom padding keeps the final item fully reachable.
            Box(Modifier.fillMaxSize().padding(bottom = navigationSpace - if (isHome) 32.dp else 0.dp)
                .consumeWindowInsets(WindowInsets.navigationBars)) {
                content()
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = showProfile, modifier = Modifier.matchParentSize(),
                enter = fadeIn(), exit = fadeOut()
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f))
                    .clickable { showProfile = false })
            }
            BoxWithConstraints(Modifier.fillMaxSize()
                .consumeWindowInsets(WindowInsets.navigationBars)) {
                val panelMaxHeight = (maxHeight - navigationSpace).coerceAtLeast(0.dp) * 0.9f + navigationSpace
                androidx.compose.animation.AnimatedVisibility(
                    visible = showProfile,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Surface(Modifier.offset { IntOffset(0, panelOffset.roundToInt()) }.fillMaxWidth().heightIn(max = panelMaxHeight).padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                        color = appearance.background.first(), shadowElevation = 0.dp) {
                        Column(Modifier.padding(bottom = navigationSpace)) {
                            Box(
                                Modifier.fillMaxWidth().height(40.dp)
                                    .draggable(
                                        state = rememberDraggableState { delta ->
                                            panelDrag = (panelDrag + delta).coerceAtLeast(0f)
                                        },
                                        orientation = Orientation.Vertical,
                                        onDragStarted = { dragging = true },
                                        onDragStopped = { velocity ->
                                            dragging = false
                                            if (panelDrag >= dismissDistance ||
                                                (panelDrag > 0f && velocity >= dismissVelocity)) {
                                                showProfile = false
                                            } else panelDrag = 0f
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.size(36.dp, 4.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)))
                            }
                            CompositionLocalProvider(LocalProfilePanel provides true) {
                                TrainerProfileScreen(onBack = { showProfile = false })
                            }
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomCenter)) {
            HomeGlassNavigation(
                language = language,
                bubbleActive = bubbleActive,
                bubbleDescription = if (bubbleActive) lt(language, "Remover modo bolha", "Remove bubble mode", "Quitar modo burbuja")
                    else lt(language, "Iniciar modo bolha", "Start bubble mode", "Iniciar modo burbuja"),
                onBubbleClick = {
                    if (bubbleActive) {
                        context.stopService(Intent(context, OverlayService::class.java))
                    } else if (!Settings.canDrawOverlays(context)) {
                        onBubbleOptionVisibleChange(false)
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    } else {
                        val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        projectionLauncher.launch(mpManager.createScreenCaptureIntent())
                    }
                },
                onCalendarClick = { openExternalUrl(context, "https://rodrigoluiz1990.github.io/laboratorio-do-sam/Calendario/calendario.html") },
                onNamesClick = { showProfile = false; onGoToPresets() },
                onChatClick = { android.widget.Toast.makeText(context, lt(language, "Chat: em breve", "Chat: coming soon", "Chat: próximamente"), android.widget.Toast.LENGTH_SHORT).show() },
                onProfileClick = { showProfile = !showProfile }
            )
            }
        }
    }
}
