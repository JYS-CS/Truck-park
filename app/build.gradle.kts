plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

import java.util.Properties

val envProperties = Properties().apply {
    val envFile = rootProject.file(".env")
    val localPropFile = rootProject.file("local.properties")
    if (envFile.exists()) {
        envFile.reader().use { load(it) }
    } else if (localPropFile.exists()) {
        localPropFile.reader().use { load(it) }
    }
}

val ordsBaseUrl: String = envProperties.getProperty("ORDS_BASE_URL")
    ?: envProperties.getProperty("ords.base.url")
    ?: "https://gf339ddde581301-hibiscusstruckpark.adb.af-johannesburg-1.oraclecloudapps.com/ords/"
val ordsModulePath: String = envProperties.getProperty("ORDS_MODULE_PATH") ?: "admin"
val ordsApiVersion: String = envProperties.getProperty("ORDS_API_VERSION") ?: "v1"

val sessionTimeoutRaw: String = envProperties.getProperty("SESSION_TIMEOUT")
    ?: envProperties.getProperty("session.timeout")
    ?: "20h"

fun parseTimeoutSeconds(raw: String): Long {
    val trimmed = raw.trim().lowercase()
    if (trimmed.isEmpty()) return 20L * 3600L
    trimmed.toLongOrNull()?.let { return it * 3600L }

    val match = Regex("""^(\d+)\s*([a-z]+)$""").find(trimmed)
    if (match != null) {
        val (numStr, unit) = match.destructured
        val num = numStr.toLongOrNull() ?: 20L
        return when (unit) {
            "d", "day", "days" -> num * 24L * 3600L
            "h", "hr", "hrs", "hour", "hours" -> num * 3600L
            "m", "min", "mins", "minute", "minutes" -> num * 60L
            "s", "sec", "secs", "second", "seconds" -> num
            else -> num * 3600L
        }
    }
    return 20L * 3600L
}

val sessionTimeoutSeconds: Long = parseTimeoutSeconds(sessionTimeoutRaw)

android {
    namespace = "com.truckpark.management"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.truckpark.management"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-m1"

        buildConfigField("String", "ORDS_BASE_URL", "\"$ordsBaseUrl\"")
        buildConfigField("String", "ORDS_MODULE_PATH", "\"$ordsModulePath\"")
        buildConfigField("String", "ORDS_API_VERSION", "\"$ordsApiVersion\"")
        buildConfigField("String", "SESSION_TIMEOUT_RAW", "\"$sessionTimeoutRaw\"")
        buildConfigField("long", "SESSION_TIMEOUT_SECONDS", "${sessionTimeoutSeconds}L")
    }

    buildTypes {
        release {
            // Enable and configure shrinking before any production release (Milestone 13).
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // CameraX & Google ML Kit Text Recognition
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.core)
}
