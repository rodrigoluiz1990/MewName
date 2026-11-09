package com.mewname.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Dialog
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.mewname.app.domain.CalendarEvent
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** Preserve local event times, while global events retain their explicit UTC offset. */
internal fun reminderTime(value: String, zone: ZoneId = ZoneId.systemDefault()): Long =
    runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .getOrElse { (if (value.length == 10) java.time.LocalDate.parse(value).atStartOfDay() else LocalDateTime.parse(value)).atZone(zone).toInstant().toEpochMilli() }

internal fun CalendarEvent.reminderJson(): JSONObject = JSONObject()
    .put("id", "$category|$title|$sourceStart")
    .put("title", title).put("category", category).put("label", categoryLabel)
    .put("start", sourceStart).put("end", sourceEnd).put("image", image).put("link", link)

internal fun reminderEvent(json: JSONObject): CalendarEvent {
    val start = com.mewname.app.domain.CalendarParser.date(json.getString("start"))
    val end = com.mewname.app.domain.CalendarParser.date(json.getString("end"))
    return CalendarEvent(json.getString("id"), json.getString("title"), json.getString("category"),
        json.getString("label"), start, end, json.optString("image").takeUnless { it.isBlank() || it == "null" },
        json.optString("link").takeUnless { it.isBlank() || it == "null" }, json.getString("start"), json.getString("end"))
}

internal class EventReminders(private val context: Context) {
    val preferences = context.getSharedPreferences("calendar_reminders", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    fun exactAllowed() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    fun notificationsAllowed(): Boolean {
        val runtimeAllowed = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimeAllowed && notifications.areNotificationsEnabled() &&
            notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    private fun pending(id: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, EventReminderReceiver::class.java).setAction(FIRE)
            .setData(Uri.parse("mewname://event-reminder/" + Uri.encode(id))).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun contains(id: String) = preferences.contains(id)
    fun cancel(id: String) {
        alarms.cancel(pending(id))
        preferences.edit().remove(id).commit()
        notifications.cancel(id, 1)
    }
    fun schedule(json: JSONObject, now: Long = System.currentTimeMillis()): Boolean {
        val time = reminderTime(json.getString("start"))
        if (time <= now || !exactAllowed() || !notificationsAllowed()) return false
        val id = json.getString("id")
        // Save before scheduling so a delivered alarm always has its payload.
        if (!preferences.edit().putString(id, json.toString()).commit()) return false
        return try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending(id))
            true
        } catch (_: SecurityException) {
            preferences.edit().remove(id).commit()
            false
        }
    }
    fun restore(now: Long = System.currentTimeMillis()) {
        preferences.all.values.filterIsInstance<String>().forEach { value ->
            runCatching {
                val json = JSONObject(value)
                if (reminderTime(json.getString("start")) > now) schedule(json, now)
                else deliver(json.getString("id"), now)
            }.onFailure { android.util.Log.w("EventReminders", "Unable to restore reminder", it) }
        }
    }
    /** Called only after a successful complete calendar refresh, never for a failed feed. */
    fun reconcile(events: List<CalendarEvent>, year: Int) {
        preferences.all.values.filterIsInstance<String>().forEach { value ->
            val old = runCatching { JSONObject(value) }.getOrNull() ?: return@forEach
            if (com.mewname.app.domain.CalendarParser.date(old.getString("start")).year != year) return@forEach
            val same = events.firstOrNull { it.reminderJson().getString("id") == old.getString("id") }
                ?: events.filter { it.category == old.getString("category") && it.title == old.getString("title") }
                    .singleOrNull()
            if (same == null) cancel(old.getString("id"))
            else if (same.reminderJson().toString() != old.toString()) {
                cancel(old.getString("id"))
                schedule(same.reminderJson())
            }
        }
    }
    fun deliver(id: String, now: Long = System.currentTimeMillis()) {
        val value = preferences.getString(id, null) ?: return
        val json = JSONObject(value)
        val start = reminderTime(json.getString("start"))
        if (start > now) { schedule(json, now); return }
        // Do not deliver stale reminders after a long period with the device off.
        if (now - start > 60 * 60 * 1000L || reminderTime(json.getString("end")) <= now || !notificationsAllowed()) {
            cancel(id); return
        }
        val language = savedAppLanguage(context)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,
            lt(language, "Eventos do calend\u00e1rio", "Calendar events", "Eventos del calendario"), NotificationManager.IMPORTANCE_DEFAULT))
        val open = Intent(context, MainActivity::class.java).setAction("com.mewname.app.OPEN_EVENT")
            .setData(Uri.parse("mewname://calendar/" + Uri.encode(id)))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("BUBBLE_DESTINATION", "CALENDAR").putExtra("CALENDAR_EVENT", json.toString())
        val tap = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_event_notification).setContentTitle(json.getString("title"))
            .setContentText(lt(language, "O evento come\u00e7ou!", "The event has started!", "\u00a1El evento ha comenzado!"))
            .setContentIntent(tap).setAutoCancel(true).setCategory(Notification.CATEGORY_EVENT).build()
        try {
            notifications.notify(id, 1, notification)
            preferences.edit().remove(id).commit()
        } catch (_: SecurityException) { cancel(id) }
    }
    companion object {
        const val CHANNEL = "calendar_events"
        const val FIRE = "com.mewname.app.EVENT_REMINDER"
    }
}

