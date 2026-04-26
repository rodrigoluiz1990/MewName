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
import androidx.compose.material3.Switch
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
            if (intent?.action == OverlayService.ACTION_CAPTURE_PERMISSION_INVALID) {
                viewModel.setBubbleOptionVisible(false)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this,
            capturePermissionInvalidReceiver,
            IntentFilter(OverlayService.ACTION_CAPTURE_PERMISSION_INVALID),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val appContext = LocalContext.current

                LaunchedEffect(Unit) {
                    viewModel.loadConfigs(appContext)
                    viewModel.checkForAppUpdate()
                }

                BackHandler(enabled = uiState.currentScreen != AppScreen.HOME) {
                    when (uiState.currentScreen) {
                        AppScreen.PRESET_EDIT -> viewModel.navigateTo(AppScreen.PRESET_LIST)
                        AppScreen.PRESET_LIST,
                        AppScreen.LEGACY_MOVES,
                        AppScreen.ADVENTURE_EFFECTS,
                        AppScreen.RAID_PLANNER,
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
                when (uiState.currentScreen) {
                    AppScreen.HOME -> HomeScreen(
                        uiState = uiState,
                        onClear = viewModel::clearResults,
                        onGoToPresets = { viewModel.navigateTo(AppScreen.PRESET_LIST) },
                        onGoToLegacyMoves = { viewModel.navigateTo(AppScreen.LEGACY_MOVES) },
                        onGoToAdventureEffects = { viewModel.navigateTo(AppScreen.ADVENTURE_EFFECTS) },
                        onGoToRaidPlanner = { viewModel.navigateTo(AppScreen.RAID_PLANNER) },
                        onGoToTypes = { viewModel.navigateTo(AppScreen.TYPE_CHART) },
                        onGoToMoves = { viewModel.navigateTo(AppScreen.MOVEDEX) },
                        onGoToPokedex = { viewModel.navigateTo(AppScreen.POKEDEX) },
                        onGoToFilters = { viewModel.navigateTo(AppScreen.FILTER_BUILDER) },
                        onGoToTestMenu = { viewModel.navigateTo(AppScreen.TEST_MENU) },
                        onGoToHelp = { viewModel.navigateTo(AppScreen.HELP_MENU) },
                        onGoToAppUpdate = { viewModel.navigateTo(AppScreen.APP_UPDATE) },
                        onDismissReview = viewModel::dismissReview,
                        onApplyReview = viewModel::applyReview,
                        onBubbleOptionVisibleChange = viewModel::setBubbleOptionVisible,
                        onAppLanguageChange = { viewModel.setAppLanguage(appContext, it) }
                    )

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
                            onRunExistingValidation = { viewModel.runDebugIvSampleValidation(appContext) }
                        )
                    }
                }
                }
            }
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
    onGoToPresets: () -> Unit,
    onGoToLegacyMoves: () -> Unit,
    onGoToAdventureEffects: () -> Unit,
    onGoToRaidPlanner: () -> Unit,
    onGoToTypes: () -> Unit,
    onGoToMoves: () -> Unit,
    onGoToPokedex: () -> Unit,
    onGoToFilters: () -> Unit,
    onGoToTestMenu: () -> Unit,
    onGoToHelp: () -> Unit,
    onGoToAppUpdate: () -> Unit,
    onDismissReview: () -> Unit,
    onApplyReview: (PokemonScreenData) -> Unit,
    onBubbleOptionVisibleChange: (Boolean) -> Unit,
    onAppLanguageChange: (AppLanguage) -> Unit
) {
    val context = LocalContext.current
    val language = appLanguage()
    val releaseLabel = BuildConfig.RELEASE_TAG.takeUnless { it.isBlank() || it == "dev" } ?: "local"
    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onBubbleOptionVisibleChange(true)
            val intent = Intent(context, OverlayService::class.java).apply {
                putExtra("PROJECTION_DATA", result.data)
            }
            context.startForegroundService(intent)
        }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier.background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                )
            ) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    title = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "MewName",
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppLanguage.entries.forEach { option ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(
                                                    if (uiState.appLanguage == option) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f)
                                                    } else {
                                                        Color.Transparent
                                                    }
                                                )
                                                .border(
                                                    width = if (uiState.appLanguage == option) 1.dp else 0.dp,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.42f),
                                                    shape = RoundedCornerShape(999.dp)
                                                )
                                                .clickable { onAppLanguageChange(option) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = appLanguageFlag(option),
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = releaseLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            Button(
                onClick = {
                    if (uiState.showBubbleOption) {
                        if (!Settings.canDrawOverlays(context)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            projectionLauncher.launch(mpManager.createScreenCaptureIntent())
                        }
                    } else {
                        val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        projectionLauncher.launch(mpManager.createScreenCaptureIntent())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    if (uiState.showBubbleOption) {
                        lt(language, "Iniciar sobreposicao", "Start overlay", "Iniciar superposicion")
                    } else {
                        lt(language, "Solicitar permissao de captura", "Request capture permission", "Solicitar permiso de captura")
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeActionSquare(
                    title = lt(language, "Calendario", "Calendar", "Calendario"),
                    iconRes = null,
                    customIcon = { HomeMenuGlyph("calendar") },
                    onClick = { openExternalUrl(context, "https://rodrigoluiz1990.github.io/laboratorio-do-sam/Calendario/calendario.html") },
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
                HomeActionSquare(
                    title = lt(language, "Definir Nomes", "Name Presets", "Definir Nombres"),
                    iconRes = null,
                    customIcon = { HomeMenuGlyph("presets") },
                    onClick = onGoToPresets,
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
                HomeActionSquare(
                    title = lt(language, "Filtros", "Filters", "Filtros"),
                    iconRes = null,
                    customIcon = { HomeMenuGlyph("filters") },
                    onClick = onGoToFilters,
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
                HomeActionSquare(
                    title = "Pokedex",
                    iconRes = null,
                    customIcon = { HomeMenuGlyph("pokedex") },
                    onClick = onGoToPokedex,
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
                HomeActionSquare(
                    title = lt(language, "Raids", "Raids", "Raids"),
                    iconRes = null,
                    customIcon = { HomeMenuGlyph("raid") },
                    onClick = onGoToRaidPlanner,
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
                HomeActionSquare(
                    title = lt(language, "Tipos", "Types", "Tipos"),
                    iconRes = null,
                    customIcon = { HomeMenuGlyph("types") },
                    onClick = onGoToTypes,
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
                HomeActionSquare(
                    title = lt(language, "Efeitos de Aventura", "Adventure Effects", "Efectos de Aventura"),
                    iconRes = null,
                    customIcon = { HomeMenuGlyph("adventure") },
                    onClick = onGoToAdventureEffects,
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
                HomeActionSquare(
                    title = lt(language, "Ataques Legados", "Legacy Moves", "Ataques Legado"),
                    iconRes = null,
                    customIcon = { HomeMenuGlyph("legacy") },
                    onClick = onGoToLegacyMoves,
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
                HomeActionSquare(
                    title = lt(language, "Ataques", "Moves", "Ataques"),
                    iconRes = null,
                    customIcon = { HomeMenuGlyph("moves") },
                    onClick = onGoToMoves,
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
                HomeActionSquare(
                    title = lt(language, "Teste", "Test", "Prueba"),
                    iconRes = null,
                    customIcon = { HomeMenuGlyph("test") },
                    onClick = onGoToTestMenu,
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
                HomeActionSquare(
                    title = if (uiState.isCheckingForUpdate) {
                        lt(language, "Verificando...", "Checking...", "Verificando...")
                    } else {
                        lt(language, "Atualizar", "Update", "Actualizar")
                    },
                    iconRes = null,
                    customIcon = { HomeMenuGlyph("update") },
                    onClick = onGoToAppUpdate,
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
                HomeActionSquare(
                    title = lt(language, "Ajuda", "Help", "Ayuda"),
                    iconRes = null,
                    customIcon = { HomeMenuGlyph("help") },
                    onClick = onGoToHelp,
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
            }

            AnalysisTabsSection(uiState = uiState, onClear = onClear)
        }

        if (uiState.isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
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
                        CircularProgressIndicator()
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
private fun AppUpdateScreen(
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
            TopAppBar(
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
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
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(lt(language, "Verificando nova release...", "Checking for a new release...", "Buscando nueva release..."), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                uiState.latestAppUpdate != null -> {
                    val update = uiState.latestAppUpdate
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(lt(language, "Nova release disponivel", "New release available", "Nueva release disponible"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${lt(language, "Versao", "Version", "Version")}: ${update.tagName}", style = MaterialTheme.typography.bodyMedium)
                            if (update.releaseName.isNotBlank()) {
                                Text("${lt(language, "Titulo", "Title", "Titulo")}: ${update.releaseName}", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (update.publishedAt.isNotBlank()) {
                                Text("${lt(language, "Publicada em", "Published at", "Publicada en")}: ${update.publishedAt}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(lt(language, "O que mudou", "What's changed", "Que cambio"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                update.releaseNotes.ifBlank { lt(language, "Sem descricao cadastrada para esta release.", "No description was added for this release.", "No hay descripcion registrada para esta release.") },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    TextButton(
                        onClick = { openExternalUrl(context, update.releasePageUrl) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(lt(language, "Ver pagina da release", "View release page", "Ver pagina de la release"))
                    }

                    Button(
                        onClick = {
                            openExternalUrl(context, update.apkDownloadUrl ?: update.releasePageUrl)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (update.apkDownloadUrl != null) lt(language, "Baixar atualizacao", "Download update", "Descargar actualizacion") else lt(language, "Abrir release", "Open release", "Abrir release"))
                    }
                }

                uiState.appUpdateError != null -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
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

                    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text(lt(language, "Tentar novamente", "Try again", "Intentar de nuevo"))
                    }
                }

                else -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(lt(language, "Seu app ja esta atualizado", "Your app is up to date", "Tu app ya esta actualizada"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                uiState.appUpdateStatusMessage ?: lt(language, "Nenhuma atualizacao mais nova foi encontrada no GitHub.", "No newer update was found on GitHub.", "No se encontro una actualizacion mas nueva en GitHub."),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
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
    val entries = remember { GameCatalogRepository.loadLegacyMoveCatalog(context) }
    var query by remember { mutableStateOf("") }
    val normalizedQuery = remember(query) {
        java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .uppercase()
            .trim()
    }
    val filteredEntries = remember(entries, normalizedQuery) {
        if (normalizedQuery.isBlank()) entries else entries.filter { entry ->
            entry.searchTerms.any { it.contains(normalizedQuery) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(lt(language, "Buscar Pokemon ou ataque", "Search Pokemon or move", "Buscar Pokemon o ataque")) },
                    singleLine = true
                )
            }
            items(filteredEntries) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(entry.pokemon, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            entry.moves.joinToString(" • ").ifBlank { lt(language, "Sem golpes cadastrados", "No registered moves", "Sin ataques registrados") },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdventureEffectsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val language = appLanguage()
    val entries = remember { GameCatalogRepository.loadAdventureEffectCatalog(context) }

    Scaffold(
        topBar = {
            TopAppBar(
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries) { entry ->
                AdventureEffectCard(entry = entry, language = language)
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
            TopAppBar(
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
            Card(modifier = Modifier.fillMaxWidth()) {
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
            TopAppBar(
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
                title = lt(language, "Gerar nome por imagem", "Generate name from image", "Generar nombre por imagen"),
                iconRes = null,
                customIcon = { HomeMenuGlyph("capture") },
                onClick = onPickImage,
                modifier = Modifier.fillMaxWidth(0.31f)
            )
            HomeActionSquare(
                title = lt(language, "Validar amostras", "Validate samples", "Validar muestras"),
                iconRes = null,
                customIcon = { HomeMenuGlyph("validation") },
                onClick = onGoToIvValidation,
                modifier = Modifier.fillMaxWidth(0.31f)
            )
            HomeActionSquare(
                title = lt(language, "Doacao", "Donate", "Donacion"),
                iconRes = null,
                customIcon = { HomeMenuGlyph("donation") },
                onClick = onGoToDonation,
                modifier = Modifier.fillMaxWidth(0.31f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpMenuScreen(
    onBack: () -> Unit,
    onGoToPrivacy: () -> Unit
) {
    val language = appLanguage()
    Scaffold(
        topBar = {
            TopAppBar(
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(lt(language, "Como usar cada tela", "How to use each screen", "Como usar cada pantalla"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        HelpLine("Calendario", lt(language, "Abre o calendario externo de eventos. Use para conferir raids, horas em destaque, dias comunitarios e eventos antes de montar filtros.", "Opens the external event calendar. Use it to check raids, spotlight hours, community days, and events before building filters.", "Abre el calendario externo de eventos. Usalo para revisar raids, horas destacadas, dias comunitarios y eventos antes de crear filtros."))
                        HelpLine(lt(language, "Definir Nomes", "Name Presets", "Definir Nombres"), lt(language, "Crie os formatos que a bolha usa para gerar apelidos. Adicione campos como nome, IV, liga PvP, genero, tamanho, fundo especial e ataques legados. A bolha usa apenas formatos ja salvos.", "Create the formats used by the bubble to generate nicknames. Add fields like name, IV, PvP league, gender, size, special background, and legacy moves. The bubble only uses saved formats.", "Crea los formatos que usa la burbuja para generar apodos. Agrega campos como nombre, IV, liga PvP, genero, tamano, fondo especial y ataques legado. La burbuja solo usa formatos guardados."))
                        HelpLine(lt(language, "Filtros", "Filters", "Filtros"), lt(language, "Monte buscas para Pokemon ou Pessoas. Toque uma opcao para incluir, toque de novo para excluir e escolha se ela combina com & ou vira alternativa com virgula. O texto copiado respeita o idioma selecionado.", "Build searches for Pokemon or People. Tap an option to include it, tap again to exclude it, and choose whether it combines with & or becomes an alternative with comma. Copied text follows the selected language.", "Crea busquedas para Pokemon o Personas. Toca una opcion para incluirla, otra vez para excluirla y elige si combina con & o si es alternativa con coma. El texto copiado respeta el idioma seleccionado."))
                        HelpLine("Pokedex", lt(language, "Pesquise por nome, numero ou apelido do catalogo. Filtre por tipo e abra os cards para comparar tipos, atributos base e formas conhecidas.", "Search by name, number, or catalog alias. Filter by type and use the cards to compare typing, base stats, and known forms.", "Busca por nombre, numero o alias del catalogo. Filtra por tipo y usa las tarjetas para comparar tipos, estadisticas base y formas conocidas."))
                        HelpLine(lt(language, "Raids", "Raids", "Raids"), lt(language, "Use a leitura da bolha ou selecione o chefe manualmente. Escolha os tipos do chefe, copie o filtro e compare as colunas Atacantes e Defensores.", "Use the bubble reading or manually select the boss. Pick the boss types, copy the filter, and compare the Attackers and Defenders columns.", "Usa la lectura de la burbuja o selecciona el jefe manualmente. Elige los tipos del jefe, copia el filtro y compara las columnas Atacantes y Defensores."))
                        HelpLine(lt(language, "Tipos", "Types", "Tipos"), lt(language, "Selecione ate dois tipos defensivos para ver fraquezas, resistencias e resistencias duplas. Use junto com Raids para decidir ataque e sobrevivencia.", "Select up to two defensive types to see weaknesses, resistances, and double resistances. Use it with Raids to decide attack and survivability.", "Selecciona hasta dos tipos defensivos para ver debilidades, resistencias y resistencias dobles. Usalo con Raids para decidir ataque y supervivencia."))
                        HelpLine(lt(language, "Ataques", "Moves", "Ataques"), lt(language, "Pesquise ataques por nome, tipo e categoria. Cada registro mostra dados de ginasio e PvP para comparar dano, energia e duracao.", "Search moves by name, type, and category. Each row shows Gym and PvP data so you can compare damage, energy, and duration.", "Busca ataques por nombre, tipo y categoria. Cada registro muestra datos de gimnasio y PvP para comparar dano, energia y duracion."))
                        HelpLine(lt(language, "Teste", "Test", "Prueba"), lt(language, "Agrupa ferramentas de manutencao: gerar nome a partir de imagem, validar amostras e abrir a tela de doacao.", "Groups maintenance tools: generate a name from an image, validate samples, and open the donation screen.", "Agrupa herramientas de mantenimiento: generar nombre desde imagen, validar muestras y abrir la pantalla de donacion."))
                        HelpLine(lt(language, "Atualizar", "Update", "Actualizar"), lt(language, "Consulta releases do app e mostra a versao instalada. Use quando quiser verificar se ha APK mais recente.", "Checks app releases and shows the installed version. Use it when you want to see whether a newer APK exists.", "Consulta releases de la app y muestra la version instalada. Usalo cuando quieras verificar si hay un APK mas reciente."))
                        HelpLine(lt(language, "Privacidade", "Privacy", "Privacidad"), lt(language, "Explica permissoes, captura de tela, processamento local, armazenamento e direitos da Pokemon Company.", "Explains permissions, screen capture, local processing, storage, and Pokemon Company rights.", "Explica permisos, captura de pantalla, procesamiento local, almacenamiento y derechos de Pokemon Company."))
                    }
                }
            }
            item {
                Button(onClick = onGoToPrivacy, modifier = Modifier.fillMaxWidth()) {
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
            TopAppBar(
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(lt(language, "Politica de privacidade", "Privacy policy", "Politica de privacidad"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            lt(
                                language,
                                "O MewName processa imagens escolhidas por voce ou capturadas pela bolha para reconhecer dados visiveis na tela e gerar sugestoes. A analise acontece no aparelho.",
                                "MewName processes images you choose or capture with the bubble to recognize visible screen data and generate suggestions. Analysis happens on the device.",
                                "MewName procesa imagenes elegidas por ti o capturadas con la burbuja para reconocer datos visibles y generar sugerencias. El analisis ocurre en el dispositivo."
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            lt(
                                language,
                                "O app nao exige cadastro, nao coleta localizacao propria, nao vende dados pessoais e nao envia suas capturas para servidores do MewName.",
                                "The app does not require an account, does not collect its own location data, does not sell personal data, and does not send captures to MewName servers.",
                                "La app no requiere cuenta, no recopila ubicacion propia, no vende datos personales y no envia capturas a servidores de MewName."
                            ),
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(lt(language, "Permissoes", "Permissions", "Permisos"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            lt(
                                language,
                                "Sobreposicao: usada para mostrar a bolha sobre o jogo. Captura de tela: usada somente depois da autorizacao do Android.",
                                "Overlay: used to show the bubble over the game. Screen capture: used only after Android authorization.",
                                "Superposicion: usada para mostrar la burbuja sobre el juego. Captura de pantalla: usada solo despues de la autorizacion de Android."
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
                Card(modifier = Modifier.fillMaxWidth()) {
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
private fun AdventureEffectCard(entry: AdventureEffectCatalogEntry, language: AppLanguage) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(entry.displayPokemonPt, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("${lt(language, "Ataque", "Move", "Ataque")}: ${entry.movePt}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text("${lt(language, "Efeito", "Effect", "Efecto")}: ${entry.effectNamePt}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(entry.description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HomeActionSquare(
    title: String,
    @DrawableRes iconRes: Int?,
    assetIconPath: String? = null,
    customIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 12.dp),
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
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
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
                "↓",
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

private fun openExternalUrl(context: Context, url: String) {
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
    onRunExistingValidation: () -> Unit
) {
    val context = LocalContext.current
    val language = appLanguage()
    val results = uiState.debugIvValidationResults
    val comparableCount = results.count { it.comparable }
    val matchedCount = results.count { it.comparable && it.matched }

    Scaffold(
        topBar = {
            TopAppBar(
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(lt(language, "Enviar novos prints para analise", "Send new screenshots for analysis", "Enviar nuevas capturas para analisis"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Button(
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
                        Button(
                            onClick = onRunExistingValidation,
                            enabled = !uiState.debugIvValidationRunning,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (uiState.debugIvValidationRunning) lt(language, "Analisando...", "Analyzing...", "Analizando...") else lt(language, "Analisar imagens existentes", "Analyze existing images", "Analizar imagenes existentes"))
                        }
                    }
                }
            }
            if (results.isNotEmpty()) {
                item {
                    Button(
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
                    Card(
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
                                !result.comparable -> "Sem referência"
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
            TopAppBar(
                title = { Text(lt(language, "Formatos de Nome", "Name Presets", "Formatos de Nombre")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, lt(language, "Voltar", "Back", "Volver"))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, lt(language, "Novo", "New", "Nuevo"))
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            itemsIndexed(configs) { _, config ->
                val previewName = generator.generate(exampleData, config)
                ListItem(
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
                HorizontalDivider()
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
    var showPatternHelp by remember { mutableStateOf(false) }

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
            "Coleção" to listOf(
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
    val previewName = generator.generate(exampleData, draftConfig)
    val sectionTitleStyle = MaterialTheme.typography.titleSmall

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(lt(language, "Configurar Nome", "Edit Preset", "Configurar Nombre")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, lt(language, "Voltar", "Back", "Volver"))
                    }
                },
                actions = {
                    IconButton(onClick = { showPatternHelp = true }) {
                        UnownQuestionIcon(
                            modifier = Modifier.size(24.dp),
                            contentDescription = lt(language, "Ajuda", "Help", "Ayuda")
                        )
                    }
                }
            )
        },
        bottomBar = {
            Button(
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
            OutlinedTextField(
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
            Card(
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
                        "Coleção" -> lt(language, "Colecao", "Collection", "Coleccion")
                        else -> lt(language, "Especial", "Special", "Especial")
                    },
                    fields = groupFields.filter { it in availableVariableFields },
                    onFieldClick = { selectedField = it },
                    extraActionLabel = if (groupTitle == "Especial") lt(language, "Texto Livre", "Free Text", "Texto Libre") else null,
                    onExtraActionClick = if (groupTitle == "Especial") {
                        { showFixedTextDialog = true }
                    } else {
                        null
                    }
                )
            }

            Text("${lt(language, "Limite de Caracteres", "Character Limit", "Limite de Caracteres")}: ${draftConfig.maxLength}", style = sectionTitleStyle, fontWeight = FontWeight.Bold)
            Slider(
                value = draftConfig.maxLength.toFloat(),
                onValueChange = { draftConfig = draftConfig.copy(maxLength = it.toInt()) },
                valueRange = 6f..30f
            )
        }
    }

    if (showPatternHelp) {
        AlertDialog(
            onDismissRequest = { showPatternHelp = false },
            title = { Text(lt(language, "Como Funciona o Padrao do Nome", "How the Name Pattern Works", "Como Funciona el Patron del Nombre")) },
            text = {
                Text(
                    lt(language, "Toque em um campo para abrir o modal, configurar os simbolos e depois adicionar ao nome.", "Tap a field to open the dialog, configure symbols, and add it to the name.", "Toca un campo para abrir el dialogo, configurar simbolos y luego agregarlo al nombre.")
                )
            },
            confirmButton = {
                TextButton(onClick = { showPatternHelp = false }) {
                    Text(lt(language, "Fechar", "Close", "Cerrar"))
                }
            }
        )
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
private fun QuestionCircleIcon() {
    UnownQuestionIcon(modifier = Modifier.size(16.dp))
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
                                TextButton(onClick = { onPickSymbol(option) }) {
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
                Button(onClick = onAdd) {
                    Text(lt(language, "ADICIONAR", "ADD", "AGREGAR"))
                }
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(onClick = onDismiss) {
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
        ScrollableTabRow(selectedTabIndex = selectedTabIndex) {
            specs.forEachIndexed { index, spec ->
                val tabTitle = spec.pokemonNames.first().lowercase().replaceFirstChar { it.titlecase() }
                Tab(
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
                    TextButton(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariableFieldGroupCard(
    title: String,
    fields: List<NamingField>,
    onFieldClick: (NamingField) -> Unit,
    extraActionLabel: String? = null,
    onExtraActionClick: (() -> Unit)? = null
) {
    val language = appLanguage()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val rows = fields.chunked(3)
            rows.forEachIndexed { rowIndex, rowFields ->
                val shouldAppendExtraAction =
                    rowIndex == rows.lastIndex &&
                        rowFields.size < 3 &&
                        !extraActionLabel.isNullOrBlank() &&
                        onExtraActionClick != null
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowFields.forEach { field ->
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
                    if (shouldAppendExtraAction) {
                        VariableActionCard(
                            label = extraActionLabel!!,
                            modifier = Modifier.weight(1f),
                            onClick = onExtraActionClick!!
                        )
                    }
                    repeat(3 - rowFields.size - if (shouldAppendExtraAction) 1 else 0) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun VariableActionCard(
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
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
        NamingField.SPECIAL_BACKGROUND -> lt(language, "Exibir o simbolo se houver fundo especial.", "Show the symbol if there is a special background.", "Mostrar el simbolo si hay fondo especial.")
        NamingField.PVP_LEAGUE -> lt(language, "Mostrar a liga PvP estimada.", "Show the estimated PvP league.", "Mostrar la liga PvP estimada.")
        NamingField.PVP_RANK -> lt(language, "Mostrar o ranking PvP calculado.", "Show the calculated PvP rank.", "Mostrar el ranking PvP calculado.")
        NamingField.LEGACY_MOVE -> lt(language, "Exibir o simbolo se o Pokemon tiver movimento legado.", "Show a symbol if the Pokemon has a legacy move.", "Mostrar un simbolo si el Pokemon tiene movimiento legado.")
        NamingField.LEGACY_MOVE_NAME -> lt(language, "Exibir o nome do ataque legado detectado.", "Show the detected legacy move name.", "Mostrar el nombre del ataque legado detectado.")
        NamingField.TYPE -> lt(language, "Adicionar o tipo principal do Pokemon usando uma sigla configuravel.", "Add the main type using a configurable abbreviation.", "Agregar el tipo principal usando una abreviatura configurable.")
        NamingField.ADVENTURE_EFFECT -> lt(language, "Exibir o marcador de efeito aventura.", "Show the adventure effect marker.", "Mostrar el marcador de efecto aventura.")
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
    fun option(key: String, label: String) = FieldSymbolOption(key, label, config.symbols[key].orEmpty())

    return when (field) {
        NamingField.GENDER -> listOf(
            option("MALE", "Masculino"),
            option("FEMALE", "Feminino")
        )
        NamingField.FAVORITE -> listOf(option("FAVORITE", "Favorito"))
        NamingField.LUCKY -> listOf(option("LUCKY", "Sortudo"))
        NamingField.SHADOW -> listOf(option("SHADOW", "Sombrio"))
        NamingField.PURIFIED -> listOf(option("PURIFIED", "Purificado"))
        NamingField.SPECIAL_BACKGROUND -> listOf(option("SPECIAL_BACKGROUND", "Fundo especial"))
        NamingField.ADVENTURE_EFFECT -> listOf(option("ADVENTURE_EFFECT", "Efeito aventura"))
        NamingField.TYPE -> listOf(
            option("TYPE_NORMAL", "Normal"),
            option("TYPE_FIRE", "Fogo"),
            option("TYPE_WATER", "Água"),
            option("TYPE_GRASS", "Planta"),
            option("TYPE_ELECTRIC", "Elétrico"),
            option("TYPE_ICE", "Gelo"),
            option("TYPE_FIGHTING", "Lutador"),
            option("TYPE_POISON", "Venenoso"),
            option("TYPE_GROUND", "Terrestre"),
            option("TYPE_FLYING", "Voador"),
            option("TYPE_PSYCHIC", "Psíquico"),
            option("TYPE_BUG", "Inseto"),
            option("TYPE_ROCK", "Pedra"),
            option("TYPE_GHOST", "Fantasma"),
            option("TYPE_DRAGON", "Dragão"),
            option("TYPE_DARK", "Sombrio"),
            option("TYPE_STEEL", "Aço"),
            option("TYPE_FAIRY", "Fada")
        )
        NamingField.SIZE -> listOf(
            option("XXL", "XXL"),
            option("XL", "XL"),
            option("XS", "XS"),
            option("XXS", "XXS")
        )
        NamingField.MASTER_IV_BADGE -> listOf(
            option("MASTER_IV_MATCH", "Melhor combinação"),
            option("MASTER_IV_OTHER", "Outra combinação")
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
            option("STAGE1", "Estágio 1"),
            option("STAGE2", "Estágio 2"),
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
        "\u2642", "\u2640", "M", "F", "*", "+", "SH", "PU", "FE", "AV", "XXL", "XXS",
        "XL", "XS", "GL", "UL", "ML", "CP", "L", "G", "D", "#", "!", "1", "2", "3", "BY", "tm", "●"
    )
    var customText by remember(title, initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    label = { Text(lt(language, "Texto customizado", "Custom text", "Texto personalizado")) }
                )
                Button(
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
            TextButton(onClick = onDismiss) {
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
            OutlinedTextField(
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
                Button(
                    onClick = {
                        if (customText.text.isNotBlank()) {
                            onAdd(customText.text)
                        }
                    }
                ) {
                    Text(lt(language, "Adicionar", "Add", "Agregar"))
                }
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(onClick = onDismiss) {
                    Text(lt(language, "Cancelar", "Cancel", "Cancelar"))
                }
            }
        }
    )
}
