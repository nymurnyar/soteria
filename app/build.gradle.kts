import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Soteria listener config lives in local.properties (not committed):
//   soteria.listenerNumber=+15551234567
//   soteria.backendUrl=https://example.ngrok.app
//   soteria.apiKey=...
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun soteriaProp(key: String): String = localProps.getProperty(key, "").trim()

// Secrets shared with the backend live in <repo>/.env (git-ignored), e.g. GEMINI_API_KEY=...
val dotEnv: Map<String, String> = rootProject.file(".env").let { file ->
    if (!file.exists()) emptyMap() else file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
        .associate { line ->
            val (k, v) = line.split("=", limit = 2)
            k.trim() to v.trim().removeSurrounding("\"").removeSurrounding("'")
        }
}
fun env(key: String, default: String = ""): String = dotEnv[key] ?: System.getenv(key) ?: default

android {
    namespace = "com.example.soteria"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.soteria"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "LISTENER_NUMBER", "\"${soteriaProp("soteria.listenerNumber")}\"")
        buildConfigField("String", "BACKEND_URL", "\"${soteriaProp("soteria.backendUrl")}\"")
        buildConfigField("String", "API_KEY", "\"${soteriaProp("soteria.apiKey")}\"")
        // Gemini AI scam analysis (from .env). Empty key = on-phone keyword check only.
        buildConfigField("String", "GEMINI_API_KEY", "\"${env("GEMINI_API_KEY")}\"")
        buildConfigField("String", "GEMINI_MODEL", "\"${env("GEMINI_MODEL", "gemini-3.5-flash-lite")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.preference)
    implementation(libs.recyclerview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}