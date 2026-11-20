package com.supplicaste.onda.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.floor

/**
 * Acceso a Spotify con la cuenta web, sin Client ID: se usan las cookies
 * `sp_dc`/`sp_key` obtenidas al iniciar sesión en un WebView.
 *
 * El token se pide al endpoint interno del reproductor web, que exige un TOTP
 * generado con el secreto rotatorio publicado por la comunidad (el mismo
 * mecanismo que utiliza ArchiveTune). Si Spotify cambia este mecanismo, el
 * flujo OAuth con Client ID sigue disponible como alternativa.
 */
object SpotifyWebAuth {
    const val LOGIN_URL = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

    private const val TOKEN_URL = "https://open.spotify.com/api/token"
    private const val SERVER_TIME_URL = "https://open.spotify.com/api/server-time"
    private const val NUANCE_URL =
        "https://gist.githubusercontent.com/sonic-liberation/22ed9c6ba463899e933427f7de1f0eef/raw/nuances.json"
    private const val NUANCE_TTL_NANOS = 5 * 60 * 1_000_000_000L
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    private val mutex = Mutex()

    @Volatile
    private var cachedNuance: Nuance? = null

    @Volatile
    private var nuanceFetchedAtNanos = 0L

    data class Token(val accessToken: String, val expiresAtMillis: Long)

    private data class Nuance(val secret: String, val version: Int)

    /** Canjea las cookies de sesión por un access token del reproductor web. */
    suspend fun fetchAccessToken(
        spDc: String,
        spKey: String = "",
    ): Result<Token> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (spDc.isBlank()) error("No se encontró la cookie de sesión de Spotify")

                val nuance = fetchNuance()
                val totp = generateTotp(nuance.secret, fetchServerTime())
                val url =
                    "$TOKEN_URL?reason=transport&productType=web-player" +
                        "&totp=$totp&totpServer=$totp&totpVer=${nuance.version}"
                val cookie =
                    buildString {
                        append("sp_dc=").append(spDc)
                        if (spKey.isNotBlank()) append("; sp_key=").append(spKey)
                    }
                val json = JSONObject(get(url, mapOf("Cookie" to cookie)))
                val token = json.optString("accessToken").orEmpty()
                if (json.optBoolean("isAnonymous") || token.isBlank()) {
                    error("La sesión de Spotify caducó; vuelve a iniciar sesión")
                }
                Token(
                    accessToken = token,
                    expiresAtMillis = json.optLong("accessTokenExpirationTimestampMs", 0L)
                        .takeIf { it > 0L } ?: System.currentTimeMillis() + 3_600_000L
                )
            }
        }

    private suspend fun fetchNuance(): Nuance {
        val now = System.nanoTime()
        cachedNuance?.let { cached ->
            if (now - nuanceFetchedAtNanos < NUANCE_TTL_NANOS) return cached
        }
        return mutex.withLock {
            val lockedNow = System.nanoTime()
            cachedNuance?.let { cached ->
                if (lockedNow - nuanceFetchedAtNanos < NUANCE_TTL_NANOS) return@withLock cached
            }
            val values = JSONArray(get(NUANCE_URL, emptyMap()))
            val latest =
                (0 until values.length())
                    .mapNotNull { index -> values.optJSONObject(index) }
                    .mapNotNull { item ->
                        val secret = item.optString("s").orEmpty()
                        val version = item.optInt("v", 0)
                        if (version > 0 && secret.isValidBase32()) Nuance(secret, version) else null
                    }
                    .maxByOrNull { it.version }
                    ?: error("Spotify no está disponible en este momento")
            cachedNuance = latest
            nuanceFetchedAtNanos = lockedNow
            latest
        }
    }

    private suspend fun fetchServerTime(): Long {
        val json = JSONObject(get(SERVER_TIME_URL, emptyMap()))
        return json.optLong("serverTime", 0L).takeIf { it > 0L }
            ?: error("No se pudo sincronizar con Spotify")
    }

    /** TOTP de 6 dígitos (RFC 6238) con HMAC-SHA1 y intervalo de 30 s. */
    private fun generateTotp(
        secret: String,
        serverTimeSeconds: Long,
    ): String {
        val key = secret.base32Decode()
        val timeStep = floor(serverTimeSeconds / 30.0).toLong()
        val timeBytes = ByteArray(8)
        var value = timeStep
        for (index in 7 downTo 0) {
            timeBytes[index] = (value and 0xFF).toByte()
            value = value shr 8
        }
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(timeBytes)
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val code =
            ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)
        return (code % 1_000_000).toString().padStart(6, '0')
    }

    private fun String.isValidBase32(): Boolean =
        isNotBlank() && all { it == '=' || BASE32_ALPHABET.contains(it.uppercaseChar()) }

    private fun String.base32Decode(): ByteArray {
        val alphabet = BASE32_ALPHABET
        val output = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0
        for (character in uppercase().replace("=", "")) {
            val value = alphabet.indexOf(character)
            if (value < 0) continue
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return output.toByteArray()
    }

    private fun get(
        url: String,
        headers: Map<String, String>,
    ): String {
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en")
                .apply { headers.forEach { (key, value) -> header(key, value) } }
                .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Spotify respondió con HTTP ${response.code}")
            }
            return body
        }
    }
}
