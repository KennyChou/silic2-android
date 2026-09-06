import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 讀取上架簽名金鑰設定
val keyPropertiesFile = rootProject.file("key.properties")
val keyProperties = Properties().apply {
    if (keyPropertiesFile.exists()) load(keyPropertiesFile.inputStream())
}

// 版本控管：由 git 自動產生
// versionCode = git commit 總數（每次 commit 自動遞增，Play Store 用）
// versionName = 最近的 git tag（例如 v1.0.0），無 tag 時退回 commit hash
fun gitVersionCode(): Int =
    runCatching {
        ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .directory(rootDir)
            .start()
            .inputStream.bufferedReader().readText().trim().toInt()
    }.getOrDefault(1)

fun gitVersionName(): String =
    runCatching {
        ProcessBuilder("git", "describe", "--tags", "--always")
            .directory(rootDir)
            .start()
            .inputStream.bufferedReader().readText().trim()
    }.getOrDefault("1.0.0")

android {
    namespace = "cc.kennydev.silic2.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "cc.kennydev.silic2.app"
        minSdk = 26
        targetSdk = 35
        versionCode = gitVersionCode()
        versionName = gitVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(keyProperties["storeFile"] as String)
            storePassword = keyProperties["storePassword"] as String
            keyAlias = keyProperties["keyAlias"] as String
            keyPassword = keyProperties["keyPassword"] as String
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Compose BOM & Material 3
    val composeBom = platform("androidx.compose:compose-bom:2024.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Google AI Edge LiteRT (原生支援 16 KB Page Size 對齊)
    implementation("com.google.ai.edge.litert:litert:1.4.2")
    implementation("com.google.ai.edge.litert:litert-api:1.4.2")
    implementation("com.google.ai.edge.litert:litert-gpu:1.4.2")
    implementation("com.google.ai.edge.litert:litert-support:1.4.2")

    // FFT library for Mel-spectrogram processing
    implementation("com.github.wendykierp:JTransforms:3.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
