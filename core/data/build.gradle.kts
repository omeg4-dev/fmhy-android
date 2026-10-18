// Pure Kotlin/JVM: dataset access, search and DatasetSync. No Android APIs here, so the unit tests
// run on the JVM against the real bundled SQLite (FTS5 + trigram).
plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
  api(libs.androidx.sqlite)
  api(libs.androidx.sqlite.bundled)
  api(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  api(libs.okhttp)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.okhttp.mockwebserver)
}

tasks.named<ProcessResources>("processResources") {
  from(rootProject.file("schema")) {
    include("dataset.sql")
  }
}

sourceSets {
  create("parityTest") {
    kotlin.srcDir("src/parityTest/kotlin")
    compileClasspath += sourceSets["main"].output + sourceSets["test"].output
    runtimeClasspath += output + compileClasspath
  }
}

val parityTestImplementation by configurations.getting {
  extendsFrom(configurations["testImplementation"], configurations["implementation"])
}
val parityTestRuntimeOnly by configurations.getting {
  extendsFrom(configurations["testRuntimeOnly"])
}

tasks.register<Test>("parityTest") {
  description = "Runs parity tests against reference Python parser output in .cache/parity"
  group = "verification"
  testClassesDirs = sourceSets["parityTest"].output.classesDirs
  classpath = sourceSets["parityTest"].runtimeClasspath
  workingDir = rootProject.rootDir
  maxHeapSize = "2g"
  testLogging {
    showStandardStreams = true
  }
}

tasks.register<JavaExec>("buildSnapshot") {
  description = "Builds the bundled dataset snapshot from local caches"
  group = "build"
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("io.github.omeg4dev.fmhy.data.snapshot.SnapshotBuilder")
  workingDir = rootProject.rootDir
  args(
    "--docs", ".cache/fmhy-edit/docs",
    "--rentry", ".cache/rentry-FMHYB64.html",
    "--unsafe", ".cache/filterlist-domains.txt",
    "--reasons", ".cache/filterlists-reasons.json",
    "--out", "app/src/main/assets/dataset"
  )
}
