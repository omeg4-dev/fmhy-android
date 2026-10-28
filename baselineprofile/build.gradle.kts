// Baseline profile generator and macrobenchmarks for :app. Needs a device or emulator (API 33+):
//   ./gw :app:generateBaselineProfile                                (writes app/src/main/generated)
//   ./gw :baselineprofile:connectedGithubBenchmarkReleaseAndroidTest (frame and startup timings)
// Journeys expect onboarding done and the dataset loaded on the device. On an emulator add
// -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR, and
// -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true to keep the app and its data.
plugins {
  alias(libs.plugins.android.test)
  alias(libs.plugins.androidx.baselineprofile)
}

android {
  namespace = "io.github.omeg4dev.fmhy.baselineprofile"
  compileSdk = 36

  defaultConfig {
    minSdk = 28
    targetSdk = 36
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  targetProjectPath = ":app"

  flavorDimensions += "store"
  productFlavors {
    create("github") { dimension = "store" }
    create("fdroid") { dimension = "store" }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

baselineProfile {
  useConnectedDevices = true
}

dependencies {
  implementation(libs.androidx.test.ext.junit)
  implementation(libs.androidx.test.uiautomator)
  implementation(libs.androidx.benchmark.macro.junit4)
}
