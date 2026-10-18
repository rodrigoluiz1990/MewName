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
        AlertDialog(onDismissRequest = { if (!busy) pending = null },
            title = { Text(tr("Restaurar backup?", "Restore backup?", "¿Restaurar copia?")) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val size = android.text.format.Formatter.formatFileSize(context, data.toByteArray(Charsets.UTF_8).size.toLong())
                Text(tr("Arquivo lido e validado: $size.", "File read and validated: $size.", "Archivo leído y validado: $size."))
                Text(tr("Os dados locais serão substituídos pelos do arquivo. A sobreposição será encerrada. Depois, reabra o app para aplicar todas as configurações.",
                "Local data will be replaced by the file. The overlay will stop. Reopen the app afterward to apply all settings.",
                "Los datos locales se reemplazarán por los del archivo. Se cerrará la superposición. Reabre la app para aplicar los ajustes.")) } },
            confirmButton = { TextButton(enabled = !busy, onClick = {
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
            }) { Text(tr("Restaurar", "Restore", "Restaurar")) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text(tr("Cancelar", "Cancel", "Cancelar")) } })
    }
    if (restored) AppActionButton(onClick = {
        context.startActivity(android.content.Intent(context, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }) { Text(tr("Reabrir aplicativo", "Reopen app", "Reabrir aplicación")) }
}