class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = EventReminders(context)
        if (intent.action == EventReminders.FIRE) intent.getStringExtra("id")?.let { store.deliver(it) }
        else store.restore()
    }
}

/** A real Activity can request permissions both from the main app and the overlay. */
class EventReminderPermissionActivity : ComponentActivity() {
    private val store by lazy { EventReminders(this) }
    private var noticeVisible = false
    private var failureText: String? = null
    private fun message(pt: String, en: String, es: String) = lt(savedAppLanguage(this), pt, en, es)
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) checkAlarm() else finishWith(message("Lembrete n\u00e3o ativado: permita as notifica\u00e7\u00f5es.", "Reminder not enabled: allow notifications.", "Recordatorio no activado: permite las notificaciones."))
    }
    private val alarmSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        save()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            val failure = savedInstanceState.getString("failure")
            if (failure != null) finishWith(failure)
            else if (savedInstanceState.getBoolean("notice")) checkAlarm()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else checkAlarm()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("notice", noticeVisible)
        outState.putString("failure", failureText)
        super.onSaveInstanceState(outState)
    }
    private fun showNotice(title: String, text: String, actionLabel: String, action: () -> Unit) {
        noticeVisible = true
        setContent {
            AppAppearanceTheme {
                CompositionLocalProvider(LocalAppLanguage provides savedAppLanguage(this)) {
                    Dialog(onDismissRequest = { finish() }) {
                        AppModalPanel(title = title, onClose = { finish() }, actions = {
                            AppActionButton(onClick = action) { Text(actionLabel) }
                        }) { Text(text) }
                    }
                }
            }
        }
    }
    private fun checkAlarm() {
        if (!store.notificationsAllowed()) {
            showNotice(message("Notifica\u00e7\u00f5es desativadas", "Notifications disabled", "Notificaciones desactivadas"),
                message("Ative as notifica\u00e7\u00f5es dos eventos nas configura\u00e7\u00f5es do Android e tente novamente.", "Enable event notifications in Android settings and try again.", "Activa las notificaciones de eventos en los ajustes de Android e int\u00e9ntalo de nuevo."),
                message("Configura\u00e7\u00f5es", "Settings", "Ajustes")) {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)); finish()
                }
        } else if (Build.VERSION.SDK_INT >= 31 && !store.exactAllowed()) {
            showNotice(message("Lembrar no hor\u00e1rio do evento", "Remind at event start", "Recordar al inicio del evento"),
                message("Permita alarmes e lembretes para avisar no hor\u00e1rio de in\u00edcio, mesmo com o app fechado.", "Allow alarms and reminders to notify you at the start, even when the app is closed.", "Permite alarmas y recordatorios para avisar al inicio, incluso con la app cerrada."),
                message("Continuar", "Continue", "Continuar")) {
                    alarmSettings.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                }
        } else save()
    }
    private fun save() {
        val success = runCatching { store.schedule(JSONObject(intent.getStringExtra("event") ?: "")) }.getOrDefault(false)
        if (success) finish() else finishWith(
            message("Lembrete n\u00e3o ativado. Confira as permiss\u00f5es e o hor\u00e1rio do evento.", "Reminder not enabled. Check permissions and event time.", "Recordatorio no activado. Revisa los permisos y la hora del evento."))
    }
    private fun finishWith(text: String) {
        failureText = text
        showNotice(message("Lembrete do evento", "Event reminder", "Recordatorio del evento"), text,
            message("Entendi", "Got it", "Entendido")) { finish() }
    }
}
