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
    val spotifyAvailable: Boolean get() = BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank()
    val spotifyLinked: Boolean get() = !prefs.getString("spotify_token", null).isNullOrBlank()
    val spotifyEmail: String? get() = prefs.getString("spotify_email", null)

    fun spotifyAccessToken(): String? = prefs.getString("spotify_token", null)

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

    private fun googleClient(): GoogleSignInClient? {
        if (!googleAvailable) return null
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun googleSignInIntent(): Intent? = googleClient()?.signInIntent

    fun googleTokenFrom(resultIntent: Intent?): String? =
        GoogleSignIn.getSignedInAccountFromIntent(resultIntent).result?.idToken

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
            .appendQueryParameter("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
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
            .add("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
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
