package com.mrjis.medicinetime.update

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data class Available(val version: String, val pageUrl: String) : UpdateResult
    data object Failed : UpdateResult
}

/**
 * Asks GitHub for the latest release and compares its tag (for example "v1.0.1") with the installed version.
 * Needs the repository to be public. It only opens the release page in the browser; it never installs anything.
 */
class UpdateChecker(context: Context) {

    private val appContext = context.applicationContext

    /** The installed version name, for example "1.0.0". */
    fun currentVersion(): String = try {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty()
    } catch (e: Exception) {
        ""
    }

    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MedicineTime-Android")
            }
            try {
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                        val tag = json.getString("tag_name")
                        val pageUrl = json.optString("html_url").ifBlank { RELEASES_PAGE }
                        if (isNewer(tag, currentVersion())) {
                            UpdateResult.Available(tag.removePrefix("v").removePrefix("V"), pageUrl)
                        } else {
                            UpdateResult.UpToDate
                        }
                    }
                    // No release has been published yet.
                    HttpURLConnection.HTTP_NOT_FOUND -> UpdateResult.UpToDate
                    else -> UpdateResult.Failed
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdateResult.Failed
        }
    }

    companion object {
        // Change these two if the repository is named differently.
        private const val REPO_OWNER = "mrjis"
        private const val REPO_NAME = "MedicineTime-AndroidApp"

        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
        const val RELEASES_PAGE = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"
        private const val TIMEOUT_MILLIS = 10_000

        /** "v1.2.0" is newer than "1.1.9.4". Compares the number groups left to right. */
        fun isNewer(remoteTag: String, installed: String): Boolean {
            val remote = numbers(remoteTag)
            val local = numbers(installed)
            if (remote.isEmpty()) return false
            for (i in 0 until maxOf(remote.size, local.size)) {
                val r = remote.getOrElse(i) { 0 }
                val l = local.getOrElse(i) { 0 }
                if (r != l) return r > l
            }
            return false
        }

        private fun numbers(version: String): List<Int> =
            Regex("\\d+").findAll(version).mapNotNull { it.value.toIntOrNull() }.toList()
    }
}