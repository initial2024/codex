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
        versionCode = 18
        versionName = "0.18.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

// Builds materialize and validate pinned mature offline dictionaries before
// Android packages assets. Network access is build-time only; the installed IME
// has no INTERNET permission. -PorbitSkipMatureImeData=true is reserved for
// deliberately offline development and must not be used for the user-test APK.
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
    description = "Download pinned audited base dictionaries and generate compact offline IME assets"
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

val augmentV018ImeAssets by tasks.registering(Exec::class) {
    group = "orbit ime"
    description = "Add pinned CC-CEDICT translation/lexicon data and Unicode Emoji 17.0 assets"
    workingDir(rootProject.projectDir)
    commandLine(
        orbitPython,
        "tools/augment_v018_data.py",
        "--output",
        "app/src/main/assets/ime",
    )
    dependsOn(prepareMatureImeAssets)
    onlyIf { !orbitSkipMatureImeData.get() }
}

val validateMatureImeAssets by tasks.registering(Exec::class) {
    group = "orbit ime"
    description = "Reject incomplete mature dictionary packs, missing notices, wrong versions or forbidden manifest capabilities"
    workingDir(rootProject.projectDir)
    commandLine(
        orbitPython,
        "tools/validate_mature_ime_assets.py",
        "--assets",
        "app/src/main/assets/ime",
    )
    dependsOn(augmentV018ImeAssets)
    onlyIf { !orbitSkipMatureImeData.get() }
}

tasks.named("preBuild") {
    dependsOn(validateMatureImeAssets)
}
