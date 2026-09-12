plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ccwu.orbitime"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ccwu.orbitime"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "0.15.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

// Builds materialize a pinned mature offline dictionary before Android packages
// assets. Network access is build-time only; the installed IME has no INTERNET
// permission. -PorbitSkipMatureImeData=true is reserved for offline development.
val orbitSkipMatureImeData = providers.gradleProperty("orbitSkipMatureImeData")
    .map { it.toBoolean() }
    .orElse(false)
val orbitPython = providers.environmentVariable("ORBIT_PYTHON").orNull
    ?: if (System.getProperty("os.name").lowercase().contains("windows")) "python" else "python3"

val testImeDataPipeline by tasks.registering(Exec::class) {
    group = "orbit ime"
    description = "Run offline tests for Orbit IME dictionary parsing, licensing and packing"
    workingDir(rootProject.projectDir)
    commandLine(orbitPython, "tools/test_ime_data_pipeline.py")
}

val prepareMatureImeAssets by tasks.registering(Exec::class) {
    group = "orbit ime"
    description = "Download pinned audited dictionaries and generate compact offline IME assets"
    workingDir(rootProject.projectDir)
    commandLine(
        orbitPython,
        "tools/prepare_mature_ime_data.py",
        "--output",
        "app/src/main/assets/ime",
    )
    dependsOn(testImeDataPipeline)
    onlyIf { !orbitSkipMatureImeData.get() }
}

tasks.named("preBuild") {
    dependsOn(prepareMatureImeAssets)
}
