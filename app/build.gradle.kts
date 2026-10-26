import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.omeg4dev.fmhy"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.omeg4dev.fmhy"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.5.4"

        val releaseRepo = "omeg4-dev/fmhy-android"
        buildConfigField("String", "RELEASE_REPO", "\"$releaseRepo\"")
    }

    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            buildConfigField("boolean", "UPDATER_ENABLED", "true")
        }
        create("fdroid") {
            dimension = "store"
            buildConfigField("boolean", "UPDATER_ENABLED", "false")
        }
    }

    // Release key lives outside the repo; without it (e.g. on F-Droid's build server) release builds are unsigned.
    val keystoreProps = file(
        System.getenv("FMHY_KEYSTORE_PROPERTIES")
            ?: "${System.getProperty("user.home")}/.local/share/fmhy-android/keystore.properties"
    ).takeIf { it.isFile }?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreProps != null) signingConfigs.getByName("release") else null
        }
    }
    // No Google-encrypted dependency metadata blob in the APK (F-Droid requirement).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    androidResources {
      noCompress += "gzbin"
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}


dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation("androidx.sqlite:sqlite-bundled-jvm:2.7.1")

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  implementation(project(":core:data"))
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.browser)
  implementation(libs.androidx.webkit)
  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.profileinstaller)
  implementation(libs.kotlinx.collections.immutable)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.material.color.utilities)
  implementation(libs.androidx.core.splashscreen)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
