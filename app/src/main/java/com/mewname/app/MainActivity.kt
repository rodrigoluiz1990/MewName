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
import com.mewname.app.domain.GameCatalogRepository
import com.mewname.app.domain.LegacyMoveCatalogEntry
import com.mewname.app.domain.NameGenerator
import com.mewname.app.domain.UniquePokemonCatalog
import com.mewname.app.model.*
import java.util.Collections

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
                        AppScreen.DONATION,
                        AppScreen.APP_UPDATE,
                        AppScreen.IV_VALIDATION -> viewModel.navigateTo(AppScreen.HOME)
                        else -> Unit
                    }
                }

                when (uiState.currentScreen) {
                    AppScreen.HOME -> HomeScreen(
                        uiState = uiState,
                        onPickImage = { pickImage.launch("image/*") },
                        onClear = viewModel::clearResults,
                        onGoToPresets = { viewModel.navigateTo(AppScreen.PRESET_LIST) },
                        onGoToLegacyMoves = { viewModel.navigateTo(AppScreen.LEGACY_MOVES) },
                        onGoToAdventureEffects = { viewModel.navigateTo(AppScreen.ADVENTURE_EFFECTS) },
                        onGoToDonation = { viewModel.navigateTo(AppScreen.DONATION) },
                        onGoToAppUpdate = { viewModel.navigateTo(AppScreen.APP_UPDATE) },
                        onGoToIvValidation = { viewModel.navigateTo(AppScreen.IV_VALIDATION) },
                        onDismissReview = viewModel::dismissReview,
                        onApplyReview = viewModel::applyReview,
                        onBubbleOptionVisibleChange = viewModel::setBubbleOptionVisible
                    )

                    AppScreen.PRESET_LIST -> {
                        val context = LocalContext.current
                        PresetListScreen(
                            configs = uiState.configs,
                            onBack = { viewModel.navigateTo(AppScreen.HOME) },
                            onAdd = { viewModel.addConfig(context, "Novo Formato") },
                            onEdit = { viewModel.navigateTo(AppScreen.PRESET_EDIT, it) },
                            onDelete = { viewModel.removeConfig(context, it) }
                        )
                    }

                    AppScreen.PRESET_EDIT -> {
                        val context = LocalContext.current
                        val config = uiState.configs.find { it.id == uiState.editingConfigId }
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

                    AppScreen.DONATION -> {
                        DonationScreen(onBack = { viewModel.navigateTo(AppScreen.HOME) })
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

    override fun onDestroy() {
        runCatching { unregisterReceiver(capturePermissionInvalidReceiver) }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    uiState: UiState,
    onPickImage: () -> Unit,
    onClear: () -> Unit,
    onGoToPresets: () -> Unit,
    onGoToLegacyMoves: () -> Unit,
    onGoToAdventureEffects: () -> Unit,
    onGoToDonation: () -> Unit,
    onGoToAppUpdate: () -> Unit,
    onGoToIvValidation: () -> Unit,
    onDismissReview: () -> Unit,
    onApplyReview: (PokemonScreenData) -> Unit,
    onBubbleOptionVisibleChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
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
                            Text(
                                text = releaseLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                            )
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
                Text(if (uiState.showBubbleOption) "Iniciar sobreposição" else "Solicitar Permissão de Captura")
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
                    title = "Gerar nome por imagem",
                    iconRes = null,
                    assetIconPath = "icon-gerar-nome-imagem.png",
                    onClick = onPickImage,
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
                HomeActionSquare(
                    title = "Definir Nomes",
                    iconRes = null,
                    assetIconPath = "icon-definir-nome.png",
                    onClick = onGoToPresets,
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
                HomeActionSquare(
                    title = "Ataques Legados",
                    iconRes = null,
                    assetIconPath = "icon-legacy.png",
                    onClick = onGoToLegacyMoves,
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
                HomeActionSquare(
                    title = "Efeitos de Aventura",
                    iconRes = null,
                    assetIconPath = "icon-fieldmove.png",
                    onClick = onGoToAdventureEffects,
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
                HomeActionSquare(
                    title = if (uiState.isCheckingForUpdate) "Verificando..." else "Atualizar app",
                    iconRes = null,
                    assetIconPath = "icon-update.png",
                    onClick = onGoToAppUpdate,
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
                HomeActionSquare(
                    title = "Validar amostras",
                    iconRes = null,
                    assetIconPath = "icon-validar-amostras.png",
                    onClick = onGoToIvValidation,
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
                HomeActionSquare(
                    title = "Calendário",
                    iconRes = null,
                    customIcon = { HomeBrowserGlyph() },
                    onClick = { openExternalUrl(context, "https://rodrigoluiz1990.github.io/laboratorio-do-sam/Calendario/calendario.html") },
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
                HomeActionSquare(
                    title = "Doação",
                    iconRes = null,
                    assetIconPath = "icon-doacao.png",
                    onClick = onGoToDonation,
                    modifier = Modifier.fillMaxWidth(0.31f)
                )
            }

            if (uiState.generatedResults.isNotEmpty()) {
                Text("Nome sugerido", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                uiState.generatedResults.forEach { result ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(result.configName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(result.generatedName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Button(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                    Text("Limpar Resultados")
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
                            "Analisando imagem",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            uiState.processingStatusMessage ?: "Lendo os dados detectados para gerar o nome sugerido.",
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

    LaunchedEffect(Unit) {
        if (!uiState.isCheckingForUpdate && uiState.latestAppUpdate == null && uiState.appUpdateError == null) {
            onRefresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Atualização do App") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Versão instalada", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Nome: v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Release: ${BuildConfig.RELEASE_TAG.takeUnless { it.isBlank() || it == "dev" } ?: "build local"}",
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
                            Text("Verificando nova release...", style = MaterialTheme.typography.bodyMedium)
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
                            Text("Nova release disponível", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Versão: ${update.tagName}", style = MaterialTheme.typography.bodyMedium)
                            if (update.releaseName.isNotBlank()) {
                                Text("Título: ${update.releaseName}", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (update.publishedAt.isNotBlank()) {
                                Text("Publicada em: ${update.publishedAt}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("O que mudou", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                update.releaseNotes.ifBlank { "Sem descrição cadastrada para esta release." },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    TextButton(
                        onClick = { openExternalUrl(context, update.releasePageUrl) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ver página da release")
                    }

                    Button(
                        onClick = {
                            openExternalUrl(context, update.apkDownloadUrl ?: update.releasePageUrl)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (update.apkDownloadUrl != null) "Baixar atualização" else "Abrir release")
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
                            Text("Falha ao verificar atualização", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                uiState.appUpdateError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text("Tentar novamente")
                    }
                }

                else -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Seu app já está atualizado", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                uiState.appUpdateStatusMessage ?: "Nenhuma atualização mais nova foi encontrada no GitHub.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text("Verificar novamente")
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
                title = { Text("Ataques Legados") },
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar Pokémon ou ataque") },
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
                            entry.moves.joinToString(" • ").ifBlank { "Sem golpes cadastrados" },
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
    val entries = remember { GameCatalogRepository.loadAdventureEffectCatalog(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Efeitos de Aventura") },
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries) { entry ->
                AdventureEffectCard(entry = entry)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DonationScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Doação") },
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
                    Text("Apoie o MewName", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Esta tela já está pronta para receber sua chave Pix, link de doação ou outra forma de apoio em uma próxima atualização.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun AdventureEffectCard(entry: AdventureEffectCatalogEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(entry.displayPokemonPt, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Ataque: ${entry.movePt}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text("Efeito: ${entry.effectNamePt}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
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
    val results = uiState.debugIvValidationResults
    val comparableCount = results.count { it.comparable }
    val matchedCount = results.count { it.comparable && it.matched }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Validador IV") },
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Enviar novos prints para análise", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = onPickImages,
                            enabled = !uiState.debugIvValidationRunning,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Selecionar imagens")
                        }
                        Text(
                            "Se o nome do arquivo tiver o formato 15-14-13, o app também compara o IV esperado com o detectado.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        HorizontalDivider()
                        Text("Imagens existentes no projeto", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = onRunExistingValidation,
                            enabled = !uiState.debugIvValidationRunning,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (uiState.debugIvValidationRunning) "Analisando..." else "Analisar imagens existentes")
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
                                putExtra(Intent.EXTRA_SUBJECT, "MewName - Resultado do Validador IV")
                                putExtra(Intent.EXTRA_TEXT, exportText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Exportar resultado"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Exportar Resultado")
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
                        if (comparableCount > 0) "Acertos: $matchedCount/$comparableCount" else "Resultados analisados: ${results.size}",
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
                                "Esperado: ${result.expectedAttack ?: "-"}/${result.expectedDefense ?: "-"}/${result.expectedStamina ?: "-"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Detectado: ${result.detectedAttack ?: "-"}/${result.detectedDefense ?: "-"}/${result.detectedStamina ?: "-"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "IV %: ${result.detectedPercent ?: "-"} | ${when {
                                    !result.comparable -> "Sem referência"
                                    result.matched -> "OK"
                                    else -> "Erro"
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
            hasLegacyMove = true,
            evolutionFlags = setOf(EvolutionFlag.MEGA)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Formatos de Nome") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, "Novo")
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
                            text = previewName.ifBlank { "Preview indisponivel" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.clickable { onEdit(config.id) },
                    trailingContent = {
                        IconButton(onClick = { if (configs.size > 1) onDelete(config.id) }) {
                            Icon(Icons.Default.Delete, "Excluir", tint = MaterialTheme.colorScheme.error)
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
            hasLegacyMove = true,
            evolutionFlags = setOf(EvolutionFlag.MEGA)
        )
    }
    val previewName = generator.generate(exampleData, draftConfig)
    val sectionTitleStyle = MaterialTheme.typography.titleSmall

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurar Nome") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
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
                Text("Salvar")
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
                label = { Text("Nome do formato") },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "Preview do Apelido",
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Padrão do Nome", style = sectionTitleStyle, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = { showPatternHelp = true },
                    modifier = Modifier.size(22.dp)
                ) {
                    QuestionCircleIcon()
                }
            }

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
                        "Nenhum campo adicionado ainda",
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
                                    block.label(),
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

            Text("Campos variáveis", style = sectionTitleStyle, fontWeight = FontWeight.Bold)
            variableFieldGroups.forEach { (groupTitle, groupFields) ->
                VariableFieldGroupCard(
                    title = groupTitle,
                    fields = groupFields.filter { it in availableVariableFields },
                    onFieldClick = { selectedField = it },
                    extraActionLabel = if (groupTitle == "Especial") "Texto Livre" else null,
                    onExtraActionClick = if (groupTitle == "Especial") {
                        { showFixedTextDialog = true }
                    } else {
                        null
                    }
                )
            }

            Text("Limite de Caracteres: ${draftConfig.maxLength}", style = sectionTitleStyle, fontWeight = FontWeight.Bold)
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
            title = { Text("Como Funciona o Padrão do Nome") },
            text = {
                Text(
                    "Toque em um campo para abrir o modal, configurar os símbolos e depois adicionar ao nome."
                )
            },
            confirmButton = {
                TextButton(onClick = { showPatternHelp = false }) {
                    Text("Fechar")
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(field.label, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(fieldDescription(field), color = Color(0xFF555555))
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
                                    Text("EDITAR")
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
                    Text("ADICIONAR")
                }
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
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
                    assetFolder = "unown",
                    pokemonNames = setOf("UNOWN"),
                    options = buildList {
                        ('A'..'Z').forEach { letter ->
                            add(com.mewname.app.domain.UniquePokemonFormOption(letter.toString(), letter.toString()))
                        }
                        add(com.mewname.app.domain.UniquePokemonFormOption("!", "!"))
                        add(com.mewname.app.domain.UniquePokemonFormOption("?", "?"))
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
                } else {
                    UniquePokemonCatalog.symbolKeyForAsset(currentSpec.assetFolder, option.code)
                }
                val pokemonLabel = currentSpec.pokemonNames.first().lowercase().replaceFirstChar { it.titlecase() }
                val displayLabel = if (currentSpec.assetFolder.equals("spinda", ignoreCase = true)) {
                    "Spinda #${index + 1}: ${currentSymbols[symbolKey] ?: option.code}"
                } else if (currentSpec.assetFolder.equals("unown", ignoreCase = true)) {
                    "Unown ${option.label}: ${currentSymbols[symbolKey] ?: option.code}"
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
                            label = field.label,
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
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
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

private fun fieldDescription(field: NamingField): String {
    return when (field) {
        NamingField.IV_PERCENT -> "Exibir o IV médio do Pokémon no apelido."
        NamingField.POKEMON_NAME -> "Adicionar o nome do Pokémon reconhecido pelo app."
        NamingField.UNOWN_LETTER -> "Campo legado do Unown. A tela de definição usa agora Forma única para variantes especiais."
        NamingField.UNIQUE_FORM -> "Mostrar o código curto da forma detectada para Pokémon com variantes especiais, como Furfrou, Genesect, Rotom, Spinda e Unown."
        NamingField.VIVILLON_PATTERN -> "Campo legado do Vivillon. Essa opção não aparece mais na tela de definição."
        NamingField.LEVEL -> "Mostrar o nível atual do Pokémon."
        NamingField.CP -> "Mostrar os pontos de combate atuais do Pokémon."
        NamingField.GENDER -> "Exibir um símbolo configurável para macho ou fêmea conforme o gênero detectado."
        NamingField.SIZE -> "Mostrar XXS, XS, XL ou XXL usando um símbolo para cada tamanho."
        NamingField.SPECIAL_BACKGROUND -> "Exibir o símbolo se houver fundo especial."
        NamingField.PVP_LEAGUE -> "Mostrar Copinha, Great League, Ultra League ou Master League conforme a liga estimada."
        NamingField.PVP_RANK -> "Mostrar o ranking PvP calculado."
        NamingField.LEGACY_MOVE -> "Exibir o símbolo se o Pokémon tiver movimento legado."
        NamingField.TYPE -> "Adicionar o tipo principal do Pokémon usando uma sigla configurável para cada tipo."
        NamingField.ADVENTURE_EFFECT -> "Exibir o marcador de efeito aventura."
        NamingField.EVOLUTION_TYPE -> "Mostrar Baby, Estágio 1, Estágio 2, Mega, Dynamax e Gigantamax, cada um com seu símbolo."
        NamingField.SHADOW -> "Exibir o símbolo se o Pokémon for sombrio."
        NamingField.PURIFIED -> "Exibir o símbolo se o Pokémon for purificado."
        NamingField.FAVORITE -> "Exibir o símbolo de favorito."
        NamingField.LUCKY -> "Exibir o símbolo de sortudo."
        NamingField.POKEDEX_NUMBER -> "Mostrar o número da Pokédex."
        NamingField.IV_COMBINATION -> "Mostrar os IVs em formato Ataque/Defesa/Stamina."
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
        NamingField.PVP_LEAGUE -> listOf(
            option("LITTLE_LEAGUE", "Copinha"),
            option("GREAT_LEAGUE", "Great League"),
            option("ULTRA_LEAGUE", "Ultra League"),
            option("MASTER_LEAGUE", "Master League")
        )
        NamingField.LEGACY_MOVE -> listOf(option("LEGACY", "Ataque legado"))
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
    val commonSymbols = listOf(
        "\u2642", "\u2640", "M", "F", "*", "+", "SH", "PU", "FE", "AV", "XXL", "XXS",
        "XL", "XS", "GL", "UL", "ML", "CP", "L", "G", "D", "#", "!", "1", "2", "3", "BY"
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
                    label = { Text("Texto customizado") }
                )
                Button(
                    onClick = { onSymbolSelected(customText) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Aplicar")
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
                Text("Fechar")
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
    var customText by remember { mutableStateOf(TextFieldValue("")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar texto fixo") },
        text = {
            OutlinedTextField(
                value = customText,
                onValueChange = { customText = it },
                label = { Text("Ex: XXL, FE, espaco") },
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
                    Text("Adicionar")
                }
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        }
    )
}
