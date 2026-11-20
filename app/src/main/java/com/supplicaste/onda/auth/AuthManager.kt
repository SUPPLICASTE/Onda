package com.supplicaste.onda.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.core.content.edit
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.supplicaste.onda.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

class AuthManager(private val context: Context) {
    private val configured =
        BuildConfig.FIREBASE_API_KEY.isNotBlank() && BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()

    val isConfigured: Boolean get() = configured
    val modeLabel: String get() = if (spotifyLinked) "Spotify" else if (configured) "Firebase" else "Local"

    val googleAvailable: Boolean get() = configured && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /** Client ID de Spotify: se puede introducir desde la app sin recompilar. */
    var spotifyClientId: String
        get() = prefs.getString("spotify_client_id", null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SPOTIFY_CLIENT_ID
        set(value) = prefs.edit { putString("spotify_client_id", value.trim()) }

    val spotifyAvailable: Boolean get() = spotifyClientId.isNotBlank()
    val spotifyLinked: Boolean get() = !prefs.getString("spotify_token", null).isNullOrBlank()
    val spotifyEmail: String? get() = prefs.getString("spotify_email", null)
    val spotifyCookiesAvailable: Boolean get() = !prefs.getString("spotify_sp_dc", null).isNullOrBlank()

    fun spotifyAccessToken(): String? = prefs.getString("spotify_token", null)

    /** Devuelve un token válido: renueva con OAuth o, si no hay Client ID, con las cookies web. */
    suspend fun validSpotifyToken(): String? {
        val token = spotifyAccessToken() ?: return null
        val expiresAt = prefs.getLong("spotify_expires_at", 0L)
        if (expiresAt - System.currentTimeMillis() > 60_000) return token
        prefs.getString("spotify_refresh_token", null)
            ?.takeIf { it.isNotBlank() }
            ?.let { refreshToken -> refreshSpotifyWithOAuth(refreshToken) }
            ?.let { return it }
        val spDc = prefs.getString("spotify_sp_dc", null)
        if (!spDc.isNullOrBlank()) {
            refreshSpotifyWithCookies(spDc, prefs.getString("spotify_sp_key", null).orEmpty())
                ?.let { return it }
        }
        return token
    }

    private suspend fun refreshSpotifyWithOAuth(refreshToken: String): String? = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", spotifyClientId)
            .build()
        try {
            val request = Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                val fresh = json?.optString("access_token").orEmpty()
                if (!response.isSuccessful || fresh.isBlank()) return@withContext null
                saveSpotifyToken(fresh, System.currentTimeMillis() + (json?.optLong("expires_in", 3600L) ?: 3600L) * 1000)
                fresh
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun refreshSpotifyWithCookies(
        spDc: String,
        spKey: String,
    ): String? =
        SpotifyWebAuth.fetchAccessToken(spDc, spKey)
            .getOrNull()
            ?.let { token ->
                saveSpotifyToken(token.accessToken, token.expiresAtMillis)
                token.accessToken
            }

    private fun saveSpotifyToken(
        token: String,
        expiresAt: Long,
    ) {
        prefs.edit {
            putString("spotify_token", token)
            putLong("spotify_expires_at", expiresAt)
        }
    }

    fun youtubeAccessToken(): String? = prefs.getString("youtube_token", null)
    val youtubeLinked: Boolean get() = !youtubeAccessToken().isNullOrBlank()

    private val prefs = context.getSharedPreferences("onda_auth", Context.MODE_PRIVATE)
    private val client = OkHttpClient()

    val localEmail: String? get() = prefs.getString("email", null)

    init {
        if (configured && FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .build()
            FirebaseApp.initializeApp(context, options)
        }
    }

    private fun auth(): FirebaseAuth = Firebase.auth

    // ---------- Google ----------

    private fun googleClient(includeYouTube: Boolean = false): GoogleSignInClient? {
        if (!googleAvailable) return null
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
        if (includeYouTube) {
            builder.requestScopes(Scope("https://www.googleapis.com/auth/youtube.readonly"))
            builder.requestServerAuthCode(BuildConfig.GOOGLE_WEB_CLIENT_ID, false)
        }
        return GoogleSignIn.getClient(context, builder.build())
    }

    fun googleSignInIntent(includeYouTube: Boolean = false): Intent? =
        googleClient(includeYouTube)?.signInIntent

    fun googleTokenFrom(resultIntent: Intent?): String? =
        GoogleSignIn.getSignedInAccountFromIntent(resultIntent).result?.idToken

    /** Devuelve el idToken y, si se pidió, el serverAuthCode para YouTube. */
    fun googleTokenAndCode(resultIntent: Intent?): Pair<String?, String?> {
        val account = GoogleSignIn.getSignedInAccountFromIntent(resultIntent).result
        return account.idToken to account.serverAuthCode
    }

    /** Canjea el serverAuthCode de Google por un access token para la API de YouTube. */
    suspend fun exchangeGoogleAuthCode(code: String): Result<String> = withContext(Dispatchers.IO) {
        if (BuildConfig.GOOGLE_CLIENT_SECRET.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Configura ONDA_GOOGLE_CLIENT_SECRET para importar YouTube")
            )
        }
        val body = FormBody.Builder()
            .add("code", code)
            .add("client_id", BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .add("client_secret", BuildConfig.GOOGLE_CLIENT_SECRET)
            .add("grant_type", "authorization_code")
            .build()
        try {
            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                val token = json?.optString("access_token").orEmpty()
                if (!response.isSuccessful || token.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException(json?.optString("error_description")
                            ?.takeIf { it.isNotBlank() } ?: "No se pudo autorizar YouTube")
                    )
                }
                prefs.edit { putString("youtube_token", token) }
                Result.success(token)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(token: String): Result<String> {
        if (!configured) return Result.failure(IllegalStateException("Firebase no configurado"))
        return try {
            val credential = GoogleAuthProvider.getCredential(token, null)
            val result = auth().signInWithCredential(credential).await()
            val email = result.user?.email.orEmpty()
            prefs.edit { putString("email", email) }
            Result.success(email)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------- Spotify (OAuth 2.0 con PKCE) ----------

    /** Prepara el estado PKCE y devuelve la URL de autorización, o null si falta Client ID. */
    fun spotifyAuthorizationUrl(): String? {
        if (!spotifyAvailable) return null
        val verifier = randomUrlSafe()
        val state = randomUrlSafe()
        prefs.edit {
            putString("spotify_verifier", verifier)
            putString("spotify_state", state)
        }
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return Uri.Builder().scheme("https").authority("accounts.spotify.com")
            .appendPath("authorize")
            .appendQueryParameter("client_id", spotifyClientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", spotifyRedirectUri())
            .appendQueryParameter("scope", "user-read-email user-read-private playlist-read-private playlist-read-collaborative")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .build()
            .toString()
    }

    private fun spotifyRedirectUri(): String =
        BuildConfig.SPOTIFY_REDIRECT_URI.ifBlank { "ondaoauth://spotify/callback" }

    suspend fun exchangeSpotifyCode(code: String, state: String?): Result<String> = withContext(Dispatchers.IO) {
        val savedState = prefs.getString("spotify_state", null)
        if (state.isNullOrBlank() || savedState == null || state != savedState) {
            return@withContext Result.failure(IllegalStateException("La sesión de Spotify caducó; inténtalo de nuevo"))
        }
        val verifier = prefs.getString("spotify_verifier", null)
            ?: return@withContext Result.failure(IllegalStateException("Falta el verificador PKCE"))

        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", spotifyRedirectUri())
            .add("client_id", spotifyClientId)
            .add("code_verifier", verifier)
            .build()
        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                val token = json?.optString("access_token").orEmpty()
                if (!response.isSuccessful || token.isBlank()) {
                    val error = json?.optString("error_description")
                        ?.takeIf { it.isNotBlank() } ?: "No se pudo autorizar Spotify"
                    return@withContext Result.failure(IllegalStateException(error))
                }
                val refresh = json?.optString("refresh_token").orEmpty()
                val expires = json?.optLong("expires_in", 3600L) ?: 3600L
                prefs.edit {
                    putString("spotify_token", token)
                    putString("spotify_refresh_token", refresh)
                    putLong("spotify_expires_at", System.currentTimeMillis() + expires * 1000)
                    remove("spotify_verifier")
                    remove("spotify_state")
                }
                Result.success(token)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Conecta con las cookies del reproductor web (mismo flujo que ArchiveTune). */
    suspend fun connectSpotifyWithCookies(
        spDc: String,
        spKey: String,
    ): Result<String> =
        SpotifyWebAuth.fetchAccessToken(spDc, spKey).map { token ->
            prefs.edit {
                putString("spotify_sp_dc", spDc)
                if (spKey.isBlank()) remove("spotify_sp_key") else putString("spotify_sp_key", spKey)
                remove("spotify_refresh_token")
                remove("spotify_verifier")
                remove("spotify_state")
            }
            saveSpotifyToken(token.accessToken, token.expiresAtMillis)
            token.accessToken
        }

    fun saveSpotifyProfile(email: String, displayName: String) {
        prefs.edit {
            putString("spotify_email", email.ifBlank { displayName })
            putString("email", email.ifBlank { displayName })
        }
    }

    fun unlinkSpotify() {
        prefs.edit {
            remove("spotify_token")
            remove("spotify_refresh_token")
            remove("spotify_expires_at")
            remove("spotify_email")
            remove("spotify_sp_dc")
            remove("spotify_sp_key")
            remove("spotify_verifier")
            remove("spotify_state")
        }
    }

    private fun randomUrlSafe(byteCount: Int = 32): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    // ---------- Correo ----------

    suspend fun signUp(email: String, password: String): Result<String> {
        if (!configured) {
            prefs.edit { putString("email", email.trim()) }
            return Result.success(email.trim())
        }
        return try {
            val result = auth().createUserWithEmailAndPassword(email.trim(), password).await()
            prefs.edit { putString("email", email.trim()) }
            Result.success(result.user?.email ?: email.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<String> {
        if (!configured) {
            prefs.edit { putString("email", email.trim()) }
            return Result.success(email.trim())
        }
        return try {
            val result = auth().signInWithEmailAndPassword(email.trim(), password).await()
            Result.success(result.user?.email ?: email.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun currentEmail(): String? =
        if (configured) auth().currentUser?.email ?: localEmail else localEmail

    fun signOut() {
        if (configured) auth().signOut()
        googleClient()?.signOut()
        prefs.edit { clear() }
    }

    companion object {
        const val RC_GOOGLE = 9002
    }
}
