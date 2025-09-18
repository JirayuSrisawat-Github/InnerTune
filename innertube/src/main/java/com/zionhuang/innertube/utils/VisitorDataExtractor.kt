package com.zionhuang.innertube.utils

import com.zionhuang.innertube.models.YouTubeClient
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.jvm.Volatile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object VisitorDataExtractor {
    private const val EMBED_URL = "https://www.youtube.com/embed"
    private const val TTL_MILLIS = 60_000L

    private val patterns = listOf(
        Regex("""['\"]visitorData['\"]\s*:\s*['\"]([^'\"]+)['\"]"""),
        Regex("""['\"]VISITOR_DATA['\"]\s*:\s*['\"]([^'\"]+)['\"]"""),
        Regex(
            """\"INNERTUBE_CONTEXT\"\s*:\s*\{[^}]*\"client\"\s*:\s*\{[^}]*\"visitorData\"\s*:\s*\"(.*?)\"""",
            RegexOption.DOT_MATCHES_ALL
        )
    )

    @Volatile
    private var cachedVisitorData: String? = null

    @Volatile
    private var cachedAtMillis: Long = 0L

    private val cacheMutex = Mutex()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun getVisitorData(): String {
        val now = System.currentTimeMillis()
        cachedVisitorData?.let { cached ->
            if (now - cachedAtMillis < TTL_MILLIS) return cached
        }

        return cacheMutex.withLock {
            val refreshedNow = System.currentTimeMillis()
            cachedVisitorData?.let { cached ->
                if (refreshedNow - cachedAtMillis < TTL_MILLIS) {
                    return@withLock cached
                }
            }

            val fresh = fetchAndExtract() ?: throw IOException("visitorData not found")
            cachedVisitorData = fresh
            cachedAtMillis = refreshedNow
            fresh
        }
    }

    fun clearCache() {
        runBlocking {
            cacheMutex.withLock {
                cachedVisitorData = null
                cachedAtMillis = 0L
            }
        }
    }

    private suspend fun fetchAndExtract(): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(EMBED_URL)
            .header("User-Agent", YouTubeClient.WEB.userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP status ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Empty visitor data response")
            extractVisitorData(body)
        }
    }

    private fun extractVisitorData(html: String): String? {
        if (html.isEmpty()) return null
        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }
}