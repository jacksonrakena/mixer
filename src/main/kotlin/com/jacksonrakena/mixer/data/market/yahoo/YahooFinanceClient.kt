package com.jacksonrakena.mixer.data.market.yahoo

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.HttpCookie
import java.net.URI
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * HTTP client for Yahoo Finance API with cookie/crumb management.
 *
 * Translated from yfinance's data.py YfData class.
 *
 * Yahoo Finance requires a valid cookie and CSRF crumb for API requests.
 * The "basic" strategy:
 *   1. GET https://fc.yahoo.com → receive Set-Cookie header
 *   2. GET https://query1.finance.yahoo.com/v1/test/getcrumb with cookie → receive crumb text
 *   3. Append crumb as query parameter to all subsequent API requests
 */
@Component
open class YahooFinanceClient {
    private val logger = KotlinLogging.logger {}

    private val restClient: RestClient = RestClient.builder()
        .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
        .build()

    private var crumb: String? = null
    private var cookieHeader: String? = null
    private val lock = ReentrantLock()

    /**
     * Makes a GET request to a Yahoo Finance API URL with cookie/crumb injection.
     */
    open fun get(url: String, params: Map<String, String> = emptyMap(), timeout: Int = 30): String {
        val (resolvedCrumb, strategy) = getCookieAndCrumb()

        val allParams = buildMap {
            putAll(params)
            if (resolvedCrumb != null) {
                put("crumb", resolvedCrumb)
            }
        }

        val fullUrl = buildUrl(url, allParams)
        logger.debug { "GET $fullUrl" }

        val response = executeGet(fullUrl)

        if (response.statusCode >= 400) {
            logger.debug { "Response code=${response.statusCode}, retrying with fresh cookie/crumb" }
            // Invalidate and retry
            lock.withLock {
                crumb = null
                cookieHeader = null
            }
            val (retryCrumb, _) = getCookieAndCrumb()
            val retryParams = buildMap {
                putAll(params)
                if (retryCrumb != null) {
                    put("crumb", retryCrumb)
                }
            }
            val retryUrl = buildUrl(url, retryParams)
            val retryResponse = executeGet(retryUrl)

            if (retryResponse.statusCode == 429) {
                throw YahooFinanceRateLimitException()
            }
            return retryResponse.body
        }

        return response.body
    }

    private fun getCookieAndCrumb(): Pair<String?, String> {
        lock.withLock {
            if (crumb != null && cookieHeader != null) {
                return crumb to "basic"
            }

            logger.debug { "Fetching new cookie and crumb (basic strategy)" }

            // Step 1: Get cookie from fc.yahoo.com
            if (cookieHeader == null) {
                fetchCookieBasic()
            }

            // Step 2: Get crumb
            if (crumb == null && cookieHeader != null) {
                fetchCrumbBasic()
            }

            return crumb to "basic"
        }
    }

    /**
     * Fetches a cookie by hitting fc.yahoo.com.
     * Translated from data.py _get_cookie_basic().
     */
    private fun fetchCookieBasic() {
        try {
            val response = restClient.get()
                .uri(COOKIE_URL)
                .exchange { _, clientResponse ->
                    val setCookieHeaders = clientResponse.headers[HttpHeaders.SET_COOKIE] ?: emptyList()
                    val cookies = setCookieHeaders.flatMap { header ->
                        try {
                            HttpCookie.parse(header)
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                    cookies
                }

            if (response.isNotEmpty()) {
                cookieHeader = response.joinToString("; ") { "${it.name}=${it.value}" }
                logger.debug { "Got cookie from fc.yahoo.com" }
            } else {
                logger.debug { "No cookies received from fc.yahoo.com" }
            }
        } catch (e: Exception) {
            logger.debug(e) { "Failed to fetch cookie from fc.yahoo.com" }
        }
    }

    /**
     * Fetches the CSRF crumb from Yahoo.
     * Translated from data.py _get_crumb_basic().
     */
    private fun fetchCrumbBasic() {
        try {
            val cookie = cookieHeader ?: return
            val crumbText = restClient.get()
                .uri(CRUMB_URL)
                .header(HttpHeaders.COOKIE, cookie)
                .exchange { _, clientResponse ->
                    val body = clientResponse.body.bufferedReader().readText()
                    val status = clientResponse.statusCode.value()
                    status to body
                }

            val (status, body) = crumbText

            if (status == 429 || body.contains("Too Many Requests")) {
                throw YahooFinanceRateLimitException()
            }

            if (body.contains("<html>") || body.isBlank()) {
                logger.debug { "Didn't receive valid crumb (got HTML or blank)" }
                return
            }

            crumb = body
            logger.debug { "Got crumb" }
        } catch (e: YahooFinanceRateLimitException) {
            throw e
        } catch (e: Exception) {
            logger.debug(e) { "Failed to fetch crumb" }
        }
    }

    private fun executeGet(url: String): SimpleResponse {
        val cookie = cookieHeader
        return restClient.get()
            .uri(url)
            .apply {
                if (cookie != null) {
                    header(HttpHeaders.COOKIE, cookie)
                }
            }
            .exchange { _, clientResponse ->
                SimpleResponse(
                    statusCode = clientResponse.statusCode.value(),
                    body = clientResponse.body.bufferedReader().readText()
                )
            }
    }

    private fun buildUrl(baseUrl: String, params: Map<String, String>): String {
        if (params.isEmpty()) return baseUrl
        val uri = URI(baseUrl)
        val query = params.entries.joinToString("&") { (k, v) ->
            "${java.net.URLEncoder.encode(k, Charsets.UTF_8)}=${java.net.URLEncoder.encode(v, Charsets.UTF_8)}"
        }
        val existingQuery = uri.rawQuery
        val fullQuery = if (existingQuery.isNullOrBlank()) query else "$existingQuery&$query"
        return URI(uri.scheme, uri.authority, uri.path, fullQuery, uri.fragment).toString()
    }

    private data class SimpleResponse(val statusCode: Int, val body: String)

    companion object {
        // Translated from const.py
        const val BASE_URL = "https://query2.finance.yahoo.com"
        const val QUERY1_URL = "https://query1.finance.yahoo.com"
        private const val COOKIE_URL = "https://fc.yahoo.com"
        private const val CRUMB_URL = "$QUERY1_URL/v1/test/getcrumb"

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
