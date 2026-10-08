plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pagreylabs.expiry"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pagreylabs.expiry"
        minSdk = 24
        targetSdk = 36
        versionCode = 11
        versionName = "1.0.5"
        // Italian is intentionally excluded because a transitive dependency ships
        // a malformed values-it resource that fails AAPT during resource merging.
        resourceConfigurations.addAll(setOf(
            "es", "en", "ca", "eu", "gl", "fr", "de", "pt", "nl", "pl", "cs",
            "da", "fi", "sv", "nb", "ro", "sk", "sl", "hu", "hr", "bg", "el", "ru",
            "uk", "kk", "uz", "tr", "he", "ur", "ar", "sw", "fil", "id", "ms", "vi",
            "hi", "bn", "pa", "gu", "mr", "ne", "as", "or", "ta", "te", "kn", "ml",
            "si", "th", "lo", "bo", "my", "km", "ko", "ja", "zh-rCN", "zh-rTW"
        ))

        // AdMob identifiers are supplied only by CI through environment variables.
        // Empty values keep local/debug builds ad-free and keep credentials out of git.
        resValue("string", "admob_app_id", System.getenv("EXPIRY_ADMOB_APP_ID").orEmpty())
        resValue("string", "admob_banner_ad_unit_id", System.getenv("EXPIRY_ADMOB_ANDROID_BANNER_ID").orEmpty())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

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
            useVersion("18.2.0")
            because("Keep AAPT-safe barcode scanning resources while retaining BarcodeScanning API")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    // Monetization foundation for the next release:
    // - AdMob 25.5.0 for the free/ad-supported tier.
    // - UMP 4.0.0 for consent and privacy choices.
    // - Play Billing 9.1.0 for the Premium no-ads subscription.
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("com.android.billingclient:billing-ktx:9.1.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
