plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Fixed debug signing key (see README "Signing"). CI decodes the keystore from repo
// secrets and exposes it through these environment variables. When they are absent
// (local builds, forks, or before the secrets exist) the default debug key is used.
val debugKeystorePath: String? = providers.environmentVariable("DEBUG_KEYSTORE_PATH").orNull
val debugKeystoreFile: File? = debugKeystorePath?.takeIf { it.isNotBlank() }?.let(::file)?.takeIf { it.isFile }

// Monotonic versionCode on CI so each newer build installs as an update.
val ciVersionCode: Int? = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull()

android {
    namespace = "dev.phonefocus"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "dev.phonefocus"
        minSdk = 34
        targetSdk = 36
        versionCode = ciVersionCode ?: 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (debugKeystoreFile != null) {
            create("fixedDebug") {
                storeFile = debugKeystoreFile
                storePassword = providers.environmentVariable("DEBUG_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("DEBUG_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("DEBUG_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            if (debugKeystoreFile != null) {
                signingConfig = signingConfigs.getByName("fixedDebug")
            }
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
