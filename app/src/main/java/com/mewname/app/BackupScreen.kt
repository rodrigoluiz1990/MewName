package com.mewname.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable
internal fun BackupControls() {
    val context = LocalContext.current
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf(false) }
    var automatic by remember { mutableStateOf(AutomaticBackup.enabled(context)) }
    var backupFolder by remember { mutableStateOf(AutomaticBackup.folder(context)) }
    var lastBackup by remember { mutableLongStateOf(AutomaticBackup.lastSuccess(context)) }
    DisposableEffect(context) {
        val preferences = context.getSharedPreferences("automatic_backup", 0)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            backupFolder = AutomaticBackup.folder(context)
            automatic = AutomaticBackup.enabled(context)
            lastBackup = AutomaticBackup.lastSuccess(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val found = withContext(NonCancellable + Dispatchers.IO) {
                    context.contentResolver.takePersistableUriPermission(uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    AutomaticBackup.selectFolder(context, uri)
                }
                message = if (found) tr("Uma cópia anterior foi encontrada. Restaure-a antes de ativar o backup diário, para não substituí-la pelos dados atuais.",
                    "An earlier copy was found. Restore it before enabling daily backup to avoid replacing it with current data.",
                    "Se encontró una copia anterior. Restáurala antes de activar la copia diaria para no reemplazarla con los datos actuales.")
                else tr("Pasta configurada. Backup salvo e rotina diária ativada.", "Folder configured. Backup saved and daily schedule enabled.", "Carpeta configurada. Copia guardada y programación diaria activada.")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = tr("Não foi possível acessar a pasta ou salvar a cópia. Escolha novamente uma pasta com permissão de leitura e gravação.",
                "Could not access the folder or save the copy. Choose a folder with read and write access.",
                "No se pudo acceder a la carpeta o guardar la copia. Elige una carpeta con acceso de lectura y escritura.") }
            finally { busy = false }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val bytes = withContext(NonCancellable + Dispatchers.IO) { UserBackup.writeDocument(context, uri) }
                val size = android.text.format.Formatter.formatFileSize(context, bytes.toLong())
                message = tr("Backup salvo e verificado: $size.", "Backup saved and verified: $size.", "Copia guardada y verificada: $size.")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = tr("Não foi possível salvar o backup.", "Could not save backup.", "No se pudo guardar la copia.") }
            finally { busy = false }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                pending = withContext(Dispatchers.IO) {
                    UserBackup.readDocument(context, uri)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = tr("Backup inválido ou incompatível. Nenhum dado foi alterado.",
                "Invalid or incompatible backup. No data changed.", "Copia inválida o incompatible. No se modificaron datos.") }
            finally { busy = false }
        }
    }
    Text(tr("Salva configurações, nomes, filtros, coleções, missões, códigos resgatados e perfil com foto e QR. Catálogos baixados e logs não são incluídos.",
        "Saves settings, names, filters, collections, missions, redeemed codes and profile with photo and QR. Downloaded catalogs and logs are excluded.",
        "Guarda ajustes, nombres, filtros, colecciones, misiones, códigos canjeados y perfil con foto y QR. No incluye catálogos descargados ni registros."))
    AppSectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(tr("Backup automático diário", "Daily automatic backup", "Copia automática diaria"),
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                AppToggle(automatic, { value ->
                    if (!busy && !restored && value && backupFolder == null) chooseFolder.launch(null)
                    else if (!busy && !restored) scope.launch {
                        busy = true
                        try {
                            withContext(NonCancellable + Dispatchers.IO) {
                                AutomaticBackup.setEnabled(context, value)
                                if (value) AutomaticBackup.writeIfDue(context)
                            }
                        } catch (e: CancellationException) { throw e } catch (_: Exception) {
                            message = tr("Não foi possível configurar o backup automático.", "Could not configure automatic backup.", "No se pudo configurar la copia automática.")
                        } finally { busy = false }
                    }
                })
            }
            Text(tr("Mantém uma cópia na pasta escolhida, mesmo após desinstalar o app. Ao reinstalar, selecione a mesma pasta para localizar e restaurar o backup. O Android pode adiar a execução diária para economizar bateria.",
                "Keeps one copy in the selected folder, even after uninstalling. After reinstalling, select the same folder to find and restore it. Android may delay daily runs to save battery.",
                "Conserva una copia en la carpeta elegida, incluso al desinstalar. Tras reinstalar, elige la misma carpeta para encontrarla y restaurarla. Android puede retrasar la ejecución diaria para ahorrar batería."), style = MaterialTheme.typography.bodySmall)
            backupFolder?.let { uri -> Text(android.net.Uri.decode(uri.lastPathSegment.orEmpty()), style = MaterialTheme.typography.bodySmall) }
            AppActionButton(onClick = { chooseFolder.launch(backupFolder) }, enabled = !busy && !restored,
                secondary = true, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Escolher pasta do backup", "Choose backup folder", "Elegir carpeta de copia"))
            }
            val date = if (lastBackup > 0) java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastBackup)) else null
            Text(if (date == null) tr("Nenhum backup automático concluído.", "No automatic backup completed.", "Ninguna copia automática completada.")
                else tr("Último backup: $date", "Last backup: $date", "Última copia: $date"), style = MaterialTheme.typography.bodySmall)
            AppActionButton(onClick = {
                scope.launch {
                    busy = true
                    try { pending = withContext(Dispatchers.IO) { AutomaticBackup.read(context) } }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { message = tr("Backup automático indisponível ou inválido.", "Automatic backup unavailable or invalid.", "Copia automática no disponible o inválida.") }
                    finally { busy = false }
                }
            }, enabled = !busy && !restored && lastBackup > 0, secondary = true, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Restaurar backup automático", "Restore automatic backup", "Restaurar copia automática"))
            }
        }
    }
    AppActionButton(onClick = { export.launch("MewName-backup-" + java.time.LocalDate.now() + ".json") },
        enabled = !busy && !restored, modifier = Modifier.fillMaxWidth()) {
        Text(tr("Salvar backup", "Save backup", "Guardar copia"))
    }
    AppActionButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        enabled = !busy && !restored, modifier = Modifier.fillMaxWidth(), secondary = true) {
        Text(tr("Restaurar backup", "Restore backup", "Restaurar copia"))
    }
    if (busy) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { AppLoadingIndicator() }
    if (message.isNotBlank()) Text(message)
    pending?.let { data ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { if (!busy) pending = null }) {
        AppModalPanel(title = tr("Restaurar backup?", "Restore backup?", "¿Restaurar copia?"),
            onClose = { pending = null }, closeEnabled = !busy,
            content = {
                val size = android.text.format.Formatter.formatFileSize(context, data.toByteArray(Charsets.UTF_8).size.toLong())
                Text(tr("Arquivo lido e validado: $size.", "File read and validated: $size.", "Archivo leído y validado: $size."))
                Text(tr("Os dados locais serão substituídos pelos do arquivo. A sobreposição será encerrada. Depois, reabra o app para aplicar todas as configurações.",
                "Local data will be replaced by the file. The overlay will stop. Reopen the app afterward to apply all settings.",
                "Los datos locales se reemplazarán por los del archivo. Se cerrará la superposición. Reabre la app para aplicar los ajustes.")) },
            actions = { AppActionButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    try {
                        context.stopService(android.content.Intent(context, OverlayService::class.java))
                        withContext(NonCancellable + Dispatchers.IO) { UserBackup.restore(context, data) }
                        pending = null; restored = true
                        message = tr("Backup restaurado. Toque em Reabrir aplicativo.", "Backup restored. Tap Reopen app.", "Copia restaurada. Toca Reabrir aplicación.")
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { message = tr("Não foi possível restaurar o backup.", "Could not restore backup.", "No se pudo restaurar la copia."); pending = null }
                    finally { busy = false }
                }
            }) { Text(tr("Restaurar", "Restore", "Restaurar")) } })
        }
    }
    if (restored) AppActionButton(onClick = {
        context.startActivity(android.content.Intent(context, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }) { Text(tr("Reabrir aplicativo", "Reopen app", "Reabrir aplicación")) }
}
