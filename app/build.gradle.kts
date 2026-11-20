import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(prop: String, env: String): String =
    providers.environmentVariable(env).orNull
        ?: localProperties.getProperty(prop)
        ?: ""

android {
    namespace = "com.supplicaste.onda"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.supplicaste.onda"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "1.1.6"

        buildConfigField("String", "FIREBASE_API_KEY", "\"${secret("onda.firebase.apiKey", "ONDA_FIREBASE_API_KEY")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${secret("onda.firebase.projectId", "ONDA_FIREBASE_PROJECT_ID")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${secret("onda.firebase.appId", "ONDA_FIREBASE_APP_ID")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${secret("onda.google.webClientId", "ONDA_GOOGLE_WEB_CLIENT_ID")}\"")
        buildConfigField("String", "GOOGLE_CLIENT_SECRET", "\"${secret("onda.google.clientSecret", "ONDA_GOOGLE_CLIENT_SECRET")}\"")
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${secret("onda.spotify.clientId", "ONDA_SPOTIFY_CLIENT_ID")}\"")
        buildConfigField(
            "String",
            "SPOTIFY_REDIRECT_URI",
            "\"${secret("onda.spotify.redirectUri", "ONDA_SPOTIFY_REDIRECT_URI").ifBlank { "ondaoauth://spotify/callback" }}\""
        )
        buildConfigField("String", "UPDATER_REPO", "\"SUPPLICASTE/Onda\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    signingConfigs {
        create("release") {
            storeFile = file("onda-release.keystore")
            storePassword = secret("onda.storePassword", "ONDA_STORE_PASSWORD")
            keyAlias = secret("onda.keyAlias", "ONDA_KEY_ALIAS").ifBlank { "onda" }
            keyPassword = secret("onda.keyPassword", "ONDA_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.27.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    implementation("com.google.firebase:firebase-auth-ktx:22.3.1")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
