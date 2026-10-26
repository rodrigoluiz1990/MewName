package com.mewname.app

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.job.JobParameters
import android.content.ComponentName
import android.content.Context
import java.io.File
import java.util.concurrent.Executors

/** Storage selected through Android's document picker; survives app uninstall. */
internal interface BackupFolder {
    fun snapshots(): List<String>
    fun read(id: String): String
    fun create(): String
    fun write(id: String, text: String)
    fun delete(id: String)
}

internal class DocumentBackupFolder(private val context: Context, private val tree: android.net.Uri) : BackupFolder {
    private val resolver get() = context.contentResolver
    private val root get() = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,
        android.provider.DocumentsContract.getTreeDocumentId(tree))
    override fun snapshots(): List<String> {
        val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree,
            android.provider.DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return requireNotNull(resolver.query(children, columns, null, null, null)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) if (Regex("MewName-auto-[0-9]+-[a-f0-9-]+\\.json").matches(cursor.getString(1)))
                    add(android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)).toString())
            }
        }
    }
    override fun read(id: String) = UserBackup.readDocument(context, android.net.Uri.parse(id))
    override fun create(): String = requireNotNull(android.provider.DocumentsContract.createDocument(resolver, root,
        "application/json", "MewName-auto-${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}.json")).toString()
    override fun write(id: String, text: String) {
        requireNotNull(resolver.openOutputStream(android.net.Uri.parse(id), "wt")).use {
            it.write(text.toByteArray(Charsets.UTF_8)); it.flush()
        }
    }
    override fun delete(id: String) { check(android.provider.DocumentsContract.deleteDocument(resolver, android.net.Uri.parse(id))) }
}

internal object AutomaticBackup {
    private const val JOB_ID = 45291
    internal const val DAY = 24 * 60 * 60 * 1000L
    private val executor = Executors.newSingleThreadExecutor()
    private fun prefs(context: Context) = context.getSharedPreferences("automatic_backup", 0)
    fun folder(context: Context) = prefs(context).getString("folder", null)?.let(android.net.Uri::parse)
    fun enabled(context: Context) = folder(context) != null && prefs(context).getBoolean("enabled", false)
    fun lastSuccess(context: Context) = prefs(context).getLong("last_success", 0)
    private fun legacy(context: Context) = File(context.filesDir, "automatic-backup/MewName-backup.json")
    private fun storage(context: Context) = DocumentBackupFolder(context, requireNotNull(folder(context)))
    internal fun latest(store: BackupFolder): String? = store.snapshots().mapNotNull { id ->
        runCatching { store.read(id).also(UserBackup::validate) }.getOrNull()
    }.maxByOrNull { org.json.JSONObject(it).getLong("createdAt") }
    /** Validate and reread the replacement BEFORE removing any old snapshots. */
    internal fun replace(store: BackupFolder, text: String) {
        UserBackup.validate(text)
        val old = store.snapshots()
        val fresh = store.create()
        try {
            store.write(fresh, text)
            check(store.read(fresh) == text)
        } catch (error: Exception) {
            runCatching { store.delete(fresh) }
            throw error
        }
        old.forEach(store::delete)
    }
    @Synchronized fun read(context: Context): String = if (folder(context) != null)
        requireNotNull(latest(storage(context))) { "No valid backup" }
    else UserBackup.readDocument(context, android.net.Uri.fromFile(legacy(context)))

    /** Existing external data stays protected until the user restores or explicitly enables backup. */
    @Synchronized fun selectFolder(context: Context, uri: android.net.Uri): Boolean {
        val store = DocumentBackupFolder(context, uri)
        val existingIds = store.snapshots()
        val existing = latest(store)
        check(prefs(context).edit().putString("folder", uri.toString()).putBoolean("enabled", false)
            .putLong("last_success", existing?.let { org.json.JSONObject(it).getLong("createdAt") } ?: 0).commit())
        schedule(context)
        if (existingIds.isNotEmpty()) return true
        val text = if (legacy(context).isFile) readLegacy(context) else synchronized(UserBackup) { UserBackup.export(context) }
        replace(store, text)
        check(prefs(context).edit().putLong("last_success", System.currentTimeMillis()).putBoolean("enabled", true).commit())
        legacy(context).delete()
        schedule(context)
        return false
    }
    private fun readLegacy(context: Context) = UserBackup.readDocument(context, android.net.Uri.fromFile(legacy(context)))
    @Synchronized fun setEnabled(context: Context, value: Boolean) {
        if (value) requireNotNull(folder(context))
        check(prefs(context).edit().putBoolean("enabled", value).commit())
        schedule(context)
    }
    fun initialize(context: Context) {
        val app = context.applicationContext
        executor.execute { runCatching { schedule(app); writeIfDue(app) } }
    }
    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (!enabled(context)) { scheduler.cancel(JOB_ID); return }
        if (scheduler.getPendingJob(JOB_ID) == null) {
            check(scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, AutomaticBackupService::class.java))
                .setPeriodic(DAY).setPersisted(true).build()) == JobScheduler.RESULT_SUCCESS)
        }
    }
    internal fun due(last: Long, now: Long) = last <= 0 || now < last || now - last >= DAY
    @Synchronized fun writeIfDue(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        if (!enabled(context) || !due(lastSuccess(context), now)) return false
        synchronized(UserBackup) {
            replace(storage(context), UserBackup.export(context))
            check(prefs(context).edit().putLong("last_success", now).commit())
        }
        return true
    }
}

class AutomaticBackupService : JobService() {
    private val executor = Executors.newSingleThreadExecutor()
    private var task: java.util.concurrent.Future<*>? = null
    override fun onStartJob(params: JobParameters): Boolean {
        task = executor.submit {
            val success = runCatching { AutomaticBackup.writeIfDue(applicationContext) }.isSuccess
            jobFinished(params, !success)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean {
        task?.cancel(true)
        return true
    }
    override fun onDestroy() { executor.shutdown(); super.onDestroy() }
}
