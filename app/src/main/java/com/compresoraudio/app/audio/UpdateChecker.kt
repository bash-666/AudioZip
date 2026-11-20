package com.compresoraudio.app.audio

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val version: String,
    val notes: String,
    val apkUrl: String?,
    val pageUrl: String,
)

/**
 * Checks GitHub Releases for a newer AudioZip build and downloads the APK
 * with the system DownloadManager so the user can install it in place.
 */
object UpdateChecker {

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/bash-666/AudioZip/releases/latest"
    private const val RELEASES_PAGE =
        "https://github.com/bash-666/AudioZip/releases"

    /** Fetches the latest GitHub release. Returns null on any failure. */
    suspend fun checkLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val version = json.optString("tag_name", "").removePrefix("v").removePrefix("V")
            val notes = json.optString("body", "")
            val pageUrl = json.optString("html_url", RELEASES_PAGE)
            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name", "").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }
            if (version.isBlank()) null else ReleaseInfo(version, notes, apkUrl, pageUrl)
        } catch (e: Exception) {
            null
        }
    }

    /** True when [latest] (e.g. "1.0.1") is newer than [current] (e.g. "1.0.0"). */
    fun isNewerVersion(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }

    /** Enqueues the APK download in the public Downloads folder. Returns the download id. */
    fun enqueueDownload(context: Context, apkUrl: String, fileName: String): Long {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle(fileName)
            .setDescription("Actualización de AudioZip")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        return dm.enqueue(request)
    }

    /** DownloadManager status for [downloadId], or null when unknown. */
    fun downloadStatus(context: Context, downloadId: Long): Int? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId)) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val idx = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            return if (idx >= 0) it.getInt(idx) else null
        }
    }

    /** Returns (downloadedBytes, totalBytes) or null when unknown. */
    fun downloadProgress(context: Context, downloadId: Long): Pair<Long, Long>? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId)) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val dlIdx = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalIdx = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            if (dlIdx < 0 || totalIdx < 0) return null
            return it.getLong(dlIdx) to it.getLong(totalIdx)
        }
    }

    /** Builds the intent that opens the system installer for a finished download. */
    fun installIntent(context: Context, downloadId: Long): Intent? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = dm.getUriForDownloadedFile(downloadId) ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Intent to the system settings where the user grants install permission. */
    fun installPermissionIntent(context: Context): Intent {
        return Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
}
