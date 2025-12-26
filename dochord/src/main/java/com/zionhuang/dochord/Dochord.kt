package com.zionhuang.dochord

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

object Dochord {
    private const val CX = "011494955911425855775:gajhx55tewv"
    private const val BASE_HOST = "https://www.dochord.com"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client = HttpClient {
        expectSuccess = false

        install(ContentNegotiation) {
            json(json)
            json(json, ContentType.Text.Html)
            json(json, ContentType.Text.Plain)
        }

        install(ContentEncoding) {
            gzip()
            deflate()
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 15.seconds.inWholeMilliseconds
            connectTimeoutMillis = 10.seconds.inWholeMilliseconds
        }

        install(HttpRequestRetry) {
            maxRetries = 1
        }
    }

    class RateLimitException(message: String) : Exception(message)

    private const val MAX_RETRIES = 3
    private val RETRY_DELAYS = listOf(2.seconds, 4.seconds, 8.seconds)

    @Volatile
    private var cachedTokens: Tokens? = null

    suspend fun fetchChordSheet(title: String, artist: String): Result<ChordSheet> {
        val query = buildQuery(title, artist)

        var lastException: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            val result = runCatching {
                val chordUrl = searchChordUrl(query)
                    ?: throw IllegalStateException("Chord result not found")
                val html = client.get(chordUrl) { applyDefaultHeaders() }.bodyAsText()
                if (html.contains("GoogleSorry") || html.isBlank()) {
                    cachedTokens = null
                    throw RateLimitException("Access to chord page blocked")
                }
                ChordSheet(ChordParser.parse(html))
            }

            result.onSuccess {
                return Result.success(it)
            }.onFailure { error ->
                lastException = error as? Exception ?: Exception(error)
                if (error is RateLimitException && attempt < MAX_RETRIES - 1) {
                    delay(RETRY_DELAYS[attempt])
                } else {
                    return Result.failure(error)
                }
            }
        }

        return Result.failure(lastException ?: IllegalStateException("Unknown error"))
    }

    private fun buildQuery(title: String, artist: String): String {
        val normalizedTitle = title.trim()
        val normalizedArtist = artist.trim()
        return when {
            normalizedTitle.isEmpty() -> normalizedArtist
            normalizedArtist.isEmpty() -> normalizedTitle
            else -> "$normalizedTitle - $normalizedArtist"
        }
    }

    private suspend fun searchChordUrl(query: String): String? {
        return searchViaCse(query)
    }

    private suspend fun searchViaCse(query: String): String? {
        val tokens = ensureTokens()
        val callback = "google.search.cse.api${Random.nextInt(10_000, 99_999)}"
        val responseText = client.get("https://cse.google.com/cse/element/v1") {
            applyDefaultHeaders()
            parameter("rsz", "filtered_cse")
            parameter("num", 5)
            parameter("hl", "th")
            parameter("source", "gcsc")
            parameter("cselibv", tokens.cselibVersion)
            parameter("cx", CX)
            parameter("q", query)
            parameter("safe", "off")
            parameter("cse_tok", tokens.cseToken)
            parameter("lr", "")
            parameter("cr", "")
            parameter("gl", "th")
            parameter("filter", 0)
            parameter("sort", "")
            parameter("as_oq", "")
            parameter("as_sitesearch", "")
            tokens.exp.takeIf { it.isNotEmpty() }?.let { parameter("exp", it) }
            parameter("callback", callback)
            parameter("rurl", BASE_HOST)
        }.bodyAsText()

        if (responseText.contains("GoogleSorry")) {
            cachedTokens = null
            throw RateLimitException("Rate limited by Google CSE")
        }

        val payload = responseText.substringAfter("$callback(", missingDelimiterValue = "")
            .substringBeforeLast(")")
        if (payload.isBlank()) return null
        val searchResponse = json.decodeFromString(SearchResponse.serializer(), payload)
        return searchResponse.results?.firstOrNull()?.formattedUrl
    }

    private suspend fun ensureTokens(): Tokens {
        cachedTokens?.let { return it }
        val script = client.get("https://cse.google.com/cse.js") {
            applyDefaultHeaders()
            parameter("cx", CX)
        }.bodyAsText()
        val token = TOKEN_REGEX.find(script)?.groupValues?.get(1)
            ?: throw IllegalStateException("Unable to resolve cse_token")
        val cselibVersion = CSE_LIB_REGEX.find(script)?.groupValues?.get(1)
            ?: throw IllegalStateException("Unable to resolve cselibVersion")
        val experiments = EXP_REGEX.find(script)?.groupValues?.get(1)
            ?.split(',')
            ?.map { it.trim().trim('"') }
            ?.filter { it.isNotEmpty() }
            ?.joinToString(",") ?: ""
        return Tokens(token, cselibVersion, experiments).also {
            cachedTokens = it
        }
    }

    private val Tokens.exp: String
        get() = experiments

    private data class Tokens(
        val cseToken: String,
        val cselibVersion: String,
        val experiments: String,
    )

    @Serializable
    internal data class SearchResponse(
        val results: List<SearchResult>? = null,
    )

    @Serializable
    internal data class SearchResult(
        @SerialName("formattedUrl")
        val formattedUrl: String? = null,
    )

    @JvmInline
    value class ChordSheet(val text: String)

    private val TOKEN_REGEX = "\"cse_token\": \"([^\"]+)\"".toRegex()
    private val CSE_LIB_REGEX = "\"cselibVersion\": \"([^\"]+)\"".toRegex()
    private val EXP_REGEX = "\"exp\": \\[(.*?)\\]".toRegex()

    private fun HttpRequestBuilder.applyDefaultHeaders() {
        header(HttpHeaders.UserAgent, USER_AGENT)
        header(HttpHeaders.Referrer, BASE_HOST)
    }

}
