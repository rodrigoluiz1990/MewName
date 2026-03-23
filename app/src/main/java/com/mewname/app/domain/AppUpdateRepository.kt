package com.mewname.app.domain

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val tagName: String,
    val releaseName: String,
    val releaseNotes: String,
    val releasePageUrl: String,
    val apkDownloadUrl: String?,
    val publishedAt: String
)

class AppUpdateRepository {
    fun fetchLatestRelease(repository: String): AppUpdateInfo {
        val connection = URL("https://api.github.com/repos/$repository/releases/latest")
            .openConnection() as HttpURLConnection
        return connection.run {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "MewName-Android")

            try {
                val code = responseCode
                val payload = (if (code in 200..299) inputStream else errorStream)?.bufferedReader()?.use { it.readText() }
                    .orEmpty()
                if (code !in 200..299) {
                    throw IllegalStateException("Falha ao buscar release no GitHub ($code).")
                }
                parseLatestRelease(JSONObject(payload))
            } finally {
                disconnect()
            }
        }
    }

    private fun parseLatestRelease(json: JSONObject): AppUpdateInfo {
        val assets = json.optJSONArray("assets") ?: JSONArray()
        val apkDownloadUrl = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?.optString("browser_download_url")
            ?.takeIf { it.isNotBlank() }

        return AppUpdateInfo(
            tagName = json.optString("tag_name").ifBlank { "desconhecida" },
            releaseName = json.optString("name").ifBlank { json.optString("tag_name") },
            releaseNotes = json.optString("body").orEmpty(),
            releasePageUrl = json.optString("html_url").orEmpty(),
            apkDownloadUrl = apkDownloadUrl,
            publishedAt = json.optString("published_at").orEmpty()
        )
    }
}
