package com.mewname.app

import android.Manifest
import android.app.*
import android.app.job.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mewname.app.domain.*
import kotlinx.coroutines.*
import java.util.Locale

internal fun promoCodeKey(code: String) = code.trim().uppercase(Locale.ROOT)
internal fun newPromoCodes(entries: List<LeekEntry>, known: Set<String>, redeemed: Set<String>, now: Long) =
    entries.filter { it.code.isNotBlank() && !it.expired && (it.expires == null || it.expires > now) &&
        promoCodeKey(it.code) !in known && promoCodeKey(it.code) !in redeemed }.distinctBy { promoCodeKey(it.code) }

/** Only successful live snapshots advance the baseline; cache reads and network errors never do. */
internal class PromoCodeNotifications(private val context: Context) {
    val preferences = context.getSharedPreferences("promo_code_notifications", Context.MODE_PRIVATE)
    fun enabled() = preferences.getBoolean("enabled", false)
    fun allowed(): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,
            lt(savedAppLanguage(context), "Novos códigos promocionais", "New promo codes", "Nuevos códigos promocionales"),
            NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun setEnabled(value: Boolean) {
        synchronized(lock) {
            val edit = preferences.edit().putBoolean("enabled", value)
            if (value && !enabled()) edit.remove("initialized").remove("known")
            check(edit.commit())
            schedule()
            if (!value) context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }
    }
    fun schedule() {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (!enabled()) { scheduler.cancel(JOB_ID); return }
        createChannel()
        if (scheduler.getPendingJob(JOB_ID)?.intervalMillis != CHECK_INTERVAL) {
            check(scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, PromoCodeNotificationService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(CHECK_INTERVAL)
                .setPersisted(true).build()) == JobScheduler.RESULT_SUCCESS)
        }
    }
    fun accept(catalog: LeekCatalog, now: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            if (!enabled() || Thread.currentThread().isInterrupted) return
            val known = preferences.getStringSet("known", emptySet()).orEmpty().toSet()
            val initialized = preferences.getBoolean("initialized", false)
            val fresh = newPromoCodes(catalog.entries, known, PromoCodeRedemptions(context).redeemedCodes(), now)
            if (initialized && fresh.isNotEmpty()) {
                if (!allowed()) return // Retry while still available when permissions return.
                createChannel()
                val language = savedAppLanguage(context)
                val title = lt(language, "Novos códigos promocionais", "New promo codes", "Nuevos códigos promocionales")
                val text = fresh.joinToString(", ") { it.code }
                val open = Intent(context, MainActivity::class.java).setAction("com.mewname.app.OPEN_PROMO_CODES")
                    .putExtra("BUBBLE_DESTINATION", "PROMO_CODES")
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val tap = PendingIntent.getActivity(context, NOTIFICATION_ID, open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_promo_notification)
                    .setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
                    .setContentIntent(tap).setAutoCancel(true).setCategory(Notification.CATEGORY_RECOMMENDATION).build()
                try { context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification) }
                catch (_: SecurityException) { return }
            }
            check(preferences.edit().putBoolean("initialized", true)
                .putStringSet("known", known + catalog.entries.map { promoCodeKey(it.code) }.filter { it.isNotBlank() }).commit())
        }
    }
    companion object {
        private val lock = Any()
        const val CHECK_INTERVAL = 24 * 60 * 60 * 1000L
        const val JOB_ID = 7312
        const val NOTIFICATION_ID = 7312
        const val CHANNEL = "promo_codes"
    }
}

class PromoCodeNotificationService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var task: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        val store = PromoCodeNotifications(applicationContext)
        if (!store.enabled() || !store.allowed()) return false
        task = scope.launch {
            var retry = false
            try {
                withContext(Dispatchers.IO) {
                    val catalog = LeekDuckRepository(applicationContext).refresh(LeekSection.CODES)
                    ensureActive()
                    store.accept(catalog)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                android.util.Log.w("PromoCodeNotifications", "Could not check promo codes", e)
                retry = true
            }
            jobFinished(params, retry)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { task?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

/** Permission request also works when the codes screen is hosted by the overlay service. */
class PromoCodePermissionActivity : ComponentActivity() {
    private val request = registerForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = PromoCodeNotifications(this)
        store.createChannel()
        if (store.allowed()) { finish(); return }
        if (savedInstanceState != null) return
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            request.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName).putExtra(Settings.EXTRA_CHANNEL_ID, PromoCodeNotifications.CHANNEL))
            finish()
        }
    }
}

@Composable internal fun PromoCodeNotificationOption(onEnabled: () -> Unit) {
    val context = LocalContext.current
    val language = appLanguage()
    val store = remember(context) { PromoCodeNotifications(context.applicationContext) }
    var enabled by remember { mutableStateOf(store.enabled()) }
    var allowed by remember { mutableStateOf(store.allowed()) }
    LaunchedEffect(Unit) { while (true) { enabled = store.enabled(); allowed = store.allowed(); delay(1000) } }
    fun permission() { context.startActivity(Intent(context, PromoCodePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    AppSectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            AppToggleRow(lt(language, "Avisar sobre novos códigos", "Notify about new codes", "Avisar de nuevos códigos"), enabled, { value ->
                store.setEnabled(value); enabled = value
                if (value) onEnabled()
                if (value && !store.allowed()) permission()
            })
            if (enabled && !allowed) AppActionButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text(lt(language, "Permitir notificações", "Allow notifications", "Permitir notificaciones")) }
        }
    }
}
