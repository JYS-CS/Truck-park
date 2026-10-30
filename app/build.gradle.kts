plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.core)
}
