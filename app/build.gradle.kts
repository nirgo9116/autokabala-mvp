import java.util.Properties
import com.google.firebase.appdistribution.gradle.firebaseAppDistribution

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
    id("com.google.firebase.appdistribution")
    id("com.google.firebase.crashlytics")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

val releaseKeystorePath = localProperties.getProperty("release.keystore.path")
val releaseKeystorePassword = localProperties.getProperty("release.keystore.password")
val releaseKeyAlias = localProperties.getProperty("release.key.alias")
val releaseKeyPassword = localProperties.getProperty("release.key.password")
val hasReleaseSigning = !releaseKeystorePath.isNullOrBlank() &&
    !releaseKeystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank() &&
    rootProject.file(releaseKeystorePath).exists()

android {
    namespace = "com.autokabala.listener"
    compileSdk = 36

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.autokabala.share"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val aiProxyUrl = localProperties.getProperty("ai.proxy.url") ?: "https://autokabala-ai-proxy.YOUR-SUBDOMAIN.workers.dev"
        val aiProxySecret = localProperties.getProperty("ai.proxy.secret") ?: ""

        buildConfigField("String", "AI_PROXY_URL", "\"$aiProxyUrl\"")
        buildConfigField("String", "AI_PROXY_SECRET", "\"$aiProxySecret\"")

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            firebaseAppDistribution {
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += "traineddata"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // Ktor Networking
    val ktor_version = "2.3.12"
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Encrypted local storage (iCount credentials)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // OCR
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.8.0")

    // Room
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
