plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pagreylabs.expiry"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pagreylabs.expiry"
        minSdk = 23
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.0"
        // Italian is intentionally excluded because a transitive dependency ships
        // a malformed values-it resource that fails AAPT during resource merging.
        resourceConfigurations.addAll(setOf(
            "es", "en", "ca", "eu", "gl", "fr", "de", "pt", "nl", "pl", "cs",
            "da", "fi", "sv", "nb", "ro", "sk", "sl", "hu", "hr", "bg", "el", "ru",
            "uk", "kk", "uz", "tr", "he", "ur", "ar", "sw", "fil", "id", "ms", "vi",
            "hi", "bn", "pa", "gu", "mr", "ne", "as", "or", "ta", "te", "kn", "ml",
            "si", "th", "lo", "bo", "my", "km", "ko", "ja", "zh-rCN", "zh-rTW"
        ))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Release signing is enabled only when all credentials are supplied by the
    // CI environment. No signing material or passwords are stored in source.
    val releaseKeystorePath = System.getenv("EXPIRY_KEYSTORE_PATH")
    val releaseStorePassword = System.getenv("EXPIRY_KEYSTORE_PASSWORD")
    val releaseKeyAlias = System.getenv("EXPIRY_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("EXPIRY_KEY_PASSWORD")
    val releaseSigningReady = listOf(
        releaseKeystorePath,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword
    ).all { !it.isNullOrBlank() }

    if (releaseSigningReady) {
        signingConfigs.create("production") {
            storeFile = file(releaseKeystorePath!!)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
        buildTypes.getByName("release") {
            signingConfig = signingConfigs.getByName("production")
        }
    }
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.google.android.gms" && requested.name == "play-services-mlkit-barcode-scanning") {
            // 18.3.1 ships a malformed locale resource. 18.2.0 exposes the same
            // barcode scanning API used here without that resource defect.
            useVersion("18.2.0")
            because("Keep AAPT-safe barcode scanning resources while retaining BarcodeScanning API")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
