plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.shreeharidas.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shreeharidas.app"
        minSdk = 30
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

dependencies {
    // Compose BOM (Updated for API 35 compatibility)
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Use only specific icons instead of the full extended set (saves ~30MB in debug APK)
    implementation("androidx.compose.material:material-icons-core")

    // Activity & Navigation
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Core
    implementation("androidx.core:core-ktx:1.15.0")

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
