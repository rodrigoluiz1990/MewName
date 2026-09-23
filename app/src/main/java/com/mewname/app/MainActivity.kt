package com.mewname.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mewname.app.BuildConfig
import com.mewname.app.domain.AdventureEffectCatalogEntry
import com.mewname.app.domain.AppLanguage
import com.mewname.app.domain.GameCatalogRepository
import com.mewname.app.domain.GameInfoRepository
import com.mewname.app.domain.MoveCatalogEntry
import com.mewname.app.domain.MoveCategory
import com.mewname.app.domain.LegacyMoveCatalogEntry
import com.mewname.app.domain.NameGenerator
import com.mewname.app.domain.UniquePokemonCatalog
import com.mewname.app.model.*
import java.util.Collections

private const val NEW_PRESET_ID = "__new_preset__"

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()
    private val capturePermissionInvalidReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                OverlayService.ACTION_CAPTURE_PERMISSION_INVALID,
                OverlayService.ACTION_OVERLAY_PERMISSION_INVALID -> viewModel.setBubbleOptionVisible(false)
            }
        }
    }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { viewModel.processImage(this, it) }
        }

    private val pickValidationImages =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                viewModel.runDebugIvUriValidation(this, uris)
            }
        }

    private fun openBubbleDestination(source: Intent?) {
        val destination = source?.getStringExtra("BUBBLE_DESTINATION") ?: return
        runCatching { AppScreen.valueOf(destination) }.getOrNull()?.let(viewModel::navigateTo)
        source.removeExtra("BUBBLE_DESTINATION")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openBubbleDestination(intent)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openBubbleDestination(intent)
        ContextCompat.registerReceiver(
            this,
            capturePermissionInvalidReceiver,
            IntentFilter().apply {
                addAction(OverlayService.ACTION_CAPTURE_PERMISSION_INVALID)
                addAction(OverlayService.ACTION_OVERLAY_PERMISSION_INVALID)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        enableEdgeToEdge()
        setContent {
            AppAppearanceTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val appContext = LocalContext.current
                var showFeatureIntroduction by rememberSaveable {
                    mutableStateOf(FeatureIntroductionPreferences.isAutoShowEnabled(appContext))
                }

                LaunchedEffect(Unit) {
                    viewModel.loadConfigs(appContext)
                    viewModel.checkForAppUpdate()
                }

                BackHandler(enabled = uiState.currentScreen != AppScreen.HOME) {
                    when (uiState.currentScreen) {

                        AppScreen.PRESET_EDIT -> viewModel.navigateTo(AppScreen.PRESET_LIST)
                        AppScreen.TRAINER_PROFILE,
                        AppScreen.CALENDAR,
                        AppScreen.COLLECTIONS,
                        AppScreen.PRESET_LIST,
                        AppScreen.LEGACY_MOVES,
                        AppScreen.ADVENTURE_EFFECTS,
                        AppScreen.RAID_PLANNER, AppScreen.ROCKET, AppScreen.EGGS, AppScreen.PROMO_CODES, AppScreen.RESEARCH,
                        AppScreen.TYPE_CHART,
                        AppScreen.MOVEDEX,
                        AppScreen.POKEDEX,
                        AppScreen.FILTER_BUILDER,
                        AppScreen.TEST_MENU,
                        AppScreen.HELP_MENU,
                        AppScreen.DONATION,
                        AppScreen.PRIVACY_POLICY,
                        AppScreen.APP_UPDATE,
                        AppScreen.IV_VALIDATION -> viewModel.navigateTo(AppScreen.HOME)
                        else -> Unit
                    }
                }

                CompositionLocalProvider(LocalAppLanguage provides uiState.appLanguage) {
                if (showFeatureIntroduction) {
                    FeatureIntroductionDialog { doNotShowAgain ->
                        if (doNotShowAgain) {
                            FeatureIntroductionPreferences.setAutoShowEnabled(appContext, false)
                        }
                        showFeatureIntroduction = false
                    }
                }
                AppNavigationShell(
                    isHome = uiState.currentScreen == AppScreen.HOME,
                    profileRequested = uiState.currentScreen == AppScreen.TRAINER_PROFILE,
                    onProfileRequestConsumed = { viewModel.navigateTo(AppScreen.HOME) },
                    onGoToPresets = { viewModel.navigateTo(AppScreen.PRESET_LIST) },
                    onGoToCalendar = { viewModel.navigateTo(AppScreen.CALENDAR) },
                    uiState = uiState,
                    onRefreshAppUpdate = { viewModel.checkForAppUpdate(forceFeedback = true) },
                    onGoToPrivacy = { viewModel.navigateTo(AppScreen.PRIVACY_POLICY) },
                    onBubbleOptionVisibleChange = viewModel::setBubbleOptionVisible,
                    onLanguageChange = { viewModel.setAppLanguage(appContext, it) }
                ) {
                when (uiState.currentScreen) {
                    AppScreen.TRAINER_PROFILE, AppScreen.HOME -> HomeScreen(
                        uiState = uiState,
                        onClear = viewModel::clearResults,
                        onGoToCollections = { viewModel.navigateTo(AppScreen.COLLECTIONS) },
                        onGoToPresets = { viewModel.navigateTo(AppScreen.PRESET_LIST) },
                        onGoToCalendar = { viewModel.navigateTo(AppScreen.CALENDAR) },
                        onGoToLegacyMoves = { viewModel.navigateTo(AppScreen.LEGACY_MOVES) },
                        onGoToAdventureEffects = { viewModel.navigateTo(AppScreen.ADVENTURE_EFFECTS) },
                        onGoToRaidPlanner = { viewModel.navigateTo(AppScreen.RAID_PLANNER) },
                        onGoToLeek = { viewModel.navigateTo(it) },
                        onGoToTypes = { viewModel.navigateTo(AppScreen.TYPE_CHART) },
                        onGoToMoves = { viewModel.navigateTo(AppScreen.MOVEDEX) },
                        onGoToPokedex = { viewModel.navigateTo(AppScreen.POKEDEX) },
                        onGoToFilters = { viewModel.navigateTo(AppScreen.FILTER_BUILDER) },
                        onDismissReview = viewModel::dismissReview,
                        onApplyReview = viewModel::applyReview,
                        onCancelProcessing = viewModel::cancelImageProcessing
                    )

                    AppScreen.CALENDAR -> CalendarScreen(onBack = { viewModel.navigateTo(AppScreen.HOME) })

                    AppScreen.COLLECTIONS -> {
                        CollectionsScreen(onBack = { viewModel.navigateTo(AppScreen.HOME) })
                    }

                    AppScreen.PRESET_LIST -> {
                        val context = LocalContext.current
                        PresetListScreen(
                            configs = uiState.configs,
                            onBack = { viewModel.navigateTo(AppScreen.HOME) },
                            onAdd = { viewModel.navigateTo(AppScreen.PRESET_EDIT, NEW_PRESET_ID) },
                            onEdit = { viewModel.navigateTo(AppScreen.PRESET_EDIT, it) },
                            onDelete = { viewModel.removeConfig(context, it) }
                        )
                    }

                    AppScreen.PRESET_EDIT -> {
                        val context = LocalContext.current
                        val config = uiState.configs.find { it.id == uiState.editingConfigId }
                            ?: if (uiState.editingConfigId == NEW_PRESET_ID) {
                                remember(uiState.appLanguage) {
                                    NamingConfig(name = lt(uiState.appLanguage, "Novo Formato", "New Preset", "Nuevo Formato"))
                                }
                            } else {
                                null
                            }
                        if (config != null) {
                            PresetEditScreen(
                                config = config,
                                onBack = { viewModel.navigateTo(AppScreen.PRESET_LIST) },
                                onUpdate = { viewModel.updateConfig(context, it) }
                            )
                        }
                    }

                    AppScreen.ROCKET, AppScreen.EGGS, AppScreen.PROMO_CODES, AppScreen.RESEARCH -> LeekDuckScreen(
                        section = when(uiState.currentScreen) {
                            AppScreen.ROCKET -> com.mewname.app.domain.LeekSection.ROCKET
                            AppScreen.EGGS -> com.mewname.app.domain.LeekSection.EGGS
                            AppScreen.RESEARCH -> com.mewname.app.domain.LeekSection.RESEARCH
                            else -> com.mewname.app.domain.LeekSection.CODES
                        }, onBack = { viewModel.navigateTo(AppScreen.HOME) })
                    AppScreen.LEGACY_MOVES -> {
                        LegacyMovesScreen(onBack = { viewModel.navigateTo(AppScreen.HOME) })
                    }

                    AppScreen.ADVENTURE_EFFECTS -> {
                        AdventureEffectsScreen(onBack = { viewModel.navigateTo(AppScreen.HOME) })
                    }

                    AppScreen.RAID_PLANNER -> {
                        RaidPlannerScreen(
                            onBack = { viewModel.navigateTo(AppScreen.HOME) }
                        )
                    }

                    AppScreen.TYPE_CHART -> {
                        TypesScreen(onBack = { viewModel.navigateTo(AppScreen.HOME) })
                    }

                    AppScreen.MOVEDEX -> {
                        MovesScreen(onBack = { viewModel.navigateTo(AppScreen.HOME) })
                    }

                    AppScreen.POKEDEX -> {
                        PokedexScreen(onBack = { viewModel.navigateTo(AppScreen.HOME) })
                    }

                    AppScreen.FILTER_BUILDER -> {
                        FilterBuilderScreen(onBack = { viewModel.navigateTo(AppScreen.HOME) })
                    }

                    AppScreen.TEST_MENU -> {
                        TestMenuScreen(
                            onBack = { viewModel.navigateTo(AppScreen.HOME) },
                            onPickImage = { pickImage.launch("image/*") },
                            onGoToIvValidation = { viewModel.navigateTo(AppScreen.IV_VALIDATION) },
                            onGoToDonation = { viewModel.navigateTo(AppScreen.DONATION) }
                        )
                    }

                    AppScreen.HELP_MENU -> {
                        HelpMenuScreen(
                            onBack = { viewModel.navigateTo(AppScreen.HOME) },
                            onGoToPrivacy = { viewModel.navigateTo(AppScreen.PRIVACY_POLICY) }
                        )
                    }

                    AppScreen.DONATION -> {
                        DonationScreen(onBack = { viewModel.navigateTo(AppScreen.HOME) })
                    }

                    AppScreen.PRIVACY_POLICY -> {
                        PrivacyPolicyScreen(onBack = { viewModel.navigateTo(AppScreen.HOME) })
                    }

                    AppScreen.APP_UPDATE -> {
                        AppUpdateScreen(
                            uiState = uiState,
                            onBack = { viewModel.navigateTo(AppScreen.HOME) },
                            onRefresh = { viewModel.checkForAppUpdate(forceFeedback = true) }
                        )
                    }

                    AppScreen.IV_VALIDATION -> {
                        IvValidationScreen(
                            uiState = uiState,
                            onBack = { viewModel.navigateTo(AppScreen.HOME) },
                            onPickImages = { pickValidationImages.launch("image/*") },
                            onRunExistingValidation = { viewModel.runDebugIvSampleValidation(appContext) },
                            onCancelValidation = viewModel::cancelIvValidation
                        )
                    }
                }
                }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!Settings.canDrawOverlays(this)) {
            viewModel.setBubbleOptionVisible(false)
            stopService(Intent(this, OverlayService::class.java))
        }
    }
    override fun onDestroy() {
        runCatching { unregisterReceiver(capturePermissionInvalidReceiver) }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    uiState: UiState,
    onClear: () -> Unit,
    onGoToCollections: () -> Unit,
    onGoToCalendar: () -> Unit,
    onGoToPresets: () -> Unit,
    onGoToLegacyMoves: () -> Unit,
    onGoToAdventureEffects: () -> Unit,
    onGoToRaidPlanner: () -> Unit,
    onGoToLeek: (AppScreen) -> Unit,
    onGoToTypes: () -> Unit,
    onGoToMoves: () -> Unit,
    onGoToPokedex: () -> Unit,
    onGoToFilters: () -> Unit,
    onDismissReview: () -> Unit,
    onApplyReview: (PokemonScreenData) -> Unit,
    onCancelProcessing: () -> Unit
) {
    val language = appLanguage()
    val appearance = LocalAppAppearance.current
    val compactHome = appearance.homeLayout == HomeLayout.COMPACT
    val homeActions = listOf(
        HomeMenuAction("calendar", lt(language, "Calendário", "Calendar", "Calendario"), onGoToCalendar),
        HomeMenuAction("names", lt(language, "Definir nomes", "Name presets", "Definir nombres"), onGoToPresets),
        HomeMenuAction("filters", lt(language, "Filtros", "Filters", "Filtros"), onGoToFilters),
        HomeMenuAction("pokedex", "Pokedex", onGoToPokedex),
        HomeMenuAction("collections", lt(language, "Cole\u00e7\u00f5es", "Collections", "Colecciones"), onGoToCollections),
        HomeMenuAction("raid", lt(language, "Raids", "Raids", "Raids"), onGoToRaidPlanner),
        HomeMenuAction("rocket", lt(language, "Equipe GO Rocket", "GO Rocket Team", "Equipo GO Rocket"), { onGoToLeek(AppScreen.ROCKET) }),
        HomeMenuAction("research", lt(language, "Pesquisas de Campo", "Field Research", "Investigaciones de Campo"), { onGoToLeek(AppScreen.RESEARCH) }),
        HomeMenuAction("eggs", lt(language, "Ovos", "Eggs", "Huevos"), { onGoToLeek(AppScreen.EGGS) }),
        HomeMenuAction("adventure", lt(language, "Efeitos de Aventura", "Adventure Effects", "Efectos de Aventura"), onGoToAdventureEffects),
        HomeMenuAction("legacy", lt(language, "Ataques Legados", "Legacy Moves", "Ataques Legado"), onGoToLegacyMoves),
        HomeMenuAction("moves", lt(language, "Ataques rápidos\ne carregados", "Fast / Charged\nMoves", "Ataques rápidos\ny cargados"), onGoToMoves),
        HomeMenuAction("promo", lt(language, "Codigos Promocionais", "Promo Codes", "Codigos Promocionales"), { onGoToLeek(AppScreen.PROMO_CODES) }),
        HomeMenuAction("types", lt(language, "Tipos", "Types", "Tipos"), onGoToTypes)
    )
    Scaffold(

        containerColor = Color.Transparent,
        topBar = { if (appearance.homeLayout != HomeLayout.ATMOSPHERIC) HomeGreeting() }
    ) { padding ->
        HomeContentLayout(
            padding = padding,
            analysis = { AnalysisTabsSection(uiState = uiState, onClear = onClear) }
        ) {
            if (appearance.homeLayout == HomeLayout.ATMOSPHERIC) {
                AtmosphericActionGroups(homeActions)
            } else {
                HomeActionsGrid(compactHome) { tileWidth ->
                    homeActions.forEach { action ->
                        HomeActionSquare(
                            glass = true,
                            compact = compactHome,
                            title = action.title,
                            iconRes = null,
                            customIcon = { HomeGlassMenuIcon(action.icon) },
                            onClick = action.onClick,
                            modifier = Modifier.fillMaxWidth(tileWidth)
                        )
                    }
                }
            }
        }
        if (uiState.isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                AppSectionCard(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppLoadingIndicator()
                        Text(
                            lt(language, "Analisando imagem", "Analyzing image", "Analizando imagen"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            uiState.processingStatusMessage ?: lt(
                                language,
                                "Lendo os dados detectados para gerar o nome sugerido.",
                                "Reading detected data to generate the suggested name.",
                                "Leyendo los datos detectados para generar el nombre sugerido."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AppSecondaryButton(onClick = onCancelProcessing) {
                            Text(
                                lt(language, "Cancelar", "Cancel", "Cancelar"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.pendingReview?.let { review ->
        ReviewDialog(
            initialData = review.data,
            fields = review.fields,
            configs = uiState.configs,
            bitmap = review.bitmap,
            onDismiss = onDismissReview,
            onConfirm = onApplyReview
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppUpdateScreen(
    uiState: UiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val language = appLanguage()

    LaunchedEffect(Unit) {
        if (!uiState.isCheckingForUpdate && uiState.latestAppUpdate == null && uiState.appUpdateError == null) {
            onRefresh()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(lt(language, "Atualizacao do App", "App Update", "Actualizacion de la App")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, lt(language, "Voltar", "Back", "Volver"))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(lt(language, "Versao instalada", "Installed version", "Version instalada"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${lt(language, "Nome", "Name", "Nombre")}: v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Release: ${BuildConfig.RELEASE_TAG.takeUnless { it.isBlank() || it == "dev" } ?: lt(language, "build local", "local build", "build local")}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            when {
                uiState.isCheckingForUpdate -> {
                    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppLoadingIndicator()
                            Text(lt(language, "Verificando nova release...", "Checking for a new release...", "Buscando nueva release..."), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                uiState.latestAppUpdate != null -> {
                    val update = uiState.latestAppUpdate
                    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(if (uiState.appUpdateAvailable) lt(language, "Nova release disponível", "New release available", "Nueva release disponible") else lt(language, "Release instalada", "Installed release", "Release instalada"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${lt(language, "Versao", "Version", "Version")}: ${update.tagName}", style = MaterialTheme.typography.bodyMedium)
                            if (update.releaseName.isNotBlank()) {
                                Text("${lt(language, "Titulo", "Title", "Titulo")}: ${update.releaseName}", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (update.publishedAt.isNotBlank()) {
                                Text("${lt(language, "Publicada em", "Published at", "Publicada en")}: ${update.publishedAt}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(lt(language, "O que mudou", "What's changed", "Qué cambió"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                update.releaseNotes.ifBlank { lt(language, "Sem descricao cadastrada para esta release.", "No description was added for this release.", "No hay descripcion registrada para esta release.") },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    AppSecondaryButton(
                        onClick = { openExternalUrl(context, update.releasePageUrl) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(lt(language, "Ver pagina da release", "View release page", "Ver pagina de la release"))
                    }

                    if (uiState.appUpdateAvailable) {
                        AppActionButton(
                            onClick = {
                                openExternalUrl(context, update.apkDownloadUrl ?: update.releasePageUrl)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (update.apkDownloadUrl != null) lt(language, "Baixar atualização", "Download update", "Descargar actualización") else lt(language, "Abrir release", "Open release", "Abrir release"))
                        }
                    }
                }

                uiState.appUpdateError != null -> {
                    AppSectionCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(lt(language, "Falha ao verificar atualizacao", "Failed to check for updates", "Error al buscar actualizacion"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                uiState.appUpdateError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    AppActionButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text(lt(language, "Tentar novamente", "Try again", "Intentar de nuevo"))
                    }
                }

                else -> {
                    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(lt(language, "Seu app ja esta atualizado", "Your app is up to date", "Tu app ya esta actualizada"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                uiState.appUpdateStatusMessage ?: lt(language, "Nenhuma atualizacao mais nova foi encontrada no GitHub.", "No newer update was found on GitHub.", "No se encontro una actualizacion mas nueva en GitHub."),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    AppActionButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text(lt(language, "Verificar novamente", "Check again", "Buscar de nuevo"))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyMovesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val language = appLanguage()
    val entries = remember(context) { GameCatalogRepository.loadLegacyMoveCatalog(context) }
    val moveIndex = remember(context) { moveCatalogIndex(GameInfoRepository.loadMoveCatalog(context)) }
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = remember(query) { normalizeMoveLookup(query) }
    val filteredEntries = remember(entries, normalizedQuery) {
        if (normalizedQuery.isBlank()) entries else entries.filter { entry ->
            entry.searchTerms.any { it.contains(normalizedQuery) }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(lt(language, "Ataques Legados", "Legacy Moves", "Ataques Legados")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, lt(language, "Voltar", "Back", "Volver"))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                AppGlassTextField(
                    search = true,
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(lt(language, "Buscar Pokémon ou ataque", "Search Pokémon or move", "Buscar Pokémon o ataque")) },
                    singleLine = true
                )
            }
            item {
                Text(
                    lt(language, "${filteredEntries.size} Pokémon", "${filteredEntries.size} Pokémon", "${filteredEntries.size} Pokémon"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(filteredEntries, key = { it.pokemon }) { entry ->
                LegacyMoveCard(entry = entry, language = language, moveIndex = moveIndex)
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdventureEffectsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val language = appLanguage()
    val entries = remember(context) { GameCatalogRepository.loadAdventureEffectCatalog(context) }
    val moveIndex = remember(context) { moveCatalogIndex(GameInfoRepository.loadMoveCatalog(context)) }
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = remember(query) { normalizeMoveLookup(query) }
    val filteredEntries = remember(entries, normalizedQuery) {
        if (normalizedQuery.isBlank()) entries else entries.filter { entry ->
            listOf(entry.pokemon, entry.displayPokemonPt, entry.move, entry.movePt, entry.effectName, entry.effectNamePt)
                .any { normalizeMoveLookup(it).contains(normalizedQuery) }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(lt(language, "Efeitos de Aventura", "Adventure Effects", "Efectos de Aventura")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, lt(language, "Voltar", "Back", "Volver"))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                AppGlassTextField(
                    search = true,
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(lt(language, "Buscar Pokémon, ataque ou efeito", "Search Pokémon, move or effect", "Buscar Pokémon, ataque o efecto")) },
                    singleLine = true
                )
            }
            items(filteredEntries, key = { "${it.pokemon}|${it.move}" }) { entry ->
                AdventureEffectCard(
                    entry = entry,
                    language = language,
                    moveDetails = moveIndex[normalizeMoveLookup(entry.move)]
                        ?: moveIndex[normalizeMoveLookup(entry.movePt)]
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DonationScreen(onBack: () -> Unit) {
    val language = appLanguage()
    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(lt(language, "Doacao", "Donate", "Donacion")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(lt(language, "Apoie o MewName", "Support MewName", "Apoya MewName"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        lt(
                            language,
                            "Esta tela ja esta pronta para receber sua chave Pix, link de doacao ou outra forma de apoio em uma proxima atualizacao.",
                            "This screen is ready to receive a Pix key, donation link, or another support option in a future update.",
                            "Esta pantalla esta lista para recibir una clave Pix, enlace de donacion u otra forma de apoyo en una proxima actualizacion."
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TestMenuScreen(
    onBack: () -> Unit,
    onPickImage: () -> Unit,
    onGoToIvValidation: () -> Unit,
    onGoToDonation: () -> Unit
) {
    val language = appLanguage()
    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(lt(language, "Teste", "Test", "Prueba")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        FlowRow(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HomeActionSquare(
                glass = LocalAppAppearance.current.glass,
                compact = LocalAppAppearance.current.compact,
                title = lt(language, "Gerar nome por imagem", "Generate name from image", "Generar nombre por imagen"),
                iconRes = null,
                customIcon = { HomeMenuGlyph("capture") },
                onClick = onPickImage,
                modifier = Modifier.fillMaxWidth(if (LocalAppAppearance.current.compact) 1f else 0.31f)
            )
            HomeActionSquare(
                glass = LocalAppAppearance.current.glass,
                compact = LocalAppAppearance.current.compact,
                title = lt(language, "Validar amostras", "Validate samples", "Validar muestras"),
                iconRes = null,
                customIcon = { HomeMenuGlyph("validation") },
                onClick = onGoToIvValidation,
                modifier = Modifier.fillMaxWidth(if (LocalAppAppearance.current.compact) 1f else 0.31f)
            )
            HomeActionSquare(
                glass = LocalAppAppearance.current.glass,
                compact = LocalAppAppearance.current.compact,
                title = lt(language, "Doacao", "Donate", "Donacion"),
                iconRes = null,
                customIcon = { HomeMenuGlyph("donation") },
                onClick = onGoToDonation,
                modifier = Modifier.fillMaxWidth(if (LocalAppAppearance.current.compact) 1f else 0.31f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HelpMenuScreen(
    onBack: () -> Unit,
    onGoToPrivacy: () -> Unit
) {
    val context = LocalContext.current
    var featureIntroductionEnabled by rememberSaveable {
        mutableStateOf(FeatureIntroductionPreferences.isAutoShowEnabled(context))
    }
    val language = appLanguage()
    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(lt(language, "Ajuda", "Help", "Ayuda")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AppToggleRow(
                    label = lt(language, "Apresentação inicial", "Getting started guide", "Presentación inicial"),
                    description = lt(
                        language,
                        "Mostrar o guia sempre que o app for aberto.",
                        "Show the guide whenever the app is opened.",
                        "Mostrar la guía cada vez que se abra la aplicación."
                    ),
                    checked = featureIntroductionEnabled,
                    onCheckedChange = { enabled ->
                        featureIntroductionEnabled = enabled
                        FeatureIntroductionPreferences.setAutoShowEnabled(context, enabled)
                    }
                )
            }
            item {
                AppSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(lt(language, "Como usar cada tela", "How to use each screen", "Como usar cada pantalla"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        HelpLine("Calendario", lt(language, "Consulte o mês, selecione um dia e filtre os eventos por categoria. Toque em um evento para ver datas e a publicação original. Atualizar busca os dados do calendário; a última consulta fica salva para uso sem internet.", "Browse the month, select a day and filter events by category. Tap an event for dates and the original post. Refresh downloads calendar data; the last successful update remains available offline.", "Consulta el mes, selecciona un día y filtra por categoría. Toca un evento para ver las fechas y la publicación original. Actualizar descarga los datos; la última consulta queda disponible sin conexión."))
                        HelpLine(lt(language, "Definir Nomes", "Name Presets", "Definir Nombres"), lt(language, "Crie os formatos que a sobreposição usa para gerar apelidos. Adicione campos como nome, IV, liga PvP, genero, tamanho, fundo especial e ataques legados. Toque em um campo para configurar os simbolos e adiciona-lo ao nome. A sobreposição usa apenas formatos ja salvos.", "Create the formats used by the overlay to generate nicknames. Add fields like name, IV, PvP league, gender, size, special background, and legacy moves. Tap a field to configure its symbols and add it to the name. The overlay only uses saved formats.", "Crea los formatos que usa la superposición para generar apodos. Agrega campos como nombre, IV, liga PvP, genero, tamano, fondo especial y ataques legado. Toca un campo para configurar sus simbolos y agregarlo al nombre. La superposición solo usa formatos guardados."))
                        HelpLine(lt(language, "Nomes sugeridos", "Suggested names", "Nombres sugeridos"), lt(language, "Na janela de nomes sugeridos, toque em uma opcao para copiar o nome. Confira os dados da leitura antes de usar o apelido. Se nenhum nome aparecer, revise a captura e os formatos salvos.", "In the suggested names window, tap an option to copy the name. Check the scanned data before using the nickname. If no name appears, review the capture and saved presets.", "En la ventana de nombres sugeridos, toca una opcion para copiar el nombre. Revisa los datos leidos antes de usar el apodo. Si no aparece ningun nombre, revisa la captura y los formatos guardados."))
                        HelpLine(lt(language, "Filtros", "Filters", "Filtros"), lt(language, "Monte buscas para Pokemon ou Pessoas. Toque uma opcao para incluir, toque de novo para excluir e mais uma vez para limpar. Escolha se ela combina com & ou vira alternativa com virgula. O texto copiado respeita o idioma selecionado.", "Build searches for Pokemon or People. Tap an option to include it, tap again to exclude it, and once more to clear it. Choose whether it combines with & or becomes an alternative with comma. Copied text follows the selected language.", "Crea busquedas para Pokemon o Personas. Toca una opcion para incluirla, otra vez para excluirla y una vez mas para limpiarla. Elige si combina con & o si es alternativa con coma. El texto copiado respeta el idioma seleccionado."))
                        HelpLine("Pokedex", lt(language, "Pesquise por nome, numero ou apelido do catalogo. Filtre por tipo e abra os cards para comparar tipos, atributos base e formas conhecidas.", "Search by name, number, or catalog alias. Filter by type and use the cards to compare typing, base stats, and known forms.", "Busca por nombre, numero o alias del catalogo. Filtra por tipo y usa las tarjetas para comparar tipos, estadisticas base y formas conocidas."))
                        HelpLine(lt(language, "Raids", "Raids", "Raids"), lt(language, "Selecione a raid para ver golpes do chefe, fraquezas, PC de captura e atacantes com golpes sugeridos. Atualize pelo app principal; a sobreposição usa os dados salvos. O filtro combina espécies e tipos de golpes; confira as formas e os ataques.", "Select a raid for boss moves, weaknesses, catch CP and counters with recommended attacks. Update in the main app; overlay mode reads saved data. Check forms and moves after using the species and attack filter.", "Selecciona una incursión para ver ataques, debilidades, PC de captura y sugerencias. Actualiza en la app principal; la superposición usa datos guardados. Revisa formas y ataques al usar el filtro."))
                        HelpLine(lt(language, "Tipos", "Types", "Tipos"), lt(language, "Selecione ate dois tipos defensivos para ver fraquezas, resistencias e resistencias duplas. Use junto com Raids para decidir ataque e sobrevivencia.", "Select up to two defensive types to see weaknesses, resistances, and double resistances. Use it with Raids to decide attack and survivability.", "Selecciona hasta dos tipos defensivos para ver debilidades, resistencias y resistencias dobles. Usalo con Raids para decidir ataque y supervivencia."))
                        HelpLine(lt(language, "Ataques rapidos/carregados", "Fast/Charged Moves", "Ataques rapidos/cargados"), lt(language, "Pesquise ataques por nome, tipo e categoria. Cada registro mostra dados de ginasio e PvP para comparar dano, energia e duracao.", "Search moves by name, type, and category. Each row shows Gym and PvP data so you can compare damage, energy, and duration.", "Busca ataques por nombre, tipo y categoria. Cada registro muestra datos de gimnasio y PvP para comparar dano, energia y duracion."))
                        HelpLine(lt(language, "Equipe GO Rocket", "Team GO Rocket", "Equipo GO Rocket"), lt(language, "Consulte recrutas, lideres e Giovanni no app. Filtre por grupo e pesquise nomes, tipos ou falas no idioma selecionado ou no original. Cada bloco mostra as tres posicoes, capturas possiveis e fraquezas. Atualizar consulta o Leek Duck; sem internet, a ultima lista salva continua disponivel.", "Browse grunts, leaders and Giovanni in the app. Filter or search names, types and quotes in your selected language or the original. Cards show three slots, catchable Pokemon and weaknesses. Refresh checks Leek Duck; saved lists remain available offline.", "Consulta reclutas, lideres y Giovanni. Filtra y busca nombres, tipos o frases en el idioma seleccionado o en el original. Se muestran posiciones, capturas y debilidades. La ultima lista queda disponible sin conexion."))
                        HelpLine(lt(language, "Codigos Promocionais", "Promo Codes", "Codigos Promocionales"), lt(language, "Consulte recompensas e validade, copie o codigo ou toque em Resgatar para abrir a loja oficial. Disponivel significa listado pela fonte, sem garantia de resgate para sua conta. Validade desconhecida e mostrada quando a fonte nao informa a data.", "View rewards and expiry, copy a code or open the official redemption store. Available means listed by the source, not guaranteed redeemable for your account. Unknown expiry is shown when no public date is supplied.", "Consulta recompensas y vencimiento, copia el codigo o abre la tienda oficial. Disponible significa listado por la fuente; el canje depende de la cuenta. Se indica cuando la fecha es desconocida."))
                        HelpLine(lt(language, "Leitura de ovos, Rocket e pesquisas", "Egg, Rocket and research scanning", "Lectura de huevos, Rocket e investigaciones"), lt(language,
                            "No jogo, abra os ovos, a fala do recruta ou as pesquisas de campo e toque na sobreposição. A janela mostra possibilidades para as distancias, a fala ou as tarefas visiveis. Ovos antigos e origens diferentes podem ter outras especies; falas compartilhadas mostram ambos os recrutas. Pesquisas com o mesmo texto podem ter recompensas diferentes por evento e icone: as opcoes sao separadas e nao sao todas garantidas. Tarefas nao reconhecidas ou ausentes no catalogo nao recebem uma recompensa presumida. A sobreposição consulta somente os dados salvos. Atualize os catalogos nas telas normais do app; X volta ao jogo.",
                            "Open eggs, a grunt quote or field research in the game and tap the overlay. The window shows possibilities for the visible distances, quote or tasks. Older eggs and different origins may have other species; shared quotes show both grunts. Identical tasks may have different rewards by event and icon: options are separated and not all are guaranteed. Unrecognized or unlisted tasks are not assigned a guessed reward. The overlay only reads saved data. Update catalogs in the normal app screens; X returns to the game.",
                            "Abre los huevos, la frase de un recluta o las investigaciones de campo y toca la superposición. Se muestran posibilidades para las distancias, la frase o las tareas visibles. Los huevos antiguos pueden tener otras especies; las frases compartidas muestran ambos reclutas. Las tareas iguales pueden variar por evento e icono: no se garantizan todas las recompensas. No se inventan recompensas para tareas no reconocidas. Actualiza los catalogos en las pantallas normales de la app; X vuelve al juego."))
                        HelpLine(lt(language, "Ovos", "Eggs", "Huevos"), lt(language, "Filtre por distancia e origem: comuns, presentes, rotas e Sincroaventura. Consulte Pokemon, CP, shiny e raridade quando confirmada. Avisos de listas incompletas sao preservados. Textos descritivos do Leek Duck permanecem no idioma original. A fonte e a ultima consulta aparecem no fim de cada tela.", "Filter eggs by distance and origin. View Pokemon, CP, shiny and confirmed rarity. Incomplete-list notices are preserved. Leek Duck descriptions remain in their original language. Source and last checked date appear at the bottom.", "Filtra huevos por distancia y origen. Consulta Pokemon, CP, shiny y rareza confirmada. Se conservan avisos de listas incompletas y descripciones originales. La fuente y la ultima consulta aparecen al final."))
                        HelpLine(lt(language, "Pesquisas de Campo", "Field Research", "Investigaciones de Campo"), lt(language, "Consulte tarefas de campo e recompensas no app, com busca e filtros de pesquisas comuns e de evento. A tela salva o catalogo para a sobreposição e para consultas sem internet. Na sobreposição, o catalogo salvo e usado sem consulta automatica a internet; atualize somente pela tela normal de Pesquisas de Campo. Sem dados salvos ou correspondencias, a sobreposição pede uma atualizacao nessa tela.", "Browse field tasks and rewards in the app, with search and regular/event filters. This screen saves the shared catalog for offline overlay scans. The overlay never downloads updates. Use the normal Field Research screen to update; the overlay directs you there when saved data or matches are missing.", "Consulta tareas de campo y recompensas en la app, con búsqueda y filtros. El catálogo se guarda para consultas sin conexión. La superposición usa exclusivamente los datos guardados. Actualiza desde la pantalla normal de investigaciones cuando falten datos o coincidencias."))
                        HelpLine(lt(language, "Atualizar", "Update", "Actualizar"), lt(language, "Consulta releases do app e mostra a versao instalada. Use quando quiser verificar se ha APK mais recente.", "Checks app releases and shows the installed version. Use it when you want to see whether a newer APK exists.", "Consulta releases de la app y muestra la version instalada. Usalo cuando quieras verificar si hay un APK mas reciente."))
                        HelpLine(lt(language, "Privacidade", "Privacy", "Privacidad"), lt(language, "Explica permissoes, captura de tela, processamento local, armazenamento e direitos da Pokemon Company.", "Explains permissions, screen capture, local processing, storage, and Pokemon Company rights.", "Explica permisos, captura de pantalla, procesamiento local, almacenamiento y derechos de Pokemon Company."))
                    }
                }
            }
            item {
                AppActionButton(onClick = onGoToPrivacy, modifier = Modifier.fillMaxWidth()) {
                    Text(lt(language, "Politica de privacidade", "Privacy policy", "Politica de privacidad"))
                }
            }
        }
    }
}

@Composable
private fun HelpLine(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val language = appLanguage()
    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(lt(language, "Privacidade", "Privacy", "Privacidad")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AppSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(lt(language, "Politica de privacidade", "Privacy policy", "Politica de privacidad"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            lt(
                                language,
                                "O MewName processa imagens escolhidas por voce ou capturadas pela sobreposição para reconhecer dados visiveis na tela e gerar sugestoes. A analise acontece no aparelho.",
                                "MewName processes images you choose or capture with the overlay to recognize visible screen data and generate suggestions. Analysis happens on the device.",
                                "MewName procesa imagenes elegidas por ti o capturadas con la superposición para reconocer datos visibles y generar sugerencias. El analisis ocurre en el dispositivo."
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            lt(
                                language,
                                "O app nao exige cadastro, nao vende dados pessoais e nao envia suas capturas para servidores do MewName.",
                                "The app does not require an account, does not sell personal data, and does not send captures to MewName servers.",
                                "La app no requiere cuenta, no vende datos personales y no envia capturas a servidores de MewName."
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            lt(language,
                                "A temperatura é opcional. Ao buscar uma cidade, o nome pesquisado é enviado ao Open-Meteo; após a seleção, as coordenadas dessa cidade são usadas para consultar o clima. Com sua permissão, o modo automático usa a localização aproximada do aparelho apenas enquanto a Home está visível e envia coordenadas arredondadas ao Open-Meteo. Não há acesso à localização em segundo plano. A localização escolhida e a temperatura recente ficam salvas no aparelho.",
                                "Temperature is optional. City searches send the search term to Open-Meteo; after selection, that city's coordinates are used for weather requests. With your permission, automatic mode uses approximate device location only while Home is visible and sends rounded coordinates to Open-Meteo. There is no background location access. The selected location and recent temperature are saved on the device.",
                                "La temperatura es opcional. Las búsquedas envían el nombre al Open-Meteo; tras seleccionar una ciudad, sus coordenadas se usan para consultar el clima. Con tu permiso, el modo automático usa la ubicación aproximada solo mientras Inicio está visible y envía coordenadas redondeadas al Open-Meteo. No se accede a la ubicación en segundo plano. La ubicación elegida y la temperatura reciente se guardan en el dispositivo."),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            lt(
                                language,
                                "Ao usar links externos, atualizacoes, calendario, doacoes ou compartilhamento de logs, o Android e os apps/sites abertos podem aplicar suas proprias politicas.",
                                "When you use external links, updates, calendar, donations, or log sharing, Android and opened apps/sites may apply their own policies.",
                                "Al usar enlaces externos, actualizaciones, calendario, donaciones o compartir registros, Android y las apps/sitios abiertos pueden aplicar sus propias politicas."
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            item {
                AppSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(lt(language, "Permissoes", "Permissions", "Permisos"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            lt(
                                language,
                                "Sobreposicao: usada para mostrar a sobreposição sobre o jogo. Captura de tela: usada somente depois da autorizacao do Android.",
                                "Overlay: used to show the overlay over the game. Screen capture: used only after Android authorization.",
                                "Superposicion: usada para mostrar la superposición sobre el juego. Captura de pantalla: usada solo despues de la autorizacion de Android."
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            lt(
                                language,
                                "Internet: usada para consultar atualizacoes, abrir paginas externas e recursos online acionados por voce.",
                                "Internet: used to check updates, open external pages, and online resources you trigger.",
                                "Internet: usada para consultar actualizaciones, abrir paginas externas y recursos online que tu activas."
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            item {
                AppSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(lt(language, "Direitos e marcas Pokemon", "Pokemon rights and trademarks", "Derechos y marcas Pokemon"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            lt(
                                language,
                                "MewName e uma ferramenta independente e nao e afiliado, endossado, patrocinado ou aprovado pela The Pokemon Company, Nintendo, Game Freak, Creatures Inc. ou Niantic.",
                                "MewName is an independent tool and is not affiliated with, endorsed, sponsored, or approved by The Pokemon Company, Nintendo, Game Freak, Creatures Inc., or Niantic.",
                                "MewName es una herramienta independiente y no esta afiliada, respaldada, patrocinada ni aprobada por The Pokemon Company, Nintendo, Game Freak, Creatures Inc. o Niantic."
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            lt(
                                language,
                                "Pokemon, Pokemon GO, nomes, imagens, marcas, personagens e materiais relacionados pertencem aos seus respectivos titulares. Todos os direitos sao reservados aos proprietarios.",
                                "Pokemon, Pokemon GO, names, images, trademarks, characters, and related materials belong to their respective owners. All rights are reserved by the owners.",
                                "Pokemon, Pokemon GO, nombres, imagenes, marcas, personajes y materiales relacionados pertenecen a sus respectivos titulares. Todos los derechos estan reservados."
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            lt(
                                language,
                                "As referencias usadas pelo app existem apenas para identificacao, organizacao pessoal e compatibilidade com a experiencia do usuario.",
                                "References used by the app exist only for identification, personal organization, and user experience compatibility.",
                                "Las referencias usadas por la app existen solo para identificacion, organizacion personal y compatibilidad con la experiencia de usuario."
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyMoveCard(
    entry: LegacyMoveCatalogEntry,
    language: AppLanguage,
    moveIndex: Map<String, MoveCatalogEntry>
) {
    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(entry.pokemon, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (entry.moves.isEmpty()) {
                Text(
                    lt(language, "Sem golpes cadastrados", "No registered moves", "Sin ataques registrados"),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                entry.moves.forEachIndexed { index, moveName ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    CatalogMoveRow(
                        displayName = moveIndex[normalizeMoveLookup(moveName)]?.localizedName(language) ?: moveName,
                        details = moveIndex[normalizeMoveLookup(moveName)],
                        language = language
                    )
                }
            }
        }
    }
}

@Composable
private fun AdventureEffectCard(
    entry: AdventureEffectCatalogEntry,
    language: AppLanguage,
    moveDetails: MoveCatalogEntry?
) {
    val pokemonName = if (language == AppLanguage.PT_BR) entry.displayPokemonPt else entry.pokemon
    val moveName = if (language == AppLanguage.PT_BR) entry.movePt else entry.move
    val effectName = if (language == AppLanguage.PT_BR) entry.effectNamePt else entry.effectName
    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(pokemonName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            CatalogMoveRow(displayName = moveName, details = moveDetails, language = language)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    lt(language, "Efeito", "Effect", "Efecto"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(effectName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(adventureEffectDescription(entry, language), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CatalogMoveRow(
    displayName: String,
    details: MoveCatalogEntry?,
    language: AppLanguage
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        details?.let { TypeIcon(type = it.type, language = language) }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (details != null) {
                Text(
                    lt(
                        language,
                        if (details.category == MoveCategory.FAST) "Ataque rápido" else "Ataque carregado",
                        if (details.category == MoveCategory.FAST) "Fast move" else "Charged move",
                        if (details.category == MoveCategory.FAST) "Ataque rápido" else "Ataque cargado"
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    moveCombatSummary(details.power, details.energyDelta, details.duration, language),
                    style = MaterialTheme.typography.bodySmall
                )
                if (details.pvpPower != null || details.pvpEnergyDelta != null) {
                    Text(
                        "PvP · ${moveCombatSummary(details.pvpPower, details.pvpEnergyDelta, details.pvpTurnDuration, language, turns = true)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun moveCombatSummary(
    damage: Int?,
    energyDelta: Int?,
    duration: Int?,
    language: AppLanguage,
    turns: Boolean = false
): String {
    val parts = buildList {
        damage?.let { add(lt(language, "Dano $it", "Damage $it", "Daño $it")) }
        energyDelta?.let { value ->
            val displayed = if (value > 0) "+$value" else kotlin.math.abs(value).toString()
            add(lt(language, "Energia $displayed", "Energy $displayed", "Energía $displayed"))
        }
        duration?.let { add(if (turns) "$it t" else "$it ms") }
    }
    return parts.joinToString(" · ").ifBlank { "-" }
}

private fun moveCatalogIndex(entries: List<MoveCatalogEntry>): Map<String, MoveCatalogEntry> {
    return buildMap {
        entries.forEach { entry ->
            listOfNotNull(entry.name, entry.namePt, entry.nameEs).forEach { name ->
                putIfAbsent(normalizeMoveLookup(name), entry)
            }
        }
    }
}

private fun normalizeMoveLookup(value: String): String {
    return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .uppercase(java.util.Locale.US)
        .trim()
}

private fun adventureEffectDescription(entry: AdventureEffectCatalogEntry, language: AppLanguage): String {
    if (language == AppLanguage.PT_BR) return entry.description
    val translations = when (entry.move) {
        "Roar of Time" -> "Pauses timers for items such as Incense, Lucky Eggs, and Star Pieces." to
            "Pausa los temporizadores de objetos como Incienso, Huevo Suerte y Trozo Estrella."
        "Spacial Rend" -> "Increases the encounter range around the Trainer for a limited time." to
            "Aumenta la distancia de encuentro alrededor del Entrenador por tiempo limitado."
        "Sunsteel Strike" -> "Attracts Pokémon during the day and changes the cycle to daytime for a limited time." to
            "Atrae Pokémon durante el día y cambia el ciclo al período diurno por tiempo limitado."
        "Moongeist Beam" -> "Attracts Pokémon at night and changes the cycle to nighttime for a limited time." to
            "Atrae Pokémon durante la noche y cambia el ciclo al período nocturno por tiempo limitado."
        "Freeze Shock" -> "Boosts encounters associated with cold weather and electricity for a limited time." to
            "Potencia encuentros asociados al clima frío y la electricidad por tiempo limitado."
        "Ice Burn" -> "Boosts encounters associated with ice and fire for a limited time." to
            "Potencia encuentros asociados al hielo y el fuego por tiempo limitado."
        "Behemoth Blade" -> "Grants an offensive bonus in encounters and battles for a limited time." to
            "Otorga una bonificación ofensiva en encuentros y combates por tiempo limitado."
        "Behemoth Bash" -> "Grants a defensive bonus in encounters and battles for a limited time." to
            "Otorga una bonificación defensiva en encuentros y combates por tiempo limitado."
        "Dynamax Cannon" -> "Channels Dynamax energy in battles and encounters for a limited time." to
            "Canaliza energía Dinamax en combates y encuentros por tiempo limitado."
        else -> entry.description to entry.description
    }
    return if (language == AppLanguage.ES) translations.second else translations.first
}
@Composable
private fun HomeActionSquare(
    title: String,
    glass: Boolean = false,
    compact: Boolean = false,
    @DrawableRes iconRes: Int?,
    assetIconPath: String? = null,
    customIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appearance = LocalAppAppearance.current
    if (compact) {
        AppSectionCard(
            modifier = modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.width(76.dp), contentAlignment = Alignment.Center) { customIcon?.invoke() }
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("›", style = MaterialTheme.typography.titleLarge)
            }
        }
        return
    }
    if (glass && customIcon != null) {
        // Draw one surface behind the content. A transparent elevated Card can
        // expose an opaque rectangular layer on some Android renderers.
        Box(modifier = modifier.clickable(onClick = onClick)) {
            Box(Modifier.matchParentSize().padding(top = 7.dp)
                .background(Brush.linearGradient(appearance.card), RoundedCornerShape(18.dp))
                .border(0.75.dp, appearance.border, RoundedCornerShape(18.dp)))
            Text(
                title,
                color = appearance.text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 52.dp, bottom = 6.dp)
                    .height(with(androidx.compose.ui.platform.LocalDensity.current) { MaterialTheme.typography.labelMedium.lineHeight.toDp() * 2 })
            )
            Box(Modifier.align(Alignment.TopCenter).height(48.dp), contentAlignment = Alignment.Center) {
                customIcon()
            }
        }
        return
    }
    Card(
        modifier = modifier
            .aspectRatio(0.94f)
            .then(if (glass) Modifier.background(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.72f),
                    Color(0xFFE8E0F7).copy(alpha = 0.48f), Color.White.copy(alpha = 0.42f))),
                RoundedCornerShape(20.dp)
            ) else Modifier)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (glass) Color.Transparent else MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (glass) androidx.compose.foundation.BorderStroke(0.75.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.25f)))) else null,
        shape = RoundedCornerShape(if (glass) 20.dp else 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()

                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                assetIconPath != null -> HomeAssetIcon(assetIconPath, title)
                customIcon != null -> customIcon()
                iconRes != null -> {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = title,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                else -> HomeKeyboardGlyph()
            }
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HomeAssetIcon(assetPath: String, contentDescription: String) {
    AssetImageIcon(
        assetPath = assetPath,
        contentDescription = contentDescription,
        modifier = Modifier.size(28.dp),
        fallbackSize = 28.dp
    )
}

@Composable
private fun HomeBrowserGlyph() {
    Box(
        modifier = Modifier
            .size(30.dp)
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(7.dp))
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 2.dp, height = 16.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        )
    }
}

@Composable
private fun HomePhotoGlyph() {
    Box(
        modifier = Modifier
            .size(30.dp)
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun HomeUpdateGlyph() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "?",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun HomeValidateGlyph() {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        listOf(8.dp, 14.dp, 20.dp).forEach { barHeight ->
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

internal fun openExternalUrl(context: Context, url: String) {
    if (url.isBlank()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .recoverCatching {
            if (it is ActivityNotFoundException) return
            throw it
        }
}

@Composable
private fun HomeKeyboardGlyph() {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IvValidationScreen(
    uiState: UiState,
    onBack: () -> Unit,
    onPickImages: () -> Unit,
    onRunExistingValidation: () -> Unit,
    onCancelValidation: () -> Unit
) {
    val context = LocalContext.current
    val language = appLanguage()
    val showLogOptions = logOptionsEnabled()
    val results = uiState.debugIvValidationResults
    val comparableCount = results.count { it.comparable }
    val matchedCount = results.count { it.comparable && it.matched }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(lt(language, "Validador IV", "IV Validator", "Validador IV")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, lt(language, "Voltar", "Back", "Volver"))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                AppSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(lt(language, "Enviar novos prints para analise", "Send new screenshots for analysis", "Enviar nuevas capturas para analisis"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        AppActionButton(
                            onClick = onPickImages,
                            enabled = !uiState.debugIvValidationRunning,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(lt(language, "Selecionar imagens", "Select images", "Seleccionar imagenes"))
                        }
                        Text(
                            lt(
                                language,
                                "Se o nome do arquivo tiver o formato 15-14-13, o app tambem compara o IV esperado com o detectado.",
                                "If the file name uses the 15-14-13 format, the app also compares the expected IV with the detected one.",
                                "Si el nombre del archivo usa el formato 15-14-13, la app tambien compara el IV esperado con el detectado."
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        HorizontalDivider()
                        Text(lt(language, "Imagens existentes no projeto", "Existing project images", "Imagenes existentes del proyecto"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        AppActionButton(
                            onClick = onRunExistingValidation,
                            enabled = !uiState.debugIvValidationRunning,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (uiState.debugIvValidationRunning) lt(language, "Analisando...", "Analyzing...", "Analizando...") else lt(language, "Analisar imagens existentes", "Analyze existing images", "Analizar imagenes existentes"))
                        }
                        if (uiState.debugIvValidationRunning) {
                            AppSecondaryButton(
                                onClick = onCancelValidation,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(lt(language, "Cancelar", "Cancel", "Cancelar"))
                            }
                        }
                    }
                }
            }
            if (showLogOptions && results.isNotEmpty()) {
                item {
                    AppActionButton(
                        onClick = {
                            val exportText = buildDebugIvValidationExport(results, matchedCount)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, lt(language, "MewName - Resultado do Validador IV", "MewName - IV Validator Result", "MewName - Resultado del Validador IV"))
                                putExtra(Intent.EXTRA_TEXT, exportText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, lt(language, "Exportar resultado", "Export result", "Exportar resultado")))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(lt(language, "Exportar Resultado", "Export Result", "Exportar Resultado"))
                    }
                }
            }
            uiState.debugIvValidationError?.let { error ->
                item {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (results.isNotEmpty()) {
                item {
                    Text(
                        if (comparableCount > 0) lt(language, "Acertos: $matchedCount/$comparableCount", "Matches: $matchedCount/$comparableCount", "Aciertos: $matchedCount/$comparableCount") else lt(language, "Resultados analisados: ${results.size}", "Analyzed results: ${results.size}", "Resultados analizados: ${results.size}"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(results) { result ->
                    AppSectionCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                !result.comparable -> MaterialTheme.colorScheme.surfaceVariant
                                result.matched -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                result.fileName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${lt(language, "Esperado", "Expected", "Esperado")}: ${result.expectedAttack ?: "-"}/${result.expectedDefense ?: "-"}/${result.expectedStamina ?: "-"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "${lt(language, "Detectado", "Detected", "Detectado")}: ${result.detectedAttack ?: "-"}/${result.detectedDefense ?: "-"}/${result.detectedStamina ?: "-"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "IV %: ${result.detectedPercent ?: "-"} | ${when {
                                    !result.comparable -> lt(language, "Sem referencia", "No reference", "Sin referencia")
                                    result.matched -> "OK"
                                    else -> lt(language, "Erro", "Error", "Error")
                                }}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (result.notes.isNotBlank()) {
                                Text(result.notes, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildDebugIvValidationExport(
    results: List<DebugIvSampleResult>,
    matchedCount: Int
): String {
    val comparableCount = results.count { it.comparable }
    return buildString {
        appendLine("MewName - Resultado do Validador IV")
        appendLine(if (comparableCount > 0) "Acertos: $matchedCount/$comparableCount" else "Resultados analisados: ${results.size}")
        appendLine()
        results.forEachIndexed { index, result ->
            appendLine(result.fileName)
            appendLine("Esperado: ${result.expectedAttack ?: "-"}/${result.expectedDefense ?: "-"}/${result.expectedStamina ?: "-"}")
            appendLine("Detectado: ${result.detectedAttack ?: "-"}/${result.detectedDefense ?: "-"}/${result.detectedStamina ?: "-"}")
            appendLine(
                "IV %: ${result.detectedPercent ?: "-"} | ${when {
                                !result.comparable -> "Sem refer\u00eancia"
                    result.matched -> "OK"
                    else -> "Erro"
                }}"
            )
            if (result.attackDebug.isNotBlank()) appendLine("Atk dbg: ${result.attackDebug}")
            if (result.defenseDebug.isNotBlank()) appendLine("Def dbg: ${result.defenseDebug}")
            if (result.staminaDebug.isNotBlank()) appendLine("HP dbg: ${result.staminaDebug}")
            if (result.notes.isNotBlank()) {
                appendLine("Obs: ${result.notes}")
            }
            if (index != results.lastIndex) {
                appendLine()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetListScreen(
    configs: List<NamingConfig>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val language = appLanguage()
    val generator = remember { NameGenerator() }
    val exampleData = remember {
        PokemonScreenData(
            pokemonName = "Beedrill",
            vivillonPattern = VivillonPattern.MARINE,
            pokedexNumber = 15,
            cp = 2806,
            ivPercent = 97,
            attIv = 15,
            defIv = 14,
            staIv = 15,
            level = 40.0,
            gender = Gender.MALE,
            type1 = "BUG",
            isFavorite = true,
            isLucky = true,
            isShadow = true,
            isPurified = true,
            hasSpecialBackground = true,
            specialBackgroundType = SpecialBackgroundType.GO_FEST,
            hasAdventureEffect = true,
            size = PokemonSize.XXL,
            pvpLeague = PvpLeague.GREAT,
            pvpRank = 1,
            masterIvBadgeMatch = true,
            legacyDebugInfo = LegacyDebugInfo(matchedLegacyMove = "Broca"),
            hasLegacyMove = true,
            evolutionFlags = setOf(EvolutionFlag.MEGA)
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(lt(language, "Formatos de Nome", "Name Presets", "Formatos de Nombre")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, lt(language, "Voltar", "Back", "Volver"))
                    }
                }
            )
        },
        floatingActionButton = {
            AppAddButton(onClick = onAdd) {
                Icon(Icons.Default.Add, lt(language, "Novo", "New", "Nuevo"))
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(configs) { _, config ->
                val previewName = generator.generate(exampleData, config)
                AppSectionCard(Modifier.fillMaxWidth()) {
                ListItem(
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(config.name, fontWeight = FontWeight.Bold) },
                    supportingContent = {
                        Text(
                            text = previewName.ifBlank { lt(language, "Preview indisponivel", "Preview unavailable", "Vista previa no disponible") },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.clickable { onEdit(config.id) },
                    trailingContent = {
                        IconButton(onClick = { if (configs.size > 1) onDelete(config.id) }) {
                            Icon(Icons.Default.Delete, lt(language, "Excluir", "Delete", "Eliminar"), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PresetEditScreen(config: NamingConfig, onBack: () -> Unit, onUpdate: (NamingConfig) -> Unit) {
    val language = appLanguage()
    var draftConfig by remember(config.id, config) { mutableStateOf(config) }
    var showSymbolPickerFor by remember { mutableStateOf<FieldSymbolOption?>(null) }
    var selectedField by remember { mutableStateOf<NamingField?>(null) }
    var showFixedTextDialog by remember { mutableStateOf(false) }
    var selectedIndexForMove by remember { mutableStateOf<Int?>(null) }

    val generator = remember { NameGenerator() }
    val availableVariableFields = remember {
        listOf(
            NamingField.POKEMON_NAME,
            NamingField.UNIQUE_FORM,
            NamingField.IV_PERCENT,
            NamingField.IV_COMBINATION,
            NamingField.MASTER_IV_BADGE,
            NamingField.LEVEL,
            NamingField.CP,
            NamingField.GENDER,
            NamingField.SIZE,
            NamingField.PVP_LEAGUE,
            NamingField.PVP_RANK,
            NamingField.TYPE,
            NamingField.SPECIAL_BACKGROUND,
            NamingField.ADVENTURE_EFFECT,
            NamingField.EVOLVE_MARKER,
            NamingField.PURIFY_MARKER,
            NamingField.LEGACY_MOVE,
            NamingField.LEGACY_MOVE_NAME,
            NamingField.EVOLUTION_TYPE,
            NamingField.SHADOW,
            NamingField.PURIFIED,
            NamingField.FAVORITE,
            NamingField.LUCKY,
            NamingField.POKEDEX_NUMBER
        )
    }
    val variableFieldGroups = remember {
        listOf(
            "Principal" to listOf(
                NamingField.POKEMON_NAME,
                NamingField.GENDER,
                NamingField.LEVEL
            ),
            "Status" to listOf(
                NamingField.IV_PERCENT,
                NamingField.IV_COMBINATION,
                NamingField.MASTER_IV_BADGE,
                NamingField.CP
            ),
            "PvP" to listOf(
                NamingField.PVP_LEAGUE,
                NamingField.PVP_RANK
            ),
            "Cole\u00e7\u00e3o" to listOf(
                NamingField.POKEDEX_NUMBER,
                NamingField.TYPE,
                NamingField.SIZE,
                NamingField.EVOLUTION_TYPE,
                NamingField.FAVORITE,
                NamingField.LUCKY,
                NamingField.SHADOW,
                NamingField.PURIFIED
            ),
            "Especial" to listOf(
                NamingField.SPECIAL_BACKGROUND,
                NamingField.ADVENTURE_EFFECT,
                NamingField.LEGACY_MOVE,
                NamingField.LEGACY_MOVE_NAME,
                NamingField.UNIQUE_FORM
            )
        )
    }
    val exampleData = remember {
        PokemonScreenData(
            pokemonName = "Beedrill",
            uniqueForm = "Heart",
            pokedexNumber = 15,
            cp = 2806,
            ivPercent = 97,
            attIv = 15,
            defIv = 14,
            staIv = 15,
            level = 40.0,
            gender = Gender.MALE,
            type1 = "BUG",
            isFavorite = true,
            isLucky = true,
            isShadow = true,
            isPurified = true,
            hasSpecialBackground = true,
            specialBackgroundType = SpecialBackgroundType.GO_FEST,
            hasAdventureEffect = true,
            shouldEvolve = true,
            shouldPurify = true,
            size = PokemonSize.XXL,
            pvpLeague = PvpLeague.GREAT,
            pvpRank = 1,
            masterIvBadgeMatch = true,
            legacyDebugInfo = LegacyDebugInfo(matchedLegacyMove = "Broca"),
            hasLegacyMove = true,
            evolutionFlags = setOf(EvolutionFlag.MEGA)
        )
    }
    val previewName = generator.generate(exampleData, draftConfig)
    val sectionTitleStyle = MaterialTheme.typography.titleSmall

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(lt(language, "Configurar Nome", "Edit Preset", "Configurar Nombre")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, lt(language, "Voltar", "Back", "Volver"))
                    }
                }
            )
        },
        bottomBar = {
            AppActionButton(
                onClick = {
                    onUpdate(draftConfig)
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Text(lt(language, "Salvar", "Save", "Guardar"))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AppGlassTextField(
                value = draftConfig.name,
                onValueChange = { draftConfig = draftConfig.copy(name = it) },
                label = { Text(lt(language, "Nome do formato", "Preset name", "Nombre del formato")) },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                lt(language, "Preview do Apelido", "Nickname Preview", "Vista previa del apodo"),
                style = sectionTitleStyle,
                fontWeight = FontWeight.Bold
            )
            AppSectionCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        previewName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(lt(language, "Padrao do Nome", "Name Pattern", "Patron del Nombre"), style = sectionTitleStyle, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val effectiveBlocks = draftConfig.effectiveBlocks()
                if (effectiveBlocks.isEmpty()) {
                    Text(
                        lt(language, "Nenhum campo adicionado ainda", "No fields added yet", "Todavia no se agregaron campos"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                } else {
                    effectiveBlocks.forEachIndexed { index, block ->
                        val isSelected = selectedIndexForMove == index
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                                .border(if (isSelected) 2.dp else 0.dp, Color.White, RoundedCornerShape(8.dp))
                                .clickable {
                                    if (selectedIndexForMove == null) {
                                        selectedIndexForMove = index
                                    } else {
                                        val selected = selectedIndexForMove!!
                                        val reordered = effectiveBlocks.toMutableList()
                                        Collections.swap(reordered, selected, index)
                                        draftConfig = draftConfig.copy(blocks = reordered)
                                        selectedIndexForMove = null
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    block.localizedLabel(language),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Icon(
                                    Icons.Default.Clear,
                                    null,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(start = 4.dp)
                                        .clickable {
                                            val updated = effectiveBlocks.toMutableList()
                                            updated.removeAt(index)
                                            draftConfig = draftConfig.copy(blocks = updated)
                                        },
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary
                                )
                            }
                        }
                    }
                }
            }

            Text(lt(language, "Campos variaveis", "Variable Fields", "Campos Variables"), style = sectionTitleStyle, fontWeight = FontWeight.Bold)
            variableFieldGroups.forEach { (groupTitle, groupFields) ->
                VariableFieldGroupCard(
                    title = when (groupTitle) {
                        "Principal" -> lt(language, "Principal", "Main", "Principal")
                        "Status" -> lt(language, "Status", "Status", "Estado")
                        "PvP" -> "PvP"
                        "Cole\u00e7\u00e3o" -> lt(language, "Colecao", "Collection", "Coleccion")
                        else -> lt(language, "Especial", "Special", "Especial")
                    },
                    fields = groupFields.filter { it in availableVariableFields },
                    onFieldClick = { selectedField = it },
                    extraActions = if (groupTitle == "Especial") {
                        listOf(
                            VariableExtraAction(
                                label = lt(language, "Texto Livre", "Free Text", "Texto Libre"),
                                onClick = { showFixedTextDialog = true }
                            ),
                            VariableExtraAction(
                                label = NamingField.EVOLVE_MARKER.localizedLabel(language),
                                onClick = { selectedField = NamingField.EVOLVE_MARKER }
                            ),
                            VariableExtraAction(
                                label = NamingField.PURIFY_MARKER.localizedLabel(language),
                                onClick = { selectedField = NamingField.PURIFY_MARKER }
                            )
                        )
                    } else {
                        null
                    }
                )
            }

            Text("${lt(language, "Limite de Caracteres", "Character Limit", "Limite de Caracteres")}: ${draftConfig.maxLength}", style = sectionTitleStyle, fontWeight = FontWeight.Bold)
            AppValueSlider(
                value = draftConfig.maxLength.toFloat(),
                onValueChange = { draftConfig = draftConfig.copy(maxLength = it.toInt()) },
                valueRange = 6f..30f
            )
        }
    }

    selectedField?.let { field ->
        FieldConfigDialog(
            field = field,
            symbolOptions = symbolOptionsForField(field, draftConfig),
            currentSymbols = draftConfig.symbols,
            onDismiss = { selectedField = null },
            onPickSymbol = { option -> showSymbolPickerFor = option },
            onAdd = {
                draftConfig = draftConfig.copy(
                    blocks = draftConfig.effectiveBlocks() + NamingBlock(
                        type = NamingBlockType.VARIABLE,
                        field = field
                    )
                )
                selectedField = null
            }
        )
    }

    showSymbolPickerFor?.let { option ->
        SymbolPickerDialog(
            title = option.label,
            initialValue = option.value,
            onDismiss = { showSymbolPickerFor = null },
            onSymbolSelected = { newSymbol ->
                val updatedSymbols = draftConfig.symbols.toMutableMap()
                updatedSymbols[option.key] = newSymbol
                draftConfig = draftConfig.copy(symbols = updatedSymbols)
                showSymbolPickerFor = null
            }
        )
    }

    if (showFixedTextDialog) {
        FixedTextDialog(
            onDismiss = { showFixedTextDialog = false },
            onAdd = { fixedText ->
                draftConfig = draftConfig.copy(
                    blocks = draftConfig.effectiveBlocks() + NamingBlock(
                        type = NamingBlockType.FIXED_TEXT,
                        fixedText = fixedText
                    )
                )
                showFixedTextDialog = false
            }
        )
    }
}

@Composable
private fun FieldConfigDialog(
    field: NamingField,
    symbolOptions: List<FieldSymbolOption>,
    currentSymbols: Map<String, String>,
    onDismiss: () -> Unit,
    onPickSymbol: (FieldSymbolOption) -> Unit,
    onAdd: () -> Unit
) {
    val language = appLanguage()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(field.localizedLabel(language), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(fieldDescription(field, language), color = Color(0xFF555555))
                if (field == NamingField.UNIQUE_FORM) {
                    UniqueFormFieldConfigContent(
                        onPickSymbol = onPickSymbol,
                        currentSymbols = currentSymbols
                    )
                } else if (symbolOptions.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        symbolOptions.forEach { option ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${option.label}: ${option.value}")
                                AppSecondaryButton(onClick = { onPickSymbol(option) }) {
                                    Text(lt(language, "EDITAR", "EDIT", "EDITAR"))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppActionButton(onClick = onAdd) {
                    Text(lt(language, "ADICIONAR", "ADD", "AGREGAR"))
                }
                Spacer(modifier = Modifier.width(12.dp))
                AppSecondaryButton(onClick = onDismiss) {
                    Text(lt(language, "Cancelar", "Cancel", "Cancelar"))
                }
            }
        }
    )
}

@Composable
private fun UniqueFormFieldConfigContent(
    onPickSymbol: (FieldSymbolOption) -> Unit,
    currentSymbols: Map<String, String>
) {
    val specs = remember {
        buildList {
            addAll(UniquePokemonCatalog.allSpecs())
            add(
                com.mewname.app.domain.UniquePokemonSpec(
                    assetFolder = "vivillon",
                    pokemonNames = setOf("VIVILLON"),
                    options = VivillonPattern.entries.map { pattern ->
                        com.mewname.app.domain.UniquePokemonFormOption(
                            label = pattern.label,
                            code = pattern.symbolKey.removePrefix("VIVILLON_")
                        )
                    }
                )
            )
        }
    }
    var selectedTabIndex by remember { mutableStateOf(0) }
    val currentSpec = specs.getOrNull(selectedTabIndex) ?: return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppPillTabRow(selectedTabIndex = selectedTabIndex) {
            specs.forEachIndexed { index, spec ->
                val tabTitle = spec.pokemonNames.first().lowercase().replaceFirstChar { it.titlecase() }
                AppPillTab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(tabTitle) }
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState())
        ) {
            currentSpec.options.forEachIndexed { index, option ->
                val symbolKey = if (currentSpec.assetFolder.equals("unown", ignoreCase = true)) {
                    "UNOWN_${option.code}"
                } else if (currentSpec.assetFolder.equals("vivillon", ignoreCase = true)) {
                    "VIVILLON_${option.code}"
                } else {
                    UniquePokemonCatalog.symbolKeyForAsset(currentSpec.assetFolder, option.code)
                }
                val pokemonLabel = currentSpec.pokemonNames.first().lowercase().replaceFirstChar { it.titlecase() }
                val displayLabel = if (currentSpec.assetFolder.equals("spinda", ignoreCase = true)) {
                    "Spinda #${index + 1}: ${currentSymbols[symbolKey] ?: option.code}"
                } else if (currentSpec.assetFolder.equals("unown", ignoreCase = true)) {
                    "Unown ${option.label}: ${currentSymbols[symbolKey] ?: option.code}"
                } else if (currentSpec.assetFolder.equals("vivillon", ignoreCase = true)) {
                    "Vivillon ${option.label}: ${currentSymbols[symbolKey] ?: option.code}"
                } else {
                    "${option.label}: ${currentSymbols[symbolKey] ?: option.code}"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(displayLabel)
                    AppSecondaryButton(
                        onClick = {
                            onPickSymbol(
                                FieldSymbolOption(
                                    key = symbolKey,
                                    label = if (currentSpec.assetFolder.equals("spinda", ignoreCase = true)) {
                                        "Spinda #${index + 1}"
                                    } else if (currentSpec.assetFolder.equals("unown", ignoreCase = true)) {
                                        "Unown ${option.label}"
                                    } else if (currentSpec.assetFolder.equals("vivillon", ignoreCase = true)) {
                                        "Vivillon ${option.label}"
                                    } else {
                                        "$pokemonLabel ${option.label}"
                                    },
                                    value = currentSymbols[symbolKey] ?: option.code
                                )
                            )
                        }
                    ) {
                        Text("EDITAR")
                    }
                }
            }
        }
    }
}

private data class FieldSymbolOption(
    val key: String,
    val label: String,
    val value: String
)

private data class VariableExtraAction(
    val label: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariableFieldGroupCard(
    title: String,
    fields: List<NamingField>,
    onFieldClick: (NamingField) -> Unit,
    extraActions: List<VariableExtraAction>? = null
) {
    val language = appLanguage()
    AppSectionCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val rows = buildList {
                addAll(fields.map { field ->
                    VariableActionItem.Field(field)
                })
                extraActions.orEmpty().forEach { action ->
                    add(VariableActionItem.Extra(action))
                }
            }.chunked(3)
            rows.forEach { rowFields ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowFields.forEach { item ->
                        when (item) {
                            is VariableActionItem.Field -> {
                                val field = item.field
                                VariableActionCard(
                                    label = field.localizedLabel(language),
                                    leadingIcon = if (field == NamingField.MASTER_IV_BADGE) {
                                        {
                                            Text(
                                                "+",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.weight(1f),
                                    onClick = { onFieldClick(field) }
                                )
                            }
                            is VariableActionItem.Extra -> {
                                VariableActionCard(
                                    label = item.action.label,
                                    modifier = Modifier.weight(1f),
                                    onClick = item.action.onClick
                                )
                            }
                        }
                    }
                    repeat(3 - rowFields.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private sealed interface VariableActionItem {
    data class Field(val field: NamingField) : VariableActionItem
    data class Extra(val action: VariableExtraAction) : VariableActionItem
}

@Composable
private fun VariableActionCard(
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    AppSectionCard(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (leadingIcon != null) {
                leadingIcon()
            } else {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun fieldDescription(field: NamingField, language: AppLanguage): String {
    return when (field) {
        NamingField.IV_PERCENT -> lt(language, "Exibir o IV medio do Pokemon no apelido.", "Show the average IV in the nickname.", "Mostrar el IV medio en el apodo.")
        NamingField.POKEMON_NAME -> lt(language, "Adicionar o nome do Pokemon reconhecido pelo app.", "Add the Pokemon name recognized by the app.", "Agregar el nombre del Pokemon reconocido por la app.")
        NamingField.UNOWN_LETTER -> lt(language, "Campo legado do Unown. A tela de definicao usa agora Forma unica para variantes especiais.", "Legacy Unown field. Use Unique Form for special variants.", "Campo legado de Unown. Usa Forma unica para variantes especiales.")
        NamingField.UNIQUE_FORM -> lt(language, "Mostrar o codigo curto da forma detectada.", "Show the short code of the detected form.", "Mostrar el codigo corto de la forma detectada.")
        NamingField.VIVILLON_PATTERN -> lt(language, "Campo legado do Vivillon. Essa opcao nao aparece mais na tela de definicao.", "Legacy Vivillon field. This option is hidden in the editor.", "Campo legado de Vivillon. Esta opcion ya no aparece en el editor.")
        NamingField.LEVEL -> lt(language, "Mostrar o nivel atual do Pokemon.", "Show the current Pokemon level.", "Mostrar el nivel actual del Pokemon.")
        NamingField.CP -> lt(language, "Mostrar os pontos de combate atuais do Pokemon.", "Show the current combat power.", "Mostrar los puntos de combate actuales.")
        NamingField.GENDER -> lt(language, "Exibir um simbolo configuravel para macho ou femea.", "Show a configurable symbol for male or female.", "Mostrar un simbolo configurable para macho o hembra.")
        NamingField.SIZE -> lt(language, "Mostrar XXS, XS, XL ou XXL usando um simbolo para cada tamanho.", "Show XXS, XS, XL or XXL with one symbol each.", "Mostrar XXS, XS, XL o XXL usando un simbolo para cada tamano.")
        NamingField.MASTER_IV_BADGE -> lt(language, "Compara a combinacao A/D/S com a melhor da familia no ranking Master.", "Compare the A/D/S spread with the best family spread for Master ranking.", "Compara la combinacion A/D/S con la mejor de la familia en Master.")
        NamingField.SPECIAL_BACKGROUND -> lt(language, "Exibir uma sigla configuravel para o tipo de fundo especial selecionado.", "Show a configurable abbreviation for the selected special background type.", "Mostrar una abreviatura configurable para el tipo de fondo especial seleccionado.")
        NamingField.PVP_LEAGUE -> lt(language, "Mostrar a liga PvP estimada.", "Show the estimated PvP league.", "Mostrar la liga PvP estimada.")
        NamingField.PVP_RANK -> lt(language, "Mostrar o ranking PvP calculado.", "Show the calculated PvP rank.", "Mostrar el ranking PvP calculado.")
        NamingField.LEGACY_MOVE -> lt(language, "Exibir o simbolo se o Pokemon tiver movimento legado.", "Show a symbol if the Pokemon has a legacy move.", "Mostrar un simbolo si el Pokemon tiene movimiento legado.")
        NamingField.LEGACY_MOVE_NAME -> lt(language, "Exibir o nome do ataque legado detectado.", "Show the detected legacy move name.", "Mostrar el nombre del ataque legado detectado.")
        NamingField.TYPE -> lt(language, "Adicionar o tipo principal do Pokemon usando uma sigla configuravel.", "Add the main type using a configurable abbreviation.", "Agregar el tipo principal usando una abreviatura configurable.")
        NamingField.ADVENTURE_EFFECT -> lt(language, "Exibir o marcador de efeito aventura.", "Show the adventure effect marker.", "Mostrar el marcador de efecto aventura.")
        NamingField.EVOLVE_MARKER -> lt(language, "Exibir o simbolo configurado quando o checkbox Evoluir estiver marcado.", "Show the configured symbol when the Evolve checkbox is checked.", "Mostrar el simbolo configurado cuando el checkbox Evolucionar este marcado.")
        NamingField.PURIFY_MARKER -> lt(language, "Exibir o simbolo configurado quando o checkbox Purificar estiver marcado.", "Show the configured symbol when the Purify checkbox is checked.", "Mostrar el simbolo configurado cuando el checkbox Purificar este marcado.")
        NamingField.EVOLUTION_TYPE -> lt(language, "Mostrar Baby, Estagio 1, Estagio 2, Mega, Dynamax e Gigantamax.", "Show Baby, Stage 1, Stage 2, Mega, Dynamax and Gigantamax.", "Mostrar Baby, Etapa 1, Etapa 2, Mega, Dynamax y Gigamax.")
        NamingField.SHADOW -> lt(language, "Exibir o simbolo se o Pokemon for sombrio.", "Show the symbol if the Pokemon is shadow.", "Mostrar el simbolo si el Pokemon es oscuro.")
        NamingField.PURIFIED -> lt(language, "Exibir o simbolo se o Pokemon for purificado.", "Show the symbol if the Pokemon is purified.", "Mostrar el simbolo si el Pokemon es purificado.")
        NamingField.FAVORITE -> lt(language, "Exibir o simbolo de favorito.", "Show the favorite symbol.", "Mostrar el simbolo de favorito.")
        NamingField.LUCKY -> lt(language, "Exibir o simbolo de sortudo.", "Show the lucky symbol.", "Mostrar el simbolo de suerte.")
        NamingField.POKEDEX_NUMBER -> lt(language, "Mostrar o numero da Pokedex.", "Show the Pokedex number.", "Mostrar el numero de la Pokedex.")
        NamingField.IV_COMBINATION -> lt(language, "Mostrar os IVs em formato Ataque/Defesa/Stamina.", "Show IVs as Attack/Defense/Stamina.", "Mostrar los IVs en formato Ataque/Defensa/Stamina.")
    }
}

private fun symbolOptionsForField(field: NamingField, config: NamingConfig): List<FieldSymbolOption> {
    val fallbackSymbols = defaultSymbols()
    fun option(key: String, label: String) = FieldSymbolOption(key, label, config.symbols[key] ?: fallbackSymbols[key].orEmpty())

    return when (field) {
        NamingField.GENDER -> listOf(
            option("MALE", "Masculino"),
            option("FEMALE", "Feminino")
        )
        NamingField.FAVORITE -> listOf(option("FAVORITE", "Favorito"))
        NamingField.LUCKY -> listOf(option("LUCKY", "Sortudo"))
        NamingField.SHADOW -> listOf(option("SHADOW", "Sombrio"))
        NamingField.PURIFIED -> listOf(option("PURIFIED", "Purificado"))
        NamingField.SPECIAL_BACKGROUND -> listOf(
            option("SPECIAL_BACKGROUND", "Fundo especial"),
            option("SPECIAL_BACKGROUND_GO_FEST", "GO Fest"),
            option("SPECIAL_BACKGROUND_WILD_AREA", "Wild Area"),
            option("SPECIAL_BACKGROUND_LOCATION", "Localidade"),
            option("SPECIAL_BACKGROUND_MEGA_EVOLUTION", "Mega evolução"),
            option("SPECIAL_BACKGROUND_COMMUNITY_DAY", "Dia da Comunidade")
        )
        NamingField.ADVENTURE_EFFECT -> listOf(option("ADVENTURE_EFFECT", "Efeito aventura"))
        NamingField.EVOLVE_MARKER -> listOf(option("EVOLVE", "Evoluir"))
        NamingField.PURIFY_MARKER -> listOf(option("PURIFY", "Purificar"))
        NamingField.TYPE -> listOf(
            option("TYPE_NORMAL", "Normal"),
            option("TYPE_FIRE", "Fogo"),
            option("TYPE_WATER", "\u00c1gua"),
            option("TYPE_GRASS", "Planta"),
            option("TYPE_ELECTRIC", "El\u00e9trico"),
            option("TYPE_ICE", "Gelo"),
            option("TYPE_FIGHTING", "Lutador"),
            option("TYPE_POISON", "Venenoso"),
            option("TYPE_GROUND", "Terrestre"),
            option("TYPE_FLYING", "Voador"),
            option("TYPE_PSYCHIC", "Ps\u00edquico"),
            option("TYPE_BUG", "Inseto"),
            option("TYPE_ROCK", "Pedra"),
            option("TYPE_GHOST", "Fantasma"),
            option("TYPE_DRAGON", "Drag\u00e3o"),
            option("TYPE_DARK", "Sombrio"),
            option("TYPE_STEEL", "A\u00e7o"),
            option("TYPE_FAIRY", "Fada")
        )
        NamingField.SIZE -> listOf(
            option("XXL", "XXL"),
            option("XL", "XL"),
            option("XS", "XS"),
            option("XXS", "XXS")
        )
        NamingField.MASTER_IV_BADGE -> listOf(
            option("MASTER_IV_MATCH", "Melhor combina\u00e7\u00e3o"),
            option("MASTER_IV_OTHER", "Outra combina\u00e7\u00e3o")
        )
        NamingField.PVP_LEAGUE -> listOf(
            option("LITTLE_LEAGUE", "Copinha"),
            option("GREAT_LEAGUE", "Great League"),
            option("ULTRA_LEAGUE", "Ultra League"),
            option("MASTER_LEAGUE", "Master League")
        )
        NamingField.LEGACY_MOVE -> listOf(option("LEGACY", "Ataque legado"))
        NamingField.LEGACY_MOVE_NAME -> emptyList()
        NamingField.EVOLUTION_TYPE -> listOf(
            option("BABY", "Baby"),
            option("STAGE1", "Est\u00e1gio 1"),
            option("STAGE2", "Est\u00e1gio 2"),
            option("MEGA", "Mega"),
            option("DYNAMAX", "Dynamax"),
            option("GIGANTAMAX", "Gigantamax")
        )
        NamingField.VIVILLON_PATTERN -> emptyList()
        else -> emptyList()
    }
}

@Composable
fun SymbolPickerDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSymbolSelected: (String) -> Unit
) {
    val language = appLanguage()
    val commonSymbols = listOf(
        "\u2642", "\u2640", "M", "F", "#", "\u00B6", "*", "+", "SH", "PU", "FE", "AV", "XXL", "XXS",
        "XL", "XS", "GL", "UL", "ML", "CP", "L", "G", "D", "#", "!", "1", "2", "3", "BY", "tm", "?"
    )
    var customText by remember(title, initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppGlassTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    label = { Text(lt(language, "Texto customizado", "Custom text", "Texto personalizado")) }
                )
                AppActionButton(
                    onClick = { onSymbolSelected(customText) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(lt(language, "Aplicar", "Apply", "Aplicar"))
                }
                HorizontalDivider()
                LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.height(200.dp)) {
                    gridItems(commonSymbols) { symbol ->
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clickable { onSymbolSelected(symbol) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(symbol, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppSecondaryButton(onClick = onDismiss) {
                Text(lt(language, "Fechar", "Close", "Cerrar"))
            }
        }
    )
}

@Composable
private fun ReviewDialog(
    initialData: PokemonScreenData,
    fields: List<NamingField>,
    configs: List<NamingConfig>,
    bitmap: Bitmap?,
    onDismiss: () -> Unit,
    onConfirm: (PokemonScreenData) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        ReviewEditorCard(
            initialData = initialData,
            fields = fields,
            configs = configs,
            bitmap = bitmap,
            onConfirm = onConfirm,
            onCancel = onDismiss
        )
    }
}

@Composable
private fun FixedTextDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    val language = appLanguage()
    var customText by remember { mutableStateOf(TextFieldValue("")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(lt(language, "Adicionar texto fixo", "Add fixed text", "Agregar texto fijo")) },
        text = {
            AppGlassTextField(
                value = customText,
                onValueChange = { customText = it },
                label = { Text(lt(language, "Ex: XXL, FE, espaco", "Ex: XXL, FE, space", "Ej: XXL, FE, espacio")) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppActionButton(
                    onClick = {
                        if (customText.text.isNotBlank()) {
                            onAdd(customText.text)
                        }
                    }
                ) {
                    Text(lt(language, "Adicionar", "Add", "Agregar"))
                }
                Spacer(modifier = Modifier.width(12.dp))
                AppSecondaryButton(onClick = onDismiss) {
                    Text(lt(language, "Cancelar", "Cancel", "Cancelar"))
                }
            }
        }
    )
}
