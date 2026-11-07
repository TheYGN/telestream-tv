package com.telestream.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Updates from the TheYGN/telestream-tv repository: release/latest.json names the newest version and its APK
 * (a GitHub release answer, with tag_name and assets, is understood too).
 * Android doesn't let an app outside a store update itself silently, so the viewer confirms the install.
 */
object Updater {
    data class Release(val version: String, val notes: String, val apkUrl: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** The newest release when it is newer than this app, else null. */
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(BuildConfig.UPDATE_URL)
            .header("Accept", "application/vnd.github+json").build()
        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string() ?: ""
        }
        val o = JSONObject(body)
        val version = o.optString("version").ifBlank { o.optString("tag_name") }.removePrefix("v")
        val assets = o.optJSONArray("assets")
        val apk = o.optString("apk").ifBlank { null } ?: (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") }?.optString("browser_download_url")
        val notes = o.optString("notes").ifBlank { o.optString("body") }.trim()
        if (apk.isNullOrBlank() || !isNewer(version, BuildConfig.VERSION_NAME)) null
        else Release(version, notes, apk)
    }

    /** "1.10.0" > "1.9.2": compares number by number. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Downloads the APK, reporting 0..1 as it goes. */
    suspend fun download(context: Context, release: Release, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // only the newest download is kept
        val file = File(dir, "TeleStreamTV-${release.version}.apk")
        client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("empty")
            val total = body.contentLength().toFloat()
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) progress(done / total)
                    }
                }
            }
        }
        file
    }

    /** Whether Android lets this app open the installer; on 8.0+ the viewer allows it once in the settings. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
