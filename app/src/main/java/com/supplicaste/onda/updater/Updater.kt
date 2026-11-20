package com.supplicaste.onda.updater

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.supplicaste.onda.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File

data class ReleaseInfo(val tag: String, val apkUrl: String, val notes: String)

object Updater {
    suspend fun latest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.UPDATER_REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Onda")
                .build()
            OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string().orEmpty())
                val assets = json.getJSONArray("assets")
                var apkUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                if (apkUrl.isEmpty()) null else ReleaseInfo(
                    tag = json.getString("tag_name"),
                    apkUrl = apkUrl,
                    notes = json.optString("body")
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun download(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder().url(url).header("User-Agent", "Onda").build()
            val file = File(context.filesDir, "onda-update.apk")
            OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.byteStream()?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext null
            }
            if (file.length() > 100_000) file else null
        } catch (e: Exception) {
            null
        }
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
