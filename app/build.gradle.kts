plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Read through the provider API rather than System.getenv so the configuration
// cache stays valid and simply re-runs when these change.
val buildNumber = providers.environmentVariable("BUILD_NUMBER").orNull?.toIntOrNull() ?: 1
val keystorePath = providers.environmentVariable("SIGNING_KEYSTORE_PATH").orNull

android {
    namespace = "com.gokul.docviewer"

    // androidx.pdf 1.0.0-beta01 requires compiling against API 36 with SDK
    // extension level 19. Note this is independent of targetSdk below: it
    // makes the newer APIs available without opting the app into new runtime
    // behaviour.
    compileSdk = 36
    compileSdkExtension = 19

    defaultConfig {
        applicationId = "com.gokul.docviewer"
        // API 28 is what androidx.pdf's backported renderer needs. Going lower
        // would mean maintaining a second PDF rendering path against the
        // platform PdfRenderer for a small slice of remaining devices.
        minSdk = 28
        targetSdk = 35

        // CI passes the run number so each build installs over the previous
        // one instead of being rejected as a downgrade.
        versionCode = buildNumber
        versionName = "0.1.0"
    }

    signingConfigs {
        // Only defined when CI has decoded a keystore. Without it, release
        // builds are unsigned and only the debug APK is publishable.
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            // Distinguishes a test build from a release install on the same
            // device, and keeps both installable side by side.
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)

    // Supplies the Material 3 XML theme that androidx.pdf's views inflate against.
    implementation(libs.material)

    // Hosting PdfViewerFragment inside Compose.
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.fragment.compose)
    implementation(libs.androidx.pdf.viewer.fragment)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
