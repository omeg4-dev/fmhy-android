// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.android.test) apply false
  alias(libs.plugins.androidx.baselineprofile) apply false
  alias(libs.plugins.detekt) apply false
}

// detekt (bug-finding rule sets only, see config/detekt.yml); existing findings live in the
// per-module baseline, so only new ones fail (tools/static-check).
listOf(":app", ":core:data").forEach { path ->
  project(path) {
    apply(plugin = "dev.detekt")
    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
      buildUponDefaultConfig.set(true)
      config.setFrom(rootProject.file("config/detekt.yml"))
      baseline.set(rootProject.file("config/detekt-baseline-${path.removePrefix(":").replace(':', '-')}.xml"))
    }
    dependencies { add("detektPlugins", rootProject.libs.compose.rules.detekt) }
  }
}
