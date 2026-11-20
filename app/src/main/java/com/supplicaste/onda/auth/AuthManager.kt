package com.supplicaste.onda.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.tasks.await

class AuthManager(private val context: Context) {
    private val configured =
        BuildConfig.FIREBASE_API_KEY.isNotBlank() && BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()
    val googleAvailable: Boolean get() = configured && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    private val prefs = context.getSharedPreferences("onda_auth", Context.MODE_PRIVATE)

    val isConfigured: Boolean get() = configured
    val modeLabel: String get() = if (configured) "Firebase" else "Local"

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
