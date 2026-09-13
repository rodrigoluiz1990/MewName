package com.mewname.app
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mewname.app.domain.TrainerProfileParser
import com.mewname.app.ocr.OcrEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrainerProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val language = appLanguage()
    val prefs = remember { context.getSharedPreferences("trainer_profile", 0) }
    var name by rememberSaveable { mutableStateOf(prefs.getString("name", "").orEmpty()) }
    var level by rememberSaveable { mutableStateOf(prefs.getString("level", "").orEmpty()) }
    var team by rememberSaveable { mutableStateOf(prefs.getString("team", "").orEmpty()) }
    var code by rememberSaveable { mutableStateOf(prefs.getString("code", "").orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    var lastReadLog by remember { mutableStateOf(prefs.getString("last_read_log", "").orEmpty()) }
    var pendingRead by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun saveReadLog(value: String) {
        lastReadLog = value
        prefs.edit().putString("last_read_log", value).apply()
    }
    fun shareReadLog() {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "MewName — Log do perfil")
            putExtra(android.content.Intent.EXTRA_TEXT, lastReadLog)
        }
        context.startActivity(android.content.Intent.createChooser(intent,
            lt(language, "Compartilhar log", "Share log", "Compartir log")))
    }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "name" -> name = prefs.getString("name", "").orEmpty()
                "level" -> level = prefs.getString("level", "").orEmpty()
                "team" -> team = prefs.getString("team", "").orEmpty()
                "code" -> code = prefs.getString("code", "").orEmpty()
                "last_read_log" -> lastReadLog = prefs.getString("last_read_log", "").orEmpty()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val appearance = LocalAppAppearance.current
    var editing by rememberSaveable { mutableStateOf(false) }
    var showQr by rememberSaveable { mutableStateOf(false) }
    var showPvp by rememberSaveable { mutableStateOf(false) }
    var showLayout by rememberSaveable { mutableStateOf(false) }
    var showBubbleOptions by rememberSaveable { mutableStateOf(false) }
    var pictureRevision by remember { mutableStateOf(0) }
    var importingQr by rememberSaveable { mutableStateOf(false) }
    val picturePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val qr = importingQr
            scope.launch {
                try {
                    storeProfilePicture(context, uri, qr)
                    pictureRevision++
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    message = lt(language, "Não foi possível abrir a imagem.", "Could not open the image.", "No se pudo abrir la imagen.")
                }
            }
        }
    }
    fun closePage() { showPvp = false; showQr = false; showLayout = false; showBubbleOptions = false; editing = false }
    val page = when {
        showPvp -> "pvp"
        showLayout -> "layout"
        showBubbleOptions -> "bubble"
        editing -> "edit"
        else -> "profile"
    }
    BackHandler { if (page != "profile") closePage() else onBack() }
    if (showQr) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showQr = false }) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
                ProfilePicture(true, pictureRevision, Modifier.fillMaxWidth().aspectRatio(1f).padding(16.dp),
                    "QR Code", lt(language, "QR Code indisponível", "QR code unavailable", "QR no disponible"))
            }
        }
    }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val direction = if (targetState == "profile") -1 else 1
            slideInHorizontally { it * direction } togetherWith slideOutHorizontally { -it * direction }
        },
        label = "profileNavigation"
    ) { destination ->
    if (destination == "profile") {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(100.dp)
                        .border(2.dp, Brush.sweepGradient(listOf(Color(0xFF22C6DF), Color(0xFFB05EEA),
                            Color(0xFFFF5379), Color(0xFF22C6DF))), CircleShape)
                        .padding(5.dp).clip(CircleShape)
                        .clickable { importingQr = false; picturePicker.launch("image/*") }) {
                        ProfilePicture(false, pictureRevision, Modifier.fillMaxSize(),
                            lt(language, "Alterar foto", "Change photo", "Cambiar foto"),
                            lt(language, "+ Foto", "+ Photo", "+ Foto"))
                    }
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(lt(language, "Equipe", "Team", "Equipo"), style = MaterialTheme.typography.labelMedium)
                        Text(team.ifBlank { "—" }, fontWeight = FontWeight.Bold)
                        Text(lt(language, "Nível do treinador", "Trainer level", "Nivel del entrenador"),
                            style = MaterialTheme.typography.labelMedium)
                        Text(level.ifBlank { "—" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(name.ifBlank { lt(language, "Seu nome de treinador", "Your trainer name", "Tu nombre de entrenador") },
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(code.chunked(4).joinToString(" ").ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                    ProfileHeaderAction("qr", "QR Code", onClick = { showQr = true })
                    ProfileHeaderAction("edit", lt(language, "Editar dados", "Edit details", "Editar datos"),
                        onClick = { editing = !editing })
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall)
                if (logOptionsEnabled() && lastReadLog.isNotBlank()) {
                    ProfileMenuRow(lt(language, "Exportar log da leitura", "Export reading log", "Exportar log de lectura"),
                        enabled = !busy, onClick = { shareReadLog() })
                }
                if (pendingRead && !editing) {
                    AppActionButton(onClick = {
                        prefs.edit().putString("name", name.trim()).putString("level", level)
                            .putString("team", team.trim()).putString("code", code).apply()
                        pendingRead = false
                        message = lt(language, "Perfil salvo.", "Profile saved.", "Perfil guardado.")
                    }, enabled = !busy) {
                        Text(lt(language, "Salvar dados lidos", "Save read data", "Guardar datos leídos"))
                    }
                }
                HorizontalDivider()
                ProfileMenuRow(lt(language, "Cálculo PvP", "PvP calculation", "Cálculo PvP"), onClick = { showPvp = true })
                ProfileMenuRow("Layout", onClick = { showLayout = true })
                ProfileMenuRow(lt(language, "Atalhos da bolha", "Bubble shortcuts", "Accesos de burbuja"),
                    onClick = { showBubbleOptions = true })
                Text(
                    if (BuildConfig.RELEASE_TAG.isBlank() || BuildConfig.RELEASE_TAG == "dev") "local"
                    else BuildConfig.VERSION_NAME,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        val title = when (destination) {
            "pvp" -> lt(language, "Cálculo PvP", "PvP calculation", "Cálculo PvP")
            "layout" -> "Layout"
            "bubble" -> lt(language, "Atalhos da bolha", "Bubble shortcuts", "Accesos de burbuja")
            else -> lt(language, "Editar dados", "Edit details", "Editar datos")
        }
        Scaffold(containerColor = appearance.background.first(), topBar = {
            AppTopBar(title = { Text(title) }, navigationIcon = {
                IconButton(onClick = { closePage() }) { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, lt(language, "Voltar", "Back", "Volver")) }
            })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (destination) {
                    "pvp" -> PvpCalculationPreferences()
                    "layout" -> AppearancePreferences()
                    "bubble" -> BubbleActionPreferences()
                    "edit" -> {
            AppGlassTextField(name, { name = it }, label = { Text(lt(language, "Nome do treinador", "Trainer name", "Nombre del entrenador")) },
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            AppGlassTextField(level, { level = it.filter(Char::isDigit).take(3) },
                label = { Text(lt(language, "Nível", "Level", "Nivel")) },
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            AppGlassTextField(team, { team = it }, label = { Text(lt(language, "Equipe", "Team", "Equipo")) },
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            AppGlassTextField(code, { code = it.filter(Char::isDigit).take(12) },
                label = { Text(lt(language, "Código de amizade", "Friend code", "Código de amistad")) },
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(lt(language, "Opcional · 12 dígitos", "Optional · 12 digits", "Opcional · 12 dígitos")) })
            AppActionButton(onClick = {
                prefs.edit().putString("name", name.trim()).putString("level", level)
                    .putString("team", team.trim()).putString("code", code).apply()
                editing = false
                pendingRead = false
                message = lt(language, "Perfil salvo.", "Profile saved.", "Perfil guardado.")
            }, enabled = !busy && name.isNotBlank() &&
                (level.isEmpty() || level.toIntOrNull()?.let { it in 1..100 } == true) &&
                (code.isEmpty() || code.length == 12), modifier = Modifier.fillMaxWidth()) {
                Text(lt(language, "Salvar perfil", "Save profile", "Guardar perfil"))
            }
                    }
                }
            }
        }
    }
    }
}